package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.turni.calendario.ui.components.SiteIcon
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.WorkProfile
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
private typealias ReportProgress = Animatable<Float, AnimationVector1D>
private data class WorkReportSummary(
    val profile: WorkProfile, val metrics: List<ShiftMetric>, val hours: Float,
    val days: Int, val count: Int, val overtime: Float, val travel: Int, val onCallDays: Int
)
private fun reportNumber(value: Float) = String.format(Locale.ITALIAN, "%.1f", value)
private fun hourLabel(value: Float) = reportNumber(value) + " h"
private val reportInk get() = if(TurniThemeState.dark) Color(0xFFF4F3F7) else Color(0xFF1D2D46)
private val reportMuted get() = if(TurniThemeState.dark) Color(0xFFBEC1CD) else Color(0xFF4E6078)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ReportScreen(active:Boolean=true,onOpenSettings: () -> Unit = {}) {
    var mode by remember { mutableStateOf(ReportPeriodMode.MESE) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var year by remember { mutableStateOf(LocalDate.now().year) }
    var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var until by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf<Int?>(null) }
    var draftFrom by remember { mutableStateOf(from) }
    var draftUntil by remember { mutableStateOf(until) }
    var pickerField by remember { mutableStateOf(0) }
    var dateBottom by remember { mutableStateOf(150f) }
    var rootTop by remember { mutableStateOf(0f) }
    LaunchedEffect(active) { if(!active)picking=null }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val start = when (mode) { ReportPeriodMode.MESE -> month.atDay(1); ReportPeriodMode.ANNO -> LocalDate.of(year, 1, 1); else -> from }
    val end = when (mode) { ReportPeriodMode.MESE -> month.atEndOfMonth(); ReportPeriodMode.ANNO -> LocalDate.of(year, 12, 31); else -> until }
    // Observe actual collections, not their size: replacing a shift must refresh every metric.
    val calendarSnapshot = ShiftRepository.calendarShifts.mapValues { it.value.toList() }
    val extrasSnapshot = ShiftRepository.dayExtraInfo.toMap()
    val definitions = ShiftRepository.shiftDefinitions.toList()
    val assignments = remember(start, end, calendarSnapshot) {
        calendarSnapshot.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.flatten()
    }
    val computedStats by produceState<com.turni.calendario.repository.ComprehensiveReportStats?>(null,start, end, calendarSnapshot, extrasSnapshot, definitions,
        ShiftRepository.reperibilitaConfig, ShiftRepository.rotations.toList(), ShiftRepository.activeRotationId) {
        val snapshot=androidx.compose.runtime.snapshots.Snapshot.takeSnapshot()
        try {
            value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                snapshot.enter{ShiftRepository.getComprehensiveReportStats(start,end)}
            }
        } finally {snapshot.dispose()}
    }
    val stats=computedStats?:com.turni.calendario.repository.ComprehensiveReportStats(0,0f,0f,0,0,0,0,0,0,emptyMap())
    val metrics = remember(assignments, definitions) { reportMetrics(assignments,definitions) }
    val profiles = ShiftRepository.workProfiles.toList()
    val workSummaries = remember(assignments, definitions, metrics, profiles, extrasSnapshot, ShiftRepository.reperibilitaConfig) {
        val byId = definitions.associateBy { it.id }
        val byWork = assignments.mapNotNull { assigned -> byId[assigned.shiftDefId]?.let { assigned to it } }
            .groupBy { it.second.workProfileId }
        val metricsByWork = metrics.groupBy { it.definition.workProfileId }
        profiles.map { work ->
            val shifts = byWork[work.id].orEmpty()
            val workMetrics = metricsByWork[work.id].orEmpty()
            WorkReportSummary(work, workMetrics, workMetrics.sumOf { it.hours.toDouble() }.toFloat(),
                shifts.filter { it.second.category == ShiftCategory.LAVORO }.map { it.first.date }.distinct().size,
                shifts.size, shifts.sumOf { it.first.overtimeHours.toDouble() }.toFloat(),
                shifts.count { it.first.isTrasferta }, shifts.map { it.first.date }.distinct().count { ShiftRepository.isOnCall(it) })
        }
    }
    val progress = remember(mode, start, end) { Animatable(0f) }
    LaunchedEffect(progress,active,computedStats) {
        if(active&&computedStats!=null){progress.snapTo(0f);progress.animateTo(1f,spring(dampingRatio=1f,stiffness=55f))}
        else progress.snapTo(1f)
    }
    val periodKey = "$mode/$start/$end"
    val shapeKeys = metrics.map { "model:"+it.definition.id } + if(stats.countReperibilita>0) listOf("oncall") else emptyList()
    val shapePlan = remember(shapeKeys) { reportShapePlan(shapeKeys) }
    Box(Modifier.fillMaxSize().background(ObsidianBackground).onGloballyPositioned { rootTop=it.boundsInRoot().top }) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal=16.dp)) {
        item("period-controls") {
        Column {
        Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=14.dp).heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Statistiche",Modifier.weight(1f).padding(start=4.dp),color=reportInk,fontSize=23.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
            Surface(shape=RoundedCornerShape(14.dp),color=ObsidianSurface,modifier=Modifier.size(40.dp)) {
                Box(Modifier.bouncyClickable(onClick=onOpenSettings),contentAlignment=Alignment.Center) { SiteIcon("settings","Impostazioni",Modifier.size(21.dp),tint=reportInk) }
            }
        }
        ReportSegments(mode) { mode=it; picking=null }
        AnimatedVisibility(mode==ReportPeriodMode.PERSONALIZZATO,enter=expandVertically(spring())+fadeIn(),exit=shrinkVertically(spring())+fadeOut()) {
            Column(Modifier.padding(bottom=8.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=12.dp).onGloballyPositioned { dateBottom=it.boundsInRoot().bottom-rootTop+6*density.density },horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(draftFrom,draftUntil).forEachIndexed { index,date ->
                        val iconMotion=remember { Animatable(0f) }
                        Column(Modifier.weight(1f)) {
                            Text(if(index==0) "Dal" else "Al",Modifier.padding(start=4.dp,bottom=5.dp),fontSize=11.sp,color=reportMuted)
                            Surface(shape=RoundedCornerShape(13.dp),color=if(picking==index) ObsidianSurfaceHigh else ObsidianSurface,modifier=Modifier.fillMaxWidth().bouncyClickable { pickerField=index;picking=if(picking==index)null else index;scope.launch { iconMotion.snapTo(0f);iconMotion.animateTo(1f,tween(560));iconMotion.snapTo(0f) } }) {
                                Row(Modifier.padding(horizontal=9.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Text(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),Modifier.weight(1f),fontSize=12.sp,color=reportInk)
                                    SiteIcon("calendar-days",null,Modifier.size(14.dp),tint=reportInk,progress=iconMotion.value)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(34.dp).bouncyClickable { from=draftFrom;until=draftUntil;picking=null },contentAlignment=Alignment.Center) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) { SiteIcon("check",null,Modifier.size(15.dp),tint=ObsidianPrimary);Text("Applica periodo",fontSize=11.sp,color=ObsidianPrimary) }
                }
            }
        }
        AnimatedVisibility(mode!=ReportPeriodMode.PERSONALIZZATO) {
            Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=8.dp).height(50.dp),verticalAlignment=Alignment.CenterVertically) {
                ReportIconButton("chevron-left","Periodo precedente") { if(mode==ReportPeriodMode.ANNO)year-- else month=month.minusMonths(1) }
                Crossfade(if(mode==ReportPeriodMode.ANNO) "$year" else month.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN),Modifier.weight(1f),tween(420)) { title ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                        Text(title,fontSize=18.sp,fontWeight=FontWeight.Bold,color=reportInk)
                        if(mode==ReportPeriodMode.MESE)Text("${month.year}",Modifier.padding(start=8.dp),fontSize=11.sp,color=reportMuted)
                    }
                }
                ReportIconButton("chevron-right","Periodo successivo") { if(mode==ReportPeriodMode.ANNO)year++ else month=month.plusMonths(1) }
            }
        }
        }
        }
        item("hero:$periodKey") { ReportHero(stats.totalHours,stats.workedDays,assignments.size,stats.totalOvertimeHours,metrics,progress) }
        item("hero-gap") { Spacer(Modifier.height(12.dp)) }
        val metricRows = metrics.chunked(2)
        items(metricRows.size, key = { "metrics:$periodKey:$it" }) { rowIndex ->
            val row = metricRows[rowIndex]
            Row(Modifier.fillMaxWidth().padding(bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { column,metric ->
                    key(metric.definition.id) {
                    ReportMetric(metric.definition.name,metric.definition.color,metric.count,metric.hours,
                        metric.definition.isAllDay && metric.hours==0f,shapePlan[rowIndex*2+column],
                        rowIndex*2+column,progress,Modifier.weight(1f))
                    }
                }
                if(row.size==1) {
                    if(stats.countReperibilita>0) ReportMetric("Reperibilità",ColorReperibilita,stats.countReperibilita,0f,false,
                        shapePlan.last(),metrics.size,progress,Modifier.weight(1f),true)
                    else Spacer(Modifier.weight(1f))
                }
            }
        }
        if(stats.countReperibilita>0 && metrics.size%2==0) item("oncall:$periodKey") {
            Row(Modifier.fillMaxWidth().padding(bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                ReportMetric("Reperibilità",ColorReperibilita,stats.countReperibilita,0f,false,
                    shapePlan.last(),metrics.size,progress,Modifier.weight(1f),true)
                Spacer(Modifier.weight(1f))
            }
        }
        if(metrics.isEmpty() && stats.countReperibilita==0) item("empty") {
            Surface(shape=RoundedCornerShape(18.dp),color=ObsidianSurface) {
                Text("Nessun turno nel periodo selezionato",Modifier.fillMaxWidth().padding(20.dp),fontSize=12.sp,color=reportMuted,textAlign=TextAlign.Center)
            }
        }
        if(profiles.size>1) item("work-note") {
            Text("I giorni lavorati del resoconto sono date uniche, anche con più lavori nello stesso giorno.",
                Modifier.padding(horizontal=5.dp,vertical=10.dp),fontSize=11.sp,lineHeight=18.sp,color=reportMuted)
        }
        item("work-heading") {
            Spacer(Modifier.height(4.dp))
            Surface(shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),color=ObsidianSurface,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start=14.dp,end=14.dp,top=16.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("Per lavoro",Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Bold,color=reportInk)
                    Text("${profiles.size} profili",fontSize=10.sp,color=reportMuted)
                }
            }
        }
        itemsIndexed(workSummaries,key={_,summary -> "work:${summary.profile.id}"}) { index,summary ->
            ReportWorkSection(summary,index,workSummaries.lastIndex,ShiftRepository.activeWorkProfileId,progress)
        }
        item("dock-clearance") { Spacer(Modifier.height(118.dp)) }
    }
    AnimatedVisibility(picking!=null,modifier=Modifier.zIndex(10f).padding(horizontal=16.dp).offset { IntOffset(0,dateBottom.roundToInt()) }.widthIn(max=320.dp),enter=fadeIn(tween(380))+scaleIn(spring(dampingRatio=.88f,stiffness=180f),initialScale=.18f,transformOrigin=TransformOrigin(if(pickerField==0).2f else .8f,0f))+slideInVertically(spring()){ -20 },exit=fadeOut(tween(220))+scaleOut(tween(260),targetScale=.2f,transformOrigin=TransformOrigin(if(pickerField==0).2f else .8f,0f))) {
        ReportDatePicker(if(pickerField==0)draftFrom else draftUntil,{picking=null}) { date -> if(pickerField==0){draftFrom=date;if(draftUntil<date)draftUntil=date}else{draftUntil=date;if(draftFrom>date)draftFrom=date};picking=null }
    }
    }
}

