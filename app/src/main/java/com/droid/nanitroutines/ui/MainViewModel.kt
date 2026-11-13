package com.droid.nanitroutines.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droid.nanitroutines.data.lights.BabyDaybookLauncher
import com.droid.nanitroutines.data.lights.LutronLeapClient
import com.droid.nanitroutines.data.lights.SmartLightController
import com.droid.nanitroutines.data.nanit.CameraController
import com.droid.nanitroutines.data.nanit.NanitAuthApi
import com.droid.nanitroutines.data.nanit.NanitLoginResult
import com.droid.nanitroutines.data.nanit.SoundLightController
import com.droid.nanitroutines.data.nanit.SoundLightSettingsPayload
import com.droid.nanitroutines.data.nanit.TokenStore
import com.droid.nanitroutines.domain.model.LightIntegrationMode
import com.droid.nanitroutines.domain.model.LutronZoneInfo
import com.droid.nanitroutines.domain.model.NanitBabyDevice
import com.droid.nanitroutines.domain.model.NurseryTelemetry
import com.droid.nanitroutines.domain.model.RoutineConfig
import com.droid.nanitroutines.domain.model.RoutineExecutionReport
import com.droid.nanitroutines.domain.model.RoutineStepResult
import com.droid.nanitroutines.domain.model.RoutineType
import com.droid.nanitroutines.domain.usecase.ExecuteRoutineUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MainUiState(
    val isAuthenticated: Boolean = false,
    val isTokenHealthy: Boolean = false,
    val tokenHealthLabel: String = "Sign in to Nanit in Settings",
    val email: String = "",
    val pendingPassword: String = "",
    val pendingMfaToken: String? = null,
    val pendingMfaChannel: String = "email",
    val isAuthenticating: Boolean = false,
    val authError: String? = null,
    val babies: List<NanitBabyDevice> = emptyList(),
    val selectedDevice: NanitBabyDevice? = null,
    val lutronBridgeIp: String = "",
    val lutronZoneHref: String = "/zone/1",
    val lutronZoneName: String = "Kips Room Main Lights",
    val discoveredLutronZones: List<LutronZoneInfo> = emptyList(),
    val lightsOffShortcutUri: String = "",
    val lightsOffShortcutName: String = "",
    val lightsOnShortcutUri: String = "",
    val lightsOnShortcutName: String = "",
    val preferredLightApp: String = "GOOGLE_HOME",
    val naptimeConfig: RoutineConfig = RoutineConfig.defaultNaptime(),
    val wakeupConfig: RoutineConfig = RoutineConfig.defaultWakeup(),
    val telemetry: NurseryTelemetry = NurseryTelemetry(),
    val isRefreshingTelemetry: Boolean = false,
    val isExecutingRoutine: Boolean = false,
    val activeRoutineType: RoutineType? = null,
    val activeSteps: List<RoutineStepResult> = emptyList(),
    val lastExecutionReport: RoutineExecutionReport? = null,
    val statusBanner: String = "Ready for 1-tap Naptime or Wakeup",
    val isBabyDaybookInstalled: Boolean = false,
    // Visual button feedback state
    val activeActionKey: String? = null,
    val completedActionKey: String? = null,
    val completedActionSuccess: Boolean = true,
    val toastMessage: String? = null,
    val toastId: Long = 0L
)

