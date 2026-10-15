package com.fitnesspro.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 一个文本章节：标题 + 正文。整页由章节列表驱动，新增内容只加一条数据即可 */
private data class PolicySection(
    val title: String,
    val body: String,
)

/**
 * 隐私政策 / 免责声明 / 开源许可（D20）。
 *
 * 离线优先（local-first）是产品的核心承诺：训练、身体、计划等数据默认只存在本机数据库，
 * 不上传任何服务器。这里把这一承诺以可读文本呈现给用户，避免"隐私页只放一段说明"的含糊。
 *
 * 免责声明与许可放在同一页而不是各开一页：用户想知道"我的数据去哪了""出事谁负责"
 * 时不该在多个入口之间找。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私政策与免责声明") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "我们重视您的隐私。本应用采用「离线优先」原则，以下说明我们如何处理您的数据：",
                style = MaterialTheme.typography.bodyMedium,
            )

            sections.forEach { section ->
                Text(section.title, style = MaterialTheme.typography.titleSmall)
                Text(section.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private val sections = listOf(
    PolicySection(
        title = "1. 数据存储",
        body = "您的训练记录、身体数据、训练计划与自定义动作均存储在您设备本地的数据库中，" +
            "不会自动上传到任何远程服务器。",
    ),
    PolicySection(
        title = "2. 崩溃报告",
        body = "仅在您于设置中开启「崩溃报告」开关时，才会发送匿名的崩溃日志用于改进应用；" +
            "关闭后不会发送任何数据。",
    ),
    PolicySection(
        title = "3. 权限说明",
        body = "通知权限用于训练提醒与组间休息计时；媒体（文件）权限用于导出/导入本地备份。" +
            "这些权限均可在系统设置中随时撤回。",
    ),
    PolicySection(
        title = "4. 后台运行与电池优化",
        body = "为了保证训练提醒能按时送达，建议您将本应用加入系统的「电池优化白名单」。" +
            "您可在设置的「电池优化」入口中一键前往设置。",
    ),
    PolicySection(
        title = "5. 备份与转移",
        body = "您可以随时在设置中导出本地备份文件，用于设备间转移或数据恢复；" +
            "导入前请确认备份来源可信。",
    ),
    PolicySection(
        title = "6. 免责声明",
        body = "本软件为纯离线应用，不涉及任何用户数据的上传与下载。" +
            "动作库、训练计划、训练记录等均由用户高度自定义，训练内容与强度完全由用户自行决定。" +
            "力量训练存在受伤风险，请在充分了解自身健康状况的前提下量力而行；" +
            "因使用本软件或其提供的动作、计划而导致的任何身体伤害、健康问题或财产损失，" +
            "均与作者 KerryZeng 无任何关联，作者不承担任何责任。",
    ),
    PolicySection(
        title = "7. 开源许可",
        body = "本软件以 MIT 许可协议开源发布，Copyright (c) 2026 KerryZeng。" +
            "您可以在遵守 MIT 许可条款（保留版权声明与许可声明）的前提下自由使用、修改与分发本软件。" +
            "完整许可文本见项目根目录的 LICENSE 文件。",
    ),
    PolicySection(
        title = "8. 联系我们",
        body = "如对本政策、免责声明或开源许可有疑问，可通过设置中的反馈渠道与我们联系。",
    ),
)
