package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Native rendering of the approved collection cards, with the live catalog under editors. */
@Composable
fun TurniModelsScreen(
    onEditorActiveChanged: ((Boolean) -> Unit)? = null,
    initialTab: Int = 0,
    initialEditingRotation: ShiftRotation? = null,
    onClearInitialRotation: (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(initialTab.coerceIn(0, 3)) }
    var shift by remember { mutableStateOf<ShiftDefinition?>(null) }
    var cycle by remember { mutableStateOf<ShiftRotation?>(initialEditingRotation) }
    var work by remember { mutableStateOf<Pair<WorkProfile, Boolean>?>(null) }
    var creatingTag by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<DayTag?>(null) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val editing = shift != null || cycle != null || work != null || creatingTag || editingTag != null

    LaunchedEffect(editing) { onEditorActiveChanged?.invoke(editing) }
    DisposableEffect(Unit) { onDispose { onEditorActiveChanged?.invoke(false) } }
    LaunchedEffect(initialEditingRotation) {
        initialEditingRotation?.let {
            tab = 2
            cycle = it
            onClearInitialRotation?.invoke()
        }
    }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2600)
            feedback = null
        }
    }
    BackHandler(!editing) { onDismiss() }

    Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val backShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(backShape)
                        .background(ObsidianSurface)
                        .border(1.dp, ObsidianBorderSubtle, backShape)
                        .bouncyClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    SiteIcon(
                        name = "arrow-left",
                        contentDescription = "Torna al calendario",
                        modifier = Modifier.size(20.dp),
                        tint = TextPrimary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Modelli e turni",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Il tuo catalogo, in un unico posto",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            SlidingSegmentBar(
                items = listOf(
                    "Turni" to "clock",
                    "Lavori" to "work",
                    "Rotazioni" to "rotation",
                    "Etichette" to "tag",
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )

            Spacer(Modifier.height(12.dp))

            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    ) { width -> if (forward) width / 5 else -width / 5 } +
                        fadeIn(tween(durationMillis = 220, easing = FastOutSlowInEasing))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        ) { width -> if (forward) -width / 5 else width / 5 } +
                            fadeOut(tween(durationMillis = 160)))
                },
                label = "catalogTabContent",
            ) { current ->
                when (current) {
                    3 -> DayTagsCatalogContent(
                        showHeaderCard = false,
                        onEditTag = { editingTag = it },
                        onFeedback = { feedback = it },
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        when (current) {
                            0 -> {
                                val definitions = ShiftRepository.shiftDefinitions.toList()
                                if (definitions.isEmpty()) {
                                    item(key = "shifts-empty") {
                                        CloudCard("Nessun modello di turno") {
                                            Text(
                                                text = "Crea il primo turno dal pulsante qui sotto per iniziare a compilare il calendario.",
                                                color = TextSecondary,
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                            )
                                        }
                                    }
                                }
                                items(definitions, key = { it.id }) { definition ->
                                    SiteCollectionCard {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            SiteCodeBadge(definition.shortCode, definition.color)
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = definition.name,
                                                    color = TextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = definition.timeRangeFormatted +
                                                        if (definition.isAllDay) "" else " · ${definition.durationFormatted}",
                                                    fontSize = 11.sp,
                                                    color = TextSecondary,
                                                    modifier = Modifier.padding(top = 3.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            CatalogEditButton("Modifica ${definition.name}") {
                                                shift = definition
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> items(ShiftRepository.workProfiles.toList(), key = { it.id }) { profile ->
                                SiteCollectionCard {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(Color(profile.colorHex)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CatalogWorkIcon(
                                                key = profile.iconKey,
                                                modifier = Modifier.size(21.dp),
                                                tint = catalogInk(Color(profile.colorHex)),
                                            )
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = profile.name,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${profile.companyOrDepartment.ifBlank { "Profilo di lavoro" }} · ${profile.weeklyHours} h/settimana",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                modifier = Modifier.padding(top = 3.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = (if (profile.id == ShiftRepository.activeWorkProfileId) "Calendario attivo" else "Calendario separato") +
                                                    if (profile.enableAdvancedCalc) " · Parametri CCNL attivi" else "",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 10.dp),
                                    ) {
                                        CloudButton(
                                            label = "Modifica",
                                            icon = "edit",
                                            onClick = { work = profile to false },
                                            modifier = Modifier.weight(1f),
                                            secondary = true,
                                        )
                                        CloudButton(
                                            label = "Apri calendario",
                                            icon = "calendar",
                                            onClick = {
                                                ShiftRepository.setActiveWorkProfile(profile.id)
                                                onDismiss()
                                            },
                                            modifier = Modifier.weight(1.3f),
                                        )
                                    }
                                }
                            }
                            2 -> {
                                val rotations = ShiftRepository.rotations.toList()
                                if (rotations.isEmpty()) {
                                    item(key = "rotations-empty") {
                                        CloudCard("Nessuna rotazione") {
                                            Text(
                                                text = "Crea una sequenza di turni ricorrente dal pulsante qui sotto per applicarla rapidamente al calendario.",
                                                color = TextSecondary,
                                                fontSize = 13.sp,
                                                lineHeight = 19.sp,
                                            )
                                        }
                                    }
                                }
                                items(rotations, key = { it.id }) { rotation ->
                                    SiteCollectionCard {
                                        val preferred = rotation.id == ShiftRepository.defaultRotationId
                                        val phase = remember { Animatable(0f) }
                                        val scope = rememberCoroutineScope()
                                        val starShape = RoundedCornerShape(11.dp)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(11.dp))
                                                    .background(ObsidianPrimaryContainer),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                SiteIcon("rotation", null, Modifier.size(21.dp), BrandInk)
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = rotation.name,
                                                    color = TextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = "${rotation.daySequence.size} giorni · ${(rotation.daySequence.size + 6) / 7} settimane" +
                                                        if (preferred) " · Predefinita" else "",
                                                    fontSize = 11.sp,
                                                    color = TextSecondary,
                                                    modifier = Modifier.padding(top = 3.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(starShape)
                                                    .background(ObsidianSurfaceContainer)
                                                    .border(1.dp, ObsidianBorderSubtle, starShape)
                                                    .bouncyClickable {
                                                        ShiftRepository.toggleDefaultRotation(rotation.id)
                                                        scope.launch {
                                                            phase.snapTo(0f)
                                                            phase.animateTo(1f, tween(720))
                                                            phase.snapTo(0f)
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CatalogDefaultStar(
                                                    selected = preferred,
                                                    description = if (preferred) "Rimuovi predefinita" else "Imposta predefinita",
                                                    modifier = Modifier.size(20.dp),
                                                    progress = phase.value,
                                                )
                                            }
                                            CatalogEditButton("Modifica ${rotation.name}") {
                                                cycle = rotation
                                            }
                                        }
                                        SiteCyclePreview(rotation)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CloudButton(
                label = when (tab) {
                    0 -> "Nuovo modello"
                    1 -> "Nuovo lavoro"
                    2 -> "Nuova rotazione"
                    else -> "Nuova etichetta"
                },
                icon = "plus",
                onClick = {
                    when (tab) {
                        0 -> if (ShiftRepository.shiftDefinitions.size < 150) {
                            shift = ShiftDefinition(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                shortCode = "TUR",
                                colorHex = catalogPastelColors[4],
                                workProfileId = ShiftRepository.activeWorkProfileId,
                            )
                        }
                        1 -> if (ShiftRepository.workProfiles.size < 20) {
                            work = WorkProfile(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                colorHex = catalogPastelColors[2],
                                isDefault = false,
                            ) to true
                        }
                        2 -> if (ShiftRepository.rotations.size < 50) {
                            cycle = ShiftRotation(UUID.randomUUID().toString(), "", 7, List(7) { "" })
                        }
                        3 -> if (ShiftRepository.dayTags.size < 500) {
                            creatingTag = true
                        }
                    }
                },
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        shift?.let { draft ->
            ShiftEditorScreen(
                shift = draft,
                onDismiss = { shift = null },
                onSave = {
                    ShiftRepository.saveShiftDefinition(it)
                    shift = null
                    feedback = ActionFeedback("Turno salvato", false)
                },
                onDelete = {
                    ShiftRepository.deleteShiftDefinition(it)
                    shift = null
                    feedback = ActionFeedback("Turno eliminato", true)
                },
            )
        }
        cycle?.let { draft ->
            RotationMatrixEditorScreen(
                rotation = draft,
                onDismiss = { cycle = null },
                onSave = {
                    ShiftRepository.saveRotation(it)
                    cycle = null
                    feedback = ActionFeedback("Rotazione salvata", false)
                },
                onApply = {},
                onDelete = {
                    ShiftRepository.deleteRotation(it)
                    cycle = null
                    feedback = ActionFeedback("Rotazione eliminata", true)
                },
            )
        }
        work?.let { (profile, isNew) ->
            WorkProfileEditorScreen(
                profile = profile,
                isNew = isNew,
                onDismiss = { work = null },
                onSave = {
                    if (isNew) ShiftRepository.addWorkProfile(it) else ShiftRepository.updateWorkProfile(it)
                    work = null
                    feedback = ActionFeedback("Lavoro salvato", false)
                },
                onDelete = {
                    if (ShiftRepository.deleteWorkProfile(it)) {
                        work = null
                        feedback = ActionFeedback("Lavoro eliminato", true)
                    }
                },
            )
        }
        if (creatingTag || editingTag != null) {
            DayTagEditor(
                original = editingTag,
                onClose = {
                    creatingTag = false
                    editingTag = null
                },
                onSaved = { saved ->
                    creatingTag = false
                    editingTag = null
                    feedback = dayTagMutationFeedback(saved)
                },
            )
        }
        MagicalActionFeedbackHUD(
            feedback = feedback,
            onDismiss = { feedback = null },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
        )
    }
}

@Composable
private fun CatalogEditButton(description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(ObsidianSurfaceContainer)
            .border(1.dp, ObsidianBorderSubtle, shape)
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SiteIcon("edit", description, Modifier.size(18.dp), BrandInk)
    }
}

@Composable
private fun SiteCollectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, ObsidianBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
    ) {
        Column(Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun SiteCodeBadge(code: String, color: Color) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code.take(4),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = catalogInk(color),
            maxLines = 1,
        )
    }
}

@Composable
private fun SiteCyclePreview(rotation: ShiftRotation) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(17.dp))
            listOf("L", "M", "M", "G", "V", "S", "D").forEach {
                Text(
                    text = it,
                    modifier = Modifier.weight(1f),
                    fontSize = 9.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        rotation.daySequence.chunked(7).forEachIndexed { week, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("S${week + 1}", Modifier.width(17.dp), fontSize = 8.sp, color = TextSecondary)
                for (day in 0..6) {
                    val definition = row.getOrNull(day)?.let { ShiftRepository.getShiftDefinition(it) }
                    val fill = definition?.color ?: ObsidianSurfaceContainer
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (day < row.size) fill else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day < row.size) {
                            Text(
                                text = definition?.shortCode?.take(4) ?: "—",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (definition != null) catalogInk(fill) else TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
