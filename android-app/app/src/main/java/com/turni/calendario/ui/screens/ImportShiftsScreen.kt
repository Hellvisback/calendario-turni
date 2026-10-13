package com.turni.calendario.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.importer.NgeniaParseResult
import com.turni.calendario.importer.NgeniaTurniParser
import com.turni.calendario.importer.ParsedEmployee
import com.turni.calendario.importer.ParsedTeamRotation
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.format.DateTimeFormatter

const val RFI_EXAMPLE_TABELLONE = """MOD. P.R.
Turno Valido dal 21/09/2026 al 18/10/2026
• F Ferie • C Congedo • PS Permesso studio • CF Corso Formativo • TGF Turno giornaliero formazione • T Trasferta
Orari:
M 06:00-13:36
P 14:00-21:36
N 22:00-05:36
MD 08:36-16:12
Reperibilità dalle 0:00 alle 24:00 chi fa il turno P. La Domenica Reperibile chi fa il turno MD.
U.T. Pescara, UMIS 3 DOIT ANCONA

Squadra B
21/9 22/9 23/9 24/9 25/9 26/9 27/9 28/9 29/9 30/9 1/10 2/10 3/10 4/10 5/10 6/10 7/10 8/10 9/10 10/10 11/10 12/10 13/10 14/10 15/10 16/10 17/10 18/10
Marcozzi Loris Man. Sì X 8024724 P P Rc N N M Ri Rc N N TGF Rc Ri TGF M M Ri Rc TGF Ri
Roscini Giordano Man. Sì X X 8807551 TGF TGF TGF TGF TGF Rc Ri TGF Rc Ri TGF TGF TGF Rc Ri Rc TGF Ri
Iezzi Valerio OP.IS Si 8027562 Rc Ri Rc Ri Rc Ri Rc TGF Ri
Tomassini Andrea OP.IS No 8052223 Rc Ri Rc F F Ri Rc Ri Rc TGF Ri
Raglione Luca Capo Tecn. Si X 8024725 N Ri M M Rc N MD P MD M M Ri Rc N M M TGF Rc Ri Rc TGF Ri
Marcucci Roberto Man. Sì X 8004855 Rc Ri TGF Rc Ri Rc Ri Rc TGF Ri
Carestia Stefano Man. Sì 8024721 Rc Ri Rc Ri Rc Ri Rc TGF Ri
Di_Camillo Gianluca Capo Tecn. Si X X 8043762 Rc Ri Rc Ri TGF Rc Ri Rc TGF Ri
Di_Iulio Orazio OP.IS No 8027558 Rc Ri TGF F Rc Ri Rc Ri Rc TGF Ri
Galli Andrea Man. Si X X 8024717 Rc Ri Rc Ri Rc F Ri Rc TGF Ri
Antinucci Alessandro Man. Si 8027554 Rc F F Ri Rc Ri Rc Ri Rc TGF Ri
Iacobucci Daniele OP.IS No 8027561 Rc Ri Rc Ri Rc Ri Rc TGF Ri
Graziani Marco OP.IS No 8050253 Rc Ri Rc Ri Rc Ri Rc TGF Ri
N N RISE M P MD M M P P P RGLS N N RISE M M RGLS N RISE P P RGLS N N M RISE

Squadra C
D'Angelo Stefano Capo Tecn. Sì X 8024458 TGF PS Rc Ri Rc Ri Rc Ri Rc Ri TGF
Naki Amine Man. Sì 8027565 TGF Rc Ri Rc Ri Rc Ri Rc Ri TGF
Genovese Marco OP.IS Sì 8050250 TGF Rc Ri Rc Ri Rc Ri Rc Ri TGF
Asanoski Ardit OP.IS Sì 8052222 TGF F Rc Ri Rc Ri Rc Ri Rc Ri TGF
Sincic Stefano Capo Tecn. Sì X X 8006501 Rc Ri Rc Ri Rc Ri Rc Ri TGF
Di_Loreto Alfonso Man. Sì X 8049757 Rc Ri Rc Ri Rc Ri Rc Ri TGF
Ferretti Gianluca Man. Sì X 8087923 Rc Ri TGF F Rc F Ri Rc Ri Rc Ri TGF
D'Amore Donato Man. Si X 8024722 TGF TGF TGF TGF TGF Rc F TGF Ri Rc Ri TGF TGF TGF Rc Ri Rc Ri TGF
D'Ambrosio Paolo Man. Si 8027556 Rc Ri Rc Ri Rc F Ri Rc Ri TGF
Caputo Raffaele OP.IS No 8027555 MD TGF Rc Ri Rc Ri Rc Ri Rc Ri TGF
Giangiulli Giorgia OP.IS No 8050251 TGF TGF TGF TGF TGF Rc Ri Rc Ri Rc Ri Rc Ri TGF
Gervasio Francesco OP.IS No 8027560 MD TGF Rc Ri Rc Ri Rc Ri Rc Ri TGF
M M P P P RGLS N N RISE M M RGLS N RISE P P RGLS N N M RISE RGLS N N RISE M P MD

Squadra B 1
Mongia Andrea Tecnico No X X 8012390 Rc Ri Ri Rc Rc Ri Rc TGF Ri
Paolucci Paolo Capo Tecn. No X X 8093141 Rc Ri M M Ri Rc TGF Rc Ri Rc Ri
P P P RGLS RISE M M M M RISE DEL 30/9 P P P RGLS M M M M P RGLS RISE P P P RGLS M M RISE

Squadra D
Argentesi Alessandro Capo Tecn. Sì X 8088362 Ri Rc Ri Rc Ri Rc Ri Rc
Tria Piergiorgio Man. Sì 8027569 Ri Rc Ri Rc Ri Rc Ri Rc
Iacutone Simone OP.IS Sì 8028556 Ri Rc Ri Rc Ri Rc Ri Rc
Leonzio Giordano Capo Tecn. Sì X X 8081569 Ri Rc Ri Rc Ri Rc Ri Rc
Manfrè Milco Man. Sì X X 8012419 Ri Rc F Ri Rc Ri Rc Ri Rc
De_Amicis Isaia Man. Sì X 8023742 Ri Rc Ri TGF Rc Ri Rc Ri Rc
Michelucci Andrea Man. Sì X 8049759 TGF TGF TGF TGF TGF Ri Ri TGF Rc Rc Ri Rc TGF TGF TGF TGF TGF Rc
Raglione Luca Capo Tecn. Si X 8024725 F Ri F F Rc F Ri Rc Ri Rc Ri Rc
Menna Federico Man. Si X 8027564 Ri F Rc Ri TGF Rc Ri Rc Ri F F F Rc
Stella Daniele Man. Si 8027567 TGF TGF TGF TGF TGF Ri Ri TGF Rc Rc Ri Rc TGF TGF TGF Ri Rc
Ciccia_Romito Davide OP.IS No 8050248 Ri Rc Ri Rc Ri Rc Ri F Rc
Marrollo Davide OP.IS No 8054100 Ri Rc Ri Rc Ri Rc Ri Rc
Calabrese Emanuele OP.IS No 8050247 F Ri Rc Ri TGF Rc F Ri Rc Ri Rc
N RISE M M RGLS N RISE P P RGLS N N M RISE RGLS N N RISE M P MD M M P P P RGLS N

Squadra A
Haddad Eros Capo Tecn. Sì X X 8015974 M TGF Rc Ri Rc F Ri MD TGF Rc Ri Rc Ri
Tardivo Antonio Man. Sì X 8049761 M TGF Rc Ri Rc Ri Rc Ri TGF Rc Ri
Ciommo Simone OP.IS Sì 8050249 M TGF Rc Ri Rc Ri F TGF Rc Ri Rc Ri
Anselmi Mariano OP.IS No 8052221 M TGF Rc F Ri Rc Ri Rc Ri Rc Ri
Amabile Crescenzo OP.IS No 8052220 M TGF Rc Ri Rc Ri Rc F F Ri F F Rc F Ri
Ricci Danny Man. Sì 8024727 Rc Ri Rc Ri Rc Ri Rc Ri
Partenza Simone Man. Sì X 8049760 Rc F Ri Rc Ri Rc Ri Rc Ri
Tinozzi Tiziano OP.IS Si 8027568 Rc Ri Rc Ri Rc Ri Rc Ri
Monaco Luca Capo Tecn. Si X 8020871 TGF TGF TGF TGF TGF Rc Ri TGF M M Ri P Rc F TGF TGF TGF Rc Ri TGF Rc Ri
Di_Petta Lorenzo Man. Si X 8023743 Rc Ri Rc Ri Rc Ri Rc Ri
Verì William OP.IS Si 8027570 Rc Ri Rc Ri Rc Ri Rc Ri
D'Orazio Manuel OP.IS Si 8027559 Rc Ri Rc Ri F Rc F F Ri F F Rc F Ri
Pizzoferrato Matteo OP.IS No 8050254 Rc C Ri Rc Ri TGF TGF Rc Ri Rc Ri
P P RGLS N N M RISE RGLS N N RISE M P MD M M P P P RGLS N N RISE M M RGLS N RISE"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportShiftsScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf<NgeniaParseResult?>(null) }
    var selectedTeam by remember { mutableStateOf<ParsedTeamRotation?>(null) }
    var employeeSearchQuery by remember { mutableStateOf("") }
    var selectedEmployee by remember { mutableStateOf<ParsedEmployee?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var popupType by remember { mutableStateOf(PopupType.SUCCESS) }
    var isProcessing by remember { mutableStateOf(false) }
    var overwriteCalendar by remember { mutableStateOf(true) }
    var setOnCallCalendar by remember { mutableStateOf(true) }

    // File picker launcher per PDF, CSV, TXT, Excel
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = reader.readText()
                    rawText = content
                    val res = NgeniaTurniParser.parseDocumentText(content)
                    parseResult = res
                    selectedTeam = res.teams.firstOrNull()
                    popupType = PopupType.SUCCESS
                    feedbackMessage = "File caricato! Rilevate ${res.teams.size} squadre e ${res.employees.size} turnisti."
                }
            } catch (e: Exception) {
                popupType = PopupType.ERROR
                feedbackMessage = "Errore durante la lettura del file: ${e.localizedMessage}"
            }
        }
    }

    // Auto-parse all'inizio se il testo cambia
    fun executeParsing(text: String) {
        if (text.isBlank()) return
        isProcessing = true
        val result = NgeniaTurniParser.parseDocumentText(text)
        parseResult = result
        selectedTeam = result.teams.find { it.rotationId == ShiftRepository.defaultRotationId } ?: result.teams.firstOrNull()
        isProcessing = false
        popupType = PopupType.SUCCESS
        feedbackMessage = "Analisi completata: trovate ${result.teams.size} squadre nel tabellone!"
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
        // Top Bar
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

        // Banner informativo & Quick Start
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Algoritmo Intelligente Tabellone",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Invia o incolla il PDF di 'ngeniaTurni o il tabellone Mod. P.R. RFI. L'algoritmo rileva le squadre, gli orari, le pause e crea le rotazioni in automatico per tutti i turnisti.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pulsante Sfoglia File
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sfoglia File", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Pulsante Carica Esempio RFI
                    OutlinedButton(
                        onClick = {
                            rawText = RFI_EXAMPLE_TABELLONE
                            executeParsing(RFI_EXAMPLE_TABELLONE)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1.3f).height(40.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Carica Esempio RFI", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFBBF24))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Campo Input Testo / OCR
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Contenuto o Testo Estratto dal PDF",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (rawText.isNotEmpty()) {
                        TextButton(onClick = { rawText = ""; parseResult = null }) {
                            Text("Cancella", color = Color(0xFFEF4444), fontSize = 11.5.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                    },
                    placeholder = { Text("Incolla qui il testo del PDF o tabellone turni...", fontSize = 12.sp, color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    maxLines = 4,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = ObsidianPrimary,
                        unfocusedBorderColor = ObsidianBorderSubtle
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { executeParsing(rawText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analizza Tabellone Turni", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            // STEP 1: TROVA IL TUO NOME NEL TABELLONE
            // ==========================================
            Text(
                text = "1. CHI SEI NEL TABELLONE? (TROVA LA TUA FILA)",
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
                    Text(
                        text = "Digita il tuo cognome o nome per caricare la tua riga di turni:",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = employeeSearchQuery,
                        onValueChange = { employeeSearchQuery = it },
                        placeholder = { Text("Es. Marcozzi, Raglione, Luca, Eros, Tardivo...", fontSize = 12.sp, color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null, tint = ObsidianPrimary) },
                        trailingIcon = {
                            if (employeeSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { employeeSearchQuery = "" }) {
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

                    val filteredEmployees = if (employeeSearchQuery.isBlank()) {
                        result.employees.take(4)
                    } else {
                        result.employees.filter {
                            it.name.contains(employeeSearchQuery, ignoreCase = true) ||
                            it.teamName.contains(employeeSearchQuery, ignoreCase = true) ||
                            it.phone.contains(employeeSearchQuery)
                        }
                    }

                    if (filteredEmployees.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
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
                                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                                        ) {
                                            Text("REP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
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
                                        val chipColor = def?.color ?: Color(0xFF1E2638)

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
                                                    color = Color.White
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
                                            checkedThumbColor = Color.White,
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
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = Color(0xFFF59E0B)
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
                                                description = "Rotazione ${team.teamName} da tabellone RFI"
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
                                    Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Applica i Miei Turni al Calendario",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White
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
                        color = if (isSelected) ObsidianPrimary else if (isDefault) Color(0xFFF59E0B) else ObsidianBorder
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
                                        if (isDefault) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "⭐ PREDEFINITO",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFFFBBF24),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${team.totalDays} giorni • ${team.members.size} turnisti assegnati",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Tasto rapido Stella Predefinito (compare per primo in modifica/rotazione)
                            Surface(
                                onClick = {
                                    ShiftRepository.setDefaultRotation(team.rotationId)
                                    feedbackMessage = "'${team.teamName}' impostata come turno predefinito!"
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isDefault) Color(0xFFF59E0B).copy(alpha = 0.2f) else ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isDefault) Color(0xFFF59E0B) else ObsidianBorderSubtle),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (isDefault) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Imposta Predefinito",
                                        tint = if (isDefault) Color(0xFFFBBF24) else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
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
                                val col = shiftDef?.color ?: Color(0xFF1E2638)
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
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Azioni rapide per la squadra
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val msg = ShiftRepository.importNgeniaTurniResult(
                                        result = result,
                                        selectedTeamName = team.teamName,
                                        makeDefault = true,
                                        applyToCalendar = false
                                    )
                                    feedbackMessage = msg
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(38.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
                            ) {
                                Text("Salva nel Catalogo", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            }

                            Button(
                                onClick = {
                                    val msg = ShiftRepository.importNgeniaTurniResult(
                                        result = result,
                                        selectedTeamName = team.teamName,
                                        makeDefault = true,
                                        applyToCalendar = true
                                    )
                                    feedbackMessage = msg
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimaryContainer),
                                modifier = Modifier.weight(1.2f).height(38.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Applica Ciclo Squadra", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    AnimatedStatusPopup(
        message = feedbackMessage,
        type = popupType,
        onDismiss = { feedbackMessage = null }
    )
    }
}
