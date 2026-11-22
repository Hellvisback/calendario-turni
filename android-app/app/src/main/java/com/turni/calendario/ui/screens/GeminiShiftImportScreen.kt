@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
package com.turni.calendario.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.data.*
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.*
import java.util.UUID

/** 3-step AI import wizard (Nome -> Chiedi -> Incolla) -> review drafts -> explicit additive save. */
@Composable
fun GeminiShiftImportScreen(onDismiss: () -> Unit, onNavigateToTurni: ((ShiftRotation?) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(SecurePreferencesManager.getUserName(context).orEmpty()) }
    var confirmedName by rememberSaveable { mutableStateOf("") }
    var setupSubStep by rememberSaveable { mutableIntStateOf(0) }
    var promptCopied by rememberSaveable { mutableStateOf(false) }
    var showPromptPreview by rememberSaveable { mutableStateOf(false) }
    val nameConfirmed = name.isNotBlank() && name == confirmedName
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var draft by remember { mutableStateOf<AiRosterDraft?>(null) }
    var selectedCycle by remember { mutableStateOf<String?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var cycleConfirmed by remember { mutableStateOf(false) }
    var makeDefault by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<ActionFeedback?>(null) }
    var pasted by remember { mutableStateOf("") }
    var editShift by remember { mutableStateOf<String?>(null) }
    var editCycle by remember { mutableStateOf<String?>(null) }
    var showSource by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<AiSavedRoster?>(null) }
    var discard by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var pastedError by remember { mutableStateOf(false) }
    val step1Motion = remember { Animatable(0f) }
    var step1Job by remember { mutableStateOf<Job?>(null) }
    val copyMotion = remember { Animatable(0f) }
    var copyJob by remember { mutableStateOf<Job?>(null) }
    val pasteMotion = remember { Animatable(0f) }
    var pasteJob by remember { mutableStateOf<Job?>(null) }
    val warningsMotion = remember { Animatable(0f) }
    var warningsJob by remember { mutableStateOf<Job?>(null) }
    val reviewMotion = remember { Animatable(0f) }
    val savedMotion = remember { Animatable(0f) }

    fun proceedFromStep0() {
        val normalized = name.trim().replace(Regex("\\s+"), " ")
        if (normalized.isNotBlank()) {
            if (normalized != confirmedName) {
                promptCopied = false
            }
            name = normalized
            confirmedName = normalized
            nameError = false
            SecurePreferencesManager.saveUserName(context, normalized)
            focusManager.clearFocus()
            keyboard?.hide()
            step1Job = scope.triggerImportMotion(step1Motion, step1Job)
            setupSubStep = 1
        } else {
            nameError = true
            message = ActionFeedback("Inserisci nome e cognome", true)
        }
    }

    val profile = ShiftRepository.activeWorkProfileId
    val prompt = remember(name) { AiRosterImport.prompt(name) }

    fun fail(error: Exception) {
        val detail = when (error) {
            is org.json.JSONException -> "Risposta JSON incompleta o non valida"
            else -> error.message ?: "Operazione non riuscita"
        }
        if (draft == null && setupSubStep == 2) pastedError = true
        message = ActionFeedback(detail, true)
    }

    fun accept(value: AiRosterDraft) {
        draft = value
        selectedCycle = value.suggestedCycleId
        confirmed = false
        cycleConfirmed = false
        makeDefault = false
        saved = null
        showSource = false
        pastedError = false
    }

    fun cancel() {
        operation?.cancel()
        operation = null
        busy = false
    }

    fun back() {
        focusManager.clearFocus()
        keyboard?.hide()
        when {
            busy -> cancel()
            draft != null && saved == null -> discard = true
            draft == null && saved == null && setupSubStep > 0 -> setupSubStep--
            else -> onDismiss()
        }
    }

    DisposableEffect(Unit) { onDispose { operation?.cancel() } }

    fun parsePasted() {
        focusManager.clearFocus()
        keyboard?.hide()
        SecurePreferencesManager.saveUserName(context, name.trim())
        operation = scope.launch {
            busy = true
            try {
                val plan = withContext(Dispatchers.Default) { AiRosterImport.parse(pasted, name, profile) }
                accept(plan)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                fail(e)
            } finally {
                busy = false
            }
        }
    }

    fun copyInstructions() {
        copyJob = scope.triggerImportMotion(copyMotion, copyJob)
        SecurePreferencesManager.saveUserName(context, name.trim())
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Istruzioni TurniSync", prompt))
        promptCopied = true
        message = ActionFeedback("Istruzioni copiate")
    }

    fun pasteFromClipboard() {
        pasteJob = scope.triggerImportMotion(pasteMotion, pasteJob)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val value = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        when {
            value.isBlank() -> {
                message = ActionFeedback("Appunti vuoti", true)
            }
            value.length > 2_000_000 -> {
                pastedError = true
                message = ActionFeedback("Risposta troppo grande", true)
            }
            else -> {
                pasted = value
                pastedError = false
                focusManager.clearFocus()
                keyboard?.hide()
                message = ActionFeedback("Risposta incollata")
            }
        }
    }

    fun openAi(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            fail(e)
        }
    }

    val current = draft
    val wizardStep = when {
        saved != null -> 4
        current != null -> 3
        else -> setupSubStep.coerceIn(0, 2)
    }
    val palette = currentTurniPalette

    LaunchedEffect(wizardStep) {
        when (wizardStep) {
            0 -> step1Job = launch {
                step1Motion.snapTo(0f)
                step1Motion.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                step1Motion.snapTo(0f)
            }
            3 -> {
                reviewMotion.snapTo(0f)
                reviewMotion.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                reviewMotion.snapTo(0f)
            }
            4 -> {
                savedMotion.snapTo(0f)
                savedMotion.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
                savedMotion.snapTo(0f)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
        ) {
            CloudFeatureScaffold(
                title = "Importa con AI",
                subtitle = null,
                onDismiss = { back() },
                feedback = message,
                onDismissFeedback = { message = null },
                scrollResetKey = wizardStep,
                scrollable = false,
                bottomBar = if (wizardStep == 3) {
                    {
                        val reviewDraft = current
                        val valid = reviewDraft != null &&
                            confirmed &&
                            AiRosterImport.validate(reviewDraft, selectedCycle, cycleConfirmed).isEmpty()
                        CloudButton(
                            label = "Salva nel catalogo",
                            icon = "check",
                            onClick = {
                                if (reviewDraft != null) {
                                    try {
                                        saved = AiRosterImport.save(reviewDraft, selectedCycle, cycleConfirmed, makeDefault)
                                        message = ActionFeedback("Catalogo aggiornato")
                                    } catch (e: Exception) {
                                        fail(e)
                                    }
                                }
                            },
                            enabled = valid && !busy,
                        )
                    }
                } else {
                    null
                },
            ) {
                ImportStepperStrip(
                    wizardStep = wizardStep,
                    showReviewStep = current != null || saved != null,
                    canJumpBack = current == null && saved == null && !busy,
                    onSelectStep = { target ->
                        focusManager.clearFocus()
                        keyboard?.hide()
                        setupSubStep = target
                    },
                )
                AnimatedContent(
                    targetState = wizardStep,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        val forward = targetState >= initialState
                        (fadeIn(tween(240)) + slideInHorizontally(spring(dampingRatio = 0.92f, stiffness = 380f)) {
                            if (forward) it / 8 else -it / 8
                        }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                                if (forward) -it / 10 else it / 10
                            })
                    },
                    label = "importStepTransition",
                ) { targetStep ->
                    val dark = TurniThemeState.dark
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when {
                            targetStep == 0 -> ImportStep1Name(
                                name = name,
                                nameConfirmed = nameConfirmed,
                                nameError = nameError,
                                busy = busy,
                                step1Motion = step1Motion.value,
                                onPulseIcon = { step1Job = scope.triggerImportMotion(step1Motion, step1Job) },
                                onNameChange = { updated ->
                                    name = updated.take(100)
                                    nameError = false
                                    if (confirmedName.isNotBlank() && updated.trim().replace(Regex("\\s+"), " ") != confirmedName) {
                                        promptCopied = false
                                    }
                                },
                                onContinue = { proceedFromStep0() },
                            )
                            targetStep == 1 -> ImportStep2AskAi(
                                confirmedName = confirmedName.ifBlank { name },
                                prompt = prompt,
                                promptCopied = promptCopied,
                                showPromptPreview = showPromptPreview,
                                busy = busy,
                                copyMotion = copyMotion.value,
                                onEditName = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    setupSubStep = 0
                                },
                                onCopyPrompt = { copyInstructions() },
                                onTogglePromptPreview = { showPromptPreview = !showPromptPreview },
                                onOpenAi = { url -> openAi(url) },
                                onHaveResponse = {
                                    focusManager.clearFocus()
                                    keyboard?.hide()
                                    setupSubStep = 2
                                },
                            )
                            targetStep == 2 -> ImportStep3Paste(
                                pasted = pasted,
                                pastedError = pastedError,
                                canAnalyze = pasted.isNotBlank() && name.isNotBlank() && !busy,
                                busy = busy,
                                pasteMotion = pasteMotion.value,
                                onPasteFromClipboard = { pasteFromClipboard() },
                                onClearPasted = {
                                    pasted = ""
                                    pastedError = false
                                },
                                onPastedChange = {
                                    pasted = it.take(2_000_001)
                                    pastedError = false
                                },
                                onAnalyze = {
                                    pasteJob = scope.triggerImportMotion(pasteMotion, pasteJob)
                                    parsePasted()
                                },
                            )
                            targetStep == 3 && current != null -> {
                                val profileName = ShiftRepository.workProfiles
                                    .firstOrNull { it.id == current.profileId }
                                    ?.name
                                    .orEmpty()
                                CloudCard {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        NeutralIconBadge(
                                            icon = "check",
                                            size = 40.dp,
                                            cornerRadius = 12.dp,
                                            iconSize = 20.dp,
                                            fill = ObsidianPrimaryContainer,
                                            tint = BrandInk,
                                            progress = reviewMotion.value,
                                        )
                                        Text(
                                            text = "Turni estratti",
                                            modifier = Modifier.weight(1f),
                                            fontSize = 16.sp,
                                            lineHeight = 21.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                        )
                                        if (current.period.isNotBlank()) {
                                            ImportStatusPill(
                                                text = current.period,
                                                fill = ObsidianSurfaceContainer,
                                                ink = BrandInk,
                                                icon = "calendar",
                                            )
                                        }
                                    }
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        if (profileName.isNotBlank()) {
                                            ImportStatusPill(
                                                text = profileName,
                                                fill = ObsidianSurfaceContainer,
                                                ink = TextPrimary,
                                                icon = "briefcase",
                                            )
                                        }
                                        ImportStatusPill(
                                            text = "${current.shifts.size} ${if (current.shifts.size == 1) "modello" else "modelli"}",
                                            fill = ObsidianPrimarySubtle,
                                            ink = BrandInk,
                                            icon = "clock",
                                        )
                                        ImportStatusPill(
                                            text = "${current.cycles.size} ${if (current.cycles.size == 1) "ciclo" else "cicli"}",
                                            fill = ObsidianPrimarySubtle,
                                            ink = BrandInk,
                                            icon = "rotation",
                                        )
                                        ImportStatusPill(
                                            text = "Solo catalogo",
                                            fill = Color(palette.success.fill),
                                            ink = Color(palette.success.ink),
                                            icon = "layers",
                                        )
                                    }
                                    if (current.warnings.isNotEmpty()) {
                                        SettingsDivider()
                                        val warningChevron by animateFloatAsState(
                                            targetValue = if (showSource) 180f else 0f,
                                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                                            label = "importWarningsChevron",
                                        )
                                        val warningShape = RoundedCornerShape(14.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(warningShape)
                                                .background(ObsidianSurfaceContainer)
                                                .bouncyClickable {
                                                    showSource = !showSource
                                                    warningsJob = scope.triggerImportMotion(warningsMotion, warningsJob)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            NeutralIconBadge(
                                                icon = "message",
                                                size = 32.dp,
                                                cornerRadius = 10.dp,
                                                iconSize = 16.dp,
                                                fill = Color(palette.warning.fill),
                                                tint = Color(palette.warning.ink),
                                                progress = warningsMotion.value,
                                            )
                                            Text(
                                                text = "${current.warnings.size} ${if (current.warnings.size == 1) "avviso" else "avvisi"}",
                                                modifier = Modifier.weight(1f),
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            SiteIcon("chevron-down", null, Modifier.size(18.dp).rotate(warningChevron), TextSecondary)
                                        }
                                        AnimatedVisibility(
                                            visible = showSource,
                                            enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)),
                                            exit = fadeOut(tween(160)) + shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)),
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(warningShape)
                                                    .background(ObsidianSurfaceContainer)
                                                    .padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                current.warnings.forEach { warning ->
                                                    Text("• $warning", fontSize = 12.sp, lineHeight = 18.sp, color = TextSecondary)
                                                }
                                            }
                                        }
                                    }
                                }
                                CloudCard("Modelli") {
                                    current.shifts.forEachIndexed { index, row ->
                                        if (index > 0) SettingsDivider()
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            PastelBadge(
                                                color = Color(row.definition.colorHex),
                                                size = 44.dp,
                                                cornerRadius = 13.dp,
                                                definitionId = row.definition.id,
                                            ) { ink ->
                                                Text(
                                                    text = row.definition.shortCode,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    lineHeight = 12.sp,
                                                    color = ink,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = centeredBadgeTextStyle(),
                                                )
                                            }
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Text(row.definition.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, color = TextPrimary)
                                                if (row.needsReview) {
                                                    ImportStatusPill(
                                                        text = "Da verificare",
                                                        fill = Color(palette.warning.fill),
                                                        ink = Color(palette.warning.ink),
                                                        icon = "alert-triangle",
                                                    )
                                                } else {
                                                    ImportStatusPill(
                                                        text = if (row.definition.isAllDay) {
                                                            "Tutto il giorno · ${row.definition.category.name.lowercase().replaceFirstChar { it.titlecase() }}"
                                                        } else {
                                                            "${row.definition.startTime} – ${row.definition.endTime}"
                                                        },
                                                        fill = ObsidianSurfaceContainer,
                                                        ink = TextSecondary,
                                                        icon = "clock",
                                                    )
                                                }
                                                if (row.evidence.isNotBlank()) {
                                                    Text(row.evidence, fontSize = 10.sp, lineHeight = 14.sp, color = TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                            TonalIconButton(
                                                icon = "edit",
                                                contentDescription = "Modifica ${row.definition.name}",
                                                onClick = { editShift = row.definition.id },
                                            )
                                            TonalIconButton(
                                                icon = "trash",
                                                contentDescription = "Rimuovi ${row.definition.name}",
                                                onClick = {
                                                    draft = current.copy(shifts = current.shifts.filterNot { it.definition.id == row.definition.id })
                                                    confirmed = false
                                                },
                                                fill = Color(palette.danger.fill),
                                                tint = Color(palette.danger.ink),
                                            )
                                        }
                                    }
                                    if (current.shifts.isNotEmpty()) SettingsDivider()
                                    CloudButton(
                                        label = "Aggiungi modello",
                                        icon = "plus",
                                        onClick = {
                                            val id = "ai-draft-" + UUID.randomUUID()
                                            val row = AiReviewShift("manual-$id", ShiftDefinition(id, "", "TUR", workProfileId = current.profileId), true, "")
                                            draft = current.copy(shifts = current.shifts + row)
                                            confirmed = false
                                            editShift = id
                                        },
                                        enabled = current.shifts.size < 150,
                                        secondary = true,
                                    )
                                }
                                CloudCard("Rotazione") {
                                    val noCycleShape = RoundedCornerShape(15.dp)
                                    val noCycleSelected = selectedCycle == null
                                    val activeCycleBg = remember(dark) {
                                        lerp(ObsidianSurfaceContainer, ObsidianPrimary, if (dark) 0.20f else 0.10f)
                                    }
                                    val noCycleBg by animateColorAsState(
                                        targetValue = if (noCycleSelected) activeCycleBg else ObsidianSurfaceContainer,
                                        animationSpec = tween(180),
                                        label = "noCycleBg",
                                    )
                                    val noCycleRing by animateColorAsState(
                                        targetValue = if (noCycleSelected) ObsidianPrimary else Color.Transparent,
                                        animationSpec = tween(180),
                                        label = "noCycleRing",
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(noCycleShape)
                                            .background(noCycleBg)
                                            .then(if (noCycleSelected) Modifier.border(2.dp, noCycleRing, noCycleShape) else Modifier)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                selectedCycle = null
                                                cycleConfirmed = false
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = noCycleSelected,
                                            onClick = {
                                                selectedCycle = null
                                                cycleConfirmed = false
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = ObsidianPrimary,
                                                unselectedColor = TextSecondary,
                                            ),
                                        )
                                        Text("Solo modelli", fontSize = 13.sp, lineHeight = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    }
                                    current.cycles.forEach { cycle ->
                                        val selected = selectedCycle == cycle.rotation.id
                                        val cycleShape = RoundedCornerShape(15.dp)
                                        val cycleBg by animateColorAsState(
                                            targetValue = if (selected) activeCycleBg else ObsidianSurfaceContainer,
                                            animationSpec = tween(180),
                                            label = "cycleCardBg",
                                        )
                                        val cycleRing by animateColorAsState(
                                            targetValue = if (selected) ObsidianPrimary else Color.Transparent,
                                            animationSpec = tween(180),
                                            label = "cycleCardRing",
                                        )
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(cycleShape)
                                                .background(cycleBg)
                                                .then(if (selected) Modifier.border(2.dp, cycleRing, cycleShape) else Modifier)
                                                .clickable(
                                                    interactionSource = remember(cycle.rotation.id) { MutableInteractionSource() },
                                                    indication = null,
                                                ) {
                                                    selectedCycle = cycle.rotation.id
                                                    cycleConfirmed = false
                                                }
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                RadioButton(
                                                    selected = selected,
                                                    onClick = {
                                                        selectedCycle = cycle.rotation.id
                                                        cycleConfirmed = false
                                                    },
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = ObsidianPrimary,
                                                        unselectedColor = TextSecondary,
                                                    ),
                                                )
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(cycle.rotation.name, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                                    FlowRow(
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        ImportStatusPill(
                                                            text = "${cycle.rotation.daySequence.size}g",
                                                            fill = ObsidianSurface,
                                                            ink = TextSecondary,
                                                            icon = "rotation",
                                                        )
                                                        val (basisLabel, basisFill, basisInk) = when (cycle.basis) {
                                                            "explicit" -> Triple("Indicato", Color(palette.success.fill), Color(palette.success.ink))
                                                            "repeated" -> Triple("Ripetuto", ObsidianPrimarySubtle, BrandInk)
                                                            else -> Triple("Da confermare", Color(palette.warning.fill), Color(palette.warning.ink))
                                                        }
                                                        ImportStatusPill(
                                                            text = basisLabel,
                                                            fill = basisFill,
                                                            ink = basisInk,
                                                        )
                                                    }
                                                }
                                                TonalIconButton(
                                                    icon = "edit",
                                                    contentDescription = "Modifica ${cycle.rotation.name}",
                                                    onClick = { editCycle = cycle.rotation.id },
                                                    fill = ObsidianSurface,
                                                )
                                            }
                                            if (selected && cycle.evidence.isNotBlank()) {
                                                Text(cycle.evidence, fontSize = 11.sp, lineHeight = 16.sp, color = TextSecondary)
                                            }
                                            if (selected) {
                                                cycle.rotation.daySequence.chunked(7).forEach { week ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        week.forEach { id ->
                                                            val def = current.shifts.firstOrNull { it.definition.id == id }?.definition
                                                            if (def != null) {
                                                                val tileStyle = shiftColorStyle(
                                                                    source = Color(def.colorHex),
                                                                    dark = dark,
                                                                    backdrop = ObsidianSurfaceContainer,
                                                                    definitionId = def.id,
                                                                )
                                                                val tileBrush = pastelSheenBrush(tileStyle.codeFill, tileStyle.codeInk)
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .height(30.dp)
                                                                        .clip(RoundedCornerShape(8.dp))
                                                                        .background(tileBrush),
                                                                    contentAlignment = Alignment.Center,
                                                                ) {
                                                                    Text(
                                                                        text = def.shortCode,
                                                                        color = tileStyle.codeInk,
                                                                        fontSize = 10.sp,
                                                                        lineHeight = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        textAlign = TextAlign.Center,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis,
                                                                        style = centeredBadgeTextStyle(),
                                                                    )
                                                                }
                                                            } else {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .height(30.dp)
                                                                        .clip(RoundedCornerShape(8.dp))
                                                                        .background(ObsidianSurface),
                                                                    contentAlignment = Alignment.Center,
                                                                ) {
                                                                    Text(
                                                                        text = "—",
                                                                        color = TextMuted,
                                                                        fontSize = 10.sp,
                                                                        lineHeight = 10.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        textAlign = TextAlign.Center,
                                                                        style = centeredBadgeTextStyle(),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    CloudButton(
                                        label = "Nuova rotazione",
                                        icon = "plus",
                                        onClick = {
                                            val id = "ai-cycle-" + UUID.randomUUID()
                                            val row = AiReviewCycle(
                                                rotation = ShiftRotation(id, "Nuova rotazione", 7, List(7) { "" }),
                                                basis = "observed",
                                                evidence = "",
                                                employees = emptyList(),
                                            )
                                            draft = current.copy(cycles = current.cycles + row)
                                            selectedCycle = id
                                            cycleConfirmed = false
                                            editCycle = id
                                        },
                                        enabled = current.cycles.size < 50,
                                        secondary = true,
                                    )
                                    AnimatedVisibility(
                                        visible = selectedCycle != null,
                                        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                                        exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            SettingsDivider()
                                            ImportSwitchRow(
                                                label = "Sequenza verificata",
                                                checked = cycleConfirmed,
                                                onCheckedChange = { cycleConfirmed = it },
                                            )
                                            SettingsDivider()
                                            ImportSwitchRow(
                                                label = "Imposta come predefinita",
                                                checked = makeDefault,
                                                onCheckedChange = { makeDefault = it },
                                            )
                                        }
                                    }
                                }
                                CloudCard {
                                    ImportSwitchRow(
                                        label = "Dati verificati",
                                        checked = confirmed,
                                        onCheckedChange = { confirmed = it },
                                    )
                                    val problems = AiRosterImport.validate(current, selectedCycle, cycleConfirmed)
                                    if (problems.isNotEmpty()) {
                                        SettingsDivider()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(palette.warning.fill))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            SiteIcon("alert-triangle", null, Modifier.padding(top = 2.dp).size(15.dp), Color(palette.warning.ink))
                                            Text(
                                                text = problems.joinToString("\n"),
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp,
                                                color = Color(palette.warning.ink),
                                            )
                                        }
                                    }
                                    SettingsDivider()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SiteIcon("layers", null, Modifier.size(14.dp), BrandInk)
                                        Text(
                                            text = "Aggiunta sicura · modelli esistenti intatti",
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = TextSecondary,
                                        )
                                    }
                                }
                            }
                            targetStep == 4 -> {
                                CloudCard {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        NeutralIconBadge(
                                            icon = "check",
                                            size = 44.dp,
                                            cornerRadius = 14.dp,
                                            iconSize = 22.dp,
                                            fill = Color(palette.success.fill),
                                            tint = Color(palette.success.ink),
                                            progress = savedMotion.value,
                                        )
                                        Text(
                                            text = "Catalogo aggiornato",
                                            fontSize = 18.sp,
                                            lineHeight = 23.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        ImportStatusPill(
                                            text = "+${saved?.shifts ?: 0} ${(saved?.shifts ?: 0).let { if (it == 1) "modello" else "modelli" }}",
                                            fill = Color(palette.success.fill),
                                            ink = Color(palette.success.ink),
                                            icon = "clock",
                                        )
                                        ImportStatusPill(
                                            text = "+${saved?.cycles ?: 0} ${(saved?.cycles ?: 0).let { if (it == 1) "rotazione" else "rotazioni" }}",
                                            fill = ObsidianPrimarySubtle,
                                            ink = BrandInk,
                                            icon = "rotation",
                                        )
                                    }
                                    CloudButton(
                                        label = "Vai ai modelli",
                                        icon = "clock",
                                        onClick = { onNavigateToTurni?.invoke(null) ?: onDismiss() },
                                    )
                                }
                            }
                            else -> Unit
                        }
                        Spacer(Modifier.height(if (current != null && saved == null) 12.dp else 24.dp))
                    }
                }
            }
        }
        if (busy) {
            BackHandler(enabled = true) { cancel() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBackground.copy(alpha = 0.96f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = BrandInk)
                    Text("Analisi in corso…", fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    CloudButton("Annulla", "x", { cancel() }, secondary = true)
                }
            }
        }
    }
    editShift?.let { id ->
        current?.shifts?.firstOrNull { it.definition.id == id }?.let { row ->
            ShiftEditorScreen(
                shift = row.definition,
                onDismiss = { editShift = null },
                onSave = { updated ->
                    draft = current.copy(shifts = current.shifts.map { if (it.definition.id == id) it.copy(definition = updated, needsReview = false) else it })
                    confirmed = false
                    editShift = null
                },
                onDelete = { editShift = null },
                allowWorkSelection = false,
            )
        }
    }
    editCycle?.let { id ->
        current?.cycles?.firstOrNull { it.rotation.id == id }?.let { row ->
            RotationMatrixEditorScreen(
                rotation = row.rotation,
                onDismiss = { editCycle = null },
                onSave = { updated ->
                    draft = current.copy(cycles = current.cycles.map { if (it.rotation.id == id) it.copy(rotation = updated) else it })
                    cycleConfirmed = false
                    confirmed = false
                    editCycle = null
                },
                onApply = {},
                availableDefinitions = current.shifts.map { it.definition },
                allowDefault = false,
            )
        }
    }
    if (discard) {
        TurniSafeBottomDialog(onClose = { discard = false }) { finish ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "alert-triangle",
                    size = 40.dp,
                    cornerRadius = 12.dp,
                    iconSize = 19.dp,
                    fill = Color(palette.danger.fill),
                    tint = Color(palette.danger.ink),
                )
                Text(
                    text = "Scartare la bozza?",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Continua revisione",
                    onClick = { finish { discard = false } },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = ObsidianSurfaceContainer,
                    tint = TextSecondary,
                )
            }
            Text(
                text = "Le modifiche non salvate andranno perse.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudButton(
                    label = "Continua",
                    icon = "check",
                    onClick = { finish { discard = false } },
                )
                CloudButton(
                    label = "Scarta",
                    icon = "trash",
                    onClick = {
                        finish {
                            discard = false
                            draft = null
                            confirmed = false
                        }
                    },
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun ImportStep1Name(
    name: String,
    nameConfirmed: Boolean,
    nameError: Boolean,
    busy: Boolean,
    step1Motion: Float,
    onPulseIcon: () -> Unit,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val palette = currentTurniPalette
    val badgeFill by animateColorAsState(
        targetValue = if (name.isNotBlank()) ObsidianPrimaryContainer else ObsidianSurfaceContainer,
        animationSpec = tween(220),
        label = "step1BadgeFill",
    )
    CloudCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(badgeFill)
                    .bouncyClickable(enabled = !busy, onClick = onPulseIcon),
                contentAlignment = Alignment.Center,
            ) {
                SiteIconMotion(
                    name = "scan-text",
                    description = null,
                    modifier = Modifier.size(21.dp),
                    tint = BrandInk,
                ) { step1Motion }
            }
            Text(
                text = "Il tuo nome",
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (nameConfirmed) {
                ImportStatusPill(
                    text = "Pronto",
                    fill = Color(palette.success.fill),
                    ink = Color(palette.success.ink),
                    icon = "check",
                )
            }
        }
        ImportField(
            label = "Nome e cognome nel prospetto",
            placeholder = "Es. Mario Rossi",
            value = name,
            change = onNameChange,
            enabled = !busy,
            isError = nameError,
            onDone = onContinue,
        )
        CloudButton(
            label = "Continua",
            icon = "arrow-right",
            onClick = onContinue,
            enabled = !busy,
        )
    }
}

