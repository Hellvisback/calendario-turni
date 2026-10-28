package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val todayButtonState=remember{MutableTransitionState(month!=todayMonth)}
    todayButtonState.targetState=month!=todayMonth
    var selectedDate by remember{mutableStateOf<LocalDate?>(null)}
    var lastDate by remember{mutableStateOf(LocalDate.now())}
    var editing by remember{mutableStateOf(false)}
    var mode by remember{mutableStateOf(CalendarEditMode.REPLACE)}
    var brush by remember{mutableStateOf(ShiftRepository.shiftDefinitions.firstOrNull()?.id?:"")}
    var rotationId by remember{mutableStateOf(ShiftRepository.defaultRotationId.ifBlank{ShiftRepository.activeRotationId})}
    var anchor by remember{mutableStateOf<LocalDate?>(null)}
    var rangeEnd by remember{mutableStateOf<LocalDate?>(null)}
    var editTool by remember{mutableStateOf<String?>(null)}
    var rangeGesture by remember{mutableStateOf<CalendarEditMode?>(null)}
    var eraseAnchor by remember{mutableStateOf<LocalDate?>(null)}
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
    LaunchedEffect(active){if(!active){selectedDate=null;workMenu=false;anchor=null;rangeEnd=null;rangeGesture=null;eraseAnchor=null}}
    DisposableEffect(Unit){onDispose{onEditModeActiveChanged(false)}}
    LaunchedEffect(active,ShiftRepository.selectedDateFromWidget){if(active)ShiftRepository.selectedDateFromWidget?.let{
        monthKey=YearMonth.from(it).toString();lastDate=it;selectedDate=it;ShiftRepository.selectedDateFromWidget=null
    }}
    fun closeEditor(){editing=false;anchor=null;rangeEnd=null;eraseAnchor=null;rangeGesture=null}
    BackHandler(active&&(editing||selectedDate!=null||workMenu)){when{workMenu->workMenu=false;selectedDate!=null->selectedDate=null;eraseAnchor!=null->{eraseAnchor=null;feedback=null};rangeGesture!=null->{rangeGesture=null;anchor=null;rangeEnd=null;feedback=null};else->closeEditor()}}
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
    fun removeAt(date:LocalDate){
        val shifts=ShiftRepository.getShiftsForDate(date).filter{ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId==ShiftRepository.activeWorkProfileId}
        if(calendarNeedsSelectiveEraser(shifts.size))eraseDate=date
        else {
            ShiftRepository.clearWorkDayShifts(date,clearOnCall=true)
            paintDay(date,"");feedback=ActionFeedback("Giornata cancellata",true)
        }
    }
    fun dayClick(date:LocalDate){
        if(dragging||abs(drag.value)>1f)return
        if(!editing){selectedDate=date;lastDate=date;return}
        if(editTool=="eraser"){
            val first=eraseAnchor
            if(first==null)removeAt(date)else{
                ShiftRepository.clearWorkShiftRange(first,date)
                paintRequest=CalendarPaintRequest(first,"",(paintRequest?.sequence?:0L)+1L,date)
                eraseAnchor=null;anchor=null;rangeEnd=null
                feedback=ActionFeedback("Periodo cancellato · puoi annullare",true)
            }
            return
        }
        if(editTool=="bell"){
            val wasOnCall=ShiftRepository.isOnCall(date)
            ShiftRepository.setDayOnCallManual(date,!wasOnCall)
            feedback=ActionFeedback(if(wasOnCall)"Reperibilità rimossa" else "Reperibilità attivata",wasOnCall)
            return
        }
        if(rangeGesture!=null&&anchor!=null){
            val first=anchor!!
            val adding=rangeGesture==CalendarEditMode.ADD
            ShiftRepository.applyShiftRange(brush,first,date,addSecondary=adding)
            paintRequest=CalendarPaintRequest(first,brush,(paintRequest?.sequence?:0L)+1L,date)
            anchor=null;rangeEnd=null;rangeGesture=null
            feedback=ActionFeedback(if(adding)"Turni aggiunti al periodo" else "Periodo applicato")
            return
        }
        when(mode){
            CalendarEditMode.REPLACE->{
                ShiftRepository.replaceShift(date,brush);paintDay(date,brush);feedback=ActionFeedback("Turno aggiornato",false)
            }
            CalendarEditMode.ADD->{
                ShiftRepository.addSecondaryShift(date,brush);paintDay(date,brush);feedback=ActionFeedback("Turno aggiunto",false)
            }
            CalendarEditMode.RANGE->if(anchor==null){anchor=date;rangeEnd=null}else{
                val start=minOf(anchor!!,date);val end=maxOf(anchor!!,date)
                ShiftRepository.applyShiftRange(brush,start,end)
                paintRequest=CalendarPaintRequest(start,if(brush=="eraser")"" else brush,(paintRequest?.sequence?:0L)+1L,end)
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
                    AnimatedVisibility(
                        visibleState=todayButtonState,
                        enter=fadeIn(tween(240))+expandHorizontally(spring(1f,550f),expandFrom=Alignment.Start)+slideInVertically(tween(280)){it/5},
                        exit=fadeOut(tween(160))+shrinkHorizontally(tween(260),shrinkTowards=Alignment.Start)+slideOutVertically(tween(220)){it/5}
                    ) {
                        CalendarTodayButton(enabled=month!=todayMonth) {
                            motion?.cancel()
                            // Even a distant month returns in one short slide, never a multi-page leap.
                            val direction=if(month.isBefore(todayMonth))1 else -1
                            Snapshot.withMutableSnapshot {
                                monthKey=todayMonth.toString()
                                drag.value=direction*width
                                selectedDate=null
                            }
                            motion=scope.launch {
                                val position=Animatable(drag.value)
                                position.animateTo(0f,spring(1f,440f)){drag.value=value}
                            }
                        }
                    }
                }
                if(!editing){
                    Row(Modifier.widthIn(max=132.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.End){
                    Box {
                        Row(Modifier.widthIn(max=if(todayButtonState.currentState||todayButtonState.targetState)64.dp else 96.dp).heightIn(min=44.dp).bouncyClickable{workMenu=!workMenu}.padding(horizontal=5.dp),
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Icon(workProfileIcon(ShiftRepository.activeWorkProfile.iconKey),null,tint=TextSecondary,modifier=Modifier.size(16.dp))
                            Text(ShiftRepository.activeWorkProfile.name,color=TextSecondary,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        SiteWorkMenu(workMenu,onDismiss={workMenu=false},onChoose={id->ShiftRepository.setActiveWorkProfile(id);workMenu=false;selectedDate=null})
                    }
                    IconButton(onClick=onOpenSettings,modifier=Modifier.size(36.dp)){SiteIcon("settings","Impostazioni",Modifier.size(21.dp))}
                    }
                }
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
                    NativeMonthGrid(month.plusMonths(delta.toLong()),selectedDate,editing,if(eraseAnchor!=null||rangeGesture!=null)CalendarEditMode.RANGE else mode,if(eraseAnchor!=null)"eraser" else brush,rotationId,eraseAnchor?:anchor,if(eraseAnchor!=null)null else rangeEnd,
                        Modifier.fillMaxSize().graphicsLayer{translationX=delta*width+drag.value},
                        paintRequest=paintRequest,animate=delta==0,onClick={dayClick(it)},onPreview={if(editing&&mode==CalendarEditMode.RANGE&&anchor!=null&&editTool!="eraser")rangeEnd=it},
                        onLongClick=if(editing&&editTool!="bell"&&(editTool=="eraser"||mode==CalendarEditMode.REPLACE||mode==CalendarEditMode.ADD))({date->
                            if(editTool=="eraser"){
                                eraseAnchor=date;anchor=null;rangeEnd=null;rangeGesture=null
                                feedback=ActionFeedback("Inizio selezionato · tocca l’ultimo giorno da cancellare")
                            }else{
                                rangeGesture=mode;anchor=date;rangeEnd=null;eraseAnchor=null
                                feedback=ActionFeedback(if(mode==CalendarEditMode.ADD)"Inizio selezionato · aggiungi turni fino all’ultimo giorno" else "Inizio selezionato · tocca l’ultimo giorno del periodo")
                            }
                        })else null)
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
            NativeEditToolbar(mode,brush,rotationId,editTool,onTool={editTool=if(editTool==it)null else it;anchor=null;rangeEnd=null;eraseAnchor=null;rangeGesture=null},onMode={mode=it;anchor=null;rangeEnd=null;eraseAnchor=null;rangeGesture=null},onBrush={brush=it;editTool=null;eraseAnchor=null;rangeGesture=null;anchor=null;rangeEnd=null},
                onRotation={rotationId=it},onUndo={if(ShiftRepository.undo()){rangeGesture=null;eraseAnchor=null;anchor=null;rangeEnd=null;feedback=ActionFeedback("Modifica annullata",false)}},onClose={closeEditor()})
        }
        MagicalActionFeedbackHUD(feedback,onDismiss={feedback=null},modifier=Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=8.dp))
        eraseDate?.let{date->
            NativeElementEraser(date,onClose={eraseDate=null},onRemove={id->
                ShiftRepository.removeAssignedShift(date,id,clearOnCall=true);paintDay(date,"")
                feedback=ActionFeedback("Turno eliminato",true)
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

/** A bounded, accessible action with surface feedback; text is never scaled or blurred. */
@Composable
private fun CalendarTodayButton(enabled:Boolean,onClick:()->Unit) {
    val interactions=remember{MutableInteractionSource()}
    val pressed by interactions.collectIsPressedAsState()
    val fill by animateColorAsState(if(pressed)ObsidianPrimaryContainer else ObsidianPrimarySubtle,spring(1f,650f))
    val offset by animateFloatAsState(if(pressed)1.5f else 0f,spring(.85f,650f))
    val shape=RoundedCornerShape(10.dp)
    Box(
        Modifier.size(width=46.dp,height=44.dp)
            .semantics{contentDescription="Torna al mese di oggi"}
            .clickable(enabled=enabled,interactionSource=interactions,indication=null,role=Role.Button,onClick=onClick),
        contentAlignment=Alignment.Center
    ) {
        Box(
            Modifier.size(width=42.dp,height=32.dp)
                .graphicsLayer{translationY=offset*density}
                .background(fill,shape).border(1.dp,ObsidianPrimary.copy(alpha=.5f),shape),
            contentAlignment=Alignment.Center
        ) {
            Text("Oggi",color=ObsidianPrimary,fontSize=11.sp,fontWeight=FontWeight.SemiBold,maxLines=1)
        }
    }
}
