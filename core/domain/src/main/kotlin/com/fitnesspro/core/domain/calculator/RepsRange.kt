package com.fitnesspro.core.domain.calculator

import kotlinx.serialization.Serializable

/**
 * 次数区间解析。
 *
 * 处方里的次数既可能是固定值（"12"）、区间（"8-12"）、也可能是 AMRAP。
 * 统一由这里解析，避免各处在字符串上做 split 然后各自处理异常。
 */
@Serializable
data class RepsRange(
    val low: Int?,
    val high: Int?,
    val isAmrap: Boolean = false,
) {
    val isEmpty: Boolean get() = low == null && high == null && !isAmrap

    /** 区间中点，用于时长估算与建议重量 */
    val midpoint: Double?
        get() = when {
            isAmrap -> null
            low != null && high != null -> (low + high) / 2.0
            low != null -> low.toDouble()
            else -> null
        }

    /** 显示用文本 */
    fun display(): String = when {
        isAmrap -> "AMRAP"
        low != null && high != null && low != high -> "$low-$high"
        low != null -> low.toString()
        else -> "-"
    }

    fun reachedTop(reps: Int?): Boolean = high != null && reps != null && reps >= high

    fun reachedLow(reps: Int?): Boolean = low != null && reps != null && reps >= low

    companion object {
        val EMPTY = RepsRange(null, null)

        /**
         * 解析规则：
         *  - "12"      → (12, 12)
         *  - "8-12"    → (8, 12)
         *  - "8~12"    → (8, 12)
         *  - "AMRAP" / "amrap" / "力竭" → isAmrap
         *  - 非法输入   → EMPTY（不抛异常，UI 侧负责提示）
         */
        fun parse(raw: String?): RepsRange {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return EMPTY
            if (text.equals("AMRAP", ignoreCase = true) || text == "力竭") {
                return RepsRange(null, null, isAmrap = true)
            }
            val parts = text.split('-', '~', '到').map { it.trim() }.filter { it.isNotEmpty() }
            return when (parts.size) {
                1 -> parts[0].toIntOrNull()?.let { RepsRange(it, it) } ?: EMPTY
                2 -> {
                    val a = parts[0].toIntOrNull()
                    val b = parts[1].toIntOrNull()
                    if (a == null || b == null) EMPTY
                    else RepsRange(minOf(a, b), maxOf(a, b))
                }
                else -> EMPTY
            }
        }

        /** 供处方展平使用：把区间转成默认次的展示文本 */
        fun normalize(raw: String?): String = parse(raw).display().takeIf { it != "-" } ?: ""
    }
}
