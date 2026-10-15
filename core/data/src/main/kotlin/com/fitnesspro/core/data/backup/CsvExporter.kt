package com.fitnesspro.core.data.backup

import android.content.Context
import android.net.Uri
import com.fitnesspro.core.database.FitnessDatabase
import com.fitnesspro.core.model.result.Outcome
import com.fitnesspro.core.model.result.runCatchingOutcomeSuspend
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CSV 导出（训练记录平铺表）。
 *
 * 存在的意义只有一个：**互操作性**。
 * zip 备份是给"本应用自己"用的（完整、可恢复）；CSV 是给
 * Excel / Python / 其他健身 App 用的（可读、通用）。
 * 两者不可互相替代，所以两个都要有。
 *
 * 列顺序刻意与 Strong / Hevy 的导出格式保持接近，降低用户迁移成本。
 */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FitnessDatabase,
    private val time: TimeProvider,
) {

    suspend fun export(target: Uri): Outcome<Long> = runCatchingOutcomeSuspend {
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(target)
                ?: throw IllegalStateException("无法写入所选位置")

            var rowCount = 0L
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                // UTF-8 BOM：没有它 Excel 打开中文列名会乱码
                writer.write('\uFEFF'.code.toString().let { "\uFEFF" })
                writer.write(HEADER)
                writer.newLine()

                database.openHelper.readableDatabase.query(EXPORT_SQL).use { cursor ->
                    val columnCount = cursor.columnCount
                    while (cursor.moveToNext()) {
                        val line = buildString {
                            for (index in 0 until columnCount) {
                                if (index > 0) append(',')
                                append(escape(cursor.getString(index)))
                            }
                        }
                        writer.write(line)
                        writer.newLine()
                        rowCount++
                    }
                }
            }
            rowCount
        }
    }

    /**
     * CSV 转义。
     *
     * 必须同时处理三种情况：含分隔符、含引号、含换行。
     * 训练备注里出现逗号和换行是非常常见的（用户会写多行备注），
     * 只处理引号会导致导出的表在 Excel 里整体错列。
     */
    private fun escape(raw: String?): String {
        if (raw == null) return ""
        val needsQuote = raw.contains(',') || raw.contains('"') || raw.contains('\n') || raw.contains('\r')
        if (!needsQuote) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }

    private companion object {
        val HEADER = listOf(
            "date",
            "plan",
            "exercise",
            "set_index",
            "set_type",
            "weight_kg",
            "reps",
            "duration_s",
            "distance_m",
            "rpe",
            "is_pr",
            "notes",
        ).joinToString(",")

        /**
         * 平铺查询：一次查出所有已完成的组成绩。
         *
         * 用 LEFT JOIN 而非 INNER JOIN：动作可能被归档（exerciseDefinitionId 可能为 NULL），
         * 但训练成绩仍然必须被导出 —— INNER JOIN 会把这些行悄悄丢掉。
         */
        val EXPORT_SQL = """
            SELECT
                date(s.startedAt / 1000, 'unixepoch', 'localtime') AS date,
                s.planNameSnapshot AS plan,
                we.nameSnapshot AS exercise,
                ws.setIndex AS set_index,
                ws.setType AS set_type,
                ws.weightKg AS weight_kg,
                ws.reps AS reps,
                ws.durationSeconds AS duration_s,
                ws.distanceMeters AS distance_m,
                ws.rpe AS rpe,
                ws.isPersonalRecord AS is_pr,
                IFNULL(ws.notes, '') AS notes
            FROM workout_set ws
            JOIN workout_exercise we ON ws.workoutExerciseId = we.id
            JOIN workout_session s ON we.sessionId = s.id
            WHERE ws.status = 'COMPLETED'
              AND s.isDeleted = 0
            ORDER BY s.startedAt DESC, we.orderIndex ASC, ws.setIndex ASC
        """.trimIndent()
    }
}
