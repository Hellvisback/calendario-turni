@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.Text
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
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.CalendarCooldownPolicy
import com.turni.calendario.sync.GoogleCalendarCooldown
import com.turni.calendario.sync.GoogleCalendarRemote
import com.turni.calendario.sync.RemoteCalendarInfo
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    var showAccountSheetForCalendar by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(email) {
        if (email == null || calendarOwner != email) {
            selectedCalendar = null; calendarOwner = null; calendars = emptyList()
            picker = false; confirmDelete = false; confirmEmpty = false
        }
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

    fun loadCalendars(token: String) {
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
    fun chooseCalendar() {
        if (apiBusy || access.busy) return
        if (ShiftRepository.googleAccountProfile == null) {
            showAccountSheetForCalendar = true
            return
        }
        access.authorize(GoogleFeature.CALENDAR) { token ->
            loadCalendars(token)
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
    val palette = currentTurniPalette
    val headerSyncMotion = remember { Animatable(0f) }
    var wasApiBusy by remember { mutableStateOf(false) }
    LaunchedEffect(apiBusy) {
        if (apiBusy) {
            wasApiBusy = true
            while (true) {
                headerSyncMotion.snapTo(0f)
                headerSyncMotion.animateTo(1f, tween(580, easing = FastOutSlowInEasing))
                headerSyncMotion.snapTo(0f)
                delay(420L)
            }
        } else if (wasApiBusy) {
            wasApiBusy = false
            headerSyncMotion.snapTo(0f)
        }
    }
    val calendarRowMotion = remember { Animatable(0f) }
    var calendarRowJob by remember { mutableStateOf<Job?>(null) }
    val periodMotion = remember { Animatable(0f) }
    var periodFirst by remember { mutableStateOf(true) }
    LaunchedEffect(range) {
        if (periodFirst) {
            periodFirst = false
        } else {
            periodMotion.snapTo(0f)
            periodMotion.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            periodMotion.snapTo(0f)
        }
    }

    CloudFeatureScaffold(
        title = "Google Calendar",
        subtitle = null,
        onDismiss = onDismiss,
        feedback = feedback,
        onDismissFeedback = { feedback = null },
        headerAction = {
            NeutralIconBadge(
                icon = "calendar-sync",
                size = 38.dp,
                cornerRadius = 12.dp,
                iconSize = 19.dp,
                fill = if (selectedCalendar != null) ObsidianPrimaryContainer else ObsidianSurface,
                tint = BrandInk,
                progress = headerSyncMotion.value,
            )
        },
    ) {
        GoogleAccountCard(
            access = access,
            onFeedback = { feedback = it },
            enabled = !apiBusy,
            feature = GoogleFeature.CALENDAR,
            explanation = null,
            onConnected = { token -> loadCalendars(token) },
            connectedContent = {
                val calShape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(calShape)
                        .background(ObsidianSurfaceContainer)
                        .bouncyClickable(enabled = !busy) {
                            calendarRowJob = scope.triggerSyncMotion(calendarRowMotion, calendarRowJob)
                            chooseCalendar()
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NeutralIconBadge(
                        icon = "calendar-sync",
                        size = 40.dp,
                        cornerRadius = 12.dp,
                        iconSize = 20.dp,
                        fill = if (selectedCalendar != null) ObsidianPrimaryContainer else ObsidianSurface,
                        tint = BrandInk,
                        progress = calendarRowMotion.value,
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
                        val calendarHint = when {
                            selectedCalendar?.primary == true -> "Calendario principale"
                            selectedCalendar == null -> "Seleziona un calendario"
                            else -> null
                        }
                        if (calendarHint != null) {
                            Text(
                                text = calendarHint,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    CalendarStatusPill(
                        text = if (selectedCalendar == null) "Scegli" else "Cambia",
                        fill = ObsidianPrimarySubtle,
                        ink = BrandInk,
                    )
                    SiteIcon("chevron-right", null, Modifier.size(16.dp), TextSecondary)
                }
                SettingsDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Periodo",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${start.format(formatter)} – ${end.format(formatter)}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SlidingSegmentBar(
                        items = listOf(
                            "Mese" to "calendar",
                            "3 mesi" to "layers",
                            "Anno" to "clock",
                        ),
                        selectedIndex = range,
                        onSelect = { range = it },
                        enabled = !busy,
                    )
                    CalendarStatusPill(
                        text = "$shiftCount ${if (shiftCount == 1) "turno" else "turni"} · ${work.name}",
                        fill = ObsidianSurfaceContainer,
                        ink = BrandInk,
                        icon = "calendar",
                        progress = periodMotion.value,
                    )
                }
                SettingsDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CalendarOptionSwitch(
                        title = "Orari precisi",
                        subtitle = "Attacco, stacco e straordinario",
                        value = exactHours,
                        enabled = !busy,
                        onChange = { exactHours = it },
                    )
                    CalendarOptionSwitch(
                        title = "Includi i riposi",
                        subtitle = null,
                        value = includeRest,
                        enabled = !busy,
                        onChange = { includeRest = it },
                    )
                    CalendarOptionSwitch(
                        title = "Promemoria",
                        subtitle = null,
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
                            Text("Anticipo avviso", fontSize = 12.sp, color = TextSecondary)
                            val activeBg = remember(dark) {
                                lerp(ObsidianSurfaceContainer, ObsidianPrimary, if (dark) 0.22f else 0.12f)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                reminderDurations.forEach { minutes ->
                                    val selected = reminderMinutes == minutes
                                    val pillShape = RoundedCornerShape(14.dp)
                                    val targetBg = if (selected) activeBg else ObsidianSurfaceContainer
                                    val targetInk = if (selected) BrandInk else TextSecondary
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
                                        targetValue = if (selected) ObsidianPrimary else Color.Transparent,
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
                SettingsDivider()
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
                                    .background(Brush.horizontalGradient(listOf(BrandInk, ObsidianPrimary))),
                            )
                        }
                        Text(
                            text = "Attendi 3 minuti tra due operazioni.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = TextSecondary,
                        )
                    }
                }
            },
        )
        AnimatedVisibility(
            visible = email != null,
            enter = expandVertically(
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) + fadeIn(tween(220)),
            exit = shrinkVertically(
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) + fadeOut(tween(160)),
        ) {
            CloudCard(
                title = "Eventi sincronizzati",
                subtitle = "Rimuove solo gli eventi creati da TurniSync",
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
    }
    if (picker) {
        TurniSafeBottomDialog(onClose = { picker = false }, scrollContent = false) { finish ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "calendar-sync",
                    size = 38.dp,
                    cornerRadius = 12.dp,
                    iconSize = 18.dp,
                    fill = ObsidianPrimaryContainer,
                    tint = BrandInk,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Scegli calendario", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        text = calendarOwner.orEmpty(),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Chiudi",
                    onClick = { finish { picker = false } },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = Color(palette.danger.fill),
                    tint = Color(palette.danger.ink),
                )
            }
            val pickerSelectedBg = remember(dark) {
                lerp(ObsidianSurfaceContainer, ObsidianPrimary, if (dark) 0.20f else 0.10f)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
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
                        targetValue = if (selected) ObsidianPrimary else Color.Transparent,
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
                                finish {
                                    selectedCalendar = calendar
                                    picker = false
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NeutralIconBadge(
                            icon = "calendar",
                            size = 38.dp,
                            cornerRadius = 12.dp,
                            iconSize = 18.dp,
                            fill = if (selected) ObsidianPrimaryContainer else ObsidianSurface,
                            tint = BrandInk,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(calendar.name, color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                            if (calendar.primary) Text("Calendario principale", fontSize = 11.sp, lineHeight = 15.sp, color = TextSecondary)
                        }
                        if (selected) SiteIcon("check", tint = BrandInk, modifier = Modifier.size(18.dp))
                    }
                }
            }
            CloudButton("Annulla", "x", { finish { picker = false } }, secondary = true)
        }
    }
    if (confirmDelete) {
        TurniSafeBottomDialog(onClose = { confirmDelete = false }) { finish ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "trash",
                    size = 40.dp,
                    cornerRadius = 12.dp,
                    iconSize = 19.dp,
                    fill = Color(palette.danger.fill),
                    tint = Color(palette.danger.ink),
                )
                Text(
                    text = "Rimuovere gli eventi TurniSync?",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Chiudi",
                    onClick = { finish { confirmDelete = false } },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = ObsidianSurfaceContainer,
                    tint = TextSecondary,
                )
            }
            Text(
                text = "Verranno rimossi dal calendario «${selectedCalendar?.name.orEmpty()}» tutti gli eventi creati da TurniSync. Gli eventi personali e i turni salvati nell'app non saranno eliminati.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudButton(
                    label = "Annulla",
                    icon = "x",
                    onClick = { finish { confirmDelete = false } },
                    modifier = Modifier.weight(1f),
                    secondary = true,
                )
                CloudButton(
                    label = "Rimuovi eventi",
                    icon = "trash",
                    onClick = {
                        finish {
                            confirmDelete = false
                            deleteEvents()
                        }
                    },
                    enabled = canChange,
                    destructive = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (confirmEmpty) {
        TurniSafeBottomDialog(onClose = { confirmEmpty = false }) { finish ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "calendar",
                    size = 40.dp,
                    cornerRadius = 12.dp,
                    iconSize = 19.dp,
                    fill = ObsidianPrimaryContainer,
                    tint = BrandInk,
                )
                Text(
                    text = "Nessun turno nel periodo",
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Chiudi",
                    onClick = { finish { confirmEmpty = false } },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = Color(palette.danger.fill),
                    tint = Color(palette.danger.ink),
                )
            }
            Text(
                text = "La sincronizzazione rimuoverà eventuali eventi TurniSync di questo lavoro nel periodo selezionato. Vuoi continuare?",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudButton(
                    label = "Annulla",
                    icon = "x",
                    onClick = { finish { confirmEmpty = false } },
                    modifier = Modifier.weight(1f),
                    secondary = true,
                )
                CloudButton(
                    label = "Aggiorna calendario",
                    icon = "upload",
                    onClick = {
                        finish {
                            confirmEmpty = false
                            synchronize()
                        }
                    },
                    enabled = canChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (showAccountSheetForCalendar) {
        GoogleAccountSheet(
            access = access,
            feature = GoogleFeature.CALENDAR,
            onFeedback = { feedback = it },
            onDismiss = { showAccountSheetForCalendar = false },
            onConnected = { token -> loadCalendars(token) },
        )
    }
}

private fun CoroutineScope.triggerSyncMotion(
    motion: Animatable<Float, *>,
    previous: Job?,
    durationMillis: Int = 520,
): Job {
    previous?.cancel()
    return launch {
        motion.snapTo(0f)
        motion.animateTo(1f, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
        motion.snapTo(0f)
    }
}

@Composable
private fun CalendarStatusPill(
    text: String,
    fill: Color,
    ink: Color,
    icon: String? = null,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            SiteIconMotion(
                name = icon,
                description = null,
                modifier = Modifier.size(12.dp),
                tint = ink,
            ) { progress }
        }
        Text(
            text = text,
            color = ink,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CalendarOptionSwitch(title: String, subtitle: String?, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
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
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, lineHeight = 17.sp, color = TextSecondary)
            }
        }
        TurniSwitch(checked = value, onCheckedChange = onChange, enabled = enabled)
    }
}