@Composable
private fun ReportIconButton(icon:String,label:String,onClick:()->Unit) {
    val motion=remember{Animatable(0f)};val scope=rememberCoroutineScope()
    Box(Modifier.size(40.dp).bouncyClickable { onClick();scope.launch { motion.snapTo(0f);motion.animateTo(1f,tween(460));motion.snapTo(0f) } },contentAlignment=Alignment.Center) {
        SiteIcon(icon,label,Modifier.size(20.dp).graphicsLayer { scaleX=1f+.10f*sin(motion.value*PI).toFloat();scaleY=scaleX },tint=reportInk,progress=motion.value)
    }
}
@Composable
private fun ReportWorkTotal(value:Float,label:String,animation:ReportProgress,modifier:Modifier,decimal:Boolean=false,suffix:String="") {
    Column(modifier) {
        Row(verticalAlignment=Alignment.Bottom) {
            ReportAnimatedValue(value,animation,decimal,fontSize=20.sp,fontWeight=FontWeight.Bold)
            if(suffix.isNotEmpty())Text(suffix,fontSize=10.sp,color=reportMuted)
        }
        Text(label,Modifier.padding(top=3.dp),fontSize=11.sp,color=reportMuted)
    }
}

/** Draw-only count-up; final text reserves stable space and supplies accessible semantics. */
@Composable
private fun ReportAnimatedValue(value:Float,animation:ReportProgress,decimal:Boolean=false,suffix:String="",
    modifier:Modifier=Modifier,fontSize:TextUnit,color:Color=reportInk,fontWeight:FontWeight?=null,
    lineHeight:TextUnit=TextUnit.Unspecified,letterSpacing:TextUnit=TextUnit.Unspecified) {
    val finalLabel=remember(value,decimal,suffix){(if(decimal)reportNumber(value)else value.roundToInt().toString())+suffix}
    val density=LocalDensity.current
    val paint=remember(fontSize,color,fontWeight,letterSpacing,density){android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{
        textSize=with(density){fontSize.toPx()};this.color=color.toArgb()
        typeface=android.graphics.Typeface.create("sans-serif",if((fontWeight?:FontWeight.Normal).weight>=600)android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        textAlign=android.graphics.Paint.Align.CENTER
        if(letterSpacing!=TextUnit.Unspecified)this.letterSpacing=letterSpacing.value/fontSize.value
    }}
    Box(modifier){
        Text(finalLabel,Modifier.graphicsLayer{alpha=0f},fontSize=fontSize,color=color,fontWeight=fontWeight,lineHeight=lineHeight,letterSpacing=letterSpacing)
        Canvas(Modifier.matchParentSize()){
            val step=(value*animation.value*(if(decimal)10f else 1f)).roundToInt()
            val label=(if(decimal)reportNumber(step/10f)else step.toString())+suffix
            drawContext.canvas.nativeCanvas.drawText(label,size.width/2f,size.height/2f-(paint.ascent()+paint.descent())/2f,paint)
        }
    }
}

@Composable
private fun ReportWorkSection(summary:WorkReportSummary,index:Int,lastIndex:Int,activeWorkId:String,animation:ReportProgress) {
    val work=summary.profile
    Surface(shape=RoundedCornerShape(bottomStart=if(index==lastIndex)24.dp else 0.dp,bottomEnd=if(index==lastIndex)24.dp else 0.dp),
        color=ObsidianSurface,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start=14.dp,end=14.dp,bottom=if(index==lastIndex)16.dp else 0.dp)) {
            if(index>0)Divider(Modifier.padding(top=16.dp),color=ObsidianBorderSubtle)
            Row(Modifier.padding(top=12.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(ObsidianSurfaceHigh),contentAlignment=Alignment.Center) {
                    SiteIcon(work.iconKey.takeIf { it.isNotBlank() } ?: "briefcase-business",null,Modifier.size(18.dp),tint=ObsidianPrimary)
                }
                Column {
                    Text(work.name,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=reportInk)
                    if(work.id==activeWorkId)Text("Calendario in uso",fontSize=11.sp,color=reportMuted)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom=11.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                ReportWorkTotal(summary.hours,"Ore lavorate",animation,Modifier.weight(1.3f),true," h")
                ReportWorkTotal(summary.days.toFloat(),"Giorni",animation,Modifier.weight(1f))
                ReportWorkTotal(summary.count.toFloat(),"Turni",animation,Modifier.weight(1f))
            }
            Column(Modifier.padding(top=10.dp,bottom=14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                summary.metrics.forEach { metric -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(metric.definition.color))
                    Text(metric.definition.name,Modifier.weight(1f),fontSize=11.sp,color=reportInk)
                    Text("${metric.count}",fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=reportInk)
                    Text(if(metric.definition.isAllDay && metric.hours==0f) "—" else hourLabel(metric.hours),Modifier.width(48.dp),fontSize=11.sp,color=reportMuted,textAlign=TextAlign.End)
                } }
            }
            if(summary.overtime>0)ReportWorkExtra("Straordinari",hourLabel(summary.overtime))
            if(summary.travel>0)ReportWorkExtra("Trasferte","${summary.travel}")
            if(summary.onCallDays>0)ReportWorkExtra("Reperibilità","${summary.onCallDays} giorni")
            if(summary.count==0)Text("Nessun turno nel periodo",Modifier.padding(vertical=10.dp),fontSize=10.sp,color=reportMuted)
        }
    }
}
@Composable
private fun ReportWorkExtra(label:String,value:String) { Row(Modifier.fillMaxWidth().padding(bottom=6.dp)) { Text(label,Modifier.weight(1f),fontSize=11.sp,color=reportMuted);Text(value,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=reportInk) } }

