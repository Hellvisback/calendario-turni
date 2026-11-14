@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun annualDayInMonth(month:Int,day:Int):Int=day.coerceIn(1,Month.of(month).maxLength())
internal fun manualOnCallDates(dates:Set<LocalDate>,overrides:Map<LocalDate,Boolean?>):Set<LocalDate> =
    (dates+overrides.filterValues{it==true}.keys).filterTo(mutableSetOf()){overrides[it]!=false}

@Composable
fun ReperibilitaSettingsScreen(onDismiss:()->Unit) {
    val config=ShiftRepository.reperibilitaConfig
    fun update(change:(ReperibilitaConfig)->ReperibilitaConfig){
        ShiftRepository.reperibilitaConfig=change(ShiftRepository.reperibilitaConfig)
    }
    val titles=listOf("Scelgo i giorni","Segue i miei turni","Cambia durante l’anno")
    val descriptions=listOf("La aggiungi tu con la campanella.","Si attiva sui modelli che scegli.","Turni in un periodo, settimane nel resto dell’anno.")
    val icons=listOf("bell","clock","calendar")
    CloudFeatureScaffold("Reperibilità",onDismiss,null,{}) {
        CloudCard("La campanella sul calendario","Segna i giorni in cui devi essere disponibile. Questa impostazione non crea una sveglia: gli avvisi si gestiscono in Sveglie dei turni.") {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(currentTurniPalette.onCall.fill)).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                SiteIcon("bell",null,Modifier.size(24.dp),ColorReperibilita)
                Text(titles[config.mode.ordinal],color=readableInk(Color(currentTurniPalette.onCall.fill),TextPrimary),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            }
        }
        CloudCard("Come vuoi aggiungerla?") {
            ReperibilitaMode.values().forEachIndexed { index,mode ->
                val selected=config.mode==mode
                val fill by animateColorAsState(if(selected)ObsidianPrimarySubtle else ObsidianSurfaceContainer,tween(220))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(fill)
                    .border(if(selected)1.5.dp else 0.dp,if(selected)BrandInk else Color.Transparent,RoundedCornerShape(14.dp))
                    .bouncyClickable{update{it.copy(mode=mode)}}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    SiteIcon(icons[index],null,Modifier.size(22.dp),if(selected)BrandInk else TextSecondary)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(titles[index],fontSize=14.sp,color=TextPrimary,fontWeight=FontWeight.SemiBold)
                        Text(descriptions[index],fontSize=12.sp,lineHeight=17.sp,color=TextSecondary)
                    }
                    if(selected)SiteIcon("check","Modalità selezionata",Modifier.size(18.dp),BrandInk)
                }
            }
        }
        AnimatedContent(config.mode,transitionSpec={
            (fadeIn(tween(220,delayMillis=60)) togetherWith fadeOut(tween(120))) using SizeTransform{_,_->tween(320)}
        }) { mode ->
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when(mode) {
                    ReperibilitaMode.MANUALE -> CloudCard("Scegli dal calendario","Apri un giorno e tocca la campanella. Puoi farlo anche dalla barra di modifica: lo stesso strumento funziona con Cambia, Più turni e Rotazioni.") {
                        val count=manualOnCallDates(config.manualDates,ShiftRepository.dayExtraInfo.mapValues{it.value.isOnCallManual}).size
                        Text("$count ${if(count==1)"giorno attivato" else "giorni attivati"} manualmente",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=BrandInk)
                        Text("Non vengono aggiunti altri giorni automaticamente.",fontSize=12.sp,color=TextSecondary)
                    }
                    ReperibilitaMode.TURNO_SPECIFICO -> CloudCard("Quali turni la attivano?","La campanella si accende nei giorni che contengono almeno uno dei modelli selezionati. Se cambi il turno, la regola si aggiorna.") {
                        OnCallModelChoices(config.shiftSpecificIds){ids->update{it.copy(shiftSpecificIds=ids)}}
                    }
                    ReperibilitaMode.ALTERNATA_STAGIONALE -> {
                        CloudCard("1 · Un periodo segue i turni","Il periodo si ripete ogni anno. Le date iniziale e finale sono comprese; puoi anche attraversare Capodanno, per esempio da ottobre a maggio.") {
                            AnnualOnCallDate("Dal",config.seasonal.winterStartMonth,config.seasonal.winterStartDay){month,day->update{it.copy(seasonal=it.seasonal.copy(winterStartMonth=month,winterStartDay=day))}}
                            AnnualOnCallDate("Al",config.seasonal.winterEndMonth,config.seasonal.winterEndDay){month,day->update{it.copy(seasonal=it.seasonal.copy(winterEndMonth=month,winterEndDay=day))}}
                            Text("In questo periodo la campanella segue questi modelli:",fontSize=12.sp,color=TextSecondary,lineHeight=18.sp)
                            OnCallModelChoices(config.seasonal.winterShiftIds){ids->update{it.copy(seasonal=it.seasonal.copy(winterShiftIds=ids))}}
                        }
                        CloudCard("2 · Nel resto dell’anno alterna settimane","Fuori dalle date qui sopra, conta i giorni a blocchi di 7 dalla data scelta, anche se non è lunedì. Le settimane reperibili valgono per tutti i giorni, anche senza turno.") {
                            OnCallReferenceDate(config.seasonal.summerCycleReferenceDate){date->update{it.copy(seasonal=it.seasonal.copy(summerCycleReferenceDate=date))}}
                            OnCallWeekCount("Settimane con reperibilità",config.seasonal.summerCycleOnCallWeeks){value->update{it.copy(seasonal=it.seasonal.copy(summerCycleOnCallWeeks=value))}}
                            OnCallWeekCount("Poi settimane senza",config.seasonal.summerCycleWorkWeeks){value->update{it.copy(seasonal=it.seasonal.copy(summerCycleWorkWeeks=value))}}
                            Text("Il ciclo si ripete: ${config.seasonal.summerCycleOnCallWeeks} con reperibilità, poi ${config.seasonal.summerCycleWorkWeeks} senza. La data scelta avvia il primo blocco reperibile.",fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)
                        }
                    }
                }
            }
        }
        CloudCard("Puoi sempre cambiare un singolo giorno","La scelta manuale sul giorno ha la precedenza sulla regola: campanella accesa significa reperibile, spenta significa non reperibile. Le impostazioni si salvano automaticamente.") {
            Text("Prossimi 7 giorni",color=TextPrimary,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                repeat(7){offset->val date=LocalDate.now().plusDays(offset.toLong());val on=ShiftRepository.isOnCall(date)
                    val fill=if(on)Color(currentTurniPalette.onCall.fill)else ObsidianSurfaceContainer
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(fill).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW,Locale.ITALIAN),fontSize=10.sp,color=TextSecondary)
                        Text("${date.dayOfMonth}",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=readableInk(fill,TextPrimary))
                        SiteIcon(if(on)"bell" else "minus",null,Modifier.size(13.dp),if(on)ColorReperibilita else TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnCallModelChoices(selected:List<String>,onChange:(List<String>)->Unit) {
    val all=ShiftRepository.shiftDefinitions.toList()
    if(all.isEmpty())Text("Non hai ancora modelli. Creali in Turni e lavori, poi torna qui per sceglierli.",fontSize=12.sp,color=TextSecondary,lineHeight=18.sp)
    all.groupBy{it.workProfileId}.forEach{(work,models)->
        if(ShiftRepository.workProfiles.size>1)Text(ShiftRepository.workProfiles.firstOrNull{it.id==work}?.name?:"Altro lavoro",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=TextSecondary)
        models.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            row.forEach{model->val chosen=model.id in selected;val tone=shiftColorStyle(model.color,TurniThemeState.dark,ObsidianSurface)
                val fill by animateColorAsState(if(chosen)tone.codeFill else ObsidianSurfaceContainer,tween(220))
                Row(Modifier.weight(1f).heightIn(min=50.dp).clip(RoundedCornerShape(12.dp)).background(fill)
                    .bouncyClickable{onChange(if(chosen)selected-model.id else selected+model.id)}.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(model.shortCode,color=if(chosen)tone.codeInk else TextPrimary,fontWeight=FontWeight.Bold,fontSize=13.sp)
                        Text(model.name,color=if(chosen)tone.codeInk else TextSecondary,fontSize=11.sp,lineHeight=15.sp)
                    }
                    if(chosen)SiteIcon("check","Selezionato",Modifier.size(16.dp),tone.codeInk)
                }
            }
            if(row.size==1)Spacer(Modifier.weight(1f))
        }}
    }
}

