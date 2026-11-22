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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
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
import kotlin.math.roundToInt

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
internal fun Modifier.keyboardAwarePanelClearance(rest: Dp): Modifier {
    val ime = WindowInsets.ime
    val navigation = WindowInsets.navigationBars
    val density = LocalDensity.current
    return this.padding(remember(rest, ime, navigation, density) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = 0.dp
            override fun calculateRightPadding(layoutDirection: androidx.compose.ui.unit.LayoutDirection) = 0.dp
            override fun calculateTopPadding() = 0.dp
            override fun calculateBottomPadding() = with(density) {
                panelKeyboardClearance(rest.value, ime.getBottom(this).toDp().value, navigation.getBottom(this).toDp().value).dp
            }
        }
    })
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
        SiteIconMotion(name, label, Modifier.size(glyphSize), tint = tint) { phase.value }
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
        if (glyph != null) { SiteIconMotion(glyph, null, Modifier.size(16.dp), ink) { phase.value }; Spacer(Modifier.width(5.dp)) }
        Text(label, color = ink, fontSize = 11.sp, lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun NativeEditToolbar(mode: CalendarEditMode, brush: String, rotationId: String, tool:String?, onTool:(String)->Unit,
    onMode: (CalendarEditMode) -> Unit, onBrush: (String) -> Unit, onRotation: (String) -> Unit,
    onUndo: () -> Unit, onClose: () -> Unit, catalogOverride:List<ShiftDefinition>?=null, rotationsOverride:List<ShiftRotation>?=null, title:String="Modifica turni",
    highlightTarget: PracticeHighlightTarget? = null, highlightPulse: Float = 0f) {
    val effectiveRotations=rotationsOverride?:ShiftRepository.getRotationsSortedByDefault()
    val rotationDefinitions=(catalogOverride?:ShiftRepository.shiftDefinitions).associateBy{it.id}
    val dark = TurniThemeState.dark
    val pulseAmt = highlightPulse.coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(24.dp))
        .background(ObsidianSurface)
        .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            val undoGuided = highlightTarget?.tool == "undo"
            val undoOutline = if (undoGuided) BrandInk.copy(alpha = 0.45f + 0.55f * pulseAmt) else Color.Transparent
            val undoFill = if (undoGuided) lerp(ObsidianSurfaceContainer, ObsidianPrimary, 0.14f + 0.22f * pulseAmt) else ObsidianSurfaceContainer
            PanelIcon("undo", "Annulla modifica", onUndo,
                modifier = Modifier
                    .graphicsLayer {
                        val s = if (undoGuided) 1f + 0.08f * pulseAmt else 1f
                        scaleX = s; scaleY = s
                    }
                    .border(2.dp, undoOutline, RoundedCornerShape(10.dp)),
                width = 36.dp, height = 36.dp,
                glyphSize = 18.dp, tint = if (undoGuided) BrandInk else TextPrimary, background = undoFill)
            Spacer(Modifier.width(4.dp))
            listOf("eraser" to "Gomma", "bell" to "Reperibilità").forEach{(glyph,label)->
                val selected=tool==glyph
                val guided = highlightTarget?.tool == glyph
                val baseOutline by animateColorAsState(if(selected)BrandInk else Color.Transparent,tween(200))
                val effectiveOutline = if (guided) BrandInk.copy(alpha = 0.45f + 0.55f * pulseAmt) else baseOutline
                val effectiveBg = when {
                    guided -> lerp(ObsidianSurfaceContainer, ObsidianPrimary, 0.14f + 0.22f * pulseAmt)
                    selected -> lerp(ObsidianSurfaceContainer, ObsidianPrimary, .18f)
                    else -> ObsidianSurfaceContainer
                }
                PanelIcon(glyph,label,{onTool(glyph)},
                    modifier=Modifier
                        .graphicsLayer {
                            val s = if (guided) 1f + 0.08f * pulseAmt else 1f
                            scaleX = s; scaleY = s
                        }
                        .border(2.dp,effectiveOutline,RoundedCornerShape(10.dp))
                        .semantics{this.selected=selected},
                    width=36.dp,height=36.dp,glyphSize=19.dp,
                    tint=if(selected || guided)BrandInk else TextPrimary,
                    background=effectiveBg)
                Spacer(Modifier.width(4.dp))
            }
            val closeGuided = highlightTarget?.tool == "x"
            val closeOutline = if (closeGuided) ColorNegative.copy(alpha = 0.45f + 0.55f * pulseAmt) else Color.Transparent
            PanelIcon("x", "Chiudi modifica", onClose,
                modifier = Modifier
                    .graphicsLayer {
                        val s = if (closeGuided) 1f + 0.08f * pulseAmt else 1f
                        scaleX = s; scaleY = s
                    }
                    .border(2.dp, closeOutline, RoundedCornerShape(10.dp)),
                width = 36.dp, height = 36.dp, glyphSize = 18.dp,
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
                    val modeGuided = highlightTarget?.mode == item && mode != item
                    val ink = when {
                        mode == item -> catalogInk(ObsidianPrimary)
                        modeGuided -> BrandInk
                        else -> TextPrimary
                    }
                    val guidedBorder = if (modeGuided) BrandInk.copy(alpha = 0.45f + 0.55f * pulseAmt) else Color.Transparent
                    val guidedBg = if (modeGuided) ObsidianPrimary.copy(alpha = 0.10f + 0.16f * pulseAmt) else Color.Transparent
                    Column(Modifier.weight(1f).fillMaxHeight().padding(3.dp)
                        .graphicsLayer {
                            val s = if (modeGuided) 1f + 0.04f * pulseAmt else 1f
                            scaleX = s; scaleY = s
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(guidedBg)
                        .border(if (modeGuided) 1.8.dp else 0.dp, guidedBorder, RoundedCornerShape(10.dp))
                        .clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                        replay?.cancel(); replay = scope.launch { phase.snapTo(0f); phase.animateTo(1f, tween(620, easing = panelEasing)); phase.snapTo(0f) }; onMode(item)
                    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        SiteIconMotion(glyph, item.label, Modifier.size(14.dp), ink) { if (modeGuided) maxOf(phase.value, pulseAmt * 0.4f) else phase.value }
                        Spacer(Modifier.height(2.dp))
                        Text(item.label, color = ink, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (modeGuided) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
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
                    val rotGuided = highlightTarget?.rotationId == rotation.id
                    val outline by animateColorAsState(if (selected) BrandInk else ObsidianBorderSubtle, tween(220), label = "rotationCardOutline")
                    val effectiveOutline = if (rotGuided) BrandInk.copy(alpha = 0.5f + 0.5f * pulseAmt) else outline
                    val cardFill by animateColorAsState(
                        if (selected) lerp(ObsidianSurfaceContainer, ObsidianPrimary, if (dark) 0.16f else 0.10f)
                        else ObsidianSurfaceContainer,
                        tween(220),
                        label = "rotationCardFill",
                    )
                    Column(
                        Modifier
                            .graphicsLayer {
                                val s = if (rotGuided) 1f + 0.03f * pulseAmt else 1f
                                scaleX = s; scaleY = s
                            }
                            .widthIn(min = 152.dp, max = 216.dp)
                            .width(IntrinsicSize.Max)
                            .height(58.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(cardFill)
                            .border(if (selected || rotGuided) 2.dp else 1.dp, effectiveOutline, RoundedCornerShape(13.dp))
                            .semantics { this.selected = selected }
                            .bouncyClickable { onRotation(rotation.id) }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rotation.name,
                                color = TextPrimary,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (ShiftRepository.defaultRotationId == rotation.id) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(currentTurniPalette.warning.fill)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SiteIcon("star", "Rotazione predefinita", Modifier.size(11.dp), Color(currentTurniPalette.warning.ink))
                                }
                            }
                            if (selected) {
                                Spacer(Modifier.width(3.dp))
                                SiteIcon("check", null, Modifier.size(12.dp), BrandInk)
                            }
                        }
                        val previewDays = rotation.daySequence.take(7)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (previewDays.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    previewDays.forEach { id ->
                                        val def = id.takeIf { it.isNotBlank() }?.let { rotationDefinitions[it] }
                                        if (def != null) {
                                            val tone = shiftColorStyle(
                                                source = def.color,
                                                dark = dark,
                                                backdrop = ObsidianSurfaceContainer,
                                                definitionId = def.id,
                                            )
                                            val code = def.shortCode.trim().ifBlank {
                                                getCompactShiftCode(def.id, def.shortCode)
                                            }.take(2)
                                            Box(
                                                Modifier
                                                    .size(width = 16.dp, height = 14.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(tone.codeFill),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = code,
                                                    color = tone.codeInk,
                                                    style = TextStyle(
                                                        fontSize = 7.sp,
                                                        lineHeight = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = (-0.2).sp,
                                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                        lineHeightStyle = LineHeightStyle(
                                                            alignment = LineHeightStyle.Alignment.Center,
                                                            trim = LineHeightStyle.Trim.Both,
                                                        ),
                                                    ),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        } else {
                                            Box(
                                                Modifier
                                                    .size(width = 16.dp, height = 14.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(ObsidianSurface),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = "—",
                                                    color = TextMuted,
                                                    style = TextStyle(
                                                        fontSize = 7.sp,
                                                        lineHeight = 8.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                        lineHeightStyle = LineHeightStyle(
                                                            alignment = LineHeightStyle.Alignment.Center,
                                                            trim = LineHeightStyle.Trim.Both,
                                                        ),
                                                    ),
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "${rotation.daySequence.size} g",
                                color = TextSecondary,
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                ),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(start = if (previewDays.isNotEmpty()) 4.dp else 0.dp),
                            )
                        }
                    }
                }
                if (effectiveRotations.isEmpty()) Text("Crea una rotazione in Turni", color = TextSecondary, fontSize = 12.sp)
            } else {
                (catalogOverride?:ShiftRepository.shiftDefinitions.filter { it.workProfileId == ShiftRepository.activeWorkProfileId }).forEach { def ->
                    val selected = tool==null && brush == def.id
                    val brushGuided = highlightTarget?.brushId == def.id
                    val phase=remember{Animatable(0f)};val scope=rememberCoroutineScope()
                    var replay by remember{mutableStateOf<Job?>(null)}
                    val pulse=kotlin.math.sin(phase.value*Math.PI).toFloat()
                    val effectivePulse = if (brushGuided) maxOf(pulse, pulseAmt) else pulse
                    val boxFill = when {
                        brushGuided -> lerp(ObsidianSurfaceContainer, def.color, 0.18f + 0.24f * pulseAmt)
                        selected -> lerp(ObsidianSurfaceContainer, def.color, .2f)
                        else -> ObsidianSurfaceContainer
                    }
                    val borderMod = when {
                        brushGuided -> Modifier.border(2.2.dp, BrandInk.copy(alpha = 0.5f + 0.5f * pulseAmt), RoundedCornerShape(12.dp))
                        selected -> Modifier.border(2.dp, def.color, RoundedCornerShape(12.dp))
                        else -> Modifier
                    }
                    Box(Modifier
                        .graphicsLayer {
                            val s = if (brushGuided) 1f + 0.08f * pulseAmt else 1f
                            scaleX = s; scaleY = s
                        }
                        .size(48.dp, 44.dp).clip(RoundedCornerShape(12.dp))
                        .background(boxFill)
                        .then(borderMod)
                        .clickable { replay?.cancel();replay=scope.launch{phase.snapTo(0f);phase.animateTo(1f,tween(660));phase.snapTo(0f)};onBrush(def.id) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(38.dp, 32.dp).clip(RoundedCornerShape(9.dp)).background(def.color), contentAlignment = Alignment.Center) {
                            Text(def.shortCode.ifBlank { getCompactShiftCode(def.id, def.shortCode) }, color = catalogInk(def.color),
                                fontSize = (12f+1.4f*effectivePulse).sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
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
    val maxPanelHeight=(LocalConfiguration.current.screenHeightDp-146).coerceAtLeast(160).dp
    val subPanel = detailShift != null
    LaunchedEffect(subPanel) { onDetailsActive(subPanel) }
    DisposableEffect(Unit) { onDispose { onDetailsActive(false) } }
    AnimatedVisibility(!subPanel, enter = fadeIn(tween(260)) + slideInVertically(tween(360, easing = panelEasing)) { it / 14 }, exit = fadeOut(tween(160))) {
        // Keep both page edges anchored while the keyboard is moving. The transition changes
        // layer opacity and offset only, without animating the container's measured height.
        AnimatedContent(dayNotes, contentAlignment = Alignment.BottomCenter, transitionSpec = {
            (fadeIn(tween(190, delayMillis = 35)) +
                slideInVertically(tween(220, easing = panelEasing)) { it / 30 } togetherWith
                fadeOut(tween(150))) using null
        }, label = "daySheetPage") { notesPage ->
            Surface(shape = RoundedCornerShape(23.dp), color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder), shadowElevation = 12.dp) {
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
                val consecutiveAlert=ShiftRepository.consecutiveShiftAlerts.firstOrNull{alert->alert.date==date&&alert.workProfileId==ShiftRepository.activeWorkProfileId}
                var lastConsecutiveAlert by remember { mutableStateOf(consecutiveAlert) }
                if (consecutiveAlert != null) lastConsecutiveAlert = consecutiveAlert
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
                AnimatedVisibility(
                    visible = consecutiveAlert != null,
                    enter = fadeIn(tween(220)) + expandVertically(tween(260, easing = panelEasing)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(200)),
                ) {
                    lastConsecutiveAlert?.let { alert ->
                        val warning = currentTurniPalette.warning
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(warning.fill))
                                .border(1.dp, Color(warning.ink).copy(alpha = .28f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SiteIcon("clock", null, Modifier.padding(top = 1.dp).size(18.dp), Color(warning.ink))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Turni consecutivi superati", color = Color(warning.ink), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(alert.description, color = Color(warning.ink), fontSize = 11.sp, lineHeight = 15.sp)
                            }
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
    val hasPermission = shift.permissionHours > 0f
    val hasNote = shift.notes.isNotBlank()
    val warning = currentTurniPalette.warning
    val gearColor = when {
        hasOvertime -> panelOvertimeInk
        hasPermission -> Color(warning.ink)
        else -> TextPrimary
    }
    val gearBackground = when {
        hasOvertime -> panelOvertimeSurface
        hasPermission -> Color(warning.fill)
        else -> ObsidianSurface
    }
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
            NativeShiftExtraChips(shift, def, Modifier.weight(1f), vertical = true)
            tools()
        } else NativeShiftExtraChips(shift, def, Modifier.fillMaxWidth())
    }
}

@Composable
private fun NativeShiftExtraChips(
    shift: AssignedShift,
    def: ShiftDefinition,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    Column(modifier) {
        val displayExitTime = remember(shift, def) { resolvedShiftDisplayExitTime(shift, def) }
        val hasExtra = shift.overtimeHours > 0 || shift.permissionHours > 0f || displayExitTime != null
        if(hasExtra) {
            val warning = currentTurniPalette.warning
            val contents: @Composable () -> Unit = {
                if (shift.overtimeHours > 0) DetailPill("+${formatOvertime(shift.overtimeHours)}", panelOvertimeInk, panelOvertimeSurface)
                if (shift.permissionHours > 0f) DetailPill(
                    "−${formatOvertime(shift.permissionHours)}",
                    Color(warning.ink),
                    Color(warning.fill),
                )
                displayExitTime?.let { DetailPill("Stacco $it", BrandInk, ObsidianSurface) }
            }
            if (vertical) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { contents() }
            else FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { contents() }
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

        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close))
            AnimatedVisibility(visible,
                enter = slideInVertically(tween(520, easing = CubicBezierEasing(.2f, .85f, .2f, 1f))) { it / 3 } + fadeIn(tween(380)),
                exit = slideOutVertically(tween(240, easing = panelEasing)) { it / 3 } + fadeOut(tween(220)),
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 14.dp).keyboardAwarePanelClearance(bottomInset)) {
                Surface(Modifier.fillMaxWidth().heightIn(max = minOf(620, (LocalConfiguration.current.screenHeightDp - 100).coerceAtLeast(240)).dp),
                    shape = RoundedCornerShape(28.dp), color = ObsidianSurface, border = BorderStroke(1.dp, ObsidianBorder), shadowElevation = 12.dp) {
                    Column(Modifier.padding(14.dp).then(if(scrollContent)Modifier.verticalScroll(rememberScrollState())else Modifier)) { content(close) }
                }
            }
        }
    }
}

private fun shiftSpanMinutes(start: LocalTime, end: LocalTime): Long {
    val startMin = start.toSecondOfDay() / 60L
    val endMin = end.toSecondOfDay() / 60L
    val diff = Math.floorMod(endMin - startMin, 1440L)
    return if (diff == 0L) 1440L else diff
}

private fun breakStartOffsetMinutes(
    start: LocalTime,
    totalSpanMin: Long,
    breakMinutes: Int,
    breakStartTime: String?,
): Long {
    val explicit = breakStartTime?.let { raw ->
        runCatching { LocalTime.parse(raw) }.getOrNull()?.let { brk ->
            Math.floorMod(brk.toSecondOfDay() / 60L - start.toSecondOfDay() / 60L, 1440L)
        }
    }
    return explicit ?: ((totalSpanMin - breakMinutes.coerceAtLeast(0)) / 2L).coerceAtLeast(0L)
}

internal data class ActualExitEvaluation(
    val overtimeHours: Float,
    val permissionHours: Float,
    val valid: Boolean,
)

internal fun evaluateActualExitTime(
    startTime: String,
    endTime: String,
    breakMinutes: Int,
    breakStartTime: String?,
    chosenExitTime: String,
    baseDurationMinutes: Long,
    baseHours: Float,
): ActualExitEvaluation {
    val start = runCatching { LocalTime.parse(startTime.trim()) }.getOrNull()
        ?: return ActualExitEvaluation(0f, 0f, false)
    val end = runCatching { LocalTime.parse(endTime.trim()) }.getOrNull()
        ?: return ActualExitEvaluation(0f, 0f, false)
    val chosen = runCatching { LocalTime.parse(chosenExitTime.trim()) }.getOrNull()
        ?: return ActualExitEvaluation(0f, 0f, false)
    if (chosen == end) return ActualExitEvaluation(0f, 0f, true)
    val spanMin = shiftSpanMinutes(start, end)
    val startMin = start.toSecondOfDay() / 60L
    val endMin = end.toSecondOfDay() / 60L
    val chosenMin = chosen.toSecondOfDay() / 60L
    val fromStart = Math.floorMod(chosenMin - startMin, 1440L)
    if (fromStart < spanMin) {
        val perm = calculatePermissionFromExitTime(
            startTime = startTime.trim(),
            endTime = endTime.trim(),
            breakMinutes = breakMinutes,
            breakStartTime = breakStartTime,
            chosenExitTime = chosenExitTime.trim(),
            baseDurationMinutes = baseDurationMinutes,
            baseHours = baseHours,
        )
        return ActualExitEvaluation(overtimeHours = 0f, permissionHours = perm, valid = true)
    }
    val afterEnd = Math.floorMod(chosenMin - endMin, 1440L)
    return if (afterEnd in 1..960) {
        ActualExitEvaluation(overtimeHours = afterEnd / 60f, permissionHours = 0f, valid = true)
    } else {
        ActualExitEvaluation(0f, 0f, false)
    }
}

internal fun calculatePermissionFromExitTime(
    startTime: String,
    endTime: String,
    breakMinutes: Int,
    breakStartTime: String?,
    chosenExitTime: String,
    baseDurationMinutes: Long,
    baseHours: Float,
): Float {
    if (baseDurationMinutes <= 0L || baseHours <= 0f) return 0f
    val start = runCatching { LocalTime.parse(startTime.trim()) }.getOrNull() ?: return 0f
    val end = runCatching { LocalTime.parse(endTime.trim()) }.getOrNull() ?: return 0f
    val chosen = runCatching { LocalTime.parse(chosenExitTime.trim()) }.getOrNull() ?: return 0f
    val totalSpanMin = shiftSpanMinutes(start, end)
    val elapsed = Math.floorMod(chosen.toSecondOfDay() / 60L - start.toSecondOfDay() / 60L, 1440L)
    val breakTaken = if (breakMinutes > 0) {
        val breakStartOffset = breakStartOffsetMinutes(start, totalSpanMin, breakMinutes, breakStartTime)
        (elapsed - breakStartOffset).coerceIn(0L, breakMinutes.toLong())
    } else 0L
    val workedUntilChosenMin = (elapsed - breakTaken).coerceAtLeast(0L)
    return ((baseDurationMinutes - workedUntilChosenMin) / 60f).coerceIn(0f, baseHours)
}

private fun permissionExitTimeString(
    startTime: String,
    endTime: String,
    breakMinutes: Int,
    breakStartTime: String?,
    permissionHours: Float,
    baseDurationMinutes: Long,
): String? {
    if (baseDurationMinutes <= 0L) return endTime
    val start = runCatching { LocalTime.parse(startTime.trim()) }.getOrNull() ?: return null
    val end = runCatching { LocalTime.parse(endTime.trim()) }.getOrNull() ?: return null
    val permMin = (permissionHours.coerceAtLeast(0f) * 60f).roundToInt().toLong()
    if (permMin <= 0L) return end.format(DateTimeFormatter.ofPattern("HH:mm"))
    val targetWorked = (baseDurationMinutes - permMin).coerceIn(0L, baseDurationMinutes)
    val totalSpanMin = shiftSpanMinutes(start, end)
    val offset = if (breakMinutes > 0) {
        val breakStartOffset = breakStartOffsetMinutes(start, totalSpanMin, breakMinutes, breakStartTime)
        if (targetWorked <= breakStartOffset) targetWorked else targetWorked + breakMinutes
    } else targetWorked
    return start.plusMinutes(offset).format(DateTimeFormatter.ofPattern("HH:mm"))
}

internal fun resolvedShiftDisplayExitTime(
    shift: AssignedShift,
    def: ShiftDefinition,
): String? {
    shift.actualEndTime?.takeIf { it.isNotBlank() }?.let { return it }
    if (shift.permissionHours <= 0f || def.isAllDay) return null
    val startTime = (shift.customStartTime ?: def.startTime).takeIf { it.isNotBlank() } ?: return null
    val endTime = (shift.customEndTime ?: def.endTime).takeIf { it.isNotBlank() } ?: return null
    val baseDurationMinutes = def.copy(startTime = startTime, endTime = endTime).durationMinutes
    if (baseDurationMinutes <= 0L) return null
    return permissionExitTimeString(
        startTime = startTime,
        endTime = endTime,
        breakMinutes = def.breakMinutes,
        breakStartTime = def.breakStartTime,
        permissionHours = shift.permissionHours,
        baseDurationMinutes = baseDurationMinutes,
    )
}

@Composable
private fun NativeShiftDetails(date: LocalDate, shift: AssignedShift, onClose: () -> Unit, onFeedback: (ActionFeedback) -> Unit) {
    val def = ShiftRepository.getShiftDefinition(shift.shiftDefId) ?: return
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val shiftStart = shift.customStartTime ?: def.startTime
    val baseEnd = shift.customEndTime ?: def.endTime
    val effectiveDef = remember(def, shiftStart, baseEnd) {
        def.copy(startTime = shiftStart, endTime = baseEnd)
    }
    val baseDurationMin = effectiveDef.durationMinutes
    val baseHours = effectiveDef.durationHoursFloat
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT) }
    val base = remember(baseEnd) { runCatching { LocalTime.parse(baseEnd.trim()) }.getOrNull() }
    var notes by remember(shift.id) { mutableStateOf(shift.notes) }
    var hours by remember(shift.id) {
        mutableStateOf(if (def.isAllDay) 0f else shift.overtimeHours.coerceAtLeast(0f))
    }
    var permHours by remember(shift.id) {
        val initial = if (shift.permissionHours.isFinite()) shift.permissionHours else 0f
        mutableStateOf(if (def.isAllDay) 0f else initial.coerceIn(0f, baseHours))
    }
    var end by remember(shift.id) {
        val initialEnd = if (def.isAllDay) {
            baseEnd
        } else if (shift.permissionHours > 0f && shift.overtimeHours == 0f) {
            shift.actualEndTime
                ?: permissionExitTimeString(shiftStart, baseEnd, def.breakMinutes, def.breakStartTime, shift.permissionHours, baseDurationMin)
                ?: baseEnd
        } else {
            shift.actualEndTime ?: baseEnd
        }
        mutableStateOf(initialEnd)
    }
    var deltaOffsetMin by remember(shift.id) {
        val initialDelta = when {
            def.isAllDay -> 0
            shift.overtimeHours > 0f -> (shift.overtimeHours * 60f).roundToInt().coerceIn(0, 960)
            shift.permissionHours > 0f -> -(shift.permissionHours * 60f).roundToInt().coerceIn(0, baseDurationMin.toInt().coerceAtLeast(0))
            else -> 0
        }
        mutableStateOf(initialDelta)
    }
    var valid by remember(shift.id) {
        mutableStateOf(
            def.isAllDay || evaluateActualExitTime(
                startTime = shiftStart,
                endTime = baseEnd,
                breakMinutes = def.breakMinutes,
                breakStartTime = def.breakStartTime,
                chosenExitTime = end,
                baseDurationMinutes = baseDurationMin,
                baseHours = baseHours,
            ).valid
        )
    }
    var noteTab by remember(shift.id) { mutableStateOf(false) }
    var showTimePicker by remember(shift.id) { mutableStateOf(false) }
    val warning = currentTurniPalette.warning
    val permInk = Color(warning.ink)
    val permSurface = Color(warning.fill)
    val minDelta = -baseDurationMin.toInt().coerceAtLeast(0)
    val maxDelta = 960

    val resetTime: () -> Unit = {
        end = baseEnd
        hours = 0f
        permHours = 0f
        deltaOffsetMin = 0
        valid = true
    }

    val applyExitTime: (String) -> Unit = { raw ->
        val filtered = raw.filter { char -> char.isDigit() || char == ':' }.take(5)
        end = filtered
        if (def.isAllDay) {
            hours = 0f
            permHours = 0f
            deltaOffsetMin = 0
            valid = true
        } else {
            val eval = evaluateActualExitTime(
                startTime = shiftStart,
                endTime = baseEnd,
                breakMinutes = def.breakMinutes,
                breakStartTime = def.breakStartTime,
                chosenExitTime = filtered,
                baseDurationMinutes = baseDurationMin,
                baseHours = baseHours,
            )
            valid = eval.valid
            if (eval.valid) {
                hours = eval.overtimeHours
                permHours = eval.permissionHours
                deltaOffsetMin = when {
                    eval.overtimeHours > 0f -> (eval.overtimeHours * 60f).roundToInt().coerceIn(1, maxDelta)
                    eval.permissionHours > 0f -> -(eval.permissionHours * 60f).roundToInt().coerceIn(0, -minDelta)
                    else -> 0
                }
            } else {
                hours = 0f
                permHours = 0f
                if (filtered.isBlank()) deltaOffsetMin = 0
            }
        }
    }

    val applyStepMinutes: (Int) -> Unit = { stepMinutes ->
        if (!def.isAllDay && base != null) {
            val currentDelta = when {
                !valid -> 0
                hours > 0f -> deltaOffsetMin.coerceIn(1, maxDelta)
                permHours > 0f -> deltaOffsetMin.coerceIn(minDelta, -1)
                else -> 0
            }
            val clampedDelta = (currentDelta + stepMinutes).coerceIn(minDelta, maxDelta)
            deltaOffsetMin = clampedDelta
            valid = true
            when {
                clampedDelta == 0 -> {
                    end = baseEnd
                    hours = 0f
                    permHours = 0f
                }
                clampedDelta > 0 -> {
                    end = base.plusMinutes(clampedDelta.toLong()).format(timeFormatter)
                    hours = clampedDelta / 60f
                    permHours = 0f
                }
                else -> {
                    val nextPerm = (-clampedDelta / 60f).coerceIn(0f, baseHours)
                    permHours = nextPerm
                    end = permissionExitTimeString(
                        startTime = shiftStart,
                        endTime = baseEnd,
                        breakMinutes = def.breakMinutes,
                        breakStartTime = def.breakStartTime,
                        permissionHours = nextPerm,
                        baseDurationMinutes = baseDurationMin,
                    ) ?: baseEnd
                    hours = 0f
                }
            }
        }
    }

    val statusKind = when {
        hours > 0f -> 1
        permHours > 0f -> -1
        else -> 0
    }
    val exitInk by animateColorAsState(
        targetValue = when {
            !valid -> panelDanger
            hours > 0f -> panelOvertime
            permHours > 0f -> permInk
            else -> TextPrimary
        },
        animationSpec = tween(180),
        label = "shiftExitInk",
    )
    val clockTint by animateColorAsState(
        targetValue = when {
            hours > 0f -> panelOvertime
            permHours > 0f -> permInk
            else -> BrandInk
        },
        animationSpec = tween(180),
        label = "shiftClockTint",
    )
    val statusColor by animateColorAsState(
        targetValue = when (statusKind) {
            1 -> panelOvertime
            -1 -> permInk
            else -> TextSecondary
        },
        animationSpec = tween(180),
        label = "shiftStatusColor",
    )
    val statusText = when {
        hours > 0f -> "+${formatOvertime(hours)} · Straordinario"
        permHours > 0f -> "−${formatOvertime(permHours)} · Permesso"
        else -> "Orario regolare"
    }
    val canReset = hours > 0f || permHours > 0f || (!def.isAllDay && end != baseEnd)

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
                Text(if (def.isAllDay) "Tutto il giorno" else "$shiftStart – $baseEnd",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(def.color), contentAlignment = Alignment.Center) {
                Text(def.shortCode, color = catalogInk(def.color), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(14.dp))
        SlidingSegmentBar(
            items = listOf("Orario" to "timer", "Note turno" to "notebook"),
            selectedIndex = if (noteTab) 1 else 0,
            onSelect = { noteTab = it == 1 },
            height = 44.dp,
            pillColor = ObsidianPrimary,
            pillInk = OnBrand,
        )
        Spacer(Modifier.height(14.dp))
        AnimatedContent(noteTab, transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically(tween(260, easing = panelEasing)) { 4 }) togetherWith fadeOut(tween(100))
        }, label = "shiftDetailTab") { showNotes ->
            if (showNotes) Column {
                Text("Note del turno", color = TextSecondary, fontSize = 11.sp)
                BasicTextField(notes, { notes = it.take(500) }, textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp),
                    cursorBrush = SolidColor(ObsidianPrimary), modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(130.dp)
                        .clip(RoundedCornerShape(15.dp)).background(ObsidianSurfaceContainer).border(1.dp, ObsidianBorder, RoundedCornerShape(15.dp)).padding(12.dp),
                    decorationBox = { field -> if (notes.isBlank()) Text("Scrivi una nota…", color = TextSecondary, fontSize = 14.sp); field() })
            } else Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianSurfaceContainer)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                    .animateContentSize(tween(220, easing = panelEasing))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (def.isAllDay) {
                    Text(
                        "Per registrare straordinario o permesso serve un turno con orario definito.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Fine prevista", color = TextSecondary, fontSize = 11.sp)
                            Text(
                                text = baseEnd,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                        }
                        Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Stacco effettivo", color = TextSecondary, fontSize = 11.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BasicTextField(
                                    value = end,
                                    onValueChange = applyExitTime,
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = exitInk,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                    cursorBrush = SolidColor(exitInk),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ObsidianSurface)
                                        .padding(horizontal = 9.dp, vertical = 7.dp),
                                    decorationBox = { field -> Box(contentAlignment = Alignment.CenterStart) { field() } },
                                )
                                PanelIcon(
                                    name = "clock",
                                    label = "Scegli stacco effettivo",
                                    onClick = {
                                        focus.clearFocus()
                                        keyboard?.hide()
                                        showTimePicker = true
                                    },
                                    width = 44.dp,
                                    height = 44.dp,
                                    glyphSize = 18.dp,
                                    tint = clockTint,
                                    background = ObsidianSurface,
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = statusText,
                            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
                            label = "shiftTimeStatus",
                            modifier = Modifier.weight(1f),
                        ) { label ->
                            Text(
                                text = label,
                                color = statusColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "Azzera",
                            color = ColorNegative,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable(enabled = canReset, onClick = resetTime)
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                                .graphicsLayer { alpha = if (canReset) 1f else 0.45f },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        val steps = listOf(
                            -60 to "−1h",
                            -30 to "−30m",
                            -15 to "−15m",
                            15 to "+15m",
                            30 to "+30m",
                            60 to "+1h",
                        )
                        steps.forEach { (stepMinutes, label) ->
                            val isNegative = stepMinutes < 0
                            val chipEnabled = if (isNegative) deltaOffsetMin > minDelta || !valid else deltaOffsetMin < maxDelta || !valid
                            val baseTint = if (isNegative) permSurface else panelOvertimeSurface
                            val chipFill = lerp(ObsidianSurface, baseTint, if (TurniThemeState.dark) 0.55f else 0.45f)
                            val chipInk = readableInk(chipFill, if (isNegative) permInk else panelOvertimeInk)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(chipFill)
                                    .bouncyClickable(enabled = chipEnabled) { applyStepMinutes(stepMinutes) }
                                    .padding(horizontal = 2.dp)
                                    .graphicsLayer { alpha = if (chipEnabled) 1f else 0.45f },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = chipInk,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    if (!valid) {
                        Text(
                            "Controlla l’orario di stacco (HH:mm, massimo 16 ore dopo la fine prevista)",
                            color = panelDanger,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PanelButton("Salva", "check", true, Modifier.fillMaxWidth(), 44.dp) {
            if (valid) {
                focus.clearFocus(); keyboard?.hide()
                val normalizedEnd = runCatching {
                    LocalTime.parse(end.trim()).format(timeFormatter)
                }.getOrDefault(end.trim())
                ShiftRepository.setShiftExtraDetails(
                    date = date,
                    assignedShiftId = shift.id,
                    actualEndTime = if (def.isAllDay || hours <= 0f) null else normalizedEnd,
                    overtimeHours = if (def.isAllDay) 0f else hours,
                    notes = notes,
                    permissionHours = if (def.isAllDay) 0f else permHours,
                )
                onFeedback(ActionFeedback("Informazioni salvate", false)); close()
            } else onFeedback(ActionFeedback("Controlla l’orario di stacco", true))
        }
    }
    if (showTimePicker) {
        TurniTimePickerDialog(
            title = "Stacco effettivo",
            initialTime = runCatching { LocalTime.parse(end.trim()).format(timeFormatter) }.getOrDefault(baseEnd),
            onDismiss = { showTimePicker = false },
            onConfirm = { chosen ->
                showTimePicker = false
                applyExitTime(chosen)
            },
        )
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
