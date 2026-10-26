package com.turni.calendario.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

internal fun calendarStartLabel(value: String, use24Hours: Boolean): String =
    if (use24Hours) value else runCatching {
        LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mma", Locale.ROOT)).lowercase(Locale.ROOT)
    }.getOrDefault(value)

private data class PaintedDay(val info:CalendarDayInfo,val shifts:List<Pair<AssignedShift,ShiftDefinition>>,
    val preview:Boolean,val onCall:Boolean,val original:String?,val holiday:Boolean)
private data class CalendarExtraState(val overtime:Float,val travel:Boolean)
internal data class CalendarPaintRequest(val date:LocalDate,val definitionId:String,val sequence:Long)

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
    return when { minutes<60 -> "+${minutes}m"; minutes%60==0 -> "+${minutes/60}h";else -> "+${minutes/60}h${minutes%60}" }
}
internal fun calendarExtraBandHeight(available:Float,base:Float,rows:Float):Float =
    if(rows<=0f)0f else ((available-base).coerceAtLeast(0f)/rows).coerceAtMost(13f)

/** One draw layer per page; transparent semantic cells retain TalkBack and full hit targets. */
@Composable
internal fun NativeMonthGrid(month:YearMonth,selection:LocalDate?,editing:Boolean,mode:CalendarEditMode,
    brush:String,rotationId:String,anchor:LocalDate?,rangeEnd:LocalDate?,modifier:Modifier,
    paintRequest:CalendarPaintRequest?=null,animate:Boolean=true,onClick:(LocalDate)->Unit,onPreview:(LocalDate)->Unit) {
    val days=remember(month,ShiftRepository.appPreferences.weekStartMonday){getMonthGridDays(month,ShiftRepository.appPreferences.weekStartMonday)}
    val defs=ShiftRepository.shiftDefinitions.associateBy{it.id}
    val rotation=ShiftRepository.rotations.firstOrNull{it.id==rotationId}
    val profile=ShiftRepository.activeWorkProfileId
    val data=days.map{day->
        val actual=ShiftRepository.getShiftsForDate(day.date).filter{defs[it.shiftDefId]?.workProfileId==profile}
        val rotating=editing&&anchor!=null&&mode==CalendarEditMode.ROTATION&&!day.date.isBefore(anchor)&&rotation?.daySequence?.isNotEmpty()==true
        val period=editing&&mode==CalendarEditMode.RANGE&&calendarPeriodContains(anchor,rangeEnd,day.date)
        val previewId=when{rotating->rotation!!.daySequence[(ChronoUnit.DAYS.between(anchor,day.date)%rotation.daySequence.size).toInt()];period&&brush!="eraser"->brush;else->null}
        val shifts=if(previewId!=null)listOfNotNull(defs[previewId]?.let{AssignedShift("preview",day.date,previewId) to it})else actual.mapNotNull{s->defs[s.shiftDefId]?.let{s to it}}
        val original=actual.firstOrNull()?.takeIf{it.isModifiedFromRotation&&ShiftRepository.appPreferences.showExShifts&&previewId==null}
            ?.originalShiftDefId?.let{defs[it]?.shortCode}
        PaintedDay(day,shifts,previewId!=null,ShiftRepository.isOnCall(day.date),original,ShiftRepository.isHoliday(day.date)!=null)
    }
    val editReveal by animateFloatAsState(if(editing)1f else 0f,if(animate)tween(380,easing=FastOutSlowInEasing)else snap())
    val dark=TurniThemeState.dark;val surface=CalendarSurface;val text=TextPrimary;val muted=TextMuted;val brand=ObsidianPrimary
    val weekend=CalendarWeekend
    val todayFill=CalendarToday
    val line=ObsidianBorder;val onBrand=if(dark)Color(0xFF24213F)else Color.White
    val amber=ColorReperibilita
    var painted by remember { mutableStateOf<Map<LocalDate,String>>(emptyMap()) }
    var paintingDate by remember { mutableStateOf<LocalDate?>(null) }
    val paintReveal=remember{Animatable(1f)}
    LaunchedEffect(editing){if(!editing){painted=emptyMap();paintingDate=null}}
    LaunchedEffect(paintRequest){
        val request=paintRequest
        if(editing&&request!=null&&days.any{it.date==request.date}){
            // Every earlier cell keeps its final color; only the latest explicit gesture animates.
            painted=if(request.definitionId.isBlank())painted-request.date else painted+(request.date to request.definitionId)
            paintingDate=request.date
            if(request.definitionId.isNotBlank()&&animate){
                paintReveal.snapTo(0f);paintReveal.animateTo(1f,tween(480,easing=FastOutSlowInEasing))
            }else paintReveal.snapTo(1f)
        }
    }
    val anchorReveal=remember{Animatable(1f)}
    LaunchedEffect(anchor,mode){if(anchor!=null&&mode==CalendarEditMode.RANGE){anchorReveal.snapTo(0f);anchorReveal.animateTo(1f,tween(420,easing=FastOutSlowInEasing))}}
    // Retain both states through removal: metadata fades and contracts without dropping the shift.
    val extrasKey=data.flatMap{it.shifts}.associate{it.first.id to CalendarExtraState(it.first.overtimeHours,it.first.isTrasferta)}
    var fromExtras by remember{mutableStateOf(extrasKey)}
    var toExtras by remember{mutableStateOf(extrasKey)}
    val extrasReveal=remember{Animatable(1f)}
    LaunchedEffect(extrasKey){if(toExtras!=extrasKey){fromExtras=toExtras;toExtras=extrasKey;extrasReveal.snapTo(0f);extrasReveal.animateTo(1f,tween(340,easing=FastOutSlowInEasing));fromExtras=extrasKey}}
    val density=LocalDensity.current
    val todayDate=LocalDate.now()
    val mediumTypeface=remember{Typeface.create("sans-serif-medium",Typeface.NORMAL)}
    val bellOutline=remember{androidx.compose.ui.graphics.vector.PathParser().parsePathString("M10.27 21a2 2 0 0 0 3.46 0M3.26 15.33A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.67C19.41 13.96 18 12.5 18 8A6 6 0 0 0 6 8c0 4.5-1.41 5.96-2.74 7.33").toPath()}
    val paint=remember{Paint(Paint.ANTI_ALIAS_FLAG).apply{textAlign=Paint.Align.CENTER}}
    val bellSet=data.filter{it.onCall}.map{it.info.date}.toSet()
    var previousBells by remember{mutableStateOf(bellSet)}
    val bellProgress=remember{Animatable(1f)}
    LaunchedEffect(bellSet){if(previousBells!=bellSet){bellProgress.snapTo(0f);bellProgress.animateTo(1f,tween(440));previousBells=bellSet}}
    var viewportHeight by remember{mutableStateOf(1f)}
    Box(modifier.clipToBounds().onSizeChanged{viewportHeight=it.height.toFloat()}) {
        Canvas(Modifier.fillMaxSize()) {
            val cellWidth=size.width/7
            val gridHeight=(size.height-128.dp.toPx()*editReveal).coerceAtLeast(1f)
            val cellHeight=gridHeight/(days.size/7)
            val radius=20.dp.toPx()
            val viewport=Path().apply{moveTo(0f,0f);lineTo(size.width,0f);lineTo(size.width,gridHeight-radius);quadraticBezierTo(size.width,gridHeight,size.width-radius,gridHeight);lineTo(radius,gridHeight);quadraticBezierTo(0f,gridHeight,0f,gridHeight-radius);close()}
            drawContext.canvas.save();drawContext.canvas.clipPath(viewport);drawRect(surface)
            val px=density.density;val fontScale=density.fontScale
            fun dp(value:Float)=value*px
            fun label(value:String,x:Float,y:Float,font:Float,color:Color,bold:Boolean=false,alpha:Float=1f){
                paint.color=color.copy(alpha=color.alpha*alpha).toArgb();paint.textSize=dp(font)*fontScale
                paint.typeface=if(bold)Typeface.DEFAULT_BOLD else mediumTypeface
                drawContext.canvas.nativeCanvas.drawText(value,x,y-(paint.ascent()+paint.descent())/2,paint)
            }
            if(editReveal>0f){var x=-size.height;while(x<size.width){drawLine(line.copy(alpha=.65f*editReveal),Offset(x,size.height),Offset(x+size.height,0f),dp(.6f));x+=dp(12f)}}
            data.forEachIndexed{index,item->
                val x=index%7*cellWidth;val y=index/7*cellHeight;val center=x+cellWidth/2
                val date=item.info.date;val today=date==todayDate;val chosen=date==selection||date==anchor
                val base=if(date.dayOfWeek.value>=6)weekend else surface
                val inset=dp(1.5f)*editReveal
                if(!editing)drawRect(base,Offset(x,y),Size(cellWidth,cellHeight))
                val paintColor=painted[date]?.takeIf{id->item.shifts.any{it.first.shiftDefId==id}}?.let{defs[it]?.color}?.takeIf{editing&&!item.preview}
                val neutral=if(editing)ObsidianSurface else base
                val cellFill=if(paintColor!=null)lerp(neutral,paintColor,if(date==paintingDate)paintReveal.value else 1f)else if(today)todayFill else if(chosen)lerp(neutral,brand,.1f)else neutral
                drawRoundRect(cellFill,Offset(x+inset,y+inset),Size(cellWidth-inset*2,cellHeight-inset*2),CornerRadius(dp(if(editing)8f else if(today||chosen)10f else 0f)))
                if(chosen)drawRoundRect(brand,Offset(x+inset+dp(1f),y+inset+dp(1f)),Size(cellWidth-inset*2-dp(2f),cellHeight-inset*2-dp(2f)),CornerRadius(dp(if(editing)8f else 10f)),style=Stroke(dp(2f)))
                if(today&&!editing)drawRoundRect(brand,Offset(center-dp(12.5f),y+dp(4f)),Size(dp(25f),dp(24f)),CornerRadius(dp(6f)))
                val dateInk=if(paintColor!=null)catalogInk(cellFill)else if(today&&!editing)onBrand else if(item.onCall||previousBells.contains(date))lerp(text,amber,if(previousBells.contains(date)!=item.onCall){if(item.onCall)bellProgress.value else 1-bellProgress.value}else 1f)else if(item.holiday)Color(if(dark)0xFFF4AAAA else 0xFFAA3042)else if(item.info.isCurrentMonth)text else muted
                label(date.dayOfMonth.toString(),center,y+dp(16f),14f,dateInk,true,if(item.info.isCurrentMonth)1f else .5f)
                if(editing&&mode==CalendarEditMode.RANGE&&date==anchor){
                    val cue=anchorReveal.value
                    val spread=dp(4f)*(1f-cue)
                    label("‹",x+dp(6f)-spread,y+dp(7f),13f,brand,true,cue)
                    label("›",x+cellWidth-dp(6f)+spread,y+dp(7f),13f,brand,true,cue)
                }
                if(editing&&mode==CalendarEditMode.ROTATION){
                    rotationCycleNumber(anchor,date,rotation?.daySequence?.size?:0)?.let{cycle->
                        val markerX=x+cellWidth-dp(7f)
                        drawCircle(brand.copy(alpha=.17f),dp(6f),Offset(markerX,y+dp(7f)))
                        label(cycle.toString(),markerX,y+dp(7f),8f,brand,true)
                    }
                }
                val changed=previousBells.contains(date)!=item.onCall
                val bellAlpha=if(changed){if(item.onCall)bellProgress.value else 1-bellProgress.value}else if(item.onCall)1f else 0f
                if(bellAlpha>0){
                    val bx=center+dp(18f);val by=y+dp(if(editing&&(mode==CalendarEditMode.ROTATION||(mode==CalendarEditMode.RANGE&&date==anchor)))21f else 16f);val color=amber.copy(alpha=bellAlpha*(if(item.info.isCurrentMonth)1f else .45f))
                    val scale=dp(10f)/24f
                    drawContext.canvas.save();drawContext.canvas.translate(bx-dp(5f),by-dp(5f));drawContext.canvas.scale(scale,scale)
                    drawPath(bellOutline,color,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round));drawContext.canvas.restore()
                }
                val faded=if(item.preview||!item.info.isCurrentMonth).45f else 1f
                var cursor=y+dp(if(editing)28f else 31f);var shown=0
                item.shifts.take(2).forEach{(shift,def)->
                    val old=fromExtras[shift.id]?:CalendarExtraState(0f,false)
                    val target=toExtras[shift.id]?:CalendarExtraState(shift.overtimeHours,shift.isTrasferta)
                    val t=extrasReveal.value
                    val overtimeWeight=if(editing)0f else (if(old.overtime>0)1f else 0f)*(1-t)+(if(target.overtime>0)1f else 0f)*t
                    val travelWeight=if(editing)0f else (if(old.travel)1f else 0f)*(1-t)+(if(target.travel)1f else 0f)*t
                    val baseChip=if(editing)34f else 40f
                    val available=(y+cellHeight-dp(6f)-cursor)/px
                    val bandHeight=calendarExtraBandHeight(available,baseChip,overtimeWeight+travelWeight)
                    val chipHeight=dp(baseChip+bandHeight*(overtimeWeight+travelWeight))
                    // Extras never make an otherwise visible assignment disappear.
                    if(available>=baseChip){
                        val fill=lerp(if(dark)ObsidianSurface else Color.White,def.color,if(dark).22f else .26f)
                        val codeFill=lerp(if(dark)ObsidianSurface else Color.White,def.color,if(dark).28f else .70f)
                        val left=x+dp(if(editing)4f else 2f);val width=cellWidth-dp(if(editing)8f else 4f)
                        drawRoundRect(fill.copy(alpha=faded),Offset(left,cursor),Size(width,chipHeight),CornerRadius(dp(7f)))
                        val topPath=Path().apply{addRoundRect(RoundRect(Rect(left,cursor,left+width,cursor+chipHeight),CornerRadius(dp(7f))))}
                        drawContext.canvas.save();drawContext.canvas.clipPath(topPath);drawRect(codeFill.copy(alpha=faded),Offset(left,cursor),Size(width,dp(if(editing)18f else 20f)));drawContext.canvas.restore()
                        var codeInk=if(dark)def.color else catalogInk(codeFill)
                        val a=codeInk.luminance();val b=codeFill.luminance()
                        if((maxOf(a,b)+.05f)/(minOf(a,b)+.05f)<4.5f)codeInk=catalogInk(codeFill)
                        label(getCompactShiftCode(def.id,def.shortCode),center,cursor+dp(if(editing)9f else 10f),12f,codeInk,true,faded)
                        label(if(def.isAllDay)"– –" else calendarStartLabel(shift.customStartTime?:def.startTime,ShiftRepository.appPreferences.timeFormat24h),center,cursor+dp(if(editing)26f else 29f),12f,text,false,faded)
                        var bandY=cursor+dp(baseChip)
                        drawContext.canvas.save();drawContext.canvas.clipPath(topPath)
                        fun band(labelText:String,weight:Float,fillColor:Color,ink:Color){
                            if(weight<=.001f)return
                            val height=dp(bandHeight*weight)
                            if(bandHeight>=9.5f){
                                drawContext.canvas.save();drawContext.canvas.clipRect(Rect(left,bandY,left+width,bandY+height))
                                drawRect(fillColor.copy(alpha=faded*weight),Offset(left,bandY),Size(width,height))
                                drawLine(surface.copy(alpha=faded),Offset(left,bandY),Offset(left+width,bandY),dp(.7f))
                                label(labelText,center,bandY+height/2,if(labelText.length>5)9f else 10f,ink,true,faded*weight)
                                drawContext.canvas.restore()
                            }else{
                                // Tight six-week cells keep the shift readable and expose metadata as tiny colored markers.
                                drawCircle(ink.copy(alpha=faded*weight),dp(1.5f),Offset(left+width-dp(if(labelText=="TR")4f else 9f),cursor+dp(baseChip-4f)))
                            }
                            bandY+=height
                        }
                        band(calendarOvertimeLabel(if(target.overtime>0)target.overtime else old.overtime),overtimeWeight,if(dark)Color(0xFF173F58)else Color(0xFFC2E7FF),if(dark)Color(0xFFC0E7FF)else Color(0xFF164967))
                        band("TR",travelWeight,if(dark)Color(0xFF18483D)else Color(0xFFABE2CF),if(dark)Color(0xFFA5EFD8)else Color(0xFF124D3B))
                        drawContext.canvas.restore()
                        cursor+=chipHeight+dp(if(editing)2f else 3f);shown++
                    }
                }
                if(item.original!=null&&cursor+dp(12f)<y+cellHeight-dp(7f)){label("ex ${item.original}",center,cursor+dp(6f),9f,muted,false,faded);cursor+=dp(13f)}
                if(item.shifts.size>shown)item.shifts.drop(shown).take(4).forEachIndexed{i,(_,def)->
                    drawCircle(def.color.copy(alpha=faded),dp(2f),Offset(center+(i-(item.shifts.size-shown-1)/2f)*dp(6f),minOf(cursor+dp(4f),y+cellHeight-dp(5f))))
                }
            }
            if(!editing)for(row in 1 until days.size/7)drawLine(line.copy(alpha=.45f),Offset(0f,row*cellHeight),Offset(size.width,row*cellHeight),dp(.5f))
            drawContext.canvas.restore()
        }
        if(animate) Column(Modifier.fillMaxSize().graphicsLayer{scaleY=((viewportHeight-128.dp.toPx()*editReveal)/viewportHeight).coerceAtLeast(.1f);transformOrigin=TransformOrigin(0f,0f)}){
            data.chunked(7).forEach{week->Row(Modifier.weight(1f).fillMaxWidth()){
                week.forEach{day->Box(Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants=true){
                    role=Role.Button;this.text=AnnotatedString(day.info.date.dayOfMonth.toString());selected=selection==day.info.date||anchor==day.info.date
                    contentDescription=buildString{append(day.info.date.toString());if(day.onCall)append(", reperibilità");day.shifts.forEach{(_,def)->append(", ${def.name}, ${def.timeRangeFormatted}")};if(day.preview)append(", anteprima");if(editing&&mode==CalendarEditMode.RANGE&&day.info.date==anchor)append(", inizio periodo: scegli un giorno precedente o successivo");if(editing&&mode==CalendarEditMode.ROTATION)rotationCycleNumber(anchor,day.info.date,rotation?.daySequence?.size?:0)?.let{append(", inizio ciclo $it")}}
                }.clickable{onPreview(day.info.date);onClick(day.info.date)})}
            }}
        }
    }
}
