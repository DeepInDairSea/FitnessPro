package com.fitnesspro.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesspro.R
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 首次启动引导。
 *
 * 三页内容只讲三件必须先说清楚的事：
 *  1. 这是什么应用（训练记录）
 *  2. 数据在哪（本地优先 —— 这是本产品最重要的承诺，必须在用户录入第一条数据前讲）
 *  3. 休息计时怎么工作（解释为什么会有常驻通知，避免用户以为是广告通知）
 *
 * "跳过"与"完成"等价：都写入 onboarding_completed。
 * 强迫用户看完全部页面是对注意力的抢劫，引导的价值在内容不在页数。
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    /** 完成引导：同时视为隐私条款的首次知情同意（本地优先说明即隐私政策） */
    fun complete() {
        viewModelScope.launch {
            settings.acceptPrivacy(System.currentTimeMillis())
            settings.setOnboardingCompleted(true)
        }
    }
}

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝也继续：通知权限随时可以在设置里补开 */ }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(FitnessTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.l),
        ) {
            Spacer(Modifier.weight(1f))

            HorizontalPager(state = pagerState) { page ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.m),
                ) {
                    Text(
                        text = stringResource(pageTitle(page)),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(pageBody(page)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            val isLast = pagerState.currentPage == pagerState.pageCount - 1
            if (isLast) {
                // 通知权限是训练体验的一部分（休息倒计时走系统通知），
                // 但放在最后一页且可跳过：先给上下文再要权限，拒绝率低得多
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(
                        onClick = {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.onboarding_enable_notifications))
                    }
                }
                Button(
                    onClick = {
                        viewModel.complete()
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_start))
                }
            } else {
                Button(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_next))
                }
            }

            TextButton(
                onClick = {
                    viewModel.complete()
                    onDone()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}

private fun pageTitle(page: Int): Int = when (page) {
    0 -> R.string.onboarding_title_1
    1 -> R.string.onboarding_title_2
    else -> R.string.onboarding_title_3
}

private fun pageBody(page: Int): Int = when (page) {
    0 -> R.string.onboarding_body_1
    1 -> R.string.onboarding_body_2
    else -> R.string.onboarding_body_3
}
