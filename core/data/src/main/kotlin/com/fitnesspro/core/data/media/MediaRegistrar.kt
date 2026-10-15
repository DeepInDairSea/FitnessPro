package com.fitnesspro.core.data.media

import com.fitnesspro.core.database.dao.MediaDao
import com.fitnesspro.core.database.entity.MediaAssetEntity
import com.fitnesspro.core.media.ImportedMedia
import com.fitnesspro.core.model.MediaAssetState
import com.fitnesspro.core.model.time.TimeProvider
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把导入成功的媒体登记到 media_asset 表。
 *
 * 这张表的作用不是"存元数据"（那些已经在引用方表里了），
 * 而是让 GC 能区分两种"没被引用的文件"：
 *  - 登记过但已无引用 → 用户删过计划，可以清理
 *  - 从未登记（导入一半失败/异常残留）→ 也应清理，但要更谨慎
 *
 * 必须在导入成功后**立即**登记：如果先写引用、后登记，中间崩溃就会产生
 * "有引用但不在登记表"的文件，GC 无法识别。
 *
 * 状态机：PENDING（刚导入，还没写引用）→ ACTIVE（引用已落库）。
 * 反向也会发生：动作被彻底删除时把登记行打回 PENDING，让 GC 用短宽限期回收。
 */
@Singleton
class MediaRegistrar @Inject constructor(
    private val mediaDao: MediaDao,
    private val time: TimeProvider,
) {

    suspend fun register(media: ImportedMedia, mimeType: String? = null) {
        mediaDao.insert(
            MediaAssetEntity(
                fileName = media.fileName,
                thumbnailName = media.thumbnailName,
                mediaType = media.type.name,
                mimeType = mimeType,
                sizeBytes = media.sizeBytes,
                durationMs = media.durationMs,
                width = media.width,
                height = media.height,
                createdAt = time.nowEpochMs(),
                lastReferencedAt = time.nowEpochMs(),
                // 关键：导入时不是"已引用"，引用要等用户点保存。
                // 否则用户导入后放弃保存，这个文件会一直占着 ACTIVE 的 30 天宽限期。
                state = MediaAssetState.PENDING.name,
            )
        )
    }

    /** 引用已写入 → 转为已引用 */
    suspend fun markActive(fileName: String) {
        mediaDao.setState(fileName, MediaAssetState.ACTIVE.name, time.nowEpochMs())
    }

    /** 引用被移除（动作彻底删除/演示被替换）→ 打回待引用，交给短宽限期 GC */
    suspend fun markPending(fileName: String) {
        runCatching { mediaDao.setState(fileName, MediaAssetState.PENDING.name, time.nowEpochMs()) }
            .onFailure { Timber.w(it, "标记媒体为未引用失败：%s", fileName) }
    }

    /**
     * 解除引用时更新 lastReferencedAt。
     * 不直接删除登记行 —— 让 GC 按"宽限期"决定，避免用户刚解除关联就永久丢失文件。
     */
    suspend fun touch(fileName: String) {
        mediaDao.touch(fileName, time.nowEpochMs())
    }
}
