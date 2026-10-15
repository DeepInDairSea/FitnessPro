package com.fitnesspro.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.fitnesspro.core.designsystem.component.FitnessTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.data.backup.BackupExporter
import com.fitnesspro.core.data.backup.BackupImporter
import com.fitnesspro.core.data.backup.CsvExporter
import com.fitnesspro.core.data.backup.ExportProgress
import com.fitnesspro.core.data.backup.ImportPreview
import com.fitnesspro.core.designsystem.R
import com.fitnesspro.core.designsystem.formatBytes
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.model.result.DomainError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.fitnesspro.feature.settings.R as SettingsR

/** 导入失败的 reason → 可读文案资源 */
private fun importErrorRes(reason: String): Int = when (reason) {
    "VERSION_TOO_NEW" -> SettingsR.string.backup_error_version_too_new
    "ILLEGAL_PATH" -> SettingsR.string.backup_error_illegal_path
    "TOO_LARGE" -> SettingsR.string.backup_error_too_large
    "CORRUPT_ZIP", "UNREADABLE" -> SettingsR.string.backup_error_corrupt
    "BROKEN_REFERENCE" -> SettingsR.string.backup_error_broken_reference
    else -> SettingsR.string.backup_error_unknown
}

private fun exportErrorRes(error: DomainError): String = error.code

data class BackupUiState(
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val exportPhase: ExportPhase = ExportPhase.IDLE,
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val preview: ImportPreview? = null,
    val pendingImportUri: Uri? = null,
    /** 用资源 id 而不是字符串：ViewModel 不认识 Context，翻译必须留在 UI 层 */
    @androidx.annotation.StringRes val messageRes: Int? = null,
    /**
     * 带参数的动态消息（如"媒体恢复失败数"）。
     * 与 [messageRes] 并存：少量带数字的消息不值得为此建 args 机制。
     */
    val dynamicMessage: String? = null,
    @androidx.annotation.StringRes val errorRes: Int? = null,
)

enum class ExportPhase { IDLE, TABLES, MEDIA, FINALIZING }

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val csvExporter: CsvExporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun exportBackup(target: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isExporting = true, exportProgress = 0f, exportPhase = ExportPhase.TABLES, errorRes = null)
            }
            runCatching {
                exporter.export(target).collect { progress ->
                    _uiState.update { state ->
                        state.copy(
                            exportProgress = progress.fraction,
                            exportPhase = when (progress.phase) {
                                ExportProgress.Phase.TABLES -> ExportPhase.TABLES
                                ExportProgress.Phase.MEDIA -> ExportPhase.MEDIA
                                ExportProgress.Phase.FINALIZING,
                                ExportProgress.Phase.DONE -> ExportPhase.FINALIZING
                            },
                        )
                    }
                }
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportPhase = ExportPhase.IDLE,
                        messageRes = SettingsR.string.backup_export_done,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        exportPhase = ExportPhase.IDLE,
                        errorRes = SettingsR.string.backup_error_unknown,
                    )
                }
            }
        }
    }

    fun exportCsv(target: Uri) {
        viewModelScope.launch {
            val outcome = csvExporter.export(target)
            _uiState.update {
                it.copy(
                    errorRes = if (outcome.isSuccess) null else SettingsR.string.backup_error_unknown,
                    messageRes = if (outcome.isSuccess) SettingsR.string.backup_csv_done else null,
                )
            }
        }
    }

    /** 先预览，让用户在真正清库之前知道自己要导入什么 */
    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            when (val outcome = importer.preview(uri)) {
                is com.fitnesspro.core.model.result.Outcome.Success -> {
                    _uiState.update { it.copy(preview = outcome.data, pendingImportUri = uri) }
                }
                is com.fitnesspro.core.model.result.Outcome.Failure -> {
                    val reason = (outcome.error as? DomainError.ImportFailure)?.reason.orEmpty()
                    _uiState.update { it.copy(errorRes = importErrorRes(reason)) }
                }
            }
        }
    }

    fun confirmImport() {
        val uri = _uiState.value.pendingImportUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = 0f, preview = null) }
            when (val outcome = importer.importFrom(uri, wipeExisting = true)) {
                is com.fitnesspro.core.model.result.Outcome.Success -> {
                    val failedMedia = outcome.data
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            pendingImportUri = null,
                            messageRes = if (failedMedia == 0) SettingsR.string.backup_import_done else null,
                            // 部分媒体恢复失败必须如实说：数据入库了，但演示文件缺了
                            dynamicMessage = if (failedMedia > 0) {
                                "数据已导入，但 $failedMedia 个媒体文件恢复失败"
                            } else {
                                null
                            },
                        )
                    }
                }
                is com.fitnesspro.core.model.result.Outcome.Failure -> {
                    val reason = (outcome.error as? DomainError.ImportFailure)?.reason.orEmpty()
                    // ImportFailure 的 reason 不在 DomainError 字段里时退化为通用文案
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            pendingImportUri = null,
                            errorRes = importErrorRes(outcome.error.code.removePrefix("IMPORT_")),
                        )
                    }
                }
            }
        }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(preview = null, pendingImportUri = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(messageRes = null, dynamicMessage = null, errorRes = null) }
    }
}

