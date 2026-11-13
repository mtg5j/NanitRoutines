package com.droid.nanitroutines.data.lights

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.droid.nanitroutines.data.nanit.TokenStore
import com.droid.nanitroutines.domain.model.LightIntegrationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SmartLightRoutineClient(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val tokenStore: TokenStore = TokenStore(context),
    private val lutronLeapClient: LutronLeapClient = LutronLeapClient()
) : SmartLightController {

    companion object {
        val LUTRON_PACKAGES = listOf(
            "com.lutron.mmw",
            "com.lutron.caseta",
            "com.lutron.home"
        )
        const val GOOGLE_HOME_PACKAGE = "com.google.android.apps.chromecast.app"

        fun parseShortcutUriToIntent(uriString: String): Intent {
            val trimmed = uriString.trim()
            val intent = if (trimmed.startsWith("intent:", ignoreCase = true) ||
                trimmed.contains("#Intent;", ignoreCase = true)
            ) {
                Intent.parseUri(trimmed, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
            }
            // Guardrail: never allow a web search intent
            if (intent.action == "com.google.android.googlequicksearchbox.GOOGLE_SEARCH") {
                intent.action = Intent.ACTION_MAIN
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun triggerSmartLights(
        mode: LightIntegrationMode,
        turnOn: Boolean,
        commandPhrase: String,
        webhookUrl: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val stateLabel = if (turnOn) "ON" else "OFF"
            val effectivePhrase = commandPhrase.ifBlank {
                if (turnOn) "Turn on Kips Room Main Lights" else "Turn off Kips Room Main Lights"
            }

            // 1. Always check if the user linked a 1-tap Shortcut (from Lutron App or Google Home) first
            val shortcutUri = if (turnOn) {
                tokenStore.lightsOnShortcutUri
            } else {
                tokenStore.lightsOffShortcutUri
            }
            val shortcutName = if (turnOn) {
                tokenStore.lightsOnShortcutName.ifBlank { "Kips Lights ON Shortcut" }
            } else {
                tokenStore.lightsOffShortcutName.ifBlank { "Kips Lights OFF Shortcut" }
            }

            if (mode != LightIntegrationMode.DISABLED && shortcutUri.isNotBlank()) {
                val launched = executeLinkedShortcut(shortcutUri)
                if (launched) {
                    if (mode == LightIntegrationMode.BOTH && webhookUrl.isNotBlank()) {
                        executeWebhook(webhookUrl.trim(), turnOn, effectivePhrase)
                    }
                    return@runCatching "Ran shortcut: $shortcutName ($stateLabel)"
                }
            }

            when (mode) {
                LightIntegrationMode.DISABLED -> {
                    "Smart lights skipped (Disabled)"
                }

                LightIntegrationMode.LUTRON_LOCAL_BRIDGE -> {
                    val bridgeIp = tokenStore.lutronBridgeIp
                    val zoneHref = tokenStore.lutronZoneHref
                    if (bridgeIp.isBlank()) {
                        return@runCatching openPreferredLightApp().getOrThrow()
                    }
                    lutronLeapClient.setZoneLevel(bridgeIp, zoneHref, turnOn).getOrElse {
                        openPreferredLightApp().getOrThrow()
                    }
                }

                LightIntegrationMode.GOOGLE_HOME_INTENT -> {
                    // Launch the user's preferred smart home app (Lutron or Google Home) directly — never Google Search
                    openPreferredLightApp().getOrElse {
                        "Link a Lutron or Google Home shortcut in Setup to toggle Kips Room Main Lights ($stateLabel)"
                    }
                }

                LightIntegrationMode.WEBHOOK -> {
                    if (webhookUrl.isBlank()) {
                        throw IllegalArgumentException("Webhook URL is empty in Routine Settings")
                    }
                    executeWebhook(webhookUrl.trim(), turnOn, effectivePhrase)
                    "Kips Room Main Lights Webhook triggered ($stateLabel)"
                }

                LightIntegrationMode.BOTH -> {
                    if (webhookUrl.isNotBlank()) {
                        executeWebhook(webhookUrl.trim(), turnOn, effectivePhrase)
                    }
                    openPreferredLightApp().getOrDefault("Kips Room Main Lights triggered ($stateLabel)")
                }
            }
        }
    }

    override suspend fun fetchSmartLightStatus(): Result<SmartLightStatus?> {
        val bridgeIp = tokenStore.lutronBridgeIp
        if (bridgeIp.isBlank()) return Result.success(null)
        return lutronLeapClient.readZoneStatus(bridgeIp, tokenStore.lutronZoneHref).map { it }
    }

    override fun openLutronApp(): Result<String> = runCatching {
        val pm = context.packageManager
        for (pkg in LUTRON_PACKAGES) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return@runCatching "Opened Lutron App (Kips Room Main Lights)"
            }
        }
        // Fallback to Google Home if Lutron app is not installed on this device
        val ghIntent = pm.getLaunchIntentForPackage(GOOGLE_HOME_PACKAGE)
        if (ghIntent != null) {
            ghIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(ghIntent)
            return@runCatching "Opened Google Home App (Kips Room Main Lights)"
        }
        throw IllegalStateException("Neither Lutron nor Google Home app found on this device")
    }

    override fun openGoogleHomeApp(): Result<String> = runCatching {
        val pm = context.packageManager
        val googleHomeIntent = pm.getLaunchIntentForPackage(GOOGLE_HOME_PACKAGE)
        if (googleHomeIntent != null) {
            googleHomeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(googleHomeIntent)
            return@runCatching "Opened Google Home App (Kips Room Main Lights)"
        }
        // Fallback to Lutron app if Google Home is not installed
        for (pkg in LUTRON_PACKAGES) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return@runCatching "Opened Lutron App (Kips Room Main Lights)"
            }
        }
        throw IllegalStateException("Google Home app not installed on this device")
    }

    override fun openPreferredLightApp(): Result<String> {
        return if (tokenStore.preferredLightApp.equals("GOOGLE_HOME", ignoreCase = true)) {
            openGoogleHomeApp()
        } else {
            openLutronApp()
        }
    }

    private fun executeLinkedShortcut(shortcutUri: String): Boolean {
        return try {
            val intent = parseShortcutUriToIntent(shortcutUri)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun executeWebhook(url: String, turnOn: Boolean, commandPhrase: String) {
        val bodyJson = JSONObject()
            .put("state", if (turnOn) "on" else "off")
            .put("turn_on", turnOn)
            .put("room", "Kip's Room")
            .put("device", "Kips Room Main Lights")
            .put("command", commandPhrase)
            .put("integration", "lutron_google_home")
            .toString()

        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Webhook returned HTTP ${response.code}")
            }
        }
    }
}
