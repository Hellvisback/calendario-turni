package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.turni.calendario.R

internal enum class WidgetKind(val layout: Int, val provider: Class<*>, val defaultWidth: Int, val defaultHeight: Int) {
    TODAY(R.layout.widget_today_tomorrow, TodayTomorrowWidgetProvider::class.java, 160, 160),
    WEEK(R.layout.widget_week_row, WeekRowWidgetProvider::class.java, 320, 84),
    PANORAMA(R.layout.widget_weeks_panorama, WeeksPanoramaWidgetProvider::class.java, 320, 168),
    MONTH(R.layout.widget_month_grid, MonthGridWidgetProvider::class.java, 320, 320)
}

abstract class TurniWidgetProvider : AppWidgetProvider() {
    internal abstract val kind: WidgetKind

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        WidgetUpdateManager.enqueue(context, kind, ids) { pending?.finish() }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val editor = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE).edit()
        ids.forEach { editor.remove("offset_$it") }
        editor.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val nav = action == WidgetUpdateManager.ACTION_NAV_NEXT || action == WidgetUpdateManager.ACTION_NAV_PREV || action == WidgetUpdateManager.ACTION_NAV_RESET
        if (nav) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val manager = AppWidgetManager.getInstance(context)
            // Exported widget receivers must not accept IDs owned by another provider.
            if (id !in manager.getAppWidgetIds(ComponentName(context, kind.provider))) return
            val current = WidgetUpdateManager.getWidgetOffset(context, id)
            val next = when (action) {
                WidgetUpdateManager.ACTION_NAV_NEXT -> current + 1
                WidgetUpdateManager.ACTION_NAV_PREV -> current - 1
                else -> 0
            }
            WidgetUpdateManager.setWidgetOffset(context, id, next)
            onUpdate(context, manager, intArrayOf(id))
        } else if (action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, kind.provider)))
        } else super.onReceive(context, intent)
    }
}
