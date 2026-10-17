package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.components.DayInspectionCard
import com.turni.calendario.ui.components.FluidGlassContainer
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.MagicalActionFeedbackHUD
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class CalendarDayInfo(
    val date: LocalDate,
    val isCurrentMonth: Boolean
)

enum class CalendarEditMode(val label: String, val icon: ImageVector) {
    REPLACE("Cambia", Icons.Default.SwapHoriz),
    ADD("Più Turni", Icons.Default.AddCircleOutline),
    RANGE("Periodo", Icons.Default.DateRange),
    ROTATION("Rotazione", Icons.Default.Loop)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun CalendarScreen(
    onEditModeActiveChanged: (Boolean) -> Unit = {}
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var monthTransitionDirection by remember { mutableStateOf(1) }

    // Modalità Modifica Turni Avanzata (Sostituisci, + Turno, Intervallo, Rotazione)
    var isPaintModeActive by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(CalendarEditMode.REPLACE) }
    var selectedPaintShiftId by remember { mutableStateOf("pomeriggio") }
    var selectedRotationId by remember { mutableStateOf(ShiftRepository.defaultRotationId.ifEmpty { ShiftRepository.activeRotationId }) }
    var rotationStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var rangeStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var showSelectEndDialog by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<ActionFeedback?>(null) }

    var newShiftToEdit by remember { mutableStateOf<ShiftDefinition?>(null) }

    LaunchedEffect(isPaintModeActive, newShiftToEdit) {
        onEditModeActiveChanged(isPaintModeActive || newShiftToEdit != null)
    }

    LaunchedEffect(ShiftRepository.selectedDateFromWidget) {
        ShiftRepository.selectedDateFromWidget?.let { widgetDate ->
            selectedDate = widgetDate
            currentMonth = YearMonth.from(widgetDate)
            ShiftRepository.selectedDateFromWidget = null
        }
    }

    // Dialog aggiunta/cambio turno per giorno specifico
    var showAddShiftDialogForDate by remember { mutableStateOf<LocalDate?>(null) }
    var isSecondaryShiftModal by remember { mutableStateOf(false) }

    // Gestione tasto indietro Android di sistema
    BackHandler(enabled = newShiftToEdit != null || showSelectEndDialog || showAddShiftDialogForDate != null || selectedDate != null || isPaintModeActive) {
        when {
            newShiftToEdit != null -> newShiftToEdit = null
            showSelectEndDialog -> showSelectEndDialog = false
            showAddShiftDialogForDate != null -> showAddShiftDialogForDate = null
            rangeStartDate != null -> rangeStartDate = null
            rotationStartDate != null -> rotationStartDate = null
            selectedDate != null -> selectedDate = null
            isPaintModeActive -> {
                isPaintModeActive = false
                rangeStartDate = null
                rotationStartDate = null
            }
        }
    }

    if (newShiftToEdit != null) {
        ShiftEditorScreen(
            shift = newShiftToEdit!!,
            onDismiss = { newShiftToEdit = null },
            onSave = { saved ->
                ShiftRepository.saveShiftDefinition(saved)
                selectedPaintShiftId = saved.id
                newShiftToEdit = null
                actionFeedback = ActionFeedback("Turno '${saved.name}' salvato!", isDestructive = false)
            },
            onDelete = { id ->
                ShiftRepository.deleteShiftDefinition(id)
                newShiftToEdit = null
            }
        )
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    var calendarWidthPx by remember { mutableStateOf(1080f) }
    var isDraggingMonth by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            // 1. Header Superiore Rigido e Stabile: Navigazione Mese + Pill Anno + Tasti Rapidi (Altezza bloccata a 48dp fissa)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selettore Mese fluido
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        onClick = {
                            coroutineScope.launch {
                                dragOffsetX.animateTo(
                                    targetValue = calendarWidthPx,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
                                )
                                currentMonth = currentMonth.minusMonths(1)
                                selectedDate = null
                                dragOffsetX.snapTo(0f)
                            }
                        },
                        shape = CircleShape,
                        color = ObsidianSurfaceContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Mese precedente",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentMonth.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN)
                                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ITALIAN) else it.toString() },
                                fontSize = 18.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianSurfaceHigh
                            ) {
                                Text(
                                    text = currentMonth.year.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPrimary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            coroutineScope.launch {
                                dragOffsetX.animateTo(
                                    targetValue = -calendarWidthPx,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
                                )
                                currentMonth = currentMonth.plusMonths(1)
                                selectedDate = null
                                dragOffsetX.snapTo(0f)
                            }
                        },
                        shape = CircleShape,
                        color = ObsidianSurfaceContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Mese successivo",
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Tasti rapidi a destra (Altezza fissa rigorosa 32dp per evitare oscillazioni verticali)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    val isCurrentActualMonth = currentMonth == YearMonth.of(2026, 10)
                    if (!isCurrentActualMonth) {
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    val isForward = currentMonth.isBefore(YearMonth.of(2026, 10))
                                    val targetAnim = if (isForward) -calendarWidthPx else calendarWidthPx
                                    dragOffsetX.animateTo(
                                        targetValue = targetAnim,
                                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
                                    )
                                    currentMonth = YearMonth.of(2026, 10)
                                    selectedDate = null
                                    dragOffsetX.snapTo(0f)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianPrimary.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    text = "OGGI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ObsidianPrimary
                                )
                            }
                        }
                    }

                    // Tasto Annulla (Undo)
                    Surface(
                        onClick = {
                            if (ShiftRepository.undo()) actionFeedback = ActionFeedback("Modifica annullata", isDestructive = true)
                            else actionFeedback = ActionFeedback("Nessuna modifica da annullare", isDestructive = false)
                        },
                        shape = CircleShape,
                        color = if (ShiftRepository.canUndo()) ObsidianPrimary.copy(alpha = 0.2f) else ObsidianSurfaceContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Annulla",
                                tint = if (ShiftRepository.canUndo()) ObsidianPrimary else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 2. Status Strip Statistiche Mese (Altezza fissa bloccata a 32dp per layout stabile)
            if (!isPaintModeActive) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .padding(horizontal = 2.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    val mStats = remember(currentMonth, ShiftRepository.calendarShifts.keys.size) {
                        val start = currentMonth.atDay(1)
                        val end = currentMonth.atEndOfMonth()
                        var curr = start
                        var mat = 0
                        var pom = 0
                        var not = 0
                        var rip = 0
                        var fer = 0
                        while (!curr.isAfter(end)) {
                            val list = ShiftRepository.getShiftsForDate(curr)
                            list.forEach {
                                when (it.shiftDefId) {
                                    "mattina" -> mat++
                                    "pomeriggio" -> pom++
                                    "notte" -> not++
                                    "riposo" -> rip++
                                    "ferie" -> fer++
                                }
                            }
                            curr = curr.plusDays(1)
                        }
                        listOf(
                            Triple("MAT", mat, ColorMattina),
                            Triple("POM", pom, ColorPomeriggio),
                            Triple("NOT", not, ColorNotte),
                            Triple("RIP", rip, ColorRiposo),
                            Triple("FER", fer, ColorFerie)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val activeProfile = ShiftRepository.activeWorkProfile
                            val rotName = ShiftRepository.activeRotation?.name ?: "Turni"
                            Icon(
                                Icons.Default.WorkOutline,
                                contentDescription = null,
                                tint = ObsidianPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${activeProfile.name} • $rotName",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            mStats.forEach { (_, count, color) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "$count",
                                        color = TextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 3. Intestazione giorni settimana con altezza fissa (22dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isMondayFirst = ShiftRepository.appPreferences.weekStartMonday
                val weekDays = if (isMondayFirst) listOf("L", "M", "M", "G", "V", "S", "D") else listOf("D", "L", "M", "M", "G", "V", "S")
                weekDays.forEachIndexed { idx, dayName ->
                    val isWeekend = if (isMondayFirst) idx >= 5 else (idx == 0 || idx == 6)
                    Text(
                        text = dayName,
                        color = if (isWeekend) Color(0xFFF59E0B) else TextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            val handleDayClick: (CalendarDayInfo) -> Unit = { dayInfo ->
                if (!isDraggingMonth && kotlin.math.abs(dragOffsetX.value) <= 6f) {
                    if (isPaintModeActive) {
                        when (editMode) {
                            CalendarEditMode.REPLACE -> {
                                if (selectedPaintShiftId == "eraser") {
                                    ShiftRepository.clearDayShifts(dayInfo.date)
                                    actionFeedback = ActionFeedback("Turno rimosso", isDestructive = true)
                                } else {
                                    ShiftRepository.replaceShift(dayInfo.date, selectedPaintShiftId)
                                    actionFeedback = ActionFeedback("Turno aggiornato", isDestructive = false)
                                }
                            }
                            CalendarEditMode.ADD -> {
                                if (selectedPaintShiftId != "eraser") {
                                    ShiftRepository.addSecondaryShift(dayInfo.date, selectedPaintShiftId)
                                    actionFeedback = ActionFeedback("Turno aggiunto", isDestructive = false)
                                }
                            }
                            CalendarEditMode.RANGE -> {
                                if (rangeStartDate == null) {
                                    rangeStartDate = dayInfo.date
                                    actionFeedback = ActionFeedback("Inizio periodo impostato • Tocca fine", isDestructive = false)
                                } else {
                                    val start = rangeStartDate ?: dayInfo.date
                                    val realStart = if (start.isBefore(dayInfo.date)) start else dayInfo.date
                                    val realEnd = if (start.isBefore(dayInfo.date)) dayInfo.date else start
                                    if (selectedPaintShiftId == "eraser") {
                                        var d = realStart
                                        while (!d.isAfter(realEnd)) {
                                            ShiftRepository.clearDayShifts(d)
                                            d = d.plusDays(1)
                                        }
                                        actionFeedback = ActionFeedback("Turni rimossi dal periodo", isDestructive = true)
                                    } else {
                                        ShiftRepository.applyShiftRange(selectedPaintShiftId, realStart, realEnd)
                                        actionFeedback = ActionFeedback("Turni applicati al periodo", isDestructive = false)
                                    }
                                    rangeStartDate = null
                                }
                            }
                            CalendarEditMode.ROTATION -> {
                                if (rotationStartDate == null) {
                                    rotationStartDate = dayInfo.date
                                    actionFeedback = ActionFeedback("Inizio impostato: ${dayInfo.date.dayOfMonth} ${dayInfo.date.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)} • Seleziona la fine", isDestructive = false)
                                } else {
                                    val start = rotationStartDate!!
                                    if (dayInfo.date.isBefore(start)) {
                                        rotationStartDate = dayInfo.date
                                        actionFeedback = ActionFeedback("Nuovo inizio: ${dayInfo.date.dayOfMonth} ${dayInfo.date.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)}", isDestructive = false)
                                    } else {
                                        val days = java.time.temporal.ChronoUnit.DAYS.between(start, dayInfo.date).toInt() + 1
                                        ShiftRepository.applyRotation(selectedRotationId, start, days)
                                        val rot = ShiftRepository.rotations.firstOrNull { it.id == selectedRotationId } ?: ShiftRepository.rotations.firstOrNull()
                                        val rotName = rot?.name ?: "Rotazione"
                                        actionFeedback = ActionFeedback("$rotName applicata fino al ${dayInfo.date.dayOfMonth} ${dayInfo.date.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)} ($days gg)", isDestructive = false)
                                        rotationStartDate = null
                                    }
                                }
                            }
                        }
                    } else {
                        selectedDate = if (selectedDate == dayInfo.date) null else dayInfo.date
                    }
                }
            }

            // 4. Griglia Mese Responsive con Scorrimento Orizzontale Continuo in Tempo Reale a 120 FPS
            val nextMonth = remember(currentMonth) { currentMonth.plusMonths(1) }
            val prevMonth = remember(currentMonth) { currentMonth.minusMonths(1) }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = if (isPaintModeActive) 210.dp else 105.dp)
                    .pointerInput(currentMonth) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDraggingMonth = true },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    dragOffsetX.snapTo(dragOffsetX.value + dragAmount)
                                }
                            },
                            onDragEnd = {
                                isDraggingMonth = false
                                val curOffset = dragOffsetX.value
                                // Soglia ultra-reattiva (~35px): basta un minimo tocco/scorrimento senza arrivare al bordo
                                val threshold = 35f
                                coroutineScope.launch {
                                    if (curOffset < -threshold) {
                                        // Swipe a sinistra -> Mese successivo istantaneo e fluido
                                        dragOffsetX.animateTo(
                                            targetValue = -calendarWidthPx,
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
                                        )
                                        currentMonth = currentMonth.plusMonths(1)
                                        selectedDate = null
                                        dragOffsetX.snapTo(0f)
                                    } else if (curOffset > threshold) {
                                        // Swipe a destra -> Mese precedente istantaneo e fluido
                                        dragOffsetX.animateTo(
                                            targetValue = calendarWidthPx,
                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
                                        )
                                        currentMonth = currentMonth.minusMonths(1)
                                        selectedDate = null
                                        dragOffsetX.snapTo(0f)
                                    } else {
                                        // Rimbalzo elastico
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                isDraggingMonth = false
                                coroutineScope.launch {
                                    dragOffsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        )
                    }
            ) {
                calendarWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(100f)
                val curOffset = dragOffsetX.value

                Box(modifier = Modifier.fillMaxSize()) {
                    // 1. Mese Corrente (arretra e sfuma con parallasse quando l'utente trascina)
                    val currentTranslationX = curOffset * 0.35f
                    val currentAlpha = (1f - (Math.abs(curOffset) / calendarWidthPx) * 0.55f).coerceIn(0.20f, 1f)

                    MonthGridView(
                        targetMonth = currentMonth,
                        selectedDate = selectedDate,
                        isPaintModeActive = isPaintModeActive,
                        editMode = editMode,
                        selectedPaintShiftId = selectedPaintShiftId,
                        selectedRotationId = selectedRotationId,
                        rotationStartDate = rotationStartDate,
                        rangeStartDate = rangeStartDate,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = currentTranslationX
                                alpha = currentAlpha
                            },
                        onDayClick = handleDayClick
                    )

                    // 2. Mese Successivo (entra da destra in sovrimpressione in tempo reale se curOffset < -1f)
                    if (curOffset < -1f) {
                        MonthGridView(
                            targetMonth = nextMonth,
                            selectedDate = null,
                            isPaintModeActive = isPaintModeActive,
                            editMode = editMode,
                            selectedPaintShiftId = selectedPaintShiftId,
                            selectedRotationId = selectedRotationId,
                            rotationStartDate = rotationStartDate,
                            rangeStartDate = rangeStartDate,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = calendarWidthPx + curOffset
                                    shadowElevation = 18.dp.toPx()
                                },
                            onDayClick = handleDayClick
                        )
                    }

                    // 3. Mese Precedente (entra da sinistra in sovrimpressione in tempo reale se curOffset > 1f)
                    if (curOffset > 1f) {
                        MonthGridView(
                            targetMonth = prevMonth,
                            selectedDate = null,
                            isPaintModeActive = isPaintModeActive,
                            editMode = editMode,
                            selectedPaintShiftId = selectedPaintShiftId,
                            selectedRotationId = selectedRotationId,
                            rotationStartDate = rotationStartDate,
                            rangeStartDate = rangeStartDate,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = -calendarWidthPx + curOffset
                                    shadowElevation = 18.dp.toPx()
                                },
                            onDayClick = handleDayClick
                        )
                    }
                }
            }
        }

        // Scheda Ispezione e Dettaglio Giorno (posizionata a 98dp per non compenetrare mai la barra di navigazione)
        AnimatedVisibility(
            visible = selectedDate != null && !isPaintModeActive,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 10.dp, end = 10.dp, bottom = 98.dp)
        ) {
            if (selectedDate != null) {
                DayInspectionCard(
                    date = selectedDate!!,
                    onPreviousDay = { selectedDate = selectedDate!!.minusDays(1) },
                    onNextDay = { selectedDate = selectedDate!!.plusDays(1) },
                    onOpenEdit = {
                        isSecondaryShiftModal = false
                        showAddShiftDialogForDate = selectedDate
                    },
                    onAddShift = {
                        isSecondaryShiftModal = true
                        showAddShiftDialogForDate = selectedDate
                    },
                    onDeleteShift = { assignedId ->
                        ShiftRepository.removeAssignedShift(selectedDate!!, assignedId)
                        actionFeedback = ActionFeedback("Turno eliminato", isDestructive = true)
                    },
                    onClose = { selectedDate = null }
                )
            }
        }

        // Pillola Flottante Guida Periodo / Rotazione (Stile Riccardo Breccia Thumb Zone)
        val currentGuideStart = rangeStartDate ?: rotationStartDate
        AnimatedVisibility(
            visible = isPaintModeActive && currentGuideStart != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 190.dp)
        ) {
            val isRange = rangeStartDate != null
            val isEraser = selectedPaintShiftId == "eraser"
            val targetShift = ShiftRepository.getShiftDefinition(selectedPaintShiftId)
            val targetShiftName = targetShift?.name ?: "Turno"
            val safeStart = rangeStartDate ?: rotationStartDate
            if (safeStart != null) {
                val startDayText = "${safeStart.dayOfMonth} ${safeStart.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ITALIAN)}"
                val guideLabel = when {
                    isRange && isEraser -> "🧹 Da $startDayText • Tocca prima o dopo per cancellare"
                    isRange -> "📍 Da $startDayText • Tocca prima o dopo per $targetShiftName"
                    else -> "🔄 Da $startDayText • Tocca fine rotazione"
                }
                val guideColor = when {
                    isRange && isEraser -> Color(0xFFDC2626)
                    isRange -> Color(0xFF0284C7)
                    else -> Color(0xFF0284C7)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .shadow(elevation = 14.dp, shape = RoundedCornerShape(22.dp), spotColor = guideColor.copy(alpha = 0.5f))
                            .bouncyClickable {
                                if (!isRange) {
                                    showSelectEndDialog = true
                                }
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = guideColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = guideLabel,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                maxLines = 1
                            )
                            if (!isRange) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Imposta cicli",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(elevation = 10.dp, shape = CircleShape)
                            .bouncyClickable {
                                rotationStartDate = null
                                rangeStartDate = null
                            },
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Annulla selezione",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // HUD Feedback Azioni Magiche (Posizionato più in alto per non ostruire il calendario e statico dove scende)
        MagicalActionFeedbackHUD(
            feedback = actionFeedback,
            onDismiss = { actionFeedback = null },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
        )

        // Palette Orizzontale Turni con selettore modalità
        AnimatedVisibility(
            visible = isPaintModeActive,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                PaintShiftPaletteBar(
                    editMode = editMode,
                    onSelectEditMode = { newMode ->
                        editMode = newMode
                        rangeStartDate = null
                        rotationStartDate = null
                    },
                    selectedShiftId = selectedPaintShiftId,
                    onSelectShift = { selectedPaintShiftId = it },
                    selectedRotationId = selectedRotationId,
                    onSelectRotation = { selectedRotationId = it },
                    rangeStartDate = rangeStartDate,
                    onCancelRange = { rangeStartDate = null },
                    rotationStartDate = rotationStartDate,
                    onCancelRotation = { rotationStartDate = null },
                    onAddNewShift = {
                        newShiftToEdit = ShiftDefinition(
                            id = java.util.UUID.randomUUID().toString(),
                            name = "Nuovo Turno",
                            shortCode = "NT",
                            startTime = "08:00",
                            endTime = "16:00",
                            colorHex = 0xFF38BDF8
                        )
                    },
                    onAddNewRotation = {
                        val rot = ShiftRotation(
                            id = "turno_custom_${System.currentTimeMillis()}",
                            name = "Nuova Rotazione",
                            totalDays = 28,
                            daySequence = ShiftRepository.rotations.firstOrNull()?.daySequence ?: emptyList()
                        )
                        ShiftRepository.rotations.add(rot)
                        selectedRotationId = rot.id
                    },
                    onUndo = {
                        if (ShiftRepository.undo()) actionFeedback = ActionFeedback("Modifica annullata", isDestructive = true)
                        else actionFeedback = ActionFeedback("Nessuna modifica da annullare", isDestructive = false)
                    },
                    onClose = {
                        isPaintModeActive = false
                        rangeStartDate = null
                        rotationStartDate = null
                    }
                )
            }
        }

        // Floating Action Button "Modifica Turni" in basso a destra (ergonomia a portata di pollice)
        AnimatedVisibility(
            visible = !isPaintModeActive && selectedDate == null,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 100.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ObsidianPrimary,
                shadowElevation = 14.dp,
                modifier = Modifier
                    .size(52.dp)
                    .bouncyClickable {
                        isPaintModeActive = true
                        selectedDate = null
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Modifica Turni",
                        tint = ObsidianBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

    }

    // Dialog selezione fine periodo per rotazione
    if (showSelectEndDialog && rotationStartDate != null) {
        val start = rotationStartDate!!
        AlertDialog(
            onDismissRequest = { showSelectEndDialog = false },
            title = { Text("Applica Rotazione", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column {
                    Text(
                        "Inizio: ${start.dayOfMonth} ${start.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ITALIAN)} ${start.year}",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    val rot = ShiftRepository.rotations.firstOrNull { it.id == selectedRotationId } ?: ShiftRepository.rotations.firstOrNull()
                    val cycleDays = (rot?.totalDays ?: 28).coerceAtLeast(1)
                    val rotName = rot?.name ?: "Rotazione"

                    Text("Scegli per quanti cicli ripetere la rotazione ($cycleDays gg a ciclo):", color = Color.White, fontSize = 13.5.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    listOf(
                        "1 Ciclo ($cycleDays giorni)" to cycleDays,
                        "2 Cicli (${cycleDays * 2} giorni)" to (cycleDays * 2),
                        "3 Cicli (${cycleDays * 3} giorni)" to (cycleDays * 3),
                        "6 Mesi (${cycleDays * 6} giorni • 6 cicli)" to (cycleDays * 6),
                        "1 Anno (${cycleDays * 13} giorni • 13 cicli)" to (cycleDays * 13)
                    ).forEach { (label, days) ->
                        OutlinedButton(
                            onClick = {
                                if (rot != null) {
                                    ShiftRepository.applyRotation(rot.id, start, days)
                                    actionFeedback = ActionFeedback("$rotName applicata per $label!", isDestructive = false)
                                }
                                showSelectEndDialog = false
                                rotationStartDate = null
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSelectEndDialog = false }) {
                    Text("Annulla", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF151B26),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Dialog rapido aggiunta turno per una data
    if (showAddShiftDialogForDate != null) {
        AddShiftModal(
            date = showAddShiftDialogForDate!!,
            initialSecondaryMode = isSecondaryShiftModal,
            onDismiss = {
                showAddShiftDialogForDate = null
                isSecondaryShiftModal = false
            },
            onSelect = { defId, isSecondary ->
                val targetDate = showAddShiftDialogForDate!!
                if (isSecondary) {
                    ShiftRepository.addSecondaryShift(targetDate, defId)
                    actionFeedback = ActionFeedback("Turno aggiunto", isDestructive = false)
                } else {
                    ShiftRepository.replaceShift(targetDate, defId)
                    actionFeedback = ActionFeedback("Turno sostituito", isDestructive = false)
                }
                showAddShiftDialogForDate = null
                isSecondaryShiftModal = false
            }
        )
    }
}

fun getCompactShiftCode(shiftDefId: String, fallback: String): String {
    return when (shiftDefId) {
        "mattina" -> "MAT"
        "pomeriggio" -> "POM"
        "notte" -> "NOT"
        "smonto_notte" -> "SMO"
        "riposo" -> "RIP"
        "ferie" -> "FER"
        "giornaliero" -> "GIO"
        "uffici" -> "UFF"
        "mat_dom" -> "DOM"
        "donazione" -> "DON"
        "malattia" -> "MAL"
        else -> if (fallback.length <= 4) fallback.uppercase() else fallback.take(3).uppercase()
    }
}

private fun Modifier.calendarCellClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) {
            try {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
            currentOnClick()
        }
}

fun getMonthGridDays(targetMonth: YearMonth, weekStartMonday: Boolean): List<CalendarDayInfo> {
    val firstDay = targetMonth.atDay(1)
    val prevM = targetMonth.minusMonths(1)
    val daysInPrev = prevM.lengthOfMonth()

    val list = mutableListOf<CalendarDayInfo>()
    if (weekStartMonday) {
        val firstDayOfWeek = firstDay.dayOfWeek.value
        for (i in (firstDayOfWeek - 1) downTo 1) {
            list.add(CalendarDayInfo(prevM.atDay(daysInPrev - i + 1), isCurrentMonth = false))
        }
    } else {
        val firstDayOfWeek = firstDay.dayOfWeek.value % 7
        for (i in firstDayOfWeek downTo 1) {
            list.add(CalendarDayInfo(prevM.atDay(daysInPrev - i + 1), isCurrentMonth = false))
        }
    }
    for (i in 1..targetMonth.lengthOfMonth()) {
        list.add(CalendarDayInfo(targetMonth.atDay(i), isCurrentMonth = true))
    }
    val nextM = targetMonth.plusMonths(1)
    var nextDay = 1
    // Completa SOLO l'ultima settimana in corso fino a multiplo di 7, senza settimane fantoccio in più!
    while (list.size % 7 != 0) {
        list.add(CalendarDayInfo(nextM.atDay(nextDay), isCurrentMonth = false))
        nextDay++
    }
    return list
}

@Composable
fun MonthGridView(
    targetMonth: YearMonth,
    selectedDate: LocalDate?,
    isPaintModeActive: Boolean,
    editMode: CalendarEditMode,
    selectedPaintShiftId: String,
    selectedRotationId: String,
    rotationStartDate: LocalDate?,
    rangeStartDate: LocalDate?,
    modifier: Modifier = Modifier,
    onDayClick: (CalendarDayInfo) -> Unit
) {
    val isMondayFirst = ShiftRepository.appPreferences.weekStartMonday
    val monthDays = remember(targetMonth, isMondayFirst) {
        getMonthGridDays(targetMonth, isMondayFirst)
    }
    val rows = remember(monthDays) {
        monthDays.chunked(7)
    }

    val currentSelectedRot = remember(selectedRotationId, ShiftRepository.rotations.size) {
        ShiftRepository.rotations.firstOrNull { it.id == selectedRotationId } ?: ShiftRepository.rotations.firstOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            .padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        rows.forEach { weekDays ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                weekDays.forEach { dayInfo ->
                    key(dayInfo.date) {
                        val isToday = dayInfo.date == LocalDate.now()
                        val isSelected = selectedDate == dayInfo.date
                        val isHoliday = ShiftRepository.isHoliday(dayInfo.date) != null
                        val shifts = ShiftRepository.getShiftsForDate(dayInfo.date)
                        val planned = ShiftRepository.getPlannedRotationShift(dayInfo.date)

                        DayCellView(
                            dayInfo = dayInfo,
                            isToday = isToday,
                            isSelected = isSelected,
                            isHoliday = isHoliday,
                            shifts = shifts,
                            plannedShift = planned,
                            rotationStartDate = if (isPaintModeActive && editMode == CalendarEditMode.ROTATION) rotationStartDate else null,
                            rangeStartDate = if (isPaintModeActive && editMode == CalendarEditMode.RANGE) rangeStartDate else null,
                            isPaintModeActive = isPaintModeActive,
                            editMode = editMode,
                            paintShiftId = selectedPaintShiftId,
                            activeRotation = if (isPaintModeActive && editMode == CalendarEditMode.ROTATION) currentSelectedRot else null,
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(dayInfo) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cella del calendario mensile con supporto a:
 * - Giorno corrente (badge bianco)
 * - Giorno selezionato (bordo celeste)
 * - Festività (sfondo rosso pieno e contrasto bianco come in SuperShift)
 * - Badge turni con orario
 * - Turno pianificato originario vs Turno modificato (Ghost shift in grigio tenue se c'è stato un cambio turno!)
 */
@Composable
fun DayCellView(
    dayInfo: CalendarDayInfo,
    isToday: Boolean,
    isSelected: Boolean,
    isHoliday: Boolean,
    shifts: List<AssignedShift>,
    plannedShift: ShiftDefinition?,
    rotationStartDate: LocalDate? = null,
    rangeStartDate: LocalDate? = null,
    isPaintModeActive: Boolean = false,
    editMode: CalendarEditMode = CalendarEditMode.REPLACE,
    paintShiftId: String = "",
    activeRotation: ShiftRotation? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isEraser = paintShiftId == "eraser"
    val isRangeStart = isPaintModeActive && editMode == CalendarEditMode.RANGE && rangeStartDate != null && dayInfo.date == rangeStartDate
    val cycleLengthDays = (activeRotation?.totalDays ?: 28).coerceAtLeast(1)
    val isCycleStart = rotationStartDate != null && dayInfo.date == rotationStartDate
    val isCoveredByCycle = rotationStartDate != null && dayInfo.date.isAfter(rotationStartDate)
    val daysBetween = if (isCoveredByCycle) java.time.temporal.ChronoUnit.DAYS.between(rotationStartDate!!, dayInfo.date) else 0L

    val isOnCall = ShiftRepository.isOnCall(dayInfo.date)
    val isDayOnCall = isOnCall && dayInfo.isCurrentMonth
    val dayExtra = ShiftRepository.getDayExtraInfo(dayInfo.date)

    val cellBackground = when {
        isRangeStart && isEraser -> Color(0xFF3B1212)
        isRangeStart -> Color(0xFF0C2A4D)
        isCycleStart -> Color(0xFF0C243B)
        isSelected && isDayOnCall -> Color(0xFF3B2507)
        isSelected -> ObsidianSurfaceHighest
        isToday && isDayOnCall -> Color(0xFF2E200C)
        isToday -> Color(0xFF13223A)
        isHoliday && dayInfo.isCurrentMonth -> Color(0xFF882020)
        isHoliday && !dayInfo.isCurrentMonth -> Color(0xFF451010)
        isDayOnCall -> Color(0xFF261904)
        dayInfo.isCurrentMonth -> ObsidianSurface
        else -> Color(0xFF070A11)
    }

    // Badge ciclo: compare '1' sul giorno d'inizio e '2', '3', '4'... all'inizio di ciascun ciclo successivo
    // (es. per ciclo da 28 giorni: '1' a giorno 0, '2' a +28 gg, '3' a +56 gg, '4' a +84 gg).
    // Se rotationStartDate è null, non compare NESSUN numero fuorviante nel mese!
    val cycleMultiplierText = when {
        isCycleStart -> "1"
        isCoveredByCycle && cycleLengthDays > 0 && daysBetween % cycleLengthDays == 0L -> {
            val mult = (daysBetween / cycleLengthDays).toInt() + 1
            mult.toString()
        }
        else -> null
    }

    val cellBorderWidth = when {
        isRangeStart || isCycleStart -> 2.5.dp
        isSelected -> 2.dp
        isDayOnCall -> 1.5.dp
        isCoveredByCycle -> 1.dp
        else -> 0.8.dp
    }

    val cellBorderColor = when {
        isRangeStart && isEraser -> Color(0xFFEF4444)
        isRangeStart -> Color(0xFF38BDF8)
        isCycleStart -> Color(0xFF38BDF8)
        isSelected -> ObsidianBorderActive
        isDayOnCall -> Color(0xFFF59E0B).copy(alpha = 0.92f)
        isCoveredByCycle -> Color(0xFF0284C7).copy(alpha = 0.65f)
        isToday -> ObsidianPrimary.copy(alpha = 0.6f)
        !dayInfo.isCurrentMonth -> Color(0x12FFFFFF)
        shifts.isEmpty() -> ObsidianBorderSubtle
        else -> ObsidianBorder
    }

    val cellCornerRadius = 10.dp
    val cellShape = RoundedCornerShape(cellCornerRadius)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(cellShape)
            .background(cellBackground)
            .border(
                width = cellBorderWidth,
                color = cellBorderColor,
                shape = cellShape
            )
            .calendarCellClickable { onClick() }
            .then(if (!dayInfo.isCurrentMonth) Modifier.alpha(0.35f) else Modifier)
            .padding(horizontal = 3.dp, vertical = 2.5.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header del giorno (numero concentrico + indicatori REP / Overtime / TR / Ciclo 2, 3...)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp, vertical = 0.5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val isSelectedToday = isToday && isSelected
                    if (isRangeStart) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = if (isEraser) Color(0xFFEF4444) else Color(0xFF0284C7)
                        ) {
                            Text(
                                text = "${dayInfo.date.dayOfMonth} ${if (isEraser) "🧹" else "📍"}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 0.8.dp)
                            )
                        }
                    } else if (isCycleStart) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = Color(0xFF0284C7)
                        ) {
                            Text(
                                text = "${dayInfo.date.dayOfMonth} 🔄1",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 0.8.dp)
                            )
                        }
                    } else if (isSelectedToday) {
                        // Oggi E Selezionato: pillola bianca con anello azzurro coordinato, nessun contatto con il bordo esterno
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, ObsidianPrimary)
                        ) {
                            Text(
                                text = dayInfo.date.dayOfMonth.toString(),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 0.8.dp)
                            )
                        }
                    } else if (isToday) {
                        // Solo Oggi: pillola bianca pura con testo nero marcato
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = Color.White
                        ) {
                            Text(
                                text = dayInfo.date.dayOfMonth.toString(),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 0.8.dp)
                            )
                        }
                    } else if (isSelected) {
                        // Solo Selezionato: pillola azzurro brillante ObsidianPrimary con testo scuro nitido
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = ObsidianPrimary
                        ) {
                            Text(
                                text = dayInfo.date.dayOfMonth.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = ObsidianBackground,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 0.8.dp)
                            )
                        }
                    } else {
                        // Giorno Normale: allineamento e respiro ottico impeccabili
                        Box(
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayInfo.date.dayOfMonth.toString(),
                                fontSize = 11.sp,
                                fontWeight = if (dayInfo.isCurrentMonth) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    !dayInfo.isCurrentMonth -> TextMuted.copy(alpha = 0.45f)
                                    isHoliday -> Color.White
                                    else -> TextPrimary
                                }
                            )
                        }
                    }

                    // Badge del ciclo di rotazione (2, 3, 4...) visibile e leggibile
                    if (cycleMultiplierText != null && dayInfo.isCurrentMonth && !isCycleStart) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF0284C7),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF38BDF8))
                        ) {
                            Text(
                                text = cycleMultiplierText,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 3.5.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                }

                val hasTrasferta = (shifts.any { it.isTrasferta } || dayExtra.isTrasferta) && dayInfo.isCurrentMonth
                val hasReperibilita = isOnCall && dayInfo.isCurrentMonth
                val hasOvertime = (shifts.any { it.overtimeHours > 0f } || dayExtra.overtimeHours > 0f) && dayInfo.isCurrentMonth
                val hasNotes = (dayExtra.notes.isNotBlank() || shifts.any { it.notes.isNotBlank() }) && dayInfo.isCurrentMonth

                // Indicatori in alto a destra: Reperibilità e Note sono sempre visibili;
                // Straordinari e Trasferta sono invece integrati direttamente nel turno specifico,
                // apparendo nell'header SOLO in caso eccezionale di giornata senza turni (shifts.isEmpty()).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Reperibilità Software / Stagionale (badge ambra)
                    if (hasReperibilita) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color(0xFFF59E0B))
                                .padding(horizontal = 1.5.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = "R",
                                fontSize = 5.8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E1300),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Fallback per Straordinario su giorno orfano senza turni
                    if (hasOvertime && shifts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(ObsidianPrimary.copy(alpha = 0.25f))
                                .padding(horizontal = 1.5.dp, vertical = 0.5.dp)
                        ) {
                            val otText = if (dayExtra.overtimeHours % 1f == 0f) "${dayExtra.overtimeHours.toInt()}h" else "${String.format(java.util.Locale.US, "%.1f", dayExtra.overtimeHours)}h"
                            Text(
                                text = "+$otText",
                                fontSize = 5.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Fallback per Trasferta su giorno orfano senza turni
                    if (hasTrasferta && shifts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.25f))
                                .padding(horizontal = 1.5.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = "TR",
                                fontSize = 5.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Indicatore Note (pallino azzurro)
                    if (hasNotes) {
                        Box(
                            modifier = Modifier
                                .size(3.5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // ANTEPRIMA ROTAZIONE O PERIODO ATTIVO (HA LA PRECEDENZA PER MOSTRARE IL PROSPETTO ESATTO!)
            val isRotationPreviewActive = (isCycleStart || isCoveredByCycle) && activeRotation != null && activeRotation.daySequence.isNotEmpty()
            val paintDef = if (isRangeStart && !isEraser && paintShiftId.isNotEmpty()) {
                ShiftRepository.getShiftDefinition(paintShiftId)
            } else null

            if (isRangeStart && isEraser) {
                // Indicatore visivo chiaro per cancellazione periodo sul giorno iniziale
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.25f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(vertical = 1.2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🧹 CANCELLA",
                        fontSize = 7.2.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else if (paintDef != null) {
                // Anteprima immediata del turno selezionato sul giorno iniziale della selezione periodo!
                ShiftCellBadge(
                    def = paintDef,
                    isModified = false,
                    customStartTime = null,
                    compactMode = false
                )
            } else if (isRotationPreviewActive) {
                val previewSeqIdx = (daysBetween % activeRotation!!.daySequence.size.coerceAtLeast(1)).toInt()
                val previewDefId = activeRotation.daySequence.getOrNull(previewSeqIdx)
                val previewDef = previewDefId?.let { ShiftRepository.getShiftDefinition(it) }
                if (previewDef != null) {
                    if (isCycleStart) {
                        // Giorno 1 d'inizio rotazione: BADGE COMPLETO E VIVIDO!
                        ShiftCellBadge(
                            def = previewDef,
                            isModified = false,
                            customStartTime = null,
                            compactMode = false
                        )
                    } else {
                        // Giorni successivi del ciclo: anteprima chiara e ad alto contrasto
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(previewDef.color.copy(alpha = 0.35f))
                                .border(1.dp, previewDef.color.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                .padding(vertical = 1.2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = getCompactShiftCode(previewDef.id, previewDef.shortCode),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            } else if (shifts.isNotEmpty()) {
                // Calcola in anticipo se c'è un ex-turno di rotazione da visualizzare
                val primaryShift = shifts.firstOrNull()
                val origShiftId = primaryShift?.originalShiftDefId
                val origDefName = if (primaryShift != null && primaryShift.isModifiedFromRotation && origShiftId != null && origShiftId != primaryShift.shiftDefId) {
                    val origDef = ShiftRepository.getShiftDefinition(origShiftId)
                    origDef?.let { getCompactShiftCode(it.id, it.shortCode) }
                } else null
                val showExBadge = ShiftRepository.appPreferences.showExShifts && !dayExtra.hideExShift && origDefName != null

                // Se c'è un ex-turno, mostra massimo 2 badge di turno interi e usa pallini compatti per i rimanenti, per garantire che l'ex-turno sia sempre visibile
                val displayCount = if (showExBadge) minOf(shifts.size, 2) else minOf(shifts.size, 3)
                val displayShifts = shifts.take(displayCount)
                val isMulti = shifts.size >= 2
                displayShifts.forEachIndexed { idx, shift ->
                    val def = ShiftRepository.getShiftDefinition(shift.shiftDefId)
                    if (def != null) {
                        if (idx > 0) {
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                        // Calcola straordinario e trasferta specifici di questo turno (o fallback da dayExtra per giorno a singolo turno)
                        val shiftTrasferta = shift.isTrasferta || (shifts.size == 1 && dayExtra.isTrasferta)
                        val shiftOtHours = if (shift.overtimeHours > 0f) {
                            shift.overtimeHours
                        } else if (shifts.size == 1 && dayExtra.overtimeHours > 0f) {
                            dayExtra.overtimeHours
                        } else {
                            0f
                        }

                        ShiftCellBadge(
                            def = def,
                            isModified = shift.isModifiedFromRotation,
                            customStartTime = shift.customStartTime,
                            compactMode = isMulti,
                            isTrasferta = shiftTrasferta,
                            overtimeHours = shiftOtHours
                        )
                    }
                }

                // Se ci sono altri turni inseriti oltre lo spazio, indicatore dinamico con pallini colorati per ciascun turno eccedente
                val overflowShifts = shifts.drop(displayCount)
                if (overflowShifts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    val overflowBg = when (overflowShifts.size) {
                        1 -> Color(0xFF38BDF8).copy(alpha = 0.22f)
                        2 -> Color(0xFFF59E0B).copy(alpha = 0.25f)
                        else -> Color(0xFFEC4899).copy(alpha = 0.30f)
                    }
                    val overflowBorder = when (overflowShifts.size) {
                        1 -> Color(0xFF38BDF8).copy(alpha = 0.5f)
                        2 -> Color(0xFFF59E0B).copy(alpha = 0.5f)
                        else -> Color(0xFFEC4899).copy(alpha = 0.6f)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(overflowBg)
                            .border(0.6.dp, overflowBorder, RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 0.8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        overflowShifts.take(3).forEach { extraShift ->
                            val extraDef = ShiftRepository.getShiftDefinition(extraShift.shiftDefId)
                            val dotColor = extraDef?.color ?: ObsidianPrimary
                            Box(
                                modifier = Modifier
                                    .size(4.5.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        if (overflowShifts.size > 3) {
                            Text(
                                text = "+${overflowShifts.size - 3}",
                                fontSize = 6.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                // Indicatore ex-turno sempre garantito nella cella (con pallino discreto e codice turno)
                if (showExBadge && origDefName != null) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF1E2638).copy(alpha = 0.95f))
                            .border(0.5.dp, Color(0xFF475569).copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 2.dp, vertical = 0.6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.width(2.5.dp))
                        Text(
                            text = "ex $origDefName",
                            fontSize = 6.2.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecondaryShiftCellBadge(def: ShiftDefinition) {
    val code = getCompactShiftCode(def.id, def.shortCode)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(def.color.copy(alpha = 0.25f))
            .border(0.8.dp, def.color, RoundedCornerShape(4.dp))
            .padding(vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+ $code",
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
fun ShiftCellBadge(
    def: ShiftDefinition,
    isModified: Boolean = false,
    customStartTime: String? = null,
    compactMode: Boolean = false,
    isTrasferta: Boolean = false,
    overtimeHours: Float = 0f
) {
    val code = getCompactShiftCode(def.id, def.shortCode)
    val timeText = customStartTime ?: if (def.isAllDay) "TG" else def.startTime

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.5.dp))
            .background(ObsidianBackground.copy(alpha = 0.85f))
            .border(
                width = if (isModified) 1.dp else 0.dp,
                color = if (isModified) Color(0xFFF59E0B) else Color.Transparent,
                shape = RoundedCornerShape(4.5.dp)
            )
    ) {
        // 1. Header con colore identificativo del turno (solido e vivido)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(def.color)
                .padding(vertical = if (compactMode) 0.6.dp else 1.2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = code,
                fontSize = if (compactMode) 7.2.sp else 8.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
        }

        // 2. Orario di inizio o indicazione giornata (TG)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (compactMode) 0.4.dp else 0.9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeText,
                fontSize = if (compactMode) 6.4.sp else 7.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                maxLines = 1,
                softWrap = false
            )
        }

        // 3. Estensione Trasferta: fascia semitrasparente verde smeraldo integrata sotto il turno
        if (isTrasferta) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10B981).copy(alpha = 0.22f))
                    .drawBehind {
                        drawLine(
                            color = Color(0xFF10B981).copy(alpha = 0.50f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 0.8.dp.toPx()
                        )
                    }
                    .padding(vertical = if (compactMode) 0.5.dp else 0.8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (compactMode) "✈ TR" else "✈ TRF",
                        fontSize = if (compactMode) 6.sp else 6.8.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF34D399),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 4. Estensione Straordinario: fascia semitrasparente blu cielo con ore di straordinario
        if (overtimeHours > 0f) {
            val otText = if (overtimeHours % 1f == 0f) {
                "+${overtimeHours.toInt()}h"
            } else {
                "+${String.format(Locale.US, "%.1f", overtimeHours)}h"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                    .drawBehind {
                        drawLine(
                            color = Color(0xFF38BDF8).copy(alpha = 0.50f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 0.8.dp.toPx()
                        )
                    }
                    .padding(vertical = if (compactMode) 0.5.dp else 0.8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = otText,
                    fontSize = if (compactMode) 6.2.sp else 7.2.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF38BDF8),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Scheda dettagli giorno in stile Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsPopover(
    date: LocalDate,
    onClose: () -> Unit,
    onAddShift: () -> Unit,
    onDeleteShift: (String) -> Unit
) {
    val shifts = ShiftRepository.getShiftsForDate(date)
    val planned = ShiftRepository.getPlannedRotationShift(date)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(26.dp), spotColor = ObsidianPrimary.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(26.dp),
        color = ObsidianSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Drag Handle centrale
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ObsidianBorder)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Header del giorno
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN)} ${date.year}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = ObsidianPrimary
                    )
                }
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = ObsidianSurfaceHigh,
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Chiudi",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Indicatore Turno Originario se modificato rispetto al ciclo di rotazione
            val modifiedShift = shifts.firstOrNull { it.isModifiedFromRotation && it.originalShiftDefId != null }
            val origRotationDef = modifiedShift?.originalShiftDefId?.let { ShiftRepository.getShiftDefinition(it) }

            if (origRotationDef != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Turno originario ciclo: ${origRotationDef.name} (${origRotationDef.timeRangeFormatted})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFCD34D)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lista Turni Attivi
            if (shifts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessun turno assegnato per questo giorno",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                shifts.forEach { assigned ->
                    val def = ShiftRepository.getShiftDefinition(assigned.shiftDefId)
                    if (def != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(def.color),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            getCompactShiftCode(def.id, def.shortCode),
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(def.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                                        Text(def.timeRangeFormatted, fontSize = 12.sp, color = TextSecondary)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ObsidianSurfaceHigh
                                    ) {
                                        Text(
                                            text = def.durationFormatted,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ObsidianPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        onClick = { onDeleteShift(assigned.id) },
                                        shape = CircleShape,
                                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Rimuovi",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottoni inferiori Thumb Zone
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onAddShift,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, null, tint = ObsidianBackground, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Assegna Turno", fontWeight = FontWeight.ExtraBold, color = ObsidianBackground, fontSize = 14.sp)
                    }
                }

                Surface(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Event, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Evento / Note", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * Barra orizzontale inferiore con supporto a Turni singoli e Rotazioni cicliche
 */
/**
 * Barra orizzontale inferiore con 4 modalità:
 * 1. Sostituisci (1-tap cambia turno al giorno)
 * 2. + Turno (Aggiunge un altro turno in parallelo)
 * 3. Intervallo 📅 (Assegna turni a intervallo di giorni inizio-fine)
 * 4. Rotazione 🔄 (Applica ciclo 28gg)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaintShiftPaletteBar(
    editMode: CalendarEditMode,
    onSelectEditMode: (CalendarEditMode) -> Unit,
    selectedShiftId: String,
    onSelectShift: (String) -> Unit,
    selectedRotationId: String,
    onSelectRotation: (String) -> Unit,
    rangeStartDate: LocalDate? = null,
    onCancelRange: () -> Unit = {},
    rotationStartDate: LocalDate? = null,
    onCancelRotation: () -> Unit = {},
    onAddNewShift: () -> Unit,
    onAddNewRotation: () -> Unit,
    onUndo: () -> Unit,
    onClose: () -> Unit
) {
    FluidGlassContainer(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .widthIn(min = 340.dp, max = 540.dp),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 22.dp,
        highlightColor = ObsidianPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            // RIGA 1: HEADER COMPATTO E NITIDO (Titolo a sinistra, Tasti Undo e Fatto a destra)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = ObsidianPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "MODALITÀ MODIFICA",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )
                }

                // Azioni rapide fisse a destra (Undo e Fatto con bouncy spring)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ObsidianSurfaceHigh.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(32.dp)
                            .bouncyClickable(onClick = onUndo)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Undo,
                                contentDescription = "Annulla",
                                tint = if (ShiftRepository.canUndo()) ObsidianPrimary else TextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceHigh.copy(alpha = 0.85f),
                        modifier = Modifier
                            .height(32.dp)
                            .bouncyClickable(onClick = onClose)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Fatto",
                                tint = ObsidianPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Fatto",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // BANNER GUIDATO INTERATTIVO SELEZIONE INIZIO PERIODO
            if (editMode == CalendarEditMode.RANGE && rangeStartDate != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedShiftId == "eraser") Color(0xFFEF4444).copy(alpha = 0.18f) else ObsidianPrimary.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, if (selectedShiftId == "eraser") Color(0xFFEF4444) else ObsidianPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = if (selectedShiftId == "eraser") Color(0xFFEF4444) else ObsidianPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Column {
                                Text(
                                    text = "INIZIO: ${rangeStartDate.dayOfMonth} ${rangeStartDate.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase()}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = if (selectedShiftId == "eraser") "Tocca il giorno finale per CANCELLARE" else "Tocca il giorno finale per applicare",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianSurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle),
                            modifier = Modifier.bouncyClickable(onClick = onCancelRange)
                        ) {
                            Text(
                                text = "Annulla",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // BANNER GUIDATO INTERATTIVO SELEZIONE INIZIO ROTAZIONE
            if (editMode == CalendarEditMode.ROTATION && rotationStartDate != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianPrimary.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, ObsidianPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Autorenew,
                                contentDescription = null,
                                tint = ObsidianPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Column {
                                Text(
                                    text = "INIZIO: ${rotationStartDate.dayOfMonth} ${rotationStartDate.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase()}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Tocca il giorno finale del ciclo",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianSurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle),
                            modifier = Modifier.bouncyClickable(onClick = onCancelRotation)
                        ) {
                            Text(
                                text = "Annulla",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // RIGA 2: SELETTORE A 4 MODALITÀ - AMPIO, CHIARO E VISIBILE SENZA TRONCAMENTI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarEditMode.values().forEach { mode ->
                    val isCurrentMode = editMode == mode
                    val shortLabel = when (mode) {
                        CalendarEditMode.REPLACE -> "Cambia"
                        CalendarEditMode.ADD -> "Più Turni"
                        CalendarEditMode.RANGE -> "Periodo"
                        CalendarEditMode.ROTATION -> "Rotazione"
                    }
                    Surface(
                        onClick = { onSelectEditMode(mode) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrentMode) ObsidianPrimary else ObsidianSurfaceHigh.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurrentMode) Color.White else ObsidianBorderSubtle
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(35.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = null,
                                tint = if (isCurrentMode) ObsidianBackground else TextPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = shortLabel,
                                fontSize = 10.sp,
                                fontWeight = if (isCurrentMode) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isCurrentMode) ObsidianBackground else TextPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // RIGA 3: ELEMENTI SELEZIONABILI (Turni o Rotazioni) - Scorrimento a tutta larghezza e cerchi ampi
            if (editMode != CalendarEditMode.ROTATION) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(12.dp))

                    // Tasto + Nuovo Turno
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .bouncyClickable { onAddNewShift() }
                            .padding(horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ObsidianSurfaceHigh)
                                .border(1.2.dp, ObsidianBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Nuovo", tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text("Nuovo", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                    }

                    // Tasto Gomma (Cancella / Svuota Giorno)
                    val isEraserSelected = selectedShiftId == "eraser"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .bouncyClickable { onSelectShift("eraser") }
                            .padding(horizontal = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isEraserSelected) Color(0xFFEF4444) else ObsidianSurfaceHigh)
                                .border(
                                    width = if (isEraserSelected) 2.5.dp else 1.2.dp,
                                    color = if (isEraserSelected) Color.White else ObsidianBorder,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixNormal,
                                contentDescription = "Gomma",
                                tint = if (isEraserSelected) Color.White else Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Gomma",
                            fontSize = 10.5.sp,
                            fontWeight = if (isEraserSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isEraserSelected) Color(0xFFEF4444) else TextSecondary
                        )
                    }

                    // Lista di tutti i modelli turno
                    ShiftRepository.shiftDefinitions.forEach { def ->
                        val isSelected = selectedShiftId == def.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .bouncyClickable { onSelectShift(def.id) }
                                .padding(horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(def.color)
                                    .border(
                                        width = if (isSelected) 2.8.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    getCompactShiftCode(def.id, def.shortCode),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = def.name,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) ObsidianPrimary else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                }
            } else {
                // Modalità Rotazioni
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(12.dp))

                    ShiftRepository.getRotationsSortedByDefault().forEach { rot ->
                        val isSelected = selectedRotationId == rot.id
                        val isDefault = rot.id == ShiftRepository.defaultRotationId
                        val previewColors = rot.daySequence.take(9).mapNotNull { ShiftRepository.getShiftDefinition(it)?.color }

                        Surface(
                            onClick = { onSelectRotation(rot.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) ObsidianSurfaceHighest else ObsidianSurfaceHigh,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else if (isDefault) 1.5.dp else 1.dp,
                                color = if (isSelected) ObsidianPrimary else if (isDefault) Color(0xFFF59E0B) else ObsidianBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.5.dp)) {
                                    for (r in 0 until 3) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                                            for (c in 0 until 3) {
                                                val idx = r * 3 + c
                                                val dotColor = previewColors.getOrNull(idx) ?: TextMuted
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.5.dp)
                                                        .clip(CircleShape)
                                                        .background(dotColor)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(9.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = rot.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        if (isDefault) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = "Predefinito",
                                                tint = Color(0xFFFBBF24),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${rot.totalDays} giorni",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Tasto + Nuova rotazione
                    Surface(
                        onClick = onAddNewRotation,
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceHigh,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Nuova",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal elegante per selezionare quale turno aggiungere al giorno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShiftModal(
    date: LocalDate,
    initialSecondaryMode: Boolean = false,
    onDismiss: () -> Unit,
    onSelect: (String, Boolean) -> Unit
) {
    var isSecondaryMode by remember(initialSecondaryMode) { mutableStateOf(initialSecondaryMode) }
    val existingShifts = ShiftRepository.getShiftsForDate(date)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isSecondaryMode) "+ Turno al ${date.dayOfMonth}/${date.monthValue}" else "Cambia Turno al ${date.dayOfMonth}/${date.monthValue}",
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
                if (existingShifts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceContainer)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { isSecondaryMode = false },
                            shape = RoundedCornerShape(9.dp),
                            color = if (!isSecondaryMode) ObsidianPrimary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Sostituisci",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isSecondaryMode) ObsidianBackground else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                        Surface(
                            onClick = { isSecondaryMode = true },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSecondaryMode) ObsidianPrimary else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "+ Aggiungi Turno",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSecondaryMode) ObsidianBackground else TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShiftRepository.shiftDefinitions.forEach { def ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(def.id, isSecondaryMode) },
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(def.color),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    getCompactShiftCode(def.id, def.shortCode),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(def.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(def.timeRangeFormatted, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla", color = TextSecondary)
            }
        },
        containerColor = ObsidianSurface,
        shape = RoundedCornerShape(20.dp)
    )
}

