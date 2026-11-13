package com.droid.nanitroutines

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.droid.nanitroutines.ui.MainScreen
import com.droid.nanitroutines.ui.MainViewModel
import com.droid.nanitroutines.widget.RoutineWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = AppContainer(applicationContext)
        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        tokenStore = container.tokenStore,
                        authApi = container.authApi,
                        cameraController = container.cameraController,
                        soundLightController = container.soundLightController,
                        smartLightController = container.smartLightController,
                        babyDaybookLauncher = container.babyDaybookLauncher,
                        executeRoutineUseCase = container.executeRoutineUseCase,
                        lutronLeapClient = container.lutronLeapClient
                    ) as T
                }
            }
        )[MainViewModel::class.java]

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            MainScreen(
                uiState = uiState,
                onRunRoutine = { type ->
                    viewModel.runRoutine(type)
                    RoutineWidgetProvider.refreshAllWidgets(applicationContext)
                },
                onQuickToggleCamera = viewModel::quickToggleCamera,
                onQuickControlSoundLight = viewModel::quickControlSoundLight,
                onOpenBabyDaybookSleep = viewModel::openBabyDaybookSleep,
                onOpenNanitLiveCamera = viewModel::openNanitLiveCamera,
                onOpenLutronApp = viewModel::openLutronApp,
                onOpenGoogleHomeApp = viewModel::openGoogleHomeApp,
                onSavePreferredLightApp = viewModel::savePreferredLightApp,
                onUpdateRoutineConfig = viewModel::updateRoutineConfig,
                onLogin = viewModel::login,
                onVerifyMfa = viewModel::verifyMfa,
                onVerifyNanitConnection = viewModel::verifyAndRefreshNanitConnection,
                onSaveManualDevice = viewModel::saveManualDeviceConfig,
                onSelectBabyDevice = viewModel::selectBabyDevice,
                onRefreshTelemetry = viewModel::refreshTelemetry,
                onLogout = viewModel::logout
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.onAppForegrounded()
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.droid.nanitroutines.wear.PhoneWearListenerService.syncCredentialsToWatch(applicationContext)
        }
    }

    override fun onPause() {
        if (::viewModel.isInitialized) {
            viewModel.onAppBackgrounded()
        }
        super.onPause()
    }
}
