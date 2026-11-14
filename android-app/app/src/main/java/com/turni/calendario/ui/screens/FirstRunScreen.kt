@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.*
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Optional full editors share the same controls and persistence as Settings. */
@Composable
fun FirstRunScreen() {
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val step=FirstRunStore.step
    val scope=rememberCoroutineScope()
    var finishing by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("") }
    var requestedRotation by remember { mutableStateOf<ShiftRotation?>(null) }
    var name by rememberSaveable { mutableStateOf(ShiftRepository.activeWorkProfile.name) }
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    val close:()->Unit={page=""}
    BackHandler(step>0||page.isNotBlank()) { if(page.isNotBlank())close() else FirstRunStore.goTo(context,step-1) }
    val finish:()->Unit={
        if(!finishing)scope.launch {
            finishing=true
            try {LocalDatabaseStore.awaitCurrentSave();FirstRunStore.complete(context)}
            catch(e:CancellationException){throw e}
            catch(e:Exception){page="";feedback=ActionFeedback("Salvataggio locale non completato: riprova tra poco",true)}
            finally{finishing=false}
        }
    }
    AnimatedContent(page,transitionSpec={fadeIn(tween(220)) with fadeOut(tween(160)) using null}) { destination ->
        when(destination) {
            "restore" -> CloudBackupScreen(onDismiss=close,restoreOnly=true,onRestored=finish)
            "models" -> TurniModelsScreen(onDismiss=close,initialTab=if(requestedRotation!=null)2 else 0,initialEditingRotation=requestedRotation,onClearInitialRotation={requestedRotation=null})
            "ai" -> GeminiShiftImportScreen(onDismiss=close,onNavigateToTurni={requestedRotation=it;page="models"})
            "work" -> WorkProfileEditorScreen(ShiftRepository.activeWorkProfile,false,close,{profile->ShiftRepository.updateWorkProfile(profile);ShiftRepository.notifyDataChanged();name=profile.name;close()})
            "holidays" -> HolidaysSettingsScreen(onDismiss=close)
            "oncall" -> ReperibilitaSettingsScreen(onDismiss=close)
            "rules" -> StatisticsRulesScreen(onDismiss=close)
            "alarms" -> SmartAlarmsSettingsScreen(onDismiss=close)
            "labels" -> DayTagsSettingsScreen(onDismiss=close)
            "backup" -> CloudBackupScreen(onDismiss=close)
            "google" -> GoogleCalendarSyncScreen(onDismiss=close)
            "pdf" -> PdfExportScreen(onDismiss=close)
            else -> Box(Modifier.fillMaxSize().background(ObsidianBackground).statusBarsPadding().navigationBarsPadding().imePadding()) {
                Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=10.dp)) {
                    Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(step>0)Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(ObsidianSurface).bouncyClickable {FirstRunStore.goTo(context,step-1)},contentAlignment=Alignment.Center) {SiteIcon("arrow-left","Indietro",Modifier.size(22.dp))}
                        else SiteIcon("calendar",null,Modifier.size(28.dp),BrandInk)
                        Spacer(Modifier.width(12.dp))
                        Text("TurniSync",fontSize=18.sp,fontWeight=FontWeight.Bold,color=TextPrimary)
                        Spacer(Modifier.weight(1f))
                        if(step>0)Text("$step di 5",fontSize=12.sp,color=TextSecondary)
                    }
                    if(step>0)Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        repeat(5) { index -> val color by animateColorAsState(if(index<step)ObsidianPrimary else ObsidianSurfaceContainer,tween(250))
                            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(3.dp)).background(color))
                        }
                    }
                    val scroll=rememberScrollState()
                    LaunchedEffect(step){scroll.scrollTo(0)}
                    Column(Modifier.weight(1f).verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        AnimatedContent(step,transitionSpec={fadeIn(tween(250))+slideInHorizontally(tween(300)){it/12} with fadeOut(tween(120)) using null}) { current ->
                            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                when(current) {
                                    0 -> {
                                        SetupTitle("Il tuo tempo,\na modo tuo.","Un calendario costruito sui tuoi turni. Parti da zero oppure recupera quello che avevi già preparato.")
                                        SetupPreview()
                                        CloudButton("Inizio da zero","plus",{FirstRunStore.goTo(context,1)})
                                        CloudButton("Ho già usato TurniSync","download",{page="restore"},secondary=true)
                                        Text("Il calendario funziona anche senza account. Google serve quando scegli backup o sincronizzazione.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                                    }
                                    1 -> {
                                        SetupTitle("Partiamo dall’aspetto","Scegli come vuoi leggere il calendario. Le modifiche si vedono subito e restano modificabili nelle Impostazioni.")
                                        CloudCard("Il tuo tema") { PreferenceThemeSelector() }
                                        SetupPreview()
                                        CloudCard("Come leggi la settimana") {
                                            SetupToggle("Lunedì come primo giorno",ShiftRepository.appPreferences.weekStartMonday){ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(weekStartMonday=it);ShiftRepository.notifyDataChanged()}
                                            SetupToggle("Orari in formato 24 ore",ShiftRepository.appPreferences.timeFormat24h){ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(timeFormat24h=it);ShiftRepository.notifyDataChanged()}
                                        }
                                    }
                                    2 -> {
                                        SetupTitle("Come chiami il tuo lavoro?","Puoi avere più lavori, ciascuno con colori, modelli e regole proprie. Iniziamo dal primo.")
                                        CloudCard("Nome del lavoro") {
                                            BasicTextField(name,{name=it.take(60)},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}),textStyle=TextStyle(fontSize=18.sp,color=TextPrimary),cursorBrush=SolidColor(BrandInk),
                                                modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceContainer).padding(14.dp),decorationBox={field->if(name.isBlank())Text("Es. Il mio lavoro",color=TextSecondary);field()})
                                            Text("Scegli un nome riconoscibile anche nel selettore in alto sul calendario.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                                        }
                                        SetupLink("work","work","Orari e riposo minimo","Imposta le ore settimanali, il riposo tra due turni e l’icona del lavoro."){if(name.isNotBlank()){ShiftRepository.updateWorkProfile(ShiftRepository.activeWorkProfile.copy(name=name.trim()));ShiftRepository.notifyDataChanged()};page=it}
                                    }
                                    3 -> {
                                        SetupTitle("Prepariamo i tuoi turni","Ogni modello contiene nome, sigla, colore e orario. Una rotazione ripete una sequenza di modelli: scegli tu quali usare.")
                                        CloudCard("${ShiftRepository.shiftDefinitions.size} modelli · ${ShiftRepository.rotations.size} rotazioni") {
                                            Text("La tua scelta non inserisce turni sul calendario: li applicherai tu ai giorni.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                                            if(ShiftRepository.shiftDefinitions.isEmpty())CloudButton("Parti da 5 esempi modificabili","plus",{
                                                if(ShiftRepository.shiftDefinitions.isEmpty())ShiftRepository.shiftDefinitions.addAll(starterShiftDefinitions(ShiftRepository.activeWorkProfileId));ShiftRepository.notifyDataChanged();feedback=ActionFeedback("Modelli di esempio aggiunti")
                                            })
                                            CloudButton(if(ShiftRepository.shiftDefinitions.isEmpty())"Crea i tuoi modelli" else "Controlla modelli e rotazioni","clock",{page="models"},secondary=true)
                                        }
                                        SetupLink("ai","scan-text","Hai già un prospetto?","Copia un prompt nella tua AI, allega il prospetto e incolla qui la risposta. Controlla e correggi ogni modello prima di salvarlo."){page=it}
                                    }
                                    4 -> {
                                        SetupTitle("Prova, senza modificare i dati","Bastano pochi gesti. Questa è una piccola area di prova: puoi toccare e tenere premuto in sicurezza.")
                                        GesturePractice()
                                        CloudCard("Quando apri un giorno") {Text("Nel dettaglio trovi i turni. La rotella apre straordinario, trasferta e nota del singolo turno. In Note ed etichette segni attività della giornata da ritrovare anche nelle statistiche.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)}
                                    }
                                    else -> {
                                        SetupTitle("L’ultimo tocco è tuo","Queste opzioni sono facoltative. Apri solo quelle che ti servono: le ritroverai tutte nelle Impostazioni.")
                                        SetupOptions {page=it}
                                        CloudCard("Pronto a iniziare") {Text("Il calendario parte vuoto. Tocca la matita per inserire i turni; nell’orologio gestisci modelli, lavori e rotazioni. Le statistiche si aggiornano dai tuoi dati.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)}
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if(step>0) {
                        Spacer(Modifier.height(10.dp))
                        CloudButton(if(finishing)"Salvataggio…" else if(step==5)"Apri il mio calendario" else "Continua","check",{
                            if(!finishing) {
                                finishing=true
                                focus.clearFocus();keyboard?.hide()
                                scope.launch {
                                    try {
                                        if(step==2) {ShiftRepository.updateWorkProfile(ShiftRepository.activeWorkProfile.copy(name=name.trim()));ShiftRepository.notifyDataChanged()}
                                        LocalDatabaseStore.awaitCurrentSave()
                                        if(step==5)FirstRunStore.complete(context)else FirstRunStore.goTo(context,step+1)
                                    } catch(e:CancellationException){throw e}
                                    catch(e:Exception){feedback=ActionFeedback("Salvataggio locale non completato: riprova tra poco",true)}
                                    finally{finishing=false}
                                }
                            }
                        },enabled=!finishing&&(step!=2||name.isNotBlank()))
                        Text("Puoi cambiare queste scelte in qualsiasi momento",color=TextSecondary,fontSize=10.sp,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=8.dp,bottom=3.dp))
                    }
                }
                MagicalActionFeedbackHUD(feedback,{feedback=null},Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable private fun SetupTitle(title:String,description:String) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(top=12.dp,bottom=4.dp)) {
        Text(title,color=TextPrimary,fontSize=29.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.6).sp)
        Text(description,color=TextSecondary,fontSize=14.sp,lineHeight=21.sp)
    }
}
@Composable private fun SetupToggle(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,color=TextPrimary,fontSize=13.sp,modifier=Modifier.weight(1f));Switch(checked=value,onCheckedChange=onChange)
    }
}
@Composable private fun SetupLink(id:String,icon:String,title:String,description:String,onOpen:(String)->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ObsidianSurface).bouncyClickable {onOpen(id)}.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        SiteIcon(icon,null,Modifier.size(22.dp),BrandInk)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,color=TextPrimary,fontSize=14.sp,fontWeight=FontWeight.SemiBold);Text(description,color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)}
        SiteIcon("chevron-right",null,Modifier.size(16.dp),TextSecondary)
    }
}
@Composable private fun SetupPreview() {
    val examples=listOf("MAT" to 0xFFF2C46DL,"POM" to 0xFFEE9C9CL,"NOT" to 0xFFBAAEF6L,"RIP" to 0xFFAAB9CEL)
    CloudCard("Un’occhiata al tuo calendario") {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {examples.forEachIndexed { index,pair ->
            val colors=shiftColorStyle(Color(pair.second),TurniThemeState.dark,ObsidianSurface)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceContainer).padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${16+index}",fontSize=16.sp,color=TextPrimary,fontWeight=FontWeight.SemiBold)
                Text(pair.first,color=colors.codeInk,fontSize=13.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clip(RoundedCornerShape(8.dp)).background(colors.codeFill).padding(horizontal=9.dp,vertical=9.dp))
            }
        }}
        Text("Esempio illustrativo",fontSize=10.sp,color=TextSecondary)
    }
}

