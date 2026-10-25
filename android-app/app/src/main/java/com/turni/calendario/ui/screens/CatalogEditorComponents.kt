@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.turni.calendario.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.components.TurniEraserIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.util.UUID

/** Approved defaults; existing custom colors remain untouched. */
val catalogPastelColors = listOf(0xFFF2C46DL, 0xFFEE9C9CL, 0xFFBAAEF6L, 0xFF83CDB7L, 0xFF94C4EFL, 0xFFAAB9CEL)
internal fun catalogInk(color: Color): Color {
    val dark = Color(0xFF171923)
    val luminance = color.luminance()
    val darkContrast = (luminance + .05f) / (dark.luminance() + .05f)
    val lightContrast = 1.05f / (luminance + .05f)
    return if (darkContrast >= lightContrast) dark else Color.White
}

@Composable
internal fun CatalogGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun CatalogToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** One real sheet, with finite spring entry/exit and stable independent scrolling. */
@Composable
internal fun CatalogEditorSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.((() -> Unit) -> Unit) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val finish: (() -> Unit) -> Unit = { action ->
        if (!closing) {
            closing = true
            visible = false
            scope.launch { delay(280); action() }
        }
    }
    LaunchedEffect(Unit) { visible = true }
    BackHandler { finish(onDismiss) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .24f)).clickable(enabled = !closing) { finish(onDismiss) })
        }
        AnimatedVisibility(visible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = .88f, stiffness = Spring.StiffnessLow)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()) {
            Surface(Modifier.fillMaxWidth().fillMaxHeight(.93f), shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        IconButton(onClick = { finish(onDismiss) }, enabled = !closing) { Icon(Icons.Default.Close, "Chiudi", tint = MaterialTheme.colorScheme.error) }
                    }
                    content(finish)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalColorPickerRow(selectedColorHex: Long, onColorSelected: (Long) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            catalogPastelColors.forEach { value ->
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color(value)).bouncyClickable { onColorSelected(value) }, contentAlignment = Alignment.Center) {
                    if (value == selectedColorHex) Icon(Icons.Default.Check, "Selezionato", tint = catalogInk(Color(value)), modifier = Modifier.size(19.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Colore personalizzato", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(colorToHex(Color(selectedColorHex)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(selectedColorHex)).bouncyClickable { showPicker = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Colorize, "Scegli colore personalizzato", tint = catalogInk(Color(selectedColorHex)), modifier = Modifier.size(22.dp))
        }
    }
    if (showPicker) CustomColorPickerDialog(selectedColorHex, { showPicker = false }) { onColorSelected(it); showPicker = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorPickerDialog(initialColorHex: Long, onDismiss: () -> Unit, onColorSelected: (Long) -> Unit) {
    val initialHsv = remember(initialColorHex) { FloatArray(3).also { android.graphics.Color.colorToHSV(initialColorHex.toInt(), it) } }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    var hex by remember { mutableStateOf(colorToHex(Color(initialColorHex))) }
    val selected = hsvToColor(hue, saturation, brightness)
    val valid = Regex("#[0-9a-fA-F]{6}").matches(hex)
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun finish(action: () -> Unit) {
        if (!closing) {
            closing = true
            visible = false
            scope.launch { delay(220); action() }
        }
    }
    LaunchedEffect(Unit) { visible = true }
    fun updateHex() { hex = colorToHex(hsvToColor(hue, saturation, brightness)) }
    Dialog(onDismissRequest = { finish(onDismiss) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(visible, enter = slideInVertically(spring(dampingRatio = .9f, stiffness = Spring.StiffnessLow)) { it / 4 } + fadeIn(), exit = slideOutVertically { it / 4 } + fadeOut()) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scegli colore", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { finish(onDismiss) }, enabled = !closing) { Icon(Icons.Default.Close, "Chiudi") }
                }
                NativeColorPlane(hue,saturation,brightness){s,v->saturation=s;brightness=v;updateHex()}
                Text("Tonalità", fontSize = 12.sp)
                Box(Modifier.fillMaxWidth().height(36.dp),contentAlignment=Alignment.Center) {
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red))))
                    Slider(hue, { hue = it; updateHex() }, valueRange = 0f..360f,
                        colors=SliderDefaults.colors(activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent))
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedTextField(hex, {
                    hex = it.take(7).uppercase()
                    if (Regex("#[0-9A-F]{6}").matches(hex)) {
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(hex), hsv)
                        if (hsv[1] > 0f) hue = hsv[0]
                        saturation = hsv[1]; brightness = hsv[2]
                    }
                }, label = { Text("HEX") }, isError = !valid, singleLine = true, modifier = Modifier.weight(1f))
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(selected))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton({ finish(onDismiss) }, Modifier.weight(1f), enabled = !closing) { Text("Annulla") }
                    Button({ parseHexToColor(hex)?.let { value -> finish { onColorSelected(colorToLong(value)) } } }, Modifier.weight(1f), enabled = valid && !closing) { Text("Imposta") }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftEditorScreen(shift: ShiftDefinition, onDismiss: () -> Unit, onSave: (ShiftDefinition) -> Unit, onDelete: (String) -> Unit) {
    var name by remember(shift.id) { mutableStateOf(shift.name) }
    var code by remember(shift.id) { mutableStateOf(shift.shortCode.take(4)) }
    var start by remember(shift.id) { mutableStateOf(shift.startTime) }
    var end by remember(shift.id) { mutableStateOf(shift.endTime) }
    var allDay by remember(shift.id) { mutableStateOf(shift.isAllDay) }
    var hasBreak by remember(shift.id) { mutableStateOf(shift.breakMinutes > 0) }
    var pause by remember(shift.id) { mutableStateOf(shift.breakMinutes.toString()) }
    var breakStart by remember(shift.id) { mutableStateOf(shift.breakStartTime ?: "12:00") }
    var color by remember(shift.id) { mutableStateOf(shift.colorHex) }
    var workId by remember(shift.id) { mutableStateOf(shift.workProfileId) }
    var category by remember(shift.id) { mutableStateOf(shift.category) }
    var workMenu by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val existing = ShiftRepository.shiftDefinitions.any { it.id == shift.id }
    fun validTime(value: String) = runCatching { LocalTime.parse(value) }.isSuccess
    val validCode = code.matches(Regex("[A-Z0-9]{1,4}"))
    val valid = name.isNotBlank() && validCode && (allDay || (validTime(start) && validTime(end) && (!hasBreak || (validTime(breakStart) && (pause.toIntOrNull() ?: -1) in 0..720))))
    fun draft() = shift.copy(name = name.trim(), shortCode = code, startTime = start, endTime = end, colorHex = color, workProfileId = workId, category = category, isAllDay = allDay, breakMinutes = if (hasBreak && !allDay) pause.toIntOrNull() ?: 0 else 0, breakStartTime = if (hasBreak && !allDay) breakStart else null)
    CatalogEditorSheet(if (existing) "Modifica turno" else "Nuovo turno", onDismiss) { finish ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(color).copy(alpha = .16f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(color)), contentAlignment = Alignment.Center) { Text(code, color = catalogInk(Color(color)), fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 12.dp)) { Text(name.ifBlank { "Il tuo turno" }, fontWeight = FontWeight.Bold); Text(if (allDay) "Tutto il giorno" else "$start – $end", fontSize = 13.sp) }
            }
            CatalogGroup("Identità") {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nome del turno") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(code, { code = it.uppercase().filter { ch -> ch in 'A'..'Z' || ch in '0'..'9' }.take(4) }, label = { Text("Sigla · massimo 4 caratteri") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = !validCode)
                if (ShiftRepository.workProfiles.size > 1) Box {
                    OutlinedButton(onClick = { workMenu = !workMenu }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(getWorkProfileIcon(ShiftRepository.workProfiles.firstOrNull { it.id == workId }?.iconKey ?: "work"), null, Modifier.size(18.dp))
                        Text(ShiftRepository.workProfiles.firstOrNull { it.id == workId }?.name ?: "Seleziona lavoro", Modifier.weight(1f).padding(horizontal = 10.dp), textAlign = TextAlign.Start, fontSize = 13.sp)
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    DropdownMenu(expanded = workMenu, onDismissRequest = { workMenu = false }) {
                        ShiftRepository.workProfiles.forEach { profile -> DropdownMenuItem(text = { Text(profile.name) }, onClick = { workId = profile.id; workMenu = false }) }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ShiftCategory.LAVORO to "Lavoro", ShiftCategory.RIPOSO to "Riposo", ShiftCategory.ASSENZA to "Assenza", ShiftCategory.ALTRO to "Altro").forEach { (value, label) ->
                        Surface(shape = RoundedCornerShape(10.dp), color = if (category == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background, modifier = Modifier.bouncyClickable { category = value }) {
                            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = if (category == value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
            CatalogGroup("Orario") {
                CatalogToggle("Tutto il giorno", allDay) { allDay = it }
                AnimatedVisibility(!allDay, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CatalogTimeField("Inizio", start, { start = it }, Modifier.weight(1f))
                        CatalogTimeField("Fine", end, { end = it }, Modifier.weight(1f))
                    }
                }
            }
            AnimatedVisibility(!allDay) {
                CatalogGroup("Pausa") {
                    CatalogToggle("Sottrai la pausa", hasBreak) { hasBreak = it }
                    AnimatedVisibility(hasBreak) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CatalogTimeField("Dalle", breakStart, { breakStart = it }, Modifier.weight(1f))
                            OutlinedTextField(pause, { pause = it.filter(Char::isDigit).take(3) }, label = { Text("Minuti") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            CatalogGroup("Colore") { UniversalColorPickerRow(color) { color = it } }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (existing) {
                IconButton(onClick = {
                    val base = code.ifBlank { "TUR" }
                    var suffix = 2
                    var candidate: String
                    do { val tail = (suffix++).toString(); candidate = base.take((4 - tail.length).coerceAtLeast(0)) + tail } while (ShiftRepository.shiftDefinitions.any { it.shortCode.equals(candidate, true) })
                    val copy = draft().copy(id = UUID.randomUUID().toString(), name = "$name copia", shortCode = candidate.take(4))
                    finish { onSave(copy) }
                }, enabled = valid) { Icon(Icons.Default.ContentCopy, "Duplica turno") }
                IconButton(onClick = { deleteConfirm = true }) { Icon(Icons.Default.DeleteOutline, "Elimina turno", tint = MaterialTheme.colorScheme.error) }
            }
            Button(onClick = { if (valid) finish { onSave(draft()) } }, enabled = valid, modifier = Modifier.weight(1f).height(48.dp)) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Salva turno") }
        }
        if (deleteConfirm) CatalogDeleteDialog("Eliminare questo turno?", { deleteConfirm = false }) { deleteConfirm = false; finish { onDelete(shift.id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogTimeField(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val current = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(6, 0))
        android.app.TimePickerDialog(context, { _, h, m -> change(String.format(java.util.Locale.ROOT, "%02d:%02d", h, m)) }, current.hour, current.minute, true).show()
    }, modifier = modifier.height(68.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(label, fontSize = 11.sp); Text(value, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun CatalogDeleteDialog(title: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text("Questa operazione non può essere annullata.") }, confirmButton = { TextButton(confirm) { Text("Elimina", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(dismiss) { Text("Annulla") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationMatrixEditorScreen(rotation: ShiftRotation, onDismiss: () -> Unit, onSave: (ShiftRotation) -> Unit, onApply: (ShiftRotation) -> Unit, onDelete: ((String) -> Unit)? = null) {
    var title by remember(rotation.id) { mutableStateOf(rotation.name) }
    val sequence = remember(rotation.id) { mutableStateListOf<String>().apply { addAll(rotation.daySequence.ifEmpty { listOf("") }) } }
    var brush by remember { mutableStateOf(ShiftRepository.shiftDefinitions.firstOrNull()?.id ?: "") }
    var favorite by remember(rotation.id) { mutableStateOf(ShiftRepository.defaultRotationId == rotation.id) }
    val starMotion = remember { Animatable(0f) }
    val starScope = rememberCoroutineScope()
    var deleteConfirm by remember { mutableStateOf(false) }
    CatalogEditorSheet("Modifica rotazione", onDismiss) { finish ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CatalogGroup("Rotazione") {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).bouncyClickable {
                    favorite = !favorite
                    starScope.launch {
                        starMotion.snapTo(-18f)
                        starMotion.animateTo(0f, spring(dampingRatio = .4f, stiffness = Spring.StiffnessLow))
                    }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, "Rotazione predefinita", tint = if (favorite) Color(0xFFB37A13) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = starMotion.value })
                    Text("Rotazione predefinita", Modifier.padding(10.dp), fontSize = 14.sp)
                }
            }
            CatalogGroup("Sequenza") {
                for (week in 0 until (sequence.size + 6) / 7) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (day in 0..6) {
                            val index = week * 7 + day
                            val def = sequence.getOrNull(index)?.let { ShiftRepository.getShiftDefinition(it) }
                            Box(Modifier.weight(1f).aspectRatio(.78f).clip(RoundedCornerShape(12.dp)).background(def?.color?.copy(alpha = .28f) ?: MaterialTheme.colorScheme.background).bouncyClickable {
                                if (index < sequence.size) sequence[index] = brush
                                else { while (sequence.size <= index && sequence.size < 70) sequence.add(""); if (index < sequence.size) sequence[index] = brush }
                            }, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${index + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(def?.shortCode?.take(4) ?: "–", fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { if (sequence.size > 1) sequence.removeAt(sequence.lastIndex) }, enabled = sequence.size > 1) { Text("− Giorno") }
                    TextButton(onClick = { if (sequence.size < 70) sequence.add("") }, enabled = sequence.size < 70) { Text("+ Giorno") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CatalogPaintChip("", "Gomma", MaterialTheme.colorScheme.surfaceVariant, brush.isEmpty()) { brush = "" }
            ShiftRepository.shiftDefinitions.forEach { def -> CatalogPaintChip(def.shortCode, def.name, def.color, brush == def.id) { brush = def.id } }
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDelete != null) IconButton(onClick = { deleteConfirm = true }) { Icon(Icons.Default.DeleteOutline, "Elimina rotazione", tint = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                val saved = rotation.copy(name = title.trim(), totalDays = sequence.size, daySequence = sequence.toList())
                finish { onSave(saved); if (favorite) ShiftRepository.setDefaultRotation(saved.id) else if (ShiftRepository.defaultRotationId == saved.id) ShiftRepository.setDefaultRotation("") }
            }, enabled = title.isNotBlank() && sequence.any { it.isNotEmpty() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Salva rotazione") }
        }
        if (deleteConfirm && onDelete != null) CatalogDeleteDialog("Eliminare la rotazione?", { deleteConfirm = false }) { deleteConfirm = false; finish { onDelete(rotation.id) } }
    }
}

@Composable
private fun CatalogPaintChip(code: String, name: String, color: Color, selected: Boolean, action: () -> Unit) {
    Column(Modifier.width(70.dp).bouncyClickable(onClick = action), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(50.dp).border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(15.dp)).padding(4.dp).clip(RoundedCornerShape(11.dp)).background(color), contentAlignment = Alignment.Center) {
            if (code.isEmpty()) Icon(TurniEraserIcon, "Gomma", tint = catalogInk(color), modifier = Modifier.size(22.dp)) else Text(code.take(4), color = catalogInk(color), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(name, fontSize = 10.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkProfileEditorScreen(profile: WorkProfile, isNew: Boolean, onDismiss: () -> Unit, onSave: (WorkProfile) -> Unit, onDelete: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var department by remember(profile.id) { mutableStateOf(profile.companyOrDepartment) }
    var icon by remember(profile.id) { mutableStateOf(profile.iconKey) }
    var color by remember(profile.id) { mutableStateOf(profile.colorHex) }
    var advanced by remember(profile.id) { mutableStateOf(profile.enableAdvancedCalc) }
    var active by remember(profile.id) { mutableStateOf(profile.isDefault) }
    var contract by remember(profile.id) { mutableStateOf(profile.linkedCcnl) }
    var hours by remember(profile.id) { mutableStateOf(profile.weeklyHours) }
    var rest by remember(profile.id) { mutableStateOf(profile.minRestHoursBetweenShifts) }
    var night by remember(profile.id) { mutableStateOf(profile.nightSurchargePercent) }
    var holiday by remember(profile.id) { mutableStateOf(profile.holidaySurchargePercent) }
    var nightHoliday by remember(profile.id) { mutableStateOf(profile.nightHolidaySurchargePercent) }
    var overtime by remember(profile.id) { mutableStateOf(profile.overtimeDaySurchargePercent) }
    var overtimeNight by remember(profile.id) { mutableStateOf(profile.overtimeNightSurchargePercent) }
    var documentName by remember(profile.id) { mutableStateOf(profile.ccnlDocumentName) }
    var documentUri by remember(profile.id) { mutableStateOf(profile.ccnlDocumentUri) }
    var documentError by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var showContracts by remember { mutableStateOf(false) }
    var stagedDocument by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) { onDispose { stagedDocument?.delete() } }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            var title = "Documento CCNL.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) title = cursor.getString(index)
            }
            val temp = File.createTempFile("ccnl-draft-", ".pdf", context.cacheDir)
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("Documento non disponibile")
                input.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                check(temp.length() > 0) { "Il documento è vuoto" }
                stagedDocument?.delete()
                stagedDocument = temp
                documentName = title
                documentUri = temp.absolutePath
                documentError = null
            } catch (error: Exception) { temp.delete(); throw error }
        }.onFailure { documentError = "Impossibile aprire il documento. Riprova." }
    }
    val iconKeys = listOf("work", "business", "people", "computer", "factory", "hospital", "shipping", "transit")
    CatalogEditorSheet(if (isNew) "Nuovo lavoro" else "Modifica lavoro", onDismiss) { finish ->
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CatalogGroup("Identità") {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nome lavoro") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(department, { department = it.take(100) }, label = { Text("Reparto o settore · facoltativo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Icona del lavoro", fontSize = 12.sp)
                iconKeys.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(if (icon == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background).border(if (icon == key) 2.dp else 0.dp, if (icon == key) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(14.dp)).bouncyClickable { icon = key }, contentAlignment = Alignment.Center) {
                                Icon(getWorkProfileIcon(key), when (key) { "work" -> "Valigetta"; "business" -> "Edificio"; "people" -> "Persone"; "computer" -> "Computer"; "factory" -> "Strumenti"; "hospital" -> "Salute"; "shipping" -> "Trasporto"; else -> "Treno" }, tint = if (icon == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
            CatalogGroup("Colore del lavoro") { UniversalColorPickerRow(color) { color = it } }
            CatalogGroup("Contratto e orario") {
                OutlinedButton(onClick = { showContracts = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(contract.name, Modifier.weight(1f), fontSize = 13.sp); Icon(Icons.Default.ExpandMore, null)
                }
                CatalogNumberRow("Ore settimanali", hours, 1..60) { hours = it }
                CatalogNumberRow("Riposo minimo · ore", rest, 1..24) { rest = it }
                CatalogToggle("Parametri CCNL avanzati", advanced) { advanced = it }
                AnimatedVisibility(advanced, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CatalogNumberRow("Notturno · %", night, 0..200, 5) { night = it }
                        CatalogNumberRow("Festivo · %", holiday, 0..200, 5) { holiday = it }
                        CatalogNumberRow("Notte e festivo · %", nightHoliday, 0..200, 5) { nightHoliday = it }
                        CatalogNumberRow("Straordinario diurno · %", overtime, 0..200, 5) { overtime = it }
                        CatalogNumberRow("Straordinario notturno · %", overtimeNight, 0..200, 5) { overtimeNight = it }
                    }
                }
            }
            CatalogGroup("Documento CCNL") {
                Text(documentName ?: "Nessun documento selezionato", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { documentPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp)); Text(if (documentUri == null) "Allega PDF" else "Sostituisci", fontSize = 12.sp) }
                    if (documentUri != null) {
                        IconButton(onClick = { openWorkProfilePdf(context, profile.copy(ccnlDocumentUri = documentUri, ccnlDocumentName = documentName)) }) { Icon(Icons.Default.OpenInNew, "Apri documento") }
                        IconButton(onClick = { stagedDocument?.delete(); stagedDocument = null; documentName = null; documentUri = null }) { Icon(Icons.Default.DeleteOutline, "Rimuovi documento", tint = MaterialTheme.colorScheme.error) }
                    }
                }
                if (documentError != null) Text(documentError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            CatalogGroup("Calendario") { CatalogToggle("Imposta come lavoro attivo", active) { active = it } }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isNew && onDelete != null && ShiftRepository.workProfiles.size > 1) IconButton(onClick = { deleteConfirm = true }) { Icon(Icons.Default.DeleteOutline, "Elimina lavoro", tint = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                runCatching {
                    var savedUri = documentUri
                    stagedDocument?.let { staged ->
                        val destination = File(context.filesDir, "ccnl_${profile.id}.pdf")
                        staged.copyTo(destination, overwrite = true)
                        savedUri = destination.absolutePath
                    }
                    val saved = profile.copy(name = name.trim(), companyOrDepartment = department.trim(), iconKey = icon, colorHex = color, enableAdvancedCalc = advanced, isDefault = active, ccnlCode = contract.code, ccnlName = contract.name, weeklyHours = hours, minRestHoursBetweenShifts = rest, nightSurchargePercent = night, holidaySurchargePercent = holiday, nightHolidaySurchargePercent = nightHoliday, overtimeDaySurchargePercent = overtime, overtimeNightSurchargePercent = overtimeNight, ccnlDocumentName = documentName, ccnlDocumentUri = savedUri)
                    finish { onSave(saved) }
                }.onFailure { documentError = "Impossibile salvare il documento. Riprova." }
            }, enabled = name.isNotBlank(), modifier = Modifier.weight(1f).height(48.dp)) { Text(if (isNew) "Crea lavoro" else "Salva lavoro") }
        }
        if (deleteConfirm && onDelete != null) CatalogDeleteDialog("Eliminare questo lavoro?", { deleteConfirm = false }) { deleteConfirm = false; finish { onDelete(profile.id) } }
        if (showContracts) AlertDialog(onDismissRequest = { showContracts = false }, title = { Text("Contratto di riferimento") }, text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                CcnlCatalog.presets.forEach { preset ->
                    TextButton(onClick = {
                        contract = preset; hours = preset.standardWeeklyHours; rest = preset.minRestHoursBetweenShifts
                        night = preset.nightSurchargePercent; holiday = preset.holidaySurchargePercent; nightHoliday = preset.nightHolidaySurchargePercent
                        overtime = preset.overtimeDaySurchargePercent; overtimeNight = preset.overtimeNightSurchargePercent
                        showContracts = false
                    }, modifier = Modifier.fillMaxWidth()) { Text(preset.name, Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                }
            }
        }, confirmButton = { TextButton(onClick = { showContracts = false }) { Text("Chiudi") } })
    }
}

@Composable
private fun CatalogNumberRow(label: String, value: Int, range: IntRange, step: Int = 1, update: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp), fontSize = 13.sp)
        IconButton(onClick = { update((value - step).coerceIn(range)) }, enabled = value > range.first, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Remove, "Diminuisci $label", Modifier.size(18.dp)) }
        Text("$value", Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { update((value + step).coerceIn(range)) }, enabled = value < range.last, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, "Aumenta $label", Modifier.size(18.dp)) }
    }
}
