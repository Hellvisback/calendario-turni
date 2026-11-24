package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import com.turni.calendario.ui.components.SiteIcon
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.WorkProfile
import com.turni.calendario.model.dayTagMetrics
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
    val days: Int, val count: Int, val overtime: Float, val permission: Float,
    val travel: Int, val onCallDays: Int
)
private fun reportNumber(value: Float) = String.format(Locale.ITALIAN, "%.1f", value)
private fun hourLabel(value: Float) = reportNumber(value) + " h"
private val reportInk get() = TextPrimary
private val reportMuted get() = TextSecondary

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ReportScreen(
    active: Boolean = true,
    onOpenSettings: () -> Unit = {},
    onOpenDate: (LocalDate) -> Unit = {},
    onOpenTurni: (() -> Unit)? = null,
) {
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
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val pickerWidth = minOf(280, LocalConfiguration.current.screenWidthDp - 32).dp
    val leftPickerX = 16.dp
    val rightPickerX = (screenWidthDp - pickerWidth - 16.dp).coerceAtLeast(16.dp)
    val pickerX = remember { Animatable(leftPickerX, Dp.VectorConverter) }
    var wasPickingOpen by remember { mutableStateOf(false) }
    LaunchedEffect(picking, pickerField, rightPickerX) {
        val target = if (pickerField == 0) leftPickerX else rightPickerX
        if (picking != null) {
            if (!wasPickingOpen) {
                pickerX.snapTo(target)
            } else {
                pickerX.animateTo(target, spring(1f, 380f))
            }
            wasPickingOpen = true
        } else {
            wasPickingOpen = false
        }
    }
    val start = when (mode) { ReportPeriodMode.MESE -> month.atDay(1); ReportPeriodMode.ANNO -> LocalDate.of(year, 1, 1); else -> from }
    val end = when (mode) { ReportPeriodMode.MESE -> month.atEndOfMonth(); ReportPeriodMode.ANNO -> LocalDate.of(year, 12, 31); else -> until }
    // Observe actual collections, not their size: replacing a shift must refresh every metric.
    val activeProfileId = ShiftRepository.activeWorkProfileId
    val calendarSnapshot = ShiftRepository.calendarShifts.mapValues { it.value.toList() }
    val extrasByProfileSnapshot = ShiftRepository.dayExtraInfoByProfile.mapValues { it.value.toMap() }
    val extrasSnapshot = extrasByProfileSnapshot[activeProfileId].orEmpty()
    val reperibilitaByProfileSnapshot = ShiftRepository.reperibilitaByProfile.toMap()
    val reperibilitaConfig = reperibilitaByProfileSnapshot[activeProfileId] ?: ShiftRepository.reperibilitaConfigFor(activeProfileId)
    val definitions = ShiftRepository.shiftDefinitions.toList()
    val activeDefinitions = remember(definitions, activeProfileId) { definitions.filter { it.workProfileId == activeProfileId } }
    val dayTags = ShiftRepository.dayTagsFor(activeProfileId)
    val tagMetrics = remember(start, end, extrasSnapshot, dayTags) { dayTagMetrics(dayTags, extrasSnapshot, start, end) }
    val allRules = ShiftRepository.statisticsRules.toList()
    val rules = ShiftRepository.statisticsRulesFor(activeProfileId)
    val assignments = remember(start, end, calendarSnapshot, definitions, allRules, ShiftRepository.appPreferences, ShiftRepository.enabledHolidayIds) {
        val byId = definitions.associateBy { it.id }
        val rulesByProfile = definitions.map { it.workProfileId }.distinct().associateWith { ShiftRepository.statisticsRulesFor(it) }
        calendarSnapshot.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.flatten().mapNotNull { assigned ->
            val profileId = byId[assigned.shiftDefId]?.workProfileId
            val profileRules = if (profileId != null) rulesByProfile[profileId] ?: emptyList() else rules
            ShiftRepository.statisticsEntry(assigned, byId, profileRules)
        }
    }
    val computedStats by key(start, end, allRules, calendarSnapshot, extrasByProfileSnapshot, definitions, ShiftRepository.appPreferences, ShiftRepository.enabledHolidayIds,
        reperibilitaByProfileSnapshot, reperibilitaConfig, ShiftRepository.rotations.toList(), ShiftRepository.activeRotationIdByProfile.toMap(), activeProfileId) {
        // A changed period gets its own loading state; never render the previous totals.
        produceState<com.turni.calendario.repository.ComprehensiveReportStats?>(null) {
        val snapshot=androidx.compose.runtime.snapshots.Snapshot.takeSnapshot()
        try {
            value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                snapshot.enter{ShiftRepository.getComprehensiveReportStats(start,end)}
            }
        } finally {snapshot.dispose()}
        }
    }
    val stats=computedStats?:com.turni.calendario.repository.ComprehensiveReportStats(0,0f,0f,0,0,0,0,0,0,emptyMap())
    val metrics = remember(assignments, definitions) { reportMetrics(assignments,definitions) }
    val profiles = ShiftRepository.workProfiles.toList()
    val workSummaries = remember(assignments, definitions, metrics, profiles, extrasByProfileSnapshot, reperibilitaByProfileSnapshot) {
        val byId = definitions.associateBy { it.id }
        val byWork = assignments.mapNotNull { assigned -> byId[assigned.shiftDefId]?.let { assigned to it } }
            .groupBy { it.second.workProfileId }
        val metricsByWork = metrics.groupBy { it.definition.workProfileId }
        profiles.map { work ->
            val shifts = byWork[work.id].orEmpty()
            val workMetrics = metricsByWork[work.id].orEmpty()
            val permissionTotal = shifts.sumOf { (assigned, def) ->
                if (def.category == ShiftCategory.LAVORO && !def.isAllDay) {
                    val baseHours = def.copy(
                        startTime = assigned.customStartTime ?: def.startTime,
                        endTime = assigned.customEndTime ?: def.endTime,
                    ).durationHoursFloat
                    assigned.effectivePermissionHours().coerceIn(0f, baseHours).toDouble()
                } else 0.0
            }.toFloat()
            WorkReportSummary(work, workMetrics, workMetrics.sumOf { it.hours.toDouble() }.toFloat(),
                shifts.filter { it.second.category == ShiftCategory.LAVORO }.map { it.first.date }.distinct().size,
                shifts.size, shifts.sumOf { it.first.effectiveOvertimeHours().toDouble() }.toFloat(), permissionTotal,
                shifts.count { it.first.isTrasferta }, shifts.map { it.first.date }.distinct().count { ShiftRepository.isOnCall(it, work.id) })
        }
    }
    // Reset atomically with activation/data, before anything can be drawn. Resetting
    // a retained clock in LaunchedEffect flashed completed charts for one frame.
    val progress = remember(mode, start, end, active, computedStats) { Animatable(if(active)0f else 1f) }
    LaunchedEffect(progress) {
        if(active&&computedStats!=null)progress.animateTo(1f,spring(dampingRatio=1f,stiffness=55f))
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
                            Surface(shape=RoundedCornerShape(13.dp),color=if(picking==index) ObsidianSurfaceHigh else ObsidianSurface,modifier=Modifier.fillMaxWidth().bouncyClickable {
                                val wasClosed = picking == null
                                pickerField = index
                                val nextPicking = if (picking == index) null else index
                                if (wasClosed && nextPicking != null) {
                                    scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                        pickerX.snapTo(if (index == 0) leftPickerX else rightPickerX)
                                    }
                                }
                                picking = nextPicking
                                scope.launch { iconMotion.snapTo(0f);iconMotion.animateTo(1f,tween(560));iconMotion.snapTo(0f) }
                            }) {
                                Row(Modifier.padding(horizontal=9.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Text(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),Modifier.weight(1f),fontSize=12.sp,color=reportInk)
                                    SiteIcon("calendar-days",null,Modifier.size(14.dp),tint=reportInk,progress=iconMotion.value)
                                }
                            }
                        }
                    }
                }
                val hasPendingRange = draftFrom != from || draftUntil != until
                val applyContentColor = if (hasPendingRange) OnBrand else Color(currentTurniPalette.onPrimaryContainer)
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (hasPendingRange) ObsidianPrimary else ObsidianPrimaryContainer,
                    border = if (hasPendingRange) null else BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .bouncyClickable { from = draftFrom; until = draftUntil; picking = null }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            SiteIcon("check", null, Modifier.size(16.dp), tint = applyContentColor)
                            Text(
                                "Applica periodo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = applyContentColor
                            )
                        }
                    }
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
        item("hero:$periodKey") { ReportHero(stats.totalHours,stats.workedDays,assignments.size,stats.totalOvertimeHours,stats.totalPermissionHours,metrics,progress) }
        item("hero-gap") { Spacer(Modifier.height(12.dp)) }
        val metricsPerRow = if (density.fontScale > 1.3f) 1 else 2
        val metricRows = metrics.chunked(metricsPerRow)
        items(metricRows.size, key = { "metrics:$periodKey:$it" }) { rowIndex ->
            val row = metricRows[rowIndex]
            Row(Modifier.fillMaxWidth().padding(bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { column,metric ->
                    key(metric.definition.id) {
                    ReportMetric(metric.definition.name,metric.definition.color,metric.count,metric.hours,
                        metric.definition.isAllDay && metric.hours==0f,shapePlan[rowIndex*metricsPerRow+column],
                        rowIndex*metricsPerRow+column,progress,Modifier.weight(1f),definitionId=metric.definition.id)
                    }
                }
                if(row.size==1 && metricsPerRow==2) {
                    if(stats.countReperibilita>0) ReportMetric("Reperibilità",ColorReperibilita,stats.countReperibilita,0f,false,
                        shapePlan.last(),metrics.size,progress,Modifier.weight(1f),true)
                    else Spacer(Modifier.weight(1f))
                }
            }
        }
        if(stats.countReperibilita>0 && (metricsPerRow==1 || metrics.size%2==0)) item("oncall:$periodKey") {
            Row(Modifier.fillMaxWidth().padding(bottom=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                ReportMetric("Reperibilità",ColorReperibilita,stats.countReperibilita,0f,false,
                    shapePlan.last(),metrics.size,progress,Modifier.weight(1f),true)
                if(metricsPerRow==2)Spacer(Modifier.weight(1f))
            }
        }
        if (activeDefinitions.isEmpty() && metrics.isEmpty() && stats.countReperibilita == 0) item("empty-models") {
            Surface(shape = RoundedCornerShape(20.dp), color = ObsidianSurface, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(ObsidianPrimaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        SiteIcon("layers", null, Modifier.size(21.dp), tint = BrandInk)
                    }
                    Text(
                        text = "Nessun modello di turno configurato",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = reportInk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Crea i tuoi primi modelli di turno per \"${ShiftRepository.activeWorkProfile.name}\" nella scheda Turni e inizia a registrarli nel calendario per vedere le statistiche.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = reportMuted,
                        textAlign = TextAlign.Center,
                    )
                    if (onOpenTurni != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianPrimary,
                            modifier = Modifier.padding(top = 4.dp).bouncyClickable(onClick = onOpenTurni),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                SiteIcon("plus", null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                Text(
                                    text = "Crea modelli di turno",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        } else if (metrics.isEmpty() && stats.countReperibilita == 0) item("empty") {
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
        if(dayTags.isNotEmpty())dayTagReport(tagMetrics,periodKey,onOpenDate)
        item("dock-clearance") { Spacer(Modifier.height(118.dp)) }
    }
    AnimatedVisibility(picking!=null,modifier=Modifier.zIndex(10f).offset { IntOffset(pickerX.value.roundToPx(),dateBottom.roundToInt()) }.width(pickerWidth),
        enter=fadeIn(tween(260))+scaleIn(tween(420,easing=CubicBezierEasing(.16f,1f,.3f,1f)),initialScale=.32f,transformOrigin=TransformOrigin(if(pickerField==0).25f else .75f,0f))+slideInVertically(tween(380)){ -it/6 },
        exit=fadeOut(tween(170))+scaleOut(tween(240,easing=FastOutSlowInEasing),targetScale=.35f,transformOrigin=TransformOrigin(if(pickerField==0).25f else .75f,0f))) {
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
    val labels=remember(decimal,suffix){ReportCountLabel(decimal,suffix)}
    val density=LocalDensity.current
    val paint=remember(fontSize,color,fontWeight,letterSpacing,density){android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{
        textSize=with(density){fontSize.toPx()};this.color=color.toArgb()
        typeface=android.graphics.Typeface.create("sans-serif",if((fontWeight?:FontWeight.Normal).weight>=600)android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        textAlign=android.graphics.Paint.Align.CENTER
        if(letterSpacing!=TextUnit.Unspecified)this.letterSpacing=letterSpacing.value/fontSize.value
    }}
    Box(modifier.semantics{text=AnnotatedString(finalLabel)}){
        Text(finalLabel,Modifier.clearAndSetSemantics{}.graphicsLayer{alpha=0f},fontSize=fontSize,color=color,fontWeight=fontWeight,lineHeight=lineHeight,letterSpacing=letterSpacing)
        Canvas(Modifier.matchParentSize()){
            val step=(value*animation.value*(if(decimal)10f else 1f)).roundToInt()
            val label=labels.at(step)
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
                    SiteIcon(work.iconKey.takeIf { it.isNotBlank() } ?: "briefcase-business",null,Modifier.size(18.dp),tint=BrandInk)
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
                val dark = TurniThemeState.dark
                val cardSurface = ObsidianSurface
                val hairlineColor = Color(currentTurniPalette.outline).copy(alpha = 0.28f)
                summary.metrics.forEach { metric -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val swatchColor = shiftDisplayColor(metric.definition.color, dark, metric.definition.id)
                    val swatchShape = RoundedCornerShape(3.dp)
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(swatchShape)
                            .background(swatchColor)
                            .then(if (contrastRatio(cardSurface, swatchColor) < 1.35f) Modifier.border(0.6.dp, hairlineColor, swatchShape) else Modifier)
                    )
                    Text(metric.definition.name,Modifier.weight(1f),fontSize=11.sp,color=reportInk)
                    Text("${metric.count}",fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=reportInk)
                    Text(if(metric.definition.isAllDay && metric.hours==0f) "—" else hourLabel(metric.hours),Modifier.width(48.dp),fontSize=11.sp,color=reportMuted,textAlign=TextAlign.End)
                } }
            }
            if(summary.overtime>0)ReportWorkExtra("Straordinari",hourLabel(summary.overtime))
            if(summary.permission>0f)ReportWorkExtra("Permessi","−"+hourLabel(summary.permission))
            if(summary.travel>0)ReportWorkExtra("Trasferte","${summary.travel}")
            if(summary.onCallDays>0)ReportWorkExtra("Reperibilità","${summary.onCallDays} giorni")
            if(summary.count==0)Text("Nessun turno nel periodo",Modifier.padding(vertical=10.dp),fontSize=10.sp,color=reportMuted)
        }
    }
}
@Composable
private fun ReportWorkExtra(label:String,value:String) { Row(Modifier.fillMaxWidth().padding(bottom=6.dp)) { Text(label,Modifier.weight(1f),fontSize=11.sp,color=reportMuted);Text(value,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=reportInk) } }

private data class DialSector(val start: Float, val end: Float, val outline: Path)

@Composable
private fun ReportDistributionDial(metrics:List<ShiftMetric>,colors:List<Color>,total:Int,animation:ReportProgress) {
    val sweep = 270f
    val sectors = remember(metrics) { reportRingBoundaries(metrics.map { it.count }, 0f, sweep, 0f) }
    val track = ObsidianBorderSubtle.copy(alpha = 0.55f)
    val cardSurface = ObsidianSurface
    val hairlineColor = Color(currentTurniPalette.outline).copy(alpha = 0.28f)
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(106.dp,92.dp),contentAlignment=Alignment.Center) {
            Spacer(Modifier.matchParentSize().drawWithCache {
                val outer = size.width / 2f
                val inner = outer - 11.dp.toPx()
                val center = Offset(outer, outer)
                val outerBounds = Rect(center,outer)
                val innerBounds = Rect(center,inner)
                val maxCorner = 3.dp.toPx()
                fun point(radius:Float,angle:Float):Offset {
                    val radians = Math.toRadians(angle.toDouble())
                    return center + Offset(radius*cos(radians).toFloat(),radius*sin(radians).toFloat())
                }
                fun outline(start:Float,span:Float,path:Path): Path {
                    // Bound each corner by the sector's own arc length, including during reveal.
                    val corner = minOf(maxCorner,inner*Math.toRadians(span.toDouble()).toFloat()*.42f)
                    val outerInset = Math.toDegrees((corner/outer).toDouble()).toFloat()
                    val innerInset = Math.toDegrees((corner/inner).toDouble()).toFloat()
                    val end = start + span
                    return path.apply {
                        reset()
                        fun line(p:Offset) = lineTo(p.x,p.y)
                        fun bend(control:Offset,target:Offset) = quadraticBezierTo(control.x,control.y,target.x,target.y)
                        arcTo(outerBounds,start+outerInset,span-2*outerInset,true)
                        bend(point(outer,end),point(outer-corner,end))
                        line(point(inner+corner,end))
                        bend(point(inner,end),point(inner,end-innerInset))
                        arcTo(innerBounds,end-innerInset,-span+2*innerInset,false)
                        bend(point(inner,start),point(inner+corner,start))
                        line(point(outer-corner,start))
                        bend(point(outer,start),point(outer,start+outerInset))
                        close()
                    }
                }
                val startAngle = 135f
                val trackPath = outline(startAngle,sweep,Path())
                val partial = Path()
                val slices = sectors.mapIndexed { index, boundary ->
                    val span = boundary.endDistance - boundary.startDistance
                    val halfGap = minOf(1.25f,span * .08f)
                    val start = boundary.startDistance + if(index==0) 0f else halfGap
                    val end = boundary.endDistance - if(index==sectors.lastIndex) 0f else halfGap
                    DialSector(start,end,outline(startAngle+start,end-start,Path()))
                }
                onDrawBehind {
                    drawPath(trackPath,track)
                    val head = sweep * animation.value.coerceIn(0f,1f)
                    for ((index,slice) in slices.withIndex()) {
                        if (head <= slice.start) break
                        val visible = if (head >= slice.end) slice.outline else
                            outline(startAngle+slice.start,head-slice.start,partial)
                        val sectorColor = colors[index]
                        drawPath(visible, sectorColor)
                        if (contrastRatio(cardSurface, sectorColor) < 1.35f) {
                            drawPath(visible, hairlineColor, style = Stroke(0.6.dp.toPx()))
                        }
                    }
                }
            })
            ReportAnimatedValue(total.toFloat(),animation,modifier=Modifier.offset(y=7.dp),fontSize=if(total>=1000)19.sp else 24.sp,fontWeight=FontWeight.Bold)
        }
        Text("Turni totali",fontSize=11.sp,color=reportMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportHero(hours:Float,days:Int,assignments:Int,overtime:Float,permission:Float,metrics:List<ShiftMetric>,animation:ReportProgress) {
    val dark = TurniThemeState.dark
    val palette=currentTurniPalette
    val largeText=LocalDensity.current.fontScale>1.3f
    val fill=Color(palette.surface)
    val titleInk=Color(palette.text)
    val detailInk=Color(palette.text)
    val hairlineColor = Color(palette.outline).copy(alpha = 0.28f)
    val distribution=metrics
    val pigments=remember(metrics,dark) { metrics.map { shiftDisplayColor(it.definition.color, dark, it.definition.id) } }
    Surface(shape=RoundedCornerShape(23.dp),color=fill,modifier=Modifier.fillMaxWidth().graphicsLayer { alpha=animation.value;translationY=(1-animation.value)*12;scaleX=.98f+.02f*animation.value;scaleY=scaleX }) {
        Box {
            Column(Modifier.padding(start=16.dp,end=16.dp,top=16.dp,bottom=12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Resoconto totale",Modifier.weight(1f),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=titleInk)
                    SiteIcon("layers-2",null,Modifier.size(12.dp),tint=detailInk)
                    Text("Tutti i lavori",Modifier.padding(start=4.dp),fontSize=10.sp,color=detailInk)
                }
                Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        ReportAnimatedValue(hours,animation,true,fontSize=if(largeText)28.sp else 38.sp,lineHeight=if(largeText)32.sp else 42.sp,letterSpacing=(-1).sp,fontWeight=FontWeight.Bold,color=titleInk)
                        Text("ore lavorate",fontSize=12.sp,color=detailInk)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            ReportAnimatedValue(days.toFloat(),animation,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=titleInk)
                            Text("Giorni lavorati",fontSize=11.sp,color=detailInk)
                        }
                    }
                    ReportDistributionDial(distribution,pigments,assignments,animation)
                }
                Divider(color=ObsidianBorderSubtle)
                distribution.chunked(if(largeText)1 else 2).forEach { row -> Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach { metric -> Row(Modifier.weight(1f).heightIn(min=27.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        val legendColor = remember(dark, metric.definition.color, metric.definition.id) {
                            shiftDisplayColor(metric.definition.color, dark, metric.definition.id)
                        }
                        val swatchShape = RoundedCornerShape(3.dp)
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(swatchShape)
                                .background(legendColor)
                                .then(if (contrastRatio(fill, legendColor) < 1.35f) Modifier.border(0.6.dp, hairlineColor, swatchShape) else Modifier)
                        )
                        Text(metric.definition.name,Modifier.weight(1f),fontSize=10.sp,color=detailInk,maxLines=2)
                        Text("${metric.count}",fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=titleInk)
                    } };if(row.size==1&&!largeText)Spacer(Modifier.weight(1f))
                } }
                if(overtime>0||permission>0f) FlowRow(Modifier.padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    if(overtime>0) Text("Inclusi ${hourLabel(overtime)} di straordinario",fontSize=10.sp,color=detailInk)
                    if(permission>0f) Text("Dedotti ${hourLabel(permission)} di permesso",fontSize=10.sp,color=detailInk)
                }
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
private fun ReportMetric(name:String,color:Color,count:Int,hours:Float,allDay:Boolean,shape:String,index:Int,animation:ReportProgress,modifier:Modifier,onCall:Boolean=false,definitionId:String?=null) {
    val dark=TurniThemeState.dark
    val style=TurniThemeState.style
    val tone=remember(color,dark,onCall,style,definitionId){metricColorStyle(color,dark,onCall,style,definitionId)}
    val background=tone.fill
    val ink=tone.ink
    val secondaryInk=tone.secondaryInk
    val face=remember(background,ink,dark) { Brush.linearGradient(
        listOf(
            readableChartTint(background, Color.White, ink, if (dark) .06f else .10f),
            background,
            if (dark) readableChartTint(background, Color.Black, ink, .05f)
            else readableChartTint(background, ink, ink, .03f)
        ),
        start=Offset(15f,5f),end=Offset(145f,155f)) }
    val path=remember(shape){reportShapePath(shape)}
    Box(modifier.aspectRatio(1f).graphicsLayer{val entrance=reportEntranceProgress(animation.value,index);alpha=entrance;translationY=(1-entrance)*12;scaleX=.94f+.06f*entrance;scaleY=scaleX;rotationZ=(if(index%2==0)-.8f else .8f)*(1-entrance)},contentAlignment=Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            scale(size.width/160f,size.height/160f,pivot=Offset.Zero) {
                fun drawFace() { drawPath(path,face) }
                if(shape=="cushion")rotate(-12f,Offset(80f,80f)){drawFace()} else drawFace()
            }
        }
        Column(Modifier.fillMaxWidth(.76f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(name,color=ink,fontSize=12.sp,lineHeight=15.sp,fontWeight=FontWeight.SemiBold,maxLines=3,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
            ReportAnimatedValue(count.toFloat(),animation,fontSize=30.sp,lineHeight=30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1.3).sp,color=ink)
            if(onCall||allDay)Text(if(onCall)"giornate" else "Giornata intera",color=secondaryInk,fontSize=11.sp,lineHeight=14.sp,fontWeight=FontWeight.Medium)
            else ReportAnimatedValue(hours,animation,true," h",fontSize=11.sp,color=secondaryInk,lineHeight=14.sp,fontWeight=FontWeight.Medium)
        }
    }
}

@Composable
private fun ReportDatePicker(selected:LocalDate,onClose:()->Unit,onDate:(LocalDate)->Unit) {
    var month by remember(selected){mutableStateOf(YearMonth.from(selected))}
    Surface(Modifier.fillMaxWidth().shadow(14.dp,RoundedCornerShape(22.dp)),shape=RoundedCornerShape(22.dp),color=ObsidianSurface) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Seleziona data",Modifier.weight(1f),fontSize=11.sp,color=TextSecondary)
                ReportIconButton("x","Chiudi calendario",onClose)
            }
            AppDateGrid(month,selected,{month=it},compact=true,select=onDate)
        }
    }
}

/** One period clock also advances widgets that have not entered the lazy viewport yet. */
internal fun reportEntranceProgress(progress:Float,index:Int):Float =
    (progress*1.5f-index.coerceIn(0,5)*.045f).coerceIn(0f,1f)
