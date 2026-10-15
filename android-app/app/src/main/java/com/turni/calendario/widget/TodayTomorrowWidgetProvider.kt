package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.turni.calendario.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayTomorrowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        val todaySummary = WidgetUpdateManager.getDaySummary(today, today)
        val tomorrowSummary = WidgetUpdateManager.getDaySummary(tomorrow, today)

        // Formato compatto elegante anti-troncamento: "Gio 15 Ottobre"
        val dateFmt = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ITALIAN)
        val formattedDate = today.format(dateFmt).replaceFirstChar { it.uppercase() }

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_tomorrow)

            // Header date
            views.setTextViewText(R.id.widget_tt_header_date, formattedDate)

            // Today Shift
            views.setTextViewText(R.id.widget_tt_shift_name, todaySummary.shiftName.uppercase())
            views.setTextViewText(R.id.widget_tt_code_pill, todaySummary.shortCode)
            views.setInt(
                R.id.widget_tt_code_pill,
                "setBackgroundResource",
                WidgetUpdateManager.getBadgeDrawableRes(todaySummary.shiftDef)
            )

            val hoursText = if (todaySummary.timeRange.isNotBlank()) {
                todaySummary.timeRange
            } else {
                "Riposo programmato"
            }
            views.setTextViewText(R.id.widget_tt_shift_hours, hoursText)

            // Overtime indicator
            if (todaySummary.overtimeHours > 0f) {
                val otFormatted = if (todaySummary.overtimeHours % 1f == 0f) {
                    "+${todaySummary.overtimeHours.toInt()}h Straord."
                } else {
                    "+${todaySummary.overtimeHours}h Straord."
                }
                views.setTextViewText(R.id.widget_tt_overtime_badge, otFormatted)
                views.setViewVisibility(R.id.widget_tt_overtime_badge, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_tt_overtime_badge, View.GONE)
            }

            // On-Call indicator
            if (todaySummary.isOnCall) {
                views.setViewVisibility(R.id.widget_tt_oncall_badge, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_tt_oncall_badge, View.GONE)
            }

            // Trasferta indicator
            if (todaySummary.isTrasferta) {
                views.setViewVisibility(R.id.widget_tt_trasferta_badge, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_tt_trasferta_badge, View.GONE)
            }

            // Tomorrow preview with dedicated colored badge
            views.setTextViewText(R.id.widget_tt_tomorrow_badge, tomorrowSummary.shortCode)
            views.setInt(
                R.id.widget_tt_tomorrow_badge,
                "setBackgroundResource",
                WidgetUpdateManager.getBadgeDrawableRes(tomorrowSummary.shiftDef)
            )

            val tomorrowDetail = if (tomorrowSummary.timeRange.isNotBlank()) {
                tomorrowSummary.timeRange
            } else {
                tomorrowSummary.shiftName
            }
            views.setTextViewText(R.id.widget_tt_tomorrow_shift, tomorrowDetail)

            // Deep link on click
            val pi = WidgetUpdateManager.createDayPendingIntent(context, today, appWidgetId)
            views.setOnClickPendingIntent(R.id.widget_tt_root, pi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodayTomorrowWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                TodayTomorrowWidgetProvider().onUpdate(context, manager, ids)
            }
        }
    }
}