class MainViewModel(
    private val tokenStore: TokenStore,
    private val authApi: NanitAuthApi,
    private val cameraController: CameraController,
    private val soundLightController: SoundLightController,
    private val smartLightController: SmartLightController,
    private val babyDaybookLauncher: BabyDaybookLauncher,
    private val executeRoutineUseCase: ExecuteRoutineUseCase,
    private val lutronLeapClient: LutronLeapClient = LutronLeapClient()
) : ViewModel() {

    companion object {
        // 2-minute foreground-only refresh interval (zero background drain)
        const val FOREGROUND_REFRESH_INTERVAL_MS = 120_000L
    }

    private var foregroundSyncJob: Job? = null
    private var feedbackResetJob: Job? = null

    private fun computeTokenHealthLabel(): Pair<Boolean, String> {
        val hasAccess = tokenStore.accessToken.isNotBlank()
        val hasRefresh = tokenStore.refreshToken.isNotBlank()
        if (!hasAccess && !hasRefresh) {
            return false to "Not signed in • Go to Settings to connect Nanit"
        }
        val remMins = tokenStore.getRemainingTokenMinutes()
        return if (remMins != null && remMins > 0) {
            true to "Nanit Connected • Auto-refresh active (${remMins}m left on current token)"
        } else if (hasRefresh) {
            true to "Nanit Connected • Auto-refreshes automatically on next tap"
        } else {
            true to "Nanit Connected"
        }
    }

    private val initialHealth = computeTokenHealthLabel()

    private val _uiState = MutableStateFlow(
        MainUiState(
            isAuthenticated = tokenStore.accessToken.isNotBlank() || tokenStore.refreshToken.isNotBlank(),
            isTokenHealthy = initialHealth.first,
            tokenHealthLabel = initialHealth.second,
            email = tokenStore.email,
            selectedDevice = tokenStore.getSelectedDevice(),
            lutronBridgeIp = tokenStore.lutronBridgeIp,
            lutronZoneHref = tokenStore.lutronZoneHref,
            lutronZoneName = tokenStore.lutronZoneName,
            lightsOffShortcutUri = tokenStore.lightsOffShortcutUri,
            lightsOffShortcutName = tokenStore.lightsOffShortcutName,
            lightsOnShortcutUri = tokenStore.lightsOnShortcutUri,
            lightsOnShortcutName = tokenStore.lightsOnShortcutName,
            preferredLightApp = tokenStore.preferredLightApp,
            naptimeConfig = tokenStore.loadRoutineConfig(RoutineType.NAPTIME),
            wakeupConfig = tokenStore.loadRoutineConfig(RoutineType.WAKEUP),
            statusBanner = tokenStore.lastRoutineSummary,
            isBabyDaybookInstalled = babyDaybookLauncher.isBabyDaybookInstalled()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private fun recordButtonCompletion(actionKey: String, success: Boolean, message: String) {
        val health = computeTokenHealthLabel()
        _uiState.update {
            it.copy(
                activeActionKey = null,
                completedActionKey = actionKey,
                completedActionSuccess = success,
                statusBanner = message,
                isTokenHealthy = health.first,
                tokenHealthLabel = health.second,
                toastMessage = message,
                toastId = System.currentTimeMillis()
            )
        }
        feedbackResetJob?.cancel()
        feedbackResetJob = viewModelScope.launch {
            delay(2600L)
            _uiState.update { state ->
                if (state.completedActionKey == actionKey) {
                    state.copy(completedActionKey = null)
                } else {
                    state
                }
            }
        }
    }

    /**
     * Called from Activity.onResume(). Immediately checks/refreshes token if needed,
     * syncs live device status, and refreshes every 2 minutes ONLY while the app stays open.
     */
    fun onAppForegrounded() {
        foregroundSyncJob?.cancel()
        foregroundSyncJob = viewModelScope.launch {
            while (isActive) {
                refreshTelemetry()
                delay(FOREGROUND_REFRESH_INTERVAL_MS)
            }
        }
    }

    /**
     * Called from Activity.onPause(). Stops all polling immediately for 0% background battery drain.
     */
    fun onAppBackgrounded() {
        foregroundSyncJob?.cancel()
        foregroundSyncJob = null
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(authError = "Please enter your Nanit email and password") }
            return
        }
        tokenStore.email = email.trim()
        _uiState.update {
            it.copy(
                email = email.trim(),
                pendingPassword = password,
                isAuthenticating = true,
                activeActionKey = "login",
                authError = null
            )
        }

        viewModelScope.launch {
            when (val res = authApi.login(email.trim(), password)) {
                is NanitLoginResult.Authenticated -> {
                    onAuthenticationSuccess(res.accessToken, res.refreshToken)
                }
                is NanitLoginResult.MfaRequired -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            activeActionKey = null,
                            pendingMfaToken = res.mfaToken,
                            pendingMfaChannel = res.channel,
                            statusBanner = "2FA code sent via ${res.channel}. Enter code to finish signing in.",
                            toastMessage = "2FA code sent via ${res.channel}",
                            toastId = System.currentTimeMillis()
                        )
                    }
                }
                is NanitLoginResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            activeActionKey = null,
                            authError = res.message
                        )
                    }
                }
            }
        }
    }

    fun verifyMfa(mfaCode: String) {
        val current = _uiState.value
        val mfaToken = current.pendingMfaToken ?: return
        if (mfaCode.isBlank()) {
            _uiState.update { it.copy(authError = "Enter the 2FA code sent to your ${current.pendingMfaChannel}") }
            return
        }
        _uiState.update { it.copy(isAuthenticating = true, activeActionKey = "verify_mfa", authError = null) }

        viewModelScope.launch {
            val res = authApi.verifyMfa(
                email = current.email,
                password = current.pendingPassword,
                mfaToken = mfaToken,
                mfaCode = mfaCode.trim(),
                channel = current.pendingMfaChannel
            )
            when (res) {
                is NanitLoginResult.Authenticated -> {
                    onAuthenticationSuccess(res.accessToken, res.refreshToken)
                }
                is NanitLoginResult.MfaRequired -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            activeActionKey = null,
                            pendingMfaToken = res.mfaToken
                        )
                    }
                }
                is NanitLoginResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            activeActionKey = null,
                            authError = res.message
                        )
                    }
                }
            }
        }
    }

    private suspend fun onAuthenticationSuccess(accessToken: String, refreshToken: String) {
        tokenStore.saveTokens(accessToken, refreshToken)
        val babiesRes = authApi.fetchBabiesAndDevices(accessToken)
        val babies = babiesRes.getOrDefault(emptyList())
        val firstDevice = babies.firstOrNull() ?: tokenStore.getSelectedDevice()
        if (firstDevice != null) {
            tokenStore.saveSelectedDevice(firstDevice)
        }
        val health = computeTokenHealthLabel()
        val msg = if (firstDevice != null) {
            "✓ Connected to ${firstDevice.babyName} (Cam & Sound+Light ready)"
        } else {
            "✓ Signed in to Nanit Cloud"
        }

        _uiState.update {
            it.copy(
                isAuthenticated = true,
                isTokenHealthy = health.first,
                tokenHealthLabel = health.second,
                isAuthenticating = false,
                activeActionKey = null,
                completedActionKey = "login",
                completedActionSuccess = true,
                pendingPassword = "",
                pendingMfaToken = null,
                authError = null,
                babies = babies,
                selectedDevice = firstDevice,
                statusBanner = msg,
                toastMessage = msg,
                toastId = System.currentTimeMillis()
            )
        }
        refreshTelemetry()
    }

    /**
     * 1-tap button in UI to explicitly verify/refresh the Nanit token and re-sync baby/device UIDs.
     */
    fun verifyAndRefreshNanitConnection() {
        _uiState.update {
            it.copy(
                activeActionKey = "token_refresh",
                statusBanner = "Refreshing Nanit token & syncing devices..."
            )
        }
        viewModelScope.launch {
            val tokenRes = authApi.ensureValidAccessToken(tokenStore, forceRefresh = true)
            tokenRes.onSuccess { validToken ->
                val babiesRes = authApi.fetchBabiesAndDevices(validToken)
                val babies = babiesRes.getOrDefault(emptyList())
                val existing = tokenStore.getSelectedDevice()
                val updatedDevice = babies.firstOrNull()?.let { discovered ->
                    discovered.copy(speakerLanIp = existing?.speakerLanIp.orEmpty())
                } ?: existing
                if (updatedDevice != null) {
                    tokenStore.saveSelectedDevice(updatedDevice)
                }
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        babies = if (babies.isNotEmpty()) babies else it.babies,
                        selectedDevice = updatedDevice ?: it.selectedDevice
                    )
                }
                recordButtonCompletion(
                    actionKey = "token_refresh",
                    success = true,
                    message = "✓ Nanit token refreshed & devices verified!"
                )
                refreshTelemetry()
            }.onFailure { err ->
                recordButtonCompletion(
                    actionKey = "token_refresh",
                    success = false,
                    message = err.message ?: "Could not refresh token — sign in again in Settings"
                )
            }
        }
    }

    fun saveManualDeviceConfig(
        babyName: String,
        cameraUid: String,
        speakerUid: String,
        speakerLanIp: String
    ) {
        val device = NanitBabyDevice(
            babyUid = _uiState.value.selectedDevice?.babyUid ?: "manual-baby",
            babyName = babyName.trim().ifBlank { "Kip's Room" },
            cameraUid = cameraUid.trim(),
            speakerUid = speakerUid.trim().ifBlank { null },
            speakerLanIp = speakerLanIp.trim()
        )
        tokenStore.saveSelectedDevice(device)
        _uiState.update { it.copy(selectedDevice = device) }
        recordButtonCompletion(
            actionKey = "save_device_uids",
            success = true,
            message = "✓ Saved Nanit device UIDs for ${device.babyName}"
        )
        refreshTelemetry()
    }

    fun saveLutronBridgeConfig(
        bridgeIp: String,
        zoneHref: String,
        zoneName: String
    ) {
        tokenStore.lutronBridgeIp = bridgeIp.trim()
        tokenStore.lutronZoneHref = zoneHref.trim().ifBlank { "/zone/1" }
        tokenStore.lutronZoneName = zoneName.trim().ifBlank { "Kips Room Main Lights" }

        if (bridgeIp.isNotBlank()) {
            val updatedNap = _uiState.value.naptimeConfig.copy(
                smartLightMode = LightIntegrationMode.LUTRON_LOCAL_BRIDGE
            )
            val updatedWake = _uiState.value.wakeupConfig.copy(
                smartLightMode = LightIntegrationMode.LUTRON_LOCAL_BRIDGE
            )
            tokenStore.saveRoutineConfig(updatedNap)
            tokenStore.saveRoutineConfig(updatedWake)
            _uiState.update {
                it.copy(
                    lutronBridgeIp = tokenStore.lutronBridgeIp,
                    lutronZoneHref = tokenStore.lutronZoneHref,
                    lutronZoneName = tokenStore.lutronZoneName,
                    naptimeConfig = updatedNap,
                    wakeupConfig = updatedWake,
                    statusBanner = "Saved Lutron Bridge (${tokenStore.lutronZoneName})"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    lutronBridgeIp = "",
                    lutronZoneHref = tokenStore.lutronZoneHref,
                    lutronZoneName = tokenStore.lutronZoneName,
                    statusBanner = "Updated Kips Room Main Lights settings"
                )
            }
        }
        refreshTelemetry()
    }

    fun discoverLutronZones(bridgeIp: String) {
        tokenStore.lutronBridgeIp = bridgeIp.trim()
        _uiState.update {
            it.copy(
                lutronBridgeIp = bridgeIp.trim(),
                statusBanner = "Querying Lutron Bridge at ${bridgeIp.trim()}..."
            )
        }
        viewModelScope.launch {
            val res = lutronLeapClient.discoverZones(bridgeIp.trim())
            res.onSuccess { zones ->
                val matched = zones.firstOrNull {
                    it.name.contains("Kip", ignoreCase = true) ||
                        it.name.contains("Main", ignoreCase = true)
                } ?: zones.firstOrNull()

                if (matched != null) {
                    tokenStore.lutronZoneHref = matched.zoneHref
                    tokenStore.lutronZoneName = matched.name
                }
                _uiState.update {
                    it.copy(
                        discoveredLutronZones = zones,
                        lutronZoneHref = matched?.zoneHref ?: it.lutronZoneHref,
                        lutronZoneName = matched?.name ?: it.lutronZoneName,
                        statusBanner = if (zones.isNotEmpty()) {
                            "Found ${zones.size} Lutron zones • Selected ${matched?.name}"
                        } else {
                            "Connected to Lutron Bridge"
                        }
                    )
                }
                refreshTelemetry()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        statusBanner = "Lutron Bridge check: ${err.message ?: "Verify IP on local Wi-Fi"}"
                    )
                }
            }
        }
    }

    fun selectBabyDevice(device: NanitBabyDevice) {
        tokenStore.saveSelectedDevice(device)
        _uiState.update {
            it.copy(
                selectedDevice = device,
                statusBanner = "Selected ${device.babyName}"
            )
        }
        refreshTelemetry()
    }

    fun updateRoutineConfig(config: RoutineConfig) {
        tokenStore.saveRoutineConfig(config)
        _uiState.update { state ->
            when (config.routineType) {
                RoutineType.NAPTIME -> state.copy(naptimeConfig = config)
                RoutineType.WAKEUP -> state.copy(wakeupConfig = config)
            }
        }
        recordButtonCompletion(
            actionKey = "save_routine_${config.routineType.name.lowercase()}",
            success = true,
            message = "✓ Saved ${config.routineType.displayName} preset"
        )
    }

    fun runRoutine(routineType: RoutineType) {
        val state = _uiState.value
        val config = when (routineType) {
            RoutineType.NAPTIME -> state.naptimeConfig
            RoutineType.WAKEUP -> state.wakeupConfig
        }
        val actionKey = "routine_${routineType.name.lowercase()}"

        _uiState.update {
            it.copy(
                isExecutingRoutine = true,
                activeRoutineType = routineType,
                activeActionKey = actionKey,
                completedActionKey = null,
                statusBanner = "Running ${routineType.displayName} routine...",
                toastMessage = "Starting ${routineType.displayName}...",
                toastId = System.currentTimeMillis()
            )
        }

        viewModelScope.launch {
            // 1. Automatically ensure a fresh Nanit access_token before running
            val validToken = authApi.ensureValidAccessToken(tokenStore)
                .getOrDefault(tokenStore.accessToken)

            // 2. Ensure device UIDs are populated
            var targetDevice = state.selectedDevice ?: tokenStore.getSelectedDevice()
            if ((targetDevice == null || targetDevice.cameraUid.isBlank()) && validToken.isNotBlank()) {
                val discovered = authApi.fetchBabiesAndDevices(validToken).getOrNull()?.firstOrNull()
                if (discovered != null) {
                    tokenStore.saveSelectedDevice(discovered)
                    targetDevice = discovered
                    _uiState.update { it.copy(selectedDevice = discovered) }
                }
            }

            // 3. Execute all routine steps concurrently
            val report = executeRoutineUseCase.execute(
                config = config,
                accessToken = validToken,
                device = targetDevice,
                onStepProgress = { steps ->
                    _uiState.update { it.copy(activeSteps = steps) }
                }
            )

            val summary = if (report.overallSuccess) {
                "✓ ${routineType.displayName} complete • Kip's Room updated"
            } else {
                "${routineType.displayName} finished • Check step details below"
            }
            tokenStore.lastRoutineSummary = summary
            val health = computeTokenHealthLabel()

            _uiState.update { current ->
                current.copy(
                    isExecutingRoutine = false,
                    activeRoutineType = routineType,
                    activeSteps = report.steps,
                    lastExecutionReport = report,
                    isTokenHealthy = health.first,
                    tokenHealthLabel = health.second,
                    telemetry = current.telemetry.copy(
                        cameraAwake = !config.cameraSleepMode,
                        cameraNightLightOn = config.cameraNightLightOn,
                        soundLightOn = config.soundLightPowerOn,
                        activeTrack = if (config.soundLightPowerOn && config.soundEnabled) config.soundTrack else "Off",
                        volumePercent = (config.soundVolume * 100).toInt(),
                        brightnessPercent = if (config.soundLightPowerOn) (config.lightBrightness * 100).toInt() else 0,
                        lutronLightsOn = config.smartLightsTurnOn,
                        lutronBrightnessPercent = if (config.smartLightsTurnOn) 100 else 0,
                        lastUpdatedMillis = System.currentTimeMillis()
                    )
                )
            }
            recordButtonCompletion(
                actionKey = actionKey,
                success = report.overallSuccess,
                message = summary
            )
        }
    }

    fun quickToggleCamera(awake: Boolean, nightLightOn: Boolean) {
        val actionKey = if (awake) "cam_awake" else "cam_standby"
        _uiState.update {
            it.copy(
                activeActionKey = actionKey,
                completedActionKey = null,
                statusBanner = if (awake) "Waking Nanit Camera ON..." else "Putting Nanit Camera in Standby..."
            )
        }
        viewModelScope.launch {
            val validToken = authApi.ensureValidAccessToken(tokenStore)
                .getOrDefault(tokenStore.accessToken)
            val device = _uiState.value.selectedDevice ?: tokenStore.getSelectedDevice()
            val camUid = device?.cameraUid.orEmpty()

            val res = cameraController.applyCameraRoutine(
                accessToken = validToken,
                cameraUid = camUid,
                sleepMode = !awake,
                nightLightOn = nightLightOn
            )
            _uiState.update { state ->
                state.copy(
                    telemetry = if (res.isSuccess) {
                        state.telemetry.copy(
                            cameraAwake = awake,
                            cameraNightLightOn = nightLightOn,
                            lastUpdatedMillis = System.currentTimeMillis()
                        )
                    } else {
                        state.telemetry
                    }
                )
            }
            recordButtonCompletion(
                actionKey = actionKey,
                success = res.isSuccess,
                message = res.fold(
                    onSuccess = { "✓ $it" },
                    onFailure = { "⚠️ ${it.message ?: "Camera command failed"}" }
                )
            )
        }
    }

    fun quickControlSoundLight(
        isOn: Boolean,
        track: String,
        volume: Float,
        brightness: Float
    ) {
        val actionKey = if (isOn) "sl_on" else "sl_off"
        _uiState.update {
            it.copy(
                activeActionKey = actionKey,
                completedActionKey = null,
                statusBanner = if (isOn) "Turning Sound + Light ON ($track)..." else "Turning Sound + Light OFF..."
            )
        }
        viewModelScope.launch {
            val validToken = authApi.ensureValidAccessToken(tokenStore)
                .getOrDefault(tokenStore.accessToken)
            val device = _uiState.value.selectedDevice ?: tokenStore.getSelectedDevice()
            val spkUid = device?.speakerUid.orEmpty()
            val lanIp = device?.speakerLanIp.orEmpty()

            val payload = if (isOn) {
                SoundLightSettingsPayload(
                    isOn = true,
                    noSound = false,
                    track = track,
                    volume = volume,
                    brightness = brightness,
                    noColor = false,
                    hue = 0.08f,
                    saturation = 0.75f
                )
            } else {
                SoundLightSettingsPayload(
                    isOn = false
                )
            }
            val res = soundLightController.applySoundLightRoutine(
                accessToken = validToken,
                speakerUid = spkUid,
                lanIp = lanIp,
                payload = payload
            )
            _uiState.update { state ->
                state.copy(
                    telemetry = if (res.isSuccess) {
                        state.telemetry.copy(
                            soundLightOn = isOn,
                            activeTrack = if (isOn) track else "Off",
                            volumePercent = (volume * 100).toInt(),
                            brightnessPercent = if (isOn) (brightness * 100).toInt() else 0,
                            lastUpdatedMillis = System.currentTimeMillis()
                        )
                    } else {
                        state.telemetry
                    }
                )
            }
            recordButtonCompletion(
                actionKey = actionKey,
                success = res.isSuccess,
                message = res.fold(
                    onSuccess = { "✓ $it" },
                    onFailure = { "⚠️ ${it.message ?: "Sound + Light command failed"}" }
                )
            )
        }
    }

    fun quickTriggerLutronLights(turnOn: Boolean) {
        val actionKey = if (turnOn) "lights_on" else "lights_off"
        val cfg = if (turnOn) _uiState.value.wakeupConfig else _uiState.value.naptimeConfig
        _uiState.update { it.copy(activeActionKey = actionKey, completedActionKey = null) }
        viewModelScope.launch {
            val res = smartLightController.triggerSmartLights(
                mode = if (cfg.smartLightMode == LightIntegrationMode.DISABLED) {
                    LightIntegrationMode.GOOGLE_HOME_INTENT
                } else {
                    cfg.smartLightMode
                },
                turnOn = turnOn,
                commandPhrase = cfg.smartLightCommandPhrase,
                webhookUrl = cfg.smartLightWebhookUrl
            )
            _uiState.update { state ->
                state.copy(
                    telemetry = state.telemetry.copy(
                        lutronLightsOn = turnOn,
                        lutronBrightnessPercent = if (turnOn) 100 else 0,
                        lastUpdatedMillis = System.currentTimeMillis()
                    )
                )
            }
            recordButtonCompletion(
                actionKey = actionKey,
                success = res.isSuccess,
                message = res.fold(
                    onSuccess = { "✓ $it" },
                    onFailure = { "⚠️ ${it.message ?: "Could not open light app"}" }
                )
            )
        }
    }

    fun openBabyDaybookSleep() {
        val res = babyDaybookLauncher.openBabyDaybookSleepPage()
        recordButtonCompletion(
            actionKey = "open_daybook",
            success = res.isSuccess,
            message = res.getOrElse { it.message ?: "Could not open Baby Daybook" }
        )
    }

    fun openNanitLiveCamera() {
        val res = babyDaybookLauncher.openNanitLiveCamera()
        recordButtonCompletion(
            actionKey = "open_nanit_camera",
            success = res.isSuccess,
            message = res.getOrElse { it.message ?: "Could not open Nanit Camera" }
        )
    }

    fun saveLightShortcut(turnOn: Boolean, uri: String, name: String) {
        val cleanUri = uri.trim()
        val cleanName = name.trim().ifBlank {
            if (turnOn) "Kips Lights ON Shortcut" else "Kips Lights OFF Shortcut"
        }
        if (turnOn) {
            tokenStore.lightsOnShortcutUri = cleanUri
            tokenStore.lightsOnShortcutName = if (cleanUri.isBlank()) "" else cleanName
            _uiState.update {
                it.copy(
                    lightsOnShortcutUri = tokenStore.lightsOnShortcutUri,
                    lightsOnShortcutName = tokenStore.lightsOnShortcutName
                )
            }
        } else {
            tokenStore.lightsOffShortcutUri = cleanUri
            tokenStore.lightsOffShortcutName = if (cleanUri.isBlank()) "" else cleanName
            _uiState.update {
                it.copy(
                    lightsOffShortcutUri = tokenStore.lightsOffShortcutUri,
                    lightsOffShortcutName = tokenStore.lightsOffShortcutName
                )
            }
        }
    }

    fun savePreferredLightApp(app: String) {
        val normalized = if (app.equals("LUTRON", ignoreCase = true)) "LUTRON" else "GOOGLE_HOME"
        tokenStore.preferredLightApp = normalized
        _uiState.update {
            it.copy(preferredLightApp = normalized)
        }
        recordButtonCompletion(
            actionKey = "pref_light_app",
            success = true,
            message = "✓ Default Light App set to ${if (normalized == "GOOGLE_HOME") "Google Home" else "Lutron"}"
        )
    }

    fun openLutronApp() {
        val res = smartLightController.openLutronApp()
        recordButtonCompletion(
            actionKey = "open_lutron",
            success = res.isSuccess,
            message = res.getOrElse { it.message ?: "Could not open Lutron App" }
        )
    }

    fun openGoogleHomeApp() {
        val res = smartLightController.openGoogleHomeApp()
        recordButtonCompletion(
            actionKey = "open_ghome",
            success = res.isSuccess,
            message = res.getOrElse { it.message ?: "Could not open Google Home App" }
        )
    }

    fun refreshTelemetry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingTelemetry = true) }

            // 1. Silently ensure valid Nanit token if signed in
            val validToken = if (tokenStore.accessToken.isNotBlank() || tokenStore.refreshToken.isNotBlank()) {
                authApi.ensureValidAccessToken(tokenStore).getOrDefault(tokenStore.accessToken)
            } else {
                ""
            }
            val health = computeTokenHealthLabel()
            val device = _uiState.value.selectedDevice ?: tokenStore.getSelectedDevice()

            _uiState.update {
                it.copy(
                    isTokenHealthy = health.first,
                    tokenHealthLabel = health.second,
                    selectedDevice = device ?: it.selectedDevice
                )
            }

            // 2. Poll Nanit Camera & Sound + Light if signed in
            if (device != null && validToken.isNotBlank()) {
                if (device.cameraUid.isNotBlank()) {
                    cameraController.fetchCameraTelemetry(validToken, device.cameraUid).onSuccess { camTel ->
                        _uiState.update { state ->
                            state.copy(
                                telemetry = state.telemetry.copy(
                                    cameraAwake = camTel.cameraAwake ?: state.telemetry.cameraAwake,
                                    cameraNightLightOn = camTel.cameraNightLightOn ?: state.telemetry.cameraNightLightOn,
                                    temperatureCelsius = camTel.temperatureCelsius ?: state.telemetry.temperatureCelsius,
                                    humidityPercent = camTel.humidityPercent ?: state.telemetry.humidityPercent,
                                    lastUpdatedMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                if (!device.speakerUid.isNullOrBlank() || device.speakerLanIp.isNotBlank()) {
                    soundLightController.fetchSoundLightTelemetry(
                        accessToken = validToken,
                        speakerUid = device.speakerUid.orEmpty(),
                        lanIp = device.speakerLanIp
                    ).onSuccess { slTel ->
                        _uiState.update { state ->
                            state.copy(
                                telemetry = state.telemetry.copy(
                                    soundLightOn = slTel.soundLightOn ?: state.telemetry.soundLightOn,
                                    activeTrack = slTel.activeTrack ?: state.telemetry.activeTrack,
                                    volumePercent = slTel.volumePercent ?: state.telemetry.volumePercent,
                                    brightnessPercent = slTel.brightnessPercent ?: state.telemetry.brightnessPercent,
                                    temperatureCelsius = slTel.temperatureCelsius ?: state.telemetry.temperatureCelsius,
                                    humidityPercent = slTel.humidityPercent ?: state.telemetry.humidityPercent,
                                    lastUpdatedMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }

            _uiState.update { it.copy(isRefreshingTelemetry = false) }
        }
    }

    fun logout() {
        tokenStore.clearAuth()
        val health = computeTokenHealthLabel()
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                isTokenHealthy = health.first,
                tokenHealthLabel = health.second,
                pendingMfaToken = null,
                statusBanner = "Signed out of Nanit Cloud",
                toastMessage = "Signed out of Nanit Cloud",
                toastId = System.currentTimeMillis()
            )
        }
    }
}
