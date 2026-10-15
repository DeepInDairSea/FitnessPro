package com.fitnesspro.ui

import com.fitnesspro.R

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.data.repository.daysLeftInTrash
import com.fitnesspro.core.domain.repository.ExerciseRepository
import com.fitnesspro.core.domain.repository.PlanRepository
import com.fitnesspro.core.domain.repository.WorkoutRepository
import com.fitnesspro.core.model.time.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 回收站分类：入口统一，进入时由来源页决定默认显示哪一类 */
enum class TrashType(val routeValue: String) {
    PLAN("plan"),
    EXERCISE("exercise"),
    HISTORY("history"),
}

/**
 * 回收站条目的统一展示模型：三类数据在这里抹平差异。
 * payload 字段按 type 取用（plan 用计数、exercise 用 isCustom、history 用时长容量），
 * 文案在 UI 层用 stringResource 拼装 —— ViewModel 不硬编码中文。
 */
data class TrashItemUi(
    val type: TrashType,
    val id: Long,
    val title: String,
    val deletedAt: Long,
    val daysLeft: Int,
    val canPurge: Boolean,
    val exerciseCount: Int = 0,
    val totalSets: Int = 0,
    val isCustom: Boolean = false,
    val durationSeconds: Int = 0,
    val totalVolumeKg: Double = 0.0,
)

@HiltViewModel
class UnifiedTrashViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val _type = MutableStateFlow(TrashType.PLAN)

    /** 当前展示的分类：页内分段切换跟随它（而不是进入时的路由参数） */
    val type: StateFlow<TrashType> = _type

    fun setType(t: TrashType) {
        _type.value = t
    }

    /** 操作结果的提示（字符串资源 id），UI 消费后置空 */
    val messageRes = MutableStateFlow<Int?>(null)

    fun consumeMessage() {
        messageRes.value = null
    }

    val items: StateFlow<List<TrashItemUi>> = _type
        .flatMapLatest { t ->
            val flow = when (t) {
                TrashType.PLAN -> planRepository.observeTrash().map { list ->
                    list.map {
                        TrashItemUi(
                            type = TrashType.PLAN,
                            id = it.id,
                            title = it.name,
                            deletedAt = it.deletedAt ?: 0L,
                            daysLeft = it.deletedAt?.let { d -> daysLeftInTrash(d, time.nowEpochMs()).toInt() } ?: 0,
                            canPurge = true,
                            exerciseCount = it.exerciseCount,
                            totalSets = it.totalSets,
                        )
                    }
                }
                TrashType.EXERCISE -> exerciseRepository.observeTrash().map { list ->
                    list.map {
                        TrashItemUi(
                            type = TrashType.EXERCISE,
                            id = it.id,
                            title = it.name,
                            deletedAt = it.deletedAt ?: 0L,
                            daysLeft = it.deletedAt?.let { d -> daysLeftInTrash(d, time.nowEpochMs()).toInt() } ?: 0,
                            canPurge = it.isCustom,
                            isCustom = it.isCustom,
                        )
                    }
                }
                TrashType.HISTORY -> workoutRepository.observeTrash().map { list ->
                    list.map {
                        TrashItemUi(
                            type = TrashType.HISTORY,
                            id = it.id,
                            title = it.planName,
                            deletedAt = it.deletedAt ?: 0L,
                            daysLeft = it.deletedAt?.let { d -> daysLeftInTrash(d, time.nowEpochMs()).toInt() } ?: 0,
                            canPurge = true,
                            durationSeconds = it.durationSeconds,
                            totalVolumeKg = it.totalVolumeKg,
                        )
                    }
                }
            }
            flow
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(item: TrashItemUi) {
        viewModelScope.launch {
            val outcome = when (item.type) {
                TrashType.PLAN -> planRepository.restoreFromTrash(item.id)
                TrashType.EXERCISE -> exerciseRepository.restoreFromTrash(item.id)
                TrashType.HISTORY -> workoutRepository.restoreSessionFromTrash(item.id)
            }
            messageRes.value = if (outcome.isSuccess) {
                R.string.trash_restored
            } else {
                R.string.trash_action_failed
            }
        }
    }

    fun deleteForever(item: TrashItemUi) {
        viewModelScope.launch {
            val outcome = when (item.type) {
                TrashType.PLAN -> planRepository.permanentlyDelete(item.id)
                TrashType.EXERCISE -> exerciseRepository.deleteForever(item.id)
                TrashType.HISTORY -> workoutRepository.deleteSessionForever(item.id)
            }
            if (outcome.isFailure) {
                messageRes.value = R.string.trash_purge_failed
            }
        }
    }
}
