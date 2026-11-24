package com.turni.calendario.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val compactStartFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT)
internal fun calendarStartLabel(value: String, use24Hours: Boolean): String =
    if (use24Hours) value else runCatching {
        LocalTime.parse(value).format(compactStartFormatter)
    }.getOrDefault(value)

/** Day and assignment notes take precedence; opening the detail never changes this indicator. */
internal fun calendarAnnotationIcon(hasNotes:Boolean,hasTags:Boolean):String? = when {
    hasNotes -> "message"
    hasTags -> "tag"
    else -> null
}

private data class PaintedDay(val info:CalendarDayInfo,val shifts:List<Pair<AssignedShift,ShiftDefinition>>,
    val preview:Boolean,val onCall:Boolean,val original:String?,val holidayName:String?,val hasNotes:Boolean,val hasTags:Boolean)
private data class CalendarExtraState(val overtime: Float, val permission: Float, val travel: Boolean)
private data class ExtraWeights(val overtime: Float, val permission: Float, val travel: Float) {
    val max: Float get() = maxOf(overtime, permission, travel)
}
private data class ExtraBand(val weight: Float, val share: Float, val fill: Color, val ink: Color, val caption: String)
private data class CalendarCellPaint(val definitionId:String,val assignmentIds:Set<String>,val origin:Offset)
internal data class CalendarPaintRequest(val date:LocalDate,val definitionId:String,val sequence:Long,val endDate:LocalDate=date)

/** Read-only input for the tutorial; rendering never swaps or writes the real database. */
internal data class CalendarGridPreview(
    val days: List<CalendarDayInfo>,
    val definitions: List<ShiftDefinition>,
    val assignments: List<AssignedShift>,
    val onCallDates: Set<LocalDate> = emptySet(),
    val extras: Map<LocalDate, DayExtraInfo> = emptyMap(),
    val rotations: List<ShiftRotation> = emptyList(),
    val timeFormat24h: Boolean = true,
    val showExShifts: Boolean = true,
    val holidays: Map<LocalDate, String> = emptyMap(),
    val restDates: Set<LocalDate> = emptySet()
)

/** Rendering consumes a paint event once; tool and metadata changes cannot replay it. */
internal fun calendarPaintPending(sequence:Long?,consumed:Long?,editing:Boolean):Boolean =
    editing && sequence!=null && sequence!=consumed

/** Reach the farthest corner from the actual press, without fading the whole cell at once. */
internal fun calendarWaveRadius(width:Float,height:Float,originX:Float,originY:Float,progress:Float):Float {
    val x=originX.coerceIn(0f,width.coerceAtLeast(0f));val y=originY.coerceIn(0f,height.coerceAtLeast(0f))
    val farX=maxOf(x,width-x);val farY=maxOf(y,height-y)
    return kotlin.math.sqrt(farX*farX+farY*farY)*progress.coerceIn(0f,1f)
}

internal fun calendarPaintDates(request:CalendarPaintRequest,visibleDates:List<LocalDate>):Set<LocalDate> =
    visibleDates.filter{it in minOf(request.date,request.endDate)..maxOf(request.date,request.endDate)}.toSet()

