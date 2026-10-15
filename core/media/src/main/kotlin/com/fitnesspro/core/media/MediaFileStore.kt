package com.fitnesspro.core.media

import android.content.Context
import android.os.StatFs
import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 媒体文件存储。
 *
 * 约定（DEV 文档 §6）：
 *  - 文件放在 `filesDir/media/`，缩略图放 `filesDir/thumbs/`，全部为应用私有目录
 *  - **数据库只存文件名，绝不存绝对路径** —— 沙箱路径在备份恢复/迁移后可能变化
 *  - 命名 `{UUID}.{ext}`，避免同名冲突
 */
@Singleton
class MediaFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val mediaDir: File get() = File(context.filesDir, DIR_MEDIA).ensure()
    val thumbDir: File get() = File(context.filesDir, DIR_THUMBS).ensure()
    val tempDir: File get() = File(context.cacheDir, DIR_TMP).ensure()
    val transcodeDir: File get() = File(context.cacheDir, DIR_TRANSCODE).ensure()

    /** 新文件名：UUID + 扩展名 */
    fun newFileName(extension: String): String =
        "${UUID.randomUUID()}.${extension.lowercase().ifBlank { "bin" }}"

    fun mediaFile(fileName: String): File = File(mediaDir, fileName)

    fun thumbFile(thumbnailName: String): File = File(thumbDir, thumbnailName)

    /** 备份包残留的解压临时目录 */
    fun importTempDir(): File = File(context.cacheDir, DIR_IMPORT_TMP).ensure()

    fun fileFor(ref: MediaRef.Local): File = mediaFile(ref.fileName)

    fun exists(fileName: String): Boolean = mediaFile(fileName).exists()

    /** 返回 true 表示文件确实被删除（或本来就不存在） */
    fun deleteMedia(fileName: String): Boolean {
        val file = mediaFile(fileName)
        return !file.exists() || file.delete()
    }

    fun deleteThumb(thumbnailName: String): Boolean {
        val file = thumbFile(thumbnailName)
        return !file.exists() || file.delete()
    }

    fun sizeOf(fileName: String): Long = mediaFile(fileName).let { if (it.exists()) it.length() else 0L }

    /** 可用空间 */
    fun availableBytes(): Long = runCatching {
        StatFs(context.filesDir.absolutePath).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    /**
     * 导入前的空间预检。
     * 要求可用空间 ≥ 文件大小 × 2 + 200MB 缓冲：
     *   一份用于落盘，一份用于转码时的临时文件，缓冲用于避免写满磁盘导致数据库损坏。
     */
    fun hasSpaceFor(bytes: Long): Boolean =
        availableBytes() >= bytes * 2 + REQUIRED_BUFFER_BYTES

    fun purgeTempDirs() {
        listOf(tempDir, transcodeDir, importTempDir()).forEach { dir ->
            runCatching { dir.deleteRecursively(); dir.mkdirs() }
        }
    }

    private fun File.ensure(): File = apply { if (!exists()) mkdirs() }

    /** 媒体文件后缀 → MediaType */
    fun mediaTypeOf(mimeType: String?): MediaType? = when (mimeType?.lowercase()) {
        "image/gif" -> MediaType.GIF
        "image/webp" -> MediaType.GIF
        "image/png", "image/jpeg", "image/jpg", "image/heic" -> MediaType.IMAGE
        "video/mp4", "video/webm", "video/quicktime", "video/x-matroska" -> MediaType.VIDEO_LOCAL
        else -> null
    }

    fun extensionOf(mimeType: String?): String = when (mimeType?.lowercase()) {
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/png" -> "png"
        "image/jpeg", "image/jpg" -> "jpg"
        "image/heic" -> "heic"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        else -> "mp4"
    }

    companion object {
        const val DIR_MEDIA = "media"
        const val DIR_THUMBS = "thumbs"
        const val DIR_TMP = "tmp"
        const val DIR_TRANSCODE = "transcode"
        const val DIR_IMPORT_TMP = "import_tmp"

        /** 上传前空间缓冲区：200MB（PRD §4.3） */
        const val REQUIRED_BUFFER_BYTES = 200L * 1024 * 1024

        const val VIDEO_LIMIT_BYTES = 200L * 1024 * 1024
        const val IMAGE_LIMIT_BYTES = 20L * 1024 * 1024

        /** 缩略图长边上限 */
        const val THUMBNAIL_MAX_EDGE_PX = 512

        // ---- 压缩参数 ----
        /** 图片保存长边上限：动作演示只需看清动作，1600px 足够 */
        const val IMAGE_MAX_EDGE_PX = 1600

        /** 小于该体积且尺寸未超限的图片不压缩：避免为低熵小图白白损画质 */
        const val IMAGE_COMPRESS_THRESHOLD_BYTES = 1024L * 1024

        /** WebP 有损质量 */
        const val IMAGE_QUALITY = 82

        /** 视频超过该体积才转码：Media3 重编码耗时，小视频不值得让用户等 */
        const val VIDEO_COMPRESS_THRESHOLD_BYTES = 30L * 1024 * 1024
    }
}
