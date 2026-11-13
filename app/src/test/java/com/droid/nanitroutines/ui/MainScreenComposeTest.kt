package com.droid.nanitroutines.ui

import com.droid.nanitroutines.data.lights.BabyDaybookLauncher
import com.droid.nanitroutines.data.lights.SmartLightController
import com.droid.nanitroutines.data.nanit.CameraController
import com.droid.nanitroutines.data.nanit.SoundLightController
import com.droid.nanitroutines.data.nanit.SoundLightSettingsPayload
import com.droid.nanitroutines.domain.model.LightIntegrationMode
import com.droid.nanitroutines.domain.model.NanitBabyDevice
import com.droid.nanitroutines.domain.model.NurseryTelemetry
import com.droid.nanitroutines.domain.model.RoutineConfig
import com.droid.nanitroutines.domain.usecase.ExecuteRoutineUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenComposeTest {

    @Test
    fun `default UI state configures Rain for Naptime and Kips Room Main Lights plus Baby Daybook Sleep for Wakeup`() {
        val state = MainUiState()

        // Verify Naptime defaults
        assertEquals("Rain", state.naptimeConfig.soundTrack)
        assertTrue(state.naptimeConfig.soundLightPowerOn)
        assertFalse(state.naptimeConfig.cameraSleepMode) // Camera ON
        assertFalse(state.naptimeConfig.smartLightsTurnOn) // Kips Room Main Lights OFF
        assertEquals("Turn off Kips Room Main Lights", state.naptimeConfig.smartLightCommandPhrase)

        // Verify Wakeup defaults
        assertFalse(state.wakeupConfig.soundLightPowerOn) // Sound + Light OFF
        assertTrue(state.wakeupConfig.cameraSleepMode) // Camera Standby (OFF)
        assertTrue(state.wakeupConfig.smartLightsTurnOn) // Kips Room Main Lights ON
        assertEquals("Turn on Kips Room Main Lights", state.wakeupConfig.smartLightCommandPhrase)
        assertTrue(state.wakeupConfig.triggerBabyDaybook) // Open Baby Daybook Sleep page
        assertEquals("GOOGLE_HOME", state.preferredLightApp)
        assertFalse(state.isTokenHealthy)
        assertEquals(null, state.activeActionKey)
        assertEquals(null, state.completedActionKey)
    }

    @Test
    fun `routine execution updates step progress for Naptime and Wakeup`() = runTest {
        var lastTrack: String? = null
        var lastCameraSleep: Boolean? = null
        var lastLightsOn: Boolean? = null
        var daybookSleepOpened = false

        val useCase = ExecuteRoutineUseCase(
            cameraController = object : CameraController {
                override suspend fun applyCameraRoutine(
                    accessToken: String,
                    cameraUid: String,
                    sleepMode: Boolean,
                    nightLightOn: Boolean
                ): Result<String> {
                    lastCameraSleep = sleepMode
                    return Result.success("OK")
                }

                override suspend fun fetchCameraTelemetry(
                    accessToken: String,
                    cameraUid: String
                ): Result<NurseryTelemetry> = Result.success(NurseryTelemetry())
            },
            soundLightController = object : SoundLightController {
                override suspend fun applySoundLightRoutine(
                    accessToken: String,
                    speakerUid: String,
                    lanIp: String,
                    payload: SoundLightSettingsPayload
                ): Result<String> {
                    lastTrack = payload.track
                    return Result.success("OK")
                }

                override suspend fun fetchSoundLightTelemetry(
                    accessToken: String,
                    speakerUid: String,
                    lanIp: String
                ): Result<NurseryTelemetry> = Result.success(NurseryTelemetry())
            },
            smartLightController = object : SmartLightController {
                override suspend fun triggerSmartLights(
                    mode: LightIntegrationMode,
                    turnOn: Boolean,
                    commandPhrase: String,
                    webhookUrl: String
                ): Result<String> {
                    lastLightsOn = turnOn
                    return Result.success("OK")
                }
            },
            babyDaybookLauncher = object : BabyDaybookLauncher {
                override fun isBabyDaybookInstalled(): Boolean = true
                override fun openBabyDaybookSleepPage(): Result<String> {
                    daybookSleepOpened = true
                    return Result.success("Opened Baby Daybook (Sleep)")
                }
            }
        )

        val device = NanitBabyDevice("b1", "Kip's Room", "cam1", "spk1")
        val napReport = useCase.execute(RoutineConfig.defaultNaptime(), "tok", device)
        assertTrue(napReport.overallSuccess)
        assertEquals("Rain", lastTrack)
        assertEquals(false, lastCameraSleep)
        assertEquals(false, lastLightsOn)

        val wakeReport = useCase.execute(RoutineConfig.defaultWakeup(), "tok", device)
        assertTrue(wakeReport.overallSuccess)
        assertEquals(true, lastCameraSleep)
        assertEquals(true, lastLightsOn)
        assertTrue(daybookSleepOpened)
    }
}
