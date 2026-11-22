package com.turni.calendario.ui.screens

import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.turni.calendario.R
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.MagicalActionFeedbackHUD
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.BrandInk
import com.turni.calendario.ui.theme.ColorMalattia
import com.turni.calendario.ui.theme.ColorMattina
import com.turni.calendario.ui.theme.ColorNegative
import com.turni.calendario.ui.theme.ColorNotte
import com.turni.calendario.ui.theme.ColorPomeriggio
import com.turni.calendario.ui.theme.ColorRiposo
import com.turni.calendario.ui.theme.ColorSmontoNotte
import com.turni.calendario.ui.theme.ObsidianBackground
import com.turni.calendario.ui.theme.ObsidianBorderSubtle
import com.turni.calendario.ui.theme.ObsidianPrimary
import com.turni.calendario.ui.theme.ObsidianPrimaryContainer
import com.turni.calendario.ui.theme.ObsidianSurface
import com.turni.calendario.ui.theme.ObsidianSurfaceContainer
import com.turni.calendario.ui.theme.ObsidianSurfaceHighest
import com.turni.calendario.ui.theme.OnBrand
import com.turni.calendario.ui.theme.ShiftColorStyle
import com.turni.calendario.ui.theme.TextPrimary
import com.turni.calendario.ui.theme.TextSecondary
import com.turni.calendario.ui.theme.TurniThemeState
import com.turni.calendario.ui.theme.currentTurniPalette
import com.turni.calendario.ui.theme.readableChartTint
import com.turni.calendario.ui.theme.readableInk
import com.turni.calendario.ui.theme.shiftChartColor
import com.turni.calendario.ui.theme.shiftColorStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

/** Curated semantic pastel anchors mapped onto the app's ShiftTone palette. */
internal enum class ExpressivePastel(val sourceColor: Color) {
    AMBER(ColorMattina),          // 0xFFF2C46D -> MorningTone (Reperibilità, Mattina, Predefinita)
    CORAL(ColorPomeriggio),       // 0xFFEE9C9C -> AfternoonTone (Festività, PDF, Pomeriggio)
    LAVENDER(ColorNotte),         // 0xFFBAAEF6 -> NightTone (Backup, Rotazioni, Notte)
    MAUVE(Color(0xFFC6AAC4)),     // EveningTone (Sveglie, Sera)
    MINT(ColorSmontoNotte),       // 0xFF83CDB7 -> RecoveryTone (Statistiche, Stato attivo)
    SAGE(ColorMalattia),          // 0xFFB4D995 -> LeaveTone (Google Calendar, Ferie)
    SKY(Color(0xFF9EC4DE)),       // DaytimeTone (Aspetto/Layout, Import AI)
    PEARL(ColorRiposo),           // 0xFFAAB9CE -> RestTone (Neutro elegante, Riposo)
}

internal fun ExpressivePastel.style(
    dark: Boolean = TurniThemeState.dark,
    backdrop: Color = ObsidianSurface,
): ShiftColorStyle = shiftColorStyle(sourceColor, dark, backdrop)

/** Diagonal luminous sheen matching ReportMetric while preserving >= 4.5:1 contrast with [ink]. */
internal fun pastelSheenBrush(fill: Color, ink: Color): Brush = Brush.linearGradient(
    colors = listOf(
        readableChartTint(fill, Color.White, ink, 0.08f),
        fill,
        readableChartTint(fill, Color.Black, ink, 0.06f),
    ),
)

@Composable
internal fun PastelBadge(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    cornerRadius: Dp = 13.dp,
    definitionId: String? = null,
    content: @Composable BoxScope.(Color) -> Unit,
) {
    val dark = TurniThemeState.dark
    val themeStyle = TurniThemeState.style
    val backdrop = ObsidianSurface
    val style = remember(color, dark, themeStyle, backdrop, definitionId) {
        shiftColorStyle(color, dark, backdrop, definitionId = definitionId)
    }
    val brush = remember(style.codeFill, style.codeInk) {
        pastelSheenBrush(style.codeFill, style.codeInk)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        content(style.codeInk)
    }
}

