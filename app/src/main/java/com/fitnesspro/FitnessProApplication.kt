package com.fitnesspro

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.fitnesspro.core.common.di.ApplicationScope
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.seed.ExerciseSeedImporter
import com.fitnesspro.core.domain.reminder.WorkoutReminderScheduler
import com.fitnesspro.crash.CrashLogInstaller
import com.fitnesspro.feature.workout.timer.RestTimerNotifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class FitnessProApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var database: FitnessDatabase
    @Inject lateinit var seedImporter: ExerciseSeedImporter
    @Inject lateinit var crashLogInstaller: CrashLogInstaller
    @Inject lateinit var restTimerNotifier: RestTimerNotifier
    @Inject lateinit var reminderScheduler: WorkoutReminderScheduler

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 崩溃 handler 必须在其余初始化之前装好：之后的任何代码都可能崩
        crashLogInstaller.install()

        configureImageLoader()

        // 启动期只做轻量清理与种子导入，全部放 IO 协程，不阻塞首帧
        // （PRD NFR-P1：冷启动 ≤ 1.5s）
        applicationScope.launch {
            runCatching {
                cleanTempDirs()
                seedImporter.importIfNeeded()
                // 通知渠道注册与提醒排程：之前渠道从未被注册（通知静默失效的真凶），
                // 排程放这里还能顺带覆盖"设置变更后重启 App"的路径
                restTimerNotifier.ensureChannels()
                reminderScheduler.reschedule()
            }.onFailure { Timber.w(it, "启动初始化失败（不影响使用）") }
        }
    }

    /**
     * Coil 全局配置。
     *
     * - `AnimatedImageDecoder`：GIF / 动态 WebP 播放（动作演示的核心格式），需要 API 28+
     * - `VideoFrameDecoder`：外链视频封面帧
     *
     * 用 SingletonImageLoader.setSafe 而不是实现 ImageLoaderFactory：不依赖 Coil 的
     * Application 扩展点，因此在任何 Application 子类形态下都成立。
     */
    private fun configureImageLoader() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(AnimatedImageDecoder.Factory())
                    }
                    add(VideoFrameDecoder.Factory())
                }
                .crossfade(true)
                .build()
        }
    }

    /**
     * 清理上次异常退出残留的临时文件。
     * 不清理 media/thumbs —— 那是用户数据，只能由 MediaGarbageCollector 按引用计数处理。
     */
    private fun cleanTempDirs() {
        // capture 是系统相机的拍摄输出目录：一次 4K 视频可达数百 MB，
        // 而导入成功后源文件不会再被引用 —— 必须在启动时清掉
        listOf("capture", "tmp", "import_tmp", "transcode").forEach { name ->
            runCatching { File(cacheDir, name).deleteRecursively() }
        }
    }
}
