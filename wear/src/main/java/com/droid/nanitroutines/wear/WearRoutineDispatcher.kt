package com.droid.nanitroutines.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object WearRoutineDispatcher {

    private const val PREFS_NAME = "wear_routine_prefs"
    private const val KEY_LAST_STATUS = "last_status"
    const val PATH_RUN_ROUTINE = "/nanit_routines/run_routine"
    const val PATH_ROUTINE_RESULT = "/nanit_routines/routine_result"
    const val PATH_AUTH_SYNC = "/nanit_routines/auth_sync"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun getLastStatus(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_STATUS, "Ready • Tap to trigger").orEmpty()
    }

    fun setLastStatus(context: Context, status: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_STATUS, status)
            .apply()
    }

    fun saveSyncedAuth(
        context: Context,
        accessToken: String,
        refreshToken: String,
        cameraUid: String,
        speakerUid: String,
        soundTrack: String,
        soundVolume: Float,
        lightBrightness: Float
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("camera_uid", cameraUid)
            .putString("speaker_uid", speakerUid)
            .putString("sound_track", soundTrack)
            .putFloat("sound_volume", soundVolume)
            .putFloat("light_brightness", lightBrightness)
            .apply()
    }

    fun vibrateWatch(context: Context, doublePulse: Boolean = false) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (doublePulse) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 45L, 65L, 55L), -1))
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(55L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    fun triggerRoutineAsync(
        context: Context,
        routineType: String,
        onStatusChanged: ((String) -> Unit)? = null
    ) {
        val appCtx = context.applicationContext
        val isWakeup = routineType.equals("WAKEUP", ignoreCase = true)
        val label = if (isWakeup) "Wakeup" else "Naptime"

        // 1. Immediate haptic vibration on wrist + optimistic Tile status
        vibrateWatch(appCtx, doublePulse = false)
        val startingStatus = "⏳ Starting $label..."
        setLastStatus(appCtx, startingStatus)
        onStatusChanged?.invoke(startingStatus)

        scope.launch {
            try {
                // Path A: Send via Wearable Data Layer to paired Pixel Phone if connected
                val nodes = runCatching { Wearable.getNodeClient(appCtx).connectedNodes.await() }
                    .getOrDefault(emptyList())

                if (nodes.isNotEmpty()) {
                    val payload = routineType.uppercase().encodeToByteArray()
                    for (node in nodes) {
                        Wearable.getMessageClient(appCtx)
                            .sendMessage(node.id, PATH_RUN_ROUTINE, payload)
                            .await()
                    }
                    val sentStatus = "✓ $label Sent"
                    setLastStatus(appCtx, sentStatus)
                    onStatusChanged?.invoke(sentStatus)
                    TileService.getUpdater(appCtx).requestUpdate(KipsRoomTileService::class.java)
                    return@launch
                }

                // Path B (Standalone Fallback over Watch Wi-Fi/LTE if phone isn't nearby):
                val standaloneOk = executeStandaloneOnWatch(appCtx, isWakeup = isWakeup)
                val finalStatus = if (standaloneOk) {
                    vibrateWatch(appCtx, doublePulse = true)
                    "✓ $label Active"
                } else {
                    "⚠️ Open Phone App once"
                }
                setLastStatus(appCtx, finalStatus)
                onStatusChanged?.invoke(finalStatus)
                TileService.getUpdater(appCtx).requestUpdate(KipsRoomTileService::class.java)
            } catch (_: Exception) {
                val errStatus = "⚠️ Check Connection"
                setLastStatus(appCtx, errStatus)
                onStatusChanged?.invoke(errStatus)
                TileService.getUpdater(appCtx).requestUpdate(KipsRoomTileService::class.java)
            }
        }
    }

    private suspend fun executeStandaloneOnWatch(context: Context, isWakeup: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", "").orEmpty()
        var accessToken = prefs.getString("access_token", "").orEmpty()
        val cameraUid = prefs.getString("camera_uid", "").orEmpty()
        val speakerUid = prefs.getString("speaker_uid", "").orEmpty()

        if (refreshToken.isNotBlank()) {
            runCatching {
                val body = JSONObject().put("refresh_token", refreshToken).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url("https://api.nanit.com/tokens/refresh")
                    .header("nanit-api-version", "1")
                    .post(body)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val freshAccess = json.optString("access_token")
                        if (freshAccess.isNotBlank()) {
                            accessToken = freshAccess
                            prefs.edit().putString("access_token", freshAccess).apply()
                        }
                    }
                }
            }
        }

        if (accessToken.isBlank() || (cameraUid.isBlank() && speakerUid.isBlank())) return false

        // Trigger Camera WebSocket if cameraUid is present
        if (cameraUid.isNotBlank()) {
            runCatching {
                val done = CompletableDeferred<Unit>()
                val req = Request.Builder()
                    .url("wss://api.nanit.com/focus/cameras/$cameraUid/user_connect")
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                val ws = httpClient.newWebSocket(req, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        // Field 1 = 1 (REQUEST), Field 2 = Request { id=101, type=5 (PUT_SETTINGS), settings(5) = { sleepMode(11) = isWakeup } }
                        val settings = ByteArrayOutputStream().apply {
                            write((11 shl 3) or 0)
                            write(if (isWakeup) 1 else 0)
                        }.toByteArray()
                        val reqBytes = ByteArrayOutputStream().apply {
                            write((1 shl 3) or 0); write(101)
                            write((2 shl 3) or 0); write(5)
                            write((5 shl 3) or 2); write(settings.size); write(settings)
                        }.toByteArray()
                        val msgBytes = ByteArrayOutputStream().apply {
                            write((1 shl 3) or 0); write(1)
                            write((2 shl 3) or 2); write(reqBytes.size); write(reqBytes)
                        }.toByteArray()
                        webSocket.send(msgBytes.toByteString())
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        if (!done.isCompleted) done.complete(Unit)
                    }
                })
                withTimeoutOrNull(3500L) { done.await() }
                ws.close(1000, "Done")
            }
        }
        return true
    }
}
