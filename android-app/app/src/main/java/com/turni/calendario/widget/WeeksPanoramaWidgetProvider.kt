package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.turni.calendario.R
import com.turni.calendario.repository.ShiftRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class WeeksPanoramaWidgetProvider : AppWidgetProvider() {

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
        val baseMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val colIds = intArrayOf(
            R.id.wp_col_0, R.id.wp_col_1, R.id.wp_col_2, R.id.wp_col_3, R.id.wp_col_4, R.id.wp_col_5, R.id.wp_col_6,
            R.id.wp_col_7, R.id.wp_col_8, R.id.wp_col_9, R.id.wp_col_10, R.id.wp_col_11, R.id.wp_col_12, R.id.wp_col_13
        )
        val nameIds = intArrayOf(
            R.id.wp_day_name_0, R.id.wp_day_name_1, R.id.wp_day_name_2, R.id.wp_day_name_3, R.id.wp_day_name_4, R.id.wp_day_name_5, R.id.wp_day_name_6,
            R.id.wp_day_name_7, R.id.wp_day_name_8, R.id.wp_day_name_9, R.id.wp_day_name_10, R.id.wp_day_name_11, R.id.wp_day_name_12, R.id.wp_day_name_13
        )
        val numIds = intArrayOf(
            R.id.wp_day_num_0, R.id.wp_day_num_1, R.id.wp_day_num_2, R.id.wp_day_num_3, R.id.wp_day_num_4, R.id.wp_day_num_5, R.id.wp_day_num_6,
            R.id.wp_day_num_7, R.id.wp_day_num_8, R.id.wp_day_num_9, R.id.wp_day_num_10, R.id.wp_day_num_11, R.id.wp_day_num_12, R.id.wp_day_num_13
        )
        val badgeIds = intArrayOf(
            R.id.wp_shift_badge_0, R.id.wp_shift_badge_1, R.id.wp_shift_badge_2, R.id.wp_shift_badge_3, R.id.wp_shift_badge_4, R.id.wp_shift_badge_5, R.id.wp_shift_badge_6,
            R.id.wp_shift_badge_7, R.id.wp_shift_badge_8, R.id.wp_shift_badge_9, R.id.wp_shift_badge_10, R.id.wp_shift_badge_11, R.id.wp_shift_badge_12, R.id.wp_shift_badge_13
        )
        val extraIds = intArrayOf(
            R.id.wp_extra_badge_0, R.id.wp_extra_badge_1, R.id.wp_extra_badge_2, R.id.wp_extra_badge_3, R.id.wp_extra_badge_4, R.id.wp_extra_badge_5, R.id.wp_extra_badge_6,
            R.id.wp_extra_badge_7, R.id.wp_extra_badge_8, R.id.wp_extra_badge_9, R.id.wp_extra_badge_10, R.id.wp_extra_badge_11, R.id.wp_extra_badge_12, R.id.wp_extra_badge_13
        )

        val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
        val rotationLabel = ShiftRepository.activeRotation?.name ?: "2 Settimane"

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_weeks_panorama)
            val weekOffset = WidgetUpdateManager.getWidgetOffset(context, appWidgetId)
            val monday = baseMonday.plusWeeks(weekOffset.toLong())

            // Title dinamico sul mese visualizzato
            val middleDate = monday.plusDays(7)
            val monthLabel = middleDate.format(monthFmt).replaceFirstChar { it.uppercase() }

            views.setTextViewText(R.id.widget_wp_title, monthLabel)
            views.setTextViewText(R.id.widget_wp_stats, rotationLabel)

            // Setup Navigation pending intents
            val nextPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_NEXT, WeeksPanoramaWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.btn_wp_next, nextPi)

            val prevPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_PREV, WeeksPanoramaWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.btn_wp_prev, prevPi)

            val resetPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_RESET, WeeksPanoramaWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.widget_wp_title, resetPi)

            // Indicatori stato navigazione
            if (weekOffset > 0) {
                views.setViewVisibility(R.id.btn_wp_prev, View.VISIBLE)
                views.setViewVisibility(R.id.wp_week_indicator, View.VISIBLE)
                views.setTextViewText(R.id.wp_week_indicator, "+$weekOffset sett.")
            } else {
                views.setViewVisibility(R.id.btn_wp_prev, View.GONE)
                views.setViewVisibility(R.id.wp_week_indicator, View.GONE)
            }

            for (i in 0..13) {
                val date = monday.plusDays(i.toLong())
                val summary = WidgetUpdateManager.getDaySummary(date, today)

                views.setTextViewText(nameIds[i], summary.dayNameShort)
                views.setTextViewText(numIds[i], summary.dayNumber.toString())
                views.setTextViewText(badgeIds[i], summary.shortCode)

                views.setInt(
                    badgeIds[i],
                    "setBackgroundResource",
                    WidgetUpdateManager.getBadgeDrawableRes(summary.shiftDef)
                )

                // Today highlight (solo se nella settimana reale odierna)
                if (summary.isToday) {
                    views.setInt(colIds[i], "setBackgroundResource", R.drawable.bg_widget_today_highlight)
                    views.setTextColor(nameIds[i], Color.parseColor("#38BDF8"))
                    views.setTextColor(numIds[i], Color.parseColor("#38BDF8"))
                } else {
                    views.setInt(colIds[i], "setBackgroundColor", Color.TRANSPARENT)
                    if (i == 6 || i == 13) { // Domenica
                        views.setTextColor(nameIds[i], Color.parseColor("#F87171"))
                        views.setTextColor(numIds[i], Color.parseColor("#FCA5A5"))
                    } else if (i == 5 || i == 12) { // Sabato
                        views.setTextColor(nameIds[i], Color.parseColor("#94A3B8"))
                        views.setTextColor(numIds[i], Color.parseColor("#F1F5F9"))
                    } else {
                        views.setTextColor(nameIds[i], Color.parseColor("#64748B"))
                        views.setTextColor(numIds[i], Color.parseColor("#F1F5F9"))
                    }
                }

                // Extra overtime or on-call indicator
                if (summary.overtimeHours > 0f) {
                    val otText = if (summary.overtimeHours % 1f == 0f) "+${summary.overtimeHours.toInt()}h" else "+${summary.overtimeHours}h"
                    views.setTextViewText(extraIds[i], otText)
                    views.setTextColor(extraIds[i], Color.parseColor("#FBBF24"))
                    views.setViewVisibility(extraIds[i], View.VISIBLE)
                } else if (summary.isOnCall) {
                    views.setTextViewText(extraIds[i], "REP")
                    views.setTextColor(extraIds[i], Color.parseColor("#C084FC"))
                    views.setViewVisibility(extraIds[i], View.VISIBLE)
                } else {
                    views.setViewVisibility(extraIds[i], View.GONE)
                }

                // Click action to open MainActivity on that day
                val pi = WidgetUpdateManager.createDayPendingIntent(
                    context,
                    date,
                    appWidgetId * 100 + i
                )
                views.setOnClickPendingIntent(colIds[i], pi)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WeeksPanoramaWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                WeeksPanoramaWidgetProvider().onUpdate(context, manager, ids)
            }
        }
    }
}
