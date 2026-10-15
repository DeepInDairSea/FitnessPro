package com.fitnesspro.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.fitnesspro.core.model.MediaType
import com.fitnesspro.core.model.result.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * 导入时压缩。
 *
 * 为什么必须压缩：
 *  - 相册原图动辄 4000×3000、8-12MB，而动作演示只需要"看清动作"，1600px 长边足够；
 *    不压缩的话几十个动作就能吃满几百 MB，备份包也随之膨胀。
 *  - 系统相机拍的视频码率不可控，4K 30 秒轻松超过上限，必须有一条"压缩后导入"的出口。
 *
 * 三条刻意不做的事（都是坑）：
 *  1. **不引第三方压缩库**：APK 体积与升级风险换不来多少收益，平台自带的
 *     Bitmap 编码 + Media3 Transformer 已经够用。
 *  2. **不压缩 GIF**：动图压缩等于把动画压成静态图，数据被破坏。GIF 只做大小上限校验。
 *  3. **压缩结果必须比原文件小才采用**：宁可保留原文件，也不做"越压越大"的蠢事。
 */
@Singleton
class MediaCompressor @Inject constructor(
    private val fileStore: MediaFileStore,
    private val transcoder: MediaTranscoder,
) {

    /**
     * 按类型压缩，返回压缩后的文件（在 cacheDir 临时目录），
     * 无需压缩或压缩失败时返回 null —— 调用方回退到原文件。
     */
    suspend fun compress(
        source: File,
        type: MediaType,
        mimeType: String?,
        sizeBytes: Long,
    ): File? = when (type) {
        MediaType.IMAGE -> compressImage(source)
        MediaType.VIDEO_LOCAL -> compressVideo(source, sizeBytes)
        // GIF 保留动画；其余类型（外链等）不落盘，无需压缩
        MediaType.GIF, MediaType.VIDEO_LINK, MediaType.NONE -> null
    }

    /** 静态图片：下采样到长边 [MediaFileStore.IMAGE_MAX_EDGE_PX] + WebP 有损 */
    suspend fun compressImage(source: File): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val longestEdge = max(bounds.outWidth, bounds.outHeight)
            // 尺寸与体积都不大时不动它：避免为了"看起来压缩了"而损失画质
            if (longestEdge <= MediaFileStore.IMAGE_MAX_EDGE_PX &&
                source.length() <= MediaFileStore.IMAGE_COMPRESS_THRESHOLD_BYTES
            ) {
                return@runCatching null
            }

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MediaFileStore.IMAGE_MAX_EDGE_PX) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@runCatching null

            val scaled = scaleDown(decoded)
            val output = File(fileStore.tempDir, "${source.nameWithoutExtension}_c.webp")
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            FileOutputStream(output).use { out ->
                scaled.compress(format, MediaFileStore.IMAGE_QUALITY, out)
                out.flush()
                out.fd.sync()
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()

            // 压出来反而更大（低熵图片常见）→ 保留原文件
            output.takeIf { it.exists() && it.length() < source.length() }
                ?: run { output.delete(); null }
        }.getOrElse {
            Timber.w(it, "图片压缩失败，回退原文件: %s", source.name)
            null
        }
    }

    /** 视频：超过阈值才转码。Media3 Transformer 重编码耗时，小文件不值得等 */
    suspend fun compressVideo(source: File, sizeBytes: Long): File? {
        if (sizeBytes < MediaFileStore.VIDEO_COMPRESS_THRESHOLD_BYTES) return null
        return when (val outcome = transcoder.transcode(source)) {
            is Outcome.Success -> outcome.data.takeIf { it.exists() && it.length() < source.length() }
                ?: run { outcome.data.delete(); null }
            is Outcome.Failure -> {
                Timber.w("视频压缩失败，回退原文件: %s", outcome.error.code)
                null
            }
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val max = MediaFileStore.IMAGE_MAX_EDGE_PX
        val longest = max(bitmap.width, bitmap.height)
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
