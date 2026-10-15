package com.fitnesspro.core.data.media

import com.fitnesspro.core.data.backup.BACKUP_TABLE_ORDER
import com.fitnesspro.core.data.worker.MediaGcScanWorker
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.dao.AppFlagDao
import com.fitnesspro.core.media.MediaFileStore
import com.fitnesspro.core.model.time.TimeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 存储维护：媒体空间统计、手动扫描、用户确认后的清理。
 *
 * 设计要点：**扫描结果缓存到 StateFlow，而不是每次进设置页都全盘扫描**。
 * 扫描要遍历 media 目录并做多次 SQL 查询，放在设置页打开路径上会让页面明显卡顿。
 * 因此默认展示上次 Worker 扫描的结果，用户主动点"重新扫描"才重跑。
 */
@Singleton
class MaintenanceRepository @Inject constructor(
    private val garbageCollector: MediaGarbageCollector,
    private val appFlagDao: AppFlagDao,
    private val time: TimeProvider,
    private val database: FitnessDatabase,
    private val fileStore: MediaFileStore,
) {

    data class MediaStats(
        val fileCount: Int = 0,
        val totalBytes: Long = 0L,
        val orphanCount: Int = 0,
        val orphanBytes: Long = 0L,
        val lastScannedAt: Long? = null,
        val isScanning: Boolean = false,
    ) {
        val hasCleanable: Boolean get() = orphanCount > 0
    }

    private val _stats = MutableStateFlow(MediaStats())
    val stats: StateFlow<MediaStats> = _stats.asStateFlow()

    /** 读取缓存值（来自上次扫描）并补充磁盘实时占用。不触发全盘扫描。 */
    suspend fun refreshCached() {
        val (fileCount, diskBytes) = garbageCollector.currentDiskUsage()
        _stats.value = MediaStats(
            fileCount = fileCount,
            totalBytes = diskBytes,
            orphanCount = appFlagDao.get(MediaGcScanWorker.FLAG_ORPHAN_COUNT)?.toIntOrNull() ?: 0,
            orphanBytes = appFlagDao.get(MediaGcScanWorker.FLAG_ORPHAN_BYTES)?.toLongOrNull() ?: 0L,
            lastScannedAt = appFlagDao.get(MediaGcScanWorker.FLAG_SCANNED_AT)?.toLongOrNull(),
            isScanning = false,
        )
    }

    /** 用户主动触发的全盘扫描 */
    suspend fun scanNow(): MediaStats {
        _stats.value = _stats.value.copy(isScanning = true)
        val report = garbageCollector.scan()

        appFlagDao.put(
            com.fitnesspro.core.database.entity.AppFlagEntity(
                MediaGcScanWorker.FLAG_ORPHAN_COUNT, report.orphans.size.toString(),
            )
        )
        appFlagDao.put(
            com.fitnesspro.core.database.entity.AppFlagEntity(
                MediaGcScanWorker.FLAG_ORPHAN_BYTES, report.orphanBytes.toString(),
            )
        )
        appFlagDao.put(
            com.fitnesspro.core.database.entity.AppFlagEntity(
                MediaGcScanWorker.FLAG_SCANNED_AT, report.scannedAt.toString(),
            )
        )

        val result = MediaStats(
            fileCount = report.totalFiles,
            totalBytes = report.totalBytes,
            orphanCount = report.orphans.size,
            orphanBytes = report.orphanBytes,
            lastScannedAt = report.scannedAt,
            isScanning = false,
        )
        _stats.value = result
        return result
    }

    /**
     * 清理孤立媒体。
     *
     * 重新扫描一次再删（而不是用缓存的列表）：从"用户看到可清理 12MB"到"点击确认"
     * 之间可能已经过了几分钟，期间用户可能刚好把某个文件重新关联上了。
     * 用新鲜列表 + GC 内部的删除前复查，双重保险。
     */
    suspend fun purgeOrphans(): Long {
        val report = garbageCollector.scan()
        val freed = garbageCollector.purge(report.orphans)

        appFlagDao.put(
            com.fitnesspro.core.database.entity.AppFlagEntity(
                MediaGcScanWorker.FLAG_ORPHAN_COUNT, "0",
            )
        )
        appFlagDao.put(
            com.fitnesspro.core.database.entity.AppFlagEntity(
                MediaGcScanWorker.FLAG_ORPHAN_BYTES, "0",
            )
        )
        appFlagDao.put(
            com.fitnesspro.core.database.entity.AppFlagEntity(
                MediaGcScanWorker.FLAG_SCANNED_AT, time.nowEpochMs().toString(),
            )
        )

        // 清理后刷新磁盘占用，让用户立刻看到效果
        val (fileCount, diskBytes) = garbageCollector.currentDiskUsage()
        _stats.value = MediaStats(
            fileCount = fileCount,
            totalBytes = diskBytes,
            orphanCount = 0,
            orphanBytes = 0L,
            lastScannedAt = time.nowEpochMs(),
        )
        return freed
    }

    /**
     * 清空全部用户数据。
     *
     * 顺序：**先清数据库，再删文件**。
     * 反过来（先删文件、清库时崩溃）会让用户看到一堆"演示文件已丢失"的引用；
     * 按这个顺序，最坏情况只是留下孤立文件，GC 会兜住。
     */
    suspend fun clearAllData() {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            // 反向删除：先子表后父表，避免外键约束拒绝
            BACKUP_TABLE_ORDER.reversed().forEach { table ->
                runCatching { db.execSQL("DELETE FROM `$table`") }
                    .onFailure { Timber.w(it, "清空表 %s 失败", table) }
            }
            runCatching { db.execSQL("DELETE FROM sqlite_sequence") }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        // 数据库已清空，再删文件：删失败也不会造成"引用存在但文件没了"
        listOf(fileStore.mediaDir, fileStore.thumbDir, fileStore.tempDir, fileStore.transcodeDir)
            .forEach { dir ->
                dir.listFiles()?.forEach { file -> runCatching { file.delete() } }
            }

        _stats.value = MediaStats()
    }
}
