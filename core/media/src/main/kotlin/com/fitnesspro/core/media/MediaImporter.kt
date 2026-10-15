package com.fitnesspro.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import com.fitnesspro.core.media.MediaFileStore.Companion.IMAGE_LIMIT_BYTES
import com.fitnesspro.core.media.MediaFileStore.Companion.VIDEO_LIMIT_BYTES
import com.fitnesspro.core.model.MediaType
import com.fitnesspro.core.model.result.DomainError
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedMedia(
    val fileName: String,
    val thumbnailName: String?,
    val type: MediaType,
    val sizeBytes: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
)

sealed interface ImportResult {
    data class Success(val media: ImportedMedia) : ImportResult
    data class Rejected(val reason: String, val detail: String) : ImportResult
    data class Failed(val error: DomainError) : ImportResult
}

sealed interface ImportProgress {
    data class Copying(val fraction: Float) : ImportProgress
    data class Transcoding(val fraction: Float) : ImportProgress
    data class Compressing(val fraction: Float) : ImportProgress
    data class Thumbnailing(val fraction: Float) : ImportProgress
    data class Completed(val result: ImportResult) : ImportProgress
}

data class ImportOptions(
    /** 视频超过该大小时不直接拒绝，而是提示可压缩（拍摄路径必须是这个行为） */
    val transcodeIfLargerThanBytes: Long? = VIDEO_LIMIT_BYTES,
    val imageLimitBytes: Long = IMAGE_LIMIT_BYTES,
    val videoLimitBytes: Long = VIDEO_LIMIT_BYTES,
    val generateThumbnail: Boolean = true,
    /**
     * 导入时压缩。默认开启：
     * 相册原图 4000×3000 / 10MB 对"动作演示"是纯浪费，压缩后体积通常只剩 1/5，
     * 备份包与磁盘占用同步下降。GIF 不参与压缩（会破坏动画）。
     */
    val compress: Boolean = true,
)

/**
 * 媒体导入流水线。
 *
 * 顺序：元数据探测 → 校验（格式/大小/空间）→ 流式复制到 cache 临时文件 →
 *      原子 rename 到 filesDir/media → 生成缩略图 → 注册到数据库（由调用方完成）
 *
 * 三个关键的正确性要求：
 *  1. **流式复制**：绝不 readBytes()，200MB 视频会把内存打爆（PRD R7）
 *  2. **原子写入**：先写 .part 再 rename，避免"文件写了一半、数据库已登记"
 *  3. **取消可清理**：协程取消时必须删除中间产物，否则会留下孤立文件
 */
