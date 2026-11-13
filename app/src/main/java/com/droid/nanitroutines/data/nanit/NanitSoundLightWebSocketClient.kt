package com.droid.nanitroutines.data.nanit

import com.droid.nanitroutines.domain.model.NurseryTelemetry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class NanitSoundLightWebSocketClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val remoteWsBaseUrl: String = "wss://remote.nanit.com",
    private val tokenStore: TokenStore? = null,
    private val authApi: NanitAuthApi? = null
) : SoundLightController {

    private val requestIdCounter = AtomicInteger(500)

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

    override suspend fun applySoundLightRoutine(
        accessToken: String,
        speakerUid: String,
        lanIp: String,
        payload: SoundLightSettingsPayload
    ): Result<String> = withContext(Dispatchers.IO) {
        if (speakerUid.isBlank() && lanIp.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Sound + Light UID is empty — check Settings"))
        }
        val activeToken = resolveValidToken(accessToken, forceRefresh = false)
        if (activeToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Sign in to Nanit in Settings to control Sound + Light"))
        }

        val firstAttempt = executeSoundLightRoutineOnce(activeToken, speakerUid, lanIp, payload)
        if (firstAttempt.isFailure && firstAttempt.exceptionOrNull() is HttpUnauthorizedException &&
            tokenStore != null && authApi != null
        ) {
            val freshToken = authApi.ensureValidAccessToken(tokenStore, forceRefresh = true).getOrNull()
            if (!freshToken.isNullOrBlank()) {
                return@withContext executeSoundLightRoutineOnce(freshToken, speakerUid, lanIp, payload)
            }
        }
        firstAttempt
    }

    private suspend fun executeSoundLightRoutineOnce(
        token: String,
        speakerUid: String,
        lanIp: String,
        payload: SoundLightSettingsPayload
    ): Result<String> = runCatching {
        val reqId = requestIdCounter.incrementAndGet()
        val sessionId = UUID.randomUUID().toString()
        val responseDeferred = CompletableDeferred<DecodedSoundLightFrame>()
        val commandSent = AtomicBoolean(false)
        val isLocalSocket = lanIp.isNotBlank()

        val targetUrl = if (isLocalSocket) {
            "wss://${lanIp.trim()}:442"
        } else {
            "$remoteWsBaseUrl/speakers/$speakerUid/user_connect/"
        }

        // CRITICAL: Sound + Light uses `Authorization: token {access_token}` (NOT Bearer)
        val request = Request.Builder()
            .url(targetUrl)
            .header("Authorization", "token $token")
            .build()

        // Normalize OFF payload: hardware expects bare `Settings{isOn = false}` (or explicit fields when ON)
        val wirePayload = if (payload.isOn == false) {
            SoundLightSettingsPayload(isOn = false)
        } else {
            payload
        }

        fun sendSettingsFrameOnce(webSocket: WebSocket) {
            if (commandSent.compareAndSet(false, true)) {
                val frameBytes = NanitProtoCodec.encodeSoundLightPutSettings(
                    requestId = reqId,
                    payload = wirePayload,
                    sessionId = sessionId
                )
                webSocket.send(frameBytes.toByteString())
            }
        }

        val scope = CoroutineScope(Dispatchers.IO)

        val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (isLocalSocket) {
                    // Direct LAN socket does not emit a Cloud Relay BackendDevice frame
                    sendSettingsFrameOnce(webSocket)
                } else {
                    // Cloud Relay emits BackendDevice.Connected first; wait for it, with a 1.5s fallback
                    // if the relay skips the Backend frame. Never send twice!
                    scope.launch {
                        delay(1500L)
                        if (!responseDeferred.isCompleted) {
                            sendSettingsFrameOnce(webSocket)
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val decoded = NanitProtoCodec.decodeSoundLightFrame(bytes.toByteArray())
                if (decoded.backendDeviceConnected == true) {
                    // Cloud relay confirmed physical Sound + Light unit is attached — send exactly once
                    sendSettingsFrameOnce(webSocket)
                }
                if (decoded.requestId == reqId || decoded.statusCode != null || decoded.settings != null) {
                    if (!responseDeferred.isCompleted) {
                        responseDeferred.complete(decoded)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!responseDeferred.isCompleted) {
                    val code = response?.code
                    if (code == 401 || code == 403) {
                        responseDeferred.completeExceptionally(
                            HttpUnauthorizedException(code, "Sound + Light auth expired (HTTP $code)")
                        )
                    } else {
                        responseDeferred.completeExceptionally(t)
                    }
                }
            }
        })

        try {
            val ack = withTimeoutOrNull(8500L) { responseDeferred.await() }
            val summary = if (payload.isOn == false) {
                "Sound + Light -> OFF"
            } else {
                val trackLabel = if (payload.noSound == true) "Muted" else (payload.track ?: "Rain")
                val volPct = ((payload.volume ?: 0.45f) * 100).roundToInt()
                val brightPct = ((payload.brightness ?: 0.20f) * 100).roundToInt()
                "Sound + Light -> ON ($trackLabel @ $volPct%, Light $brightPct%)"
            }
            if (ack != null && ack.statusCode != null && ack.statusCode !in 200..299 && ack.statusCode != 0) {
                throw IllegalStateException("Sound + Light error ${ack.statusCode}: ${ack.statusMessage}")
            }
            summary
        } finally {
            ws.close(1000, "Sound+Light routine complete")
        }
    }

    override suspend fun fetchSoundLightTelemetry(
        accessToken: String,
        speakerUid: String,
        lanIp: String
    ): Result<NurseryTelemetry> = withContext(Dispatchers.IO) {
        val activeToken = resolveValidToken(accessToken, forceRefresh = false)
        if ((speakerUid.isBlank() && lanIp.isBlank()) || activeToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Missing Sound + Light credentials"))
        }

        val firstAttempt = fetchSoundLightTelemetryOnce(activeToken, speakerUid, lanIp)
        if (firstAttempt.isFailure && firstAttempt.exceptionOrNull() is HttpUnauthorizedException &&
            tokenStore != null && authApi != null
        ) {
            val freshToken = authApi.ensureValidAccessToken(tokenStore, forceRefresh = true).getOrNull()
            if (!freshToken.isNullOrBlank()) {
                return@withContext fetchSoundLightTelemetryOnce(freshToken, speakerUid, lanIp)
            }
        }
        firstAttempt
    }

    private suspend fun fetchSoundLightTelemetryOnce(
        token: String,
        speakerUid: String,
        lanIp: String
    ): Result<NurseryTelemetry> = runCatching {
        val reqId = requestIdCounter.incrementAndGet()
        val sessionId = UUID.randomUUID().toString()
        val telemetryDeferred = CompletableDeferred<DecodedSoundLightFrame>()
        val getSent = AtomicBoolean(false)
        val isLocalSocket = lanIp.isNotBlank()

        val targetUrl = if (isLocalSocket) {
            "wss://${lanIp.trim()}:442"
        } else {
            "$remoteWsBaseUrl/speakers/$speakerUid/user_connect/"
        }

        val request = Request.Builder()
            .url(targetUrl)
            .header("Authorization", "token $token")
            .build()

        fun sendGetSettingsOnce(webSocket: WebSocket) {
            if (getSent.compareAndSet(false, true)) {
                val getBytes = NanitProtoCodec.encodeSoundLightGetSettings(reqId, sessionId)
                webSocket.send(getBytes.toByteString())
            }
        }

        val scope = CoroutineScope(Dispatchers.IO)

        val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (isLocalSocket) {
                    sendGetSettingsOnce(webSocket)
                } else {
                    scope.launch {
                        delay(1500L)
                        if (!telemetryDeferred.isCompleted) {
                            sendGetSettingsOnce(webSocket)
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val decoded = NanitProtoCodec.decodeSoundLightFrame(bytes.toByteArray())
                if (decoded.backendDeviceConnected == true) {
                    sendGetSettingsOnce(webSocket)
                }
                if (decoded.settings != null && !telemetryDeferred.isCompleted) {
                    telemetryDeferred.complete(decoded)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!telemetryDeferred.isCompleted) {
                    val code = response?.code
                    if (code == 401 || code == 403) {
                        telemetryDeferred.completeExceptionally(
                            HttpUnauthorizedException(code, "Sound + Light auth expired (HTTP $code)")
                        )
                    } else {
                        telemetryDeferred.completeExceptionally(t)
                    }
                }
            }
        })

        try {
            val frame = withTimeoutOrNull(6500L) { telemetryDeferred.await() }
            val s = frame?.settings
            NurseryTelemetry(
                soundLightOn = s?.isOn,
                activeTrack = if (s?.noSound == true) "Off" else s?.track,
                volumePercent = s?.volume?.let { (it * 100).roundToInt() },
                brightnessPercent = s?.brightness?.let { (it * 100).roundToInt() },
                temperatureCelsius = frame?.temperature,
                humidityPercent = frame?.humidity,
                lastUpdatedMillis = System.currentTimeMillis()
            )
        } finally {
            ws.close(1000, "Sound+Light telemetry complete")
        }
    }
}
