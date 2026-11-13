package com.droid.nanitroutines.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

class WearResultListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        for (event in dataEvents) {
            if (event.dataItem.uri.path == WearRoutineDispatcher.PATH_AUTH_SYNC) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                WearRoutineDispatcher.saveSyncedAuth(
                    context = applicationContext,
                    accessToken = map.getString("access_token").orEmpty(),
                    refreshToken = map.getString("refresh_token").orEmpty(),
                    cameraUid = map.getString("camera_uid").orEmpty(),
                    speakerUid = map.getString("speaker_uid").orEmpty(),
                    soundTrack = map.getString("sound_track") ?: "Rain",
                    soundVolume = map.getFloat("sound_volume", 0.45f),
                    lightBrightness = map.getFloat("light_brightness", 0.20f)
                )
                TileService.getUpdater(applicationContext).requestUpdate(KipsRoomTileService::class.java)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            WearRoutineDispatcher.PATH_ROUTINE_RESULT -> {
                val resultText = messageEvent.data.decodeToString().trim()
                    .takeIf { it.isNotBlank() }
                    ?: "✓ Routine Complete"

                WearRoutineDispatcher.setLastStatus(applicationContext, resultText)
                WearRoutineDispatcher.vibrateWatch(applicationContext, doublePulse = true)
                TileService.getUpdater(applicationContext).requestUpdate(KipsRoomTileService::class.java)
            }
            WearRoutineDispatcher.PATH_AUTH_SYNC -> {
                try {
                    val json = JSONObject(messageEvent.data.decodeToString())
                    WearRoutineDispatcher.saveSyncedAuth(
                        context = applicationContext,
                        accessToken = json.optString("access_token"),
                        refreshToken = json.optString("refresh_token"),
                        cameraUid = json.optString("camera_uid"),
                        speakerUid = json.optString("speaker_uid"),
                        soundTrack = json.optString("sound_track", "Rain"),
                        soundVolume = json.optDouble("sound_volume", 0.45).toFloat(),
                        lightBrightness = json.optDouble("light_brightness", 0.20).toFloat()
                    )
                    TileService.getUpdater(applicationContext).requestUpdate(KipsRoomTileService::class.java)
                } catch (_: Exception) {
                }
            }
        }
    }
}
