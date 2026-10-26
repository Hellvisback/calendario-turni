@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.export.PdfExportPlan
import com.turni.calendario.export.PdfGenerator
import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PdfExportScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var options by remember { mutableStateOf(ShiftRepository.pdfExportOptions) }
    var dateDialog by remember { mutableStateOf(false) }
    var filterDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<PdfPreview?>(null) }
    var previewLoading by remember { mutableStateOf(true) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf(false) }
    val calendar=ShiftRepository.calendarShifts.mapValues{it.value.toList()}
    val extras=ShiftRepository.dayExtraInfo.toMap()
    val definitions=ShiftRepository.shiftDefinitions.toList()
    LaunchedEffect(options,ShiftRepository.activeWorkProfileId,calendar,extras,definitions) {
        previewLoading=true;previewError=null;preview=null
        try {
            delay(280)
            val snapshot=PdfGenerator.capture(options)
            preview=withContext(Dispatchers.IO) {
                val file=PdfGenerator.writePdf(context,snapshot)
                ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        check(renderer.pageCount==1){"Il documento deve contenere una sola pagina"}
                        renderer.openPage(0).use { page ->
                            val width=1100
                            val bitmap=Bitmap.createBitmap(width,(width.toFloat()*page.height/page.width).toInt(),Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PdfPreview(file,bitmap)
                        }
                    }
                }
            }
        } catch(error:Exception) {
            if(error is kotlinx.coroutines.CancellationException)throw error
            previewError=if(error is IllegalArgumentException)error.message else "Impossibile preparare l’anteprima. Riprova."
        } finally {previewLoading=false}
    }
    val update: (PdfExportOptions) -> Unit = { options = it; ShiftRepository.pdfExportOptions = it; ShiftRepository.notifyDataChanged() }
    val range = remember(options.mode, options.startDate, options.endDate, options.selectedYear) { PdfExportPlan.range(options) }
    val formatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN) }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) { SiteIcon("arrow-left", "Indietro") }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Esporta & Stampa PDF", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(ShiftRepository.activeWorkProfile.name, color = TextSecondary, fontSize = 12.sp)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PdfSection("Formato", "Scegli come organizzare il documento") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        PdfExportMode.values().forEach { mode ->
                            val selected = options.mode == mode
                            val color by animateColorAsState(if (selected) ObsidianPrimaryContainer else ObsidianSurfaceContainer, spring(stiffness = 240f))
                            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(13.dp)).background(color).selectable(selected, role = Role.RadioButton) { update(options.copy(mode = mode)) }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text(mode.label, fontSize = 12.sp, color = TextPrimary, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                    Text(if (options.mode == PdfExportMode.ELENCO) "Elenco compatto su una pagina, con tutti i dettagli selezionati." else "Calendario su una pagina, senza aggiungere un elenco separato.", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                }
                PdfSection("Periodo e turni") {
                    PdfAction("calendar", "${range.first.format(formatter)} - ${range.second.format(formatter)}", "Cambia periodo") { dateDialog = true }
                    Divider(color = ObsidianBorderSubtle, modifier = Modifier.padding(vertical = 9.dp))
                    PdfAction("layers", options.filterShiftId?.let { ShiftRepository.getShiftDefinition(it)?.name } ?: "Tutti i turni", "Filtra modelli") { filterDialog = true }
                }
                PdfSection("Anteprima", "Lo stesso documento che verrà condiviso · 1 pagina") {
                    when {
                        previewLoading -> Box(Modifier.fillMaxWidth().height(220.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(26.dp),color=ObsidianPrimary,strokeWidth=2.dp)}
                        preview!=null -> Surface(onClick={zoom=true},shape=RoundedCornerShape(12.dp),color=androidx.compose.ui.graphics.Color.White) {
                            Image(preview!!.bitmap.asImageBitmap(),"Anteprima PDF, tocca per ingrandire",Modifier.fillMaxWidth().height(250.dp).padding(6.dp),contentScale=ContentScale.Fit)
                        }
                        else -> Text(previewError?:"Nessuna anteprima disponibile",color=TextSecondary,fontSize=12.sp)
                    }
                    if(preview!=null&&!previewLoading)Text("Tocca per ingrandire",fontSize=11.sp,color=TextMuted,modifier=Modifier.padding(top=9.dp))
                }
                PdfSection("Contenuti", "Anteprima aggiornata automaticamente") {
                    PdfToggle("Nome del turno", options.includeTitle) { update(options.copy(includeTitle = it)) }
                    PdfToggle("Orario di inizio e fine", options.includeTime) { update(options.copy(includeTime = it)) }
                    PdfToggle("Durata prevista", options.includeDuration) { update(options.copy(includeDuration = it)) }
                    PdfToggle("Pausa", options.includeBreak) { update(options.copy(includeBreak = it)) }
                    PdfToggle("Trasferte", options.includeLocation) { update(options.copy(includeLocation = it)) }
                    PdfToggle("Note e tag", options.includeNotes) { update(options.copy(includeNotes = it)) }
                    PdfToggle("Festività", options.includeEvents) { update(options.copy(includeEvents = it)) }
                    PdfToggle("Colori dei turni", options.includeIcon) { update(options.copy(includeIcon = it)) }
                    if(options.mode==PdfExportMode.ELENCO)PdfToggle("Giorni senza turni", options.includeEmptyRows) { update(options.copy(includeEmptyRows = it)) }
                    PdfToggle("Marchio TurniSync", options.includeLogo) { update(options.copy(includeLogo = it)) }
                }
                PdfSection("Nota del documento") {
                    OutlinedTextField(value = options.headerNotes, onValueChange = { update(options.copy(headerNotes = it.take(4000))) }, placeholder = { Text("Una nota per chi riceve il prospetto") }, modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp), maxLines = 5, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = TextPrimary, focusedBorderColor = ObsidianPrimary, unfocusedBorderColor = ObsidianBorderSubtle), shape = RoundedCornerShape(14.dp))
                    Text("Il PDF usa fondo bianco e testo scuro per la lettura e la stampa.", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Surface(color = ObsidianBackground) {
                Button(onClick = {
                    preview?.let { ready ->
                        busy=true
                        try { PdfGenerator.sharePdf(context,ready.file) }
                        catch(error:Exception){message="Impossibile condividere il PDF. Riprova."}
                        finally{busy=false}
                    }
                }, enabled = !busy && !previewLoading && preview!=null, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary, contentColor = OnBrand), modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 50.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(19.dp), color = OnBrand, strokeWidth = 2.dp) else SiteIcon("download", null, tint = OnBrand)
                    Spacer(Modifier.width(10.dp)); Text(if (previewLoading) "Preparazione anteprima…" else "Condividi PDF · 1 pagina", fontWeight = FontWeight.Bold)
                }
            }
        }
        AnimatedStatusPopup(message, PopupType.ERROR, { message = null }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp))
    }
    if(zoom&&preview!=null) PdfPreviewDialog(preview!!){zoom=false}
    if (dateDialog) PdfPeriodDialog(options, { dateDialog = false }) { update(it); dateDialog = false }
    if (filterDialog) AlertDialog(onDismissRequest = { filterDialog = false }, title = { Text("Turni da includere") }, containerColor = ObsidianSurface, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            val entries = listOf(null to "Tutti i turni") + ShiftRepository.shiftDefinitions.filter { it.workProfileId == ShiftRepository.activeWorkProfileId }.map { it.id to it.name }
            entries.forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().selectable(options.filterShiftId == id, role = Role.RadioButton) { update(options.copy(filterShiftId = id)); filterDialog = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(options.filterShiftId == id, onClick = null); Text(label, color = TextPrimary, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { filterDialog = false }) { Text("Chiudi") } })
}

