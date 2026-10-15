package com.fitnesspro.core.media

import com.fitnesspro.core.model.MediaRef
import com.fitnesspro.core.model.MediaType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI 解析媒体引用的唯一入口。
 *
 * 把"数据库里的文件名"翻译成"磁盘上真实的 File"，并处理文件丢失的情况 ——
 * 文件丢失必须成为一个**可展示的状态**（占位图 + 重新关联按钮），而不是崩溃或空白。
 */
sealed interface MediaSource {
    /** 本地文件存在 */
    data class LocalFile(
        val file: File,
        val thumbnail: File?,
        val type: MediaType,
    ) : MediaSource {
        val isVideo: Boolean get() = type == MediaType.VIDEO_LOCAL
    }

    /** 外部链接。MediaRef.Remote 只保存 URL，类型恒为外链视频 */
    data class Remote(val url: String, val type: MediaType = MediaType.VIDEO_LINK) : MediaSource

    /** 数据库有记录但磁盘上文件没了 */
    data class Missing(val fileName: String) : MediaSource
}

@Singleton
class MediaAccess @Inject constructor(
    private val fileStore: MediaFileStore,
) {

    fun resolve(media: MediaRef?): MediaSource? = when (media) {
        null -> null
        is MediaRef.Remote -> MediaSource.Remote(url = media.url)
        is MediaRef.Local -> {
            val file = fileStore.mediaFile(media.fileName)
            if (!file.exists()) {
                MediaSource.Missing(media.fileName)
            } else {
                MediaSource.LocalFile(
                    file = file,
                    thumbnail = media.thumbnailName
                        ?.let { fileStore.thumbFile(it) }
                        ?.takeIf { it.exists() },
                    type = media.type,
                )
            }
        }
    }

    /** 缩略图目录，供 Compose 组件直接拼接 File 使用 */
    fun thumbnailDir(): File = fileStore.thumbDir

    fun mediaDir(): File = fileStore.mediaDir

    /** 演示资源是否可播放（用于列表项的"播放"图标状态） */
    fun isPlayable(media: MediaRef?): Boolean = when (val source = resolve(media)) {
        is MediaSource.LocalFile -> true
        is MediaSource.Remote -> true
        else -> false
    }

    /** 磁盘占用（用于设置页的媒体空间统计） */
    fun totalMediaBytes(): Long =
        fileStore.mediaDir.listFiles()?.sumOf { it.length() } ?: 0L
}
