@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.CalendarCooldownPolicy
import com.turni.calendario.sync.GoogleCalendarCooldown
import com.turni.calendario.sync.GoogleCalendarRemote
import com.turni.calendario.sync.RemoteCalendarInfo
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Only fresh Google authorization and explicitly selected writable calendars are used. */
@Composable
fun GoogleCalendarSyncScreen(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val access = rememberGoogleAccess { feedback = ActionFeedback(it, true) }
    var apiBusy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("") }
    val busy = apiBusy || access.busy
    val email = ShiftRepository.googleAccountProfile?.email
    var cooldown by remember { mutableStateOf(GoogleCalendarCooldown.remaining(context,email)) }
    LaunchedEffect(email) {
        while(true){cooldown=GoogleCalendarCooldown.remaining(context,email);delay(1000)}
    }
    val canChange = !busy && cooldown==0L
    var calendarOwner by remember { mutableStateOf<String?>(null) }
    var selectedCalendar by remember { mutableStateOf<RemoteCalendarInfo?>(null) }
    var calendars by remember { mutableStateOf(emptyList<RemoteCalendarInfo>()) }
    var picker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(email) {
        selectedCalendar = null; calendarOwner = null; calendars = emptyList()
        picker = false; confirmDelete = false; confirmEmpty = false
    }
    val today = remember { LocalDate.now() }
    var range by remember { mutableStateOf(0) }
    var exactHours by remember { mutableStateOf(true) }
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderMinutes by remember { mutableStateOf(60) }
    var includeRest by remember { mutableStateOf(false) }
    val start = if (range == 2) today.withDayOfYear(1) else today.withDayOfMonth(1)
    val end = when (range) { 1 -> start.plusMonths(3).minusDays(1); 2 -> start.plusYears(1).minusDays(1); else -> start.plusMonths(1).minusDays(1) }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN) }
    val work = ShiftRepository.activeWorkProfile
    val shiftCount by remember(start,end,work.id,includeRest){derivedStateOf{
        val selectedIds=ShiftRepository.shiftDefinitions.filter{it.workProfileId==work.id&&(includeRest||it.category!=ShiftCategory.RIPOSO)}.map{it.id}.toSet()
        ShiftRepository.calendarShifts.entries.filter { it.key >= start && it.key <= end }.sumOf { entry -> entry.value.count { it.shiftDefId in selectedIds } }
    }}

    fun chooseCalendar() {
        if (apiBusy || access.busy) return
        access.authorize(GoogleFeature.CALENDAR) { token ->
            val owner = ShiftRepository.googleAccountProfile?.email
            apiBusy = true; operation = "Caricamento dei calendari…"
            scope.launch {
                try {
                    val available = withContext(Dispatchers.IO) { GoogleCalendarRemote.calendars(token) }
                    if (ShiftRepository.googleAccountProfile?.email == owner) {
                        calendars = available; calendarOwner = owner; picker = available.isNotEmpty()
                        if (available.isEmpty()) feedback = ActionFeedback("Questo account non contiene calendari con permesso di scrittura.", true)
                    }
                } catch (error: CancellationException) { throw error
                } catch (error: Exception) { feedback = ActionFeedback(error.message ?: "Impossibile caricare i calendari Google.", true)
                } finally { apiBusy = false; operation = "" }
            }
        }
    }
    fun synchronize() {
        if (apiBusy || access.busy) return
        val selected = selectedCalendar ?: return
        val owner = calendarOwner
        access.authorize(GoogleFeature.CALENDAR) { token ->
            if (owner == null || ShiftRepository.googleAccountProfile?.email != owner) {
                selectedCalendar = null; feedback = ActionFeedback("L'account è cambiato. Scegli di nuovo il calendario Google.", true)
            } else {
                if(!GoogleCalendarCooldown.tryStart(context,owner)){
                    cooldown=GoogleCalendarCooldown.remaining(context,owner)
                    feedback=ActionFeedback("Attendi prima di avviare un’altra operazione su Calendar.",true)
                    return@authorize
                }
                cooldown=CalendarCooldownPolicy.DURATION_MILLIS
                // Snapshot repository state on main, then perform all network work on IO.
                val workId = ShiftRepository.activeWorkProfileId
                val models = ShiftRepository.shiftDefinitions.filter { it.workProfileId == workId }.toList()
                val modelIds = models.map { it.id }.toSet()
                val shifts = ShiftRepository.calendarShifts.entries.filter { it.key >= start && it.key <= end }
                    .flatMap { entry -> entry.value.toList().filter { it.shiftDefId in modelIds }.map { entry.key to it } }
                val syncStart = start; val syncEnd = end; val hours = exactHours; val rest = includeRest
                val reminder = if (reminderEnabled) reminderMinutes else null
                apiBusy = true; operation = "Sincronizzazione in corso…"
                scope.launch {
                    try {
                        val count = withContext(Dispatchers.IO) { GoogleCalendarRemote.sync(token, selected.id, shifts, models, syncStart, syncEnd, hours, reminder, rest, workId) }
                        feedback = ActionFeedback(if (count == 1) "Un turno sincronizzato su Google Calendar" else "$count turni sincronizzati su Google Calendar")
                    } catch (error: CancellationException) { throw error
                    } catch (error: Exception) { feedback = ActionFeedback(error.message ?: "Sincronizzazione Google Calendar non riuscita.", true)
                    } finally { GoogleCalendarCooldown.finished(context,owner);cooldown=GoogleCalendarCooldown.remaining(context,owner);apiBusy = false; operation = "" }
                }
            }
        }
    }
    fun deleteEvents() {
        if (apiBusy || access.busy) return
        val selected = selectedCalendar ?: return
        val owner = calendarOwner
        access.authorize(GoogleFeature.CALENDAR) { token ->
            if (owner == null || ShiftRepository.googleAccountProfile?.email != owner) {
                selectedCalendar = null; feedback = ActionFeedback("L'account è cambiato. Scegli di nuovo il calendario Google.", true)
            } else {
                if(!GoogleCalendarCooldown.tryStart(context,owner)){
                    cooldown=GoogleCalendarCooldown.remaining(context,owner)
                    feedback=ActionFeedback("Attendi prima di avviare un’altra operazione su Calendar.",true)
                    return@authorize
                }
                cooldown=CalendarCooldownPolicy.DURATION_MILLIS
                apiBusy = true; operation = "Rimozione degli eventi TurniSync…"
                scope.launch {
                    try {
                        val count = withContext(Dispatchers.IO) { GoogleCalendarRemote.deleteOwnedEvents(token, selected.id) }
                        feedback = ActionFeedback(if (count == 1) "Un evento TurniSync rimosso" else "$count eventi TurniSync rimossi", true)
                    } catch (error: CancellationException) { throw error
                    } catch (error: Exception) { feedback = ActionFeedback(error.message ?: "Impossibile rimuovere gli eventi TurniSync.", true)
                    } finally { GoogleCalendarCooldown.finished(context,owner);cooldown=GoogleCalendarCooldown.remaining(context,owner);apiBusy = false; operation = "" }
                }
            }
        }
    }

    val dark = TurniThemeState.dark
    val sageStyle = remember(dark) { ExpressivePastel.SAGE.style(dark, ObsidianSurface) }
    val sageAccent = remember(dark) { shiftChartColor(ExpressivePastel.SAGE.sourceColor, dark) }

    CloudFeatureScaffold(
        title = "Google Calendar",
        subtitle = "Sincronizza i turni di ${work.name} sul tuo calendario",
        onDismiss = onDismiss,
        feedback = feedback,
        onDismissFeedback = { feedback = null },
        headerAction = {
            PastelIconBadge(
                pastel = ExpressivePastel.SAGE,
                icon = "calendar-sync",
                size = 38.dp,
                iconSize = 19.dp,
            )
        },
    ) {
        GoogleAccountCard(access, { feedback = it }, enabled = !apiBusy)
        CloudCard(
            title = "Calendario di destinazione",
            subtitle = "Scegli dove sincronizzare i turni di ${work.name}.",
            accent = ExpressivePastel.SAGE,
        ) {
            val calShape = RoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(calShape)
                    .background(ObsidianSurfaceContainer)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !busy,
                    ) { chooseCalendar() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PastelIconBadge(
                    pastel = ExpressivePastel.SAGE,
                    icon = "calendar-sync",
                    size = 40.dp,
                    iconSize = 20.dp,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = selectedCalendar?.name ?: "Scegli calendario",
                        color = TextPrimary.copy(alpha = if (!busy) 1f else 0.5f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (selectedCalendar != null) {
                            calendarOwner.orEmpty()
                        } else {
                            "Seleziona un calendario Google scrivibile"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PastelPill(
                    text = if (selectedCalendar == null) "Scegli" else "Cambia",
                    color = ExpressivePastel.SAGE.sourceColor,
                )
                SiteIcon("chevron-right", null, Modifier.size(16.dp), TextSecondary)
            }
        }
        CloudCard(
            title = "Periodo",
            subtitle = "${start.format(formatter)} – ${end.format(formatter)}",
            accent = ExpressivePastel.SAGE,
        ) {
            CalendarOptionTabs(
                options = listOf(
                    "Mese" to "calendar",
                    "3 mesi" to "layers",
                    "Anno" to "clock",
                ),
                selected = range,
                enabled = !busy,
                onSelect = { range = it },
            )
            PastelPill(
                text = "$shiftCount ${if (shiftCount == 1) "turno" else "turni"} · ${work.name}",
                color = ExpressivePastel.SAGE.sourceColor,
                icon = "calendar",
            )
        }
        CloudCard("Opzioni degli eventi") {
            CalendarOptionSwitch(
                title = "Orari precisi",
                subtitle = "Usa attacco, stacco effettivo e straordinario.",
                value = exactHours,
                enabled = !busy,
                onChange = { exactHours = it },
            )
            SettingsDivider()
            CalendarOptionSwitch(
                title = "Includi i riposi",
                subtitle = "Mostra anche i turni della categoria Riposo.",
                value = includeRest,
                enabled = !busy,
                onChange = { includeRest = it },
            )
            SettingsDivider()
            CalendarOptionSwitch(
                title = "Promemoria",
                subtitle = "Aggiunge un avviso nel calendario Google.",
                value = reminderEnabled,
                enabled = !busy,
                onChange = { reminderEnabled = it },
            )
            val reminderDurations = remember { listOf(15, 30, 60) }
            AnimatedVisibility(
                visible = reminderEnabled,
                enter = expandVertically(tween(260)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(180)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsDivider()
                    Text("Anticipo avviso", fontSize = 12.sp, color = TextSecondary)
                    val activeBg = remember(sageStyle, dark) {
                        lerp(ObsidianSurfaceContainer, sageStyle.codeFill, if (dark) 0.26f else 0.44f)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        reminderDurations.forEach { minutes ->
                            val selected = reminderMinutes == minutes
                            val pillShape = RoundedCornerShape(14.dp)
                            val targetBg = if (selected) activeBg else ObsidianSurfaceContainer
                            val targetInk = if (selected) {
                                readableInk(activeBg, if (dark) sageStyle.codeFill else sageStyle.codeInk)
                            } else {
                                TextSecondary
                            }
                            val bg by animateColorAsState(
                                targetValue = targetBg,
                                animationSpec = tween(180),
                                label = "reminderPillBg",
                            )
                            val ink by animateColorAsState(
                                targetValue = targetInk.copy(alpha = if (!busy) 1f else 0.5f),
                                animationSpec = tween(180),
                                label = "reminderPillInk",
                            )
                            val ring by animateColorAsState(
                                targetValue = if (selected) sageAccent else Color.Transparent,
                                animationSpec = tween(180),
                                label = "reminderPillRing",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(pillShape)
                                    .background(bg)
                                    .then(if (selected) Modifier.border(2.dp, ring, pillShape) else Modifier)
                                    .clickable(
                                        interactionSource = remember(minutes) { MutableInteractionSource() },
                                        indication = null,
                                        enabled = !busy,
                                    ) { reminderMinutes = minutes },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (minutes == 60) "1 ora" else "$minutes min",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        CloudButton(
            label = if (apiBusy) operation else if (cooldown > 0) "Attendi ${CalendarCooldownPolicy.label(cooldown)}" else "Sincronizza ora",
            icon = "upload",
            onClick = { if (shiftCount == 0) confirmEmpty = true else synchronize() },
            enabled = canChange && selectedCalendar != null,
        )
        AnimatedVisibility(
            visible = cooldown > 0,
            enter = expandVertically(tween(240)) + fadeIn(),
            exit = shrinkVertically(tween(200)) + fadeOut(),
        ) {
            val targetProgress = (1f - cooldown.toFloat() / CalendarCooldownPolicy.DURATION_MILLIS).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                label = "calendarCooldownProgress",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(ObsidianSurfaceHigh),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(sageAccent, ObsidianPrimary))),
                    )
                }
                Text(
                    text = "Google può impiegare qualche minuto ad aggiornare gli eventi. Dopo ogni sincronizzazione o rimozione attendi 3 minuti prima della prossima operazione.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = TextSecondary,
                )
            }
        }
        Text(
            text = "La sincronizzazione aggiorna gli eventi TurniSync del lavoro e del periodo scelti. Gli eventi personali restano invariati.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = TextSecondary,
        )
        CloudCard(
            title = "Gestione degli eventi",
            subtitle = "Rimuove dal calendario scelto solo gli eventi creati da TurniSync, anche degli altri lavori e periodi. I turni nell'app restano salvati.",
        ) {
            CloudButton(
                label = if (cooldown > 0) "Rimuovi tra ${CalendarCooldownPolicy.label(cooldown)}" else "Rimuovi eventi TurniSync",
                icon = "trash",
                onClick = { confirmDelete = true },
                enabled = canChange && selectedCalendar != null,
                destructive = true,
            )
        }
    }
    if (picker) {
        val palette = currentTurniPalette
        Dialog(
            onDismissRequest = { picker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = ObsidianSurface,
                    tonalElevation = 0.dp,
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Scegli calendario", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(calendarOwner.orEmpty(), fontSize = 12.sp, lineHeight = 16.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            TonalIconButton(
                                icon = "x",
                                contentDescription = "Chiudi",
                                onClick = { picker = false },
                                size = 36.dp,
                                cornerRadius = 12.dp,
                                fill = Color(palette.danger.fill),
                                tint = Color(palette.danger.ink),
                            )
                        }
                        val pickerSelectedBg = remember(sageStyle, dark) {
                            lerp(ObsidianSurfaceContainer, sageStyle.codeFill, if (dark) 0.20f else 0.34f)
                        }
                        Column(
                            modifier = Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            calendars.forEach { calendar ->
                                val selected = selectedCalendar?.id == calendar.id
                                val itemShape = RoundedCornerShape(15.dp)
                                val rowBg by animateColorAsState(
                                    targetValue = if (selected) pickerSelectedBg else ObsidianSurfaceContainer,
                                    animationSpec = tween(180),
                                    label = "calendarRowBg",
                                )
                                val rowRing by animateColorAsState(
                                    targetValue = if (selected) sageAccent else Color.Transparent,
                                    animationSpec = tween(180),
                                    label = "calendarRowRing",
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(itemShape)
                                        .background(rowBg)
                                        .then(if (selected) Modifier.border(2.dp, rowRing, itemShape) else Modifier)
                                        .clickable(
                                            interactionSource = remember(calendar.id) { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            selectedCalendar = calendar
                                            picker = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    PastelIconBadge(
                                        pastel = ExpressivePastel.SAGE,
                                        icon = "calendar",
                                        size = 38.dp,
                                        iconSize = 18.dp,
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(calendar.name, color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                                        if (calendar.primary) Text("Calendario principale", fontSize = 11.sp, lineHeight = 15.sp, color = TextSecondary)
                                    }
                                    if (selected) SiteIcon("check", tint = sageAccent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        CloudButton("Annulla", "x", { picker = false }, secondary = true)
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = ObsidianSurface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(26.dp),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Rimuovere gli eventi TurniSync?", color = TextPrimary) },
            text = {
                Text(
                    text = "Verranno rimossi dal calendario «${selectedCalendar?.name.orEmpty()}» tutti gli eventi creati da TurniSync. Gli eventi personali e i turni salvati nell'app non saranno eliminati.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        deleteEvents()
                    },
                    enabled = canChange,
                    colors = ButtonDefaults.textButtonColors(contentColor = ColorNegative),
                ) {
                    Text("Rimuovi eventi", color = ColorNegative, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                ) {
                    Text("Annulla", color = TextSecondary)
                }
            },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            containerColor = ObsidianSurface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(26.dp),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Nessun turno nel periodo", color = TextPrimary) },
            text = {
                Text(
                    text = "La sincronizzazione rimuoverà eventuali eventi TurniSync di questo lavoro nel periodo selezionato. Vuoi continuare?",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEmpty = false
                        synchronize()
                    },
                    enabled = canChange,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary, contentColor = OnBrand),
                ) {
                    Text("Aggiorna calendario", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmEmpty = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                ) {
                    Text("Annulla", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun CalendarOptionSwitch(title: String, subtitle: String, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) { onChange(!value) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, lineHeight = 19.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = TextSecondary)
        }
        TurniSwitch(checked = value, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun CalendarOptionTabs(
    options: List<Pair<String, String?>>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    if (options.size <= 4) {
        SlidingSegmentBar(
            items = options,
            selectedIndex = selected,
            onSelect = onSelect,
            enabled = enabled,
        )
    } else {
        val trackShape = RoundedCornerShape(16.dp)
        val pillShape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(trackShape)
                .background(ObsidianSurfaceContainer)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, (label, iconKey) ->
                val isSelected = selected == index
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) ObsidianPrimary else ObsidianSurfaceContainer,
                    animationSpec = tween(180),
                    label = "calendarOptionBg",
                )
                val inkColor by animateColorAsState(
                    targetValue = (if (isSelected) OnBrand else TextSecondary).copy(alpha = if (enabled) 1f else 0.5f),
                    animationSpec = tween(180),
                    label = "calendarOptionInk",
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .clip(pillShape)
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember(index) { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled,
                        ) { onSelect(index) }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (iconKey != null) {
                        SiteIcon(iconKey, null, Modifier.size(14.dp), inkColor)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = inkColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
