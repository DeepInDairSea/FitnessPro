package com.fitnesspro.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitnesspro.core.domain.repository.ExerciseRepository
import com.fitnesspro.core.model.Exercise
import com.fitnesspro.core.model.ExerciseFilter
import com.fitnesspro.core.model.ExerciseTaxonomy
import com.fitnesspro.core.model.MajorGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseLibraryUiState(
    val isLoading: Boolean = true,
    val tab: ExerciseTab = ExerciseTab.ALL,
    val query: String = "",
    /** 选中的大肌群/类别（v2 分类体系第一层） */
    val majorGroup: MajorGroup? = null,
    /** 选中的细分肌群（第二层，须属于 majorGroup） */
    val subGroup: String? = null,
    val exercises: List<Exercise> = emptyList(),
)

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    /** 查询条件的单一来源。Tab / 搜索 / 分类筛选都只是往这里写字段。 */
    private data class Query(
        val tab: ExerciseTab = ExerciseTab.ALL,
        val text: String = "",
        val majorGroup: MajorGroup? = null,
        val subGroup: String? = null,
    )

    private val query = MutableStateFlow(Query())

    val uiState: StateFlow<ExerciseLibraryUiState> = query
        .flatMapLatest { q ->
            val filter = q.toFilter()
            // "最近使用"走独立查询（按 usageCount / lastUsedAt 排序），
            // 其余分组复用同一个筛选接口，只是标志位不同
            val source = if (q.tab == ExerciseTab.RECENT) {
                exerciseRepository.observeRecentlyUsed(limit = 50)
            } else {
                exerciseRepository.observeFiltered(filter)
            }
            source.map { list ->
                ExerciseLibraryUiState(
                    isLoading = false,
                    tab = q.tab,
                    query = q.text,
                    majorGroup = q.majorGroup,
                    subGroup = q.subGroup,
                    exercises = list,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExerciseLibraryUiState(),
        )

    private fun Query.toFilter(): ExerciseFilter = ExerciseFilter(
        query = text,
        majorGroup = majorGroup,
        subGroup = subGroup,
        favoritesOnly = tab == ExerciseTab.FAVORITES,
        customOnly = tab == ExerciseTab.CUSTOM,
    )

    fun selectTab(tab: ExerciseTab) {
        query.value = query.value.copy(tab = tab)
    }

    fun onQueryChange(text: String) {
        query.value = query.value.copy(text = text)
    }

    /**
     * 选择大肌群：切换到别的大肌群时清掉不属于它的细分筛选；
     * 再点一次同一大肌群 = 取消（细分随之清除）。
     */
    fun onMajorGroupChange(major: MajorGroup?) {
        val current = query.value
        if (current.majorGroup == major) {
            query.value = current.copy(majorGroup = null, subGroup = null)
        } else {
            query.value = current.copy(majorGroup = major, subGroup = null)
        }
    }

    fun onSubGroupChange(sub: String?) {
        query.value = query.value.copy(subGroup = sub)
    }

    fun toggleFavorite(exercise: Exercise) {
        viewModelScope.launch {
            exerciseRepository.setFavorite(exercise.id, !exercise.isFavorite)
        }
    }

    /** 删除 = 移入回收站（30 天可恢复），与计划/历史删除语义一致 */
    fun moveToTrash(id: Long) {
        viewModelScope.launch {
            exerciseRepository.moveToTrash(id)
        }
    }
}
