package com.turni.calendario.widget

import android.content.Context

class WeekRowWidgetProvider : TurniWidgetProvider() {
    internal override val kind = WidgetKind.WEEK
    companion object { fun updateAll(context: Context) = WidgetUpdateManager.updateKind(context, WidgetKind.WEEK) }
}
