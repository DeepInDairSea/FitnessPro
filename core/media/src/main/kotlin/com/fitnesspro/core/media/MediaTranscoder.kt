package com.fitnesspro.core.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.fitnesspro.core.model.result.DomainError
import com.fitnesspro.core.model.result.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 视频压缩。
 *
 * 为什么必须有：系统相机不可控分辨率与码率，4K/60fps 拍 30 秒就超过 200MB 上限。
 * 而**用户刚拍的视频不能被直接拒绝** —— 那等于告诉用户"你刚才白拍了"。
 * 所以导入流水线在超限时走「提示 → 压缩 → 导入」。
 *
 * 实现用 Media3 Transformer（H.264 + AAC 重编码）。
 *
 * 诚实记录的限制：当前只重编码，不做分辨率缩放。
 * 对 4K/HEVC 源文件收益明显；若源原本就是低码率 H.264，收益有限。
 * 分辨率/码率控制需要效果链（Presentation / ScaleAndRotate），
 * 属于 M6 后续优化，需在真机上验证 Media3 版本支持的效果 API。
 */
@Singleton
class MediaTranscoder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: MediaFileStore,
) {

    /**
     * 转码到临时文件。成功后由调用方移动到 media 目录。
     * 协程取消时会同时取消 Transformer 并清理半成品，避免留下孤立文件。
     */
    suspend fun transcode(
        source: File,
        outputFile: File = File(
            fileStore.transcodeDir,
            "${source.nameWithoutExtension}_out_${System.currentTimeMillis()}.mp4",
        ),
    ): Outcome<File> = withContext(Dispatchers.IO) {
        if (!source.exists()) {
            return@withContext Outcome.Failure(DomainError.MediaFileMissing(source.name))
        }

        // 必须显式指定泛型：否则 onCompleted 里的 Outcome.Success 会把 T 推断成 Outcome.Success<File>
        suspendCancellableCoroutine<Outcome<File>> { continuation ->
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Outcome.Success(outputFile))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    Timber.w(exportException, "视频转码失败：%s", source.name)
                    runCatching { outputFile.delete() }
                    if (continuation.isActive) {
                        continuation.resume(
                            Outcome.Failure(
                                DomainError.TranscodeFailure(
                                    exportException.message ?: exportException.errorCodeName,
                                )
                            )
                        )
                    }
                }
            }

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()

            continuation.invokeOnCancellation {
                runCatching { transformer.cancel() }
                runCatching { outputFile.delete() }
            }

            try {
                transformer.start(
                    MediaItem.fromUri(android.net.Uri.fromFile(source)),
                    outputFile.absolutePath,
                )
            } catch (t: Throwable) {
                Timber.w(t, "无法启动转码：%s", source.name)
                runCatching { outputFile.delete() }
                if (continuation.isActive) {
                    continuation.resume(
                        Outcome.Failure(DomainError.TranscodeFailure(t.message ?: "start_failed"))
                    )
                }
            }
        }
    }

    /** 只有明显超限时才提示压缩，避免对小文件做无意义的等待 */
    fun shouldSuggestTranscode(
        sizeBytes: Long,
        limitBytes: Long = MediaFileStore.VIDEO_LIMIT_BYTES,
    ): Boolean = sizeBytes > limitBytes

    /** 预估压缩后体积，用于"预计可降到约 XX MB"的提示 */
    fun estimateCompressedBytes(sizeBytes: Long): Long = (sizeBytes * ESTIMATED_RATIO).toLong()

    private companion object {
        /** 经验值：H.264 重编码通常能压到原始体积的 40% 左右 */
        const val ESTIMATED_RATIO = 0.4
    }
}
