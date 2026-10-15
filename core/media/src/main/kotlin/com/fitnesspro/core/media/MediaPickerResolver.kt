package com.fitnesspro.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 媒体选择来源解析。
 *
 * 主路径是 **Photo Picker**：它由系统提供，用户只授权自己选中的那一张，
 * 应用**完全不需要存储权限**。这让我们彻底绕开 Android 13/14 的
 * `READ_MEDIA_*` 与"部分媒体访问（reselect）"复杂度（ADR-05）。
 *
 * 但 Photo Picker 的**回填依赖 Google Play 服务**（API 33 以下）。国内无 GMS 机型
 * 上 `isPhotoPickerAvailable` 返回 false，必须降级到 SAF —— 这条路径从第一行代码
 * 起就必须存在，不能当补丁后加。
 */
enum class MediaPickerSource {
    /** 系统 Photo Picker（首选，零权限） */
    PHOTO_PICKER,

    /** Storage Access Framework（无 GMS 时的降级路径，同样零权限） */
    SAF_DOCUMENT,
}

@Singleton
class MediaPickerResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun resolve(): MediaPickerSource =
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)) {
            MediaPickerSource.PHOTO_PICKER
        } else {
            MediaPickerSource.SAF_DOCUMENT
        }

    /**
     * SAF 降级时的 Intent。
     *
     * 注意 `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`：我们会在导入过程中多次读取该 uri
     * （探测大小、复制、生成缩略图），必须拿到持久化授权，否则进程被回收后授权失效。
     */
    fun buildSafIntent(mimeTypes: List<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

    fun persistPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /**
     * 系统相机拍摄。
     *
     * 走系统相机 Intent 的**好处是清单里不需要声明 `CAMERA` 权限**，
     * 用户权限列表里也看不到相机权限，心理门槛更低。
     *
     * 但必须接受两个现实：
     *  1. 分辨率/码率不可控，可能产出远超 200MB 的 4K 文件 → 由导入流水线提示压缩
     *  2. 部分 ROM 在用户取消拍摄时仍返回 RESULT_OK → 由导入流水线识别"空文件"兜底
     */
    fun captureTargetFile(extension: String = "mp4"): Pair<Uri, java.io.File> {
        val dir = java.io.File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
        val file = java.io.File(dir, "capture_${System.currentTimeMillis()}.$extension")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return uri to file
    }

    /**
     * 通知权限被永久拒绝时，引导到系统设置。
     * 这是唯一正确的处理方式 —— 反复弹系统权限框只会让用户更反感。
     */
    fun appSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 回到应用后调用，判断拍摄/选择的临时文件是否需要清理 */
    fun cleanupCaptureDir() {
        runCatching { java.io.File(context.cacheDir, CAPTURE_DIR).deleteRecursively() }
    }

    private companion object {
        const val CAPTURE_DIR = "capture"
    }
}
