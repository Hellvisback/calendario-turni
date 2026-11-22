@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.turni.calendario.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.model.WorkProfile
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.BrandInk
import com.turni.calendario.ui.theme.CalendarSurface
import com.turni.calendario.ui.theme.CalendarWeekend
import com.turni.calendario.ui.theme.ColorNegative
import com.turni.calendario.ui.theme.ObsidianBackground
import com.turni.calendario.ui.theme.ObsidianPrimary
import com.turni.calendario.ui.theme.ObsidianPrimaryContainer
import com.turni.calendario.ui.theme.ObsidianSurface
import com.turni.calendario.ui.theme.ObsidianSurfaceContainer
import com.turni.calendario.ui.theme.ObsidianSurfaceHigh
import com.turni.calendario.ui.theme.OnBrand
import com.turni.calendario.ui.theme.TextPrimary
import com.turni.calendario.ui.theme.TextSecondary
import com.turni.calendario.ui.theme.TurniThemeState
import com.turni.calendario.ui.theme.contrastRatio
import com.turni.calendario.ui.theme.currentTurniPalette
import com.turni.calendario.ui.theme.readableInk
import com.turni.calendario.ui.theme.readablePigment
import com.turni.calendario.ui.theme.shiftColorStyle
import com.turni.calendario.ui.theme.shiftDisplayColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** Approved defaults; existing custom colors remain untouched. */
val catalogPastelColors = listOf(0xFFDBC38EL, 0xFFD9AE9BL, 0xFFAEBCE4L, 0xFF97C6BFL, 0xFF9EC4DEL, 0xFFB8C1C8L)
internal fun catalogInk(color: Color): Color = readableInk(color, Color(0xFF211728))

private val catalogTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private fun CoroutineScope.replayCatalogMotion(
    phase: Animatable<Float, *>,
    previous: Job?,
    durationMillis: Int = 480,
): Job {
    previous?.cancel()
    return launch {
        phase.snapTo(0f)
        phase.animateTo(1f, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
        phase.snapTo(0f)
    }
}

private suspend fun Animatable<Float, *>.popSpring(peak: Float = 1.08f, dip: Float = 0.93f) {
    snapTo(dip)
    animateTo(peak, tween(durationMillis = 115, easing = FastOutSlowInEasing))
    animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 460f))
}

private fun harmonizedSelectionRing(
    swatchColor: Color,
    backdrop: Color,
    definitionId: String? = null,
): Color {
    val dark = TurniThemeState.dark
    val style = shiftColorStyle(swatchColor, dark, backdrop, definitionId = definitionId)
    val candidate = if (dark) style.codeFill else style.codeInk
    return if (contrastRatio(backdrop, candidate) >= 3f) candidate
    else readablePigment(backdrop, style.codeFill)
}

private fun Modifier.tactileNoFlashClick(
    enabled: Boolean = true,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    this.clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = role,
        onClickLabel = onClickLabel,
    ) {
        runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
        onClick()
    }
}

@Composable
internal fun CatalogGroup(
    title: String,
    outlined: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val horizontalPad = if (outlined) 16.dp else 15.dp
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPad, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
internal fun CatalogToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .tactileNoFlashClick { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        )
        TurniSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Matches the site's external label, compact filled input and square typography. */
@Composable
private fun CatalogField(
    label: String,
    value: String,
    change: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    error: Boolean = false,
    height: Dp = 44.dp,
    centered: Boolean = false,
    placeholder: String = "",
) {
    val shape = RoundedCornerShape(14.dp)
    val errorBorder by animateColorAsState(
        targetValue = if (error) ColorNegative else Color.Transparent,
        animationSpec = tween(180),
        label = "catalogFieldError",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
        )
        BasicTextField(
            value = value,
            onValueChange = change,
            singleLine = true,
            textStyle = TextStyle(
                color = TextPrimary,
                fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                fontSize = if (centered) 17.sp else 14.sp,
                fontWeight = if (centered) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            cursorBrush = SolidColor(ObsidianPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(shape)
                .background(ObsidianSurfaceContainer)
                .then(if (error) Modifier.border(1.5.dp, errorBorder, shape) else Modifier)
                .padding(horizontal = if (centered) 10.dp else 13.dp)
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = TextSecondary.copy(alpha = 0.65f),
                            fontSize = if (centered) 15.sp else 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
internal fun CatalogButton(
    label: String,
    action: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: String? = null,
) {
    CatalogActionButton(
        label = label,
        action = action,
        fillColor = if (primary) ObsidianPrimary else ObsidianSurfaceContainer,
        inkColor = if (primary) OnBrand else TextPrimary,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
    )
}

@Composable
private fun CatalogActionButton(
    label: String,
    action: () -> Unit,
    fillColor: Color,
    inkColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: String? = null,
) {
    val phase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var replay by remember { mutableStateOf<Job?>(null) }
    val buttonShape = RoundedCornerShape(14.dp)
    val fill by animateColorAsState(
        targetValue = fillColor,
        animationSpec = tween(180),
        label = "catalogButtonFill",
    )
    val ink by animateColorAsState(
        targetValue = inkColor,
        animationSpec = tween(180),
        label = "catalogButtonInk",
    )
    val contentAlpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(buttonShape)
            .background(fill)
            .semantics { role = Role.Button }
            .bouncyClickable(enabled = enabled) {
                if (icon != null) {
                    replay = scope.replayCatalogMotion(phase, replay, durationMillis = 460)
                }
                action()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (icon != null) {
                SiteIconMotion(
                    name = icon,
                    description = null,
                    modifier = Modifier.size(17.dp),
                    tint = ink.copy(alpha = contentAlpha),
                ) { phase.value }
            }
            Text(
                text = label,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun CatalogIconAction(
    name: String,
    label: String,
    action: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = if (name == "x") ColorNegative else TextPrimary,
    fill: Color = if (name == "x") Color(currentTurniPalette.danger.fill) else ObsidianSurfaceContainer,
) {
    val phase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var replay by remember { mutableStateOf<Job?>(null) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 44.dp)
            .clip(shape)
            .background(fill)
            .semantics { role = Role.Button }
            .bouncyClickable {
                replay = scope.replayCatalogMotion(phase, replay, durationMillis = 520)
                action()
            },
        contentAlignment = Alignment.Center,
    ) {
        SiteIconMotion(
            name = name,
            description = label,
            modifier = Modifier.size(20.dp),
            tint = tint,
        ) { phase.value }
    }
}

/** One real sheet, with finite spring entry/exit and stable independent scrolling. */
@Composable
internal fun CatalogEditorSheet(
    title: String,
    onDismiss: () -> Unit,
    heightFraction: Float = .82f,
    subtitle: String = "Modello per il tuo calendario",
    fullPage: Boolean = false,
    content: @Composable ColumnScope.((() -> Unit) -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val headerProgress = remember { Animatable(0f) }
    val finish: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            visible = false
            scope.launch { delay(280); action() }
        }
    }
    LaunchedEffect(Unit) {
        visible = true
        delay(140)
        headerProgress.animateTo(1f, tween(680, easing = FastOutSlowInEasing))
        headerProgress.snapTo(0f)
    }
    BackHandler { finish(onDismiss) }

    val isFullScreen = fullPage || heightFraction >= 0.9f
    val cornerRadius = if (fullPage) 0.dp else 26.dp
    val scrimAlpha = if (TurniThemeState.dark) 0.54f else 0.32f

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .tactileNoFlashClick(enabled = !closing) { finish(onDismiss) },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = .88f, stiffness = Spring.StiffnessLow)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heightFraction),
                shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
                color = ObsidianBackground,
                shadowElevation = if (fullPage) 0.dp else 16.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianBackground)
                            .then(if (isFullScreen) Modifier.statusBarsPadding() else Modifier)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (isFullScreen) {
                            TonalIconButton(
                                icon = "arrow-left",
                                contentDescription = "Indietro",
                                onClick = { finish(onDismiss) },
                                size = 40.dp,
                                cornerRadius = 14.dp,
                                fill = ObsidianSurface,
                                tint = TextPrimary,
                                iconSize = 20.dp,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianSurface),
                                contentAlignment = Alignment.Center,
                            ) {
                                SiteIconMotion(
                                    name = "pencil",
                                    description = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = BrandInk,
                                ) { headerProgress.value }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = title,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (fullPage) 23.sp else if (isFullScreen) 21.sp else 19.sp,
                                lineHeight = if (fullPage) 27.sp else 24.sp,
                                letterSpacing = (-0.5).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (!isFullScreen) {
                            TonalIconButton(
                                icon = "x",
                                contentDescription = "Chiudi",
                                onClick = { finish(onDismiss) },
                                size = 38.dp,
                                cornerRadius = 12.dp,
                                fill = Color(currentTurniPalette.danger.fill),
                                tint = ColorNegative,
                                iconSize = 18.dp,
                            )
                        }
                    }
                    content(finish)
                }
            }
        }
    }
}

