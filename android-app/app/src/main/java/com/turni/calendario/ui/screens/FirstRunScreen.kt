@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.*
import com.turni.calendario.sync.DriveAutomaticBackup
import com.turni.calendario.sync.GoogleDriveBackupHelper
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Optional full editors share the same controls and persistence as Settings. */
@Composable
fun FirstRunScreen() {
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val step=FirstRunStore.step
    val lastStep=FirstRunStore.LAST_STEP
    val scope=rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("") }
    var requestedRotation by remember { mutableStateOf<ShiftRotation?>(null) }
    var name by rememberSaveable { mutableStateOf(ShiftRepository.activeWorkProfile.name) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val close:()->Unit={page=""}
    val persistWorkName:()->Unit={
        val trimmed=name.trim()
        if(trimmed.isNotEmpty()) {
            name=trimmed
            val current=ShiftRepository.activeWorkProfile
            if(trimmed!=current.name) {
                ShiftRepository.updateWorkProfile(current.copy(name=trimmed))
                ShiftRepository.notifyDataChanged()
            }
        }
    }
    BackHandler(!finishing&&(step>0||page.isNotBlank())) { if(page.isNotBlank())close() else FirstRunStore.goTo(context,step-1) }
    val finish:()->Unit={
        if(!finishing)scope.launch {
            finishing=true
            try {LocalDatabaseStore.awaitCurrentSave();FirstRunStore.complete(context)}
            catch(e:TimeoutCancellationException){name=ShiftRepository.activeWorkProfile.name;page="";feedback=ActionFeedback("Salvataggio locale non completato: riprova tra poco",true)}
            catch(e:CancellationException){throw e}
            catch(e:Exception){name=ShiftRepository.activeWorkProfile.name;page="";feedback=ActionFeedback("Salvataggio locale non completato: riprova tra poco",true)}
            finally{finishing=false}
        }
    }
    AnimatedContent(page,transitionSpec={fadeIn(spring(stiffness=420f)) + slideInHorizontally(spring(.95f,420f)){it/16} togetherWith fadeOut(tween(120)) using null}) { destination ->
        when(destination) {
            "restore" -> CloudBackupScreen(onDismiss=close,restoreOnly=true,onRestored=finish,accountEntry=true)
            "models" -> TurniModelsScreen(onDismiss={name=ShiftRepository.activeWorkProfile.name;close()},initialTab=if(requestedRotation!=null)2 else 0,initialEditingRotation=requestedRotation,onClearInitialRotation={requestedRotation=null})
            "ai" -> GeminiShiftImportScreen(onDismiss=close,onNavigateToTurni={requestedRotation=it;page="models"})
            "work" -> WorkProfileEditorScreen(ShiftRepository.activeWorkProfile,false,close,{profile->ShiftRepository.updateWorkProfile(profile);ShiftRepository.notifyDataChanged();name=profile.name;close()})
            "holidays" -> HolidaysSettingsScreen(onDismiss=close)
            "oncall" -> ReperibilitaSettingsScreen(onDismiss=close)
            "rules" -> StatisticsRulesScreen(onDismiss=close)
            "alarms" -> SmartAlarmsSettingsScreen(onDismiss=close)
            "labels" -> DayTagsSettingsScreen(onDismiss=close)
            "backup" -> CloudBackupScreen(onDismiss={name=ShiftRepository.activeWorkProfile.name;close()})
            "google" -> GoogleCalendarSyncScreen(onDismiss=close)
            else -> Box(Modifier.fillMaxSize().background(ObsidianBackground).statusBarsPadding().navigationBarsPadding().imePadding()) {
                val automaticBackup by DriveAutomaticBackup.statusFlow.collectAsState()
                val localBackupInfo by produceState<String?>(null, page, step) {
                    value = if (step == lastStep && page.isEmpty()) {
                        withContext(Dispatchers.IO) {
                            DriveAutomaticBackup.refresh(context)
                            GoogleDriveBackupHelper.getLocalBackupInfo(context)
                        }
                    } else null
                }
                Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min=42.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(step>0)Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(ObsidianSurface).bouncyClickable(!finishing) {FirstRunStore.goTo(context,step-1)},contentAlignment=Alignment.Center) {SiteIcon("arrow-left","Indietro",Modifier.size(22.dp))}
                        else SiteIcon("calendar",null,Modifier.size(28.dp),BrandInk)
                        Spacer(Modifier.width(12.dp))
                        Text("TurniSync",fontSize=18.sp,fontWeight=FontWeight.Bold,color=TextPrimary,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        if(step>0)Text("$step / $lastStep",fontSize=12.sp,color=TextSecondary,maxLines=1,softWrap=false)
                    }
                    if(step>0)Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=10.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        repeat(lastStep) { index -> val color by animateColorAsState(if(index<step)ObsidianPrimary else ObsidianSurfaceContainer,spring(stiffness=350f))
                            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(3.dp)).background(color))
                        }
                    }
                    val scroll=rememberScrollState()
                    LaunchedEffect(step){scroll.scrollTo(0)}
                    Column(Modifier.weight(1f).verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        AnimatedContent(step,transitionSpec={val direction=if(targetState>initialState)1 else -1;fadeIn(spring(stiffness=380f))+slideInHorizontally(spring(.95f,380f)){direction*it/14} togetherWith fadeOut(tween(110)) using null}) { current ->
                            Column(verticalArrangement=Arrangement.spacedBy(if(current==1||current==3)8.dp else 10.dp)) {
                                when(current) {
                                    0 -> {
                                        SetupTitle("I tuoi turni,\na colpo d’occhio.","Organizza turni, riposi e ferie in un solo calendario.",large=true)
                                        SetupPreview()
                                        Text("Inizi in meno di un minuto. I dati restano sul telefono: l’account è facoltativo.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                                    }
                                    1 -> {
                                        SetupTitle("Scegli il tuo stile","Personalizza tema e formato.")
                                        CloudCard("Tema") { PreferenceThemeSelector() }
                                        SetupPreview(compact=true)
                                        SetupCalendarFormatCard()
                                    }
                                    2 -> {
                                        val activeWorkId=ShiftRepository.activeWorkProfileId
                                        val activeWork=ShiftRepository.activeWorkProfile
                                        val profileModels=ShiftRepository.shiftDefinitions.filter{it.workProfileId==activeWorkId}
                                        SetupTitle("Il tuo lavoro e i turni","Dai un nome al calendario e prepara i primi modelli. Non inseriamo ancora giorni.")
                                        CloudCard("Il tuo lavoro") {
                                            BasicTextField(name,{name=it.take(60)},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}),textStyle=TextStyle(fontSize=17.sp,color=TextPrimary),cursorBrush=SolidColor(BrandInk),
                                                modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceContainer).semantics {contentDescription="Nome del lavoro"}.padding(13.dp),decorationBox={field->if(name.isBlank())Text("Es. Reparto, Ufficio, Impianto",color=TextSecondary);field()})
                                            if(name.isBlank()) {
                                                Text("Inserisci un nome per continuare.",color=Color(currentTurniPalette.warning.ink),fontSize=11.sp,lineHeight=16.sp)
                                            }
                                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceContainer).bouncyClickable {persistWorkName();page="work"}.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                                SiteIcon(activeWork.iconKey,null,Modifier.size(20.dp),BrandInk)
                                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                                    Text("Dettagli del lavoro (facoltativo)",color=TextPrimary,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                                                    val detailsSummary=buildList {
                                                        if(activeWork.weeklyHours>0)add("${activeWork.weeklyHours} h/sett")
                                                        if(activeWork.minRestHoursBetweenShifts>0)add("riposo ${activeWork.minRestHoursBetweenShifts} h")
                                                        if(activeWork.maxConsecutiveWorkDays>0)add("max ${activeWork.maxConsecutiveWorkDays} di fila")
                                                    }.joinToString(" · ").ifEmpty { "Controlli orario facoltativi" }
                                                    Text(detailsSummary,color=TextSecondary,fontSize=11.sp,lineHeight=16.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                                                }
                                                SiteIcon("chevron-right",null,Modifier.size(16.dp),TextSecondary)
                                            }
                                        }
                                        if(profileModels.isEmpty()) {
                                            val availableStarters=missingStarterShiftDefinitions(activeWorkId,ShiftRepository.shiftDefinitions)
                                            if(availableStarters.isNotEmpty()) {
                                                CloudCard("Modelli di turno","Esempi modificabili pronti per il tuo lavoro · il calendario resta vuoto") {
                                                    ShiftChipPreviewRow(availableStarters,ObsidianSurface)
                                                    CloudButton(if(availableStarters.size==5)"Parti da 5 modelli base" else "Aggiungi ${availableStarters.size} modelli base","plus",{
                                                        val missing=missingStarterShiftDefinitions(activeWorkId,ShiftRepository.shiftDefinitions)
                                                        if(missing.isNotEmpty()) {
                                                            ShiftRepository.shiftDefinitions.addAll(missing)
                                                            ShiftRepository.notifyDataChanged()
                                                            feedback=ActionFeedback("${missing.size} modelli base aggiunti")
                                                        }
                                                    })
                                                    CloudButton("Crea i tuoi modelli","clock",{persistWorkName();page="models"},secondary=true)
                                                }
                                            } else {
                                                CloudCard("Modelli di turno","Nessun modello per questo lavoro · il calendario resta vuoto") {
                                                    CloudButton("Crea i tuoi modelli","clock",{persistWorkName();page="models"})
                                                }
                                            }
                                        } else {
                                            val rotCount=ShiftRepository.rotations.count { it.workProfileId==activeWorkId }
                                            CloudCard(
                                                "${profileModels.size} ${if(profileModels.size==1)"modello pronto" else "modelli pronti"}",
                                                if(rotCount==0)"Puoi già usarli o aggiungere una rotazione." else "$rotCount ${if(rotCount==1)"rotazione configurata" else "rotazioni configurate"}."
                                            ) {
                                                ShiftChipPreviewRow(profileModels,ObsidianSurface)
                                                CloudButton("Modifica modelli e rotazioni","clock",{persistWorkName();page="models"},secondary=true)
                                            }
                                        }
                                        SetupLink("ai","scan-text","Hai già un prospetto turni?","Copia il prompt per la tua AI, incolla la risposta e controlla i turni prima di salvare (senza API key)."){persistWorkName();page=it}
                                    }
                                    3 -> {
                                        Text(
                                            text = "Prova le funzioni di modifica",
                                            color = TextPrimary,
                                            fontSize = 21.sp,
                                            lineHeight = 25.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-.4).sp
                                        )
                                        GesturePractice(onTutorialCompleted = { if (!finishing && step == 3) FirstRunStore.goTo(context, step + 1) })
                                    }
                                    else -> {
                                        SetupTitle("Tutto pronto","Puoi aprire subito il calendario, oppure rifinire ora le opzioni qui sotto.")
                                        SetupRecapCard(onOpenModels={page="models"})
                                        SetupOptions(localBackupInfo=localBackupInfo,driveAutoEnabled=automaticBackup.enabled,onOpen={page=it})
                                        SetupCalendarLegendCard()
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if(step==0) {
                        Spacer(Modifier.height(8.dp))
                        CloudButton("Inizia","arrow-right",{FirstRunStore.goTo(context,1)})
                        Spacer(Modifier.height(8.dp))
                        CloudButton("Hai già un account? Accedi","users",{page="restore"},secondary=true)
                    }
                    if(step>0) {
                        Spacer(Modifier.height(8.dp))
                        CloudButton(if(finishing)"Salvataggio…" else if(step==lastStep)"Apri il mio calendario" else "Continua",if(step==lastStep)"check" else "arrow-right",{
                            if(!finishing) {
                                finishing=true
                                focus.clearFocus();keyboard?.hide()
                                scope.launch {
                                    try {
                                        if(step==2)persistWorkName()
                                        LocalDatabaseStore.awaitCurrentSave()
                                        if(step==lastStep)FirstRunStore.complete(context)else FirstRunStore.goTo(context,step+1)
                                    } catch(e:TimeoutCancellationException){feedback=ActionFeedback("Salvataggio locale non completato: riprova tra poco",true)}
                                    catch(e:CancellationException){throw e}
                                    catch(e:Exception){feedback=ActionFeedback("Salvataggio locale non completato: riprova tra poco",true)}
                                    finally{finishing=false}
                                }
                            }
                        },enabled=!finishing&&(step!=2||name.isNotBlank()))
                        Text("Puoi cambiare queste scelte in qualsiasi momento",color=TextSecondary,fontSize=10.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=6.dp,bottom=2.dp))
                    }
                }
                MagicalActionFeedbackHUD(feedback,{feedback=null},Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable private fun SetupTitle(title:String,description:String,large:Boolean=false) {
    Column(verticalArrangement=Arrangement.spacedBy(if(large)8.dp else 5.dp),modifier=Modifier.padding(top=if(large)8.dp else 0.dp,bottom=2.dp)) {
        Text(title,color=TextPrimary,fontSize=if(large)29.sp else 26.sp,lineHeight=if(large)33.sp else 30.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
        Text(description,color=TextSecondary,fontSize=13.sp,lineHeight=19.sp)
    }
}
@Composable private fun SetupToggle(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,color=TextPrimary,fontSize=13.sp,modifier=Modifier.weight(1f));Switch(checked=value,onCheckedChange=onChange)
    }
}
@Composable private fun SetupCalendarFormatCard() {
    val largeFont=LocalDensity.current.fontScale>1.3f
    val prefs=ShiftRepository.appPreferences
    Surface(shape=RoundedCornerShape(18.dp),color=ObsidianSurface,modifier=Modifier.fillMaxWidth()) {
        if(!largeFont) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                FormatSegmentGroup(
                    label="Settimana",
                    options=listOf(
                        Triple("Lun","Inizio settimana da lunedì",prefs.weekStartMonday),
                        Triple("Dom","Inizio settimana da domenica",!prefs.weekStartMonday)
                    ),
                    modifier=Modifier.weight(1f)
                ) { idx ->
                    ShiftRepository.appPreferences=prefs.copy(weekStartMonday=idx==0)
                    ShiftRepository.notifyDataChanged()
                }
                FormatSegmentGroup(
                    label="Orario",
                    options=listOf(
                        Triple("24h","Formato 24 ore",prefs.timeFormat24h),
                        Triple("12h","Formato 12 ore",!prefs.timeFormat24h)
                    ),
                    modifier=Modifier.weight(1f)
                ) { idx ->
                    ShiftRepository.appPreferences=prefs.copy(timeFormat24h=idx==0)
                    ShiftRepository.notifyDataChanged()
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                FormatSegmentGroup(
                    label="Settimana",
                    options=listOf(
                        Triple("Lunedì","Inizio settimana da lunedì",prefs.weekStartMonday),
                        Triple("Domenica","Inizio settimana da domenica",!prefs.weekStartMonday)
                    ),
                    modifier=Modifier.fillMaxWidth()
                ) { idx ->
                    ShiftRepository.appPreferences=prefs.copy(weekStartMonday=idx==0)
                    ShiftRepository.notifyDataChanged()
                }
                FormatSegmentGroup(
                    label="Orario",
                    options=listOf(
                        Triple("24 ore","Formato 24 ore",prefs.timeFormat24h),
                        Triple("12 ore","Formato 12 ore",!prefs.timeFormat24h)
                    ),
                    modifier=Modifier.fillMaxWidth()
                ) { idx ->
                    ShiftRepository.appPreferences=prefs.copy(timeFormat24h=idx==0)
                    ShiftRepository.notifyDataChanged()
                }
            }
        }
    }
}
@Composable private fun FormatSegmentGroup(label:String,options:List<Triple<String,String,Boolean>>,modifier:Modifier=Modifier,onSelect:(Int)->Unit) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,color=TextSecondary,fontSize=11.sp,fontWeight=FontWeight.Medium)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ObsidianSurfaceHigh).padding(3.dp).selectableGroup(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            options.forEachIndexed { idx,(text,semanticLabel,active) ->
                val fill by animateColorAsState(if(active)ObsidianPrimary else Color.Transparent,tween(180))
                val ink=if(active)OnBrand else TextSecondary
                Box(Modifier.weight(1f).heightIn(min=40.dp).clip(RoundedCornerShape(9.dp)).background(fill)
                    .semantics { stateDescription=semanticLabel; selected=active; role=Role.Tab }.bouncyClickable { if(!active)onSelect(idx) },contentAlignment=Alignment.Center) {
                    Text(text,color=ink,fontSize=12.sp,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}
@Composable private fun ShiftChipPreviewRow(models:List<ShiftDefinition>,backdrop:Color) {
    val dark=TurniThemeState.dark
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        models.forEach { def ->
            val tone=shiftColorStyle(Color(def.colorHex),dark,backdrop,definitionId=def.id)
            Column(Modifier.clip(RoundedCornerShape(10.dp)).background(tone.codeFill).padding(horizontal=10.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(def.shortCode.ifBlank { def.name.take(4) },color=tone.codeInk,fontSize=11.sp,fontWeight=FontWeight.Bold,maxLines=1)
                Text(if(def.isAllDay)def.name else def.startTime,color=tone.codeInk,fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable private fun SetupLink(id:String,icon:String,title:String,description:String,onOpen:(String)->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ObsidianSurface).bouncyClickable {onOpen(id)}.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        SiteIcon(icon,null,Modifier.size(22.dp),BrandInk)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(title,color=TextPrimary,fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text(description,color=TextSecondary,fontSize=12.sp,lineHeight=17.sp)}
        SiteIcon("chevron-right",null,Modifier.size(16.dp),TextSecondary)
    }
}
@Composable private fun SetupPreview(compact:Boolean=false) {
    GesturePractice(showGuide=false,compactPreview=compact)
}
@Composable private fun SetupRecapCard(onOpenModels:()->Unit) {
    val palette=currentTurniPalette
    val activeWork=ShiftRepository.activeWorkProfile
    val prefs=ShiftRepository.appPreferences
    val modelCount=ShiftRepository.shiftDefinitions.count { it.workProfileId==ShiftRepository.activeWorkProfileId }
    CloudCard("La tua base") {
        RecapLine("Lavoro",activeWork.name.ifBlank { "Il mio lavoro" })
        RecapLine("Stile","${TurniThemeState.mode.label} · ${if(prefs.weekStartMonday)"Lun–Dom" else "Dom–Sab"} · ${if(prefs.timeFormat24h)"24h" else "12h"}")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Modelli",color=TextSecondary,fontSize=12.sp,modifier=Modifier.widthIn(min=58.dp))
            if(modelCount>0) {
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    SiteIcon("check",null,Modifier.size(14.dp),Color(palette.success.ink))
                    Text("$modelCount ${if(modelCount==1)"pronto" else "pronti"}",color=TextPrimary,fontSize=13.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            } else {
                Text("Nessuno",color=TextSecondary,fontSize=13.sp,modifier=Modifier.weight(1f))
                Box(Modifier.heightIn(min=40.dp).clip(RoundedCornerShape(10.dp)).background(ObsidianPrimarySubtle).bouncyClickable(onClick=onOpenModels).padding(horizontal=12.dp),contentAlignment=Alignment.Center) {
                    Text("Aggiungi",color=BrandInk,fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
}
@Composable private fun RecapLine(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,color=TextSecondary,fontSize=12.sp,modifier=Modifier.widthIn(min=58.dp))
        Text(value,color=TextPrimary,fontSize=13.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
    }
}
@Composable private fun SetupCalendarLegendCard() {
    val largeFont=LocalDensity.current.fontScale>1.3f
    val items=listOf(
        Triple("edit","Matita","Inserisci turni"),
        Triple("clock","Orologio","Modelli e rotazioni"),
        Triple("report","Grafico","Statistiche"),
        Triple("settings","Ingranaggio","Opzioni e backup")
    )
    CloudCard("Dove trovi tutto nel calendario","Il tuo calendario parte vuoto: l’esempio di prova non viene copiato.") {
        if(!largeFont) {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (icon,title,subtitle) -> LegendTile(icon,title,subtitle,Modifier.weight(1f).fillMaxHeight()) }
                    }
                }
            }
        } else {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items.forEach { (icon,title,subtitle) -> LegendTile(icon,title,subtitle,Modifier.fillMaxWidth()) }
            }
        }
    }
}
@Composable private fun LegendTile(icon:String,title:String,subtitle:String,modifier:Modifier=Modifier) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(ObsidianSurfaceContainer).padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
        SiteIcon(icon,null,Modifier.size(18.dp),BrandInk)
        Column(verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Text(title,color=TextPrimary,fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(subtitle,color=TextSecondary,fontSize=11.sp,lineHeight=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

private data class SetupOptionItem(val id:String,val icon:String,val title:String,val description:String)
@Composable private fun SetupOptions(localBackupInfo:String?,driveAutoEnabled:Boolean,onOpen:(String)->Unit) {
    val prefs=ShiftRepository.appPreferences
    val holidayStatus=if(!prefs.showHolidays)"Disattivate nel calendario" else "${ShiftRepository.enabledHolidayIds.size} attive · Nazionali, Patroni e CCNL"
    val tagCount=ShiftRepository.activeDayTags.size
    val labelsStatus=if(tagCount==0)"Nessuna etichetta · es. Formazione, Visita" else "$tagCount ${if(tagCount==1)"etichetta pronta" else "etichette pronte"}"
    val rulesCount=ShiftRepository.activeStatisticsRules.count { it.enabled }
    val exStatus=if(prefs.showExShifts)"mostra ex turno" else "ex turno nascosto"
    val rulesStatus=if(rulesCount==0)"Standard · $exStatus" else "$rulesCount ${if(rulesCount==1)"regola attiva" else "regole attive"} · $exStatus"
    val onCallStatus=reperibilitaSetupSummary(ShiftRepository.reperibilitaConfig)
    val alarmsStatus=alarmsSetupSummary(ShiftRepository.smartAlarmsConfig)
    val account=ShiftRepository.googleAccountProfile
    val backupStatus=when {
        account!=null -> "Account collegato (${account.email})" + if(driveAutoEnabled)" · Drive auto attivo" else ""
        localBackupInfo!=null -> "Copia locale ($localBackupInfo) · Drive facoltativo"
        else -> "Solo su telefono · Drive o file facoltativo"
    }
    val googleCalStatus=if(account!=null)"Account collegato · esporta su un calendario" else "Facoltativo · copia i turni sul tuo calendario"
    val groups=listOf(
        "Calendario e statistiche" to listOf(
            SetupOptionItem("holidays","calendar-heart","Festività",holidayStatus),
            SetupOptionItem("labels","tag","Etichette dei giorni",labelsStatus),
            SetupOptionItem("rules","chart-no-axes-combined","Conteggio statistiche",rulesStatus)
        ),
        "Reperibilità e sveglie" to listOf(
            SetupOptionItem("oncall","bell-ring","Reperibilità",onCallStatus),
            SetupOptionItem("alarms","alarm-clock","Sveglie dei turni",alarmsStatus)
        ),
        "Backup e sincronizzazione" to listOf(
            SetupOptionItem("backup","database-backup","Backup e ripristino",backupStatus),
            SetupOptionItem("google","calendar-sync","Google Calendar",googleCalStatus)
        )
    )
    groups.forEach { group ->
        var expanded by rememberSaveable(group.first) {mutableStateOf(false)}
        val chevronAngle by animateFloatAsState(if(expanded)180f else 0f,spring(.8f,380f))
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ObsidianSurfaceContainer).semantics {stateDescription=if(expanded)"Aperto" else "Chiuso"}.bouncyClickable {expanded=!expanded}.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(group.first,color=TextPrimary,fontSize=14.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                SiteIcon("chevron-down",null,Modifier.size(18.dp).rotate(chevronAngle),BrandInk)
            }
            AnimatedVisibility(expanded,enter=expandVertically(spring(.9f,400f))+fadeIn(),exit=shrinkVertically(spring(.95f,400f))+fadeOut()) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){group.second.forEach {row->SetupLink(row.id,row.icon,row.title,row.description,onOpen)}}
            }
        }
    }
}

@Composable internal fun QuickGuideScreen(onDismiss:()->Unit) {
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(rulesOpen) { rulesOpen=false }
    AnimatedContent(rulesOpen,transitionSpec={fadeIn(spring(stiffness=420f))+slideInHorizontally(spring(.95f,420f)){it/16} togetherWith fadeOut(tween(120))}) { showRules ->
        if(showRules) StatisticsRulesScreen(onDismiss={rulesOpen=false})
        else CloudFeatureScaffold("Guida rapida",onDismiss,null,{}) {
            Text("Prova gli strumenti senza cambiare i tuoi turni. Scegli una funzione o esplora con la barra qui sotto.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)
            GesturePractice()
            CloudCard("Ritrova tutto al suo posto") {
                Text("Scorri per cambiare mese; Oggi ti riporta alla data attuale. La X chiude il dettaglio. Nell’orologio prepari modelli, lavori e rotazioni: la stella sceglie la sequenza predefinita.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)
            }
            ShiftOriginExplanation { rulesOpen=true }
            CloudCard("Dai turni al riepilogo") {
                Text("Le statistiche leggono i tuoi dati per Mese, Anno o un periodo Personalizzato. Le etichette raggruppano le attività e ne mostrano le date. Nelle Impostazioni trovi sveglie, backup e sincronizzazione; controlli sempre l’anteprima prima di esportare un PDF.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)
            }
        }
    }
}

@Composable private fun ShiftOriginExplanation(onOpenRules:()->Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val angle by animateFloatAsState(if(expanded)180f else 0f,spring(.9f,380f))
    CloudCard("Quando sostituisci un turno") {
        Text("Un turno inserito da una rotazione può conservare la sua provenienza quando lo cambi.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)
        TextButton(onClick={expanded=!expanded},modifier=Modifier.semantics { stateDescription=if(expanded)"Aperto" else "Chiuso" }) {
            Text(if(expanded)"Chiudi la spiegazione" else "Ex turno e statistiche",color=BrandInk,fontSize=13.sp)
            Spacer(Modifier.width(6.dp))
            SiteIcon("chevron-down",null,Modifier.size(16.dp).rotate(angle),BrandInk)
        }
        AnimatedVisibility(expanded,enter=expandVertically(spring(.9f,400f))+fadeIn(),exit=shrinkVertically(spring(.95f,400f))+fadeOut()) {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Per esempio, cambi Mattina in Ferie: il calendario mostra Ferie e può ricordare «ex MAT». La gomma rimuove anche questa provenienza.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)
                SetupToggle("Mostra turno di provenienza",ShiftRepository.appPreferences.showExShifts) {
                    ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(showExShifts=it)
                    ShiftRepository.notifyDataChanged()
                }
                Text("La scelta viene salvata per il tuo calendario. Cambia solo la griglia: nascondere l’ex turno non lo cancella e non cambia le statistiche.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                Text("Di norma quel giorno conta come Ferie. Se attivi una regola che, per Ferie, ammette Mattina come turno originale, conterà Mattina al suo posto. La regola funziona solo quando quella provenienza è registrata.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)
                Text("Le eventuali esclusioni delle ferie nei festivi si applicano dopo questa scelta, al modello che viene contato.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                CloudButton("Regole di conteggio","report",onOpenRules,secondary=true)
            }
        }
    }
}
