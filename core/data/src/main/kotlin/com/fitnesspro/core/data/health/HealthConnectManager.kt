package com.fitnesspro.core.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health Connect 的唯一入口（DEV 文档 §11）。
 *
 * 边界刻意收得很窄：**只读体重**。
 *  - 写训练会话需要 WRITE_EXERCISE 权限，带来的隐私弹窗与审核成本远高于价值，
 *    v1.0 不做（文档 §11.1 里它也只是"写入"方向，无任何读取方）
 *  - 步数留给 v1.1
 *
 * 所有方法都假设调用方已处理权限；这里只负责"能不能用"与"读数据"两件事。
 * 任何 Health Connect 异常都收敛为空结果 / false —— 健康数据是可选增强，
 * 它的任何失败都不允许影响本地功能的正确性。
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    sealed interface Availability {
        data object Available : Availability
        data object UpdateRequired : Availability
        data object NotInstalled : Availability
        data object Unsupported : Availability
    }

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.UpdateRequired
        HealthConnectClient.SDK_UNAVAILABLE -> Availability.NotInstalled
        else -> Availability.Unsupported
    }

    suspend fun hasReadWeightPermission(): Boolean = runCatching {
        HealthConnectClient.getOrCreate(context)
            .permissionController
            .getGrantedPermissions()
            .containsAll(PERMISSIONS)
    }.getOrDefault(false)

    /**
     * 读取 [fromEpochMs] 之后的体重记录，按时间升序。
     * 同一天可能有多条（智能秤一天称好几次），去重策略由调用方决定。
     */
    suspend fun readWeights(fromEpochMs: Long): List<HealthWeight> = runCatching {
        val response = HealthConnectClient.getOrCreate(context).readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    Instant.ofEpochMilli(fromEpochMs),
                    Instant.now(),
                ),
            ),
        )
        response.records
            .sortedBy { it.time.toEpochMilli() }
            .map { HealthWeight(timeEpochMs = it.time.toEpochMilli(), weightKg = it.weight.inKilograms) }
    }.onFailure { Timber.w(it, "读取 Health Connect 体重失败") }
        .getOrDefault(emptyList())

    data class HealthWeight(val timeEpochMs: Long, val weightKg: Double)

    companion object {
        /**
         * 请求集只有读体重一项。直接用权限字符串常量而不是
         * `HealthPermission.getPermission(WeightRecord.WEIGHT)`：
         * 字符串就是 Manifest 声明的那一个，两处对照即可发现不一致，
         * 还避开了 connect-client 各版本间权限 API 的签名漂移。
         */
        const val READ_WEIGHT_PERMISSION = "android.permission.health.READ_WEIGHT"
        val PERMISSIONS: Set<String> = setOf(READ_WEIGHT_PERMISSION)
    }
}
