package com.fitnesspro.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.theme.FitnessTheme

/**
 * 折线趋势图（自绘）。
 *
 * 为什么自绘而不是引图表库：本项目只有 4 种图表（折线 / 条形 / 饼 / 热力），
 * 引库要付出 1-2MB 的 APK 体积与版本升级风险，而自绘这些形状的代码不到 100 行。
 *
 * 三个必须处理好的边界：
 *  1. **空数据**：显示占位文案，不能画一条空线
 *  2. **单点**：无法成线，画一个圆点
 *  3. **全等值**（例如连续 5 次都是 60kg）：最大最小值相等会导致除零，需要人为撑开值域
 */
@Composable
fun TrendLineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    emptyText: String = stringResource(R.string.common_empty_default),
    contentDescription: String? = null,
    /** 页面强调色：默认跟随 `FitnessTheme.accent`（各一级页面不同，风格统一） */
    accent: Color? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (points.isEmpty()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

        val lineColor = accent ?: FitnessTheme.accent
        val gridColor = MaterialTheme.colorScheme.outlineVariant
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

        val minValue = points.min()
        val maxValue = points.max()
        // 全等值时人为撑开值域，否则 (v-min)/(max-min) 会除零
        val span = if (maxValue - minValue < 0.001f) 1f else maxValue - minValue

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(end = 44.dp, top = 8.dp, bottom = 20.dp)
        ) {
            val w = size.width
            val h = size.height

            // 三条水平参考线
            repeat(3) { index ->
                val y = h * index / 2f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                )
            }

            fun pointAt(index: Int): Offset {
                val x = if (points.size == 1) w / 2f else w * index / (points.size - 1).toFloat()
                val ratio = (points[index] - minValue) / span
                val y = h - ratio * h
                return Offset(x, y)
            }

            if (points.size == 1) {
                // 单点无法成线：画点而不是画一条退化的线
                drawCircle(lineColor, radius = 5f, center = pointAt(0))
            } else {
                val path = Path()
                points.indices.forEach { index ->
                    val offset = pointAt(index)
                    if (index == 0) path.moveTo(offset.x, offset.y)
                    else path.lineTo(offset.x, offset.y)
                }
                drawPath(path, color = lineColor, style = Stroke(width = 3f))

                points.indices.forEach { index ->
                    drawCircle(lineColor, radius = 3.5f, center = pointAt(index))
                }
            }

            // 最大值 / 最小值标注（用原生 canvas 画文字，避免 Compose 文本在 draw scope 中的限制）
            val paint = android.graphics.Paint().apply {
                color = labelColor.toArgbCompat()
                textSize = 28f
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.apply {
                drawText(valueFormatter(maxValue), w + 8f, 32f, paint)
                drawText(valueFormatter(minValue), w + 8f, h, paint)
            }
        }
    }
}

/**
 * 横向条形分布图（肌群容量等）。
 *
 * 用条形而不是饼图：比例差异在 1% 量级时，饼图完全看不出来，
 * 而条形图的长度差异仍然可读。
 */
@Composable
fun DistributionBarChart(
    entries: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    valueFormatter: (Float) -> String = { it.toInt().toString() },
    emptyText: String = stringResource(R.string.common_empty_default),
    /**
     * 条槽底色。默认用半透明次级文字色而不是 surfaceVariant ——
     * 后者在"白卡上的图表"（便当盒软卡片）里与卡片同色，整条槽会看不见。
     */
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f),
    /** 页面强调色：默认跟随 `FitnessTheme.accent` */
    accent: Color? = null,
    /** 可选的逐条颜色（与 entries 等长时逐条生效，如肌群身份色）；为空则统一用 accent */
    barColors: List<Color> = emptyList(),
) {
    if (entries.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val maxValue = entries.maxOf { it.second }.coerceAtLeast(0.001f)
    val barColor = accent ?: FitnessTheme.accent

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s),
    ) {
        entries.forEachIndexed { index, (label, value) ->
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    Text(
                        valueFormatter(value),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(trackColor)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((value / maxValue).coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColors.getOrNull(index) ?: barColor)
                    )
                }
            }
        }
    }
}

/**
 * 年历热力图（GitHub 风格）。
 *
 * @param counts key = epochDay，value = 当天训练次数
 */
@Composable
fun CalendarHeatmap(
    counts: Map<Long, Int>,
    weeks: Int = 26,
    modifier: Modifier = Modifier,
    todayEpochDay: Long,
    /**
     * 无训练日期的格子色。默认半透明次级文字色，理由同 [DistributionBarChart.trackColor]：
     * surfaceVariant 在白色软卡片上会与卡片融为一体，整张热力图看起来是空的。
     */
    emptyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f),
    /** 页面强调色：默认跟随 `FitnessTheme.accent` */
    accent: Color? = null,
) {
    val activeColor = accent ?: FitnessTheme.accent

    Column(modifier = modifier.fillMaxWidth()) {
        // 高度不写死：宽 26 周、高 7 天，随宽度等比缩放，单元格在任何屏幕宽度下都是正方形
        // （写死 90dp 时平板/横屏上单元格会被裁掉下半部分）
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(weeks / 7f)
        ) {
            val totalDays = weeks * 7
            val startDay = todayEpochDay - totalDays + 1
            val cell = size.width / weeks

            for (dayOffset in 0 until totalDays) {
                val epochDay = startDay + dayOffset
                val weekIndex = dayOffset / 7
                val dayIndex = dayOffset % 7
                val count = counts[epochDay] ?: 0

                drawRoundRect(
                    color = if (count > 0) activeColor.copy(alpha = 0.35f + 0.25f * count.coerceAtMost(3))
                    else emptyColor,
                    topLeft = Offset(weekIndex * cell, dayIndex * cell),
                    size = androidx.compose.ui.geometry.Size(cell - 2f, cell - 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                )
            }
        }
        Spacer(Modifier.height(FitnessTheme.spacing.xs))
        // 图例：用「无训练 / 有训练」两个色块。只用颜色区分是不够的（无障碍要求），
        // 因此必须配文字标签。
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(emptyColor)
            )
            Text(
                stringResource(R.string.chart_legend_no_training),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(FitnessTheme.spacing.s))
            Box(
                Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(activeColor)
            )
            Text(
                stringResource(R.string.chart_legend_has_training),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