@Singleton
class MediaImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: MediaFileStore,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val compressor: MediaCompressor,
    private val time: TimeProvider,
) {

    fun import(uri: Uri, options: ImportOptions = ImportOptions()): Flow<ImportProgress> = flow {
        val result = runImport(uri, options) { progress -> emit(progress) }
        emit(ImportProgress.Completed(result))
    }.flowOn(Dispatchers.IO)

    private suspend fun runImport(
        uri: Uri,
        options: ImportOptions,
        emit: suspend (ImportProgress) -> Unit,
    ): ImportResult {
        val resolver = context.contentResolver

        // 1) 元数据探测
        val mimeType = resolver.getType(uri)
        val mediaType = fileStore.mediaTypeOf(mimeType)
            ?: return ImportResult.Rejected(
                reason = "UNSUPPORTED_FORMAT",
                detail = mimeType ?: "未知格式",
            )

        val declaredSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val size = if (declaredSize > 0) declaredSize else estimateSize(uri)

        // 2) 上限（真正的判定在压缩之后，见步骤 6；这里只用于复制过程的硬上限）
        val limit = if (mediaType == MediaType.VIDEO_LOCAL) options.videoLimitBytes else options.imageLimitBytes

        // 3) 空间校验（预检按声明大小的 2 倍 + 200MB 缓冲）
        if (size > 0 && !fileStore.hasSpaceFor(size)) {
            return ImportResult.Rejected(
                reason = "INSUFFICIENT_STORAGE",
                detail = "需要约 ${(size * 2 + MediaFileStore.REQUIRED_BUFFER_BYTES) / 1024 / 1024} MB，" +
                    "当前可用 ${fileStore.availableBytes() / 1024 / 1024} MB",
            )
        }

        val fileName = fileStore.newFileName(fileStore.extensionOf(mimeType))
        val finalFile = fileStore.mediaFile(fileName)
        val tempFile = File(fileStore.tempDir, "$fileName.part")

        var copiedBytes = 0L
        var totalBytes = if (size > 0) size else 1L
        var width: Int? = null
        var height: Int? = null
        var durationMs: Long? = null
        // 压缩产物：异常/取消时必须和 .part 一起清理，否则会留在 cacheDir 变成垃圾
        var compressedFile: File? = null

        try {
            // 4) 流式复制 + 进度
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    // 声明大小未知时的硬上限：超过上限 2 倍直接中止，避免把磁盘写满。
                    // 这比"导入前预读一遍整个文件"更可靠，也不浪费一次完整 IO。
                    val hardCap = limit * HARD_CAP_FACTOR
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        copiedBytes += read
                        if (copiedBytes > hardCap) {
                            throw IllegalStateException("文件超过大小上限")
                        }
                        output.write(buffer, 0, read)
                        if (size <= 0) totalBytes = copiedBytes
                        emit(ImportProgress.Copying((copiedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)))
                    }
                    output.flush()
                    // fsync：确保数据真正落盘，避免断电/强杀后文件内容不完整
                    output.fd.sync()
                }
            } ?: return ImportResult.Rejected("UNREADABLE", "无法读取所选文件")

            if (copiedBytes == 0L) {
                // 系统相机在部分 ROM 上取消拍摄仍返回 RESULT_OK，且 uri 无内容。
                // 这里是最关键的兜底：把"空文件"识别为取消，而不是写入数据库。
                return ImportResult.Rejected("EMPTY_FILE", "未获取到内容，可能是相机取消或文件已损坏")
            }

            // 5) 压缩（图片下采样 + WebP，视频超阈值转码）
            //    顺序刻意放在"落盘前、校验体积前"：
            //    先压缩再判上限，原本超限的文件压完可能就合规了，避免无谓拒绝。
            var sourceFile = tempFile
            var finalBytes = copiedBytes
            if (options.compress) {
                emit(ImportProgress.Compressing(0.5f))
                val compressed = compressor.compress(tempFile, mediaType, mimeType, copiedBytes)
                if (compressed != null) {
                    runCatching { tempFile.delete() }
                    compressedFile = compressed
                    sourceFile = compressed
                    finalBytes = compressed.length()
                }
                emit(ImportProgress.Compressing(1f))
            }

            // 6) 压缩后仍超限 → 拒绝（拍摄路径由 UI 决定是否再转码一次）
            if (finalBytes > limit) {
                runCatching { sourceFile.delete() }
                return ImportResult.Rejected(
                    reason = "TOO_LARGE",
                    detail = "文件为 ${finalBytes / 1024 / 1024} MB，超过上限 ${limit / 1024 / 1024} MB",
                )
            }

            // 7) 原子改名落盘
            if (!sourceFile.renameTo(finalFile)) {
                sourceFile.copyTo(finalFile, overwrite = true)
                sourceFile.delete()
            }

            // 8) 读取宽高与时长（失败不影响导入，只是元数据缺失）
            readMetadata(finalFile, mediaType).let { meta ->
                width = meta.first
                height = meta.second
                durationMs = meta.third
            }

            // 9) 生成缩略图
            var thumbnailName: String? = null
            if (options.generateThumbnail) {
                emit(ImportProgress.Thumbnailing(0f))
                thumbnailName = thumbnailGenerator.generate(
                    source = finalFile,
                    mediaType = mediaType,
                    sourceUri = uri,
                    baseName = fileName.substringBeforeLast('.'),
                )
                emit(ImportProgress.Thumbnailing(1f))
            }

            return ImportResult.Success(
                ImportedMedia(
                    fileName = fileName,
                    thumbnailName = thumbnailName,
                    type = mediaType,
                    sizeBytes = finalBytes,
                    durationMs = durationMs,
                    width = width,
                    height = height,
                )
            )
        } catch (t: Throwable) {
            // 取消与异常都必须清理中间产物，否则会留下孤立文件
            runCatching { tempFile.delete() }
            compressedFile?.let { runCatching { it.delete() } }
            if (finalFile.exists() && t is kotlinx.coroutines.CancellationException) {
                runCatching { finalFile.delete() }
            }
            throw t
        }
    }

    /**
     * 声明大小未知时返回 -1，不在导入前预读整份文件。
     * 真正的兜底是复制过程中的硬上限（见 HARD_CAP_FACTOR）。
     */
    private fun estimateSize(uri: Uri): Long = runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    }.getOrDefault(-1L)

    private suspend fun readMetadata(
        file: File,
        mediaType: MediaType,
    ): Triple<Int?, Int?, Long?> = withContext(Dispatchers.IO) {
        // MediaMetadataRetriever 到 API 29 才实现 AutoCloseable，minSdk 26 必须手动 release()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            Triple(width, height, duration)
        } catch (t: Throwable) {
            Timber.w(t, "读取媒体元数据失败: %s (%s)", file.name, mediaType.name)
            Triple(null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024

        /** 声明大小未知时，复制过程中的硬上限倍数 */
        private const val HARD_CAP_FACTOR = 2L
    }
}