@Composable
private fun PastelSwatchTile(
    swatchColor: Color,
    selected: Boolean,
    size: Dp,
    cornerRadius: Dp,
    contentDescription: String,
    shiftId: String? = null,
    onClick: () -> Unit,
) {
    val ink = catalogInk(swatchColor)
    val ringTarget = harmonizedSelectionRing(swatchColor, ObsidianSurface, shiftId)
    val ringColor by animateColorAsState(
        targetValue = if (selected) ringTarget else Color.Transparent,
        animationSpec = tween(180),
        label = "swatchRing",
    )
    val popScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var popJob by remember { mutableStateOf<Job?>(null) }
    var wasSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !wasSelected) {
            popJob?.cancel()
            popJob = scope.launch { popScale.popSpring(peak = 1.1f, dip = 0.92f) }
        }
        wasSelected = selected
    }
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = popScale.value
                scaleY = popScale.value
            }
            .clip(RoundedCornerShape(cornerRadius + 3.dp))
            .border(2.dp, ringColor, RoundedCornerShape(cornerRadius + 3.dp))
            .padding(3.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(pastelSheenBrush(swatchColor, ink))
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
                this.role = Role.RadioButton
            }
            .tactileNoFlashClick {
                popJob?.cancel()
                popJob = scope.launch { popScale.popSpring(peak = 1.1f, dip = 0.92f) }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(dampingRatio = 0.65f, stiffness = 520f)) + fadeIn(tween(150)),
            exit = scaleOut(tween(120)) + fadeOut(tween(120)),
        ) {
            SiteIcon("check", null, Modifier.size(16.dp), ink)
        }
    }
}

@Composable
fun UniversalColorPickerRow(
    selectedColorHex: Long,
    showNames: Boolean = false,
    shiftId: String? = null,
    onColorSelected: (Long) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerPhase = remember { Animatable(0f) }
    val pickerScope = rememberCoroutineScope()
    var pickerJob by remember { mutableStateOf<Job?>(null) }
    fun display(value: Long) = shiftDisplayColor(Color(value), definitionId = shiftId)
    val selectedColor = display(selectedColorHex)
    val swatchNames = remember { listOf("Ambra", "Corallo", "Lavanda", "Menta", "Azzurro", "Perla") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            catalogPastelColors.forEachIndexed { index, value ->
                val swatchColor = display(value)
                val isSelected = swatchColor == selectedColor
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .tactileNoFlashClick { onColorSelected(value) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PastelSwatchTile(
                        swatchColor = swatchColor,
                        selected = isSelected,
                        size = if (showNames) 42.dp else 40.dp,
                        cornerRadius = if (showNames) 11.dp else 10.dp,
                        contentDescription = swatchNames[index],
                        shiftId = shiftId,
                        onClick = { onColorSelected(value) },
                    )
                    if (showNames) {
                        Text(
                            text = swatchNames[index],
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) TextPrimary else TextSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
        }
        if (showNames) {
            val previewColor = selectedColor
            val previewInk = catalogInk(previewColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurfaceContainer)
                    .tactileNoFlashClick(
                        role = Role.Button,
                        onClickLabel = "Scegli colore personalizzato",
                    ) {
                        pickerJob = pickerScope.replayCatalogMotion(pickerPhase, pickerJob, durationMillis = 420)
                        showPicker = true
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pastelSheenBrush(previewColor, previewInk)),
                    contentAlignment = Alignment.Center,
                ) {
                    SiteIcon("pipette", null, Modifier.size(16.dp), previewInk)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Colore personalizzato",
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = colorToHex(previewColor),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = TextSecondary,
                    )
                }
                SiteIconMotion("chevron-right", null, Modifier.size(16.dp), TextSecondary) {
                    pickerPhase.value
                }
            }
        } else {
            val pipInk = catalogInk(selectedColor)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    modifier = Modifier.width(62.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Colore",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(pastelSheenBrush(selectedColor, pipInk))
                            .tactileNoFlashClick(role = Role.Button) { showPicker = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        SiteIcon("pipette", "Scegli colore", Modifier.size(18.dp), pipInk)
                    }
                }
                var hexDraft by remember(selectedColorHex, selectedColor) {
                    mutableStateOf(colorToHex(selectedColor))
                }
                CatalogField(
                    label = "HEX",
                    value = hexDraft,
                    change = {
                        hexDraft = it.take(7).uppercase()
                        if (Regex("#[0-9A-F]{6}").matches(hexDraft)) {
                            parseHexToColor(hexDraft)?.let { parsed ->
                                onColorSelected(colorToLong(parsed))
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    error = !Regex("#[0-9A-F]{6}").matches(hexDraft),
                )
            }
        }
    }
    if (showPicker) {
        CustomColorPickerDialog(colorToLong(selectedColor), { showPicker = false }) {
            onColorSelected(it)
            showPicker = false
        }
    }
}

@Composable
internal fun CatalogWorkIcon(
    key: String,
    modifier: Modifier = Modifier,
    tint: Color = TextSecondary,
) {
    CatalogWorkIconMotion(key = key, modifier = modifier, tint = tint)
}

@Composable
private fun CatalogWorkIconMotion(
    key: String,
    modifier: Modifier = Modifier,
    tint: Color = TextSecondary,
    progress: () -> Float = { 0f },
) {
    val scale = remember { Animatable(1f) }
    var previousKey by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key != previousKey) {
            previousKey = key
            scale.popSpring(peak = 1.12f, dip = 0.86f)
        }
    }
    val name = when (key) {
        "business" -> "building"
        "people", "groups" -> "users"
        "computer", "laptop" -> "laptop"
        "factory", "build", "wrench" -> "wrench"
        "hospital", "medical", "health" -> "hospital"
        "shipping", "truck", "local_shipping" -> "truck"
        "transit", "train" -> "train"
        "security" -> "shield-alert"
        else -> "briefcase-business"
    }
    SiteIconMotion(
        name = name,
        description = null,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        },
        tint = tint,
        progress = progress,
    )
}

