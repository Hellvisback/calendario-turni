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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
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
                    CloudCard(
                        title = "Cambia il conteggio, non il calendario",
                        subtitle = "Qui scegli quali turni usare nei totali, nelle ore e nei grafici. Il nome e il colore che vedi sul calendario restano gli stessi.",
                    ) {
                        SettingsDivider()
                        Column(
                            modifier = Modifier.animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Turno originale: conta l’ex turno al posto di quello visibile.\nFerie nei festivi: non conta i modelli scelti nelle feste abilitate.\nPuoi usare entrambe: prima si sceglie quale turno contare, poi si verifica l’esclusione nei festivi.",
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = TextSecondary,
                            )
                        }
                    }

                    val preferences = ShiftRepository.appPreferences
                    val holidayActive = preferences.excludeVacationOnHolidays
                    val holidayStatusColor by animateColorAsState(
                        targetValue = if (holidayActive) BrandInk else TextSecondary,
                        animationSpec = tween(180),
                        label = "holidayRuleStatusColor",
                    )
                    CloudCard(
                        title = "Ferie nei giorni festivi",
                        subtitle = "Esempio: Ferie a Natale resta sul calendario ma non aumenta il totale delle ferie. Vale per i modelli che scegli e per le ricorrenze attivate in Festività.",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
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
                                    text = if (holidayActive) "Regola attiva" else "Regola disattivata",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = holidayStatusColor,
                                )
                                Text(
                                    text = definitions
                                        .filter { it.id in preferences.holidayExcludedShiftIds }
                                        .joinToString { it.name }
                                        .ifBlank { "Nessun modello selezionato" },
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = TextSecondary,
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
                        SettingsDivider()
                        CloudButton("Modifica regola", "pencil", { editingHolidays = true }, secondary = true)
                    }

                    if (rules.isEmpty()) {
                        CloudCard(
                            title = "Turno originale",
                            subtitle = "Esempio: sostituisci Riposo con Ferie per vedere un periodo continuo sul calendario. Con questa regola, nelle statistiche quel giorno resta Riposo.",
                        ) {
                            SettingsDivider()
                            Text(
                                text = "Funziona se il turno originale proviene da una rotazione ed è ancora registrato come ex turno. Se prima cancelli con la gomma, questa provenienza viene rimossa.",
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
                                val ruleStatusColor by animateColorAsState(
                                    targetValue = if (rule.enabled) BrandInk else TextSecondary,
                                    animationSpec = tween(180),
                                    label = "ruleStatusColor",
                                )
                                CloudCard(
                                    title = "Turno originale · ${visible.name}",
                                    subtitle = "Quando ${visible.name} copre uno dei modelli qui sotto, conta il turno originale al suo posto. Senza un ex turno registrato, conta quello visibile.",
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(visible.color),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = visible.shortCode,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = catalogInk(visible.color),
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
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = if (rule.enabled) "Turni originali ammessi:" else "Regola in pausa · turni ammessi:",
                                                fontSize = 12.sp,
                                                color = ruleStatusColor,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                text = definitions
                                                    .filter { it.id in rule.originalShiftIds }
                                                    .joinToString { it.name },
                                                fontSize = 14.sp,
                                                lineHeight = 20.sp,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
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
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    definitions.filterNot { it.id in used }.chunked(2).forEach { pair ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            subtitle = "Scegli cosa contare al suo posto, per esempio Riposo e Smonto. La regola vale solo se uno di questi modelli è registrato come ex turno di quel giorno.",
                        ) {
                            if (candidateOriginals.isEmpty()) {
                                Text(
                                    text = "Nessun altro modello disponibile per questo lavoro.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                )
                            } else {
                                candidateOriginals.chunked(2).forEach { pair ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        CloudCard("La tua regola") {
                            Column(
                                modifier = Modifier.animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                ),
                            ) {
                                Text(
                                    text = "${selected?.name.orEmpty()} sopra ${definitions.filter { it.id in originals }.joinToString { it.name }} → conta il turno originale.",
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

    CloudCard(
        title = "1 · Attiva la regola",
        subtitle = "I turni scelti restano nel calendario, ma non vengono conteggiati nelle festività abilitate in Impostazioni → Festività.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    fontWeight = FontWeight.Medium,
                    color = toggleLabelColor,
                )
                Text(
                    text = if (enabled) {
                        if (selected.isEmpty()) "Seleziona i modelli qui sotto" else "${selected.size} modelli esclusi nei festivi"
                    } else {
                        "Tutti i turni vengono conteggiati normalmente"
                    },
                    fontSize = 11.sp,
                    color = TextSecondary,
                )
            }
            TurniSwitch(checked = enabled, onCheckedChange = { enabled = it })
        }
    }

    CloudCard(
        title = "2 · Modelli da escludere",
        subtitle = "Scegli uno o più modelli, anche con nomi personalizzati. Nei giorni che non sono festivi continueranno a essere contati normalmente.",
    ) {
        definitions.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
    val tileShape = RoundedCornerShape(12.dp)
    val fill by animateColorAsState(
        targetValue = if (selected) ObsidianPrimarySubtle else ObsidianSurfaceContainer,
        animationSpec = tween(180),
        label = "statisticsModelTileFill",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) ObsidianPrimary else ObsidianBorderSubtle,
        animationSpec = tween(180),
        label = "statisticsModelTileBorder",
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) TextPrimary else TextSecondary,
        animationSpec = tween(180),
        label = "statisticsModelTileTitle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(tileShape)
            .background(fill)
            .border(1.dp, borderColor, tileShape)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .bouncyClickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(model.color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = model.shortCode,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = catalogInk(model.color),
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
            SiteIcon("check", null, Modifier.size(14.dp), tint = BrandInk)
        }
    }
}
