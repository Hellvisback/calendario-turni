@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.turni.calendario.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.data.*
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Schermata di Importazione Intelligente Turni con Intelligenza Artificiale (Obsidian Liquid Glass UX).
 *
 * Architettura a Carosello Guidato (3 Step):
 * - STEP 1: Spiegazione, Motivo dell'importazione (Turni + Rotazioni) e Copia Prompt Universale negli appunti.
 * - STEP 2: Richiesta Nome Lavoratore (senza default precompilato) + Incolla Risposta AI con ricerca automatica squadra.
 * - STEP 3: Revisione Dati con pulsanti dedicati per modificare singolarmente Turni e Rotazioni + Salvataggio nel catalogo.
 */
@Composable
fun GeminiShiftImportScreen(
    onDismiss: () -> Unit,
    onNavigateToTurni: ((ShiftRotation?) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Stato avanzamento carosello a 3 step
    var currentStep by remember { mutableStateOf(1) }

    // Configurazione Nome Utente (vuoto di default se non impostato precedentemente dall'utente)
    var userNameInput by remember {
        mutableStateOf(SecurePreferencesManager.getUserName(context) ?: "")
    }

    // Risposta AI incollata nello Step 2
    var pastedAiText by remember { mutableStateOf("") }

    // Dati estratti e liste modificabili nello Step 3
    var geminiData by remember { mutableStateOf<GeminiShiftData?>(null) }
    val editableShifts = remember { mutableStateListOf<GeminiShiftDefinition>() }
    val editableSquads = remember { mutableStateListOf<GeminiSquadRotation>() }
    var selectedSquadName by remember { mutableStateOf<String?>(null) }

    // Dialoghi di modifica singola per lo Step 3
    var editingShiftIndex by remember { mutableStateOf<Int?>(null) }
    var showAddShiftDialog by remember { mutableStateOf(false) }
    var editingSquadIndex by remember { mutableStateOf<Int?>(null) }
    var showAddSquadDialog by remember { mutableStateOf(false) }
    var showExitToEditDialog by remember { mutableStateOf(false) }

    // Stato API Key e Document Picker opzionale
    var apiKey by remember { mutableStateOf(SecurePreferencesManager.getApiKey(context) ?: "") }
    var apiKeyInput by remember { mutableStateOf(apiKey) }
    var isEditingApiKey by remember { mutableStateOf(apiKey.isBlank()) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var showDirectApiSection by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var documentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var documentTypeLabel by remember { mutableStateOf<String?>(null) }
    var isProcessingDocument by remember { mutableStateOf(false) }
    var isAnalyzingDirectApi by remember { mutableStateOf(false) }

    // Stato di salvataggio e notifiche
    var isSaving by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var popupType by remember { mutableStateOf(PopupType.SUCCESS) }

    // Prompt universale generato
    val generatedPrompt = remember(userNameInput) {
        GeminiShiftMapper.generateExternalAiPrompt(userNameInput.ifBlank { null })
    }

    // Helper per copiare il prompt negli appunti con feedback visivo
    val copyPromptToClipboard: () -> Unit = {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Prompt Turni Calediario", generatedPrompt)
            clipboard?.setPrimaryClip(clip)
            popupType = PopupType.SUCCESS
            feedbackMessage = "Prompt universale copiato negli appunti! Ora incollalo nell'app AI con la foto dei turni."
        } catch (e: Exception) {
            popupType = PopupType.ERROR
            feedbackMessage = "Impossibile copiare il prompt: ${e.message}"
        }
    }

    // Helper per incollare il testo dagli appunti
    val pasteFromClipboard: () -> Unit = {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
            if (!clipText.isNullOrBlank()) {
                pastedAiText = clipText
                popupType = PopupType.INFO
                feedbackMessage = "Testo incollato dagli appunti (${clipText.length} caratteri)!"
            } else {
                popupType = PopupType.WARNING
                feedbackMessage = "Gli appunti di sistema sono vuoti."
            }
        } catch (e: Exception) {
            popupType = PopupType.ERROR
            feedbackMessage = "Errore durante la lettura degli appunti: ${e.message}"
        }
    }

    // Helper per aprire siti/app AI esterne
    val openExternalAiUrl: (String) -> Unit = { url ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            popupType = PopupType.WARNING
            feedbackMessage = "Impossibile aprire il browser per $url"
        }
    }

    // Elaborazione e parsing del testo incollato
    val processPastedText: () -> Unit = {
        if (pastedAiText.isBlank()) {
            popupType = PopupType.WARNING
            feedbackMessage = "Incolla prima la risposta o il codice JSON restituito dall'AI."
        } else {
            val parseResult = GeminiShiftMapper.parseFromExternalAiText(pastedAiText)
            if (parseResult.isSuccess) {
                val data = parseResult.getOrNull()!!
                geminiData = data

                // Popola le collezioni modificabili
                editableShifts.clear()
                editableShifts.addAll(data.shiftDefinitions)

                editableSquads.clear()
                editableSquads.addAll(data.squads)

                // Ricerca automatica squadra dell'utente
                val targetName = userNameInput.trim()
                if (targetName.isNotBlank()) {
                    SecurePreferencesManager.saveUserName(context, targetName)
                    val matchedSquad = GeminiShiftMapper.matchUserSquad(data, targetName)
                    selectedSquadName = matchedSquad ?: data.squads.firstOrNull()?.squadName
                    popupType = PopupType.SUCCESS
                    feedbackMessage = if (matchedSquad != null) {
                        "Analisi riuscita! Trovata la tua squadra: $matchedSquad"
                    } else {
                        "Analisi completata! Trovati ${data.shiftDefinitions.size} turni e ${data.squads.size} squadre."
                    }
                } else {
                    selectedSquadName = data.squads.firstOrNull()?.squadName
                    popupType = PopupType.SUCCESS
                    feedbackMessage = "Analisi completata! Trovati ${data.shiftDefinitions.size} turni e ${data.squads.size} squadre."
                }

                // Transizione automatica al terzo step per revisione e modifica
                currentStep = 3
            } else {
                val err = parseResult.exceptionOrNull()
                popupType = PopupType.ERROR
                feedbackMessage = err?.message ?: "Impossibile decodificare il JSON. Assicurati di aver copiato la risposta completa dell'AI."
            }
        }
    }

    // Launchers per file picker opzionale
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            documentTypeLabel = "Immagine / Screenshot"
            coroutineScope.launch {
                isProcessingDocument = true
                try {
                    val bmp = withContext(Dispatchers.IO) { DocumentProcessor.processImageUriToBitmap(context, uri) }
                    documentBitmap = bmp
                    thumbnailBitmap = withContext(Dispatchers.IO) { DocumentProcessor.createThumbnail(bmp) }
                    popupType = PopupType.INFO
                    feedbackMessage = "Immagine caricata (${bmp.width}×${bmp.height}px)"
                } catch (e: Exception) {
                    popupType = PopupType.ERROR
                    feedbackMessage = "Errore durante il caricamento: ${e.message}"
                } finally {
                    isProcessingDocument = false
                }
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            documentTypeLabel = "Documento PDF"
            coroutineScope.launch {
                isProcessingDocument = true
                try {
                    val bmp = withContext(Dispatchers.IO) { DocumentProcessor.processPdfToBitmap(context, uri) }
                    documentBitmap = bmp
                    thumbnailBitmap = withContext(Dispatchers.IO) { DocumentProcessor.createThumbnail(bmp) }
                    popupType = PopupType.INFO
                    feedbackMessage = "PDF renderizzato in alta risoluzione (${bmp.width}×${bmp.height}px)"
                } catch (e: Exception) {
                    popupType = PopupType.ERROR
                    feedbackMessage = "Errore conversione PDF: ${e.message}"
                } finally {
                    isProcessingDocument = false
                }
            }
        }
    }

    // Hardware back navigation
    BackHandler {
        if (currentStep > 1) {
            currentStep -= 1
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header: Top Bar con unico pulsante indietro Breccia UX e titolo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentStep > 1) {
                            currentStep -= 1
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurfaceContainer)
                        .border(1.dp, ObsidianBorderSubtle, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Indietro",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Importazione Intelligente Turni",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Assistente AI per Turni e Rotazioni",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // CAROSELLO WIZARD: INDICATORE PROGRESSIVO A 3 STEP
            // =========================================================================
            StepProgressHeader(
                currentStep = currentStep,
                onStepClick = { targetStep ->
                    // Consente di cliccare sullo step solo se coerente con lo stato
                    if (targetStep < currentStep || (targetStep == 3 && geminiData != null)) {
                        currentStep = targetStep
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // STEP 1: SPIEGAZIONE E COPIA PROMPT UNIVERSALE
            // =========================================================================
            if (currentStep == 1) {
                // Card 1: Spiegazione del motivo (Turni + Rotazioni insieme)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = ObsidianPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Come Funziona l'Importazione",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Automatica, completa e per qualsiasi azienda",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Questa procedura ti permette di importare in un colpo solo sia il catalogo completo dei tuoi turni (orari di inizio e fine, codici e colori) sia i cicli di rotazione delle squadre direttamente dalla foto o dal PDF del tabellone inviato dalla tua azienda.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3 Semplici passaggi
                        StepMiniRow(number = "1", title = "Copia il Prompt", description = "Premi il pulsante qui sotto per copiare le istruzioni intelligenti.")
                        Spacer(modifier = Modifier.height(8.dp))
                        StepMiniRow(number = "2", title = "Invia all'AI", description = "Apri ChatGPT, Gemini o Claude, allega la foto del tuo foglio turni e incolla il prompt.")
                        Spacer(modifier = Modifier.height(8.dp))
                        StepMiniRow(number = "3", title = "Incolla qui la Risposta", description = "L'app estrarrà automaticamente tutti i turni e la tua squadra!")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Card 2: Pulsante Copia Prompt & Box del Prompt
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "PROMPT INTELLIGENTE UNIVERSALE",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Pronto all'uso",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Pulsante Primario Copia Prompt
                        Button(
                            onClick = copyPromptToClipboard,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .bouncyClickable(onClick = copyPromptToClipboard)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Copia Prompt negli Appunti",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Box anteprima scrollabile del prompt
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = generatedPrompt,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Scorciatoie per aprire le AI preferite
                        Text(
                            text = "APRI DIRETTAMENTE L'INTELLIGENZA ARTIFICIALE:",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openExternalAiUrl("https://gemini.google.com") },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Text("🌐 Gemini", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { openExternalAiUrl("https://chatgpt.com") },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Text("💬 ChatGPT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = { openExternalAiUrl("https://claude.ai") },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Text("🧠 Claude", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pulsante d'azione: Passa allo Step 2
                Button(
                    onClick = { currentStep = 2 },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Avanti: Incolla Risposta AI →",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Opzione avanzata espandibile per chi ha l'API Key Gemini
                TextButton(
                    onClick = { showDirectApiSection = !showDirectApiSection },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        imageVector = if (showDirectApiSection) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showDirectApiSection) "Nascondi opzione API Key Gemini" else "Possiedi un'API Key Gemini? Clicca qui",
                        fontSize = 11.5.sp,
                        color = TextMuted
                    )
                }

                if (showDirectApiSection) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DirectApiCard(
                        apiKey = apiKey,
                        apiKeyInput = apiKeyInput,
                        isEditingApiKey = isEditingApiKey,
                        isApiKeyVisible = isApiKeyVisible,
                        thumbnailBitmap = thumbnailBitmap,
                        documentTypeLabel = documentTypeLabel,
                        isProcessingDocument = isProcessingDocument,
                        isAnalyzing = isAnalyzingDirectApi,
                        onApiKeyInputChange = { apiKeyInput = it },
                        onApiKeyVisibilityToggle = { isApiKeyVisible = !isApiKeyVisible },
                        onSaveApiKey = {
                            val trimmed = apiKeyInput.trim()
                            apiKey = trimmed
                            SecurePreferencesManager.saveApiKey(context, trimmed)
                            isEditingApiKey = false
                            popupType = PopupType.SUCCESS
                            feedbackMessage = "API Key memorizzata con successo!"
                        },
                        onEditApiKey = {
                            apiKeyInput = apiKey
                            isEditingApiKey = true
                        },
                        onPickImage = { imagePickerLauncher.launch("image/*") },
                        onPickPdf = { pdfPickerLauncher.launch("application/pdf") },
                        onAnalyze = {
                            val activeKey = apiKey.trim().ifBlank { apiKeyInput.trim() }
                            if (activeKey.isBlank()) {
                                popupType = PopupType.WARNING
                                feedbackMessage = "Inserisci prima la tua Google Gemini API Key."
                                return@DirectApiCard
                            }
                            val bmp = documentBitmap
                            if (bmp == null) {
                                popupType = PopupType.WARNING
                                feedbackMessage = "Seleziona prima una foto o un PDF dei turni."
                                return@DirectApiCard
                            }
                            isAnalyzingDirectApi = true
                            coroutineScope.launch {
                                try {
                                    val (base64, mime) = withContext(Dispatchers.IO) { DocumentProcessor.bitmapToBase64(bmp) }
                                    val result = withContext(Dispatchers.IO) {
                                        GeminiApiClient.extractShiftData(apiKey = activeKey, imageBase64 = base64, mimeType = mime)
                                    }
                                    if (result.isSuccess) {
                                        val data = result.getOrNull()!!
                                        geminiData = data
                                        editableShifts.clear()
                                        editableShifts.addAll(data.shiftDefinitions)
                                        editableSquads.clear()
                                        editableSquads.addAll(data.squads)

                                        val targetName = userNameInput.trim()
                                        val matched = if (targetName.isNotBlank()) GeminiShiftMapper.matchUserSquad(data, targetName) else null
                                        selectedSquadName = matched ?: data.squads.firstOrNull()?.squadName

                                        popupType = PopupType.SUCCESS
                                        feedbackMessage = "Analisi Gemini completata con successo!"
                                        currentStep = 3
                                    } else {
                                        val ex = result.exceptionOrNull()
                                        popupType = PopupType.ERROR
                                        feedbackMessage = ex?.message ?: "Errore durante l'analisi API."
                                    }
                                } catch (e: Exception) {
                                    popupType = PopupType.ERROR
                                    feedbackMessage = "Errore: ${e.message}"
                                } finally {
                                    isAnalyzingDirectApi = false
                                }
                            }
                        }
                    )
                }
            }

            // =========================================================================
            // STEP 2: RICHIESTA NOME LAVORATORE + INCOLLA RISPOSTA AI
            // =========================================================================
            if (currentStep == 2) {
                // Card 1: Richiesta Nome Lavoratore (Nessun default precompilato!)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PersonSearch,
                                    contentDescription = null,
                                    tint = ObsidianPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Nome o Cognome Lavoratore",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Opzionale: per trovare automaticamente la tua squadra",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Se inserisci il tuo nome o cognome, cercheremo la tua squadra tra i turnisti del prospetto per preselezionarla per te:",
                            fontSize = 12.5.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = userNameInput,
                            onValueChange = {
                                userNameInput = it
                                if (it.isNotBlank()) {
                                    SecurePreferencesManager.saveUserName(context, it)
                                }
                            },
                            placeholder = { Text("Es: Mario Rossi (oppure solo Rossi)", fontSize = 13.sp, color = TextMuted) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Outlined.Badge, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (userNameInput.isNotBlank()) {
                                    IconButton(onClick = { userNameInput = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Cancella", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = ObsidianPrimary,
                                unfocusedBorderColor = ObsidianBorderSubtle
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Card 2: Box Incolla Risposta AI
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ObsidianTertiary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentPaste,
                                        contentDescription = null,
                                        tint = ObsidianTertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Incolla Risposta AI",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            // Tasto Incolla Rapido dagli Appunti
                            Surface(
                                onClick = pasteFromClipboard,
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianPrimary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.3f)),
                                modifier = Modifier.bouncyClickable(onClick = pasteFromClipboard)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentPaste,
                                        contentDescription = null,
                                        tint = ObsidianPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Incolla",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // TextField per la risposta
                        OutlinedTextField(
                            value = pastedAiText,
                            onValueChange = { pastedAiText = it },
                            placeholder = {
                                Text(
                                    text = "Incolla qui l'intera risposta o il blocco JSON fornito da ChatGPT, Gemini o Claude...\n\nIl nostro parser ripulirà automaticamente commenti, markdown e formattazione!",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    lineHeight = 16.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 220.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = ObsidianPrimary,
                                unfocusedBorderColor = ObsidianBorderSubtle
                            ),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        )

                        if (pastedAiText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${pastedAiText.length} caratteri inseriti",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                TextButton(onClick = { pastedAiText = "" }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancella", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pulsante Primario: Elabora Turni e Squadre
                Button(
                    onClick = processPastedText,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .bouncyClickable(onClick = processPastedText)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Elabora Turni e Squadre ✨",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pulsante Indietro allo Step 1
                OutlinedButton(
                    onClick = { currentStep = 1 },
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("← Torna alle Istruzioni e Prompt", fontSize = 12.5.sp, color = TextSecondary)
                }
            }

            // =========================================================================
            // STEP 3: REVISIONE DATI, MODIFICA SINGOLA SEPARATA & SALVATAGGIO
            // =========================================================================
            if (currentStep == 3) {
                // Summary Card in alto
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = ObsidianTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dati Rilevati con Successo",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${editableShifts.size} turni • ${editableSquads.size} squadre",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (!selectedSquadName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianTertiary.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianTertiary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Star,
                                        contentDescription = null,
                                        tint = ColorReperibilita,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Squadra predefinita per l'utente: $selectedSquadName",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ---------------------------------------------------------------------
                // SEZIONE 1: DEFINIZIONI DEI TURNI (con tasti modifica singola)
                // ---------------------------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DEFINIZIONI DEI TURNI (${editableShifts.size})",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    TextButton(
                        onClick = { showAddShiftDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Aggiungi Turno", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ObsidianPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (editableShifts.isEmpty()) {
                            Text(
                                text = "Nessun turno inserito. Clicca '+ Aggiungi Turno' per crearne uno.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            editableShifts.forEachIndexed { index, def ->
                                val colorParsed = GeminiShiftMapper.parseColorHex(def.colorHex, index)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(colorParsed)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = def.code.take(4),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = catalogInk(Color(colorParsed))
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = def.name.ifBlank { def.code },
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${def.startTime} - ${def.endTime}",
                                                    fontSize = 11.sp,
                                                    color = TextSecondary
                                                )
                                                if (def.isReperibilita) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = ColorReperibilita.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "Reperibile",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = ColorReperibilita,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Pulsanti Modifica Singola ed Elimina Turno
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { editingShiftIndex = index },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = "Modifica turno",
                                                tint = ObsidianPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (index in editableShifts.indices) {
                                                    editableShifts.removeAt(index)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Elimina turno",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (index < editableShifts.size - 1) {
                                    Divider(
                                        color = ObsidianBorderSubtle,
                                        thickness = 0.6.dp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ---------------------------------------------------------------------
                // SEZIONE 2: SQUADRE E ROTAZIONI (con tasti modifica singola)
                // ---------------------------------------------------------------------
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "SQUADRE E ROTAZIONI CICLICHE (${editableSquads.size})",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )

                    TextButton(
                        onClick = { showAddSquadDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Aggiungi Squadra", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ObsidianPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (editableSquads.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                    ) {
                        Text(
                            text = "Nessuna squadra presente. Clicca '+ Aggiungi Squadra' per crearne una.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    editableSquads.forEachIndexed { index, squad ->
                        val isSelected = selectedSquadName.equals(squad.squadName, ignoreCase = true)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable { selectedSquadName = squad.squadName },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) ObsidianSurfaceContainer else ObsidianSurface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) ObsidianPrimary else ObsidianBorder
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedSquadName = squad.squadName },
                                            colors = RadioButtonDefaults.colors(selectedColor = ObsidianPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = squad.squadName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = if (isSelected) ObsidianPrimary else TextPrimary
                                            )
                                            Text(
                                                text = "Ciclo di ${squad.rotationDays.size} giorni" +
                                                        if (squad.employees.isNotEmpty()) " • ${squad.employees.size} turnisti" else "",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    // Pulsanti Modifica Rotazione ed Elimina Squadra
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Stellina predefinita (se selezionata)
                                        ImportElasticStarButton(
                                            isDefault = isSelected,
                                            onToggle = {
                                                selectedSquadName = if (isSelected) null else squad.squadName
                                                popupType = PopupType.INFO
                                                feedbackMessage = if (!isSelected) {
                                                    "'${squad.squadName}' selezionata come squadra attiva!"
                                                } else {
                                                    "Selezione squadra rimossa."
                                                }
                                            }
                                        )

                                        Spacer(modifier = Modifier.width(4.dp))

                                        IconButton(
                                            onClick = { editingSquadIndex = index },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = "Modifica rotazione",
                                                tint = ObsidianPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                if (index in editableSquads.indices) {
                                                    editableSquads.removeAt(index)
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "Elimina squadra",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Sequenza turni
                                if (squad.rotationDays.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        squad.rotationDays.forEach { code ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = ObsidianSurfaceContainer,
                                                border = androidx.compose.foundation.BorderStroke(0.6.dp, ObsidianBorderSubtle)
                                            ) {
                                                Text(
                                                    text = code.take(5),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Nomi dei colleghi se presenti
                                if (squad.employees.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Groups,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = squad.employees.joinToString(", "),
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Promemoria rifinitura nel catalogo
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = ObsidianPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Potrai sempre rifinire o modificare sia i singoli turni sia le rotazioni nella sezione 'Turni' dell'app.",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pulsante Primario: Salva nel catalogo
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true

                        val dataToSave = GeminiShiftData(
                            shiftDefinitions = editableShifts.toList(),
                            squads = editableSquads.toList(),
                            validityStart = geminiData?.validityStart,
                            validityEnd = geminiData?.validityEnd,
                            notes = geminiData?.notes
                        )

                        val setupResult = GeminiShiftMapper.registerExtractedData(
                            repository = ShiftRepository,
                            data = dataToSave,
                            targetUserName = userNameInput.ifBlank { null }
                        )

                        // Se l'utente ha selezionato una specifica squadra come attiva, impostala
                        if (!selectedSquadName.isNullOrBlank()) {
                            val targetId = GeminiShiftMapper.normalizeRotationId(selectedSquadName!!)
                            ShiftRepository.setDefaultRotation(targetId)
                        }

                        popupType = PopupType.SUCCESS
                        feedbackMessage = "Salvataggio completato nel catalogo! ${setupResult.message}"

                        coroutineScope.launch {
                            delay(1400)
                            isSaving = false
                            onDismiss()
                        }
                    },
                    enabled = !isSaving && editableShifts.isNotEmpty(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimaryContainer, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvataggio in corso...", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salva nel Catalogo", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pulsante Secondario: Modifica avanzata in Turni
                if (onNavigateToTurni != null) {
                    OutlinedButton(
                        onClick = {
                            if (!isSaving) {
                                showExitToEditDialog = true
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Outlined.EditCalendar, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Modifica Avanzata Turni", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pulsante Torna allo Step 2
                TextButton(
                    onClick = { currentStep = 2 },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("← Modifica risposta incollata", fontSize = 11.5.sp, color = TextMuted)
                }
            }

            // Ergonomia Riccardo Breccia: Spacer 100dp per Thumb Zone
            Spacer(modifier = Modifier.height(100.dp))
        }

        // =========================================================================
        // DIALOGHI MODALI DI MODIFICA SINGOLA PER TURNI E SQUADRE
        // =========================================================================

        // 1. Modifica Turno Singolo
        if (editingShiftIndex != null && editingShiftIndex in editableShifts.indices) {
            val shiftToEdit = editableShifts[editingShiftIndex!!]
            EditShiftDialog(
                initialShift = shiftToEdit,
                onDismiss = { editingShiftIndex = null },
                onSave = { updated ->
                    editableShifts[editingShiftIndex!!] = updated
                    editingShiftIndex = null
                    popupType = PopupType.INFO
                    feedbackMessage = "Turno '${updated.name}' aggiornato!"
                }
            )
        }

        // 2. Aggiungi Nuovo Turno
        if (showAddShiftDialog) {
            EditShiftDialog(
                initialShift = GeminiShiftDefinition(code = "", name = "", startTime = "08:00", endTime = "16:00", colorHex = "#38BDF8"),
                isNew = true,
                onDismiss = { showAddShiftDialog = false },
                onSave = { newShift ->
                    editableShifts.add(newShift)
                    showAddShiftDialog = false
                    popupType = PopupType.SUCCESS
                    feedbackMessage = "Nuovo turno '${newShift.name}' aggiunto!"
                }
            )
        }

        // 3. Modifica Squadra / Rotazione Singola
        if (editingSquadIndex != null && editingSquadIndex in editableSquads.indices) {
            val squadToEdit = editableSquads[editingSquadIndex!!]
            EditSquadDialog(
                initialSquad = squadToEdit,
                availableShiftCodes = editableShifts.map { it.code },
                onDismiss = { editingSquadIndex = null },
                onSave = { updated ->
                    editableSquads[editingSquadIndex!!] = updated
                    editingSquadIndex = null
                    popupType = PopupType.INFO
                    feedbackMessage = "Rotazione '${updated.squadName}' aggiornata!"
                }
            )
        }

        // 4. Aggiungi Nuova Squadra
        if (showAddSquadDialog) {
            EditSquadDialog(
                initialSquad = GeminiSquadRotation(
                    squadName = "Squadra ${editableSquads.size + 1}",
                    rotationDays = listOf("M", "P", "N", "SMO", "RIP")
                ),
                availableShiftCodes = editableShifts.map { it.code },
                isNew = true,
                onDismiss = { showAddSquadDialog = false },
                onSave = { newSquad ->
                    editableSquads.add(newSquad)
                    showAddSquadDialog = false
                    popupType = PopupType.SUCCESS
                    feedbackMessage = "Squadra '${newSquad.squadName}' aggiunta!"
                }
            )
        }

        // 5. Dialog di avviso passaggio a Modifica avanzata
        if (showExitToEditDialog) {
            AlertDialog(
                onDismissRequest = { showExitToEditDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = ColorReperibilita, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvataggio e Modifica", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                    }
                },
                text = {
                    Text(
                        text = "I turni e le squadre attuali verranno salvati nel catalogo e verrai reindirizzato alla schermata avanzata di gestione turni.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitToEditDialog = false
                            val dataToSave = GeminiShiftData(
                                shiftDefinitions = editableShifts.toList(),
                                squads = editableSquads.toList()
                            )
                            GeminiShiftMapper.registerExtractedData(
                                repository = ShiftRepository,
                                data = dataToSave,
                                targetUserName = userNameInput.ifBlank { null }
                            )
                            val targetRot = ShiftRepository.rotations.find {
                                it.name.equals(selectedSquadName, ignoreCase = true)
                            } ?: ShiftRepository.activeRotation
                            onNavigateToTurni?.invoke(targetRot)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Procedi a Modifica", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showExitToEditDialog = false },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                    ) {
                        Text("Rimani qui", color = TextPrimary, fontSize = 13.sp)
                    }
                },
                containerColor = ObsidianSurfaceContainer,
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Notifiche esclusivamente via AnimatedStatusPopup (nessuna card statica inline)
        AnimatedStatusPopup(
            message = feedbackMessage,
            type = popupType,
            onDismiss = { feedbackMessage = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * Header progressivo del carosello a 3 step con animazioni fluide Obsidian Liquid Glass.
 */
@Composable
private fun StepProgressHeader(
    currentStep: Int,
    onStepClick: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StepPillItem(
                stepNumber = 1,
                label = "1. Prompt",
                isActive = currentStep == 1,
                isCompleted = currentStep > 1,
                onClick = { onStepClick(1) }
            )

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (currentStep > 1) ObsidianPrimary else TextMuted,
                modifier = Modifier.size(16.dp)
            )

            StepPillItem(
                stepNumber = 2,
                label = "2. Incolla",
                isActive = currentStep == 2,
                isCompleted = currentStep > 2,
                onClick = { onStepClick(2) }
            )

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (currentStep > 2) ObsidianPrimary else TextMuted,
                modifier = Modifier.size(16.dp)
            )

            StepPillItem(
                stepNumber = 3,
                label = "3. Salva",
                isActive = currentStep == 3,
                isCompleted = false,
                onClick = { onStepClick(3) }
            )
        }
    }
}

@Composable
private fun StepPillItem(
    stepNumber: Int,
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = when {
            isActive -> ObsidianPrimaryContainer
            isCompleted -> ObsidianTertiary.copy(alpha = 0.15f)
            else -> Color.Transparent
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                isActive -> ObsidianPrimary
                isCompleted -> ObsidianTertiary.copy(alpha = 0.3f)
                else -> Color.Transparent
            }
        ),
        modifier = Modifier.bouncyClickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = ObsidianTertiary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                    isCompleted -> ObsidianTertiary
                    else -> TextSecondary
                }
            )
        }
    }
}

@Composable
private fun StepMiniRow(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(ObsidianPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ObsidianPrimary)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = description, fontSize = 11.5.sp, color = TextSecondary, lineHeight = 15.sp)
        }
    }
}

/**
 * Dialog Modale per modificare un turno singolarmente.
 */
@Composable
private fun EditShiftDialog(
    initialShift: GeminiShiftDefinition,
    isNew: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (GeminiShiftDefinition) -> Unit
) {
    var code by remember { mutableStateOf(initialShift.code) }
    var name by remember { mutableStateOf(initialShift.name) }
    var startTime by remember { mutableStateOf(initialShift.startTime) }
    var endTime by remember { mutableStateOf(initialShift.endTime) }
    var colorHex by remember { mutableStateOf(initialShift.colorHex ?: "#38BDF8") }
    var isReperibilita by remember { mutableStateOf(initialShift.isReperibilita) }

    val presetColors = listOf("#38BDF8", "#F59E0B", "#10B981", "#EF4444", "#6366F1", "#8B5CF6", "#EC4899", "#14B8A6")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "Aggiungi Turno" else "Modifica Turno: ${initialShift.code}",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text("Sigla / Codice", fontSize = 11.sp) },
                        placeholder = { Text("Es: M") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = ObsidianPrimary)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nome esteso", fontSize = 11.sp) },
                        placeholder = { Text("Es: Mattina") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = ObsidianPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Inizio (HH:mm)", fontSize = 11.sp) },
                        placeholder = { Text("06:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = ObsidianPrimary)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("Fine (HH:mm)", fontSize = 11.sp) },
                        placeholder = { Text("13:36") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = ObsidianPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Colore turno (Palette rapida)
                Text("Colore identificativo:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetColors.forEach { hex ->
                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    if (isSelected) 2.dp else 0.dp,
                                    if (isSelected) TextPrimary else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle Reperibilità
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isReperibilita = !isReperibilita }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Turno con Reperibilità associata", fontSize = 12.sp, color = TextPrimary)
                    Switch(
                        checked = isReperibilita,
                        onCheckedChange = { isReperibilita = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianPrimary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.isNotBlank()) {
                        onSave(
                            GeminiShiftDefinition(
                                code = code.trim().uppercase(),
                                name = name.trim().ifBlank { code.trim().uppercase() },
                                startTime = startTime.trim().ifBlank { "00:00" },
                                endTime = endTime.trim().ifBlank { "00:00" },
                                colorHex = colorHex,
                                isReperibilita = isReperibilita
                            )
                        )
                    }
                },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Salva", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
            ) {
                Text("Annulla", color = TextPrimary, fontSize = 13.sp)
            }
        },
        containerColor = ObsidianSurfaceContainer,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Dialog Modale per modificare una squadra o sequenza giorni.
 */
@Composable
private fun EditSquadDialog(
    initialSquad: GeminiSquadRotation,
    availableShiftCodes: List<String>,
    isNew: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (GeminiSquadRotation) -> Unit
) {
    var squadName by remember { mutableStateOf(initialSquad.squadName) }
    var rotationDaysString by remember {
        mutableStateOf(initialSquad.rotationDays.joinToString(" "))
    }
    var employeesString by remember {
        mutableStateOf(initialSquad.employees.joinToString(", "))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "Aggiungi Squadra" else "Modifica: ${initialSquad.squadName}",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = squadName,
                    onValueChange = { squadName = it },
                    label = { Text("Nome Squadra / Turnista", fontSize = 11.sp) },
                    placeholder = { Text("Es: Squadra A") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = ObsidianPrimary)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rotationDaysString,
                    onValueChange = { rotationDaysString = it },
                    label = { Text("Sequenza codici turno (separati da spazio)", fontSize = 11.sp) },
                    placeholder = { Text("Es: M P N SMO RIP") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = ObsidianPrimary)
                )

                // Scorciatoie per aggiungere al volo codici disponibili
                if (availableShiftCodes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Tocca per aggiungere un turno alla sequenza:", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        availableShiftCodes.distinct().forEach { code ->
                            Surface(
                                onClick = {
                                    val current = rotationDaysString.trim()
                                    rotationDaysString = if (current.isEmpty()) code else "$current $code"
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianSurface,
                                border = androidx.compose.foundation.BorderStroke(0.6.dp, ObsidianBorderSubtle)
                            ) {
                                Text(
                                    text = "+ $code",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = employeesString,
                    onValueChange = { employeesString = it },
                    label = { Text("Lavoratori assegnati (separati da virgola)", fontSize = 11.sp) },
                    placeholder = { Text("Es: Rossi Mario, Bianchi Luigi") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = ObsidianPrimary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (squadName.isNotBlank()) {
                        val days = rotationDaysString
                            .split(Regex("[,;\\s]+"))
                            .map { it.trim().uppercase() }
                            .filter { it.isNotBlank() }

                        val emps = employeesString
                            .split(Regex("[,;]+"))
                            .map { it.trim() }
                            .filter { it.isNotBlank() }

                        onSave(
                            GeminiSquadRotation(
                                squadName = squadName.trim(),
                                rotationDays = if (days.isEmpty()) listOf("RIP") else days,
                                cycleDays = if (days.isEmpty()) 1 else days.size,
                                employees = emps
                            )
                        )
                    }
                },
                enabled = squadName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Salva", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
            ) {
                Text("Annulla", color = TextPrimary, fontSize = 13.sp)
            }
        },
        containerColor = ObsidianSurfaceContainer,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Card espandibile opzionale per configurazione diretta API Key e Vision picker.
 */
@Composable
private fun DirectApiCard(
    apiKey: String,
    apiKeyInput: String,
    isEditingApiKey: Boolean,
    isApiKeyVisible: Boolean,
    thumbnailBitmap: Bitmap?,
    documentTypeLabel: String?,
    isProcessingDocument: Boolean,
    isAnalyzing: Boolean,
    onApiKeyInputChange: (String) -> Unit,
    onApiKeyVisibilityToggle: () -> Unit,
    onSaveApiKey: () -> Unit,
    onEditApiKey: () -> Unit,
    onPickImage: () -> Unit,
    onPickPdf: () -> Unit,
    onAnalyze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "CONFIGURAZIONE DIRETTA CON GOOGLE GEMINI API",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ObsidianPrimary,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!isEditingApiKey && apiKey.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "API Key: ••••••••",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        TextButton(onClick = onEditApiKey) {
                            Text("Modifica", fontSize = 11.sp, color = ObsidianPrimary)
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = onApiKeyInputChange,
                    label = { Text("Gemini API Key", fontSize = 11.sp) },
                    placeholder = { Text("AIzaSy...", fontSize = 11.sp) },
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onApiKeyVisibilityToggle) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onSaveApiKey,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Salva Chiave", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selezione Immagine o PDF
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickImage,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Foto / Gallery", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onPickPdf,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF Turni", fontSize = 11.sp)
                }
            }

            if (thumbnailBitmap != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = documentTypeLabel ?: "Documento", fontSize = 12.sp, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onAnalyze,
                enabled = !isAnalyzing && !isProcessingDocument && thumbnailBitmap != null,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimaryContainer, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analisi in corso...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    Text("Analizza con Gemini Vision", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

/**
 * Pulsante stella per turno o squadra predefinita con micro-animazione elastica Riccardo Breccia UX.
 */
@Composable
private fun ImportElasticStarButton(
    isDefault: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }

    Surface(
        onClick = {
            coroutineScope.launch {
                scale.snapTo(0.84f)
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            onToggle()
        },
        shape = RoundedCornerShape(10.dp),
        color = if (isDefault) ColorReperibilita.copy(alpha = 0.2f) else ObsidianSurfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isDefault) ColorReperibilita else ObsidianBorderSubtle
        ),
        modifier = modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isDefault) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isDefault) "Revoca predefinito" else "Imposta come predefinito",
                tint = if (isDefault) ColorReperibilita else TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .scale(scale.value)
            )
        }
    }
}