@Composable
internal fun PastelIconBadge(
    pastel: ExpressivePastel,
    icon: String,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    PastelBadge(
        color = pastel.sourceColor,
        modifier = modifier,
        size = size,
        cornerRadius = (size.value * 0.31f).dp,
    ) { ink ->
        SiteIcon(
            name = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = ink,
            progress = progress,
        )
    }
}

@Composable
internal fun NeutralIconBadge(
    icon: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp = 20.dp,
    fill: Color = ObsidianSurfaceContainer,
    tint: Color = BrandInk,
    progress: Float = 0f,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        SiteIcon(
            name = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = tint,
            progress = progress,
        )
    }
}

@Composable
internal fun PastelPill(
    text: String,
    color: Color,
    solid: Boolean = false,
    icon: String? = null,
    modifier: Modifier = Modifier,
) {
    val dark = TurniThemeState.dark
    val themeStyle = TurniThemeState.style
    val surface = ObsidianSurface
    val container = ObsidianSurfaceContainer
    val tone = remember(color, dark, themeStyle, surface) {
        shiftColorStyle(color, dark, surface)
    }
    val targetFill = remember(solid, tone, container, dark) {
        if (solid) tone.codeFill else lerp(container, tone.codeFill, if (dark) 0.22f else 0.38f)
    }
    val targetInk = remember(solid, tone, targetFill, dark) {
        if (solid) tone.codeInk else readableInk(targetFill, if (dark) tone.codeFill else tone.codeInk)
    }
    val fill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = tween(durationMillis = 180),
        label = "pastelPillFill",
    )
    val ink by animateColorAsState(
        targetValue = targetInk,
        animationSpec = tween(durationMillis = 180),
        label = "pastelPillInk",
    )
    val backgroundBrush = remember(solid, fill, targetInk) {
        if (solid) pastelSheenBrush(fill, targetInk) else SolidColor(fill)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundBrush)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            SiteIcon(
                name = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = ink,
            )
        }
        Text(
            text = text,
            color = ink,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    accent: ExpressivePastel? = null,
) {
    val dark = TurniThemeState.dark
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (accent != null) {
            val dotColor = remember(accent, dark) { shiftChartColor(accent.sourceColor, dark) }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private fun CoroutineScope.replayIconMotion(
    phase: Animatable<Float, *>,
    previous: Job?,
    durationMillis: Int,
): Job {
    previous?.cancel()
    return launch {
        phase.snapTo(0f)
        phase.animateTo(1f, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
        phase.snapTo(0f)
    }
}

@Composable
private fun Modifier.tactilePressClickable(
    shape: Shape,
    enabled: Boolean,
    pressedScale: Float,
    role: Role,
    onClickLabel: String? = null,
    extraScale: () -> Float = { 1f },
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
        label = "tactilePressScale",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.075f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "tactilePressAlpha",
    )
    val haptic = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            val scale = minOf(animatedScale, extraScale())
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            if (animatedAlpha > 0f) {
                drawRect(ObsidianPrimary.copy(alpha = animatedAlpha))
            }
        }
        .clickable(
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
internal fun TonalIconButton(
    icon: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    cornerRadius: Dp = 12.dp,
    fill: Color = ObsidianSurfaceContainer,
    tint: Color = TextPrimary,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
) {
    val phase = remember { Animatable(0f) }
    val scaleBounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var replay by remember { mutableStateOf<Job?>(null) }
    var bounceJob by remember { mutableStateOf<Job?>(null) }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .tactilePressClickable(
                shape = shape,
                enabled = enabled,
                pressedScale = 0.94f,
                role = Role.Button,
                onClickLabel = contentDescription,
                extraScale = { scaleBounce.value },
            ) {
                replay = scope.replayIconMotion(phase, replay, durationMillis = 520)
                bounceJob?.cancel()
                bounceJob = scope.launch {
                    scaleBounce.snapTo(1f)
                    scaleBounce.animateTo(0.94f, tween(durationMillis = 70))
                    scaleBounce.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 480f),
                    )
                }
                onClick()
            }
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        SiteIconMotion(
            name = icon,
            description = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = tint.copy(alpha = if (enabled) 1f else 0.45f),
        ) {
            phase.value
        }
    }
}

