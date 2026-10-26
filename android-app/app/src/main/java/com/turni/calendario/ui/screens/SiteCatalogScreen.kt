@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Native rendering of the approved collection cards, with the live catalog under editors. */
@Composable
fun TurniModelsScreen(onEditorActiveChanged:((Boolean)->Unit)?=null,initialTab:Int=0,
    initialEditingRotation:ShiftRotation?=null,onClearInitialRotation:(()->Unit)?=null,onDismiss:()->Unit={}) {
    var tab by remember{mutableStateOf(initialTab.coerceIn(0,2))}
    var shift by remember{mutableStateOf<ShiftDefinition?>(null)}
    var cycle by remember{mutableStateOf<ShiftRotation?>(initialEditingRotation)}
    var work by remember{mutableStateOf<Pair<WorkProfile,Boolean>?>(null)}
    var feedback by remember{mutableStateOf<ActionFeedback?>(null)}
    val editing=shift!=null||cycle!=null||work!=null
    val colors=MaterialTheme.colorScheme
    LaunchedEffect(editing){onEditorActiveChanged?.invoke(editing)}
    DisposableEffect(Unit){onDispose{onEditorActiveChanged?.invoke(false)}}
    LaunchedEffect(initialEditingRotation){initialEditingRotation?.let{tab=2;cycle=it;onClearInitialRotation?.invoke()}}
    LaunchedEffect(feedback){if(feedback!=null){delay(2600);feedback=null}}
    BackHandler(!editing){onDismiss()}
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal=14.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                CatalogIconAction("arrow-left","Torna al calendario",onDismiss)
                Column {Text("Modelli e turni",color=colors.onBackground,fontSize=25.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.7).sp)
                    Text("Il tuo catalogo, in un unico posto",fontSize=11.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))}
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant).padding(3.dp)) {
                val unit=maxWidth/3
                val position by animateDpAsState(unit*tab,spring(.9f,380f))
                Box(Modifier.offset(x=position).width(unit).fillMaxHeight().clip(RoundedCornerShape(13.dp)).background(colors.primary))
                Row(Modifier.fillMaxSize()){listOf("Turni","Lavori","Rotazioni").forEachIndexed{index,label->
                    Box(Modifier.weight(1f).fillMaxHeight().clickable{tab=index},contentAlignment=Alignment.Center){Text(label,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=if(tab==index)colors.onPrimary else colors.onSurfaceVariant)}
                }}
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(tab,modifier=Modifier.weight(1f),transitionSpec={
                (slideInHorizontally(spring(.95f,420f)){if(targetState>initialState)it/5 else -it/5}+fadeIn()) with
                    (slideOutHorizontally(tween(180)){if(targetState>initialState)-it/5 else it/5}+fadeOut())
            }){current->
                LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=12.dp)) {
                    when(current) {
                        0->items(ShiftRepository.shiftDefinitions,key={it.id}){definition->
                            SiteCollectionCard {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    SiteCodeBadge(definition.shortCode,definition.color)
                                    Column(Modifier.weight(1f)){Text(definition.name,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                                        Text(definition.timeRangeFormatted+(if(definition.isAllDay)"" else " · ${definition.durationFormatted}"),fontSize=11.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))}
                                    CatalogIconAction("pencil","Modifica ${definition.name}",{shift=definition},Modifier.size(36.dp))
                                }
                            }
                        }
                        1->items(ShiftRepository.workProfiles,key={it.id}){profile->
                            SiteCollectionCard {
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(profile.colorHex)),contentAlignment=Alignment.Center){CatalogWorkIcon(profile.iconKey,Modifier.size(21.dp),catalogInk(Color(profile.colorHex)))}
                                    Column(Modifier.weight(1f)) {
                                        Text(profile.name,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                                        Text("${profile.companyOrDepartment.ifBlank{"Profilo di lavoro"}} · ${profile.weeklyHours} h/settimana",fontSize=11.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))
                                        Text((if(profile.id==ShiftRepository.activeWorkProfileId)"Calendario attivo" else "Calendario separato")+(if(profile.enableAdvancedCalc)" · Parametri CCNL attivi" else ""),fontSize=11.sp,color=colors.onSurfaceVariant)
                                    }
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(7.dp),modifier=Modifier.padding(top=10.dp)) {
                                    CatalogButton("Modifica",{work=profile to false},Modifier.weight(1f),icon="pencil")
                                    CatalogButton("Apri calendario",{ShiftRepository.setActiveWorkProfile(profile.id);onDismiss()},Modifier.weight(1.3f),primary=true,icon="calendar")
                                }
                            }
                        }
                        2->items(ShiftRepository.getRotationsSortedByDefault(),key={it.id}){rotation->
                            SiteCollectionCard {
                                val preferred=rotation.id==ShiftRepository.defaultRotationId
                                val phase=remember{Animatable(0f)};val scope=rememberCoroutineScope()
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(colors.primaryContainer),contentAlignment=Alignment.Center){SiteIcon("rotation",null,Modifier.size(21.dp),colors.primary)}
                                    Column(Modifier.weight(1f)){Text(rotation.name,fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text("${rotation.daySequence.size} giorni · ${(rotation.daySequence.size+6)/7} settimane"+(if(preferred)" · Predefinita" else ""),fontSize=10.sp,color=colors.onSurfaceVariant,modifier=Modifier.padding(top=3.dp))}
                                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(colors.surfaceVariant).clickable{
                                        ShiftRepository.toggleDefaultRotation(rotation.id)
                                        scope.launch{phase.snapTo(0f);phase.animateTo(1f,tween(720));phase.snapTo(0f)}
                                    },contentAlignment=Alignment.Center){CatalogDefaultStar(preferred,if(preferred)"Rimuovi predefinita" else "Imposta predefinita",Modifier.size(20.dp),phase.value)}
                                    CatalogIconAction("pencil","Modifica ${rotation.name}",{cycle=rotation},Modifier.size(34.dp))
                                }
                                SiteCyclePreview(rotation)
                            }
                        }
                    }
                }
            }
            CatalogButton(when(tab){0->"Nuovo modello";1->"Nuovo lavoro";else->"Nuova rotazione"},{
                when(tab) {
                    0->if(ShiftRepository.shiftDefinitions.size<150)shift=ShiftDefinition(UUID.randomUUID().toString(),"","TUR",colorHex=catalogPastelColors[4],workProfileId=ShiftRepository.activeWorkProfileId)
                    1->if(ShiftRepository.workProfiles.size<20)work=WorkProfile(UUID.randomUUID().toString(),"",colorHex=catalogPastelColors[2],isDefault=false) to true
                    2->if(ShiftRepository.rotations.size<50)cycle=ShiftRotation(UUID.randomUUID().toString(),"",7,List(7){""})
                }
            },Modifier.fillMaxWidth().padding(vertical=10.dp),primary=true,icon="plus")
        }
        shift?.let{draft->ShiftEditorScreen(draft,{shift=null},{ShiftRepository.saveShiftDefinition(it);shift=null;feedback=ActionFeedback("Turno salvato",false)},{ShiftRepository.deleteShiftDefinition(it);shift=null;feedback=ActionFeedback("Turno eliminato",true)})}
        cycle?.let{draft->RotationMatrixEditorScreen(draft,{cycle=null},{ShiftRepository.saveRotation(it);cycle=null;feedback=ActionFeedback("Rotazione salvata",false)},{},{ShiftRepository.deleteRotation(it);cycle=null;feedback=ActionFeedback("Rotazione eliminata",true)})}
        work?.let{(profile,isNew)->WorkProfileEditorScreen(profile,isNew,{work=null},{if(isNew)ShiftRepository.addWorkProfile(it)else ShiftRepository.updateWorkProfile(it);work=null;feedback=ActionFeedback("Lavoro salvato",false)},{if(ShiftRepository.deleteWorkProfile(it)){work=null;feedback=ActionFeedback("Lavoro eliminato",true)}})}
        MagicalActionFeedbackHUD(feedback,{feedback=null},modifier=Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }
}

