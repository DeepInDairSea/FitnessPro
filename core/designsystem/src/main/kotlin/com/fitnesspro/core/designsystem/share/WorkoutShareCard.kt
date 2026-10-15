package com.fitnesspro.core.designsystem.share

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.fitnesspro.core.designsystem.formatDuration
import com.fitnesspro.core.designsystem.formatVolume
import com.fitnesspro.core.designsystem.formatWeightCompact
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.model.UnitConversion
import com.fitnesspro.core.model.WeightUnit
import com.fitnesspro.core.model.WorkoutSummary
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

// ---------------------------------------------------------------------------
// 训练分享卡片：Canvas 生成 PNG → 存相册 → 经 FileProvider 走系统分享。
// 训练总结页与历史"训练复盘"页共用。
// ---------------------------------------------------------------------------

object WorkoutShareCard {

    private val INK = Color.parseColor("#1B1A1F")
    private val MUTED = Color.parseColor("#6B6975")
    private val ACCENT = Color.parseColor("#F0653C")

    /**
     * 生成分享卡片位图（1080 宽、高度按内容自适应）。
     * 头像解码走 [com.fitnesspro.core.designsystem.component.AvatarDecoder]，
     * 带 EXIF 方向修正，不会再出现"头像横躺"。
     */
    fun buildCard(
        context: Context,
        summary: WorkoutSummary,
        nickname: String,
        avatarUri: String?,
        weightUnit: WeightUnit,
    ): Bitmap {
        val width = 1080
        val left = 72f
        val name = nickname.ifBlank { "健身者" }
        val date = Instant.ofEpochMilli(summary.completedAt ?: summary.startedAt)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1]
        val dateText = "${date.year}年${date.monthValue}月${date.dayOfMonth}日 · $weekday"

        // 高度按内容自适应：头部 + 总览 + 逐动作 + PR + 底部余量
        val exerciseRows = summary.exercises.take(14)
        val headerHeight = 300f
        val totalsHeight = 220f
        val rowsHeight = exerciseRows.size * 110f + (if (summary.exercises.size > 14) 90f else 0f)
        val prHeight = if (summary.prs.isEmpty()) 0f else 30f + 52f + summary.prs.take(4).size * 64f
        val bottomPadding = 90f
        val height = (headerHeight + totalsHeight + rowsHeight + prHeight + bottomPadding)
            .toInt()
            .coerceAtLeast(1080)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(0f, 0f, width.toFloat(), 14f, Paint().apply { color = ACCENT })

