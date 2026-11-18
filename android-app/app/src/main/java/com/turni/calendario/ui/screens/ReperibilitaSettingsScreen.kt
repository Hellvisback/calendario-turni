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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.*
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
    CloudFeatureScaffold("Reperibilità", onDismiss, null, {}) {
        OnCallPreview(config.mode == ReperibilitaMode.MANUALE)
        CloudCard("Come si attiva", "La modalità scelta vale per tutti i lavori.") {
            OnCallModeSelector(config.mode) { mode -> update { it.copy(mode = mode) } }
        }
        AnimatedContent(config.mode,transitionSpec={
            (fadeIn(tween(220,delayMillis=60)) togetherWith fadeOut(tween(120))) using SizeTransform{_,_->tween(320)}
        }) { mode ->
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when(mode) {
                    ReperibilitaMode.MANUALE -> CloudCard("Altre date","Per modificare una data fuori dall’anteprima, tocca la campanella nel dettaglio del giorno sul calendario.") {
                        val count=manualOnCallDates(config.manualDates,ShiftRepository.dayExtraInfo.mapValues{it.value.isOnCallManual}).size
                        Text("$count ${if(count==1)"giorno attivato" else "giorni attivati"} manualmente",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=BrandInk)
                    }
                    ReperibilitaMode.TURNO_SPECIFICO -> CloudCard("Modelli di turno","La campanella si accende nei giorni che contengono almeno uno dei modelli selezionati.") {
                        OnCallModelChoices(config.shiftSpecificIds){ids->update{it.copy(shiftSpecificIds=ids)}}
                    }
                    ReperibilitaMode.ALTERNATA_STAGIONALE -> {
                        CloudCard("Periodo con turni selezionati","Il periodo si ripete ogni anno, comprende entrambe le date e può proseguire oltre Capodanno.") {
                            AnnualOnCallDate("Dal",config.seasonal.winterStartMonth,config.seasonal.winterStartDay){month,day->update{it.copy(seasonal=it.seasonal.copy(winterStartMonth=month,winterStartDay=day))}}
                            AnnualOnCallDate("Al",config.seasonal.winterEndMonth,config.seasonal.winterEndDay){month,day->update{it.copy(seasonal=it.seasonal.copy(winterEndMonth=month,winterEndDay=day))}}
                            Text("Modelli attivi nel periodo",fontSize=12.sp,color=TextSecondary,lineHeight=18.sp)
                            OnCallModelChoices(config.seasonal.winterShiftIds){ids->update{it.copy(seasonal=it.seasonal.copy(winterShiftIds=ids))}}
                        }
                        CloudCard("Ciclo nel resto dell’anno","Fuori dalle date qui sopra, conta i giorni a blocchi di 7 dalla data scelta, anche se non è lunedì. Le settimane reperibili valgono per tutti i giorni, anche senza turno.") {
                            OnCallReferenceDate(config.seasonal.summerCycleReferenceDate){date->update{it.copy(seasonal=it.seasonal.copy(summerCycleReferenceDate=date))}}
                            OnCallWeekCount("Settimane con reperibilità",config.seasonal.summerCycleOnCallWeeks){value->update{it.copy(seasonal=it.seasonal.copy(summerCycleOnCallWeeks=value))}}
                            OnCallWeekCount("Settimane senza reperibilità",config.seasonal.summerCycleWorkWeeks){value->update{it.copy(seasonal=it.seasonal.copy(summerCycleWorkWeeks=value))}}
                            Text("Il ciclo si ripete: ${config.seasonal.summerCycleOnCallWeeks} con reperibilità, poi ${config.seasonal.summerCycleWorkWeeks} senza. La data scelta avvia il primo blocco reperibile.",fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)
                        }
                    }
                }
            }
        }
        CloudCard("Modifiche sul calendario") {
            Text("La campanella impostata su un singolo giorno ha la precedenza in tutte le modalità.", color = TextPrimary, fontSize = 13.sp, lineHeight = 19.sp)
            Text("Le modifiche si salvano automaticamente. Per ricevere un avviso, usa Sveglie automatiche.", color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun OnCallModeSelector(active: ReperibilitaMode, onChange: (ReperibilitaMode) -> Unit) {
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val group = Modifier.fillMaxWidth().selectableGroup()
            .background(ObsidianSurfaceContainer, RoundedCornerShape(15.dp)).padding(3.dp)
        if (maxWidth < 270.dp || fontScale > 1.2f) {
            Column(group, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ReperibilitaMode.values().forEach { mode ->
                    OnCallModeButton(mode, active == mode, Modifier.fillMaxWidth()) { onChange(mode) }
                }
            }
        } else {
            Row(group, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ReperibilitaMode.values().forEach { mode ->
                    OnCallModeButton(mode, active == mode, Modifier.weight(1f)) { onChange(mode) }
                }
            }
        }
    }
}

@Composable
private fun OnCallModeButton(mode: ReperibilitaMode, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = when (mode) {
        ReperibilitaMode.MANUALE -> "Manuale"
        ReperibilitaMode.TURNO_SPECIFICO -> "Per turno"
        ReperibilitaMode.ALTERNATA_STAGIONALE -> "Stagionale"
    }
    val fill by animateColorAsState(if (active) ObsidianPrimary else ObsidianSurfaceContainer, tween(220))
    val ink by animateColorAsState(if (active) OnBrand else TextPrimary, tween(220))
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(fill)
        .semantics(mergeDescendants = true) { selected = active; role = Role.RadioButton }
        .bouncyClickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@Composable
private fun OnCallPreview(manual: Boolean) {
    val today = LocalDate.now()
    val days = remember(today) { (0L..6L).map { today.plusDays(it) } }
    val modelsByDate = days.associateWith { date ->
        ShiftRepository.getShiftsForDate(date).mapNotNull { ShiftRepository.getShiftDefinition(it.shiftDefId) }
    }
    val visibleModels = modelsByDate.values.flatten().distinctBy { it.id }
    val count = days.count { ShiftRepository.isOnCall(it) }
    val tone = currentTurniPalette.onCall
    val amberInk = Color(tone.ink)
    val amberFill = Color(tone.fill)
    val fullDate = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN) }
    val detail = when (count) {
        0 -> "Nessun giorno di reperibilità"
        1 -> "1 giorno di reperibilità"
        else -> "$count giorni di reperibilità"
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = ObsidianSurface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(40.dp).background(amberFill, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    SiteIcon("bell", null, Modifier.size(21.dp), amberInk)
                }
                Column(Modifier.weight(1f)) {
                    Text("Prossimi 7 giorni", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(detail, color = TextSecondary, fontSize = 13.sp)
                }
            }
            if (manual) Text("Tocca un giorno per attivare o disattivare la reperibilità.", color = TextSecondary, fontSize = 13.sp)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val minWidth = 38.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
                val width = maxOf(minWidth, (maxWidth - 24.dp) / 7)
                Row(Modifier.horizontalScroll(rememberScrollState()).height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.forEach { date ->
                        val models = modelsByDate.getValue(date)
                        val active = ShiftRepository.isOnCall(date)
                        val fill by animateColorAsState(if (active) amberFill else ObsidianSurfaceContainer, tween(220))
                        val state = if (active) "Reperibile" else "Senza reperibilità"
                        val labels = models.joinToString(", ") { model ->
                            val job = ShiftRepository.workProfiles.firstOrNull { it.id == model.workProfileId }
                            if (ShiftRepository.workProfiles.size > 1 && job != null) "${model.name}, ${job.name}" else model.name
                        }.ifEmpty { "Nessun turno" }
                        val action = if (manual) Modifier.toggleable(value = active, role = Role.Checkbox) {
                            ShiftRepository.setDayOnCallManual(date, it)
                        } else Modifier
                        Column(Modifier.width(width).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(fill).then(action)
                            .clearAndSetSemantics {
                                contentDescription = "${date.format(fullDate)}, $labels"
                                stateDescription = state
                            }.padding(horizontal = 2.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ITALIAN), color = if (active) amberInk else TextSecondary, fontSize = 11.sp)
                            Text(date.dayOfMonth.toString(), color = if (active) amberInk else TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            SiteIcon(if (active) "bell" else "minus", null, Modifier.size(13.dp), if (active) amberInk else TextMuted)
                            if (models.isEmpty()) Text("–", color = if (active) amberInk else TextSecondary,
                                fontSize = 10.sp, lineHeight = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            models.forEach { model ->
                                val tone = shiftColorStyle(model.color, TurniThemeState.dark, fill)
                                Text(model.shortCode.ifBlank { model.name }, Modifier.background(tone.codeFill, RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = tone.codeInk, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            if (visibleModels.isNotEmpty()) {
                val legend = visibleModels.joinToString("   ·   ") { model ->
                    val job = ShiftRepository.workProfiles.firstOrNull { it.id == model.workProfileId }
                    val label = if (model.shortCode.equals(model.name, ignoreCase = true)) model.name else "${model.shortCode}  ${model.name}"
                    if (ShiftRepository.workProfiles.size > 1 && job != null) "$label (${job.name})" else label
                }
                Text(legend, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val minWidth = 88.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns = ((maxWidth + 6.dp) / (minWidth + 6.dp)).toInt().coerceIn(1, 3)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                models.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { model ->
                            val chosen = model.id in selected
                            val tone = shiftColorStyle(model.color, TurniThemeState.dark, ObsidianSurface)
                            val fill by animateColorAsState(if (chosen) ObsidianPrimarySubtle else ObsidianSurfaceContainer, tween(220))
                            val shape = RoundedCornerShape(12.dp)
                            Column(Modifier.weight(1f).heightIn(min = 48.dp).clip(shape).background(fill)
                                .border(1.dp, if (chosen) BrandInk else Color.Transparent, shape)
                                .toggleable(value = chosen, role = Role.Checkbox) { enabled ->
                                    onChange(if (enabled) selected + model.id else selected - model.id)
                                }.padding(horizontal = 7.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(8.dp).background(tone.accent, RoundedCornerShape(50)))
                                    Text(model.shortCode, Modifier.weight(1f), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (chosen) SiteIcon("check", null, Modifier.size(13.dp), BrandInk)
                                }
                                Text(model.name, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
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
                    week.forEach{number->Box(Modifier.weight(1f).heightIn(min=44.dp).clip(RoundedCornerShape(8.dp)).background(if(shown==month&&number==day)ObsidianPrimary else ObsidianSurfaceContainer).bouncyClickable{onDate(shown,number);open=false},contentAlignment=Alignment.Center){Text("$number",color=if(shown==month&&number==day)OnBrand else TextPrimary,fontSize=12.sp)}}
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
        PanelIcon("minus","Riduci $label",{if(value>1)onChange(value-1)},width=48.dp,height=48.dp,background=ObsidianSurfaceContainer)
        Text("$value",Modifier.widthIn(min=24.dp),fontSize=16.sp,color=BrandInk,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        PanelIcon("plus","Aumenta $label",{if(value<Int.MAX_VALUE)onChange(value+1)},width=48.dp,height=48.dp,background=ObsidianSurfaceContainer)
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