@Composable
private fun ImportStep2AskAi(
    confirmedName: String,
    prompt: String,
    promptCopied: Boolean,
    showPromptPreview: Boolean,
    busy: Boolean,
    copyMotion: Float,
    onEditName: () -> Unit,
    onCopyPrompt: () -> Unit,
    onTogglePromptPreview: () -> Unit,
    onOpenAi: (String) -> Unit,
    onHaveResponse: () -> Unit,
) {
    val palette = currentTurniPalette
    val copyFill by animateColorAsState(
        targetValue = if (promptCopied) Color(palette.success.fill) else ObsidianPrimary,
        animationSpec = tween(240),
        label = "copyButtonFill",
    )
    val copyInk by animateColorAsState(
        targetValue = if (promptCopied) Color(palette.success.ink) else OnBrand,
        animationSpec = tween(240),
        label = "copyButtonInk",
    )
    val copyEnabled = confirmedName.isNotBlank() && !busy
    val copyAlpha = if (copyEnabled) 1f else 0.45f

    CloudCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NeutralIconBadge(
                icon = "message",
                size = 38.dp,
                cornerRadius = 12.dp,
                iconSize = 19.dp,
                fill = ObsidianPrimaryContainer,
                tint = BrandInk,
                progress = copyMotion,
            )
            Text(
                text = "Chiedi all’AI",
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .widthIn(max = 156.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianSurfaceContainer)
                    .bouncyClickable(enabled = !busy, onClick = onEditName)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = confirmedName,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                SiteIcon("edit", "Modifica nome", Modifier.size(11.dp), BrandInk)
            }
        }

        Surface(
            shape = RoundedCornerShape(15.dp),
            color = copyFill,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(15.dp))
                .bouncyClickable(enabled = copyEnabled, onClick = onCopyPrompt),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                AnimatedContent(
                    targetState = promptCopied,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.68f, stiffness = 460f), initialScale = 0.55f)) togetherWith
                            (fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.55f))
                    },
                    label = "copyMorphIcon",
                ) { copied ->
                    SiteIconMotion(
                        name = if (copied) "check" else "copy",
                        description = null,
                        modifier = Modifier.size(18.dp),
                        tint = copyInk.copy(alpha = copyAlpha),
                    ) { copyMotion }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (promptCopied) "Istruzioni copiate" else "Copia istruzioni",
                    color = copyInk.copy(alpha = copyAlpha),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            SiteIcon("file-text", null, Modifier.size(13.dp), TextSecondary)
            Text(
                text = "Allega il prospetto nella chat",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "ChatGPT" to "https://chatgpt.com/",
                "Gemini" to "https://gemini.google.com/",
                "Claude" to "https://claude.ai/",
            ).forEach { (serviceName, serviceUrl) ->
                val pillShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(pillShape)
                        .background(ObsidianSurfaceContainer)
                        .bouncyClickable(enabled = !busy) { onOpenAi(serviceUrl) }
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                ) {
                    SiteIcon(
                        name = "message",
                        contentDescription = "Apri $serviceName",
                        modifier = Modifier.size(13.dp),
                        tint = BrandInk.copy(alpha = if (!busy) 1f else 0.45f),
                    )
                    Text(
                        text = serviceName,
                        color = TextPrimary.copy(alpha = if (!busy) 1f else 0.45f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        val promptChevron by animateFloatAsState(
            targetValue = if (showPromptPreview) 180f else 0f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            label = "importPromptChevron",
        )
        val toggleShape = RoundedCornerShape(13.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(toggleShape)
                .background(ObsidianSurfaceContainer)
                .bouncyClickable(enabled = !busy, onClick = onTogglePromptPreview)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SiteIcon("file-text", null, Modifier.size(15.dp), BrandInk)
            Text(
                text = "Vedi istruzioni",
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            SiteIcon("chevron-down", null, Modifier.size(17.dp).rotate(promptChevron), TextSecondary)
        }
        AnimatedVisibility(
            visible = showPromptPreview,
            enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(160)) + shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(toggleShape)
                    .background(ObsidianSurfaceContainer)
                    .padding(12.dp),
            ) {
                Text(
                    text = prompt,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }

        CloudButton(
            label = "Ho la risposta",
            icon = "arrow-right",
            onClick = onHaveResponse,
            enabled = !busy,
            secondary = !promptCopied,
        )
    }
}

@Composable
private fun ImportStep3Paste(
    pasted: String,
    pastedError: Boolean,
    canAnalyze: Boolean,
    busy: Boolean,
    pasteMotion: Float,
    onPasteFromClipboard: () -> Unit,
    onClearPasted: () -> Unit,
    onPastedChange: (String) -> Unit,
    onAnalyze: () -> Unit,
) {
    val palette = currentTurniPalette
    CloudCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NeutralIconBadge(
                icon = "file-text",
                size = 40.dp,
                cornerRadius = 12.dp,
                iconSize = 20.dp,
                fill = if (pasted.isNotBlank() && !pastedError) ObsidianPrimaryContainer else ObsidianSurfaceContainer,
                tint = BrandInk,
                progress = pasteMotion,
            )
            Text(
                text = "Incolla la risposta",
                modifier = Modifier.weight(1f),
                color = TextPrimary,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (pasted.isNotBlank() && !pastedError) {
                ImportStatusPill(
                    text = "Pronta",
                    fill = Color(palette.success.fill),
                    ink = Color(palette.success.ink),
                    icon = "check",
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(ObsidianPrimarySubtle)
                    .bouncyClickable(enabled = !busy, onClick = onPasteFromClipboard)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                SiteIconMotion(
                    name = "copy",
                    description = null,
                    modifier = Modifier.size(16.dp),
                    tint = BrandInk.copy(alpha = if (!busy) 1f else 0.45f),
                ) { pasteMotion }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Incolla dagli appunti",
                    color = BrandInk.copy(alpha = if (!busy) 1f else 0.45f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AnimatedVisibility(visible = pasted.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(ObsidianSurfaceContainer)
                        .bouncyClickable(enabled = !busy, onClick = onClearPasted)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    SiteIcon(
                        name = "trash",
                        contentDescription = "Svuota",
                        modifier = Modifier.size(14.dp),
                        tint = TextSecondary.copy(alpha = if (!busy) 1f else 0.45f),
                    )
                    Text(
                        text = "Svuota",
                        color = TextSecondary.copy(alpha = if (!busy) 1f else 0.45f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
        OutlinedTextField(
            value = pasted,
            onValueChange = onPastedChange,
            placeholder = { Text("Risposta JSON dall’AI…") },
            isError = pastedError,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 230.dp),
            maxLines = 9,
            colors = importTextFieldColors(),
            shape = RoundedCornerShape(15.dp),
        )
        AnimatedVisibility(visible = pastedError) {
            Text(
                text = "Copia l’intera risposta JSON dall’inizio alla fine",
                color = ColorNegative,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        CloudButton(
            label = "Analizza",
            icon = "check",
            onClick = onAnalyze,
            enabled = canAnalyze,
        )
    }
}

private fun CoroutineScope.triggerImportMotion(
    motion: Animatable<Float, *>,
    previous: Job?,
    durationMillis: Int = 520,
): Job {
    previous?.cancel()
    return launch {
        motion.snapTo(0f)
        motion.animateTo(1f, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
        motion.snapTo(0f)
    }
}

private fun centeredBadgeTextStyle(): TextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
private fun ImportStatusPill(
    text: String,
    fill: Color,
    ink: Color,
    icon: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
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
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = centeredBadgeTextStyle(),
        )
    }
}

private data class ImportWizardStepMeta(
    val index: Int,
    val number: String,
    val label: String,
)

private val importBaseSteps = listOf(
    ImportWizardStepMeta(0, "1", "Nome"),
    ImportWizardStepMeta(1, "2", "Chiedi"),
    ImportWizardStepMeta(2, "3", "Incolla"),
)

private val importReviewStep = ImportWizardStepMeta(3, "4", "Salva")

@Composable
private fun ImportStepperStrip(
    wizardStep: Int,
    showReviewStep: Boolean,
    canJumpBack: Boolean,
    onSelectStep: (Int) -> Unit,
) {
    val steps = if (showReviewStep) importBaseSteps + importReviewStep else importBaseSteps
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            steps.forEachIndexed { index, item ->
                if (index > 0) {
                    val connectorTint by animateColorAsState(
                        targetValue = if (item.index <= wizardStep) ObsidianPrimary else TextMuted,
                        animationSpec = tween(220),
                        label = "importStepConnector$index",
                    )
                    SiteIcon(
                        name = "chevron-right",
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = connectorTint,
                    )
                }
                val isDone = item.index < wizardStep
                val isActive = item.index == wizardStep
                val isClickable = canJumpBack && isDone
                ImportStepChip(
                    number = item.number,
                    label = item.label,
                    active = isActive,
                    done = isDone,
                    clickable = isClickable,
                    onClick = { onSelectStep(item.index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ImportStepChip(
    number: String,
    label: String,
    active: Boolean,
    done: Boolean,
    clickable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetFill = when {
        active -> ObsidianPrimary
        done -> ObsidianPrimarySubtle
        else -> ObsidianSurfaceContainer
    }
    val targetInk = when {
        active -> OnBrand
        done -> BrandInk
        else -> TextSecondary
    }
    val fill by animateColorAsState(
        targetValue = targetFill,
        animationSpec = tween(220),
        label = "importStepChipFill",
    )
    val ink by animateColorAsState(
        targetValue = targetInk,
        animationSpec = tween(220),
        label = "importStepChipInk",
    )
    val badgeBg by animateColorAsState(
        targetValue = when {
            active -> OnBrand.copy(alpha = 0.2f)
            done -> ObsidianPrimary
            else -> ObsidianSurface
        },
        animationSpec = tween(220),
        label = "importStepBadgeBg",
    )
    val badgeInk by animateColorAsState(
        targetValue = when {
            active -> OnBrand
            done -> OnBrand
            else -> TextSecondary
        },
        animationSpec = tween(220),
        label = "importStepBadgeInk",
    )
    val chipScale by animateFloatAsState(
        targetValue = if (active) 1f else 0.985f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "importStepScale",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = chipScale
                scaleY = chipScale
            }
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(fill)
            .then(
                if (clickable) {
                    Modifier.bouncyClickable(enabled = true, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics {
                selected = active
                contentDescription = buildString {
                    append("Passo $number: $label")
                    if (done) append(", completato")
                    else if (active) append(", attuale")
                    if (clickable) append(", tocca per tornare indietro")
                }
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                SiteIcon("check", null, Modifier.size(12.dp), badgeInk)
            } else {
                Text(
                    text = number,
                    color = badgeInk,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = centeredBadgeTextStyle(),
                )
            }
        }
        Text(
            text = label,
            color = ink,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (active || done) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = centeredBadgeTextStyle(),
        )
    }
}

@Composable
private fun importTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    disabledTextColor = TextSecondary,
    errorTextColor = TextPrimary,
    focusedContainerColor = ObsidianSurfaceContainer,
    unfocusedContainerColor = ObsidianSurfaceContainer,
    disabledContainerColor = ObsidianSurfaceContainer,
    errorContainerColor = ObsidianSurfaceContainer,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent,
    errorBorderColor = ColorNegative,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextSecondary,
    cursorColor = ObsidianPrimary,
)

@Composable
private fun ImportField(
    label: String,
    placeholder: String,
    value: String,
    change: (String) -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    onDone: () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isError) ColorNegative else TextSecondary,
            fontWeight = FontWeight.Medium,
        )
        OutlinedTextField(
            value = value,
            onValueChange = change,
            placeholder = { Text(placeholder) },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            colors = importTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
        )
    }
}

@Composable
private fun ImportSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        TurniSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
