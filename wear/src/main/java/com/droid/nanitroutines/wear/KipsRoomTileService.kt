package com.droid.nanitroutines.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class KipsRoomTileService : TileService() {

    companion object {
        private const val RESOURCES_VERSION = "1"
        const val ID_CLICK_NAPTIME = "CLICK_NAPTIME"
        const val ID_CLICK_WAKEUP = "CLICK_WAKEUP"
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val lastClickedId = requestParams.currentState.lastClickableId
        when (lastClickedId) {
            ID_CLICK_NAPTIME -> {
                WearRoutineDispatcher.triggerRoutineAsync(applicationContext, "NAPTIME")
            }
            ID_CLICK_WAKEUP -> {
                WearRoutineDispatcher.triggerRoutineAsync(applicationContext, "WAKEUP")
            }
        }

        val currentStatus = WearRoutineDispatcher.getLastStatus(applicationContext)
        val rootLayout = buildTileLayout(currentStatus)

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(0L)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(rootLayout)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun buildTileLayout(statusText: String): LayoutElementBuilders.LayoutElement {
        val titleElement = LayoutElementBuilders.Text.Builder()
            .setText("Kip's Room")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(15f))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .setColor(argb(0xFFFFFFFF.toInt()))
                    .build()
            )
            .build()

        val statusColor = when {
            statusText.startsWith("✓") -> 0xFF34D399.toInt()
            statusText.startsWith("⚠️") -> 0xFFFBBF24.toInt()
            statusText.startsWith("⏳") -> 0xFF818CF8.toInt()
            else -> 0xFF94A3B8.toInt()
        }

        val subtitleElement = LayoutElementBuilders.Text.Builder()
            .setText(statusText)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(11f))
                    .setColor(argb(statusColor))
                    .build()
            )
            .build()

        val naptimeButton = buildPillActionButton(
            label = "🌙  Naptime",
            backgroundColor = 0xFF4F46E5.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            clickableId = ID_CLICK_NAPTIME
        )

        val wakeupButton = buildPillActionButton(
            label = "☀️  Wakeup",
            backgroundColor = 0xFFD97706.toInt(),
            textColor = 0xFFFFFFFF.toInt(),
            clickableId = ID_CLICK_WAKEUP
        )

        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(titleElement)
            .addContent(spacer(3f))
            .addContent(subtitleElement)
            .addContent(spacer(8f))
            .addContent(naptimeButton)
            .addContent(spacer(6f))
            .addContent(wakeupButton)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(0xFF0F172A.toInt()))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(18f))
                            .setEnd(dp(18f))
                            .setTop(dp(10f))
                            .setBottom(dp(10f))
                            .build()
                    )
                    .build()
            )
            .addContent(column)
            .build()
    }

    private fun buildPillActionButton(
        label: String,
        backgroundColor: Int,
        textColor: Int,
        clickableId: String
    ): LayoutElementBuilders.LayoutElement {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(clickableId)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(dp(148f))
            .setHeight(dp(42f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription(label)
                            .build()
                    )
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(backgroundColor))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(dp(21f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(14f))
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .setColor(argb(textColor))
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun spacer(heightDp: Float): LayoutElementBuilders.LayoutElement {
        return LayoutElementBuilders.Spacer.Builder()
            .setHeight(dp(heightDp))
            .build()
    }
}
