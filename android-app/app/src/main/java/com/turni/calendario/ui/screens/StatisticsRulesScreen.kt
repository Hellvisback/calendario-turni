@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun StatisticsRulesScreen(onDismiss:()->Unit) {
    var editing by remember { mutableStateOf<StatisticsRule?>(null) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val definitions=ShiftRepository.shiftDefinitions.toList()
    val rules=ShiftRepository.statisticsRules.toList()
    fun close(){ if(editing!=null)editing=null else onDismiss() }
    BackHandler(onBack=::close)
    CloudFeatureScaffold(if(editing==null)"Conteggio statistiche" else "Regola di conteggio",::close,feedback,{feedback=null},scrollResetKey=editing?.id) {
        AnimatedContent(editing,transitionSpec={fadeIn()+slideInHorizontally(spring(stiffness=450f)){it/8} with fadeOut()}) { draft ->
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(draft==null) {
                    CloudCard("Il calendario resta come piace a te","Scegli quando contare il turno originale al posto di quello visualizzato. La regola vale per conteggi, ore e grafici, anche sui periodi passati. Non modifica il calendario.") {
                        Text("Esempio: mostri Ferie per tutto un periodo, anche sopra Smonto o Riposo. Se il turno originale è registrato, le statistiche conteranno Smonto o Riposo; gli altri giorni resteranno Ferie.",fontSize=12.sp,lineHeight=19.sp,color=TextSecondary)
                        Text("Senza un turno originale registrato, viene contato il turno visibile. Cancellarlo con la gomma rimuove anche la provenienza.",fontSize=11.sp,lineHeight=17.sp,color=TextMuted)
                    }
                    rules.forEach { rule -> key(rule.id) {
                        val visible=definitions.find { it.id==rule.displayedShiftId }
                        if(visible!=null) CloudCard(visible.name,ShiftRepository.workProfiles.find { it.id==visible.workProfileId }?.name) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Conta il turno originale se è:",fontSize=12.sp,color=TextSecondary)
                                    Text(definitions.filter { it.id in rule.originalShiftIds }.joinToString { it.name },fontSize=14.sp,lineHeight=20.sp,color=TextPrimary,fontWeight=FontWeight.Medium)
                                }
                                Switch(rule.enabled,{ ShiftRepository.saveStatisticsRule(rule.copy(enabled=it));feedback=ActionFeedback(if(it)"Regola attivata" else "Regola disattivata") })
                            }
                            CloudButton("Modifica regola","pencil",{editing=rule},secondary=true)
                        }
                    } }
                    CloudButton("Aggiungi regola","plus",{editing=StatisticsRule(UUID.randomUUID().toString(),"",emptySet())},enabled=definitions.size>1)
                    if(definitions.size<2)Text("Crea almeno due modelli di turno per aggiungere una regola.",fontSize=12.sp,color=TextSecondary)
                } else {
                    var displayed by remember(draft.id) { mutableStateOf(draft.displayedShiftId) }
                    var originals by remember(draft.id) { mutableStateOf(draft.originalShiftIds) }
                    var choosing by remember(draft.id) { mutableStateOf(draft.displayedShiftId.isBlank()) }
                    val selected=definitions.find { it.id==displayed }
                    val used=rules.filterNot { it.id==draft.id }.map { it.displayedShiftId }.toSet()
                    CloudCard("1 · Turno mostrato nel calendario","Scegli il modello che usi per coprire gli altri turni, per esempio Ferie.") {
                        CloudButton(selected?.name?:"Seleziona un turno","chevron-down",{choosing=!choosing},secondary=true)
                        AnimatedVisibility(choosing,enter=expandVertically(spring())+fadeIn(),exit=shrinkVertically()+fadeOut()) {
                            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                definitions.filterNot { it.id in used }.chunked(2).forEach { pair -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    pair.forEach { model -> Box(Modifier.weight(1f)) { StatisticsModelTile(model,model.id==displayed,definitions.map { it.workProfileId }.distinct().size>1) { if(displayed!=model.id){displayed=model.id;originals=emptySet()};choosing=false } } }
                                    if(pair.size==1)Spacer(Modifier.weight(1f))
                                } }
                            }
                        }
                    }
                    AnimatedVisibility(selected!=null,enter=expandVertically(spring())+fadeIn(),exit=shrinkVertically()+fadeOut()) {
                        CloudCard("2 · Turni originali da contare","Quando il turno mostrato sostituisce uno di questi modelli, viene contato il modello originale. Puoi selezionarne più di uno.") {
                            definitions.filter { it.id!=displayed && it.workProfileId==selected?.workProfileId }.chunked(2).forEach { pair -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                pair.forEach { model -> Box(Modifier.weight(1f)) { StatisticsModelTile(model,model.id in originals,false) { originals=if(model.id in originals)originals-model.id else originals+model.id } } }
                                if(pair.size==1)Spacer(Modifier.weight(1f))
                            } }
                        }
                    }
                    if(selected!=null&&originals.isNotEmpty())CloudCard("La tua regola") {
                        Text("${selected.name} sopra ${definitions.filter { it.id in originals }.joinToString { it.name }} → conta il turno originale.",fontSize=13.sp,lineHeight=20.sp,color=TextPrimary)
                    }
                    CloudButton("Salva regola","check",{
                        ShiftRepository.saveStatisticsRule(draft.copy(displayedShiftId=displayed,originalShiftIds=originals));editing=null;feedback=ActionFeedback("Regola salvata · statistiche aggiornate")
                    },enabled=selected!=null&&originals.isNotEmpty())
                    if(rules.any { it.id==draft.id })CloudButton("Elimina regola","trash-2",{ShiftRepository.removeStatisticsRule(draft.id);editing=null;feedback=ActionFeedback("Regola eliminata",true)},destructive=true)
                }
            }
        }
    }
}

@Composable
private fun StatisticsModelTile(model:ShiftDefinition,selected:Boolean,showWork:Boolean,onClick:()->Unit) {
    val fill by animateColorAsState(if(selected)ObsidianPrimary.copy(alpha=.16f)else ObsidianSurfaceHigh)
    Row(Modifier.fillMaxWidth().heightIn(min=60.dp).clip(RoundedCornerShape(12.dp)).background(fill).bouncyClickable(onClick=onClick).padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(model.color),contentAlignment=Alignment.Center) {Text(model.shortCode,fontSize=9.sp,fontWeight=FontWeight.Bold,color=catalogInk(model.color))}
        Column(Modifier.weight(1f)) {Text(model.name,fontSize=12.sp,lineHeight=16.sp,color=TextPrimary,maxLines=2,overflow=TextOverflow.Ellipsis);if(showWork)Text(ShiftRepository.workProfiles.find { it.id==model.workProfileId }?.name.orEmpty(),fontSize=9.sp,color=TextMuted,maxLines=1,overflow=TextOverflow.Ellipsis)}
        if(selected)SiteIcon("check",null,Modifier.size(14.dp),tint=ObsidianPrimary)
    }
}
