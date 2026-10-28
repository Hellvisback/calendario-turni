@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.turni.calendario.data.*
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.*
import java.util.UUID

/** Document -> review drafts -> explicit additive catalogue save. No automatic calendar changes. */
@Composable
fun GeminiShiftImportScreen(onDismiss:()->Unit,onNavigateToTurni:((ShiftRotation?)->Unit)?=null){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var name by remember{mutableStateOf(SecurePreferencesManager.getUserName(context).orEmpty())}
    var document by remember{mutableStateOf<AiRosterDocument?>(null)}
    var draft by remember{mutableStateOf<AiRosterDraft?>(null)}
    var selectedCycle by remember{mutableStateOf<String?>(null)}
    var confirmed by remember{mutableStateOf(false)}
    var cycleConfirmed by remember{mutableStateOf(false)}
    var makeDefault by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var operation by remember{mutableStateOf<Job?>(null)}
    var message by remember{mutableStateOf<ActionFeedback?>(null)}
    var pasted by remember{mutableStateOf("")}
    var editShift by remember{mutableStateOf<String?>(null)}
    var editCycle by remember{mutableStateOf<String?>(null)}
    var showSource by remember{mutableStateOf(false)}
    var saved by remember{mutableStateOf<AiSavedRoster?>(null)}
    var discard by remember{mutableStateOf(false)}
    val profile=ShiftRepository.activeWorkProfileId
    val prompt=remember(name){AiRosterImport.prompt(name)}
    fun fail(error:Exception){
        val detail=when(error){
            is org.json.JSONException->"La risposta non è un JSON completo. Copia tutto il risultato oppure chiedi all’AI di rigenerarlo con le istruzioni."
            is java.nio.charset.CharacterCodingException->"Il file deve contenere il risultato JSON in formato UTF-8. Prova a incollare direttamente la risposta."
            else->error.message?:"Operazione non riuscita. Riprova."
        }
        message=ActionFeedback(detail,true)
    }
    fun accept(value:AiRosterDraft){draft=value;selectedCycle=value.suggestedCycleId;confirmed=false;cycleConfirmed=false;makeDefault=false;saved=null}
    fun cancel(){operation?.cancel();operation=null;busy=false}
    fun back(){when{busy->cancel();draft!=null&&saved==null->discard=true;else->onDismiss()}}
    DisposableEffect(Unit){onDispose{operation?.cancel()}}
    BackHandler{back()}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){
        operation=scope.launch {busy=true
            try{document=withContext(Dispatchers.IO){AiRosterDocuments.read(context,uri)}}catch(e:Exception){if(e is CancellationException)throw e;fail(e)}finally{busy=false}
        }
    }}
    fun parsePasted(){SecurePreferencesManager.saveUserName(context,name.trim());operation=scope.launch {busy=true
        try{val plan=withContext(Dispatchers.Default){AiRosterImport.parse(pasted,name,profile)};accept(plan)}catch(e:Exception){if(e is CancellationException)throw e;fail(e)}finally{busy=false}
    }}
    val jsonPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null){
        operation=scope.launch{busy=true
            try{
                val text=withContext(Dispatchers.IO){
                    context.contentResolver.openInputStream(uri)?.use{Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(AiRosterDocuments.readBounded(it))).toString()}?:throw IllegalArgumentException("Impossibile leggere il file.")
                }
                val plan=withContext(Dispatchers.Default){AiRosterImport.parse(text,name,profile)}
                accept(plan)
            }catch(e:Exception){if(e is CancellationException)throw e;fail(e)}finally{busy=false}
        }
    }}
    fun openAi(url:String){try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(e:Exception){fail(e)}}
    val current=draft
    Box(Modifier.fillMaxSize().background(ObsidianBackground)){
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()){
            Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                CatalogIconAction("arrow-left","Indietro",{back()},Modifier.size(40.dp))
                Column(Modifier.weight(1f)){
                    Text(if(saved!=null)"Catalogo aggiornato" else if(current==null)"Importazione con AI" else "Controlla i turni",fontSize=23.sp,fontWeight=FontWeight.Bold,color=TextPrimary)
                    Text(if(current==null)"Nome e documento · AI · Revisione" else "Modifiche in bozza, prima del salvataggio",fontSize=11.sp,color=TextSecondary,modifier=Modifier.padding(top=3.dp))
                }
            }
            AnimatedContent(if(saved!=null)2 else if(current!=null)1 else 0,modifier=Modifier.weight(1f),transitionSpec={
                (fadeIn(tween(260))+slideInHorizontally(spring(1f,440f)){it/10}) with (fadeOut(tween(160))+slideOutHorizontally(tween(220)){-it/12})
            }){step->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=14.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    if(step==0){
                        CatalogGroup("1 · Il tuo prospetto"){
                            Text("Scrivi il nome come compare nel prospetto. Allegalo poi alla tua AI insieme alle istruzioni che copierai qui.",fontSize=13.sp,lineHeight=19.sp,color=TextSecondary)
                            ImportField("Nome e cognome nel documento",name,{name=it.take(100)},enabled=!busy)
                            CatalogButton(if(document==null)"Scegli prospetto · facoltativo" else "Cambia prospetto",{picker.launch(arrayOf("application/pdf","image/*"))},Modifier.fillMaxWidth(),enabled=!busy,icon="upload")
                            document?.let{file->
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                                    SiteIcon("file-text",null,Modifier.size(24.dp),ObsidianPrimary)
                                    Column(Modifier.weight(1f)){Text(file.name,fontSize=13.sp,color=TextPrimary,maxLines=2,overflow=TextOverflow.Ellipsis);Text("${file.pages} ${if(file.pages==1)"pagina" else "pagine"} · documento completo",fontSize=11.sp,color=TextSecondary)}
                                }
                            }
                        }
                        CatalogGroup("2 · Copia e invia alla tua AI"){
                            Text("Copia le istruzioni e inviale alla tua AI insieme al PDF originale o alle immagini di tutte le pagine.",fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)
                            CatalogButton("Copia istruzioni",{
                                SecurePreferencesManager.saveUserName(context,name.trim())
                                (context.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Istruzioni TurniSync",prompt));message=ActionFeedback("Istruzioni copiate · allega anche il prospetto")
                            },Modifier.fillMaxWidth(),primary=true,enabled=name.isNotBlank()&&!busy,icon="copy")
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                CatalogButton("ChatGPT",{openAi("https://chatgpt.com/")},Modifier.weight(1f),enabled=!busy)
                                CatalogButton("Gemini",{openAi("https://gemini.google.com/")},Modifier.weight(1f),enabled=!busy)
                                CatalogButton("Claude",{openAi("https://claude.ai/")},Modifier.weight(1f),enabled=!busy)
                            }
                            Text("Apri il servizio che preferisci e allega il prospetto: questi pulsanti non inviano il file automaticamente.",fontSize=11.sp,lineHeight=16.sp,color=TextSecondary)
                        }
                        CatalogGroup("3 · Riporta il risultato"){
                            Text("Incolla la risposta completa dell’AI oppure apri il suo file JSON. La controllerai prima di salvare.",fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)
                            OutlinedTextField(pasted,{pasted=it.take(2_000_001)},label={Text("Risposta dell’AI")},modifier=Modifier.fillMaxWidth().heightIn(min=120.dp,max=210.dp),maxLines=7)
                            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                                CatalogButton("Incolla",{
                                    val value=(context.getSystemService(Context.CLIPBOARD_SERVICE)as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    if(value.length>2_000_000)message=ActionFeedback("Risposta troppo grande",true)else pasted=value
                                },Modifier.weight(1f),enabled=!busy,icon="copy")
                                CatalogButton("Apri JSON",{jsonPicker.launch(arrayOf("application/json","text/plain","application/octet-stream"))},Modifier.weight(1f),enabled=!busy&&name.isNotBlank(),icon="folder")
                            }
                            CatalogButton("Controlla risultato",{parsePasted()},Modifier.fillMaxWidth(),primary=true,enabled=!busy&&pasted.isNotBlank()&&name.isNotBlank(),icon="check")
                        }

                    }else if(step==1&&current!=null){
                        CatalogGroup("Proposta estratta"){
                            Text("${current.shifts.size} modelli · ${current.cycles.size} ${if(current.cycles.size==1)"sequenza" else "sequenze"}",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=TextPrimary)
                            if(current.period.isNotBlank())Text(current.period,fontSize=11.sp,color=TextSecondary)
                            Text("Salvi nel lavoro ${ShiftRepository.workProfiles.firstOrNull{it.id==current.profileId}?.name.orEmpty()}. Puoi correggere nomi, sigle, orari, colori e ogni giorno del ciclo. Nessun turno sarà applicato al calendario.",fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)
                            if(current.warnings.isNotEmpty()){
                                CatalogButton(if(showSource)"Nascondi osservazioni" else "Leggi ${current.warnings.size} osservazioni",{showSource=!showSource},Modifier.fillMaxWidth(),icon="message")
                                AnimatedVisibility(showSource,enter=expandVertically()+fadeIn(),exit=shrinkVertically()+fadeOut()){
                                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){current.warnings.forEach{Text("• $it",fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)}}
                                }
                            }
                        }
                        CatalogGroup("Modelli dei turni"){
                            current.shifts.forEach{row->
                                Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(row.definition.color),contentAlignment=Alignment.Center){Text(row.definition.shortCode,fontWeight=FontWeight.Bold,fontSize=12.sp,color=catalogInk(row.definition.color))}
                                    Column(Modifier.weight(1f)){
                                        Text(row.definition.name,fontWeight=FontWeight.SemiBold,fontSize=14.sp,color=TextPrimary)
                                        Text(if(row.needsReview)"Da completare: apri e verifica" else if(row.definition.isAllDay)"Giornata intera · ${row.definition.category.name.lowercase().replaceFirstChar{it.titlecase()}}" else "${row.definition.startTime} – ${row.definition.endTime}",fontSize=11.sp,color=if(row.needsReview)ObsidianPrimary else TextSecondary)
                                        if(row.evidence.isNotBlank())Text(row.evidence,fontSize=10.sp,color=TextMuted,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    }
                                    CatalogIconAction("pencil","Modifica ${row.definition.name}",{editShift=row.definition.id},Modifier.size(38.dp))
                                    CatalogIconAction("trash","Rimuovi ${row.definition.name}",{draft=current.copy(shifts=current.shifts.filterNot{it.definition.id==row.definition.id});confirmed=false},Modifier.size(36.dp))
                                }
                            }
                            CatalogButton("Aggiungi modello mancante",{
                                val id="ai-draft-"+UUID.randomUUID()
                                val row=AiReviewShift("manual-"+id,ShiftDefinition(id,"","TUR",workProfileId=current.profileId),true,"")
                                draft=current.copy(shifts=current.shifts+row);confirmed=false;editShift=id
                            },Modifier.fillMaxWidth(),enabled=current.shifts.size<150,icon="plus")
                        }
                        CatalogGroup("La tua rotazione"){
                            Text("Scegli una sequenza. Se il nome non è stato trovato con certezza, nessuna viene selezionata automaticamente.",fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)
                            Row(Modifier.fillMaxWidth().clickable{selectedCycle=null;cycleConfirmed=false},verticalAlignment=Alignment.CenterVertically){RadioButton(selectedCycle==null,{selectedCycle=null;cycleConfirmed=false});Text("Salva soltanto i modelli",fontSize=13.sp,color=TextPrimary)}
                            current.cycles.forEach{cycle->
                                val selected=selectedCycle==cycle.rotation.id
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ObsidianSurfaceContainer).border(if(selected)2.dp else 0.dp,if(selected)ObsidianPrimary else Color.Transparent,RoundedCornerShape(14.dp)).padding(9.dp)){
                                    Row(verticalAlignment=Alignment.CenterVertically){RadioButton(selected,{selectedCycle=cycle.rotation.id;cycleConfirmed=false});Column(Modifier.weight(1f)){Text(cycle.rotation.name,fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=TextPrimary);Text("${cycle.rotation.daySequence.size} giorni · "+when(cycle.basis){"explicit"->"ciclo indicato";"repeated"->"ripetizione rilevata";else->"sequenza osservata, da confermare"},fontSize=11.sp,color=TextSecondary)};CatalogIconAction("pencil","Modifica ${cycle.rotation.name}",{editCycle=cycle.rotation.id},Modifier.size(38.dp))}
                                    if(selected&&cycle.evidence.isNotBlank())Text(cycle.evidence,fontSize=11.sp,lineHeight=16.sp,color=TextSecondary)
                                    if(selected)cycle.rotation.daySequence.chunked(7).forEach{week->Row(Modifier.fillMaxWidth().padding(top=5.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){
                                        week.forEach{id->val def=current.shifts.firstOrNull{it.definition.id==id}?.definition
                                            Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(6.dp)).background(def?.color?:ObsidianSurface),contentAlignment=Alignment.Center){Text(def?.shortCode?:"?",color=if(def!=null)catalogInk(def.color)else TextPrimary,fontSize=9.sp,fontWeight=FontWeight.Bold)}
                                        }
                                        repeat(7-week.size){Spacer(Modifier.weight(1f))}
                                    }}
                                }
                            }
                            CatalogButton("Crea una sequenza da correggere",{
                                val id="ai-cycle-"+UUID.randomUUID()
                                val row=AiReviewCycle(ShiftRotation(id,"Nuova rotazione",7,List(7){""}),"observed","Sequenza manuale da completare",emptyList())
                                draft=current.copy(cycles=current.cycles+row);selectedCycle=id;cycleConfirmed=false;editCycle=id
                            },Modifier.fillMaxWidth(),enabled=current.cycles.size<50,icon="plus")
                            if(selectedCycle!=null){
                                CatalogToggle("Ho verificato la sequenza e voglio usarla come ciclo",cycleConfirmed){cycleConfirmed=it}
                                CatalogToggle("Imposta come rotazione predefinita",makeDefault){makeDefault=it}
                            }
                        }
                        CatalogGroup("Prima di salvare"){
                            CatalogToggle("Ho verificato modelli, orari e osservazioni",confirmed){confirmed=it}
                            val problems=AiRosterImport.validate(current,selectedCycle,cycleConfirmed)
                            if(problems.isNotEmpty())Text(problems.joinToString("\n"),fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)
                            Text("I modelli esistenti rimangono invariati. Le corrispondenze esatte vengono riutilizzate; eventuali varianti vengono aggiunte al catalogo.",fontSize=11.sp,lineHeight=16.sp,color=TextSecondary)
                        }
                    }else if(step==2){
                        CatalogGroup("Salvataggio completato"){
                            SiteIcon("check",null,Modifier.size(34.dp),ObsidianPrimary)
                            Text("${saved?.shifts?:0} nuovi modelli · ${saved?.cycles?:0} rotazioni salvate",fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=TextPrimary)
                            Text("Li trovi in Modelli e turni. Puoi modificarli ancora da lì e applicare la rotazione dal calendario.",fontSize=13.sp,lineHeight=19.sp,color=TextSecondary)
                            CatalogButton("Apri Modelli e turni",{onNavigateToTurni?.invoke(null)?:onDismiss()},Modifier.fillMaxWidth(),primary=true,icon="clock")
                        }
                    }
                }
            }
            if(current!=null&&saved==null){
                val valid=confirmed&&AiRosterImport.validate(current,selectedCycle,cycleConfirmed).isEmpty()
                CatalogButton("Salva nel catalogo",{
                    try{saved=AiRosterImport.save(current,selectedCycle,cycleConfirmed,makeDefault);message=ActionFeedback("Catalogo aggiornato")}catch(e:Exception){fail(e)}
                },Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),primary=true,enabled=valid&&!busy,icon="check")
            }
        }
        if(busy)Box(Modifier.fillMaxSize().background(ObsidianBackground.copy(alpha=.96f)),contentAlignment=Alignment.Center){
            Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)){
                CircularProgressIndicator(color=ObsidianPrimary)
                Text("Lettura del risultato…",fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=TextPrimary)
                Text("Controlliamo il formato sul dispositivo. Il calendario resta invariato.",fontSize=13.sp,color=TextSecondary)
                CatalogButton("Annulla",{cancel()},icon="x")
            }
        }
        MagicalActionFeedbackHUD(message,onDismiss={message=null},modifier=Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }
    editShift?.let{id->current?.shifts?.firstOrNull{it.definition.id==id}?.let{row->
        ShiftEditorScreen(row.definition,{editShift=null},{updated->draft=current.copy(shifts=current.shifts.map{if(it.definition.id==id)it.copy(definition=updated,needsReview=false)else it});confirmed=false;editShift=null},{editShift=null},allowWorkSelection=false)
    }}
    editCycle?.let{id->current?.cycles?.firstOrNull{it.rotation.id==id}?.let{row->
        RotationMatrixEditorScreen(row.rotation,{editCycle=null},{updated->draft=current.copy(cycles=current.cycles.map{if(it.rotation.id==id)it.copy(rotation=updated)else it});cycleConfirmed=false;confirmed=false;editCycle=null},{},availableDefinitions=current.shifts.map{it.definition},allowDefault=false)
    }}
    if(discard) AppConfirmationDialog(
        title="Scartare la revisione?",
        message="Le modifiche sono ancora in bozza. Se torni al documento, perderai questa revisione. I turni già salvati nel catalogo restano invariati.",
        confirmLabel="Scarta e torna al documento",dismissLabel="Continua revisione",
        onDismiss={discard=false},onConfirm={discard=false;draft=null;confirmed=false})

}

@Composable
private fun ImportField(label:String,value:String,change:(String)->Unit,enabled:Boolean=true,secret:Boolean=false){
    OutlinedTextField(value,change,label={Text(label)},singleLine=true,enabled=enabled,visualTransformation=if(secret)PasswordVisualTransformation()else androidx.compose.ui.text.input.VisualTransformation.None,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp))
}
