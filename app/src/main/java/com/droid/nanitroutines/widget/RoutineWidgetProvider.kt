package com.droid.nanitroutines.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.droid.nanitroutines.AppContainer
import com.droid.nanitroutines.MainActivity
import com.droid.nanitroutines.R
import com.droid.nanitroutines.domain.model.RoutineType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RoutineWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_RUN_NAPTIME = "com.droid.nanitroutines.ACTION_RUN_NAPTIME"
        const val ACTION_RUN_WAKEUP = "com.droid.nanitroutines.ACTION_RUN_WAKEUP"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_routines)
            // Naptime PendingIntent
            val napIntent = Intent(context, RoutineActionReceiver::class.java).apply {
                action = ACTION_RUN_NAPTIME
            }
            val napPendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                napIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_naptime, napPendingIntent)

            // Wakeup PendingIntent
            val wakeIntent = Intent(context, RoutineActionReceiver::class.java).apply {
                action = ACTION_RUN_WAKEUP
            }
            val wakePendingIntent = PendingIntent.getBroadcast(
                context,
                1002,
                wakeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_wakeup, wakePendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RoutineWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }
}

class RoutineActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val routineType = when (intent.action) {
            RoutineWidgetProvider.ACTION_RUN_NAPTIME -> RoutineType.NAPTIME
            RoutineWidgetProvider.ACTION_RUN_WAKEUP -> RoutineType.WAKEUP
            else -> return
        }

        val pendingResult = goAsync()
        val container = AppContainer(context)
        val config = container.tokenStore.loadRoutineConfig(routineType)

        scope.launch {
            try {
                container.tokenStore.lastRoutineSummary = "Running ${routineType.displayName}..."
                RoutineWidgetProvider.refreshAllWidgets(context)

                val validToken = container.authApi.ensureValidAccessToken(container.tokenStore)
                    .getOrDefault(container.tokenStore.accessToken)
                val device = container.tokenStore.getSelectedDevice()

                val report = container.executeRoutineUseCase.execute(
                    config = config,
                    accessToken = validToken,
                    device = device
                )

                container.tokenStore.lastRoutineSummary = if (report.overallSuccess) {
                    "${routineType.displayName} active"
                } else {
                    "${routineType.displayName} triggered"
                }
                RoutineWidgetProvider.refreshAllWidgets(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
