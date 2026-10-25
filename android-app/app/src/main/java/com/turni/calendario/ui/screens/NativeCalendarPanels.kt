@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class,androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.sin
import kotlin.math.roundToInt

private val danger=Color(0xFFC84458)
private val overtime=Color(0xFF94C4EF)
private fun contrast(color:Color)=catalogInk(color)

@Composable
internal fun CalendarAction(icon:ImageVector,label:String,onClick:()->Unit,modifier:Modifier=Modifier,
    tint:Color=TextPrimary,background:Color=Color.Transparent) {
    val phase=remember{Animatable(0f)};val scope=rememberCoroutineScope()
    Box(modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background).bouncyClickable{
        scope.launch{phase.snapTo(0f);phase.animateTo(1f,tween(620));phase.snapTo(0f)};onClick()
    },contentAlignment=Alignment.Center) {
        Icon(icon,label,tint=tint,modifier=Modifier.size(22.dp).graphicsLayer{
            val p=phase.value;val pulse=sin(p*Math.PI).toFloat()
            rotationZ=when(icon){Icons.Default.Notifications->sin(p*Math.PI*5).toFloat()*13f
                Icons.Default.Settings,Icons.Default.Cached->p*360f
                Icons.Default.SwapHoriz->0f;else->pulse*4f}
            translationX=if(icon==Icons.Default.Flight)sin(p*Math.PI*2).toFloat()*7.dp.toPx() else if(icon==Icons.Default.SwapHoriz)sin(p*Math.PI*2).toFloat()*3.dp.toPx() else 0f
            alpha=if(icon==Icons.Default.Flight)1f-pulse*.55f else 1f
        })
    }
}

