package com.droid.nanitroutines.domain.usecase

import com.droid.nanitroutines.data.lights.BabyDaybookLauncher
import com.droid.nanitroutines.data.lights.SmartLightController
import com.droid.nanitroutines.data.nanit.CameraController
import com.droid.nanitroutines.data.nanit.SoundLightController
import com.droid.nanitroutines.data.nanit.SoundLightSettingsPayload
import com.droid.nanitroutines.domain.model.NanitBabyDevice
import com.droid.nanitroutines.domain.model.RoutineConfig
import com.droid.nanitroutines.domain.model.RoutineExecutionReport
import com.droid.nanitroutines.domain.model.RoutineStepResult
import com.droid.nanitroutines.domain.model.StepStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class ExecuteRoutineUseCase(
    private val cameraController: CameraController,
    private val soundLightController: SoundLightController,
    private val smartLightController: SmartLightController,
    private val babyDaybookLauncher: BabyDaybookLauncher
) {
    suspend fun execute(
        config: RoutineConfig,
        accessToken: String,
        device: NanitBabyDevice?,
        onStepProgress: (List<RoutineStepResult>) -> Unit = {}
    ): RoutineExecutionReport = coroutineScope {
        val steps = mutableListOf(
            RoutineStepResult(
                stepName = "Kips Room Main Lights (Lutron)",
                status = StepStatus.RUNNING,
                detail = if (config.smartLightsTurnOn) {
                    "Turning Kips Room Main Lights ON..."
                } else {
                    "Turning Kips Room Main Lights OFF..."
                }
            ),
            RoutineStepResult(
                stepName = "Nanit Pro Camera",
                status = StepStatus.RUNNING,
                detail = if (config.cameraSleepMode) {
                    "Switching camera to Standby (OFF)..."
                } else {
                    "Waking camera ON..."
                }
            ),
            RoutineStepResult(
                stepName = "Nanit Sound + Light",
                status = StepStatus.RUNNING,
                detail = if (config.soundLightPowerOn) {
                    "Starting ${config.soundTrack} & nightlight..."
                } else {
                    "Turning Sound + Light OFF..."
                }
            )
        )
        if (config.triggerBabyDaybook) {
            steps.add(
                RoutineStepResult(
                    stepName = "Baby Daybook (Sleep)",
                    status = StepStatus.PENDING,
                    detail = "Opening Baby Daybook sleep page..."
                )
            )
        }
        onStepProgress(steps.toList())

        // Execute Smart Lights, Nanit Camera, and Nanit Sound + Light concurrently for 1-tap speed
        val lightsDeferred = async {
            smartLightController.triggerSmartLights(
                mode = config.smartLightMode,
                turnOn = config.smartLightsTurnOn,
                commandPhrase = config.smartLightCommandPhrase,
                webhookUrl = config.smartLightWebhookUrl
            )
        }

        val cameraDeferred = async {
            val camUid = device?.cameraUid.orEmpty()
            if (camUid.isBlank()) {
                Result.failure(IllegalArgumentException("No Nanit Camera UID configured"))
            } else {
                cameraController.applyCameraRoutine(
                    accessToken = accessToken,
                    cameraUid = camUid,
                    sleepMode = config.cameraSleepMode,
                    nightLightOn = config.cameraNightLightOn
                )
            }
        }

        val soundLightDeferred = async {
            val spkUid = device?.speakerUid.orEmpty()
            val lanIp = device?.speakerLanIp.orEmpty()
            if (spkUid.isBlank() && lanIp.isBlank()) {
                Result.failure(IllegalArgumentException("No Nanit Sound + Light UID configured"))
            } else {
                val payload = if (config.soundLightPowerOn) {
                    SoundLightSettingsPayload(
                        isOn = true,
                        noSound = !config.soundEnabled,
                        track = config.soundTrack.ifBlank { "Rain" },
                        volume = config.soundVolume,
                        brightness = config.lightBrightness,
                        noColor = config.lightNoColor,
                        hue = config.lightHue,
                        saturation = config.lightSaturation
                    )
                } else {
                    // Wakeup: turn off Sound and Light completely
                    SoundLightSettingsPayload(
                        isOn = false,
                        noSound = true,
                        brightness = 0.0f
                    )
                }
                soundLightController.applySoundLightRoutine(
                    accessToken = accessToken,
                    speakerUid = spkUid,
                    lanIp = lanIp,
                    payload = payload
                )
            }
        }

        val lightsRes = lightsDeferred.await()
        val lightStepStatus = when {
            config.smartLightMode == com.droid.nanitroutines.domain.model.LightIntegrationMode.DISABLED -> StepStatus.SKIPPED
            lightsRes.isSuccess -> StepStatus.SUCCESS
            else -> StepStatus.FAILED
        }
        steps[0] = RoutineStepResult(
            stepName = "Kips Room Main Lights (Lutron)",
            status = lightStepStatus,
            detail = lightsRes.getOrElse { it.message ?: "Failed to trigger Kips Room Main Lights" }
        )
        onStepProgress(steps.toList())

        val cameraRes = cameraDeferred.await()
        steps[1] = RoutineStepResult(
            stepName = "Nanit Pro Camera",
            status = if (cameraRes.isSuccess) StepStatus.SUCCESS else StepStatus.FAILED,
            detail = cameraRes.getOrElse { it.message ?: "Failed to update camera" }
        )
        onStepProgress(steps.toList())

        val soundLightRes = soundLightDeferred.await()
        steps[2] = RoutineStepResult(
            stepName = "Nanit Sound + Light",
            status = if (soundLightRes.isSuccess) StepStatus.SUCCESS else StepStatus.FAILED,
            detail = soundLightRes.getOrElse { it.message ?: "Failed to update Sound + Light" }
        )
        onStepProgress(steps.toList())

        var babyDaybookLaunched = false
        if (config.triggerBabyDaybook) {
            val daybookRes = babyDaybookLauncher.openBabyDaybookSleepPage()
            babyDaybookLaunched = daybookRes.isSuccess
            steps[3] = RoutineStepResult(
                stepName = "Baby Daybook (Sleep)",
                status = if (daybookRes.isSuccess) StepStatus.SUCCESS else StepStatus.FAILED,
                detail = daybookRes.getOrElse { it.message ?: "Could not open Baby Daybook" }
            )
            onStepProgress(steps.toList())
        }

        val overallSuccess = steps.all { it.status == StepStatus.SUCCESS || it.status == StepStatus.SKIPPED }
        RoutineExecutionReport(
            routineType = config.routineType,
            overallSuccess = overallSuccess,
            steps = steps.toList(),
            babyDaybookLaunchRequested = babyDaybookLaunched
        )
    }
}
