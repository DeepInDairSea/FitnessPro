package com.fitnesspro.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 基础排版：沿用 Material3 默认，训练相关数值另用等宽字体（见 numericStyle） */
val FitnessTypography = Typography()

/**
 * 数值专用样式。
 *
 * 训练页的重量、次数、倒计时必须用等宽字体 —— 否则数字变化时字宽跳动，
 * 整行内容会左右抖动，长时间盯着看非常难受。
 */
object NumericText {
    val small: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    )

    val medium: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    )

    val large: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
    )

    val display: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
    )
}

@Composable
@ReadOnlyComposable
fun numericTextStyle(size: NumericSize = NumericSize.Medium): TextStyle = when (size) {
    NumericSize.Small -> NumericText.small
    NumericSize.Medium -> NumericText.medium
    NumericSize.Large -> NumericText.large
    NumericSize.Display -> NumericText.display
}

enum class NumericSize { Small, Medium, Large, Display }