@Composable
internal fun NativeEditToolbar(mode:CalendarEditMode,brush:String,rotationId:String,
    onMode:(CalendarEditMode)->Unit,onBrush:(String)->Unit,onRotation:(String)->Unit,
    onUndo:()->Unit,onClose:()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ObsidianSurface).padding(8.dp)) {
        Row(Modifier.fillMaxWidth().height(32.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Modifica dei turni",color=TextSecondary,fontSize=12.sp,modifier=Modifier.weight(1f).padding(start=8.dp))
            CalendarAction(Icons.Default.Undo,"Annulla modifica",onUndo,Modifier.size(32.dp),TextSecondary)
            CalendarAction(Icons.Default.Close,"Chiudi modifica",onClose,Modifier.size(32.dp),danger)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(ObsidianSurfaceContainer)) {
            val unit=maxWidth/4;val offset by animateDpAsState(unit*mode.ordinal,spring(.9f,380f))
            Box(Modifier.offset(x=offset).width(unit).fillMaxHeight().padding(3.dp).clip(RoundedCornerShape(15.dp)).background(ObsidianPrimary))
            Row(Modifier.fillMaxSize()) {CalendarEditMode.values().forEach{item->
                val phase=remember{Animatable(0f)};val scope=rememberCoroutineScope()
                Column(Modifier.weight(1f).fillMaxHeight().clickable{
                    scope.launch{phase.snapTo(0f);phase.animateTo(1f,tween(780));phase.snapTo(0f)};onMode(item)
                },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                    Icon(item.icon,item.label,tint=if(mode==item)ObsidianBackground else TextPrimary,
                        modifier=Modifier.size(24.dp).graphicsLayer{
                            rotationZ=if(item==CalendarEditMode.ROTATION)phase.value*360 else 0f
                            translationX=if(item==CalendarEditMode.REPLACE)sin(phase.value*Math.PI*2).toFloat()*4.dp.toPx()else 0f
                            translationY=if(item==CalendarEditMode.ADD||item==CalendarEditMode.RANGE)-sin(phase.value*Math.PI).toFloat()*3.dp.toPx()else 0f
                        })
                    Text(item.label,fontSize=10.sp,fontWeight=FontWeight.Medium,color=if(mode==item)ObsidianBackground else TextPrimary,textAlign=TextAlign.Center)
                }
            }}
        }
        Row(Modifier.fillMaxWidth().height(58.dp).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            if(mode==CalendarEditMode.ROTATION) {
                ShiftRepository.getRotationsSortedByDefault().forEach{rotation->
                    Surface(shape=RoundedCornerShape(14.dp),color=if(rotation.id==rotationId)ObsidianPrimary else ObsidianSurfaceHigh,
                        modifier=Modifier.height(44.dp).bouncyClickable{onRotation(rotation.id)}) {
                        Row(Modifier.padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            if(ShiftRepository.defaultRotationId==rotation.id)Icon(Icons.Default.Star,null,Modifier.size(15.dp),tint=if(rotation.id==rotationId)ObsidianBackground else ObsidianPrimary)
                            Text(rotation.name,color=if(rotation.id==rotationId)ObsidianBackground else TextPrimary,fontSize=13.sp)
                        }
                    }
                }
                if(ShiftRepository.rotations.isEmpty())Text("Crea una rotazione in Turni",color=TextSecondary,fontSize=12.sp)
            }else{
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(if(brush=="eraser")ObsidianPrimary else ObsidianSurfaceHigh),contentAlignment=Alignment.Center) {
                    CalendarAction(TurniEraserIcon,"Gomma",{onBrush("eraser")},tint=if(brush=="eraser")ObsidianBackground else TextPrimary)
                }
                ShiftRepository.shiftDefinitions.filter{it.workProfileId==ShiftRepository.activeWorkProfileId}.forEach{def->
                    val selected=brush==def.id;var tapped by remember{mutableStateOf(false)}
                    val pulse by animateFloatAsState(if(tapped)1f else 0f,spring(.85f,300f))
                    LaunchedEffect(tapped){if(tapped){delay(180);tapped=false}}
                    Box(Modifier.size(48.dp).padding(2.dp).clip(RoundedCornerShape(12.dp))
                        .background(if(selected)def.color else lerp(ObsidianSurface,def.color,.3f))
                        .then(if(selected)Modifier.border(2.dp,TextPrimary.copy(alpha=.7f),RoundedCornerShape(12.dp))else Modifier)
                        .clickable{tapped=true;onBrush(def.id)},contentAlignment=Alignment.Center) {
                        Text(getCompactShiftCode(def.id,def.shortCode),color=if(selected)contrast(def.color)else TextPrimary,
                            fontWeight=FontWeight.Bold,fontSize=(13+pulse*1.5f).sp,maxLines=1,textAlign=TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NativeDayCard(date:LocalDate,onClose:()->Unit,onSelectShift:(Boolean)->Unit,onFeedback:(ActionFeedback)->Unit) {
    val shifts=ShiftRepository.getShiftsForDate(date).filter{ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId==ShiftRepository.activeWorkProfileId}
    var detailShift by remember(date){mutableStateOf<AssignedShift?>(null)}
    val oncall=ShiftRepository.isOnCall(date)
    val bellTint=if(TurniThemeState.dark)Color(0xFFF4CA70)else Color(0xFF805100)
    Surface(shape=RoundedCornerShape(22.dp),color=ObsidianSurface,shadowElevation=12.dp) {
        Column(Modifier.fillMaxWidth().heightIn(max=410.dp).verticalScroll(rememberScrollState()).animateContentSize(spring(.95f,400f)).padding(11.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)}",color=TextPrimary,
                    fontWeight=FontWeight.Bold,fontSize=17.sp,modifier=Modifier.weight(1f))
                CalendarAction(Icons.Default.Notifications,"Reperibilità",{
                    ShiftRepository.setDayOnCallManual(date,!oncall)
                    onFeedback(ActionFeedback(if(oncall)"Reperibilità rimossa" else "Reperibilità attivata",oncall))
                },tint=if(oncall)bellTint else TextSecondary,background=if(oncall)bellTint.copy(alpha=.14f)else Color.Transparent)
                CalendarAction(Icons.Default.Close,"Chiudi giorno",onClose)
            }
            shifts.forEach{shift->key(shift.id){
                val def=ShiftRepository.getShiftDefinition(shift.shiftDefId)
                if(def!=null)Column(Modifier.fillMaxWidth().animateContentSize(spring(.95f,400f)).padding(top=6.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(def.color),contentAlignment=Alignment.Center){
                            Text(def.shortCode.take(4),color=contrast(def.color),fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=1)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(def.name,color=TextPrimary,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                            Text(if(def.isAllDay)"Tutto il giorno" else "${shift.customStartTime?:def.startTime} – ${shift.actualEndTime?:shift.customEndTime?:def.endTime}",color=TextSecondary,fontSize=12.sp,maxLines=1)
                        }
                        CalendarAction(Icons.Default.Flight,"Trasferta",{ShiftRepository.toggleShiftTrasferta(date,shift.id)
                            onFeedback(ActionFeedback(if(shift.isTrasferta)"Trasferta rimossa" else "Trasferta aggiunta",shift.isTrasferta))},
                            modifier=Modifier.size(34.dp),tint=if(shift.isTrasferta)Color(0xFF64B99E)else TextSecondary)
                        CalendarAction(Icons.Default.Settings,"Impostazioni turno ${def.name}",{detailShift=shift},Modifier.size(34.dp))
                    }
                    AnimatedVisibility(shift.isTrasferta||shift.overtimeHours>0||shift.notes.isNotBlank(),
                        enter=expandVertically(spring(.95f,400f))+fadeIn(),exit=shrinkVertically()+fadeOut()) {
                        Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            if(shift.overtimeHours>0)DetailPill("+${formatOvertime(shift.overtimeHours)}",overtime)
                            if(shift.isTrasferta)DetailPill("TR · Trasferta",Color(0xFF83CDB7))
                            if(shift.notes.isNotBlank())Icon(Icons.Default.Notes,"Note presenti",Modifier.size(16.dp),tint=TextSecondary)
                        }
                    }
                }
            }}
            Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={onSelectShift(false)},shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f)){
                    Icon(Icons.Default.Edit,null,Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text("Cambia",fontSize=12.sp)
                }
                Button(onClick={onSelectShift(true)},shape=RoundedCornerShape(12.dp),modifier=Modifier.weight(1f)){
                    Icon(Icons.Default.Add,null,Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text("Aggiungi",fontSize=12.sp)
                }
            }
        }
    }
    detailShift?.let{shift->NativeShiftDetails(date,shift,onClose={detailShift=null},onFeedback=onFeedback)}
}

@Composable
private fun DetailPill(text:String,color:Color){
    Text(text,color=contrast(color),fontSize=10.sp,modifier=Modifier.clip(RoundedCornerShape(7.dp)).background(color).padding(horizontal=7.dp,vertical=3.dp))
}

/** One real, opaque dialog surface; closing retains its contents through the exit. */
@Composable
internal fun NativeBottomDialog(onClose:()->Unit,bottomInset:Dp=0.dp,content:@Composable ColumnScope.(close:()->Unit)->Unit) {
    var visible by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    val close={scope.launch{visible=false;delay(260);onClose()};Unit}
    LaunchedEffect(Unit){visible=true}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().clickable(onClick=close))
            AnimatedVisibility(visible,enter=slideInVertically(spring(.95f,380f)){it}+fadeIn(),exit=slideOutVertically(tween(240)){it}+fadeOut(),modifier=Modifier.padding(bottom=bottomInset)) {
                Surface(Modifier.fillMaxWidth().heightIn(max=620.dp).navigationBarsPadding(),shape=RoundedCornerShape(topStart=26.dp,topEnd=26.dp),color=ObsidianSurface) {
                    Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()).imePadding()){content(close)}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeShiftDetails(date:LocalDate,shift:AssignedShift,onClose:()->Unit,onFeedback:(ActionFeedback)->Unit) {
    val def=ShiftRepository.getShiftDefinition(shift.shiftDefId)?:return
    val baseEnd=shift.customEndTime?:def.endTime
    var end by remember(shift.id){mutableStateOf(shift.actualEndTime?:baseEnd)}
    var notes by remember(shift.id){mutableStateOf(shift.notes)}
    var noteTab by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf(false)}
    val parsed=runCatching{LocalTime.parse(end)}.getOrNull()
    val hours=if(parsed!=null)ShiftRepository.calculateOvertimeFromEndTime(baseEnd,end)else 0f
    val valid=parsed!=null&&hours<=16f
    NativeBottomDialog(onClose,bottomInset=84.dp){close->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL,Locale.ITALIAN)}",color=TextSecondary,fontSize=12.sp)
                Text(def.name,color=TextPrimary,fontSize=21.sp,fontWeight=FontWeight.Bold)
            }
            CalendarAction(Icons.Default.Close,"Chiudi dettagli",close)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(lerp(ObsidianSurface,def.color,.18f)).padding(12.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(def.color),contentAlignment=Alignment.Center){Text(def.shortCode.take(4),color=contrast(def.color),fontWeight=FontWeight.Bold,fontSize=13.sp)}
            Text(def.timeRangeFormatted,color=TextPrimary,fontSize=14.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceContainer).padding(3.dp)) {
            listOf("Straordinario","Note turno").forEachIndexed{index,label->
                Surface(Modifier.weight(1f).clickable{noteTab=index==1},shape=RoundedCornerShape(11.dp),color=if(noteTab==(index==1))ObsidianPrimary else Color.Transparent){
                    Text(label,color=if(noteTab==(index==1))ObsidianBackground else TextPrimary,fontSize=13.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(vertical=11.dp))
                }
            }
        }
        AnimatedContent(noteTab,transitionSpec={fadeIn(tween(180)) with fadeOut(tween(100))}){showNotes->
            if(showNotes)OutlinedTextField(value=notes,onValueChange={notes=it},label={Text("Note di questo turno")},modifier=Modifier.fillMaxWidth().padding(top=12.dp).heightIn(min=100.dp))
            else Column(Modifier.fillMaxWidth().padding(top=12.dp)) {
                if(def.isAllDay)Text("Questo turno è impostato su tutto il giorno.",color=TextSecondary,fontSize=13.sp)
                else {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(end,{end=it.take(5)},label={Text("Stacco effettivo")},singleLine=true,isError=!valid,
                            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii),modifier=Modifier.weight(1f))
                        CalendarAction(Icons.Default.Undo,"Azzera straordinario",{end=baseEnd},tint=danger)
                    }
                    Text(if(valid&&hours>0)"Straordinario: ${formatOvertime(hours)}" else if(!valid)"Inserisci HH:mm, massimo 16 ore extra" else "Fine prevista $baseEnd",color=if(!valid)danger else TextSecondary,fontSize=12.sp,modifier=Modifier.padding(top=7.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        listOf(15,30,45,60,90,120).forEach{mins->
                            Surface(Modifier.height(38.dp).bouncyClickable{end=LocalTime.parse(baseEnd).plusMinutes(mins.toLong()).toString()},shape=RoundedCornerShape(11.dp),color=ObsidianSurfaceHigh){
                                Box(Modifier.padding(horizontal=11.dp),contentAlignment=Alignment.Center){Text(if(mins<60)"+${mins}m" else if(mins%60==0)"+${mins/60}h" else "+1h30",color=TextPrimary,fontSize=12.sp)}
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
            CalendarAction(Icons.Default.DeleteOutline,"Elimina turno",{deleting=true},tint=danger)
            Button(onClick={ShiftRepository.setShiftExtraDetails(date,shift.id,if(def.isAllDay)null else end,if(def.isAllDay)0f else hours,notes)
                onFeedback(ActionFeedback("Informazioni salvate",false));close()},enabled=def.isAllDay||valid,modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){
                Icon(Icons.Default.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Salva",modifier=Modifier.padding(vertical=5.dp))
            }
        }
        if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("Eliminare questo turno?")},
            confirmButton={TextButton(onClick={ShiftRepository.removeAssignedShift(date,shift.id);onFeedback(ActionFeedback("Turno eliminato",true));close()}){Text("Elimina",color=danger)}},
            dismissButton={TextButton(onClick={deleting=false}){Text("Annulla")}},containerColor=ObsidianSurface)
    }
}

@Composable
internal fun NativeElementEraser(date:LocalDate,extrasOnly:Boolean,onClose:()->Unit,onRemove:(String)->Unit) {
    NativeBottomDialog(onClose){close->
        Row(verticalAlignment=Alignment.CenterVertically){Text("Cosa vuoi eliminare?",color=TextPrimary,fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));CalendarAction(Icons.Default.Close,"Chiudi gomma",close)}
        ShiftRepository.getShiftsForDate(date).filter{ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId==ShiftRepository.activeWorkProfileId}.drop(if(extrasOnly)1 else 0).forEach{shift->
            val def=ShiftRepository.getShiftDefinition(shift.shiftDefId)
            Row(Modifier.fillMaxWidth().padding(vertical=7.dp).clip(RoundedCornerShape(13.dp)).background(ObsidianSurfaceContainer).clickable{
                onRemove(shift.id)
                val remaining=ShiftRepository.getShiftsForDate(date).filter{ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId==ShiftRepository.activeWorkProfileId}
                if((extrasOnly&&remaining.size<=1)||(remaining.isEmpty()&&!ShiftRepository.isOnCall(date)))close()
            }.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(12.dp).background(def?.color?:ObsidianPrimary,CircleShape));Spacer(Modifier.width(10.dp));Text(def?.name?:"Turno",color=TextPrimary,modifier=Modifier.weight(1f));Icon(Icons.Default.DeleteOutline,"Elimina",tint=danger)
            }
        }
        if(!extrasOnly&&ShiftRepository.isOnCall(date))TextButton(onClick={onRemove("oncall");if(ShiftRepository.getShiftsForDate(date).isEmpty())close()}){Icon(Icons.Default.Notifications,null);Spacer(Modifier.width(10.dp));Text("Rimuovi reperibilità")}
    }
}

@Composable
internal fun NativeShiftChooser(onDismiss:()->Unit,onChoose:(String)->Unit) {
    NativeBottomDialog(onDismiss){close->
        Row(verticalAlignment=Alignment.CenterVertically){Text("Scegli turno",color=TextPrimary,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));CalendarAction(Icons.Default.Close,"Chiudi selezione",close)}
        ShiftRepository.shiftDefinitions.filter{it.workProfileId==ShiftRepository.activeWorkProfileId}.forEach{def->
            Row(Modifier.fillMaxWidth().padding(top=7.dp).clip(RoundedCornerShape(14.dp)).background(lerp(ObsidianSurface,def.color,.15f)).bouncyClickable{onChoose(def.id);close()}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(def.color),contentAlignment=Alignment.Center){Text(def.shortCode.take(4),color=contrast(def.color),fontWeight=FontWeight.Bold,fontSize=13.sp)}
                Column(Modifier.padding(start=12.dp)){Text(def.name,color=TextPrimary,fontWeight=FontWeight.SemiBold);Text(def.timeRangeFormatted,color=TextSecondary,fontSize=12.sp)}
            }
        }
    }
}
