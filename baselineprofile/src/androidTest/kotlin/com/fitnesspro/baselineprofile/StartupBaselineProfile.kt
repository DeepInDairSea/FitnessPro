package com.fitnesspro.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

class StartupBaselineProfile {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect("com.fitnesspro") {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}