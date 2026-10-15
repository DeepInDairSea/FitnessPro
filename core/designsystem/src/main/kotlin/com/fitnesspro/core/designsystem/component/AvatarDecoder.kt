package com.fitnesspro.core.designsystem.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * 用户头像统一解码入口：设置页、首页、分享卡片共用。
 *
 * 修复两个历史问题：
 * 1. **EXIF 旋转**：Photo Picker 选的图常带方向标记，BitmapFactory.decodeFile 不处理，
 *    导致头像在分享卡里"横躺/倒置"。这里统一读取 EXIF 并旋转到位。
 * 2. **旧缩略图遗留**：早期版本生成 avatar_thumb.jpg 时没做 EXIF 修正，
 *    已损坏的缩略图无法靠重解码修复。只要原图 avatar.jpg 还在，就永远绕过缩略图
 *    直接从原图解码（256~512px 的降采样成本可忽略），相当于对所有旧头像自动生效。
 *
 * 另外做了降采样（目标边长 maxDim）+ 进程内 LruCache，避免大图 OOM 与重复解码。
 */
object AvatarDecoder {

    private const val LEGACY_THUMB = "avatar_thumb.jpg"
    private const val ORIGINAL = "avatar.jpg"

    /** 最多缓存 2 个头像（通常只有 1 个用户），512px ARGB_8888 ≈ 1MB/张，开销可忽略 */
    private val cache = object : LruCache<String, Bitmap>(2) {}

    fun decode(context: Context, avatarUri: String?, maxDim: Int = 512): Bitmap? {
        if (avatarUri.isNullOrBlank()) return null
        // 键里带上文件修改时间：用户重新选头像后文件变更，缓存自动失效、立即用新图，
        // 否则只按路径做键会导致"换头像但卡片/首页仍显示旧图"的体感问题
        val file = File(context.filesDir, avatarUri)
        val version = if (file.exists()) file.lastModified() else 0L
        val key = "$avatarUri@$maxDim@$version"
        cache.get(key)?.let { return it }

        val bitmap = decodeUncached(context, avatarUri, maxDim)
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    private fun decodeUncached(context: Context, avatarUri: String, maxDim: Int): Bitmap? {
        // 路径候选：filesDir 相对路径（当前约定）→ 绝对路径 → content:// / file://
        val file = File(context.filesDir, avatarUri)

        // 旧缩略图修复：thumb 是早期无 EXIF 逻辑的产物，只要原图在就强制用原图
        val sourceFile = if (file.name == LEGACY_THUMB && File(context.filesDir, ORIGINAL).exists()) {
            File(context.filesDir, ORIGINAL)
        } else {
            file
        }

        if (sourceFile.exists()) {
            decodeFileOriented(sourceFile.absolutePath, maxDim)?.let { return it }
        }
        runCatching { decodeFileOriented(avatarUri, maxDim) }.getOrNull()?.let { return it }
        return runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(avatarUri))?.use { stream ->
                decodeStreamOriented(stream, maxDim)
            }
        }.getOrNull()
    }

    private fun decodeFileOriented(path: String, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDim)
        }
        val raw = BitmapFactory.decodeFile(path, opts) ?: return null
        return applyExif(path, raw)
    }

    private fun decodeStreamOriented(stream: java.io.InputStream, maxDim: Int): Bitmap? {
        // 流只能读一遍：先整体读进内存（头像源最多几 MB），再走边界→采样两段式解码
        val bytes = stream.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDim)
        }
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val exif = runCatching {
            ExifInterface(java.io.ByteArrayInputStream(bytes))
        }.getOrNull()
        return rotateToUpright(raw, exif, maxDim)
    }

    private fun applyExif(path: String, raw: Bitmap): Bitmap {
        val exif = runCatching { ExifInterface(path) }.getOrNull()
        return rotateToUpright(raw, exif, maxOf(raw.width, raw.height))
    }

    private fun rotateToUpright(raw: Bitmap, exif: ExifInterface?, maxDim: Int): Bitmap {
        val degrees = when (
            runCatching { exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> 0f
            ExifInterface.ORIENTATION_TRANSPOSE -> 90f
            ExifInterface.ORIENTATION_TRANSVERSE -> 270f
            else -> 0f
        }
        val rotated = if (degrees == 0f) raw else Bitmap.createBitmap(
            raw, 0, 0, raw.width, raw.height,
            Matrix().apply { postRotate(degrees) }, true,
        )
        // 90° 旋转后宽高互换，可能超出 maxDim：再兜底缩一刀
        val largest = maxOf(rotated.width, rotated.height)
        if (largest <= maxDim) return rotated
        val scale = maxDim.toFloat() / largest
        return Bitmap.createScaledBitmap(
            rotated,
            (rotated.width * scale).toInt().coerceAtLeast(1),
            (rotated.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun sampleSizeFor(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / (sample * 2) >= maxDim) sample *= 2
        return sample
    }
}