@Composable
fun CustomColorPickerDialog(
    initialColorHex: Long,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit,
) {
    val initialHsv = remember(initialColorHex) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColorHex.toInt(), it) }
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    var hex by remember { mutableStateOf(colorToHex(Color(initialColorHex))) }
    val selected = hsvToColor(hue, saturation, brightness)
    val valid = Regex("#[0-9a-fA-F]{6}").matches(hex)
    fun updateHex() {
        hex = colorToHex(hsvToColor(hue, saturation, brightness))
    }

    TurniSafeBottomDialog(onClose = onDismiss) { finish ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Colore personalizzato",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Palette e personalizzazione",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            TonalIconButton(
                icon = "x",
                contentDescription = "Chiudi",
                onClick = { finish(onDismiss) },
                size = 36.dp,
                cornerRadius = 12.dp,
                fill = Color(currentTurniPalette.danger.fill),
                tint = ColorNegative,
                iconSize = 18.dp,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            catalogPastelColors.forEach { value ->
                val swatchColor = Color(value)
                val isSelected = colorToLong(selected) == value ||
                    shiftDisplayColor(selected) == shiftDisplayColor(swatchColor)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    PastelSwatchTile(
                        swatchColor = swatchColor,
                        selected = isSelected,
                        size = 42.dp,
                        cornerRadius = 11.dp,
                        contentDescription = colorToHex(swatchColor),
                        onClick = {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(value.toInt(), hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                            updateHex()
                        },
                    )
                }
            }
        }
        NativeColorPlane(hue, saturation, brightness) { s, v ->
            saturation = s
            brightness = v
            updateHex()
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Tonalità",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Red,
                                    Color.Yellow,
                                    Color.Green,
                                    Color.Cyan,
                                    Color.Blue,
                                    Color.Magenta,
                                    Color.Red,
                                ),
                            ),
                        ),
                )
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        updateHex()
                    },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = hsvToColor(hue, 1f, 1f),
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CatalogField(
                label = "HEX",
                value = hex,
                change = {
                    hex = it.take(7).uppercase()
                    if (Regex("#[0-9A-F]{6}").matches(hex)) {
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), hsv)
                        if (hsv[1] > 0f) hue = hsv[0]
                        saturation = hsv[1]
                        brightness = hsv[2]
                    }
                },
                modifier = Modifier.weight(1f),
                error = !valid,
                height = 44.dp,
            )
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(pastelSheenBrush(selected, catalogInk(selected))),
            )
        }
        AnimatedVisibility(visible = !valid) {
            Text(
                text = "Inserisci un colore HEX valido",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = ColorNegative,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CatalogButton("Annulla", { finish(onDismiss) }, Modifier.weight(1f))
            CatalogButton(
                label = "Imposta",
                action = {
                    parseHexToColor(hex)?.let { parsed ->
                        finish { onColorSelected(colorToLong(parsed)) }
                    }
                },
                modifier = Modifier.weight(1f),
                primary = true,
                enabled = valid,
                icon = "check",
            )
        }
    }
}

