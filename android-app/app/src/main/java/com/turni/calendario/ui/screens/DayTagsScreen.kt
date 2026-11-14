@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val tagSwatches = listOf(0xFF94C4EFL, 0xFF83CDB7L, 0xFFF2C46DL, 0xFFEE9C9CL, 0xFFBAAEF6L, 0xFFB4D995L)
private val tagIconLabels = listOf("Etichetta", "Formazione", "Salute", "Studio", "Sorriso", "Obiettivo")

@Composable
internal fun DayTagChip(tag: DayTag, selected: Boolean = false, compact: Boolean = false, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = shiftColorStyle(Color(tag.colorHex), TurniThemeState.dark, ObsidianSurface)
    val fill by animateColorAsState(if (selected) colors.codeFill else ObsidianSurfaceContainer, tween(220))
    val ink = if (selected) colors.codeInk else TextPrimary
    val phase by animateFloatAsState(if (selected) 1f else 0f, spring(.85f, 420f))
    Row(modifier.heightIn(min = if(compact)28.dp else 36.dp).clip(RoundedCornerShape(11.dp)).background(fill)
        .border(if (selected) 1.5.dp else 0.dp, if (selected) colors.codeInk.copy(alpha=.5f) else Color.Transparent, RoundedCornerShape(11.dp))
        .then(if (onClick != null) Modifier.semantics { this.selected = selected; role = Role.Checkbox }.bouncyClickable(onClick=onClick) else Modifier)
        .padding(horizontal=if(compact)7.dp else 10.dp, vertical=if(compact)4.dp else 7.dp), verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        SiteIcon(tag.iconKey, null, Modifier.size(16.dp), if(selected)ink else readablePigment(fill,shiftDisplayColor(Color(tag.colorHex))), phase)
        Text(tag.name, color=ink, fontSize=if(compact)11.sp else 12.sp, maxLines=1, overflow=TextOverflow.Ellipsis, modifier=Modifier.widthIn(max=if(compact)160.dp else 220.dp))
        if (selected && onClick != null) SiteIcon("check", null, Modifier.size(13.dp), ink)
    }
}

