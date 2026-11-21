package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.turni.calendario.ui.theme.ColorNotte
import com.turni.calendario.ui.theme.ColorRiposo
import com.turni.calendario.ui.theme.ColorSmontoNotte
import com.turni.calendario.ui.theme.ColorPomeriggio
import com.turni.calendario.ui.theme.ObsidianBackground
import com.turni.calendario.ui.theme.ObsidianBorderSubtle
import com.turni.calendario.ui.theme.ObsidianPrimary
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    var replay by remember { mutableStateOf<Job?>(null) }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(fill)
            .semantics { role = Role.Button }
            .bouncyClickable(enabled = enabled) {
                replay?.cancel()
                replay = scope.launch {
                    phase.snapTo(0f)
                    phase.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
                    phase.snapTo(0f)
                }
                onClick()
            },
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
                        SiteIcon(
                            name = iconKey,
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = ink,
                        )
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
    val iconMotion = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
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
            .clip(buttonShape)
            .bouncyClickable(enabled) {
                scope.launch {
                    iconMotion.snapTo(0f)
                    iconMotion.animateTo(1f, tween(340))
                }
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            SiteIconMotion(icon, null, Modifier.size(18.dp), ink.copy(alpha = contentAlpha)) {
                iconMotion.value
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
) {
    var showAccessDetails by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (showAccessDetails) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 360f),
        label = "googleAccessChevron",
    )
    val profile = ShiftRepository.googleAccountProfile
    fun connect() {
        access.authorize(feature, true) { token ->
            onConnected?.invoke(token) ?: onFeedback(ActionFeedback("Account Google collegato"))
        }
    }
    CloudCard(
        title = "Account Google",
        subtitle = if (profile == null) {
            explanation ?: "Accedi con il tuo account. I tuoi dati rimangono nel tuo spazio Google."
        } else {
            null
        },
    ) {
        if (profile == null) {
            GoogleSignInButton(enabled && !access.busy, access.busy, ::connect)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurfaceContainer)
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
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = ::connect,
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
        TextButton(
            onClick = { showAccessDetails = !showAccessDetails },
            colors = ButtonDefaults.textButtonColors(contentColor = BrandInk),
        ) {
            Text("Quali dati autorizzo?", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(5.dp))
            SiteIcon(
                name = "chevron-down",
                contentDescription = null,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(chevronAngle),
                tint = BrandInk,
            )
        }
        AnimatedVisibility(
            visible = showAccessDetails,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when (feature) {
                        GoogleFeature.DRIVE -> "Google conferma nome ed email e permette a TurniSync di usare il proprio spazio privato su Drive. Gli altri file di Drive non vengono letti. Collegare l’account non avvia un backup."
                        GoogleFeature.CALENDAR -> "Google conferma nome ed email, mostra i calendari e chiede il permesso di gestire eventi. Nell’app scegli la destinazione: TurniSync gestisce gli eventi che ha creato, riconoscibili dal proprio contrassegno."
                        else -> "Google conferma nome ed email dell’account scelto. La password rimane gestita da Google."
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Text(
                    text = "Durante la beta l’accesso può essere limitato agli account di prova. Il consenso Google e le eventuali limitazioni dipendono dalla configurazione del progetto; cambiare account non trasferisce automaticamente i dati.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
