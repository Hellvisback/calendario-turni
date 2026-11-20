package com.turni.calendario.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.HolidayItem
import com.turni.calendario.model.HolidaysCatalog
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
    val activeCount = HolidaysCatalog.allHolidays.count { it.id in enabledIds }
    val defaultIds = remember { HolidaysCatalog.allHolidays.filter { it.isDefaultEnabled }.map { it.id }.toSet() }
    val canReset = enabledIds != defaultIds
    val scope = rememberCoroutineScope()
    val resetMotion = remember { Animatable(0f) }
    val subtitle = if (activeCount == 1) {
        "Calendario $year · 1 ricorrenza attiva"
    } else {
        "Calendario $year · $activeCount ricorrenze attive"
    }

    CloudFeatureScaffold(
        title = "Festività",
        subtitle = subtitle,
        onDismiss = onDismiss,
        feedback = feedback,
        onDismissFeedback = { feedback = null },
        scrollResetKey = sectionIndex,
        headerAction = {
            val actionShape = RoundedCornerShape(14.dp)
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .alpha(if (canReset) 1f else 0.5f)
                    .clip(actionShape)
                    .background(actionBg)
                    .border(1.dp, ObsidianBorderSubtle, actionShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ripristina predefinite"
                    }
                    .bouncyClickable(enabled = canReset) {
                        ShiftRepository.resetHolidaysToDefault()
                        feedback = ActionFeedback("Festività predefinite ripristinate")
                        scope.launch {
                            resetMotion.snapTo(0f)
                            resetMotion.animateTo(1f, tween(520))
                            resetMotion.snapTo(0f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                SiteIconMotion(
                    name = "rotation",
                    description = null,
                    modifier = Modifier.size(19.dp),
                    tint = actionTint,
                ) { resetMotion.value }
            }
        },
    ) {
        val statusBadgeFill by animateColorAsState(
            targetValue = if (canReset) ObsidianPrimarySubtle else ObsidianSurfaceContainer,
            animationSpec = tween(200),
            label = "holidaySummaryBadgeFill",
        )
        val statusBadgeInk by animateColorAsState(
            targetValue = if (canReset) BrandInk else TextSecondary,
            animationSpec = tween(200),
            label = "holidaySummaryBadgeInk",
        )
        CloudCard(
            title = "Calendario $year",
            subtitle = "Scegli le ricorrenze da evidenziare sul calendario.",
        ) {
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianPrimaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    SiteIcon(
                        name = "calendar",
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = BrandInk,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeCount == 1) "1 festività selezionata" else "$activeCount festività selezionate",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = if (canReset) "Selezione personalizzata" else "Impostazione predefinita",
                        fontSize = 11.sp,
                        color = TextSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusBadgeFill)
                        .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (canReset) "Modificate" else "Predefinite",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusBadgeInk,
                    )
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
    val palette = currentTurniPalette

    CloudCard(
        title = title,
        subtitle = "$activeInSection di ${holidays.size} attive nel $year",
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
                val fill by animateColorAsState(
                    targetValue = if (selected) ObsidianPrimaryContainer else ObsidianSurfaceContainer,
                    animationSpec = tween(180),
                    label = "holidayDateFill",
                )
                val badgeBorder by animateColorAsState(
                    targetValue = if (selected) ObsidianPrimary.copy(alpha = 0.5f) else ObsidianBorderSubtle,
                    animationSpec = tween(180),
                    label = "holidayDateBorder",
                )
                val ink by animateColorAsState(
                    targetValue = if (selected) Color(palette.onPrimaryContainer) else TextSecondary,
                    animationSpec = tween(180),
                    label = "holidayDateInk",
                )
                val titleColor by animateColorAsState(
                    targetValue = if (selected) TextPrimary else TextSecondary,
                    animationSpec = tween(180),
                    label = "holidayTitleColor",
                )
                val rowInteraction = remember(holiday.id) { MutableInteractionSource() }

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
                            .widthIn(min = 50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(fill)
                            .border(1.dp, badgeBorder, RoundedCornerShape(12.dp))
                            .clearAndSetSemantics { contentDescription = date.format(fullDate) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ink,
                            )
                            Text(
                                text = date.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN),
                                style = MaterialTheme.typography.labelSmall,
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
                            fontWeight = FontWeight.SemiBold,
                            color = titleColor,
                        )
                        Text(
                            text = if (selected) "$weekday · attiva sul calendario" else weekday,
                            fontSize = 11.sp,
                            color = if (selected) BrandInk else TextSecondary,
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