@Composable
internal fun DayTagsSettingsScreen(onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<DayTag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val tags = ShiftRepository.dayTags.toList()
    CloudFeatureScaffold("Etichette dei giorni", onDismiss, feedback, { feedback=null }) {
        CloudCard("Ritrova ciò che hai fatto", "Aggiungi etichette come Formazione o Visita medica. Ogni etichetta conta i giorni nelle statistiche; puoi affiancarle una nota con i dettagli.") {
            CloudButton("Nuova etichetta", "plus", { creating=true })
        }
        if (tags.none { !it.archived }) CloudCard("Le tue etichette") {
            Text("Crea la prima etichetta, poi apri un giorno del calendario e tocca Note ed etichette per assegnarla.", color=TextSecondary, fontSize=13.sp, lineHeight=19.sp)
        }
        tags.filter { !it.archived }.forEach { tag ->
            TagManagementRow(tag) { editing=tag }
        }
        if (tags.any { it.archived }) {
            TextButton(onClick={showArchived=!showArchived}, colors=ButtonDefaults.textButtonColors(contentColor=BrandInk)) {
                Text("Archiviate (${tags.count { it.archived }})")
            }
            AnimatedVisibility(showArchived, enter=expandVertically(spring())+fadeIn(), exit=shrinkVertically()+fadeOut()) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Restano visibili sui giorni già segnati e nelle statistiche.", color=TextSecondary, fontSize=12.sp)
                    tags.filter { it.archived }.forEach { tag -> TagManagementRow(tag) { editing=tag } }
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
    if (creating || editing != null) DayTagEditor(editing, { creating=false; editing=null }) {
        creating=false
        editing=null
        val removed=ShiftRepository.dayTags.none { tag -> tag.id==it.id }
        val message=when {
            removed -> "Etichetta eliminata"
            it.archived -> "Etichetta archiviata"
            else -> "Etichetta salvata"
        }
        feedback=ActionFeedback(message,isDestructive=removed)
    }
}

@Composable
private fun TagManagementRow(tag: DayTag, onEdit: () -> Unit) {
    val count = ShiftRepository.dayExtraInfo.values.count { tag.id in it.tagIds }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(ObsidianSurface).bouncyClickable(onClick=onEdit).padding(12.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { DayTagChip(tag) }
        Text("$count ${if(count==1) "giorno" else "giorni"}", color=TextSecondary, fontSize=11.sp)
        SiteIcon("pencil", "Modifica ${tag.name}", Modifier.size(18.dp), BrandInk)
    }
}

@Composable
internal fun DayTagEditor(original: DayTag?, onClose: () -> Unit,
    inlineContent:Boolean=false,inlineScope:ColumnScope?=null,onExit:(()->Unit)?=null,onSaved:(DayTag)->Unit) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var name by remember(original?.id) { mutableStateOf(original?.name ?: "") }
    var icon by remember(original?.id) { mutableStateOf(original?.iconKey ?: "tag") }
    var color by remember(original?.id) { mutableStateOf(original?.colorHex ?: tagSwatches.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedTag by remember { mutableStateOf<DayTag?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val editor:@Composable ColumnScope.(() -> Unit)->Unit = { close ->
        fun save(archived: Boolean) {
            if(savedTag!=null)return
            val tag = DayTag(original?.id ?: UUID.randomUUID().toString(), name.trim(), icon, color, archived)
            try { ShiftRepository.saveDayTag(tag);focus.clearFocus();keyboard?.hide();savedTag=tag;close() }
            catch (problem: IllegalArgumentException) { error=problem.message }
        }
        if(deleting&&original!=null)AppConfirmationDialog("Eliminare questa etichetta?", "Non è associata a nessuna giornata. Potrai crearne una nuova quando vuoi.", "Elimina etichetta", "Annulla",
            onDismiss={deleting=false},onConfirm={
                deleting=false
                try { ShiftRepository.deleteUnusedDayTag(original.id);savedTag=original;close() }
                catch(problem:IllegalArgumentException) { error=problem.message }
            })
        TagSheetHeading(if(original==null)"Nuova etichetta" else "Modifica etichetta",
            onClose=onExit?:close,onBack=close,showClose=false)
        Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill=false), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Nome", color=TextSecondary, fontSize=12.sp)
            BasicTextField(name, { name=it.take(40);error=null }, singleLine=true,
                textStyle=TextStyle(color=TextPrimary,fontSize=14.sp), cursorBrush=SolidColor(ObsidianPrimary),
                modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(ObsidianSurfaceContainer).padding(14.dp),
                decorationBox={ field -> if(name.isBlank()) Text("Es. Formazione", color=TextSecondary,fontSize=14.sp);field() })
            error?.let { Text(it, color=ColorNegative, fontSize=12.sp) }
            Text("Icona", color=TextSecondary, fontSize=12.sp)
            dayTagIcons.chunked(3).forEachIndexed { row, icons ->
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    icons.forEachIndexed { column, key ->
                        val chosen=key==icon
                        Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(11.dp))
                            .background(if(chosen)ObsidianPrimaryContainer else ObsidianSurfaceContainer)
                            .border(if(chosen)1.5.dp else 0.dp,if(chosen)BrandInk else Color.Transparent,RoundedCornerShape(11.dp))
                            .semantics { contentDescription=tagIconLabels[row*3+column]; selected=chosen }
                            .bouncyClickable { icon=key }, contentAlignment=Alignment.Center) {
                            SiteIcon(key,null,Modifier.size(22.dp),if(chosen)BrandInk else TextPrimary)
                        }
                    }
                }
            }
            Text("Colore",color=TextSecondary,fontSize=12.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                tagSwatches.forEachIndexed { index, swatch ->
                    val colors=shiftColorStyle(Color(swatch),TurniThemeState.dark,ObsidianSurface)
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.codeFill)
                        .semantics { contentDescription="Colore ${index+1}"; selected=color==swatch }
                        .bouncyClickable { color=swatch },contentAlignment=Alignment.Center) {
                        if(color==swatch)SiteIcon("check",null,Modifier.size(20.dp),colors.codeInk)
                    }
                }
            }
            DayTagChip(DayTag("preview",name.ifBlank { "La tua etichetta" },icon,color),true)
            if(original!=null) {
                Text("Le modifiche aggiornano anche i giorni già segnati. Archiviare nasconde l’etichetta dalle nuove selezioni e conserva lo storico.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                CloudButton(if(original.archived) "Riattiva etichetta" else "Archivia etichetta", "folder", { save(!original.archived) }, enabled=name.isNotBlank()&&savedTag==null, secondary=true)
                if(ShiftRepository.dayExtraInfo.values.none { original.id in it.tagIds }) {
                    TextButton(onClick={deleting=true},colors=ButtonDefaults.textButtonColors(contentColor=ColorNegative)) { Text("Elimina etichetta inutilizzata") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        CloudButton("Salva etichetta","check",{ save(original?.archived ?: false) },enabled=name.isNotBlank()&&savedTag==null)
    }
    if(inlineContent)with(requireNotNull(inlineScope)){editor { savedTag?.let(onSaved)?:onClose() }}
    else NativeBottomDialog({savedTag?.let(onSaved)?:onClose()},scrollContent=false,content=editor)
}

@Composable
private fun TagSheetHeading(title: String, onClose: () -> Unit,onBack:(()->Unit)?=null,showClose:Boolean=true) {
    Row(Modifier.fillMaxWidth().heightIn(min=36.dp).padding(bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
        if(onBack!=null)PanelIcon("arrow-left","Indietro",onBack,width=36.dp,height=36.dp,glyphSize=18.dp,background=ObsidianSurfaceContainer)
        Text(title,color=TextPrimary,fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
        if(showClose)PanelIcon("x","Chiudi note ed etichette",onClose,width=36.dp,height=36.dp,glyphSize=18.dp,tint=ColorNegative)
    }
}

@Composable
internal fun ColumnScope.DayAnnotationsContent(date:LocalDate,maxHeight:Dp,onBack:()->Unit,onClose:()->Unit,onFeedback:(ActionFeedback)->Unit) {
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    var notes by remember(date) { mutableStateOf(ShiftRepository.getDayExtraInfo(date).notes) }
    var selected by remember(date) { mutableStateOf(ShiftRepository.getDayExtraInfo(date).tagIds) }
    var creating by remember(date) { mutableStateOf(false) }
    var query by remember(date) { mutableStateOf("") }
    BackHandler { if(creating)creating=false else onBack() }
    AnimatedContent(creating,modifier=Modifier.fillMaxWidth().heightIn(max=maxHeight),
        contentAlignment=Alignment.BottomCenter,transitionSpec={
            (fadeIn(tween(180,delayMillis=70)) togetherWith fadeOut(tween(100))) using
                SizeTransform(clip=true){_,_->tween(360,easing=FastOutSlowInEasing)}
        }) { creatingPage ->
    Column(Modifier.fillMaxWidth().heightIn(max=maxHeight)) {
    if(creatingPage) {
        DayTagEditor(null,{creating=false},inlineContent=true,inlineScope=this,onExit=onClose,onSaved={tag->selected=(selected+tag.id).distinct();query="";creating=false})
    } else {
        TagSheetHeading("Note ed etichette",onClose,onBack)
        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            val tags=ShiftRepository.dayTags.filter { !it.archived || it.id in selected }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER){it.name})
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Etichette della giornata",Modifier.weight(1f),color=TextSecondary,fontSize=12.sp)
                if(selected.isNotEmpty())Text("${selected.size} ${if(selected.size==1)"selezionata" else "selezionate"}",color=BrandInk,fontSize=11.sp)
            }
            if(tags.isEmpty())Text("Segna attività da ritrovare nelle statistiche, come Formazione o Visita medica.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
            if(tags.size>=7)BasicTextField(query,{query=it},singleLine=true,textStyle=TextStyle(color=TextPrimary,fontSize=12.sp),cursorBrush=SolidColor(ObsidianPrimary),
                modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(ObsidianSurfaceContainer).padding(11.dp),
                decorationBox={field->if(query.isBlank())Text("Cerca un’etichetta",color=TextSecondary,fontSize=12.sp);field()})
            val visibleTags=tags.filter{query.isBlank()||it.name.contains(query.trim(),ignoreCase=true)}
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                visibleTags.forEach { tag -> key(tag.id) {
                    DayTagChip(tag,selected=tag.id in selected,onClick={selected=if(tag.id in selected)selected-tag.id else selected+tag.id})
                } }
            }
            if(tags.isNotEmpty()&&visibleTags.isEmpty())Text("Nessuna etichetta corrisponde alla ricerca",color=TextSecondary,fontSize=12.sp)
            TextButton(onClick={creating=true},colors=ButtonDefaults.textButtonColors(contentColor=BrandInk)) { Text("+ Nuova etichetta") }
            Text("Nota della giornata",color=TextSecondary,fontSize=12.sp)
            BasicTextField(notes,{notes=it.take(500)},textStyle=TextStyle(color=TextPrimary,fontSize=12.sp,lineHeight=17.sp),
                cursorBrush=SolidColor(ObsidianPrimary),modifier=Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(13.dp)).background(ObsidianSurfaceContainer).padding(12.dp),
                decorationBox={field->if(notes.isBlank())Text("Aggiungi i dettagli, se vuoi…",color=TextSecondary,fontSize=12.sp,lineHeight=17.sp);field()})
        }
        Spacer(Modifier.height(12.dp))
        CloudButton("Salva giornata","check",{
            try {
                ShiftRepository.saveDayAnnotations(date,notes,selected)
                focus.clearFocus();keyboard?.hide();onFeedback(ActionFeedback("Note ed etichette salvate"));onBack()
            }catch(problem:IllegalArgumentException) {
                selected=selected.filter { id -> ShiftRepository.dayTags.any { it.id==id } }
                onFeedback(ActionFeedback("Le etichette sono cambiate: ricontrolla e salva",true))
            }
        })
    }
    }
    }
}

