package com.turni.calendario.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.HolidayItem
import com.turni.calendario.model.HolidaysCatalog
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.BrandInk
import com.turni.calendario.ui.theme.ObsidianSurface
import com.turni.calendario.ui.theme.ObsidianSurfaceContainer
import com.turni.calendario.ui.theme.TextMuted
import com.turni.calendario.ui.theme.TextPrimary
import com.turni.calendario.ui.theme.TextSecondary
import com.turni.calendario.ui.theme.TurniThemeState
import com.turni.calendario.ui.theme.readableInk
import com.turni.calendario.ui.theme.shiftChartColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val fullDate = DateTimeFormatter.ofPattern("EEEE d MMMM uuuu", Locale.ITALIAN)

@Composable
fun HolidaysSettingsScreen(onDismiss: () -> Unit) {
    val year = LocalDate.now().year
    val enabledIds = ShiftRepository.enabledHolidayIds
    val sections = remember(year) {
        val catalog = HolidaysCatalog.allHolidays.sortedBy { it.getDateForYear(year) }
        listOf(
            "Festività nazionali e civili" to catalog.filter { !it.isCcnlExHoliday && !it.isPatronSaint },
            "Santi patroni" to catalog.filter { !it.isCcnlExHoliday && it.isPatronSaint },
            "Ex festività CCNL e giorni speciali" to catalog.filter { it.isCcnlExHoliday },
        )
    }
    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val scope = rememberCoroutineScope()
    val heroBadgeMotion = remember { Animatable(0f) }
    val inlineResetMotion = remember { Animatable(0f) }
    var heroMotionJob by remember { mutableStateOf<Job?>(null) }
    var inlineMotionJob by remember { mutableStateOf<Job?>(null) }
    val activeCount = HolidaysCatalog.allHolidays.count { it.id in enabledIds }
    val defaultIds = remember { HolidaysCatalog.allHolidays.filter { it.isDefaultEnabled }.map { it.id }.toSet() }
    val canReset = enabledIds != defaultIds
    var lastActiveCount by remember { mutableIntStateOf(activeCount) }
    fun pulseHeroBadge() {
        heroMotionJob?.cancel()
        heroMotionJob = scope.launch {
            heroBadgeMotion.snapTo(0f)
            heroBadgeMotion.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            heroBadgeMotion.snapTo(0f)
        }
    }
    LaunchedEffect(Unit) { pulseHeroBadge() }
    LaunchedEffect(activeCount) {
        if (activeCount != lastActiveCount) {
            lastActiveCount = activeCount
            pulseHeroBadge()
        }
    }

    fun restoreDefaults() {
        pulseHeroBadge()
        inlineMotionJob?.cancel()
        inlineMotionJob = scope.launch {
            inlineResetMotion.snapTo(0f)
            inlineResetMotion.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            inlineResetMotion.snapTo(0f)
        }
        ShiftRepository.resetHolidaysToDefault()
        feedback = ActionFeedback("Festività predefinite ripristinate")
    }

    CloudFeatureScaffold(
        title = "Festività",
        onDismiss = onDismiss,
        feedback = feedback,
        onDismissFeedback = { feedback = null },
        scrollResetKey = sectionIndex,
        headerAction = {
            val actionBg by animateColorAsState(
                targetValue = if (canReset) ObsidianSurface else ObsidianSurfaceContainer,
                animationSpec = tween(180),
                label = "holidayResetBg",
            )
            val actionTint by animateColorAsState(
                targetValue = if (canReset) BrandInk else TextMuted,
                animationSpec = tween(180),
                label = "holidayResetTint",
            )
            TonalIconButton(
                icon = "rotation",
                contentDescription = "Ripristina predefinite",
                onClick = ::restoreDefaults,
                size = 40.dp,
                cornerRadius = 14.dp,
                fill = actionBg,
                tint = actionTint,
                iconSize = 19.dp,
                enabled = canReset,
                modifier = Modifier.alpha(if (canReset) 1f else 0.5f),
            )
        },
    ) {
        CloudCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PastelIconBadge(
                    pastel = ExpressivePastel.CORAL,
                    icon = "calendar-heart",
                    size = 46.dp,
                    iconSize = 22.dp,
                    progress = heroBadgeMotion.value,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnimatedContent(
                            targetState = activeCount,
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                            },
                            label = "holidayActiveCount",
                        ) { count ->
                            Text(
                                text = "$count",
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                fontSize = 28.sp,
                                lineHeight = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.8).sp,
                                color = TextPrimary,
                            )
                        }
                        PastelPill(
                            text = if (activeCount == 1) "attiva" else "attive",
                            color = ExpressivePastel.CORAL.sourceColor,
                            solid = true,
                        )
                    }
                    Text(
                        text = if (canReset) "Personalizzata · $year" else "Predefinita · $year",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextSecondary,
                    )
                }
                AnimatedVisibility(
                    visible = canReset,
                    enter = fadeIn(tween(180)) + scaleIn(tween(180)),
                    exit = fadeOut(tween(220)) + scaleOut(tween(220)),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceContainer)
                            .bouncyClickable(onClick = ::restoreDefaults)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        SiteIconMotion(
                            name = "rotation",
                            description = null,
                            modifier = Modifier.size(13.dp),
                            tint = BrandInk,
                        ) {
                            inlineResetMotion.value
                        }
                        Text(
                            text = "Ripristina",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandInk,
                        )
                    }
                }
            }
        }

        SlidingSegmentBar(
            items = listOf(
                "Nazionali" to "flag",
                "Patroni" to "star",
                "CCNL" to "briefcase",
            ),
            selectedIndex = sectionIndex,
            onSelect = { sectionIndex = it },
        )

        AnimatedContent(
            targetState = sectionIndex,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                    if (forward) it / 5 else -it / 5
                } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                    if (forward) -it / 5 else it / 5
                } + fadeOut(tween(180)))
            },
            label = "holidaySectionContent",
        ) { currentIndex ->
            val (title, holidays) = sections[currentIndex]
            HolidaySection(title, holidays, enabledIds, year)
        }
    }
}

