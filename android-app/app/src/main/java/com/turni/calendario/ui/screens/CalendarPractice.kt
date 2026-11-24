@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.model.*
import com.turni.calendario.repository.*
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PracticeLesson(
    val title: String,
    val instruction: String,
    val mode: CalendarEditMode = CalendarEditMode.REPLACE,
    val tool: String? = null
)

private val practiceLessons = listOf(
    PracticeLesson("Cambia un turno", "Scegli un modello nella barra, poi tocca il giorno da modificare."),
    PracticeLesson("Cambia più giorni", "Tieni premuto sul primo giorno, poi tocca l’ultimo (anche a ritroso)."),
    PracticeLesson("Aggiungi più turni", "In Più turni aggiungi senza sostituire. Tieni premuto per un intervallo.", CalendarEditMode.ADD),
    PracticeLesson("Reperibilità", "Attiva la campanella nella barra e tocca il giorno per segnare la reperibilità."),
    PracticeLesson("Ripeti una rotazione", "Scegli Rotazione, poi tocca il giorno iniziale e quello finale.", CalendarEditMode.ROTATION),
    PracticeLesson("Gomma e Annulla", "Cancella un turno con la gomma, poi tocca Annulla per recuperarlo.")
)

/** The production calendar canvas and toolbar, supplied only with local rehearsal data. */
@Composable
internal fun GesturePractice(
    showGuide: Boolean = true,
    interactive: Boolean = showGuide,
    compactPreview: Boolean = false,
    onTutorialCompleted: () -> Unit = {}
) {
    val work = ShiftRepository.activeWorkProfileId
    val models = ShiftRepository.shiftDefinitions.filter { it.workProfileId == work }.ifEmpty { starterShiftDefinitions(work) }
    val defs = models.associateBy { it.id }
    val modelIds = models.map { it.id }
    val modelNames = remember(models) { models.associate { it.id to it.name } }
    val cycle = remember(modelIds) { ShiftRotation("practice", "Sequenza di prova", 4, List(4) { modelIds[it % modelIds.size] }) }
    val initial = remember(modelIds) { initialCalendarPractice(modelIds) }
    var state by remember(modelIds) { mutableStateOf(initial) }
    var history by remember(modelIds) { mutableStateOf(listOf<CalendarPracticeState>()) }
    var completedLessons by remember(modelIds) { mutableStateOf(emptySet<Int>()) }
    var editing by remember { mutableStateOf(interactive) }
    val editProgress = animateFloatAsState(if (editing) 1f else 0f, spring(.92f, 240f), label = "practiceEditProgress")
    var editSession by remember { mutableStateOf(0L) }
    var mode by remember { mutableStateOf(CalendarEditMode.REPLACE) }
    var brush by remember(modelIds) { mutableStateOf(modelIds.first()) }
    var tool by remember { mutableStateOf<String?>(null) }
    var anchor by remember { mutableStateOf<Int?>(null) }
    var eraseChoice by remember { mutableStateOf<Int?>(null) }
    var lesson by remember { mutableStateOf(0) }
    var subStep by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var isSuccessBanner by remember { mutableStateOf(false) }
    var isAdvancing by remember { mutableStateOf(false) }
    var idleHintActive by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var advanceJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val accessibility = LocalAccessibilityManager.current
    val fontScale = LocalDensity.current.fontScale
    val largeFont = fontScale > 1.3f

    val monday = ShiftRepository.appPreferences.weekStartMonday
    val dates = remember(monday) { calendarPracticeDates(LocalDate.now(), monday) }
    val firstDate = dates.first()

    val preferredEraseDay = remember(state) {
        if (state.days.getOrNull(2)?.isNotEmpty() == true || 2 in state.bells) 2
        else state.days.indexOfFirst { it.isNotEmpty() }.takeIf { it >= 0 } ?: 2
    }

    val coach = remember(lesson, subStep, mode, tool, brush, anchor, modelIds, modelNames, dates, cycle.id, history.size, preferredEraseDay) {
        evaluatePracticeCoach(
            lesson = lesson,
            subStep = subStep,
            mode = mode,
            tool = tool,
            brush = brush,
            anchor = anchor,
            modelIds = modelIds,
            modelNames = modelNames,
            dates = dates,
            rotationId = cycle.id,
            canUndo = history.isNotEmpty(),
            preferredEraseDay = preferredEraseDay
        )
    }

    // Idle timer: if the user stays still for 2.2s on a step, activate prominent beacon hint
    LaunchedEffect(lesson, coach.subStepIndex, mode, tool, brush, anchor, isAdvancing, editing) {
        idleHintActive = false
        if (showGuide && editing && !isAdvancing) {
            delay(2200L)
            idleHintActive = true
        }
    }

    // Continuous pulse animation for the highlighted toolbar button or calendar cell
    val infiniteTransition = rememberInfiniteTransition(label = "practiceCoachPulse")
    val rawPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (idleHintActive) 560 else 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "practicePulseFloat"
    )
    val longPressWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1280, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "practiceLongPressWave"
    )
    val activePulse = if (showGuide && editing && !isAdvancing) rawPulse else 0f

    val feedbackTimeout = accessibility?.calculateRecommendedTimeoutMillis(3800L, containsText = true) ?: 3800L
    LaunchedEffect(message, isAdvancing, feedbackTimeout) {
        if (message.isNotEmpty() && !isAdvancing) {
            delay(feedbackTimeout)
            message = ""
            isSuccessBanner = false
        }
    }

    var paintRequest by remember { mutableStateOf<CalendarPaintRequest?>(null) }
    var paintSequence by remember { mutableStateOf(0L) }

    val assignments = state.days.flatMapIndexed { day, values -> values.mapIndexed { position, id ->
        AssignedShift("practice-$day-$position", dates[day], id,
            overtimeHours = if (position == 0 && day in state.overtime) 1f else 0f,
            isTrasferta = position == 0 && day in state.travel)
    } }
    val preview = CalendarGridPreview(
        days = dates.map { CalendarDayInfo(it, true) }, definitions = models, assignments = assignments,
        onCallDates = state.bells.map { dates[it] }.toSet(),
        extras = state.notes.associate { dates[it] to DayExtraInfo(dates[it], notes = "Nota di prova") },
        rotations = listOf(cycle), timeFormat24h = ShiftRepository.appPreferences.timeFormat24h
    )

    fun change(next: CalendarPracticeState) {
        history = (history + state).takeLast(20)
        state = next
    }

    fun prepareLesson(next: Int) {
        advanceJob?.cancel()
        isAdvancing = false
        isSuccessBanner = false
        idleHintActive = false
        paintRequest = null
        editSession++
        lesson = next.coerceIn(0, practiceLessons.lastIndex)
        subStep = 0
        anchor = null
        eraseChoice = null
        message = ""
        when (lesson) {
            0 -> {
                mode = CalendarEditMode.REPLACE
                tool = null
                brush = modelIds.first()
            }
            1 -> {
                mode = CalendarEditMode.REPLACE
                tool = null
                val currentDay3 = state.days.getOrNull(3)?.firstOrNull()
                brush = modelIds.firstOrNull { it != currentDay3 } ?: modelIds.first()
            }
            2 -> {
                // Start in REPLACE so the user interactively taps "Più turni" as Step 1!
                mode = CalendarEditMode.REPLACE
                tool = null
                val currentDay4 = state.days.getOrNull(4)?.firstOrNull()
                brush = modelIds.firstOrNull { it != currentDay4 } ?: modelIds.first()
            }
            3 -> {
                // Start with tool = null so the user interactively taps the Bell icon as Step 1!
                mode = CalendarEditMode.REPLACE
                tool = null
            }
            4 -> {
                // Start in REPLACE so the user interactively taps "Rotazione" as Step 1!
                mode = CalendarEditMode.REPLACE
                tool = null
            }
            5 -> {
                // Start with tool = null so the user interactively taps the Eraser icon as Step 1!
                mode = CalendarEditMode.REPLACE
                tool = null
            }
        }
        if (!editing) {
            editing = true
        }
    }

    fun celebrateAndAdvance(completedIdx: Int, successMsg: String) {
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        completedLessons = completedLessons + completedIdx
        isSuccessBanner = true
        message = successMsg
        if (!showGuide) return
        advanceJob?.cancel()
        isAdvancing = true
        advanceJob = scope.launch {
            delay(800L)
            isAdvancing = false
            isSuccessBanner = false
            val nextUninitialized = (0..practiceLessons.lastIndex).firstOrNull {
                val candidate = (completedIdx + 1 + it) % practiceLessons.size
                candidate !in completedLessons
            }
            if (completedLessons.size == practiceLessons.size) {
                isSuccessBanner = true
                message = "Perfetto! Hai finito il tutorial"
                showCompletionDialog = true
            } else if (completedIdx < practiceLessons.lastIndex) {
                prepareLesson(completedIdx + 1)
            } else if (nextUninitialized != null) {
                prepareLesson(nextUninitialized)
            } else {
                isSuccessBanner = true
                message = "Perfetto! Hai finito il tutorial"
                showCompletionDialog = true
            }
        }
    }

    fun resetPractice() {
        advanceJob?.cancel()
        isAdvancing = false
        isSuccessBanner = false
        showCompletionDialog = false
        state = initial
        history = emptyList()
        completedLessons = emptySet()
        anchor = null
        eraseChoice = null
        prepareLesson(0)
        message = "Esempio ripristinato: ripartiamo dal primo passo."
    }

    fun apply(index: Int) {
        if (index !in state.days.indices || !interactive || !editing || isAdvancing) return
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

        // Lesson 1 Step 0 requires a long-press; guide gently on single tap
        if (showGuide && lesson == 1 && anchor == null && tool == null && mode == CalendarEditMode.REPLACE) {
            isSuccessBanner = false
            idleHintActive = true
            message = "Quasi! Tieni il dito premuto sul giorno ${dates[coach.target.dayIndex ?: index].dayOfMonth} finché non si apre l’intervallo."
            return
        }

        // Guard against tapping a day before picking the required toolbar button in Lessons 2..5
        if (showGuide && lesson == 2 && mode != CalendarEditMode.ADD) {
            isSuccessBanner = false
            idleHintActive = true
            message = "Prima tocca «Più turni» che lampeggia nella barra sotto il calendario."
            return
        }
        if (showGuide && lesson == 3 && tool != "bell") {
            isSuccessBanner = false
            idleHintActive = true
            message = "Prima tocca la campanella (Reperibilità) che lampeggia in alto nella barra."
            return
        }
        if (showGuide && lesson == 4 && mode != CalendarEditMode.ROTATION) {
            isSuccessBanner = false
            idleHintActive = true
            message = "Prima tocca «Rotazione» che lampeggia a destra nella barra modalità."
            return
        }
        if (showGuide && lesson == 5 && tool != "eraser") {
            if (subStep >= 2 && history.isNotEmpty()) {
                isSuccessBanner = false
                idleHintActive = true
                message = "Ora tocca il tasto Annulla (freccia indietro) che lampeggia in alto per recuperare!"
            } else {
                isSuccessBanner = false
                idleHintActive = true
                message = "Prima tocca l’icona della Gomma che lampeggia in alto nella barra."
            }
            return
        }

        if (tool == "bell") {
            val first = anchor
            if (first != null) {
                val (nextState, enabled) = applyBellPracticeRange(state, first, index)
                change(nextState)
                anchor = null
                eraseChoice = null
                paintRequest = null
                val count = kotlin.math.abs(index - first) + 1
                if (lesson == 3) {
                    celebrateAndAdvance(3, "Reperibilità applicata su $count giorni!")
                } else {
                    completedLessons = completedLessons + 3
                    isSuccessBanner = true
                    message = if (enabled) "Reperibilità applicata su $count giorni!" else "Reperibilità rimossa su $count giorni."
                }
            } else {
                val enabled = index !in state.bells
                change(state.copy(bells = if (enabled) state.bells + index else state.bells - index))
                if (lesson == 3) {
                    celebrateAndAdvance(3, "Reperibilità ${if (enabled) "attivata" else "aggiornata"} sul giorno ${dates[index].dayOfMonth}!")
                } else {
                    completedLessons = completedLessons + 3
                    isSuccessBanner = true
                    message = if (enabled) "Reperibilità attivata sul giorno ${dates[index].dayOfMonth}." else "Reperibilità rimossa."
                }
            }
            return
        }

        if (mode == CalendarEditMode.ROTATION && tool != "eraser" && (anchor == null || index < anchor!!)) {
            anchor = index
            subStep = 2
            isSuccessBanner = true
            message = "Ottimo! Inizio rotazione sul giorno ${dates[index].dayOfMonth}: ora tocca il giorno finale lampeggiante."
            return
        }

        if (lesson == 1 && anchor != null && index == anchor) {
            isSuccessBanner = false
            idleHintActive = true
            message = "Hai toccato lo stesso giorno di partenza! Tocca un giorno diverso (es. ${dates[coach.target.dayIndex ?: 6].dayOfMonth}) per chiudere l’intervallo."
            return
        }

        if (lesson == 2 && anchor != null && index == anchor) {
            isSuccessBanner = false
            idleHintActive = true
            message = "Tocca un giorno diverso dal ${dates[anchor!!].dayOfMonth} per aggiungere i turni a tutto l’intervallo."
            return
        }

        if (tool == "eraser" && anchor == null && state.days[index].size > 1 && lesson != 5) {
            eraseChoice = index
            isSuccessBanner = false
            message = "Scegli quale turno eliminare dal giorno ${dates[index].dayOfMonth}."
            return
        }

        val first = anchor ?: index
        val erased = tool == "eraser"
        val nextState = applyCalendarPractice(state, first, index, brush, mode, erased, cycle.daySequence)
        if (lesson == 5 && erased && nextState == state) {
            anchor = null
            isSuccessBanner = false
            idleHintActive = true
            message = "Il giorno ${dates[index].dayOfMonth} è già vuoto! Tocca un giorno con un turno (es. ${dates[preferredEraseDay].dayOfMonth})."
            return
        }
        change(nextState)
        paintSequence++
        paintRequest = if (!erased && mode == CalendarEditMode.ROTATION) {
            val definitionId = cycle.daySequence[(index - first) % cycle.daySequence.size]
            CalendarPaintRequest(dates[index], definitionId, paintSequence)
        } else {
            CalendarPaintRequest(dates[first], if (erased) "" else brush, paintSequence, dates[index])
        }
        anchor = null
        eraseChoice = null
        val count = kotlin.math.abs(index - first) + 1

        when {
            lesson == 0 && !erased && mode == CalendarEditMode.REPLACE -> {
                if (subStep == 0 && brush == modelIds.first() && modelIds.size > 1) {
                    val targetName = modelNames[recommendedPracticeBrushId(modelIds)] ?: "Pomeriggio"
                    subStep = 0
                    isSuccessBanner = false
                    idleHintActive = true
                    message = "Hai cambiato il giorno! Ora prova a toccare prima $targetName che lampeggia nella barra e poi un giorno."
                } else {
                    celebrateAndAdvance(0, "Benissimo! Hai inserito ${defs[brush]?.name ?: "il turno"} sul giorno ${dates[index].dayOfMonth}. Ora proviamo su più giorni!")
                }
            }
            lesson == 1 && !erased && count > 1 -> {
                celebrateAndAdvance(1, "Grande! Hai aggiornato $count giorni in un colpo solo. Passiamo a Più turni!")
            }
            lesson == 2 && !erased && mode == CalendarEditMode.ADD -> {
                if (subStep <= 1 && count == 1) {
                    subStep = 2
                    isSuccessBanner = true
                    message = "2° turno aggiunto sul ${dates[index].dayOfMonth}! Vedi il pallino sotto? Tocca ancora per un 3° turno!"
                } else {
                    val msg = if (count > 1) {
                        "Perfetto! Turno aggiunto su $count giorni insieme. I pallini (• •) indicano i turni extra. Passiamo alla Reperibilità!"
                    } else {
                        "Ecco i pallini (• •)! I turni extra restano leggibili senza comprimere i riquadri (nel mese intero ne vedi 2 impilati). Passiamo alla Reperibilità!"
                    }
                    celebrateAndAdvance(2, msg)
                }
            }
            lesson == 4 && !erased && mode == CalendarEditMode.ROTATION -> {
                celebrateAndAdvance(4, "Fantastico! Rotazione applicata su $count giorni. Ultimo passo: Gomma e Annulla!")
            }
            lesson == 5 && erased -> {
                subStep = 2
                isSuccessBanner = true
                message = if (count > 1) {
                    "$count giorni eliminati! Ora tocca Annulla in alto."
                } else {
                    "Giorno ${dates[index].dayOfMonth} eliminato! Ora tocca Annulla in alto."
                }
            }
            else -> {
                val achieved = completedLessonForAction(mode, tool, count)
                completedLessons = completedLessons + achieved
                isSuccessBanner = true
                message = if (erased) {
                    "${if (count == 1) "Giorno liberato" else "$count giorni liberati"}. Tocca Annulla per recuperare."
                } else if (count > 1) {
                    "$count giorni aggiornati con un’unica azione."
                } else {
                    "Turno ${if (mode == CalendarEditMode.ADD) "aggiunto" else "cambiato"} sul giorno ${dates[index].dayOfMonth}."
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (showGuide && editing) {
            val palette = currentTurniPalette
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianPrimarySubtle)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Lezione ${lesson + 1}/${practiceLessons.size}",
                            color = BrandInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            softWrap = false
                        )
                    }
                    Text(
                        text = practiceLessons[lesson].title,
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (lesson in completedLessons) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(palette.success.fill))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SiteIcon("check", "Completata", Modifier.size(12.dp), Color(palette.success.ink))
                            Text("Fatto", color = Color(palette.success.ink), fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "${completedLessons.size}/${practiceLessons.size} completate",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            softWrap = false
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    practiceLessons.forEachIndexed { idx, item ->
                        val active = idx == lesson
                        val done = idx in completedLessons
                        val segmentColor by animateColorAsState(
                            when {
                                active -> ObsidianPrimary
                                done -> Color(palette.success.ink)
                                else -> ObsidianSurfaceContainer
                            },
                            spring(stiffness = 350f),
                            label = "lessonSegmentColor"
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { prepareLesson(idx) }
                                .semantics {
                                    contentDescription = "${idx + 1}: ${item.title}" + if (done) ", provata" else ""
                                    selected = active
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (active) 5.dp else 4.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(segmentColor)
                            )
                        }
                    }
                }

                // Dynamic Interactive Coach Banner (placed right above the calendar & toolbar so instructions are always clear)
                InteractiveCoachBanner(
                    coach = coach,
                    feedbackMessage = message,
                    isSuccess = isSuccessBanner,
                    isAdvancing = isAdvancing,
                    idleHintActive = idleHintActive,
                    pulse = activePulse,
                    largeFont = largeFont
                )
            }
        }

        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CalendarSurface).padding(4.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${firstDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))} — ${dates.last().format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    if (showGuide && editing && coach.target.dayIndex != null) {
                        val targetDayNum = dates.getOrNull(coach.target.dayIndex)?.dayOfMonth ?: 1
                        val gestureLabel = if (coach.target.action == PracticeTargetAction.LONG_PRESS) {
                            "Tieni premuto sul giorno $targetDayNum"
                        } else {
                            "Tocca il giorno $targetDayNum"
                        }
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    val s = 1f + 0.04f * activePulse
                                    scaleX = s; scaleY = s
                                }
                                .clip(RoundedCornerShape(50))
                                .background(lerp(ObsidianSurfaceContainer, ObsidianPrimary, 0.16f + 0.20f * activePulse))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(BrandInk)
                            )
                            Text(
                                text = gestureLabel,
                                color = BrandInk,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    dates.take(7).forEach { date ->
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.ITALIAN)).uppercase(Locale.ITALIAN),
                            color = if (date.dayOfWeek.value >= 6) BrandInk else TextSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (date.dayOfWeek.value >= 6) CalendarWeekend else CalendarSurface)
                                .padding(vertical = 4.dp)
                        )
                    }
                }
                val gridHeight by animateDpAsState(
                    when {
                        largeFont -> (176.dp * (fontScale / 1.3f)).coerceIn(184.dp, 224.dp)
                        editing -> 144.dp
                        compactPreview -> 162.dp
                        else -> 184.dp
                    },
                    spring(.92f, 240f),
                    label = "practiceGridHeight"
                )
                Box(Modifier.fillMaxWidth().height(gridHeight)) {
                    NativeMonthGrid(
                        month = YearMonth.from(firstDate), selection = null, editing = editing,
                        mode = if (anchor != null && (tool == "eraser" || tool == "bell" || mode != CalendarEditMode.ROTATION)) CalendarEditMode.RANGE else mode,
                        brush = when (tool) { "eraser" -> "eraser"; "bell" -> "bell"; else -> brush }, rotationId = cycle.id,
                        anchor = anchor?.let { dates[it] }, rangeEnd = null,
                        modifier = Modifier.fillMaxSize().then(if (!editing) Modifier.clearAndSetSemantics { contentDescription = "Anteprima del calendario con turni, riposi e ferie" } else Modifier),
                        paintRequest = paintRequest, animate = interactive,
                        editSession = editSession, editProgress = editProgress,
                        onPaintConsumed = { paintRequest = null },
                        onClick = { val idx = dates.indexOf(it); if (idx >= 0) apply(idx) },
                        onPreview = {},
                        longPressEnabled = editing && (tool == "eraser" || tool == "bell" || mode != CalendarEditMode.ROTATION),
                        onLongClick = { date ->
                            if (isAdvancing) return@NativeMonthGrid
                            val idx = dates.indexOf(date)
                            if (idx >= 0) {
                                if (showGuide) {
                                    when {
                                        lesson == 0 && subStep == 0 -> {
                                            isSuccessBanner = false
                                            idleHintActive = true
                                            message = "Prima tocca il turno lampeggiante nella barra in basso."
                                            return@NativeMonthGrid
                                        }
                                        lesson == 2 && mode != CalendarEditMode.ADD -> {
                                            isSuccessBanner = false
                                            idleHintActive = true
                                            message = "Prima tocca «Più turni» che lampeggia nella barra modalità."
                                            return@NativeMonthGrid
                                        }
                                        lesson == 3 && tool != "bell" -> {
                                            isSuccessBanner = false
                                            idleHintActive = true
                                            message = "Prima tocca la campanella (Reperibilità) in alto nella barra."
                                            return@NativeMonthGrid
                                        }
                                        lesson == 4 && mode != CalendarEditMode.ROTATION -> {
                                            isSuccessBanner = false
                                            idleHintActive = true
                                            message = "Prima tocca «Rotazione» che lampeggia nella barra modalità."
                                            return@NativeMonthGrid
                                        }
                                        lesson == 5 && tool != "eraser" -> {
                                            isSuccessBanner = false
                                            idleHintActive = true
                                            message = if (subStep >= 2 && history.isNotEmpty()) "Ora tocca Annulla in alto nella barra per recuperare il turno!" else "Prima tocca l’icona della Gomma in alto nella barra."
                                            return@NativeMonthGrid
                                        }
                                    }
                                }
                                runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                                anchor = idx
                                eraseChoice = null
                                when {
                                    tool == "bell" -> {
                                        lesson = 3
                                        subStep = 1
                                        isSuccessBanner = true
                                        message = "Inizio reperibilità · tocca la fine"
                                    }
                                    tool == "eraser" -> {
                                        lesson = 5
                                        subStep = 1
                                        isSuccessBanner = true
                                        val endIdx = if (idx <= 4) 5 else (idx - 2).coerceAtLeast(0)
                                        message = "Inizio gomma sul ${date.dayOfMonth}: tocca il giorno ${dates[endIdx].dayOfMonth}."
                                    }
                                    mode == CalendarEditMode.ADD -> {
                                        lesson = 2
                                        subStep = 2
                                        isSuccessBanner = true
                                        val endIdx = if (idx == 6) 4 else 6
                                        message = "Inizio intervallo in Più turni sul ${date.dayOfMonth}: ora tocca il giorno ${dates[endIdx].dayOfMonth}."
                                    }
                                    else -> {
                                        lesson = 1
                                        subStep = 1
                                        isSuccessBanner = true
                                        val endIdx = if (idx == 6) 4 else 6
                                        message = "Perfetto! Inizio fissato sul giorno ${date.dayOfMonth}: ora tocca il giorno ${dates[endIdx].dayOfMonth} che lampeggia."
                                    }
                                }
                            }
                        },
                        previewData = preview, editingBottomInset = 0.dp
                    )
                    if (showGuide && editing && !isAdvancing && coach.target.dayIndex != null) {
                        PracticeGridTargetOverlay(
                            targetDayIndex = coach.target.dayIndex,
                            action = coach.target.action,
                            pulse = activePulse,
                            longPressWave = longPressWave,
                            idleHintActive = idleHintActive,
                            gridHeight = gridHeight
                        )
                    }
                }
            }
        }

        AnimatedVisibility(editing, enter = expandVertically(spring(.92f, 300f)) + fadeIn(), exit = shrinkVertically(spring(.95f, 300f)) + fadeOut()) {
            NativeEditToolbar(
                mode = mode,
                brush = brush,
                rotationId = cycle.id,
                tool = tool,
                highlightTarget = if (showGuide && !isAdvancing) coach.target else null,
                highlightPulse = activePulse,
                onTool = { clickedTool ->
                    if (isAdvancing) return@NativeEditToolbar
                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    val nextTool = if (tool == clickedTool) null else clickedTool
                    tool = nextTool
                    anchor = null
                    eraseChoice = null
                    when {
                        nextTool == "bell" -> {
                            lesson = 3
                            subStep = 1
                            isSuccessBanner = true
                            message = "Campanella attiva! Tocca il giorno ${dates[5].dayOfMonth} o tieni premuto per più giorni."
                        }
                        nextTool == "eraser" -> {
                            lesson = 5
                            subStep = if (subStep >= 2 && history.isNotEmpty()) 2 else 1
                            isSuccessBanner = true
                            message = if (subStep >= 2) {
                                "Gomma attiva! Oppure tocca Annulla in alto per recuperare la modifica precedente."
                            } else {
                                "Gomma attiva! Tieni premuto sul giorno ${dates[preferredEraseDay].dayOfMonth} per un intervallo (o tocca un giorno)."
                            }
                        }
                        else -> {
                            if (lesson == 3 || (lesson == 5 && subStep <= 1)) subStep = 0
                            isSuccessBanner = false
                            message = if (clickedTool == "bell") "Reperibilità chiusa." else "Gomma chiusa."
                        }
                    }
                },
                onMode = { nextMode ->
                    if (isAdvancing) return@NativeEditToolbar
                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    mode = nextMode
                    tool = null
                    anchor = null
                    eraseChoice = null
                    when (nextMode) {
                        CalendarEditMode.ADD -> {
                            lesson = 2
                            subStep = 1
                            isSuccessBanner = true
                            message = "Modalità «Più turni» attiva! Tocca il giorno ${dates[4].dayOfMonth} per aggiungere un 2° turno senza sostituire."
                        }
                        CalendarEditMode.ROTATION -> {
                            lesson = 4
                            subStep = 1
                            isSuccessBanner = true
                            message = "Modalità «Rotazione» attiva! Ora tocca il giorno ${dates[7].dayOfMonth} lampeggiante per fissare l’inizio della sequenza."
                        }
                        else -> {
                            if (lesson != 1) lesson = 0
                            subStep = if (lesson == 0 && subStep >= 1 && brush != modelIds.first()) 1 else 0
                            isSuccessBanner = false
                            message = ""
                        }
                    }
                },
                onBrush = { pickedBrush ->
                    if (isAdvancing) return@NativeEditToolbar
                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    brush = pickedBrush
                    tool = null
                    if (lesson != 1 && !(lesson == 2 && mode == CalendarEditMode.ADD)) {
                        anchor = null
                    }
                    val pickedName = defs[pickedBrush]?.name.orEmpty()
                    when {
                        lesson == 0 -> {
                            subStep = 1
                            isSuccessBanner = true
                            message = "Ottimo, hai scelto $pickedName! Ora tocca il giorno ${dates[1].dayOfMonth} che lampeggia sul calendario."
                        }
                        lesson == 2 && mode == CalendarEditMode.ADD -> {
                            isSuccessBanner = true
                            message = "$pickedName pronto: tocca il giorno ${dates[4].dayOfMonth} per aggiungerlo senza sostituire."
                        }
                        else -> {
                            isSuccessBanner = false
                            message = "$pickedName selezionato: tocca il giorno evidenziato sul calendario."
                        }
                    }
                },
                onRotation = {
                    if (isAdvancing) return@NativeEditToolbar
                    anchor = null
                    if (lesson == 4) subStep = 1
                    isSuccessBanner = true
                    message = "Sequenza pronta! Tocca il giorno ${dates[7].dayOfMonth} lampeggiante per iniziare la rotazione."
                },
                onUndo = {
                    if (isAdvancing) return@NativeEditToolbar
                    if (history.isNotEmpty()) {
                        state = history.last()
                        history = history.dropLast(1)
                        anchor = null
                        eraseChoice = null
                        paintRequest = null
                        editSession++
                        if (lesson == 5 && subStep >= 2) {
                            runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                            completedLessons = completedLessons + 5
                            isSuccessBanner = true
                            message = "Perfetto! Hai finito il tutorial"
                            if (showGuide) {
                                advanceJob?.cancel()
                                isAdvancing = true
                                advanceJob = scope.launch {
                                    delay(420L)
                                    isAdvancing = false
                                    showCompletionDialog = true
                                }
                            }
                        } else if (lesson == 5) {
                            isSuccessBanner = true
                            message = "Modifica annullata. Ora prova la gomma: tocca Gomma e poi un giorno."
                        } else {
                            completedLessons = completedLessons + 5
                            isSuccessBanner = true
                            message = "Ultima modifica annullata con successo."
                        }
                    } else {
                        isSuccessBanner = false
                        idleHintActive = true
                        message = "Prima cancella o modifica un giorno sul calendario, poi premi Annulla per recuperarlo."
                    }
                },
                onClose = {
                    if (isAdvancing) return@NativeEditToolbar
                    editing = false
                    anchor = null
                    eraseChoice = null
                    message = ""
                },
                catalogOverride = models,
                rotationsOverride = listOf(cycle)
            )
        }

        if (interactive && !editing) {
            CloudButton("Riapri la matita", "edit", { editing = true; editSession++; message = "" }, secondary = true)
        }
        eraseChoice?.let { day ->
            CloudCard("Quale turno del ${dates[day].dayOfMonth}?") {
                state.days[day].forEachIndexed { position, id ->
                    CloudButton(defs[id]?.name ?: "Turno", "eraser", {
                        val remaining = state.days[day].filterIndexed { index, _ -> index != position }
                        change(state.copy(days = state.days.mapIndexed { index, shifts -> if (index == day) remaining else shifts }, bells = state.bells - day))
                        completedLessons = completedLessons + 5
                        eraseChoice = null
                        isSuccessBanner = true
                        message = "Eliminato solo il turno scelto."
                    }, secondary = true)
                }
            }
        }
        if (showGuide && editing) {
            if (!largeFont) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lesson > 0) {
                        TextButton(onClick = { prepareLesson(lesson - 1) }) { Text("Precedente", color = TextSecondary, maxLines = 1) }
                    } else {
                        Text("Guida interattiva", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (interactive) {
                        TextButton(onClick = ::resetPractice) { Text("Ripristina", color = TextSecondary, fontSize = 12.sp, maxLines = 1) }
                    }
                    TextButton(onClick = { prepareLesson(if (lesson < practiceLessons.lastIndex) lesson + 1 else 0) }) {
                        Text(if (lesson < practiceLessons.lastIndex) "Salta passo" else "Rivedi da capo", color = BrandInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lesson > 0) TextButton(onClick = { prepareLesson(lesson - 1) }) { Text("Precedente", color = TextSecondary) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { prepareLesson(if (lesson < practiceLessons.lastIndex) lesson + 1 else 0) }) {
                            Text(if (lesson < practiceLessons.lastIndex) "Salta passo" else "Rivedi da capo", color = BrandInk, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (interactive) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Guida interattiva", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = ::resetPractice) { Text("Ripristina", color = BrandInk, fontSize = 12.sp) }
                        }
                    }
                }
            }
        } else if (!showGuide && interactive && editing) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Le prove non cambiano il calendario", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = ::resetPractice) { Text("Ripristina", color = BrandInk, fontSize = 11.sp) }
            }
        }
    }

    if (showCompletionDialog) {
        TutorialCompletionDialog(
            onContinue = {
                showCompletionDialog = false
                onTutorialCompleted()
            },
            onRetry = {
                showCompletionDialog = false
                resetPractice()
            },
            onDismiss = {
                showCompletionDialog = false
            }
        )
    }
}

