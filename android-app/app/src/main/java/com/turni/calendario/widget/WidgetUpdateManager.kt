package com.turni.calendario.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.turni.calendario.MainActivity
import com.turni.calendario.R
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.ShiftRepository
import java.time.DayOfWeek
import java.time.LocalDate

data class DayShiftSummary(
    val date: LocalDate,
    val dayNameShort: String,
    val dayNumber: Int,
    val isToday: Boolean,
    val shiftDef: ShiftDefinition?,
    val shortCode: String,
    val shiftName: String,
    val timeRange: String,
    val overtimeHours: Float,
    val isOnCall: Boolean,
    val isTrasferta: Boolean,
    val notes: String = "",
    val hasMultipleShifts: Boolean = false
) {
    val displayShortCode: String
        get() = if (hasMultipleShifts) "$shortCode •" else shortCode
}

object WidgetUpdateManager {

    const val EXTRA_SELECTED_DATE = "EXTRA_SELECTED_DATE"

    fun getDaySummary(date: LocalDate, today: LocalDate = LocalDate.now()): DayShiftSummary {
        val allAssigned = ShiftRepository.getShiftsForDate(date)
        val assigned = allAssigned.firstOrNull()
        val hasMultipleShifts = allAssigned.size > 1
        val shiftDef = if (assigned != null) {
            ShiftRepository.getShiftDefinition(assigned.shiftDefId)
        } else {
            ShiftRepository.getPlannedRotationShift(date)
        }
        val extra = ShiftRepository.getDayExtraInfo(date)
        val isOnCall = ShiftRepository.isOnCall(date)
        val combinedNotes = (assigned?.notes?.ifBlank { null } ?: extra.notes).trim()

        val dayLetter = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "L"
            DayOfWeek.TUESDAY -> "M"
            DayOfWeek.WEDNESDAY -> "M"
            DayOfWeek.THURSDAY -> "G"
            DayOfWeek.FRIDAY -> "V"
            DayOfWeek.SATURDAY -> "S"
            DayOfWeek.SUNDAY -> "D"
            else -> "L"
        }

        return DayShiftSummary(
            date = date,
            dayNameShort = dayLetter,
            dayNumber = date.dayOfMonth,
            isToday = (date == today),
            shiftDef = shiftDef,
            shortCode = shiftDef?.shortCode ?: "RIP",
            shiftName = shiftDef?.name ?: "Riposo",
            timeRange = shiftDef?.timeRangeFormatted ?: "",
            overtimeHours = extra.overtimeHours,
            isOnCall = isOnCall,
            isTrasferta = extra.isTrasferta,
            notes = combinedNotes,
            hasMultipleShifts = hasMultipleShifts
        )
    }

    fun getBadgeDrawableRes(shiftDef: ShiftDefinition?): Int {
        if (shiftDef == null) return R.drawable.bg_badge_pink // Riposo programmato nell'app è Fucsia
        return when (shiftDef.id.lowercase()) {
            "pomeriggio", "pom" -> R.drawable.bg_badge_red      // #EF4444 Rosso corallo
            "mattina", "mat" -> R.drawable.bg_badge_sky          // #0284C7 Azzurro avio
            "notte", "not" -> R.drawable.bg_badge_green         // #22C55E Verde chiaro
            "smonto_notte", "smo", "smonto" -> R.drawable.bg_badge_cyan // #06B6D4 Ciano
            "riposo", "rip" -> R.drawable.bg_badge_pink         // #EC4899 Fucsia
            "mat_dom", "dom", "md" -> R.drawable.bg_badge_amber // #F59E0B Giallo ambra
            "ferie", "fer" -> R.drawable.bg_badge_blue          // #2563EB Blu cobalto
            "giornaliero", "gio" -> R.drawable.bg_badge_emerald // #10B981 Verde smeraldo
            "uffici", "uff" -> R.drawable.bg_badge_gray         // #64748B Grigio ardesia
            else -> {
                when (shiftDef.category) {
                    ShiftCategory.RIPOSO -> R.drawable.bg_badge_pink
                    ShiftCategory.ASSENZA -> R.drawable.bg_badge_blue
                    else -> R.drawable.bg_badge_green
                }
            }
        }
    }

    fun getFadedBadgeDrawableRes(shiftDef: ShiftDefinition?): Int {
        if (shiftDef == null) return R.drawable.bg_badge_pink_faded
        return when (shiftDef.id.lowercase()) {
            "pomeriggio", "pom" -> R.drawable.bg_badge_red_faded
            "mattina", "mat" -> R.drawable.bg_badge_sky_faded
            "notte", "not" -> R.drawable.bg_badge_green_faded
            "smonto_notte", "smo", "smonto" -> R.drawable.bg_badge_cyan_faded
            "riposo", "rip" -> R.drawable.bg_badge_pink_faded
            "mat_dom", "dom", "md" -> R.drawable.bg_badge_amber_faded
            "ferie", "fer" -> R.drawable.bg_badge_blue_faded
            "giornaliero", "gio" -> R.drawable.bg_badge_emerald_faded
            "uffici", "uff" -> R.drawable.bg_badge_gray_faded
            else -> {
                when (shiftDef.category) {
                    ShiftCategory.RIPOSO -> R.drawable.bg_badge_pink_faded
                    ShiftCategory.ASSENZA -> R.drawable.bg_badge_blue_faded
                    else -> R.drawable.bg_badge_green_faded
                }
            }
        }
    }

    fun createDayPendingIntent(context: Context, date: LocalDate, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_SELECTED_DATE, date.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val ACTION_NAV_NEXT = "com.turni.calendario.widget.ACTION_NAV_NEXT"
    const val ACTION_NAV_PREV = "com.turni.calendario.widget.ACTION_NAV_PREV"
    const val ACTION_NAV_RESET = "com.turni.calendario.widget.ACTION_NAV_RESET"

    fun createNavPendingIntent(context: Context, appWidgetId: Int, action: String, targetClass: Class<*>): PendingIntent {
        val intent = Intent(context, targetClass).apply {
            this.action = action
            this.setPackage(context.packageName)
            this.data = android.net.Uri.parse("widget://nav/$appWidgetId/$action")
            putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val reqCode = appWidgetId * 1000 + (if (action == ACTION_NAV_NEXT) 1 else if (action == ACTION_NAV_PREV) 2 else 0)
        return PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun getWidgetOffset(context: Context, appWidgetId: Int): Int {
        val prefs = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
        return prefs.getInt("offset_$appWidgetId", 0)
    }

    fun setWidgetOffset(context: Context, appWidgetId: Int, offset: Int) {
        val prefs = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
        prefs.edit().putInt("offset_$appWidgetId", offset).apply()
    }

    fun updateAllWidgets(context: Context) {
        try {
            WeeksPanoramaWidgetProvider.updateAll(context)
            TodayTomorrowWidgetProvider.updateAll(context)
            WeekRowWidgetProvider.updateAll(context)
            MonthGridWidgetProvider.updateAll(context)
        } catch (_: Exception) {
        }
    }

    fun notifyWidgetsChanged(context: Context) {
        updateAllWidgets(context)
    }
}
