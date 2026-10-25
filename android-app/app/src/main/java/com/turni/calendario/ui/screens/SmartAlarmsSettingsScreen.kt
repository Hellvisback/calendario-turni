package com.turni.calendario.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.alarm.ShiftAlarmScheduler
import com.turni.calendario.alarm.UpcomingAlarmInfo
import com.turni.calendario.model.ShiftAlarmRule
import com.turni.calendario.model.SmartAlarmsConfig
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAlarmsSettingsScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(ShiftRepository.smartAlarmsConfig) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Regole, 1 = Sveglie Programmate
    var syncFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var popupType by remember { mutableStateOf(PopupType.SUCCESS) }
    var lastSyncDate by remember { mutableStateOf<String?>(null) }

    // Verifica permessi Android (Sveglie Esatte e Notifiche)
    var hasExactAlarmPerm by remember {
        mutableStateOf(ShiftAlarmScheduler.canScheduleExactAlarms(context))
    }
    var hasNotificationPerm by remember {
        mutableStateOf(ShiftAlarmScheduler.hasNotificationPermission(context))
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPerm = isGranted
        if (isGranted) {
            popupType = PopupType.SUCCESS
            syncFeedbackMessage = "Autorizzazione notifiche concessa con successo!"
        }
    }

    var upcomingAlarm by remember(config) {
        mutableStateOf(ShiftAlarmScheduler.getNextUpcomingAlarm(context))
    }

    val allUpcomingAlarms = remember(config) {
        ShiftAlarmScheduler.getAllUpcomingAlarms(context, 30)
    }

    // Set di gruppi settimanali espansi (di default tutti espansi)
    val weekKeys = remember(allUpcomingAlarms) {
        allUpcomingAlarms.map { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }.distinct()
    }
    var expandedWeekKeys by remember(weekKeys) {
        mutableStateOf(weekKeys.toSet())
    }

    BackHandler {
        onDismiss()
    }

    // Calcola l'orario effettivo di sveglia per una regola
    fun getResolvedAlarmTime(rule: ShiftAlarmRule): Pair<Int, Int>? {
        val t = ShiftAlarmScheduler.getResolvedAlarmTime(rule) ?: return null
        return Pair(t.hour, t.minute)
    }

    // Funzione per sincronizzare e programmare la prossima sveglia utile
    fun syncAndScheduleSmartAlarm() {
        if (!config.isGlobalEnabled) {
            ShiftAlarmScheduler.cancelScheduledAlarm(context)
            upcomingAlarm = null
            popupType = PopupType.WARNING
            syncFeedbackMessage = "Tutte le sveglie automatiche sono disattivate."
            return
        }

        val scheduled = ShiftAlarmScheduler.scheduleNextAlarm(context)
        upcomingAlarm = scheduled
        val nowFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        lastSyncDate = "Oggi alle $nowFormatted"

        if (scheduled != null) {
            val dateStr = scheduled.triggerDateTime.format(
                DateTimeFormatter.ofPattern("EEE d MMM 'alle' HH:mm", Locale.ITALIAN)
            )
            popupType = PopupType.SUCCESS
            syncFeedbackMessage = "Sveglia programmata con successo per $dateStr (${scheduled.shiftName})!"
            Toast.makeText(context, "Sveglia turni programmata: $dateStr", Toast.LENGTH_SHORT).show()
        } else {
            popupType = PopupType.INFO
            syncFeedbackMessage = "Nessuna sveglia attiva programmata nei prossimi 30 giorni."
            Toast.makeText(context, "Nessuna sveglia attiva", Toast.LENGTH_SHORT).show()
        }
    }

    // Imposta una sveglia singola di test nell'app Orologio di sistema
    fun setSingleSystemAlarm(hour: Int, minute: Int, label: String) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "Sveglia di test: ${String.format("%02d:%02d", hour, minute)} ($label)", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Impossibile aprire l'app Orologio di sistema", Toast.LENGTH_SHORT).show()
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Top App Bar Ergonomica Breccia (Unico tasto indietro in alto)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = ObsidianSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
                    modifier = Modifier.size(42.dp)
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
                Column {
                    Text(
                        text = "Sveglie Automatiche",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Pianificazione intelligente orari e avvisi turno",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Switcher Tab 2.0 Obsidian (Tocca o scorri a destra per vedere tutte le sveglie)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 0: Regole
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = if (selectedTab == 0) ObsidianPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .bouncyClickable { selectedTab = 0 }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (selectedTab == 0) ObsidianBackground else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Regole & Orari",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) ObsidianBackground else TextSecondary
                            )
                        }
                    }

                    // Tab 1: Tutte le Sveglie Programmate
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = if (selectedTab == 1) ObsidianPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .bouncyClickable { selectedTab = 1 }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                tint = if (selectedTab == 1) ObsidianBackground else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sveglie Programmate",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) ObsidianBackground else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (selectedTab == 1) ObsidianBackground.copy(alpha = 0.25f) else ObsidianPrimary.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "${allUpcomingAlarms.count { it.isEnabled }}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selectedTab == 1) ObsidianBackground else ObsidianPrimary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Contenitore con supporto a gesture Swipe (scorri a destra o a sinistra per cambiare tab)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(selectedTab) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount < -45 && selectedTab == 0) {
                                selectedTab = 1
                            } else if (dragAmount > 45 && selectedTab == 1) {
                                selectedTab = 0
                            }
                        }
                    }
            ) {
                if (selectedTab == 0) {
                    // TAB 0: REGOLE E IMPOSTAZIONI ORARI
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Banner Permessi Android (Sveglie Esatte e Notifiche)
                        if (!hasExactAlarmPerm || !hasNotificationPerm) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = ColorReperibilita.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ColorReperibilita.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = ColorReperibilita,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "AUTORIZZAZIONI ANDROID NECESSARIE",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ColorReperibilita
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Per garantire che le sveglie suonino con precisione al minuto senza ritardi o limitazioni di risparmio energetico e mostrino gli avvisi:",
                                        fontSize = 11.5.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!hasExactAlarmPerm) {
                                            Button(
                                                onClick = {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                        try {
                                                            val intent = Intent(
                                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                                Uri.parse("package:${context.packageName}")
                                                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "Apri Impostazioni > App > Calendario Turni > Sveglie", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = ColorReperibilita)
                                            ) {
                                                Text("Autorizza Sveglie", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ObsidianBackground)
                                            }
                                        }

                                        if (!hasNotificationPerm && Build.VERSION.SDK_INT >= 33) {
                                            Button(
                                                onClick = {
                                                    notificationLauncher.launch("android.permission.POST_NOTIFICATIONS")
                                                },
                                                modifier = Modifier.weight(1f).height(38.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                                            ) {
                                                Text("Consenti Notifiche", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Badge Convalida Permessi
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianTertiary.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianTertiary.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ObsidianTertiary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Autorizzazioni Android attive: Sveglie esatte e notifiche autorizzate",
                                        fontSize = 11.sp,
                                        color = ObsidianTertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Card Master Switch
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = ObsidianSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (config.isGlobalEnabled) ObsidianPrimary.copy(alpha = 0.2f)
                                                else ObsidianSurfaceContainer
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Alarm,
                                            contentDescription = null,
                                            tint = if (config.isGlobalEnabled) ObsidianPrimary else TextMuted,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "Sveglie Automatiche Attive",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = if (config.isGlobalEnabled) "Sincronizza in automatico con i turni" else "Disattivate globalmente",
                                            fontSize = 11.5.sp,
                                            color = if (config.isGlobalEnabled) ObsidianPrimary else TextSecondary
                                        )
                                    }
                                }
                                Switch(
                                    checked = config.isGlobalEnabled,
                                    onCheckedChange = {
                                        config = config.copy(isGlobalEnabled = it)
                                        ShiftRepository.smartAlarmsConfig = config
                                        syncAndScheduleSmartAlarm()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = ObsidianPrimaryContainer,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = ObsidianSurfaceHigh,
                                        uncheckedBorderColor = ObsidianBorder
                                    )
                                )
                            }
                        }

                        // Card Stato / Prossima Sveglia Programmata
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (upcomingAlarm != null && config.isGlobalEnabled) ObsidianPrimary.copy(alpha = 0.5f)
                                else ObsidianBorderSubtle
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(
                                            imageVector = if (upcomingAlarm != null && config.isGlobalEnabled) Icons.Default.AlarmOn else Icons.Default.AlarmOff,
                                            contentDescription = null,
                                            tint = if (upcomingAlarm != null && config.isGlobalEnabled) ObsidianPrimary else TextMuted,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(modifier = Modifier.width(7.dp))
                                        Text(
                                            text = "PROSSIMA SVEGLIA PROGRAMMATA",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            color = if (upcomingAlarm != null && config.isGlobalEnabled) ObsidianPrimary else TextMuted,
                                            letterSpacing = 0.4.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (upcomingAlarm != null && config.isGlobalEnabled) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ObsidianPrimary.copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(0.6.dp, ObsidianPrimary)
                                        ) {
                                            Text(
                                                text = "ATTIVA",
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                fontWeight = FontWeight.Black,
                                                color = ObsidianPrimary,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (!config.isGlobalEnabled) {
                                    Text(
                                        text = "Le sveglie automatiche sono disattivate. Attiva il selettore sopra per consentire all'app di pianificare automaticamente gli orari.",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                } else if (upcomingAlarm != null) {
                                    val alarm = upcomingAlarm!!
                                    val formattedDate = alarm.triggerDateTime.format(
                                        DateTimeFormatter.ofPattern("EEEE d MMMM 'alle' HH:mm", Locale.ITALIAN)
                                    ).replaceFirstChar { it.uppercase() }

                                    Text(
                                        text = formattedDate,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(alarm.shiftColorHex))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = alarm.message,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "ℹ️ L'icona della sveglia in alto a destra sulla barra di stato del telefono indica che questa sveglia è registrata su Android tramite AlarmManager. L'app programma sempre e solo una sveglia alla volta per non intasare il sistema.",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                } else {
                                    Text(
                                        text = "Nessuna sveglia utile programmata nei prossimi 30 giorni. Controlla le regole o attiva i turni nella scheda 'Sveglie Programmate'.",
                                        fontSize = 12.sp,
                                        color = TextSecondary
                                    )
                                }

                                if (lastSyncDate != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Ultimo aggiornamento: $lastSyncDate",
                                        fontSize = 10.5.sp,
                                        color = ObsidianTertiary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (config.isGlobalEnabled) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { syncAndScheduleSmartAlarm() },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Aggiorna Programmazione Sveglia", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ObsidianBackground)
                                    }
                                }
                            }
                        }

                        Text(
                            text = "CONFIGURAZIONE REGOLE TURNI",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )

                        // 1. REPERIBILITÀ (Promemoria la sera prima con orario manuale)
                        val repRule = config.rules.firstOrNull { it.shiftDefId == "reperibilita" }
                            ?: ShiftAlarmRule("reperibilita", isEnabled = true, customTime = "20:00")
                        AlarmRuleCard(
                            title = "Giorno di Reperibilità",
                            subtitle = "Suonerà la sera del giorno prima per avvertirti che l'indomani sei reperibile",
                            icon = Icons.Default.PhoneCallback,
                            iconTint = ColorReperibilita,
                            iconBg = ColorReperibilita.copy(alpha = 0.22f),
                            isEnabled = repRule.isEnabled && config.isGlobalEnabled,
                            isGlobalEnabled = config.isGlobalEnabled,
                            onToggleEnabled = { enabled ->
                                val updated = repRule.copy(isEnabled = enabled)
                                val newRules = config.rules.filter { it.shiftDefId != "reperibilita" } + updated
                                config = config.copy(rules = newRules)
                                ShiftRepository.smartAlarmsConfig = config
                                syncAndScheduleSmartAlarm()
                            },
                            timeValue = repRule.customTime ?: "20:00",
                            onTimeChange = { newTime ->
                                val updated = repRule.copy(customTime = newTime)
                                val newRules = config.rules.filter { it.shiftDefId != "reperibilita" } + updated
                                config = config.copy(rules = newRules)
                                ShiftRepository.smartAlarmsConfig = config
                            },
                            quickPresets = listOf("19:30", "20:00", "20:30", "21:00", "21:30"),
                            labelPreset = "Orario sera prima:",
                            onTestAlarm = {
                                val p = getResolvedAlarmTime(repRule)
                                if (p != null) setSingleSystemAlarm(p.first, p.second, "Promemoria Reperibilità")
                            }
                        )

                        // 2. TURNO MATTINA
                        val matRule = config.rules.firstOrNull { it.shiftDefId == "mattina" }
                            ?: ShiftAlarmRule("mattina", isEnabled = true, customTime = "05:00")
                        AlarmRuleCard(
                            title = "Turno Mattina (06:00 - 13:36)",
                            subtitle = "Sveglia automatica per servizio mattutino",
                            icon = Icons.Default.WbTwilight,
                            iconTint = ObsidianPrimary,
                            iconBg = ObsidianPrimary.copy(alpha = 0.22f),
                            isEnabled = matRule.isEnabled && config.isGlobalEnabled,
                            isGlobalEnabled = config.isGlobalEnabled,
                            onToggleEnabled = { enabled ->
                                val updated = matRule.copy(isEnabled = enabled)
                                val newRules = config.rules.filter { it.shiftDefId != "mattina" } + updated
                                config = config.copy(rules = newRules)
                                ShiftRepository.smartAlarmsConfig = config
                                syncAndScheduleSmartAlarm()
                            },
                            timeValue = matRule.customTime ?: "05:00",
                            onTimeChange = { newTime ->
                                val updated = matRule.copy(customTime = newTime)
                                val newRules = config.rules.filter { it.shiftDefId != "mattina" } + updated
                                config = config.copy(rules = newRules)
                                ShiftRepository.smartAlarmsConfig = config
                            },
                            quickPresets = listOf("04:30", "04:45", "05:00", "05:15", "05:30"),
                            labelPreset = "Orario sveglia mattina:",
                            onTestAlarm = {
                                val p = getResolvedAlarmTime(matRule)
                                if (p != null) setSingleSystemAlarm(p.first, p.second, "Turno Mattina")
                            }
                        )

                        // 3. TURNO POMERIGGIO
                        val pomRule = config.rules.firstOrNull { it.shiftDefId == "pomeriggio" }
                            ?: ShiftAlarmRule("pomeriggio", isEnabled = false, customTime = "12:30")
                        AlarmRuleCard(
                            title = "Turno Pomeriggio (14:00 - 21:36)",
                            subtitle = "Sveglia automatica per servizio pomeridiano",
                            icon = Icons.Default.WbSunny,
                            iconTint = ColorNegative,
                            iconBg = ColorNegative.copy(alpha = 0.22f),
                            isEnabled = pomRule.isEnabled && config.isGlobalEnabled,
                            isGlobalEnabled = config.isGlobalEnabled,
                            onToggleEnabled = { enabled ->
                                val updated = pomRule.copy(isEnabled = enabled)
                                val newRules = config.rules.filter { it.shiftDefId != "pomeriggio" } + updated
                                config = config.copy(rules = newRules)
                                ShiftRepository.smartAlarmsConfig = config
                                syncAndScheduleSmartAlarm()
                            },
                            timeValue = pomRule.customTime ?: "12:30",
                            onTimeChange = { newTime ->
                                val updated = pomRule.copy(customTime = newTime)
                                val newRules = config.rules.filter { it.shiftDefId != "pomeriggio" } + updated
                                config = config.copy(rules = newRules)
                                ShiftRepository.smartAlarmsConfig = config
                            },
                            quickPresets = listOf("11:30", "12:00", "12:30", "13:00"),
                            labelPreset = "Orario sveglia pomeriggio:",
                            onTestAlarm = {
                                val p = getResolvedAlarmTime(pomRule)
                                if (p != null) setSingleSystemAlarm(p.first, p.second, "Turno Pomeriggio")
                            }
                        )

                        // 4. TURNO NOTTE
                        val notRule = config.rules.firstOrNull { it.shiftDefId == "notte" }
                            ?: ShiftAlarmRule("notte", isEnabled = false, customTime = "20:30")
                        AlarmRuleCard(
                            title = "Turno Notte (22:00 - 05:36)",
                            subtitle = "Sveglia automatica per servizio notturno",
                            icon = Icons.Default.Bedtime,
                            iconTint = ObsidianTertiary,
                            iconBg = ObsidianTertiary.copy(alpha = 0.22f),
                            isEnabled = notRule.isEnabled && config.isGlobalEnabled,
                            isGlobalEnabled = config.isGlobalEnabled,
                            onToggleEnabled = { enabled ->
                                val updated = notRule.copy(isEnabled = enabled)
                                val newRules = config.rules.filter { it.shiftDefId != "notte" } + updated
                                config = config.copy(rules = newRules)
                                ShiftRepository.smartAlarmsConfig = config
                                syncAndScheduleSmartAlarm()
                            },
                            timeValue = notRule.customTime ?: "20:30",
                            onTimeChange = { newTime ->
                                val updated = notRule.copy(customTime = newTime)
                                val newRules = config.rules.filter { it.shiftDefId != "notte" } + updated
                                config = config.copy(rules = newRules)
                                ShiftRepository.smartAlarmsConfig = config
                            },
                            quickPresets = listOf("19:30", "20:00", "20:30", "21:00"),
                            labelPreset = "Orario sveglia notte:",
                            onTestAlarm = {
                                val p = getResolvedAlarmTime(notRule)
                                if (p != null) setSingleSystemAlarm(p.first, p.second, "Turno Notte")
                            }
                        )

                        Spacer(modifier = Modifier.height(30.dp))
                    }
                } else {
                    // TAB 1: SVEGLIE PROGRAMMATE (LISTA COMPLETA 30 GIORNI CON COLLAPSE E PULSANTI COMODI)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Pulsanti Comodi Superiori: Disattiva Tutte, Attiva Tutte, Collassa/Espandi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tasto Disattiva Tutte
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ColorNegative.copy(alpha = 0.16f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, ColorNegative.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable {
                                        ShiftAlarmScheduler.toggleAllAlarms(context, false)
                                        config = ShiftRepository.smartAlarmsConfig
                                        upcomingAlarm = null
                                        popupType = PopupType.WARNING
                                        syncFeedbackMessage = "Tutte le sveglie sono state disattivate."
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AlarmOff, contentDescription = null, tint = ColorNegative, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Disattiva Tutte", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ColorNegative)
                                }
                            }

                            // Tasto Attiva Tutte
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianPrimary.copy(alpha = 0.16f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianPrimary.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable {
                                        ShiftAlarmScheduler.toggleAllAlarms(context, true)
                                        config = ShiftRepository.smartAlarmsConfig
                                        upcomingAlarm = ShiftAlarmScheduler.getNextUpcomingAlarm(context)
                                        popupType = PopupType.SUCCESS
                                        syncFeedbackMessage = "Tutte le sveglie per i turni in programma sono state attivate!"
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AlarmOn, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Attiva Tutte", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = ObsidianPrimary)
                                }
                            }

                            // Tasto Collassa / Espandi Tutto
                            val areAllExpanded = weekKeys.isNotEmpty() && expandedWeekKeys.size == weekKeys.size
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceContainer,
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle),
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable {
                                        expandedWeekKeys = if (areAllExpanded) emptySet() else weekKeys.toSet()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (areAllExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        if (areAllExpanded) "Collassa" else "Espandi",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        // Card Informativa: Zero Rischio "300 Sveglie"
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "GESTIONE INTELLIGENTE A SVEGLIA SINGOLA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Non ti ritroverai mai con centinaia di sveglie aperte nel telefono! L'app programma nell'orologio Android esclusivamente la prima sveglia utile, e appena suona imposta automaticamente la successiva.",
                                        fontSize = 10.5.sp,
                                        color = TextMuted,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        if (allUpcomingAlarms.isEmpty()) {
                            // Nessun turno nei prossimi 30 giorni
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = ObsidianSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.EventBusy, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Nessun turno in programma",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Non ci sono turni o reperibilità registrati nei prossimi 30 giorni per cui sia attiva una regola sveglia.",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // Raggruppamento delle sveglie per settimana
                            val groupedByWeek = allUpcomingAlarms.groupBy {
                                it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            }

                            groupedByWeek.forEach { (monday, weekAlarms) ->
                                val sunday = monday.plusDays(6)
                                val isExpanded = expandedWeekKeys.contains(monday)
                                val enabledInWeek = weekAlarms.count { it.isEnabled }
                                val allInWeekEnabled = enabledInWeek == weekAlarms.size

                                val weekLabel = "${monday.dayOfMonth} ${monday.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)} - ${sunday.dayOfMonth} ${sunday.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)}"

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = ObsidianSurface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (enabledInWeek > 0) ObsidianBorder else ObsidianBorderSubtle
                                    )
                                ) {
                                    Column {
                                        // Header Settimana (Cliccabile per espandere/collassare)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expandedWeekKeys = if (isExpanded) {
                                                        expandedWeekKeys - monday
                                                    } else {
                                                        expandedWeekKeys + monday
                                                    }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val rotationAngle by animateFloatAsState(if (isExpanded) 180f else 0f)
                                                Icon(
                                                    Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = TextSecondary,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .rotate(rotationAngle)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = weekLabel,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary
                                                    )
                                                    Text(
                                                        text = "$enabledInWeek attive su ${weekAlarms.size} turni",
                                                        fontSize = 11.sp,
                                                        color = if (enabledInWeek > 0) ObsidianPrimary else TextMuted
                                                    )
                                                }
                                            }

                                            // Toggle rapido per l'intera settimana
                                            Switch(
                                                checked = allInWeekEnabled,
                                                onCheckedChange = { enableWeek ->
                                                    val ids = weekAlarms.map { it.id }.toSet()
                                                    val updatedDisabled = if (enableWeek) {
                                                        config.disabledAlarmIds - ids
                                                    } else {
                                                        config.disabledAlarmIds + ids
                                                    }
                                                    config = config.copy(disabledAlarmIds = updatedDisabled)
                                                    ShiftRepository.smartAlarmsConfig = config
                                                    upcomingAlarm = ShiftAlarmScheduler.scheduleNextAlarm(context)
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedTrackColor = ObsidianPrimaryContainer,
                                                    uncheckedThumbColor = TextSecondary,
                                                    uncheckedTrackColor = ObsidianSurfaceHigh,
                                                    uncheckedBorderColor = ObsidianBorder
                                                )
                                            )
                                        }

                                        // Corpo lista sveglie della settimana (Espandibile)
                                        AnimatedVisibility(
                                            visible = isExpanded,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp)
                                                    .padding(bottom = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Divider(color = ObsidianBorderSubtle, thickness = 0.8.dp)
                                                Spacer(modifier = Modifier.height(2.dp))

                                                weekAlarms.forEach { alarm ->
                                                    val dayName = alarm.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() }
                                                    val dayNumber = alarm.date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN))
                                                    val alarmTimeStr = alarm.triggerDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))

                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = if (alarm.isEnabled) ObsidianSurfaceContainer else ObsidianBackground,
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            0.8.dp,
                                                            if (alarm.isEnabled) ObsidianBorderSubtle else ObsidianSurfaceHigh
                                                        ),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.weight(1f),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                // Orario sveglia grande
                                                                Text(
                                                                    text = alarmTimeStr,
                                                                    fontSize = 18.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = if (alarm.isEnabled) TextPrimary else TextMuted
                                                                )
                                                                Spacer(modifier = Modifier.width(12.dp))
                                                                Column {
                                                                    Text(
                                                                        text = "$dayName $dayNumber",
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = if (alarm.isEnabled) TextSecondary else TextMuted
                                                                    )
                                                                    Spacer(modifier = Modifier.height(2.dp))
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(6.dp)
                                                                                .clip(CircleShape)
                                                                                .background(Color(alarm.shiftColorHex))
                                                                        )
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text(
                                                                            text = alarm.shiftName,
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = Color(alarm.shiftColorHex)
                                                                        )
                                                                    }
                                                                }
                                                            }

                                                            // Switch singolo per la singola sveglia
                                                            Switch(
                                                                checked = alarm.isEnabled,
                                                                onCheckedChange = { isChecked ->
                                                                    upcomingAlarm = ShiftAlarmScheduler.toggleAlarmId(context, alarm.id, isChecked)
                                                                    config = ShiftRepository.smartAlarmsConfig
                                                                },
                                                                colors = SwitchDefaults.colors(
                                                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                                    checkedTrackColor = ObsidianPrimaryContainer,
                                                                    uncheckedThumbColor = TextSecondary,
                                                                    uncheckedTrackColor = ObsidianSurfaceHigh,
                                                                    uncheckedBorderColor = ObsidianBorder
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }

        AnimatedStatusPopup(
            message = syncFeedbackMessage,
            type = popupType,
            onDismiss = { syncFeedbackMessage = null }
        )
    }
}

