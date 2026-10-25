package com.turni.calendario.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.importer.NgeniaParseResult
import com.turni.calendario.importer.NgeniaTurniParser
import com.turni.calendario.importer.ParsedEmployee
import com.turni.calendario.importer.ParsedTeamRotation
import com.turni.calendario.importer.TextImportSanitizer
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportShiftsScreen(
    onDismiss: () -> Unit,
    onNavigateToEdit: ((com.turni.calendario.model.ShiftRotation?) -> Unit)? = null
) {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf<NgeniaParseResult?>(null) }
    var selectedTeam by remember { mutableStateOf<ParsedTeamRotation?>(null) }
    var workerNameFilter by remember { mutableStateOf("") }
    var employeeSearchQuery by remember { mutableStateOf("") }
    var selectedEmployee by remember { mutableStateOf<ParsedEmployee?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var popupType by remember { mutableStateOf(PopupType.SUCCESS) }
    var isProcessing by remember { mutableStateOf(false) }
    var showAllEmployees by remember { mutableStateOf(false) }
    var overwriteCalendar by remember { mutableStateOf(true) }
    var setOnCallCalendar by remember { mutableStateOf(true) }
    var showExitToEditDialog by remember { mutableStateOf(false) }
    var rotationToEditOnExit by remember { mutableStateOf<com.turni.calendario.model.ShiftRotation?>(null) }

    // Esegue il parsing completo del testo con auto-selezione turnista a monte
    fun executeParsing(text: String) {
        if (text.isBlank()) return
        isProcessing = true
        val sanitized = TextImportSanitizer.sanitize(text)
        val result = NgeniaTurniParser.parseDocumentText(sanitized)
        parseResult = result

        // Auto-filtro e pre-selezione a monte del lavoratore se indicato
        val query = workerNameFilter.trim().ifBlank { employeeSearchQuery.trim() }
        if (query.isNotBlank()) {
            val match = result.employees.find { NgeniaTurniParser.matchesEmployeeQuery(it, query) }
            if (match != null) {
                selectedEmployee = match
                selectedTeam = result.teams.find { it.teamName.equals(match.teamName, ignoreCase = true) }
            } else {
                selectedEmployee = null
                selectedTeam = result.teams.find { it.rotationId == ShiftRepository.defaultRotationId } ?: result.teams.firstOrNull()
            }
        } else {
            selectedTeam = result.teams.find { it.rotationId == ShiftRepository.defaultRotationId } ?: result.teams.firstOrNull()
        }

        isProcessing = false
        popupType = PopupType.SUCCESS
        feedbackMessage = "Analisi completata: rilevate ${result.teams.size} squadre e ${result.employees.size} turnisti!"
    }

    // File picker launcher per PDF, CSV, TXT, Excel con pulizia automatica di byte/binari
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    val cleaned = TextImportSanitizer.extractAndCleanBytes(bytes)
                    if (cleaned.isNotBlank()) {
                        rawText = cleaned
                        executeParsing(cleaned)
                    } else {
                        popupType = PopupType.WARNING
                        feedbackMessage = "Nessun testo leggibile trovato nel file. Incolla il testo direttamente o verifica il formato."
                    }
                }
            } catch (e: Exception) {
                popupType = PopupType.ERROR
                feedbackMessage = "Errore durante la lettura del file: ${e.localizedMessage}"
            }
        }
    }

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
        // Top Bar (Singolo pulsante indietro in alto a sinistra)
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
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Indietro",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Importazione Turni",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Importa da file Excel, CSV, PDF o testo tabellone",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // PANNELLO UNIFICATO: Sfoglia file, inserimento/modifica manuale del testo e selezione turnista a monte
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = ObsidianPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Caricamento e Analisi Turni",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Sfoglia file o incolla testo, indicando il lavoratore da filtrare",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Campo a monte: Nome del lavoratore/dipendente da cercare nel file
                Text(
                    text = "Nome da cercare nel file",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(5.dp))
                OutlinedTextField(
                    value = workerNameFilter,
                    onValueChange = { query ->
                        workerNameFilter = query
                        employeeSearchQuery = query
                        parseResult?.let { res ->
                            if (query.isNotBlank()) {
                                val match = res.employees.find { NgeniaTurniParser.matchesEmployeeQuery(it, query) }
                                if (match != null) {
                                    selectedEmployee = match
                                    selectedTeam = res.teams.find { it.teamName.equals(match.teamName, ignoreCase = true) }
                                } else {
                                    selectedEmployee = null
                                }
                            }
                        }
                    },
                    placeholder = { Text("Es. Marcozzi, Raglione, Luca, Eros, Tardivo...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.PersonSearch,
                            contentDescription = null,
                            tint = ObsidianPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (workerNameFilter.isNotEmpty()) {
                            IconButton(onClick = {
                                workerNameFilter = ""
                                employeeSearchQuery = ""
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Pulisci", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = ObsidianPrimary,
                        unfocusedBorderColor = ObsidianBorderSubtle
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Selezione file ("Sfoglia File")
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sfoglia File (PDF, Excel, CSV, TXT)", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Area di testo modificabile per correzioni in tempo reale
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Testo estratto o inserimento manuale:",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    if (rawText.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                rawText = ""
                                parseResult = null
                                selectedEmployee = null
                                selectedTeam = null
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Cancella", color = ColorNegative, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                    },
                    placeholder = { Text("Il testo decodificato apparirà qui. Puoi correggerlo ed eseguire nuovamente l'analisi...", fontSize = 11.5.sp, color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp),
                    maxLines = 6,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = ObsidianPrimary,
                        unfocusedBorderColor = ObsidianBorderSubtle
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Pulsante Analizza
                Button(
                    onClick = { executeParsing(rawText) },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analisi in corso...", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Analizza Tabellone Turni", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }


        // SEZIONE RISULTATI ANALISI (Se il parsing è riuscito)
        val result = parseResult
        if (result != null) {
            Spacer(modifier = Modifier.height(18.dp))

            // Riepilogo Documento & Orari
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (result.validityStart != null && result.validityEnd != null) {
                                "Turno Valido: ${result.validityStart.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} - ${result.validityEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
                            } else {
                                "Periodo di Turnazione Rilevato"
                            },
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Orari rilevati con badge
                    if (result.detectedHours.isNotEmpty()) {
                        Text("Orari e turni rilevati dal PDF (nessuna duplicazione):", fontSize = 11.5.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            result.detectedHours.forEach { (code, times) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ObsidianSurfaceHigh,
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle)
                                ) {
                                    Text(
                                        text = "$code: ${times.first} - ${times.second}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Orari sincronizzati automaticamente senza duplicazioni",
                                fontSize = 10.5.sp,
                                color = TextMuted
                            )
                            TextButton(
                                onClick = {
                                    ShiftRepository.ensureShiftDefinitionsFromParsedHours(result.detectedHours)
                                    feedbackMessage = "Orari turni sincronizzati con successo (${result.detectedHours.size} fasce)!"
                                }
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aggiorna Orari", fontSize = 11.sp, color = ObsidianPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // STEP 1: TURNISTA PRE-SELEZIONATO A MONTE
            // ==========================================
            val activeQuery = workerNameFilter.trim()
            val filteredEmployees = if (activeQuery.isBlank()) {
                if (showAllEmployees) result.employees else result.employees.take(5)
            } else {
                result.employees.filter { NgeniaTurniParser.matchesEmployeeQuery(it, activeQuery) }
            }

            Text(
                text = "1. TURNISTA PRE-SELEZIONATO A MONTE (${result.employees.size} RILEVATI)",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ObsidianPrimary,
                letterSpacing = 1.1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (activeQuery.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianPrimary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianPrimary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Filtro impostato a monte: \"$activeQuery\"",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianPrimary
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Nessun filtro a monte impostato. Seleziona il tuo nominativo tra quelli rilevati:",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                    }

                    if (filteredEmployees.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        filteredEmployees.forEach { emp ->
                            val isChosen = selectedEmployee?.name == emp.name
                            Surface(
                                onClick = {
                                    selectedEmployee = emp
                                    // Sincronizza anche la squadra
                                    selectedTeam = result.teams.find { it.teamName.equals(emp.teamName, ignoreCase = true) }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChosen) ObsidianPrimary.copy(alpha = 0.15f) else ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isChosen) 1.5.dp else 0.8.dp,
                                    color = if (isChosen) ObsidianPrimary else ObsidianBorderSubtle
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = isChosen,
                                            onClick = {
                                                selectedEmployee = emp
                                                selectedTeam = result.teams.find { it.teamName.equals(emp.teamName, ignoreCase = true) }
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = ObsidianPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(emp.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = if (isChosen) ObsidianPrimary else TextPrimary)
                                            Text("${emp.teamName} • ${emp.role} (Matr. ${emp.phone})", fontSize = 11.sp, color = TextSecondary)
                                        }
                                    }
                                    if (emp.isOnCallGeneral) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ColorReperibilita.copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(0.6.dp, ColorReperibilita.copy(alpha = 0.5f))
                                        ) {
                                            Text("REP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ColorReperibilita, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else if (activeQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nessun turnista trovato per \"$activeQuery\". Prova con cognome, nome o matricola.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (activeQuery.isBlank() && result.employees.size > 5) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = { showAllEmployees = !showAllEmployees },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (showAllEmployees) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = ObsidianPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showAllEmployees) "Mostra meno" else "Mostra tutti i ${result.employees.size} turnisti...",
                                    color = ObsidianPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Se è selezionato un dipendente, mostra l'anteprima dei suoi turni e il tasto per applicarli al calendario
                    val currentEmp = selectedEmployee
                    if (currentEmp != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Turni di ${currentEmp.name}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.5.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${currentEmp.dayShifts.size} turni individuali rilevati per il periodo",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = ObsidianPrimary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = currentEmp.teamName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ObsidianPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Visualizzatore orizzontale dei turni effettivi del dipendente
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    currentEmp.dayShifts.toList().sortedBy { it.first }.forEach { (date, rawCode) ->
                                        val defId = NgeniaTurniParser.mapCodeToShiftDefId(rawCode)
                                        val def = ShiftRepository.getShiftDefinition(defId)
                                        val chipColor = def?.color ?: ObsidianSurfaceContainer

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(36.dp)
                                        ) {
                                            Text(
                                                text = "${date.dayOfMonth}/${date.monthValue}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextMuted
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(chipColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = rawCode.take(3),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = catalogInk(chipColor)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Opzioni di inserimento
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Sovrascrivi turni esistenti", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("Sostituisce i turni già presenti in queste date", fontSize = 10.sp, color = TextSecondary)
                                    }
                                    Switch(
                                        checked = overwriteCalendar,
                                        onCheckedChange = { overwriteCalendar = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = ObsidianPrimary
                                        )
                                    )
                                }

                                if (currentEmp.isOnCallGeneral) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Imposta Reperibilità", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                            Text("Marca reperibile nelle date del turno", fontSize = 10.sp, color = TextSecondary)
                                        }
                                        Switch(
                                            checked = setOnCallCalendar,
                                            onCheckedChange = { setOnCallCalendar = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                checkedTrackColor = ColorReperibilita
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Bottone primario: Applica i Miei Turni al Calendario
                                Button(
                                    onClick = {
                                        // 1. Assicura orari e definizioni
                                        ShiftRepository.ensureShiftDefinitionsFromParsedHours(result.detectedHours)
                                        // 2. Salva le rotazioni di tutte le squadre nel catalogo
                                        for (team in result.teams) {
                                            val rot = com.turni.calendario.model.ShiftRotation(
                                                id = team.rotationId,
                                                name = team.teamName,
                                                totalDays = team.totalDays,
                                                daySequence = team.dayShiftDefIds,
                                                description = "Rotazione ${team.teamName} importata"
                                            )
                                            ShiftRepository.saveRotation(rot)
                                        }
                                        // 3. Imposta la squadra dell'utente come predefinita se trovata
                                        val myTeam = result.teams.find { it.teamName.equals(currentEmp.teamName, ignoreCase = true) }
                                        if (myTeam != null) {
                                            ShiftRepository.setDefaultRotation(myTeam.rotationId)
                                        }
                                        // 4. Applica i turni individuali al calendario
                                        val count = ShiftRepository.applyEmployeeShiftsToCalendar(
                                            employee = currentEmp,
                                            overwrite = overwriteCalendar,
                                            alsoSetOnCall = setOnCallCalendar
                                        )
                                        feedbackMessage = "Perfetto! Applicati $count turni al calendario per ${currentEmp.name}. Squadra '${currentEmp.teamName}' impostata come predefinita."
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Applica i Miei Turni al Calendario",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // STEP 2: SQUADRE E ROTAZIONI RILEVATE
            // ==========================================
            Text(
                text = "2. SQUADRE E ROTAZIONI CICLICHE (${result.teams.size})",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ObsidianPrimary,
                letterSpacing = 1.1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            result.teams.forEach { team ->
                val isSelected = selectedTeam?.teamName == team.teamName
                val isDefault = team.rotationId == ShiftRepository.defaultRotationId

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .bouncyClickable {
                            selectedTeam = team
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) ObsidianSurfaceHighest else ObsidianSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 1.5.dp else if (isDefault) 1.2.dp else 1.dp,
                        color = if (isSelected) ObsidianPrimary else if (isDefault) ColorReperibilita else ObsidianBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedTeam = team },
                                    colors = RadioButtonDefaults.colors(selectedColor = ObsidianPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = team.teamName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = if (isSelected) ObsidianPrimary else TextPrimary
                                        )
                                    }
                                    Text(
                                        text = "${team.totalDays} giorni • ${team.members.size} turnisti assegnati",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Tasto Stella Predefinito commutabile con micro-animazione elastica spring
                            ElasticStarButton(
                                isDefault = isDefault,
                                onToggle = {
                                    val isNowDefault = ShiftRepository.toggleDefaultRotation(team.rotationId)
                                    popupType = PopupType.INFO
                                    feedbackMessage = if (isNowDefault) {
                                        "'${team.teamName}' impostata come turno predefinito!"
                                    } else {
                                        "Turno predefinito revocato per '${team.teamName}' (stato neutro)"
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Mini-matrice visuale della sequenza turni (primi 14 o tutti)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            team.dayCodes.forEachIndexed { idx, code ->
                                val shiftId = team.dayShiftDefIds.getOrNull(idx)
                                val shiftDef = shiftId?.let { ShiftRepository.getShiftDefinition(it) }
                                val col = shiftDef?.color ?: ObsidianSurfaceContainer
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(col),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = code.take(3),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = catalogInk(col)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Azioni distinte: "Salva nel Catalogo" e "Modifica"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    ShiftRepository.ensureShiftDefinitionsFromParsedHours(result.detectedHours)
                                    val rot = com.turni.calendario.model.ShiftRotation(
                                        id = team.rotationId,
                                        name = team.teamName,
                                        totalDays = team.totalDays,
                                        daySequence = team.dayShiftDefIds,
                                        description = "Rotazione ${team.teamName} da importazione"
                                    )
                                    ShiftRepository.saveRotation(rot)
                                    popupType = PopupType.SUCCESS
                                    feedbackMessage = "Rotazione '${team.teamName}' e definizioni turni salvate nel catalogo!"
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                                modifier = Modifier.weight(1.3f).height(40.dp)
                            ) {
                                Icon(Icons.Default.BookmarkAdded, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Salva nel catalogo", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }

                            OutlinedButton(
                                onClick = {
                                    val rot = com.turni.calendario.model.ShiftRotation(
                                        id = team.rotationId,
                                        name = team.teamName,
                                        totalDays = team.totalDays,
                                        daySequence = team.dayShiftDefIds,
                                        description = "Rotazione ${team.teamName} da importazione"
                                    )
                                    rotationToEditOnExit = rot
                                    showExitToEditDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Modifica", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }
                        }
                    }
                }
            }

            // Nota visiva esplicativa per la rifinitura dei turni nel catalogo
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = ObsidianPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "I turni salvati nel catalogo potranno essere rifiniti in qualsiasi momento nell'apposita sezione dell'app.",
                        fontSize = 11.5.sp,
                        color = TextSecondary,
                        lineHeight = 16.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Azioni finali di chiusura e salvaguardia dati
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Azioni di Salvataggio e Chiusura",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Salva l'intera turnazione nel catalogo oppure passa alla schermata di modifica.",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                ShiftRepository.ensureShiftDefinitionsFromParsedHours(result.detectedHours)
                                for (team in result.teams) {
                                    val rot = com.turni.calendario.model.ShiftRotation(
                                        id = team.rotationId,
                                        name = team.teamName,
                                        totalDays = team.totalDays,
                                        daySequence = team.dayShiftDefIds,
                                        description = "Rotazione ${team.teamName} da importazione"
                                    )
                                    ShiftRepository.saveRotation(rot)
                                }
                                popupType = PopupType.SUCCESS
                                feedbackMessage = "Tutte le ${result.teams.size} squadre e definizioni orarie salvate con successo nel catalogo!"
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.BookmarkAdded, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salva nel catalogo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }

                        OutlinedButton(
                            onClick = {
                                val rot = selectedTeam?.let { team ->
                                    com.turni.calendario.model.ShiftRotation(
                                        id = team.rotationId,
                                        name = team.teamName,
                                        totalDays = team.totalDays,
                                        daySequence = team.dayShiftDefIds,
                                        description = "Rotazione ${team.teamName} da importazione"
                                    )
                                }
                                rotationToEditOnExit = rot
                                showExitToEditDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Modifica", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // Dialog modale di avviso prima di passare alla schermata di modifica
    if (showExitToEditDialog) {
        AlertDialog(
            onDismissRequest = { showExitToEditDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = ColorReperibilita,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Passa a Modifica",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Stai per uscire da questa schermata per passare a quella di modifica, perderai queste informazioni",
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetRot = rotationToEditOnExit ?: selectedTeam?.let { team ->
                            com.turni.calendario.model.ShiftRotation(
                                id = team.rotationId,
                                name = team.teamName,
                                totalDays = team.totalDays,
                                daySequence = team.dayShiftDefIds,
                                description = "Rotazione ${team.teamName} da importazione"
                            )
                        }
                        // 1. Assicura orari e definizioni turni singoli (M, P, N, MD, etc.) nel catalogo
                        parseResult?.let { res ->
                            ShiftRepository.ensureShiftDefinitionsFromParsedHours(res.detectedHours)
                        }
                        // 2. Salva la rotazione nel catalogo
                        if (targetRot != null) {
                            ShiftRepository.saveRotation(targetRot)
                        }
                        showExitToEditDialog = false
                        if (onNavigateToEdit != null) {
                            onNavigateToEdit(targetRot)
                        } else {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorNegative),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Conferma", color = catalogInk(ColorNegative), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitToEditDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                ) {
                    Text("Annulla", color = TextPrimary)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

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

@OptIn(ExperimentalMaterial3Api::class)
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
        color = if (isDefault) ColorReperibilita.copy(alpha = 0.2f) else ObsidianSurfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isDefault) ColorReperibilita else ObsidianBorderSubtle
        ),
        modifier = modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isDefault) "Revoca predefinito" else "Imposta come predefinito",
                tint = if (isDefault) ColorReperibilita else TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .scale(scale.value)
            )
        }
    }
}
