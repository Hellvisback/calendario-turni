package com.turni.calendario.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.ReperibilitaConfig
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.theme.BrandInk
import com.turni.calendario.ui.theme.ObsidianBorder
import com.turni.calendario.ui.theme.ObsidianBorderSubtle
import com.turni.calendario.ui.theme.ObsidianPrimary
import com.turni.calendario.ui.theme.ObsidianPrimarySubtle
import com.turni.calendario.ui.theme.ObsidianSurface
import com.turni.calendario.ui.theme.ObsidianSurfaceContainer
import com.turni.calendario.ui.theme.ObsidianSurfaceHigh
import com.turni.calendario.ui.theme.OnBrand
import com.turni.calendario.ui.theme.TextPrimary
import com.turni.calendario.ui.theme.TextSecondary
import com.turni.calendario.ui.theme.TurniThemeState
import com.turni.calendario.ui.theme.currentTurniPalette
import com.turni.calendario.ui.theme.shiftColorStyle
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

internal fun annualDayInMonth(month:Int,day:Int):Int=day.coerceIn(1,Month.of(month).maxLength())
internal fun manualOnCallDates(dates:Set<LocalDate>,overrides:Map<LocalDate,Boolean?>):Set<LocalDate> =
    (dates+overrides.filterValues{it==true}.keys).filterTo(mutableSetOf()){overrides[it]!=false}

internal fun visibleOnCallMode(mode: ReperibilitaMode): ReperibilitaMode = when (mode) {
    ReperibilitaMode.MANUALE -> ReperibilitaMode.TURNO_SPECIFICO
    ReperibilitaMode.TURNO_SPECIFICO,
    ReperibilitaMode.ALTERNATA_STAGIONALE -> mode
}

internal fun onCallStatusSummary(config: ReperibilitaConfig): String = when (config.mode) {
    ReperibilitaMode.MANUALE -> "Nessuna regola automatica"
    ReperibilitaMode.TURNO_SPECIFICO -> {
        val count = config.shiftSpecificIds.size
        "Attiva · $count ${if (count == 1) "turno" else "turni"}"
    }
    ReperibilitaMode.ALTERNATA_STAGIONALE -> "Attiva · Stagionale"
}

