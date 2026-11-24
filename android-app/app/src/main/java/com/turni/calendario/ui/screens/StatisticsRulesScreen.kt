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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.StatisticsRule
import com.turni.calendario.model.WorkProfile
import com.turni.calendario.model.holidayExclusionSelectionForToggle
import com.turni.calendario.model.sortedChronologically
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.theme.BrandInk
import com.turni.calendario.ui.theme.ObsidianPrimary
import com.turni.calendario.ui.theme.ObsidianPrimaryContainer
import com.turni.calendario.ui.theme.ObsidianSurface
import com.turni.calendario.ui.theme.ObsidianSurfaceContainer
import com.turni.calendario.ui.theme.TextMuted
import com.turni.calendario.ui.theme.TextPrimary
import com.turni.calendario.ui.theme.TextSecondary
import com.turni.calendario.ui.theme.TurniThemeState
import com.turni.calendario.ui.theme.readableInk
import com.turni.calendario.ui.theme.shiftColorStyle
import com.turni.calendario.ui.theme.shiftDisplayColor
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun StatisticsRulesScreen(
    onDismiss: () -> Unit,
    onNavigateToTurni: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf<StatisticsRule?>(null) }
    var editingHolidays by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val activeProfileId = ShiftRepository.activeWorkProfileId
    val definitions = ShiftRepository.shiftDefinitions
        .filter { it.workProfileId == activeProfileId }
        .sortedChronologically()
    val rules = ShiftRepository.activeStatisticsRules
    val profiles = ShiftRepository.workProfiles.toList()

    fun close() {
        if (editingHolidays) {
            editingHolidays = false
        } else if (editing != null) {
            editing = null
        } else {
            onDismiss()
        }
    }

    val title = when {
        editingHolidays -> "Esclusioni nei festivi"
        editing != null -> "Regola di sostituzione"
        else -> "Conteggio statistiche"
    }

    CloudFeatureScaffold(
        title = title,
        onDismiss = ::close,
        feedback = feedback,
        onDismissFeedback = { feedback = null },
        scrollResetKey = if (editingHolidays) "holidays" else editing?.id,
    ) {
        AnimatedContent(
            targetState = editing to editingHolidays,
            transitionSpec = {
                val enteringEditor = (initialState.first == null && !initialState.second) &&
                    (targetState.first != null || targetState.second)
                (
                    fadeIn(tween(220, delayMillis = 30)) + slideInHorizontally(
                        tween(260, easing = FastOutSlowInEasing),
                    ) { if (enteringEditor) it / 4 else -it / 6 }
                    ) togetherWith (
                    fadeOut(tween(160)) + slideOutHorizontally(
                        tween(220, easing = FastOutSlowInEasing),
                    ) { if (enteringEditor) -it / 6 else it / 4 }
                    ) using SizeTransform(clip = false)
            },
            label = "statisticsRulesContent",
        ) { (draft, holidays) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    holidays -> HolidayStatisticsRuleEditor(
                        definitions = definitions,
                        profiles = profiles,
                        onSaved = {
                            editingHolidays = false
                            feedback = ActionFeedback("Regola salvata · statistiche aggiornate")
                        },
                    )
                    draft != null -> SubstitutionRuleEditor(
                        draft = draft,
                        definitions = definitions,
                        rules = rules,
                        profiles = profiles,
                        onSaved = {
                            editing = null
                            feedback = ActionFeedback("Regola salvata · statistiche aggiornate")
                        },
                        onDeleted = {
                            ShiftRepository.removeStatisticsRule(draft.id)
                            editing = null
                            feedback = ActionFeedback("Regola eliminata", true)
                        },
                    )
                    else -> StatisticsRulesOverview(
                        definitions = definitions,
                        rules = rules,
                        profiles = profiles,
                        onEditHolidays = { editingHolidays = true },
                        onEditRule = { editing = it },
                        onCreateRule = {
                            editing = StatisticsRule(
                                id = UUID.randomUUID().toString(),
                                displayedShiftId = "",
                                originalShiftIds = emptySet(),
                                workProfileId = activeProfileId,
                            )
                        },
                        onNavigateToTurni = onNavigateToTurni,
                        onFeedback = { feedback = it },
                    )
                }
            }
        }
    }
}