@Composable
private fun AnnualOnCallDate(label:String,month:Int,day:Int,onDate:(Int,Int)->Unit) {
    var open by remember{mutableStateOf(false)}
    var shown by remember(month){mutableStateOf(month)}
    val value=LocalDate.of(2028,month,annualDayInMonth(month,day)).format(DateTimeFormatter.ofPattern("d MMMM",Locale.ITALIAN))
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ObsidianSurfaceContainer).bouncyClickable{open=!open;shown=month}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){Text("$label · ogni anno",fontSize=11.sp,color=TextSecondary);Text(value,fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=TextPrimary)}
            SiteIcon("calendar","Scegli data",Modifier.size(21.dp),BrandInk)
        }
        AnimatedVisibility(open,enter=expandVertically(tween(300))+fadeIn(),exit=shrinkVertically(tween(220))+fadeOut()) {
            Column(Modifier.padding(top=8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    PanelIcon("chevron-left","Mese precedente",{shown=if(shown==1)12 else shown-1},width=36.dp)
                    Text(Month.of(shown).getDisplayName(java.time.format.TextStyle.FULL,Locale.ITALIAN),Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=TextPrimary,fontSize=14.sp)
                    PanelIcon("chevron-right","Mese successivo",{shown=if(shown==12)1 else shown+1},width=36.dp)
                }
                (1..Month.of(shown).maxLength()).toList().chunked(7).forEach{week->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)){
                    week.forEach{number->Box(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(8.dp)).background(if(shown==month&&number==day)ObsidianPrimary else ObsidianSurfaceContainer).bouncyClickable{onDate(shown,number);open=false},contentAlignment=Alignment.Center){Text("$number",color=if(shown==month&&number==day)OnBrand else TextPrimary,fontSize=12.sp)}}
                    repeat(7-week.size){Spacer(Modifier.weight(1f))}
                };Spacer(Modifier.height(3.dp))}
            }
        }
    }
}