/** A cycle marker is shown only at the start of a complete repeated cycle. */
internal fun rotationCycleNumber(anchor:LocalDate?,date:LocalDate,cycleLength:Int):Int? {
    if(anchor==null||cycleLength<=0||date.isBefore(anchor))return null
    val offset=ChronoUnit.DAYS.between(anchor,date)
    return if(offset%cycleLength==0L)(offset/cycleLength+1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()else null
}

internal fun calendarNeedsSelectiveEraser(shiftCount:Int):Boolean = shiftCount>1


/** The first period tap identifies an anchor; only a real endpoint can define a preview. */
internal fun calendarPeriodContains(anchor:LocalDate?,end:LocalDate?,date:LocalDate):Boolean =
    anchor!=null&&end!=null&&date in minOf(anchor,end)..maxOf(anchor,end)

internal fun calendarOvertimeLabel(hours:Float):String {
    val minutes=(hours*60).roundToInt().coerceAtLeast(0)
    return when { minutes<60 -> "+${minutes}m"; minutes%60==0 -> "+${minutes/60}h";else -> "+${minutes/60}h${(minutes%60).toString().padStart(2,'0')}" }
}
internal fun calendarPermissionLabel(hours: Float): String {
    val minutes = (if (hours.isFinite()) (hours * 60).roundToInt() else 0).coerceAtLeast(0)
    return when {
        minutes < 60 -> "−${minutes}m"
        minutes % 60 == 0 -> "−${minutes / 60}h"
        else -> "−${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"
    }
}
internal fun calendarShiftAccessibility(
    name: String,
    timeRange: String,
    overtime: Float,
    travel: Boolean,
    permission: Float = 0f,
): String =
    buildString {
        append(name)
        append(", ")
        append(timeRange)
        if (overtime > 0f) append(", straordinario ${calendarOvertimeLabel(overtime)}")
        if (permission > 0f) append(", permesso ${calendarPermissionLabel(permission)}")
        if (travel) append(", trasferta")
    }

internal data class CalendarShiftLayout(val visibleCount: Int, val chipHeight: Float, val gap: Float = 2f, val overflowHeight: Float = 0f)

/** Fixed-size badges: overflow never changes an assignment already on screen.
 * Keep the marker lane reserved even before overflow, so adding a shift cannot
 * displace the last visible badge. Metadata belongs to its own assignment.
 */
internal fun calendarShiftLayout(availableHeight: Float, shiftCount: Int, fontScale: Float): CalendarShiftLayout {
    val height = 30f * fontScale.coerceAtLeast(1f)
    val markerHeight = 6f
    val budget = (availableHeight - markerHeight).coerceAtLeast(0f)
    var used = 0f
    var count = 0
    for (index in 0 until shiftCount.coerceIn(0, 3)) {
        val next = height + if(index > 0) 2f else 0f
        if (used + next > budget + .001f) break
        used += next
        count++
    }
    return CalendarShiftLayout(count, height, overflowHeight = markerHeight)
}

/** One draw layer per page; transparent semantic cells retain TalkBack and full hit targets. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NativeMonthGrid(month:YearMonth,selection:LocalDate?,editing:Boolean,mode:CalendarEditMode,
    brush:String,rotationId:String,anchor:LocalDate?,rangeEnd:LocalDate?,modifier:Modifier,
    paintRequest:CalendarPaintRequest?=null,editSession:Long=0L,editProgress:State<Float>,animate:Boolean=true,onPaintConsumed:(Long)->Unit={},onClick:(LocalDate)->Unit,onPreview:(LocalDate)->Unit,longPressEnabled:Boolean=true,onLongClick:((LocalDate)->Unit)?=null,
    previewData:CalendarGridPreview?=null,editingBottomInset:Dp=136.dp) {
    val days=previewData?.days ?: remember(month,ShiftRepository.appPreferences.weekStartMonday){getMonthGridDays(month,ShiftRepository.appPreferences.weekStartMonday)}
    val defs by if(previewData!=null)rememberUpdatedState(previewData.definitions.associateBy{it.id})
        else remember{derivedStateOf{ShiftRepository.shiftDefinitions.associateBy{it.id}}}
    val rotation=(previewData?.rotations ?: ShiftRepository.rotations).firstOrNull{it.id==rotationId}
    val profile=if(previewData==null)ShiftRepository.activeWorkProfileId else null
    val timeFormat24h=previewData?.timeFormat24h ?: ShiftRepository.appPreferences.timeFormat24h
    val showExShifts=previewData?.showExShifts ?: ShiftRepository.appPreferences.showExShifts
    val restDates=previewData?.restDates ?: ShiftRepository.minimumRestAlerts.filter { warning->
        ShiftRepository.getShiftsForDate(warning.date).any{it.id==warning.nextId&&defs[it.shiftDefId]?.workProfileId==profile}
    }.map{it.date}.toSet()
    val consecutiveAlertDates = if (previewData != null) emptySet()
    else ShiftRepository.consecutiveShiftAlerts
        .filter { it.workProfileId == profile }
        .map { it.date }
        .toSet()
    val alertDates = restDates + consecutiveAlertDates
    val data by remember(days,editing,mode,brush,rotation,anchor,rangeEnd,profile,defs,previewData,showExShifts){derivedStateOf{
        val activeRepConfig=if(previewData==null&&profile!=null)ShiftRepository.reperibilitaByProfile[profile] else null
        val activeProfileExtras=if(previewData==null&&profile!=null)ShiftRepository.dayExtraInfoByProfile[profile] else null
        days.map{day->
        val actual=if(previewData!=null)previewData.assignments.filter{it.date==day.date}
            else ShiftRepository.getShiftsForDate(day.date).filter{defs[it.shiftDefId]?.workProfileId==profile}
        val rotating=editing&&anchor!=null&&mode==CalendarEditMode.ROTATION&&!day.date.isBefore(anchor)&&rotation?.daySequence?.isNotEmpty()==true
        val period=editing&&mode==CalendarEditMode.RANGE&&calendarPeriodContains(anchor,rangeEnd,day.date)
        val previewId=when{rotating->rotation!!.daySequence[(ChronoUnit.DAYS.between(anchor,day.date)%rotation.daySequence.size).toInt()];period&&brush!="eraser"&&brush!="bell"->brush;else->null}
        val shifts=if(previewId!=null)listOfNotNull(defs[previewId]?.let{AssignedShift("preview",day.date,previewId) to it})
            else actual.mapNotNull{s->defs[s.shiftDefId]?.let{s to it}}.sortedWith(
                compareBy<Pair<AssignedShift,ShiftDefinition>>{(_,def)->def.isAllDay}
                    .thenBy{(shift,def)->
                        val raw=shift.customStartTime?:def.startTime
                        runCatching{LocalTime.parse(raw)}.getOrNull()?:LocalTime.MAX
                    }
                    .thenBy{(shift,def)->shift.customStartTime?:def.startTime}
            )
        val original=actual.firstOrNull()?.takeIf{it.isModifiedFromRotation&&showExShifts&&previewId==null}
            ?.originalShiftDefId?.let{defs[it]?.shortCode}
        val extra=if(previewData!=null)previewData.extras[day.date] ?: DayExtraInfo(day.date) else activeProfileExtras?.get(day.date) ?: ShiftRepository.getDayExtraInfo(day.date)
        val onCall=if(previewData!=null)day.date in previewData.onCallDates else {
            @Suppress("UNUSED_VARIABLE") val repSnapshot=activeRepConfig
            ShiftRepository.isOnCall(day.date)
        }
        val holidayName=if(previewData!=null)previewData.holidays[day.date] else ShiftRepository.isHoliday(day.date)?.name
        PaintedDay(day,shifts,previewId!=null,onCall,original,holidayName,calendarHasNotes(extra.notes,actual),extra.tagIds.isNotEmpty())
    }}}
    val settledEditProgress=rememberUpdatedState(if(editing)1f else 0f)
    // Offscreen pages keep settled geometry; only the visible page redraws on each motion frame.
    val editReveal by if(animate)editProgress else settledEditProgress
    val dark=TurniThemeState.dark;val surface=CalendarSurface;val text=TextPrimary;val muted=TextMuted;val brand=ObsidianPrimary
    val weekend=CalendarWeekend
    val todayFill=CalendarToday
    val line=ObsidianBorder;val onBrand=OnBrand;val brandInk=BrandInk
    val amber=ColorReperibilita
    val palette=turniPalette(dark)
    // Tonal surfaces belong to the metadata, independently of the user's shift color.
    val overtimeBand = lerp(Color(palette.overtime.fill), Color(palette.overtime.ink), if (dark) .17f else .06f)
    val permissionBand = lerp(Color(palette.warning.fill), Color(palette.warning.ink), if (dark) .17f else .06f)
    val travelBand = lerp(surface, Color(palette.travel.edge), if (dark) .27f else .17f)
    val overtimeText = readableInk(overtimeBand, Color(palette.overtime.ink))
    val permissionText = readableInk(permissionBand, Color(palette.warning.ink))
    val travelText = readableInk(travelBand, Color(palette.travel.edge))
    val shiftPaints=remember(defs,dark,palette) {
        defs.mapValues { (_, def) ->
            shiftColorStyle(def.color,dark,Color(palette.surface)) to
                shiftColorStyle(def.color,dark,Color(palette.calendar),subdued=true)
        }
    }
    val pressOrigins=remember { mutableMapOf<LocalDate,Offset>() }
    var painted by remember(editSession) { mutableStateOf<Map<LocalDate,CalendarCellPaint>>(emptyMap()) }
    var paintingDates by remember(editSession) { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var appliedPaintSequence by remember(editSession) { mutableStateOf<Long?>(null) }
    val affected=remember(paintRequest,days){paintRequest?.let{calendarPaintDates(it,days.map{day->day.date})}.orEmpty()}
    val pendingPaint=calendarPaintPending(paintRequest?.sequence,appliedPaintSequence,editing)&&affected.isNotEmpty()
    // Prepare the transparent first frame synchronously; an effect alone lets the new chip flash.
    val paintReveal=remember(editSession){Animatable(1f)}
    val paintExit=remember(editSession){Animatable(if(editing)1f else 0f)}
    val paintScope=rememberCoroutineScope()
    var paintJob by remember(editSession){mutableStateOf<Job?>(null)}
    fun marksForRequest():Map<LocalDate,CalendarCellPaint> = affected.associateWith{date->
        CalendarCellPaint(paintRequest!!.definitionId,data.firstOrNull{it.info.date==date}?.shifts.orEmpty()
            .filter{it.first.shiftDefId==paintRequest.definitionId}.map{it.first.id}.toSet(),pressOrigins[date]?:Offset(.5f,.5f))
    }
    val visiblePainted=if(pendingPaint) {
        if(paintRequest!!.definitionId.isBlank())painted-affected else painted+marksForRequest()
    }else painted
    val visiblePaintingDates=if(pendingPaint)affected else paintingDates
    LaunchedEffect(editing,editSession){
        if(editing)paintExit.snapTo(1f) else {
            paintJob?.cancel()
            if(animate)paintExit.animateTo(0f,tween(520,easing=CubicBezierEasing(.3f,0f,.2f,1f))) else paintExit.snapTo(0f)
            snapshotFlow{editProgress.value}.first{it<=.001f}
            painted=emptyMap();paintingDates=emptySet()
        }
    }
    LaunchedEffect(paintRequest,editSession){
        if(pendingPaint){
            paintJob?.cancel()
            paintReveal.snapTo(if(paintRequest!!.definitionId.isNotBlank()&&animate)0f else 1f)
            painted=visiblePainted;paintingDates=affected;appliedPaintSequence=paintRequest.sequence
            // This job belongs to the page, not the event: acknowledgement must not cancel it.
            if(paintRequest.definitionId.isNotBlank()&&animate)
                paintJob=paintScope.launch{paintReveal.animateTo(1f,tween(440,easing=CubicBezierEasing(.2f,0f,.2f,1f)))}
            if(animate)onPaintConsumed(paintRequest.sequence)
        }
    }
    val anchorReveal=remember(anchor,mode){Animatable(if(anchor!=null&&mode==CalendarEditMode.RANGE)0f else 1f)}
    LaunchedEffect(anchor,mode){if(anchor!=null&&mode==CalendarEditMode.RANGE){anchorReveal.snapTo(0f);anchorReveal.animateTo(1f,tween(420,easing=FastOutSlowInEasing))}}
    // Retain both states through removal: metadata fades and contracts without dropping the shift.
    val extrasKey = data.flatMap { it.shifts }.associate {
        it.first.id to CalendarExtraState(it.first.overtimeHours, it.first.permissionHours, it.first.isTrasferta)
    }
    var fromExtras by remember{mutableStateOf(extrasKey)}
    var toExtras by remember{mutableStateOf(extrasKey)}
    val extrasReveal=remember{Animatable(1f)}
    LaunchedEffect(extrasKey){if(toExtras!=extrasKey){fromExtras=toExtras;toExtras=extrasKey;extrasReveal.snapTo(0f);extrasReveal.animateTo(1f,tween(340,easing=FastOutSlowInEasing));fromExtras=extrasKey}}
    val density=LocalDensity.current
    val todayDate=LocalDate.now()
    val mediumTypeface=remember{Typeface.create("sans-serif-medium",Typeface.NORMAL)}
    val bellOutline=remember{androidx.compose.ui.graphics.vector.PathParser().parsePathString("M10.27 21a2 2 0 0 0 3.46 0M3.26 15.33A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.67C19.41 13.96 18 12.5 18 8A6 6 0 0 0 6 8c0 4.5-1.41 5.96-2.74 7.33").toPath()}
    val messageOutline=remember{androidx.compose.ui.graphics.vector.PathParser().parsePathString("M4 4h16v16l-5-4H4V4ZM8 8h8M8 12h5").toPath()}
    val tagOutline=remember{androidx.compose.ui.graphics.vector.PathParser().parsePathString("M20.59 13.41 13.42 20.59a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.58a2 2 0 0 1 0 2.83ZM7 7h.01").toPath()}
    val paint=remember{Paint(Paint.ANTI_ALIAS_FLAG).apply{textAlign=Paint.Align.CENTER}}
    val digitBounds=remember(density.density,density.fontScale){mutableMapOf<String,android.graphics.RectF>()}
    val digitPath=remember{android.graphics.Path()}
    val chipClip=remember { Path() }
    val bandClip=remember { Path() }
    val viewport=remember { Path() }
    val bellSet=data.filter{it.onCall}.map{it.info.date}.toSet()
    var previousBells by remember{mutableStateOf(bellSet)}
    val bellProgress=remember{Animatable(1f)}
    LaunchedEffect(bellSet){if(previousBells!=bellSet){bellProgress.snapTo(0f);bellProgress.animateTo(1f,tween(440));previousBells=bellSet}}
    val noteSet=data.filter{it.hasNotes||it.hasTags}.map{it.info.date}.toSet()
    var previousNotes by remember{mutableStateOf(noteSet)}
    val noteProgress=remember{Animatable(1f)}
    LaunchedEffect(noteSet){if(previousNotes!=noteSet){noteProgress.snapTo(0f);noteProgress.animateTo(1f,tween(360));previousNotes=noteSet}}
    var viewportHeight by remember{mutableStateOf(1f)}
    Box(modifier.clipToBounds().onSizeChanged{viewportHeight=it.height.toFloat()}) {
        Canvas(Modifier.fillMaxSize()) {
            val cellWidth=size.width/7
            val gridHeight=(size.height-editingBottomInset.toPx()*editReveal).coerceAtLeast(1f)
            val totalRows=(days.size/7).coerceAtLeast(1)
            val cellHeight=gridHeight/totalRows
            val radius=20.dp.toPx()
            viewport.reset()
            viewport.apply{moveTo(0f,0f);lineTo(size.width,0f);lineTo(size.width,gridHeight-radius);quadraticBezierTo(size.width,gridHeight,size.width-radius,gridHeight);lineTo(radius,gridHeight);quadraticBezierTo(0f,gridHeight,0f,gridHeight-radius);close()}
            drawContext.canvas.save();drawContext.canvas.clipPath(viewport);drawRect(surface)
            val px=density.density;val fontScale=density.fontScale
            fun dp(value:Float)=value*px
            fun rowBottomInset(rowIndex:Int):Float=dp(if(rowIndex==totalRows-1&&editingBottomInset>0.dp) 3f+15f*(1f-editReveal) else 3f)
            fun label(value:String,x:Float,y:Float,font:Float,color:Color,bold:Boolean=false,alpha:Float=1f,maxWidth:Float=Float.MAX_VALUE,maxHeight:Float=Float.MAX_VALUE,optical:Boolean=false){
                paint.color=color.copy(alpha=color.alpha*alpha).toArgb();paint.textSize=dp(font)*fontScale
                paint.typeface=if(bold)Typeface.DEFAULT_BOLD else mediumTypeface
                val fit=minOf(1f,dp(maxWidth)/paint.measureText(value).coerceAtLeast(1f),dp(maxHeight)/(paint.descent()-paint.ascent()).coerceAtLeast(1f))
                paint.textSize*=fit
                if(optical){
                    paint.textAlign=Paint.Align.LEFT
                    val bounds=digitBounds.getOrPut("$value:${paint.textSize.toBits()}:$bold"){
                        digitPath.rewind();paint.getTextPath(value,0,value.length,0f,0f,digitPath)
                        android.graphics.RectF().also{digitPath.computeBounds(it,true)}
                    }
                    drawContext.canvas.nativeCanvas.drawText(value,x-bounds.centerX(),y-bounds.centerY(),paint)
                    paint.textAlign=Paint.Align.CENTER
                }else drawContext.canvas.nativeCanvas.drawText(value,x,y-(paint.ascent()+paint.descent())/2,paint)
            }
            fun clockLabel(value:String,x:Float,y:Float,font:Float,color:Color,maxWidth:Float) {
                val suffix=value.takeLast(3)
                if(suffix!=" AM"&&suffix!=" PM"){label(value,x,y,font,color,maxWidth=maxWidth);return}
                val time=value.dropLast(3)
                paint.typeface=mediumTypeface;paint.textSize=dp(font)*fontScale
                val timeWidth=paint.measureText(time)
                paint.textSize=dp(font*.68f)*fontScale
                val suffixWidth=paint.measureText(suffix.trim())
                val gap=dp(1.8f)*fontScale
                val fit=minOf(1f,dp(maxWidth)/(timeWidth+gap+suffixWidth))
                val left=x-(timeWidth+gap+suffixWidth)*fit/2f
                paint.color=color.toArgb();paint.textAlign=Paint.Align.LEFT
                paint.textSize=dp(font)*fontScale*fit
                val baseline=y-(paint.ascent()+paint.descent())/2f
                drawContext.canvas.nativeCanvas.drawText(time,left,baseline,paint)
                paint.textSize=dp(font*.68f)*fontScale*fit
                drawContext.canvas.nativeCanvas.drawText(suffix.trim(),left+(timeWidth+gap)*fit,baseline,paint)
                paint.textAlign=Paint.Align.CENTER
            }
            fun cellMark(item:PaintedDay):CalendarCellPaint? = visiblePainted[item.info.date]?.takeIf { mark ->
                mark.assignmentIds.isNotEmpty()&&mark.assignmentIds.all{id->
                    item.shifts.any{it.first.id==id&&it.first.shiftDefId==mark.definitionId}
                }&&(editing||paintExit.value>.001f||editReveal>.001f)&&!item.preview
            }
            fun cellPaint(item:PaintedDay):Color?=cellMark(item)?.let{
                shiftPaints[it.definitionId]?.let{styles->if(item.info.isCurrentMonth)styles.first.codeFill else styles.second.codeFill}
            }
            fun baseCellBackground(item:PaintedDay):Color {
                val date=item.info.date
                val weekendCol=date.dayOfWeek.value>=6
                val base=if(weekendCol)weekend else surface
                return when {
                    date==todayDate->todayFill
                    date==selection||date==anchor->lerp(base,brand,.1f)
                    else->base
                }
            }
            fun operationReveal(item:PaintedDay)=if(item.info.date in visiblePaintingDates){if(pendingPaint&&animate)0f else paintReveal.value}else 1f
            fun cellBackground(item:PaintedDay):Color = cellPaint(item)?.let{
                lerp(baseCellBackground(item),it,paintExit.value*operationReveal(item))
            }?:baseCellBackground(item)
            fun extraWeights(shift: AssignedShift): ExtraWeights {
                val old = fromExtras[shift.id] ?: CalendarExtraState(0f, 0f, false)
                val target = toExtras[shift.id] ?: CalendarExtraState(shift.overtimeHours, shift.permissionHours, shift.isTrasferta)
                val t = extrasReveal.value
                val scale = 1f - editReveal
                return ExtraWeights(
                    overtime = ((if (old.overtime > 0f) 1f else 0f) * (1f - t) + (if (target.overtime > 0f) 1f else 0f) * t) * scale,
                    permission = ((if (old.permission > 0f) 1f else 0f) * (1f - t) + (if (target.permission > 0f) 1f else 0f) * t) * scale,
                    travel = ((if (old.travel) 1f else 0f) * (1f - t) + (if (target.travel) 1f else 0f) * t) * scale,
                )
            }
            // Only use space left after reserving every visible badge and its overflow dots.
            fun footerHeight(item: PaintedDay, layout: CalendarShiftLayout, available: Float): Float {
                var occupied = 0
                for (index in 0 until layout.visibleCount) {
                    if (extraWeights(item.shifts[index].first).max > .001f) occupied++
                }
                if (occupied == 0) return 0f
                val spare = available - layout.overflowHeight - layout.visibleCount * layout.chipHeight -
                    (layout.visibleCount - 1) * layout.gap - if (item.original != null) 13f else 0f
                val needed = 12f * fontScale.coerceAtLeast(1f)
                return if (spare >= occupied * needed) needed else 0f
            }
            fun paintDestination(item:PaintedDay,index:Int,mark:CalendarCellPaint):Rect {
                val x=index%7*cellWidth;val y=index/7*cellHeight
                val bottom=y+cellHeight-rowBottomInset(index/7)
                var cursor=y+dp(27f-2f*editReveal)
                val layout=calendarShiftLayout((bottom-cursor)/px,item.shifts.size,fontScale)
                val footer = footerHeight(item, layout, (bottom-cursor)/px)
                item.shifts.take(layout.visibleCount).forEach{(shift,_)->
                    val height=dp(layout.chipHeight)
                    if(shift.id in mark.assignmentIds)return Rect(x+dp(4f),cursor,x+cellWidth-dp(4f),cursor+height)
                    val weights = extraWeights(shift)
                    cursor+=height+dp(layout.gap + footer * weights.max)
                }
                // If the assignment is overflowed, contract into its compact marker instead.
                return Rect(x+cellWidth/2-dp(1.7f),bottom-dp(4.7f),x+cellWidth/2+dp(1.7f),bottom-dp(1.3f))
            }
            // Paint continuous weekend columns first, including the gaps in edit mode.
            data.take(7).forEachIndexed { column, item ->
                if (item.info.date.dayOfWeek.value >= 6) {
                    drawRect(weekend, Offset(column * cellWidth, 0f), Size(cellWidth, gridHeight))
                }
            }
            data.forEachIndexed { index, item ->
                val inset = dp(1.5f) * editReveal
                val highlighted = item.info.date == todayDate || item.info.date == selection || item.info.date == anchor
                drawRoundRect(baseCellBackground(item),
                    Offset(index % 7 * cellWidth + inset, index / 7 * cellHeight + inset),
                    Size(cellWidth - inset * 2, cellHeight - inset * 2),
                    CornerRadius(dp(if(highlighted)10f-2f*editReveal else 8f*editReveal)))
                cellMark(item)?.let{mark->
                    val chip=paintDestination(item,index,mark)
                    val cell=Rect(index%7*cellWidth+inset,index/7*cellHeight+inset,
                        (index%7+1)*cellWidth-inset,(index/7+1)*cellHeight-inset)
                    val pigment=cellPaint(item)!!
                    val shape=chipClip.apply{reset();addRoundRect(RoundRect(cell,CornerRadius(dp(8f))))}
                    drawContext.canvas.save();drawContext.canvas.clipPath(shape)
                    if(!editing){
                        // The wave retreats towards its assignment; the badge grows opaque above it.
                        drawRoundRect(pigment,chip.topLeft,chip.size,CornerRadius(dp(5f)))
                        val pressed=Offset(cell.left+cell.width*mark.origin.x,cell.top+cell.height*mark.origin.y)
                        val origin=androidx.compose.ui.geometry.lerp(chip.center,pressed,paintExit.value)
                        val radius=calendarWaveRadius(cell.width,cell.height,origin.x-cell.left,origin.y-cell.top,paintExit.value*operationReveal(item))
                        if(radius>0f)drawCircle(pigment,radius,origin)
                    }else{
                        val origin=Offset(cell.left+cell.width*mark.origin.x,cell.top+cell.height*mark.origin.y)
                        val radius=calendarWaveRadius(cell.width,cell.height,cell.width*mark.origin.x,cell.height*mark.origin.y,operationReveal(item))
                        if(radius>0f)drawCircle(pigment,radius,origin)
                    }
                    drawContext.canvas.restore()
                }
            }
            // One uninterrupted hatch lies above the backgrounds and below all labels/chips.
            if (editReveal > 0f) {
                var start = -gridHeight
                while (start < size.width) {
                    drawLine((if(dark)Color.White else Color.Black).copy(alpha=(if(dark).065f else .048f) * editReveal), Offset(start, gridHeight),
                        Offset(start + gridHeight, 0f), dp(.6f))
                    start += dp(16f)
                }
            }
            data.forEachIndexed{index,item->
                val row=index/7;val isBottomRow=row==totalRows-1
                val x=index%7*cellWidth;val y=row*cellHeight;val center=x+cellWidth/2
                val cellAlert=item.info.date in alertDates
                val hasAnnotation=item.hasNotes||item.hasTags
                val date=item.info.date;val today=date==todayDate;val chosen=date==selection||date==anchor
                val cycle=if(editing&&mode==CalendarEditMode.ROTATION)rotationCycleNumber(anchor,date,rotation?.daySequence?.size?:0)else null
                // Leave distinct lanes for the date, cycle badge and on-call bell.
                val dateCenter=if(cycle!=null)center-dp(2f)else center
                val periodAnchor=editing&&mode==CalendarEditMode.RANGE&&date==anchor
                val erasingAnchor=periodAnchor&&brush=="eraser"
                val bellAnchorCue=periodAnchor&&brush=="bell"
                val eraseCueFill=Color(palette.danger.fill)
                val eraseCueInk=readableInk(eraseCueFill,Color(palette.danger.ink))
                val bellCueFill=Color(palette.onCall.fill)
                val bellCueInk=readableInk(bellCueFill,Color(palette.onCall.ink))
                val anchorInk=when{erasingAnchor->eraseCueInk;bellAnchorCue->bellCueInk;else->brandInk}
                val inset=dp(1.5f)*editReveal
                val contentBottom=y+cellHeight-rowBottomInset(row)
                val paintColor=cellPaint(item)
                val cellFill=cellBackground(item)
                val annotationBackdrop=cellFill
                if(chosen){
                    // Stroke sits fully inside the rounded cell, with the same gap on every side.
                    val focusInset=inset+dp(2.4f)
                    val focusRadius=dp(7.6f-2f*editReveal)
                    val focusInk=when{
                        erasingAnchor -> readablePigment(cellFill,ColorNegative)
                        bellAnchorCue -> readablePigment(cellFill,Color(palette.onCall.ink))
                        else -> readableIndicator(cellFill,brandInk)
                    }
                    drawRoundRect(focusInk,Offset(x+focusInset,y+focusInset),
                        Size(cellWidth-focusInset*2,cellHeight-focusInset*2),CornerRadius(focusRadius),
                        style=Stroke(dp(if(erasingAnchor)1f else 1.15f)))
                }
                if(today&&!periodAnchor)drawRoundRect(brand,Offset(dateCenter-dp(10f),y+dp(3f)),Size(dp(20f),dp(20f)),CornerRadius(dp(5f)))
                if(periodAnchor){
                    // A compact date badge replaces the full-width destructive banner.
                    val cue=anchorReveal.value
                    val cueFill=when{erasingAnchor->eraseCueFill;bellAnchorCue->bellCueFill;else->brand}
                    drawRoundRect(cueFill.copy(alpha=cue),
                        Offset(dateCenter-dp(8f),y+dp(6f)),Size(dp(16f),dp(16f)),CornerRadius(dp(4f)))
                }
                val onCallActive=item.onCall||previousBells.contains(date)
                val changed=previousBells.contains(date)!=item.onCall
                val bellWeight=if(changed){if(item.onCall)bellProgress.value else 1f-bellProgress.value}else if(item.onCall)1f else 0f
                val showOnCallDateTone=!erasingAnchor&&!today&&!periodAnchor&&onCallActive&&bellWeight>0f
                val needsOnCallPill=showOnCallDateTone&&(paintColor!=null||contrastRatio(cellFill,amber)<3.2f)
                if(needsOnCallPill){
                    val pillTopLeft=if(item.holidayName!=null)Offset(dateCenter-dp(9f),y+dp(4f)) else Offset(dateCenter-dp(9.5f),y+dp(3.5f))
                    val pillSize=if(item.holidayName!=null)Size(dp(18f),dp(18f)) else Size(dp(19f),dp(19f))
                    drawRoundRect(Color(palette.onCall.fill).copy(alpha=0.90f*bellWeight),pillTopLeft,pillSize,CornerRadius(dp(5f)))
                }
                if(item.holidayName!=null){
                    // Keep the holiday mark around the date, separate from full-cell selection.
                    val dateBox=Offset(dateCenter-dp(if(periodAnchor)8f else 9f),y+dp(if(periodAnchor)6f else 4f))
                    val dateSize=Size(dp(if(periodAnchor)16f else 18f),dp(if(periodAnchor)16f else 18f))
                    val corner=CornerRadius(dp(5f))
                    // No outer halo: it could collide with the adjacent on-call bell.
                    val dateBackdrop=when {
                        periodAnchor -> when {
                            erasingAnchor -> eraseCueFill
                            bellAnchorCue -> bellCueFill.copy(alpha=anchorReveal.value).compositeOver(cellFill)
                            else -> brand.copy(alpha=anchorReveal.value).compositeOver(cellFill)
                        }
                        today -> brand
                        needsOnCallPill -> Color(palette.onCall.fill).copy(alpha=0.90f*bellWeight).compositeOver(cellFill)
                        else -> cellFill
                    }
                    val holidayInk=Color(holidayDayColor(dark))
                    drawRoundRect(readablePigment(dateBackdrop,holidayInk),dateBox,dateSize,corner,style=Stroke(dp(1.1f)))
                }
                val outOfMonthAlpha=if(!item.info.isCurrentMonth)if(dark).72f else .58f else 1f
                val baseDateInk=when {
                    paintColor!=null -> catalogInk(cellFill)
                    item.info.isCurrentMonth -> text
                    else -> muted.copy(alpha=outOfMonthAlpha)
                }
                val targetAmber=Color(palette.onCall.ink)
                val dateInk=when {
                    erasingAnchor -> lerp(readableInk(cellFill,text),eraseCueInk,anchorReveal.value)
                    bellAnchorCue -> lerp(readableInk(cellFill,text),bellCueInk,anchorReveal.value)
                    today -> onBrand
                    periodAnchor -> lerp(text,onBrand,anchorReveal.value)
                    showOnCallDateTone -> if(needsOnCallPill){
                        lerp(baseDateInk,readableInk(Color(palette.onCall.fill),targetAmber),bellWeight)
                    }else{
                        lerp(baseDateInk,readablePigment(cellFill,amber),bellWeight).let{if(item.info.isCurrentMonth||dark)it else it.copy(alpha=.70f)}
                    }
                    else -> baseDateInk
                }
                label(date.dayOfMonth.toString(),dateCenter,y+dp(if(periodAnchor)14f else 13f),if(periodAnchor)11.5f else 12.5f,dateInk,true,maxWidth=if(periodAnchor)14f else 18f,maxHeight=if(periodAnchor)14f else 18f,optical=true)
                if(periodAnchor){
                    val cue=anchorReveal.value
                    // Thin complete arrows stay in the date lane, safely inside the cell outline.
                    val middle=y+dp(14f)
                    val left=x+dp(7.5f);val right=x+cellWidth-dp(7.5f)
                    val travel=dp(1f)*(1f-cue)
                    val ink=when{
                        erasingAnchor -> readableIndicator(cellFill,ColorNegative)
                        bellAnchorCue -> readableIndicator(cellFill,Color(palette.onCall.ink))
                        else -> readableIndicator(cellFill,anchorInk)
                    }.copy(alpha=cue)
                    val stroke=dp(.85f)
                    fun arrow(tip:Float,direction:Float){
                        val shaft=tip+direction*dp(4f)
                        val shoulder=tip+direction*dp(2.2f)
                        drawLine(ink,Offset(tip,middle),Offset(shaft,middle),stroke,StrokeCap.Round)
                        drawLine(ink,Offset(shoulder,middle-dp(2.2f)),Offset(tip,middle),stroke,StrokeCap.Round)
                        drawLine(ink,Offset(tip,middle),Offset(shoulder,middle+dp(2.2f)),stroke,StrokeCap.Round)
                    }
                    arrow(left+travel,1f);arrow(right-travel,-1f)
                }
                cycle?.let{number->
                    // Five dp of inset keeps the complete badge inside the selection stroke.
                    val markerX=x+cellWidth-dp(10f)
                    val markerY=y+dp(9f)
                    drawCircle(brand.copy(alpha=.20f),dp(5f),Offset(markerX,markerY))
                    val digits=number.toString()
                    // Fit long-running cycles too, without expanding into the date or border.
                    paint.textSize=dp(8f)*fontScale
                    val fit=(dp(8f)/paint.measureText(digits)).coerceAtMost(1f)
                    label(digits,markerX,markerY,8f*fit,brandInk,true,optical=true)
                }
                val bellAlpha=bellWeight
                if(bellAlpha>0){
                    val compactBell=periodAnchor||cycle!=null||(editing&&mode==CalendarEditMode.ROTATION)
                    val bellSize=dp(if(periodAnchor)7.5f else if(compactBell)8f else 10f)
                    val bx=center+dp(if(periodAnchor)16f else 18f)
                    val by=y+dp(if(periodAnchor)20.5f else if(compactBell)19.5f else 13f)
                    val needsBellBacking=contrastRatio(annotationBackdrop,amber)<3.0f
                    if(needsBellBacking){
                        drawCircle(Color(palette.onCall.fill).copy(alpha=0.90f*bellAlpha),bellSize*0.72f,Offset(bx,by))
                    }
                    val color=if(needsBellBacking){
                        Color(palette.onCall.ink).copy(alpha=bellAlpha)
                    }else{
                        readableIndicator(annotationBackdrop,amber).copy(alpha=bellAlpha)
                    }
                    val scale=bellSize/24f
                    drawContext.canvas.save();drawContext.canvas.translate(bx-bellSize/2,by-bellSize/2);drawContext.canvas.scale(scale,scale)
                    drawPath(bellOutline,color,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round));drawContext.canvas.restore()
                }
                val noteChanged=previousNotes.contains(date)!=hasAnnotation
                val noteAlpha=if(noteChanged){if(hasAnnotation)noteProgress.value else 1f-noteProgress.value}else if(hasAnnotation)1f else 0f
                if(noteAlpha>0f){
                    val compactNote=periodAnchor||(cellAlert&&isBottomRow)
                    val nx=center-dp(if(periodAnchor)16f else 18f);val ny=y+dp(if(periodAnchor)20.5f else if(compactNote)19.5f else 13f)
                    val noteSize=dp(if(periodAnchor)7.5f else if(compactNote)8f else 10f)
                    val scale=noteSize/24f
                    drawContext.canvas.save();drawContext.canvas.translate(nx-noteSize/2,ny-noteSize/2);drawContext.canvas.scale(scale,scale)
                    drawPath(if(calendarAnnotationIcon(item.hasNotes,item.hasTags)=="tag")tagOutline else messageOutline,readableIndicator(annotationBackdrop,Color(palette.secondary)).copy(alpha=noteAlpha),style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    drawContext.canvas.restore()
                }
                val subdued=item.preview||!item.info.isCurrentMonth
                var cursor=y+dp(27f-2f*editReveal);var shown=0
                val assignmentBottom=contentBottom
                val layout=calendarShiftLayout((assignmentBottom-cursor)/px,item.shifts.size,fontScale)
                val footer = footerHeight(item, layout, (assignmentBottom-cursor)/px)
                item.shifts.take(layout.visibleCount).forEach{(shift,def)->
                    val weights = extraWeights(shift)
                    val baseChip=layout.chipHeight
                    val available=(assignmentBottom-cursor)/px-layout.overflowHeight
                    val chipHeight=dp(baseChip)
                    if(available+.01f>=baseChip){
                        val paints=shiftPaints.getValue(def.id)
                        val style=if(subdued)paints.second else paints.first
                        val left=x+dp(4f);val width=cellWidth-dp(8f)
                        val merged=cellMark(item)?.assignmentIds?.contains(shift.id)==true
                        val badgeAlpha=if(merged)1f-paintExit.value else 1f
                        val visibility = weights.max
                        val compact = footer == 0f
                        val attachBlend = if (!compact) visibility.coerceIn(0f, 1f) else 0f
                        val topRadius = CornerRadius(dp(5f))
                        val bottomRadius = CornerRadius(dp(5f) * (1f - attachBlend))
                        val topPath = chipClip.apply {
                            reset()
                            addRoundRect(
                                RoundRect(
                                    left = left,
                                    top = cursor,
                                    right = left + width,
                                    bottom = cursor + chipHeight,
                                    topLeftCornerRadius = topRadius,
                                    topRightCornerRadius = topRadius,
                                    bottomRightCornerRadius = bottomRadius,
                                    bottomLeftCornerRadius = bottomRadius,
                                ),
                            )
                        }
                        drawPath(topPath, style.timeFill.copy(alpha = badgeAlpha))
                        drawContext.canvas.save();drawContext.canvas.clipPath(topPath);drawRect(style.codeFill.copy(alpha=badgeAlpha),Offset(left,cursor),Size(width,dp(baseChip/2)));drawContext.canvas.restore()
                        if(!subdued)drawRoundRect(style.accent.copy(alpha=badgeAlpha),Offset(left+dp(2f),cursor+dp(5f)),Size(dp(1.5f),dp(baseChip-10f)),CornerRadius(dp(1f)))
                        label(getCompactShiftCode(def.id,def.shortCode),center,cursor+dp(baseChip*.27f),11f,if(merged)readableInk(lerp(baseCellBackground(item),style.codeFill,operationReveal(item)),style.codeInk)else style.codeInk,true,maxWidth=width/px-10f)
                        clockLabel(if(def.isAllDay)"– –" else calendarStartLabel(shift.customStartTime?:def.startTime,timeFormat24h),center,cursor+dp(baseChip*.74f),11f,if(merged)readableInk(lerp(baseCellBackground(item),style.timeFill,operationReveal(item)),style.timeInk)else style.timeInk,maxWidth=width/px-2f)
                        val footerSpace = dp(footer) * visibility
                        if (visibility > .001f) {
                            val bandInset = if (compact) dp(4f) else 0f
                            val bandTop = cursor + chipHeight + if (compact) -dp(2.5f) else 0f
                            val height = if (compact) dp(2f) else footerSpace
                            val start = left + bandInset
                            val end = left + width - bandInset
                            fun band(
                                x1: Float,
                                x2: Float,
                                weight: Float,
                                fill: Color,
                                ink: Color,
                                caption: String,
                                isFirst: Boolean,
                                isLast: Boolean,
                            ) {
                                if (weight <= .001f || x2 <= x1) return
                                val color = if (compact) readablePigment(style.timeFill, ink) else fill
                                if (compact) {
                                    drawRoundRect(
                                        color.copy(alpha = weight),
                                        Offset(x1, bandTop),
                                        Size(x2 - x1, height),
                                        CornerRadius(minOf(dp(3f), height / 2, (x2 - x1) / 2)),
                                    )
                                } else {
                                    val bottomR = CornerRadius(minOf(dp(5f), height / 2f, (x2 - x1) / 2f))
                                    val segPath = bandClip.apply {
                                        reset()
                                        addRoundRect(
                                            RoundRect(
                                                left = x1,
                                                top = bandTop,
                                                right = x2,
                                                bottom = bandTop + height,
                                                topLeftCornerRadius = CornerRadius.Zero,
                                                topRightCornerRadius = CornerRadius.Zero,
                                                bottomRightCornerRadius = if (isLast) bottomR else CornerRadius.Zero,
                                                bottomLeftCornerRadius = if (isFirst) bottomR else CornerRadius.Zero,
                                            ),
                                        )
                                    }
                                    drawPath(segPath, color.copy(alpha = weight))
                                    label(
                                        caption,
                                        (x1 + x2) / 2,
                                        bandTop + height / 2,
                                        8.5f,
                                        ink,
                                        true,
                                        weight,
                                        maxWidth = ((x2 - x1) / px - 2.5f).coerceAtLeast(0f),
                                        maxHeight = height / px,
                                    )
                                }
                            }
                            val overtimeValue = toExtras[shift.id]?.overtime?.takeIf { it > 0f }
                                ?: fromExtras[shift.id]?.overtime ?: shift.overtimeHours
                            val permissionValue = toExtras[shift.id]?.permission?.takeIf { it > 0f }
                                ?: fromExtras[shift.id]?.permission ?: shift.permissionHours
                            val active = buildList {
                                if (weights.overtime > .001f) {
                                    add(ExtraBand(weights.overtime, 1f, overtimeBand, overtimeText, calendarOvertimeLabel(overtimeValue)))
                                }
                                if (weights.permission > .001f) {
                                    add(ExtraBand(weights.permission, 1f, permissionBand, permissionText, calendarPermissionLabel(permissionValue)))
                                }
                                if (weights.travel > .001f) {
                                    add(ExtraBand(weights.travel, .59f, travelBand, travelText, "TR"))
                                }
                            }
                            if (active.isNotEmpty()) {
                                val totalShare = active.sumOf { (it.weight * it.share).toDouble() }.toFloat().coerceAtLeast(.001f)
                                val gapWidths = FloatArray((active.size - 1).coerceAtLeast(0)) { gapIndex ->
                                    dp(1f) * minOf(active[gapIndex].weight, active[gapIndex + 1].weight)
                                }
                                val usableWidth = (end - start - gapWidths.sum()).coerceAtLeast(0f)
                                var segX = start
                                active.forEachIndexed { segIndex, seg ->
                                    val segWidth = if (segIndex == active.lastIndex) {
                                        (end - segX).coerceAtLeast(0f)
                                    } else {
                                        usableWidth * (seg.weight * seg.share / totalShare)
                                    }
                                    band(
                                        segX,
                                        segX + segWidth,
                                        seg.weight,
                                        seg.fill,
                                        seg.ink,
                                        seg.caption,
                                        isFirst = segIndex == 0,
                                        isLast = segIndex == active.lastIndex,
                                    )
                                    segX += segWidth + if (segIndex < gapWidths.size) gapWidths[segIndex] else 0f
                                }
                            }
                        }
                        cursor+=chipHeight+dp(layout.gap)+footerSpace;shown++
                    }
                }
                if(item.original!=null&&cursor+dp(12f)<assignmentBottom-dp(7f)){label("ex ${item.original}",center,cursor+dp(6f),9f,readableInk(cellFill,muted));cursor+=dp(13f)}
                if(item.shifts.size>shown) {
                    val hidden=item.shifts.drop(shown)
                    val spacing=minOf(dp(6f),(cellWidth-dp(14f))/hidden.size.coerceAtLeast(1))
                    val radius=minOf(dp(1.8f),spacing*.3f)
                    val pillY=assignmentBottom-dp(3f)
                    val pillWidth=(hidden.size-1).coerceAtLeast(0)*spacing+radius*2f+dp(5f)
                    val pillHeight=radius*2f+dp(2.4f)
                    val pillTopLeft=Offset(center-pillWidth/2f,pillY-pillHeight/2f)
                    val pillSize=Size(pillWidth,pillHeight)
                    val pillRadius=CornerRadius(pillHeight/2f)
                    // Neutral halo keeps overflow dots legible over painted cells in light edit mode.
                    drawRoundRect(surface.copy(alpha=.92f),pillTopLeft,pillSize,pillRadius)
                    drawRoundRect(line.copy(alpha=.85f),pillTopLeft,pillSize,pillRadius,style=Stroke(dp(.55f)))
                    hidden.forEachIndexed { i, (_,def) ->
                        val marker=Offset(center+(i-(hidden.size-1)/2f)*spacing,pillY)
                        // Preserve the actual chosen color; the outline separates pale dots.
                        drawCircle(def.color,radius,marker)
                        drawCircle(readableIndicator(surface,line),radius,marker,style=Stroke(minOf(dp(.4f),radius*.22f)))
                    }
                }
                if(cellAlert){
                    // Classic bottom-left corner on normal rows; lift to top-left date lane on bottom row so the floating dock never hides it.
                    val warningCenter=if(isBottomRow) Offset(x+dp(9f),y+dp(8.5f)) else Offset(x+dp(9f),y+cellHeight-dp(9.5f))
                    val warningPath=Path().apply {
                        moveTo(warningCenter.x,warningCenter.y-dp(4f))
                        lineTo(warningCenter.x+dp(4.5f),warningCenter.y+dp(3.5f))
                        lineTo(warningCenter.x-dp(4.5f),warningCenter.y+dp(3.5f));close()
                    }
                    val fill=Color(palette.warning.fill)
                    drawPath(warningPath,fill)
                    drawPath(warningPath,readableIndicator(cellFill,Color(palette.warning.ink)),style=Stroke(dp(.65f),join=StrokeJoin.Round))
                    label("!",warningCenter.x,warningCenter.y+dp(.5f),6.5f,Color(palette.warning.ink),true,optical=true)
                }
            }
            if(editReveal<1f)for(row in 1 until days.size/7)drawLine(line.copy(alpha=.45f*(1-editReveal)),Offset(0f,row*cellHeight),Offset(size.width,row*cellHeight),dp(.5f))
            drawContext.canvas.restore()
        }
        if(animate) Column(Modifier.fillMaxSize().graphicsLayer{scaleY=((viewportHeight-editingBottomInset.toPx()*editReveal)/viewportHeight).coerceAtLeast(.1f);transformOrigin=TransformOrigin(0f,0f)}){
            data.chunked(7).forEach{week->Row(Modifier.weight(1f).fillMaxWidth()){
                week.forEach{day->Box(Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants=true){
                    role=Role.Button;this.text=AnnotatedString(day.info.date.dayOfMonth.toString());selected=selection==day.info.date||anchor==day.info.date
                    contentDescription=buildString{append(day.info.date.toString());if(day.info.date in restDates)append(", riposo minimo non rispettato");if(day.info.date in consecutiveAlertDates)append(", turni consecutivi superati");day.holidayName?.let{append(", festività: $it")};if(day.onCall)append(", reperibilità");if(day.hasNotes)append(", note presenti");if(day.hasTags)append(", etichette presenti");day.shifts.forEach{(shift,def)->append(", ");append(calendarShiftAccessibility(def.name,def.timeRangeFormatted,shift.overtimeHours,shift.isTrasferta,shift.permissionHours))};if(day.preview)append(", anteprima");if(editing&&mode==CalendarEditMode.RANGE&&day.info.date==anchor)append(", inizio periodo: scegli un giorno precedente o successivo");if(editing&&mode==CalendarEditMode.ROTATION)rotationCycleNumber(anchor,day.info.date,rotation?.daySequence?.size?:0)?.let{append(", inizio ciclo $it")}}
                }.pointerInput(day.info.date){
                    awaitEachGesture{
                        val down=awaitFirstDown(requireUnconsumed=false)
                        pressOrigins[day.info.date]=Offset((down.position.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f),(down.position.y/size.height.coerceAtLeast(1)).coerceIn(0f,1f))
                    }
                }.combinedClickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick={onPreview(day.info.date);onClick(day.info.date)},onLongClick=if(longPressEnabled)onLongClick?.let{callback->{callback(day.info.date)}}else null))}
            }}
        }
    }
}

internal fun calendarHasNotes(dayNotes:String,shifts:List<AssignedShift>):Boolean =
    dayNotes.isNotBlank() || shifts.any{it.notes.isNotBlank()}