@Composable
private fun PdfSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = ObsidianSurface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = .9f, stiffness = 220f))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            subtitle?.let { Text(it, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(13.dp)); content()
        }
    }
}

@Composable
private fun PdfAction(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Surface(onClick = { scope.launch { progress.snapTo(0f); progress.animateTo(1f, tween(560)); progress.snapTo(0f) }; onClick() }, color = ObsidianSurface, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            SiteIcon(icon, null, tint = ObsidianPrimary, progress = progress.value)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium); Text(subtitle, color = TextMuted, fontSize = 11.sp) }
            SiteIcon("chevron-right", null, Modifier.size(18.dp), TextMuted)
        }
    }
}

@Composable
private fun PdfToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), color = TextPrimary, fontSize = 13.sp)
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = OnBrand, checkedTrackColor = ObsidianPrimary))
    }
}

@Composable
private fun PdfPeriodDialog(options: PdfExportOptions, dismiss: () -> Unit, save: (PdfExportOptions) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(options.startDate)) }
    var year by remember { mutableStateOf(options.selectedYear) }
    var from by remember { mutableStateOf(options.startDate) }
    var to by remember { mutableStateOf(options.endDate) }
    var selectingEnd by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, containerColor = ObsidianSurface, title = { Text("Periodo da esportare", color = TextPrimary) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (options.mode == PdfExportMode.MESE || options.mode == PdfExportMode.ANNO) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (options.mode == PdfExportMode.MESE) { if (month > YearMonth.of(1900, 1)) month = month.minusMonths(1) } else if (year > 1900) year-- }) { SiteIcon("chevron-left", "Periodo precedente") }
                    Text(if (options.mode == PdfExportMode.MESE) month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)) else "$year", color = TextPrimary, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { if (options.mode == PdfExportMode.MESE) { if (month < YearMonth.of(2200, 12)) month = month.plusMonths(1) } else if (year < 2200) year++ }) { SiteIcon("chevron-right", "Periodo successivo") }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false, true).forEach { end ->
                        val selected = selectingEnd == end
                        val tone by animateColorAsState(if (selected) ObsidianPrimaryContainer else ObsidianSurfaceContainer)
                        Surface(onClick = { selectingEnd = end; month = YearMonth.from(if (end) to else from) }, color = tone, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(if (end) "Al" else "Dal", color = TextSecondary, fontSize = 11.sp)
                                Text((if (end) to else from).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                PdfDateGrid(month, if (selectingEnd) to else from, { month = it }) { date ->
                    error = null
                    if (selectingEnd) to = date else { from = date; if (to < from) to = from; selectingEnd = true }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Annulla") } }, confirmButton = {
        TextButton(onClick = {
            try {
                val changed = when (options.mode) {
                    PdfExportMode.MESE -> options.copy(startDate = month.atDay(1), endDate = month.atEndOfMonth())
                    PdfExportMode.ANNO -> options.copy(selectedYear = year)
                    else -> options.copy(startDate = from, endDate = to)
                }
                PdfExportPlan.dates(changed); save(changed)
            } catch (_: java.time.format.DateTimeParseException) { error = "Controlla le date: esempio 2026-11-01" }
            catch (exception: IllegalArgumentException) { error = exception.message }
        }) { Text("Conferma") }
    })
}

@Composable
private fun PdfDateGrid(month: YearMonth, selected: LocalDate, changeMonth: (YearMonth) -> Unit, select: (LocalDate) -> Unit) {
    Column(Modifier.animateContentSize(spring(stiffness = 250f))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (month > YearMonth.of(1900, 1)) changeMonth(month.minusMonths(1)) }) { SiteIcon("chevron-left", "Mese precedente") }
            Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)), color = TextPrimary, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { if (month < YearMonth.of(2200, 12)) changeMonth(month.plusMonths(1)) }) { SiteIcon("chevron-right", "Mese successivo") }
        }
        Row(Modifier.fillMaxWidth()) { listOf("L", "M", "M", "G", "V", "S", "D").forEach { Text(it, color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        val offset = month.atDay(1).dayOfWeek.value - 1
        repeat((offset + month.lengthOfMonth() + 6) / 7) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = row * 7 + column - offset + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        val tone by animateColorAsState(if (selected == date) ObsidianPrimaryContainer else ObsidianSurface)
                        Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(tone).selectable(selected == date, role = Role.RadioButton) { select(date) }, contentAlignment = Alignment.Center) { Text("$day", color = TextPrimary, fontSize = 12.sp) }
                    } else Spacer(Modifier.weight(1f).height(38.dp))
                }
            }
        }
    }
}

