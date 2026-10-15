package com.fitnesspro.feature.workout.timer

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 休息结束的语音播报（设置里的"语音提示"开关）。
 *
 * TextToSpeech 的初始化是异步的，而播报发生在 AlarmReceiver 的短暂生命周期里，
 * 因此这里**常驻引擎实例**（Application 级单例）而不是"用时创建、说完销毁"：
 *  - 用时创建的话，第一次播报几乎必然落在初始化完成之前，用户听不到
 *  - 常驻的代价是一点内存与一个引擎连接，比"功能时灵时不灵"便宜得多
 *
 * 初始化完成前到达的播报请求挂在 [pending]，初始化成功后立即补播。
 * 引擎不可用 / 播报失败只记日志 —— TTS 是锦上添花，任何失败都不能影响计时正确性。
 */
@Singleton
class RestTtsAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun announce(text: String) {
        runCatching {
            val current = engine
            if (current == null) {
                pending = text
                engine = TextToSpeech(context) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    if (ready) {
                        runCatching {
                            engine?.language = Locale.SIMPLIFIED_CHINESE
                            pending?.let { speakNow(it) }
                        }.onFailure { Timber.w(it, "TTS 初始化后设置语言失败") }
                        pending = null
                    }
                }
                return
            }
            if (ready) speakNow(text) else pending = text
        }.onFailure { Timber.w(it, "TTS 播报失败") }
    }

    private fun speakNow(text: String) {
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rest_${System.nanoTime()}")
    }
}
