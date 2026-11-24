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

internal enum class PracticeTargetAction {
    TAP,
    LONG_PRESS
}

internal data class PracticeHighlightTarget(
    val dayIndex: Int? = null,
    val action: PracticeTargetAction = PracticeTargetAction.TAP,
    val brushId: String? = null,
    val mode: CalendarEditMode? = null,
    val tool: String? = null,
    val rotationId: String? = null
)

internal data class PracticeCoachEvaluation(
    val lessonIndex: Int,
    val subStepIndex: Int,
    val totalSubSteps: Int,
    val subStepTitle: String,
    val instruction: String,
    val hintInstruction: String,
    val target: PracticeHighlightTarget
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

internal fun applyBellPracticeRange(
    state: CalendarPracticeState,
    anchor: Int,
    end: Int
): Pair<CalendarPracticeState, Boolean> {
    require(anchor in state.days.indices && end in state.days.indices)
    val enable = anchor !in state.bells
    val targets = minOf(anchor, end)..maxOf(anchor, end)
    val updated = if (enable) state.bells + targets else state.bells - targets.toSet()
    return state.copy(bells = updated) to enable
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

/**
 * Resolve the recommended shift model to pick during Lesson 0 (prefers "Pomeriggio" / index 1
 * when available so the user visibly switches away from the initial "Mattina" brush).
 */
internal fun recommendedPracticeBrushId(modelIds: List<String>): String {
    require(modelIds.isNotEmpty())
    return modelIds.firstOrNull { it.contains("afternoon", ignoreCase = true) || it.contains("pomeriggio", ignoreCase = true) }
        ?: modelIds.getOrElse(1) { modelIds.first() }
}

/**
 * Evaluate the live step-by-step interactive coach state for the current lesson (0..5),
 * determining the exact sub-step instruction, idle hint prompt, and visual highlight target
 * on either the edit toolbar or the calendar grid.
 */
internal fun evaluatePracticeCoach(
    lesson: Int,
    subStep: Int,
    mode: CalendarEditMode,
    tool: String?,
    brush: String,
    anchor: Int?,
    modelIds: List<String>,
    modelNames: Map<String, String>,
    dates: List<LocalDate>,
    rotationId: String = "practice",
    canUndo: Boolean = true,
    preferredEraseDay: Int = 2
): PracticeCoachEvaluation {
    val safeLesson = lesson.coerceIn(0, 5)
    val targetBrush = recommendedPracticeBrushId(modelIds.ifEmpty { listOf(brush) })
    val targetBrushName = modelNames[targetBrush]?.ifBlank { "Pomeriggio" } ?: "Pomeriggio"
    val activeBrushName = modelNames[brush]?.ifBlank { targetBrushName } ?: targetBrushName
    fun dayLabel(idx: Int): String = dates.getOrNull(idx)?.dayOfMonth?.toString() ?: "${idx + 1}"

    return when (safeLesson) {
        0 -> {
            // Lesson 0: Cambia un turno (2 sub-steps: 0 = pick Pomeriggio, 1 = tap day 1 to replace)
            val effectiveSub = if (subStep >= 1 && mode == CalendarEditMode.REPLACE && tool == null) 1 else 0
            if (effectiveSub == 0) {
                PracticeCoachEvaluation(
                    lessonIndex = 0,
                    subStepIndex = 0,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 1 di 2 · Scegli il turno",
                    instruction = "Tocca $targetBrushName lampeggiante nella barra.",
                    hintInstruction = "Tocca la tessera $targetBrushName in basso.",
                    target = PracticeHighlightTarget(brushId = targetBrush)
                )
            } else {
                val targetDay = 1
                PracticeCoachEvaluation(
                    lessonIndex = 0,
                    subStepIndex = 1,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 2 di 2 · Cambia il giorno",
                    instruction = "Tocca il giorno ${dayLabel(targetDay)} per inserire $activeBrushName.",
                    hintInstruction = "Tocca la casella del giorno ${dayLabel(targetDay)}.",
                    target = PracticeHighlightTarget(dayIndex = targetDay, action = PracticeTargetAction.TAP)
                )
            }
        }
        1 -> {
            // Lesson 1: Cambia più giorni (2 sub-steps: 0 = long-press start day 3, 1 = tap end day 6)
            val startDay = 3
            val endDay = if (anchor == 6) 4 else 6
            if (anchor == null) {
                PracticeCoachEvaluation(
                    lessonIndex = 1,
                    subStepIndex = 0,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 1 di 2 · Tieni premuto",
                    instruction = "Tieni premuto sul giorno ${dayLabel(startDay)} per iniziare.",
                    hintInstruction = "Tieni il dito fermo sul giorno ${dayLabel(startDay)}.",
                    target = PracticeHighlightTarget(dayIndex = startDay, action = PracticeTargetAction.LONG_PRESS)
                )
            } else {
                PracticeCoachEvaluation(
                    lessonIndex = 1,
                    subStepIndex = 1,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 2 di 2 · Chiudi intervallo",
                    instruction = "Tocca il giorno ${dayLabel(endDay)} per cambiare più giorni.",
                    hintInstruction = "Tocca il giorno ${dayLabel(endDay)} sul calendario.",
                    target = PracticeHighlightTarget(dayIndex = endDay, action = PracticeTargetAction.TAP)
                )
            }
        }
        2 -> {
            // Lesson 2: Aggiungi più turni (3 sub-steps: 0 = tap mode ADD, 1 = tap day 4 for 2nd shift, 2 = tap again for 3rd shift & • •)
            val targetDay = 4
            when {
                mode != CalendarEditMode.ADD || tool != null || subStep == 0 -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 2,
                        subStepIndex = 0,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 1 di 3 · Più turni",
                        instruction = "Tocca «Più turni» lampeggiante nella barra.",
                        hintInstruction = "Tocca «Più turni» al centro della barra.",
                        target = PracticeHighlightTarget(mode = CalendarEditMode.ADD)
                    )
                }
                anchor != null -> {
                    val endDay = if (anchor == 6) 4 else 6
                    PracticeCoachEvaluation(
                        lessonIndex = 2,
                        subStepIndex = 2,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 3 di 3 · Chiudi intervallo",
                        instruction = "Tocca il giorno ${dayLabel(endDay)} per aggiungere il turno.",
                        hintInstruction = "Tocca il giorno ${dayLabel(endDay)} lampeggiante.",
                        target = PracticeHighlightTarget(dayIndex = endDay, action = PracticeTargetAction.TAP)
                    )
                }
                subStep == 1 -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 2,
                        subStepIndex = 1,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 2 di 3 · 2° turno",
                        instruction = "Tocca il giorno ${dayLabel(targetDay)} per aggiungere un 2° turno.",
                        hintInstruction = "Tocca il giorno ${dayLabel(targetDay)} sul calendario.",
                        target = PracticeHighlightTarget(dayIndex = targetDay, action = PracticeTargetAction.TAP)
                    )
                }
                else -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 2,
                        subStepIndex = 2,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 3 di 3 · Pallini • •",
                        instruction = "Tocca ancora il ${dayLabel(targetDay)} per un 3° turno (• •).",
                        hintInstruction = "Tocca il ${dayLabel(targetDay)} o tieni premuto per più giorni.",
                        target = PracticeHighlightTarget(dayIndex = targetDay, action = PracticeTargetAction.TAP)
                    )
                }
            }
        }
        3 -> {
            // Lesson 3: Reperibilità (2 sub-steps: 0 = tap bell icon, 1 = tap day 5 or long-press range)
            val effectiveSub = if (tool == "bell" && subStep >= 1) 1 else 0
            if (effectiveSub == 0) {
                PracticeCoachEvaluation(
                    lessonIndex = 3,
                    subStepIndex = 0,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 1 di 2 · Campanella",
                    instruction = "Tocca la campanella lampeggiante in alto.",
                    hintInstruction = "Tocca l’icona campanella in alto a destra.",
                    target = PracticeHighlightTarget(tool = "bell")
                )
            } else if (anchor != null) {
                val endDay = if (anchor <= 5) 7 else (anchor - 2).coerceAtLeast(0)
                PracticeCoachEvaluation(
                    lessonIndex = 3,
                    subStepIndex = 1,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 2 di 2 · Chiudi intervallo",
                    instruction = "Tocca il giorno ${dayLabel(endDay)} per segnare tutti i giorni reperibili.",
                    hintInstruction = "Tocca il giorno ${dayLabel(endDay)} sul calendario.",
                    target = PracticeHighlightTarget(dayIndex = endDay, action = PracticeTargetAction.TAP)
                )
            } else {
                val targetDay = 5
                PracticeCoachEvaluation(
                    lessonIndex = 3,
                    subStepIndex = 1,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 2 di 2 · Reperibilità",
                    instruction = "Tocca il giorno ${dayLabel(targetDay)} o tieni premuto per più giorni.",
                    hintInstruction = "Tocca il giorno ${dayLabel(targetDay)} (o tieni premuto per un intervallo).",
                    target = PracticeHighlightTarget(dayIndex = targetDay, action = PracticeTargetAction.TAP)
                )
            }
        }
        4 -> {
            // Lesson 4: Ripeti una rotazione (3 sub-steps: 0 = tap mode ROTATION, 1 = tap start day 7, 2 = tap end day 13)
            val startDay = 7
            val endDay = 13
            when {
                mode != CalendarEditMode.ROTATION || tool != null || subStep == 0 -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 4,
                        subStepIndex = 0,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 1 di 3 · Rotazione",
                        instruction = "Tocca «Rotazione» lampeggiante nella barra.",
                        hintInstruction = "Tocca la scheda «Rotazione» in basso.",
                        target = PracticeHighlightTarget(mode = CalendarEditMode.ROTATION)
                    )
                }
                anchor == null -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 4,
                        subStepIndex = 1,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 2 di 3 · Giorno iniziale",
                        instruction = "Tocca il giorno ${dayLabel(startDay)} per iniziare.",
                        hintInstruction = "Tocca il giorno ${dayLabel(startDay)} sul calendario.",
                        target = PracticeHighlightTarget(dayIndex = startDay, action = PracticeTargetAction.TAP, rotationId = rotationId)
                    )
                }
                else -> {
                    val targetEnd = if (anchor < endDay) endDay else 13
                    PracticeCoachEvaluation(
                        lessonIndex = 4,
                        subStepIndex = 2,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 3 di 3 · Giorno finale",
                        instruction = "Tocca il giorno ${dayLabel(targetEnd)} per confermare.",
                        hintInstruction = "Tocca il giorno ${dayLabel(targetEnd)} lampeggiante.",
                        target = PracticeHighlightTarget(dayIndex = targetEnd, action = PracticeTargetAction.TAP)
                    )
                }
            }
        }
        else -> {
            // Lesson 5: Gomma e Annulla (3 sub-steps: 0 = pick eraser, 1 = long-press+tap or tap to erase, 2 = tap undo)
            val targetDay = preferredEraseDay.coerceIn(0, (dates.size - 1).coerceAtLeast(0))
            when {
                subStep >= 2 && canUndo -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 5,
                        subStepIndex = 2,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 3 di 3 · Annulla",
                        instruction = "Turni eliminati! Ora tocca Annulla (freccia indietro) in alto.",
                        hintInstruction = "Tocca la freccia indietro Annulla in alto nella barra.",
                        target = PracticeHighlightTarget(tool = "undo")
                    )
                }
                tool != "eraser" -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 5,
                        subStepIndex = 0,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 1 di 3 · Gomma",
                        instruction = "Tocca la Gomma lampeggiante in alto nella barra.",
                        hintInstruction = "Tocca l’icona Gomma accanto alla campanella.",
                        target = PracticeHighlightTarget(tool = "eraser")
                    )
                }
                anchor != null -> {
                    val endDay = if (anchor <= 4) 5 else (anchor - 2).coerceAtLeast(0)
                    PracticeCoachEvaluation(
                        lessonIndex = 5,
                        subStepIndex = 1,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 2 di 3 · Chiudi intervallo",
                        instruction = "Ora tocca il giorno ${dayLabel(endDay)} per svuotare tutti i giorni insieme.",
                        hintInstruction = "Tocca il giorno ${dayLabel(endDay)} sul calendario.",
                        target = PracticeHighlightTarget(dayIndex = endDay, action = PracticeTargetAction.TAP)
                    )
                }
                else -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 5,
                        subStepIndex = 1,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 2 di 3 · Elimina turni",
                        instruction = "Tieni premuto sul giorno ${dayLabel(targetDay)} per eliminare più turni insieme.",
                        hintInstruction = "Tieni premuto sul giorno ${dayLabel(targetDay)}, poi scegli la fine (oppure tocca un giorno).",
                        target = PracticeHighlightTarget(dayIndex = targetDay, action = PracticeTargetAction.LONG_PRESS)
                    )
                }
            }
        }
    }
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
