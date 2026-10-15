package com.fitnesspro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * logo 几何必须与系统启动画面的自适应图标渲染完全一致。
 * 实测（420dpi 模拟器）：系统层 = 288dp 画布 + 192dp 圆形遮罩（可见 2/3 中心内容）、圆心=屏幕正中。
 * 品牌页用同参数复刻：192dp 圆内居中放 288dp 画布，切换零跳动。
 */
private const val CIRCLE_DP = 192f
private const val CANVAS_DP = 288f

/**
 * 品牌启动页：Bento 画布同渐变背景 + 应用 logo + 标题 + 底部版权与版本。
 *
 * 系统启动画面（splashscreen 兼容库）为同色纯底 + 同款同尺寸同位置 logo，
 * 交接瞬间画面完全静止，仅标题与版权文案淡入 —— 无任何"切换感"。
 * 系统层无法渲染文字，版本 / 版权只能在本页展示。
 */
@Composable
fun SplashScreen(
    versionName: String,
    /** 停留结束回调；由 MainActivity 切换到主界面 */
    onFinished: () -> Unit,
    holdMillis: Long = 400L,
) {
    // 只有"新增"的文案做淡入；logo 绝对不能动，否则与系统层交接会跳
    var visible by remember { mutableStateOf(false) }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "splashAppear",
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(holdMillis)
        onFinished()
    }

    // 与 Theme.kt appBackground / cold_start_background 渐变逐像素一致
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF6F5F8),
                        Color(0xFFF1F0F4),
                        Color(0xFFEAE9EF),
                    ),
                ),
            ),
    ) {
        // 与系统启动画面几何完全一致的 logo：160dp 可见圆 + 240dp 画布（自适应图标规范），屏幕正中、无动画
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(CIRCLE_DP.dp)
                .clip(CircleShape),
        ) {
            Image(
                painter = painterResource(com.fitnesspro.core.designsystem.R.drawable.ic_launcher_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // requiredSize：忽略外层 160dp 圆盒的 max 约束，画布必须足 240dp，
                // 否则内容缩放会和系统层的自适应图标渲染不一致
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(CANVAS_DP.dp),
            )
        }

        // 标题：紧贴可见圆下缘（圆半径 96dp + 20dp 间距 + 半行高 18dp）
        Text(
            text = "Fitness Pro",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1A1F),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (CIRCLE_DP / 2 + 38f).dp)
                .graphicsLayer { alpha = appear },
        )

        // 底部：版权 + 版本
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "© 2026 KerryZeng · MIT License",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF6B6975),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = appear },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "版本 $versionName",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF9B99A4),
                modifier = Modifier.graphicsLayer { alpha = appear },
            )
        }
    }
}
