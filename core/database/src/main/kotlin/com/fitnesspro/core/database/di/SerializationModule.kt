package com.fitnesspro.core.database.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * 全局唯一的 Json 配置。
 *
 * 关键设置：
 *  - ignoreUnknownKeys：读取旧版本备份/种子文件时，新增字段不会导致解析失败（向前兼容）
 *  - encodeDefaults：导出备份时保留默认值，便于人工核对与跨版本比对
 */
@Module
@InstallIn(SingletonComponent::class)
object SerializationModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
        explicitNulls = false
    }
}