private fun CoroutineScope.launchPulse(
    phase: Animatable<Float, *>,
    previous: Job?,
    durationMillis: Int = 520,
): Job {
    previous?.cancel()
    return launch {
        phase.snapTo(0f)
        phase.animateTo(1f, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
        phase.snapTo(0f)
    }
}

@Composable
private fun Modifier.tactileCardClickable(
    shape: Shape,
    enabled: Boolean = true,
    pressedScale: Float = 0.985f,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 500f),
        label = "tactileCardScale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.065f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "tactileCardOverlay",
    )
    val haptic = LocalHapticFeedback.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            if (pressAlpha > 0f) {
                drawRect(ObsidianPrimary.copy(alpha = pressAlpha))
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
        ) {
            runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            onClick()
        }
}

@Composable
private fun StatisticsRulesOverview(
    definitions: List<ShiftDefinition>,
    rules: List<StatisticsRule>,
    profiles: List<WorkProfile>,
    onEditHolidays: () -> Unit,
    onEditRule: (StatisticsRule) -> Unit,
    onCreateRule: () -> Unit,
    onNavigateToTurni: (() -> Unit)? = null,
    onFeedback: (ActionFeedback) -> Unit,
) {
    if (definitions.isEmpty()) {
        CloudCard(
            title = "Nessun modello di turno",
            subtitle = "Crea almeno un turno nel lavoro attivo per configurare le statistiche.",
            accent = ExpressivePastel.LAVENDER,
        ) {
            if (onNavigateToTurni != null) {
                CloudButton(
                    label = "Crea un turno",
                    icon = "plus",
                    onClick = onNavigateToTurni,
                )
            }
        }
        return
    }
    val preferences = ShiftRepository.appPreferences
    val holidayActive = preferences.excludeVacationOnHolidays
    val excludedDefinitions = definitions.filter { it.id in preferences.holidayExcludedShiftIds }
    val definitionIndex = remember(definitions) {
        definitions.withIndex().associate { (index, def) -> def.id to index }
    }
    val visibleRules = rules
        .mapNotNull { rule -> definitions.find { it.id == rule.displayedShiftId }?.let { rule to it } }
        .sortedBy { (_, def) -> definitionIndex[def.id] ?: Int.MAX_VALUE }
    val showWork = definitions.map { it.workProfileId }.distinct().size > 1

    SectionLabel(
        text = "Esclusioni nei festivi",
        accent = ExpressivePastel.CORAL,
    )
    HolidayExclusionCard(
        active = holidayActive,
        excludedDefinitions = excludedDefinitions,
        onToggle = { enabled ->
            val current = ShiftRepository.appPreferences
            val nextExcluded = holidayExclusionSelectionForToggle(
                current.holidayExcludedShiftIds,
                definitions,
                enabled,
            )
            ShiftRepository.appPreferences = current.copy(
                excludeVacationOnHolidays = enabled,
                holidayExcludedShiftIds = nextExcluded,
            )
            ShiftRepository.notifyDataChanged()
            onFeedback(
                ActionFeedback(
                    when {
                        !enabled -> "Esclusione festivi in pausa"
                        nextExcluded.isEmpty() -> "Attivata · scegli i turni"
                        else -> "Esclusione festivi attivata"
                    },
                ),
            )
        },
        onEdit = onEditHolidays,
    )

    Spacer(Modifier.height(6.dp))

    SectionLabel(
        text = "Sostituzioni di turno",
        trailing = if (visibleRules.isEmpty()) null else "${visibleRules.size}",
        accent = ExpressivePastel.LAVENDER,
    )

    AnimatedContent(
        targetState = visibleRules.isEmpty(),
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith
                fadeOut(tween(150))
        },
        label = "substitutionSectionState",
    ) { empty ->
        if (empty) {
            SubstitutionEmptyCard()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleRules.forEach { (rule, visible) ->
                    key(rule.id) {
                        val originalDefinitions = definitions.filter { it.id in rule.originalShiftIds }
                        val workName = if (showWork) {
                            profiles.find { it.id == visible.workProfileId }?.name.orEmpty()
                        } else {
                            ""
                        }
                        SubstitutionRuleCard(
                            rule = rule,
                            visible = visible,
                            originals = originalDefinitions,
                            workName = workName,
                            onToggle = { enabled ->
                                ShiftRepository.saveStatisticsRule(rule.copy(enabled = enabled))
                                onFeedback(ActionFeedback(if (enabled) "Regola attivata" else "Regola in pausa"))
                            },
                            onEdit = { onEditRule(rule) },
                        )
                    }
                }
            }
        }
    }

    val configuredDisplayIds = rules.map { it.displayedShiftId }.toSet()
    val hasSameWorkPair = definitions.groupBy { it.workProfileId }.values.any { it.size > 1 }
    val canCreateRule = hasSameWorkPair && definitions.any { model ->
        model.id !in configuredDisplayIds &&
            definitions.count { it.workProfileId == model.workProfileId } > 1
    }

    CloudButton(
        label = "Nuova regola",
        icon = "plus",
        onClick = onCreateRule,
        enabled = canCreateRule,
    )
    if (!canCreateRule) {
        val hint = if (!hasSameWorkPair) {
            "Crea almeno due turni nello stesso lavoro per aggiungere una regola."
        } else {
            "Tutti i turni compatibili hanno già una regola."
        }
        Text(
            text = hint,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun HolidayExclusionCard(
    active: Boolean,
    excludedDefinitions: List<ShiftDefinition>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val badgeMotion = remember { Animatable(0f) }
    val actionMotion = remember { Animatable(0f) }
    var badgeJob by remember { mutableStateOf<Job?>(null) }
    var actionJob by remember { mutableStateOf<Job?>(null) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (!initialized) {
            initialized = true
            badgeJob = scope.launchPulse(badgeMotion, badgeJob, 560)
        } else {
            badgeJob = scope.launchPulse(badgeMotion, badgeJob, 520)
        }
    }

    val stripAlpha by animateFloatAsState(
        targetValue = if (active || excludedDefinitions.isEmpty()) 1f else 0.58f,
        animationSpec = tween(200),
        label = "holidayStripAlpha",
    )

    CloudCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    badgeJob = scope.launchPulse(badgeMotion, badgeJob, 520)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PastelIconBadge(
                pastel = ExpressivePastel.CORAL,
                icon = "calendar-heart",
                size = 44.dp,
                iconSize = 21.dp,
                progress = badgeMotion.value,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Escludi nei giorni festivi",
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                StatisticsStatusPill(
                    active = active,
                    activeText = "Attiva",
                    inactiveText = "In pausa",
                )
            }
            TurniSwitch(
                checked = active,
                onCheckedChange = onToggle,
            )
        }

        val stripShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .tactileCardClickable(shape = stripShape, pressedScale = 0.985f, role = Role.Button) {
                    actionJob = scope.launchPulse(actionMotion, actionJob, 420)
                    onEdit()
                }
                .background(ObsidianSurfaceContainer),
        ) {
            AnimatedContent(
                targetState = excludedDefinitions,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.97f)) togetherWith
                        fadeOut(tween(140))
                },
                label = "holidayExcludedStrip",
            ) { defs ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .graphicsLayer { alpha = stripAlpha }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PastelPill(
                        text = "Festivo",
                        color = ExpressivePastel.CORAL.sourceColor,
                        solid = true,
                    )
                    Text(
                        text = "×",
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                    )
                    if (defs.isEmpty()) {
                        Text(
                            text = "Nessun turno selezionato",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        ShiftChipStrip(definitions = defs)
                    }
                }
            }
        }

        val actionShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .tactileCardClickable(shape = actionShape, pressedScale = 0.98f, role = Role.Button) {
                    actionJob = scope.launchPulse(actionMotion, actionJob, 420)
                    onEdit()
                }
                .background(ObsidianSurfaceContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SiteIconMotion(
                name = "pencil",
                description = null,
                modifier = Modifier.size(16.dp),
                tint = BrandInk,
            ) {
                actionMotion.value
            }
            Text(
                text = "Scegli turni",
                color = BrandInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = excludedDefinitions.isNotEmpty(),
                enter = fadeIn(tween(180)) + scaleIn(tween(180)),
                exit = fadeOut(tween(140)) + scaleOut(tween(140)),
            ) {
                Text(
                    text = "${excludedDefinitions.size}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SiteIconMotion(
                name = "chevron-right",
                description = null,
                modifier = Modifier.size(16.dp),
                tint = BrandInk,
            ) {
                actionMotion.value
            }
        }
    }
}

