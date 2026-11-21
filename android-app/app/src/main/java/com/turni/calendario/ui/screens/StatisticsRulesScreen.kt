package com.turni.calendario.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.StatisticsRule
import com.turni.calendario.model.holidayExclusionSelectionForToggle
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.theme.ObsidianSurface
import com.turni.calendario.ui.theme.ObsidianSurfaceContainer
import com.turni.calendario.ui.theme.TextMuted
import com.turni.calendario.ui.theme.TextPrimary
import com.turni.calendario.ui.theme.TextSecondary
import com.turni.calendario.ui.theme.TurniThemeState
import com.turni.calendario.ui.theme.shiftColorStyle
import com.turni.calendario.ui.theme.shiftDisplayColor
import java.util.UUID

@Composable
fun StatisticsRulesScreen(onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<StatisticsRule?>(null) }
    var editingHolidays by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val definitions = ShiftRepository.shiftDefinitions.toList()
    val rules = ShiftRepository.statisticsRules.toList()
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
        editingHolidays -> "Ferie nei festivi"
        editing != null -> "Turno originale"
        else -> "Conteggio statistiche"
    }
    val subtitle = when {
        editingHolidays -> "Escludi alcuni turni dal totale nei giorni festivi"
        editing != null -> "Conta l’ex turno al posto del modello visibile"
        else -> "Scegli come contare sostituzioni e ferie nei festivi"
    }

    CloudFeatureScaffold(
        title = title,
        subtitle = subtitle,
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
                (fadeIn(tween(220)) + slideInHorizontally(
                    tween(240, easing = FastOutSlowInEasing),
                ) { if (enteringEditor) it / 6 else -it / 6 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(
                        tween(220, easing = FastOutSlowInEasing),
                    ) { if (enteringEditor) -it / 6 else it / 6 })
            },
            label = "statisticsRulesContent",
        ) { (draft, holidays) ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (holidays) {
                    HolidayStatisticsRuleEditor(definitions) {
                        editingHolidays = false
                        feedback = ActionFeedback("Regola salvata · statistiche aggiornate")
                    }
                } else if (draft == null) {
                    StatisticsRulesExplainerCard()

                    val preferences = ShiftRepository.appPreferences
                    val holidayActive = preferences.excludeVacationOnHolidays
                    val excludedDefinitions = definitions.filter { it.id in preferences.holidayExcludedShiftIds }

                    CloudCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PastelIconBadge(
                                pastel = ExpressivePastel.SAGE,
                                icon = "calendar-heart",
                                size = 44.dp,
                                iconSize = 21.dp,
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
                                    text = "Ferie nei giorni festivi",
                                    fontSize = 16.sp,
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                )
                                PastelPill(
                                    text = if (holidayActive) "Attiva" else "Disattivata",
                                    color = if (holidayActive) {
                                        ExpressivePastel.MINT.sourceColor
                                    } else {
                                        ExpressivePastel.PEARL.sourceColor
                                    },
                                    solid = holidayActive,
                                )
                            }
                            TurniSwitch(
                                checked = holidayActive,
                                onCheckedChange = { enabled ->
                                    val current = ShiftRepository.appPreferences
                                    ShiftRepository.appPreferences = current.copy(
                                        excludeVacationOnHolidays = enabled,
                                        holidayExcludedShiftIds = holidayExclusionSelectionForToggle(
                                            current.holidayExcludedShiftIds,
                                            definitions,
                                            enabled,
                                        ),
                                    )
                                    ShiftRepository.notifyDataChanged()
                                    feedback = ActionFeedback(if (enabled) "Regola attivata" else "Regola disattivata")
                                },
                            )
                        }
                        Text(
                            text = "I modelli scelti restano sul calendario, ma non aumentano i totali nei festivi attivi.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = TextSecondary,
                        )
                        if (excludedDefinitions.isNotEmpty()) {
                            ShiftChipStrip(definitions = excludedDefinitions)
                        } else {
                            Text(
                                text = "Nessun modello selezionato",
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = TextMuted,
                            )
                        }
                        SettingsDivider()
                        CloudButton("Modifica regola", "pencil", { editingHolidays = true }, secondary = true)
                    }

                    if (rules.isEmpty()) {
                        CloudCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PastelIconBadge(
                                    pastel = ExpressivePastel.MINT,
                                    icon = "layers",
                                    size = 44.dp,
                                    iconSize = 21.dp,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = "Turno originale",
                                        fontSize = 16.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        text = "Nessuna regola attiva",
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = TextSecondary,
                                    )
                                }
                            }
                            Text(
                                text = "Sostituisci ad esempio Riposo con Ferie sul calendario: con questa regola nelle statistiche resta contato Riposo se proviene da una rotazione.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = TextSecondary,
                            )
                        }
                    }

                    rules.forEach { rule ->
                        key(rule.id) {
                            val visible = definitions.find { it.id == rule.displayedShiftId }
                            if (visible != null) {
                                val originalDefinitions = definitions.filter { it.id in rule.originalShiftIds }
                                CloudCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        PastelBadge(
                                            color = Color(visible.colorHex),
                                            size = 44.dp,
                                            cornerRadius = 13.dp,
                                            definitionId = visible.id,
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
                                                text = "Turno originale · ${visible.name}",
                                                fontSize = 15.sp,
                                                lineHeight = 20.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                            )
                                            PastelPill(
                                                text = if (rule.enabled) "Attiva" else "In pausa",
                                                color = if (rule.enabled) {
                                                    ExpressivePastel.MINT.sourceColor
                                                } else {
                                                    ExpressivePastel.PEARL.sourceColor
                                                },
                                                solid = rule.enabled,
                                            )
                                        }
                                        TurniSwitch(
                                            checked = rule.enabled,
                                            onCheckedChange = { enabled ->
                                                ShiftRepository.saveStatisticsRule(rule.copy(enabled = enabled))
                                                feedback = ActionFeedback(if (enabled) "Regola attivata" else "Regola disattivata")
                                            },
                                        )
                                    }
                                    Text(
                                        text = "Quando ${visible.name} copre uno di questi ex turni, nei totali conta l’originale:",
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp,
                                        color = TextSecondary,
                                    )
                                    if (originalDefinitions.isNotEmpty()) {
                                        ShiftChipStrip(definitions = originalDefinitions)
                                    }
                                    SettingsDivider()
                                    CloudButton("Modifica regola", "pencil", { editing = rule }, secondary = true)
                                }
                            }
                        }
                    }

                    CloudButton(
                        label = "Aggiungi regola sul turno originale",
                        icon = "plus",
                        onClick = { editing = StatisticsRule(UUID.randomUUID().toString(), "", emptySet()) },
                        enabled = definitions.size > 1,
                    )
                    if (definitions.size < 2) {
                        Text(
                            text = "Crea almeno due modelli di turno per aggiungere una regola.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = TextSecondary,
                        )
                    }
                } else {
                    var displayed by remember(draft.id) { mutableStateOf(draft.displayedShiftId) }
                    var originals by remember(draft.id) { mutableStateOf(draft.originalShiftIds) }
                    var choosing by remember(draft.id) { mutableStateOf(draft.displayedShiftId.isBlank()) }
                    val selected = definitions.find { it.id == displayed }
                    val used = rules.filterNot { it.id == draft.id }.map { it.displayedShiftId }.toSet()

                    CloudCard(
                        title = "1 · Il turno che inserisci sopra",
                        subtitle = "Scegli il modello che usi per sostituire un turno originale, per esempio Ferie.",
                        accent = ExpressivePastel.MINT,
                    ) {
                        CloudButton(
                            label = selected?.name ?: "Seleziona un turno",
                            icon = if (choosing) "chevron-up" else "chevron-down",
                            onClick = { choosing = !choosing },
                            secondary = true,
                        )
                        AnimatedVisibility(
                            visible = choosing,
                            enter = expandVertically(spring()) + fadeIn(tween(180)),
                            exit = shrinkVertically() + fadeOut(tween(150)),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsDivider()
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    definitions.filterNot { it.id in used }.chunked(2).forEach { pair ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            pair.forEach { model ->
                                                Box(Modifier.weight(1f)) {
                                                    StatisticsModelTile(
                                                        model = model,
                                                        selected = model.id == displayed,
                                                        showWork = definitions.map { it.workProfileId }.distinct().size > 1,
                                                    ) {
                                                        if (displayed != model.id) {
                                                            displayed = model.id
                                                            originals = emptySet()
                                                        }
                                                        choosing = false
                                                    }
                                                }
                                            }
                                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = selected != null,
                        enter = expandVertically(spring()) + fadeIn(tween(200)),
                        exit = shrinkVertically() + fadeOut(tween(150)),
                    ) {
                        val candidateOriginals = definitions.filter {
                            it.id != displayed && it.workProfileId == selected?.workProfileId
                        }
                        CloudCard(
                            title = "2 · Gli ex turni da contare",
                            subtitle = "Scegli cosa contare al suo posto. La regola vale solo se uno di questi modelli è registrato come ex turno.",
                            accent = ExpressivePastel.SAGE,
                        ) {
                            if (candidateOriginals.isEmpty()) {
                                Text(
                                    text = "Nessun altro modello disponibile per questo lavoro.",
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = TextSecondary,
                                )
                            } else {
                                candidateOriginals.chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pair.forEach { model ->
                                            Box(Modifier.weight(1f)) {
                                                StatisticsModelTile(
                                                    model = model,
                                                    selected = model.id in originals,
                                                    showWork = false,
                                                ) {
                                                    originals = if (model.id in originals) {
                                                        originals - model.id
                                                    } else {
                                                        originals + model.id
                                                    }
                                                }
                                            }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = selected != null && originals.isNotEmpty(),
                        enter = expandVertically(spring()) + fadeIn(tween(200)),
                        exit = shrinkVertically() + fadeOut(tween(150)),
                    ) {
                        val selectedOriginals = definitions.filter { it.id in originals }
                        CloudCard(
                            title = "La tua regola",
                            accent = ExpressivePastel.MINT,
                        ) {
                            Column(
                                modifier = Modifier.animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (selected != null) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        ShiftCodePill(selected)
                                        SiteIcon(
                                            name = "arrow-right",
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = TextSecondary,
                                        )
                                        selectedOriginals.forEach { origin ->
                                            ShiftCodePill(origin)
                                        }
                                    }
                                }
                                Text(
                                    text = "${selected?.name.orEmpty()} sopra ${selectedOriginals.joinToString { it.name }} → conta il turno originale.",
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = TextPrimary,
                                )
                            }
                        }
                    }

                    CloudButton(
                        label = "Salva regola",
                        icon = "check",
                        onClick = {
                            ShiftRepository.saveStatisticsRule(
                                draft.copy(displayedShiftId = displayed, originalShiftIds = originals),
                            )
                            editing = null
                            feedback = ActionFeedback("Regola salvata · statistiche aggiornate")
                        },
                        enabled = selected != null && originals.isNotEmpty(),
                    )
                    if (rules.any { it.id == draft.id }) {
                        CloudButton(
                            label = "Elimina regola",
                            icon = "trash-2",
                            onClick = {
                                ShiftRepository.removeStatisticsRule(draft.id)
                                editing = null
                                feedback = ActionFeedback("Regola eliminata", true)
                            },
                            destructive = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsRulesExplainerCard() {
    val compactStack = LocalDensity.current.fontScale > 1.3f

    CloudCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PastelIconBadge(
                pastel = ExpressivePastel.MINT,
                icon = "report",
                size = 44.dp,
                iconSize = 21.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Cambia il conteggio, non il calendario",
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = "I turni sul calendario restano uguali.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary,
                )
            }
        }

        SettingsDivider()

        ExplainerExampleRow(
            stacked = compactStack,
            title = "Turno originale",
            description = "Conta l’ex turno al posto di quello sopra",
            pills = {
                PastelPill(text = "RIP", color = ExpressivePastel.PEARL.sourceColor, solid = true)
                SiteIcon(
                    name = "arrow-right",
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = TextSecondary,
                )
                PastelPill(text = "FER", color = ExpressivePastel.SAGE.sourceColor, solid = true)
            },
        )

        ExplainerExampleRow(
            stacked = compactStack,
            title = "Ferie nei festivi",
            description = "Esclude i modelli scelti nei giorni festivi",
            pills = {
                PastelPill(text = "25 dic", color = ExpressivePastel.CORAL.sourceColor, solid = false)
                Text(
                    text = "·",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                PastelPill(text = "FER", color = ExpressivePastel.SAGE.sourceColor, solid = true)
            },
        )
    }
}

@Composable
private fun ExplainerExampleRow(
    stacked: Boolean,
    title: String,
    description: String,
    pills: @Composable () -> Unit,
) {
    val containerModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(15.dp))
        .background(ObsidianSurfaceContainer)
        .padding(horizontal = 12.dp, vertical = 10.dp)

    if (stacked) {
        Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                pills()
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextSecondary,
                )
            }
        }
    } else {
        Row(
            modifier = containerModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                pills()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun ShiftChipStrip(definitions: List<ShiftDefinition>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        definitions.forEach { def ->
            ShiftCodePill(def)
        }
    }
}

@Composable
private fun ShiftCodePill(definition: ShiftDefinition) {
    val dark = TurniThemeState.dark
    val resolvedColor = remember(definition.colorHex, definition.id, dark) {
        shiftDisplayColor(Color(definition.colorHex), dark = dark, definitionId = definition.id)
    }
    PastelPill(
        text = "${definition.shortCode.ifBlank { definition.name.take(3) }.take(4)} · ${definition.name}",
        color = resolvedColor,
        solid = true,
    )
}

@Composable
private fun HolidayStatisticsRuleEditor(
    definitions: List<ShiftDefinition>,
    onSaved: () -> Unit,
) {
    val initial = remember { ShiftRepository.appPreferences }
    var selected by remember { mutableStateOf(initial.holidayExcludedShiftIds) }
    var enabled by remember { mutableStateOf(initial.excludeVacationOnHolidays) }
    val toggleLabelColor by animateColorAsState(
        targetValue = if (enabled) TextPrimary else TextSecondary,
        animationSpec = tween(180),
        label = "holidayEditorToggleLabel",
    )

    CloudCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PastelIconBadge(
                pastel = ExpressivePastel.SAGE,
                icon = "calendar-heart",
                size = 42.dp,
                iconSize = 20.dp,
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
                    text = "Escludi dal conteggio nei festivi",
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = toggleLabelColor,
                )
                Text(
                    text = if (enabled) {
                        if (selected.isEmpty()) "Seleziona i modelli qui sotto" else "${selected.size} modelli esclusi nei festivi"
                    } else {
                        "Tutti i turni vengono conteggiati normalmente"
                    },
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextSecondary,
                )
            }
            TurniSwitch(checked = enabled, onCheckedChange = { enabled = it })
        }
        SettingsDivider()
        Text(
            text = "I turni scelti restano nel calendario, ma non vengono conteggiati nelle festività abilitate in Impostazioni → Festività.",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = TextSecondary,
        )
    }

    CloudCard(
        title = "2 · Modelli da escludere",
        subtitle = "Scegli uno o più modelli, anche con nomi personalizzati. Nei giorni feriali continuano a essere contati.",
        accent = ExpressivePastel.SAGE,
    ) {
        definitions.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { model ->
                    Box(Modifier.weight(1f)) {
                        StatisticsModelTile(
                            model = model,
                            selected = model.id in selected,
                            showWork = definitions.map { it.workProfileId }.distinct().size > 1,
                        ) {
                            selected = if (model.id in selected) {
                                selected - model.id
                            } else {
                                selected + model.id
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    AnimatedVisibility(
        visible = enabled && selected.isEmpty(),
        enter = expandVertically(spring()) + fadeIn(tween(180)),
        exit = shrinkVertically() + fadeOut(tween(140)),
    ) {
        Text(
            text = "Seleziona almeno un modello oppure disattiva la regola.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = TextSecondary,
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
private fun StatisticsModelTile(
    model: ShiftDefinition,
    selected: Boolean,
    showWork: Boolean,
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
    val tileShape = RoundedCornerShape(14.dp)
    val fill by animateColorAsState(
        targetValue = if (selected) {
            lerp(container, tone.codeFill, if (dark) 0.20f else 0.28f)
        } else {
            container
        },
        animationSpec = tween(180),
        label = "statisticsModelTileFill",
    )
    val ringColor by animateColorAsState(
        targetValue = tone.codeFill.copy(alpha = if (selected) 0.92f else 0f),
        animationSpec = tween(180),
        label = "statisticsModelTileRing",
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) TextPrimary else TextSecondary,
        animationSpec = tween(180),
        label = "statisticsModelTileTitle",
    )
    val interactionSource = remember(model.id) { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(tileShape)
            .background(fill)
            .then(if (selected || ringColor.alpha > 0.01f) Modifier.border(2.dp, ringColor, tileShape) else Modifier)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                try {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                } catch (_: Exception) {
                }
                onClick()
            }
            .padding(horizontal = 9.dp, vertical = 8.dp),
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
            if (showWork) {
                Text(
                    text = ShiftRepository.workProfiles.find { it.id == model.workProfileId }?.name.orEmpty(),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(tween(160)) + scaleIn(tween(160)),
            exit = fadeOut(tween(140)) + scaleOut(tween(140)),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(tone.codeFill),
                contentAlignment = Alignment.Center,
            ) {
                SiteIcon("check", null, Modifier.size(12.dp), tint = tone.codeInk)
            }
        }
    }
}
