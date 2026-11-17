@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val panelEasing = CubicBezierEasing(.3f, .08f, .3f, 1f)
private val panelDanger get() = ColorNegative
private val panelOvertime get() = Color(currentTurniPalette.overtime.ink)
private val panelOvertimeSurface get() = Color(currentTurniPalette.overtime.fill)
private val panelOvertimeInk get() = panelOvertime
private val panelOncallSurface get() = Color(currentTurniPalette.onCall.fill)
private val panelOncallInk get() = ColorReperibilita
private val panelTravelSurface get() = Color(currentTurniPalette.travel.fill)
private val panelTravelInk get() = ColorTrasferta
private val panelInfoSurface get() = Color(currentTurniPalette.secondaryContainer)
private val panelInfoInk get() = Color(currentTurniPalette.onSecondaryContainer)
private fun panelDate(date: LocalDate, weekday: Boolean = false): String =
    date.format(DateTimeFormatter.ofPattern(if (weekday) "EEEE d MMMM" else "d MMMM", Locale.ITALIAN))
        .replaceFirstChar { it.titlecase(Locale.ITALIAN) }

private fun panelGlyph(icon: ImageVector): String = when (icon.name.substringAfterLast('.')) {
    "ArrowBack" -> "arrow-left"
    "ChevronLeft" -> "chevron-left"
    "ChevronRight" -> "chevron-right"
    "Notifications", "NotificationsActive" -> "bell"
    "Flight", "AirplanemodeActive" -> "plane"
    "Settings", "Tune" -> "settings"
    "SwapHoriz" -> "swap"
    "DateRange", "CalendarMonth" -> "calendar"
    "Layers", "AddCircleOutline" -> "layers"
    "Cached", "Loop", "Repeat" -> "rotation"
    "Delete", "DeleteOutline" -> "trash"
    "Check" -> "check"
    "Close" -> "x"
    "Add" -> "plus"
    "Undo" -> "undo"
    "Notes", "EditNote", "NoteAdd" -> "notebook"
    "Edit" -> "pencil"
    "Eraser", "AutoFixNormal" -> "eraser"
    else -> "clock"
}

/** Targets stay fixed; only the site's outline artwork receives motion. */
/** Remove dock clearance progressively as the keyboard takes that space. */
internal fun panelKeyboardClearance(rest:Float,ime:Float,navigation:Float):Float =
    (rest-(ime-navigation).coerceAtLeast(0f)).coerceAtLeast(minOf(rest,8f))

@Composable
internal fun keyboardAwarePanelClearance(rest:Dp):Dp {
    val density=LocalDensity.current
    val ime=WindowInsets.ime.getBottom(density)
    val navigation=WindowInsets.navigationBars.getBottom(density)
    return with(density){panelKeyboardClearance(rest.value,ime.toDp().value,navigation.toDp().value).dp}
}

@Composable
internal fun PanelIcon(name: String, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, width: Dp = 30.dp, height: Dp = 34.dp,
    glyphSize: Dp = 16.dp, tint: Color = if(name=="x")ColorNegative else TextPrimary, background: Color = Color.Transparent,
    activeDot: Boolean = false, dotTint:Color = ObsidianPrimary) {
    val phase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var replay by remember { mutableStateOf<Job?>(null) }
    Box(modifier.size(width, height).clip(RoundedCornerShape(10.dp)).background(background)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClickLabel = label) {
            replay?.cancel()
            replay = scope.launch { phase.snapTo(0f); phase.animateTo(1f, tween(620, easing = panelEasing)); phase.snapTo(0f) }
            onClick()
        }, contentAlignment = Alignment.Center) {
        SiteIcon(name, label, Modifier.size(glyphSize), tint = tint, progress = phase.value)
        if (activeDot) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(4.dp)
            .clip(RoundedCornerShape(2.dp)).background(dotTint))
    }
}

@Composable
internal fun CalendarAction(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, tint: Color = TextPrimary, background: Color = Color.Transparent) {
    PanelIcon(panelGlyph(icon), label, onClick, modifier, 40.dp, 40.dp, 22.dp, tint, background)
}

