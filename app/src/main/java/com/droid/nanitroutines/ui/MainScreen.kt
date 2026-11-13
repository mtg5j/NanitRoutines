package com.droid.nanitroutines.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.nanitroutines.domain.model.LightIntegrationMode
import com.droid.nanitroutines.domain.model.NanitBabyDevice
import com.droid.nanitroutines.domain.model.RoutineConfig
import com.droid.nanitroutines.domain.model.RoutineStepResult
import com.droid.nanitroutines.domain.model.RoutineType
import com.droid.nanitroutines.domain.model.StepStatus
import kotlin.math.roundToInt

private val NurseryColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    secondary = Color(0xFFFBBF24),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onPrimary = Color.White,
    onSecondary = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

@Composable
fun NanitRoutinesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NurseryColorScheme,
        content = content
    )
}

/**
 * Interactive button with 4 layers of tap feedback:
 * 1. Immediate haptic vibration (`HapticFeedbackType.LongPress`)
 * 2. Physical press-down scale animation (`0.94x`)
 * 3. Inline spinner while `isWorking == true`
 * 4. Emerald green flash (`✓ Sent!`) when `isCompleted == true`
 */
@Composable
fun FeedbackActionButton(
    text: String,
    workingText: String = "Working...",
    completedText: String = "✓ Sent!",
    actionKey: String,
    activeActionKey: String?,
    completedActionKey: String?,
    completedActionSuccess: Boolean = true,
    containerColor: Color = Color(0xFF4F46E5),
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isWorking = activeActionKey == actionKey
    val isCompleted = completedActionKey == actionKey

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "btn_scale"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = when {
            isPressed -> containerColor.copy(alpha = 0.75f)
            isCompleted && completedActionSuccess -> Color(0xFF059669) // Emerald green confirmation
            isCompleted && !completedActionSuccess -> Color(0xFFD97706) // Amber alert
            isWorking -> Color(0xFF4338CA)
            else -> containerColor
        },
        animationSpec = tween(durationMillis = 200),
        label = "btn_color"
    )

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled && !isWorking,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = animatedBgColor,
            contentColor = if (isCompleted) Color.White else contentColor,
            disabledContainerColor = Color(0xFF312E81),
            disabledContentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = workingText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            } else if (isCompleted) {
                Text(
                    text = if (completedActionSuccess) completedText else "⚠️ Check Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            } else {
                Text(
                    text = text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    uiState: MainUiState,
    onRunRoutine: (RoutineType) -> Unit,
    onQuickToggleCamera: (awake: Boolean, nightLightOn: Boolean) -> Unit,
    onQuickControlSoundLight: (isOn: Boolean, track: String, volume: Float, brightness: Float) -> Unit,
    onOpenBabyDaybookSleep: () -> Unit,
    onOpenNanitLiveCamera: () -> Unit = {},
    onOpenLutronApp: () -> Unit = {},
    onOpenGoogleHomeApp: () -> Unit = {},
    onSavePreferredLightApp: (app: String) -> Unit = {},
    onUpdateRoutineConfig: (RoutineConfig) -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onVerifyMfa: (code: String) -> Unit,
    onVerifyNanitConnection: () -> Unit = {},
    onSaveManualDevice: (babyName: String, cameraUid: String, speakerUid: String, lanIp: String) -> Unit,
    onSelectBabyDevice: (NanitBabyDevice) -> Unit,
    onRefreshTelemetry: () -> Unit,
    onLogout: () -> Unit
) {
    // Consolidated 2-tab layout: 0 = Home (Routines & Controls), 1 = Settings
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(uiState.toastId) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank() && uiState.toastId > 0L) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    NanitRoutinesTheme {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = Color(0xFF312E81),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF1E293B)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTab = 0
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home & Controls") },
                        modifier = Modifier.testTag("nav_routines")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTab = 1
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        modifier = Modifier.testTag("nav_setup")
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeaderBanner(
                    uiState = uiState,
                    onRefreshTelemetry = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefreshTelemetry()
                    },
                    onVerifyNanitConnection = onVerifyNanitConnection,
                    onOpenNanitLiveCamera = onOpenNanitLiveCamera,
                    onOpenBabyDaybookSleep = onOpenBabyDaybookSleep,
                    onOpenGoogleHomeApp = onOpenGoogleHomeApp,
                    onOpenLutronApp = onOpenLutronApp
                )

                when (selectedTab) {
                    0 -> HomeAndControlsTabContent(
                        uiState = uiState,
                        onRunRoutine = onRunRoutine,
                        onQuickToggleCamera = onQuickToggleCamera,
                        onQuickControlSoundLight = onQuickControlSoundLight,
                        onOpenBabyDaybookSleep = onOpenBabyDaybookSleep,
                        onOpenGoogleHomeApp = onOpenGoogleHomeApp,
                        onOpenLutronApp = onOpenLutronApp
                    )
                    1 -> SettingsTabContent(
                        uiState = uiState,
                        onUpdateRoutineConfig = onUpdateRoutineConfig,
                        onLogin = onLogin,
                        onVerifyMfa = onVerifyMfa,
                        onVerifyNanitConnection = onVerifyNanitConnection,
                        onSaveManualDevice = onSaveManualDevice,
                        onSavePreferredLightApp = onSavePreferredLightApp,
                        onSelectBabyDevice = onSelectBabyDevice,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderBanner(
    uiState: MainUiState,
    onRefreshTelemetry: () -> Unit,
    onVerifyNanitConnection: () -> Unit,
    onOpenNanitLiveCamera: () -> Unit,
    onOpenBabyDaybookSleep: () -> Unit,
    onOpenGoogleHomeApp: () -> Unit,
    onOpenLutronApp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val roomTitle = uiState.selectedDevice?.babyName?.let {
                        if (it.contains("Room", ignoreCase = true)) it else "$it's Room"
                    } ?: "Kip's Room"

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = roomTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (uiState.telemetry.temperatureCelsius != null) {
                            val tempF = (uiState.telemetry.temperatureCelsius * 9f / 5f + 32f).roundToInt()
                            val hum = uiState.telemetry.humidityPercent?.roundToInt() ?: 48
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F172A)
                            ) {
                                Text(
                                    text = "${tempF}°F • ${hum}%",
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = uiState.statusBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.testTag("status_banner_text")
                    )
                }
                IconButton(onClick = onRefreshTelemetry) {
                    if (uiState.isRefreshingTelemetry) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF818CF8)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Nursery Status",
                            tint = Color(0xFF818CF8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nanit Token & Auto-Refresh Health Strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onVerifyNanitConnection() }
                    .testTag("token_health_strip")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isTokenHealthy) Color(0xFF34D399) else Color(0xFFF87171)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.tokenHealthLabel,
                            fontSize = 11.sp,
                            color = if (uiState.isTokenHealthy) Color(0xFFA7F3D0) else Color(0xFFFCA5A5)
                        )
                    }
                    Text(
                        text = if (uiState.activeActionKey == "token_refresh") "Syncing..." else "↻ Verify",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF818CF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1-Tap Quick-Action App Links: Live Camera, Google Home / Lutron, & Baby Daybook
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FeedbackActionButton(
                    text = "📹 Camera",
                    workingText = "Opening...",
                    completedText = "✓ Opened",
                    actionKey = "open_nanit_camera",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFF312E81),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("btn_open_nanit_camera"),
                    onClick = onOpenNanitLiveCamera
                )

                val useGoogleHome = !uiState.preferredLightApp.equals("LUTRON", ignoreCase = true)
                FeedbackActionButton(
                    text = if (useGoogleHome) "🏠 Google Home" else "💡 Lutron",
                    workingText = "Opening...",
                    completedText = "✓ Opened",
                    actionKey = if (useGoogleHome) "open_ghome" else "open_lutron",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFF78350F),
                    contentColor = Color(0xFFFDE68A),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("btn_header_light_app"),
                    onClick = {
                        if (useGoogleHome) onOpenGoogleHomeApp() else onOpenLutronApp()
                    }
                )

                FeedbackActionButton(
                    text = "🍼 Daybook",
                    workingText = "Opening...",
                    completedText = "✓ Opened",
                    actionKey = "open_daybook",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFF334155),
                    contentColor = Color(0xFFFBBF24),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("btn_header_baby_daybook"),
                    onClick = onOpenBabyDaybookSleep
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeAndControlsTabContent(
    uiState: MainUiState,
    onRunRoutine: (RoutineType) -> Unit,
    onQuickToggleCamera: (awake: Boolean, nightLightOn: Boolean) -> Unit,
    onQuickControlSoundLight: (isOn: Boolean, track: String, volume: Float, brightness: Float) -> Unit,
    onOpenBabyDaybookSleep: () -> Unit,
    onOpenGoogleHomeApp: () -> Unit,
    onOpenLutronApp: () -> Unit
) {
    val napCfg = uiState.naptimeConfig
    val wakeCfg = uiState.wakeupConfig

    var selectedTrack by remember(napCfg.soundTrack) {
        mutableStateOf(napCfg.soundTrack)
    }
    var volume by remember(napCfg.soundVolume) {
        mutableFloatStateOf(napCfg.soundVolume)
    }
    var brightness by remember(napCfg.lightBrightness) {
        mutableFloatStateOf(napCfg.lightBrightness)
    }
    var camNightLight by remember { mutableStateOf(false) }

    // 1. NAPTIME HERO CARD
    RoutineHeroCard(
        title = "🌙 Naptime Routine",
        subtitle = "Nanit Camera ON • Sound + Light ON (${napCfg.soundTrack}) • Kip's Room Lights OFF",
        bulletPoints = listOf(
            "Nanit Pro Camera: Wake Camera ON (Active)",
            "Nanit Sound + Light: Play \"${napCfg.soundTrack}\" @ ${(napCfg.soundVolume * 100).roundToInt()}% • Nightlight ${(napCfg.lightBrightness * 100).roundToInt()}%",
            if (napCfg.smartLightMode != LightIntegrationMode.DISABLED) {
                "Kip's Room Lights: Opens ${if (uiState.preferredLightApp == "LUTRON") "Lutron" else "Google Home"} to turn lights OFF"
            } else {
                "Kip's Room Lights: Use 🏠 Google Home button above anytime"
            }
        ),
        gradientColors = listOf(Color(0xFF312E81), Color(0xFF1E1B4B)),
        buttonColor = Color(0xFF6366F1),
        buttonLabel = "Start Naptime",
        workingLabel = "Running Naptime...",
        completedLabel = "✓ Naptime Complete!",
        actionKey = "routine_naptime",
        activeActionKey = uiState.activeActionKey,
        completedActionKey = uiState.completedActionKey,
        completedActionSuccess = uiState.completedActionSuccess,
        buttonTestTag = "btn_run_naptime",
        onRunClick = { onRunRoutine(RoutineType.NAPTIME) }
    )

    // 2. WAKEUP HERO CARD
    RoutineHeroCard(
        title = "☀️ Wakeup Routine",
        subtitle = "Nanit Camera Standby • Sound + Light OFF • Baby Daybook Sleep",
        bulletPoints = listOf(
            "Nanit Pro Camera: Put Camera in Standby (OFF)",
            "Nanit Sound + Light: Turn OFF Sound & Nightlight completely",
            if (wakeCfg.triggerBabyDaybook) {
                "Baby Daybook: Opens Sleep page automatically"
            } else {
                "Baby Daybook: Quick link available at top"
            }
        ),
        gradientColors = listOf(Color(0xFF78350F), Color(0xFF451A03)),
        buttonColor = Color(0xFFF59E0B),
        buttonTextColor = Color(0xFF0F172A),
        buttonLabel = "Start Wakeup",
        workingLabel = "Running Wakeup...",
        completedLabel = "✓ Wakeup Complete!",
        actionKey = "routine_wakeup",
        activeActionKey = uiState.activeActionKey,
        completedActionKey = uiState.completedActionKey,
        completedActionSuccess = uiState.completedActionSuccess,
        buttonTestTag = "btn_run_wakeup",
        onRunClick = { onRunRoutine(RoutineType.WAKEUP) }
    )

    // 3. LIVE EXECUTION PROGRESS / STEP CHECKLIST
    if (uiState.activeSteps.isNotEmpty()) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("routine_steps_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${uiState.activeRoutineType?.displayName ?: "Routine"} Step Checklist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                uiState.activeSteps.forEach { step ->
                    StepResultRow(step)
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onOpenBabyDaybookSleep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_open_baby_daybook")
                ) {
                    Text(
                        text = "🍼 Open Baby Daybook (Sleep)",
                        color = Color(0xFFFBBF24)
                    )
                }
            }
        }
    }

    // 4. DIRECT DEVICE CONTROLS (All on the same Home screen — no duplicate tab!)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color(0xFF818CF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nanit Pro Camera",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                val camStateBadge = when (uiState.telemetry.cameraAwake) {
                    true -> "🟢 Awake (ON)"
                    false -> "🌙 Standby"
                    null -> "Ready"
                }
                Text(
                    text = camStateBadge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCBD5E1)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Include Camera Nightlight", color = Color(0xFFCBD5E1), fontSize = 14.sp)
                Switch(
                    checked = camNightLight,
                    onCheckedChange = { camNightLight = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeedbackActionButton(
                    text = "📹 Camera ON",
                    workingText = "Waking...",
                    completedText = "✓ Camera ON!",
                    actionKey = "cam_awake",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFF4F46E5),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_camera_awake"),
                    onClick = { onQuickToggleCamera(true, camNightLight) }
                )

                FeedbackActionButton(
                    text = "🌙 Standby OFF",
                    workingText = "Standby...",
                    completedText = "✓ In Standby!",
                    actionKey = "cam_standby",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFF334155),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_camera_standby"),
                    onClick = { onQuickToggleCamera(false, false) }
                )
            }
        }
    }

    // 5. NANIT SOUND + LIGHT MACHINE DIRECT CONTROLS
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nanit Sound + Light",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                val slBadge = if (uiState.telemetry.soundLightOn == true) {
                    "🔊 Playing ${uiState.telemetry.activeTrack ?: selectedTrack}"
                } else {
                    "🔇 OFF"
                }
                Text(
                    text = slBadge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoutineConfig.AVAILABLE_TRACKS.forEach { track ->
                    FilterChip(
                        selected = selectedTrack == track,
                        onClick = { selectedTrack = track },
                        label = { Text(track) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4F46E5),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Text("Volume: ${(volume * 100).roundToInt()}%", color = Color(0xFFCBD5E1), fontSize = 13.sp)
            Slider(
                value = volume,
                onValueChange = { volume = it },
                valueRange = 0f..1f
            )

            Text(
                "Nightlight Brightness: ${(brightness * 100).roundToInt()}%",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp
            )
            Slider(
                value = brightness,
                onValueChange = { brightness = it },
                valueRange = 0f..1f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeedbackActionButton(
                    text = "🔊 Turn ON ($selectedTrack)",
                    workingText = "Starting...",
                    completedText = "✓ Playing $selectedTrack!",
                    actionKey = "sl_on",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFF0284C7),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_soundlight_on"),
                    onClick = {
                        onQuickControlSoundLight(true, selectedTrack, volume, brightness)
                    }
                )

                FeedbackActionButton(
                    text = "🔇 Turn OFF",
                    workingText = "Stopping...",
                    completedText = "✓ Turned OFF!",
                    actionKey = "sl_off",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFF334155),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_soundlight_off"),
                    onClick = {
                        onQuickControlSoundLight(false, selectedTrack, 0f, 0f)
                    }
                )
            }
        }
    }

    // 6. KIP'S ROOM MAIN LIGHTS — CLEAN 1-TAP GOOGLE HOME / LUTRON CARD
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LightMode, contentDescription = null, tint = Color(0xFFFBBF24))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Kip's Room Main Lights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = "Tap below to jump straight into Google Home or Lutron to toggle Kip's Room Main Lights.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeedbackActionButton(
                    text = "🏠 Open Google Home",
                    workingText = "Opening...",
                    completedText = "✓ Opened!",
                    actionKey = "open_ghome",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFF0F766E),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenGoogleHomeApp
                )
                FeedbackActionButton(
                    text = "💡 Open Lutron App",
                    workingText = "Opening...",
                    completedText = "✓ Opened!",
                    actionKey = "open_lutron",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFFB45309),
                    modifier = Modifier.weight(1f),
                    onClick = onOpenLutronApp
                )
            }
        }
    }
}

