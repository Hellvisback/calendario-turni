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
    rotationId: String = "practice"
): PracticeCoachEvaluation {
    val safeLesson = lesson.coerceIn(0, 5)
    val targetBrush = recommendedPracticeBrushId(modelIds.ifEmpty { listOf(brush) })
    val targetBrushName = modelNames[targetBrush]?.ifBlank { "Pomeriggio" } ?: "Pomeriggio"
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
                    instruction = "Tocca il turno $targetBrushName che lampeggia nella barra in basso.",
                    hintInstruction = "Guarda in basso nella barra: tocca la tessera lampeggiante $targetBrushName.",
                    target = PracticeHighlightTarget(brushId = targetBrush)
                )
            } else {
                val targetDay = 1
                PracticeCoachEvaluation(
                    lessonIndex = 0,
                    subStepIndex = 1,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 2 di 2 · Applica al giorno",
                    instruction = "Ora tocca il giorno ${dayLabel(targetDay)} lampeggiante sul calendario per inserire $targetBrushName.",
                    hintInstruction = "Tocca la casella lampeggiante del giorno ${dayLabel(targetDay)} sul calendario (o un altro giorno).",
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
                    subStepTitle = "Passo 1 di 2 · Tieni premuto l’inizio",
                    instruction = "Tieni premuto (pressione prolungata) sul giorno ${dayLabel(startDay)} che lampeggia per aprire l’intervallo.",
                    hintInstruction = "Non fare un tocco rapido: tieni il dito premuto sul giorno ${dayLabel(startDay)} finché non compare l’indicatore.",
                    target = PracticeHighlightTarget(dayIndex = startDay, action = PracticeTargetAction.LONG_PRESS)
                )
            } else {
                PracticeCoachEvaluation(
                    lessonIndex = 1,
                    subStepIndex = 1,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 2 di 2 · Chiudi l’intervallo",
                    instruction = "Perfetto! Ora tocca il giorno ${dayLabel(endDay)} lampeggiante per colorare tutti i giorni fino a lì.",
                    hintInstruction = "Tocca il giorno ${dayLabel(endDay)} che lampeggia sul calendario per completare l’intervallo.",
                    target = PracticeHighlightTarget(dayIndex = endDay, action = PracticeTargetAction.TAP)
                )
            }
        }
        2 -> {
            // Lesson 2: Aggiungi più turni (2 sub-steps: 0 = tap mode ADD "Più turni", 1 = tap day 4 to add second shift)
            val effectiveSub = if (mode == CalendarEditMode.ADD && tool == null && subStep >= 1) 1 else 0
            if (effectiveSub == 0) {
                PracticeCoachEvaluation(
                    lessonIndex = 2,
                    subStepIndex = 0,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 1 di 2 · Modalità Più turni",
                    instruction = "Tocca il pulsante «Più turni» che lampeggia al centro della barra strumenti.",
                    hintInstruction = "Guarda al centro della barra: tocca «Più turni» per aggiungere un secondo turno nello stesso giorno.",
                    target = PracticeHighlightTarget(mode = CalendarEditMode.ADD)
                )
            } else {
                val targetDay = 4
                PracticeCoachEvaluation(
                    lessonIndex = 2,
                    subStepIndex = 1,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 2 di 2 · Doppio turno nello stesso giorno",
                    instruction = "Ora tocca il giorno ${dayLabel(targetDay)} lampeggiante per aggiungere un secondo turno senza cancellare quello presente.",
                    hintInstruction = "Tocca la casella del giorno ${dayLabel(targetDay)} che lampeggia sul calendario.",
                    target = PracticeHighlightTarget(dayIndex = targetDay, action = PracticeTargetAction.TAP)
                )
            }
        }
        3 -> {
            // Lesson 3: Reperibilità (2 sub-steps: 0 = tap bell icon in toolbar, 1 = tap day 5 to mark on-call)
            val effectiveSub = if (tool == "bell" && subStep >= 1) 1 else 0
            if (effectiveSub == 0) {
                PracticeCoachEvaluation(
                    lessonIndex = 3,
                    subStepIndex = 0,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 1 di 2 · Attiva la campanella",
                    instruction = "Tocca l’icona della campanella (Reperibilità) che lampeggia in alto a destra nella barra.",
                    hintInstruction = "Guarda in alto a destra nella barra di modifica: tocca la campanella lampeggiante.",
                    target = PracticeHighlightTarget(tool = "bell")
                )
            } else {
                val targetDay = 5
                PracticeCoachEvaluation(
                    lessonIndex = 3,
                    subStepIndex = 1,
                    totalSubSteps = 2,
                    subStepTitle = "Passo 2 di 2 · Segna la reperibilità",
                    instruction = "Ora tocca il giorno ${dayLabel(targetDay)} lampeggiante sul calendario per inserire la campanella di reperibilità.",
                    hintInstruction = "Tocca il giorno ${dayLabel(targetDay)} sul calendario per applicare la reperibilità.",
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
                        subStepTitle = "Passo 1 di 3 · Modalità Rotazione",
                        instruction = "Tocca la scheda «Rotazione» che lampeggia a destra nella barra modalità.",
                        hintInstruction = "Guarda nella barra: tocca «Rotazione» per ripetere automaticamente una sequenza di turni.",
                        target = PracticeHighlightTarget(mode = CalendarEditMode.ROTATION)
                    )
                }
                anchor == null -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 4,
                        subStepIndex = 1,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 2 di 3 · Giorno di partenza",
                        instruction = "La sequenza è pronta in basso: tocca il giorno ${dayLabel(startDay)} lampeggiante per scegliere da dove iniziare.",
                        hintInstruction = "Tocca il giorno ${dayLabel(startDay)} all’inizio della seconda settimana per vedere l’anteprima della rotazione.",
                        target = PracticeHighlightTarget(dayIndex = startDay, action = PracticeTargetAction.TAP, rotationId = rotationId)
                    )
                }
                else -> {
                    val targetEnd = if (anchor < endDay) endDay else (anchor + 3).coerceAtMost(13)
                    PracticeCoachEvaluation(
                        lessonIndex = 4,
                        subStepIndex = 2,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 3 di 3 · Conferma la fine",
                        instruction = "Vedi l’anteprima sul calendario? Tocca il giorno ${dayLabel(targetEnd)} lampeggiante per confermare tutta la rotazione.",
                        hintInstruction = "Tocca il giorno ${dayLabel(targetEnd)} che lampeggia per applicare la sequenza fino a quella data.",
                        target = PracticeHighlightTarget(dayIndex = targetEnd, action = PracticeTargetAction.TAP)
                    )
                }
            }
        }
        else -> {
            // Lesson 5: Gomma e Annulla (2 sub-steps: 0 = tap eraser or day to clear, 1 = tap undo to recover)
            when {
                subStep == 0 && tool != "eraser" -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 5,
                        subStepIndex = 0,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 1 di 3 · Prendi la gomma",
                        instruction = "Tocca l’icona della Gomma che lampeggia in alto nella barra strumenti.",
                        hintInstruction = "Tocca la Gomma lampeggiante accanto alla campanella nella barra di modifica.",
                        target = PracticeHighlightTarget(tool = "eraser")
                    )
                }
                subStep <= 1 -> {
                    val targetDay = 2
                    PracticeCoachEvaluation(
                        lessonIndex = 5,
                        subStepIndex = 1,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 2 di 3 · Cancella un turno",
                        instruction = "Ora tocca il giorno ${dayLabel(targetDay)} lampeggiante sul calendario per svuotarlo con la gomma.",
                        hintInstruction = "Tocca il giorno ${dayLabel(targetDay)} sul calendario per provare a cancellare il turno.",
                        target = PracticeHighlightTarget(dayIndex = targetDay, action = PracticeTargetAction.TAP)
                    )
                }
                else -> {
                    PracticeCoachEvaluation(
                        lessonIndex = 5,
                        subStepIndex = 2,
                        totalSubSteps = 3,
                        subStepTitle = "Passo 3 di 3 · Annulla l’errore",
                        instruction = "Hai cancellato per sbaglio? Tocca il tasto Annulla (freccia indietro) che lampeggia per recuperare subito il turno!",
                        hintInstruction = "Tocca la freccia Annulla lampeggiante in alto nella barra di modifica.",
                        target = PracticeHighlightTarget(tool = "undo")
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
