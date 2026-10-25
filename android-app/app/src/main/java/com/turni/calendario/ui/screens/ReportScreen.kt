package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.*

private enum class ReportPeriodMode(val label: String) { MESE("Mese"), ANNO("Anno"), PERSONALIZZATO("Personalizzato") }
private data class ShiftMetric(val definition: ShiftDefinition, val count: Int, val hours: Float)
private fun hourLabel(value: Float) = String.format(Locale.ITALIAN, "%.1f h", value)

@Composable
fun ReportScreen(onOpenSettings: () -> Unit = {}) {
    var mode by remember { mutableStateOf(ReportPeriodMode.MESE) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var year by remember { mutableStateOf(LocalDate.now().year) }
    var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var until by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf<Int?>(null) }
    val start = when (mode) { ReportPeriodMode.MESE -> month.atDay(1); ReportPeriodMode.ANNO -> LocalDate.of(year, 1, 1); else -> from }
    val end = when (mode) { ReportPeriodMode.MESE -> month.atEndOfMonth(); ReportPeriodMode.ANNO -> LocalDate.of(year, 12, 31); else -> until }
    // Observe actual collections, not their size: replacing a shift must refresh every metric.
    val calendarSnapshot = ShiftRepository.calendarShifts.mapValues { it.value.toList() }
    val extrasSnapshot = ShiftRepository.dayExtraInfo.toMap()
    val definitions = ShiftRepository.shiftDefinitions.toList()
    val assignments = remember(start, end, calendarSnapshot) {
        calendarSnapshot.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.flatten()
    }
    val stats = remember(start, end, calendarSnapshot, extrasSnapshot, definitions,
        ShiftRepository.reperibilitaConfig, ShiftRepository.rotations.toList(), ShiftRepository.activeRotationId) {
        ShiftRepository.getComprehensiveReportStats(start, end)
    }
    val metrics = remember(assignments, definitions, stats) { definitions.mapNotNull { def ->
        // Presentation follows the assigned model; payroll may classify leave by its rotation origin.
        val count = assignments.count { it.shiftDefId == def.id }
        if (count == 0) null else ShiftMetric(def, count, if (def.category == ShiftCategory.LAVORO) assignments.filter { it.shiftDefId == def.id }.sumOf { s ->
            def.copy(startTime = s.customStartTime ?: def.startTime, endTime = s.customEndTime ?: def.endTime).durationHoursFloat.toDouble() + s.overtimeHours
        }.toFloat() else 0f)
    }
    }
    val progress = remember(start, end) { Animatable(0f) }
    LaunchedEffect(progress) { progress.animateTo(1f, spring(dampingRatio = 1f, stiffness = 55f)) }
    val periodKey = "$start/$end"
    Column(Modifier.fillMaxSize().background(ObsidianBackground).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Statistiche", Modifier.weight(1f), color = TextPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Impostazioni", tint = TextPrimary) }
        }
        ReportSegments(mode) { mode = it; picking = null }
        AnimatedVisibility(mode == ReportPeriodMode.PERSONALIZZATO, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically(spring()) + fadeOut()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(from, until).forEachIndexed { index, date ->
                        Surface(Modifier.weight(1f).bouncyClickable { picking = if (picking == index) null else index }, color = ObsidianSurface, shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp), tint = ObsidianPrimary)
                                Spacer(Modifier.width(8.dp))
                                Column { Text(if(index == 0) "Dal" else "Al", fontSize = 11.sp, color = TextSecondary); Text(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
                AnimatedVisibility(picking != null, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically(spring()) + fadeOut()) {
                    ReportDatePicker(if(picking == 0) from else until, { picking = null }) { date ->
                        if(picking == 0) { from = date; if(until < date) until = date } else { until = date; if(from > date) from = date }
                        picking = null
                    }
                }
            }
        }
        AnimatedVisibility(mode != ReportPeriodMode.PERSONALIZZATO) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if(mode == ReportPeriodMode.ANNO) year-- else month = month.minusMonths(1) }) { Icon(Icons.Default.ChevronLeft, "Periodo precedente", tint = TextPrimary) }
                Crossfade(targetState = if(mode == ReportPeriodMode.ANNO) "$year" else "${month.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.titlecase(Locale.ITALIAN) }} ${month.year}", modifier = Modifier.weight(1f), animationSpec = tween(300)) { title -> Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
                IconButton(onClick = { if(mode == ReportPeriodMode.ANNO) year++ else month = month.plusMonths(1) }) { Icon(Icons.Default.ChevronRight, "Periodo successivo", tint = TextPrimary) }
            }
        }
        Surface(shape = RoundedCornerShape(26.dp), color = ObsidianSurface, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.clip(RoundedCornerShape(26.dp))) {
                val ringMetrics = metrics.filter { it.definition.category == ShiftCategory.LAVORO }
                Canvas(Modifier.matchParentSize()) {
                    val total = ringMetrics.sumOf { it.count }.coerceAtLeast(1)
                    val radius = 172.dp.toPx()
                    val centerX = size.width + 24.dp.toPx()
                    val centerY = -24.dp.toPx()
                    var angle = 98.02f
                    ringMetrics.forEachIndexed { index, metric ->
                        val sweep = 73.96f * metric.count / total * progress.value
                        drawArc(metric.definition.color.copy(alpha = if(TurniThemeState.dark) .50f else .48f), angle - if(index == 0) 7f else 0f, sweep + if(index == 0 || index == ringMetrics.lastIndex) 7f else .4f, false,
                            topLeft = androidx.compose.ui.geometry.Offset(centerX-radius, centerY-radius), size = androidx.compose.ui.geometry.Size(radius*2,radius*2), style = Stroke(30.dp.toPx()))
                        angle += sweep
                    }
                }
                Column(Modifier.padding(20.dp)) {
                    Text("Resoconto totale", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    Text(hourLabel(stats.totalHours * progress.value), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("${stats.workedDays} giorni lavorati  ·  ${assignments.size} turni", fontSize = 13.sp, color = TextPrimary)
                    if(stats.totalOvertimeHours > 0) Text("${hourLabel(stats.totalOvertimeHours)} di straordinario", Modifier.padding(top=8.dp), color = ColorTrasferta, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if(metrics.isEmpty()) Surface(shape = RoundedCornerShape(22.dp), color = ObsidianSurface) { Text("Nessun turno nel periodo selezionato", Modifier.fillMaxWidth().padding(24.dp), color = TextSecondary, textAlign = TextAlign.Center) }
        metrics.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { column, metric -> key(metric.definition.id, periodKey) { ReportMetric(metric, rowIndex*2+column, progress.value, Modifier.weight(1f)) } }
                if(row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if(stats.countReperibilita > 0 || stats.countTrasferte > 0) {
            Surface(shape = RoundedCornerShape(22.dp), color = ObsidianSurface, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.NotificationsActive, null, tint = ColorReperibilita)
                    Text("${stats.countReperibilita} reperibilità   ·   ${stats.countTrasferte} trasferte", color = TextPrimary, fontSize = 13.sp)
                }
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = ObsidianSurface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Per lavoro", color = TextSecondary, fontSize = 12.sp)
                ShiftRepository.workProfiles.forEachIndexed { index, work ->
                    val workShifts = assignments.mapNotNull { assigned -> ShiftRepository.getShiftDefinition(assigned.shiftDefId)?.takeIf { it.workProfileId == work.id }?.let { assigned to it } }
                    val hours = workShifts.filter { it.second.category == ShiftCategory.LAVORO }.sumOf { (a,d) -> d.copy(startTime=a.customStartTime?:d.startTime,endTime=a.customEndTime?:d.endTime).durationHoursFloat.toDouble()+a.overtimeHours }.toFloat()
                    if(index > 0) Divider(Modifier.padding(vertical=12.dp), color=ObsidianBorderSubtle)
                    Row(Modifier.padding(top=10.dp), verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(work.colorHex)))
                        Text(work.name, Modifier.weight(1f).padding(horizontal=10.dp), color=TextPrimary, fontWeight=FontWeight.SemiBold, maxLines=2)
                        Text(hourLabel(hours), color=TextPrimary, fontWeight=FontWeight.Bold)
                    }
                    Text("${workShifts.map { it.first.date }.distinct().size} giorni  ·  ${workShifts.size} turni", Modifier.padding(start=20.dp,top=5.dp), color=TextSecondary, fontSize=12.sp)
                }
            }
        }
        Spacer(Modifier.height(118.dp))
    }
}