@Composable
private fun SubstitutionEmptyCard() {
    val scope = rememberCoroutineScope()
    val swapMotion = remember { Animatable(0f) }
    val arrowMotion = remember { Animatable(0f) }
    var motionJob by remember { mutableStateOf<Job?>(null) }

    fun replay() {
        motionJob?.cancel()
        motionJob = scope.launch {
            launch {
                swapMotion.snapTo(0f)
                swapMotion.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                swapMotion.snapTo(0f)
            }
            arrowMotion.snapTo(0f)
            arrowMotion.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
            arrowMotion.snapTo(0f)
        }
    }

    LaunchedEffect(Unit) { replay() }

    CloudCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { replay() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PastelIconBadge(
                pastel = ExpressivePastel.LAVENDER,
                icon = "swap",
                size = 44.dp,
                iconSize = 21.dp,
                progress = swapMotion.value,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Conteggio dopo un cambio",
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = "Accredita il turno precedente al posto del nuovo",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianSurfaceContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { replay() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PastelPill(
                    text = "Turno nuovo",
                    color = ExpressivePastel.LAVENDER.sourceColor,
                    solid = false,
                )
                SiteIconMotion(
                    name = "arrow-right",
                    description = null,
                    modifier = Modifier.size(14.dp),
                    tint = TextSecondary,
                ) {
                    arrowMotion.value
                }
                ContaBadge(active = true)
                PastelPill(
                    text = "Precedente",
                    color = ExpressivePastel.PEARL.sourceColor,
                    solid = false,
                )
            }
        }
    }
}

