@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.animation.ExperimentalAnimationApi::class)
package com.turni.calendario.ui.screens

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.model.*
import com.turni.calendario.repository.*
import com.turni.calendario.sync.*
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SuperShiftImportScreen(onDismiss:()->Unit,onAssistedImport:()->Unit={}) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var feedback by remember { mutableStateOf<ActionFeedback?>(null) }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(0) }
    var requestPreview by remember { mutableStateOf(false) }
    var backupHelp by remember { mutableStateOf(false) }
    val access=rememberGoogleAccess { feedback=ActionFeedback(it,true) }
    var calendars by remember { mutableStateOf<List<ImportCalendar>>(emptyList()) }
    var owner by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ImportCalendar?>(null) }
    var preview by remember { mutableStateOf<ShiftImportPlan?>(null) }
    var source by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
    var categories by remember { mutableStateOf<Map<String,ShiftCategory>>(emptyMap()) }
    var fromText by remember { mutableStateOf(LocalDate.now().withDayOfYear(1).toString()) }
    var toText by remember { mutableStateOf(LocalDate.now().withDayOfYear(1).plusYears(1).minusDays(1).toString()) }
    val from=runCatching { LocalDate.parse(fromText) }.getOrNull()
    val until=runCatching { LocalDate.parse(toText) }.getOrNull()
    val validRange=from!=null&&until!=null&&until>=from&&java.time.temporal.ChronoUnit.DAYS.between(from,until)<=3660
    val email=ShiftRepository.googleAccountProfile?.email
    LaunchedEffect(email) { if(owner!=null&&owner!=email) { calendars=emptyList();selected=null;preview=null;owner=null } }
    fun close(){ if(busy||access.busy)return; if(preview!=null)preview=null else onDismiss() }
    BackHandler(onBack=::close)
    fun fail(error:Exception){feedback=ActionFeedback(error.message?:"Importazione non riuscita. Nessun dato modificato.",true)}
    fun prepare(plan:ShiftImportPlan,label:String) {
        preview=plan;source=label;categories=plan.definitions.associate { it.id to it.category };acknowledged=false
    }
    val document=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            val base=DatabaseSnapshot.capture();busy=true;operation="Lettura del calendario…"
            scope.launch {try {
                val events=withContext(Dispatchers.IO) {
                    val input=context.contentResolver.openInputStream(uri)?:error("File non accessibile.")
                    val text=input.use { stream -> val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true) {val n=stream.read(buffer);if(n<0)break;require(output.size()+n<=16*1024*1024){"Il file supera 16 MB."};output.write(buffer,0,n)};Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString() }
                    SuperShiftCalendarAdapter.readCalendar(text)
                }
                prepare(withContext(Dispatchers.Default){SuperShiftCalendarAdapter.plan(events,base)},"Calendario da file")
            }catch(e:CancellationException){throw e}catch(e:Exception){fail(e)}finally {busy=false;operation=""} }
        }
    }
    fun chooseGoogle() {
        access.authorize(GoogleFeature.CALENDAR_IMPORT) { token ->
            val account=ShiftRepository.googleAccountProfile?.email;busy=true;operation="Lettura dei calendari Google…"
            scope.launch {try {
                val available=withContext(Dispatchers.IO){GoogleCalendarRemote.importCalendars(token)}
                if(account==ShiftRepository.googleAccountProfile?.email) {calendars=available;owner=account;selected=null;if(available.isEmpty())feedback=ActionFeedback("Nessun calendario leggibile in questo account.",true)}
            }catch(e:CancellationException){throw e}catch(e:Exception){fail(e)}finally{busy=false;operation=""} }
        }
    }
    fun readGoogle() {
        val calendar=selected?:return;val first=from?:return;val last=until?:return;val expected=owner
        access.authorize(GoogleFeature.CALENDAR_IMPORT) { token ->
            if(expected==null||expected!=ShiftRepository.googleAccountProfile?.email) {feedback=ActionFeedback("L’account è cambiato: seleziona di nuovo il calendario.",true);return@authorize}
            val base=DatabaseSnapshot.capture();busy=true;operation="Preparazione anteprima…"
            scope.launch {try {
                val events=withContext(Dispatchers.IO){GoogleCalendarRemote.importEvents(token,calendar,first,last)}
                if(expected==ShiftRepository.googleAccountProfile?.email)prepare(withContext(Dispatchers.Default){SuperShiftCalendarAdapter.plan(events,base)},calendar.name)
            }catch(e:CancellationException){throw e}catch(e:Exception){fail(e)}finally{busy=false;operation=""} }
        }
    }
    fun confirm() {
        val plan=preview?:return
        if(LocalDatabaseStore.loadError!=null){feedback=ActionFeedback("Ripristina prima una copia valida dei dati locali.",true);return}
        val before=DatabaseSnapshot.capture()
        val imported=try {SuperShiftCalendarAdapter.merge(plan,categories,before)}catch(e:Exception){fail(e);return}
        busy=true;operation="Salvataggio dei turni…"
        scope.launch {try {
            // Keep an independent recovery copy, never overwrite the user's regular backup.
            withContext(Dispatchers.IO) {
                val folder=File(context.filesDir,"import_recovery").apply { check(exists()||mkdirs()){ "Impossibile preparare la copia di sicurezza." } }
                val temp=File(folder,"before-supershift.tmp")
                temp.outputStream().use { stream -> stream.write(GoogleDriveBackupHelper.exportDatabaseToJson(before).toByteArray(Charsets.UTF_8));stream.fd.sync() }
                DatabaseRestore.parse(temp.readText(Charsets.UTF_8),before)
                check(temp.renameTo(File(folder,"before-supershift-${System.currentTimeMillis()}.json"))){"Impossibile salvare la copia di sicurezza."}
            }
            check(DatabaseSnapshot.capture()==before){"I dati sono cambiati durante l’importazione. Controlla l’anteprima e riprova."}
            DatabaseRestore.apply(imported.snapshot)
            if(imported.importedCount>0)ShiftRepository.setActiveWorkProfile(SuperShiftCalendarAdapter.PROFILE_ID)
            preview=null;feedback=ActionFeedback("${imported.importedCount} turni importati · ${imported.duplicateCount} già presenti")
        }catch(e:CancellationException){throw e}catch(e:Exception){fail(e)}finally{busy=false;operation=""} }
    }
    fun openLink(url:String) {try{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}catch(_:Exception){feedback=ActionFeedback("Non è disponibile un’app per aprire il collegamento.",true)}}
    fun openSuperShift() {
        val intent=context.packageManager.getLaunchIntentForPackage("app.supershift")
        if(intent==null)feedback=ActionFeedback("SuperShift non risulta installato su questo dispositivo.",true)
        else try{context.startActivity(intent)}catch(_:Exception){feedback=ActionFeedback("Impossibile aprire SuperShift.",true)}
    }
    CloudFeatureScaffold("Trasferisci da SuperShift",::close,feedback,{feedback=null},scrollResetKey=Pair(preview!=null,method)) {
        AnimatedVisibility(busy||access.busy,enter=expandVertically(spring())+fadeIn(),exit=shrinkVertically()+fadeOut()) {
            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {CircularProgressIndicator(Modifier.size(22.dp),color=ObsidianPrimary,strokeWidth=2.dp);Text(operation.ifBlank { "Collegamento Google…" },fontSize=12.sp,color=TextSecondary)}
        }
        if(preview==null) {
            Text("Scegli il metodo disponibile nel tuo account",fontSize=13.sp,lineHeight=19.sp,color=TextSecondary)
            BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ObsidianSurfaceHigh).padding(4.dp)) {
                val width=maxWidth/3
                val offset by animateDpAsState(width*method,spring(dampingRatio=.88f,stiffness=350f))
                Box(Modifier.offset(x=offset).width(width).height(44.dp).clip(RoundedCornerShape(12.dp)).background(ObsidianPrimary))
                Row { listOf("Gratuita","Pro","File .ics").forEachIndexed { index,label ->
                    Box(Modifier.width(width).height(44.dp).bouncyClickable(!busy&&!access.busy){method=index},contentAlignment=Alignment.Center) {
                        Text(label,fontSize=13.sp,fontWeight=FontWeight.Medium,color=if(method==index)catalogInk(ObsidianPrimary)else TextPrimary)
                    }
                } }
            }
            AnimatedContent(method,transitionSpec={fadeIn()+slideInHorizontally(spring(stiffness=450f)){it/12} with fadeOut()}) { selectedMethod ->
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(selectedMethod==0) {
                CloudCard("Versione gratuita","La sincronizzazione dell’account conserva i dati dentro SuperShift. Non crea un file da selezionare in TurniSync.") {
                    Text("Turni, modelli, rotazioni e widget sono funzioni di base. Nei menu verificati manca un’esportazione completa gratuita: non c’è un file da cercare in Download.",fontSize=12.sp,lineHeight=18.sp,color=TextSecondary)
                    CloudButton("Apri SuperShift","calendar-days",{openSuperShift()},secondary=true)
                }
                CloudCard("Richiedi una copia dei dati","Chiedi a SuperShift se può fornirti i dati in un formato riutilizzabile. Disponibilità e formato devono essere confermati dalla loro assistenza.") {
                    Text("1 · In SuperShift apri Altro → Account e controlla l’indirizzo collegato.\n2 · Invia la richiesta usando quell’indirizzo.\n3 · Conserva la risposta e l’eventuale file ricevuto: la compatibilità va verificata prima del trasferimento.",fontSize=12.sp,lineHeight=20.sp,color=TextPrimary)
                    CloudButton("Prepara richiesta all’assistenza","message",{requestPreview=true},secondary=true)
                    Text("Il trasferimento completo automatico dalla versione gratuita non è ancora disponibile in TurniSync. Non inserire qui la password SuperShift.",fontSize=11.sp,lineHeight=17.sp,color=TextMuted)
                }
                CloudCard("Puoi iniziare dalle schermate","L’importazione assistita aiuta a ricreare modelli e rotazioni dalle informazioni visibili. Richiede il tuo controllo: non trasferisce automaticamente tutto lo storico o i dati non mostrati.") {
                    CloudButton("Importazione assistita","scan-text",onAssistedImport,secondary=true)
                    Text("L’eventuale servizio AI viene scelto e autorizzato nella schermata successiva. Nessun invio parte da qui.",fontSize=11.sp,lineHeight=17.sp,color=TextMuted)
                }
                CloudCard("Il backup è nel tuo account Google?") {
                    TextButton(onClick={backupHelp=!backupHelp}) {Text(if(backupHelp)"Chiudi le indicazioni" else "Come riconoscere il backup",color=ObsidianPrimary)}
                    AnimatedVisibility(backupHelp,enter=expandVertically(spring())+fadeIn(),exit=shrinkVertically()+fadeOut()) {
                        Text("Se è il backup Android/Google One, apri le impostazioni del telefono → Google → Backup → Dettagli backup: puoi controllare se SuperShift è incluso. I nomi possono variare. Quel backup serve al ripristino dell’app, non è un file calendario per TurniSync. Se vedi invece SuperShift Cloud dentro SuperShift, l’accesso Google identifica l’account SuperShift; non crea un file .ics su Drive.",fontSize=12.sp,lineHeight=19.sp,color=TextSecondary)
                    }
                    TextButton(onClick={openLink("https://support.google.com/android/answer/2819582?hl=it")}) {Text("Guida ufficiale Google",color=ObsidianPrimary,fontSize=12.sp)}
                }
            }
            if(selectedMethod==1) {
                CloudCard("Hai già SuperShift Pro?","L’esportazione verso il calendario Android/Google, il PDF, gli eventi dei calendari esterni e la rimozione degli annunci sono funzioni Pro nei menu verificati.") {
                    Text("Per trasferire i turni: in SuperShift apri Altro → Esportazione calendario e seleziona un calendario Google dedicato. Qui scegli lo stesso account e calendario. La sincronizzazione SuperShift Cloud, da sola, non esegue questa esportazione.",fontSize=12.sp,lineHeight=19.sp,color=TextSecondary)
                    Text(SuperShiftCalendarAdapter.limitation,fontSize=11.sp,lineHeight=17.sp,color=TextMuted)
                }
            GoogleAccountCard(access,{feedback=it},enabled=!busy,feature=GoogleFeature.CALENDAR_IMPORT,explanation="Usa l’account dove hai esportato i turni. L’accesso al calendario è in sola lettura.")
            CloudCard("Da Google Calendar","Scegli il calendario dove SuperShift esporta i tuoi turni. Verranno letti solo il calendario e il periodo che selezioni.") {
                CloudButton("Scegli calendario Google","calendar-days",{chooseGoogle()},enabled=!busy&&!access.busy,secondary=true)
                calendars.forEach { calendar ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if(selected?.id==calendar.id)ObsidianPrimary.copy(alpha=.12f)else ObsidianSurfaceHigh).bouncyClickable(!busy&&!access.busy){selected=calendar}.padding(horizontal=8.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected?.id==calendar.id,{selected=calendar},enabled=!busy&&!access.busy)
                        Text(calendar.name,fontSize=13.sp,color=TextPrimary,modifier=Modifier.weight(1f))
                    }
                }
                AnimatedVisibility(selected!=null,enter=expandVertically(spring())+fadeIn(),exit=shrinkVertically()+fadeOut()) {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(fromText,{fromText=it},label={Text("Dal · AAAA-MM-GG")},singleLine=true,enabled=!busy,modifier=Modifier.weight(1f))
                            OutlinedTextField(toText,{toText=it},label={Text("Al · AAAA-MM-GG")},singleLine=true,enabled=!busy,modifier=Modifier.weight(1f))
                        }
                        if(!validRange)Text("Inserisci date valide, in ordine, entro dieci anni.",fontSize=11.sp,color=ColorNegative)
                        CloudButton("Mostra anteprima","search",{readGoogle()},enabled=validRange&&!busy&&!access.busy)
                    }
                }
            }
            }
            if(selectedMethod==2)CloudCard("Hai già un calendario .ics?","Questo è un importatore di calendari standard. Non è un’esportazione gratuita del backup SuperShift.") {
                Text("Se possiedi già un file .ics, selezionalo dal telefono o da Drive. Non cercare un comando “Esporta file” in SuperShift: non è presente nei menu verificati. Il backup Android e SuperShift Cloud non sono file .ics.",fontSize=12.sp,lineHeight=19.sp,color=TextSecondary)
                CloudButton("Seleziona calendario .ics","folder-open",{document.launch(arrayOf("*/*"))},enabled=!busy&&!access.busy,secondary=true)
            }
            TextButton(onClick={openLink("https://play.google.com/store/apps/details?id=app.supershift")}) {Text("Funzioni e piani su Google Play",color=ObsidianPrimary,fontSize=12.sp)}
            }
            }

        } else {
            val plan=preview!!
            CloudCard("Controlla prima di importare",source) {
                Text("${plan.assignments.size} nuovi turni · ${plan.definitions.size} modelli · ${plan.duplicateCount} già presenti",fontSize=16.sp,lineHeight=23.sp,color=TextPrimary,fontWeight=FontWeight.SemiBold)
                val formatter=DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ITALIAN)
                if(plan.assignments.isNotEmpty())Text("${plan.assignments.minOf { it.date }.format(formatter)} — ${plan.assignments.maxOf { it.date }.format(formatter)}",fontSize=12.sp,color=TextSecondary)
                Text(SuperShiftCalendarAdapter.limitation,fontSize=12.sp,lineHeight=19.sp,color=TextSecondary)
                Text("I turni già importati vengono conservati, anche se li hai modificati manualmente. Questa è un’importazione, non una sincronizzazione continua.",fontSize=11.sp,lineHeight=17.sp,color=TextMuted)
            }
            if(plan.definitions.isNotEmpty())CloudCard("Modelli di turno","Nessuna categoria viene dedotta dal nome. Scegli come conteggiare ciascun modello; potrai modificarlo anche in seguito.") {
                plan.definitions.forEach { model ->
                    Text(model.name,fontSize=14.sp,color=TextPrimary,fontWeight=FontWeight.SemiBold)
                    Text(if(model.isAllDay)"Giornata intera" else "${model.startTime} — ${model.endTime}",fontSize=11.sp,color=TextSecondary)
                    val exists=ShiftRepository.shiftDefinitions.any { it.id==model.id }
                    if(exists)Text("Modello già importato: le tue modifiche vengono conservate.",fontSize=11.sp,color=TextMuted)
                    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        ShiftCategory.values().forEach { category ->
                            val active=categories[model.id]==category
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if(active)ObsidianPrimary else ObsidianSurfaceHigh).bouncyClickable(!busy){categories=categories+(model.id to category)}.padding(vertical=10.dp),contentAlignment=Alignment.Center) {
                                Text(when(category){ShiftCategory.LAVORO->"Lavoro";ShiftCategory.RIPOSO->"Riposo";ShiftCategory.ASSENZA->"Assenza";ShiftCategory.ALTRO->"Altro"},fontSize=10.sp,color=if(active)catalogInk(ObsidianPrimary)else TextPrimary)
                            }
                        }
                    }
                    Divider(color=ObsidianBorderSubtle)
                }
            }
            CloudCard("Anteprima dei turni") {
                val models=plan.definitions.associateBy { it.id }
                plan.assignments.sortedBy { it.date }.take(12).forEach { shift ->
                    val model=models.getValue(shift.shiftDefId)
                    Column { Text("${shift.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} · ${model.name}",fontSize=12.sp,color=TextPrimary)
                        Text(if(model.isAllDay)"Giornata intera" else "${shift.customStartTime?:model.startTime} — ${shift.customEndTime?:model.endTime}",fontSize=11.sp,color=TextSecondary)
                        if(shift.notes.isNotBlank())Text(shift.notes,fontSize=11.sp,color=TextSecondary,maxLines=3)
                    }
                }
                if(plan.assignments.size>12)Text("Altri ${plan.assignments.size-12} turni verranno importati nello stesso modo.",fontSize=11.sp,color=TextMuted)
                if(plan.assignments.isEmpty()) {
                    Text("Tutti questi turni sono già stati importati. Non occorre aggiungerli di nuovo.",fontSize=13.sp,lineHeight=19.sp,color=TextPrimary)
                    CloudButton("Fine","check",{preview=null;onDismiss()},enabled=!busy)
                } else {
                Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(acknowledged,{acknowledged=it},enabled=!busy);Text("Ho controllato l’anteprima e i dati disponibili nell’esportazione.",fontSize=12.sp,lineHeight=18.sp,color=TextPrimary,modifier=Modifier.weight(1f))}
                CloudButton("Importa ${plan.assignments.size} turni","check",::confirm,enabled=acknowledged&&!busy)
                }
            }
        }
    }
    if(requestPreview)AlertDialog(onDismissRequest={requestPreview=false},containerColor=ObsidianSurface,shape=RoundedCornerShape(22.dp),
        title={Text("Richiesta di copia dei dati",color=TextPrimary,fontSize=20.sp)},
        text={Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("A: support@supershift.app",fontSize=12.sp,color=TextSecondary)
            Text(SuperShiftMigrationHelp.requestText,fontSize=12.sp,lineHeight=18.sp,color=TextPrimary)
            Text("La richiesta si apre come bozza. Controllala e scegli l’indirizzo del tuo account SuperShift prima di inviarla. La disponibilità della copia non è garantita.",fontSize=11.sp,lineHeight=17.sp,color=TextMuted)
        }},
        confirmButton={TextButton(onClick={
            val uri=Uri.parse("mailto:support@supershift.app?subject="+Uri.encode(SuperShiftMigrationHelp.requestSubject)+"&body="+Uri.encode(SuperShiftMigrationHelp.requestText))
            try{context.startActivity(Intent(Intent.ACTION_SENDTO,uri));requestPreview=false}catch(_:Exception){feedback=ActionFeedback("Nessuna app email disponibile. Puoi copiare la richiesta.",true)}
        }){Text("Apri email",color=ObsidianPrimary)}},
        dismissButton={TextButton(onClick={
            val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(SuperShiftMigrationHelp.requestSubject,SuperShiftMigrationHelp.requestText))
            requestPreview=false;feedback=ActionFeedback("Richiesta copiata")
        }){Text("Copia testo",color=TextSecondary)}})

}
