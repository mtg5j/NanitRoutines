package com.droid.nanitroutines.domain.model

enum class RoutineType(val displayName: String, val subtitle: String) {
    NAPTIME(
        displayName = "Naptime",
        subtitle = "Kips Room Main Lights OFF • Camera ON • Rain Sound & Light ON"
    ),
    WAKEUP(
        displayName = "Wakeup",
        subtitle = "Kips Room Main Lights ON • Camera Standby • Sound & Light OFF • Baby Daybook"
    )
}

enum class LightIntegrationMode(val label: String) {
    GOOGLE_HOME_INTENT("Open Google Home / Lutron"),
    WEBHOOK("Webhook URL"),
    LUTRON_LOCAL_BRIDGE("Lutron Bridge"),
    BOTH("App + Webhook"),
    DISABLED("Don't Auto-Open")
}

data class LutronZoneInfo(
    val zoneHref: String,
    val name: String
)

data class RoutineConfig(
    val routineType: RoutineType,
    val cameraSleepMode: Boolean,
    val cameraNightLightOn: Boolean,
    val soundLightPowerOn: Boolean,
    val soundEnabled: Boolean,
    val soundTrack: String,
    val soundVolume: Float,
    val lightBrightness: Float,
    val lightHue: Float = 0.08f,
    val lightSaturation: Float = 0.75f,
    val lightNoColor: Boolean = false,
    val smartLightMode: LightIntegrationMode = LightIntegrationMode.GOOGLE_HOME_INTENT,
    val smartLightsTurnOn: Boolean,
    val smartLightCommandPhrase: String,
    val smartLightWebhookUrl: String = "",
    val triggerBabyDaybook: Boolean
) {
    companion object {
        val AVAILABLE_TRACKS = listOf(
            "Rain",
            "White Noise",
            "Wind",
            "Waves",
            "Birds",
            "Lullaby 1"
        )

        fun defaultNaptime(): RoutineConfig = RoutineConfig(
            routineType = RoutineType.NAPTIME,
            cameraSleepMode = false, // Camera ON (Awake)
            cameraNightLightOn = false,
            soundLightPowerOn = true, // Sound + Light ON
            soundEnabled = true,
            soundTrack = "Rain", // User's default sound
            soundVolume = 0.45f,
            lightBrightness = 0.20f,
            lightHue = 0.08f, // Warm amber nursery glow
            lightSaturation = 0.75f,
            lightNoColor = false,
            smartLightMode = LightIntegrationMode.GOOGLE_HOME_INTENT,
            smartLightsTurnOn = false, // Kips Room Main Lights OFF
            smartLightCommandPhrase = "Turn off Kips Room Main Lights",
            smartLightWebhookUrl = "",
            triggerBabyDaybook = false
        )

        fun defaultWakeup(): RoutineConfig = RoutineConfig(
            routineType = RoutineType.WAKEUP,
            cameraSleepMode = true, // Camera OFF (Standby)
            cameraNightLightOn = false,
            soundLightPowerOn = false, // Turn off Sound and Light completely
            soundEnabled = false,
            soundTrack = "Rain",
            soundVolume = 0.45f,
            lightBrightness = 0.0f,
            lightHue = 0.08f,
            lightSaturation = 0.75f,
            lightNoColor = false,
            smartLightMode = LightIntegrationMode.GOOGLE_HOME_INTENT,
            smartLightsTurnOn = true, // Kips Room Main Lights ON
            smartLightCommandPhrase = "Turn on Kips Room Main Lights",
            smartLightWebhookUrl = "",
            triggerBabyDaybook = true // Open Baby Daybook sleep page
        )
    }
}

data class NanitBabyDevice(
    val babyUid: String,
    val babyName: String,
    val cameraUid: String,
    val speakerUid: String?,
    val speakerLanIp: String = ""
)

data class NurseryTelemetry(
    val cameraAwake: Boolean? = null,
    val cameraNightLightOn: Boolean? = null,
    val soundLightOn: Boolean? = null,
    val activeTrack: String? = null,
    val volumePercent: Int? = null,
    val brightnessPercent: Int? = null,
    val lutronLightsOn: Boolean? = null,
    val lutronBrightnessPercent: Int? = null,
    val temperatureCelsius: Float? = null,
    val humidityPercent: Float? = null,
    val lastUpdatedMillis: Long = 0L
)

enum class StepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    SKIPPED,
    FAILED
}

data class RoutineStepResult(
    val stepName: String,
    val status: StepStatus,
    val detail: String
)

data class RoutineExecutionReport(
    val routineType: RoutineType,
    val overallSuccess: Boolean,
    val steps: List<RoutineStepResult>,
    val babyDaybookLaunchRequested: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
)
