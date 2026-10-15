package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
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

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

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
        val monthLabel = today.format(monthFmt).replaceFirstChar { it.uppercase() }
        val rotationLabel = ShiftRepository.activeRotation?.name ?: "2 Settimane"

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_weeks_panorama)

            views.setTextViewText(R.id.widget_wp_title, monthLabel)
            views.setTextViewText(R.id.widget_wp_stats, rotationLabel)

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

                // Today highlight
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

                // Click action
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
