package com.fitnesspro.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fitnesspro.R
import com.fitnesspro.core.designsystem.theme.FitnessProTheme
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Health Connect 权限说明页（Android 14+ 强制要求）。
 *
 * Health Connect 会在权限弹窗前跳转到这里，用户必须能先看到
 * "为什么读这个数据、数据去哪了"再决定授权 —— 这也是本地优先承诺的展示窗口。
 */
@AndroidEntryPoint
class HealthConnectRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FitnessProTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RationaleContent(onClose = ::finish)
                }
            }
        }
    }
}

@Composable
private fun RationaleContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FitnessTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.m),
    ) {
        Text(
            text = stringResource(R.string.hc_rationale_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.hc_rationale_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hc_rationale_close))
        }
    }
}