@Composable
private fun SubstitutionRuleCard(
    rule: StatisticsRule,
    visible: ShiftDefinition,
    originals: List<ShiftDefinition>,
    workName: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val badgeScale = remember(rule.id) { Animatable(1f) }
    val arrowMotion = remember(rule.id) { Animatable(0f) }
    val editMotion = remember(rule.id) { Animatable(0f) }
    var editJob by remember { mutableStateOf<Job?>(null) }
    var lastEnabled by remember(rule.id) { mutableStateOf(rule.enabled) }
    var initialized by remember(rule.id) { mutableStateOf(false) }

    LaunchedEffect(rule.enabled, rule.originalShiftIds) {
        if (!initialized) {
            initialized = true
            arrowMotion.snapTo(0f)
            arrowMotion.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
            arrowMotion.snapTo(0f)
        } else {
            if (rule.enabled != lastEnabled) {
                lastEnabled = rule.enabled
                launch {
                    badgeScale.snapTo(1f)
                    badgeScale.animateTo(
                        targetValue = if (rule.enabled) 1.09f else 0.94f,
                        animationSpec = tween(90),
                    )
                    badgeScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.62f, stiffness = 460f),
                    )
                }
            }
            arrowMotion.snapTo(0f)
            arrowMotion.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
            arrowMotion.snapTo(0f)
        }
    }

    val openEditor = {
        editJob = scope.launchPulse(editMotion, editJob, 400)
        onEdit()
    }

    CloudCard(
        modifier = Modifier.tactileCardClickable(
            shape = RoundedCornerShape(23.dp),
            pressedScale = 0.985f,
            role = Role.Button,
            onClick = openEditor,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PastelBadge(
                color = Color(visible.colorHex),
                size = 42.dp,
                cornerRadius = 13.dp,
                definitionId = visible.id,
                modifier = Modifier.graphicsLayer {
                    scaleX = badgeScale.value
                    scaleY = badgeScale.value
                },
            ) { ink ->
                Text(
                    text = visible.shortCode.ifBlank { visible.name.take(3) }.take(4),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 1,
                    softWrap = false,
                )
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = visible.name,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatisticsStatusPill(
                        active = rule.enabled,
                        activeText = "Attiva",
                        inactiveText = "In pausa",
                    )
                    if (workName.isNotBlank()) {
                        Text(
                            text = workName,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            TurniSwitch(
                checked = rule.enabled,
                onCheckedChange = onToggle,
            )
        }

        StatisticsEquationBox(
            visible = visible,
            originals = originals,
            active = rule.enabled,
            arrowProgress = arrowMotion.value,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${originals.size} ${if (originals.size == 1) "turno sostituito" else "turni sostituiti"}",
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianSurfaceContainer)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SiteIconMotion(
                    name = "pencil",
                    description = null,
                    modifier = Modifier.size(12.dp),
                    tint = BrandInk,
                ) {
                    editMotion.value
                }
                Text(
                    text = "Modifica",
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandInk,
                )
                SiteIcon(
                    name = "chevron-right",
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = BrandInk,
                )
            }
        }
    }
}

