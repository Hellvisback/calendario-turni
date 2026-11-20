package com.turni.calendario.ui.screens

import com.turni.calendario.model.ReperibilitaConfig
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.model.SmartAlarmsConfig
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A self-contained rehearsal: no repository references or persistence hooks. */
internal data class CalendarPracticeState(
    val days: List<List<String>> = List(14) { emptyList() },
    val bells: Set<Int> = emptySet(),
    val notes: Set<Int> = emptySet(),
    val overtime: Set<Int> = emptySet(),
    val travel: Set<Int> = emptySet()
)

internal fun initialCalendarPractice(modelIds: List<String>): CalendarPracticeState {
    require(modelIds.isNotEmpty())
    val pattern = listOf(0, 0, 1, 1, 2, 3, 3, 0, 0, 1, 2, 3, 4, 3)
    return CalendarPracticeState(
        days = pattern.map { listOf(modelIds[it % modelIds.size]) },
        bells = setOf(9), notes = setOf(2), overtime = setOf(7)
    )
}

internal fun applyCalendarPractice(
    state: CalendarPracticeState,
    anchor: Int,
    end: Int,
    brush: String,
    mode: CalendarEditMode,
    erasing: Boolean,
    cycle: List<String>
): CalendarPracticeState {
    require(anchor in state.days.indices && end in state.days.indices)
    val targets = minOf(anchor, end)..maxOf(anchor, end)
    val updated = state.days.mapIndexed { index, values ->
        if (index !in targets) values else when {
            erasing -> emptyList()
            mode == CalendarEditMode.ADD -> values + brush
            mode == CalendarEditMode.ROTATION -> {
                require(cycle.isNotEmpty())
                listOf(cycle[(index - targets.first) % cycle.size])
            }
            else -> listOf(brush)
        }
    }
    return state.copy(
        days = updated,
        bells = if (erasing) state.bells - targets.toSet() else state.bells,
        overtime = if (erasing) state.overtime - targets.toSet() else state.overtime,
        travel = if (erasing) state.travel - targets.toSet() else state.travel
    )
}

/** Two complete weeks, even when the example crosses a month or year boundary. */
internal fun calendarPracticeDates(today: LocalDate, weekStartMonday: Boolean): List<LocalDate> {
    val start = today.with(TemporalAdjusters.previousOrSame(if (weekStartMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY))
    return List(14) { start.plusDays(it.toLong()) }
}

/** Map an interactive rehearsal action to the matching onboarding lesson index (0..5). */
internal fun completedLessonForAction(mode: CalendarEditMode, tool: String?, affectedDays: Int): Int = when {
    tool == "eraser" || tool == "bell" -> 3
    mode == CalendarEditMode.ROTATION -> 4
    mode == CalendarEditMode.ADD -> 2
    affectedDays > 1 -> 1
    else -> 0
}

/** Concise status string for the on-call entry in the final onboarding step. */
internal fun reperibilitaSetupSummary(config: ReperibilitaConfig): String = when (config.mode) {
    ReperibilitaMode.MANUALE -> {
        val count = config.manualDates.size
        if (count == 0) "Manuale · nessuna data attiva"
        else "Manuale · $count ${if (count == 1) "data" else "date"}"
    }
    ReperibilitaMode.TURNO_SPECIFICO -> {
        val count = config.shiftSpecificIds.size
        if (count == 0) "Per turno · nessun turno scelto"
        else "Per turno · $count ${if (count == 1) "turno" else "turni"}"
    }
    ReperibilitaMode.ALTERNATA_STAGIONALE -> "Stagionale · regole invernali ed estive"
}

/** Concise status string for smart alarms in the final onboarding step. */
internal fun alarmsSetupSummary(config: SmartAlarmsConfig): String {
    if (!config.isGlobalEnabled) return "Spente · attivabili quando vuoi"
    val activeRules = config.rules.count { it.isEnabled }
    return if (activeRules == 0) "Attive · nessuna regola"
    else "Attive · $activeRules ${if (activeRules == 1) "regola" else "regole"}"
}
