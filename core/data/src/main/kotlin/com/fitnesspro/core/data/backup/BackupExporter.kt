package com.fitnesspro.core.data.backup

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.database.dao.MediaDao
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ExportProgress(
    val phase: Phase,
    val done: Int = 0,
    val total: Int = 0,
    val bytesWritten: Long = 0L,
) {
    enum class Phase { TABLES, MEDIA, FINALIZING, DONE }
    val fraction: Float
        get() = when {
            phase == Phase.DONE -> 1f
            total <= 0 -> 0f
            else -> (done.toFloat() / total).coerceIn(0f, 1f)
        }
}

/**
 * 完整备份导出。
 *
 * 产物结构：
 * ```
 * fitnesspro_backup_YYYYMMDD_HHmmss.zip
 * ├── manifest.json     格式版本 / 各表行数
 * ├── data.json         全部业务表快照（通用表快照，见 BackupFormat.kt）
 * └── media/{uuid}.xxx  媒体原文件
 *     thumbs/{uuid}.webp
 * ```
 *
 * 三个工程要点：
 *  1. **全流式**：一边查表一边写 zip，绝不把整库读进内存（用户的库可能几十 MB）
 *  2. **媒体一并打包**：PRD v1.1 要求用户"手动重新关联媒体"，那是把工程债转嫁给用户，
 *     实际没人会做。这里直接打包，导入即完整恢复
 *  3. **导出前 checkpoint WAL**：否则最近的写入还在 -wal 文件里，快照会缺数据
 *
 * 另外一个不变量：**目标位置要么出现完整 zip，要么什么都没有**。
 * zip 先在 cacheDir 落成一个完整本地文件，最后一步才拷向 SAF 目标；
 * 中途任何失败（IO 错误 / 磁盘满 / 用户取消存储权限）都不会在目标位置留下半截压缩包。
 */
