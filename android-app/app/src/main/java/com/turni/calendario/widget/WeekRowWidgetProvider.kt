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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class WeekRowWidgetProvider : AppWidgetProvider() {

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

        val cellIds = intArrayOf(
            R.id.cell_wr_0, R.id.cell_wr_1, R.id.cell_wr_2, R.id.cell_wr_3,
            R.id.cell_wr_4, R.id.cell_wr_5, R.id.cell_wr_6
        )
        val nameIds = intArrayOf(
            R.id.wr_day_name_0, R.id.wr_day_name_1, R.id.wr_day_name_2, R.id.wr_day_name_3,
            R.id.wr_day_name_4, R.id.wr_day_name_5, R.id.wr_day_name_6
        )
        val numIds = intArrayOf(
            R.id.wr_day_num_0, R.id.wr_day_num_1, R.id.wr_day_num_2, R.id.wr_day_num_3,
            R.id.wr_day_num_4, R.id.wr_day_num_5, R.id.wr_day_num_6
        )
        val codeIds = intArrayOf(
            R.id.wr_shift_code_0, R.id.wr_shift_code_1, R.id.wr_shift_code_2, R.id.wr_shift_code_3,
            R.id.wr_shift_code_4, R.id.wr_shift_code_5, R.id.wr_shift_code_6
        )
        val dotsContainerIds = intArrayOf(
            R.id.wr_dots_0, R.id.wr_dots_1, R.id.wr_dots_2, R.id.wr_dots_3,
            R.id.wr_dots_4, R.id.wr_dots_5, R.id.wr_dots_6
        )
        val dot1Ids = intArrayOf(
            R.id.wr_dot_0_1, R.id.wr_dot_1_1, R.id.wr_dot_2_1, R.id.wr_dot_3_1,
            R.id.wr_dot_4_1, R.id.wr_dot_5_1, R.id.wr_dot_6_1
        )
        val dot2Ids = intArrayOf(
            R.id.wr_dot_0_2, R.id.wr_dot_1_2, R.id.wr_dot_2_2, R.id.wr_dot_3_2,
            R.id.wr_dot_4_2, R.id.wr_dot_5_2, R.id.wr_dot_6_2
        )
        val dot3Ids = intArrayOf(
            R.id.wr_dot_0_3, R.id.wr_dot_1_3, R.id.wr_dot_2_3, R.id.wr_dot_3_3,
            R.id.wr_dot_4_3, R.id.wr_dot_5_3, R.id.wr_dot_6_3
        )

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_week_row)
            val weekOffset = WidgetUpdateManager.getWidgetOffset(context, appWidgetId)
            val monday = baseMonday.plusWeeks(weekOffset.toLong())

            val nextPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_NEXT, WeekRowWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.btn_wr_next, nextPi)

            val prevPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_PREV, WeekRowWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.btn_wr_prev, prevPi)

            if (weekOffset > 0) {
                views.setViewVisibility(R.id.btn_wr_prev, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.btn_wr_prev, View.GONE)
            }

            for (i in 0..6) {
                val date = monday.plusDays(i.toLong())
                val summary = WidgetUpdateManager.getDaySummary(date, today)

                views.setTextViewText(nameIds[i], summary.dayNameShort)
                views.setTextViewText(numIds[i], summary.dayNumber.toString())
                views.setTextViewText(codeIds[i], summary.displayShortCode)

                // Badge color
                views.setInt(
                    codeIds[i],
                    "setBackgroundResource",
                    WidgetUpdateManager.getBadgeDrawableRes(summary.shiftDef)
                )

                // Today & On-Call styling
                if (summary.isOnCall && summary.isToday) {
                    views.setInt(cellIds[i], "setBackgroundResource", R.drawable.bg_widget_today_oncall)
                    views.setTextColor(nameIds[i], Color.parseColor("#38BDF8"))
                    views.setTextColor(numIds[i], Color.parseColor("#38BDF8"))
                } else if (summary.isOnCall) {
                    views.setInt(cellIds[i], "setBackgroundResource", R.drawable.bg_widget_col_oncall)
                    views.setTextColor(nameIds[i], Color.parseColor("#FDE68A"))
                    views.setTextColor(numIds[i], Color.WHITE)
                } else if (summary.isToday) {
                    views.setInt(cellIds[i], "setBackgroundResource", R.drawable.bg_widget_today_highlight)
                    views.setTextColor(nameIds[i], Color.parseColor("#38BDF8"))
                    views.setTextColor(numIds[i], Color.parseColor("#38BDF8"))
                } else {
                    views.setInt(cellIds[i], "setBackgroundColor", Color.TRANSPARENT)
                    if (i == 6) { // Domenica
                        views.setTextColor(nameIds[i], Color.parseColor("#F87171"))
                        views.setTextColor(numIds[i], Color.parseColor("#FCA5A5"))
                    } else if (i == 5) { // Sabato
                        views.setTextColor(nameIds[i], Color.parseColor("#94A3B8"))
                        views.setTextColor(numIds[i], Color.parseColor("#F1F5F9"))
                    } else {
                        views.setTextColor(nameIds[i], Color.parseColor("#64748B"))
                        views.setTextColor(numIds[i], Color.parseColor("#F1F5F9"))
                    }
                }

                // Gestione pallini colorati separati sotto il turno per turni multipli
                val secondaryColors = summary.secondaryShiftColors
                if (secondaryColors.isNotEmpty()) {
                    views.setViewVisibility(dotsContainerIds[i], View.VISIBLE)
                    if (secondaryColors.size >= 1) {
                        views.setViewVisibility(dot1Ids[i], View.VISIBLE)
                        views.setInt(dot1Ids[i], "setColorFilter", secondaryColors[0])
                    } else {
                        views.setViewVisibility(dot1Ids[i], View.GONE)
                    }
                    if (secondaryColors.size >= 2) {
                        views.setViewVisibility(dot2Ids[i], View.VISIBLE)
                        views.setInt(dot2Ids[i], "setColorFilter", secondaryColors[1])
                    } else {
                        views.setViewVisibility(dot2Ids[i], View.GONE)
                    }
                    if (secondaryColors.size >= 3) {
                        views.setViewVisibility(dot3Ids[i], View.VISIBLE)
                        views.setInt(dot3Ids[i], "setColorFilter", secondaryColors[2])
                    } else {
                        views.setViewVisibility(dot3Ids[i], View.GONE)
                    }
                } else {
                    views.setViewVisibility(dotsContainerIds[i], View.GONE)
                }

                // Click action
                val pi = WidgetUpdateManager.createDayPendingIntent(
                    context,
                    date,
                    appWidgetId * 100 + i
                )
                views.setOnClickPendingIntent(cellIds[i], pi)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WeekRowWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                WeekRowWidgetProvider().onUpdate(context, manager, ids)
            }
        }
    }
}
