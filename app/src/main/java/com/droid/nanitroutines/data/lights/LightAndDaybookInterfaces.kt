package com.droid.nanitroutines.data.lights

import com.droid.nanitroutines.domain.model.LightIntegrationMode

data class SmartLightStatus(
    val isOn: Boolean,
    val brightnessPercent: Int
)

interface SmartLightController {
    suspend fun triggerSmartLights(
        mode: LightIntegrationMode,
        turnOn: Boolean,
        commandPhrase: String,
        webhookUrl: String
    ): Result<String>

    suspend fun fetchSmartLightStatus(): Result<SmartLightStatus?> = Result.success(null)

    fun openLutronApp(): Result<String> = Result.success("Opened Lutron App")
    fun openGoogleHomeApp(): Result<String> = Result.success("Opened Google Home App")
    fun openPreferredLightApp(): Result<String> = Result.success("Opened Smart Light App")
}

interface BabyDaybookLauncher {
    fun isBabyDaybookInstalled(): Boolean
    fun openBabyDaybookSleepPage(): Result<String>
    fun openNanitLiveCamera(): Result<String> = Result.success("Opened Nanit Live Camera")
}
