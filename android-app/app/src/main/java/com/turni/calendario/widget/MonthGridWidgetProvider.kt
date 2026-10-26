package com.turni.calendario.widget

import android.content.Context

class MonthGridWidgetProvider : TurniWidgetProvider() {
    internal override val kind = WidgetKind.MONTH
    companion object { fun updateAll(context: Context) = WidgetUpdateManager.updateKind(context, WidgetKind.MONTH) }
}