@Composable
private fun ReportHero(hours:Float,days:Int,assignments:Int,overtime:Float,metrics:List<ShiftMetric>,animation:ReportProgress) {
    Surface(shape=RoundedCornerShape(23.dp),color=ObsidianSurface,modifier=Modifier.fillMaxWidth().graphicsLayer { alpha=animation.value;translationY=(1-animation.value)*12;scaleX=.98f+.02f*animation.value;scaleY=scaleX }) {
        Box(Modifier.clip(RoundedCornerShape(23.dp))) {
            // Every assigned user model participates, including rest, leave and all-day models.
            val distribution=metrics
            // Preserve the three original paths exactly; only their reveal is shared.
            // A single head crosses each path in order, including both clipped continuations.
            val ring=remember { PathParser().parsePathString("M38.7048 0 A100 100 0 0 0 190 117.4616").toPath() }
            val leading=remember { PathParser().parsePathString("M56.7053 -29.8082 A100 100 0 0 0 38.7048 0").toPath() }
            val trailing=remember { PathParser().parsePathString("M190 117.4616 A100 100 0 0 0 214.4167 92.6347").toPath() }
            val ringMeasure=remember(ring){PathMeasure().apply{setPath(ring,false)}}
            val leadingMeasure=remember(leading){PathMeasure().apply{setPath(leading,false)}}
            val trailingMeasure=remember(trailing){PathMeasure().apply{setPath(trailing,false)}}
            val measures=remember(leadingMeasure,ringMeasure,trailingMeasure){listOf(leadingMeasure,ringMeasure,trailingMeasure)}
            val fullLength=leadingMeasure.length+ringMeasure.length+trailingMeasure.length
            val boundaries=remember(distribution,leadingMeasure,ringMeasure,trailingMeasure) {
                reportRingBoundaries(distribution.map { it.count },leadingMeasure.length,ringMeasure.length,trailingMeasure.length)
            }
            // Reuse native paths throughout the reveal instead of allocating per frame/segment.
            val segmentBuffer=remember { Path() }
            val ringStroke=remember { Stroke(26f) }
            Canvas(Modifier.matchParentSize()) {
                val head=fullLength*animation.value.coerceIn(0f,1f)
                val densityScale=1.dp.toPx()
                translate(left=size.width-190*densityScale) { scale(densityScale,densityScale,pivot=Offset.Zero) {
                    drawPath(ring,if(TurniThemeState.dark)Color(0xFF383B46).copy(alpha=.23f) else Color(0xFFE5EBF3).copy(alpha=.23f),style=ringStroke)
                    distribution.forEachIndexed { index,metric ->
                        val boundary=boundaries[index]
                        val endDistance=minOf(head,boundary.endDistance)
                        if(endDistance>boundary.startDistance) {
                            var offset=0f
                            measures.forEach { piece ->
                                val localStart=(boundary.startDistance-offset).coerceAtLeast(0f)
                                val localEnd=(endDistance-offset).coerceAtMost(piece.length)
                                if(localEnd>localStart) {
                                    segmentBuffer.reset()
                                    piece.getSegment(localStart,localEnd,segmentBuffer,true)
                                    drawPath(segmentBuffer,metric.definition.color.copy(alpha=if(TurniThemeState.dark).46f else .38f),style=ringStroke)
                                }
                                offset+=piece.length
                            }
                        }
                    }
                } }
            }
            Column(Modifier.padding(start=16.dp,end=16.dp,top=16.dp,bottom=12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Resoconto totale",Modifier.weight(1f),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=reportInk)
                    SiteIcon("layers-2",null,Modifier.size(12.dp),tint=reportMuted);Text("Tutti i lavori",Modifier.padding(start=4.dp),fontSize=10.sp,color=reportMuted)
                }
                Row(Modifier.padding(top=12.dp,bottom=11.dp),verticalAlignment=Alignment.Bottom) {
                    ReportAnimatedValue(hours,animation,true,fontSize=38.sp,lineHeight=40.sp,letterSpacing=(-1).sp,fontWeight=FontWeight.Bold)
                    Text("ore lavorate",Modifier.padding(start=7.dp,bottom=4.dp),fontSize=11.sp,color=reportMuted)
                }
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) { ReportAnimatedValue(days.toFloat(),animation,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Giorni lavorati",Modifier.padding(start=6.dp),fontSize=10.sp,color=reportMuted) }
                    Box(Modifier.width(1.dp).height(20.dp).background(ObsidianBorderSubtle))
                    Row(Modifier.weight(1f).padding(start=13.dp),verticalAlignment=Alignment.CenterVertically) { ReportAnimatedValue(assignments.toFloat(),animation,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Turni totali",Modifier.padding(start=6.dp),fontSize=10.sp,color=reportMuted) }
                }
                Divider(color=ObsidianBorderSubtle)
                distribution.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach { metric -> Row(Modifier.weight(1f).heightIn(min=27.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(metric.definition.color))
                        Text(metric.definition.name,Modifier.weight(1f),fontSize=10.sp,color=reportMuted,maxLines=2)
                        Text("${metric.count}",fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=reportInk)
                    } };if(row.size==1)Spacer(Modifier.weight(1f))
                } }
                if(overtime>0)Text("Inclusi ${hourLabel(overtime)} di straordinario",Modifier.padding(top=6.dp),fontSize=10.sp,color=reportMuted)
            }
        }
    }
}