@Singleton
class BackupExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FitnessDatabase,
    private val mediaFileStore: MediaFileStore,
    private val mediaDao: MediaDao,
    private val time: TimeProvider,
    private val json: Json,
) {

    fun export(target: Uri): Flow<ExportProgress> = flow {
        val db = database.openHelper.writableDatabase

        // 关键：把 WAL 内容合并回主库，否则刚完成的训练可能不在快照里
        runCatching { db.query("PRAGMA wal_checkpoint(FULL)").close() }
            .onFailure { Timber.w(it, "WAL checkpoint 失败，快照可能缺少最近写入") }

        // 只打包"有引用"的文件：
        // 以前是整目录扫描，孤儿文件会被一起导出，恢复后又原样写回变成新的孤儿 ——
        // 用户清了空间，换个手机备份恢复，垃圾又回来了。
        // 取值口径是"引用方表 ∪ 已登记的 ACTIVE 行"：以引用为准，
        // 登记表只是补充（预置媒体可能没登记，但引用确实存在）。
        val referencedMedia = mediaDao.referencedMediaNames().toSet()
        val referencedThumbs = mediaDao.referencedThumbnailNames().toSet()

        val mediaFiles = mediaFileStore.mediaDir.listFiles()
            ?.filter { it.isFile && it.name in referencedMedia }
            .orEmpty()
        val thumbFiles = mediaFileStore.thumbDir.listFiles()
            ?.filter { it.isFile && it.name in referencedThumbs }
            .orEmpty()
        val totalMediaBytes = mediaFiles.sumOf { it.length() } + thumbFiles.sumOf { it.length() }

        emit(ExportProgress(ExportProgress.Phase.TABLES, 0, BACKUP_TABLE_ORDER.size))

        val tables = linkedMapOf<String, TableSnapshot>()
        val rowCounts = linkedMapOf<String, Int>()
        BACKUP_TABLE_ORDER.forEachIndexed { index, table ->
            val snapshot = dumpTable(db, table)
            tables[table] = snapshot
            rowCounts[table] = snapshot.rows.size
            emit(ExportProgress(ExportProgress.Phase.TABLES, index + 1, BACKUP_TABLE_ORDER.size))
        }

        // 版本信息从 PackageManager 读，而不是 BuildConfig：
        // core:database 关闭了 buildConfig，且版本号属于应用自身信息，
        // 通过 Context 读取是唯一不需要跨模块暴露 BuildConfig 的方式。
        val (versionName, versionCode) = readAppVersion()

        val manifest = BackupManifest(
            databaseVersion = db.version,
            appVersionName = versionName,
            appVersionCode = versionCode,
            exportedAtEpochMs = time.nowEpochMs(),
            mediaFileCount = mediaFiles.size + thumbFiles.size,
            mediaTotalBytes = totalMediaBytes,
            tableRowCounts = rowCounts,
        )

        // 先在 cacheDir 落完整本地 zip，最后一步才拷向目标 —— 保证目标位置不会出现半截文件
        val temp = File.createTempFile("export_", ".zip", context.cacheDir)
        try {
            var written = 0L
            ZipOutputStream(BufferedOutputStream(temp.outputStream(), BUFFER_SIZE)).use { zip ->
                // manifest
                zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
                written += ENTRY_MANIFEST.length

                // data
                zip.putNextEntry(ZipEntry(ENTRY_DATA))
                zip.write(
                    json.encodeToString(
                        BackupData.serializer(),
                        BackupData(tables),
                    ).toByteArray()
                )
                zip.closeEntry()

                // media
                val allMedia = mediaFiles.map { MEDIA_DIR to it } + thumbFiles.map { THUMB_DIR to it }
                emit(ExportProgress(ExportProgress.Phase.MEDIA, 0, allMedia.size, written))

                allMedia.forEachIndexed { index, (dir, file) ->
                    zip.putNextEntry(ZipEntry("$dir/${file.name}"))
                    file.inputStream().use { input -> input.copyTo(zip, BUFFER_SIZE) }
                    zip.closeEntry()
                    written += file.length()
                    emit(ExportProgress(ExportProgress.Phase.MEDIA, index + 1, allMedia.size, written))
                }

                emit(ExportProgress(ExportProgress.Phase.FINALIZING, 0, 0, written))
            }

            // 提交：本地完整 zip → SAF 目标。这一步失败时目标最多是空文件（可安全重试）
            val output = context.contentResolver.openOutputStream(target)
                ?: throw IllegalStateException("无法写入所选位置")
            output.use { out ->
                temp.inputStream().use { input -> input.copyTo(out, BUFFER_SIZE) }
            }

            emit(ExportProgress(ExportProgress.Phase.DONE, 1, 1, temp.length()))
        } finally {
            runCatching { temp.delete() }
        }
    }.flowOn(Dispatchers.IO)

    /** 便捷封装：把 Flow 收敛为 Outcome，并保证异常不会漏成崩溃 */
    suspend fun exportTo(target: Uri): Outcome<Long> = runCatchingOutcomeSuspend {
        var written = 0L
        export(target).collect { written = it.bytesWritten }
        written
    }

    // ------------------------------------------------------------------
    // 表快照
    // ------------------------------------------------------------------

    private fun dumpTable(db: SupportSQLiteDatabase, table: String): TableSnapshot {
        if (table in BACKUP_EXCLUDED_TABLES) return TableSnapshot(emptyList(), emptyList())

        val columns = readColumns(db, table)
        if (columns.isEmpty()) return TableSnapshot(emptyList(), emptyList())

        val rows = mutableListOf<List<JsonElement>>()
        db.query("SELECT * FROM `$table`").use { cursor ->
            while (cursor.moveToNext()) {
                rows += List(cursor.columnCount) { index -> cursorToJson(cursor, index) }
            }
        }
        return TableSnapshot(columns, rows)
    }

    private fun readColumns(db: SupportSQLiteDatabase, table: String): List<String> {
        val columns = mutableListOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex < 0) return emptyList()
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        return columns
    }

    @Suppress("DEPRECATION")
    private fun readAppVersion(): Pair<String, Long> = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        (info.versionName ?: "") to code
    }.getOrDefault("" to 0L)

    internal companion object {
        const val ENTRY_MANIFEST = "manifest.json"
        const val ENTRY_DATA = "data.json"
        const val MEDIA_DIR = "media"
        const val THUMB_DIR = "thumbs"
        const val BUFFER_SIZE = 8 * 1024

        /**
         * SQLite 动态类型 → JSON。
         *
         * 必须按 `getType` 而不是"试着当字符串读"：如果统一转字符串，
         * 导入时写回的 REAL 会变成 TEXT，`weight_kg` 的排序与 SUM 会静默出错。
         */
        fun cursorToJson(cursor: Cursor, index: Int): JsonElement = when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JsonNull
            Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cursor.getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(cursor.getDouble(index))
            Cursor.FIELD_TYPE_STRING -> JsonPrimitive(cursor.getString(index))
            Cursor.FIELD_TYPE_BLOB -> JsonNull // schema 无 BLOB；若引入需改为 base64 + 类型标记
            else -> JsonNull
        }
    }
}


