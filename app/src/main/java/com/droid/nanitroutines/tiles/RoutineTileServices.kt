package com.droid.nanitroutines.tiles

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.droid.nanitroutines.AppContainer
import com.droid.nanitroutines.domain.model.RoutineType
import com.droid.nanitroutines.widget.RoutineWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseRoutineTileService(
    private val routineType: RoutineType
) : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Nursery ${routineType.displayName}"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                subtitle = "Running..."
            }
            updateTile()
        }

        val container = AppContainer(applicationContext)
        val config = container.tokenStore.loadRoutineConfig(routineType)

        scope.launch {
            val validToken = container.authApi.ensureValidAccessToken(container.tokenStore)
                .getOrDefault(container.tokenStore.accessToken)
            val device = container.tokenStore.getSelectedDevice()

            val report = container.executeRoutineUseCase.execute(
                config = config,
                accessToken = validToken,
                device = device
            )
            container.tokenStore.lastRoutineSummary = "${routineType.displayName} triggered via Quick Tile"
            RoutineWidgetProvider.refreshAllWidgets(applicationContext)

            qsTile?.apply {
                state = if (report.overallSuccess) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    subtitle = "Done"
                }
                updateTile()
            }
        }
    }
}

class NaptimeTileService : BaseRoutineTileService(RoutineType.NAPTIME)

class WakeupTileService : BaseRoutineTileService(RoutineType.WAKEUP)
