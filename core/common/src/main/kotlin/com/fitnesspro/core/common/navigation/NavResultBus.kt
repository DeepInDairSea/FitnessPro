package com.fitnesspro.core.common.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 跨页面的**一次性结果**通道。
 *
 * ## 为什么不用 NavBackStackEntry.savedStateHandle
 *
 * 这是本项目实测踩到的坑：在 Hilt + Navigation Compose 下，
 * `hiltViewModel()` 注入给 ViewModel 的 SavedStateHandle
 * 与 `navController.previousBackStackEntry.savedStateHandle`
 * **不是同一个实例**。
 *
 * 真机日志（同一时刻、同一页面）：
 * ```
 * PlanEditViewModel 创建        handle=145941405
 * picker 选中     prev=plan_edit  prevHandle=259180680
 * ```
 * 选择器把选中的动作 id 写进了 `259180680`，而 ViewModel 在监听 `145941405`。
 * 结果是"选了动作却什么都没加上"，并且**两端都不认为出了问题**：
 * 写入成功、无异常、无日志、界面照常返回。
 *
 * 这类静默失效无法靠 try/catch 或加日志发现，只能换掉机制。
 *
 * ## 语义（有意选择，不要改成"顺手"的行为）
 *
 * - **投递即忘**：发布方发完就 pop，不关心谁在听。
 * - **按 key 订阅**：订阅方只收自己关心的 key，各页面互不干扰。
 * - **不重放历史**（`replay = 0`）：页面重建时不会重复消费上一次的选择。
 *   重复消费会导致"重建一次就多加一个动作"，比丢一次结果更糟。
 *
 * ## 使用约束
 *
 * 订阅方必须是**长生命周期**的：在 ViewModel 作用域内持续 collect。
 * 因为在 [publish] 的那一刻如果没有活跃订阅者，事件会被丢弃。
 * 当前两处使用（计划编辑页、训练页）都满足这一约束。
 */
@Singleton
class NavResultBus @Inject constructor() {

    private val events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 8,
    )

    private data class Event(val key: String, val value: Any)

    /**
     * 投递一个结果。key 一律使用 [NavResults] 中的常量，避免两端字符串写歪。
     *
     * 用 `tryEmit`：有缓冲区，正常不会失败；即便极端情况下丢了一次，
     * 用户最多需要再选一次动作，不会产生错误数据 —— 这是可接受的失败模式。
     */
    fun publish(key: String, value: Any) {
        events.tryEmit(Event(key, value))
    }

    /**
     * 订阅指定 key 的结果流。
     * 调用方按约定类型过滤，例如 `.filterIsInstance<Long>()`。
     */
    fun observe(key: String): Flow<Any> =
        events.filter { it.key == key }.map { it.value }
}
