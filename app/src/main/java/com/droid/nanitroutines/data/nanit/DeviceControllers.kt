package com.droid.nanitroutines.data.nanit

import com.droid.nanitroutines.domain.model.NurseryTelemetry

interface CameraController {
    suspend fun applyCameraRoutine(
        accessToken: String,
        cameraUid: String,
        sleepMode: Boolean,
        nightLightOn: Boolean
    ): Result<String>

    suspend fun fetchCameraTelemetry(
        accessToken: String,
        cameraUid: String
    ): Result<NurseryTelemetry>
}

interface SoundLightController {
    suspend fun applySoundLightRoutine(
        accessToken: String,
        speakerUid: String,
        lanIp: String = "",
        payload: SoundLightSettingsPayload
    ): Result<String>

    suspend fun fetchSoundLightTelemetry(
        accessToken: String,
        speakerUid: String,
        lanIp: String = ""
    ): Result<NurseryTelemetry>
}
