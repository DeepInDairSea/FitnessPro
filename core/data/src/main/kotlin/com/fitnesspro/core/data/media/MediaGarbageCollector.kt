package com.fitnesspro.core.data.media

import com.fitnesspro.core.database.dao.MediaDao
import com.fitnesspro.core.media.MediaFileStore
import com.fitnesspro.core.model.MediaAssetState
import com.fitnesspro.core.model.time.TimeProvider
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 孤立媒体扫描与清理。
 *
 * 安全约束（必须逐条守住，任何一条破掉都会造成用户数据丢失）：
 *
 * 1. **回收站内的计划必须被计入引用方。**
 *    `MediaDao.findOrphanCandidates()` 刻意**不筛选** `deleted_at`。
 *    如果筛选了，流程会变成"删除计划 → 媒体被判为孤立 → GC 清理 → 用户从回收站恢复计划
 *    → 演示文件已经没了"。这是本类最容易写错的地方。
 *
 * 2. **只提示，不静默删除。**
 *    扫描只产出报告，真正删除必须由用户在设置页确认。用户数据不该在后台悄悄消失。
 *
 * 3. **宽限期。**
 *    刚导入但还没写引用的文件（导入成功、写库前崩溃）不应被立刻清理，
 *    否则会误删用户刚拍的东西。宽限期默认 30 天。
 */
@Singleton
class MediaGarbageCollector @Inject constructor(
    private val fileStore: MediaFileStore,
    private val mediaDao: MediaDao,
    private val time: TimeProvider,
) {

    data class OrphanFile(
        val fileName: String,
        val sizeBytes: Long,
        val reason: Reason,
    ) {
        enum class Reason {
            /** 登记过，但已经没有任何引用方 */
            UNREFERENCED,

            /** 磁盘上有文件，但从未登记（异常残留） */
            UNREGISTERED,
        }
    }

    data class ScanReport(
        val totalFiles: Int,
        val totalBytes: Long,
        val orphans: List<OrphanFile>,
        val orphanBytes: Long,
        val scannedAt: Long,
    ) {
        val hasCleanable: Boolean get() = orphans.isNotEmpty()
        val cleanableBytes: Long get() = orphanBytes
    }

    /**
     * 只读扫描，不删除任何文件。
     *
     * 宽限期按状态区分（P1 的关键约束）：
     *  - PENDING（导入后没保存 / 引用方已删除）：24 小时即可回收，
     *    这类孤儿是常态路径产物，30 天太久会白白占着空间
     *  - ACTIVE（引用方存在过）：30 天，给"误删后想恢复"留足余地
     */
    suspend fun scan(gracePeriodDays: Int = DEFAULT_GRACE_DAYS): ScanReport = runCatching {
        val now = time.nowEpochMs()
        val graceThreshold = now - gracePeriodDays * MILLIS_PER_DAY
        val pendingThreshold = now - PENDING_GRACE_HOURS * MILLIS_PER_HOUR

        val allFiles = (fileStore.mediaDir.listFiles() ?: emptyArray())
            .filter { it.isFile }
        val thumbFiles = (fileStore.thumbDir.listFiles() ?: emptyArray())
            .filter { it.isFile }

        val registered = mediaDao.getAll()
        val registeredNames = registered.map { it.fileName }.toSet()
        val registeredThumbs = registered.mapNotNull { it.thumbnailName }.toSet()

        val orphans = mutableListOf<OrphanFile>()

        // 1) 已登记但无引用，且超过对应状态的宽限期
        mediaDao.findOrphanCandidates().forEach { asset ->
            val threshold = if (asset.state == MediaAssetState.PENDING.name) {
                pendingThreshold
            } else {
                graceThreshold
            }
            if (asset.createdAt > threshold) return@forEach
            val file = fileStore.mediaFile(asset.fileName)
            if (file.exists()) {
                orphans += OrphanFile(asset.fileName, asset.sizeBytes, OrphanFile.Reason.UNREFERENCED)
            }
        }

        // 2) 磁盘上有、登记表里没，且文件本身超过宽限期（用最后修改时间兜底）
        allFiles.forEach { file ->
            if (file.name in registeredNames) return@forEach
            if (file.lastModified() > graceThreshold) return@forEach
            if (file.name.endsWith(".part")) return@forEach
            orphans += OrphanFile(file.name, file.length(), OrphanFile.Reason.UNREGISTERED)
        }

        val totalBytes = allFiles.sumOf { it.length() } + thumbFiles.sumOf { it.length() }

        ScanReport(
            totalFiles = allFiles.size + thumbFiles.size,
            totalBytes = totalBytes,
            orphans = orphans,
            orphanBytes = orphans.sumOf { it.sizeBytes },
            scannedAt = now,
        )
    }.getOrElse { throwable ->
        Timber.w(throwable, "媒体扫描失败")
        ScanReport(0, 0L, emptyList(), 0L, time.nowEpochMs())
    }

    /**
     * 删除指定文件（含缩略图）与登记行。
     *
     * 删除前**再次校验**引用：扫描与确认之间用户可能刚好把文件重新关联上了。
     * 这种"展示 → 等用户确认 → 状态已变"的竞态，只能靠删除前复查来防。
     */
    suspend fun purge(orphans: List<OrphanFile>, recheckReferences: Boolean = true): Long {
        if (orphans.isEmpty()) return 0L

        val stillReferenced = if (recheckReferences) {
            mediaDao.findOrphanCandidates().map { it.fileName }.toSet()
        } else {
            emptySet()
        }

        var freed = 0L
        orphans.forEach { orphan ->
            if (orphan.fileName in stillReferenced) {
                Timber.d("跳过 %s：复查时发现已被重新引用", orphan.fileName)
                return@forEach
            }

            val mediaFile = fileStore.mediaFile(orphan.fileName)
            val thumbFile = mediaDao.getByFileName(orphan.fileName)?.thumbnailName
                ?.let { fileStore.thumbFile(it) }

            val mediaDeleted = fileStore.deleteMedia(orphan.fileName)
            thumbFile?.let { fileStore.deleteThumb(it.name) }

            if (mediaDeleted || !mediaFile.exists()) {
                mediaDao.delete(orphan.fileName)
                freed += orphan.sizeBytes
            }
        }
        return freed
    }

    /** 统计磁盘上媒体总占用（用于设置页展示，不依赖登记表） */
    fun currentDiskUsage(): Pair<Int, Long> {
        val files = (fileStore.mediaDir.listFiles() ?: emptyArray()).filter { it.isFile } +
            (fileStore.thumbDir.listFiles() ?: emptyArray()).filter { it.isFile }
        return files.size to files.sumOf { it.length() }
    }

    private companion object {
        const val DEFAULT_GRACE_DAYS = 30

        /** 未引用文件的短宽限期：24 小时 */
        const val PENDING_GRACE_HOURS = 24L
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        const val MILLIS_PER_HOUR = 60L * 60 * 1000
    }
}
