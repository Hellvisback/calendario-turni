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
import androidx.compose.ui.platform.LocalAccessibilityManager
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
    PracticeLesson("Un tocco, un turno", "Scegli un modello nella barra, poi tocca una data: Cambia sostituisce il turno di quel giorno."),
    PracticeLesson("Più giorni insieme", "Tieni premuto sul primo giorno, poi tocca l’ultimo. Puoi scegliere l’intervallo anche a ritroso."),
    PracticeLesson("Due turni, stesso giorno", "Con Più turni aggiungi senza sostituire. I riquadri restano uguali; i pallini indicano quelli che non entrano.", CalendarEditMode.ADD),
    PracticeLesson("Una sequenza che si ripete", "Scegli il primo e l’ultimo giorno: la rotazione riempie l’intervallo ripetendo i tuoi modelli.", CalendarEditMode.ROTATION),
    PracticeLesson("Campanella e gomma", "La campanella attiva la reperibilità. La gomma elimina un turno; tienila premuta su una data per cancellare un intervallo.", tool = "bell"),
    PracticeLesson("Chiudi e guarda i dettagli", "Annulla recupera l’ultima azione. La X chiude la modifica: tocca una data per provare note, straordinario e trasferta.")
)

/** The production calendar canvas and toolbar, supplied only with local rehearsal data. */
@Composable
internal fun GesturePractice(showGuide: Boolean = true) {
    val work = ShiftRepository.activeWorkProfileId
    val models = ShiftRepository.shiftDefinitions.filter { it.workProfileId == work }.ifEmpty { starterShiftDefinitions(work) }
    val defs = models.associateBy { it.id }
    val modelIds = models.map { it.id }
    val cycle = remember(modelIds) { ShiftRotation("practice", "Sequenza di prova", 4, List(4) { modelIds[it % modelIds.size] }) }
    val initial = remember(modelIds) { initialCalendarPractice(modelIds) }
    var state by remember(modelIds) { mutableStateOf(initial) }
    var history by remember(modelIds) { mutableStateOf(listOf<CalendarPracticeState>()) }
    var editing by remember { mutableStateOf(true) }
    val editProgress = animateFloatAsState(if (editing) 1f else 0f, spring(.92f, 240f))
    var editSession by remember { mutableStateOf(0L) }
    var mode by remember { mutableStateOf(CalendarEditMode.REPLACE) }
    var brush by remember(modelIds) { mutableStateOf(modelIds.first()) }
    var tool by remember { mutableStateOf<String?>(null) }
    var anchor by remember { mutableStateOf<Int?>(null) }
    var eraseChoice by remember { mutableStateOf<Int?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var lesson by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var lastFeedback by remember { mutableStateOf("") }
    val accessibility = LocalAccessibilityManager.current
    val feedbackTimeout = accessibility?.calculateRecommendedTimeoutMillis(4200L, containsText = true) ?: 4200L
    LaunchedEffect(message, feedbackTimeout) {
        if (message.isNotEmpty()) {
            lastFeedback = message
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
        mode = target.mode; tool = target.tool; anchor = null; eraseChoice = null; selectedDay = null; message = ""
        if (!editing) { editing = true; editSession++ }
    }
    fun apply(index: Int) {
        if (!editing) { selectedDay = index; message = ""; return }
        if (tool == "bell") {
            val enabled = index !in state.bells
            change(state.copy(bells = if (enabled) state.bells + index else state.bells - index))
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
        message = if (erased) "${if (count == 1) "Giorno liberato" else "$count giorni liberati"}. Annulla recupera la modifica." else if (count > 1) "$count giorni aggiornati con un’unica azione." else "Turno ${if (mode == CalendarEditMode.ADD) "aggiunto" else "cambiato"}. Prova un’altra data."
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showGuide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(practiceLessons[lesson].title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${lesson + 1} / ${practiceLessons.size}", color = TextSecondary, fontSize = 11.sp)
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CalendarSurface).padding(4.dp)) {
                Text("${firstDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))} — ${dates.last().format(DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN))}", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 5.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth()) {
                    dates.take(7).forEach { date -> Text(date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.ITALIAN)).uppercase(Locale.ITALIAN), color = if (date.dayOfWeek.value >= 6) BrandInk else TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).background(if(date.dayOfWeek.value >= 6) CalendarWeekend else CalendarSurface).padding(vertical = 4.dp)) }
                }
                val gridHeight by animateDpAsState(
                    if (showGuide) { if (editing) 204.dp else 224.dp }
                    else { if (editing) 168.dp else 184.dp }, spring(.92f, 240f))
                NativeMonthGrid(
                    month = YearMonth.from(firstDate), selection = selectedDay?.let { dates[it] }, editing = editing,
                    mode = if (anchor != null && mode != CalendarEditMode.ROTATION) CalendarEditMode.RANGE else mode,
                    brush = if (tool == "eraser") "eraser" else brush, rotationId = cycle.id,
                    anchor = anchor?.let { dates[it] }, rangeEnd = null,
                    modifier = Modifier.fillMaxWidth().height(gridHeight), paintRequest = paintRequest,
                    editSession = editSession, editProgress = editProgress,
                    onPaintConsumed = { paintRequest = null }, onClick = { apply(dates.indexOf(it)) }, onPreview = {},
                    longPressEnabled = editing && tool != "bell" && (tool == "eraser" || mode != CalendarEditMode.ROTATION),
                    onLongClick = { date -> anchor = dates.indexOf(date); eraseChoice = null; lesson = if (tool == "eraser") 4 else 1; message = "Inizio scelto: tocca l’ultimo giorno, anche a ritroso." },
                    previewData = preview, editingBottomInset = 0.dp
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = message.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                enter = slideInVertically(spring(.95f, 380f)) { it / 3 } + fadeIn(),
                exit = slideOutVertically(spring(1f, 420f)) { it / 3 } + fadeOut()
            ) {
                // No click/pointer handler: the calendar remains interactive beneath the feedback.
                Text(message.ifEmpty { lastFeedback }, color = TextPrimary, fontSize = 12.sp, lineHeight = 18.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceHigh).padding(horizontal = 12.dp, vertical = 10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        AnimatedVisibility(editing, enter = expandVertically(spring(.92f, 300f)) + fadeIn(), exit = shrinkVertically(spring(.95f, 300f)) + fadeOut()) {
            NativeEditToolbar(mode, brush, cycle.id, tool,
                onTool = { tool = if (tool == it) null else it; anchor = null; eraseChoice = null; lesson = 4; message = when (it) { "bell" -> "Tocca una data per cambiare la reperibilità."; else -> "Tocca un turno da cancellare, oppure tieni premuto per un intervallo." } },
                onMode = { mode = it; tool = null; anchor = null; eraseChoice = null; lesson = when (it) { CalendarEditMode.ADD -> 2; CalendarEditMode.ROTATION -> 3; else -> 0 }; message = "" },
                onBrush = { brush = it; tool = null; anchor = null; message = "${defs[it]?.name.orEmpty()} selezionato: scegli una data." },
                onRotation = { anchor = null; message = "Scegli il primo e l’ultimo giorno della sequenza." },
                onUndo = { if (history.isNotEmpty()) { state = history.last(); history = history.dropLast(1); anchor = null; eraseChoice = null; paintRequest = null; editSession++; message = "Ultima azione annullata." } else message = "Non hai ancora modificato questo esempio." },
                onClose = { editing = false; anchor = null; eraseChoice = null; lesson = 5; message = "Tocca una data per i dettagli, oppure riapri la matita." },
                catalogOverride = models, rotationsOverride = listOf(cycle))
        }
        if (!editing) CloudButton("Riapri la matita", "edit", { editing = true; selectedDay = null; editSession++; message = "" }, secondary = true)
        if (showGuide) AnimatedContent(lesson, transitionSpec = { fadeIn(spring(stiffness = 420f)) + slideInHorizontally(spring(.95f, 420f)) { it / 14 } togetherWith fadeOut(tween(110)) }) { index ->
            Text(practiceLessons[index].instruction, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        eraseChoice?.let { day ->
            CloudCard("Quale turno del ${dates[day].dayOfMonth}?") {
                state.days[day].forEachIndexed { position, id -> CloudButton(defs[id]?.name ?: "Turno", "eraser", {
                    val remaining = state.days[day].filterIndexed { index, _ -> index != position }
                    change(state.copy(days = state.days.mapIndexed { index, shifts -> if (index == day) remaining else shifts }, bells = state.bells - day))
                    eraseChoice = null; message = "Eliminato solo il turno scelto."
                }, secondary = true) }
            }
        }
        selectedDay?.takeIf { !editing }?.let { day ->
            CloudCard("${dates[day].dayOfMonth} · dettagli di prova") {
                Text(state.days[day].joinToString(" · ") { defs[it]?.name.orEmpty() }.ifEmpty { "Nessun turno" }, color = TextPrimary, fontSize = 13.sp)
                if (state.days[day].isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = day in state.overtime, onClick = { change(state.copy(overtime = if (day in state.overtime) state.overtime - day else state.overtime + day)) }, label = { Text("+1h", fontSize = 12.sp) })
                        FilterChip(selected = day in state.travel, onClick = { change(state.copy(travel = if (day in state.travel) state.travel - day else state.travel + day)) }, label = { Text("Trasferta", fontSize = 12.sp) })
                    }
                }
                TextButton(onClick = { change(state.copy(notes = if (day in state.notes) state.notes - day else state.notes + day)) }) { SiteIcon("message", null, Modifier.size(16.dp), BrandInk); Spacer(Modifier.width(6.dp)); Text(if (day in state.notes) "Rimuovi nota di prova" else "Aggiungi nota di prova", color = BrandInk, fontSize = 12.sp) }
                Text("Nel calendario, la rotella apre i dettagli del turno. Note ed etichette raccoglie le attività della giornata.", color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        if (showGuide) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lesson > 0) TextButton(onClick = { changeLesson(lesson - 1) }) { Text("Precedente", color = TextSecondary) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { if (lesson < practiceLessons.lastIndex) changeLesson(lesson + 1) else changeLesson(0) }) { Text(if (lesson < practiceLessons.lastIndex) "Prossimo gesto" else "Rivedi i gesti", color = BrandInk, fontWeight = FontWeight.SemiBold) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Esempio locale · nessun dato salvato", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { state = initial; history = emptyList(); anchor = null; eraseChoice = null; selectedDay = null; paintRequest = null; editSession++; message = "Esempio ripristinato." }) { Text("Ricomincia", color = BrandInk, fontSize = 11.sp) }
        }
    }
}
