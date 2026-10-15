package com.fitnesspro.core.model.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

/**
 * 时间抽象 —— 本项目**必须**的接口。
 *
 * 休息计时、PR 判定、训练时长、连续打卡全部依赖时间；不抽象则无法写确定性测试。
 * 业务代码**禁止**直接调用 System.currentTimeMillis()。
 * 测试使用 MutableTimeProvider 手动推进时间。
 *
 * 放在 core:model 而不是 core:common：core:model 与 core:domain 都是纯 Kotlin JVM 模块，
 * 而 core:common 是 Android 库。接口定义在 model 里，Android 实现（SystemTimeProvider）
 * 放在 core:common，这样领域层既能用到它，又不被迫引入 Android 依赖。
 */
interface TimeProvider {
    /** 墙钟时间（UTC epoch millis）—— 用于时间戳、休息计时的跨进程恢复 */
    fun nowEpochMs(): Long

    /** 单调时钟（开机以来的毫秒）—— 用于训练中的实时显示，用户改系统时间不影响结果 */
    fun elapsedRealtimeMs(): Long

    fun zoneId(): ZoneId

    fun todayEpochDay(): Long = LocalDate.now(zoneId()).toEpochDay()

    fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun epochMsToLocalDate(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zoneId()).toLocalDate()

    fun epochMsToEpochDay(epochMs: Long): Long = epochMsToLocalDate(epochMs).toEpochDay()

    /** 当天 00:00 的 epoch millis，用于构造查询区间 */
    fun startOfDayEpochMs(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zoneId()).toInstant().toEpochMilli()

    /** 按用户设置的周起始日计算该 epochDay 所属周的起始 epochDay */
    fun weekStartEpochDay(epochDay: Long, firstDayOfWeek: Int): Long {
        val date = LocalDate.ofEpochDay(epochDay)
        val weekFields = WeekFields.of(DayOfWeek.of(firstDayOfWeek.coerceIn(1, 7)), 1)
        val start = date.minusDays((date.get(weekFields.dayOfWeek()) - 1).toLong())
        return start.toEpochDay()
    }

    fun monthStartEpochDay(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).withDayOfMonth(1).toEpochDay()
}
