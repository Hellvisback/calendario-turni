package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.turni.calendario.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayTomorrowWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        var appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val seg = intent.data?.pathSegments?.getOrNull(1)?.toIntOrNull()
            if (seg != null) appWidgetId = seg
        }
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            when (intent.action) {
                WidgetUpdateManager.ACTION_NAV_NEXT -> {
                    val current = WidgetUpdateManager.getWidgetOffset(context, appWidgetId)
                    val next = if (current >= 12) 0 else current + 1
                    WidgetUpdateManager.setWidgetOffset(context, appWidgetId, next)
                    val manager = AppWidgetManager.getInstance(context)
                    onUpdate(context, manager, intArrayOf(appWidgetId))
                }
                WidgetUpdateManager.ACTION_NAV_PREV -> {
                    val current = WidgetUpdateManager.getWidgetOffset(context, appWidgetId)
                    val prev = (current - 1).coerceAtLeast(0)
                    WidgetUpdateManager.setWidgetOffset(context, appWidgetId, prev)
                    val manager = AppWidgetManager.getInstance(context)
                    onUpdate(context, manager, intArrayOf(appWidgetId))
                }
                WidgetUpdateManager.ACTION_NAV_RESET -> {
                    WidgetUpdateManager.setWidgetOffset(context, appWidgetId, 0)
                    val manager = AppWidgetManager.getInstance(context)
                    onUpdate(context, manager, intArrayOf(appWidgetId))
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val today = LocalDate.now()
        val dateFmt = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ITALIAN)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_tomorrow)
            val weekOffset = WidgetUpdateManager.getWidgetOffset(context, appWidgetId)
            val currentDay = today.plusWeeks(weekOffset.toLong())
            val nextDay = currentDay.plusDays(1)

            val todaySummary = WidgetUpdateManager.getDaySummary(currentDay, today)
            val tomorrowSummary = WidgetUpdateManager.getDaySummary(nextDay, today)

            val baseDateStr = currentDay.format(dateFmt).replaceFirstChar { it.uppercase() }
            val formattedDate = if (weekOffset > 0) {
                "$baseDateStr (+$weekOffset sett.)"
            } else {
                baseDateStr
            }

            // Navigation buttons
            val nextPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_NEXT, TodayTomorrowWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.btn_tt_next, nextPi)

            val prevPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_PREV, TodayTomorrowWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.btn_tt_prev, prevPi)

            val resetPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_RESET, TodayTomorrowWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.widget_tt_header_date, resetPi)

            if (weekOffset > 0) {
                views.setViewVisibility(R.id.btn_tt_prev, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.btn_tt_prev, View.GONE)
            }

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
                tomorrowSummary.timeRange.replace(" ", "")
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
