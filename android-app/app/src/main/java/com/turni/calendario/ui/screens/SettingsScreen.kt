package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.BuildConfig
import com.turni.calendario.alarm.ShiftAlarmScheduler
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.launch

private enum class SettingsSubScreen {
    NONE,
    APPEARANCE,
    CLOUD_BACKUP,
    GOOGLE_CALENDAR_SYNC,
    IMPORT_SHIFTS,
    PDF_EXPORT,
    REPERIBILITA,
    SMART_ALARMS,
    HOLIDAYS,
    STATISTICS_RULES,
}

@Composable
fun SettingsScreen(
    onSubScreenActiveChanged: (Boolean) -> Unit = {},
    onNavigateToTurni: (ShiftRotation?) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bellMotion = remember { Animatable(0f) }
    val settingsScroll = rememberScrollState()
    var screen by remember { mutableStateOf(SettingsSubScreen.NONE) }
    var deleteStep by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(screen) { onSubScreenActiveChanged(screen != SettingsSubScreen.NONE) }
    DisposableEffect(Unit) { onDispose { onSubScreenActiveChanged(false) } }
    BackHandler { if (screen != SettingsSubScreen.NONE) screen = SettingsSubScreen.NONE else onDismiss() }

    val close: () -> Unit = { screen = SettingsSubScreen.NONE }

    Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val openingSub = initialState == SettingsSubScreen.NONE && targetState != SettingsSubScreen.NONE
                val closingSub = initialState != SettingsSubScreen.NONE && targetState == SettingsSubScreen.NONE
                when {
                    openingSub -> (
                        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 4 } +
                            fadeIn(tween(240, easing = FastOutSlowInEasing))
                        ) togetherWith (
                        slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { -it / 6 } +
                            fadeOut(tween(180))
                        )
                    closingSub -> (
                        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 6 } +
                            fadeIn(tween(220, easing = FastOutSlowInEasing))
                        ) togetherWith (
                        slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 4 } +
                            fadeOut(tween(180))
                        )
                    else -> fadeIn(tween(200)) togetherWith fadeOut(tween(160))
                }
            },
            label = "settingsSubScreen",
        ) { destination ->
            Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
                when (destination) {
                    SettingsSubScreen.CLOUD_BACKUP -> CloudBackupScreen(onDismiss = close)
                    SettingsSubScreen.STATISTICS_RULES -> StatisticsRulesScreen(onDismiss = close)
                    SettingsSubScreen.GOOGLE_CALENDAR_SYNC -> GoogleCalendarSyncScreen(onDismiss = close)
                    SettingsSubScreen.IMPORT_SHIFTS -> GeminiShiftImportScreen(
                        onDismiss = close,
                        onNavigateToTurni = { rotation ->
                            close()
                            onNavigateToTurni(rotation)
                        },
                    )
                    SettingsSubScreen.PDF_EXPORT -> PdfExportScreen(onDismiss = close)
                    SettingsSubScreen.REPERIBILITA -> ReperibilitaSettingsScreen(onDismiss = close)
                    SettingsSubScreen.SMART_ALARMS -> SmartAlarmsSettingsScreen(onDismiss = close)
                    SettingsSubScreen.HOLIDAYS -> HolidaysSettingsScreen(onDismiss = close)
                    SettingsSubScreen.APPEARANCE -> CloudFeatureScaffold(
                        title = "Aspetto e calendario",
                        subtitle = "Tema, inizio settimana, orari ed ex turni",
                        onDismiss = close,
                    ) {
                        CloudCard(
                            title = "Tema",
                            subtitle = "Adatta superfici e contrasto al tuo dispositivo.",
                        ) {
                            PreferenceThemeSelector()
                            SettingsDivider()
                            PreferencePaletteSelector()
                        }
                        CloudCard(
                            title = "Calendario",
                            subtitle = "Preferenze della griglia, formato orario ed ex turni.",
                        ) {
                            PreferenceToggle(
                                title = "La settimana inizia lunedì",
                                subtitle = "Disattiva per iniziare dalla domenica",
                                checked = ShiftRepository.appPreferences.weekStartMonday,
                            ) {
                                ShiftRepository.appPreferences =
                                    ShiftRepository.appPreferences.copy(weekStartMonday = it)
                                ShiftRepository.notifyDataChanged()
                            }
                            SettingsDivider()
                            PreferenceToggle(
                                title = "Formato 24 ore",
                                subtitle = "Attivo: 14:00 · disattivo: 2:00 PM",
                                checked = ShiftRepository.appPreferences.timeFormat24h,
                            ) {
                                ShiftRepository.appPreferences =
                                    ShiftRepository.appPreferences.copy(timeFormat24h = it)
                                ShiftRepository.notifyDataChanged()
                            }
                            SettingsDivider()
                            PreferenceToggle(
                                title = "Mostra turno di provenienza",
                                subtitle = "Mostra «ex turno» quando sostituisci un turno inserito da una rotazione. La gomma rimuove anche questa provenienza.",
                                checked = ShiftRepository.appPreferences.showExShifts,
                            ) {
                                ShiftRepository.appPreferences =
                                    ShiftRepository.appPreferences.copy(showExShifts = it)
                                ShiftRepository.notifyDataChanged()
                            }
                        }
                    }
                    SettingsSubScreen.NONE -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(settingsScroll)
                            .padding(horizontal = 16.dp),
                    ) {
                        PreferenceHeading("Impostazioni", ShiftRepository.activeWorkProfile.name, onDismiss)

                        PreferenceGroup("Calendario e statistiche") {
                            PreferenceRow(
                                icon = "palette",
                                title = "Aspetto e calendario",
                                subtitle = "Tema, inizio settimana, orari ed ex turni",
                            ) {
                                screen = SettingsSubScreen.APPEARANCE
                            }
                            SettingsDivider()
                            PreferenceRow(
                                icon = "calendar-heart",
                                title = "Festività",
                                subtitle = "Ricorrenze valide per il tuo lavoro",
                            ) {
                                screen = SettingsSubScreen.HOLIDAYS
                            }
                            SettingsDivider()
                            PreferenceRow(
                                icon = "chart-no-axes-combined",
                                title = "Conteggio statistiche",
                                subtitle = "Turni sostituiti e ferie nei festivi",
                            ) {
                                screen = SettingsSubScreen.STATISTICS_RULES
                            }
                        }

                        PreferenceGroup("Reperibilità e sveglie", "bell", iconProgress = bellMotion.value) {
                            PreferenceRow(
                                icon = "bell-ring",
                                title = "Reperibilità",
                                subtitle = "Mostra la campanella sui giorni in cui sei reperibile",
                            ) {
                                screen = SettingsSubScreen.REPERIBILITA
                            }
                            SettingsDivider()
                            val alarms = ShiftRepository.smartAlarmsConfig
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PreferenceRow(
                                    icon = "alarm-clock",
                                    title = "Regole e prossime sveglie",
                                    subtitle = "Configura gli avvisi sonori legati ai turni",
                                    modifier = Modifier.weight(1f),
                                ) {
                                    screen = SettingsSubScreen.SMART_ALARMS
                                }
                                TurniSwitch(
                                    checked = alarms.isGlobalEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled && (!ShiftAlarmScheduler.canScheduleExactAlarms(context) || !ShiftAlarmScheduler.hasNotificationPermission(context))) {
                                            screen = SettingsSubScreen.SMART_ALARMS
                                        } else {
                                            ShiftRepository.smartAlarmsConfig = alarms.copy(isGlobalEnabled = enabled)
                                            if (enabled) {
                                                ShiftAlarmScheduler.scheduleNextAlarm(context)
                                                scope.launch {
                                                    bellMotion.snapTo(0f)
                                                    bellMotion.animateTo(1f, tween(580))
                                                    bellMotion.snapTo(0f)
                                                }
                                            } else {
                                                ShiftAlarmScheduler.cancelScheduledAlarm(context)
                                            }
                                            ShiftRepository.notifyDataChanged()
                                        }
                                    },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Sveglie automatiche"
                                    },
                                )
                            }
                        }

                        PreferenceGroup("Importazione e condivisione") {
                            PreferenceRow(
                                icon = "scan-text",
                                title = "Importa turni con AI",
                                subtitle = "Crea modelli e rotazioni da un prospetto",
                            ) {
                                screen = SettingsSubScreen.IMPORT_SHIFTS
                            }
                            SettingsDivider()
                            PreferenceRow(
                                icon = "file-text",
                                title = "Esporta e stampa PDF",
                                subtitle = "Calendario o elenco dei turni",
                            ) {
                                screen = SettingsSubScreen.PDF_EXPORT
                            }
                            SettingsDivider()
                            PreferenceRow(
                                icon = "calendar-sync",
                                title = "Google Calendar",
                                subtitle = "Sincronizza i turni come eventi",
                            ) {
                                screen = SettingsSubScreen.GOOGLE_CALENDAR_SYNC
                            }
                        }

                        PreferenceGroup("Dati dell’app") {
                            PreferenceRow(
                                icon = "database-backup",
                                title = "Backup e ripristino",
                                subtitle = "Salva o recupera tutti i dati dell’app",
                            ) {
                                screen = SettingsSubScreen.CLOUD_BACKUP
                            }
                            SettingsDivider()
                            PreferenceRow(
                                icon = "trash-2",
                                title = "Elimina tutti i turni",
                                subtitle = "Da tutti i calendari · richiede conferma",
                                danger = true,
                            ) {
                                deleteStep = 1
                            }
                        }

                        Text(
                            text = "TurniSync · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            modifier = Modifier.padding(vertical = 16.dp),
                            fontSize = 11.sp,
                            color = TextSecondary,
                        )

                        Spacer(Modifier.height(116.dp))
                    }
                }
            }
        }

        if (deleteStep > 0) {
            PreferenceDeleteDialog(
                step = deleteStep,
                onClose = { deleteStep = 0 },
                onContinue = { deleteStep = 2 },
                onDelete = {
                    ShiftRepository.clearAllShifts()
                    deleteStep = 0
                    message = "Turni eliminati"
                },
            )
        }
        AnimatedStatusPopup(
            message = message,
            type = if (message == "Turni eliminati") PopupType.ERROR else PopupType.SUCCESS,
            onDismiss = { message = null },
        )
    }
}

