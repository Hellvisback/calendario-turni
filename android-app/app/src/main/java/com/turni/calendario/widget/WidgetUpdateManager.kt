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
    val isTrasferta: Boolean
)

object WidgetUpdateManager {

    const val EXTRA_SELECTED_DATE = "EXTRA_SELECTED_DATE"

    fun getDaySummary(date: LocalDate, today: LocalDate = LocalDate.now()): DayShiftSummary {
        val assigned = ShiftRepository.getShiftsForDate(date).firstOrNull()
        val shiftDef = if (assigned != null) {
            ShiftRepository.getShiftDefinition(assigned.shiftDefId)
        } else {
            ShiftRepository.getPlannedRotationShift(date)
        }
        val extra = ShiftRepository.getDayExtraInfo(date)
        val isOnCall = ShiftRepository.isOnCall(date)

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
            isTrasferta = extra.isTrasferta
        )
    }

    fun getBadgeDrawableRes(shiftDef: ShiftDefinition?): Int {
        if (shiftDef == null) return R.drawable.bg_badge_gray
        return when (shiftDef.id.lowercase()) {
            "mattina", "mat" -> R.drawable.bg_badge_cyan
            "pomeriggio", "pom" -> R.drawable.bg_badge_red
            "notte", "not" -> R.drawable.bg_badge_purple
            "smonto_notte", "smo", "smonto" -> R.drawable.bg_badge_slate
            "riposo", "rip" -> R.drawable.bg_badge_gray
            "ferie", "fer" -> R.drawable.bg_badge_amber
            "mat_dom", "md" -> R.drawable.bg_badge_pink
            else -> {
                when (shiftDef.category) {
                    ShiftCategory.RIPOSO -> R.drawable.bg_badge_gray
                    ShiftCategory.ASSENZA -> R.drawable.bg_badge_amber
                    else -> R.drawable.bg_badge_green
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
