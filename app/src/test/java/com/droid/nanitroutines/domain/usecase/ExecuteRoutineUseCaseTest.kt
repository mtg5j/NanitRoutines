package com.droid.nanitroutines.domain.usecase

import com.droid.nanitroutines.data.lights.BabyDaybookLauncher
import com.droid.nanitroutines.data.lights.SmartLightController
import com.droid.nanitroutines.data.nanit.CameraController
import com.droid.nanitroutines.data.nanit.SoundLightController
import com.droid.nanitroutines.data.nanit.SoundLightSettingsPayload
import com.droid.nanitroutines.domain.model.LightIntegrationMode
import com.droid.nanitroutines.domain.model.NanitBabyDevice
import com.droid.nanitroutines.domain.model.NurseryTelemetry
import com.droid.nanitroutines.domain.model.RoutineConfig
import com.droid.nanitroutines.domain.model.RoutineType
import com.droid.nanitroutines.domain.model.StepStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecuteRoutineUseCaseTest {

    private var recordedCameraSleepMode: Boolean? = null
    private var recordedSoundLightPayload: SoundLightSettingsPayload? = null
    private var recordedSmartLightsTurnOn: Boolean? = null
    private var recordedSmartLightPhrase: String? = null
    private var babyDaybookSleepPageOpened: Boolean = false

    private val fakeCamera = object : CameraController {
        override suspend fun applyCameraRoutine(
            accessToken: String,
            cameraUid: String,
            sleepMode: Boolean,
            nightLightOn: Boolean
        ): Result<String> {
            recordedCameraSleepMode = sleepMode
            return Result.success(if (sleepMode) "Camera Standby (OFF)" else "Camera Awake (ON)")
        }

        override suspend fun fetchCameraTelemetry(
            accessToken: String,
            cameraUid: String
        ): Result<NurseryTelemetry> = Result.success(NurseryTelemetry(cameraAwake = true))
    }

    private val fakeSoundLight = object : SoundLightController {
        override suspend fun applySoundLightRoutine(
            accessToken: String,
            speakerUid: String,
            lanIp: String,
            payload: SoundLightSettingsPayload
        ): Result<String> {
            recordedSoundLightPayload = payload
            return Result.success("Sound + Light updated")
        }

        override suspend fun fetchSoundLightTelemetry(
            accessToken: String,
            speakerUid: String,
            lanIp: String
        ): Result<NurseryTelemetry> = Result.success(NurseryTelemetry(soundLightOn = true))
    }

    private val fakeSmartLights = object : SmartLightController {
        override suspend fun triggerSmartLights(
            mode: LightIntegrationMode,
            turnOn: Boolean,
            commandPhrase: String,
            webhookUrl: String
        ): Result<String> {
            recordedSmartLightsTurnOn = turnOn
            recordedSmartLightPhrase = commandPhrase
            return Result.success("Kips Room Main Lights triggered: $commandPhrase")
        }
    }

    private val fakeBabyDaybook = object : BabyDaybookLauncher {
        override fun isBabyDaybookInstalled(): Boolean = true

        override fun openBabyDaybookSleepPage(): Result<String> {
            babyDaybookSleepPageOpened = true
            return Result.success("Opened Baby Daybook (Sleep)")
        }
    }

    private val testDevice = NanitBabyDevice(
        babyUid = "b-1",
        babyName = "Kip",
        cameraUid = "cam-123",
        speakerUid = "spk-456"
    )

    @Test
    fun `execute Naptime turns Camera ON, starts Rain on SoundLight, and turns Kips Room Main Lights OFF`() = runTest {
        val useCase = ExecuteRoutineUseCase(
            cameraController = fakeCamera,
            soundLightController = fakeSoundLight,
            smartLightController = fakeSmartLights,
            babyDaybookLauncher = fakeBabyDaybook
        )

        val report = useCase.execute(
            config = RoutineConfig.defaultNaptime(),
            accessToken = "token-abc",
            device = testDevice
        )

        assertEquals(RoutineType.NAPTIME, report.routineType)
        assertTrue(report.overallSuccess)
        // Camera ON means sleepMode = false
        assertEquals(false, recordedCameraSleepMode)
        // Sound + Light ON with Rain track
        assertNotNull(recordedSoundLightPayload)
        assertEquals(true, recordedSoundLightPayload!!.isOn)
        assertEquals(false, recordedSoundLightPayload!!.noSound)
        assertEquals("Rain", recordedSoundLightPayload!!.track)
        // Kips Room Main Lights OFF
        assertEquals(false, recordedSmartLightsTurnOn)
        assertEquals("Turn off Kips Room Main Lights", recordedSmartLightPhrase)
        assertFalse(report.babyDaybookLaunchRequested)
    }

    @Test
    fun `execute Wakeup puts Camera in Standby, turns SoundLight OFF, turns Kips Room Main Lights ON, and opens Baby Daybook Sleep page`() = runTest {
        val useCase = ExecuteRoutineUseCase(
            cameraController = fakeCamera,
            soundLightController = fakeSoundLight,
            smartLightController = fakeSmartLights,
            babyDaybookLauncher = fakeBabyDaybook
        )

        val report = useCase.execute(
            config = RoutineConfig.defaultWakeup(),
            accessToken = "token-abc",
            device = testDevice
        )

        assertEquals(RoutineType.WAKEUP, report.routineType)
        assertTrue(report.overallSuccess)
        // Camera OFF (Standby) means sleepMode = true
        assertEquals(true, recordedCameraSleepMode)
        // Sound + Light completely OFF
        assertNotNull(recordedSoundLightPayload)
        assertEquals(false, recordedSoundLightPayload!!.isOn)
        assertEquals(true, recordedSoundLightPayload!!.noSound)
        assertEquals(0.0f, recordedSoundLightPayload!!.brightness ?: 1f, 0.001f)
        // Kips Room Main Lights ON
        assertEquals(true, recordedSmartLightsTurnOn)
        assertEquals("Turn on Kips Room Main Lights", recordedSmartLightPhrase)
        // Baby Daybook Sleep page opened
        assertTrue(report.babyDaybookLaunchRequested)
        assertTrue(babyDaybookSleepPageOpened)
        assertTrue(report.steps.all { it.status == StepStatus.SUCCESS })
    }
}