@Composable
private fun PreferenceHeading(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 18.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val backShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(backShape)
                .background(ObsidianSurface)
                .border(1.dp, ObsidianBorderSubtle, backShape)
                .bouncyClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            SiteIcon("arrow-left", "Indietro", Modifier.size(20.dp), tint = TextPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.5).sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreferenceGroup(
    title: String,
    icon: String? = null,
    bottom: Int = 6,
    iconProgress: Float = 0f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianBorderSubtle),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottom.dp)) {
            Row(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    SiteIcon(icon, null, Modifier.size(18.dp), tint = BrandInk, progress = iconProgress)
                }
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            content()
        }
    }
}

@Composable
private fun PreferenceRow(
    icon: String,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = currentTurniPalette
    val tileFill = Color(if (danger) palette.danger.fill else palette.primaryContainer)
    val tint = Color(if (danger) palette.danger.ink else palette.onPrimaryContainer)
    val scope = rememberCoroutineScope()
    val motion = remember { Animatable(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .bouncyClickable {
                scope.launch {
                    motion.snapTo(0f)
                    motion.animateTo(1f, tween(520))
                    motion.snapTo(0f)
                }
                onClick()
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tileFill),
            contentAlignment = Alignment.Center,
        ) {
            SiteIconMotion(icon, null, Modifier.size(19.dp), tint = tint) { motion.value }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (danger) tint else TextPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 2.dp),
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        SiteIcon("chevron-right", null, Modifier.size(16.dp), tint = TextSecondary)
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, Modifier.padding(top = 4.dp), fontSize = 11.sp, lineHeight = 16.sp, color = TextSecondary)
        }
        TurniSwitch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun PreferenceDeleteDialog(step: Int, onClose: () -> Unit, onContinue: () -> Unit, onDelete: () -> Unit) {
    var acknowledged by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val palette = currentTurniPalette
    val canContinue = step == 1 || acknowledged
    val actionFill = if (canContinue) Color(palette.destructive) else ObsidianSurfaceHigh
    val actionInk = if (canContinue) Color(palette.onDestructive) else TextMuted

    LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = .88f, stiffness = 170f)) }
    fun dismiss() {
        scope.launch {
            progress.animateTo(0f, tween(260))
            onClose()
        }
    }

    Dialog(onDismissRequest = { dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .padding(bottom = 112.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = progress.value
                        translationY = (1 - progress.value) * 130
                        scaleX = .96f + .04f * progress.value
                        scaleY = scaleX
                    },
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(palette.danger.fill)),
                            contentAlignment = Alignment.Center,
                        ) {
                            SiteIcon("trash-2", null, Modifier.size(25.dp), tint = Color(palette.danger.ink))
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Passaggio $step di 2", fontSize = 10.sp, color = TextMuted)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .bouncyClickable { dismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            SiteIcon("x", "Chiudi", Modifier.size(19.dp), tint = ColorNegative)
                        }
                    }
                    Text(
                        text = if (step == 1) "Eliminare tutti i turni?" else "Conferma eliminazione",
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        letterSpacing = (-0.5).sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Sei sicuro di voler eliminare definitivamente tutti i turni? Questa azione è irreversibile.",
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary,
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurfaceHigh,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        Text(
                            text = "Tutti i calendari\nI modelli di turno e i lavori restano disponibili.",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            color = TextMuted,
                        )
                    }
                    if (step == 2) {
                        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(acknowledged, { acknowledged = it })
                            Text(
                                text = "Ho capito che i turni e i relativi dettagli verranno eliminati.",
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                color = TextPrimary,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .height(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(actionFill)
                            .bouncyClickable(enabled = canContinue) {
                                if (step == 1) onContinue()
                                else scope.launch {
                                    progress.animateTo(0f, tween(260))
                                    onDelete()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (step == 1) "Continua" else "Conferma eliminazione",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = actionInk,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .bouncyClickable { dismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Annulla", fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PreferenceThemeSelector() {
    val context = LocalContext.current
    val modes = remember { listOf(ThemeMode.AUTOMATIC, ThemeMode.LIGHT, ThemeMode.DARK) }
    val selected = TurniThemeState.mode
    val items = remember {
        listOf(
            "Sistema" to "smartphone",
            "Chiaro" to "sun",
            "Scuro" to "moon",
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SlidingSegmentBar(
            items = items,
            selectedIndex = modes.indexOf(selected).coerceAtLeast(0),
            onSelect = { index ->
                TurniThemeState.setMode(context, modes[index])
            },
            height = 50.dp,
        )
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            },
            label = "themeModeDescription",
        ) { mode ->
            Text(
                text = when (mode) {
                    ThemeMode.AUTOMATIC -> "Segue il tema del dispositivo, anche quando cambia durante la giornata."
                    ThemeMode.LIGHT -> "Superfici chiare e colori ad alto contrasto."
                    ThemeMode.DARK -> "Superfici scure e luminosità ridotta."
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun PreferencePaletteSelector() {
    val palette = currentTurniPalette
    Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(palette.dock))
                    .border(1.dp, ObsidianBorderSubtle, CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(palette.dockSelected)),
            )
            Text("Grafite e cobalto", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = "La stessa palette si adatta al tema chiaro o scuro. I colori personalizzati dei tuoi turni restano disponibili.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}