@Composable
private fun PanelButton(label: String, glyph: String? = null, primary: Boolean = false,
    modifier: Modifier = Modifier, height: Dp = 36.dp, onClick: () -> Unit) {
    val phase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var replay by remember { mutableStateOf<Job?>(null) }
    val ink = if (primary) catalogInk(ObsidianPrimary) else TextPrimary
    Row(modifier.heightIn(min = height).clip(RoundedCornerShape(12.dp))
        .background(if (primary) ObsidianPrimary else ObsidianSurfaceContainer)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
            replay?.cancel(); replay = scope.launch { phase.snapTo(0f); phase.animateTo(1f, tween(480, easing = panelEasing)); phase.snapTo(0f) }
            onClick()
        }.padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (glyph != null) { SiteIcon(glyph, null, Modifier.size(16.dp), ink, phase.value); Spacer(Modifier.width(5.dp)) }
        Text(label, color = ink, fontSize = 11.sp, lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun NativeEditToolbar(mode: CalendarEditMode, brush: String, rotationId: String, tool:String?, onTool:(String)->Unit,
    onMode: (CalendarEditMode) -> Unit, onBrush: (String) -> Unit, onRotation: (String) -> Unit,
    onUndo: () -> Unit, onClose: () -> Unit, catalogOverride:List<ShiftDefinition>?=null, rotationsOverride:List<ShiftRotation>?=null, title:String="Modifica turni") {
    val effectiveRotations=rotationsOverride?:ShiftRepository.getRotationsSortedByDefault()
    val rotationDefinitions=(catalogOverride?:ShiftRepository.shiftDefinitions).associateBy{it.id}
    Column(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp))
        .background(ObsidianSurface)
        .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            PanelIcon("undo", "Annulla modifica", onUndo, width = 36.dp, height = 36.dp,
                glyphSize = 18.dp, background = ObsidianSurfaceContainer)
            Spacer(Modifier.width(4.dp))
            listOf("eraser" to "Gomma", "bell" to "Reperibilità").forEach{(glyph,label)->
                val selected=tool==glyph
                val outline by animateColorAsState(if(selected)BrandInk else Color.Transparent,tween(200))
                PanelIcon(glyph,label,{onTool(glyph)},
                    modifier=Modifier.border(2.dp,outline,RoundedCornerShape(10.dp)).semantics{this.selected=selected},
                    width=36.dp,height=36.dp,glyphSize=19.dp,
                    tint=if(selected)BrandInk else TextPrimary,
                    background=if(selected)lerp(ObsidianSurfaceContainer,ObsidianPrimary,.18f)else ObsidianSurfaceContainer)
                Spacer(Modifier.width(4.dp))
            }
            PanelIcon("x", "Chiudi modifica", onClose, width = 36.dp, height = 36.dp, glyphSize = 18.dp,
                tint = ColorNegative,
                background = Color(currentTurniPalette.danger.fill))
        }
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(13.dp)).background(ObsidianSurfaceContainer)) {
            val modes = CalendarEditMode.values().filterNot { it == CalendarEditMode.RANGE }
            val width = maxWidth / modes.size
            val selectedX by animateDpAsState(width * modes.indexOf(mode).coerceAtLeast(0), spring(.95f, 380f))
            Box(Modifier.offset(x = selectedX).width(width).fillMaxHeight().padding(3.dp)
                .clip(RoundedCornerShape(10.dp)).background(ObsidianPrimary))
            Row(Modifier.fillMaxSize()) {
                modes.forEach { item ->
                    val glyph = when (item) { CalendarEditMode.REPLACE -> "swap"; CalendarEditMode.RANGE -> "calendar"; CalendarEditMode.ADD -> "layers"; CalendarEditMode.ROTATION -> "rotation" }
                    val phase = remember { Animatable(0f) }; val scope = rememberCoroutineScope()
                    var replay by remember { mutableStateOf<Job?>(null) }
                    val ink = if (mode == item) catalogInk(ObsidianPrimary) else TextPrimary
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                        replay?.cancel(); replay = scope.launch { phase.snapTo(0f); phase.animateTo(1f, tween(620, easing = panelEasing)); phase.snapTo(0f) }; onMode(item)
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        SiteIcon(glyph, item.label, Modifier.size(14.dp), ink, phase.value)
                        Spacer(Modifier.height(2.dp))
                        Text(item.label, color = ink, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (mode == CalendarEditMode.ROTATION) {
                effectiveRotations.forEach { rotation ->
                    val selected = rotation.id == rotationId
                    val outline by animateColorAsState(if(selected)BrandInk else ObsidianBorderSubtle,tween(220))
                    Column(Modifier.width(144.dp).height(48.dp).clip(RoundedCornerShape(13.dp))
                        .background(ObsidianSurfaceContainer)
                        .border(if(selected)2.dp else 1.dp,outline,RoundedCornerShape(13.dp))
                        .semantics{this.selected=selected}
                        .bouncyClickable { onRotation(rotation.id) }.padding(horizontal = 9.dp, vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rotation.name, color = TextPrimary,
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (ShiftRepository.defaultRotationId == rotation.id) Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(currentTurniPalette.warning.fill)),contentAlignment=Alignment.Center){
                                SiteIcon("star", "Rotazione predefinita", Modifier.size(12.dp), Color(currentTurniPalette.warning.ink))
                            }
                            if (selected) {Spacer(Modifier.width(3.dp));SiteIcon("check", null, Modifier.size(12.dp), BrandInk)}
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.clip(RoundedCornerShape(4.dp)).background(ObsidianSurfaceHigh).padding(3.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)){
                                rotation.daySequence.take(7).forEach { id -> Box(Modifier.width(8.dp).height(5.dp).clip(RoundedCornerShape(2.dp)).background(rotationDefinitions[id]?.color ?: ObsidianBorder)) }
                            }
                            Text("${rotation.daySequence.size} g", color = TextSecondary,
                                fontSize = 9.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                if (effectiveRotations.isEmpty()) Text("Crea una rotazione in Turni", color = TextSecondary, fontSize = 12.sp)
            } else {
                (catalogOverride?:ShiftRepository.shiftDefinitions.filter { it.workProfileId == ShiftRepository.activeWorkProfileId }).forEach { def ->
                    val selected = tool==null && brush == def.id
                    val phase=remember{Animatable(0f)};val scope=rememberCoroutineScope()
                    var replay by remember{mutableStateOf<Job?>(null)}
                    val pulse=kotlin.math.sin(phase.value*Math.PI).toFloat()
                    Box(Modifier.size(48.dp, 44.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (selected) lerp(ObsidianSurfaceContainer, def.color, .2f) else ObsidianSurfaceContainer)
                        .then(if (selected) Modifier.border(2.dp, def.color, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { replay?.cancel();replay=scope.launch{phase.snapTo(0f);phase.animateTo(1f,tween(660));phase.snapTo(0f)};onBrush(def.id) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(38.dp, 32.dp).clip(RoundedCornerShape(9.dp)).background(def.color), contentAlignment = Alignment.Center) {
                            Text(def.shortCode.ifBlank { getCompactShiftCode(def.id, def.shortCode) }, color = catalogInk(def.color),
                                fontSize = (12f+1.4f*pulse).sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NativeDayCard(date: LocalDate, onClose: () -> Unit, onSelectShift: (Boolean) -> Unit,
    onFeedback: (ActionFeedback) -> Unit, onDateChange: (LocalDate) -> Unit = {},
    onDetailsActive: (Boolean) -> Unit = {}) {
    val shifts = ShiftRepository.getShiftsForDate(date).filter { ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId == ShiftRepository.activeWorkProfileId }
    var detailShift by remember(date) { mutableStateOf<AssignedShift?>(null) }
    var dayNotes by remember(date) { mutableStateOf(false) }
    val holidays = ShiftRepository.holidaysForDate(date)
    val oncall = ShiftRepository.isOnCall(date)
    val note = ShiftRepository.getDayExtraInfo(date).notes
    val dayTags = ShiftRepository.dayTags.filter { it.id in ShiftRepository.getDayExtraInfo(date).tagIds }
    val panelDensity=LocalDensity.current
    val keyboardHeight=WindowInsets.ime.getBottom(panelDensity)/panelDensity.density
    val maxPanelHeight=(LocalConfiguration.current.screenHeightDp-146-keyboardHeight).coerceAtLeast(160f).dp
    val subPanel = detailShift != null
    LaunchedEffect(subPanel) { onDetailsActive(subPanel) }
    DisposableEffect(Unit) { onDispose { onDetailsActive(false) } }
    AnimatedVisibility(!subPanel, enter = fadeIn(tween(260)) + slideInVertically(tween(360, easing = panelEasing)) { it / 14 }, exit = fadeOut(tween(160))) {
        Surface(shape = RoundedCornerShape(23.dp), color = ObsidianSurface, border = BorderStroke(1.dp, ObsidianBorder), shadowElevation = 12.dp) {
            AnimatedContent(dayNotes,contentAlignment=Alignment.BottomCenter,transitionSpec={
                (fadeIn(tween(180,delayMillis=70)) togetherWith fadeOut(tween(100))) using
                    SizeTransform(clip=true){_,_->tween(360,easing=panelEasing)}
            }) { notesPage ->
            Column(Modifier.fillMaxWidth().heightIn(max=maxPanelHeight).padding(10.dp)) {
                if(notesPage) {
                    DayAnnotationsContent(date,maxPanelHeight-20.dp,onBack={dayNotes=false},onClose=onClose,onFeedback=onFeedback)
                }else {
                Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                    PanelIcon("chevron-left", "Giorno precedente", { onDateChange(date.minusDays(1)) })
                    Text(panelDate(date, true), color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    PanelIcon("chevron-right", "Giorno successivo", { onDateChange(date.plusDays(1)) })
                    PanelIcon("x", "Chiudi dettaglio", onClose)
                }
                Column(Modifier.fillMaxWidth().heightIn(max=(maxPanelHeight-150.dp).coerceAtLeast(56.dp))
                    .animateContentSize(tween(360,easing=panelEasing))) {
                key(date) {
                Text("${shifts.size} ${if (shifts.size == 1) "turno" else "turni"}", color = TextSecondary,
                    fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(start = 2.dp, bottom = 5.dp))
                if(holidays.isNotEmpty()) {
                    val tone=Color(holidayDayColor(TurniThemeState.dark))
                    Row(Modifier.fillMaxWidth().padding(bottom=7.dp).clip(RoundedCornerShape(11.dp))
                        .background(tone.copy(alpha=if(TurniThemeState.dark).09f else .06f)).padding(horizontal=9.dp,vertical=7.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        SiteIcon("calendar",null,Modifier.size(17.dp),tint=tone)
                        Column(Modifier.weight(1f)) {
                            Text("Festività",color=TextSecondary,fontSize=9.sp,lineHeight=12.sp)
                            Text(holidays.joinToString(" · "){it.name},color=TextPrimary,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium)
                        }
                    }
                }
                val restAlerts=ShiftRepository.minimumRestAlerts.filter{warning->warning.date==date&&shifts.any{it.id==warning.nextId}}
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if(restAlerts.isNotEmpty()) {
                    val warning=currentTurniPalette.warning
                    Row(Modifier.fillMaxWidth().padding(bottom=8.dp).clip(RoundedCornerShape(12.dp))
                        .background(Color(warning.fill)).border(1.dp,Color(warning.ink).copy(alpha=.28f),RoundedCornerShape(12.dp))
                        .padding(10.dp),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        SiteIcon("alert-triangle",null,Modifier.padding(top=1.dp).size(18.dp),Color(warning.ink))
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text("Riposo minimo non rispettato",color=Color(warning.ink),fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold)
                            restAlerts.forEach{Text(it.description,color=Color(warning.ink),fontSize=11.sp,lineHeight=15.sp)}
                        }
                    }
                }
                    if (shifts.isEmpty()) Text("Nessun turno assegnato", color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp, vertical = 12.dp))
                    shifts.forEach { shift -> key(shift.id) {
                        NativeDayShiftRow(date, shift, onDetails = { detailShift = shift }, onFeedback = onFeedback)
                    } }
                    NativeNotePreview(note, "Nota della giornata")
                }
                }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if(dayTags.isEmpty())Text("Giornata", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f).padding(start = 3.dp))
                    else FlowRow(Modifier.weight(1f).heightIn(max=72.dp).verticalScroll(rememberScrollState()).bouncyClickable { dayNotes=true },horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        dayTags.forEach { DayTagChip(it,selected=true,compact=true) }
                    }
                    PanelIcon("bell", "Reperibilità della giornata", {
                        ShiftRepository.setDayOnCallManual(date, !oncall)
                        onFeedback(ActionFeedback(if (oncall) "Reperibilità rimossa" else "Reperibilità attivata", oncall))
                    }, width = 44.dp, height = 36.dp, glyphSize = 18.dp,
                        tint = if (oncall) panelOncallInk else TextPrimary,
                        background = if (oncall) panelOncallSurface else ObsidianSurfaceContainer)
                    PanelIcon("notebook", "Note ed etichette della giornata", { dayNotes = true }, width = 44.dp, height = 36.dp,
                        glyphSize = 18.dp, tint = if (note.isNotBlank() || dayTags.isNotEmpty()) BrandInk else TextPrimary,
                        background = ObsidianSurfaceContainer, activeDot = note.isNotBlank() || dayTags.isNotEmpty())
                }
                Row(Modifier.fillMaxWidth().padding(top = 13.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (shifts.isNotEmpty()) PanelButton(if (shifts.size > 1) "Cambia turni" else "Cambia turno", "swap",
                        modifier = Modifier.weight(1f), onClick = { onSelectShift(false) })
                    PanelButton("Aggiungi turno", "circle-plus", true, Modifier.weight(1f)) { onSelectShift(true) }
                }
                }
            }
            }
        }
    }
    detailShift?.let { NativeShiftDetails(date, it, onClose = { detailShift = null }, onFeedback = onFeedback) }
}

@Composable
private fun NativeDayShiftRow(date: LocalDate, shift: AssignedShift, onDetails: () -> Unit, onFeedback: (ActionFeedback) -> Unit) {
    val def = ShiftRepository.getShiftDefinition(shift.shiftDefId) ?: return
    val narrow = LocalConfiguration.current.screenWidthDp <= 310
    val hasOvertime = shift.overtimeHours > 0
    val hasNote = shift.notes.isNotBlank()
    val gearColor = if (hasOvertime) panelOvertimeInk else TextPrimary
    val gearBackground = if (hasOvertime) panelOvertimeSurface else ObsidianSurface
    val tools: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            PanelIcon("settings", "Informazioni turno ${def.name}", onDetails, tint = gearColor, background = gearBackground, activeDot = hasNote, dotTint=ObsidianPrimary)
            PanelIcon("plane", "Trasferta turno ${def.name}", {
                ShiftRepository.toggleShiftTrasferta(date, shift.id)
                onFeedback(ActionFeedback(if (shift.isTrasferta) "Trasferta rimossa" else "Trasferta aggiunta", shift.isTrasferta))
            }, modifier=Modifier.border(if(shift.isTrasferta)1.dp else 0.dp,if(shift.isTrasferta)panelTravelInk.copy(alpha=.6f)else Color.Transparent,RoundedCornerShape(10.dp)),
                tint = if (shift.isTrasferta) panelTravelInk else TextPrimary, background = if (shift.isTrasferta) panelTravelSurface else ObsidianSurface)
            PanelIcon("trash", "Elimina turno ${def.name}", {
                ShiftRepository.removeAssignedShift(date, shift.id); onFeedback(ActionFeedback("Turno eliminato", true))
            }, tint = panelDanger, background = ObsidianSurface)
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp))
        .background(ObsidianSurfaceContainer).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(def.color), contentAlignment = Alignment.Center) {
                    Text(def.shortCode.ifBlank { getCompactShiftCode(def.id, def.shortCode) }, color = catalogInk(def.color),
                        fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
                }
                Column(Modifier.weight(1f)) {
                    Text(def.name, color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (def.isAllDay) "Tutto il giorno" else "${shift.customStartTime ?: def.startTime} – ${shift.customEndTime ?: def.endTime}",
                        color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 1)
                }
            }
            if (!narrow) tools()
        }
        if (narrow) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            NativeShiftExtraChips(shift, Modifier.weight(1f), vertical = true)
            tools()
        } else NativeShiftExtraChips(shift, Modifier.fillMaxWidth())
    }
}

@Composable
private fun NativeShiftExtraChips(shift: AssignedShift, modifier: Modifier = Modifier, vertical: Boolean = false) {
    Column(modifier) {
        val hasExtra = shift.overtimeHours > 0 || shift.actualEndTime != null
        if(hasExtra) {
            val contents: @Composable () -> Unit = {
                if (shift.overtimeHours > 0) DetailPill("+${formatOvertime(shift.overtimeHours)}", panelOvertimeInk, panelOvertimeSurface)
                shift.actualEndTime?.let { DetailPill("Stacco $it", BrandInk, ObsidianSurface) }
            }
            if (vertical) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { contents() }
            else Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { contents() }
        }
        NativeNotePreview(shift.notes, "Nota del turno")
    }
}

@Composable
private fun NativeNotePreview(note:String,label:String) {
    if(note.isNotBlank()) {
        Row(Modifier.fillMaxWidth().padding(top=7.dp).clip(RoundedCornerShape(10.dp))
            .background(ObsidianPrimarySubtle).padding(9.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            SiteIcon("message",label,Modifier.size(16.dp),tint=BrandInk)
            Text(note,color=TextPrimary,fontSize=12.sp,lineHeight=17.sp,modifier=Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailPill(text: String, color: Color, background: Color) {
    Text(text, color = color, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 6.dp, vertical = 4.dp))
}

/** One floating, opaque surface; the existing day surface is hidden while this is open. */
@Composable
internal fun NativeBottomDialog(onClose: () -> Unit, bottomInset: Dp = 86.dp, dimAmount: Float = 0f,
    scrollContent: Boolean = true, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var visible by remember { mutableStateOf(false) }; var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close = { if (!closing) { closing = true; scope.launch { visible = false; delay(260); onClose() } }; Unit }
    LaunchedEffect(Unit) { visible = true }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val dark=TurniThemeState.dark
        val chrome=ObsidianBackground.toArgb()
        DisposableEffect(view,dark,chrome,dimAmount) {
            // Apply after attachment: showing a dialog can reset its initial system-bar flags.
            val applyChrome=Runnable {
                val window=(view as? DialogWindowProvider)?.window
                    ?: (view.parent as? DialogWindowProvider)?.window
                window?.let {
                    it.setDimAmount(dimAmount)
                    it.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR)
                    WindowCompat.setDecorFitsSystemWindows(it,false)
                    it.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                        android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                    it.statusBarColor=chrome
                    it.navigationBarColor=chrome
                    WindowCompat.getInsetsController(it,it.decorView).apply {
                        isAppearanceLightStatusBars=!dark
                        isAppearanceLightNavigationBars=!dark
                    }
                }
            }
            view.post(applyChrome)
            onDispose { view.removeCallbacks(applyChrome) }
        }
        val clearance=keyboardAwarePanelClearance(bottomInset)
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close))
            AnimatedVisibility(visible,
                enter = slideInVertically(tween(520, easing = CubicBezierEasing(.2f, .85f, .2f, 1f))) { it / 3 } + fadeIn(tween(380)),
                exit = slideOutVertically(tween(240, easing = panelEasing)) { it / 3 } + fadeOut(tween(220)),
                modifier = Modifier.navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = clearance)) {
                Surface(Modifier.fillMaxWidth().heightIn(max = minOf(620, (LocalConfiguration.current.screenHeightDp - 100).coerceAtLeast(240)).dp),
                    shape = RoundedCornerShape(28.dp), color = ObsidianSurface, border = BorderStroke(1.dp, ObsidianBorder), shadowElevation = 12.dp) {
                    Column(Modifier.padding(14.dp).then(if(scrollContent)Modifier.verticalScroll(rememberScrollState())else Modifier)) { content(close) }
                }
            }
        }
    }
}

@Composable
private fun NativeShiftDetails(date: LocalDate, shift: AssignedShift, onClose: () -> Unit, onFeedback: (ActionFeedback) -> Unit) {
    val def = ShiftRepository.getShiftDefinition(shift.shiftDefId) ?: return
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val baseEnd = shift.customEndTime ?: def.endTime
    var end by remember(shift.id) { mutableStateOf(shift.actualEndTime ?: baseEnd) }
    var notes by remember(shift.id) { mutableStateOf(shift.notes) }
    var hours by remember(shift.id) { mutableStateOf(shift.overtimeHours) }
    var noteTab by remember(shift.id) { mutableStateOf(false) }
    val parsed = runCatching { LocalTime.parse(end) }.getOrNull()
    val base = runCatching { LocalTime.parse(baseEnd) }.getOrNull()
    val extraMinutes = if (parsed != null && base != null) (parsed.toSecondOfDay() / 60 - base.toSecondOfDay() / 60 + 1440) % 1440 else -1
    val valid = def.isAllDay || (parsed != null && extraMinutes in 0..960)
    val updateEnd: (String) -> Unit = { value -> end = value; hours = if (value.isBlank()) 0f else ShiftRepository.calculateOvertimeFromEndTime(baseEnd, value) }
    NativeBottomDialog(onClose, bottomInset = 86.dp) { close ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelIcon("arrow-left", "Torna al dettaglio giorno", close, width = 40.dp, height = 40.dp, glyphSize = 20.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    SiteIcon("settings", null, Modifier.size(12.dp), TextSecondary)
                    Text(panelDate(date), color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                }
                Text(def.name, color = TextPrimary, fontSize = 20.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 3.dp))
                Text(if (def.isAllDay) "Tutto il giorno" else "${shift.customStartTime ?: def.startTime} – $baseEnd",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(def.color), contentAlignment = Alignment.Center) {
                Text(def.shortCode, color = catalogInk(def.color), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceContainer).padding(3.dp)) {
            listOf("Straordinario" to "timer", "Note turno" to "notebook").forEachIndexed { index, (label, glyph) ->
                val selected = noteTab == (index == 1)
                val accent = if (index == 0) panelOvertimeSurface else ObsidianPrimary
                val ink = if (selected) if (index == 0) panelOvertimeInk else OnBrand else TextPrimary
                Row(Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (selected) accent else Color.Transparent).clickable { noteTab = index == 1 }
                    .padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    SiteIcon(glyph, null, Modifier.size(16.dp), ink); Spacer(Modifier.width(5.dp))
                    Text(label, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        AnimatedContent(noteTab, transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween(260, easing = panelEasing)) { 4 }) with fadeOut(tween(100))
        }) { showNotes ->
            if (showNotes) Column {
                Text("Note del turno", color = TextSecondary, fontSize = 11.sp)
                BasicTextField(notes, { notes = it.take(500) }, textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp),
                    cursorBrush = SolidColor(ObsidianPrimary), modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(130.dp)
                        .clip(RoundedCornerShape(15.dp)).background(ObsidianSurfaceContainer).border(1.dp, ObsidianBorder, RoundedCornerShape(15.dp)).padding(12.dp),
                    decorationBox = { field -> if (notes.isBlank()) Text("Scrivi una nota…", color = TextSecondary, fontSize = 14.sp); field() })
            } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ObsidianSurfaceContainer)
                .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp)).padding(10.dp)) {
                if (def.isAllDay) Text("Per calcolare lo straordinario serve un turno con orario di fine.", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                else Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Fine prevista", color = TextSecondary, fontSize = 11.sp)
                        Text(baseEnd, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.widthIn(max = 125.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Stacco effettivo", color = TextSecondary, fontSize = 11.sp)
                        BasicTextField(end, { updateEnd(it.filter { char -> char.isDigit() || char == ':' }.take(5)) }, singleLine = true,
                            textStyle = TextStyle(color = if (valid) panelOvertime else panelDanger, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), cursorBrush = SolidColor(panelOvertime),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(ObsidianSurface).padding(7.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hours > 0) "+${formatOvertime(hours)}" else "Nessuno straordinario", color = panelOvertime,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("Azzera", color = ColorNegative,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(9.dp)).clickable { end = baseEnd; hours = 0f }.padding(horizontal = 8.dp, vertical = 10.dp))
                }
                if (!def.isAllDay) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(15, 30, 45, 60, 90, 120).forEach { minutes ->
                        val label = if (minutes < 60) "+${minutes}m" else if (minutes == 90) "+1,5h" else "+${minutes / 60}h"
                        Box(Modifier.widthIn(min = 43.dp).height(44.dp).clip(RoundedCornerShape(11.dp)).background(ObsidianSurface)
                            .clickable { val current = runCatching { LocalTime.parse(end) }.getOrNull() ?: base
                                if (current != null) updateEnd(current.plusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))) }
                            .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (!valid) Text("Controlla l’orario di stacco: massimo 16 ore dopo la fine prevista", color = panelDanger,
                    fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        PanelButton("Salva", "check", true, Modifier.fillMaxWidth(), 44.dp) {
            if (valid) {
                focus.clearFocus();keyboard?.hide()
                ShiftRepository.setShiftExtraDetails(date, shift.id, if (def.isAllDay || end == baseEnd) null else end,
                    if (def.isAllDay) 0f else hours, notes)
                onFeedback(ActionFeedback("Informazioni salvate", false)); close()
            } else onFeedback(ActionFeedback("Controlla l’orario di stacco", true))
        }
    }
}

@Composable
internal fun NativeElementEraser(date: LocalDate, onClose: () -> Unit, onRemove: (String) -> Unit) {
    NativeBottomDialog(onClose) { close ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cosa vuoi rimuovere?", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(panelDate(date, true), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            PanelIcon("x", "Chiudi senza altre modifiche", close, width = 36.dp, height = 36.dp, glyphSize = 18.dp)
        }
        Text("Rimuovi i singoli elementi. Gli altri turni e le note del giorno restano.", color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
        val shifts = ShiftRepository.getShiftsForDate(date).filter { ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId == ShiftRepository.activeWorkProfileId }
        shifts.forEach { shift ->
            val def = ShiftRepository.getShiftDefinition(shift.shiftDefId)
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp).clip(RoundedCornerShape(15.dp)).background(ObsidianSurfaceContainer).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (def != null) Box(Modifier.size(38.dp, 35.dp).clip(RoundedCornerShape(9.dp)).background(def.color), contentAlignment = Alignment.Center) {
                    Text(def.shortCode, color = catalogInk(def.color), fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 2)
                }
                Column(Modifier.weight(1f)) {
                    Text(def?.name ?: "Turno", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Turno ${shifts.indexOf(shift) + 1} · ${def?.timeRangeFormatted ?: ""}", color = TextSecondary, fontSize = 10.sp)
                }
                PanelIcon("trash", "Rimuovi ${def?.name ?: "turno"}", {
                    onRemove(shift.id)
                    val remaining = ShiftRepository.getShiftsForDate(date).filter { ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId == ShiftRepository.activeWorkProfileId }
                    if (remaining.isEmpty()) close()
                }, width = 44.dp, height = 44.dp, glyphSize = 18.dp, tint = panelDanger, background = ObsidianSurface)
            }
        }

    }
}

@Composable
internal fun NativeShiftChooser(onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    NativeBottomDialog(onDismiss) { close ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scegli turno", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            PanelIcon("x", "Chiudi selezione", close, width = 36.dp, height = 36.dp, glyphSize = 18.dp)
        }
        ShiftRepository.shiftDefinitions.filter { it.workProfileId == ShiftRepository.activeWorkProfileId }.forEach { def ->
            Row(Modifier.fillMaxWidth().padding(top = 7.dp).clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceContainer)
                .clickable { onChoose(def.id); close() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(def.color), contentAlignment = Alignment.Center) {
                    Text(def.shortCode, color = catalogInk(def.color), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(def.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(def.timeRangeFormatted, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