/**
 * 缩略图生成。
 *
 * 优先用 ContentResolver.loadThumbnail（API 29+，系统有缓存，快且省内存），
 * 低版本回退到 MediaMetadataRetriever + inSampleSize 下采样。
 * 输出统一为 WebP：同画质下体积远小于 PNG/JPEG。
 */
@Singleton
class ThumbnailGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: MediaFileStore,
) {

    suspend fun generate(
        source: File,
        mediaType: MediaType,
        sourceUri: Uri?,
        baseName: String,
    ): String? = withContext(Dispatchers.IO) {
        val targetName = "$baseName.webp"
        val targetFile = fileStore.thumbFile(targetName)

        runCatching {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && sourceUri != null) {
                context.contentResolver.loadThumbnail(
                    sourceUri,
                    Size(MediaFileStore.THUMBNAIL_MAX_EDGE_PX, MediaFileStore.THUMBNAIL_MAX_EDGE_PX),
                    null,
                )
            } else if (mediaType == MediaType.VIDEO_LOCAL) {
                videoFrame(source)
            } else {
                decodeSampled(source)
            } ?: return@runCatching null

            val scaled = scaleDown(bitmap)
            // WEBP_LOSSY 是 API 30 才有的常量；低版本用已废弃的 WEBP（语义相同：有损）
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            FileOutputStream(targetFile).use { output ->
                scaled.compress(format, 80, output)
            }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            targetName
        }.getOrElse {
            Timber.w(it, "生成缩略图失败: %s", source.name)
            null
        }
    }

    private fun videoFrame(source: File): Bitmap? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(source.absolutePath)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }.getOrNull()

    /** 下采样解码：避免把 4000×3000 的原图整张读进内存 */
    private fun decodeSampled(source: File): Bitmap? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(source.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MediaFileStore.THUMBNAIL_MAX_EDGE_PX * 2 ||
            bounds.outHeight / sample > MediaFileStore.THUMBNAIL_MAX_EDGE_PX * 2
        ) {
            sample *= 2
        }
        android.graphics.BitmapFactory.decodeFile(
            source.absolutePath,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val max = MediaFileStore.THUMBNAIL_MAX_EDGE_PX
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= max) return bitmap
        val ratio = max.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}

/** 供上层判断导入结果的小工具 */
fun Outcome<ImportedMedia>.mediaOrNull(): ImportedMedia? = (this as? Outcome.Success)?.data

/** 便捷包装：把 ImportResult 转成 Outcome（用于「失败也要返回原因」的场景） */
fun ImportResult.toOutcome(): Outcome<ImportedMedia> = when (this) {
    is ImportResult.Success -> Outcome.Success(media)
    is ImportResult.Rejected -> Outcome.Failure(DomainError.MediaRejected(reason, detail))
    is ImportResult.Failed -> Outcome.Failure(error)
}

/** 供悬停式调用：确保异常路径也被收敛 */
suspend fun importSafely(
    importer: MediaImporter,
    uri: Uri,
    options: ImportOptions = ImportOptions(),
): Outcome<ImportedMedia> = runCatchingOutcomeSuspend {
    var outcome: Outcome<ImportedMedia> = Outcome.Failure(DomainError.Unknown(IllegalStateException()))
    importer.import(uri, options).collect { progress ->
        if (progress is ImportProgress.Completed) outcome = progress.result.toOutcome()
    }
    outcome.getOrNull() ?: throw IllegalStateException(
        (outcome.errorOrNull() as? DomainError.MediaRejected)?.detail ?: "导入失败"
    )
}