@Composable
internal fun CloudFeatureScaffold(
    title: String,
    onDismiss: () -> Unit,
    feedback: ActionFeedback? = null,
    onDismissFeedback: () -> Unit = {},
    scrollResetKey: Any? = null,
    subtitle: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val scroll = rememberScrollState()
    LaunchedEffect(scrollResetKey) {
        if (scrollable) {
            scroll.scrollTo(0)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .statusBarsPadding()
            .let { if (bottomBar == null) it.navigationBarsPadding() else it },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TonalIconButton(
                    icon = "arrow-left",
                    contentDescription = "Indietro",
                    onClick = onDismiss,
                    size = 40.dp,
                    cornerRadius = 14.dp,
                    fill = ObsidianSurface,
                    tint = TextPrimary,
                    iconSize = 20.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 23.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (headerAction != null) {
                    headerAction()
                }
            }

            val bodyModifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .let { if (scrollable) it.verticalScroll(scroll) else it }
                .padding(horizontal = 16.dp)

            Column(
                modifier = bodyModifier,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
                if (scrollable) {
                    Spacer(Modifier.height(if (bottomBar == null) 24.dp else 12.dp))
                }
            }

            if (bottomBar != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianBackground)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    bottomBar()
                }
            }
        }
        MagicalActionFeedbackHUD(feedback, onDismissFeedback, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
internal fun CloudCard(
    title: String = "",
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accent: ExpressivePastel? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = TurniThemeState.dark
    Surface(
        shape = RoundedCornerShape(23.dp),
        color = ObsidianSurface,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title.isNotBlank() || !subtitle.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (title.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (accent != null) {
                                val dotColor = remember(accent, dark) {
                                    shiftChartColor(accent.sourceColor, dark)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor),
                                )
                            }
                            Text(
                                text = title,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
            content()
        }
    }
}

/**
 * Borderless sliding segmented bar with an animated pill and zero click-flash.
 *
 * Uses `indication = null` and 180ms color interpolation on each item's label/icon
 * so the foreground tint transitions in lockstep with the sliding pill.
 */
@Composable
internal fun SlidingSegmentBar(
    items: List<Pair<String, String?>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 46.dp,
    pillColor: Color = ObsidianPrimary,
    pillInk: Color = OnBrand,
) {
    if (items.isEmpty()) return
    val safeSelected = selectedIndex.coerceIn(0, items.lastIndex)
    val haptic = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale
    val trackShape = RoundedCornerShape(18.dp)
    val pillShape = RoundedCornerShape(14.dp)
    val trackPadding = 4.dp
    val animatedPillColor by animateColorAsState(
        targetValue = pillColor.copy(alpha = if (enabled) 1f else 0.5f),
        animationSpec = tween(durationMillis = 180),
        label = "slidingSegmentPillColor",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(trackShape)
            .background(ObsidianSurfaceContainer)
            .padding(trackPadding)
            .selectableGroup(),
    ) {
        val segmentWidth = maxWidth / items.size
        val isCompact = items.size >= 4 || segmentWidth < 96.dp
        val labelFontSize = when {
            items.size >= 4 && (segmentWidth < 86.dp || fontScale > 1.1f) -> 11.sp
            items.size >= 4 -> 11.5.sp
            else -> 12.sp
        }
        val iconSize = if (isCompact) 14.dp else 16.dp
        val iconGap = if (isCompact) 3.dp else 6.dp
        val horizontalPadding = if (isCompact) 3.dp else 6.dp
        val maxLabelLength = items.maxOf { it.first.length }
        val estimatedMaxLabelDp = (maxLabelLength * labelFontSize.value * 0.62f * fontScale).dp
        val showIcons = items.any { it.second != null } &&
            (estimatedMaxLabelDp + iconSize + iconGap + horizontalPadding * 2 <= segmentWidth)

        val pillOffset by animateDpAsState(
            targetValue = segmentWidth * safeSelected,
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMedium,
            ),
            label = "slidingSegmentPillOffset",
        )

        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(pillShape)
                .background(animatedPillColor),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, (label, iconKey) ->
                val isSelected = index == safeSelected
                val interactionSource = remember(index) { MutableInteractionSource() }
                val iconPhase = remember(index, iconKey) { Animatable(0f) }
                var wasSelected by remember(index, iconKey) { mutableStateOf(isSelected) }
                LaunchedEffect(isSelected, iconKey) {
                    if (isSelected && !wasSelected) {
                        iconPhase.snapTo(0f)
                        iconPhase.animateTo(1f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
                        iconPhase.snapTo(0f)
                    } else if (!isSelected && iconPhase.value != 0f) {
                        iconPhase.snapTo(0f)
                    }
                    wasSelected = isSelected
                }
                val targetInk = (if (isSelected) pillInk else TextSecondary)
                    .copy(alpha = if (enabled) 1f else 0.5f)
                val ink by animateColorAsState(
                    targetValue = targetInk,
                    animationSpec = tween(durationMillis = 180),
                    label = "slidingSegmentInk",
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(pillShape)
                        .semantics { selected = isSelected }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            role = Role.Tab,
                        ) {
                            if (enabled && index != safeSelected) {
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } catch (_: Exception) {
                                }
                                onSelect(index)
                            }
                        }
                        .padding(horizontal = horizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (showIcons && iconKey != null) {
                        SiteIconMotion(
                            name = iconKey,
                            description = null,
                            modifier = Modifier.size(iconSize),
                            tint = ink,
                        ) {
                            iconPhase.value
                        }
                        Spacer(Modifier.width(iconGap))
                    }
                    Text(
                        text = label,
                        color = ink,
                        fontSize = labelFontSize,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TurniSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = ObsidianPrimary,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val activeThumbColor = remember(activeColor) { readableInk(activeColor, OnBrand) }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 3.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 320f,
        ),
        label = "turniSwitchThumbOffset",
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) activeColor else ObsidianSurfaceHighest,
        animationSpec = tween(durationMillis = 200),
        label = "turniSwitchTrackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) activeThumbColor else TextSecondary,
        animationSpec = tween(durationMillis = 200),
        label = "turniSwitchThumbColor",
    )
    val thumbScale = remember { Animatable(1f) }
    var lastChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked != lastChecked) {
            lastChecked = checked
            thumbScale.snapTo(1f)
            thumbScale.animateTo(
                targetValue = 1.14f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 620f),
            )
            thumbScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
            )
        }
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { next ->
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (_: Exception) {
                    }
                    onCheckedChange(next)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 27.dp)
                .alpha(if (enabled) 1f else 0.48f)
                .clip(CircleShape)
                .background(trackColor),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(21.dp)
                    .graphicsLayer {
                        scaleX = thumbScale.value
                        scaleY = thumbScale.value
                    }
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}

