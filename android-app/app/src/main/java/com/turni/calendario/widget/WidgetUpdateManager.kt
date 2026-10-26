package com.turni.calendario.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.snapshots.Snapshot
import com.turni.calendario.MainActivity
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.ShiftRepository
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlin.math.roundToInt

data class WidgetShift(
    val id: String,
    val definition: ShiftDefinition,
    val startTime: String,
    val overtimeHours: Float,
    val isTrasferta: Boolean,
    val notes: String
)

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
    val hasMultipleShifts: Boolean = false,
    val secondaryShiftColors: List<Int> = emptyList(),
    val shifts: List<WidgetShift> = emptyList(),
    val holidayName: String? = null
) {
    val displayShortCode: String get() = shortCode
    val accessibilityLabel: String get() = buildString {
        append(date.toString())
        if (isToday) append(", oggi")
        holidayName?.let { append(", festività: ").append(it) }
        shifts.forEach {
            append(", ").append(it.definition.name).append(" ").append(it.startTime)
        }
        if (shifts.isEmpty()) append(", nessun turno")
        if (isOnCall) append(", reperibile")
        append(". Apri il dettaglio del giorno")
    }
}

internal fun widgetOvertimeLabel(hours: Float): String {
    val minutes = if (hours.isFinite()) (hours.coerceAtLeast(0f) * 60f).roundToInt() else 0
    return when {
        minutes < 60 -> "+${minutes}m"
        minutes % 60 == 0 -> "+${minutes / 60}h"
        else -> "+${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"
    }
}

object WidgetUpdateManager {
    const val EXTRA_SELECTED_DATE = "EXTRA_SELECTED_DATE"
    const val ACTION_NAV_NEXT = "com.turni.calendario.widget.ACTION_NAV_NEXT"
    const val ACTION_NAV_PREV = "com.turni.calendario.widget.ACTION_NAV_PREV"
    const val ACTION_NAV_RESET = "com.turni.calendario.widget.ACTION_NAV_RESET"
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "TurniSyncWidgets") }

    fun getDaySummary(date: LocalDate, today: LocalDate = LocalDate.now()): DayShiftSummary {
        // Match the selected work calendar, including all of its per-shift metadata.
        val shifts = ShiftRepository.getShiftsForDate(date).mapNotNull { assigned ->
            val definition = ShiftRepository.getShiftDefinition(assigned.shiftDefId) ?: return@mapNotNull null
            if (definition.workProfileId != ShiftRepository.activeWorkProfileId) return@mapNotNull null
            WidgetShift(assigned.id, definition,
                if (definition.isAllDay) "— —" else assigned.customStartTime ?: definition.startTime,
                assigned.overtimeHours, assigned.isTrasferta, assigned.notes)
        }
        val first = shifts.firstOrNull()
        return DayShiftSummary(
            date, listOf("L", "M", "M", "G", "V", "S", "D")[date.dayOfWeek.value - 1],
            date.dayOfMonth, date == today, first?.definition,
            first?.definition?.shortCode ?: "—", first?.definition?.name ?: "Nessun turno",
            first?.startTime ?: "", shifts.sumOf { it.overtimeHours.toDouble() }.toFloat(),
            ShiftRepository.isOnCall(date), shifts.any { it.isTrasferta },
            shifts.map { it.notes }.filter { it.isNotBlank() }.joinToString(" • "),
            shifts.size > 1, shifts.drop(1).map { it.definition.colorHex.toInt() }, shifts, ShiftRepository.isHoliday(date)?.name)
    }

    fun createDayPendingIntent(context: Context, date: LocalDate, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            // Extras alone do not distinguish PendingIntents belonging to different widgets.
            data = Uri.parse("turnisync://widget/day/$requestCode/$date")
            putExtra(EXTRA_SELECTED_DATE, date.toString())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun createNavPendingIntent(context: Context, appWidgetId: Int, action: String, targetClass: Class<*>): PendingIntent {
        val intent = Intent(context, targetClass).apply {
            this.action = action
            setPackage(context.packageName)
            data = Uri.parse("turnisync://widget/nav/$appWidgetId/$action")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun getWidgetOffset(context: Context, appWidgetId: Int): Int =
        context.getSharedPreferences("widget_state", Context.MODE_PRIVATE).getInt("offset_$appWidgetId", 0)

    fun setWidgetOffset(context: Context, appWidgetId: Int, offset: Int) {
        context.getSharedPreferences("widget_state", Context.MODE_PRIVATE).edit()
            .putInt("offset_$appWidgetId", offset.coerceIn(-120, 120)).apply()
    }

    internal fun enqueue(context: Context, kind: WidgetKind, ids: IntArray, finished: (() -> Unit)? = null) {
        val appContext = context.applicationContext
        executor.execute {
            val snapshot = Snapshot.takeSnapshot()
            try {
                snapshot.enter {
                    val manager = AppWidgetManager.getInstance(appContext)
                    ids.forEach { id ->
                        try { TurniWidgetRenderer.update(appContext, manager, kind, id) }
                        catch (error: Exception) { Log.e("TurniSyncWidgets", "Widget $id update failed", error) }
                    }
                }
            } finally { snapshot.dispose(); finished?.invoke() }
        }
    }

    internal fun updateKind(context: Context, kind: WidgetKind) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, kind.provider))
        if (ids.isNotEmpty()) enqueue(context, kind, ids)
    }

    fun updateAllWidgets(context: Context) { WidgetKind.values().forEach { updateKind(context, it) } }
    fun notifyWidgetsChanged(context: Context) = updateAllWidgets(context)
}
