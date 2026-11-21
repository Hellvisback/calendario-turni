@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.data.*
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.AppConfirmationDialog
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.*
import java.util.UUID

/** Document -> review drafts -> explicit additive catalogue save. No automatic calendar changes. */
@Composable
fun GeminiShiftImportScreen(onDismiss:()->Unit,onNavigateToTurni:((ShiftRotation?)->Unit)?=null){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var name by rememberSaveable{mutableStateOf(SecurePreferencesManager.getUserName(context).orEmpty())}
    var confirmedName by rememberSaveable{mutableStateOf("")}
    val nameConfirmed=name.isNotBlank()&&name==confirmedName
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var document by remember{mutableStateOf<AiRosterDocument?>(null)}
    var draft by remember{mutableStateOf<AiRosterDraft?>(null)}
    var selectedCycle by remember{mutableStateOf<String?>(null)}
    var confirmed by remember{mutableStateOf(false)}
    var cycleConfirmed by remember{mutableStateOf(false)}
    var makeDefault by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var operation by remember{mutableStateOf<Job?>(null)}
    var message by remember{mutableStateOf<ActionFeedback?>(null)}
    var pasted by remember{mutableStateOf("")}
    var editShift by remember{mutableStateOf<String?>(null)}
    var editCycle by remember{mutableStateOf<String?>(null)}
    var showSource by remember{mutableStateOf(false)}
    var saved by remember{mutableStateOf<AiSavedRoster?>(null)}
    var discard by remember{mutableStateOf(false)}
    var nameError by remember{mutableStateOf(false)}
    var pastedError by remember{mutableStateOf(false)}
    fun confirmName(){
        val normalized=name.trim().replace(Regex("\\s+")," ")
        if(normalized.isNotBlank()){
            name=normalized;confirmedName=normalized;nameError=false
            SecurePreferencesManager.saveUserName(context,normalized)
            focusManager.clearFocus();keyboard?.hide()
            message=ActionFeedback("Nome confermato · pronto per le istruzioni")
        } else {
            nameError=true
        }
    }
    val profile=ShiftRepository.activeWorkProfileId
    val prompt=remember(name){AiRosterImport.prompt(name)}
    fun fail(error:Exception){
        val detail=when(error){
            is org.json.JSONException->"Il testo incollato è incompleto o non rispetta il formato richiesto. Copia tutta la risposta oppure chiedi all’AI di rigenerarla con le istruzioni."
            is java.nio.charset.CharacterCodingException->"Il file deve contenere il risultato JSON in formato UTF-8. Prova a incollare direttamente la risposta."
            else->error.message?:"Operazione non riuscita. Riprova."
        }
        if (draft == null) pastedError = true
        message=ActionFeedback(detail,true)
    }
    fun accept(value:AiRosterDraft){draft=value;selectedCycle=value.suggestedCycleId;confirmed=false;cycleConfirmed=false;makeDefault=false;saved=null;pastedError=false}
    fun cancel(){operation?.cancel();operation=null;busy=false}
    fun back(){when{busy->cancel();draft!=null&&saved==null->discard=true;else->onDismiss()}}
    DisposableEffect(Unit) { onDispose { operation?.cancel() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            operation = scope.launch {
                busy = true
                try {
                    document = withContext(Dispatchers.IO) { AiRosterDocuments.read(context, uri) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    fail(e)
                } finally {
                    busy = false
                }
            }
        }
    }
    fun parsePasted() {
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
    fun openAi(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            fail(e)
        }
    }
    val current = draft
    val step = if (saved != null) 2 else if (current != null) 1 else 0

    Box(Modifier.fillMaxSize()) {
        CloudFeatureScaffold(
            title = if (saved != null) "Catalogo aggiornato" else if (current == null) "Importazione con AI" else "Controlla i turni",
            subtitle = if (current == null) "Nome e documento · AI · Revisione" else if (saved != null) "Modelli e rotazione salvati" else "Modifiche in bozza, prima del salvataggio",
            onDismiss = { back() },
            feedback = message,
            onDismissFeedback = { message = null },
            scrollResetKey = step,
            scrollable = false,
            bottomBar = if (current != null && saved == null) {
                {
                    val valid = confirmed && AiRosterImport.validate(current, selectedCycle, cycleConfirmed).isEmpty()
                    CloudButton(
                        label = "Salva nel catalogo",
                        icon = "check",
                        onClick = {
                            try {
                                saved = AiRosterImport.save(current, selectedCycle, cycleConfirmed, makeDefault)
                                message = ActionFeedback("Catalogo aggiornato")
                            } catch (e: Exception) {
                                fail(e)
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
                step = step,
                prospettoReady = nameConfirmed || document != null,
            )
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState >= initialState
                    (fadeIn(tween(240)) + slideInHorizontally(spring(1f, 440f)) {
                        if (forward) it / 10 else -it / 10
                    }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(220)) {
                            if (forward) -it / 12 else it / 12
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
                    if (targetStep == 0) {
                        CloudCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PastelIconBadge(
                                    pastel = ExpressivePastel.SKY,
                                    icon = "scan-text",
                                    size = 40.dp,
                                    iconSize = 20.dp,
                                )
                                Text(
                                    text = "1 · Il tuo prospetto",
                                    modifier = Modifier.weight(1f),
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (nameConfirmed) {
                                    PastelPill(
                                        text = "Pronto",
                                        color = ExpressivePastel.MINT.sourceColor,
                                        icon = "check",
                                    )
                                }
                            }
                            Text(
                                text = "Scrivi il nome come compare nel prospetto. Allegalo poi alla tua AI insieme alle istruzioni che copierai qui.",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = TextSecondary,
                            )
                            ImportField(
                                label = "Nome e cognome nel documento",
                                placeholder = "Es. Mario Rossi",
                                value = name,
                                change = {
                                    name = it.take(100)
                                    nameError = false
                                },
                                enabled = !busy,
                                isError = nameError,
                                onDone = { confirmName() },
                            )
                            CloudButton(
                                label = if (nameConfirmed) "Nome confermato" else "Conferma nome",
                                icon = "check",
                                onClick = { confirmName() },
                                enabled = !busy && name.isNotBlank(),
                                secondary = nameConfirmed,
                            )
                            SettingsDivider()
                            val docPickerShape = RoundedCornerShape(15.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(docPickerShape)
                                    .background(ObsidianSurfaceContainer)
                                    .bouncyClickable(enabled = !busy) {
                                        picker.launch(arrayOf("application/pdf", "image/*"))
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PastelIconBadge(
                                    pastel = ExpressivePastel.SKY,
                                    icon = if (document == null) "upload" else "file-text",
                                    size = 38.dp,
                                    iconSize = 18.dp,
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = document?.name ?: "Scegli prospetto · facoltativo",
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary.copy(alpha = if (!busy) 1f else 0.5f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = document?.let { file ->
                                            "${file.pages} ${if (file.pages == 1) "pagina" else "pagine"} · documento completo"
                                        } ?: "PDF o immagine delle pagine",
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = TextSecondary,
                                    )
                                }
                                PastelPill(
                                    text = if (document == null) "Allega" else "Cambia",
                                    color = ExpressivePastel.SKY.sourceColor,
                                )
                            }
                        }
                        CloudCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PastelIconBadge(
                                    pastel = ExpressivePastel.MAUVE,
                                    icon = "copy",
                                    size = 40.dp,
                                    iconSize = 20.dp,
                                )
                                Text(
                                    text = "2 · Copia e invia alla tua AI",
                                    modifier = Modifier.weight(1f),
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = "Copia le istruzioni e inviale alla tua AI insieme al PDF originale o alle immagini di tutte le pagine.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = TextSecondary,
                            )
                            CloudButton(
                                label = "Copia istruzioni",
                                icon = "copy",
                                onClick = {
                                    SecurePreferencesManager.saveUserName(context, name.trim())
                                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("Istruzioni TurniSync", prompt))
                                    message = ActionFeedback("Istruzioni copiate · allega anche il prospetto")
                                },
                                enabled = name.isNotBlank() && !busy,
                            )
                            SettingsDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "ChatGPT" to "https://chatgpt.com/",
                                    "Gemini" to "https://gemini.google.com/",
                                    "Claude" to "https://claude.ai/",
                                ).forEach { (serviceName, serviceUrl) ->
                                    val pillShape = RoundedCornerShape(14.dp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .clip(pillShape)
                                            .background(ObsidianSurfaceContainer)
                                            .bouncyClickable(enabled = !busy) { openAi(serviceUrl) }
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
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
                            Text(
                                text = "Apri il servizio che preferisci e allega il prospetto: questi pulsanti non inviano il file automaticamente.",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = TextSecondary,
                            )
                        }
                        CloudCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PastelIconBadge(
                                    pastel = ExpressivePastel.MINT,
                                    icon = "file-text",
                                    size = 40.dp,
                                    iconSize = 20.dp,
                                )
                                Text(
                                    text = "3 · Riporta il risultato",
                                    modifier = Modifier.weight(1f),
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = "Copia tutto il testo restituito dall’AI e incollalo qui. Controllerai modelli e rotazione prima di salvarli.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = TextSecondary,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Risposta dell’AI",
                                    color = if (pastedError) ColorNegative else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                val pasteShape = RoundedCornerShape(12.dp)
                                Row(
                                    modifier = Modifier
                                        .clip(pasteShape)
                                        .background(ObsidianSurfaceContainer)
                                        .bouncyClickable(enabled = !busy) {
                                            val value = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                                .primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                            if (value.length > 2_000_000) {
                                                pastedError = true
                                                message = ActionFeedback("Risposta troppo grande", true)
                                            } else {
                                                pasted = value
                                                pastedError = false
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    SiteIcon(
                                        name = "copy",
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = TextPrimary.copy(alpha = if (!busy) 1f else 0.45f),
                                    )
                                    Text(
                                        text = "Incolla",
                                        color = TextPrimary.copy(alpha = if (!busy) 1f else 0.45f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = pasted,
                                onValueChange = {
                                    pasted = it.take(2_000_001)
                                    pastedError = false
                                },
                                placeholder = { Text("Incolla qui la risposta dell’AI") },
                                isError = pastedError,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 210.dp),
                                maxLines = 7,
                                colors = importTextFieldColors(),
                                shape = RoundedCornerShape(15.dp),
                            )
                            CloudButton(
                                label = "Controlla risultato",
                                icon = "check",
                                onClick = { parsePasted() },
                                enabled = !busy && pasted.isNotBlank() && name.isNotBlank(),
                            )
                        }
                    } else if (targetStep == 1 && current != null) {
                        CloudCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PastelIconBadge(
                                    pastel = ExpressivePastel.MINT,
                                    icon = "check",
                                    size = 40.dp,
                                    iconSize = 20.dp,
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Proposta estratta",
                                        fontSize = 15.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        text = "${current.shifts.size} modelli · ${current.cycles.size} ${if (current.cycles.size == 1) "sequenza" else "sequenze"}",
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = TextSecondary,
                                    )
                                }
                                if (current.period.isNotBlank()) {
                                    PastelPill(
                                        text = current.period,
                                        color = ExpressivePastel.SKY.sourceColor,
                                    )
                                }
                            }
                            Text(
                                text = "Salvi nel lavoro ${ShiftRepository.workProfiles.firstOrNull { it.id == current.profileId }?.name.orEmpty()}. Puoi correggere nomi, sigle, orari, colori e ogni giorno del ciclo. Nessun turno sarà applicato al calendario.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = TextSecondary,
                            )
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
                                        .bouncyClickable { showSource = !showSource }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    PastelIconBadge(
                                        pastel = ExpressivePastel.AMBER,
                                        icon = "message",
                                        size = 32.dp,
                                        iconSize = 16.dp,
                                    )
                                    Text(
                                        text = if (showSource) "Nascondi ${current.warnings.size} osservazioni" else "Leggi ${current.warnings.size} osservazioni",
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
                        CloudCard("Modelli dei turni", accent = ExpressivePastel.MINT) {
                            val palette = currentTurniPalette
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
                                            color = ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(row.definition.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, color = TextPrimary)
                                        if (row.needsReview) {
                                            PastelPill(
                                                text = "Da completare: apri e verifica",
                                                color = ExpressivePastel.AMBER.sourceColor,
                                            )
                                        } else {
                                            Text(
                                                text = if (row.definition.isAllDay) {
                                                    "Giornata intera · ${row.definition.category.name.lowercase().replaceFirstChar { it.titlecase() }}"
                                                } else {
                                                    "${row.definition.startTime} – ${row.definition.endTime}"
                                                },
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp,
                                                color = TextSecondary,
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
                                label = "Aggiungi modello mancante",
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
                        CloudCard("La tua rotazione", accent = ExpressivePastel.LAVENDER) {
                            Text(
                                text = "Scegli una sequenza. Se il nome non è stato trovato con certezza, nessuna viene selezionata automaticamente.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = TextSecondary,
                            )
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
                                Text("Salva soltanto i modelli", fontSize = 13.sp, lineHeight = 18.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
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
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(cycle.rotation.name, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                            Text(
                                                text = "${cycle.rotation.daySequence.size} giorni · " + when (cycle.basis) {
                                                    "explicit" -> "ciclo indicato"
                                                    "repeated" -> "ripetizione rilevata"
                                                    else -> "sequenza osservata, da confermare"
                                                },
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp,
                                                color = TextSecondary,
                                            )
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
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
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
                                                                fontWeight = FontWeight.Bold,
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
                                label = "Crea una sequenza da correggere",
                                icon = "plus",
                                onClick = {
                                    val id = "ai-cycle-" + UUID.randomUUID()
                                    val row = AiReviewCycle(
                                        rotation = ShiftRotation(id, "Nuova rotazione", 7, List(7) { "" }),
                                        basis = "observed",
                                        evidence = "Sequenza manuale da completare",
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
                                        label = "Ho verificato la sequenza e voglio usarla come ciclo",
                                        checked = cycleConfirmed,
                                        onCheckedChange = { cycleConfirmed = it },
                                    )
                                    SettingsDivider()
                                    ImportSwitchRow(
                                        label = "Imposta come rotazione predefinita",
                                        checked = makeDefault,
                                        onCheckedChange = { makeDefault = it },
                                    )
                                }
                            }
                        }
                        CloudCard("Prima di salvare") {
                            ImportSwitchRow(
                                label = "Ho verificato modelli, orari e osservazioni",
                                checked = confirmed,
                                onCheckedChange = { confirmed = it },
                            )
                            val problems = AiRosterImport.validate(current, selectedCycle, cycleConfirmed)
                            if (problems.isNotEmpty()) {
                                SettingsDivider()
                                Text(
                                    text = problems.joinToString("\n"),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Color(currentTurniPalette.warning.ink),
                                )
                            }
                            SettingsDivider()
                            Text(
                                text = "I modelli esistenti rimangono invariati. Le corrispondenze esatte vengono riutilizzate; eventuali varianti vengono aggiunte al catalogo.",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = TextSecondary,
                            )
                        }
                    } else if (targetStep == 2) {
                        CloudCard("Salvataggio completato", accent = ExpressivePastel.MINT) {
                            PastelIconBadge(
                                pastel = ExpressivePastel.MINT,
                                icon = "check",
                                size = 46.dp,
                                iconSize = 24.dp,
                            )
                            Text(
                                text = "${saved?.shifts ?: 0} nuovi modelli · ${saved?.cycles ?: 0} rotazioni salvate",
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                            )
                            Text(
                                text = "Li trovi in Modelli e turni. Puoi modificarli ancora da lì e applicare la rotazione dal calendario.",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = TextSecondary,
                            )
                            CloudButton(
                                label = "Apri Modelli e turni",
                                icon = "clock",
                                onClick = { onNavigateToTurni?.invoke(null) ?: onDismiss() },
                            )
                        }
                    }
                    Spacer(Modifier.height(if (current != null && saved == null) 12.dp else 24.dp))
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
                    Text("Lettura del risultato…", fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        text = "Controlliamo il formato sul dispositivo. Il calendario resta invariato.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = TextSecondary,
                    )
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
        AppConfirmationDialog(
            title = "Scartare la revisione?",
            message = "Le modifiche sono ancora in bozza. Se torni al documento, perderai questa revisione. I turni già salvati nel catalogo restano invariati.",
            confirmLabel = "Scarta e torna al documento",
            dismissLabel = "Continua revisione",
            onDismiss = { discard = false },
            onConfirm = {
                discard = false
                draft = null
                confirmed = false
            },
        )
    }
}

@Composable
private fun ImportStepperStrip(
    step: Int,
    prospettoReady: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ImportStepChip(
                number = "1",
                label = "Prospetto",
                pastel = ExpressivePastel.SKY,
                active = step == 0 && !prospettoReady,
                done = step > 0 || prospettoReady,
                modifier = Modifier.weight(1f),
            )
            ImportStepChip(
                number = "2",
                label = "Prompt AI",
                pastel = ExpressivePastel.MAUVE,
                active = step == 0 && prospettoReady,
                done = step > 0,
                modifier = Modifier.weight(1f),
            )
            ImportStepChip(
                number = "3",
                label = "Revisione",
                pastel = ExpressivePastel.MINT,
                active = step == 1,
                done = step == 2,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ImportStepChip(
    number: String,
    label: String,
    pastel: ExpressivePastel,
    active: Boolean,
    done: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = TurniThemeState.dark
    val tone = remember(pastel, dark) { pastel.style(dark, ObsidianSurface) }
    val doneFill = remember(tone, dark) {
        lerp(ObsidianSurfaceContainer, tone.codeFill, if (dark) 0.24f else 0.40f)
    }
    val targetFill = when {
        active -> tone.codeFill
        done -> doneFill
        else -> ObsidianSurfaceContainer
    }
    val targetInk = when {
        active -> tone.codeInk
        done -> readableInk(doneFill, if (dark) tone.codeFill else tone.codeInk)
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
            active -> tone.codeInk.copy(alpha = 0.16f)
            done -> tone.codeFill
            else -> ObsidianSurface
        },
        animationSpec = tween(220),
        label = "importStepBadgeBg",
    )
    val badgeInk by animateColorAsState(
        targetValue = when {
            active -> tone.codeInk
            done -> tone.codeInk
            else -> TextSecondary
        },
        animationSpec = tween(220),
        label = "importStepBadgeInk",
    )
    val sheenBrush = remember(tone) { pastelSheenBrush(tone.codeFill, tone.codeInk) }
    val sheenAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(220),
        label = "importStepSheenAlpha",
    )

    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(fill)
            .then(if (sheenAlpha > 0f) Modifier.background(sheenBrush, alpha = sheenAlpha) else Modifier)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                SiteIcon("check", null, Modifier.size(11.dp), badgeInk)
            } else {
                Text(
                    text = number,
                    color = badgeInk,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
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
