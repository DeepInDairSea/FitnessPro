package com.fitnesspro.feature.workout.timer

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D18：RestTimerNotifier 测试（Robolectric 提供 Context / NotificationManager / 资源）。
 * 聚焦休息结束通知渠道的注册与调用不崩溃。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RestTimerNotifierTest {

    private lateinit var context: Context
    private lateinit var notifier: RestTimerNotifier

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val actions = mockk<RestTimerActions>(relaxed = true)
        notifier = RestTimerNotifier(context, actions)
    }

    @Test
    fun showFinished_doesNotThrow() {
        notifier.showFinished("卧推", "下一组 12 次")
    }

    @Test
    fun cancelAll_doesNotThrow() {
        // 先触发结束通知，再清理，验证通知生命周期的两半都不崩溃
        notifier.showFinished("卧推", "下一组 12 次")
        notifier.cancelAll()
    }
}
