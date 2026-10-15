package com.fitnesspro.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.designsystem.theme.FitnessTheme

/**
 * 圆形用户头像。
 *
 * 头像文件名是相对 filesDir 的（与设置页/分享卡片同一约定）；
 * 没有头像时回退为"昵称首字母 + 强调色实心圆"。
 * 解码统一走 [AvatarDecoder]（EXIF 旋转修正 + 降采样 + 缓存）。
 */
@Composable
fun UserAvatar(
    nickname: String,
    avatarUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // 不用 remember(avatarUri) 缓存解码结果：重选头像后存储路径不变（仍是 avatar.jpg），
    // remember 不会重新执行，会导致"换头像后要退出页面才刷新"。
    // AvatarDecoder 内部缓存键带文件修改时间，这里每次组合直接查缓存即可，命中时开销可忽略
    val bitmap = AvatarDecoder.decode(context, avatarUri)

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = nickname,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        val initial = nickname.trim().firstOrNull()?.toString() ?: "健"
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(FitnessTheme.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initial,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}