@Composable
private fun ReportSegments(selected:ReportPeriodMode,onSelect:(ReportPeriodMode)->Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ObsidianSurfaceHigh).padding(4.dp)) {
        val tabWidth=maxWidth/3
        val offset by animateDpAsState(tabWidth*selected.ordinal,spring(dampingRatio=.86f,stiffness=240f))
        Box(Modifier.offset(x=offset).width(tabWidth).height(42.dp).clip(RoundedCornerShape(14.dp)).background(ObsidianPrimary))
        Row { ReportPeriodMode.values().forEach { mode -> Box(Modifier.width(tabWidth).height(42.dp).bouncyClickable { onSelect(mode) },contentAlignment=Alignment.Center) {
            Text(mode.label,color=if(mode==selected)MaterialTheme.colorScheme.onPrimary else reportMuted,fontWeight=FontWeight.Medium,fontSize=12.sp,maxLines=1)
        } } }
    }
}

@Composable
private fun ReportMetric(name:String,color:Color,count:Int,hours:Float,allDay:Boolean,shape:String,index:Int,animation:ReportProgress,modifier:Modifier,onCall:Boolean=false) {
    val surface=if(TurniThemeState.dark)Color(0xFF1A1C22)else Color.White
    val background=lerp(surface,color,if(onCall).24f else if(TurniThemeState.dark).28f else .38f)
    val path=remember(shape){reportShapePath(shape)}
    Box(modifier.aspectRatio(1f).graphicsLayer{val entrance=reportEntranceProgress(animation.value,index);alpha=entrance;translationY=(1-entrance)*12;scaleX=.82f+.18f*entrance;scaleY=scaleX;rotationZ=(if(index%2==0)-2f else 2f)*(1-entrance)},contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize()){scale(size.width/160f,size.height/160f,pivot=Offset.Zero){if(shape=="cushion")rotate(-12f,Offset(80f,80f)){drawPath(path,background)}else drawPath(path,background)}}
        Column(Modifier.fillMaxWidth(.76f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(name,color=reportInk,fontSize=12.sp,lineHeight=15.sp,fontWeight=FontWeight.SemiBold,maxLines=3,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
            ReportAnimatedValue(count.toFloat(),animation,fontSize=30.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1.3).sp)
            if(onCall||allDay)Text(if(onCall)"giornate" else "Giornata intera",color=reportMuted,fontSize=11.sp,lineHeight=14.sp,fontWeight=FontWeight.Medium)
            else ReportAnimatedValue(hours,animation,true," h",fontSize=11.sp,color=reportMuted,lineHeight=14.sp,fontWeight=FontWeight.Medium)
        }
    }
}

