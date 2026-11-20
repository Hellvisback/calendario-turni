package com.turni.calendario.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.turni.calendario.alarm.ShiftAlarmReceiver
import com.turni.calendario.alarm.ShiftAlarmScheduler
import com.turni.calendario.alarm.ShiftAlarmService
import com.turni.calendario.model.ShiftAlarmRule
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SmartAlarmsSettingsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val config = ShiftRepository.smartAlarmsConfig
    var refresh by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackType by remember { mutableStateOf(PopupType.INFO) }
    var tab by remember { mutableIntStateOf(0) }
    var pendingTest by remember { mutableStateOf(false) }
    LaunchedEffect(pendingTest) {
        if (pendingTest) {
            delay(31_000L)
            pendingTest = false
        }
    }
    val ringing by ShiftAlarmService.ringing.collectAsState()
    val exact = remember(refresh) { ShiftAlarmScheduler.canScheduleExactAlarms(context) }
    val notifications = remember(refresh) { ShiftAlarmScheduler.hasNotificationPermission(context) }
    val fullScreen = remember(refresh) { ShiftAlarmScheduler.canShowFullScreenAlarms(context) }
    // Rendering reads the plan; only explicit actions/storage register alarms with Android.
    val scheduled = remember(config, refresh) {
        if (config.isGlobalEnabled && exact && notifications) ShiftAlarmScheduler.getNextUpcomingAlarm(context) else null
    }
    val alarms = remember(config, refresh) { ShiftAlarmScheduler.getAllUpcomingAlarms(context, 30) }
    val definitions = ShiftRepository.shiftDefinitions.filter {
        (!it.isAllDay && it.category == ShiftCategory.LAVORO) || config.rules.any { rule -> rule.shiftDefId == it.id }
    }
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val alarmVolume = remember(refresh) { audio.getStreamVolume(AudioManager.STREAM_ALARM) }

    fun report(message: String, type: PopupType = PopupType.INFO) {
        feedback = message
        feedbackType = type
    }

    fun openSettings(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: RuntimeException) {
            report("Impostazioni non disponibili su questo dispositivo", PopupType.ERROR)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        if (!it) report("Consenti le notifiche nelle impostazioni per ricevere e fermare le sveglie", PopupType.WARNING)
    }

    fun permissionsReady(): Boolean {
        ShiftAlarmService.ensureChannel(context)
        if (!ShiftAlarmScheduler.canScheduleExactAlarms(context)) {
            if (Build.VERSION.SDK_INT >= 31) {
                openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
            report("Consenti Sveglie e promemoria, poi ripeti la prova", PopupType.WARNING)
            return false
        }
        if (!ShiftAlarmScheduler.hasNotificationPermission(context)) {
            if (Build.VERSION.SDK_INT >= 33 && context.applicationInfo.targetSdkVersion >= 33) {
                notificationLauncher.launch("android.permission.POST_NOTIFICATIONS")
            } else {
                // Older Android releases expose notification controls through system settings.
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            report("Abilita le notifiche delle sveglie, poi ripeti la prova", PopupType.WARNING)
            return false
        }
        refresh++
        return true
    }

    fun updateRule(id: String, update: (ShiftAlarmRule) -> ShiftAlarmRule) {
        ShiftRepository.updateSmartAlarmRule(id, update)
        val latest = ShiftRepository.smartAlarmsConfig
        if (latest.rules.any { it.shiftDefId == id && it.isEnabled } && latest.isGlobalEnabled) permissionsReady()
        ShiftAlarmScheduler.scheduleNextAlarm(context)
        refresh++
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }

    var toolsExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bellMotion = remember { Animatable(0f) }
    val activeRules = config.rules.count { it.isEnabled }
    val upcoming = alarms.filter { alarm ->
        config.rules.any { rule ->
            rule.isEnabled && (
                if (alarm.isReperibilita) rule.shiftDefId == "reperibilita"
                else alarm.id == "${alarm.date}_${rule.shiftDefId}"
            )
        }
    }
    val palette = currentTurniPalette

    Box(modifier = Modifier.fillMaxSize()) {
        CloudFeatureScaffold(
            title = "Sveglie",
            subtitle = "Collegate ai tuoi turni",
            onDismiss = onDismiss,
            scrollable = false,
        ) {
            val bellBadgeFill by animateColorAsState(
                targetValue = if (config.isGlobalEnabled) ObsidianPrimaryContainer else ObsidianSurfaceContainer,
                animationSpec = tween(180),
                label = "alarmsSummaryBellFill",
            )
            val bellTint by animateColorAsState(
                targetValue = if (config.isGlobalEnabled) BrandInk else TextSecondary,
                animationSpec = tween(180),
                label = "alarmsSummaryBellTint",
            )
            val summaryBorder by animateColorAsState(
                targetValue = if (config.isGlobalEnabled) ObsidianBorder else ObsidianBorderSubtle,
                animationSpec = tween(180),
                label = "alarmsSummaryBorder",
            )

            AlarmSettingsCard(borderColor = summaryBorder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bellBadgeFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        SiteIcon(
                            name = "bell",
                            tint = bellTint,
                            modifier = Modifier.size(20.dp),
                            progress = bellMotion.value,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ),
                    ) {
                        Text(
                            text = "Sveglie automatiche",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                        )
                        Text(
                            text = if (config.isGlobalEnabled) "$activeRules regole attive" else "In pausa · regole conservate",
                            fontSize = 12.sp,
                            color = TextSecondary,
                        )
                    }
                    TurniSwitch(
                        checked = config.isGlobalEnabled,
                        onCheckedChange = { enabled ->
                            ShiftRepository.smartAlarmsConfig = ShiftRepository.smartAlarmsConfig.copy(isGlobalEnabled = enabled)
                            if (enabled) permissionsReady() else ShiftAlarmScheduler.cancelScheduledAlarm(context)
                            scope.launch {
                                bellMotion.snapTo(0f)
                                bellMotion.animateTo(1f, tween(620))
                                bellMotion.snapTo(0f)
                            }
                            refresh++
                        },
                    )
                }
                SettingsDivider()
                val nextAlarmText = if (scheduled == null) {
                    if (!config.isGlobalEnabled) {
                        "Sveglie in pausa"
                    } else if (!exact || !notifications) {
                        "Autorizzazioni da completare"
                    } else {
                        "Nessuna sveglia programmata"
                    }
                } else {
                    "Prossima: " + scheduled.triggerDateTime.format(
                        DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.ITALIAN),
                    )
                }
                val nextAlarmTint = when {
                    scheduled != null -> BrandInk
                    config.isGlobalEnabled && (!exact || !notifications) -> Color(palette.warning.ink)
                    else -> TextSecondary
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SiteIcon(
                        name = if (config.isGlobalEnabled && (!exact || !notifications)) "shield-alert" else "alarm-clock",
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = nextAlarmTint,
                    )
                    Text(
                        text = nextAlarmText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = nextAlarmTint,
                    )
                }
            }

            SlidingSegmentBar(
                items = listOf(
                    "Regole" to "settings",
                    "Prossime sveglie" to "alarm-clock",
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
            )

            AnimatedContent(
                targetState = tab,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) {
                        if (forward) it / 5 else -it / 5
                    } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                        if (forward) -it / 5 else it / 5
                    } + fadeOut(tween(180)))
                },
                label = "smartAlarmsTabContent",
            ) { selectedTab ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (selectedTab == 0) {
                        item {
                            val toggleAllFill by animateColorAsState(
                                targetValue = if (activeRules > 0) Color(palette.danger.fill) else ObsidianPrimarySubtle,
                                animationSpec = tween(180),
                                label = "toggleAllAlarmsFill",
                            )
                            val toggleAllInk by animateColorAsState(
                                targetValue = if (activeRules > 0) Color(palette.danger.ink) else BrandInk,
                                animationSpec = tween(180),
                                label = "toggleAllAlarmsInk",
                            )
                            val toggleAllBorder by animateColorAsState(
                                targetValue = if (activeRules > 0) Color(palette.danger.edge).copy(alpha = 0.4f) else ObsidianPrimary.copy(alpha = 0.4f),
                                animationSpec = tween(180),
                                label = "toggleAllAlarmsBorder",
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Scegli quando suonare",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(toggleAllFill)
                                        .border(1.dp, toggleAllBorder, RoundedCornerShape(10.dp))
                                        .bouncyClickable {
                                            val enable = activeRules == 0
                                            val current = ShiftRepository.smartAlarmsConfig
                                            val allIds = (definitions.map { it.id } + "reperibilita").toSet()
                                            val rules = allIds.map { id ->
                                                (current.rules.firstOrNull { it.shiftDefId == id }
                                                    ?: ShiftAlarmRule(
                                                        id,
                                                        isEnabled = false,
                                                        customTime = if (id == "reperibilita") "20:00" else null,
                                                    )).copy(isEnabled = enable)
                                            }
                                            ShiftRepository.smartAlarmsConfig = current.copy(
                                                rules = rules,
                                                disabledAlarmIds = if (enable) emptySet() else current.disabledAlarmIds,
                                            )
                                            if (enable && current.isGlobalEnabled) permissionsReady()
                                            ShiftAlarmScheduler.scheduleNextAlarm(context)
                                            refresh++
                                            report(
                                                if (enable) "Tutte le regole sono attive" else "Tutte le regole sono disattivate",
                                                PopupType.SUCCESS,
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                ) {
                                    Text(
                                        text = if (activeRules > 0) "Disattiva tutte" else "Riattiva tutte",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = toggleAllInk,
                                    )
                                }
                            }
                        }
                        items(definitions, key = { it.id }) { definition ->
                            val rule = config.rules.firstOrNull { it.shiftDefId == definition.id }
                                ?: ShiftAlarmRule(definition.id, isEnabled = false)
                            NativeAlarmRule(
                                title = definition.name,
                                color = Color(definition.colorHex),
                                rule = rule,
                                onCall = false,
                                onChange = { updateRule(definition.id, it) },
                                onTimeSaved = { report("Orario impostato · $it", PopupType.SUCCESS) },
                            )
                        }
                        item(key = "on-call-rule") {
                            val rule = config.rules.firstOrNull { it.shiftDefId == "reperibilita" }
                                ?: ShiftAlarmRule("reperibilita", isEnabled = false, customTime = "20:00")
                            NativeAlarmRule(
                                title = "Reperibilità",
                                color = ObsidianTertiary,
                                rule = rule,
                                onCall = true,
                                onChange = { updateRule("reperibilita", it) },
                                onTimeSaved = { report("Orario impostato · $it", PopupType.SUCCESS) },
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "Prossimi 30 giorni",
                                modifier = Modifier.padding(vertical = 4.dp),
                                fontSize = 12.sp,
                                color = TextSecondary,
                            )
                        }
                        if (!config.isGlobalEnabled) {
                            item {
                                CloudCard(
                                    title = "Sveglie in pausa",
                                    subtitle = "Riattiva lo switch in alto per programmare le sveglie. Le tue regole restano salvate.",
                                ) {}
                            }
                        }
                        if (upcoming.isEmpty()) {
                            item {
                                CloudCard(
                                    title = "Nessuna sveglia da mostrare",
                                    subtitle = "Le sveglie compaiono quando un turno ha una regola attiva.",
                                ) {
                                    CloudButton(
                                        label = "Scegli le regole",
                                        icon = "settings",
                                        onClick = { tab = 0 },
                                        secondary = true,
                                    )
                                }
                            }
                        }
                        items(upcoming, key = { it.id }) { alarm ->
                            val shiftColor = Color(alarm.shiftColorHex)
                            val alarmActive = alarm.isEnabled && config.isGlobalEnabled
                            val alarmBorder by animateColorAsState(
                                targetValue = if (alarmActive) ObsidianBorder else ObsidianBorderSubtle,
                                animationSpec = tween(180),
                                label = "upcomingAlarmBorder",
                            )
                            val alarmTint by animateColorAsState(
                                targetValue = if (alarmActive) BrandInk else TextSecondary,
                                animationSpec = tween(180),
                                label = "upcomingAlarmTint",
                            )
                            AlarmSettingsCard(borderColor = alarmBorder) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(shiftColor.copy(alpha = 0.2f))
                                            .border(1.dp, shiftColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        SiteIcon(
                                            name = if (alarm.isReperibilita) "bell" else "alarm-clock",
                                            tint = alarmTint,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = alarm.triggerDateTime.format(
                                                DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN),
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp,
                                            color = if (alarmActive) TextPrimary else TextSecondary,
                                        )
                                        Text(
                                            text = alarm.triggerDateTime.format(
                                                DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN),
                                            ),
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                        )
                                        Text(
                                            text = alarm.shiftName,
                                            color = TextSecondary,
                                            fontSize = 13.sp,
                                        )
                                    }
                                    TurniSwitch(
                                        checked = alarm.isEnabled,
                                        onCheckedChange = { enabled ->
                                            ShiftAlarmScheduler.toggleAlarmId(context, alarm.id, enabled)
                                            if (enabled) permissionsReady()
                                            refresh++
                                        },
                                        enabled = config.isGlobalEnabled,
                                    )
                                }
                            }
                        }
                    }

                    item {
                        val permissionsOk = exact && notifications
                        val shieldFill = if (permissionsOk) ObsidianPrimaryContainer else Color(palette.danger.fill)
                        val shieldTint = if (permissionsOk) BrandInk else Color(palette.danger.ink)
                        val toolsChevron by animateFloatAsState(
                            targetValue = if (toolsExpanded) 180f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "toolsChevronRotation",
                        )
                        AlarmSettingsCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .bouncyClickable { toolsExpanded = !toolsExpanded }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(shieldFill),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SiteIcon(
                                        name = "shield-alert",
                                        tint = shieldTint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Verifica e prova",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                    )
                                    Text(
                                        text = if (!permissionsOk) {
                                            "Autorizzazioni da completare"
                                        } else if (alarmVolume == 0) {
                                            "Volume sveglie a zero"
                                        } else {
                                            "Audio, autorizzazioni e prova sonora"
                                        },
                                        fontSize = 11.sp,
                                        color = if (!permissionsOk || alarmVolume == 0) Color(palette.warning.ink) else TextSecondary,
                                    )
                                }
                                SiteIcon(
                                    name = "chevron-down",
                                    tint = BrandInk,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(toolsChevron),
                                )
                            }
                            AnimatedVisibility(
                                visible = toolsExpanded,
                                enter = expandVertically(spring(stiffness = 260f)) + fadeIn(tween(180)),
                                exit = shrinkVertically() + fadeOut(tween(140)),
                            ) {
                                Column(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SettingsDivider()
                                    if (Build.VERSION.SDK_INT >= 34) {
                                        AlarmPermissionRow("Schermata della sveglia", fullScreen) {
                                            openSettings(
                                                Intent(
                                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                                    Uri.parse("package:${context.packageName}"),
                                                ),
                                            )
                                        }
                                        Text(
                                            text = "Consente di aprire la sveglia a schermo bloccato. Senza questa opzione restano audio e notifica; il dispositivo decide come mostrarla.",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 17.sp,
                                        )
                                    }
                                    AlarmPermissionRow("Sveglie esatte", exact) { permissionsReady() }
                                    AlarmPermissionRow("Notifiche delle sveglie", notifications) {
                                        ShiftAlarmService.ensureChannel(context)
                                        openSettings(
                                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                .putExtra(Settings.EXTRA_CHANNEL_ID, ShiftAlarmReceiver.CHANNEL_ID),
                                        )
                                    }
                                    SettingsDivider()
                                    Text(
                                        text = "Volume sveglie: $alarmVolume/${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}",
                                        color = if (alarmVolume == 0) Color(palette.danger.ink) else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        CloudButton(
                                            label = "Controlla audio",
                                            icon = "bell",
                                            onClick = { openSettings(Intent(Settings.ACTION_SOUND_SETTINGS)) },
                                            modifier = Modifier.weight(1f),
                                            secondary = true,
                                        )
                                        CloudButton(
                                            label = "Attività in background",
                                            icon = "smartphone",
                                            onClick = {
                                                openSettings(
                                                    Intent(
                                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                        Uri.parse("package:${context.packageName}"),
                                                    ),
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            secondary = true,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        CloudButton(
                                            label = "Prova tra 10 s",
                                            icon = "timer",
                                            onClick = {
                                                if (permissionsReady()) {
                                                    val ok = ShiftAlarmScheduler.scheduleTestAlarm(context)
                                                    pendingTest = ok
                                                    report(
                                                        if (ok) "Prova programmata tra 10 secondi. Puoi spegnere lo schermo." else "Prova non programmata: controlla le autorizzazioni",
                                                        if (ok) PopupType.SUCCESS else PopupType.ERROR,
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (pendingTest || ringing) {
                                            CloudButton(
                                                label = "Ferma",
                                                icon = "x",
                                                onClick = {
                                                    ShiftAlarmScheduler.cancelTestAlarm(context)
                                                    pendingTest = false
                                                    report("Prova fermata")
                                                },
                                                destructive = true,
                                                modifier = Modifier.weight(0.65f),
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Prova: massimo 20 secondi. Sveglie: massimo 2 minuti. Volume e Non disturbare restano sotto il tuo controllo.",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp,
                                    )
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
private fun AlarmSettingsCard(
    borderColor: Color = ObsidianBorderSubtle,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun AlarmPermissionRow(
    label: String,
    allowed: Boolean,
    onClick: () -> Unit,
) {
    val palette = currentTurniPalette
    val rowShape = RoundedCornerShape(12.dp)
    val badgeFill = if (allowed) Color(palette.success.fill) else ObsidianPrimarySubtle
    val badgeInk = if (allowed) Color(palette.success.ink) else BrandInk
    val iconTint = if (allowed) Color(palette.success.ink) else Color(palette.danger.ink)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(ObsidianSurfaceContainer)
            .border(1.dp, ObsidianBorderSubtle, rowShape)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SiteIcon(
            name = if (allowed) "check" else "settings",
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeFill)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = if (allowed) "Consentite" else "Consenti",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = badgeInk,
            )
        }
    }
}

@Composable
private fun NativeAlarmRule(
    title: String,
    color: Color,
    rule: ShiftAlarmRule,
    onCall: Boolean,
    onChange: ((ShiftAlarmRule) -> ShiftAlarmRule) -> Unit,
    onTimeSaved: (String) -> Unit,
) {
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var customText by remember(rule.shiftDefId, rule.customTime) {
        mutableStateOf(rule.customTime ?: if (onCall) "20:00" else "05:00")
    }
    var expanded by remember(rule.shiftDefId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val iconMotion = remember { Animatable(0f) }
    fun animateIcon() {
        scope.launch {
            iconMotion.snapTo(0f)
            iconMotion.animateTo(1f, tween(580))
            iconMotion.snapTo(0f)
        }
    }
    val validTime = remember(customText) { runCatching { LocalTime.parse(customText) }.getOrNull() != null }
    fun saveTime() {
        val parsed = runCatching { LocalTime.parse(customText.trim()) }.getOrNull() ?: return
        val normalized = parsed.format(DateTimeFormatter.ofPattern("HH:mm"))
        onChange { it.copy(customTime = normalized) }
        customText = normalized
        focus.clearFocus()
        expanded = false
        animateIcon()
        onTimeSaved(normalized)
    }

    val cardBorder by animateColorAsState(
        targetValue = when {
            rule.isEnabled && expanded -> ObsidianPrimary
            rule.isEnabled -> ObsidianBorder
            else -> ObsidianBorderSubtle
        },
        animationSpec = tween(200),
        label = "alarmRuleCardBorder",
    )
    val badgeIconTint by animateColorAsState(
        targetValue = if (rule.isEnabled) BrandInk else TextSecondary,
        animationSpec = tween(180),
        label = "alarmRuleIconTint",
    )
    val titleTint by animateColorAsState(
        targetValue = if (rule.isEnabled) TextPrimary else TextSecondary,
        animationSpec = tween(180),
        label = "alarmRuleTitleTint",
    )
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "alarmRuleChevron",
    )

    AlarmSettingsCard(borderColor = cardBorder) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .bouncyClickable(enabled = rule.isEnabled) {
                    expanded = !expanded
                    animateIcon()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.2f))
                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                SiteIcon(
                    name = if (onCall) "bell" else "alarm-clock",
                    tint = badgeIconTint,
                    modifier = Modifier.size(20.dp),
                    progress = iconMotion.value,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = titleTint,
                )
                Text(
                    text = if (onCall) {
                        "Alle ${rule.customTime ?: "20:00"} · giorno precedente"
                    } else if (rule.customTime == null) {
                        "${rule.minutesBefore} min prima dell'attacco"
                    } else {
                        "Alle ${rule.customTime}"
                    },
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
            }
            AnimatedVisibility(
                visible = rule.isEnabled,
                enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                exit = fadeOut(tween(120)) + scaleOut(tween(120)),
            ) {
                SiteIcon(
                    name = "chevron-down",
                    contentDescription = "Modifica orario",
                    tint = BrandInk,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronAngle),
                )
            }
            TurniSwitch(
                checked = rule.isEnabled,
                onCheckedChange = { enabled ->
                    expanded = enabled
                    animateIcon()
                    focus.clearFocus()
                    onChange { it.copy(isEnabled = enabled) }
                },
            )
        }

        AnimatedVisibility(
            visible = rule.isEnabled && expanded,
            enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 260f)) + fadeIn(tween(180)),
            exit = shrinkVertically(spring(stiffness = 280f)) + fadeOut(tween(140)),
        ) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsDivider()
                if (!onCall) {
                    SlidingSegmentBar(
                        items = listOf(
                            "Anticipo" to "timer",
                            "Orario fisso" to "clock",
                        ),
                        selectedIndex = if (rule.customTime == null) 0 else 1,
                        onSelect = { index ->
                            if (index == 0) {
                                onChange { it.copy(customTime = null) }
                            } else {
                                onChange { it.copy(customTime = if (validTime) customText else "05:00") }
                            }
                            animateIcon()
                        },
                        height = 40.dp,
                    )
                }
                if (rule.customTime == null && !onCall) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(15, 30, 60, 90, 120).forEach { minutes ->
                            val selected = rule.minutesBefore == minutes
                            val chipShape = RoundedCornerShape(12.dp)
                            val chipFill by animateColorAsState(
                                targetValue = if (selected) ObsidianPrimary else ObsidianSurfaceContainer,
                                animationSpec = tween(180),
                                label = "alarmMinutesFill",
                            )
                            val chipBorder by animateColorAsState(
                                targetValue = if (selected) ObsidianPrimary else ObsidianBorderSubtle,
                                animationSpec = tween(180),
                                label = "alarmMinutesBorder",
                            )
                            val chipInk by animateColorAsState(
                                targetValue = if (selected) OnBrand else TextSecondary,
                                animationSpec = tween(180),
                                label = "alarmMinutesInk",
                            )
                            val interactionSource = remember(minutes) { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(chipShape)
                                    .background(chipFill)
                                    .border(1.dp, chipBorder, chipShape)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                    ) {
                                        try {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } catch (_: Exception) {
                                        }
                                        onChange { it.copy(minutesBefore = minutes) }
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$minutes'",
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = chipInk,
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { if (it.length <= 5) customText = it },
                            label = { Text("Orario HH:mm") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { saveTime() }),
                            isError = !validTime,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ObsidianPrimary,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedLabelColor = BrandInk,
                                unfocusedLabelColor = TextSecondary,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = ObsidianPrimary,
                                errorBorderColor = ColorNegative,
                                errorLabelColor = ColorNegative,
                            ),
                        )
                        val saveShape = RoundedCornerShape(14.dp)
                        val saveFill by animateColorAsState(
                            targetValue = if (validTime) ObsidianPrimary else ObsidianSurfaceHigh,
                            animationSpec = tween(180),
                            label = "saveAlarmTimeFill",
                        )
                        val saveInk by animateColorAsState(
                            targetValue = if (validTime) OnBrand else TextMuted,
                            animationSpec = tween(180),
                            label = "saveAlarmTimeInk",
                        )
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .alpha(if (validTime) 1f else 0.6f)
                                .clip(saveShape)
                                .background(saveFill)
                                .bouncyClickable(enabled = validTime) { saveTime() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Imposta",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = saveInk,
                            )
                        }
                    }
                }
            }
        }
    }
}