@Composable
fun ShiftEditorScreen(
    shift: ShiftDefinition,
    onDismiss: () -> Unit,
    onSave: (ShiftDefinition) -> Unit,
    onDelete: (String) -> Unit,
    allowWorkSelection: Boolean = true,
) {
    var name by remember(shift.id) { mutableStateOf(shift.name) }
    var code by remember(shift.id) { mutableStateOf(shift.shortCode.take(4)) }
    var start by remember(shift.id) { mutableStateOf(shift.startTime) }
    var end by remember(shift.id) { mutableStateOf(shift.endTime) }
    var allDay by remember(shift.id) { mutableStateOf(shift.isAllDay) }
    var hasBreak by remember(shift.id) { mutableStateOf(shift.breakMinutes > 0) }
    var pause by remember(shift.id) { mutableStateOf(shift.breakMinutes.toString()) }
    var breakStart by remember(shift.id) { mutableStateOf(shift.breakStartTime ?: "12:00") }
    var color by remember(shift.id) { mutableStateOf(shift.colorHex) }
    var workId by remember(shift.id) { mutableStateOf(shift.workProfileId) }
    var workMenu by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val existing = ShiftRepository.shiftDefinitions.any { it.id == shift.id }
    val previewColor = shiftDisplayColor(Color(color), definitionId = shift.id)
    fun validTime(value: String) = runCatching { LocalTime.parse(value) }.isSuccess
    val validCode = code.matches(Regex("[A-Z0-9]{1,4}"))
    val pauseInt = pause.toIntOrNull()
    val validPause = !hasBreak || (validTime(breakStart) && (pauseInt ?: -1) in 0..720)
    val valid = name.isNotBlank() && validCode && (allDay || (validTime(start) && validTime(end) && validPause))

    fun draft() = shift.copy(
        name = name.trim(),
        shortCode = code,
        startTime = start,
        endTime = end,
        colorHex = color,
        workProfileId = workId,
        isAllDay = allDay,
        breakMinutes = if (hasBreak && !allDay) (pauseInt ?: 0).coerceAtLeast(0) else 0,
        breakStartTime = if (hasBreak && !allDay) breakStart else null,
    )

    CatalogEditorSheet(
        title = if (existing) "Modifica turno" else "Nuovo turno",
        onDismiss = onDismiss,
        heightFraction = 1f,
        subtitle = "Orari, stile e pausa del turno",
        fullPage = true,
    ) { finish ->
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val heroFill = lerp(ObsidianSurface, previewColor, if (TurniThemeState.dark) 0.14f else 0.22f)
            val miniStyle = shiftColorStyle(Color(color), TurniThemeState.dark, CalendarSurface, definitionId = shift.id)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = heroFill,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PastelBadge(
                        color = Color(color),
                        size = 52.dp,
                        cornerRadius = 15.dp,
                        definitionId = shift.id,
                    ) { ink ->
                        Text(
                            text = code.ifBlank { "—" },
                            color = ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (code.length >= 4) 13.sp else 15.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = name.ifBlank { "Il tuo turno" },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (allDay) "Tutto il giorno" else "$start – $end",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = TextSecondary,
                            maxLines = 1,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (allDay) {
                                PastelPill(
                                    text = "Giornata intera",
                                    color = Color(color),
                                    icon = "calendar",
                                )
                            } else {
                                val durationText = if (validTime(start) && validTime(end)) {
                                    draft().durationFormatted
                                } else {
                                    "Orario non valido"
                                }
                                PastelPill(
                                    text = durationText,
                                    color = Color(color),
                                    icon = "clock",
                                )
                                if (hasBreak && (pauseInt ?: 0) > 0) {
                                    PastelPill(
                                        text = "Pausa ${pauseInt}m",
                                        color = ExpressivePastel.PEARL.sourceColor,
                                    )
                                }
                            }
                        }
                    }
                    // Miniature calendar-day cell mock showing how the badge looks in the monthly grid.
                    Column(
                        modifier = Modifier
                            .width(54.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(CalendarSurface)
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "15",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(7.dp))
                                .background(pastelSheenBrush(miniStyle.codeFill, miniStyle.codeInk))
                                .padding(horizontal = 3.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = code.ifBlank { "TUR" },
                                color = miniStyle.codeInk,
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(
                                text = if (allDay) "– –" else start,
                                color = miniStyle.codeInk.copy(alpha = 0.85f),
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            CatalogGroup("Identità") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CatalogField(
                        label = "Nome del turno",
                        value = name,
                        change = { name = it.take(60) },
                        modifier = Modifier.weight(2f),
                        placeholder = "Es. Mattina",
                    )
                    CatalogField(
                        label = "Sigla",
                        value = code,
                        change = { code = it.uppercase().filter { ch -> ch in 'A'..'Z' || ch in '0'..'9' }.take(4) },
                        modifier = Modifier.weight(1f),
                        error = !validCode,
                        centered = true,
                        placeholder = "MAT",
                    )
                }
                if (allowWorkSelection && ShiftRepository.workProfiles.size > 1) {
                    val selectedWork = ShiftRepository.workProfiles.firstOrNull { it.id == workId }
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (workMenu) 180f else 0f,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
                        label = "workPickerChevron",
                    )
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianSurfaceContainer)
                                .tactileNoFlashClick(role = Role.Button) { workMenu = !workMenu }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PastelBadge(
                                color = Color(selectedWork?.colorHex ?: catalogPastelColors[4]),
                                size = 34.dp,
                                cornerRadius = 10.dp,
                            ) { ink ->
                                CatalogWorkIcon(
                                    key = selectedWork?.iconKey ?: "work",
                                    modifier = Modifier.size(18.dp),
                                    tint = ink,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Calendario di lavoro",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                )
                                Text(
                                    text = selectedWork?.name ?: "Seleziona lavoro",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            SiteIcon(
                                name = "chevron-down",
                                contentDescription = null,
                                modifier = Modifier
                                    .size(18.dp)
                                    .rotate(chevronRotation),
                                tint = TextSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded = workMenu,
                            onDismissRequest = { workMenu = false },
                            containerColor = ObsidianSurface,
                            shape = RoundedCornerShape(18.dp),
                            shadowElevation = 12.dp,
                        ) {
                            ShiftRepository.workProfiles.forEach { profile ->
                                val isSelected = profile.id == workId
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = profile.name,
                                            color = if (isSelected) BrandInk else TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = {
                                        PastelBadge(
                                            color = Color(profile.colorHex),
                                            size = 28.dp,
                                            cornerRadius = 8.dp,
                                        ) { ink ->
                                            CatalogWorkIcon(profile.iconKey, Modifier.size(15.dp), ink)
                                        }
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            SiteIcon("check", null, Modifier.size(16.dp), BrandInk)
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        workId = profile.id
                                        workMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            CatalogGroup("Orario") {
                CatalogToggle("Tutto il giorno", allDay) { allDay = it }
                AnimatedVisibility(
                    visible = !allDay,
                    enter = expandVertically(spring()) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CatalogTimeField("Inizio", start, { start = it }, Modifier.weight(1f))
                        CatalogTimeField("Fine", end, { end = it }, Modifier.weight(1f))
                    }
                }
            }

            AnimatedVisibility(!allDay) {
                CatalogGroup("Pausa") {
                    CatalogToggle("Sottrai la pausa", hasBreak) { hasBreak = it }
                    AnimatedVisibility(hasBreak) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                CatalogTimeField(
                                    label = "Dalle",
                                    value = breakStart,
                                    change = { breakStart = it },
                                    modifier = Modifier.weight(1f),
                                    pickerTitle = "Inizio pausa",
                                )
                                CatalogField(
                                    label = "Minuti",
                                    value = pause,
                                    change = { pause = it.filter(Char::isDigit).take(3) },
                                    modifier = Modifier.weight(1f),
                                    numeric = true,
                                    error = !validPause,
                                    centered = true,
                                )
                            }
                            val shiftTone = shiftColorStyle(
                                source = Color(color),
                                dark = TurniThemeState.dark,
                                backdrop = ObsidianSurface,
                                definitionId = shift.id,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 30, 60).forEach { value ->
                                    val active = pauseInt == value
                                    val pillScale = remember { Animatable(1f) }
                                    val pillScope = rememberCoroutineScope()
                                    var pillJob by remember { mutableStateOf<Job?>(null) }
                                    var wasActive by remember { mutableStateOf(active) }
                                    LaunchedEffect(active) {
                                        if (active && !wasActive) {
                                            pillJob?.cancel()
                                            pillJob = pillScope.launch { pillScale.popSpring(peak = 1.06f, dip = 0.94f) }
                                        }
                                        wasActive = active
                                    }
                                    val pillFill by animateColorAsState(
                                        targetValue = if (active) shiftTone.codeFill else ObsidianSurfaceContainer,
                                        animationSpec = tween(180),
                                        label = "breakPresetFill",
                                    )
                                    val pillInk by animateColorAsState(
                                        targetValue = if (active) shiftTone.codeInk else TextPrimary,
                                        animationSpec = tween(180),
                                        label = "breakPresetInk",
                                    )
                                    val pillShape = RoundedCornerShape(12.dp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .graphicsLayer {
                                                scaleX = pillScale.value
                                                scaleY = pillScale.value
                                            }
                                            .clip(pillShape)
                                            .background(
                                                if (active) pastelSheenBrush(pillFill, pillInk)
                                                else SolidColor(pillFill),
                                            )
                                            .tactileNoFlashClick {
                                                pillJob?.cancel()
                                                pillJob = pillScope.launch { pillScale.popSpring(peak = 1.06f, dip = 0.94f) }
                                                pause = value.toString()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "$value min",
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = pillInk,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CatalogGroup("Colore") {
                UniversalColorPickerRow(color, shiftId = shift.id) { color = it }
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (existing) {
                TonalIconButton(
                    icon = "copy",
                    contentDescription = "Duplica turno",
                    onClick = {
                        val base = code.ifBlank { "TUR" }
                        var suffix = 2
                        var candidate: String
                        do {
                            val tail = (suffix++).toString()
                            candidate = base.take((4 - tail.length).coerceAtLeast(0)) + tail
                        } while (ShiftRepository.shiftDefinitions.any { it.shortCode.equals(candidate, true) })
                        val copy = draft().copy(
                            id = UUID.randomUUID().toString(),
                            name = "$name copia",
                            shortCode = candidate.take(4),
                        )
                        finish { onSave(copy) }
                    },
                    size = 46.dp,
                    cornerRadius = 14.dp,
                    fill = ObsidianSurfaceContainer,
                    tint = TextPrimary,
                    iconSize = 20.dp,
                )
                TonalIconButton(
                    icon = "trash-2",
                    contentDescription = "Elimina turno",
                    onClick = { deleteConfirm = true },
                    size = 46.dp,
                    cornerRadius = 14.dp,
                    fill = Color(currentTurniPalette.danger.fill),
                    tint = Color(currentTurniPalette.danger.ink),
                    iconSize = 20.dp,
                )
            }
            CloudButton(
                label = "Salva turno",
                icon = "check",
                onClick = { if (valid) finish { onSave(draft()) } },
                enabled = valid,
                modifier = Modifier.weight(1f),
            )
        }
        if (deleteConfirm) {
            CatalogDeleteDialog("Eliminare questo turno?", { deleteConfirm = false }) {
                deleteConfirm = false
                finish { onDelete(shift.id) }
            }
        }
    }
}

@Composable
private fun CatalogTimeField(
    label: String,
    value: String,
    change: (String) -> Unit,
    modifier: Modifier = Modifier,
    pickerTitle: String = label,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val fieldInteraction = remember { MutableInteractionSource() }
    val isHovered by fieldInteraction.collectIsHoveredAsState()
    val haptic = LocalHapticFeedback.current
    val clockPhase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var clockJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            clockJob = scope.replayCatalogMotion(clockPhase, clockJob, durationMillis = 520)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianSurfaceContainer)
                .hoverable(interactionSource = fieldInteraction)
                .clickable(
                    interactionSource = fieldInteraction,
                    indication = null,
                    role = Role.Button,
                ) {
                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    clockJob = scope.replayCatalogMotion(clockPhase, clockJob, durationMillis = 520)
                    showTimePicker = true
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            SiteIconMotion(
                name = "clock",
                description = null,
                modifier = Modifier.size(14.dp),
                tint = TextSecondary,
            ) { clockPhase.value }
            Spacer(Modifier.width(6.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-15, 15).forEach { delta ->
                val stepLabel = if (delta < 0) "−15" else "+15"
                val description = if (delta < 0) "Anticipa $label di 15 minuti" else "Posticipa $label di 15 minuti"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSurfaceContainer)
                        .semantics {
                            contentDescription = description
                            role = Role.Button
                        }
                        .tactileNoFlashClick {
                            clockJob = scope.replayCatalogMotion(clockPhase, clockJob, durationMillis = 420)
                            runCatching { LocalTime.parse(value).plusMinutes(delta.toLong()) }
                                .onSuccess { change(it.format(catalogTimeFormatter)) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stepLabel,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        TurniTimePickerDialog(
            title = pickerTitle,
            initialTime = value,
            onDismiss = { showTimePicker = false },
            onConfirm = { chosen ->
                showTimePicker = false
                change(chosen)
            },
        )
    }
}

@Composable
private fun CatalogDeleteDialog(title: String, dismiss: () -> Unit, confirm: () -> Unit) {
    val trashPhase = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        trashPhase.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        trashPhase.snapTo(0f)
    }
    TurniSafeBottomDialog(onClose = dismiss) { finish ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(currentTurniPalette.danger.fill)),
                contentAlignment = Alignment.Center,
            ) {
                SiteIconMotion("trash-2", null, Modifier.size(20.dp), ColorNegative) {
                    trashPhase.value
                }
            }
            Text(
                text = title,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            TonalIconButton(
                icon = "x",
                contentDescription = "Chiudi",
                onClick = { finish(dismiss) },
                size = 34.dp,
                cornerRadius = 11.dp,
                fill = Color(currentTurniPalette.danger.fill),
                tint = ColorNegative,
                iconSize = 17.dp,
            )
        }
        Text(
            text = "Questa operazione non può essere annullata.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CatalogButton("Annulla", { finish(dismiss) }, Modifier.weight(1f))
            CloudButton(
                label = "Elimina",
                icon = "trash-2",
                onClick = { finish(confirm) },
                destructive = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun RotationMatrixEditorScreen(
    rotation: ShiftRotation,
    onDismiss: () -> Unit,
    onSave: (ShiftRotation) -> Unit,
    @Suppress("UNUSED_PARAMETER") onApply: (ShiftRotation) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    availableDefinitions: List<ShiftDefinition> = ShiftRepository.shiftDefinitions.toList(),
    allowDefault: Boolean = true,
) {
    var title by remember(rotation.id) { mutableStateOf(rotation.name) }
    val sequence = remember(rotation.id) {
        mutableStateListOf<String>().apply { addAll(rotation.daySequence.ifEmpty { listOf("") }) }
    }
    var brush by remember { mutableStateOf(availableDefinitions.firstOrNull()?.id ?: "") }
    var favorite by remember(rotation.id) { mutableStateOf(ShiftRepository.defaultRotationId == rotation.id) }
    val starMotion = remember { Animatable(0f) }
    val starScope = rememberCoroutineScope()
    var starJob by remember { mutableStateOf<Job?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val existing = ShiftRepository.rotations.any { it.id == rotation.id }

    CatalogEditorSheet(
        title = if (existing) "Modifica rotazione" else "Nuova rotazione",
        onDismiss = onDismiss,
        heightFraction = 1f,
        subtitle = "Scegli un turno e componi la sequenza",
        fullPage = true,
    ) { finish ->
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CatalogGroup("Nome della rotazione", outlined = true) {
                CatalogField(
                    label = "Nome",
                    value = title,
                    change = { title = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Es. Ciclo 6×6 · 28 giorni",
                )
            }

            // Hero cycle length card with tabular day counter, week pill, and compact steppers.
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ObsidianSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Lunghezza del ciclo",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "${sequence.size}",
                                fontSize = 32.sp,
                                lineHeight = 36.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.8).sp,
                                color = TextPrimary,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                            )
                            Text(
                                text = if (sequence.size == 1) "giorno" else "giorni",
                                fontSize = 13.sp,
                                lineHeight = 17.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        val weeks = (sequence.size + 6) / 7
                        val weeksText = if (weeks == 1) "1 settimana" else "$weeks settimane"
                        PastelPill(
                            text = weeksText,
                            color = ExpressivePastel.LAVENDER.sourceColor,
                            icon = "calendar",
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(-7 to "−7g", -1 to "−1g", 1 to "+1g", 7 to "+7g").forEach { (delta, label) ->
                            val enabled = if (delta < 0) sequence.size > 1 else sequence.size < 70
                            val contentAlpha = if (enabled) 1f else 0.4f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianSurfaceContainer)
                                    .alpha(contentAlpha)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = when (delta) {
                                            -7 -> "Riduci di 7 giorni"
                                            -1 -> "Riduci di 1 giorno"
                                            1 -> "Aumenta di 1 giorno"
                                            else -> "Aumenta di 7 giorni"
                                        }
                                    }
                                    .tactileNoFlashClick(enabled = enabled) {
                                        val target = (sequence.size + delta).coerceIn(1, 70)
                                        while (sequence.size < target) sequence.add("")
                                        while (sequence.size > target) sequence.removeAt(sequence.lastIndex)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                )
                            }
                        }
                    }
                }
            }

            CatalogStep(1, "Scegli il turno")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CatalogPaintChip("", "", ObsidianSurfaceContainer, brush.isEmpty()) { brush = "" }
                availableDefinitions.forEach { def ->
                    CatalogPaintChip(def.shortCode, def.name, def.color, brush == def.id) { brush = def.id }
                }
            }

            CatalogStep(2, "Tocca i giorni da compilare")
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ObsidianSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(22.dp))
                        listOf("L", "M", "M", "G", "V", "S", "D").forEachIndexed { dayIndex, day ->
                            val weekend = dayIndex >= 5
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (weekend) CalendarWeekend else Color.Transparent)
                                    .padding(vertical = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = day,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    color = if (weekend) BrandInk else TextSecondary,
                                )
                            }
                        }
                    }
                    for (week in 0 until (sequence.size + 6) / 7) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "S${week + 1}",
                                modifier = Modifier.width(22.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                            )
                            for (day in 0..6) {
                                val index = week * 7 + day
                                val def = sequence.getOrNull(index)?.let { id ->
                                    availableDefinitions.firstOrNull { definition -> definition.id == id }
                                }
                                if (index >= sequence.size) {
                                    Spacer(Modifier.weight(1f).height(40.dp))
                                } else {
                                    val weekend = day >= 5
                                    val emptyColor = if (weekend) CalendarWeekend else ObsidianSurfaceContainer
                                    val targetColor = if (def != null) {
                                        shiftDisplayColor(def.color, definitionId = def.id)
                                    } else {
                                        emptyColor
                                    }
                                    val fill by animateColorAsState(
                                        targetValue = targetColor,
                                        animationSpec = spring(1f, 420f),
                                        label = "matrixCellFill",
                                    )
                                    val cellShape = RoundedCornerShape(9.dp)
                                    val ink = if (def != null) {
                                        catalogInk(fill)
                                    } else if (weekend) {
                                        BrandInk.copy(alpha = 0.72f)
                                    } else {
                                        TextSecondary
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(cellShape)
                                            .background(
                                                if (def != null) pastelSheenBrush(fill, ink)
                                                else SolidColor(fill),
                                            )
                                            .then(
                                                if (index == sequence.lastIndex) {
                                                    Modifier.border(1.5.dp, ObsidianPrimary, cellShape)
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .tactileNoFlashClick { sequence[index] = brush },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = def?.shortCode?.take(4) ?: "—",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ink,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (allowDefault) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = ObsidianSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val toggleFavorite = { next: Boolean ->
                        favorite = next
                        starJob = starScope.replayCatalogMotion(starMotion, starJob, durationMillis = 680)
                        Unit
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tactileNoFlashClick { toggleFavorite(!favorite) }
                            .padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val badgeFill by animateColorAsState(
                            targetValue = if (favorite) Color(currentTurniPalette.warning.fill) else ObsidianSurfaceContainer,
                            animationSpec = tween(200),
                            label = "defaultRotationStarBg",
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(badgeFill),
                            contentAlignment = Alignment.Center,
                        ) {
                            CatalogDefaultStar(favorite, "Rotazione predefinita", Modifier.size(21.dp), starMotion.value)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Rotazione predefinita",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                AnimatedVisibility(visible = favorite) {
                                    PastelPill(
                                        text = "Attiva",
                                        color = ExpressivePastel.AMBER.sourceColor,
                                        solid = true,
                                    )
                                }
                            }
                            Text(
                                text = "Mostrata per prima nella modifica rapida del calendario",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                        TurniSwitch(
                            checked = favorite,
                            onCheckedChange = toggleFavorite,
                            activeColor = ExpressivePastel.AMBER.style().codeFill,
                        )
                    }
                }
            }
            if (existing && onDelete != null) {
                CloudButton(
                    label = "Elimina rotazione",
                    icon = "trash-2",
                    onClick = { deleteConfirm = true },
                    destructive = true,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CloudButton(
                label = "Salva",
                icon = "check",
                onClick = {
                    val saved = rotation.copy(
                        name = title.trim(),
                        totalDays = sequence.size,
                        daySequence = sequence.toList(),
                    )
                    finish {
                        onSave(saved)
                        if (allowDefault) {
                            if (favorite) ShiftRepository.setDefaultRotation(saved.id)
                            else if (ShiftRepository.defaultRotationId == saved.id) ShiftRepository.setDefaultRotation("")
                        }
                    }
                },
                enabled = title.isNotBlank() && sequence.any { it.isNotEmpty() },
            )
        }
        if (deleteConfirm && onDelete != null) {
            CatalogDeleteDialog("Eliminare la rotazione?", { deleteConfirm = false }) {
                deleteConfirm = false
                finish { onDelete(rotation.id) }
            }
        }
    }
}

@Composable
private fun CatalogPaintChip(
    code: String,
    name: String,
    color: Color,
    selected: Boolean,
    action: () -> Unit,
) {
    val font = remember { Animatable(12f) }
    val phase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isEraser = code.isEmpty()
    val dark = TurniThemeState.dark
    val chipStyle = remember(color, dark, isEraser) {
        if (isEraser) null else shiftColorStyle(color, dark, ObsidianSurfaceContainer)
    }
    val targetBg = when {
        isEraser && selected -> lerp(ObsidianSurfaceContainer, ObsidianPrimary, 0.18f)
        !isEraser && selected -> lerp(ObsidianSurfaceContainer, chipStyle!!.codeFill, 0.22f)
        else -> ObsidianSurfaceContainer
    }
    val targetRing = when {
        !selected -> Color.Transparent
        isEraser -> BrandInk
        else -> harmonizedSelectionRing(color, ObsidianSurfaceContainer)
    }
    val bg by animateColorAsState(targetBg, tween(180), label = "paintChipBg")
    val ring by animateColorAsState(targetRing, tween(180), label = "paintChipRing")
    val chipShape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .width(if (isEraser) 68.dp else 78.dp)
            .height(68.dp)
            .clip(chipShape)
            .background(bg)
            .border(2.dp, ring, chipShape)
            .semantics {
                this.selected = selected
                this.role = Role.RadioButton
            }
            .tactileNoFlashClick {
                scope.launch {
                    font.snapTo(13.5f)
                    font.animateTo(12f, spring(0.8f, 300f))
                }
                scope.launch {
                    phase.snapTo(0f)
                    phase.animateTo(1f, tween(620))
                    phase.snapTo(0f)
                }
                action()
            }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isEraser) {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                SiteIcon(
                    name = "eraser",
                    contentDescription = "Gomma",
                    modifier = Modifier.size(22.dp),
                    tint = if (selected) BrandInk else TextPrimary,
                    progress = phase.value,
                )
            }
            Text(
                text = "Gomma",
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) BrandInk else TextSecondary,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        } else {
            val badgeFill = chipStyle!!.codeFill
            val badgeInk = chipStyle.codeInk
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(pastelSheenBrush(badgeFill, badgeInk)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = code.take(4),
                    color = badgeInk,
                    fontSize = font.value.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (selected) TextPrimary else TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CatalogStep(number: Int, label: String) {
    Row(
        modifier = Modifier.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ObsidianPrimaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BrandInk,
            )
        }
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun filterOptionalPositiveIntInput(raw: String, range: IntRange): String? {
    if (raw.isEmpty()) return ""
    if (!raw.all { it.isDigit() }) return null
    val parsed = raw.toIntOrNull() ?: return null
    return if (parsed in range) parsed.toString() else null
}

@Composable
fun WorkProfileEditorScreen(
    profile: WorkProfile,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (WorkProfile) -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var department by remember(profile.id) { mutableStateOf(profile.companyOrDepartment) }
    var icon by remember(profile.id) { mutableStateOf(profile.iconKey) }
    var color by remember(profile.id) { mutableStateOf(profile.colorHex) }
    var active by remember(profile.id) { mutableStateOf(profile.isDefault) }
    var hoursText by remember(profile.id, isNew) {
        mutableStateOf(if (isNew) "" else profile.weeklyHours.takeIf { it > 0 }?.toString().orEmpty())
    }
    var restText by remember(profile.id, isNew) {
        mutableStateOf(if (isNew) "" else profile.minRestHoursBetweenShifts.takeIf { it > 0 }?.toString().orEmpty())
    }
    var maxConsecutiveText by remember(profile.id, isNew) {
        mutableStateOf(if (isNew) "" else profile.maxConsecutiveWorkDays.takeIf { it > 0 }?.toString().orEmpty())
    }
    var lastHoursText by remember(profile.id, isNew) { mutableStateOf(hoursText) }
    var lastRestText by remember(profile.id, isNew) { mutableStateOf(restText) }
    var lastMaxConsecutiveText by remember(profile.id, isNew) { mutableStateOf(maxConsecutiveText) }
    var controlsEnabled by remember(profile.id, isNew) {
        mutableStateOf(
            !isNew && (
                profile.weeklyHours > 0 ||
                    profile.minRestHoursBetweenShifts > 0 ||
                    profile.maxConsecutiveWorkDays > 0
                ),
        )
    }
    var controlsExpanded by remember(profile.id, isNew) { mutableStateOf(false) }
    val parsedHours = if (controlsEnabled) (hoursText.toIntOrNull()?.coerceIn(1, 60) ?: 0) else 0
    val parsedRest = if (controlsEnabled) (restText.toIntOrNull()?.coerceIn(1, 24) ?: 0) else 0
    val parsedConsecutive = if (controlsEnabled) (maxConsecutiveText.toIntOrNull()?.coerceIn(1, 14) ?: 0) else 0
    var displayHours by remember(profile.id, isNew) { mutableIntStateOf(parsedHours) }
    if (parsedHours > 0) displayHours = parsedHours
    var displayRest by remember(profile.id, isNew) { mutableIntStateOf(parsedRest) }
    if (parsedRest > 0) displayRest = parsedRest
    var displayConsecutive by remember(profile.id, isNew) { mutableIntStateOf(parsedConsecutive) }
    if (parsedConsecutive > 0) displayConsecutive = parsedConsecutive
    var documentName by remember(profile.id) { mutableStateOf(profile.ccnlDocumentName) }
    var documentUri by remember(profile.id) { mutableStateOf(profile.ccnlDocumentUri) }
    var documentError by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var stagedDocument by remember { mutableStateOf<File?>(null) }
    val heroIconPhase = remember { Animatable(0f) }
    var previousHeroIcon by remember(profile.id) { mutableStateOf(icon) }
    LaunchedEffect(icon) {
        if (icon != previousHeroIcon) {
            previousHeroIcon = icon
            heroIconPhase.snapTo(0f)
            heroIconPhase.animateTo(1f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
            heroIconPhase.snapTo(0f)
        }
    }
    DisposableEffect(Unit) { onDispose { stagedDocument?.delete() } }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                var title = "Documento CCNL.pdf"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) title = cursor.getString(index)
                }
                val temp = File.createTempFile("ccnl-draft-", ".pdf", context.cacheDir)
                try {
                    val input = context.contentResolver.openInputStream(uri) ?: error("Documento non disponibile")
                    input.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                    check(temp.length() > 0) { "Il documento è vuoto" }
                    stagedDocument?.delete()
                    stagedDocument = temp
                    documentName = title
                    documentUri = temp.absolutePath
                    documentError = null
                } catch (error: Exception) {
                    temp.delete()
                    throw error
                }
            }.onFailure { documentError = "Impossibile aprire il documento. Riprova." }
        }
    }

    val iconKeys = listOf("work", "business", "people", "computer", "factory", "hospital", "shipping", "transit")
    val workTone = shiftColorStyle(Color(color), TurniThemeState.dark, ObsidianSurface)

    CatalogEditorSheet(
        title = if (isNew) "Nuovo lavoro" else "Modifica lavoro",
        onDismiss = onDismiss,
        heightFraction = 1f,
        subtitle = "Calendario e impostazioni del profilo",
        fullPage = true,
    ) { finish ->
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Live Hero Preview Card with 50.dp PastelBadge, animated icon, and live PastelPills.
            val heroFill = lerp(ObsidianSurface, workTone.codeFill, if (TurniThemeState.dark) 0.14f else 0.22f)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = heroFill,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PastelBadge(
                            color = Color(color),
                            size = 50.dp,
                            cornerRadius = 15.dp,
                        ) { ink ->
                            CatalogWorkIconMotion(icon, Modifier.size(25.dp), ink) {
                                heroIconPhase.value
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = name.ifBlank { "Il tuo lavoro" },
                                color = TextPrimary,
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = department.ifBlank { "Un calendario dedicato" },
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val hasAnyControl = parsedHours > 0 || parsedRest > 0 || parsedConsecutive > 0
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AnimatedVisibility(visible = parsedHours > 0) {
                            PastelPill(
                                text = "${displayHours}h/sett",
                                color = Color(color),
                                icon = "clock",
                            )
                        }
                        AnimatedVisibility(visible = parsedRest > 0) {
                            PastelPill(
                                text = "Riposo ${displayRest}h",
                                color = ExpressivePastel.MINT.sourceColor,
                            )
                        }
                        AnimatedVisibility(visible = parsedConsecutive > 0) {
                            PastelPill(
                                text = "Max $displayConsecutive di fila",
                                color = ExpressivePastel.AMBER.sourceColor,
                                icon = "calendar",
                            )
                        }
                        AnimatedVisibility(visible = !hasAnyControl) {
                            PastelPill(
                                text = "Nessun limite orario",
                                color = ExpressivePastel.PEARL.sourceColor,
                            )
                        }
                    }
                }
            }

            CatalogGroup("Identità", outlined = true) {
                CatalogField(
                    label = "Nome lavoro",
                    value = name,
                    change = { name = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                    placeholder = "Es. Impianto Nord",
                )
                CatalogField(
                    label = "Reparto o settore",
                    value = department,
                    change = { department = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                    placeholder = "Es. Manutenzione, Reparto A",
                )
                Text(
                    text = "Icona del lavoro",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                )
                iconKeys.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { key ->
                            val isSelected = icon == key
                            val tileScale = remember { Animatable(1f) }
                            val tileIconPhase = remember { Animatable(0f) }
                            val tileScope = rememberCoroutineScope()
                            var tileScaleJob by remember { mutableStateOf<Job?>(null) }
                            var tileIconJob by remember { mutableStateOf<Job?>(null) }
                            var wasSelected by remember { mutableStateOf(isSelected) }
                            LaunchedEffect(isSelected) {
                                if (isSelected && !wasSelected) {
                                    tileScaleJob?.cancel()
                                    tileScaleJob = tileScope.launch { tileScale.popSpring(peak = 1.07f, dip = 0.92f) }
                                    tileIconJob = tileScope.replayCatalogMotion(
                                        tileIconPhase,
                                        tileIconJob,
                                        durationMillis = 480,
                                    )
                                }
                                wasSelected = isSelected
                            }
                            val tileFill by animateColorAsState(
                                targetValue = if (isSelected) workTone.codeFill else ObsidianSurfaceContainer,
                                animationSpec = tween(180),
                                label = "workIconFill",
                            )
                            val tileInk by animateColorAsState(
                                targetValue = if (isSelected) workTone.codeInk else TextSecondary,
                                animationSpec = tween(180),
                                label = "workIconInk",
                            )
                            val activeRing = harmonizedSelectionRing(Color(color), ObsidianSurface)
                            val ringColor by animateColorAsState(
                                targetValue = if (isSelected) activeRing else Color.Transparent,
                                animationSpec = tween(180),
                                label = "workIconRing",
                            )
                            val ringInset by animateDpAsState(
                                targetValue = if (isSelected) 4.dp else 0.dp,
                                animationSpec = tween(180),
                                label = "workIconInset",
                            )
                            val iconLabel = when (key) {
                                "work" -> "Valigetta"
                                "business" -> "Edificio"
                                "people" -> "Persone"
                                "computer" -> "Computer"
                                "factory" -> "Strumenti"
                                "hospital" -> "Salute"
                                "shipping" -> "Trasporto"
                                else -> "Treno"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .graphicsLayer {
                                        scaleX = tileScale.value
                                        scaleY = tileScale.value
                                    }
                                    .clip(RoundedCornerShape(15.dp))
                                    .border(2.dp, ringColor, RoundedCornerShape(15.dp))
                                    .padding(ringInset)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) pastelSheenBrush(tileFill, tileInk)
                                        else SolidColor(tileFill),
                                    )
                                    .semantics {
                                        contentDescription = iconLabel
                                        selected = isSelected
                                        role = Role.RadioButton
                                    }
                                    .tactileNoFlashClick {
                                        tileScaleJob?.cancel()
                                        tileScaleJob = tileScope.launch {
                                            tileScale.popSpring(peak = 1.07f, dip = 0.92f)
                                        }
                                        tileIconJob = tileScope.replayCatalogMotion(
                                            tileIconPhase,
                                            tileIconJob,
                                            durationMillis = 480,
                                        )
                                        icon = key
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                CatalogWorkIconMotion(key, Modifier.size(22.dp), tileInk) {
                                    tileIconPhase.value
                                }
                            }
                        }
                    }
                }
            }

            CatalogGroup("Colore del lavoro", outlined = true) {
                UniversalColorPickerRow(color, showNames = true) { color = it }
            }

            val summaryParts = buildList {
                if (parsedHours > 0) add("${parsedHours}h/sett")
                if (parsedRest > 0) add("Riposo ${parsedRest}h")
                if (parsedConsecutive > 0) add("Max $parsedConsecutive di fila")
            }
            val controlsSummary = if (summaryParts.isNotEmpty()) {
                summaryParts.joinToString(" · ")
            } else {
                "Facoltativi · nessun conteggio"
            }
            val chevronAngle by animateFloatAsState(
                targetValue = if (controlsEnabled && controlsExpanded) 180f else 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
                label = "workControlsChevron",
            )
            val toggleControlsGroup: (Boolean) -> Unit = { enabled ->
                if (enabled) {
                    hoursText = lastHoursText
                    restText = lastRestText
                    maxConsecutiveText = lastMaxConsecutiveText
                    controlsEnabled = true
                    controlsExpanded = true
                } else {
                    lastHoursText = hoursText
                    lastRestText = restText
                    lastMaxConsecutiveText = maxConsecutiveText
                    hoursText = ""
                    restText = ""
                    maxConsecutiveText = ""
                    controlsEnabled = false
                    controlsExpanded = false
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ObsidianSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics {
                                stateDescription = if (controlsEnabled && controlsExpanded) {
                                    "Aperto"
                                } else if (controlsEnabled) {
                                    "Chiuso"
                                } else {
                                    "Disattivato"
                                }
                            }
                            .tactileNoFlashClick {
                                if (controlsEnabled) {
                                    controlsExpanded = !controlsExpanded
                                } else {
                                    toggleControlsGroup(true)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Orario e controlli",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = controlsSummary,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SiteIcon(
                            name = "chevron-down",
                            contentDescription = if (controlsEnabled && controlsExpanded) {
                                "Comprimi controlli orario"
                            } else {
                                "Espandi controlli orario"
                            },
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(chevronAngle),
                            tint = TextSecondary,
                        )
                        TurniSwitch(
                            checked = controlsEnabled,
                            onCheckedChange = toggleControlsGroup,
                        )
                    }

                    AnimatedVisibility(
                        visible = controlsEnabled && controlsExpanded,
                        enter = expandVertically(spring()) + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SettingsDivider()
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CatalogField(
                                    label = "Ore settimanali",
                                    value = hoursText,
                                    change = { raw ->
                                        filterOptionalPositiveIntInput(raw, 1..60)?.let { hoursText = it }
                                    },
                                    modifier = Modifier.weight(1f),
                                    numeric = true,
                                    height = 44.dp,
                                    centered = true,
                                    placeholder = "—",
                                )
                                CatalogField(
                                    label = "Riposo minimo · ore",
                                    value = restText,
                                    change = { raw ->
                                        filterOptionalPositiveIntInput(raw, 1..24)?.let { restText = it }
                                    },
                                    modifier = Modifier.weight(1f),
                                    numeric = true,
                                    height = 44.dp,
                                    centered = true,
                                    placeholder = "—",
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CatalogField(
                                    label = "Turni consecutivi massimi",
                                    value = maxConsecutiveText,
                                    change = { raw ->
                                        filterOptionalPositiveIntInput(raw, 1..14)?.let { maxConsecutiveText = it }
                                    },
                                    modifier = Modifier.weight(1f),
                                    numeric = true,
                                    height = 44.dp,
                                    centered = true,
                                    placeholder = "—",
                                )
                                TonalIconButton(
                                    icon = "minus",
                                    contentDescription = "Riduci turni consecutivi massimi",
                                    onClick = {
                                        val current = maxConsecutiveText.toIntOrNull() ?: 0
                                        maxConsecutiveText = if (current <= 1) "" else (current - 1).coerceIn(1, 14).toString()
                                    },
                                    size = 44.dp,
                                    cornerRadius = 14.dp,
                                    fill = ObsidianSurfaceContainer,
                                    tint = TextPrimary,
                                    iconSize = 16.dp,
                                    enabled = parsedConsecutive > 0,
                                )
                                TonalIconButton(
                                    icon = "plus",
                                    contentDescription = "Aumenta turni consecutivi massimi",
                                    onClick = {
                                        val current = maxConsecutiveText.toIntOrNull() ?: 0
                                        val next = if (current <= 0) 1 else (current + 1).coerceIn(1, 14)
                                        maxConsecutiveText = next.toString()
                                    },
                                    size = 44.dp,
                                    cornerRadius = 14.dp,
                                    fill = ObsidianSurfaceContainer,
                                    tint = TextPrimary,
                                    iconSize = 16.dp,
                                    enabled = parsedConsecutive < 14,
                                )
                            }
                            Text(
                                text = "Compila solo ciò che vuoi monitorare; lascia vuoto per disattivare.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }
            }

            CatalogGroup("Documento CCNL · facoltativo", outlined = true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianSurfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (documentUri != null) {
                        PastelIconBadge(
                            pastel = ExpressivePastel.CORAL,
                            icon = "file-text",
                            size = 38.dp,
                            iconSize = 18.dp,
                        )
                    } else {
                        NeutralIconBadge(
                            icon = "file-text",
                            size = 38.dp,
                            cornerRadius = 12.dp,
                            iconSize = 18.dp,
                            fill = ObsidianSurfaceHigh,
                            tint = TextSecondary,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = documentName ?: "Nessun documento allegato",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (documentUri != null) {
                                "PDF pronto per la consultazione rapida"
                            } else {
                                "Allega il tuo PDF per averlo sempre a portata di tap"
                            },
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
                if (documentUri == null) {
                    CatalogButton(
                        label = "Scegli PDF",
                        action = { documentPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = "upload",
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatalogActionButton(
                            label = "Apri CCNL",
                            action = {
                                openWorkProfilePdf(
                                    context,
                                    profile.copy(ccnlDocumentUri = documentUri, ccnlDocumentName = documentName),
                                )
                            },
                            fillColor = ObsidianPrimaryContainer,
                            inkColor = Color(currentTurniPalette.onPrimaryContainer),
                            modifier = Modifier.fillMaxWidth(),
                            icon = "book-open",
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CatalogButton(
                                label = "Sostituisci",
                                action = { documentPicker.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.weight(1f),
                                icon = "upload",
                            )
                            CatalogActionButton(
                                label = "Rimuovi",
                                action = {
                                    stagedDocument?.delete()
                                    stagedDocument = null
                                    documentName = null
                                    documentUri = null
                                },
                                fillColor = Color(currentTurniPalette.danger.fill),
                                inkColor = Color(currentTurniPalette.danger.ink),
                                modifier = Modifier.weight(1f),
                                icon = "trash-2",
                            )
                        }
                    }
                }
                if (documentError != null) {
                    Text(documentError!!, color = ColorNegative, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }

            CatalogGroup("Calendario", outlined = true) {
                CatalogToggle("Imposta come lavoro attivo", active) { active = it }
            }

            if (!isNew && onDelete != null && ShiftRepository.workProfiles.size > 1) {
                CloudButton(
                    label = "Elimina lavoro",
                    icon = "trash-2",
                    onClick = { deleteConfirm = true },
                    destructive = true,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CloudButton(
                label = "Salva lavoro",
                icon = "check",
                onClick = {
                    runCatching {
                        var savedUri = documentUri
                        val destination = File(context.filesDir, "ccnl_${profile.id}.pdf")
                        stagedDocument?.let { staged ->
                            staged.copyTo(destination, overwrite = true)
                            savedUri = destination.absolutePath
                        }
                        if (savedUri == null && destination.exists()) {
                            destination.delete()
                        }
                        val saved = profile.copy(
                            name = name.trim(),
                            companyOrDepartment = department.trim(),
                            iconKey = icon,
                            colorHex = color,
                            enableAdvancedCalc = profile.enableAdvancedCalc,
                            isDefault = active,
                            ccnlCode = profile.ccnlCode,
                            ccnlName = profile.ccnlName,
                            weeklyHours = parsedHours,
                            minRestHoursBetweenShifts = parsedRest,
                            nightSurchargePercent = profile.nightSurchargePercent,
                            holidaySurchargePercent = profile.holidaySurchargePercent,
                            nightHolidaySurchargePercent = profile.nightHolidaySurchargePercent,
                            overtimeDaySurchargePercent = profile.overtimeDaySurchargePercent,
                            overtimeNightSurchargePercent = profile.overtimeNightSurchargePercent,
                            ccnlDocumentName = documentName,
                            ccnlDocumentUri = savedUri,
                            maxConsecutiveWorkDays = parsedConsecutive,
                        )
                        finish { onSave(saved) }
                    }.onFailure { documentError = "Impossibile salvare il documento. Riprova." }
                },
                enabled = name.isNotBlank(),
            )
        }

        if (deleteConfirm && onDelete != null) {
            CatalogDeleteDialog("Eliminare questo lavoro?", { deleteConfirm = false }) {
                deleteConfirm = false
                finish { onDelete(profile.id) }
            }
        }
    }
}

/** Filled gold is reserved for a default rotation; the unselected state stays outline. */
@Composable
internal fun CatalogDefaultStar(
    selected: Boolean,
    description: String,
    modifier: Modifier = Modifier.size(21.dp),
    progress: Float = 0f,
) {
    val popScale = remember { Animatable(1f) }
    var wasSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != wasSelected) {
            wasSelected = selected
            popScale.popSpring(peak = 1.14f, dip = 0.88f)
        }
    }
    if (selected) {
        val wave = kotlin.math.sin(progress.coerceIn(0f, 1f) * kotlin.math.PI).toFloat()
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = description,
            modifier = modifier.graphicsLayer {
                scaleX = (1f + .18f * wave) * popScale.value
                scaleY = scaleX
                rotationZ = 10f * kotlin.math.sin(progress * 2f * kotlin.math.PI).toFloat()
            },
            tint = Color(currentTurniPalette.warning.ink),
        )
    } else {
        SiteIcon(
            name = "star",
            contentDescription = description,
            modifier = modifier.graphicsLayer {
                scaleX = popScale.value
                scaleY = popScale.value
            },
            tint = TextSecondary,
            progress = progress,
        )
    }
}
