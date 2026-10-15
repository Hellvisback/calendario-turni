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
import androidx.compose.material.icons.filled.*
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
 * Modalità di importazione assistita da Intelligenza Artificiale:
 * - EXTERNAL_PROMPT: Generazione prompt ottimizzato per ChatGPT, Claude, Gemini Web, DeepSeek + importazione risposta incollata.
 * - DIRECT_API: Chiamata automatica e istantanea alle API Google Gemini Flash Vision tramite API Key.
 */
enum class ShiftImportMode {
    EXTERNAL_PROMPT,
    DIRECT_API
}

/**
 * Obsidian Liquid Glass screen for intelligent shift roster importation using Google Gemini Flash Vision.
 *
 * Implements:
 * 1. Single top-left back button (40dp) with hardware BackHandler support.
 * 2. Zero inline static status cards — all feedback via bottom AnimatedStatusPopup.
 * 3. 100dp thumb-zone bottom spacer for ergonomic one-handed use.
 * 4. Unified configuration panel: masked Gemini API Key, employee name auto-save, image/PDF selection & thumbnail preview.
 * 5. Asynchronous Gemini Flash multimodal analysis on Dispatchers.IO.
 * 6. Structured results: detected shifts list, squad rotations with coworker matching, and toggleable ElasticStarButton.
 * 7. One-click catalog registration and default squad application via GeminiShiftMapper.
 */
