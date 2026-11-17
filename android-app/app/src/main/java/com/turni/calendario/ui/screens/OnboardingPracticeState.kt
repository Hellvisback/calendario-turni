package com.turni.calendario.ui.screens

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
