package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.BuildConfig
import com.turni.calendario.alarm.ShiftAlarmScheduler
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.CalendarSurface
import com.turni.calendario.ui.theme.CalendarToday
import com.turni.calendario.ui.theme.CalendarWeekend
import com.turni.calendario.ui.theme.ColorMattina
import com.turni.calendario.ui.theme.ColorNegative
import com.turni.calendario.ui.theme.ColorNotte
import com.turni.calendario.ui.theme.ColorPomeriggio
import com.turni.calendario.ui.theme.ColorRiposo
import com.turni.calendario.ui.theme.ObsidianBackground
import com.turni.calendario.ui.theme.ObsidianBorderSubtle
import com.turni.calendario.ui.theme.ObsidianPrimary
import com.turni.calendario.ui.theme.ObsidianSurface
import com.turni.calendario.ui.theme.ObsidianSurfaceContainer
import com.turni.calendario.ui.theme.ObsidianSurfaceHigh
import com.turni.calendario.ui.theme.ObsidianSurfaceHighest
import com.turni.calendario.ui.theme.OnBrand
import com.turni.calendario.ui.theme.TextMuted
import com.turni.calendario.ui.theme.TextPrimary
import com.turni.calendario.ui.theme.TextSecondary
import com.turni.calendario.ui.theme.ThemeMode
import com.turni.calendario.ui.theme.TurniThemeState
import com.turni.calendario.ui.theme.currentTurniPalette
import com.turni.calendario.ui.theme.readableInk
import com.turni.calendario.ui.theme.shiftColorStyle
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
    var deleteStep by remember { mutableIntStateOf(0) }
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
                        AppearanceSettingsContent()
                    }
                    SettingsSubScreen.NONE -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(settingsScroll)
                            .padding(horizontal = 16.dp),
                    ) {
                        PreferenceHeading(title = "Impostazioni", onBack = onDismiss)

                        SectionLabel(
                            text = "Calendario e statistiche",
                            accent = ExpressivePastel.SKY,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        PreferenceGroup {
                            val themeBadge = when (TurniThemeState.mode) {
                                ThemeMode.AUTOMATIC -> "Sistema"
                                ThemeMode.LIGHT -> "Chiaro"
                                ThemeMode.DARK -> "Scuro"
                            }
                            PreferenceRow(
                                pastel = ExpressivePastel.SKY,
                                icon = "palette",
                                title = "Aspetto e calendario",
                                subtitle = "Tema, inizio settimana, orari ed ex turni",
                                statusPill = themeBadge,
                            ) {
                                screen = SettingsSubScreen.APPEARANCE
                            }
                            SettingsDivider(Modifier.padding(horizontal = 4.dp))
                            PreferenceRow(
                                pastel = ExpressivePastel.CORAL,
                                icon = "calendar-heart",
                                title = "Festività",
                                subtitle = "Ricorrenze valide per il tuo lavoro",
                                statusPill = "${ShiftRepository.enabledHolidayIds.size} attive",
                            ) {
                                screen = SettingsSubScreen.HOLIDAYS
                            }
                            SettingsDivider(Modifier.padding(horizontal = 4.dp))
                            PreferenceRow(
                                pastel = ExpressivePastel.MINT,
                                icon = "report",
                                title = "Conteggio statistiche",
                                subtitle = "Turni sostituiti e ferie nei festivi",
                            ) {
                                screen = SettingsSubScreen.STATISTICS_RULES
                            }
                        }

                        SectionLabel(
                            text = "Reperibilità e sveglie",
                            accent = ExpressivePastel.AMBER,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        PreferenceGroup {
                            val onCallMode = ShiftRepository.reperibilitaConfig.mode
                            val onCallStatus = when (onCallMode) {
                                ReperibilitaMode.TURNO_SPECIFICO -> "Per turno"
                                ReperibilitaMode.ALTERNATA_STAGIONALE -> "Stagionale"
                                ReperibilitaMode.MANUALE -> "Disattiva"
                            }
                            val onCallPastel = if (onCallMode == ReperibilitaMode.MANUALE) {
                                ExpressivePastel.PEARL
                            } else {
                                ExpressivePastel.AMBER
                            }
                            PreferenceRow(
                                pastel = ExpressivePastel.AMBER,
                                icon = "bell",
                                title = "Reperibilità",
                                subtitle = "Mostra la campanella sui giorni in cui sei reperibile",
                                statusPill = onCallStatus,
                                statusPastel = onCallPastel,
                            ) {
                                screen = SettingsSubScreen.REPERIBILITA
                            }
                            SettingsDivider(Modifier.padding(horizontal = 4.dp))
                            val alarms = ShiftRepository.smartAlarmsConfig
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                PreferenceRow(
                                    pastel = ExpressivePastel.MAUVE,
                                    icon = "alarm-clock",
                                    title = "Regole e prossime sveglie",
                                    subtitle = "Configura gli avvisi sonori legati ai turni",
                                    externalProgress = bellMotion.value,
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

                        SectionLabel(
                            text = "Importazione e condivisione",
                            accent = ExpressivePastel.SAGE,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        PreferenceGroup {
                            PreferenceRow(
                                pastel = ExpressivePastel.SKY,
                                icon = "scan-text",
                                title = "Importa turni con AI",
                                subtitle = "Crea modelli e rotazioni da un prospetto",
                            ) {
                                screen = SettingsSubScreen.IMPORT_SHIFTS
                            }
                            SettingsDivider(Modifier.padding(horizontal = 4.dp))
                            PreferenceRow(
                                pastel = ExpressivePastel.CORAL,
                                icon = "file-text",
                                title = "Esporta e stampa PDF",
                                subtitle = "Calendario o elenco dei turni",
                            ) {
                                screen = SettingsSubScreen.PDF_EXPORT
                            }
                            SettingsDivider(Modifier.padding(horizontal = 4.dp))
                            PreferenceRow(
                                pastel = ExpressivePastel.SAGE,
                                icon = "calendar-sync",
                                title = "Google Calendar",
                                subtitle = "Sincronizza i turni come eventi",
                            ) {
                                screen = SettingsSubScreen.GOOGLE_CALENDAR_SYNC
                            }
                        }

                        SectionLabel(
                            text = "Dati dell’app",
                            accent = ExpressivePastel.LAVENDER,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        PreferenceGroup {
                            PreferenceRow(
                                pastel = ExpressivePastel.LAVENDER,
                                icon = "database-backup",
                                title = "Backup e ripristino",
                                subtitle = "Salva o recupera tutti i dati dell’app",
                            ) {
                                screen = SettingsSubScreen.CLOUD_BACKUP
                            }
                            SettingsDivider(Modifier.padding(horizontal = 4.dp))
                            PreferenceRow(
                                pastel = null,
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
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
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
private fun PreferenceHeading(title: String, onBack: () -> Unit) {
    val activeWork = ShiftRepository.activeWorkProfile
    val hasMultipleProfiles = ShiftRepository.workProfiles.size > 1
    var workMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 18.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TonalIconButton(
            icon = "arrow-left",
            contentDescription = "Indietro",
            onClick = onBack,
            size = 40.dp,
            cornerRadius = 14.dp,
            fill = ObsidianSurface,
            tint = TextPrimary,
            iconSize = 20.dp,
        )
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.6).sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.widthIn(max = 136.dp)) {
            val pillShape = RoundedCornerShape(14.dp)
            val workIcon = activeWork.iconKey.takeIf { it.isNotBlank() } ?: "briefcase"
            val pillModifier = if (hasMultipleProfiles) {
                Modifier
                    .clip(pillShape)
                    .bouncyClickable { workMenuOpen = true }
            } else {
                Modifier
            }
            Surface(
                shape = pillShape,
                color = ObsidianSurface,
                modifier = pillModifier,
            ) {
                Row(
                    modifier = Modifier.padding(start = 5.dp, end = if (hasMultipleProfiles) 8.dp else 10.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PastelIconBadge(
                        pastel = ExpressivePastel.SKY,
                        icon = workIcon,
                        size = 24.dp,
                        iconSize = 13.dp,
                    )
                    Text(
                        text = activeWork.name,
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (hasMultipleProfiles) {
                        SiteIcon(
                            name = "chevron-down",
                            contentDescription = "Cambia calendario di lavoro",
                            modifier = Modifier.size(12.dp),
                            tint = TextSecondary,
                        )
                    }
                }
            }
            if (hasMultipleProfiles) {
                SiteWorkMenu(
                    open = workMenuOpen,
                    onDismiss = { workMenuOpen = false },
                    onChoose = { id ->
                        ShiftRepository.setActiveWorkProfile(id)
                        workMenuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PreferenceGroup(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        shape = RoundedCornerShape(23.dp),
        color = ObsidianSurface,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            content()
        }
    }
}

@Composable
private fun PreferenceRow(
    pastel: ExpressivePastel?,
    icon: String,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    statusPill: String? = null,
    statusPastel: ExpressivePastel? = pastel,
    externalProgress: Float = 0f,
    showChevron: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = currentTurniPalette
    val scope = rememberCoroutineScope()
    val motion = remember { Animatable(0f) }
    val iconPhase = maxOf(motion.value, externalProgress)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(14.dp))
            .bouncyClickable {
                scope.launch {
                    motion.snapTo(0f)
                    motion.animateTo(1f, tween(520))
                    motion.snapTo(0f)
                }
                onClick()
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (danger || pastel == null) {
            val tileFill = Color(palette.danger.fill)
            val tint = Color(palette.danger.ink)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tileFill),
                contentAlignment = Alignment.Center,
            ) {
                SiteIconMotion(icon, null, Modifier.size(19.dp), tint = tint) { iconPhase }
            }
        } else {
            PastelIconBadge(
                pastel = pastel,
                icon = icon,
                size = 38.dp,
                iconSize = 19.dp,
                progress = iconPhase,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (danger) Color(palette.danger.ink) else TextPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 2.dp),
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!statusPill.isNullOrBlank() && statusPastel != null) {
            Spacer(Modifier.width(8.dp))
            PastelPill(
                text = statusPill,
                color = statusPastel.sourceColor,
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            SiteIcon(
                name = "chevron-right",
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (danger) Color(palette.danger.ink).copy(alpha = 0.72f) else TextSecondary,
            )
        }
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 3.dp),
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = TextSecondary,
            )
        }
        TurniSwitch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
private fun PreferenceDeleteDialog(
    step: Int,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
) {
    var acknowledged by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val palette = currentTurniPalette
    val canContinue = step == 1 || acknowledged
    val targetActionFill = if (canContinue) Color(palette.destructive) else ObsidianSurfaceHigh
    val targetActionInk = if (canContinue) Color(palette.onDestructive) else TextMuted
    val actionFill by animateColorAsState(targetActionFill, tween(180), label = "deleteActionFill")
    val actionInk by animateColorAsState(targetActionInk, tween(180), label = "deleteActionInk")

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
                shape = RoundedCornerShape(26.dp),
                color = ObsidianSurface,
                shadowElevation = 18.dp,
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
                            SiteIcon("trash-2", null, Modifier.size(24.dp), tint = Color(palette.danger.ink))
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianSurfaceContainer)
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "Passaggio $step di 2",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                            )
                        }
                        TonalIconButton(
                            icon = "x",
                            contentDescription = "Chiudi",
                            onClick = { dismiss() },
                            size = 36.dp,
                            cornerRadius = 12.dp,
                            fill = ObsidianSurfaceContainer,
                            tint = ColorNegative,
                            iconSize = 18.dp,
                        )
                    }
                    Text(
                        text = if (step == 1) "Eliminare tutti i turni?" else "Conferma eliminazione",
                        modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        letterSpacing = (-0.5).sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Sei sicuro di voler eliminare definitivamente tutti i turni? Questa azione è irreversibile.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = TextPrimary,
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        Text(
                            text = "Tutti i calendari\nI modelli di turno e i lavori restano disponibili.",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = TextSecondary,
                        )
                    }
                    if (step == 2) {
                        val targetAckFill = if (acknowledged) Color(palette.danger.fill) else ObsidianSurfaceContainer
                        val ackFill by animateColorAsState(targetAckFill, tween(180), label = "ackTileFill")
                        val boxFill by animateColorAsState(
                            targetValue = if (acknowledged) Color(palette.destructive) else ObsidianSurfaceHighest,
                            animationSpec = tween(180),
                            label = "ackBoxFill",
                        )
                        val ackInk = if (acknowledged) {
                            readableInk(targetAckFill, Color(palette.danger.ink))
                        } else {
                            TextPrimary
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ackFill)
                                .toggleable(
                                    value = acknowledged,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Checkbox,
                                    onValueChange = { next ->
                                        try {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } catch (_: Exception) {
                                        }
                                        acknowledged = next
                                    },
                                )
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(boxFill),
                                contentAlignment = Alignment.Center,
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = acknowledged,
                                    enter = fadeIn(tween(150)) + scaleIn(tween(180), initialScale = 0.6f),
                                    exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f),
                                ) {
                                    SiteIcon(
                                        name = "check",
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(palette.onDestructive),
                                    )
                                }
                            }
                            Text(
                                text = "Ho capito che i turni e i relativi dettagli verranno eliminati.",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                fontWeight = if (acknowledged) FontWeight.SemiBold else FontWeight.Medium,
                                color = ackInk,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .height(48.dp)
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
                            fontSize = 13.sp,
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
                            .background(ObsidianSurfaceContainer)
                            .bouncyClickable { dismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Annulla", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettingsContent() {
    val prefs = ShiftRepository.appPreferences

    AppearanceLivePreviewCard()

    CloudCard(
        title = "Tema",
        subtitle = "Adatta superfici e contrasto al tuo dispositivo.",
        accent = ExpressivePastel.SKY,
    ) {
        PreferenceThemeSelector()
        SettingsDivider()
        PreferencePaletteSelector()
    }

    CloudCard(
        title = "Layout e formato",
        subtitle = "Inizio settimana, formato orario ed ex turni.",
        accent = ExpressivePastel.MINT,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Inizio settimana",
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                PastelPill(
                    text = if (prefs.weekStartMonday) "Lun – Dom" else "Dom – Sab",
                    color = ExpressivePastel.MINT.sourceColor,
                )
            }
            SlidingSegmentBar(
                items = listOf(
                    "Lunedì" to null,
                    "Domenica" to null,
                ),
                selectedIndex = if (prefs.weekStartMonday) 0 else 1,
                onSelect = { index ->
                    ShiftRepository.appPreferences = prefs.copy(weekStartMonday = index == 0)
                    ShiftRepository.notifyDataChanged()
                },
                height = 44.dp,
            )
            Text(
                text = if (prefs.weekStartMonday) {
                    "Le colonne iniziano dal lunedì (L M M G V S D)."
                } else {
                    "Le colonne iniziano dalla domenica (D L M M G V S)."
                },
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
            )
        }

        SettingsDivider()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Formato orario",
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                PastelPill(
                    text = if (prefs.timeFormat24h) "14:00" else "2:00 PM",
                    color = ExpressivePastel.SKY.sourceColor,
                )
            }
            SlidingSegmentBar(
                items = listOf(
                    "24 ore" to null,
                    "12 ore" to null,
                ),
                selectedIndex = if (prefs.timeFormat24h) 0 else 1,
                onSelect = { index ->
                    ShiftRepository.appPreferences = prefs.copy(timeFormat24h = index == 0)
                    ShiftRepository.notifyDataChanged()
                },
                height = 44.dp,
            )
            Text(
                text = if (prefs.timeFormat24h) {
                    "Formato 24 ore attivo (es. 06:00 · 14:00)."
                } else {
                    "Formato 12 ore attivo (es. 6:00 AM · 2:00 PM)."
                },
                color = TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
            )
        }

        SettingsDivider()

        PreferenceToggle(
            title = "Mostra turno di provenienza",
            subtitle = "Mostra «ex turno» quando sostituisci un turno inserito da una rotazione. La gomma rimuove anche questa provenienza.",
            checked = prefs.showExShifts,
        ) {
            ShiftRepository.appPreferences = prefs.copy(showExShifts = it)
            ShiftRepository.notifyDataChanged()
        }
    }
}

private data class PreviewDayCell(
    val weekdayLetter: String,
    val dayNumber: String,
    val isWeekend: Boolean,
    val isToday: Boolean,
    val shiftCode: String,
    val shiftColor: Color,
    val definitionId: String?,
    val time24h: String?,
    val exShiftCode: String? = null,
)

@Composable
private fun AppearanceLivePreviewCard() {
    val prefs = ShiftRepository.appPreferences
    val density = LocalDensity.current
    val previewDays = remember(prefs.weekStartMonday) {
        val monToSat = listOf(
            PreviewDayCell("L", "12", false, false, "M", ColorMattina, "mattina", "06:00"),
            PreviewDayCell("M", "13", false, true, "M", ColorMattina, "mattina", "06:00"),
            PreviewDayCell("M", "14", false, false, "P", ColorPomeriggio, "pomeriggio", "14:00", exShiftCode = "M"),
            PreviewDayCell("G", "15", false, false, "N", ColorNotte, "notte", "22:00"),
            PreviewDayCell("V", "16", false, false, "M", ColorMattina, "mattina", "06:00"),
            PreviewDayCell("S", "17", true, false, "R", ColorRiposo, "riposo", null),
        )
        if (prefs.weekStartMonday) {
            monToSat + PreviewDayCell("D", "18", true, false, "R", ColorRiposo, "riposo", null)
        } else {
            listOf(PreviewDayCell("D", "11", true, false, "R", ColorRiposo, "riposo", null)) + monToSat
        }
    }

    CloudCard(
        title = "",
        modifier = Modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Anteprima calendario",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Settimana, orario ed ex turno in tempo reale",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            PastelPill(
                text = "${if (prefs.weekStartMonday) "Lun–Dom" else "Dom–Sab"} · ${if (prefs.timeFormat24h) "24h" else "12h"}",
                color = ExpressivePastel.SKY.sourceColor,
            )
        }

        // Recessed frame separates CalendarSurface from the enclosing ObsidianSurface card in both themes.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ObsidianBackground)
                .padding(5.dp),
        ) {
            // Mirror the canvas grid's compact width scaling inside this miniature graphic.
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1.1f)),
            ) {
                AnimatedContent(
                    targetState = previewDays,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                    },
                    label = "miniCalendarWeekOrder",
                ) { week ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CalendarSurface),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            week.forEach { day ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(if (day.isWeekend) CalendarWeekend else CalendarSurface),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = day.weekdayLetter,
                                        color = TextSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.6.dp)
                                .background(ObsidianBorderSubtle.copy(alpha = 0.45f)),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                        ) {
                            week.forEach { day ->
                                MiniCalendarCell(
                                    day = day,
                                    timeFormat24h = prefs.timeFormat24h,
                                    showExShifts = prefs.showExShifts,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniCalendarCell(
    day: PreviewDayCell,
    timeFormat24h: Boolean,
    showExShifts: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = TurniThemeState.dark
    val themeStyle = TurniThemeState.style
    val cellFill by animateColorAsState(
        targetValue = when {
            day.isToday -> CalendarToday
            day.isWeekend -> CalendarWeekend
            else -> CalendarSurface
        },
        animationSpec = tween(200),
        label = "miniCellFill",
    )
    val style = remember(day.shiftColor, dark, themeStyle, cellFill, day.definitionId) {
        shiftColorStyle(day.shiftColor, dark, cellFill, definitionId = day.definitionId)
    }
    val badgeBrush = remember(style.codeFill, style.codeInk) {
        pastelSheenBrush(style.codeFill, style.codeInk)
    }

    Column(
        modifier = modifier
            .background(cellFill)
            .padding(top = 5.dp, bottom = 5.dp, start = 1.5.dp, end = 1.5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(19.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (day.isToday) ObsidianPrimary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayNumber,
                color = if (day.isToday) OnBrand else TextPrimary,
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(badgeBrush)
                .padding(horizontal = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = day.shiftCode,
                color = style.codeInk,
                fontSize = 10.5.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
            AnimatedContent(
                targetState = timeFormat24h,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
                label = "miniCellTimeFormat",
            ) { use24h ->
                if (day.time24h == null) {
                    Text(
                        text = "– –",
                        color = style.codeInk.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                } else {
                    MiniTimeLabel(
                        value24h = day.time24h,
                        use24h = use24h,
                        color = style.codeInk,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (day.exShiftCode != null) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showExShifts,
                    enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(ObsidianSurfaceContainer)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "ex ${day.exShiftCode}",
                            color = TextSecondary,
                            fontSize = 7.5.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTimeLabel(value24h: String, use24h: Boolean, color: Color) {
    val label = remember(value24h, use24h) { calendarStartLabel(value24h, use24h) }
    val isMeridiem = label.endsWith(" AM") || label.endsWith(" PM")
    if (isMeridiem) {
        val timePart = label.dropLast(3)
        val meridiemPart = label.takeLast(2)
        val isTwoDigitHour = timePart.length >= 5
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isTwoDigitHour) 0.5.dp else 1.dp),
        ) {
            Text(
                text = timePart,
                color = color,
                fontSize = if (isTwoDigitHour) 7.2.sp else 8.sp,
                lineHeight = 10.sp,
                letterSpacing = if (isTwoDigitHour) (-0.3).sp else (-0.2).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = meridiemPart,
                color = color.copy(alpha = 0.88f),
                fontSize = if (isTwoDigitHour) 5.4.sp else 6.sp,
                lineHeight = 8.sp,
                letterSpacing = (-0.2).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    } else {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
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
    val dark = TurniThemeState.dark
    val fontScale = LocalDensity.current.fontScale
    val ribbonPastels = remember {
        listOf(
            ExpressivePastel.AMBER,
            ExpressivePastel.CORAL,
            ExpressivePastel.LAVENDER,
            ExpressivePastel.MINT,
            ExpressivePastel.SKY,
        )
    }

    Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (fontScale > 1.3f) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaletteIdentityLabel(
                        graphiteColor = Color(palette.calendar),
                        graphiteOutline = Color(palette.outline).copy(alpha = 0.55f),
                        cobaltColor = Color(palette.dockSelected),
                    )
                    PastelPaletteRibbon(ribbonPastels, Color(palette.dockSelected), dark)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PaletteIdentityLabel(
                        graphiteColor = Color(palette.calendar),
                        graphiteOutline = Color(palette.outline).copy(alpha = 0.55f),
                        cobaltColor = Color(palette.dockSelected),
                    )
                    PastelPaletteRibbon(ribbonPastels, Color(palette.dockSelected), dark)
                }
            }
        }
        Text(
            text = "La stessa palette si adatta al tema chiaro o scuro. I colori personalizzati dei tuoi turni restano disponibili.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun PaletteIdentityLabel(
    graphiteColor: Color,
    graphiteOutline: Color,
    cobaltColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(graphiteColor)
                .border(1.dp, graphiteOutline, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(cobaltColor),
        )
        Text(
            text = "Grafite e cobalto",
            color = TextPrimary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PastelPaletteRibbon(
    pastels: List<ExpressivePastel>,
    cobaltColor: Color,
    dark: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pastels.forEach { pastel ->
            val style = remember(pastel, dark) { pastel.style(dark = dark, backdrop = ObsidianSurfaceContainer) }
            val brush = remember(style.codeFill, style.codeInk) {
                pastelSheenBrush(style.codeFill, style.codeInk)
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(brush),
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(cobaltColor),
        )
    }
}
