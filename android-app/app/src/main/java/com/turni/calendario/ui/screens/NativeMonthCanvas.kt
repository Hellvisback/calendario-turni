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

internal fun calendarStartLabel(value: String, use24Hours: Boolean): String =
    if (use24Hours) value else runCatching {
        LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mma", Locale.ROOT)).lowercase(Locale.ROOT)
    }.getOrDefault(value)

private data class PaintedDay(val info:CalendarDayInfo,val shifts:List<Pair<AssignedShift,ShiftDefinition>>,
    val preview:Boolean,val onCall:Boolean,val original:String?)

/** One draw layer per page; transparent semantic cells retain TalkBack and full hit targets. */
@Composable
internal fun NativeMonthGrid(month:YearMonth,selection:LocalDate?,editing:Boolean,mode:CalendarEditMode,
    brush:String,rotationId:String,anchor:LocalDate?,rangeEnd:LocalDate?,modifier:Modifier,
    onClick:(LocalDate)->Unit,onPreview:(LocalDate)->Unit) {
    val days=remember(month,ShiftRepository.appPreferences.weekStartMonday){getMonthGridDays(month,ShiftRepository.appPreferences.weekStartMonday)}
    val defs=ShiftRepository.shiftDefinitions.associateBy{it.id}
    val rotation=ShiftRepository.rotations.firstOrNull{it.id==rotationId}
    val profile=ShiftRepository.activeWorkProfileId
    val data=days.map{day->
        val actual=ShiftRepository.getShiftsForDate(day.date).filter{defs[it.shiftDefId]?.workProfileId==profile}
        val rotating=editing&&anchor!=null&&mode==CalendarEditMode.ROTATION&&!day.date.isBefore(anchor)&&rotation?.daySequence?.isNotEmpty()==true
        val period=editing&&anchor!=null&&mode==CalendarEditMode.RANGE&&(rangeEnd==null||day.date in minOf(anchor,rangeEnd)..maxOf(anchor,rangeEnd))
        val previewId=when{rotating->rotation!!.daySequence[(ChronoUnit.DAYS.between(anchor,day.date)%rotation.daySequence.size).toInt()];period&&brush!="eraser"->brush;else->null}
        val shifts=if(previewId!=null)listOfNotNull(defs[previewId]?.let{AssignedShift("preview",day.date,previewId) to it})else actual.mapNotNull{s->defs[s.shiftDefId]?.let{s to it}}
        val original=actual.firstOrNull()?.takeIf{it.isModifiedFromRotation&&ShiftRepository.appPreferences.showExShifts&&previewId==null}
            ?.originalShiftDefId?.let{defs[it]?.shortCode}
        PaintedDay(day,shifts,previewId!=null,ShiftRepository.isOnCall(day.date),original)
    }
    val dark=TurniThemeState.dark;val surface=ObsidianSurface;val text=TextPrimary;val muted=TextMuted;val brand=ObsidianPrimary
    val weekend=if(dark)Color(0xFF20222B)else Color(0xFFE6EDF7)
    val todayFill=if(dark)Color(0xFF343146)else Color(0xFFD6E5FF)
    val line=ObsidianBorder;val onBrand=if(dark)Color(0xFF24213F)else Color.White
    val amber=ColorReperibilita
    val density=LocalDensity.current
    val paint=remember{Paint(Paint.ANTI_ALIAS_FLAG).apply{textAlign=Paint.Align.CENTER}}
    val bellSet=data.filter{it.onCall}.map{it.info.date}.toSet()
    var previousBells by remember{mutableStateOf(bellSet)}
    val bellProgress=remember{Animatable(1f)}
    LaunchedEffect(bellSet){if(previousBells!=bellSet){bellProgress.snapTo(0f);bellProgress.animateTo(1f,tween(440));previousBells=bellSet}}
    Box(modifier.clipToBounds().background(surface)) {
        Canvas(Modifier.fillMaxSize()) {
            val cellWidth=size.width/7;val cellHeight=size.height/(days.size/7)
            val px=density.density;val fontScale=density.fontScale
            fun dp(value:Float)=value*px
            fun label(value:String,x:Float,y:Float,font:Float,color:Color,bold:Boolean=false,alpha:Float=1f){
                paint.color=color.copy(alpha=color.alpha*alpha).toArgb();paint.textSize=dp(font)*fontScale
                paint.typeface=if(bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                drawContext.canvas.nativeCanvas.drawText(value,x,y-(paint.ascent()+paint.descent())/2,paint)
            }
            if(editing){var x=-size.height;while(x<size.width){drawLine(line.copy(alpha=.3f),Offset(x,size.height),Offset(x+size.height,0f),dp(.6f));x+=dp(18f)}}
            data.forEachIndexed{index,item->
                val x=index%7*cellWidth;val y=index/7*cellHeight;val center=x+cellWidth/2
                val date=item.info.date;val today=date==LocalDate.now();val chosen=date==selection||date==anchor
                val base=if(date.dayOfWeek.value>=6)weekend else surface
                val inset=if(editing)dp(2f)else 0f
                if(!editing)drawRect(base,Offset(x,y),Size(cellWidth,cellHeight))
                val cellFill=if(today)todayFill else if(chosen)lerp(base,brand,.16f)else if(editing)surface else base
                drawRoundRect(cellFill,Offset(x+inset,y+inset),Size(cellWidth-inset*2,cellHeight-inset*2),CornerRadius(dp(if(editing)12f else if(today||chosen)10f else 0f)))
                if(chosen)drawRoundRect(brand,Offset(x+inset+dp(1f),y+inset+dp(1f)),Size(cellWidth-inset*2-dp(2f),cellHeight-inset*2-dp(2f)),CornerRadius(dp(if(editing)12f else 10f)),style=Stroke(dp(2f)))
                if(today)drawRoundRect(brand,Offset(center-dp(12.5f),y+dp(4f)),Size(dp(25f),dp(24f)),CornerRadius(dp(6f)))
                val dateInk=if(today)onBrand else if(ShiftRepository.isHoliday(date)!=null)Color(if(dark)0xFFF4AAAA else 0xFFAA3042)else if(item.info.isCurrentMonth)text else muted
                label(date.dayOfMonth.toString(),center,y+dp(16f),15f,dateInk,true,if(item.info.isCurrentMonth)1f else .5f)
                val changed=previousBells.contains(date)!=item.onCall
                val bellAlpha=if(changed){if(item.onCall)bellProgress.value else 1-bellProgress.value}else if(item.onCall)1f else 0f
                if(bellAlpha>0){
                    val bx=center+dp(18f);val by=y+dp(16f);val color=amber.copy(alpha=bellAlpha)
                    val bell=Path().apply{moveTo(bx-dp(3.6f),by+dp(2.5f));lineTo(bx-dp(2.8f),by+dp(1f));lineTo(bx-dp(2.8f),by-dp(1.4f));cubicTo(bx-dp(2.8f),by-dp(5.4f),bx+dp(2.8f),by-dp(5.4f),bx+dp(2.8f),by-dp(1.4f));lineTo(bx+dp(2.8f),by+dp(1f));lineTo(bx+dp(3.6f),by+dp(2.5f));close()}
                    drawPath(bell,color);drawCircle(color,dp(1f),Offset(bx,by+dp(4f)))
                }
                val faded=if(item.preview||!item.info.isCurrentMonth).45f else 1f
                var cursor=y+dp(33f);var shown=0
                item.shifts.take(2).forEach{(shift,def)->
                    val extraHeight=(if(shift.overtimeHours>0)10f else 0f)+(if(shift.isTrasferta)10f else 0f)
                    val chipHeight=dp(35f+extraHeight)
                    if(cursor+chipHeight<y+cellHeight-dp(7f)){
                        val fill=lerp(if(dark)surface else Color.White,def.color,if(dark).28f else .56f)
                        val left=x+dp(if(editing)4f else 2f);val width=cellWidth-dp(if(editing)8f else 4f)
                        drawRoundRect(fill.copy(alpha=faded),Offset(left,cursor),Size(width,chipHeight),CornerRadius(dp(5f)))
                        var codeInk=if(dark)lerp(def.color,Color.White,.32f)else catalogInk(fill)
                        val a=codeInk.luminance();val b=fill.luminance()
                        if((maxOf(a,b)+.05f)/(minOf(a,b)+.05f)<4.5f)codeInk=catalogInk(fill)
                        label(getCompactShiftCode(def.id,def.shortCode),center,cursor+dp(10f),12f,codeInk,true,faded)
                        label(if(def.isAllDay)"– –" else calendarStartLabel(shift.customStartTime?:def.startTime,ShiftRepository.appPreferences.timeFormat24h),center,cursor+dp(26f),11f,text,false,faded)
                        var bandY=cursor+dp(35f)
                        if(shift.overtimeHours>0){drawRect(Color(0xFF94C4EF).copy(alpha=faded),Offset(left,bandY),Size(width,dp(10f)));label("+${formatOvertime(shift.overtimeHours)}",center,bandY+dp(5f),8f,Color(0xFF15547D),false,faded);bandY+=dp(10f)}
                        if(shift.isTrasferta){drawRect(Color(0xFF83CDB7).copy(alpha=faded),Offset(left,bandY),Size(width,dp(10f)));label("TR",center,bandY+dp(5f),8f,Color(0xFF214B3C),false,faded)}
                        cursor+=chipHeight+dp(3f);shown++
                    }
                }
                if(item.original!=null&&cursor+dp(12f)<y+cellHeight-dp(7f)){label("ex ${item.original}",center,cursor+dp(6f),9f,muted,false,faded);cursor+=dp(13f)}
                if(item.shifts.size>shown)item.shifts.drop(shown).take(4).forEachIndexed{i,(_,def)->
                    drawCircle(def.color.copy(alpha=faded),dp(2f),Offset(center+(i-(item.shifts.size-shown-1)/2f)*dp(6f),minOf(cursor+dp(4f),y+cellHeight-dp(5f))))
                }
            }
            if(!editing)for(row in 1 until days.size/7)drawLine(line.copy(alpha=.45f),Offset(0f,row*cellHeight),Offset(size.width,row*cellHeight),dp(.5f))
        }
        Column(Modifier.fillMaxSize()){
            data.chunked(7).forEach{week->Row(Modifier.weight(1f).fillMaxWidth()){
                week.forEach{day->Box(Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants=true){
                    role=Role.Button;this.text=AnnotatedString(day.info.date.dayOfMonth.toString());selected=selection==day.info.date||anchor==day.info.date
                    contentDescription=buildString{append(day.info.date.toString());if(day.onCall)append(", reperibilità");day.shifts.forEach{(_,def)->append(", ${def.name}, ${def.timeRangeFormatted}")};if(day.preview)append(", anteprima")}
                }.clickable{onPreview(day.info.date);onClick(day.info.date)})}
            }}
        }
    }
}