@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.75.dp,
        color = ObsidianBorderSubtle.copy(alpha = 0.45f),
    )
}

@Composable
internal fun CloudButton(
    label: String,
    icon: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
) {
    val iconPhase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var iconJob by remember { mutableStateOf<Job?>(null) }
    val palette = currentTurniPalette
    val buttonShape = RoundedCornerShape(15.dp)
    val targetFill = when {
        destructive -> Color(palette.danger.fill)
        secondary -> ObsidianSurfaceContainer
        else -> ObsidianPrimary
    }
    val targetInk = when {
        destructive -> Color(palette.danger.ink)
        secondary -> TextPrimary
        else -> OnBrand
    }
    val fill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = tween(durationMillis = 180),
        label = "cloudButtonFill",
    )
    val ink by animateColorAsState(
        targetValue = targetInk,
        animationSpec = tween(durationMillis = 180),
        label = "cloudButtonInk",
    )
    val contentAlpha = if (enabled) 1f else 0.45f
    Surface(
        shape = buttonShape,
        color = fill,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .tactilePressClickable(
                shape = buttonShape,
                enabled = enabled,
                pressedScale = 0.98f,
                role = Role.Button,
            ) {
                iconJob = scope.replayIconMotion(iconPhase, iconJob, durationMillis = 420)
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            SiteIconMotion(icon, null, Modifier.size(18.dp), ink.copy(alpha = contentAlpha)) {
                iconPhase.value
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = ink.copy(alpha = contentAlpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Google branding stays recognizable on borderless Grafite tonal surfaces in both themes. */
@Composable
internal fun GoogleSignInButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val buttonShape = RoundedCornerShape(16.dp)
    Surface(
        color = ObsidianSurfaceContainer,
        shape = buttonShape,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(buttonShape)
            .bouncyClickable(enabled, onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.google_g),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (loading) "Collegamento…" else "Accedi con Google",
                color = TextPrimary.copy(alpha = if (enabled) 1f else 0.5f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun GoogleAccountCard(
    access: GoogleAccess,
    onFeedback: (ActionFeedback) -> Unit,
    enabled: Boolean = true,
    feature: GoogleFeature = GoogleFeature.PROFILE,
    explanation: String? = null,
    onConnected: ((String) -> Unit)? = null,
    connectedContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var showAccessDetails by remember { mutableStateOf(false) }
    var showAccountSheet by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (showAccessDetails) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
        label = "googleAccessChevron",
    )
    val detailsIconPhase = remember { Animatable(0f) }
    val detailsScope = rememberCoroutineScope()
    var detailsIconJob by remember { mutableStateOf<Job?>(null) }
    val profile = ShiftRepository.googleAccountProfile
    CloudCard(
        title = "Account Google",
        subtitle = if (profile == null) explanation?.takeIf { it.isNotBlank() } else null,
    ) {
        if (profile == null) {
            GoogleSignInButton(enabled && !access.busy, access.busy) {
                showAccountSheet = true
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurfaceContainer)
                    .bouncyClickable(enabled && !access.busy) {
                        showAccountSheet = true
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.google_g),
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(3.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = profile.displayName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = profile.email,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SiteIcon("chevron-right", null, Modifier.size(16.dp), TextSecondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { showAccountSheet = true },
                    enabled = enabled && !access.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = BrandInk),
                ) {
                    Text("Cambia account", color = BrandInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = {
                        access.disconnect {
                            onFeedback(ActionFeedback("Account Google scollegato"))
                        }
                    },
                    enabled = enabled && !access.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                ) {
                    Text("Disconnetti", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        AnimatedVisibility(
            visible = profile != null && connectedContent != null,
            enter = expandVertically(
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(tween(220)),
            exit = shrinkVertically(
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) + fadeOut(tween(160)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsDivider()
                connectedContent?.invoke(this)
            }
        }
        val detailsShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(detailsShape)
                .background(ObsidianSurfaceContainer)
                .bouncyClickable {
                    showAccessDetails = !showAccessDetails
                    detailsIconJob = detailsScope.replayIconMotion(detailsIconPhase, detailsIconJob, durationMillis = 480)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SiteIconMotion(
                name = "shield-alert",
                description = null,
                modifier = Modifier.size(16.dp),
                tint = BrandInk,
            ) {
                detailsIconPhase.value
            }
            Text(
                text = "Quali dati autorizzo?",
                color = BrandInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SiteIcon(
                name = "chevron-down",
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronAngle),
                tint = BrandInk,
            )
        }
        AnimatedVisibility(
            visible = showAccessDetails,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text = when (feature) {
                    GoogleFeature.DRIVE -> "Nome, email e spazio privato TurniSync su Drive. Gli altri file su Drive restano inaccessibili."
                    GoogleFeature.CALENDAR -> "Nome, email e lista calendari. Gestisce solo gli eventi creati da TurniSync."
                    else -> "Nome ed email dell’account scelto. La password resta gestita da Google."
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    if (showAccountSheet) {
        GoogleAccountSheet(
            access = access,
            feature = feature,
            onFeedback = onFeedback,
            onDismiss = { showAccountSheet = false },
            onConnected = onConnected,
        )
    }
}

/** Cohesive Grafite bottom sheet for choosing, switching, or disconnecting Google accounts. */
@Composable
internal fun GoogleAccountSheet(
    access: GoogleAccess,
    feature: GoogleFeature,
    onFeedback: (ActionFeedback) -> Unit,
    onDismiss: () -> Unit,
    onConnected: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val palette = currentTurniPalette
    val activeProfile = ShiftRepository.googleAccountProfile
    val remembered = GoogleAccountSession.rememberedAccounts
    val accounts = remember(remembered, activeProfile) {
        if (activeProfile == null || remembered.any { it.email.equals(activeProfile.email, ignoreCase = true) }) {
            remembered
        } else {
            listOf(activeProfile) + remembered
        }
    }
    val subtitle = when (feature) {
        GoogleFeature.DRIVE -> "Backup su Drive"
        GoogleFeature.CALENDAR -> "Google Calendar"
        else -> "Scegli account"
    }

    TurniSafeBottomDialog(onClose = onDismiss, scrollContent = false) { finish ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.google_g),
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(3.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Account Google",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Chiudi",
                    onClick = { finish(onDismiss) },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = Color(palette.danger.fill),
                    tint = ColorNegative,
                    iconSize = 18.dp,
                )
            }

            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ObsidianSurfaceContainer)
                        .padding(14.dp),
                ) {
                    Text(
                        text = when (feature) {
                            GoogleFeature.DRIVE -> "Collega un account Google per backup e ripristino su Drive."
                            GoogleFeature.CALENDAR -> "Collega un account Google per sincronizzare i turni."
                            else -> "Collega un account Google per le funzioni cloud."
                        },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(accounts, key = { it.email.lowercase(Locale.ROOT) }) { account ->
                        val isActive = activeProfile?.email?.equals(account.email, ignoreCase = true) == true
                        val itemShape = RoundedCornerShape(16.dp)
                        val activeBorderColor by animateColorAsState(
                            targetValue = if (isActive) ObsidianPrimary else Color.Transparent,
                            animationSpec = tween(durationMillis = 180),
                            label = "googleAccountCardBorder",
                        )
                        val initial = account.displayName.trim().firstOrNull()?.uppercaseChar()?.toString()
                            ?: account.email.trim().firstOrNull()?.uppercaseChar()?.toString()
                            ?: "G"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .tactilePressClickable(
                                    shape = itemShape,
                                    enabled = !access.busy,
                                    pressedScale = 0.985f,
                                    role = Role.Button,
                                ) {
                                    finish {
                                        onDismiss()
                                        if (!isActive || onConnected != null) {
                                            access.authorizeKnownAccount(account.email, feature) { token ->
                                                onConnected?.invoke(token)
                                                    ?: onFeedback(ActionFeedback("Account Google collegato"))
                                            }
                                        }
                                    }
                                }
                                .background(ObsidianSurfaceContainer)
                                .then(
                                    if (isActive) {
                                        Modifier.border(1.5.dp, activeBorderColor, itemShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) ObsidianPrimaryContainer else ObsidianSurface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = initial,
                                    color = if (isActive) BrandInk else TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = account.displayName.ifBlank { account.email.substringBefore('@') },
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = account.email,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isActive) {
                                PastelPill(
                                    text = "Attivo",
                                    color = ExpressivePastel.MINT.sourceColor,
                                    icon = "check",
                                )
                            } else {
                                TonalIconButton(
                                    icon = "trash",
                                    contentDescription = "Rimuovi ${account.email} dalla cronologia",
                                    onClick = { GoogleAccountSession.forgetRemembered(context, account.email) },
                                    size = 34.dp,
                                    cornerRadius = 11.dp,
                                    fill = ObsidianSurface,
                                    tint = TextSecondary,
                                    iconSize = 16.dp,
                                    enabled = !access.busy,
                                )
                            }
                        }
                    }
                }
            }

            CloudButton(
                label = if (accounts.isEmpty()) "Scegli account Google" else "Aggiungi o cambia su Google",
                icon = "plus",
                onClick = {
                    finish {
                        onDismiss()
                        access.authorize(feature, true) { token ->
                            onConnected?.invoke(token)
                                ?: onFeedback(ActionFeedback("Account Google collegato"))
                        }
                    }
                },
                enabled = !access.busy,
            )

            if (activeProfile != null) {
                CloudButton(
                    label = "Disconnetti account attivo",
                    icon = "x",
                    onClick = {
                        finish {
                            onDismiss()
                            access.disconnect {
                                onFeedback(ActionFeedback("Account Google scollegato"))
                            }
                        }
                    },
                    enabled = !access.busy,
                    destructive = true,
                )
            }
        }
    }
}

private val safeBottomDialogEasing = CubicBezierEasing(0.3f, 0.08f, 0.3f, 1f)

/** Edge-to-edge bottom dialog keeping bottom actions safely above system navigation bars. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TurniSafeBottomDialog(
    onClose: () -> Unit,
    scrollContent: Boolean = true,
    maxHeightFraction: Float = 0.86f,
    content: @Composable ColumnScope.(finish: (() -> Unit) -> Unit) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val finish: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            scope.launch {
                visible = false
                delay(220)
                action()
            }
        }
    }
    LaunchedEffect(Unit) { visible = true }

    Dialog(
        onDismissRequest = { finish(onClose) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val dark = TurniThemeState.dark
        val dimAmount = if (dark) 0.56f else 0.36f
        DisposableEffect(view, dark) {
            val applyChrome = Runnable {
                val window = (view as? DialogWindowProvider)?.window
                    ?: (view.parent as? DialogWindowProvider)?.window
                window?.let {
                    it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    it.setDimAmount(dimAmount)
                    @Suppress("DEPRECATION")
                    it.addFlags(
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                    )
                    @Suppress("DEPRECATION")
                    it.clearFlags(
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                    )
                    WindowCompat.setDecorFitsSystemWindows(it, false)
                    @Suppress("DEPRECATION")
                    it.statusBarColor = Color.Transparent.toArgb()
                    @Suppress("DEPRECATION")
                    it.navigationBarColor = Color.Transparent.toArgb()
                    WindowCompat.getInsetsController(it, it.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
            }
            applyChrome.run()
            view.post(applyChrome)
            onDispose { view.removeCallbacks(applyChrome) }
        }

        val navBottom = WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding()
            .coerceAtLeast(24.dp)
        val maxHeight = (LocalConfiguration.current.screenHeightDp * maxHeightFraction)
            .coerceAtLeast(280f)
            .dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !closing,
                    ) { finish(onClose) },
            )
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = tween(durationMillis = 360, easing = safeBottomDialogEasing),
                ) { it / 3 } + fadeIn(tween(durationMillis = 260)),
                exit = slideOutVertically(
                    animationSpec = tween(durationMillis = 200, easing = safeBottomDialogEasing),
                ) { it / 3 } + fadeOut(tween(durationMillis = 160)),
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .padding(bottom = navBottom),
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = ObsidianSurface,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        // Prevent uncaptured card taps from dismissing through the scrim sibling.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                }
                            }
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        content(finish)
                    }
                }
            }
        }
    }
}

/** Grafite and Cobalto Material 3 time picker with smooth dial/keyboard switching. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TurniTimePickerDialog(
    title: String,
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    is24Hour: Boolean = ShiftRepository.appPreferences.timeFormat24h,
) {
    val parsed = remember(initialTime) {
        runCatching { LocalTime.parse(initialTime) }.getOrDefault(LocalTime.of(8, 0))
    }
    val state = rememberTimePickerState(
        initialHour = parsed.hour,
        initialMinute = parsed.minute,
        is24Hour = is24Hour,
    )
    var dialMode by remember { mutableStateOf(true) }
    val modeIconPhase = remember { Animatable(0f) }
    var firstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(dialMode) {
        if (firstComposition) {
            firstComposition = false
        } else {
            modeIconPhase.snapTo(0f)
            modeIconPhase.animateTo(1f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
            modeIconPhase.snapTo(0f)
        }
    }

    val pickerColors = TimePickerDefaults.colors(
        clockDialColor = ObsidianSurfaceContainer,
        clockDialSelectedContentColor = OnBrand,
        clockDialUnselectedContentColor = TextPrimary,
        selectorColor = ObsidianPrimary,
        containerColor = ObsidianSurface,
        periodSelectorBorderColor = ObsidianBorderSubtle,
        periodSelectorSelectedContainerColor = ObsidianPrimaryContainer,
        periodSelectorUnselectedContainerColor = ObsidianSurfaceContainer,
        periodSelectorSelectedContentColor = BrandInk,
        periodSelectorUnselectedContentColor = TextSecondary,
        timeSelectorSelectedContainerColor = ObsidianPrimaryContainer,
        timeSelectorUnselectedContainerColor = ObsidianSurfaceContainer,
        timeSelectorSelectedContentColor = BrandInk,
        timeSelectorUnselectedContentColor = TextPrimary,
    )
    val palette = currentTurniPalette

    TurniSafeBottomDialog(onClose = onDismiss, scrollContent = true) { finish ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeutralIconBadge(
                icon = "clock",
                fill = ObsidianPrimaryContainer,
                tint = BrandInk,
                progress = modeIconPhase.value,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute),
                    color = BrandInk,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
            }
            TonalIconButton(
                icon = if (dialMode) "edit" else "clock",
                contentDescription = if (dialMode) "Inserimento numerico" else "Quadrante orologio",
                onClick = { dialMode = !dialMode },
                size = 36.dp,
                cornerRadius = 12.dp,
                fill = ObsidianSurfaceContainer,
                tint = BrandInk,
                iconSize = 18.dp,
            )
            TonalIconButton(
                icon = "x",
                contentDescription = "Chiudi",
                onClick = { finish(onDismiss) },
                size = 36.dp,
                cornerRadius = 12.dp,
                fill = Color(palette.danger.fill),
                tint = ColorNegative,
                iconSize = 18.dp,
            )
        }

        AnimatedContent(
            targetState = dialMode,
            transitionSpec = {
                (fadeIn(tween(durationMillis = 220)) + scaleIn(tween(durationMillis = 220), initialScale = 0.96f)) togetherWith
                    fadeOut(tween(durationMillis = 150))
            },
            label = "turniTimePickerMode",
            modifier = Modifier.fillMaxWidth(),
        ) { isDial ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (isDial) {
                    TimePicker(state = state, colors = pickerColors)
                } else {
                    TimeInput(state = state, colors = pickerColors)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CloudButton(
                label = "Annulla",
                icon = "x",
                onClick = { finish(onDismiss) },
                secondary = true,
                modifier = Modifier.weight(1f),
            )
            CloudButton(
                label = "Conferma",
                icon = "check",
                onClick = {
                    finish {
                        onConfirm(String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute))
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