@Composable
private fun RoutineHeroCard(
    title: String,
    subtitle: String,
    bulletPoints: List<String>,
    gradientColors: List<Color>,
    buttonColor: Color,
    buttonTextColor: Color = Color.White,
    buttonLabel: String,
    workingLabel: String,
    completedLabel: String,
    actionKey: String,
    activeActionKey: String?,
    completedActionKey: String?,
    completedActionSuccess: Boolean,
    buttonTestTag: String,
    onRunClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(gradientColors))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE2E8F0)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                bulletPoints.forEach { bullet ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(buttonColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            FeedbackActionButton(
                text = buttonLabel,
                workingText = workingLabel,
                completedText = completedLabel,
                actionKey = actionKey,
                activeActionKey = activeActionKey,
                completedActionKey = completedActionKey,
                completedActionSuccess = completedActionSuccess,
                containerColor = buttonColor,
                contentColor = buttonTextColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag(buttonTestTag),
                onClick = onRunClick
            )
        }
    }
}

@Composable
private fun StepResultRow(step: RoutineStepResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (step.status) {
            StepStatus.RUNNING, StepStatus.PENDING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF818CF8)
                )
            }
            StepStatus.SUCCESS, StepStatus.SKIPPED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(20.dp)
                )
            }
            StepStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Info",
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.stepName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color.White
            )
            Text(
                text = step.detail,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsTabContent(
    uiState: MainUiState,
    onUpdateRoutineConfig: (RoutineConfig) -> Unit,
    onLogin: (email: String, password: String) -> Unit,
    onVerifyMfa: (code: String) -> Unit,
    onVerifyNanitConnection: () -> Unit,
    onSaveManualDevice: (babyName: String, cameraUid: String, speakerUid: String, lanIp: String) -> Unit,
    onSavePreferredLightApp: (app: String) -> Unit,
    onSelectBabyDevice: (NanitBabyDevice) -> Unit,
    onLogout: () -> Unit
) {
    var emailInput by remember(uiState.email) { mutableStateOf(uiState.email) }
    var passwordInput by remember { mutableStateOf("") }
    var mfaCodeInput by remember { mutableStateOf("") }

    var babyNameInput by remember(uiState.selectedDevice) {
        mutableStateOf(uiState.selectedDevice?.babyName ?: "Kip's Room")
    }
    var cameraUidInput by remember(uiState.selectedDevice) {
        mutableStateOf(uiState.selectedDevice?.cameraUid.orEmpty())
    }
    var speakerUidInput by remember(uiState.selectedDevice) {
        mutableStateOf(uiState.selectedDevice?.speakerUid.orEmpty())
    }
    var speakerLanIpInput by remember(uiState.selectedDevice) {
        mutableStateOf(uiState.selectedDevice?.speakerLanIp.orEmpty())
    }

    // 1. Nanit Cloud Account & Automatic Token Health Card
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF818CF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Nanit Account & Token Health",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (uiState.isTokenHealthy) {
                            "🟢 ${uiState.tokenHealthLabel}"
                        } else {
                            "🔴 ${uiState.tokenHealthLabel}"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (uiState.isTokenHealthy) Color(0xFF34D399) else Color(0xFFF87171)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nanit access tokens last 60 minutes. This app now automatically refreshes your token in the background using your saved refresh token before every routine or button tap—so you never have to log in again.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            if (uiState.isAuthenticated) {
                FeedbackActionButton(
                    text = "🔄 Test & Refresh Nanit Token Now",
                    workingText = "Refreshing Token...",
                    completedText = "✓ Token Refreshed & Verified!",
                    actionKey = "token_refresh",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    containerColor = Color(0xFF0284C7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_verify_nanit_token"),
                    onClick = onVerifyNanitConnection
                )
            }

            if (uiState.authError != null) {
                Text(
                    text = uiState.authError,
                    color = Color(0xFFF87171),
                    fontSize = 13.sp
                )
            }

            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Nanit Account Email") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_nanit_email")
            )

            OutlinedTextField(
                value = passwordInput,
                onValueChange = { passwordInput = it },
                label = { Text("Nanit Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_nanit_password")
            )

            if (uiState.pendingMfaToken != null) {
                OutlinedTextField(
                    value = mfaCodeInput,
                    onValueChange = { mfaCodeInput = it },
                    label = { Text("2FA Code (sent via ${uiState.pendingMfaChannel})") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_nanit_mfa")
                )
                FeedbackActionButton(
                    text = "Verify 2FA & Connect Nanit",
                    workingText = "Verifying 2FA...",
                    completedText = "✓ Connected!",
                    actionKey = "verify_mfa",
                    activeActionKey = uiState.activeActionKey,
                    completedActionKey = uiState.completedActionKey,
                    completedActionSuccess = uiState.completedActionSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_verify_mfa"),
                    onClick = { onVerifyMfa(mfaCodeInput) }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeedbackActionButton(
                        text = if (uiState.isAuthenticated) "Re-Sign In to Nanit" else "Sign In to Nanit",
                        workingText = "Signing in...",
                        completedText = "✓ Signed In!",
                        actionKey = "login",
                        activeActionKey = uiState.activeActionKey,
                        completedActionKey = uiState.completedActionKey,
                        completedActionSuccess = uiState.completedActionSuccess,
                        enabled = !uiState.isAuthenticating,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_nanit_login"),
                        onClick = { onLogin(emailInput, passwordInput) }
                    )
                    if (uiState.isAuthenticated) {
                        OutlinedButton(onClick = onLogout) {
                            Text("Sign Out")
                        }
                    }
                }
            }

            if (uiState.babies.isNotEmpty()) {
                Text("Discovered Babies & Devices:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.babies.forEach { baby ->
                        FilterChip(
                            selected = uiState.selectedDevice?.babyUid == baby.babyUid,
                            onClick = { onSelectBabyDevice(baby) },
                            label = {
                                Text("${baby.babyName} (Cam: ${baby.cameraUid.take(6)})")
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF334155))

            Text(
                text = "Nanit Device UIDs (Auto-filled when you sign in)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1)
            )
            OutlinedTextField(
                value = babyNameInput,
                onValueChange = { babyNameInput = it },
                label = { Text("Room / Baby Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cameraUidInput,
                onValueChange = { cameraUidInput = it },
                label = { Text("Nanit Pro Camera UID (camera_uid)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = speakerUidInput,
                onValueChange = { speakerUidInput = it },
                label = { Text("Nanit Sound + Light UID (speaker_uid)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = speakerLanIpInput,
                onValueChange = { speakerLanIpInput = it },
                label = { Text("Optional Sound + Light Local LAN IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FeedbackActionButton(
                text = "Save Nanit Device UIDs",
                workingText = "Saving...",
                completedText = "✓ Saved UIDs!",
                actionKey = "save_device_uids",
                activeActionKey = uiState.activeActionKey,
                completedActionKey = uiState.completedActionKey,
                completedActionSuccess = uiState.completedActionSuccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_device_uids"),
                onClick = {
                    onSaveManualDevice(
                        babyNameInput,
                        cameraUidInput,
                        speakerUidInput,
                        speakerLanIpInput
                    )
                }
            )
        }
    }

    // 2. Simple Routine & Google Home Preferences Card
    RoutinePreferencesCard(
        uiState = uiState,
        onSavePreferredLightApp = onSavePreferredLightApp,
        onSaveConfig = onUpdateRoutineConfig
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutinePreferencesCard(
    uiState: MainUiState,
    onSavePreferredLightApp: (String) -> Unit,
    onSaveConfig: (RoutineConfig) -> Unit
) {
    val napCfg = uiState.naptimeConfig
    val wakeCfg = uiState.wakeupConfig

    var soundTrack by remember(napCfg.soundTrack) { mutableStateOf(napCfg.soundTrack) }
    var soundVolume by remember(napCfg.soundVolume) { mutableFloatStateOf(napCfg.soundVolume) }
    var lightBrightness by remember(napCfg.lightBrightness) { mutableFloatStateOf(napCfg.lightBrightness) }
    var autoOpenLightAppDuringRoutine by remember(napCfg.smartLightMode) {
        mutableStateOf(napCfg.smartLightMode != LightIntegrationMode.DISABLED)
    }
    var openDaybookOnWakeup by remember(wakeCfg.triggerBabyDaybook) {
        mutableStateOf(wakeCfg.triggerBabyDaybook)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Routine & Google Home Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Preferred App for Kip's Room Main Lights:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !uiState.preferredLightApp.equals("LUTRON", ignoreCase = true),
                    onClick = { onSavePreferredLightApp("GOOGLE_HOME") },
                    label = { Text("🏠 Google Home App") }
                )
                FilterChip(
                    selected = uiState.preferredLightApp.equals("LUTRON", ignoreCase = true),
                    onClick = { onSavePreferredLightApp("LUTRON") },
                    label = { Text("💡 Lutron App") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open Google Home / Lutron during Routine",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "When enabled, tapping Start Naptime or Start Wakeup also opens Google Home/Lutron so you can flip Kip's Lights",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = autoOpenLightAppDuringRoutine,
                    onCheckedChange = { autoOpenLightAppDuringRoutine = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open Baby Daybook (Sleep) on Wakeup",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Automatically opens the Baby Daybook sleep tracker when Wakeup finishes",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = openDaybookOnWakeup,
                    onCheckedChange = { openDaybookOnWakeup = it }
                )
            }

            HorizontalDivider(color = Color(0xFF334155))

            Text("Default Naptime Sound Track:", color = Color(0xFFCBD5E1), fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutineConfig.AVAILABLE_TRACKS.forEach { track ->
                    FilterChip(
                        selected = soundTrack == track,
                        onClick = { soundTrack = track },
                        label = { Text(track) }
                    )
                }
            }

            Text(
                "Default Naptime Volume: ${(soundVolume * 100).roundToInt()}%",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp
            )
            Slider(value = soundVolume, onValueChange = { soundVolume = it })

            Text(
                "Default Naptime Nightlight Brightness: ${(lightBrightness * 100).roundToInt()}%",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp
            )
            Slider(value = lightBrightness, onValueChange = { lightBrightness = it })

            FeedbackActionButton(
                text = "Save Routine Preferences",
                workingText = "Saving...",
                completedText = "✓ Preferences Saved!",
                actionKey = "save_routine_naptime",
                activeActionKey = uiState.activeActionKey,
                completedActionKey = uiState.completedActionKey,
                completedActionSuccess = uiState.completedActionSuccess,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val chosenMode = if (autoOpenLightAppDuringRoutine) {
                        LightIntegrationMode.GOOGLE_HOME_INTENT
                    } else {
                        LightIntegrationMode.DISABLED
                    }
                    onSaveConfig(
                        napCfg.copy(
                            soundTrack = soundTrack,
                            soundVolume = soundVolume,
                            lightBrightness = lightBrightness,
                            smartLightMode = chosenMode
                        )
                    )
                    onSaveConfig(
                        wakeCfg.copy(
                            smartLightMode = chosenMode,
                            triggerBabyDaybook = openDaybookOnWakeup
                        )
                    )
                }
            )
        }
    }
}