@Composable
private fun InteractiveCoachBanner(
    coach: PracticeCoachEvaluation,
    feedbackMessage: String,
    isSuccess: Boolean,
    isAdvancing: Boolean,
    idleHintActive: Boolean,
    pulse: Float,
    largeFont: Boolean
) {
    val palette = currentTurniPalette
    val activeText = when {
        feedbackMessage.isNotEmpty() -> feedbackMessage
        idleHintActive -> coach.hintInstruction
        else -> coach.instruction
    }
    val containerFill by animateColorAsState(
        when {
            isSuccess -> Color(palette.success.fill)
            idleHintActive -> lerp(ObsidianSurfaceHigh, ObsidianPrimary, 0.16f + 0.12f * pulse)
            else -> ObsidianSurfaceHigh
        },
        tween(240),
        label = "coachBannerFill"
    )
    val borderColor by animateColorAsState(
        when {
            isSuccess -> Color(palette.success.ink).copy(alpha = 0.55f)
            idleHintActive -> BrandInk.copy(alpha = 0.45f + 0.50f * pulse)
            else -> BrandInk.copy(alpha = 0.22f + 0.25f * pulse)
        },
        tween(220),
        label = "coachBannerBorder"
    )
    val accentInk = if (isSuccess) Color(palette.success.ink) else BrandInk
    val badgeIcon = when {
        isSuccess -> "check"
        coach.target.action == PracticeTargetAction.LONG_PRESS -> "clock"
        coach.target.tool == "bell" -> "bell"
        coach.target.tool == "eraser" -> "eraser"
        coach.target.tool == "undo" -> "undo"
        coach.target.mode == CalendarEditMode.ROTATION -> "rotation"
        coach.target.mode == CalendarEditMode.ADD -> "layers"
        else -> "edit"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(containerFill)
            .border(1.5.dp, borderColor, RoundedCornerShape(15.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val s = if (!isSuccess) 1f + 0.08f * pulse else 1f
                        scaleX = s; scaleY = s
                    }
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSuccess) Color(palette.success.ink).copy(alpha = 0.16f) else ObsidianPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                SiteIcon(badgeIcon, null, Modifier.size(14.dp), accentInk)
            }
            Text(
                text = when {
                    isAdvancing -> "Completato! Passo successivo in arrivo…"
                    isSuccess -> "Ottimo! Continua con il prossimo passo"
                    idleHintActive -> "Suggerimento · ${coach.subStepTitle}"
                    else -> coach.subStepTitle
                },
                color = accentInk,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Sub-step dots
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(coach.totalSubSteps) { idx ->
                    val reached = isAdvancing || idx < coach.subStepIndex
                    val current = !isAdvancing && idx == coach.subStepIndex
                    Box(
                        Modifier
                            .size(width = if (current) 14.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    reached -> Color(palette.success.ink)
                                    current -> accentInk
                                    else -> ObsidianSurfaceContainer
                                }
                            )
                    )
                }
            }
        }
        AnimatedContent(
            targetState = activeText to isSuccess,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(130)) },
            label = "coachInstructionText"
        ) { (text, successState) ->
            Text(
                text = text,
                color = if (successState) Color(palette.success.ink) else TextPrimary,
                fontSize = 12.5.sp,
                lineHeight = 17.5.sp,
                fontWeight = if (successState || idleHintActive) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = if (largeFont) 6 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun PracticeGridTargetOverlay(
    targetDayIndex: Int,
    action: PracticeTargetAction,
    pulse: Float,
    longPressWave: Float,
    idleHintActive: Boolean,
    gridHeight: Dp
) {
    val col = targetDayIndex.coerceIn(0, 13) % 7
    val row = targetDayIndex.coerceIn(0, 13) / 7
    val accent = BrandInk
    val primary = ObsidianPrimary

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellW = maxWidth / 7f
        val cellH = gridHeight / 2f
        val cellX = cellW * col
        val cellY = cellH * row

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width / 7f
            val h = size.height / 2f
            val left = col * w
            val top = row * h
            val inset = 2.dp.toPx()
            val strokeWidth = (if (idleHintActive) 2.6.dp else 2.dp).toPx() * (0.85f + 0.30f * pulse)
            val corner = 9.dp.toPx()

            // Glowing fill inside the target day cell
            drawRoundRect(
                color = primary.copy(alpha = (if (idleHintActive) 0.14f else 0.08f) + 0.14f * pulse),
                topLeft = Offset(left + inset, top + inset),
                size = Size(w - inset * 2, h - inset * 2),
                cornerRadius = CornerRadius(corner, corner)
            )
            // Pulsing border around the target day cell
            drawRoundRect(
                color = accent.copy(alpha = 0.50f + 0.50f * pulse),
                topLeft = Offset(left + inset, top + inset),
                size = Size(w - inset * 2, h - inset * 2),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = strokeWidth)
            )

            // If this sub-step requires a LONG PRESS, draw expanding hold ripple rings in the center of the cell
            if (action == PracticeTargetAction.LONG_PRESS) {
                val center = Offset(left + w / 2f, top + h * 0.56f)
                val maxR = minOf(w, h) * 0.44f
                val r1 = maxR * (0.35f + 0.65f * longPressWave)
                drawCircle(
                    color = accent.copy(alpha = (1f - longPressWave) * 0.75f),
                    radius = r1,
                    center = center,
                    style = Stroke(width = 2.2.dp.toPx())
                )
                drawCircle(
                    color = accent.copy(alpha = 0.25f + 0.35f * pulse),
                    radius = maxR * 0.32f,
                    center = center
                )
            } else if (idleHintActive) {
                // Subtle tap target ring when user hesitates on a TAP step
                val center = Offset(left + w / 2f, top + h * 0.56f)
                val maxR = minOf(w, h) * 0.36f
                drawCircle(
                    color = accent.copy(alpha = 0.25f + 0.45f * pulse),
                    radius = maxR * (0.75f + 0.25f * pulse),
                    center = center,
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }
        }

        // Floating pill badge above/on the target day cell ("TIENI PREMUTO" or "TOCCA QUI" on idle)
        if (action == PracticeTargetAction.LONG_PRESS || idleHintActive) {
            val badgeText = if (action == PracticeTargetAction.LONG_PRESS) "TIENI PREMUTO" else "TOCCA QUI"
            val badgeWidth = if (action == PracticeTargetAction.LONG_PRESS) 98.dp else 74.dp
            val rawOffsetX = cellX + (cellW - badgeWidth) / 2f
            val clampedOffsetX = rawOffsetX.coerceIn(2.dp, (maxWidth - badgeWidth - 2.dp).coerceAtLeast(2.dp))
            val badgeOffsetY = if (row == 0) (cellY + cellH - 20.dp) else (cellY - 10.dp).coerceAtLeast(2.dp)

            Box(
                modifier = Modifier
                    .offset(x = clampedOffsetX, y = badgeOffsetY)
                    .width(badgeWidth)
                    .graphicsLayer {
                        val s = 0.96f + 0.08f * pulse
                        scaleX = s; scaleY = s
                    }
                    .clip(RoundedCornerShape(50))
                    .background(ObsidianPrimary)
                    .padding(horizontal = 6.dp, vertical = 2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = OnBrand,
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TutorialCompletionDialog(
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = currentTurniPalette
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun finish(action: () -> Unit) {
        if (!closing) {
            closing = true
            scope.launch {
                visible = false
                delay(180L)
                action()
            }
        }
    }

    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = { finish(onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(220)) + scaleIn(
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
                    initialScale = 0.88f
                ),
                exit = fadeOut(tween(160)) + scaleOut(tween(180), targetScale = 0.94f)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = ObsidianSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(palette.success.fill))
                                .border(
                                    1.5.dp,
                                    Color(palette.success.ink).copy(alpha = 0.28f),
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            SiteIcon("check", null, Modifier.size(28.dp), Color(palette.success.ink))
                        }
                        Text(
                            text = "Hai finito il tutorial!",
                            color = TextPrimary,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "A posto! Ora sai cambiare, aggiungere, segnare la reperibilità ed eliminare i turni.",
                            color = TextSecondary,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CloudButton(
                                label = "A posto · Continua",
                                icon = "arrow-right",
                                onClick = { finish(onContinue) },
                                enabled = !closing
                            )
                            CloudButton(
                                label = "Riprova",
                                icon = "rotate-ccw",
                                onClick = { finish(onRetry) },
                                enabled = !closing,
                                secondary = true
                            )
                        }
                    }
                }
            }
        }
    }
}