private data class PdfPreview(val file:File,val bitmap:Bitmap)

@Composable
private fun PdfPreviewDialog(preview:PdfPreview,onClose:()->Unit) {
    var scale by remember(preview){mutableStateOf(1f)}
    var offset by remember(preview){mutableStateOf(Offset.Zero)}
    val gestures=rememberTransformableState{zoom,pan,_->
        scale=(scale*zoom).coerceIn(1f,4f)
        offset=if(scale==1f)Offset.Zero else offset+pan
    }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth(.94f).fillMaxHeight(.88f),shape=RoundedCornerShape(20.dp),color=ObsidianSurface) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("Anteprima · 1 pagina",Modifier.weight(1f),color=TextPrimary,fontWeight=FontWeight.Bold)
                    IconButton(onClick=onClose){SiteIcon("x","Chiudi anteprima")}
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color.White),contentAlignment=Alignment.Center) {
                    Image(preview.bitmap.asImageBitmap(),"Documento PDF",Modifier.fillMaxSize().transformable(gestures).graphicsLayer{scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y},contentScale=ContentScale.Fit)
                }
                Text("Allarga con due dita per leggere i dettagli",fontSize=11.sp,color=TextSecondary,modifier=Modifier.padding(top=10.dp,bottom=4.dp))
            }
        }
    }
}