@Composable
private fun OnCallReferenceDate(value:LocalDate,onDate:(LocalDate)->Unit) {
    var open by remember{mutableStateOf(false)};var month by remember(value){mutableStateOf(YearMonth.from(value))}
    Column {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ObsidianSurfaceContainer).bouncyClickable{open=!open}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){Text("Inizio della prima settimana reperibile",fontSize=11.sp,color=TextSecondary);Text(value.format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.ITALIAN)),fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=TextPrimary)}
            SiteIcon("calendar","Scegli inizio ciclo",Modifier.size(21.dp),BrandInk)
        }
        AnimatedVisibility(open,enter=expandVertically(tween(300))+fadeIn(),exit=shrinkVertically(tween(220))+fadeOut()) {
            AppDateGrid(month,value,{month=it},compact=true,select={onDate(it);open=false})
        }
    }
}

@Composable
private fun OnCallWeekCount(label:String,value:Int,onChange:(Int)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(label,Modifier.weight(1f),fontSize=13.sp,lineHeight=18.sp,color=TextPrimary)
        PanelIcon("minus","Riduci $label",{if(value>1)onChange(value-1)},width=34.dp,height=34.dp,background=ObsidianSurfaceContainer)
        Text("$value",Modifier.widthIn(min=24.dp),fontSize=16.sp,color=BrandInk,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        PanelIcon("plus","Aumenta $label",{if(value<Int.MAX_VALUE)onChange(value+1)},width=34.dp,height=34.dp,background=ObsidianSurfaceContainer)
    }
}

@Composable
fun SimpleFlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val hSpacingPx = horizontalSpacing.roundToPx()
        val vSpacingPx = verticalSpacing.roundToPx()

        val rows = mutableListOf<List<Placeable>>()
        val rowHeights = mutableListOf<Int>()
        var currentRow = mutableListOf<Placeable>()
        var currentLineWidth = 0
        var currentLineHeight = 0

        for (measurable in measurables) {
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            if (currentLineWidth + placeable.width + (if (currentRow.isNotEmpty()) hSpacingPx else 0) > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                rowHeights.add(currentLineHeight)
                currentRow = mutableListOf(placeable)
                currentLineWidth = placeable.width
                currentLineHeight = placeable.height
            } else {
                if (currentRow.isNotEmpty()) currentLineWidth += hSpacingPx
                currentRow.add(placeable)
                currentLineWidth += placeable.width
                currentLineHeight = maxOf(currentLineHeight, placeable.height)
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowHeights.add(currentLineHeight)
        }

        val totalHeight = rowHeights.sum() + (if (rows.size > 1) (rows.size - 1) * vSpacingPx else 0)

        layout(constraints.maxWidth, totalHeight.coerceAtLeast(0)) {
            var currentY = 0
            for (i in rows.indices) {
                val row = rows[i]
                var currentX = 0
                for (placeable in row) {
                    placeable.placeRelative(currentX, currentY)
                    currentX += placeable.width + hSpacingPx
                }
                currentY += rowHeights[i] + vSpacingPx
            }
        }
    }
}

