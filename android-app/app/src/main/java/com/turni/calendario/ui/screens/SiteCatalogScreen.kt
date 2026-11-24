@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.Job
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

    val activeProfileId = ShiftRepository.activeWorkProfileId
    val activeProfileName = ShiftRepository.activeWorkProfile.name
    val allDefinitions = ShiftRepository.shiftDefinitions.toList()
    val definitions = allDefinitions
        .filter { it.workProfileId == activeProfileId }
        .sortedChronologically()
    val profiles = ShiftRepository.workProfiles.toList()
    val otherProfileGroups = profiles
        .filter { it.id != activeProfileId }
        .mapNotNull { profile ->
            val profileShifts = allDefinitions
                .filter { it.workProfileId == profile.id }
                .sortedChronologically()
            if (profileShifts.isNotEmpty()) profile to profileShifts else null
        }
    val rotations = ShiftRepository.getRotationsSortedByDefault(activeProfileId)
    val dayTags = ShiftRepository.activeDayTags
    val (headerPastel, headerCountLabel) = when (tab) {
        0 -> ExpressivePastel.SKY to "${definitions.size} ${if (definitions.size == 1) "modello" else "modelli"}"
        1 -> ExpressivePastel.LAVENDER to "${profiles.size} ${if (profiles.size == 1) "lavoro" else "lavori"}"
        2 -> ExpressivePastel.AMBER to "${rotations.size} ${if (rotations.size == 1) "rotazione" else "rotazioni"}"
        else -> ExpressivePastel.MINT to "${dayTags.size} ${if (dayTags.size == 1) "etichetta" else "etichette"}"
    }
    fun startNewShift() {
        if (ShiftRepository.shiftDefinitions.size < 150) {
            shift = ShiftDefinition(
                id = UUID.randomUUID().toString(),
                name = "",
                shortCode = "TUR",
                colorHex = catalogPastelColors[4],
                workProfileId = activeProfileId,
            )
        }
    }

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
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TonalIconButton(
                    icon = "arrow-left",
                    contentDescription = "Torna al calendario",
                    onClick = onDismiss,
                    size = 40.dp,
                    cornerRadius = 14.dp,
                    fill = ObsidianSurface,
                    tint = TextPrimary,
                    iconSize = 20.dp,
                )
                Text(
                    text = "Modelli e turni",
                    color = TextPrimary,
                    fontSize = 23.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PastelPill(
                    text = headerCountLabel,
                    color = headerPastel.sourceColor,
                )
            }

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
                                if (definitions.isEmpty()) {
                                    item(key = "shifts-empty") {
                                        CloudCard(
                                            title = "Nessun modello di turno",
                                            subtitle = "Crea il primo turno per $activeProfileName.",
                                            accent = ExpressivePastel.SKY,
                                            modifier = Modifier.animateItem(),
                                        ) {
                                            CloudButton(
                                                label = "Nuovo modello",
                                                icon = "plus",
                                                onClick = ::startNewShift,
                                            )
                                        }
                                    }
                                }
                                items(definitions, key = { it.id }) { definition ->
                                    ShiftCatalogCard(
                                        definition = definition,
                                        onEdit = { shift = definition },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                items(otherProfileGroups, key = { (profile, _) -> "other-${profile.id}" }) { (otherProfile, otherShifts) ->
                                    OtherProfileShiftsCard(
                                        profile = otherProfile,
                                        shifts = otherShifts,
                                        onDuplicateAll = {
                                            val count = ShiftRepository.duplicateAllShiftsToProfile(otherProfile.id, activeProfileId)
                                            feedback = ActionFeedback(
                                                if (count > 0) "$count turni duplicati in $activeProfileName" else "Turni già presenti",
                                                false,
                                            )
                                        },
                                        onDuplicateOne = { definition ->
                                            val duplicated = ShiftRepository.duplicateShiftToProfile(definition.id, activeProfileId)
                                            feedback = ActionFeedback(
                                                if (duplicated != null) "Turno duplicato: ${duplicated.name}" else "Impossibile duplicare il turno",
                                                duplicated == null,
                                            )
                                        },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                            1 -> items(profiles, key = { it.id }) { profile ->
                                WorkProfileCatalogCard(
                                    profile = profile,
                                    isActive = profile.id == ShiftRepository.activeWorkProfileId,
                                    onEdit = { work = profile to false },
                                    onOpenCalendar = {
                                        ShiftRepository.setActiveWorkProfile(profile.id)
                                        onDismiss()
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            2 -> {
                                if (rotations.isEmpty()) {
                                    item(key = "rotations-empty") {
                                        CloudCard(
                                            title = "Nessuna rotazione",
                                            accent = ExpressivePastel.LAVENDER,
                                            modifier = Modifier.animateItem(),
                                        ) {}
                                    }
                                }
                                items(rotations, key = { it.id }) { rotation ->
                                    RotationCatalogCard(
                                        rotation = rotation,
                                        preferred = rotation.id == ShiftRepository.defaultRotationId,
                                        onToggleDefault = {
                                            ShiftRepository.toggleDefaultRotation(rotation.id)
                                        },
                                        onEdit = { cycle = rotation },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBackground)
                    .padding(top = 4.dp, bottom = 10.dp),
            ) {
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
                            0 -> startNewShift()
                            1 -> if (ShiftRepository.workProfiles.size < 20) {
                                work = WorkProfile(
                                    id = UUID.randomUUID().toString(),
                                    name = "",
                                    colorHex = catalogPastelColors[2],
                                    isDefault = false,
                                    weeklyHours = 0,
                                    minRestHoursBetweenShifts = 0,
                                    maxConsecutiveWorkDays = 0,
                                ) to true
                            }
                            2 -> if (ShiftRepository.rotations.size < 50) {
                                cycle = ShiftRotation(
                                    id = UUID.randomUUID().toString(),
                                    name = "",
                                    totalDays = 7,
                                    daySequence = List(7) { "" },
                                    workProfileId = activeProfileId,
                                )
                            }
                            3 -> if (ShiftRepository.activeDayTags.size < 500) {
                                creatingTag = true
                            }
                        }
                    },
                )
            }
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
private fun ShiftCatalogCard(
    definition: ShiftDefinition,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(21.dp)
    val badgeCode = definition.shortCode.ifBlank { definition.name.take(3).uppercase() }.take(4)
    val clockPhase = remember { Animatable(0f) }
    val badgeScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var tapJob by remember { mutableStateOf<Job?>(null) }

    Surface(
        color = ObsidianSurface,
        shape = cardShape,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .bouncyClickable {
                    tapJob?.cancel()
                    tapJob = scope.launch {
                        launch {
                            badgeScale.snapTo(0.94f)
                            badgeScale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 460f))
                        }
                        clockPhase.snapTo(0f)
                        clockPhase.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
                        clockPhase.snapTo(0f)
                    }
                    onEdit()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PastelBadge(
                color = Color(definition.colorHex),
                size = 48.dp,
                cornerRadius = 14.dp,
                definitionId = definition.id,
                modifier = Modifier.graphicsLayer {
                    scaleX = badgeScale.value
                    scaleY = badgeScale.value
                },
            ) { codeInk ->
                Text(
                    text = badgeCode,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = codeInk,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = definition.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (definition.isAllDay) {
                        TonalMetaChip(text = "Tutto il giorno")
                    } else {
                        TonalMetaChip(
                            text = "${definition.startTime} – ${definition.endTime}",
                            icon = "clock",
                            iconProgress = clockPhase.value,
                        )
                        PastelPill(
                            text = definition.durationFormatted,
                            color = Color(definition.colorHex),
                        )
                        if (definition.breakMinutes > 0) {
                            TonalMetaChip(text = "Pausa ${definition.breakMinutes}m")
                        }
                    }
                }
            }
            TonalIconButton(
                icon = "edit",
                contentDescription = "Modifica ${definition.name}",
                onClick = onEdit,
                size = 38.dp,
                cornerRadius = 12.dp,
                fill = ObsidianSurfaceContainer,
                tint = BrandInk,
                iconSize = 18.dp,
            )
        }
    }
}

@Composable
private fun OtherProfileShiftsCard(
    profile: WorkProfile,
    shifts: List<ShiftDefinition>,
    onDuplicateAll: () -> Unit,
    onDuplicateOne: (ShiftDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(profile.id) { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(0.85f, 380f),
        label = "otherProfileChevron",
    )
    val cardShape = RoundedCornerShape(21.dp)

    Surface(
        color = ObsidianSurface,
        shape = cardShape,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .bouncyClickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PastelBadge(
                    color = Color(profile.colorHex),
                    size = 36.dp,
                    cornerRadius = 11.dp,
                ) { ink ->
                    CatalogWorkIcon(
                        key = profile.iconKey,
                        modifier = Modifier.size(18.dp),
                        tint = ink,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Turni di ${profile.name.ifBlank { "Altro lavoro" }} (${shifts.size})",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(ObsidianPrimaryContainer)
                        .bouncyClickable(onClick = onDuplicateAll)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Duplica tutti qui",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandInk,
                        maxLines = 1,
                    )
                }
                SiteIcon(
                    name = "chevron-down",
                    contentDescription = if (expanded) "Comprimi" else "Espandi",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = chevronAngle },
                    tint = TextSecondary,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shifts.forEach { definition ->
                        val badgeCode = definition.shortCode.ifBlank { definition.name.take(3).uppercase() }.take(4)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(ObsidianSurfaceContainer)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PastelBadge(
                                color = Color(definition.colorHex),
                                size = 36.dp,
                                cornerRadius = 10.dp,
                                definitionId = definition.id,
                            ) { codeInk ->
                                Text(
                                    text = badgeCode,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = codeInk,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = definition.name,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (definition.isAllDay) "Tutto il giorno" else "${definition.startTime} – ${definition.endTime}",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 1,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianSurface)
                                    .bouncyClickable { onDuplicateOne(definition) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "Duplica qui",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandInk,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkProfileCatalogCard(
    profile: WorkProfile,
    isActive: Boolean,
    onEdit: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(22.dp)
    val dark = TurniThemeState.dark
    val mintTone = ExpressivePastel.MINT.style(dark, ObsidianSurface)
    val activeFill = lerp(ObsidianSurfaceContainer, mintTone.codeFill, if (dark) 0.22f else 0.36f)
    val activeInk = readableInk(activeFill, if (dark) mintTone.codeFill else mintTone.codeInk)
    val hasPdf = !profile.ccnlDocumentUri.isNullOrBlank()
    val clockPhase = remember { Animatable(0f) }
    val badgeScale = remember { Animatable(1f) }
    val activeScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var tapJob by remember { mutableStateOf<Job?>(null) }

    Surface(
        color = ObsidianSurface,
        shape = cardShape,
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
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .bouncyClickable {
                    tapJob?.cancel()
                    tapJob = scope.launch {
                        launch {
                            badgeScale.snapTo(0.94f)
                            badgeScale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 460f))
                        }
                        clockPhase.snapTo(0f)
                        clockPhase.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
                        clockPhase.snapTo(0f)
                    }
                    onEdit()
                }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PastelBadge(
                    color = Color(profile.colorHex),
                    size = 48.dp,
                    cornerRadius = 14.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = badgeScale.value
                        scaleY = badgeScale.value
                    },
                ) { ink ->
                    CatalogWorkIcon(
                        key = profile.iconKey,
                        modifier = Modifier.size(22.dp),
                        tint = ink,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = profile.name.ifBlank { "Profilo di lavoro" },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (profile.companyOrDepartment.isNotBlank()) {
                        Text(
                            text = profile.companyOrDepartment,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasPdf) {
                        TonalIconButton(
                            icon = "book-open",
                            contentDescription = "Apri CCNL",
                            onClick = { openWorkProfilePdf(context, profile) },
                            size = 38.dp,
                            cornerRadius = 12.dp,
                            fill = ObsidianSurfaceContainer,
                            tint = BrandInk,
                            iconSize = 18.dp,
                        )
                    }
                    TonalIconButton(
                        icon = "edit",
                        contentDescription = "Modifica ${profile.name}",
                        onClick = onEdit,
                        size = 38.dp,
                        cornerRadius = 12.dp,
                        fill = ObsidianSurfaceContainer,
                        tint = BrandInk,
                        iconSize = 18.dp,
                    )
                }
            }

            val hasMeta = profile.weeklyHours > 0 ||
                profile.minRestHoursBetweenShifts > 0 ||
                profile.maxConsecutiveWorkDays > 0 ||
                hasPdf
            if (hasMeta) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (profile.weeklyHours > 0) {
                        TonalMetaChip(
                            text = "${profile.weeklyHours}h/sett",
                            icon = "clock",
                            iconProgress = clockPhase.value,
                        )
                    }
                    if (profile.minRestHoursBetweenShifts > 0) {
                        TonalMetaChip(text = "Riposo ${profile.minRestHoursBetweenShifts}h")
                    }
                    if (profile.maxConsecutiveWorkDays > 0) {
                        TonalMetaChip(text = "Max ${profile.maxConsecutiveWorkDays} di fila")
                    }
                    if (hasPdf) {
                        PastelPill(
                            text = "PDF CCNL",
                            color = ExpressivePastel.CORAL.sourceColor,
                            icon = "file-text",
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .bouncyClickable { openWorkProfilePdf(context, profile) },
                        )
                    }
                }
            }

            if (isActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(activeFill)
                        .bouncyClickable {
                            scope.launch {
                                activeScale.snapTo(0.92f)
                                activeScale.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 460f))
                            }
                            onOpenCalendar()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    SiteIcon(
                        name = "check",
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                scaleX = activeScale.value
                                scaleY = activeScale.value
                            },
                        tint = activeInk,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Calendario in uso",
                        color = activeInk,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                CloudButton(
                    label = "Apri calendario",
                    icon = "calendar",
                    onClick = onOpenCalendar,
                    secondary = true,
                )
            }
        }
    }
}

@Composable
private fun RotationCatalogCard(
    rotation: ShiftRotation,
    preferred: Boolean,
    onToggleDefault: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(22.dp)
    val phase = remember { Animatable(0f) }
    val starScale = remember { Animatable(1f) }
    val badgePhase = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var starJob by remember { mutableStateOf<Job?>(null) }
    var badgeJob by remember { mutableStateOf<Job?>(null) }
    val starShape = RoundedCornerShape(12.dp)
    val starFill by animateColorAsState(
        targetValue = if (preferred) Color(currentTurniPalette.warning.fill) else ObsidianSurfaceContainer,
        animationSpec = tween(durationMillis = 180),
        label = "rotationStarFill",
    )
    val weeks = (rotation.daySequence.size + 6) / 7

    Surface(
        color = ObsidianSurface,
        shape = cardShape,
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
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .bouncyClickable {
                    badgeJob?.cancel()
                    badgeJob = scope.launch {
                        badgePhase.snapTo(0f)
                        badgePhase.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
                        badgePhase.snapTo(0f)
                    }
                    onEdit()
                }
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PastelIconBadge(
                    pastel = ExpressivePastel.LAVENDER,
                    icon = "rotation",
                    size = 44.dp,
                    iconSize = 21.dp,
                    progress = badgePhase.value,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = rotation.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TonalMetaChip(text = "${rotation.daySequence.size} g · $weeks sett")
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            scaleX = starScale.value
                            scaleY = starScale.value
                        }
                        .clip(starShape)
                        .background(starFill)
                        .bouncyClickable {
                            onToggleDefault()
                            starJob?.cancel()
                            starJob = scope.launch {
                                launch {
                                    starScale.snapTo(0.88f)
                                    starScale.animateTo(1.08f, spring(dampingRatio = 0.6f, stiffness = 480f))
                                    starScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f))
                                }
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
                        modifier = Modifier.size(19.dp),
                        progress = phase.value,
                    )
                }
                TonalIconButton(
                    icon = "edit",
                    contentDescription = "Modifica ${rotation.name}",
                    onClick = onEdit,
                    size = 38.dp,
                    cornerRadius = 12.dp,
                    fill = ObsidianSurfaceContainer,
                    tint = BrandInk,
                    iconSize = 18.dp,
                )
            }
            SiteCyclePreview(rotation)
        }
    }
}

