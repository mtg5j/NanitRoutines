package com.droid.nanitroutines.data.nanit

import com.droid.nanitroutines.domain.model.NurseryTelemetry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.atomic.AtomicInteger

class NanitCameraWebSocketClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val wsBaseUrl: String = "wss://api.nanit.com",
    private val tokenStore: TokenStore? = null,
    private val authApi: NanitAuthApi? = null
) : CameraController {

    private val requestIdCounter = AtomicInteger(100)

    private class HttpUnauthorizedException(val code: Int, message: String) : IllegalStateException(message)

    private suspend fun resolveValidToken(passedToken: String, forceRefresh: Boolean = false): String {
        if (tokenStore != null && authApi != null) {
            val refreshed = authApi.ensureValidAccessToken(tokenStore, forceRefresh = forceRefresh)
            if (refreshed.isSuccess) {
                return refreshed.getOrThrow()
            }
        }
        return passedToken
    }

    override suspend fun applyCameraRoutine(
        accessToken: String,
        cameraUid: String,
        sleepMode: Boolean,
        nightLightOn: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        if (cameraUid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Camera UID is empty — check Settings"))
        }
        val activeToken = resolveValidToken(accessToken, forceRefresh = false)
        if (activeToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Sign in to Nanit in Settings to control camera"))
        }

        val firstAttempt = executeCameraRoutineOnce(activeToken, cameraUid, sleepMode, nightLightOn)
        if (firstAttempt.isFailure && firstAttempt.exceptionOrNull() is HttpUnauthorizedException &&
            tokenStore != null && authApi != null
        ) {
            // Token rejected with 401/403 — force-refresh token once and retry automatically
            val freshToken = authApi.ensureValidAccessToken(tokenStore, forceRefresh = true).getOrNull()
            if (!freshToken.isNullOrBlank()) {
                return@withContext executeCameraRoutineOnce(freshToken, cameraUid, sleepMode, nightLightOn)
            }
        }
        firstAttempt
    }

    private suspend fun executeCameraRoutineOnce(
        token: String,
        cameraUid: String,
        sleepMode: Boolean,
        nightLightOn: Boolean
    ): Result<String> = runCatching {
        val putSettingsReqId = requestIdCounter.incrementAndGet()
        val putControlReqId = requestIdCounter.incrementAndGet()
        val ackDeferred = CompletableDeferred<DecodedCameraFrame>()

        val request = Request.Builder()
            .url("$wsBaseUrl/focus/cameras/$cameraUid/user_connect")
            .header("Authorization", "Bearer $token")
            .build()

        val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val settingsBytes = NanitProtoCodec.encodeCameraPutSettings(
                    requestId = putSettingsReqId,
                    sleepMode = sleepMode
                )
                webSocket.send(settingsBytes.toByteString())

                val controlBytes = NanitProtoCodec.encodeCameraPutControl(
                    requestId = putControlReqId,
                    nightLightOn = nightLightOn
                )
                webSocket.send(controlBytes.toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val decoded = NanitProtoCodec.decodeCameraFrame(bytes.toByteArray())
                if (decoded.messageType == NanitProtoCodec.CAM_MSG_RESPONSE &&
                    (decoded.requestId == putSettingsReqId || decoded.requestId == putControlReqId)
                ) {
                    if (!ackDeferred.isCompleted) {
                        ackDeferred.complete(decoded)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!ackDeferred.isCompleted) {
                    val code = response?.code
                    if (code == 401 || code == 403) {
                        ackDeferred.completeExceptionally(
                            HttpUnauthorizedException(code, "Camera auth expired (HTTP $code)")
                        )
                    } else {
                        ackDeferred.completeExceptionally(t)
                    }
                }
            }
        })

        try {
            val ack = withTimeoutOrNull(6500L) { ackDeferred.await() }
            val modeDesc = if (sleepMode) "Standby (OFF)" else "Awake (ON)"
            val lightDesc = if (nightLightOn) "Nightlight ON" else "Nightlight OFF"
            if (ack != null && (ack.statusCode == null || ack.statusCode in 200..299)) {
                "Camera -> $modeDesc • $lightDesc"
            } else if (ack != null) {
                throw IllegalStateException("Camera returned status ${ack.statusCode}: ${ack.statusMessage}")
            } else {
                "Camera -> $modeDesc"
            }
        } finally {
            ws.close(1000, "Routine complete")
        }
    }

    override suspend fun fetchCameraTelemetry(
        accessToken: String,
        cameraUid: String
    ): Result<NurseryTelemetry> = withContext(Dispatchers.IO) {
        val activeToken = resolveValidToken(accessToken, forceRefresh = false)
        if (cameraUid.isBlank() || activeToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Missing camera credentials"))
        }

        val firstAttempt = fetchCameraTelemetryOnce(activeToken, cameraUid)
        if (firstAttempt.isFailure && firstAttempt.exceptionOrNull() is HttpUnauthorizedException &&
            tokenStore != null && authApi != null
        ) {
            val freshToken = authApi.ensureValidAccessToken(tokenStore, forceRefresh = true).getOrNull()
            if (!freshToken.isNullOrBlank()) {
                return@withContext fetchCameraTelemetryOnce(freshToken, cameraUid)
            }
        }
        firstAttempt
    }

    private suspend fun fetchCameraTelemetryOnce(
        token: String,
        cameraUid: String
    ): Result<NurseryTelemetry> = runCatching {
        val getSettingsId = requestIdCounter.incrementAndGet()
        val getSensorsId = requestIdCounter.incrementAndGet()
        val settingsDeferred = CompletableDeferred<DecodedCameraFrame>()
        val sensorsDeferred = CompletableDeferred<DecodedCameraFrame>()

        val request = Request.Builder()
            .url("$wsBaseUrl/focus/cameras/$cameraUid/user_connect")
            .header("Authorization", "Bearer $token")
            .build()

        val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(NanitProtoCodec.encodeCameraGetSettings(getSettingsId).toByteString())
                webSocket.send(NanitProtoCodec.encodeCameraGetSensorData(getSensorsId).toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val frame = NanitProtoCodec.decodeCameraFrame(bytes.toByteArray())
                if (frame.sleepMode != null && !settingsDeferred.isCompleted) {
                    settingsDeferred.complete(frame)
                }
                if ((frame.temperatureMilli != null || frame.humidityMilli != null) && !sensorsDeferred.isCompleted) {
                    sensorsDeferred.complete(frame)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                val ex = if (code == 401 || code == 403) {
                    HttpUnauthorizedException(code, "Camera auth expired (HTTP $code)")
                } else {
                    t
                }
                if (!settingsDeferred.isCompleted) settingsDeferred.completeExceptionally(ex)
                if (!sensorsDeferred.isCompleted) sensorsDeferred.completeExceptionally(ex)
            }
        })

        try {
            val settingsFrame = withTimeoutOrNull(5000L) { settingsDeferred.await() }
            val sensorFrame = withTimeoutOrNull(3000L) { sensorsDeferred.await() }
            NurseryTelemetry(
                cameraAwake = settingsFrame?.sleepMode?.not(),
                cameraNightLightOn = settingsFrame?.nightLightOn,
                temperatureCelsius = sensorFrame?.temperatureMilli?.let { it / 1000f },
                humidityPercent = sensorFrame?.humidityMilli?.let { it / 1000f },
                lastUpdatedMillis = System.currentTimeMillis()
            )
        } finally {
            ws.close(1000, "Telemetry read complete")
        }
    }
}
