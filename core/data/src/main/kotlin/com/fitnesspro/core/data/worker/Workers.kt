package com.fitnesspro.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fitnesspro.core.data.media.MediaGarbageCollector
import com.fitnesspro.core.data.repository.TRASH_RETENTION_DAYS
import com.fitnesspro.core.database.dao.AppFlagDao
import com.fitnesspro.core.database.entity.AppFlagEntity
import com.fitnesspro.core.database.seed.ExerciseSeedImporter
import com.fitnesspro.core.domain.repository.ExerciseRepository
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.domain.repository.WorkoutRepository
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.time.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 种子动作库导入。
 *
 * 放在 Worker 而不是 Application 里直接做：300 条动作的单事务写入在中低端机上
 * 可能耗时数百毫秒，放启动路径会直接顶穿 PRD NFR-P1（冷启动 ≤ 1.5s）。
 */
@HiltWorker
class SeedImportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val seedImporter: ExerciseSeedImporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val imported = seedImporter.importIfNeeded()
        Timber.i("动作库种子导入完成，新增 %d 条", imported)
        Result.success()
    }.getOrElse {
        Timber.w(it, "动作库种子导入失败")
        // 不 retry：种子数据缺失不应阻塞用户使用，下次启动会自然重试
        Result.success()
    }
}

/**
 * 回收站过期清理。
 *
 * "保留 30 天"这个承诺的**唯一实现**就是本 Worker。没有它，
 * 回收站里的数据会永久堆积，用户也看不到"剩余天数"归零后会发生什么。
 */
@HiltWorker
class RecycleBinCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        // 计划 / 动作 / 历史三类回收站共用同一个保留期与同一个 Worker：
        // 任何一类失败都整体 retry —— 部分成功会使用户看到不一致的过期状态
        val results = listOf(
            planRepository.purgeExpiredTrash(TRASH_RETENTION_DAYS),
            exerciseRepository.purgeExpiredTrash(TRASH_RETENTION_DAYS),
            workoutRepository.purgeExpiredTrash(TRASH_RETENTION_DAYS),
        )
        val failure = results.filterIsInstance<Outcome.Failure>().firstOrNull()
        when (failure) {
            null -> {
                val total = results.filterIsInstance<Outcome.Success<Int>>().sumOf { it.data }
                if (total > 0) Timber.i("回收站清理了 %d 条过期数据", total)
                Result.success()
            }
            else -> {
                Timber.w("回收站清理失败：%s", failure.error)
                Result.retry()
            }
        }
    }.getOrElse {
        Timber.w(it, "回收站清理异常")
        Result.retry()
    }
}

/**
 * 媒体孤立文件扫描。
 *
 * 只扫描并记录结果，**不删除**。删除必须由用户在设置页确认 ——
 * 用户数据不该在后台悄悄消失（ADR-08）。
 * 因此本 Worker 的产出是"一条待清理提示"，而不是一次清理动作。
 */
@HiltWorker
class MediaGcScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val garbageCollector: MediaGarbageCollector,
    private val appFlagDao: AppFlagDao,
    private val time: TimeProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val report = garbageCollector.scan()
        val now = time.nowEpochMs()

        appFlagDao.put(AppFlagEntity(FLAG_ORPHAN_COUNT, report.orphans.size.toString()))
        appFlagDao.put(AppFlagEntity(FLAG_ORPHAN_BYTES, report.orphanBytes.toString()))
        appFlagDao.put(AppFlagEntity(FLAG_TOTAL_BYTES, report.totalBytes.toString()))
        appFlagDao.put(AppFlagEntity(FLAG_SCANNED_AT, now.toString()))

        Timber.i(
            "媒体扫描完成：共 %d 个文件 / %d MB，可清理 %d 个 / %d MB",
            report.totalFiles,
            report.totalBytes / 1024 / 1024,
            report.orphans.size,
            report.orphanBytes / 1024 / 1024,
        )
        Result.success()
    }.getOrElse {
        Timber.w(it, "媒体扫描失败")
        Result.retry()
    }

    companion object {
        const val FLAG_ORPHAN_COUNT = "media_orphan_count"
        const val FLAG_ORPHAN_BYTES = "media_orphan_bytes"
        const val FLAG_TOTAL_BYTES = "media_total_bytes"
        const val FLAG_SCANNED_AT = "media_scanned_at"
    }
}

/**
 * 后台任务注册中心。
 *
 * 全部任务集中在这里排队，而不是散落在各模块的 init 里 ——
 * 否则"到底有哪些后台任务在跑"会变成需要全局搜索才能回答的问题。
 */
object Workers {

    const val UNIQUE_SEED_IMPORT = "seed_import"
    const val UNIQUE_RECYCLE_CLEANUP = "recycle_bin_cleanup"
    const val UNIQUE_MEDIA_GC = "media_gc_scan"

    fun scheduleAll(workManager: WorkManager) {
        // 种子导入：一次性，若已导入则导入器内部直接返回 0
        workManager.enqueueUniqueWork(
            UNIQUE_SEED_IMPORT,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SeedImportWorker>()
                .setInitialDelay(2, TimeUnit.SECONDS)
                .build(),
        )

        // 回收站清理：每日一次，首次延迟 1 小时避开启动高峰
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_RECYCLE_CLEANUP,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecycleBinCleanupWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build(),
        )

        // 媒体扫描：每周一次，且要求充电 + 空闲 —— 扫描要遍历整个媒体目录，
        // 是纯 IO 密集任务，在用户正用手机时跑会明显影响体验
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_MEDIA_GC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MediaGcScanWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .build(),
        )
    }

    /** 训练结束后触发一次媒体扫描（仅在确实产生过新文件时才有意义，此处保守起见总是排队） */
    fun scheduleMediaScanOnce(workManager: WorkManager) {
        workManager.enqueueUniqueWork(
            "${UNIQUE_MEDIA_GC}_once",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MediaGcScanWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build(),
        )
    }
}
