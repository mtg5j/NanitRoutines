package com.droid.nanitroutines.data.nanit

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.droid.nanitroutines.domain.model.LightIntegrationMode
import com.droid.nanitroutines.domain.model.NanitBabyDevice
import com.droid.nanitroutines.domain.model.RoutineConfig
import com.droid.nanitroutines.domain.model.RoutineType
import org.json.JSONObject
import java.util.Base64

class TokenStore(context: Context) {

    companion object {
        // Refresh proactively after 45 minutes (Nanit tokens expire at 60 minutes)
        const val PROACTIVE_REFRESH_AGE_MS = 45 * 60 * 1000L
        // Refresh if JWT `exp` is within 10 minutes
        const val JWT_EXPIRY_BUFFER_MS = 10 * 60 * 1000L

        fun extractJwtExpirationMillis(jwt: String): Long? {
            return try {
                val parts = jwt.trim().split(".")
                if (parts.size < 2) return null
                val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
                val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
                val expSeconds = json.optLong("exp", 0L)
                if (expSeconds > 0L) expSeconds * 1000L else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nanit_routines_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("nanit_routines_fallback_prefs", Context.MODE_PRIVATE)
    }

    var accessToken: String
        get() = prefs.getString("access_token", "").orEmpty()
        set(value) {
            val editor = prefs.edit().putString("access_token", value)
            if (value.isNotBlank()) {
                editor.putLong("access_token_ts", System.currentTimeMillis())
            } else {
                editor.remove("access_token_ts")
            }
            editor.apply()
        }

    var refreshToken: String
        get() = prefs.getString("refresh_token", "").orEmpty()
        set(value) = prefs.edit().putString("refresh_token", value).apply()

    var accessTokenTimestampMillis: Long
        get() = prefs.getLong("access_token_ts", 0L)
        set(value) = prefs.edit().putLong("access_token_ts", value).apply()

    fun saveTokens(newAccessToken: String, newRefreshToken: String, nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString("access_token", newAccessToken)
            .putString("refresh_token", newRefreshToken.ifBlank { refreshToken })
            .putLong("access_token_ts", nowMillis)
            .apply()
    }

    /**
     * Checks whether the current `accessToken` is missing, expired, or within 10-15 minutes of expiring.
     * Note: If `accessTokenTimestampMillis == 0L` (e.g., upgraded from v1.2.0) and the token has no JWT `exp`,
     * returns `true` so the app immediately refreshes it using `refreshToken`.
     */
    fun isAccessTokenExpiredOrExpiringSoon(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val token = accessToken
        if (token.isBlank()) return true

        val jwtExpMillis = extractJwtExpirationMillis(token)
        if (jwtExpMillis != null) {
            return nowMillis >= (jwtExpMillis - JWT_EXPIRY_BUFFER_MS)
        }

        val savedAt = accessTokenTimestampMillis
        if (savedAt <= 0L) return true
        return (nowMillis - savedAt) >= PROACTIVE_REFRESH_AGE_MS
    }

    fun getRemainingTokenMinutes(nowMillis: Long = System.currentTimeMillis()): Int? {
        val token = accessToken
        if (token.isBlank()) return null

        val jwtExpMillis = extractJwtExpirationMillis(token)
        if (jwtExpMillis != null) {
            val diff = jwtExpMillis - nowMillis
            return (diff / 60_000L).toInt().coerceAtLeast(0)
        }

        val savedAt = accessTokenTimestampMillis
        if (savedAt <= 0L) return 0
        val expiresAt = savedAt + 60 * 60_000L
        return ((expiresAt - nowMillis) / 60_000L).toInt().coerceAtLeast(0)
    }

    var email: String
        get() = prefs.getString("email", "").orEmpty()
        set(value) = prefs.edit().putString("email", value).apply()

    // Lutron Caséta Smart Bridge Local Wi-Fi settings
    var lutronBridgeIp: String
        get() = prefs.getString("lutron_bridge_ip", "").orEmpty()
        set(value) = prefs.edit().putString("lutron_bridge_ip", value.trim()).apply()

    var lutronZoneHref: String
        get() = prefs.getString("lutron_zone_href", "/zone/1").orEmpty().ifBlank { "/zone/1" }
        set(value) = prefs.edit().putString("lutron_zone_href", value.trim()).apply()

    var lutronZoneName: String
        get() = prefs.getString("lutron_zone_name", "Kips Room Main Lights").orEmpty()
            .ifBlank { "Kips Room Main Lights" }
        set(value) = prefs.edit().putString("lutron_zone_name", value.trim()).apply()

    // Linked Android Shortcuts (legacy compatibility)
    var lightsOffShortcutUri: String
        get() = prefs.getString("lights_off_shortcut_uri", "").orEmpty()
        set(value) = prefs.edit().putString("lights_off_shortcut_uri", value.trim()).apply()

    var lightsOffShortcutName: String
        get() = prefs.getString("lights_off_shortcut_name", "").orEmpty()
        set(value) = prefs.edit().putString("lights_off_shortcut_name", value.trim()).apply()

    var lightsOnShortcutUri: String
        get() = prefs.getString("lights_on_shortcut_uri", "").orEmpty()
        set(value) = prefs.edit().putString("lights_on_shortcut_uri", value.trim()).apply()

    var lightsOnShortcutName: String
        get() = prefs.getString("lights_on_shortcut_name", "").orEmpty()
        set(value) = prefs.edit().putString("lights_on_shortcut_name", value.trim()).apply()

    // Default to GOOGLE_HOME since user controls Kip's Room Main Lights in Google Home
    var preferredLightApp: String
        get() = prefs.getString("preferred_light_app", "GOOGLE_HOME").orEmpty().ifBlank { "GOOGLE_HOME" }
        set(value) = prefs.edit().putString("preferred_light_app", value.trim()).apply()

    fun saveSelectedDevice(device: NanitBabyDevice) {
        prefs.edit()
            .putString("baby_uid", device.babyUid)
            .putString("baby_name", device.babyName)
            .putString("camera_uid", device.cameraUid)
            .putString("speaker_uid", device.speakerUid.orEmpty())
            .putString("speaker_lan_ip", device.speakerLanIp)
            .apply()
    }

    fun getSelectedDevice(): NanitBabyDevice? {
        val camUid = prefs.getString("camera_uid", "").orEmpty()
        val spkUid = prefs.getString("speaker_uid", "").orEmpty()
        if (camUid.isBlank() && spkUid.isBlank()) return null
        val savedName = prefs.getString("baby_name", "Kip's Room").orEmpty()
        val normalizedName = if (savedName == "Nursery" || savedName.isBlank()) "Kip's Room" else savedName
        return NanitBabyDevice(
            babyUid = prefs.getString("baby_uid", "baby-1").orEmpty(),
            babyName = normalizedName,
            cameraUid = camUid,
            speakerUid = spkUid.ifBlank { null },
            speakerLanIp = prefs.getString("speaker_lan_ip", "").orEmpty()
        )
    }

    fun saveRoutineConfig(config: RoutineConfig) {
        val prefix = config.routineType.name.lowercase()
        prefs.edit()
            .putBoolean("${prefix}_cam_sleep", config.cameraSleepMode)
            .putBoolean("${prefix}_cam_nightlight", config.cameraNightLightOn)
            .putBoolean("${prefix}_sl_power", config.soundLightPowerOn)
            .putBoolean("${prefix}_sl_sound_en", config.soundEnabled)
            .putString("${prefix}_sl_track", config.soundTrack)
            .putFloat("${prefix}_sl_vol", config.soundVolume)
            .putFloat("${prefix}_sl_bright", config.lightBrightness)
            .putFloat("${prefix}_sl_hue", config.lightHue)
            .putFloat("${prefix}_sl_sat", config.lightSaturation)
            .putBoolean("${prefix}_sl_nocolor", config.lightNoColor)
            .putString("${prefix}_light_mode", config.smartLightMode.name)
            .putBoolean("${prefix}_lights_on", config.smartLightsTurnOn)
            .putString("${prefix}_light_phrase", config.smartLightCommandPhrase)
            .putString("${prefix}_light_webhook", config.smartLightWebhookUrl)
            .putBoolean("${prefix}_daybook", config.triggerBabyDaybook)
            .apply()
    }

    fun loadRoutineConfig(type: RoutineType): RoutineConfig {
        val defaults = when (type) {
            RoutineType.NAPTIME -> RoutineConfig.defaultNaptime()
            RoutineType.WAKEUP -> RoutineConfig.defaultWakeup()
        }
        val prefix = type.name.lowercase()
        if (!prefs.contains("${prefix}_cam_sleep")) return defaults

        val modeStr = prefs.getString("${prefix}_light_mode", defaults.smartLightMode.name).orEmpty()
        val mode = LightIntegrationMode.entries.find { it.name == modeStr } ?: defaults.smartLightMode

        val rawPhrase = prefs.getString("${prefix}_light_phrase", defaults.smartLightCommandPhrase).orEmpty()
        val migratedPhrase = when (rawPhrase) {
            "Turn off nursery lights" -> "Turn off Kips Room Main Lights"
            "Turn on nursery lights" -> "Turn on Kips Room Main Lights"
            "" -> defaults.smartLightCommandPhrase
            else -> rawPhrase
        }

        return RoutineConfig(
            routineType = type,
            cameraSleepMode = prefs.getBoolean("${prefix}_cam_sleep", defaults.cameraSleepMode),
            cameraNightLightOn = prefs.getBoolean("${prefix}_cam_nightlight", defaults.cameraNightLightOn),
            soundLightPowerOn = prefs.getBoolean("${prefix}_sl_power", defaults.soundLightPowerOn),
            soundEnabled = prefs.getBoolean("${prefix}_sl_sound_en", defaults.soundEnabled),
            soundTrack = prefs.getString("${prefix}_sl_track", defaults.soundTrack).orEmpty().ifBlank { "Rain" },
            soundVolume = prefs.getFloat("${prefix}_sl_vol", defaults.soundVolume),
            lightBrightness = prefs.getFloat("${prefix}_sl_bright", defaults.lightBrightness),
            lightHue = prefs.getFloat("${prefix}_sl_hue", defaults.lightHue),
            lightSaturation = prefs.getFloat("${prefix}_sl_sat", defaults.lightSaturation),
            lightNoColor = prefs.getBoolean("${prefix}_sl_nocolor", defaults.lightNoColor),
            smartLightMode = mode,
            smartLightsTurnOn = prefs.getBoolean("${prefix}_lights_on", defaults.smartLightsTurnOn),
            smartLightCommandPhrase = migratedPhrase,
            smartLightWebhookUrl = prefs.getString("${prefix}_light_webhook", defaults.smartLightWebhookUrl).orEmpty(),
            triggerBabyDaybook = prefs.getBoolean("${prefix}_daybook", defaults.triggerBabyDaybook)
        )
    }

    var lastRoutineSummary: String
        get() = prefs.getString("last_routine_summary", "Ready for Naptime or Wakeup").orEmpty()
        set(value) = prefs.edit().putString("last_routine_summary", value).apply()

    fun clearAuth() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("access_token_ts")
            .apply()
    }
}
