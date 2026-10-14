@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.turni.calendario.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.export.PdfGenerator
import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(ShiftRepository.pdfExportOptions) }
    var headerNotes by remember { mutableStateOf(options.headerNotes) }
    var showShiftFilterDialog by remember { mutableStateOf(false) }
    var showDateRangeDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .statusBarsPadding()
    ) {
        // Top App Bar Ergonomica Breccia (Unico tasto indietro in alto)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = ObsidianSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Indietro",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Esporta & Stampa PDF",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Text(
                        text = "Prospetto turni professionale",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ObsidianPrimary.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianPrimary.copy(alpha = 0.5f))
            ) {
                Text(
                    text = options.mode.label.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = ObsidianPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Segmented Control stile Liquid Glass: Elenco | Mese | Anno
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PdfExportMode.values().forEach { mode ->
                        val isSelected = options.mode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ObsidianPrimary else Color.Transparent)
                                .clickable {
                                    options = options.copy(mode = mode)
                                    ShiftRepository.pdfExportOptions = options
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                color = if (isSelected) ObsidianBackground else TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Foglio Bianco di Anteprima A4 in Miniatura con elegante ombreggiatura
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val previewWidth = if (options.mode == PdfExportMode.ELENCO) 230.dp else 280.dp
                val previewHeight = if (options.mode == PdfExportMode.ELENCO) 310.dp else 210.dp

                Surface(
                    modifier = Modifier
                        .size(width = previewWidth, height = previewHeight)
                        .shadow(16.dp, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (options.mode) {
                            PdfExportMode.ELENCO -> PreviewElencoContent(options)
                            PdfExportMode.MESE -> PreviewMeseContent(options)
                            PdfExportMode.ANNO -> PreviewAnnoContent(options)
                            PdfExportMode.PERSONALIZZATO -> PreviewPersonalizzatoContent(options)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sezione Parametri Principali
            Text(
                text = "PARAMETRI DOCUMENTO",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Riga Intervallo / Periodo
                    val intervalText = when (options.mode) {
                        PdfExportMode.ELENCO, PdfExportMode.PERSONALIZZATO -> "${options.startDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"))} - ${options.endDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"))}"
                        PdfExportMode.MESE -> "${options.startDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }} ${options.startDate.year}"
                        PdfExportMode.ANNO -> "${options.selectedYear}"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable { showDateRangeDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Periodo di Stampa", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(intervalText, color = ObsidianPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }

                    Divider(color = ObsidianBorderSubtle, thickness = 0.8.dp)

                    // Riga Filtro Turni
                    val currentShiftFilterName = if (options.filterShiftId == null) "Tutti i turni" else {
                        ShiftRepository.getShiftDefinition(options.filterShiftId!!)?.name ?: "Tutti i turni"
                    }
                    val filterDef = options.filterShiftId?.let { ShiftRepository.getShiftDefinition(it) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable { showShiftFilterDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Filtro Turni", color = TextPrimary, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (filterDef != null) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(filterDef.colorHex))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(currentShiftFilterName, color = TextSecondary, fontSize = 13.5.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Sezione Opzioni di Layout
            Text(
                text = "DETTAGLI E COLONNE INCLUSE",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    when (options.mode) {
                        PdfExportMode.ELENCO -> {
                            ExportToggleRow("Festività & Eventi", options.includeEvents) {
                                options = options.copy(includeEvents = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Badge Colore Turno", options.includeIcon) {
                                options = options.copy(includeIcon = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Orario Inizio e Fine", options.includeTime) {
                                options = options.copy(includeTime = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Denominazione Turno", options.includeTitle) {
                                options = options.copy(includeTitle = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Note del Giorno", options.includeNotes) {
                                options = options.copy(includeNotes = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Orario e Durata Pausa", options.includeBreak) {
                                options = options.copy(includeBreak = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Ore Totali Lavorate", options.includeDuration) {
                                options = options.copy(includeDuration = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Includi Righe Vuote", options.includeEmptyRows) {
                                options = options.copy(includeEmptyRows = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Intestazione Logo", options.includeLogo) {
                                options = options.copy(includeLogo = it); ShiftRepository.pdfExportOptions = options
                            }
                        }
                        PdfExportMode.MESE -> {
                            ExportToggleRow("Festività & Eventi", options.includeEvents) {
                                options = options.copy(includeEvents = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Nome del Turno", options.includeTitle) {
                                options = options.copy(includeTitle = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Orari di Servizio", options.includeTime) {
                                options = options.copy(includeTime = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Badge Cromatico Turno", options.includeIcon) {
                                options = options.copy(includeIcon = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Intestazione Logo", options.includeLogo) {
                                options = options.copy(includeLogo = it); ShiftRepository.pdfExportOptions = options
                            }
                        }
                        PdfExportMode.ANNO -> {
                            ExportToggleRow("Badge Cromatico Turno", options.includeIcon) {
                                options = options.copy(includeIcon = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Intestazione Logo", options.includeLogo) {
                                options = options.copy(includeLogo = it); ShiftRepository.pdfExportOptions = options
                            }
                        }
                        PdfExportMode.PERSONALIZZATO -> {
                            ExportToggleRow("Festività & Eventi", options.includeEvents) {
                                options = options.copy(includeEvents = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Badge Cromatico Turno", options.includeIcon) {
                                options = options.copy(includeIcon = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Orari di Servizio (per intervalli brevi)", options.includeTime) {
                                options = options.copy(includeTime = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Nome del Turno", options.includeTitle) {
                                options = options.copy(includeTitle = it); ShiftRepository.pdfExportOptions = options
                            }
                            Divider(color = ObsidianBorderSubtle, thickness = 0.6.dp)
                            ExportToggleRow("Intestazione Logo", options.includeLogo) {
                                options = options.copy(includeLogo = it); ShiftRepository.pdfExportOptions = options
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Sezione Intestazione / Note
            Text(
                text = "INTESTAZIONE & NOTE DOCUMENTO",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = headerNotes,
                    onValueChange = {
                        headerNotes = it
                        options = options.copy(headerNotes = it)
                        ShiftRepository.pdfExportOptions = options
                    },
                    placeholder = { Text("Note o diciture da stampare in cima al foglio (es. Squadra B - UT Pescara)...", color = TextMuted, fontSize = 13.sp) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = TextPrimary,
                        containerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Barra Inferiore Fluttuante con UNICO pulsante di Azione Esporta
        Surface(
            color = ObsidianSurface.copy(alpha = 0.95f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
                        val file = PdfGenerator.generateAndSharePdf(context, options)
                        if (file != null) {
                            Toast.makeText(context, "PDF generato e condiviso con successo!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Errore nella generazione del PDF", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Genera e Condividi PDF",
                        color = ObsidianBackground,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    // Dialog Selezione Filtro Turni
    if (showShiftFilterDialog) {
        AlertDialog(
            onDismissRequest = { showShiftFilterDialog = false },
            title = { Text("Filtra per Turno", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClickable {
                                options = options.copy(filterShiftId = null)
                                ShiftRepository.pdfExportOptions = options
                                showShiftFilterDialog = false
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tutti i turni", color = TextPrimary, fontSize = 14.5.sp)
                        if (options.filterShiftId == null) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = ObsidianPrimary)
                        }
                    }
                    Divider(color = ObsidianBorderSubtle)
                    ShiftRepository.shiftDefinitions.forEach { def ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable {
                                    options = options.copy(filterShiftId = def.id)
                                    ShiftRepository.pdfExportOptions = options
                                    showShiftFilterDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(def.colorHex))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(def.name, color = TextPrimary, fontSize = 14.sp)
                            }
                            if (options.filterShiftId == def.id) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = ObsidianPrimary)
                            }
                        }
                        Divider(color = ObsidianBorderSubtle)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShiftFilterDialog = false }) {
                    Text("Annulla", color = TextMuted)
                }
            },
            containerColor = ObsidianSurfaceContainer,
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Dialog Selezione Periodo
    if (showDateRangeDialog) {
        var pickerYear by remember { mutableStateOf(options.startDate.year) }
        val monthNames = listOf(
            "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
            "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
        )

        AlertDialog(
            onDismissRequest = { showDateRangeDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Seleziona Periodo", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { pickerYear-- },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Anno precedente", tint = TextPrimary)
                        }
                        Text(
                            text = pickerYear.toString(),
                            color = ObsidianPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        IconButton(
                            onClick = { pickerYear++ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Anno successivo", tint = TextPrimary)
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (options.mode == PdfExportMode.ANNO) {
                        Text(
                            text = "Tocca per esportare il prospetto per l'anno $pickerYear:",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Button(
                            onClick = {
                                options = options.copy(selectedYear = pickerYear)
                                ShiftRepository.pdfExportOptions = options
                                showDateRangeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                        ) {
                            Text("Seleziona Anno $pickerYear", color = ObsidianBackground, fontWeight = FontWeight.Bold)
                        }
                    } else if (options.mode == PdfExportMode.PERSONALIZZATO) {
                        Text(
                            text = "Intervallo rapido preimpostato:",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("30 Giorni", 30L, "1 Mese"),
                                Triple("60 Giorni", 60L, "Bimestre"),
                                Triple("90 Giorni", 90L, "Trimestre"),
                                Triple("180 Giorni", 180L, "Semestre")
                            ).forEach { (label, days, sub) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ObsidianSurfaceHigh,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                                    modifier = Modifier
                                        .weight(1f)
                                        .bouncyClickable {
                                            val start = LocalDate.now()
                                            options = options.copy(
                                                startDate = start,
                                                endDate = start.plusDays(days)
                                            )
                                            ShiftRepository.pdfExportOptions = options
                                            showDateRangeDialog = false
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(sub, fontSize = 9.sp, color = ObsidianPrimary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Oppure scegli il mese di inizio nel $pickerYear:",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in 0 until 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (col in 0 until 3) {
                                        val monthIndex = row * 3 + col + 1
                                        val monthName = monthNames[monthIndex - 1]
                                        val isCurrentSelected = options.startDate.year == pickerYear && options.startDate.monthValue == monthIndex

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isCurrentSelected) ObsidianPrimary else ObsidianSurfaceHigh,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isCurrentSelected) ObsidianPrimary else ObsidianBorderSubtle
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clickable {
                                                    val start = LocalDate.of(pickerYear, monthIndex, 1)
                                                    val end = start.withDayOfMonth(start.lengthOfMonth())
                                                    options = options.copy(
                                                        startDate = start,
                                                        endDate = end,
                                                        selectedYear = pickerYear
                                                    )
                                                    ShiftRepository.pdfExportOptions = options
                                                    showDateRangeDialog = false
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = monthName.take(3),
                                                    color = if (isCurrentSelected) ObsidianBackground else TextPrimary,
                                                    fontWeight = if (isCurrentSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Scegli il mese da esportare:",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in 0 until 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (col in 0 until 3) {
                                        val monthIndex = row * 3 + col + 1
                                        val monthName = monthNames[monthIndex - 1]
                                        val isCurrentSelected = options.startDate.year == pickerYear && options.startDate.monthValue == monthIndex

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isCurrentSelected) ObsidianPrimary else ObsidianSurfaceHigh,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isCurrentSelected) ObsidianPrimary else ObsidianBorderSubtle
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
                                                .clickable {
                                                    val start = LocalDate.of(pickerYear, monthIndex, 1)
                                                    val end = start.withDayOfMonth(start.lengthOfMonth())
                                                    options = options.copy(
                                                        startDate = start,
                                                        endDate = end,
                                                        selectedYear = pickerYear
                                                    )
                                                    ShiftRepository.pdfExportOptions = options
                                                    showDateRangeDialog = false
                                                }
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = monthName.take(3),
                                                    color = if (isCurrentSelected) ObsidianBackground else TextPrimary,
                                                    fontWeight = if (isCurrentSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDateRangeDialog = false }) {
                    Text("Annulla", color = TextMuted)
                }
            },
            containerColor = ObsidianSurfaceContainer,
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun ExportToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ObsidianPrimaryContainer,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E293B),
                uncheckedBorderColor = Color(0xFF475569)
            )
        )
    }
}

// Miniatura per Anteprima Elenco (Allineata al nuovo layout PdfGenerator)
@Composable
private fun PreviewElencoContent(options: PdfExportOptions) {
    val monthName = options.startDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).uppercase(Locale.ITALIAN)
    val stats = ShiftRepository.getComprehensiveReportStats(options.startDate, options.endDate)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(5.dp)
    ) {
        // 1. Header Fascia Navy
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF0F172A))
                .border(0.5.dp, Color(0xFF0284C7), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.5.dp)
        ) {
            Column {
                Text(
                    text = "CALENDARIO TURNI",
                    fontSize = 4.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "$monthName ${options.startDate.year}",
                    fontSize = 3.8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.5.dp))

        // 2. Micro Card Statistiche
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            listOf(
                Pair(String.format(Locale.US, "%.0fh", stats.totalHours), "Ore"),
                Pair("${stats.workedDays}", "Turni"),
                Pair("${stats.countRiposo}R", "Riposi"),
                Pair("+${String.format(Locale.US, "%.0fh", stats.totalOvertimeHours)}", "Extra")
            ).forEach { (valore, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(0.3.dp, Color(0xFFCBD5E1), RoundedCornerShape(2.dp))
                        .padding(vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(valore, fontSize = 3.6.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text(label, fontSize = 2.4.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.5.dp))

        // 3. Intestazione Tabella
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xFF1E293B))
                .padding(horizontal = 3.dp, vertical = 1.5.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("GIORNO", fontSize = 3.2.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(0.7f))
                Text("ORA", fontSize = 3.2.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(0.9f))
                Text("TURNO", fontSize = 3.2.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1.4f))
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        // 4. Righe della Tabella con Zebra Striping e Pillole
        val previewDates = (0 until 9).map { options.startDate.plusDays(it.toLong()) }
        previewDates.forEachIndexed { idx, date ->
            val dayNum = date.dayOfMonth
            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).lowercase(Locale.ITALIAN)
            val isSunday = date.dayOfWeek == java.time.DayOfWeek.SUNDAY
            val holiday = ShiftRepository.isHoliday(date)
            val isHoliday = isSunday || holiday != null

            val shifts = ShiftRepository.getShiftsForDate(date)
            val activeShift = shifts.firstOrNull()
            val def = activeShift?.let { ShiftRepository.getShiftDefinition(it.shiftDefId) }

            val rowBg = when {
                isHoliday -> Color(0xFFFEF2F2)
                idx % 2 == 1 -> Color(0xFFF8FAFC)
                else -> Color.White
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .border(0.2.dp, Color(0xFFE2E8F0))
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.ITALIAN, "%02d %s", dayNum, dayOfWeek),
                    fontSize = 3.sp,
                    color = if (isHoliday) Color(0xFFDC2626) else Color(0xFF0F172A),
                    fontWeight = if (isHoliday) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(0.7f)
                )

                val timeStr = when {
                    def != null && def.isAllDay -> "Tutto il gg"
                    def != null -> "${def.startTime}-${def.endTime}"
                    else -> "—"
                }
                Text(
                    text = timeStr,
                    fontSize = 2.8.sp,
                    color = Color(0xFF475569),
                    modifier = Modifier.weight(0.9f)
                )

                Box(
                    modifier = Modifier.weight(1.4f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (def != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(def.colorHex))
                                .padding(horizontal = 2.dp, vertical = 0.5.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "${def.shortCode} • ${def.name.take(6)}",
                                fontSize = 2.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    } else if (holiday != null) {
                        Text(
                            text = "★ ${holiday.name.take(8)}",
                            fontSize = 2.6.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    } else {
                        Text("—", fontSize = 2.8.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}

// Miniatura per Anteprima Mese: A4 Landscape espanso a tutta altezza senza vuoti
@Composable
private fun PreviewMeseContent(options: PdfExportOptions) {
    val monthName = options.startDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).uppercase(Locale.ITALIAN)
    val ym = java.time.YearMonth.from(options.startDate)
    val firstDay = ym.atDay(1)
    val firstCol = firstDay.dayOfWeek.value - 1
    val totalDays = ym.lengthOfMonth()
    val numRows = kotlin.math.ceil((firstCol + totalDays) / 7.0).toInt().coerceAtLeast(5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // Header Fascia Navy Landscape
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.5.dp))
                .background(Color(0xFF0F172A))
                .border(0.5.dp, Color(0xFF0284C7), RoundedCornerShape(2.5.dp))
                .padding(horizontal = 5.dp, vertical = 2.5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CALENDARIO TURNI • PROSPETTO MENSILE", fontSize = 4.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("$monthName ${ym.year}", fontSize = 3.4.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                }
                Text("Foglio A4 Orizzontale", fontSize = 2.8.sp, color = Color(0xFF94A3B8))
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Intestazione giorni L M M G V S D
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xFF1E293B))
                .padding(vertical = 1.2.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica").forEach { d ->
                    Text(d.take(3), fontSize = 3.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        // Griglia Mese: usa weight(1f) su ogni riga per riempire tutto il foglio senza spazi bianchi!
        Column(modifier = Modifier.weight(1f)) {
            var curDay = 1
            for (r in 0 until numRows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (c in 0 until 7) {
                        val cellIndex = r * 7 + c
                        if (cellIndex >= firstCol && curDay <= totalDays) {
                            val thisDate = ym.atDay(curDay)
                            val shifts = ShiftRepository.getShiftsForDate(thisDate)
                            val def = shifts.firstOrNull()?.let { ShiftRepository.getShiftDefinition(it.shiftDefId) }
                            val isSunday = c == 6
                            val holiday = ShiftRepository.isHoliday(thisDate)
                            val isHoliday = isSunday || holiday != null

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.25.dp, Color(0xFFCBD5E1))
                                    .background(if (isHoliday) Color(0xFFFEF2F2) else Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(1.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$curDay",
                                        fontSize = 3.sp,
                                        color = if (isHoliday) Color(0xFFDC2626) else Color(0xFF0F172A),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.End)
                                    )
                                    if (def != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.92f)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(Color(def.colorHex))
                                                .padding(vertical = 1.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${def.shortCode} ${if (!def.isAllDay) def.startTime else ""}",
                                                fontSize = 2.4.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(1.dp))
                                    }
                                }
                            }
                            curDay++
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.2.dp, Color(0xFFF1F5F9))
                                    .background(Color(0xFFF8FAFC))
                            )
                        }
                    }
                }
            }
        }

        // Striscia Legenda Turni in calce
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xFFF1F5F9))
                .border(0.25.dp, Color(0xFFCBD5E1))
                .padding(horizontal = 4.dp, vertical = 1.2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("LEGENDA:", fontSize = 2.6.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
            ShiftRepository.shiftDefinitions.take(6).forEach { def ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color(def.colorHex)))
                    Spacer(modifier = Modifier.width(1.dp))
                    Text(def.shortCode, fontSize = 2.4.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// Miniatura per Anteprima Anno: tutti i 31 giorni a piena pagina
@Composable
private fun PreviewAnnoContent(options: PdfExportOptions) {
    val year = options.selectedYear
    val months = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        // Header Fascia Anno
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF0F172A))
                .border(0.5.dp, Color(0xFF0284C7), RoundedCornerShape(2.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CALENDARIO TURNI • PIANO ANNUALE $year", fontSize = 3.6.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("12 Mesi Completi", fontSize = 2.8.sp, color = Color(0xFF38BDF8))
            }
        }

        Spacer(modifier = Modifier.height(1.5.dp))

        // Intestazione Mesi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF1E293B))
                .padding(vertical = 0.8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                months.forEach { m ->
                    Text(m, fontSize = 2.6.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(1.dp))

        // Tabellone 31 giorni a tutta altezza
        Column(modifier = Modifier.weight(1f)) {
            for (day in 1..31) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    for (m in 1..12) {
                        val date = try { LocalDate.of(year, m, day) } catch (_: Exception) { null }
                        val shifts = date?.let { ShiftRepository.getShiftsForDate(it) } ?: emptyList()
                        val def = shifts.firstOrNull()?.let { ShiftRepository.getShiftDefinition(it.shiftDefId) }
                        val isSunday = date?.dayOfWeek == java.time.DayOfWeek.SUNDAY

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(0.15.dp, Color(0xFFE2E8F0))
                                .background(
                                    when {
                                        date == null -> Color(0xFFF1F5F9)
                                        def != null -> Color(def.colorHex).copy(alpha = 0.65f)
                                        isSunday -> Color(0xFFFEF2F2)
                                        else -> Color.White
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (date != null) {
                                Text(
                                    text = if (def != null) def.shortCode.take(2) else "$day",
                                    fontSize = 1.9.sp,
                                    fontWeight = if (def != null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (def != null) Color.White else if (isSunday) Color(0xFFDC2626) else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Striscia Legenda Turni in calce
        Spacer(modifier = Modifier.height(1.5.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFFF8FAFC))
                .border(0.2.dp, Color(0xFFCBD5E1))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("LEGENDA:", fontSize = 2.4.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
            ShiftRepository.shiftDefinitions.take(7).forEach { def ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(2.8.dp).clip(CircleShape).background(Color(def.colorHex)))
                    Spacer(modifier = Modifier.width(1.dp))
                    Text(def.shortCode, fontSize = 2.2.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Miniatura per Anteprima Personalizzato Dinamico
@Composable
private fun PreviewPersonalizzatoContent(options: PdfExportOptions) {
    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(options.startDate, options.endDate)
    if (daysBetween <= 35) {
        PreviewMeseContent(options)
    } else {
        // Multi-mese adattivo a colonne
        var curYm = java.time.YearMonth.from(options.startDate)
        val endYm = java.time.YearMonth.from(options.endDate)
        val monthsList = mutableListOf<java.time.YearMonth>()
        while (!curYm.isAfter(endYm) && monthsList.size < 12) {
            monthsList.add(curYm)
            curYm = curYm.plusMonths(1)
        }
        val numMonths = monthsList.size.coerceAtLeast(1)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color(0xFF0F172A))
                    .border(0.5.dp, Color(0xFF0284C7), RoundedCornerShape(2.5.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PROSPETTO DINAMICO • ${options.startDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"))} - ${options.endDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"))}",
                        fontSize = 3.6.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text("$numMonths Mesi", fontSize = 2.8.sp, color = Color(0xFF38BDF8))
                }
            }

            Spacer(modifier = Modifier.height(1.5.dp))

            // Intestazione Mesi Selezionati
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF1E293B))
                    .padding(vertical = 0.8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    monthsList.forEach { ym ->
                        val mName = ym.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase(Locale.ITALIAN)
                        Text(
                            "$mName '${ym.year % 100}",
                            fontSize = 2.4.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(1.dp))

            // Griglia 31 giorni a colonne larghe adattive
            Column(modifier = Modifier.weight(1f)) {
                for (day in 1..31) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        monthsList.forEach { ym ->
                            val date = try { ym.atDay(day) } catch (_: Exception) { null }
                            val isInRange = date != null && !date.isBefore(options.startDate) && !date.isAfter(options.endDate)
                            val shifts = if (isInRange) ShiftRepository.getShiftsForDate(date!!) else emptyList()
                            val def = shifts.firstOrNull()?.let { ShiftRepository.getShiftDefinition(it.shiftDefId) }
                            val isSunday = date?.dayOfWeek == java.time.DayOfWeek.SUNDAY

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(0.15.dp, Color(0xFFE2E8F0))
                                    .background(
                                        when {
                                            !isInRange -> Color(0xFFF1F5F9)
                                            def != null -> Color(def.colorHex).copy(alpha = 0.65f)
                                            isSunday -> Color(0xFFFEF2F2)
                                            else -> Color.White
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isInRange) {
                                    Text(
                                        text = if (def != null) def.shortCode.take(3) else "$day",
                                        fontSize = 1.9.sp,
                                        fontWeight = if (def != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (def != null) Color.White else if (isSunday) Color(0xFFDC2626) else Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Striscia Legenda Turni in calce
            Spacer(modifier = Modifier.height(1.5.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFFF8FAFC))
                .border(0.2.dp, Color(0xFFCBD5E1))
                .padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("LEGENDA:", fontSize = 2.4.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
            ShiftRepository.shiftDefinitions.take(7).forEach { def ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(2.8.dp).clip(CircleShape).background(Color(def.colorHex)))
                    Spacer(modifier = Modifier.width(1.dp))
                    Text(def.shortCode, fontSize = 2.2.sp, color = Color(0xFF1E293B), fontWeight = FontWeight.Bold)
                }
            }
        }
        }
    }
}