@Composable
fun ReperibilitaSettingsScreen(onDismiss: () -> Unit) {
    val config = ShiftRepository.reperibilitaConfig
    val enabled = config.mode != ReperibilitaMode.MANUALE
    var lastAutoModeName by rememberSaveable {
        mutableStateOf(visibleOnCallMode(config.mode).name)
    }
    LaunchedEffect(config.mode) {
        if (config.mode != ReperibilitaMode.MANUALE) {
            lastAutoModeName = config.mode.name
        }
    }
    val lastAutoMode = remember(lastAutoModeName) {
        runCatching { ReperibilitaMode.valueOf(lastAutoModeName) }
            .getOrDefault(ReperibilitaMode.TURNO_SPECIFICO)
            .let(::visibleOnCallMode)
    }

    fun updateConfig(change: (ReperibilitaConfig) -> ReperibilitaConfig) {
        val current = ShiftRepository.reperibilitaConfig
        val updated = change(current)
        if (updated != current) {
            ShiftRepository.reperibilitaConfig = updated
            ShiftRepository.notifyDataChanged()
        }
    }

    fun setReperibilitaMode(mode: ReperibilitaMode) {
        updateConfig { it.copy(mode = mode) }
    }

    CloudFeatureScaffold(
        title = "Reperibilità",
        subtitle = "Configura regole automatiche per turno o stagionali",
        onDismiss = onDismiss,
    ) {
        OnCallHeroCard(
            enabled = enabled,
            statusSummary = onCallStatusSummary(config),
            onToggle = { turnOn ->
                setReperibilitaMode(if (turnOn) lastAutoMode else ReperibilitaMode.MANUALE)
            },
        )

        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn(tween(220)) + expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
            exit = fadeOut(tween(160)) + shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        ) {
            val displayMode = if (enabled) visibleOnCallMode(config.mode) else lastAutoMode
            val segmentIndex = if (displayMode == ReperibilitaMode.ALTERNATA_STAGIONALE) 1 else 0

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SlidingSegmentBar(
                    items = listOf(
                        "Per turno" to "clock",
                        "Stagionale" to "calendar",
                    ),
                    selectedIndex = segmentIndex,
                    onSelect = { index ->
                        val nextMode = if (index == 1) {
                            ReperibilitaMode.ALTERNATA_STAGIONALE
                        } else {
                            ReperibilitaMode.TURNO_SPECIFICO
                        }
                        setReperibilitaMode(nextMode)
                    },
                )

                AnimatedContent(
                    targetState = displayMode,
                    transitionSpec = {
                        val direction = if (
                            targetState == ReperibilitaMode.ALTERNATA_STAGIONALE &&
                            initialState == ReperibilitaMode.TURNO_SPECIFICO
                        ) {
                            1
                        } else {
                            -1
                        }
                        (
                            fadeIn(tween(220, delayMillis = 40)) +
                                slideInHorizontally(
                                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                                ) { width -> direction * width / 8 }
                            )
                            .togetherWith(
                                fadeOut(tween(140)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                                    ) { width -> -direction * width / 8 },
                            )
                            .using(SizeTransform(clip = true))
                    },
                    label = "onCallRuleModeContent",
                ) { activeMode ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (activeMode) {
                            ReperibilitaMode.TURNO_SPECIFICO -> {
                                CloudCard(
                                    title = "Modelli di turno",
                                    subtitle = "La campanella si accende automaticamente nei giorni con almeno uno dei turni selezionati.",
                                ) {
                                    OnCallModelChoices(
                                        selected = config.shiftSpecificIds,
                                        onChange = { ids ->
                                            updateConfig { it.copy(shiftSpecificIds = ids) }
                                        },
                                    )
                                }
                            }

                            ReperibilitaMode.ALTERNATA_STAGIONALE -> {
                                val seasonal = config.seasonal
                                CloudCard(
                                    title = "Periodo annuale su turno",
                                    subtitle = "Tra queste due date (comprese, anche oltre Capodanno) sei reperibile solo nei modelli scelti.",
                                ) {
                                    AnnualOnCallDate(
                                        label = "Dal",
                                        month = seasonal.winterStartMonth,
                                        day = seasonal.winterStartDay,
                                        onDate = { month, day ->
                                            updateConfig {
                                                it.copy(
                                                    seasonal = it.seasonal.copy(
                                                        winterStartMonth = month,
                                                        winterStartDay = day,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                    SettingsDivider()
                                    AnnualOnCallDate(
                                        label = "Al",
                                        month = seasonal.winterEndMonth,
                                        day = seasonal.winterEndDay,
                                        onDate = { month, day ->
                                            updateConfig {
                                                it.copy(
                                                    seasonal = it.seasonal.copy(
                                                        winterEndMonth = month,
                                                        winterEndDay = day,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                    SettingsDivider()
                                    Text(
                                        text = "Modelli attivi nel periodo",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                    )
                                    OnCallModelChoices(
                                        selected = seasonal.winterShiftIds,
                                        onChange = { ids ->
                                            updateConfig {
                                                it.copy(seasonal = it.seasonal.copy(winterShiftIds = ids))
                                            }
                                        },
                                    )
                                }

                                CloudCard(
                                    title = "Ciclo nel resto dell'anno",
                                    subtitle = "Fuori dal periodo sopra, i giorni seguono blocchi da 7 dalla data iniziale, anche senza turno.",
                                ) {
                                    OnCallReferenceDate(
                                        value = seasonal.summerCycleReferenceDate,
                                        onDate = { date ->
                                            updateConfig {
                                                it.copy(
                                                    seasonal = it.seasonal.copy(
                                                        summerCycleReferenceDate = date,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                    SettingsDivider()
                                    OnCallCadencePresets(
                                        onCallWeeks = seasonal.summerCycleOnCallWeeks,
                                        workWeeks = seasonal.summerCycleWorkWeeks,
                                        onSelect = { onCall, work ->
                                            updateConfig {
                                                it.copy(
                                                    seasonal = it.seasonal.copy(
                                                        summerCycleOnCallWeeks = onCall,
                                                        summerCycleWorkWeeks = work,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                    SettingsDivider()
                                    OnCallWeekCount(
                                        label = "Settimane con reperibilità",
                                        value = seasonal.summerCycleOnCallWeeks,
                                        minValue = 1,
                                        onChange = { value ->
                                            updateConfig {
                                                it.copy(
                                                    seasonal = it.seasonal.copy(
                                                        summerCycleOnCallWeeks = value,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                    OnCallWeekCount(
                                        label = "Settimane senza reperibilità",
                                        value = seasonal.summerCycleWorkWeeks,
                                        minValue = 0,
                                        onChange = { value ->
                                            updateConfig {
                                                it.copy(
                                                    seasonal = it.seasonal.copy(
                                                        summerCycleWorkWeeks = value,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                    OnCallCycleSummary(
                                        onCallWeeks = seasonal.summerCycleOnCallWeeks,
                                        workWeeks = seasonal.summerCycleWorkWeeks,
                                        referenceDate = seasonal.summerCycleReferenceDate,
                                    )
                                }
                            }

                            ReperibilitaMode.MANUALE -> Unit
                        }
                    }
                }
            }
        }

        CloudCard(title = "Precedenza sul calendario") {
            Text(
                text = "La campanella impostata su un singolo giorno dal calendario ha sempre la precedenza sulle regole automatiche.",
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Text(
                text = "Le modifiche si salvano automaticamente. Per ricevere un avviso acustico, usa Sveglie automatiche.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun OnCallHeroCard(
    enabled: Boolean,
    statusSummary: String,
    onToggle: (Boolean) -> Unit,
) {
    val palette = currentTurniPalette
    val scope = rememberCoroutineScope()
    val bellMotion = remember { Animatable(0f) }
    val heroShape = RoundedCornerShape(20.dp)
    val heroBorder by animateColorAsState(
        targetValue = if (enabled) Color(palette.onCall.edge).copy(alpha = 0.42f) else ObsidianBorderSubtle,
        animationSpec = tween(220),
        label = "onCallHeroBorder",
    )
    val badgeFill by animateColorAsState(
        targetValue = if (enabled) Color(palette.onCall.fill) else ObsidianSurfaceHigh,
        animationSpec = tween(220),
        label = "onCallHeroBadgeFill",
    )
    val badgeInk by animateColorAsState(
        targetValue = if (enabled) Color(palette.onCall.ink) else TextSecondary,
        animationSpec = tween(220),
        label = "onCallHeroBadgeInk",
    )
    val pillFill by animateColorAsState(
        targetValue = if (enabled) Color(palette.onCall.fill) else ObsidianSurfaceHigh,
        animationSpec = tween(220),
        label = "onCallHeroPillFill",
    )
    val pillInk by animateColorAsState(
        targetValue = if (enabled) Color(palette.onCall.ink) else TextSecondary,
        animationSpec = tween(220),
        label = "onCallHeroPillInk",
    )
    val pillBorder by animateColorAsState(
        targetValue = if (enabled) Color(palette.onCall.edge).copy(alpha = 0.48f) else ObsidianBorderSubtle,
        animationSpec = tween(220),
        label = "onCallHeroPillBorder",
    )

    Surface(
        shape = heroShape,
        color = ObsidianSurface,
        border = BorderStroke(1.dp, heroBorder),
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
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(badgeFill),
                    contentAlignment = Alignment.Center,
                ) {
                    SiteIconMotion(
                        name = "bell",
                        description = null,
                        modifier = Modifier.size(21.dp),
                        tint = badgeInk,
                    ) { bellMotion.value }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Reperibilità automatica",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusSummary,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TurniSwitch(
                    checked = enabled,
                    onCheckedChange = { next ->
                        if (next) {
                            scope.launch {
                                bellMotion.snapTo(0f)
                                bellMotion.animateTo(1f, tween(560))
                                bellMotion.snapTo(0f)
                            }
                        }
                        onToggle(next)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Reperibilità automatica"
                    },
                )
            }

            SettingsDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(pillFill)
                        .border(1.dp, pillBorder, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(pillInk),
                    )
                    Text(
                        text = if (enabled) "Attiva" else "Disattiva",
                        color = pillInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Text(
                    text = if (enabled) {
                        "La campanella segue la regola automatica configurata."
                    } else {
                        "Attivala per collegare la campanella a turni o periodi."
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OnCallModelChoices(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
) {
    val all = ShiftRepository.shiftDefinitions.toList()
    val profiles = ShiftRepository.workProfiles.toList()
    val haptic = LocalHapticFeedback.current

    if (all.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianSurfaceContainer)
                .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SiteIcon(
                name = "clock",
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = TextSecondary,
            )
            Text(
                text = "Non hai ancora modelli di turno. Creali in Modelli e rotazioni, poi torna qui per sceglierli.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = TextSecondary,
            )
        }
        return
    }

    val grouped = all.groupBy { it.workProfileId }.entries.toList()
    grouped.forEachIndexed { groupIndex, (workId, models) ->
        if (groupIndex > 0) {
            SettingsDivider()
        }
        if (profiles.size > 1) {
            val profile = profiles.firstOrNull { it.id == workId }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (profile != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(profile.colorHex)),
                    )
                }
                Text(
                    text = profile?.name ?: "Altro lavoro",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 250.dp || LocalDensity.current.fontScale > 1.3f) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                models.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { model ->
                            OnCallModelTile(
                                model = model,
                                chosen = model.id in selected,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onToggle = { checked ->
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } catch (_: Exception) {
                                    }
                                    val updated = if (checked) {
                                        (selected + model.id).distinct()
                                    } else {
                                        selected.filterNot { it == model.id }
                                    }
                                    onChange(updated)
                                },
                            )
                        }
                        repeat(columns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnCallModelTile(
    model: ShiftDefinition,
    chosen: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
) {
    val shiftStyle = shiftColorStyle(
        source = Color(model.colorHex),
        dark = TurniThemeState.dark,
        backdrop = ObsidianSurface,
        definitionId = model.id,
    )
    val tileFill by animateColorAsState(
        targetValue = if (chosen) ObsidianPrimarySubtle else ObsidianSurfaceContainer,
        animationSpec = tween(180),
        label = "onCallModelTileFill",
    )
    val tileBorder by animateColorAsState(
        targetValue = if (chosen) ObsidianPrimary else ObsidianBorderSubtle,
        animationSpec = tween(180),
        label = "onCallModelTileBorder",
    )
    val indicatorFill by animateColorAsState(
        targetValue = if (chosen) ObsidianPrimary else Color.Transparent,
        animationSpec = tween(180),
        label = "onCallModelIndicatorFill",
    )
    val indicatorBorder by animateColorAsState(
        targetValue = if (chosen) ObsidianPrimary else ObsidianBorder,
        animationSpec = tween(180),
        label = "onCallModelIndicatorBorder",
    )
    val interactionSource = remember(model.id) { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(tileFill)
            .border(1.dp, tileBorder, shape)
            .toggleable(
                value = chosen,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onValueChange = onToggle,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(shiftStyle.codeFill)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = model.shortCode.ifBlank { model.name.take(3) }.take(4),
                color = shiftStyle.codeInk,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = model.name,
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (model.isAllDay) "Tutto il giorno" else model.timeRangeFormatted,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(indicatorFill)
                .border(1.5.dp, indicatorBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (chosen) {
                SiteIcon(
                    name = "check",
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = OnBrand,
                )
            }
        }
    }
}

@Composable
private fun AnnualOnCallDate(
    label: String,
    month: Int,
    day: Int,
    onDate: (Int, Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var shown by remember(month) { mutableIntStateOf(month) }
    val clampedDay = annualDayInMonth(month, day)
    val value = LocalDate.of(2028, month, clampedDay)
        .format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN))
    val chevronRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(220),
        label = "annualDateChevron",
    )
    val rowBorder by animateColorAsState(
        targetValue = if (open) ObsidianPrimary else ObsidianBorderSubtle,
        animationSpec = tween(180),
        label = "annualDateBorder",
    )
    val rowShape = RoundedCornerShape(14.dp)
    val headerInteraction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .background(ObsidianSurfaceContainer)
                .border(1.dp, rowBorder, rowShape)
                .clickable(
                    interactionSource = headerInteraction,
                    indication = null,
                    role = Role.Button,
                ) {
                    if (!open) shown = month
                    open = !open
                }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$label · ogni anno",
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
            SiteIcon(
                name = "calendar",
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = BrandInk,
            )
            SiteIcon(
                name = "chevron-down",
                contentDescription = if (open) "Chiudi selezione data" else "Scegli data",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation),
                tint = TextSecondary,
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(260)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
        ) {
            val monthName = Month.of(shown)
                .getDisplayName(TextStyle.FULL, Locale.ITALIAN)
                .replaceFirstChar { it.titlecase(Locale.ITALIAN) }

            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurfaceContainer)
                    .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactIconButton(
                        icon = "chevron-left",
                        contentDescription = "Mese precedente",
                        onClick = { shown = if (shown == 1) 12 else shown - 1 },
                    )
                    Text(
                        text = monthName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    CompactIconButton(
                        icon = "chevron-right",
                        contentDescription = "Mese successivo",
                        onClick = { shown = if (shown == 12) 1 else shown + 1 },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..Month.of(shown).maxLength()).toList().chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            week.forEach { number ->
                                val selectedDay = shown == month && number == clampedDay
                                val dayFill by animateColorAsState(
                                    targetValue = if (selectedDay) ObsidianPrimary else ObsidianSurfaceHigh,
                                    animationSpec = tween(160),
                                    label = "annualDayFill",
                                )
                                val dayInk by animateColorAsState(
                                    targetValue = if (selectedDay) OnBrand else TextPrimary,
                                    animationSpec = tween(160),
                                    label = "annualDayInk",
                                )
                                val dayInteraction = remember(shown, number) { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(dayFill)
                                        .clickable(
                                            interactionSource = dayInteraction,
                                            indication = null,
                                            role = Role.Button,
                                        ) {
                                            try {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            } catch (_: Exception) {
                                            }
                                            onDate(shown, number)
                                            open = false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$number",
                                        color = dayInk,
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedDay) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                            repeat(7 - week.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnCallReferenceDate(
    value: LocalDate,
    onDate: (LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var month by remember(value) { mutableStateOf(YearMonth.from(value)) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(220),
        label = "referenceDateChevron",
    )
    val rowBorder by animateColorAsState(
        targetValue = if (open) ObsidianPrimary else ObsidianBorderSubtle,
        animationSpec = tween(180),
        label = "referenceDateBorder",
    )
    val rowShape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .background(ObsidianSurfaceContainer)
                .border(1.dp, rowBorder, rowShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                ) {
                    if (!open) month = YearMonth.from(value)
                    open = !open
                }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inizio della prima settimana reperibile",
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
                Text(
                    text = value.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
            }
            SiteIcon(
                name = "calendar",
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = BrandInk,
            )
            SiteIcon(
                name = "chevron-down",
                contentDescription = if (open) "Chiudi calendario" else "Scegli inizio ciclo",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation),
                tint = TextSecondary,
            )
        }

        AnimatedVisibility(
            visible = open,
            enter = expandVertically(tween(260)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(140)),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianSurfaceContainer)
                    .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                AppDateGrid(
                    month = month,
                    selected = value,
                    changeMonth = { month = it },
                    compact = true,
                    select = {
                        onDate(it)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun OnCallCadencePresets(
    onCallWeeks: Int,
    workWeeks: Int,
    onSelect: (Int, Int) -> Unit,
) {
    val presets = listOf(
        Triple("Ogni sett.", 1, 0),
        Triple("Alterne", 1, 1),
        Triple("1 + 3 sett.", 1, 3),
    )
    val haptic = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Cadenza rapida",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 260.dp || LocalDensity.current.fontScale > 1.35f
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (label, on, off) ->
                        OnCallPresetPill(
                            label = label,
                            selected = onCallWeeks == on && workWeeks == off,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } catch (_: Exception) {
                                }
                                onSelect(on, off)
                            },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { (label, on, off) ->
                        OnCallPresetPill(
                            label = label,
                            selected = onCallWeeks == on && workWeeks == off,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                try {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } catch (_: Exception) {
                                }
                                onSelect(on, off)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnCallPresetPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val fill by animateColorAsState(
        targetValue = if (selected) ObsidianPrimary else ObsidianSurfaceContainer,
        animationSpec = tween(180),
        label = "onCallPresetFill",
    )
    val border by animateColorAsState(
        targetValue = if (selected) ObsidianPrimary else ObsidianBorderSubtle,
        animationSpec = tween(180),
        label = "onCallPresetBorder",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) OnBrand else TextPrimary,
        animationSpec = tween(180),
        label = "onCallPresetInk",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OnCallWeekCount(
    label: String,
    value: Int,
    minValue: Int,
    onChange: (Int) -> Unit,
) {
    val canDecrease = value > minValue
    val canIncrease = value < Int.MAX_VALUE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = TextPrimary,
        )
        CompactIconButton(
            icon = "minus",
            contentDescription = "Riduci $label",
            enabled = canDecrease,
            onClick = { if (canDecrease) onChange(value - 1) },
        )
        Box(
            modifier = Modifier
                .widthIn(min = 38.dp)
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ObsidianPrimarySubtle)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$value",
                fontSize = 15.sp,
                color = BrandInk,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        CompactIconButton(
            icon = "plus",
            contentDescription = "Aumenta $label",
            enabled = canIncrease,
            onClick = { if (canIncrease) onChange(value + 1) },
        )
    }
}

@Composable
private fun CompactIconButton(
    icon: String,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 42.dp, minHeight = 42.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(ObsidianSurfaceContainer)
            .border(1.dp, ObsidianBorderSubtle, shape)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
            ) {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (_: Exception) {
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        SiteIcon(
            name = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = TextPrimary,
        )
    }
}

@Composable
private fun OnCallCycleSummary(
    onCallWeeks: Int,
    workWeeks: Int,
    referenceDate: LocalDate,
) {
    val formattedDate = referenceDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN))
    val summary = if (workWeeks == 0) {
        "Reperibilità attiva tutte le settimane fuori dal periodo annuale, con inizio dal $formattedDate."
    } else {
        val total = onCallWeeks + workWeeks
        val onLabel = if (onCallWeeks == 1) "1 settimana" else "$onCallWeeks settimane"
        val offLabel = if (workWeeks == 1) "1 senza" else "$workWeeks senza"
        "Ciclo da $total settimane: $onLabel con reperibilità, poi $offLabel, a partire dal $formattedDate."
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = summary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = TextSecondary,
        )
    }
}

