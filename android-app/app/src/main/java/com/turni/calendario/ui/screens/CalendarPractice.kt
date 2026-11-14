@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.*
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*

private data class PracticeState(val days:List<List<String>> = List(7){emptyList()},val bells:Set<Int> = emptySet(),val colors:Map<Int,String> = emptyMap())

/** Same toolbar and shift artwork as the editor; data lives only in this composition. */
@Composable
internal fun GesturePractice() {
    val work=ShiftRepository.activeWorkProfileId
    val models=ShiftRepository.shiftDefinitions.filter{it.workProfileId==work}.ifEmpty{starterShiftDefinitions(work)}
    val defs=models.associateBy{it.id}
    val cycle=remember(models.map{it.id}){ShiftRotation("practice","Rotazione di esempio",4,List(4){models[it%models.size].id})}
    var state by remember{mutableStateOf(PracticeState())}
    var history by remember{mutableStateOf(listOf<PracticeState>())}
    var editing by remember{mutableStateOf(true)}
    val editPhase by animateFloatAsState(if(editing)1f else 0f,spring(1f,180f))
    val cellHeight by animateDpAsState(if(editing)116.dp else 132.dp,spring(1f,180f))
    var mode by remember{mutableStateOf(CalendarEditMode.REPLACE)}
    var brush by remember{mutableStateOf(models.first().id)}
    var tool by remember{mutableStateOf<String?>(null)}
    var anchor by remember{mutableStateOf<Int?>(null)}
    var eraseChoice by remember{mutableStateOf<Int?>(null)}
    var message by remember{mutableStateOf("Scegli un turno qui sotto e tocca un giorno. Questa prova non salva nulla nel calendario.")}
    fun change(next:PracticeState){history=(history+state).takeLast(20);state=next}
    fun apply(index:Int){
        if(!editing){message="Nel calendario reale qui si apre il dettaglio del giorno. Tocca la matita sotto per riprendere la prova.";return}
        if(tool=="bell"){change(state.copy(bells=if(index in state.bells)state.bells-index else state.bells+index));message="Campanella ${if(index in state.bells)"attivata" else "rimossa"} sul giorno ${index+1}.";return}
        if(mode==CalendarEditMode.ROTATION&&tool!="eraser"&&anchor==null){anchor=index;message="Inizio della rotazione scelto. Tocca il giorno finale: il ciclo si ripeterà fino a lì.";return}
        val first=anchor?:index
        if(mode==CalendarEditMode.ROTATION&&tool!="eraser"&&index<first){anchor=index;message="Nuovo inizio della rotazione scelto. Tocca il giorno finale: il ciclo si ripeterà fino a lì.";return}
        val targets=(minOf(first,index)..maxOf(first,index)).toList()
        if(tool=="eraser"&&anchor==null&&state.days[index].size>1){eraseChoice=index;message="Questo giorno ha più turni: scegli quale eliminare qui sotto.";return}
        val days=state.days.mapIndexed{day,values->if(day !in targets)values else when {
            tool=="eraser"->emptyList()
            mode==CalendarEditMode.ADD->values+brush
            mode==CalendarEditMode.ROTATION->listOf(cycle.daySequence[(day-first)%cycle.daySequence.size])
            else->listOf(brush)
        }}
        val colors=if(tool=="eraser")state.colors-targets.toSet()else state.colors+targets.associateWith{day->days[day].last()}
        change(state.copy(days=days,bells=if(tool=="eraser")state.bells-targets.toSet()else state.bells,colors=colors))
        anchor=null;eraseChoice=null
        message=if(tool=="eraser")"Cancellati ${targets.size} ${if(targets.size==1)"giorno" else "giorni"} nella prova." else if(targets.size>1)"Turni applicati dal giorno ${targets.first()+1} al ${targets.last()+1}." else "Turno applicato al giorno ${index+1}. Prova Annulla oppure cambia modalità."
    }
    CloudCard("Prova la modifica", "Usa i tuoi modelli oppure gli esempi, se non ne hai ancora. La barra e le sue animazioni sono le stesse del calendario; questa settimana è solo una simulazione.") {
        Row(Modifier.fillMaxWidth()) { listOf("L","M","M","G","V","S","D").forEachIndexed { i,day -> Text(day,Modifier.weight(1f).background(if(i>=5)CalendarWeekend else CalendarSurface).padding(vertical=5.dp),textAlign=TextAlign.Center,fontSize=10.sp,color=TextSecondary) } }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CalendarSurface),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            repeat(7){index->
                val preview=editing&&mode==CalendarEditMode.ROTATION&&tool!="eraser"&&anchor!=null&&index>=anchor!!
                val values=if(preview)listOf(cycle.daySequence[(index-anchor!!)%cycle.daySequence.size])else state.days[index]
                val source=state.colors[index]?.let{defs[it]}
                val fill by animateColorAsState(if(editing&&source!=null)shiftDisplayColor(source.color)else if(index>=5)CalendarWeekend else CalendarSurface,spring(1f,220f))
                Column(Modifier.weight(1f).height(cellHeight).clip(RoundedCornerShape(10.dp)).background(fill)
                    .border(if(anchor==index)2.dp else 0.dp,if(anchor==index)BrandInk else Color.Transparent,RoundedCornerShape(10.dp))
                    .semantics{contentDescription="Giorno di prova ${index+1}"}
                    .combinedClickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick={apply(index)},onLongClick={
                        if(editing&&tool!="bell"&&(tool=="eraser"||mode!=CalendarEditMode.ROTATION)){anchor=index;message="Inizio scelto: tocca un altro giorno, prima o dopo, per ${if(tool=="eraser")"cancellare" else if(mode==CalendarEditMode.ADD)"aggiungere un turno" else "applicare il turno"} all’intervallo."}
                    }).padding(horizontal=2.dp,vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.fillMaxWidth().height(22.dp),contentAlignment=Alignment.Center) {
                        Text("${index+1}",color=readableInk(fill,TextPrimary),fontSize=12.sp,fontWeight=FontWeight.Bold)
                        if(anchor==index){SiteIcon("arrow-left",null,Modifier.align(Alignment.CenterStart).size(10.dp),BrandInk);SiteIcon("arrow-right",null,Modifier.align(Alignment.CenterEnd).size(10.dp),BrandInk)}
                        if(index in state.bells)SiteIcon("bell",null,Modifier.align(Alignment.TopEnd).size(9.dp),readableIndicator(fill,ColorReperibilita))
                    }
                    values.take(2).forEach{id->val def=defs[id]?:return@forEach;val tone=shiftColorStyle(def.color,TurniThemeState.dark,CalendarSurface,subdued=preview)
                        val merged=source?.id==def.id&&!preview
                        Column(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(5.dp)).background(if(merged)tone.codeFill.copy(alpha=1f-editPhase) else tone.codeFill),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Text(def.shortCode,fontSize=9.sp,fontWeight=FontWeight.Bold,color=if(merged)readableInk(fill,tone.codeInk)else tone.codeInk,maxLines=1)
                            Text(if(def.isAllDay)"– –" else calendarStartLabel(def.startTime,ShiftRepository.appPreferences.timeFormat24h),fontSize=8.sp,color=if(merged)readableInk(fill,tone.timeInk)else tone.timeInk,maxLines=1)
                        }
                    }
                    if(values.size>2)Text("+${values.size-2}",fontSize=9.sp,color=readableInk(fill,TextSecondary))
                }
            }
        }
        Text(message,color=TextSecondary,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.fillMaxWidth().heightIn(min=40.dp))
        eraseChoice?.let{day->
            Text("Elimina un turno dal giorno ${day+1}",fontSize=13.sp,color=TextPrimary,fontWeight=FontWeight.SemiBold)
            state.days[day].forEachIndexed{index,id->CloudButton("Elimina ${defs[id]?.name?:id}","trash",{
                val updated=state.days[day].filterIndexed{position,_->position!=index}
                change(state.copy(days=state.days.mapIndexed{i,list->if(i==day)updated else list},bells=state.bells-day,colors=state.colors-day));eraseChoice=null
                message="Eliminato solo il turno scelto. Gli altri rimangono."
            },secondary=true)}
        }
        AnimatedVisibility(editing,enter=expandVertically(tween(320))+fadeIn(),exit=shrinkVertically(tween(260))+fadeOut()) {
            NativeEditToolbar(mode,brush,cycle.id,tool,onTool={tool=if(tool==it)null else it;anchor=null;eraseChoice=null},
                onMode={mode=it;tool=null;anchor=null;eraseChoice=null;message=when(it){CalendarEditMode.ADD->"Scegli il turno e tocca un giorno: si aggiungerà a quello esistente. Tieni premuto per più giorni.";CalendarEditMode.ROTATION->"La sequenza di esempio ripete ${cycle.daySequence.joinToString(" → "){id->defs[id]?.shortCode.orEmpty()}}. Scegli il primo e l’ultimo giorno.";else->"Scegli il turno e tocca un giorno per sostituirlo. Tieni premuto sul primo giorno per un intervallo."}},
                onBrush={brush=it;tool=null;anchor=null;message="${defs[it]?.name} selezionato. Ora tocca un giorno."},onRotation={message="Rotazione selezionata: tocca il primo giorno."},
                onUndo={if(history.isNotEmpty()){state=history.last();history=history.dropLast(1);anchor=null;eraseChoice=null;message="Ultima modifica annullata."}},
                onClose={editing=false;anchor=null;eraseChoice=null;message="Modifica chiusa: i riquadri tornano nella vista normale. Tocca la matita per riaprirla."},
                catalogOverride=models,rotationsOverride=listOf(cycle),title="Modifica di prova")
        }
        if(!editing)CloudButton("Riapri modifica di prova","edit",{editing=true})
        TextButton(onClick={state=PracticeState();history=emptyList();anchor=null;eraseChoice=null;message="Prova azzerata. Scegli un turno e ricomincia."}){Text("Azzera la prova",color=BrandInk)}
        Text("Nel calendario vero la gomma apre una scelta dal basso se ci sono più turni. Qui la scelta rimane nella prova. La X chiude la modifica; Annulla recupera l’ultima azione.",color=TextSecondary,fontSize=11.sp,lineHeight=17.sp)
    }
}