@Composable
private fun ReportSegments(selected: ReportPeriodMode, onSelect: (ReportPeriodMode) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(ObsidianSurfaceContainer).padding(4.dp)) {
        val tabWidth = maxWidth / 3
        val offset by animateDpAsState(tabWidth * selected.ordinal, spring(dampingRatio=.85f, stiffness=300f))
        Box(Modifier.offset(x=offset).width(tabWidth).height(44.dp).clip(RoundedCornerShape(15.dp)).background(ObsidianSurface))
        Row {
            ReportPeriodMode.values().forEach { mode ->
                Box(Modifier.width(tabWidth).height(44.dp).bouncyClickable { onSelect(mode) }, contentAlignment = Alignment.Center) {
                    Text(mode.label, color = if(mode == selected) ObsidianPrimary else TextSecondary,
                        fontWeight = if(mode == selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ReportMetric(metric: ShiftMetric, index: Int, progress: Float, modifier: Modifier) {
    val background = lerp(metric.definition.color, if(TurniThemeState.dark) Color(0xFF20212A) else Color.White, if(TurniThemeState.dark) .15f else .22f)
    val ink = if(background.luminance() > .179f) Color.Black else Color.White
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay((index.coerceAtMost(7)*35).toLong()); entrance.animateTo(1f,spring(dampingRatio=1f,stiffness=130f)) }
    Box(modifier.height(134.dp).graphicsLayer { alpha=entrance.value; translationY=(1-entrance.value)*16 },contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val path = Path()
            for(step in 0..120) {
                val angle=step/120.0*PI*2
                val radius = when(index%8) { 0 -> .89+.09*cos(angle*4); 1 -> .94; 2 -> .91+.055*cos(angle*6); 3 -> .91+.06*cos(angle*4+PI); 4 -> .88+.09*cos(angle*8); 5 -> .88+.10*cos(angle*4); 6 -> .90+.065*cos(angle*5); else -> .91+.05*cos(angle*3) }
                val x=(.5+cos(angle)*radius*.5).toFloat()*size.width
                val y=(.5+sin(angle)*radius*.5).toFloat()*size.height
                if(step==0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            path.close(); drawPath(path,background)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(metric.definition.name,color=ink,fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
            Text("${(metric.count*progress).roundToInt()}",color=ink,fontSize=28.sp,fontWeight=FontWeight.Bold)
            Text(hourLabel(metric.hours*progress),color=ink,fontSize=12.sp)
        }
    }
}

@Composable
private fun ReportDatePicker(selected: LocalDate, onClose: () -> Unit, onDate: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    Surface(Modifier.fillMaxWidth().padding(bottom=12.dp),shape=RoundedCornerShape(22.dp),color=ObsidianSurface) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={month=month.minusMonths(1)}) { Icon(Icons.Default.ChevronLeft,"Mese precedente",tint=TextPrimary) }
                Text("${month.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)} ${month.year}",Modifier.weight(1f),color=TextPrimary,textAlign=TextAlign.Center,fontWeight=FontWeight.Bold)
                IconButton(onClick={month=month.plusMonths(1)}) { Icon(Icons.Default.ChevronRight,"Mese successivo",tint=TextPrimary) }
                IconButton(onClick=onClose) { Icon(Icons.Default.Close,"Chiudi calendario",tint=TextSecondary) }
            }
            Row { listOf("L","M","M","G","V","S","D").forEach { Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,color=TextSecondary,fontSize=11.sp) } }
            val leading=month.atDay(1).dayOfWeek.value-1
            val cells=((leading+month.lengthOfMonth()+6)/7)*7
            (0 until cells step 7).forEach { week ->
                Row { (0..6).forEach { offset ->
                    val day=week+offset-leading+1
                    val valid=day in 1..month.lengthOfMonth()
                    val date=if(valid) month.atDay(day) else null
                    Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(if(date==selected) ObsidianPrimary else Color.Transparent).then(if(valid) Modifier.bouncyClickable { onDate(date!!) } else Modifier),contentAlignment=Alignment.Center) {
                        if(valid) Text("$day",color=if(date==selected) MaterialTheme.colorScheme.onPrimary else TextPrimary,fontSize=13.sp)
                    }
                } }
            }
        }
    }
}