private val tagReportDateFormatter=DateTimeFormatter.ofPattern("EEE d MMM yyyy",Locale.ITALIAN)

/** Keep a single grouped surface while composing only the date rows in the viewport. */
internal fun LazyListScope.dayTagReport(metrics:List<DayTagMetric>,periodKey:String,onOpenDate:(LocalDate)->Unit) {
    val prefix="day-tags:$periodKey"
    item("$prefix:intro",contentType="tag-intro") {
        Spacer(Modifier.height(12.dp))
        Surface(color=ObsidianSurface,shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start=14.dp,end=14.dp,top=14.dp,bottom=10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Attività ed etichette",color=TextPrimary,fontSize=16.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold)
                Text("Tutti i giorni segnati nel periodo, raggruppati per attività. Tocca una data solo se vuoi aprirla nel calendario.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                if(metrics.isEmpty())Text("Nessuna attività segnata in questo periodo",color=TextSecondary,fontSize=12.sp)
            }
        }
    }
    metrics.forEachIndexed { index,metric ->
        item("$prefix:header:${metric.tag.id}",contentType="tag-header") {
            Surface(color=ObsidianSurface,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal=14.dp)) {
                    if(index>0)HorizontalDivider(Modifier.padding(top=6.dp,bottom=12.dp),color=ObsidianBorder)
                    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        val tone=shiftColorStyle(Color(metric.tag.colorHex),TurniThemeState.dark,ObsidianSurface)
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tone.codeFill),contentAlignment=Alignment.Center){SiteIcon(metric.tag.iconKey,null,Modifier.size(18.dp),tone.codeInk)}
                        Text(metric.tag.name,Modifier.weight(1f),color=TextPrimary,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
                        Text("${metric.dates.size} ${if(metric.dates.size==1)"giorno" else "giorni"}",color=TextPrimary,fontWeight=FontWeight.SemiBold,fontSize=13.sp)
                    }
                }
            }
        }
        items(metric.dates,key={date->"$prefix:date:${metric.tag.id}:$date"},contentType={"tag-date"}) { date ->
            val notes=buildList {
                ShiftRepository.getDayExtraInfo(date).notes.takeIf{it.isNotBlank()}?.let{add(it)}
                ShiftRepository.getShiftsForDate(date).map{it.notes}.filter{it.isNotBlank()}.distinct().forEach{add(it)}
            }.distinct()
            Surface(color=ObsidianSurface,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal=14.dp).clip(RoundedCornerShape(12.dp)).bouncyClickable{onOpenDate(date)}.padding(horizontal=8.dp,vertical=8.dp),
                    verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    SiteIcon("calendar",null,Modifier.padding(top=2.dp).size(16.dp),BrandInk)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(date.format(tagReportDateFormatter),color=TextPrimary,fontSize=13.sp,fontWeight=FontWeight.Medium)
                        notes.forEach{Text(it,color=TextSecondary,fontSize=12.sp,lineHeight=17.sp)}
                    }
                    SiteIcon("chevron-right","Apri giorno",Modifier.size(16.dp),TextSecondary)
                }
            }
        }
    }
    item("$prefix:footer",contentType="tag-footer") {
        Surface(color=ObsidianSurface,shape=RoundedCornerShape(bottomStart=20.dp,bottomEnd=20.dp),modifier=Modifier.fillMaxWidth()) {Spacer(Modifier.height(14.dp))}
        Spacer(Modifier.height(12.dp))
    }
}