@Composable
private fun SiteCollectionCard(content:@Composable ColumnScope.()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(color=colors.surface,shape=RoundedCornerShape(17.dp),border=androidx.compose.foundation.BorderStroke(1.dp,colors.outline.copy(alpha=.18f)),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),content=content)
    }
}

@Composable
private fun SiteCodeBadge(code:String,color:Color) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(color),contentAlignment=Alignment.Center){Text(code.take(4),fontSize=11.sp,fontWeight=FontWeight.Bold,color=catalogInk(color),maxLines=1)}
}

@Composable
private fun SiteCyclePreview(rotation:ShiftRotation) {
    val colors=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){Spacer(Modifier.width(17.dp));listOf("L","M","M","G","V","S","D").forEach{Text(it,Modifier.weight(1f),fontSize=9.sp,color=colors.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)}}
        rotation.daySequence.chunked(7).forEachIndexed{week,row->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)){
            Text("S${week+1}",Modifier.width(17.dp),fontSize=8.sp,color=colors.onSurfaceVariant)
            for(day in 0..6){val definition=row.getOrNull(day)?.let{ShiftRepository.getShiftDefinition(it)};val fill=definition?.color?:colors.surfaceVariant
                Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(if(day<row.size)fill else Color.Transparent),contentAlignment=Alignment.Center){if(day<row.size)Text(definition?.shortCode?.take(4)?:"—",fontSize=9.sp,fontWeight=FontWeight.SemiBold,color=if(definition!=null)catalogInk(fill)else colors.onSurfaceVariant)}
            }
        }}
    }
}
