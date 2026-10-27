package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

data class CalendarDayInfo(val date:LocalDate,val isCurrentMonth:Boolean)
enum class CalendarEditMode(val label:String,val icon:ImageVector) {
    REPLACE("Cambia",siteVector("swap")), RANGE("Periodo",siteVector("calendar")),
    ADD("Più turni",siteVector("layers")), ROTATION("Rotazioni",siteVector("rotation"))
}

fun getCompactShiftCode(shiftDefId:String,fallback:String):String =
    fallback.ifBlank{shiftDefId.take(4)}.take(4).uppercase(Locale.ROOT)

fun getMonthGridDays(targetMonth:YearMonth,weekStartMonday:Boolean):List<CalendarDayInfo> {
    val first=targetMonth.atDay(1)
    val leading=if(weekStartMonday) first.dayOfWeek.value-1 else first.dayOfWeek.value%7
    val count=((leading+targetMonth.lengthOfMonth()+6)/7)*7
    return List(count){i->val date=first.minusDays(leading.toLong()).plusDays(i.toLong());CalendarDayInfo(date,YearMonth.from(date)==targetMonth)}
}

/** Short drags work anywhere in the grid; a deliberate fling can commit sooner. */
fun monthSwipeDirection(distance:Float,velocity:Float,width:Float,density:Float):Int {
    if(width<=0||density<=0)return 0
    val threshold=(width*.10f).coerceIn(24*density,40*density)
    return when {
        abs(distance)>=threshold -> if(distance<0)1 else -1
        abs(distance)>=12*density&&abs(velocity)>=500*density&&distance*velocity>0 -> if(distance<0)1 else -1
        else -> 0
    }
}