@Composable
private fun AlarmRuleCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    isEnabled: Boolean,
    isGlobalEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    timeValue: String,
    onTimeChange: (String) -> Unit,
    quickPresets: List<String>,
    labelPreset: String,
    onTestAlarm: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isEnabled) ObsidianBorderActive else ObsidianBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = subtitle, fontSize = 11.sp, color = TextSecondary)
                    }
                }
                Switch(
                    checked = isEnabled,
                    enabled = isGlobalEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = ObsidianPrimaryContainer,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = ObsidianSurfaceHigh,
                        uncheckedBorderColor = ObsidianBorder
                    )
                )
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = ObsidianBorderSubtle, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Text(text = labelPreset, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Spacer(modifier = Modifier.height(6.dp))

                // Casella di testo editabile a mano per l'orario
                OutlinedTextField(
                    value = timeValue,
                    onValueChange = onTimeChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    placeholder = { Text("es. 05:00, 12:30, 20:00", color = TextMuted, fontSize = 13.sp) },
                    trailingIcon = {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = TextPrimary,
                        containerColor = ObsidianSurfaceContainer,
                        focusedBorderColor = ObsidianPrimary,
                        unfocusedBorderColor = ObsidianBorderSubtle
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Chip rapidi preimpostati
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPresets.forEach { preset ->
                        val isSel = timeValue.trim() == preset
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) ObsidianPrimary else ObsidianSurfaceContainer,
                            border = androidx.compose.foundation.BorderStroke(
                                0.8.dp,
                                if (isSel) ObsidianPrimary else ObsidianBorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .bouncyClickable { onTimeChange(preset) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) ObsidianBackground else TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tasto opzionale prova sveglia
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onTestAlarm) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Imposta sveglia di prova", color = ObsidianPrimary, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}
