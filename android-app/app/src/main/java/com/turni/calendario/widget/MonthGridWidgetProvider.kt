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
import com.turni.calendario.model.ShiftCategory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MonthGridWidgetProvider : AppWidgetProvider() {

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
        val baseMonth = YearMonth.now()
        val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)

        val cellIds = intArrayOf(
            R.id.mg_cell_0,             R.id.mg_cell_1,             R.id.mg_cell_2,             R.id.mg_cell_3,             R.id.mg_cell_4,             R.id.mg_cell_5,             R.id.mg_cell_6,
            R.id.mg_cell_7,             R.id.mg_cell_8,             R.id.mg_cell_9,             R.id.mg_cell_10,             R.id.mg_cell_11,             R.id.mg_cell_12,             R.id.mg_cell_13,
            R.id.mg_cell_14,             R.id.mg_cell_15,             R.id.mg_cell_16,             R.id.mg_cell_17,             R.id.mg_cell_18,             R.id.mg_cell_19,             R.id.mg_cell_20,
            R.id.mg_cell_21,             R.id.mg_cell_22,             R.id.mg_cell_23,             R.id.mg_cell_24,             R.id.mg_cell_25,             R.id.mg_cell_26,             R.id.mg_cell_27,
            R.id.mg_cell_28,             R.id.mg_cell_29,             R.id.mg_cell_30,             R.id.mg_cell_31,             R.id.mg_cell_32,             R.id.mg_cell_33,             R.id.mg_cell_34,
            R.id.mg_cell_35,             R.id.mg_cell_36,             R.id.mg_cell_37,             R.id.mg_cell_38,             R.id.mg_cell_39,             R.id.mg_cell_40,             R.id.mg_cell_41
        )
        val numIds = intArrayOf(
            R.id.mg_num_0,             R.id.mg_num_1,             R.id.mg_num_2,             R.id.mg_num_3,             R.id.mg_num_4,             R.id.mg_num_5,             R.id.mg_num_6,
            R.id.mg_num_7,             R.id.mg_num_8,             R.id.mg_num_9,             R.id.mg_num_10,             R.id.mg_num_11,             R.id.mg_num_12,             R.id.mg_num_13,
            R.id.mg_num_14,             R.id.mg_num_15,             R.id.mg_num_16,             R.id.mg_num_17,             R.id.mg_num_18,             R.id.mg_num_19,             R.id.mg_num_20,
            R.id.mg_num_21,             R.id.mg_num_22,             R.id.mg_num_23,             R.id.mg_num_24,             R.id.mg_num_25,             R.id.mg_num_26,             R.id.mg_num_27,
            R.id.mg_num_28,             R.id.mg_num_29,             R.id.mg_num_30,             R.id.mg_num_31,             R.id.mg_num_32,             R.id.mg_num_33,             R.id.mg_num_34,
            R.id.mg_num_35,             R.id.mg_num_36,             R.id.mg_num_37,             R.id.mg_num_38,             R.id.mg_num_39,             R.id.mg_num_40,             R.id.mg_num_41
        )
        val codeIds = intArrayOf(
            R.id.mg_code_0,             R.id.mg_code_1,             R.id.mg_code_2,             R.id.mg_code_3,             R.id.mg_code_4,             R.id.mg_code_5,             R.id.mg_code_6,
            R.id.mg_code_7,             R.id.mg_code_8,             R.id.mg_code_9,             R.id.mg_code_10,             R.id.mg_code_11,             R.id.mg_code_12,             R.id.mg_code_13,
            R.id.mg_code_14,             R.id.mg_code_15,             R.id.mg_code_16,             R.id.mg_code_17,             R.id.mg_code_18,             R.id.mg_code_19,             R.id.mg_code_20,
            R.id.mg_code_21,             R.id.mg_code_22,             R.id.mg_code_23,             R.id.mg_code_24,             R.id.mg_code_25,             R.id.mg_code_26,             R.id.mg_code_27,
            R.id.mg_code_28,             R.id.mg_code_29,             R.id.mg_code_30,             R.id.mg_code_31,             R.id.mg_code_32,             R.id.mg_code_33,             R.id.mg_code_34,
            R.id.mg_code_35,             R.id.mg_code_36,             R.id.mg_code_37,             R.id.mg_code_38,             R.id.mg_code_39,             R.id.mg_code_40,             R.id.mg_code_41
        )
        val dotsContainerIds = intArrayOf(
            R.id.mg_dots_0,             R.id.mg_dots_1,             R.id.mg_dots_2,             R.id.mg_dots_3,             R.id.mg_dots_4,             R.id.mg_dots_5,             R.id.mg_dots_6,
            R.id.mg_dots_7,             R.id.mg_dots_8,             R.id.mg_dots_9,             R.id.mg_dots_10,             R.id.mg_dots_11,             R.id.mg_dots_12,             R.id.mg_dots_13,
            R.id.mg_dots_14,             R.id.mg_dots_15,             R.id.mg_dots_16,             R.id.mg_dots_17,             R.id.mg_dots_18,             R.id.mg_dots_19,             R.id.mg_dots_20,
            R.id.mg_dots_21,             R.id.mg_dots_22,             R.id.mg_dots_23,             R.id.mg_dots_24,             R.id.mg_dots_25,             R.id.mg_dots_26,             R.id.mg_dots_27,
            R.id.mg_dots_28,             R.id.mg_dots_29,             R.id.mg_dots_30,             R.id.mg_dots_31,             R.id.mg_dots_32,             R.id.mg_dots_33,             R.id.mg_dots_34,
            R.id.mg_dots_35,             R.id.mg_dots_36,             R.id.mg_dots_37,             R.id.mg_dots_38,             R.id.mg_dots_39,             R.id.mg_dots_40,             R.id.mg_dots_41
        )
        val dot1Ids = intArrayOf(
            R.id.mg_dot_0_1,             R.id.mg_dot_1_1,             R.id.mg_dot_2_1,             R.id.mg_dot_3_1,             R.id.mg_dot_4_1,             R.id.mg_dot_5_1,             R.id.mg_dot_6_1,
            R.id.mg_dot_7_1,             R.id.mg_dot_8_1,             R.id.mg_dot_9_1,             R.id.mg_dot_10_1,             R.id.mg_dot_11_1,             R.id.mg_dot_12_1,             R.id.mg_dot_13_1,
            R.id.mg_dot_14_1,             R.id.mg_dot_15_1,             R.id.mg_dot_16_1,             R.id.mg_dot_17_1,             R.id.mg_dot_18_1,             R.id.mg_dot_19_1,             R.id.mg_dot_20_1,
            R.id.mg_dot_21_1,             R.id.mg_dot_22_1,             R.id.mg_dot_23_1,             R.id.mg_dot_24_1,             R.id.mg_dot_25_1,             R.id.mg_dot_26_1,             R.id.mg_dot_27_1,
            R.id.mg_dot_28_1,             R.id.mg_dot_29_1,             R.id.mg_dot_30_1,             R.id.mg_dot_31_1,             R.id.mg_dot_32_1,             R.id.mg_dot_33_1,             R.id.mg_dot_34_1,
            R.id.mg_dot_35_1,             R.id.mg_dot_36_1,             R.id.mg_dot_37_1,             R.id.mg_dot_38_1,             R.id.mg_dot_39_1,             R.id.mg_dot_40_1,             R.id.mg_dot_41_1
        )
        val dot2Ids = intArrayOf(
            R.id.mg_dot_0_2,             R.id.mg_dot_1_2,             R.id.mg_dot_2_2,             R.id.mg_dot_3_2,             R.id.mg_dot_4_2,             R.id.mg_dot_5_2,             R.id.mg_dot_6_2,
            R.id.mg_dot_7_2,             R.id.mg_dot_8_2,             R.id.mg_dot_9_2,             R.id.mg_dot_10_2,             R.id.mg_dot_11_2,             R.id.mg_dot_12_2,             R.id.mg_dot_13_2,
            R.id.mg_dot_14_2,             R.id.mg_dot_15_2,             R.id.mg_dot_16_2,             R.id.mg_dot_17_2,             R.id.mg_dot_18_2,             R.id.mg_dot_19_2,             R.id.mg_dot_20_2,
            R.id.mg_dot_21_2,             R.id.mg_dot_22_2,             R.id.mg_dot_23_2,             R.id.mg_dot_24_2,             R.id.mg_dot_25_2,             R.id.mg_dot_26_2,             R.id.mg_dot_27_2,
            R.id.mg_dot_28_2,             R.id.mg_dot_29_2,             R.id.mg_dot_30_2,             R.id.mg_dot_31_2,             R.id.mg_dot_32_2,             R.id.mg_dot_33_2,             R.id.mg_dot_34_2,
            R.id.mg_dot_35_2,             R.id.mg_dot_36_2,             R.id.mg_dot_37_2,             R.id.mg_dot_38_2,             R.id.mg_dot_39_2,             R.id.mg_dot_40_2,             R.id.mg_dot_41_2
        )
        val dot3Ids = intArrayOf(
            R.id.mg_dot_0_3,             R.id.mg_dot_1_3,             R.id.mg_dot_2_3,             R.id.mg_dot_3_3,             R.id.mg_dot_4_3,             R.id.mg_dot_5_3,             R.id.mg_dot_6_3,
            R.id.mg_dot_7_3,             R.id.mg_dot_8_3,             R.id.mg_dot_9_3,             R.id.mg_dot_10_3,             R.id.mg_dot_11_3,             R.id.mg_dot_12_3,             R.id.mg_dot_13_3,
            R.id.mg_dot_14_3,             R.id.mg_dot_15_3,             R.id.mg_dot_16_3,             R.id.mg_dot_17_3,             R.id.mg_dot_18_3,             R.id.mg_dot_19_3,             R.id.mg_dot_20_3,
            R.id.mg_dot_21_3,             R.id.mg_dot_22_3,             R.id.mg_dot_23_3,             R.id.mg_dot_24_3,             R.id.mg_dot_25_3,             R.id.mg_dot_26_3,             R.id.mg_dot_27_3,
            R.id.mg_dot_28_3,             R.id.mg_dot_29_3,             R.id.mg_dot_30_3,             R.id.mg_dot_31_3,             R.id.mg_dot_32_3,             R.id.mg_dot_33_3,             R.id.mg_dot_34_3,
            R.id.mg_dot_35_3,             R.id.mg_dot_36_3,             R.id.mg_dot_37_3,             R.id.mg_dot_38_3,             R.id.mg_dot_39_3,             R.id.mg_dot_40_3,             R.id.mg_dot_41_3
        )

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_month_grid)
            val monthOffset = WidgetUpdateManager.getWidgetOffset(context, appWidgetId)
            val yearMonth = baseMonth.plusMonths(monthOffset.toLong())
            val firstDay = yearMonth.atDay(1)
            val dayOfWeekOffset = firstDay.dayOfWeek.value - 1 // 0 per Lunedì, 6 per Domenica
            val startDate = firstDay.minusDays(dayOfWeekOffset.toLong())

            // Controllo se il mese si estende sulla 6ª riga (es. Novembre con 30 giorni che inizia di domenica)
            val totalDaysNeeded = dayOfWeekOffset + yearMonth.lengthOfMonth()
            val hasSixRows = totalDaysNeeded > 35
            views.setViewVisibility(R.id.mg_row_5, if (hasSixRows) View.VISIBLE else View.GONE)

            val titleText = yearMonth.format(monthFmt).replaceFirstChar { it.uppercase() }
            views.setTextViewText(R.id.widget_mg_month_title, titleText)

            // Navigation pending intents
            val nextPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_NEXT, MonthGridWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.btn_mg_next, nextPi)

            val prevPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_PREV, MonthGridWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.btn_mg_prev, prevPi)

            val resetPi = WidgetUpdateManager.createNavPendingIntent(
                context, appWidgetId, WidgetUpdateManager.ACTION_NAV_RESET, MonthGridWidgetProvider::class.java
            )
            views.setOnClickPendingIntent(R.id.widget_mg_month_title, resetPi)

            if (monthOffset > 0) {
                views.setViewVisibility(R.id.btn_mg_prev, View.VISIBLE)
                views.setViewVisibility(R.id.mg_nav_indicator, View.VISIBLE)
                views.setTextViewText(R.id.mg_nav_indicator, "+$monthOffset m.")
            } else {
                views.setViewVisibility(R.id.btn_mg_prev, View.GONE)
                views.setViewVisibility(R.id.mg_nav_indicator, View.GONE)
            }

            for (i in 0..41) {
                val date = startDate.plusDays(i.toLong())
                val isCurrentMonth = (date.month == yearMonth.month)
                val summary = WidgetUpdateManager.getDaySummary(date, today)

                views.setTextViewText(numIds[i], date.dayOfMonth.toString())
                views.setTextViewText(codeIds[i], summary.displayShortCode)

                if (isCurrentMonth) {
                    views.setInt(
                        codeIds[i],
                        "setBackgroundResource",
                        WidgetUpdateManager.getBadgeDrawableRes(summary.shiftDef)
                    )
                    views.setTextColor(codeIds[i], Color.WHITE)

                    if (summary.isOnCall && summary.isToday) {
                        views.setInt(cellIds[i], "setBackgroundResource", R.drawable.bg_widget_today_oncall)
                        views.setTextColor(numIds[i], Color.parseColor("#38BDF8"))
                    } else if (summary.isOnCall) {
                        views.setInt(cellIds[i], "setBackgroundResource", R.drawable.bg_widget_cell_oncall)
                        views.setTextColor(numIds[i], Color.parseColor("#FBBF24"))
                    } else if (summary.isToday) {
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
                } else {
                    // Giorno fuori dal mese corrente: visualizza turno con badge attenuato (faded)
                    views.setInt(
                        codeIds[i],
                        "setBackgroundResource",
                        WidgetUpdateManager.getFadedBadgeDrawableRes(summary.shiftDef)
                    )
                    views.setTextColor(codeIds[i], Color.parseColor("#99FFFFFF"))

                    if (summary.isOnCall) {
                        views.setInt(cellIds[i], "setBackgroundResource", R.drawable.bg_widget_cell_oncall)
                        views.setTextColor(numIds[i], Color.parseColor("#C28E10"))
                    } else {
                        views.setInt(cellIds[i], "setBackgroundColor", Color.TRANSPARENT)
                        if (i % 7 == 6) {
                            views.setTextColor(numIds[i], Color.parseColor("#80FCA5A5"))
                        } else {
                            views.setTextColor(numIds[i], Color.parseColor("#64748B"))
                        }
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

                val pi = WidgetUpdateManager.createDayPendingIntent(
                    context,
                    date,
                    appWidgetId * 1000 + i
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
                ComponentName(context, MonthGridWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                MonthGridWidgetProvider().onUpdate(context, manager, ids)
            }
        }
    }
}
