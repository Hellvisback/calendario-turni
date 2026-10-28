@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.sync.GoogleCalendarRemote
import com.turni.calendario.sync.RemoteCalendarInfo
import com.turni.calendario.sync.GoogleCalendarCooldown
import com.turni.calendario.sync.CalendarCooldownPolicy
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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

    CloudFeatureScaffold("Google Calendar", onDismiss, feedback, { feedback = null }) {
        GoogleAccountCard(access, { feedback = it }, enabled = !apiBusy)
        CloudCard("Calendario di destinazione", "Scegli dove sincronizzare i turni di ${work.name}.") {
            selectedCalendar?.let { selected ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SiteIcon("calendar", modifier = Modifier.size(25.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(selected.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(calendarOwner.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            CloudButton(if (selectedCalendar == null) "Scegli calendario" else "Cambia calendario", "calendar", ::chooseCalendar, enabled = !busy)
        }
        CloudCard("Periodo", "${start.format(formatter)} – ${end.format(formatter)}") {
            CalendarOptionTabs(listOf("Mese", "3 mesi", "Anno"), range, !busy) { range = it }
            Spacer(Modifier.height(12.dp))
            Text("$shiftCount ${if (shiftCount == 1) "turno" else "turni"} · ${work.name}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CloudCard("Opzioni degli eventi") {
            CalendarOptionSwitch("Orari precisi", "Usa attacco, stacco effettivo e straordinario.", exactHours, !busy) { exactHours = it }
            CalendarOptionSwitch("Includi i riposi", "Mostra anche i turni della categoria Riposo.", includeRest, !busy) { includeRest = it }
            CalendarOptionSwitch("Promemoria", "Aggiunge un avviso nel calendario Google.", reminderEnabled, !busy) { reminderEnabled = it }
            AnimatedVisibility(reminderEnabled, enter = expandVertically(tween(260)) + fadeIn(tween(220)), exit = shrinkVertically(tween(220)) + fadeOut(tween(180))) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(15, 30, 60).forEach { minutes ->
                        FilterChip(selected = minutes == reminderMinutes, onClick = { reminderMinutes = minutes }, enabled = !busy,
                            label = { Text(if (minutes == 60) "1 ora" else "$minutes min", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        CloudButton(if (apiBusy) operation else if(cooldown>0)"Attendi ${CalendarCooldownPolicy.label(cooldown)}" else "Sincronizza i turni", "upload", {
            if (shiftCount == 0) confirmEmpty = true else synchronize()
        }, enabled = canChange && selectedCalendar != null)
        AnimatedVisibility(cooldown>0,enter=expandVertically(tween(240))+fadeIn(),exit=shrinkVertically(tween(200))+fadeOut()){
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                LinearProgressIndicator(progress=1f-cooldown.toFloat()/CalendarCooldownPolicy.DURATION_MILLIS,modifier=Modifier.fillMaxWidth().height(3.dp))
                Text("Google può impiegare qualche minuto ad aggiornare gli eventi. Dopo ogni sincronizzazione o rimozione attendi 3 minuti prima della prossima operazione.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("La sincronizzazione aggiorna gli eventi TurniSync del lavoro e del periodo scelti. Gli eventi personali restano invariati.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CloudCard("Gestione degli eventi", "Rimuove dal calendario scelto solo gli eventi creati da TurniSync, anche degli altri lavori e periodi. I turni nell'app restano salvati.") {
            CloudButton(if(cooldown>0)"Rimuovi tra ${CalendarCooldownPolicy.label(cooldown)}" else "Rimuovi eventi TurniSync", "trash", { confirmDelete = true }, enabled = canChange && selectedCalendar != null, destructive = true)
        }
    }
    if (picker) Dialog(onDismissRequest = { picker = false }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scegli calendario", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(calendarOwner.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    calendars.forEach { calendar ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(if (selectedCalendar?.id == calendar.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedCalendar = calendar; picker = false }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SiteIcon("calendar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Column(Modifier.weight(1f)) {
                                Text(calendar.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                if (calendar.primary) Text("Calendario principale", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selectedCalendar?.id == calendar.id) SiteIcon("check", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                TextButton(onClick = { picker = false }, modifier = Modifier.align(Alignment.End)) { Text("Annulla") }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Rimuovere gli eventi TurniSync?") },
        text = { Text("Verranno rimossi dal calendario «${selectedCalendar?.name.orEmpty()}» tutti gli eventi creati da TurniSync. Gli eventi personali e i turni salvati nell'app non saranno eliminati.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; deleteEvents() }, enabled = canChange) { Text("Rimuovi eventi", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } })
    if (confirmEmpty) AlertDialog(onDismissRequest = { confirmEmpty = false }, title = { Text("Nessun turno nel periodo") },
        text = { Text("La sincronizzazione rimuoverà eventuali eventi TurniSync di questo lavoro nel periodo selezionato. Vuoi continuare?") },
        confirmButton = { TextButton(onClick = { confirmEmpty = false; synchronize() }, enabled = canChange) { Text("Aggiorna calendario") } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Annulla") } })
}

@Composable
private fun CalendarOptionSwitch(title: String, subtitle: String, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = value, onCheckedChange = onChange, enabled = enabled)
    }
}
@Composable
private fun CalendarOptionTabs(options: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index, label ->
            val color by animateColorAsState(if (selected == index) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, tween(230))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(color).clickable(enabled = enabled) { onSelect(index) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 13.sp, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
