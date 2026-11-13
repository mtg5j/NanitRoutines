package com.droid.nanitroutines.wear

import android.content.Context
import com.droid.nanitroutines.AppContainer
import com.droid.nanitroutines.domain.model.RoutineType
import com.droid.nanitroutines.widget.RoutineWidgetProvider
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class PhoneWearListenerService : WearableListenerService() {

    companion object {
        const val PATH_RUN_ROUTINE = "/nanit_routines/run_routine"
        const val PATH_ROUTINE_RESULT = "/nanit_routines/routine_result"
        const val PATH_AUTH_SYNC = "/nanit_routines/auth_sync"

        /**
         * Pushes the phone's authenticated Nanit token & device UIDs to the paired Pixel Watch
         * so the watch can also run standalone over Wi-Fi/LTE even if the phone is in another room.
         */
        suspend fun syncCredentialsToWatch(context: Context) {
            try {
                val appCtx = context.applicationContext
                val container = AppContainer(appCtx)
                val validToken = container.authApi.ensureValidAccessToken(container.tokenStore)
                    .getOrDefault(container.tokenStore.accessToken)
                val device = container.tokenStore.getSelectedDevice()
                val napCfg = container.tokenStore.loadRoutineConfig(RoutineType.NAPTIME)

                val req = PutDataMapRequest.create(PATH_AUTH_SYNC).apply {
                    dataMap.putString("access_token", validToken)
                    dataMap.putString("refresh_token", container.tokenStore.refreshToken)
                    dataMap.putString("camera_uid", device?.cameraUid.orEmpty())
                    dataMap.putString("speaker_uid", device?.speakerUid.orEmpty())
                    dataMap.putString("sound_track", napCfg.soundTrack)
                    dataMap.putFloat("sound_volume", napCfg.soundVolume)
                    dataMap.putFloat("light_brightness", napCfg.lightBrightness)
                    dataMap.putLong("synced_at", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                Wearable.getDataClient(appCtx).putDataItem(req).await()

                val jsonPayload = JSONObject()
                    .put("access_token", validToken)
                    .put("refresh_token", container.tokenStore.refreshToken)
                    .put("camera_uid", device?.cameraUid.orEmpty())
                    .put("speaker_uid", device?.speakerUid.orEmpty())
                    .put("sound_track", napCfg.soundTrack)
                    .put("sound_volume", napCfg.soundVolume.toDouble())
                    .put("light_brightness", napCfg.lightBrightness.toDouble())
                    .toString()
                    .encodeToByteArray()

                val nodes = Wearable.getNodeClient(appCtx).connectedNodes.await()
                for (node in nodes) {
                    Wearable.getMessageClient(appCtx)
                        .sendMessage(node.id, PATH_AUTH_SYNC, jsonPayload)
                        .await()
                }
            } catch (_: Exception) {
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (messageEvent.path != PATH_RUN_ROUTINE) return

        val payloadStr = messageEvent.data.decodeToString().trim()
        val routineType = if (payloadStr.equals("WAKEUP", ignoreCase = true)) {
            RoutineType.WAKEUP
        } else {
            RoutineType.NAPTIME
        }
        val sourceNodeId = messageEvent.sourceNodeId

        serviceScope.launch {
            val container = AppContainer(applicationContext)
            val config = container.tokenStore.loadRoutineConfig(routineType)

            val validToken = container.authApi.ensureValidAccessToken(container.tokenStore)
                .getOrDefault(container.tokenStore.accessToken)

            var device = container.tokenStore.getSelectedDevice()
            if ((device == null || device.cameraUid.isBlank()) && validToken.isNotBlank()) {
                val discovered = container.authApi.fetchBabiesAndDevices(validToken).getOrNull()?.firstOrNull()
                if (discovered != null) {
                    container.tokenStore.saveSelectedDevice(discovered)
                    device = discovered
                }
            }

            val report = container.executeRoutineUseCase.execute(
                config = config,
                accessToken = validToken,
                device = device
            )

            val summary = if (report.overallSuccess) {
                "✓ ${routineType.displayName} Active"
            } else {
                "✓ ${routineType.displayName} Sent"
            }
            container.tokenStore.lastRoutineSummary = "$summary (from Watch)"
            RoutineWidgetProvider.refreshAllWidgets(applicationContext)

            // Sync latest token to watch as well
            syncCredentialsToWatch(applicationContext)

            try {
                Wearable.getMessageClient(applicationContext).sendMessage(
                    sourceNodeId,
                    PATH_ROUTINE_RESULT,
                    summary.encodeToByteArray()
                ).await()
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
