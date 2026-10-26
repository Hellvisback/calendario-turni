@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.turni.calendario.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.turni.calendario.ui.components.SiteIcon
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.turni.calendario.alarm.ShiftAlarmScheduler
import com.turni.calendario.alarm.ShiftAlarmService
import com.turni.calendario.alarm.ShiftAlarmReceiver
import com.turni.calendario.model.ShiftAlarmRule
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.bouncyClickable
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SmartAlarmsSettingsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val config = ShiftRepository.smartAlarmsConfig
    var refresh by remember { mutableStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackType by remember { mutableStateOf(PopupType.INFO) }
    var tab by remember { mutableStateOf(0) }
    var pendingTest by remember { mutableStateOf(false) }
    LaunchedEffect(pendingTest) { if (pendingTest) { delay(31_000L); pendingTest = false } }
    val ringing by ShiftAlarmService.ringing.collectAsState()
    val exact = remember(refresh) { ShiftAlarmScheduler.canScheduleExactAlarms(context) }
    val notifications = remember(refresh) { ShiftAlarmScheduler.hasNotificationPermission(context) }
    val scheduled = remember(config, refresh) { ShiftAlarmScheduler.scheduleNextAlarm(context) }
    val alarms = remember(config, refresh) { ShiftAlarmScheduler.getAllUpcomingAlarms(context, 30) }
    val definitions = ShiftRepository.shiftDefinitions.filter { (!it.isAllDay && it.category == ShiftCategory.LAVORO) || config.rules.any { rule -> rule.shiftDefId == it.id } }
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val alarmVolume = remember(refresh) { audio.getStreamVolume(AudioManager.STREAM_ALARM) }

    fun report(message: String, type: PopupType = PopupType.INFO) { feedback = message; feedbackType = type }
    fun openSettings(intent: Intent) {
        try { context.startActivity(intent) } catch (_: RuntimeException) { report("Impostazioni non disponibili su questo dispositivo", PopupType.ERROR) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        if (!it) report("Consenti le notifiche nelle impostazioni per ricevere e fermare le sveglie", PopupType.WARNING)
    }
    fun permissionsReady(): Boolean {
        ShiftAlarmService.ensureChannel(context)
        if (!ShiftAlarmScheduler.canScheduleExactAlarms(context)) {
            if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            report("Consenti Sveglie e promemoria, poi ripeti la prova", PopupType.WARNING)
            return false
        }
        if (!ShiftAlarmScheduler.hasNotificationPermission(context)) {
            if (Build.VERSION.SDK_INT >= 33 && context.applicationInfo.targetSdkVersion >= 33) {
                notificationLauncher.launch("android.permission.POST_NOTIFICATIONS")
            } else {
                // targetSdk 32: Android 13 owns the first channel prompt; denied access
                // must be restored in system settings rather than a non-working request.
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            report("Abilita le notifiche delle sveglie, poi ripeti la prova", PopupType.WARNING)
            return false
        }
        refresh++
        return true
    }
    fun updateRule(rule: ShiftAlarmRule) {
        ShiftRepository.smartAlarmsConfig = config.copy(rules = config.rules.filterNot { it.shiftDefId == rule.shiftDefId } + rule)
        if (rule.isEnabled && config.isGlobalEnabled) permissionsReady()
        ShiftAlarmScheduler.scheduleNextAlarm(context)
        refresh++
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    var toolsExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bellMotion = remember { Animatable(0f) }
    val tabMotion = remember { Animatable(0f) }
    val activeRules = config.rules.count { it.isEnabled }
    val upcoming = alarms.filter { alarm ->
        config.rules.any { rule -> rule.isEnabled && (
            if (alarm.isReperibilita) rule.shiftDefId == "reperibilita"
            else alarm.id == "${alarm.date}_${rule.shiftDefId}"
        ) }
    }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp).bouncyClickable { onDismiss() }) {
                        Box(contentAlignment = Alignment.Center) { SiteIcon("arrow-left", "Indietro", tint = MaterialTheme.colorScheme.onSurface) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("Sveglie", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground); Text("Collegate ai tuoi turni", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                }
            Spacer(Modifier.height(12.dp))
                AlarmSettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon("bell", tint = MaterialTheme.colorScheme.primary, progress = bellMotion.value)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text("Sveglie automatiche", fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Text(if (config.isGlobalEnabled) "$activeRules regole attive" else "In pausa · regole conservate", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Switch(config.isGlobalEnabled, {
                            ShiftRepository.smartAlarmsConfig = config.copy(isGlobalEnabled = it)
                            if (it) permissionsReady() else ShiftAlarmScheduler.cancelScheduledAlarm(context)
                            scope.launch { bellMotion.snapTo(0f); bellMotion.animateTo(1f, tween(620)); bellMotion.snapTo(0f) }
                            refresh++
                        })
                    }
                    Divider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))
                    Text(if (scheduled == null) {
                        if (!config.isGlobalEnabled) "Sveglie in pausa" else if (!exact || !notifications) "Autorizzazioni da completare" else "Nessuna sveglia programmata"
                    } else "Prossima: " + scheduled.triggerDateTime.format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.ITALIAN)), fontWeight = FontWeight.Medium, fontSize = 12.sp)

                }

            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                val slotWidth = maxWidth / 2
                val offset by animateDpAsState(if (tab == 0) 0.dp else slotWidth, spring(dampingRatio = .88f, stiffness = 300f))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.offset(x = offset).padding(4.dp).width(slotWidth - 8.dp).fillMaxHeight()) {}
                Row(Modifier.fillMaxSize()) {
                    listOf("Regole" to "settings", "Prossime sveglie" to "alarm-clock").forEachIndexed { index, item ->
                        Row(Modifier.weight(1f).fillMaxHeight().bouncyClickable {
                            tab = index
                            scope.launch { tabMotion.snapTo(0f); tabMotion.animateTo(1f, tween(560)); tabMotion.snapTo(0f) }
                        }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            val tint = if (tab == index) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            SiteIcon(item.second, modifier = Modifier.size(18.dp), tint = tint, progress = if (tab == index) tabMotion.value else 0f)
                            Spacer(Modifier.width(7.dp))
                            Text(item.first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Crossfade(tab, animationSpec = tween(260), modifier = Modifier.weight(1f)) { selectedTab ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (selectedTab == 0) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Scegli quando suonare", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val enable = activeRules == 0
                            val current = ShiftRepository.smartAlarmsConfig
                            val allIds = (definitions.map { it.id } + "reperibilita").toSet()
                            val rules = allIds.map { id ->
                                (current.rules.firstOrNull { it.shiftDefId == id } ?: ShiftAlarmRule(id, isEnabled = false, customTime = if (id == "reperibilita") "20:00" else null)).copy(isEnabled = enable)
                            }
                            ShiftRepository.smartAlarmsConfig = current.copy(rules = rules, disabledAlarmIds = if (enable) emptySet() else current.disabledAlarmIds)
                            if (enable && current.isGlobalEnabled) permissionsReady()
                            ShiftAlarmScheduler.scheduleNextAlarm(context)
                            refresh++
                            report(if (enable) "Tutte le regole sono attive" else "Tutte le regole sono disattivate", PopupType.SUCCESS)
                        }) { Text(if (activeRules > 0) "Disattiva tutte" else "Riattiva tutte", fontSize = 12.sp, color = if (activeRules > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                    }
                }
                items(definitions, key = { it.id }) { definition ->
                    val rule = config.rules.firstOrNull { it.shiftDefId == definition.id } ?: ShiftAlarmRule(definition.id, isEnabled = false)
                    NativeAlarmRule(definition.name, Color(definition.colorHex), rule, false, ::updateRule)
                }
                item {
                    val rule = config.rules.firstOrNull { it.shiftDefId == "reperibilita" } ?: ShiftAlarmRule("reperibilita", isEnabled = false, customTime = "20:00")
                    NativeAlarmRule("Reperibilità", MaterialTheme.colorScheme.tertiary, rule, true, ::updateRule)
                }
            } else {
                item { Text("Prossimi 30 giorni", Modifier.padding(vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (!config.isGlobalEnabled) item { AlarmSettingsCard {
                    Text("Sveglie in pausa", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Riattiva lo switch in alto per programmare le sveglie. Le tue regole restano salvate.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } }
                if (upcoming.isEmpty()) item { AlarmSettingsCard {
                    SiteIcon("alarm-clock", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("Nessuna sveglia da mostrare", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Le sveglie compaiono quando un turno ha una regola attiva.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    TextButton(onClick = { tab = 0 }) { Text("Scegli le regole") }
                } }
                items(upcoming, key = { it.id }) { alarm ->
                    AlarmSettingsCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color(alarm.shiftColorHex).copy(alpha = .18f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(40.dp)) {
                                Box(contentAlignment = Alignment.Center) { SiteIcon(if (alarm.isReperibilita) "bell" else "alarm-clock", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(alarm.triggerDateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text(alarm.triggerDateTime.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(alarm.shiftName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                            Switch(alarm.isEnabled, { ShiftAlarmScheduler.toggleAlarmId(context, alarm.id, it); if (it) permissionsReady(); refresh++ }, enabled = config.isGlobalEnabled)
                        }
                    }
                }
            }
            item {
                AlarmSettingsCard {
                    Row(Modifier.fillMaxWidth().bouncyClickable { toolsExpanded = !toolsExpanded }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon("shield-alert", tint = if (exact && notifications) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Verifica e prova", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (!exact || !notifications) "Autorizzazioni da completare" else if (alarmVolume == 0) "Volume sveglie a zero" else "Audio, autorizzazioni e prova sonora", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SiteIcon(if (toolsExpanded) "minus" else "plus", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    AnimatedVisibility(toolsExpanded, enter = expandVertically(spring(stiffness = 260f)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    AlarmPermissionRow("Sveglie esatte", exact) { permissionsReady() }
                    AlarmPermissionRow("Notifiche delle sveglie", notifications) {
                        ShiftAlarmService.ensureChannel(context)
                        openSettings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, ShiftAlarmReceiver.CHANNEL_ID))
                    }
                    Text("Volume sveglie: $alarmVolume/${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}", color = if (alarmVolume == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    TextButton(onClick = { openSettings(Intent(Settings.ACTION_SOUND_SETTINGS)) }) { Text("Controlla audio") }
                    TextButton(onClick = { openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Attività in background") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (permissionsReady()) {
                                val ok = ShiftAlarmScheduler.scheduleTestAlarm(context)
                                pendingTest = ok
                                report(if (ok) "Prova programmata tra 10 secondi. Puoi spegnere lo schermo." else "Prova non programmata: controlla le autorizzazioni", if (ok) PopupType.SUCCESS else PopupType.ERROR)
                            }
                        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Prova tra 10 s") }
                        if (pendingTest || ringing) OutlinedButton(onClick = { ShiftAlarmScheduler.cancelTestAlarm(context); pendingTest = false; report("Prova fermata") }, shape = RoundedCornerShape(16.dp)) { Text("Ferma") }
                    }
                    Text("Prova: massimo 20 secondi. Sveglie: massimo 2 minuti. Volume e Non disturbare restano sotto il tuo controllo.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    }
                }
            }
                }
            }
        }
        AnimatedStatusPopup(feedback, feedbackType, { feedback = null })
    }
}

@Composable
private fun AlarmSettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
    }
}

@Composable
private fun AlarmPermissionRow(label: String, allowed: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().bouncyClickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        SiteIcon(if (allowed) "check" else "settings", tint = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(if (allowed) "Consentite" else "Consenti", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NativeAlarmRule(title: String, color: Color, rule: ShiftAlarmRule, onCall: Boolean, onChange: (ShiftAlarmRule) -> Unit) {
    var customText by remember(rule.customTime) { mutableStateOf(rule.customTime ?: if (onCall) "20:00" else "05:00") }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val iconMotion = remember { Animatable(0f) }
    fun animateIcon() { scope.launch { iconMotion.snapTo(0f); iconMotion.animateTo(1f, tween(580)); iconMotion.snapTo(0f) } }
    val validTime = remember(customText) { runCatching { LocalTime.parse(customText) }.getOrNull() != null }
    AlarmSettingsCard {
        Row(Modifier.fillMaxWidth().bouncyClickable { expanded = !expanded; animateIcon() }, verticalAlignment = Alignment.CenterVertically) {
            Surface(color = color.copy(alpha = .2f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { SiteIcon(if (onCall) "bell" else "alarm-clock", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp), progress = iconMotion.value) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp); Text(if (onCall) "Promemoria il giorno precedente" else if (rule.customTime == null) "${rule.minutesBefore} min prima dell'attacco" else "Alle ${rule.customTime}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (rule.isEnabled) {
                SiteIcon(if (expanded) "minus" else "edit", contentDescription = "Modifica orario", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Switch(rule.isEnabled, { enabled ->
                expanded = enabled
                animateIcon()
                onChange(rule.copy(isEnabled = enabled))
            })
        }
        AnimatedVisibility(rule.isEnabled && expanded, enter = expandVertically(spring(dampingRatio = .9f, stiffness = 260f)) + fadeIn(), exit = shrinkVertically(spring(stiffness = 280f)) + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (!onCall) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onChange(rule.copy(customTime = null)); animateIcon() }) { Text("Anticipo", fontSize = 13.sp, fontWeight = if (rule.customTime == null) FontWeight.Bold else FontWeight.Normal) }
                    TextButton(onClick = { onChange(rule.copy(customTime = if (validTime) customText else "05:00")); animateIcon() }) { Text("Orario fisso", fontSize = 13.sp, fontWeight = if (rule.customTime != null) FontWeight.Bold else FontWeight.Normal) }
                }
                if (rule.customTime == null && !onCall) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(15, 30, 60, 90, 120).forEach { minutes ->
                            Surface(shape = RoundedCornerShape(12.dp), color = if (rule.minutesBefore == minutes) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f).bouncyClickable { onChange(rule.copy(minutesBefore = minutes)) }) {
                                Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("$minutes'", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(customText, { if (it.length <= 5) customText = it }, label = { Text("Orario HH:mm") }, singleLine = true,
                            isError = !validTime, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                        TextButton(onClick = { if (validTime) onChange(rule.copy(customTime = customText)) }, enabled = validTime) { Text("Imposta") }
                    }
                }
            }
        }
    }
}