private val setupGroups=listOf(
    "Regole del tuo calendario" to listOf(
        listOf("holidays","calendar-heart","Festività","Scegli le ricorrenze valide per il tuo lavoro. Il bordo rosso le rende riconoscibili."),
        listOf("oncall","bell-ring","Reperibilità","Segna le date a mano oppure scegli regole basate su turni o periodi dell’anno."),
        listOf("rules","chart-no-axes-combined","Conteggio statistiche","Decidi come contare un turno sostitutivo rispetto a quello originale e le ferie nei festivi.")),
    "Attività e promemoria" to listOf(
        listOf("labels","tag","Etichette dei giorni","Crea attività come Formazione: nelle statistiche ritrovi il numero di giorni e le date."),
        listOf("alarms","alarm-clock","Sveglie dei turni","Scegli regole e orari. Android ti chiederà le autorizzazioni quando le attivi.")),
    "Copie e condivisione" to listOf(
        listOf("backup","database-backup","Backup e ripristino","Salva dati e impostazioni in un file o nel tuo Drive. Il backup automatico si attiva solo se lo scegli."),
        listOf("google","calendar-sync","Google Calendar","Copia i tuoi turni nel calendario Google selezionato. Ogni utente usa il proprio account."),
        listOf("pdf","file-text","Esporta e stampa PDF","Controlla l’anteprima e condividi un prospetto nel periodo e nel formato che preferisci."))
)
@Composable private fun SetupOptions(onOpen:(String)->Unit) {
    setupGroups.forEachIndexed { index,group ->
        var expanded by rememberSaveable {mutableStateOf(false)}
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ObsidianSurfaceContainer).bouncyClickable {expanded=!expanded}.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(group.first,color=TextPrimary,fontSize=14.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                SiteIcon(if(expanded)"chevron-up" else "chevron-down",null,Modifier.size(18.dp),BrandInk)
            }
            AnimatedVisibility(expanded,enter=expandVertically(spring(.9f,400f))+fadeIn(),exit=shrinkVertically()+fadeOut()) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){group.second.forEach {row->SetupLink(row[0],row[1],row[2],row[3],onOpen)}}
            }
        }
    }
}