@Composable
fun CalendarScreen(active:Boolean=true,onEditModeActiveChanged:(Boolean)->Unit={},editRequest:Int=0,onOpenSettings:()->Unit={}) {
    var monthKey by rememberSaveable{mutableStateOf(YearMonth.now().toString())}
    val month=remember(monthKey){YearMonth.parse(monthKey)}
    val todayMonth=YearMonth.now()
    var selectedDate by remember{mutableStateOf<LocalDate?>(null)}
    var lastDate by remember{mutableStateOf(LocalDate.now())}
    var editing by remember{mutableStateOf(false)}
    var mode by remember{mutableStateOf(CalendarEditMode.REPLACE)}
    var brush by remember{mutableStateOf(ShiftRepository.shiftDefinitions.firstOrNull()?.id?:"")}
    var rotationId by remember{mutableStateOf(ShiftRepository.defaultRotationId.ifBlank{ShiftRepository.activeRotationId})}
    var anchor by remember{mutableStateOf<LocalDate?>(null)}
    var rangeEnd by remember{mutableStateOf<LocalDate?>(null)}
    var eraseDate by remember{mutableStateOf<LocalDate?>(null)}
    var selectShift by remember{mutableStateOf<LocalDate?>(null)}
    var addSecondary by remember{mutableStateOf(false)}
    var workMenu by remember{mutableStateOf(false)}
    var feedback by remember{mutableStateOf<ActionFeedback?>(null)}
    var paintRequest by remember{mutableStateOf<CalendarPaintRequest?>(null)}
    fun paintDay(date:LocalDate,definitionId:String){paintRequest=CalendarPaintRequest(date,definitionId,(paintRequest?.sequence?:0L)+1L)}
    val scope=rememberCoroutineScope();val drag=remember{mutableStateOf(0f)}
    var motion by remember{mutableStateOf<Job?>(null)}
    var width by remember{mutableStateOf(1f)}
    var dragging by remember{mutableStateOf(false)}
    val density=LocalDensity.current.density
    LaunchedEffect(editRequest){if(editRequest>0){editing=true;selectedDate=null}}
    LaunchedEffect(editing,active){onEditModeActiveChanged(active&&editing)}
    LaunchedEffect(active){if(!active){selectedDate=null;workMenu=false;anchor=null;rangeEnd=null}}
    DisposableEffect(Unit){onDispose{onEditModeActiveChanged(false)}}
    LaunchedEffect(active,ShiftRepository.selectedDateFromWidget){if(active)ShiftRepository.selectedDateFromWidget?.let{
        monthKey=YearMonth.from(it).toString();lastDate=it;selectedDate=it;ShiftRepository.selectedDateFromWidget=null
    }}
    fun closeEditor(){editing=false;anchor=null;rangeEnd=null}
    BackHandler(active&&(editing||selectedDate!=null||workMenu)){when{workMenu->workMenu=false;selectedDate!=null->selectedDate=null;else->closeEditor()}}
    fun changeMonth(direction:Int){
        motion?.cancel()
        if(direction!=0)Snapshot.withMutableSnapshot {
            monthKey=month.plusMonths(direction.toLong()).toString()
            drag.value+=direction*width
            selectedDate=null
        }
        motion=scope.launch {
            val position=Animatable(drag.value)
            position.animateTo(0f,spring(1f,440f)){drag.value=value}
        }
    }
    fun removeAt(date:LocalDate,extrasOnly:Boolean=false){
        val shifts=ShiftRepository.getShiftsForDate(date).filter{ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId==ShiftRepository.activeWorkProfileId}
        if(extrasOnly){
            val extras=calendarExtraEraserTargets(shifts.map{it.id})
            when {
                extras.size==1->{ShiftRepository.removeAssignedShift(date,extras.single());paintDay(date,"");feedback=ActionFeedback("Turno aggiuntivo eliminato",true)}
                extras.size>1->eraseDate=date
            }
        }
        else if(calendarNeedsSelectiveEraser(shifts.size))eraseDate=date
        else if(shifts.isNotEmpty()){ShiftRepository.clearWorkDayShifts(date,clearOnCall=true);paintDay(date,"");feedback=ActionFeedback("Turno eliminato",true)}
        else if(ShiftRepository.isOnCall(date)){ShiftRepository.setDayOnCallManual(date,false);feedback=ActionFeedback("Reperibilità rimossa",true)}
    }
    fun dayClick(date:LocalDate){
        if(dragging||abs(drag.value)>1f)return
        if(!editing){selectedDate=date;lastDate=date;return}
        when(mode){
            CalendarEditMode.REPLACE->if(brush=="eraser")removeAt(date)else{
                ShiftRepository.replaceShift(date,brush);paintDay(date,brush);feedback=ActionFeedback("Turno aggiornato",false)
            }
            CalendarEditMode.ADD->if(brush=="eraser")removeAt(date,true)else{
                ShiftRepository.addSecondaryShift(date,brush);paintDay(date,brush);feedback=ActionFeedback("Turno aggiunto",false)
            }
            CalendarEditMode.RANGE->if(anchor==null){anchor=date;rangeEnd=null}else{
                val start=minOf(anchor!!,date);val end=maxOf(anchor!!,date)
                ShiftRepository.applyShiftRange(brush,start,end)
                paintDay(date,if(brush=="eraser")"" else brush)
                anchor=null;rangeEnd=null;feedback=ActionFeedback(if(brush=="eraser")"Turni eliminati" else "Periodo applicato",brush=="eraser")
            }
            CalendarEditMode.ROTATION->if(anchor==null||date.isBefore(anchor)){anchor=date}else{
                val rotation=ShiftRepository.rotations.firstOrNull{it.id==rotationId}
                val offset=ChronoUnit.DAYS.between(anchor,date).toInt()
                ShiftRepository.applyRotation(rotationId,anchor!!,offset+1)
                rotation?.daySequence?.takeIf{it.isNotEmpty()}?.let{paintDay(date,it[offset%it.size])}
                anchor=null;feedback=ActionFeedback("Rotazione applicata",false)
            }
        }
    }
    Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=8.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={changeMonth(-1)},modifier=Modifier.size(32.dp)){SiteIcon("chevron-left","Mese precedente",Modifier.size(21.dp))}
                    Text(androidx.compose.ui.text.buildAnnotatedString {
                        append(month.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN).replaceFirstChar{it.titlecase(Locale.ITALIAN)})
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize=14.sp,fontWeight=FontWeight.Medium,color=TextSecondary))
                        append(" "+month.year);pop()
                    },color=TextPrimary,fontSize=18.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,
                        modifier=Modifier.weight(1f,fill=false))
                    IconButton(onClick={changeMonth(1)},modifier=Modifier.size(32.dp)){SiteIcon("chevron-right","Mese successivo",Modifier.size(21.dp))}
                    AnimatedVisibility(month!=todayMonth,enter=fadeIn(tween(180))+expandHorizontally(),exit=fadeOut(tween(120))+shrinkHorizontally()) {
                        Text("Oggi",fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=ObsidianPrimary,
                            modifier=Modifier.clip(RoundedCornerShape(8.dp)).bouncyClickable {
                                motion?.cancel();drag.value=0f;monthKey=todayMonth.toString();selectedDate=null
                            }.padding(horizontal=5.dp,vertical=10.dp))
                    }
                }
                if(!editing){
                    Row(Modifier.width(132.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.End){
                    Box {
                        Row(Modifier.widthIn(max=96.dp).heightIn(min=44.dp).bouncyClickable{workMenu=!workMenu}.padding(horizontal=5.dp),
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Icon(workProfileIcon(ShiftRepository.activeWorkProfile.iconKey),null,tint=TextSecondary,modifier=Modifier.size(16.dp))
                            Text(ShiftRepository.activeWorkProfile.name,color=TextSecondary,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        SiteWorkMenu(workMenu,onDismiss={workMenu=false},onChoose={id->ShiftRepository.setActiveWorkProfile(id);workMenu=false;selectedDate=null})
                    }
                    IconButton(onClick=onOpenSettings,modifier=Modifier.size(36.dp)){SiteIcon("settings","Impostazioni",Modifier.size(21.dp))}
                    }
                }else Spacer(Modifier.width(132.dp))
            }
            Row(Modifier.fillMaxWidth().height(28.dp).background(CalendarSurface,RoundedCornerShape(topStart=20.dp,topEnd=20.dp)),verticalAlignment=Alignment.CenterVertically){
                val days=if(ShiftRepository.appPreferences.weekStartMonday)listOf("L","M","M","G","V","S","D")else listOf("D","L","M","M","G","V","S")
                days.forEach{Text(it,Modifier.weight(1f),color=TextSecondary,fontSize=12.sp,textAlign=TextAlign.Center)}
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(bottom=62.dp)
                .clip(RoundedCornerShape(bottomStart=20.dp,bottomEnd=20.dp)).onSizeChanged{width=it.width.toFloat()}
                .pointerInput(monthKey,editing){
                    val velocity=VelocityTracker()
                    var gestureDistance=0f
                    detectHorizontalDragGestures(onDragStart={motion?.cancel();dragging=true;gestureDistance=0f;velocity.resetTracking()},
                        onHorizontalDrag={change,amount->change.consume();velocity.addPosition(change.uptimeMillis,change.position)
                            gestureDistance+=amount
                            drag.value=(drag.value+amount).coerceIn(-width,width)},
                        onDragCancel={dragging=false;changeMonth(0)},
                        onDragEnd={dragging=false;changeMonth(monthSwipeDirection(gestureDistance,velocity.calculateVelocity().x,width,density))})
                }) {
                // Precomposed neighbors: motion updates layer translation, not 42 cell compositions per frame.
                for(delta in -1..1) {
                    key(month.plusMonths(delta.toLong())) {
                    NativeMonthGrid(month.plusMonths(delta.toLong()),selectedDate,editing,mode,brush,rotationId,anchor,rangeEnd,
                        Modifier.fillMaxSize().graphicsLayer{translationX=delta*width+drag.value},
                        paintRequest=paintRequest,animate=delta==0,onClick={dayClick(it)},onPreview={if(editing&&mode==CalendarEditMode.RANGE&&anchor!=null)rangeEnd=it})
                    }
                }
            }
        }
        AnimatedVisibility(selectedDate!=null&&!editing,enter=slideInVertically(spring(.9f,340f)){it/2}+fadeIn(),
            exit=slideOutVertically(spring(1f,440f)){it/2}+fadeOut(),
            modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start=17.dp,end=17.dp,bottom=86.dp)) {
            NativeDayCard(lastDate,onClose={selectedDate=null},onSelectShift={addSecondary=it;selectShift=lastDate},
                onFeedback={feedback=it},onDateChange={lastDate=it;selectedDate=it;if(YearMonth.from(it)!=month)monthKey=YearMonth.from(it).toString()})
        }
        AnimatedVisibility(editing,enter=slideInVertically(spring(.95f,380f)){it}+fadeIn(),
            exit=slideOutVertically(spring(1f,420f)){it}+fadeOut(),
            modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=8.dp).padding(bottom=6.dp)) {
            NativeEditToolbar(mode,brush,rotationId,onMode={mode=it;anchor=null;rangeEnd=null},onBrush={brush=it},
                onRotation={rotationId=it},onUndo={if(ShiftRepository.undo()){anchor=null;rangeEnd=null;feedback=ActionFeedback("Modifica annullata",false)}},onClose={closeEditor()})
        }
        MagicalActionFeedbackHUD(feedback,onDismiss={feedback=null},modifier=Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=8.dp))
        eraseDate?.let{date->
            NativeElementEraser(date,mode==CalendarEditMode.ADD,onClose={eraseDate=null},onRemove={id->
                if(id=="oncall")ShiftRepository.setDayOnCallManual(date,false)else {ShiftRepository.removeAssignedShift(date,id);paintDay(date,"")}
                feedback=ActionFeedback(if(id=="oncall")"Reperibilità rimossa" else "Turno eliminato",true)
            })
        }
        selectShift?.let{date-> NativeShiftChooser(onDismiss={selectShift=null},onChoose={id->
            if(addSecondary)ShiftRepository.addSecondaryShift(date,id)else ShiftRepository.replaceShift(date,id)
            feedback=ActionFeedback(if(addSecondary)"Turno aggiunto" else "Turno aggiornato",false)
        })}
    }
}

fun workProfileIcon(key:String):ImageVector=siteVector(when(key){
    "train","transit"->"train";"hospital","medical","health"->"hospital"
    "wrench","build","factory"->"wrench";"groups","people"->"users"
    "laptop","computer"->"laptop";"truck","local_shipping","shipping"->"truck"
    "business","building"->"building";else->"briefcase"
})

internal fun formatOvertime(hours:Float):String = calendarOvertimeLabel(hours).removePrefix("+")
