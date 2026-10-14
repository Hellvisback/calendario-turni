package com.turni.calendario.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
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
import androidx.compose.foundation.text.KeyboardOptions
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAlarmsSettingsScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(ShiftRepository.smartAlarmsConfig) }
    var syncFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var popupType by remember { mutableStateOf(PopupType.SUCCESS) }
    var lastSyncDate by remember { mutableStateOf<String?>(null) }
    var upcomingAlarm by remember(config) {
        mutableStateOf(ShiftAlarmScheduler.getNextUpcomingAlarm(context))
    }

    BackHandler {
        onDismiss()
    }

    // Calcola l'orario effettivo di sveglia per una regola
    fun getResolvedAlarmTime(rule: ShiftAlarmRule): Pair<Int, Int>? {
        val t = ShiftAlarmScheduler.getResolvedAlarmTime(rule) ?: return null
        return Pair(t.hour, t.minute)
    }

    // Funzione per aggiornare e programmare la prossima sveglia precisa con AlarmManager
    fun syncAndScheduleSmartAlarm() {
        if (!config.isGlobalEnabled) {
            ShiftAlarmScheduler.cancelScheduledAlarm(context)
            upcomingAlarm = null
            popupType = PopupType.WARNING
            syncFeedbackMessage = "Le sveglie automatiche sono disattivate."
            return
        }

        val scheduled = ShiftAlarmScheduler.scheduleNextAlarm(context)
        upcomingAlarm = scheduled
        val nowFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        lastSyncDate = "Oggi alle $nowFormatted"

        if (scheduled != null) {
            val dateStr = scheduled.triggerDateTime.format(DateTimeFormatter.ofPattern("EEE d MMM 'alle' HH:mm", Locale.ITALIAN))
            popupType = PopupType.SUCCESS
            syncFeedbackMessage = "Sveglia programmata con successo per $dateStr (${scheduled.shiftName})!"
            Toast.makeText(context, "Sveglia turni programmata: $dateStr", Toast.LENGTH_SHORT).show()
        } else {
            popupType = PopupType.INFO
            syncFeedbackMessage = "Nessun turno o reperibilità programmata nei prossimi 30 giorni."
            Toast.makeText(context, "Nessun turno in programma", Toast.LENGTH_SHORT).show()
        }
    }

    // Imposta una sveglia singola di test tramite l'orologio di sistema o notifica immediata
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top App Bar Ergonomica Breccia (Unico tasto indietro in alto)
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
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorderSubtle),
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
            Column {
                Text(
                    text = "Sveglie Automatiche",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "Pianificazione orari e avvisi turno",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Contenuto Scrollabile
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ObsidianPrimaryContainer,
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF1E293B),
                            uncheckedBorderColor = Color(0xFF475569)
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
                    if (upcomingAlarm != null && config.isGlobalEnabled) Color(0xFF0284C7).copy(alpha = 0.5f)
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
                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF0284C7))
                            ) {
                                Text(
                                    text = "ATTIVA",
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF38BDF8),
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
                            text = "L'app programma sempre e solo la prossima sveglia utile, senza intasare il tuo orologio di sistema con decine di sveglie duplicate.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    } else {
                        Text(
                            text = "Nessun turno o reperibilità programmata nei prossimi 30 giorni con regole sveglia attive.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    if (lastSyncDate != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ultimo aggiornamento: $lastSyncDate",
                            fontSize = 10.5.sp,
                            color = Color(0xFF34D399),
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
                iconTint = Color(0xFFFBBF24),
                iconBg = Color(0xFFF59E0B).copy(alpha = 0.22f),
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
                iconTint = Color(0xFF38BDF8),
                iconBg = Color(0xFF0284C7).copy(alpha = 0.22f),
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
                iconTint = Color(0xFFF87171),
                iconBg = Color(0xFFEF4444).copy(alpha = 0.22f),
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
                iconTint = Color(0xFF4ADE80),
                iconBg = Color(0xFF22C55E).copy(alpha = 0.22f),
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
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ObsidianPrimaryContainer,
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E293B),
                        uncheckedBorderColor = Color(0xFF475569)
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
