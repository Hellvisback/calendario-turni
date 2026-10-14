@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.turni.calendario.ui.screens

import android.Manifest
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.turni.calendario.model.AssignedShift
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.DeviceCalendarInfo
import com.turni.calendario.sync.GoogleCalendarSyncHelper
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.MagicalActionFeedbackHUD
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Schermata di Sincronizzazione Google Calendar Reale:
 * - Interroga il ContentProvider CalendarContract di Android per ottenere i veri account Google e calendari del device.
 * - Inserisce o rimuove eventi reali su Google Calendar con orari esatti, fusi orari e promemoria.
 * - Non intacca mai gli eventi personali: sincronizza solo i turni di lavoro.
 * - Cooldown di 5 minuti dopo ogni sincronizzazione/eliminazione per evitare spam.
 * - Operazioni in background su Dispatchers.IO.
 */
@Composable
fun GoogleCalendarSyncScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember { mutableStateOf(GoogleCalendarSyncHelper.hasCalendarPermissions(context)) }
    var deviceCalendars by remember { mutableStateOf(if (hasPermission) GoogleCalendarSyncHelper.getDeviceCalendars(context) else emptyList()) }
    var selectedCalendarId by remember { mutableStateOf<Long?>(deviceCalendars.firstOrNull()?.id) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        hasPermission = granted
        if (granted) {
            deviceCalendars = GoogleCalendarSyncHelper.getDeviceCalendars(context)
            if (selectedCalendarId == null) {
                selectedCalendarId = deviceCalendars.firstOrNull()?.id
            }
        } else {
            Toast.makeText(context, "Permessi Calendario necessari per la sincronizzazione Google", Toast.LENGTH_LONG).show()
        }
    }

    val today = remember { LocalDate.now() }
    val currentMonthLabel = remember(today) {
        val monthName = today.month.name.lowercase().replaceFirstChar { it.uppercase() }
        "Mese Corrente ($monthName ${today.year})"
    }
    val threeMonthsLabel = remember(today) {
        val endM = today.plusMonths(2).month.name.lowercase().replaceFirstChar { it.uppercase() }
        "Prossimi 3 Mesi (fino a $endM ${today.plusMonths(2).year})"
    }
    val wholeYearLabel = "Intero Anno Lavorativo (12 Mesi)"

    var selectedRange by remember { mutableStateOf(currentMonthLabel) }
    var syncExactHours by remember { mutableStateOf(true) }
    var setReminderOneHourBefore by remember { mutableStateOf(true) }
    var includeRestDays by remember { mutableStateOf(false) }

    // Feedback animato HUD con particelle (stile Breccia e Liquid Glass)
    var actionFeedback by remember { mutableStateOf<ActionFeedback?>(null) }
    var showEventListPreview by remember { mutableStateOf(false) }

    // Cooldown & loading states
    var isSyncing by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var syncCooldownUntil by remember { mutableStateOf(0L) }
    var cooldownRemainingSeconds by remember { mutableStateOf(0L) }

    // Confirm delete dialog
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }

    // Countdown timer for cooldown
    LaunchedEffect(syncCooldownUntil) {
        if (syncCooldownUntil > 0L) {
            while (true) {
                val remaining = (syncCooldownUntil - System.currentTimeMillis()) / 1000L
                if (remaining <= 0) {
                    cooldownRemainingSeconds = 0
                    break
                }
                cooldownRemainingSeconds = remaining
                delay(1000L)
            }
        }
    }

    val isCooldownActive = cooldownRemainingSeconds > 0

    // Genera lista turni nel periodo per l'anteprima e la sincronizzazione
    val shiftsToSync = remember(selectedRange, includeRestDays) {
        val list = mutableListOf<Pair<LocalDate, AssignedShift>>()
        val startDate = today.withDayOfMonth(1)
        val endDate = when {
            selectedRange.contains("3 Mesi") -> today.plusMonths(3).withDayOfMonth(1).minusDays(1)
            selectedRange.contains("12 Mesi") || selectedRange.contains("Anno") -> today.plusMonths(12).withDayOfMonth(1).minusDays(1)
            else -> today.plusMonths(1).withDayOfMonth(1).minusDays(1)
        }

        var curr = startDate
        while (!curr.isAfter(endDate)) {
            val shifts = ShiftRepository.getShiftsForDate(curr)
            shifts.forEach { s ->
                val def = ShiftRepository.getShiftDefinition(s.shiftDefId)
                if (def != null) {
                    val isRest = def.category.name == "RIPOSO" || def.shortCode.uppercase() in listOf("RIP", "SMO", "R", "S")
                    if (!isRest || includeRestDays) {
                        list.add(Pair(curr, s))
                    }
                }
            }
            curr = curr.plusDays(1)
        }
        list
    }

    BackHandler(onBack = onDismiss)

    // Wrap everything in a Box to allow overlay popup
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
                .padding(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2638))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                }

                Text(
                    text = "Google Calendar Sync",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Box(modifier = Modifier.size(40.dp))
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Banner Richiesta Permessi se mancanti
            if (!hasPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E200C)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SecurityUpdateWarning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Permessi Calendario Richiesti", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Per sincronizzare i tuoi turni con Google Calendar sul tuo telefono, concedi i permessi di lettura e scrittura calendario.",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Autorizza Accesso Calendario", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Badge Garanzia di Sicurezza
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2537)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Sincronizzazione Reale & Sicura",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sincronizza direttamente con il tuo account Google e con l'app Google Calendar installata sul tuo smartphone.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card Selezione Calendario Google Reale
            val currentGoogleAccount = ShiftRepository.googleAccountProfile
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B26)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ColorMattina),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentGoogleAccount?.email ?: "Account Google Principale",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (deviceCalendars.isNotEmpty()) "${deviceCalendars.size} calendari rilevati sul dispositivo" else "Sincronizzazione nativa attiva",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color(0xFF1E2638), thickness = 0.8.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Seleziona Calendario di Destinazione:", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (deviceCalendars.isNotEmpty()) {
                        deviceCalendars.forEach { cal ->
                            val isSelected = cal.id == selectedCalendarId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedCalendarId = cal.id }
                                    .padding(vertical = 6.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) ColorMattina else Color(0xFF64748B))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(cal.displayName, color = if (isSelected) Color.White else Color(0xFFCBD5E1), fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        if (cal.accountName.isNotBlank()) {
                                            Text(cal.accountName, color = Color(0xFF64748B), fontSize = 10.5.sp)
                                        }
                                    }
                                }
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = ColorMattina.copy(alpha = 0.2f)
                                    ) {
                                        Text("Attivo", color = ColorMattina, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        // Fallback se nessun calendario ancora sincronizzato
                        listOf("Turni di Lavoro (Google)", "Calendario Principale").forEachIndexed { idx, name ->
                            val isSelected = (idx == 0 && selectedCalendarId == null)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(vertical = 5.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (isSelected) ColorMattina else Color(0xFF64748B)))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(name, color = if (isSelected) Color.White else Color(0xFFCBD5E1), fontSize = 12.5.sp)
                                }
                                if (isSelected) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = ColorMattina.copy(alpha = 0.2f)) {
                                        Text("Predefinito", color = ColorMattina, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selettore Intervallo Temporale
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B26)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Periodo da Sincronizzare",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    listOf(
                        currentMonthLabel,
                        threeMonthsLabel,
                        wholeYearLabel
                    ).forEach { rangeOption ->
                        val isSelected = selectedRange == rangeOption
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1E293B) else Color.Transparent)
                                .clickable { selectedRange = rangeOption }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedRange = rangeOption },
                                colors = RadioButtonDefaults.colors(selectedColor = ColorMattina)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = rangeOption,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Opzioni Avanzate Sincronizzazione
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151B26)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Parametri Eventi",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Orari Esatti Turno", color = Color.White, fontSize = 14.sp)
                            Text("Es. 06:00 - 13:36 invece di tutto il giorno", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Switch(
                            checked = syncExactHours,
                            onCheckedChange = { syncExactHours = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ColorMattina)
                        )
                    }

                    Divider(color = Color(0xFF1E2638), modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Includi Riposi e Smonti", color = Color.White, fontSize = 14.sp)
                            Text("Consigliato disattivato per non intasare il calendario", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Switch(
                            checked = includeRestDays,
                            onCheckedChange = { includeRestDays = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ColorMattina)
                        )
                    }

                    Divider(color = Color(0xFF1E2638), modifier = Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notifica e Promemoria", color = Color.White, fontSize = 14.sp)
                            Text("Notifica 1 ora prima dell'inizio turno", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Switch(
                            checked = setReminderOneHourBefore,
                            onCheckedChange = { setReminderOneHourBefore = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = ColorMattina)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tasto Anteprima Turni
            OutlinedButton(
                onClick = { showEventListPreview = !showEventListPreview },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    if (showEventListPreview) Icons.Default.ExpandLess else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showEventListPreview) "Nascondi Anteprima (${shiftsToSync.size} turni)" else "Mostra Anteprima Turni (${shiftsToSync.size} turni)",
                    color = Color.White
                )
            }

            if (showEventListPreview) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151B26))
                ) {
                    LazyColumn(modifier = Modifier.padding(12.dp)) {
                        items(shiftsToSync) { (date, assigned) ->
                            val def = ShiftRepository.getShiftDefinition(assigned.shiftDefId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${def?.name ?: assigned.shiftDefId} (${def?.startTime ?: "00:00"} - ${def?.endTime ?: "00:00"})",
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                            Divider(color = Color(0xFF1E2638), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pulsante Sincronizza Ora su Google Calendar (con cooldown e loading)
            Button(
                onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                        )
                    } else {
                        isSyncing = true
                        scope.launch(Dispatchers.IO) {
                            try {
                                val count = GoogleCalendarSyncHelper.syncShiftsToCalendar(
                                    context = context,
                                    targetCalendarId = selectedCalendarId,
                                    shiftsToSync = shiftsToSync,
                                    syncExactHours = syncExactHours,
                                    reminderMinutesBefore = if (setReminderOneHourBefore) 60 else null,
                                    includeRestDays = includeRestDays
                                )

                                withContext(Dispatchers.Main) {
                                    isSyncing = false
                                    syncCooldownUntil = System.currentTimeMillis() + 300_000L
                                    cooldownRemainingSeconds = 300L
                                    actionFeedback = ActionFeedback(
                                        message = "Sincronizzati con successo $count turni",
                                        timestamp = System.currentTimeMillis(),
                                        isDestructive = false
                                    )
                                }
                            } catch (e: Throwable) {
                                withContext(Dispatchers.Main) {
                                    isSyncing = false
                                    actionFeedback = ActionFeedback(
                                        message = "Errore: ${e.localizedMessage ?: "Verifica permessi calendario"}",
                                        timestamp = System.currentTimeMillis(),
                                        isDestructive = true
                                    )
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCooldownActive || isSyncing) Color(0xFF334155) else ColorMattina,
                    disabledContainerColor = Color(0xFF334155)
                ),
                enabled = !isCooldownActive && !isSyncing && !isDeleting
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Sincronizzazione in corso...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else if (isCooldownActive) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    val minutes = cooldownRemainingSeconds / 60
                    val seconds = cooldownRemainingSeconds % 60
                    Text(
                        "Attendi ${minutes}:${String.format("%02d", seconds)} prima della prossima sincronizzazione",
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Sincronizza Ora con Google Calendar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tasto Pulizia Eventi App dal Calendario (con dialog di conferma)
            TextButton(
                onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                        )
                    } else {
                        showConfirmDeleteDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCooldownActive && !isSyncing && !isDeleting
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color(0xFFEF4444),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eliminazione in corso...", color = Color(0xFFEF4444), fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rimuovi eventi dell'app dal Google Calendar", color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            }

            // Spacer for popup clearance
            Spacer(modifier = Modifier.height(80.dp))
        }

        // Feedback HUD dall'alto con particelle perimetrali
        MagicalActionFeedbackHUD(
            feedback = actionFeedback,
            onDismiss = { actionFeedback = null },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        )

        // Confirm Delete Dialog
        if (showConfirmDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDeleteDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Conferma Eliminazione", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text(
                        "Sei sicuro di voler rimuovere tutti i turni sincronizzati dal Google Calendar?\n\n" +
                        "Questa operazione potrebbe richiedere alcuni secondi. Solo gli eventi creati da questa app verranno eliminati.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmDeleteDialog = false
                            isDeleting = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val removed = GoogleCalendarSyncHelper.clearAppEventsFromCalendar(context, selectedCalendarId)

                                    withContext(Dispatchers.Main) {
                                        isDeleting = false
                                        syncCooldownUntil = System.currentTimeMillis() + 300_000L
                                        cooldownRemainingSeconds = 300L
                                        actionFeedback = if (removed > 0) {
                                            ActionFeedback(
                                                message = "Rimossi $removed eventi sincronizzati",
                                                timestamp = System.currentTimeMillis(),
                                                isDestructive = true
                                            )
                                        } else {
                                            ActionFeedback(
                                                message = "Nessun evento da rimuovere",
                                                timestamp = System.currentTimeMillis(),
                                                isDestructive = false
                                            )
                                        }
                                    }
                                } catch (e: Throwable) {
                                    withContext(Dispatchers.Main) {
                                        isDeleting = false
                                        actionFeedback = ActionFeedback(
                                            message = "Errore durante la rimozione",
                                            timestamp = System.currentTimeMillis(),
                                            isDestructive = true
                                        )
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Elimina Tutto", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDeleteDialog = false }) {
                        Text("Annulla", color = Color(0xFF94A3B8))
                    }
                },
                containerColor = Color(0xFF151B26),
                shape = RoundedCornerShape(18.dp)
            )
        }
    }
}
