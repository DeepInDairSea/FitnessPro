package com.fitnesspro.crash

import android.content.Context
import com.fitnesspro.core.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地崩溃日志。
 *
 * 这是"设置 → 崩溃报告"开关的真实实现：开关打开时，未捕获异常会把完整堆栈
 * 写到 `files/crash/last_crash.txt`，供用户手动导出反馈。
 *
 * 三个刻意的设计决定：
 *  1. **不联网上报**：本地优先是本产品的底线（PRD），接入任何第三方 SDK 都会破坏它。
 *  2. **只保留最近一次**：崩溃日志的价值在于"刚才为什么崩了"，历史堆栈只会占空间。
 *  3. **在 handler 里用 runBlocking 读设置**：进程正在死亡，阻塞主线程没有任何后果；
 *     相反，若在这里起协程，多半来不及执行就退出了。
 *
 * handler 必须**无条件**先链回 previous handler：
 * 我们的写文件本身也可能抛异常，吞掉崩溃会让系统无法正常终止应用。
 */
@Singleton
class CrashLogInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val enabled = runBlocking { settings.current().crashReportingEnabled }
                if (enabled) writeCrashLog(thread, throwable)
            }.onFailure { Timber.w(it, "写崩溃日志失败") }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        File(dir, FILE_NAME).writeText(
            buildString {
                appendLine("time: ${System.currentTimeMillis()}")
                appendLine("thread: ${thread.name}")
                appendLine(stack)
            },
        )
    }

    private companion object {
        const val CRASH_DIR = "crash"
        const val FILE_NAME = "last_crash.txt"
    }
}
