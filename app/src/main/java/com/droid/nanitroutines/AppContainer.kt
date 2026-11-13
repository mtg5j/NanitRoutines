package com.droid.nanitroutines

import android.content.Context
import com.droid.nanitroutines.data.lights.BabyDaybookHelper
import com.droid.nanitroutines.data.lights.LutronLeapClient
import com.droid.nanitroutines.data.lights.SmartLightRoutineClient
import com.droid.nanitroutines.data.nanit.NanitAuthApi
import com.droid.nanitroutines.data.nanit.NanitCameraWebSocketClient
import com.droid.nanitroutines.data.nanit.NanitSoundLightWebSocketClient
import com.droid.nanitroutines.data.nanit.TokenStore
import com.droid.nanitroutines.domain.usecase.ExecuteRoutineUseCase
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    val tokenStore = TokenStore(appContext)
    val authApi = NanitAuthApi(httpClient)
    val cameraController = NanitCameraWebSocketClient(
        httpClient = httpClient,
        tokenStore = tokenStore,
        authApi = authApi
    )
    val soundLightController = NanitSoundLightWebSocketClient(
        httpClient = httpClient,
        tokenStore = tokenStore,
        authApi = authApi
    )
    val lutronLeapClient = LutronLeapClient()
    val smartLightController = SmartLightRoutineClient(
        context = appContext,
        httpClient = httpClient,
        tokenStore = tokenStore,
        lutronLeapClient = lutronLeapClient
    )
    val babyDaybookLauncher = BabyDaybookHelper(appContext)

    val executeRoutineUseCase = ExecuteRoutineUseCase(
        cameraController = cameraController,
        soundLightController = soundLightController,
        smartLightController = smartLightController,
        babyDaybookLauncher = babyDaybookLauncher
    )
}