@Composable
private fun HolidaySection(
    title: String,
    holidays: List<HolidayItem>,
    enabledIds: Set<String>,
    year: Int,
) {
    val activeInSection = holidays.count { it.id in enabledIds }
    val haptic = LocalHapticFeedback.current
    val dark = TurniThemeState.dark
    val themeStyle = TurniThemeState.style
    val surface = ObsidianSurface
    val coralStyle = remember(dark, themeStyle, surface) {
        ExpressivePastel.CORAL.style(dark, surface)
    }
    val coralSheen = remember(coralStyle.codeFill, coralStyle.codeInk) {
        pastelSheenBrush(coralStyle.codeFill, coralStyle.codeInk)
    }
    val activeSubtitleInk = remember(dark, themeStyle, surface) {
        readableInk(surface, shiftChartColor(ExpressivePastel.CORAL.sourceColor, dark))
    }

    CloudCard(
        title = title,
        subtitle = "$activeInSection / ${holidays.size}",
        modifier = Modifier.semantics { heading() },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            holidays.forEachIndexed { index, holiday ->
                val selected = holiday.id in enabledIds
                val date = remember(holiday, year) { holiday.getDateForYear(year) }
                val weekday = remember(date) {
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN)
                        .replaceFirstChar { it.titlecase(Locale.ITALIAN) }
                }
                val tileFill by animateColorAsState(
                    targetValue = if (selected) coralStyle.codeFill else ObsidianSurfaceContainer,
                    animationSpec = tween(200),
                    label = "holidayDateFill",
                )
                val sheenAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(200),
                    label = "holidayDateSheenAlpha",
                )
                val ink by animateColorAsState(
                    targetValue = if (selected) coralStyle.codeInk else TextSecondary,
                    animationSpec = tween(200),
                    label = "holidayDateInk",
                )
                val titleColor by animateColorAsState(
                    targetValue = if (selected) TextPrimary else TextSecondary,
                    animationSpec = tween(200),
                    label = "holidayTitleColor",
                )
                val tilePop = remember(holiday.id) { Animatable(1f) }
                var lastSelected by remember(holiday.id) { mutableStateOf(selected) }
                LaunchedEffect(selected) {
                    if (selected != lastSelected) {
                        lastSelected = selected
                        tilePop.snapTo(1f)
                        tilePop.animateTo(
                            targetValue = if (selected) 1.07f else 0.93f,
                            animationSpec = tween(90),
                        )
                        tilePop.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 460f),
                        )
                    }
                }
                val rowInteraction = remember(holiday.id) { MutableInteractionSource() }
                val rowPressed by rowInteraction.collectIsPressedAsState()
                val pressScale by animateFloatAsState(
                    targetValue = if (rowPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                    label = "holidayRowTilePress",
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                        .clickable(
                            interactionSource = rowInteraction,
                            indication = null,
                        ) {
                            try {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } catch (_: Exception) {
                            }
                            ShiftRepository.toggleHoliday(holiday.id)
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                val combined = tilePop.value * pressScale
                                scaleX = combined
                                scaleY = combined
                            }
                            .clip(RoundedCornerShape(13.dp))
                            .background(tileFill)
                            .clearAndSetSemantics { contentDescription = date.format(fullDate) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sheenAlpha > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .alpha(sheenAlpha)
                                    .background(coralSheen),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 16.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ink,
                            )
                            Text(
                                text = date.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN),
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ink,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = holiday.name,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor,
                        )
                        Text(
                            text = weekday,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = if (selected) activeSubtitleInk else TextSecondary,
                        )
                    }
                    TurniSwitch(
                        checked = selected,
                        onCheckedChange = { ShiftRepository.toggleHoliday(holiday.id) },
                    )
                }
                if (index < holidays.lastIndex) {
                    SettingsDivider()
                }
            }
        }
    }
}