@Composable
fun GeminiShiftImportScreen(
    onDismiss: () -> Unit,
    onNavigateToTurni: ((ShiftRotation?) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Configuration state
    var apiKey by remember {
        mutableStateOf(SecurePreferencesManager.getApiKey(context) ?: "")
    }
    var isEditingApiKey by remember {
        mutableStateOf(apiKey.isBlank())
    }
    var apiKeyInput by remember {
        mutableStateOf(apiKey)
    }
    var isApiKeyVisible by remember {
        mutableStateOf(false)
    }
    var showExitToEditDialog by remember {
        mutableStateOf(false)
    }
    var isSaving by remember {
        mutableStateOf(false)
    }

    var userName by remember {
        mutableStateOf(SecurePreferencesManager.getUserName(context) ?: SecurePreferencesManager.DEFAULT_USER_NAME)
    }

    // Non-intrusive status notification state (AnimatedStatusPopup only)
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var popupType by remember { mutableStateOf(PopupType.SUCCESS) }

    // Modalità di importazione (Default a EXTERNAL_PROMPT se API Key assente)
    var importMode by remember {
        mutableStateOf(if (apiKey.isNotBlank()) ShiftImportMode.DIRECT_API else ShiftImportMode.EXTERNAL_PROMPT)
    }
    var pastedAiText by remember { mutableStateOf("") }

    // Helper per copiare il prompt negli appunti con feedback visivo
    val copyPromptToClipboard: (String) -> Unit = { prompt ->
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Prompt Turni Calediario", prompt)
            clipboard?.setPrimaryClip(clip)
            popupType = PopupType.SUCCESS
            feedbackMessage = "Prompt copiato negli appunti! Ora incollalo nell'app AI con la foto dei turni."
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
                feedbackMessage = "Testo incollato dagli appunti!"
            } else {
                popupType = PopupType.WARNING
                feedbackMessage = "Gli appunti di sistema sono vuoti."
            }
        } catch (e: Exception) {
            popupType = PopupType.ERROR
            feedbackMessage = "Errore durante la lettura degli appunti: ${e.message}"
        }
    }

    // Helper per aprire in sicurezza le app/siti AI esterni
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

    // Document processing state
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var documentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var documentTypeLabel by remember { mutableStateOf<String?>(null) }
    var isProcessingDocument by remember { mutableStateOf(false) }

    // Analysis & mapping state
    var isAnalyzing by remember { mutableStateOf(false) }
    var geminiData by remember { mutableStateOf<GeminiShiftData?>(null) }
    var selectedSquadName by remember { mutableStateOf<String?>(null) }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            documentTypeLabel = "Immagine / Screenshot"
            coroutineScope.launch {
                isProcessingDocument = true
                try {
                    val bmp = withContext(Dispatchers.IO) {
                        DocumentProcessor.processImageUriToBitmap(context, uri)
                    }
                    documentBitmap = bmp
                    thumbnailBitmap = withContext(Dispatchers.IO) {
                        DocumentProcessor.createThumbnail(bmp)
                    }
                    popupType = PopupType.INFO
                    feedbackMessage = "Immagine caricata (${bmp.width}×${bmp.height}px)"
                } catch (e: Exception) {
                    popupType = PopupType.ERROR
                    feedbackMessage = "Errore durante il caricamento: ${e.localizedMessage ?: e.message}"
                } finally {
                    isProcessingDocument = false
                }
            }
        }
    }

    // PDF Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            documentTypeLabel = "Documento PDF"
            coroutineScope.launch {
                isProcessingDocument = true
                try {
                    val bmp = withContext(Dispatchers.IO) {
                        DocumentProcessor.processPdfToBitmap(context, uri)
                    }
                    documentBitmap = bmp
                    thumbnailBitmap = withContext(Dispatchers.IO) {
                        DocumentProcessor.createThumbnail(bmp)
                    }
                    popupType = PopupType.INFO
                    feedbackMessage = "PDF renderizzato in alta risoluzione (${bmp.width}×${bmp.height}px)"
                } catch (e: Exception) {
                    popupType = PopupType.ERROR
                    feedbackMessage = "Errore nella conversione del PDF: ${e.localizedMessage ?: e.message}"
                } finally {
                    isProcessingDocument = false
                }
            }
        }
    }

    // Hardware back button navigation
    BackHandler(onBack = onDismiss)

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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // =========================================================================
            // Top Bar: Singolo pulsante indietro in alto a sinistra (40dp)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = ObsidianSurfaceContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Indietro",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Importazione Turni Intelligente",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Visione AI diretta o Generatore Prompt per qualsiasi AI",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // SELETTORE MODALITÀ OBSIDIAN LIQUID GLASS (RICCARDO BRECCIA UX)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Modalità 1: Con Prompt AI (Senza API Key, Gratuito)
                    Surface(
                        onClick = { importMode = ShiftImportMode.EXTERNAL_PROMPT },
                        shape = RoundedCornerShape(12.dp),
                        color = if (importMode == ShiftImportMode.EXTERNAL_PROMPT) ObsidianPrimaryContainer else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = if (importMode == ShiftImportMode.EXTERNAL_PROMPT) Color.White else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Prompt AI (Gratis)",
                                fontSize = 12.sp,
                                fontWeight = if (importMode == ShiftImportMode.EXTERNAL_PROMPT) FontWeight.Bold else FontWeight.Medium,
                                color = if (importMode == ShiftImportMode.EXTERNAL_PROMPT) Color.White else TextSecondary
                            )
                        }
                    }

                    // Modalità 2: Con API Key (Google Gemini Flash)
                    Surface(
                        onClick = { importMode = ShiftImportMode.DIRECT_API },
                        shape = RoundedCornerShape(12.dp),
                        color = if (importMode == ShiftImportMode.DIRECT_API) ObsidianPrimaryContainer else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = if (importMode == ShiftImportMode.DIRECT_API) Color.White else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Con API Key",
                                fontSize = 12.sp,
                                fontWeight = if (importMode == ShiftImportMode.DIRECT_API) FontWeight.Bold else FontWeight.Medium,
                                color = if (importMode == ShiftImportMode.DIRECT_API) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // CONTENUTO DINAMICO IN BASE ALLA MODALITÀ SCELTA
            // =========================================================================
            if (importMode == ShiftImportMode.EXTERNAL_PROMPT) {
                // ---------------------------------------------------------------------
                // MODALITÀ A: PROMPT MAKER PER AI ESTERNA (ChatGPT, Claude, Gemini Web)
                // ---------------------------------------------------------------------
                val currentGeneratedPrompt = remember(userName) {
                    GeminiShiftMapper.generateExternalAiPrompt(userName)
                }

                // 1. Identificazione Lavoratore per auto-matching squadra
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = ObsidianPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Auto-Selezione Squadra",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Il nome verrà inserito nel prompt per trovare la tua squadra",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = userName,
                            onValueChange = {
                                userName = it
                                SecurePreferencesManager.saveUserName(context, it)
                            },
                            label = { Text("Il tuo Nome e Cognome", fontSize = 12.sp) },
                            placeholder = { Text("Es. Marco Graziani o Graziani Marco", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Badge, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
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

                // 2. Step 1: Generatore e Copia Prompt
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = ObsidianPrimary.copy(alpha = 0.18f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("1", fontWeight = FontWeight.Bold, color = ObsidianPrimary, fontSize = 13.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Copia il Prompt per l'AI",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Invia questo prompt insieme alla foto o allo screenshot dei turni alla tua AI preferita (ChatGPT, Claude, Gemini Web, DeepSeek).",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Box anteprima scrollabile del prompt
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                        ) {
                            Text(
                                text = currentGeneratedPrompt,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = TextSecondary,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Pulsante Copia Prompt
                        Button(
                            onClick = { copyPromptToClipboard(currentGeneratedPrompt) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Copia Prompt negli Appunti",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Scorciatoie per aprire le app AI
                        Text(
                            text = "APRI DIRETTAMENTE L'AI:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openExternalAiUrl("https://chatgpt.com") },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text("ChatGPT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            OutlinedButton(
                                onClick = { openExternalAiUrl("https://gemini.google.com") },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text("Gemini", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                            OutlinedButton(
                                onClick = { openExternalAiUrl("https://claude.ai") },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Text("Claude", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Step 2: Incolla Risposta AI ed Elabora
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = ObsidianPrimary.copy(alpha = 0.18f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("2", fontWeight = FontWeight.Bold, color = ObsidianPrimary, fontSize = 13.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Incolla Risposta AI",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = pasteFromClipboard,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Incolla", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ObsidianPrimary)
                                }
                                if (pastedAiText.isNotBlank()) {
                                    IconButton(
                                        onClick = { pastedAiText = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Pulisci", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = pastedAiText,
                            onValueChange = { pastedAiText = it },
                            placeholder = {
                                Text("Incolla qui il testo o il JSON generato dall'AI...", fontSize = 12.sp, color = TextMuted)
                            },
                            singleLine = false,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = ObsidianPrimary,
                                unfocusedBorderColor = ObsidianBorderSubtle
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Bottone primario: Elabora Risposta AI
                        Button(
                            onClick = {
                                if (pastedAiText.isBlank()) {
                                    popupType = PopupType.WARNING
                                    feedbackMessage = "Incolla prima la risposta fornita dall'AI."
                                    return@Button
                                }
                                val result = GeminiShiftMapper.parseFromExternalAiText(pastedAiText)
                                if (result.isSuccess) {
                                    val data = result.getOrNull()!!
                                    geminiData = data
                                    val matchedSquad = GeminiShiftMapper.matchUserSquad(data, userName)
                                    selectedSquadName = matchedSquad ?: data.squads.firstOrNull()?.squadName

                                    popupType = PopupType.SUCCESS
                                    feedbackMessage = if (matchedSquad != null) {
                                        "Elaborazione riuscita! Individuata la tua squadra: $matchedSquad"
                                    } else {
                                        "Dati importati! Rilevate ${data.squads.size} squadre e ${data.shiftDefinitions.size} turni."
                                    }
                                } else {
                                    val ex = result.exceptionOrNull()
                                    popupType = PopupType.ERROR
                                    feedbackMessage = ex?.message ?: "Impossibile estrarre i dati. Verifica il testo incollato."
                                }
                            },
                            enabled = pastedAiText.isNotBlank() && !isSaving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ObsidianPrimaryContainer,
                                disabledContainerColor = ObsidianSurfaceContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Elabora Risposta AI",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // ---------------------------------------------------------------------
                // MODALITÀ B: CON API KEY GOOGLE GEMINI FLASH VISION (DIRETTA)
                // ---------------------------------------------------------------------
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = ObsidianPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Configurazione & Documento",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 1. Google Gemini API Key (visualizzazione mascherata & modifica)
                        if (!isEditingApiKey && apiKey.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Gemini API Key",
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = SecurePreferencesManager.getMaskedApiKey(context).ifBlank { "••••••••" },
                                                fontSize = 13.sp,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            apiKeyInput = apiKey
                                            isEditingApiKey = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = ObsidianPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Modifica",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ObsidianPrimary
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("Google Gemini API Key", fontSize = 12.sp) },
                                placeholder = { Text("AIzaSy...", fontSize = 12.sp) },
                                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                leadingIcon = {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                            Icon(
                                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (isApiKeyVisible) "Nascondi chiave" else "Mostra chiave",
                                                tint = TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (apiKeyInput.isNotBlank()) {
                                            IconButton(
                                                onClick = {
                                                    val trimmed = apiKeyInput.trim()
                                                    apiKey = trimmed
                                                    SecurePreferencesManager.saveApiKey(context, trimmed)
                                                    isEditingApiKey = false
                                                    popupType = PopupType.SUCCESS
                                                    feedbackMessage = "API Key memorizzata con successo!"
                                                }
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Salva", tint = Color(0xFF10B981))
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = ObsidianPrimary,
                                    unfocusedBorderColor = ObsidianBorderSubtle
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (apiKey.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { isEditingApiKey = false }) {
                                        Text("Annulla", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Inserimento Nome Utente per auto-matching squadra a monte
                        OutlinedTextField(
                            value = userName,
                            onValueChange = {
                                userName = it
                                SecurePreferencesManager.saveUserName(context, it)
                            },
                            label = { Text("Nome da cercare nel file (Auto-selezione)", fontSize = 12.sp) },
                            placeholder = { Text("Es. Marco Graziani o Graziani Marco", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = ObsidianPrimary,
                                unfocusedBorderColor = ObsidianBorderSubtle
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3. Selezione Documento (Immagini & PDF) con visualizzazione miniatura
                        Text(
                            text = "DOCUMENTO O PROSPETTO TURNI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isProcessingDocument) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ObsidianSurfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = ObsidianPrimary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Elaborazione file ad alta risoluzione...",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        } else if (thumbnailBitmap != null) {
                            // Anteprima miniatura creata da DocumentProcessor.createThumbnail
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                                        contentDescription = "Miniatura Documento",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(8.dp))
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = documentTypeLabel ?: "Documento Caricato",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                        val bmp = documentBitmap
                                        if (bmp != null) {
                                            Text(
                                                text = "${bmp.width} × ${bmp.height} px • Pronto per Vision",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                        if (selectedUri != null) {
                                            Text(
                                                text = selectedUri?.lastPathSegment ?: "",
                                                fontSize = 10.sp,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            selectedUri = null
                                            documentBitmap = null
                                            thumbnailBitmap = null
                                            documentTypeLabel = null
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Rimuovi file",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            // Pulsanti di selezione file
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Foto / Gallery", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Documento PDF", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 4. Bottone primario: "Analizza con Gemini Flash Vision"
                        Button(
                            onClick = {
                                val activeKey = apiKey.trim().ifBlank { apiKeyInput.trim() }
                                if (activeKey.isBlank()) {
                                    popupType = PopupType.WARNING
                                    feedbackMessage = "Configura prima la tua API Key di Google Gemini."
                                    return@Button
                                }
                                val bmp = documentBitmap
                                if (bmp == null) {
                                    popupType = PopupType.WARNING
                                    feedbackMessage = "Seleziona prima un'immagine o un documento PDF da analizzare."
                                    return@Button
                                }

                                isAnalyzing = true
                                coroutineScope.launch {
                                    try {
                                        val (base64, mime) = withContext(Dispatchers.IO) {
                                            DocumentProcessor.bitmapToBase64(bmp)
                                        }
                                        val result = withContext(Dispatchers.IO) {
                                            GeminiApiClient.extractShiftData(
                                                apiKey = activeKey,
                                                imageBase64 = base64,
                                                mimeType = mime
                                            )
                                        }

                                        if (result.isSuccess) {
                                            val data = result.getOrNull()!!
                                            geminiData = data

                                            // Auto-matching squadra con il nome utente configurato
                                            val matchedSquad = GeminiShiftMapper.matchUserSquad(data, userName)
                                            selectedSquadName = matchedSquad ?: data.squads.firstOrNull()?.squadName

                                            popupType = PopupType.SUCCESS
                                            feedbackMessage = if (matchedSquad != null) {
                                                "Analisi riuscita! Individuata la tua squadra: $matchedSquad"
                                            } else {
                                                "Analisi completata! Rilevate ${data.squads.size} squadre e ${data.shiftDefinitions.size} turni."
                                            }
                                        } else {
                                            val ex = result.exceptionOrNull()
                                            popupType = PopupType.ERROR
                                            feedbackMessage = ex?.message ?: "Errore durante l'analisi con Gemini Flash."
                                        }
                                    } catch (e: Exception) {
                                        popupType = PopupType.ERROR
                                        feedbackMessage = "Errore: ${e.localizedMessage ?: e.message}"
                                    } finally {
                                        isAnalyzing = false
                                    }
                                }
                            },
                            enabled = !isAnalyzing && !isProcessingDocument && documentBitmap != null,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ObsidianPrimaryContainer,
                                disabledContainerColor = ObsidianSurfaceContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Analisi Gemini Flash Vision in corso...",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Analizza con Gemini Flash Vision",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // RISULTATI ESTRAZIONE & ASSEGNAZIONE SQUADRA
            // =========================================================================
            val currentData = geminiData
            if (currentData != null) {
                Spacer(modifier = Modifier.height(20.dp))

                // Card turni e orari estratti
                Text(
                    text = "TURNI E ORARI RILEVATI DALLA LEGENDA",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (currentData.shiftDefinitions.isEmpty()) {
                            Text(
                                text = "Nessuna tabella oraria esplicita trovata nel documento.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        } else {
                            currentData.shiftDefinitions.forEachIndexed { idx, def ->
                                val colorParsed = GeminiShiftMapper.parseColorHex(def.colorHex, idx)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(colorParsed)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = def.code.take(4),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = def.name.ifBlank { def.code },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                            if (def.isReperibilita) {
                                                Text(
                                                    text = "Reperibilità attiva",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFF59E0B),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ObsidianSurfaceContainer
                                    ) {
                                        Text(
                                            text = "${def.startTime} - ${def.endTime}",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (idx < currentData.shiftDefinitions.size - 1) {
                                    Divider(
                                        color = ObsidianBorderSubtle,
                                        thickness = 0.6.dp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        if (!currentData.validityStart.isNullOrBlank() || !currentData.validityEnd.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Periodo di validità: ${currentData.validityStart ?: "Inizio"} → ${currentData.validityEnd ?: "Fine"}",
                                fontSize = 11.sp,
                                color = ObsidianPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!currentData.notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Note: ${currentData.notes}",
                                fontSize = 10.5.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Card Squadre e Rotazioni rilevate
                Text(
                    text = "SQUADRE E ROTAZIONI CICLICHE",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                currentData.squads.forEach { squad ->
                    val squadNormId = GeminiShiftMapper.normalizeRotationId(squad.squadName)
                    val isSelected = selectedSquadName.equals(squad.squadName, ignoreCase = true)
                    val isDefault = (ShiftRepository.defaultRotationId == squadNormId)

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
                            if (isSelected) ObsidianPrimary else if (isDefault) Color(0xFFF59E0B) else ObsidianBorder
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
                                            text = "Ciclo di ${squad.cycleDays} giorni • ${squad.employees.size} turnisti rilevati",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                // Pulsante stella predefinito con micro-animazione elastica
                                ElasticStarButton(
                                    isDefault = isDefault,
                                    onToggle = {
                                        val isNowDefault = ShiftRepository.toggleDefaultRotation(squadNormId)
                                        popupType = PopupType.INFO
                                        feedbackMessage = if (isNowDefault) {
                                            "'${squad.squadName}' impostata come squadra predefinita!"
                                        } else {
                                            "Squadra predefinita revocata per '${squad.squadName}'"
                                        }
                                    }
                                )
                            }

                            // Elenco colleghi assegnati
                            if (squad.employees.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Groups,
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

                            // Visualizzazione sequenza codici turno
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
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // Nota visiva esplicativa (§ R4): Promemoria rifinitura turni nel catalogo
                // =========================================================================
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ObsidianPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = ObsidianPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "I turni potranno essere rifiniti in qualsiasi momento nell'apposita sezione 'Catalogo Turni' dell'app.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottone primario: Salva nel catalogo (con debounce isSaving)
                Button(
                    onClick = {
                        if (isSaving || isAnalyzing) return@Button
                        isSaving = true

                        val setupResult = GeminiShiftMapper.registerExtractedData(
                            repository = ShiftRepository,
                            data = currentData,
                            targetUserName = userName
                        )

                        popupType = PopupType.SUCCESS
                        feedbackMessage = "Turni registrati nel catalogo! ${setupResult.message}"

                        coroutineScope.launch {
                            delay(1500)
                            isSaving = false
                            onDismiss()
                        }
                    },
                    enabled = !isSaving && !isAnalyzing,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianPrimaryContainer,
                        disabledContainerColor = ObsidianPrimaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Salvataggio in corso...",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Salva nel catalogo",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Bottone secondario facoltativo: Modifica (con dialog modale di avviso di uscita)
                if (onNavigateToTurni != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (!isSaving && !isAnalyzing) {
                                showExitToEditDialog = true
                            }
                        },
                        enabled = !isSaving && !isAnalyzing,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditCalendar,
                            contentDescription = null,
                            tint = if (!isSaving && !isAnalyzing) TextPrimary else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Modifica",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!isSaving && !isAnalyzing) TextPrimary else TextMuted
                        )
                    }

                    if (showExitToEditDialog) {
                        AlertDialog(
                            onDismissRequest = { showExitToEditDialog = false },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Attenzione",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 17.sp
                                    )
                                }
                            },
                            text = {
                                Text(
                                    text = "Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni.",
                                    color = TextSecondary,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showExitToEditDialog = false
                                        GeminiShiftMapper.registerExtractedData(
                                            repository = ShiftRepository,
                                            data = currentData,
                                            targetUserName = userName
                                        )
                                        val targetRot = ShiftRepository.rotations.find {
                                            it.name.equals(selectedSquadName, ignoreCase = true)
                                        } ?: ShiftRepository.activeRotation
                                        onNavigateToTurni(targetRot)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "Esci e Modifica",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { showExitToEditDialog = false },
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                                ) {
                                    Text(
                                        text = "Rimani qui",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            containerColor = ObsidianSurfaceContainer,
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // Ergonomia Riccardo Breccia: Spacer 100dp in fondo per la Thumb Zone
            // =========================================================================
            Spacer(modifier = Modifier.height(100.dp))
        }

        // =========================================================================
        // ZERO card inline statiche: Notifiche ESCLUSIVAMENTE via AnimatedStatusPopup
        // =========================================================================
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
 * Pulsante stella per turno predefinito con micro-animazione elastica Riccardo Breccia UX.
 */
@Composable
private fun ElasticStarButton(
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
        color = if (isDefault) Color(0xFFF59E0B).copy(alpha = 0.2f) else ObsidianSurfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isDefault) Color(0xFFF59E0B) else ObsidianBorderSubtle
        ),
        modifier = modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isDefault) "Revoca predefinito" else "Imposta come predefinito",
                tint = if (isDefault) Color(0xFFFBBF24) else TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .scale(scale.value)
            )
        }
    }
}