/**
 * 备份与恢复。
 *
 * 这里必须体现一条产品原则：**恢复是有损操作，必须让用户看清代价再执行**。
 * 因此流程是「选择文件 → 展示预览（要导入多少计划/训练/媒体）→ 输入 DELETE 确认 → 执行」，
 * 而不是选完文件就直接清库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmInput by remember { mutableStateOf("") }

    // 资源 id → 字符串。放在 Composable 内部是为了不把 Context 传进 ViewModel。
    fun contextString(resId: Int): String = context.getString(resId)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(viewModel::exportCsv) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::previewImport) }

    LaunchedEffect(state.messageRes, state.dynamicMessage) {
        val res = state.messageRes
        val text = state.dynamicMessage
        if (res == null && text == null) return@LaunchedEffect
        viewModel.consumeMessage()
        snackbarHostState.showSnackbar(text ?: contextString(res!!), actionLabel = null)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FitnessTopAppBar(
                title = { Text(stringResource(SettingsR.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(SettingsR.string.cd_settings_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = FitnessTheme.spacing.s),
        ) {
            item(key = "tip") {
                Text(
                    stringResource(SettingsR.string.backup_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(FitnessTheme.spacing.m),
                )
            }

            item(key = "export") {
                ActionRow(
                    title = stringResource(SettingsR.string.backup_export_title),
                    subtitle = stringResource(SettingsR.string.backup_export_desc),
                    enabled = !state.isExporting && !state.isImporting,
                    onClick = {
                        exportLauncher.launch(
                            "fitnesspro_backup_${System.currentTimeMillis()}.zip",
                        )
                    },
                )
            }

            item(key = "export-csv") {
                ActionRow(
                    title = stringResource(SettingsR.string.backup_export_csv_title),
                    subtitle = stringResource(SettingsR.string.backup_export_csv_desc),
                    enabled = !state.isExporting && !state.isImporting,
                    onClick = {
                        csvLauncher.launch("fitnesspro_records_${System.currentTimeMillis()}.csv")
                    },
                )
            }

            item(key = "import") {
                ActionRow(
                    title = stringResource(SettingsR.string.backup_import_title),
                    subtitle = stringResource(SettingsR.string.backup_import_desc),
                    enabled = !state.isExporting && !state.isImporting,
                    onClick = {
                        importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                )
            }

            if (state.isExporting) {
                item(key = "export-progress") {
                    Column(Modifier.padding(FitnessTheme.spacing.m)) {
                        Text(
                            text = when (state.exportPhase) {
                                ExportPhase.TABLES -> stringResource(
                                    SettingsR.string.backup_exporting_tables,
                                    (state.exportProgress * 100).toInt(),
                                )
                                ExportPhase.MEDIA -> stringResource(
                                    SettingsR.string.backup_exporting_media,
                                    (state.exportProgress * 100).toInt(),
                                )
                                else -> stringResource(SettingsR.string.backup_exporting_finalizing)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(FitnessTheme.spacing.s))
                        LinearProgressIndicator(
                            progress = { state.exportProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (state.isImporting) {
                item(key = "import-progress") {
                    Column(Modifier.padding(FitnessTheme.spacing.m)) {
                        Text(
                            stringResource(SettingsR.string.backup_importing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(FitnessTheme.spacing.s))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            state.errorRes?.let { res ->
                item(key = "error") {
                    Text(
                        stringResource(res),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(FitnessTheme.spacing.m),
                    )
                }
            }

            item(key = "bottom") { Spacer(Modifier.height(FitnessTheme.spacing.xl)) }
        }
    }

    // 恢复预览与二次确认
    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPreview,
            title = { Text(stringResource(SettingsR.string.backup_preview_title)) },
            text = {
                Column {
                    Text(stringResource(SettingsR.string.backup_preview_format, preview.formatVersion))
                    Text(
                        stringResource(
                            SettingsR.string.backup_preview_created,
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(preview.exportedAtEpochMs)),
                        ),
                    )
                    preview.appVersionName.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(SettingsR.string.backup_preview_app_version, it))
                    }
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    Text(stringResource(SettingsR.string.backup_preview_plans, preview.planCount))
                    Text(stringResource(SettingsR.string.backup_preview_sessions, preview.sessionCount))
                    Text(stringResource(SettingsR.string.backup_preview_sets, preview.setCount))
                    Text(
                        stringResource(
                            SettingsR.string.backup_preview_media,
                            preview.mediaFileCount,
                            formatBytes(preview.mediaTotalBytes),
                        ),
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.m))
                    Text(
                        stringResource(SettingsR.string.backup_overwrite_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.s))
                    OutlinedTextField(
                        value = confirmInput,
                        onValueChange = { confirmInput = it },
                        singleLine = true,
                        label = { Text(stringResource(SettingsR.string.backup_overwrite_confirm_word)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmInput = ""
                        viewModel.confirmImport()
                    },
                    enabled = confirmInput == "DELETE",
                ) {
                    Text(
                        stringResource(SettingsR.string.backup_import_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPreview) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = FitnessTheme.spacing.m, vertical = FitnessTheme.spacing.m),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
