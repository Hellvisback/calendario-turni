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
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, TodayTomorrowWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            onUpdate(context, manager, ids)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_today_tomorrow)

            val todaySummary = WidgetUpdateManager.getDaySummary(today, today)
            val tomorrowSummary = WidgetUpdateManager.getDaySummary(tomorrow, today)

            // Header date nitida per esteso (es. "Sabato 17 Ottobre")
            val formattedDate = today.format(dateFmt).replaceFirstChar { it.uppercase() }
            views.setTextViewText(R.id.widget_tt_header_date, formattedDate)

            // Turno di Oggi
            views.setTextViewText(R.id.widget_tt_shift_name, todaySummary.shiftName)
            views.setTextViewText(R.id.widget_tt_code_pill, todaySummary.displayShortCode)
            views.setInt(
                R.id.widget_tt_code_pill,
                "setBackgroundResource",
                WidgetUpdateManager.getBadgeDrawableRes(todaySummary.shiftDef)
            )

            // Pallini colorati sotto il turno se ci sono turni multipli oggi
            val todayDots = todaySummary.secondaryShiftColors
            if (todayDots.isNotEmpty()) {
                views.setViewVisibility(R.id.widget_tt_dots, View.VISIBLE)
                if (todayDots.size >= 1) {
                    views.setViewVisibility(R.id.widget_tt_dot_1, View.VISIBLE)
                    views.setInt(R.id.widget_tt_dot_1, "setColorFilter", todayDots[0])
                } else {
                    views.setViewVisibility(R.id.widget_tt_dot_1, View.GONE)
                }
                if (todayDots.size >= 2) {
                    views.setViewVisibility(R.id.widget_tt_dot_2, View.VISIBLE)
                    views.setInt(R.id.widget_tt_dot_2, "setColorFilter", todayDots[1])
                } else {
                    views.setViewVisibility(R.id.widget_tt_dot_2, View.GONE)
                }
                if (todayDots.size >= 3) {
                    views.setViewVisibility(R.id.widget_tt_dot_3, View.VISIBLE)
                    views.setInt(R.id.widget_tt_dot_3, "setColorFilter", todayDots[2])
                } else {
                    views.setViewVisibility(R.id.widget_tt_dot_3, View.GONE)
                }
            } else {
                views.setViewVisibility(R.id.widget_tt_dots, View.GONE)
            }

            val hoursText = if (todaySummary.timeRange.isNotBlank()) {
                todaySummary.timeRange
            } else {
                "Riposo programmato"
            }
            views.setTextViewText(R.id.widget_tt_shift_hours, hoursText)

            // Indicatore Straordinario
            if (todaySummary.overtimeHours > 0f) {
                val otFormatted = if (todaySummary.overtimeHours % 1f == 0f) {
                    "+${todaySummary.overtimeHours.toInt()}h Straordinario"
                } else {
                    "+${todaySummary.overtimeHours}h Straordinario"
                }
                views.setTextViewText(R.id.widget_tt_overtime_badge, otFormatted)
                views.setViewVisibility(R.id.widget_tt_overtime_badge, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_tt_overtime_badge, View.GONE)
            }

            // Indicatore Reperibilità
            if (todaySummary.isOnCall) {
                views.setTextViewText(R.id.widget_tt_oncall_badge, "🔔 Reperibile")
                views.setViewVisibility(R.id.widget_tt_oncall_badge, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_tt_oncall_badge, View.GONE)
            }

            // Indicatore Trasferta (Verde vivid)
            if (todaySummary.isTrasferta) {
                views.setTextViewText(R.id.widget_tt_trasferta_badge, "✈ Trasferta")
                views.setViewVisibility(R.id.widget_tt_trasferta_badge, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_tt_trasferta_badge, View.GONE)
            }

            // Note se presenti
            if (todaySummary.notes.isNotBlank()) {
                views.setTextViewText(R.id.widget_tt_notes, "📝 ${todaySummary.notes}")
                views.setViewVisibility(R.id.widget_tt_notes, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_tt_notes, View.GONE)
            }

            // Card Domani: Spaziosa a piena larghezza con nome ed eventuale orario
            views.setTextViewText(R.id.widget_tt_tomorrow_badge, tomorrowSummary.displayShortCode)
            views.setInt(
                R.id.widget_tt_tomorrow_badge,
                "setBackgroundResource",
                WidgetUpdateManager.getBadgeDrawableRes(tomorrowSummary.shiftDef)
            )

            val tomorrowDetail = if (tomorrowSummary.timeRange.isNotBlank() && tomorrowSummary.timeRange.contains(":")) {
                "${tomorrowSummary.shiftName} • ${tomorrowSummary.timeRange}"
            } else {
                tomorrowSummary.shiftName
            }
            views.setTextViewText(R.id.widget_tt_tomorrow_shift, tomorrowDetail)

            // Deep link per aprire l'app sulla data odierna
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
