package com.fitnesspro.core.common.time

import android.os.SystemClock
import com.fitnesspro.core.model.time.TimeProvider
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生产实现：墙钟取系统时间，单调时钟取 SystemClock.elapsedRealtime()。
 * elapsedRealtime 包含深度睡眠时间，正是训练时长需要的行为。
 */
@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {

    override fun nowEpochMs(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}
