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

    BackHandler {
        onDismiss()
    }

    // Calcola l'orario effettivo di sveglia per una regola
    fun getResolvedAlarmTime(rule: ShiftAlarmRule): Pair<Int, Int>? {
        if (!rule.customTime.isNullOrBlank()) {
            val parts = rule.customTime.split(":")
            if (parts.size == 2) {
                val h = parts[0].trim().toIntOrNull()
                val m = parts[1].trim().toIntOrNull()
                if (h != null && m != null && h in 0..23 && m in 0..59) {
                    return Pair(h, m)
                }
            }
        }
        val def = ShiftRepository.getShiftDefinition(rule.shiftDefId) ?: return null
        return try {
            val startTime = LocalTime.parse(def.startTime)
            val alarmTime = startTime.minusMinutes(rule.minutesBefore.toLong())
            Pair(alarmTime.hour, alarmTime.minute)
        } catch (_: Exception) {
            null
        }
    }

    // Funzione di sincronizzazione automatica batch per tutte le date del mese
    fun syncAllMonthAlarms() {
        if (!config.isGlobalEnabled) {
            syncFeedbackMessage = "Le sveglie automatiche sono disattivate globalmente."
            return
        }

        var count = 0
        val startDate = LocalDate.now()
        val endDate = startDate.plusDays(30)

        val repRule = config.rules.firstOrNull { it.shiftDefId == "reperibilita" }
        val morningRule = config.rules.firstOrNull { it.shiftDefId == "mattina" }
        val afternoonRule = config.rules.firstOrNull { it.shiftDefId == "pomeriggio" }
        val nightRule = config.rules.firstOrNull { it.shiftDefId == "notte" }

        var curr = startDate
        while (!curr.isAfter(endDate)) {
            // 1. REPERIBILITÀ: Avviso la sera del giorno prima
            val nextDay = curr.plusDays(1)
            val isNextDayOnCall = ShiftRepository.isOnCall(nextDay)
            if (isNextDayOnCall && repRule != null && repRule.isEnabled) {
                count++
            }

            // 2. TURNI DELLA GIORNATA
            val dayShifts = ShiftRepository.getShiftsForDate(curr)
            val planned = ShiftRepository.getPlannedRotationShift(curr)
            val targetShiftIds = (dayShifts.map { it.shiftDefId } + listOfNotNull(planned?.id)).distinct()

            if (morningRule != null && morningRule.isEnabled && targetShiftIds.contains("mattina")) {
                count++
            }
            if (afternoonRule != null && afternoonRule.isEnabled && targetShiftIds.contains("pomeriggio")) {
                count++
            }
            if (nightRule != null && nightRule.isEnabled && targetShiftIds.contains("notte")) {
                count++
            }

            curr = curr.plusDays(1)
        }

        val nowFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        lastSyncDate = "Oggi alle $nowFormatted"
        syncFeedbackMessage = "Sincronizzazione completata con successo: $count turni/avvisi programmati per i prossimi 30 giorni!"
        Toast.makeText(context, "$count sveglie automatiche pianificate!", Toast.LENGTH_SHORT).show()
    }

    // Imposta una sveglia singola nativa tramite l'orologio di sistema
    fun setSingleSystemAlarm(hour: Int, minute: Int, label: String) {
        try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "Sveglia impostata alle ${String.format("%02d:%02d", hour, minute)}: $label", Toast.LENGTH_SHORT).show()
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
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ObsidianPrimaryContainer,
                            uncheckedTrackColor = ObsidianSurfaceHigh
                        )
                    )
                }
            }

            // Banner Azione: Sincronizzazione Batch Automatica
            if (config.isGlobalEnabled) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPrimary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = ObsidianPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Automazione Mensile Senza Clic Giornalieri",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "L'app calcola e mantiene aggiornate tutte le sveglie future del mese in base alla tua turnazione reale.",
                            fontSize = 11.5.sp,
                            color = TextSecondary
                        )
                        if (lastSyncDate != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ultima sincronizzazione: $lastSyncDate",
                                fontSize = 11.sp,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { syncAllMonthAlarms() },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizza Tutte le Sveglie del Mese", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = ObsidianBackground)
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
                subtitle = "Suona la sera del giorno prima per accendere il telefono",
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
                        uncheckedTrackColor = ObsidianSurfaceHigh
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