@Composable
private fun TonalMetaChip(
    text: String,
    icon: String? = null,
    iconProgress: Float = 0f,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            SiteIcon(
                name = icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = TextSecondary,
                progress = iconProgress,
            )
        }
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SiteCyclePreview(rotation: ShiftRotation) {
    if (rotation.daySequence.isEmpty()) return
    val dark = TurniThemeState.dark
    val previewDays = rotation.daySequence.take(14)
    val overflow = (rotation.daySequence.size - previewDays.size).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurfaceContainer)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        previewDays.chunked(7).forEach { weekDays ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                weekDays.forEach { definitionId ->
                    val definition = definitionId.takeIf { it.isNotBlank() }?.let {
                        ShiftRepository.getShiftDefinition(it)
                    }
                    if (definition != null) {
                        val tone = remember(definition.colorHex, dark, definition.id) {
                            shiftColorStyle(
                                source = definition.color,
                                dark = dark,
                                backdrop = ObsidianSurfaceContainer,
                                definitionId = definition.id,
                            )
                        }
                        val sheen = remember(tone.codeFill, tone.codeInk) {
                            pastelSheenBrush(tone.codeFill, tone.codeInk)
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 30.dp, height = 28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(sheen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = definition.shortCode.take(4).ifBlank { "—" },
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp,
                                color = tone.codeInk,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(width = 30.dp, height = 28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "—",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        if (overflow > 0) {
            PastelPill(
                text = "+$overflow giorni",
                color = ExpressivePastel.LAVENDER.sourceColor,
            )
        }
    }
}