@Composable internal fun QuickGuideScreen(onDismiss:()->Unit) {
    CloudFeatureScaffold("Guida rapida · beta",onDismiss,null,{}) {
        CloudCard("Impara senza modificare i tuoi dati") {Text("Scorri orizzontalmente per cambiare mese. Oggi ti riporta al mese attuale. Tocca un giorno per aprirlo; la X chiude il dettaglio e deseleziona la data.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)}
        GesturePractice()
        CloudCard("Modelli e rotazioni") {Text("Nell’orologio crei i modelli e le sequenze. La stella sceglie una rotazione predefinita. Nella matita scegli Rotazione: il primo giorno avvia l’anteprima, l’ultimo conferma l’intervallo. Annulla torna alla situazione precedente.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)}
        CloudCard("Dettagli e attività") {Text("La rotella di un turno raccoglie straordinario, trasferta e nota. Lo straordinario si calcola dall’orario di uscita. Note ed etichette riguarda la giornata: le etichette contano i giorni nelle statistiche e permettono di ritrovarli.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)}
        CloudCard("Statistiche e impostazioni") {Text("Mese, Anno e Personalizzato cambiano il periodo del resoconto. Nelle Impostazioni scegli festività, regole di conteggio, reperibilità, sveglie e backup. Il PDF si controlla in anteprima prima di esportarlo. L’importazione AI richiede sempre la tua revisione.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)}
    }
}
