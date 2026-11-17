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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    AnimatedContent(page,transitionSpec={fadeIn(spring(stiffness=420f)) + slideInHorizontally(spring(.95f,420f)){it/16} togetherWith fadeOut(tween(120)) using null}) { destination ->
        when(destination) {
            "restore" -> CloudBackupScreen(onDismiss=close,restoreOnly=true,onRestored=finish,accountEntry=true)
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
                    Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(step>0)Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(ObsidianSurface).bouncyClickable {FirstRunStore.goTo(context,step-1)},contentAlignment=Alignment.Center) {SiteIcon("arrow-left","Indietro",Modifier.size(22.dp))}
                        else SiteIcon("calendar",null,Modifier.size(28.dp),BrandInk)
                        Spacer(Modifier.width(12.dp))
                        Text("TurniSync",fontSize=18.sp,fontWeight=FontWeight.Bold,color=TextPrimary,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        if(step>0)Text("$step / 5",fontSize=12.sp,color=TextSecondary,maxLines=1,softWrap=false)
                    }
                    if(step>0)Row(Modifier.fillMaxWidth().padding(top=14.dp,bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        repeat(5) { index -> val color by animateColorAsState(if(index<step)ObsidianPrimary else ObsidianSurfaceContainer,spring(stiffness=350f))
                            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(3.dp)).background(color))
                        }
                    }
                    val scroll=rememberScrollState()
                    LaunchedEffect(step){scroll.scrollTo(0)}
                    Column(Modifier.weight(1f).verticalScroll(scroll),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        AnimatedContent(step,transitionSpec={val direction=if(targetState>initialState)1 else -1;fadeIn(spring(stiffness=380f))+slideInHorizontally(spring(.95f,380f)){direction*it/14} togetherWith fadeOut(tween(110)) using null}) { current ->
                            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                when(current) {
                                    0 -> {
                                        SetupTitle("I tuoi turni,\na colpo d’occhio.","Organizza turni, riposi e ferie in un solo calendario.")
                                        SetupPreview()
                                        Text("Puoi iniziare anche senza un account.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                                    }
                                    1 -> {
                                        SetupTitle("Fallo tuo","Scegli il tema e guarda subito il risultato. Potrai cambiarlo quando vuoi.")
                                        CloudCard("Il tuo tema") { PreferenceThemeSelector() }
                                        SetupPreview()
                                        CloudCard("Come leggi la settimana") {
                                            SetupToggle("Lunedì come primo giorno",ShiftRepository.appPreferences.weekStartMonday){ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(weekStartMonday=it);ShiftRepository.notifyDataChanged()}
                                            SetupToggle("Orari in formato 24 ore",ShiftRepository.appPreferences.timeFormat24h){ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(timeFormat24h=it);ShiftRepository.notifyDataChanged()}
                                        }
                                    }
                                    2 -> {
                                        SetupTitle("Da quale lavoro partiamo?","Scegli un nome riconoscibile. Più avanti potrai aggiungere altri lavori, con turni e regole indipendenti.")
                                        CloudCard("Nome del lavoro") {
                                            BasicTextField(name,{name=it.take(60)},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()}),textStyle=TextStyle(fontSize=18.sp,color=TextPrimary),cursorBrush=SolidColor(BrandInk),
                                                modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceContainer).padding(14.dp),decorationBox={field->if(name.isBlank())Text("Es. Il mio lavoro",color=TextSecondary);field()})
                                            Text("Scegli un nome riconoscibile anche nel selettore in alto sul calendario.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                                        }
                                        SetupLink("work","work","Aggiungi dettagli al lavoro","Facoltativo · personalizza “${name.trim().ifBlank { "questo lavoro" }}”: orari, riposo, icona e contratto."){if(name.isNotBlank()){ShiftRepository.updateWorkProfile(ShiftRepository.activeWorkProfile.copy(name=name.trim()));ShiftRepository.notifyDataChanged()};page=it}
                                    }
                                    3 -> {
                                        SetupTitle("I turni, una volta sola","Crea i modelli che usi ogni giorno: nome, colore e orario. Le rotazioni li ripetono nella sequenza che scegli.")
                                        CloudCard("${ShiftRepository.shiftDefinitions.count{it.workProfileId==ShiftRepository.activeWorkProfileId}} modelli pronti") {
                                            Text("La tua scelta non inserisce turni sul calendario: li applicherai tu ai giorni.",color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                                            if(ShiftRepository.shiftDefinitions.isEmpty())CloudButton("Parti da 5 esempi modificabili","plus",{
                                                if(ShiftRepository.shiftDefinitions.isEmpty())ShiftRepository.shiftDefinitions.addAll(starterShiftDefinitions(ShiftRepository.activeWorkProfileId));ShiftRepository.notifyDataChanged();feedback=ActionFeedback("Modelli di esempio aggiunti")
                                            })
                                            CloudButton(if(ShiftRepository.shiftDefinitions.isEmpty())"Crea i tuoi modelli" else "Controlla modelli e rotazioni","clock",{page="models"},secondary=true)
                                        }
                                        SetupLink("ai","scan-text","Hai già un prospetto?","Copia un prompt nella tua AI, allega il prospetto e incolla qui la risposta. Controlla e correggi ogni modello prima di salvarlo."){page=it}
                                    }
                                    4 -> {
                                        SetupTitle("Prova le funzioni di modifica","Tieni premuto per scegliere un intervallo.")
                                        GesturePractice()
                                    }
                                    else -> {
                                        SetupTitle("L’essenziale è pronto","Vuoi aggiungere qualcosa? Apri solo ciò che ti serve: tutte queste opzioni restano nelle Impostazioni.")
                                        SetupOptions {page=it}
                                        CloudCard("Da qui cominci tu") {Text("L’esempio non viene copiato: il tuo calendario parte vuoto. La matita inserisce i turni, l’orologio gestisce modelli e rotazioni. Le statistiche si aggiornano da sole.",color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)}
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if(step==0) {
                        Spacer(Modifier.height(10.dp))
                        CloudButton("Inizia","arrow-right",{FirstRunStore.goTo(context,1)})
                        Spacer(Modifier.height(8.dp))
                        CloudButton("Hai già un account? Accedi","users",{page="restore"},secondary=true)
                    }
                    if(step>0) {
                        Spacer(Modifier.height(10.dp))
                        CloudButton(if(finishing)"Salvataggio…" else if(step==5)"Apri il mio calendario" else "Continua",if(step==5)"check" else "arrow-right",{
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
    GesturePractice(showGuide=false)
}

private val setupGroups=listOf(
    "Regole del tuo calendario" to listOf(
        listOf("holidays","calendar-heart","Festività","Scegli le ricorrenze del tuo lavoro. Un piccolo contorno sulla data le distingue senza coprire i turni."),
        listOf("oncall","bell-ring","Reperibilità","Segna le date a mano oppure scegli regole basate su turni o periodi dell’anno."),
        listOf("rules","chart-no-axes-combined","Conteggio statistiche","La visibilità dell’ex turno non cambia i totali. Scegli se contare il turno visibile o l’originale, e le esclusioni nei festivi.")),
    "Attività e promemoria" to listOf(
        listOf("labels","tag","Etichette dei giorni","Crea attività come Formazione: nelle statistiche ritrovi il numero di giorni e le date."),
        listOf("alarms","alarm-clock","Sveglie dei turni","Scegli regole e orari. Android ti chiederà le autorizzazioni quando le attivi.")),
    "Copie e condivisione" to listOf(
        listOf("backup","database-backup","Backup e ripristino","Salva dati e impostazioni in un file o nel tuo Drive. Il backup automatico si attiva solo se lo scegli."),
        listOf("google","calendar-sync","Google Calendar","Copia i tuoi turni nel calendario Google selezionato. Ogni utente usa il proprio account."),
        listOf("pdf","file-text","Esporta e stampa PDF","Controlla l’anteprima e condividi un prospetto nel periodo e nel formato che preferisci."))
)
@Composable private fun SetupOptions(onOpen:(String)->Unit) {
    setupGroups.forEach { group ->
        var expanded by rememberSaveable(group.first) {mutableStateOf(false)}
        val chevronAngle by animateFloatAsState(if(expanded)180f else 0f,spring(.8f,380f))
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ObsidianSurfaceContainer).semantics {stateDescription=if(expanded)"Aperto" else "Chiuso"}.bouncyClickable {expanded=!expanded}.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(group.first,color=TextPrimary,fontSize=14.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                SiteIcon("chevron-down",null,Modifier.size(18.dp).rotate(chevronAngle),BrandInk)
            }
            AnimatedVisibility(expanded,enter=expandVertically(spring(.9f,400f))+fadeIn(),exit=shrinkVertically(spring(.95f,400f))+fadeOut()) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){group.second.forEach {row->SetupLink(row[0],row[1],row[2],row[3],onOpen)}}
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
