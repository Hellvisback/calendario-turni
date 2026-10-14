package com.turni.calendario.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.theme.*

enum class SettingsSubScreen {
    NONE,
    CLOUD_BACKUP,
    GOOGLE_CALENDAR_SYNC,
    IMPORT_SHIFTS,
    PDF_EXPORT,
    REPERIBILITA,
    SMART_ALARMS,
    HOLIDAYS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSubScreenActiveChanged: (Boolean) -> Unit = {},
    onNavigateToTurni: (com.turni.calendario.model.ShiftRotation?) -> Unit = {}
) {
    val context = LocalContext.current
    var activeSubScreen by remember { mutableStateOf(SettingsSubScreen.NONE) }
    var showWeekStartDialog by remember { mutableStateOf(false) }
    var showTimeFormatDialog by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }

    val isSubScreenActive = activeSubScreen != SettingsSubScreen.NONE

    LaunchedEffect(isSubScreenActive) {
        onSubScreenActiveChanged(isSubScreenActive)
    }

    BackHandler(enabled = isSubScreenActive) {
        activeSubScreen = SettingsSubScreen.NONE
    }

    when (activeSubScreen) {
        SettingsSubScreen.PDF_EXPORT -> {
            PdfExportScreen(onDismiss = { activeSubScreen = SettingsSubScreen.NONE })
            return
        }
        SettingsSubScreen.HOLIDAYS -> {
            HolidaysSettingsScreen(onDismiss = { activeSubScreen = SettingsSubScreen.NONE })
            return
        }
        SettingsSubScreen.CLOUD_BACKUP -> {
            CloudBackupScreen(onDismiss = { activeSubScreen = SettingsSubScreen.NONE })
            return
        }
        SettingsSubScreen.GOOGLE_CALENDAR_SYNC -> {
            GoogleCalendarSyncScreen(onDismiss = { activeSubScreen = SettingsSubScreen.NONE })
            return
        }
        SettingsSubScreen.IMPORT_SHIFTS -> {
            ImportShiftsScreen(
                onDismiss = { activeSubScreen = SettingsSubScreen.NONE },
                onNavigateToEdit = { rotation ->
                    activeSubScreen = SettingsSubScreen.NONE
                    onNavigateToTurni(rotation)
                }
            )
            return
        }
        SettingsSubScreen.REPERIBILITA -> {
            ReperibilitaSettingsScreen(onDismiss = { activeSubScreen = SettingsSubScreen.NONE })
            return
        }
        SettingsSubScreen.SMART_ALARMS -> {
            SmartAlarmsSettingsScreen(onDismiss = { activeSubScreen = SettingsSubScreen.NONE })
            return
        }
        SettingsSubScreen.NONE -> {
            // Mostra menu principale impostazioni
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Hero Header: Profilo Utente & App (Obsidian Surface)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(PrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Calendario Turni",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "RFI PRO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Gestione turni personalizzata e sincronizzata • CCNL Mobilità",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Glanceable Quick Pills (visual status glanceability)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Drive Cloud", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sincronizzato", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Offline Safe", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))



        // Sezione Cloud
        SectionTitle("CLOUD & SINCRONIZZAZIONE")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            SettingsRow(
                icon = Icons.Default.CloudSync,
                title = "Account Google & Cloud Backup",
                subtitle = "Backup automatico crittografato su Google Drive",
                onClick = { activeSubScreen = SettingsSubScreen.CLOUD_BACKUP }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Sezione Import / Export
        SectionTitle("DATI, EXCEL & CONDIVISIONE")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column {
                SettingsRow(
                    icon = Icons.Default.UploadFile,
                    title = "Importazione Turni (Excel / CSV)",
                    subtitle = "Carica o incolla turni da file o tabella",
                    onClick = { activeSubScreen = SettingsSubScreen.IMPORT_SHIFTS }
                )
                Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                SettingsRow(
                    icon = Icons.Default.PictureAsPdf,
                    title = "Esportazione PDF & Stampa",
                    subtitle = "Condividi prospetto turni identico a SuperShift (Elenco, Mese, Anno)",
                    onClick = { activeSubScreen = SettingsSubScreen.PDF_EXPORT }
                )
                Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                SettingsRow(
                    icon = Icons.Default.Event,
                    title = "Sincronizza con Google Calendar",
                    subtitle = "Crea eventi nel calendario nativo dello smartphone",
                    onClick = { activeSubScreen = SettingsSubScreen.GOOGLE_CALENDAR_SYNC }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Sezione Reperibilità & Sveglie Intelligenti
        SectionTitle("REPERIBILITÀ SOFTWARE & SVEGLIE")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column {
                val repConfig = ShiftRepository.reperibilitaConfig
                val repSubtitle = when (repConfig.mode) {
                    ReperibilitaMode.MANUALE -> "Modalità Manuale (${repConfig.manualDates.size} date selezionate)"
                    ReperibilitaMode.TURNO_SPECIFICO -> "Attiva automaticamente sui turni configurati"
                    ReperibilitaMode.ALTERNATA_STAGIONALE -> "Alternata: Inverno su turni / Estate rotazione settimanale"
                }
                SettingsRow(
                    icon = Icons.Default.PhoneCallback,
                    title = "Gestione Reperibilità Software",
                    subtitle = repSubtitle,
                    onClick = { activeSubScreen = SettingsSubScreen.REPERIBILITA }
                )
                Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                val alarmConfig = ShiftRepository.smartAlarmsConfig
                val alarmSubtitle = if (alarmConfig.isGlobalEnabled) {
                    val activeCount = alarmConfig.rules.count { it.isEnabled }
                    "Sveglie attive per $activeCount tipologie di turno"
                } else {
                    "Sveglie automatiche disattivate"
                }
                SettingsRow(
                    icon = Icons.Default.Alarm,
                    title = "Sveglie Automatiche Intelligenti",
                    subtitle = alarmSubtitle,
                    onClick = { activeSubScreen = SettingsSubScreen.SMART_ALARMS }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Sezione Calendario
        SectionTitle("PREFERENZE CALENDARIO & CONTRATTO")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
        ) {
            Column {
                SettingsRow(
                    icon = Icons.Default.Flag,
                    title = "Festività Nazionali, Religiose & CCNL",
                    subtitle = "${ShiftRepository.enabledHolidayIds.size} festività selezionate (evidenziate in rosso)",
                    onClick = { activeSubScreen = SettingsSubScreen.HOLIDAYS }
                )
                Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                SettingsRow(
                    icon = Icons.Default.CalendarToday,
                    title = "Inizio settimana",
                    subtitle = if (ShiftRepository.appPreferences.weekStartMonday) "Lunedì (Norma europea e CCNL)" else "Domenica",
                    onClick = { showWeekStartDialog = true }
                )
                Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                SettingsRow(
                    icon = Icons.Default.AccessTime,
                    title = "Formato orario",
                    subtitle = if (ShiftRepository.appPreferences.timeFormat24h) "24 ore (es. 14:00 - 21:36)" else "12 ore AM/PM (es. 02:00 PM)",
                    onClick = { showTimeFormatDialog = true }
                )
                Divider(color = ObsidianBorderSubtle, thickness = 1.dp)
                SettingsToggleRow(
                    icon = Icons.Default.History,
                    title = "Mostra Ex-Turno Sostituito",
                    subtitle = "Mostra in grigio il turno ciclico se sovrascritto",
                    checked = ShiftRepository.appPreferences.showExShifts,
                    onCheckedChange = {
                        ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(showExShifts = it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Versione 2.0.0 Pro • Progettato per turnisti italiani con supporto RFI",
            color = TextMuted,
            fontSize = 11.5.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(100.dp))
    }

    // Dialog Inizio Settimana
    if (showWeekStartDialog) {
        AlertDialog(
            onDismissRequest = { showWeekStartDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Inizio Settimana", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleziona il primo giorno della settimana:", color = TextSecondary, fontSize = 13.sp)

                    val isMon = ShiftRepository.appPreferences.weekStartMonday
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isMon) ObsidianPrimary.copy(alpha = 0.2f) else ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isMon) ObsidianPrimary else ObsidianBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(weekStartMonday = true)
                                showWeekStartDialog = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isMon,
                                onClick = {
                                    ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(weekStartMonday = true)
                                    showWeekStartDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ObsidianPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Lunedì", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Standard europeo e CCNL Ferroviario", color = TextSecondary, fontSize = 11.5.sp)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isMon) ObsidianPrimary.copy(alpha = 0.2f) else ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (!isMon) ObsidianPrimary else ObsidianBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(weekStartMonday = false)
                                showWeekStartDialog = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !isMon,
                                onClick = {
                                    ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(weekStartMonday = false)
                                    showWeekStartDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ObsidianPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Domenica", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Formato anglosassone internazionale", color = TextSecondary, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showWeekStartDialog = false }) {
                    Text("Annulla", color = TextSecondary)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(22.dp)
        )
    }

    // Dialog Formato Orario
    if (showTimeFormatDialog) {
        AlertDialog(
            onDismissRequest = { showTimeFormatDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Formato Orario", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleziona il formato di visualizzazione orari:", color = TextSecondary, fontSize = 13.sp)

                    val is24h = ShiftRepository.appPreferences.timeFormat24h
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (is24h) ObsidianPrimary.copy(alpha = 0.2f) else ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (is24h) ObsidianPrimary else ObsidianBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(timeFormat24h = true)
                                showTimeFormatDialog = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = is24h,
                                onClick = {
                                    ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(timeFormat24h = true)
                                    showTimeFormatDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ObsidianPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("24 Ore", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("es. 06:00 - 13:36 / 14:00 - 21:36", color = TextSecondary, fontSize = 11.5.sp)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!is24h) ObsidianPrimary.copy(alpha = 0.2f) else ObsidianSurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (!is24h) ObsidianPrimary else ObsidianBorderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(timeFormat24h = false)
                                showTimeFormatDialog = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !is24h,
                                onClick = {
                                    ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(timeFormat24h = false)
                                    showTimeFormatDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ObsidianPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("12 Ore (AM/PM)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("es. 06:00 AM - 01:36 PM / 02:00 PM - 09:36 PM", color = TextSecondary, fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTimeFormatDialog = false }) {
                    Text("Annulla", color = TextSecondary)
                }
            },
            containerColor = ObsidianSurface,
            shape = RoundedCornerShape(22.dp)
        )
    }

    AnimatedStatusPopup(
        message = importStatusMessage,
        type = PopupType.SUCCESS,
        onDismiss = { importStatusMessage = null }
    )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurfaceContainer)
                .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(ObsidianSurfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianSurfaceContainer)
                .border(1.dp, ObsidianBorderSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ObsidianPrimary,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = ObsidianSurfaceContainer
            )
        )
    }
}