        // 头部：头像 + 昵称 + 日期
        canvas.drawAvatar(context, avatarUri, name, left + 52f, 150f, 52f)
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 46f; isFakeBoldText = true
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 30f }
        canvas.drawText(name, left + 130f, 140f, namePaint)
        canvas.drawText(dateText, left + 130f, 190f, metaPaint)
        canvas.drawText("Fitness Pro · 离线训练记录", left, 262f, metaPaint)

        // 计划名 + 总览
        var y = headerHeight + 90f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 58f; isFakeBoldText = true
        }
        canvas.drawText(titlePaint.fitText(summary.planName, width - left * 2), left, y, titlePaint)
        y += 56f
        val totals = listOf(
            "时长" to formatDuration(summary.durationSeconds),
            "容量" to formatVolume(summary.totalVolumeKg),
            "组数" to "${summary.totalSets}",
            "次数" to "${summary.totalReps}",
        )
        val columnWidth = (width - left * 2) / totals.size
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 28f }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT; textSize = 52f; isFakeBoldText = true
        }
        totals.forEachIndexed { i, (label, value) ->
            val x = left + i * columnWidth
            canvas.drawText(label, x, y, labelPaint)
            canvas.drawText(value, x, y + 64f, valuePaint)
        }
        y += totalsHeight

        // 逐动作：练了什么、几组、多少容量、最好成绩
        val rowNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 34f; isFakeBoldText = true
        }
        val rowSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 28f }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; textSize = 34f; isFakeBoldText = true
        }
        if (exerciseRows.isNotEmpty()) {
            canvas.drawText("训练内容", left, y, sectionPaint)
            y += 40f
            exerciseRows.forEach { row ->
                canvas.drawCircle(left + 8f, y + 24f, 8f, Paint().apply { color = ACCENT })
                canvas.drawText(
                    rowNamePaint.fitText(row.name, width - left * 2 - 40f),
                    left + 34f, y + 34f, rowNamePaint,
                )
                val bestWeight = row.bestWeightKg
                val bestDistance = row.distanceMeters
                val best = when {
                    bestWeight != null && row.bestReps != null ->
                        "最好 ${formatWeightCompact(UnitConversion.fromKg(bestWeight, weightUnit))}kg × ${row.bestReps}"
                    bestDistance != null -> "最好 ${(bestDistance / 1000).toInt()} km"
                    else -> ""
                }
                val sub = buildString {
                    append("${row.completedSets} 组 · ${row.totalReps} 次 · ${formatVolume(row.volumeKg)}")
                    if (best.isNotEmpty()) append(" · $best")
                }
                canvas.drawText(sub, left + 34f, y + 78f, rowSubPaint)
                y += 110f
            }
            if (summary.exercises.size > exerciseRows.size) {
                canvas.drawText(
                    "… 还有 ${summary.exercises.size - exerciseRows.size} 个动作",
                    left, y + 20f, rowSubPaint,
                )
                y += 80f
            }
        }

        // PR
        if (summary.prs.isNotEmpty()) {
            y += 30f
            canvas.drawText("🏆 个人纪录 × ${summary.prs.size}", left, y, sectionPaint)
            y += 52f
            val prPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 30f }
            summary.prs.take(4).forEach { pr ->
                val prValue = when (pr.prType) {
                    com.fitnesspro.core.model.PrType.MAX_REPS -> "${pr.value.toInt()} 次"
                    else -> "${formatWeightCompact(pr.value)} kg"
                }
                val prTypeName = context.getString(pr.prType.labelRes())
                canvas.drawText(
                    prPaint.fitText("${pr.exerciseName} · $prTypeName $prValue", width - left * 2),
                    left, y, prPaint,
                )
                y += 64f
            }
        }

        // 底部：仅保留轻量应用标识
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 26f }
        canvas.drawText("Fitness Pro", width - left - footerPaint.measureText("Fitness Pro"), height - 40f, footerPaint)
        return bitmap
    }

    /**
     * 写入系统相册（Pictures/FitnessPro）。Android 10+ 走 MediaStore（scoped storage）；
     * 9 及以下需 WRITE_EXTERNAL_STORAGE，由调用方先申请。
     */
    fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "fitness_pro_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FitnessPro")
            }
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw IOException("MediaStore.insert 返回 null（权限被拒或外部存储不可用）")

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("openOutputStream 返回 null：uri=$uri")

        outputStream.use { output ->
            val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            if (!ok) throw IOException("Bitmap.compress 返回 false（写入未完成）")
        }
        return true
    }

    /** 写一份到 cacheDir 供 FileProvider 分享；返回 content:// uri（失败返回 null） */
    fun createShareUri(context: Context, bitmap: Bitmap): Uri? {
        val cacheFile = runCatching {
            File(context.cacheDir, "share").apply { mkdirs() }
                .resolve("workout_summary.png")
                .also { f ->
                    f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
        }.getOrNull() ?: return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
    }

    private fun Paint.fitText(text: String, maxWidth: Float): String {
        if (measureText(text) <= maxWidth) return text
        val count = breakText(text, true, maxWidth - measureText("…"), null)
        return text.take(count.coerceAtLeast(1)) + "…"
    }

    /** 圆形头像：有图用图（中心裁剪成正方形，避免非方图被拉伸），没图用昵称首字母的实心圆 */
    private fun Canvas.drawAvatar(context: Context, avatarUri: String?, nickname: String, cx: Float, cy: Float, radius: Float) {
        val decoded = com.fitnesspro.core.designsystem.component.AvatarDecoder
            .decode(context, avatarUri)
        if (decoded != null && decoded.width > 0 && decoded.height > 0) {
            // 中心裁剪成正方形再画圆：横向/竖向照片都不会被拉伸成白条或变形
            val side = minOf(decoded.width, decoded.height)
            val square = if (decoded.width == side && decoded.height == side) {
                decoded
            } else {
                Bitmap.createBitmap(
                    decoded,
                    (decoded.width - side) / 2,
                    (decoded.height - side) / 2,
                    side,
                    side,
                )
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = radius * 2f / square.width
            val matrix = android.graphics.Matrix().apply {
                // 关键：shader 位图默认铺在画布原点 (0,0)，而圆心在 (cx,cy)；
                // 不平移的话圆会采样到 CLAMP 的边缘像素，表现为纯色圈/白圈，
                // 且与首页 ContentScale.Crop 的中心裁剪不一致。这里把位图中心对齐到圆心
                setTranslate(-square.width / 2f, -square.height / 2f)
                postScale(scale, scale)
                postTranslate(cx, cy)
            }
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            drawCircle(cx, cy, radius, paint)
        } else {
            drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
            val initial = nickname.firstOrNull()?.toString() ?: "健"
            val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = radius * 1.1f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            drawText(initial, cx, cy + radius * 0.36f, initialPaint)
        }
    }
}