@Composable
private fun StatisticsEquationBox(
    visible: ShiftDefinition,
    originals: List<ShiftDefinition>,
    active: Boolean,
    arrowProgress: Float,
    modifier: Modifier = Modifier,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0.6f,
        animationSpec = tween(200),
        label = "statisticsEquationAlpha",
    )
    val arrowTint by animateColorAsState(
        targetValue = if (active) BrandInk else TextSecondary,
        animationSpec = tween(200),
        label = "statisticsEquationArrowTint",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .graphicsLayer { alpha = contentAlpha }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ShiftCodePill(visible)
            SiteIconMotion(
                name = "arrow-right",
                description = null,
                modifier = Modifier.size(14.dp),
                tint = arrowTint,
            ) {
                arrowProgress
            }
            ContaBadge(active = active)
            if (originals.isEmpty()) {
                Text(
                    text = "Nessun turno",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            } else {
                originals.forEach { origin ->
                    key(origin.id) {
                        ShiftCodePill(origin)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContaBadge(active: Boolean) {
    val fill by animateColorAsState(
        targetValue = if (active) ObsidianPrimaryContainer else ObsidianSurface,
        animationSpec = tween(180),
        label = "contaBadgeFill",
    )
    val ink by animateColorAsState(
        targetValue = if (active) BrandInk else TextSecondary,
        animationSpec = tween(180),
        label = "contaBadgeInk",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = "conta",
            color = ink,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun StatisticsStatusPill(
    active: Boolean,
    activeText: String,
    inactiveText: String,
) {
    val fill by animateColorAsState(
        targetValue = if (active) ObsidianPrimaryContainer else ObsidianSurfaceContainer,
        animationSpec = tween(180),
        label = "statisticsStatusPillFill",
    )
    val ink by animateColorAsState(
        targetValue = if (active) BrandInk else TextSecondary,
        animationSpec = tween(180),
        label = "statisticsStatusPillInk",
    )
    val dotColor by animateColorAsState(
        targetValue = if (active) BrandInk else TextMuted,
        animationSpec = tween(180),
        label = "statisticsStatusPillDot",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Text(
            text = if (active) activeText else inactiveText,
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
private fun ShiftChipStrip(
    definitions: List<ShiftDefinition>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        definitions.forEach { def ->
            key(def.id) {
                ShiftCodePill(def)
            }
        }
    }
}

@Composable
private fun ShiftCodePill(definition: ShiftDefinition) {
    val dark = TurniThemeState.dark
    val resolvedColor = remember(definition.colorHex, definition.id, dark) {
        shiftDisplayColor(Color(definition.colorHex), dark = dark, definitionId = definition.id)
    }
    val code = definition.shortCode.ifBlank { definition.name.take(3) }.take(4)
    val label = if (code.equals(definition.name.trim(), ignoreCase = true)) {
        definition.name
    } else {
        "$code · ${definition.name}"
    }
    PastelPill(
        text = label,
        color = resolvedColor,
        solid = true,
    )
}

@Composable
private fun SubstitutionRuleEditor(
    draft: StatisticsRule,
    definitions: List<ShiftDefinition>,
    rules: List<StatisticsRule>,
    profiles: List<WorkProfile>,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    var displayed by remember(draft.id) { mutableStateOf(draft.displayedShiftId) }
    var originals by remember(draft.id) { mutableStateOf(draft.originalShiftIds) }
    var choosing by remember(draft.id) { mutableStateOf(draft.displayedShiftId.isBlank()) }

    val selected = definitions.find { it.id == displayed }
    val used = rules.filterNot { it.id == draft.id }.map { it.displayedShiftId }.toSet()
    val availableDisplay = definitions.filterNot { it.id in used }
    val showWork = definitions.map { it.workProfileId }.distinct().size > 1
    val candidateOriginals = definitions.filter {
        it.id != displayed && it.workProfileId == selected?.workProfileId
    }
    val validOriginals = originals.filter { id -> candidateOriginals.any { it.id == id } }.toSet()
    val selectedOriginals = candidateOriginals.filter { it.id in validOriginals }

    val chevronAngle by animateFloatAsState(
        targetValue = if (choosing) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "substitutionSelectorChevron",
    )

    CloudCard(
        title = "1 · Turno nel calendario",
        accent = ExpressivePastel.LAVENDER,
    ) {
        val selectorShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .tactileCardClickable(shape = selectorShape, pressedScale = 0.985f, role = Role.Button) {
                    choosing = !choosing
                }
                .background(ObsidianSurfaceContainer)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selected != null) {
                PastelBadge(
                    color = Color(selected.colorHex),
                    size = 34.dp,
                    cornerRadius = 10.dp,
                    definitionId = selected.id,
                ) { ink ->
                    Text(
                        text = selected.shortCode.ifBlank { selected.name.take(3) }.take(4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ink,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = selected.name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showWork) {
                        val workName = profiles.find { it.id == selected.workProfileId }?.name.orEmpty()
                        if (workName.isNotBlank()) {
                            Text(
                                text = workName,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                PastelIconBadge(
                    pastel = ExpressivePastel.LAVENDER,
                    icon = "clock",
                    size = 34.dp,
                    iconSize = 17.dp,
                )
                Text(
                    text = "Seleziona un turno",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
            }
            SiteIcon(
                name = "chevron-down",
                contentDescription = if (choosing) "Chiudi elenco" else "Scegli turno",
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronAngle),
                tint = BrandInk,
            )
        }

        AnimatedVisibility(
            visible = choosing,
            enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) +
                fadeIn(tween(180)),
            exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) +
                fadeOut(tween(140)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsDivider()
                if (availableDisplay.isEmpty()) {
                    Text(
                        text = "Nessun altro turno disponibile.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = TextSecondary,
                    )
                } else {
                    ShiftModelGrid(
                        models = availableDisplay,
                        selectedIds = setOfNotNull(displayed.takeIf { it.isNotBlank() }),
                        showWork = showWork,
                        profiles = profiles,
                    ) { model ->
                        if (displayed != model.id) {
                            displayed = model.id
                            originals = emptySet()
                        }
                        choosing = false
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = selected != null,
        enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) +
            fadeIn(tween(200)),
        exit = shrinkVertically() + fadeOut(tween(150)),
    ) {
        CloudCard(
            title = "2 · Conta invece come",
            accent = ExpressivePastel.LAVENDER,
        ) {
            if (candidateOriginals.isEmpty()) {
                Text(
                    text = "Nessun altro turno disponibile per questo lavoro.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary,
                )
            } else {
                ShiftModelGrid(
                    models = candidateOriginals,
                    selectedIds = validOriginals,
                    showWork = false,
                    profiles = profiles,
                ) { model ->
                    originals = if (model.id in validOriginals) {
                        validOriginals - model.id
                    } else {
                        validOriginals + model.id
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = selected != null && selectedOriginals.isNotEmpty(),
        enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) +
            fadeIn(tween(200)),
        exit = shrinkVertically() + fadeOut(tween(150)),
    ) {
        val previewArrowMotion = remember { Animatable(0f) }
        LaunchedEffect(displayed, validOriginals) {
            previewArrowMotion.snapTo(0f)
            previewArrowMotion.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
            previewArrowMotion.snapTo(0f)
        }
        CloudCard(
            title = "Anteprima",
            accent = ExpressivePastel.MINT,
        ) {
            if (selected != null) {
                StatisticsEquationBox(
                    visible = selected,
                    originals = selectedOriginals,
                    active = true,
                    arrowProgress = previewArrowMotion.value,
                )
                Text(
                    text = "Se ${selected.name} sostituisce ${selectedOriginals.joinToString(" o ") { it.name }}, conta il turno sostituito.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary,
                )
            }
        }
    }

    CloudButton(
        label = "Salva regola",
        icon = "check",
        onClick = {
            ShiftRepository.saveStatisticsRule(
                draft.copy(displayedShiftId = displayed, originalShiftIds = validOriginals),
            )
            onSaved()
        },
        enabled = selected != null && validOriginals.isNotEmpty(),
    )
    if (rules.any { it.id == draft.id }) {
        CloudButton(
            label = "Elimina regola",
            icon = "trash-2",
            onClick = onDeleted,
            destructive = true,
        )
    }
}

@Composable
private fun HolidayStatisticsRuleEditor(
    definitions: List<ShiftDefinition>,
    profiles: List<WorkProfile>,
    onSaved: () -> Unit,
) {
    val initial = remember { ShiftRepository.appPreferences }
    var selected by remember { mutableStateOf(initial.holidayExcludedShiftIds) }
    var enabled by remember { mutableStateOf(initial.excludeVacationOnHolidays) }
    val scope = rememberCoroutineScope()
    val editorBadgeMotion = remember { Animatable(0f) }
    var editorBadgeJob by remember { mutableStateOf<Job?>(null) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!initialized) {
            initialized = true
            editorBadgeJob = scope.launchPulse(editorBadgeMotion, editorBadgeJob, 560)
        } else {
            editorBadgeJob = scope.launchPulse(editorBadgeMotion, editorBadgeJob, 520)
        }
    }

    val toggleLabelColor by animateColorAsState(
        targetValue = if (enabled) TextPrimary else TextSecondary,
        animationSpec = tween(180),
        label = "holidayEditorToggleLabel",
    )
    val showWork = definitions.map { it.workProfileId }.distinct().size > 1

    CloudCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    editorBadgeJob = scope.launchPulse(editorBadgeMotion, editorBadgeJob, 520)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PastelIconBadge(
                pastel = ExpressivePastel.CORAL,
                icon = "calendar-heart",
                size = 44.dp,
                iconSize = 21.dp,
                progress = editorBadgeMotion.value,
            )
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
                    text = "Escludi nei festivi",
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = toggleLabelColor,
                )
                Text(
                    text = if (enabled) {
                        when (selected.size) {
                            0 -> "Seleziona almeno un turno"
                            1 -> "1 turno escluso"
                            else -> "${selected.size} turni esclusi"
                        }
                    } else {
                        "In pausa"
                    },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = TextSecondary,
                )
            }
            TurniSwitch(
                checked = enabled,
                onCheckedChange = { enabled = it },
            )
        }
    }

    CloudCard(
        title = "Turni da escludere",
        accent = ExpressivePastel.CORAL,
    ) {
        if (definitions.isEmpty()) {
            Text(
                text = "Nessun turno disponibile.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = TextSecondary,
            )
        } else {
            ShiftModelGrid(
                models = definitions,
                selectedIds = selected,
                showWork = showWork,
                profiles = profiles,
            ) { model ->
                selected = if (model.id in selected) {
                    selected - model.id
                } else {
                    selected + model.id
                }
            }
        }
    }

    AnimatedVisibility(
        visible = enabled && selected.isEmpty(),
        enter = expandVertically(spring()) + fadeIn(tween(180)),
        exit = shrinkVertically() + fadeOut(tween(140)),
    ) {
        Text(
            text = "Seleziona almeno un turno oppure metti in pausa l'esclusione.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    CloudButton(
        label = "Salva regola",
        icon = "check",
        onClick = {
            ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(
                excludeVacationOnHolidays = enabled,
                holidayExcludedShiftIds = selected,
            )
            ShiftRepository.notifyDataChanged()
            onSaved()
        },
        enabled = !enabled || selected.isNotEmpty(),
    )
}

@Composable
private fun ShiftModelGrid(
    models: List<ShiftDefinition>,
    selectedIds: Set<String>,
    showWork: Boolean,
    profiles: List<WorkProfile>,
    onSelect: (ShiftDefinition) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        models.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { model ->
                    val workName = if (showWork) {
                        profiles.find { it.id == model.workProfileId }?.name.orEmpty()
                    } else {
                        ""
                    }
                    Box(Modifier.weight(1f)) {
                        StatisticsModelTile(
                            model = model,
                            selected = model.id in selectedIds,
                            workName = workName,
                            onClick = { onSelect(model) },
                        )
                    }
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatisticsModelTile(
    model: ShiftDefinition,
    selected: Boolean,
    workName: String,
    onClick: () -> Unit,
) {
    val dark = TurniThemeState.dark
    val themeStyle = TurniThemeState.style
    val surface = ObsidianSurface
    val container = ObsidianSurfaceContainer
    val tone = remember(model.colorHex, model.id, dark, themeStyle, surface) {
        shiftColorStyle(
            source = Color(model.colorHex),
            dark = dark,
            backdrop = surface,
            definitionId = model.id,
        )
    }
    val tileShape = RoundedCornerShape(15.dp)
    val selectedFill = remember(container, tone, dark) {
        lerp(container, tone.codeFill, if (dark) 0.28f else 0.42f)
    }
    val fill by animateColorAsState(
        targetValue = if (selected) selectedFill else container,
        animationSpec = tween(200),
        label = "statisticsModelTileFill",
    )
    val targetTitleColor = remember(selected, selectedFill, container, dark) {
        if (selected) readableInk(selectedFill, TextPrimary) else TextSecondary
    }
    val titleColor by animateColorAsState(
        targetValue = targetTitleColor,
        animationSpec = tween(200),
        label = "statisticsModelTileTitle",
    )
    val workColor = remember(fill, dark) { readableInk(fill, TextMuted).copy(alpha = 0.8f) }

    val interactionSource = remember(model.id) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "statisticsModelTilePress",
    )
    val selectionPop = remember(model.id) { Animatable(1f) }
    val checkMotion = remember(model.id) { Animatable(0f) }
    var wasSelected by remember(model.id) { mutableStateOf(selected) }

    LaunchedEffect(selected) {
        if (selected && !wasSelected) {
            launch {
                selectionPop.snapTo(0.94f)
                selectionPop.animateTo(1.035f, tween(durationMillis = 110, easing = FastOutSlowInEasing))
                selectionPop.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 460f))
            }
            checkMotion.snapTo(0f)
            checkMotion.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
            checkMotion.snapTo(0f)
        } else if (!selected) {
            selectionPop.snapTo(1f)
            checkMotion.snapTo(0f)
        }
        wasSelected = selected
    }

    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val combinedScale = pressScale * selectionPop.value
                scaleX = combinedScale
                scaleY = combinedScale
            }
            .heightIn(min = 60.dp)
            .clip(tileShape)
            .background(fill)
            .semantics {
                this.selected = selected
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PastelBadge(
            color = Color(model.colorHex),
            size = 34.dp,
            cornerRadius = 10.dp,
            definitionId = model.id,
        ) { ink ->
            Text(
                text = model.shortCode.ifBlank { model.name.take(3) }.take(4),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.name,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (workName.isNotBlank()) {
                Text(
                    text = workName,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    color = workColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f)),
            exit = fadeOut(tween(140)) + scaleOut(tween(140)),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(tone.codeFill),
                contentAlignment = Alignment.Center,
            ) {
                SiteIconMotion("check", null, Modifier.size(12.dp), tint = tone.codeInk) {
                    checkMotion.value
                }
            }
        }
    }
}
