package com.turni.calendario.widget

import android.content.Context

class WeeksPanoramaWidgetProvider : TurniWidgetProvider() {
    internal override val kind = WidgetKind.PANORAMA
    companion object { fun updateAll(context: Context) = WidgetUpdateManager.updateKind(context, WidgetKind.PANORAMA) }
}
