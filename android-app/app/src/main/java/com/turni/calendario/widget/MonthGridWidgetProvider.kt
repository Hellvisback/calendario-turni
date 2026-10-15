package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import com.turni.calendario.R
import com.turni.calendario.model.ShiftCategory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MonthGridWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val today = LocalDate.now()
        val yearMonth = YearMonth.now()
        val firstDay = yearMonth.atDay(1)
        val dayOfWeekOffset = firstDay.dayOfWeek.value - 1 // 0 for Monday, 6 for Sunday
        val startDate = firstDay.minusDays(dayOfWeekOffset.toLong())

        val cellIds = intArrayOf(
            R.id.mg_cell_0, R.id.mg_cell_1, R.id.mg_cell_2, R.id.mg_cell_3, R.id.mg_cell_4, R.id.mg_cell_5, R.id.mg_cell_6,
            R.id.mg_cell_7, R.id.mg_cell_8, R.id.mg_cell_9, R.id.mg_cell_10, R.id.mg_cell_11, R.id.mg_cell_12, R.id.mg_cell_13,
            R.id.mg_cell_14, R.id.mg_cell_15, R.id.mg_cell_16, R.id.mg_cell_17, R.id.mg_cell_18, R.id.mg_cell_19, R.id.mg_cell_20,
            R.id.mg_cell_21, R.id.mg_cell_22, R.id.mg_cell_23, R.id.mg_cell_24, R.id.mg_cell_25, R.id.mg_cell_26, R.id.mg_cell_27,
            R.id.mg_cell_28, R.id.mg_cell_29, R.id.mg_cell_30, R.id.mg_cell_31, R.id.mg_cell_32, R.id.mg_cell_33, R.id.mg_cell_34
        )
        val numIds = intArrayOf(
            R.id.mg_num_0, R.id.mg_num_1, R.id.mg_num_2, R.id.mg_num_3, R.id.mg_num_4, R.id.mg_num_5, R.id.mg_num_6,
            R.id.mg_num_7, R.id.mg_num_8, R.id.mg_num_9, R.id.mg_num_10, R.id.mg_num_11, R.id.mg_num_12, R.id.mg_num_13,
            R.id.mg_num_14, R.id.mg_num_15, R.id.mg_num_16, R.id.mg_num_17, R.id.mg_num_18, R.id.mg_num_19, R.id.mg_num_20,
            R.id.mg_num_21, R.id.mg_num_22, R.id.mg_num_23, R.id.mg_num_24, R.id.mg_num_25, R.id.mg_num_26, R.id.mg_num_27,
            R.id.mg_num_28, R.id.mg_num_29, R.id.mg_num_30, R.id.mg_num_31, R.id.mg_num_32, R.id.mg_num_33, R.id.mg_num_34
        )
        val codeIds = intArrayOf(
            R.id.mg_code_0, R.id.mg_code_1, R.id.mg_code_2, R.id.mg_code_3, R.id.mg_code_4, R.id.mg_code_5, R.id.mg_code_6,
            R.id.mg_code_7, R.id.mg_code_8, R.id.mg_code_9, R.id.mg_code_10, R.id.mg_code_11, R.id.mg_code_12, R.id.mg_code_13,
            R.id.mg_code_14, R.id.mg_code_15, R.id.mg_code_16, R.id.mg_code_17, R.id.mg_code_18, R.id.mg_code_19, R.id.mg_code_20,
            R.id.mg_code_21, R.id.mg_code_22, R.id.mg_code_23, R.id.mg_code_24, R.id.mg_code_25, R.id.mg_code_26, R.id.mg_code_27,
            R.id.mg_code_28, R.id.mg_code_29, R.id.mg_code_30, R.id.mg_code_31, R.id.mg_code_32, R.id.mg_code_33, R.id.mg_code_34
        )

        // Month stats
        var workCount = 0
        var restCount = 0
        for (day in 1..yearMonth.lengthOfMonth()) {
            val d = yearMonth.atDay(day)
            val s = WidgetUpdateManager.getDaySummary(d, today)
            if (s.shiftDef != null && s.shiftDef.category == ShiftCategory.LAVORO) {
                workCount++
            } else {
                restCount++
            }
        }

        val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
        val titleText = yearMonth.format(monthFmt).replaceFirstChar { it.uppercase() }

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_month_grid)

            views.setTextViewText(R.id.widget_mg_month_title, titleText)
            views.setTextViewText(R.id.widget_mg_stats_pill, "$workCount Turni • $restCount Riposi")

            for (i in 0..34) {
                val date = startDate.plusDays(i.toLong())
                val isCurrentMonth = (date.month == yearMonth.month)
                val summary = WidgetUpdateManager.getDaySummary(date, today)

                views.setTextViewText(numIds[i], date.dayOfMonth.toString())

                if (isCurrentMonth) {
                    views.setTextViewText(codeIds[i], summary.shortCode)
                    views.setInt(
                        codeIds[i],
                        "setBackgroundResource",
                        WidgetUpdateManager.getBadgeDrawableRes(summary.shiftDef)
                    )

                    if (summary.isToday) {
                        views.setInt(cellIds[i], "setBackgroundResource", R.drawable.bg_widget_today_highlight)
                        views.setTextColor(numIds[i], Color.parseColor("#38BDF8"))
                    } else {
                        views.setInt(cellIds[i], "setBackgroundColor", Color.TRANSPARENT)
                        if (i % 7 == 6) { // Domenica
                            views.setTextColor(numIds[i], Color.parseColor("#FCA5A5"))
                        } else if (i % 7 == 5) { // Sabato
                            views.setTextColor(numIds[i], Color.parseColor("#94A3B8"))
                        } else {
                            views.setTextColor(numIds[i], Color.parseColor("#F1F5F9"))
                        }
                    }

                    val pi = WidgetUpdateManager.createDayPendingIntent(
                        context,
                        date,
                        appWidgetId * 1000 + i
                    )
                    views.setOnClickPendingIntent(cellIds[i], pi)
                } else {
                    // Day outside month
                    views.setTextViewText(codeIds[i], "")
                    views.setInt(codeIds[i], "setBackgroundColor", Color.TRANSPARENT)
                    views.setInt(cellIds[i], "setBackgroundColor", Color.TRANSPARENT)
                    views.setTextColor(numIds[i], Color.parseColor("#334155"))
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MonthGridWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                MonthGridWidgetProvider().onUpdate(context, manager, ids)
            }
        }
    }
}
