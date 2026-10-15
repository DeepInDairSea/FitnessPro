package com.fitnesspro.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.media.MediaFileStore
import com.fitnesspro.core.model.result.DomainError
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportProgress(
    val phase: Phase,
    val done: Int = 0,
    val total: Int = 0,
    val message: String? = null,
) {
    enum class Phase { VALIDATING, EXTRACTING, WRITING_DATA, MOVING_MEDIA, DONE }

    val fraction: Float
        get() = when {
            phase == Phase.DONE -> 1f
            total <= 0 -> 0f
            else -> (done.toFloat() / total).coerceIn(0f, 1f)
        }
}

/** 导入前的预览信息，让用户在真正写库前知道自己要导入什么 */
data class ImportPreview(
    val formatVersion: Int,
    val exportedAtEpochMs: Long,
    val appVersionName: String,
    val planCount: Int,
    val sessionCount: Int,
    val setCount: Int,
    val mediaFileCount: Int,
    val mediaTotalBytes: Long,
)

/**
 * 备份导入。
 *
 * 采用**清空覆盖**语义，不做合并。原因：快照里保存的是真实主键，
 * 而合并需要把所有外键（plan_id / plan_exercise_id / session_id …）按新旧 id 映射重写，
 * 任何一处漏改都会导致"计划指向别人的动作"这种静默数据错乱。
 * 草率实现合并的代价远高于它的收益，因此 v1.0 明确只支持覆盖导入
 * （界面会强制用户二次确认，并要求输入确认词）。
 *
 * 五道校验，任何一步失败都**不产生任何数据**：
 *  1. zip 可读且含 manifest.json
 *  2. formatVersion 不高于当前支持版本
 *  3. 防 Zip Slip（目录穿越）
 *  4. 防 Zip 炸弹（条目数 / 单文件大小 / 解压后总大小）
 *  5. 外键引用完整性预检
 */
