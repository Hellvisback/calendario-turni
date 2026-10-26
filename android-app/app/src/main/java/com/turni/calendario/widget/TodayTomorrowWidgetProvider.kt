package com.turni.calendario.widget

import android.content.Context

class TodayTomorrowWidgetProvider : TurniWidgetProvider() {
    internal override val kind = WidgetKind.TODAY
    companion object { fun updateAll(context: Context) = WidgetUpdateManager.updateKind(context, WidgetKind.TODAY) }
}
