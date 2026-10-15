package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import com.turni.calendario.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class WeekRowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val today = LocalDate.now()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

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

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_week_row)

            for (i in 0..6) {
                val date = monday.plusDays(i.toLong())
                val summary = WidgetUpdateManager.getDaySummary(date, today)

                views.setTextViewText(nameIds[i], summary.dayNameShort)
                views.setTextViewText(numIds[i], summary.dayNumber.toString())
                views.setTextViewText(codeIds[i], summary.shortCode)

                // Badge color
                views.setInt(
                    codeIds[i],
                    "setBackgroundResource",
                    WidgetUpdateManager.getBadgeDrawableRes(summary.shiftDef)
                )

                // Today highlight
                if (summary.isToday) {
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
