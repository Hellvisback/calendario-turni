@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.*
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private data class PracticeLesson(val title: String, val instruction: String, val mode: CalendarEditMode = CalendarEditMode.REPLACE, val tool: String? = null)
private val practiceLessons = listOf(
    PracticeLesson("Cambia un turno", "Scegli un modello nella barra, poi tocca il giorno da modificare."),
    PracticeLesson("Cambia più giorni", "Tieni premuto sul primo giorno, poi tocca l’ultimo (anche a ritroso)."),
    PracticeLesson("Aggiungi più turni", "In Più turni aggiungi senza sostituire. Tieni premuto per un intervallo.", CalendarEditMode.ADD),
    PracticeLesson("Gomma e reperibilità", "Con la gomma tieni premuto sul primo giorno e tocca l’ultimo; la campanella segna la reperibilità.", tool = "eraser"),
    PracticeLesson("Ripeti una rotazione", "Tocca il giorno iniziale e quello finale: la sequenza scelta si ripete.", CalendarEditMode.ROTATION),
    PracticeLesson("Annulla o chiudi", "Annulla recupera l’ultima azione. La X chiude gli strumenti; la matita li riapre.")
)

/** The production calendar canvas and toolbar, supplied only with local rehearsal data. */
@Composable
internal fun GesturePractice(showGuide: Boolean = true, interactive: Boolean = showGuide, compactPreview: Boolean = false) {
    val work = ShiftRepository.activeWorkProfileId
    val models = ShiftRepository.shiftDefinitions.filter { it.workProfileId == work }.ifEmpty { starterShiftDefinitions(work) }
    val defs = models.associateBy { it.id }
    val modelIds = models.map { it.id }
    val cycle = remember(modelIds) { ShiftRotation("practice", "Sequenza di prova", 4, List(4) { modelIds[it % modelIds.size] }) }
    val initial = remember(modelIds) { initialCalendarPractice(modelIds) }
    var state by remember(modelIds) { mutableStateOf(initial) }
    var history by remember(modelIds) { mutableStateOf(listOf<CalendarPracticeState>()) }
    var completedLessons by remember(modelIds) { mutableStateOf(emptySet<Int>()) }
    var editing by remember { mutableStateOf(interactive) }
    val editProgress = animateFloatAsState(if (editing) 1f else 0f, spring(.92f, 240f))
    var editSession by remember { mutableStateOf(0L) }
    var mode by remember { mutableStateOf(CalendarEditMode.REPLACE) }
    var brush by remember(modelIds) { mutableStateOf(modelIds.first()) }
    var tool by remember { mutableStateOf<String?>(null) }
    var anchor by remember { mutableStateOf<Int?>(null) }
    var eraseChoice by remember { mutableStateOf<Int?>(null) }
    var lesson by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    val accessibility = LocalAccessibilityManager.current
    val fontScale = LocalDensity.current.fontScale
    val largeFont = fontScale > 1.3f
    val feedbackTimeout = accessibility?.calculateRecommendedTimeoutMillis(4200L, containsText = true) ?: 4200L
    LaunchedEffect(message, feedbackTimeout) {
        if (message.isNotEmpty()) {
            delay(feedbackTimeout)
            message = ""
        }
    }
    var paintRequest by remember { mutableStateOf<CalendarPaintRequest?>(null) }
    var paintSequence by remember { mutableStateOf(0L) }
    val monday = ShiftRepository.appPreferences.weekStartMonday
    val dates = remember(monday) { calendarPracticeDates(LocalDate.now(), monday) }
    val firstDate = dates.first()
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
    fun change(next: CalendarPracticeState) { history = (history + state).takeLast(20); state = next }
    fun changeLesson(next: Int) {
        lesson = next; val target = practiceLessons[next]
        mode = target.mode; tool = target.tool; anchor = null; eraseChoice = null; message = ""
        if (!editing) { editing = true; editSession++ }
    }
    fun resetPractice() {
        state = initial; history = emptyList(); completedLessons = emptySet()
        anchor = null; eraseChoice = null; paintRequest = null; editSession++
        message = "Esempio ripristinato."
    }
    fun apply(index: Int) {
        if (index !in state.days.indices || !interactive || !editing) return
        if (tool == "bell") {
            val enabled = index !in state.bells
            change(state.copy(bells = if (enabled) state.bells + index else state.bells - index))
            completedLessons = completedLessons + 3
            message = if (enabled) "Reperibilità attivata. Tocca ancora per rimuoverla." else "Reperibilità rimossa."
            return
        }
        if (mode == CalendarEditMode.ROTATION && tool != "eraser" && (anchor == null || index < anchor!!)) {
            anchor = index; message = "Inizio scelto. Tocca il giorno finale della rotazione."; return
        }
        if (tool == "eraser" && anchor == null && state.days[index].size > 1) {
            eraseChoice = index; message = "Scegli il solo turno da eliminare."; return
        }
        val first = anchor ?: index
        val erased = tool == "eraser"
        change(applyCalendarPractice(state, first, index, brush, mode, erased, cycle.daySequence))
        paintSequence++
        paintRequest = if (!erased && mode == CalendarEditMode.ROTATION) {
            // Match the production editor: acknowledge the endpoint with its actual cycle model.
            val definitionId = cycle.daySequence[(index - first) % cycle.daySequence.size]
            CalendarPaintRequest(dates[index], definitionId, paintSequence)
        } else CalendarPaintRequest(dates[first], if (erased) "" else brush, paintSequence, dates[index])
        anchor = null; eraseChoice = null
        val count = kotlin.math.abs(index - first) + 1
        completedLessons = completedLessons + completedLessonForAction(mode, tool, count)
        message = if (erased) "${if (count == 1) "Giorno liberato" else "$count giorni liberati"}. Annulla recupera la modifica." else if (count > 1) "$count giorni aggiornati con un’unica azione." else "Turno ${if (mode == CalendarEditMode.ADD) "aggiunto" else "cambiato"}. Prova un’altra data."
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showGuide && editing) {
            val palette = currentTurniPalette
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(practiceLessons[lesson].title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (lesson in completedLessons) {
                        Box(Modifier.clip(RoundedCornerShape(50)).background(Color(palette.success.fill)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            SiteIcon("check", "Funzione provata", Modifier.size(12.dp), Color(palette.success.ink))
                        }
                    }
                    Text("${lesson + 1} / ${practiceLessons.size}", color = TextSecondary, fontSize = 11.sp, softWrap = false)
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
                            spring(stiffness = 350f)
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { changeLesson(idx) }
                                .semantics {
                                    contentDescription = "${idx + 1}: ${item.title}" + if (done) ", provata" else ""
                                    selected = active
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.fillMaxWidth().height(if (active) 5.dp else 4.dp).clip(RoundedCornerShape(3.dp)).background(segmentColor))
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CalendarSurface).padding(4.dp)) {
                Text("${firstDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))} — ${dates.last().format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))}", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 5.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth()) {
                    dates.take(7).forEach { date -> Text(date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.ITALIAN)).uppercase(Locale.ITALIAN), color = if (date.dayOfWeek.value >= 6) BrandInk else TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).background(if(date.dayOfWeek.value >= 6) CalendarWeekend else CalendarSurface).padding(vertical = 4.dp)) }
                }
                val gridHeight by animateDpAsState(
                    when {
                        largeFont -> (176.dp * (fontScale / 1.3f)).coerceIn(184.dp, 224.dp)
                        editing -> 152.dp
                        compactPreview -> 162.dp
                        else -> 184.dp
                    },
                    spring(.92f, 240f)
                )
                NativeMonthGrid(
                    month = YearMonth.from(firstDate), selection = null, editing = editing,
                    mode = if (anchor != null && (tool == "eraser" || mode != CalendarEditMode.ROTATION)) CalendarEditMode.RANGE else mode,
                    brush = if (tool == "eraser") "eraser" else brush, rotationId = cycle.id,
                    anchor = anchor?.let { dates[it] }, rangeEnd = null,
                    modifier = Modifier.fillMaxWidth().height(gridHeight).then(if (!editing) Modifier.clearAndSetSemantics { contentDescription = "Anteprima del calendario con turni, riposi e ferie" } else Modifier), paintRequest = paintRequest, animate = interactive,
                    editSession = editSession, editProgress = editProgress,
                    onPaintConsumed = { paintRequest = null },
                    onClick = { val idx = dates.indexOf(it); if (idx >= 0) apply(idx) },
                    onPreview = {},
                    longPressEnabled = editing && tool != "bell" && (tool == "eraser" || mode != CalendarEditMode.ROTATION),
                    onLongClick = { date ->
                        val idx = dates.indexOf(date)
                        if (idx >= 0) {
                            anchor = idx; eraseChoice = null
                            lesson = if (tool == "eraser") 3 else if (mode == CalendarEditMode.ADD) 2 else 1
                            message = "Inizio scelto: tocca l’ultimo giorno, anche a ritroso."
                        }
                    },
                    previewData = preview, editingBottomInset = 0.dp
                )
            }
        }

        AnimatedVisibility(editing, enter = expandVertically(spring(.92f, 300f)) + fadeIn(), exit = shrinkVertically(spring(.95f, 300f)) + fadeOut()) {
            NativeEditToolbar(mode, brush, cycle.id, tool,
                onTool = {
                    val nextTool = if (tool == it) null else it
                    tool = nextTool; anchor = null; eraseChoice = null
                    if (nextTool == "eraser" || nextTool == "bell") lesson = 3
                    message = when (nextTool) {
                        "bell" -> "Tocca una data per attivare o togliere la reperibilità."
                        "eraser" -> "Tocca un turno da cancellare, o tieni premuto per un intervallo."
                        else -> if (it == "bell") "Reperibilità chiusa." else "Gomma chiusa."
                    }
                },
                onMode = {
                    mode = it; tool = null; anchor = null; eraseChoice = null
                    lesson = when (it) {
                        CalendarEditMode.ADD -> 2
                        CalendarEditMode.ROTATION -> 4
                        else -> if (lesson == 1) 1 else 0
                    }
                    message = ""
                },
                onBrush = { brush = it; tool = null; anchor = null; message = "${defs[it]?.name.orEmpty()} selezionato: scegli una data." },
                onRotation = { anchor = null; message = "Scegli il primo e l’ultimo giorno della sequenza." },
                onUndo = {
                    if (history.isNotEmpty()) {
                        state = history.last(); history = history.dropLast(1)
                        anchor = null; eraseChoice = null; paintRequest = null; editSession++
                        completedLessons = completedLessons + 5
                        message = "Ultima azione annullata."
                    } else message = "Non hai ancora modificato questo esempio."
                },
                onClose = {
                    editing = false; anchor = null; eraseChoice = null; message = ""
                    completedLessons = completedLessons + 5
                },
                catalogOverride = models, rotationsOverride = listOf(cycle))
        }
        if (showGuide && editing) {
            val activeFeedback = message.isNotEmpty()
            val cardHeight = if (largeFont) 92.dp else 66.dp
            Box(Modifier.fillMaxWidth().heightIn(min = cardHeight)
                .clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(message.ifEmpty { practiceLessons[lesson].instruction } to activeFeedback, transitionSpec = { fadeIn() togetherWith fadeOut() }) { (text, isFeedback) ->
                    Text(text, color = if (isFeedback) TextPrimary else TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = if (largeFont) 6 else 3,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
        if (interactive && !editing) CloudButton("Riapri la matita", "edit", { editing = true; editSession++; message = "" }, secondary = true)
        eraseChoice?.let { day ->
            CloudCard("Quale turno del ${dates[day].dayOfMonth}?") {
                state.days[day].forEachIndexed { position, id -> CloudButton(defs[id]?.name ?: "Turno", "eraser", {
                    val remaining = state.days[day].filterIndexed { index, _ -> index != position }
                    change(state.copy(days = state.days.mapIndexed { index, shifts -> if (index == day) remaining else shifts }, bells = state.bells - day))
                    completedLessons = completedLessons + 3
                    eraseChoice = null; message = "Eliminato solo il turno scelto."
                }, secondary = true) }
            }
        }
        if (showGuide && editing) {
            if (!largeFont) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lesson > 0) {
                        TextButton(onClick = { changeLesson(lesson - 1) }) { Text("Precedente", color = TextSecondary, maxLines = 1) }
                    } else {
                        Text("Prova locale", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (interactive) {
                        TextButton(onClick = ::resetPractice) { Text("Ripristina", color = TextSecondary, fontSize = 12.sp, maxLines = 1) }
                    }
                    TextButton(onClick = { changeLesson(if (lesson < practiceLessons.lastIndex) lesson + 1 else 0) }) {
                        Text(if (lesson < practiceLessons.lastIndex) "Prossima" else "Rivedi", color = BrandInk, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lesson > 0) TextButton(onClick = { changeLesson(lesson - 1) }) { Text("Precedente", color = TextSecondary) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { changeLesson(if (lesson < practiceLessons.lastIndex) lesson + 1 else 0) }) {
                            Text(if (lesson < practiceLessons.lastIndex) "Prossima" else "Rivedi", color = BrandInk, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (interactive) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Prova locale", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
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
}
