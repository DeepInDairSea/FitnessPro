package com.fitnesspro.core.mediaui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitnesspro.core.designsystem.theme.FitnessTheme
import com.fitnesspro.core.designsystem.labelRes
import com.fitnesspro.core.model.Exercise
import com.fitnesspro.core.model.MediaRef

/**
 * 动作详情底部弹层：训练页与计划编辑页共用的"查看动作说明"入口。
 *
 * 种子数据里每个动作都带 instructions（分号分隔的要领 + 变式）、器械、肌群、
 * 默认组数/次数/休息，但训练与做计划时一直没有入口能看到 —— 这里把它们集中呈现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    exercise: Exercise?,
    onDismiss: () -> Unit,
    onPlayMedia: (MediaRef) -> Unit = {},
) {
    if (exercise == null) return

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FitnessTheme.spacing.l)
                .padding(bottom = FitnessTheme.spacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseMedia(
                    media = exercise.media,
                    size = 64.dp,
                    onClick = { exercise.media?.let(onPlayMedia) },
                )
                Spacer(Modifier.width(FitnessTheme.spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(FitnessTheme.spacing.xs))
                    Text(
                        buildString {
                            append(stringResource(exercise.primaryMuscle.labelRes()))
                            exercise.subGroup?.let { append(" · ").append(it) }
                            append(" · ").append(stringResource(exercise.equipment.labelRes()))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(FitnessTheme.spacing.m))

            // 处方默认值：没跟计划时用户常问"这个动作该做几组几次"
            Text(
                stringResource(
                    R.string.exercise_detail_defaults,
                    exercise.defaultSets,
                    exercise.defaultReps ?: "-",
                    exercise.defaultRestSeconds,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(FitnessTheme.spacing.l))
            Text(
                stringResource(R.string.exercise_detail_instructions_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(FitnessTheme.spacing.s))

            val steps = exercise.instructions
                ?.split("\n")
                ?.firstOrNull()
                ?.split(';')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            if (steps.isEmpty()) {
                Text(
                    stringResource(R.string.exercise_detail_no_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(FitnessTheme.spacing.s)) {
                    steps.forEachIndexed { index, step ->
                        Row {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(FitnessTheme.spacing.s))
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 变式说明：instructions 第二段以"变式："开头
            val variation = exercise.instructions
                ?.split("\n")
                ?.firstOrNull { it.startsWith("变式") }
            if (variation != null) {
                Spacer(Modifier.height(FitnessTheme.spacing.m))
                Text(
                    variation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
