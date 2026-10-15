package com.fitnesspro.core.model.result

import kotlinx.coroutines.CancellationException

/**
 * 数据层统一返回类型。
 *
 * 约定（DEV 文档 §3.3）：Repository 不向上抛业务异常，而是返回 Outcome；
 * ViewModel 负责把 DomainError 映射为可展示的 UiError（在那里绑定资源 id，保证可测试性）。
 *
 * 放在 core:model 而不是 core:common：core:model 与 core:domain 都是纯 Kotlin JVM 模块，
 * 而 core:common 是 Android 库。领域层需要引用 Outcome，因此它必须位于纯 JVM 侧。
 */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): DomainError? = (this as? Failure)?.error
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(data)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (DomainError) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(error)
    return this
}

/**
 * 领域错误。每个错误都带有稳定的 code，用于日志与埋点。
 * **code 一经发布不可更改** —— 否则线上问题的归因会断掉。
 */
sealed interface DomainError {
    val code: String

    data object DatabaseFailure : DomainError {
        override val code = "DB_FAILURE"
    }

    /** 媒体文件在磁盘上已丢失（用户清理了应用数据、迁移失败等） */
    data class MediaFileMissing(val fileName: String) : DomainError {
        override val code = "MEDIA_MISSING"
    }

    data class InsufficientStorage(val requiredBytes: Long, val availableBytes: Long) : DomainError {
        override val code = "NO_SPACE"
    }

    data class MediaRejected(val reason: String, val detail: String) : DomainError {
        override val code = "MEDIA_REJECTED"
    }

    /** 视频压缩失败（编码器不支持、源文件损坏、空间不足等） */
    data class TranscodeFailure(val reason: String) : DomainError {
        override val code = "TRANSCODE_FAILED"
    }

    data object PermissionDenied : DomainError {
        override val code = "PERMISSION_DENIED"
    }

    data object NotFound : DomainError {
        override val code = "NOT_FOUND"
    }

    data object ValidationFailure : DomainError {
        override val code = "VALIDATION"
    }

    /** 名称与既有记录重复。携带名称便于调用方给出具体提示 */
    data class DuplicateName(val name: String) : DomainError {
        override val code = "DUPLICATE_NAME"
    }

    /** 导入失败，reason 由调用方给出（CORRUPT_ZIP / VERSION_TOO_NEW / ILLEGAL_PATH ...） */
    data class ImportFailure(val reason: String) : DomainError {
        override val code = "IMPORT_$reason"
    }

    data class ExportFailure(val reason: String) : DomainError {
        override val code = "EXPORT_$reason"
    }

    data class Unknown(val cause: Throwable) : DomainError {
        override val code = "UNKNOWN"
    }
}

/** 把任意异常收敛为 DomainError */
fun Throwable.toDomainError(): DomainError = when (this) {
    is IllegalStateException -> DomainError.ValidationFailure
    is IllegalArgumentException -> DomainError.ValidationFailure
    else -> DomainError.Unknown(this)
}

/** 便捷包装：把可能抛异常的块收敛为 Outcome。协程取消必须重新抛出，不能被吞掉。 */
inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Outcome.Failure(t.toDomainError())
    }

/** 挂起版本 */
suspend inline fun <T> runCatchingOutcomeSuspend(crossinline block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Outcome.Failure(t.toDomainError())
    }