@Composable
private fun ReportDatePicker(selected:LocalDate,onClose:()->Unit,onDate:(LocalDate)->Unit) {
    var month by remember(selected){mutableStateOf(YearMonth.from(selected))}
    Surface(Modifier.fillMaxWidth().shadow(16.dp,RoundedCornerShape(20.dp)),shape=RoundedCornerShape(20.dp),color=ObsidianSurface) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Seleziona data",fontSize=9.sp,color=reportMuted);Text("${month.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)} ${month.year}",fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold,color=reportInk) }
                ReportIconButton("chevron-left","Mese precedente"){month=month.minusMonths(1)}
                ReportIconButton("chevron-right","Mese successivo"){month=month.plusMonths(1)}
                ReportIconButton("x","Chiudi calendario",onClose)
            }
            Text(selected.format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.ITALIAN)),Modifier.padding(vertical=8.dp),fontSize=13.sp,color=reportInk)
            Row { listOf("L","M","M","G","V","S","D").forEach{Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,color=reportMuted,fontSize=10.sp)} }
            val leading=month.atDay(1).dayOfWeek.value-1;val cells=((leading+month.lengthOfMonth()+6)/7)*7
            (0 until cells step 7).forEach{week->Row{(0..6).forEach{offset->
                val day=week+offset-leading+1;val valid=day in 1..month.lengthOfMonth();val date=if(valid)month.atDay(day)else null
                Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(9.dp)).background(if(date==selected)ObsidianPrimary else Color.Transparent).then(if(valid)Modifier.bouncyClickable{onDate(date!!)}else Modifier),contentAlignment=Alignment.Center){if(valid)Text("$day",fontSize=12.sp,color=if(date==selected)MaterialTheme.colorScheme.onPrimary else reportInk)}
            }}}
        }
    }
}

/** One period clock also advances widgets that have not entered the lazy viewport yet. */
internal fun reportEntranceProgress(progress:Float,index:Int):Float =
    (progress*1.5f-index.coerceIn(0,5)*.045f).coerceIn(0f,1f)