@Singleton
class BackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FitnessDatabase,
    private val mediaFileStore: MediaFileStore,
    private val time: TimeProvider,
    private val json: Json,
) {

    /** 只读取 manifest 与关键计数，不解压媒体、不写库 */
    suspend fun preview(uri: Uri): Outcome<ImportPreview> = runCatchingOutcomeSuspend {
        val extracted = extractSecurely(uri) { _, _ -> }
        try {
            val manifest = extracted.manifest ?: error("备份文件缺少 manifest.json")
            val data = extracted.data ?: error("备份文件缺少 data.json")
            ImportPreview(
                formatVersion = manifest.formatVersion,
                exportedAtEpochMs = manifest.exportedAtEpochMs,
                appVersionName = manifest.appVersionName,
                planCount = data.tables["training_plan"]?.rows?.size ?: 0,
                sessionCount = data.tables["workout_session"]?.rows?.size ?: 0,
                setCount = data.tables["workout_set"]?.rows?.size ?: 0,
                mediaFileCount = manifest.mediaFileCount,
                mediaTotalBytes = manifest.mediaTotalBytes,
            )
        } finally {
            runCatching { extracted.tempDir.deleteRecursively() }
        }
    }

    fun import(uri: Uri, wipeExisting: Boolean = true): Flow<ImportProgress> = flow {
        doImport(uri, wipeExisting) { emit(it) }
    }.flowOn(Dispatchers.IO)

    /**
     * 核心导入流程。进度经 [emitProgress] 上报（供 UI 进度条），
     * 返回值是**恢复失败的媒体文件数**（0 = 完整恢复）—— 结构化传递，不从文案里反解。
     */
    private suspend fun doImport(
        uri: Uri,
        wipeExisting: Boolean,
        emitProgress: suspend (ImportProgress) -> Unit,
    ): Int {
        emitProgress(ImportProgress(ImportProgress.Phase.VALIDATING))

        // 解压阶段在非 suspend 回调里无法 emit，因此进度只按阶段粒度上报。
        // 这是有意的取舍：更细的进度需要把 ZipInputStream 改成挂起式逐块读取，
        // 收益是"进度条更平滑"，代价是代码复杂度和出错面显著上升。
        val extracted = extractSecurely(uri) { _, _ -> }

        try {
            val manifest = extracted.manifest ?: throw ImportFailure("CORRUPT_ZIP", "缺少 manifest.json")
            val data = extracted.data ?: throw ImportFailure("CORRUPT_ZIP", "缺少 data.json")

            // 校验 2：格式版本
            if (manifest.formatVersion > BackupManifest.CURRENT_FORMAT_VERSION) {
                throw ImportFailure(
                    "VERSION_TOO_NEW",
                    "备份由更新版本的应用创建（${manifest.formatVersion} > " +
                        "${BackupManifest.CURRENT_FORMAT_VERSION}）",
                )
            }

            // 校验 5：引用完整性
            validateReferences(data)

            emitProgress(ImportProgress(ImportProgress.Phase.WRITING_DATA, 0, BACKUP_TABLE_ORDER.size))

            writeTables(data, wipeExisting) { _, _ -> }

            emitProgress(ImportProgress(ImportProgress.Phase.MOVING_MEDIA, 0, extracted.mediaFiles.size))
            val failedMedia = moveMediaIntoPlace(extracted)

            // 数据库已完整落库，媒体恢复失败不推翻整个导入 —— 但不能谎报"全部成功"：
            // 缺一个演示视频，导入后动作详情页就是裂图，用户必须知道。
            emitProgress(
                ImportProgress(
                    ImportProgress.Phase.DONE,
                    1, 1,
                    message = if (failedMedia > 0) {
                        "数据已导入，但 $failedMedia 个媒体文件恢复失败"
                    } else {
                        null
                    },
                ),
            )
            return failedMedia
        } catch (failure: ImportFailure) {
            Timber.w("导入失败：%s - %s", failure.reason, failure.detail)
            throw failure
        } finally {
            runCatching { extracted.tempDir.deleteRecursively() }
        }
    }

    /**
     * 便捷封装：成功值是**恢复失败的媒体文件数**（0 = 完整恢复）。
     * 调用方（UI）必须把非零值如实展示给用户。
     */
    suspend fun importFrom(uri: Uri, wipeExisting: Boolean = true): Outcome<Int> =
        runCatchingOutcomeSuspend {
            doImport(uri, wipeExisting) { }
        }

    // ------------------------------------------------------------------
    // 校验 3 & 4：安全解压
    // ------------------------------------------------------------------

    private data class Extracted(
        val tempDir: File,
        val manifest: BackupManifest?,
        val data: BackupData?,
        val mediaFiles: List<File>,
    )

    private fun extractSecurely(uri: Uri, onEntry: (Int, Int) -> Unit): Extracted {
        val tempDir = File(
            mediaFileStore.importTempDir(),
            "import_${time.nowEpochMs()}",
        ).apply { mkdirs() }

        var manifest: BackupManifest? = null
        var data: BackupData? = null
        val mediaFiles = mutableListOf<File>()
        var entryCount = 0
        var totalExtracted = 0L

        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportFailure("UNREADABLE", "无法读取所选文件")

        BufferedInputStream(input, BUFFER_SIZE).use { buffered ->
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++

                    // 校验 4：条目数上限，防"无数小文件"型 zip 炸弹
                    if (entryCount > MAX_ENTRIES) {
                        throw ImportFailure("TOO_LARGE", "压缩包条目过多（>$MAX_ENTRIES）")
                    }

                    val name = entry.name
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    // 校验 3：Zip Slip —— 规范化后的目标路径必须仍在临时目录内
                    val target = File(tempDir, name).canonicalFile
                    if (!target.path.startsWith(tempDir.canonicalFile.path + File.separator)) {
                        throw ImportFailure("ILLEGAL_PATH", "压缩包内含非法路径：$name")
                    }
                    target.parentFile?.mkdirs()

                    if (name == BackupExporter.ENTRY_MANIFEST || name == BackupExporter.ENTRY_DATA) {
                        val bytes = zip.readBytes()
                        totalExtracted += bytes.size
                        if (totalExtracted > MAX_TOTAL_BYTES) {
                            throw ImportFailure("TOO_LARGE", "解压后体积过大")
                        }
                        val text = bytes.decodeToString()
                        if (name == BackupExporter.ENTRY_MANIFEST) {
                            manifest = runCatching { json.decodeFromString(BackupManifest.serializer(), text) }
                                .getOrElse { throw ImportFailure("CORRUPT_ZIP", "manifest.json 解析失败") }
                        } else {
                            data = runCatching { json.decodeFromString(BackupData.serializer(), text) }
                                .getOrElse { throw ImportFailure("CORRUPT_ZIP", "data.json 解析失败") }
                        }
                    } else {
                        // 媒体文件：逐块写盘，同时累计体积
                        FileOutputStream(target).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var written = 0L
                            while (true) {
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                written += read
                                totalExtracted += read
                                if (written > MAX_SINGLE_FILE_BYTES) {
                                    throw ImportFailure("TOO_LARGE", "单个文件过大：$name")
                                }
                                if (totalExtracted > MAX_TOTAL_BYTES) {
                                    throw ImportFailure("TOO_LARGE", "解压后体积过大")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                        mediaFiles += target
                    }

                    zip.closeEntry()
                    onEntry(entryCount, entryCount)
                }
            }
        }

        return Extracted(tempDir, manifest, data, mediaFiles)
    }

    // ------------------------------------------------------------------
    // 校验 5：外键引用完整性
    // ------------------------------------------------------------------

    private fun validateReferences(data: BackupData) {
        val exerciseIds = idsOf(data, "exercise_definition")
        val planIds = idsOf(data, "training_plan")
        val planExerciseIds = idsOf(data, "plan_exercise")

        data.tables["plan_exercise"]?.let { table ->
            // PlanExercise.planId 是 NOT NULL 外键，指向不存在的计划会直接写库失败；
            // 提前检测可以给出可读的错误，而不是让用户面对 SQLite 的报错码。
            val planIdIndex = table.columns.indexOf("planId")
            if (planIdIndex >= 0) {
                table.rows.forEach { row ->
                    val value = (row.getOrNull(planIdIndex) as? JsonPrimitive)?.content?.toLongOrNull()
                    if (value != null && value !in planIds) {
                        throw ImportFailure("BROKEN_REFERENCE", "计划动作引用了不存在的计划 $value")
                    }
                }
            }
            val exerciseIndex = table.columns.indexOf("exerciseDefinitionId")
            if (exerciseIndex >= 0) {
                table.rows.forEach { row ->
                    val value = (row.getOrNull(exerciseIndex) as? JsonPrimitive)?.content?.toLongOrNull()
                    if (value != null && value !in exerciseIds) {
                        throw ImportFailure("BROKEN_REFERENCE", "计划动作引用了不存在的动作 $value")
                    }
                }
            }
        }

        data.tables["plan_set"]?.let { table ->
            val index = table.columns.indexOf("planExerciseId")
            if (index >= 0) {
                table.rows.forEach { row ->
                    val value = (row.getOrNull(index) as? JsonPrimitive)?.content?.toLongOrNull()
                    if (value != null && value !in planExerciseIds) {
                        throw ImportFailure("BROKEN_REFERENCE", "组处方引用了不存在的计划动作 $value")
                    }
                }
            }
        }
    }

    private fun idsOf(data: BackupData, table: String): Set<Long> {
        val snapshot = data.tables[table] ?: return emptySet()
        val idIndex = snapshot.columns.indexOf("id")
        if (idIndex < 0) return emptySet()
        return snapshot.rows.mapNotNull { row ->
            (row.getOrNull(idIndex) as? JsonPrimitive)?.content?.toLongOrNull()
        }.toSet()
    }

    // ------------------------------------------------------------------
    // 写库（单事务）
    // ------------------------------------------------------------------

    private fun writeTables(
        data: BackupData,
        wipeExisting: Boolean,
        onTable: (Int, Int) -> Unit,
    ) {
        val db: SupportSQLiteDatabase = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            if (wipeExisting) {
                // 反向删除：先删子表再删父表，否则外键约束会拒绝
                BACKUP_TABLE_ORDER.reversed().forEach { table ->
                    if (table in BACKUP_EXCLUDED_TABLES) return@forEach
                    runCatching { db.execSQL("DELETE FROM `$table`") }
                        .onFailure { Timber.w(it, "清空表 %s 失败", table) }
                }
                // 重置自增序列，保证导入后的新记录 id 不会与历史 id 撞车
                runCatching {
                    db.execSQL("DELETE FROM sqlite_sequence WHERE name IN " +
                        BACKUP_TABLE_ORDER.joinToString(",", "(", ")") { "'$it'" })
                }
            }

            BACKUP_TABLE_ORDER.forEachIndexed { index, table ->
                val snapshot = data.tables[table] ?: return@forEachIndexed
                if (snapshot.columns.isEmpty()) return@forEachIndexed

                val placeholders = snapshot.columns.joinToString(",") { "?" }
                val columnList = snapshot.columns.joinToString(",") { "`$it`" }
                // INSERT OR REPLACE：即使备份内部有重复主键（理论上不该有），也不会中断导入
                val sql = "INSERT OR REPLACE INTO `$table` ($columnList) VALUES ($placeholders)"
                val statement = db.compileStatement(sql)

                snapshot.rows.forEach { row ->
                    statement.clearBindings()
                    snapshot.columns.indices.forEach { columnIndex ->
                        when (val element = row.getOrNull(columnIndex)) {
                            null, is JsonNull -> statement.bindNull(columnIndex + 1)
                            is JsonPrimitive -> {
                                val content = element.content
                                when {
                                    // isString 为 false 时才可能是数字/布尔，这样不会把 TEXT 的 "123" 误当整数
                                    !element.isString && content.toLongOrNull() != null ->
                                        statement.bindLong(columnIndex + 1, content.toLong())
                                    !element.isString && content.toDoubleOrNull() != null ->
                                        statement.bindDouble(columnIndex + 1, content.toDouble())
                                    else -> statement.bindString(columnIndex + 1, content)
                                }
                            }
                            else -> statement.bindString(columnIndex + 1, element.toString())
                        }
                    }
                    statement.executeInsert()
                }
                onTable(index + 1, BACKUP_TABLE_ORDER.size)
            }

            db.setTransactionSuccessful()
        } catch (t: Throwable) {
            throw ImportFailure("DB_WRITE_FAILED", t.message ?: "写入失败，已回滚")
        } finally {
            db.endTransaction()
        }
    }

    /** @return 恢复失败的媒体文件数。部分失败不推翻导入（数据已完整入库），但必须上报 */
    private fun moveMediaIntoPlace(extracted: Extracted): Int {
        var failed = 0
        extracted.mediaFiles.forEach { file ->
            val relative = extracted.tempDir.toURI().relativize(file.toURI()).path
            if (relative.isBlank() || relative.startsWith("..")) return@forEach

            val destination = when {
                relative.startsWith("${BackupExporter.MEDIA_DIR}/") ->
                    File(mediaFileStore.mediaDir, file.name)
                relative.startsWith("${BackupExporter.THUMB_DIR}/") ->
                    File(mediaFileStore.thumbDir, file.name)
                else -> null
            } ?: return@forEach

            runCatching {
                if (destination.exists()) destination.delete()
                if (!file.renameTo(destination)) file.copyTo(destination, overwrite = true)
            }.onFailure {
                Timber.w(it, "移动媒体文件失败：%s", relative)
                failed++
            }
        }
        return failed
    }

    /** 导入过程中的可读失败，携带稳定的 reason 便于 UI 与埋点区分 */
    class ImportFailure(val reason: String, val detail: String) :
        IllegalStateException("$reason: $detail")

    fun toDomainError(failure: Throwable): DomainError = when (failure) {
        is ImportFailure -> DomainError.ImportFailure(failure.reason)
        else -> DomainError.Unknown(failure)
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024

        /** 条目数上限：正常备份最多"媒体数 × 2 + 2"，20000 足够宽松并能挡住 zip 炸弹 */
        const val MAX_ENTRIES = 20_000

        /** 单文件上限 500MB */
        const val MAX_SINGLE_FILE_BYTES = 500L * 1024 * 1024

        /** 解压后总大小上限 5GB */
        const val MAX_TOTAL_BYTES = 5L * 1024 * 1024 * 1024
    }
}
