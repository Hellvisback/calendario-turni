@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.bouncyClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.repository.*
import com.turni.calendario.sync.*
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class BackupRestorePreview(val data:DatabaseSnapshot,val source:String)

@Composable
fun CloudBackupScreen(onDismiss:()->Unit,onImportSuperShift:()->Unit={}){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var feedback by remember{mutableStateOf<ActionFeedback?>(null)}
    var busy by remember{mutableStateOf(false)}
    val access=rememberGoogleAccess{feedback=ActionFeedback(it,true)}
    var remoteInfo by remember{mutableStateOf<String?>(null)}
    var localInfo by remember{mutableStateOf(GoogleDriveBackupHelper.getLocalBackupInfo(context))}
    var preview by remember{mutableStateOf<BackupRestorePreview?>(null)}
    var exportSnapshot by remember{mutableStateOf<DatabaseSnapshot?>(null)}
    val importRecovery=remember { File(context.filesDir,"import_recovery").listFiles()?.filter { it.name.startsWith("before-supershift-")&&it.extension=="json" }?.maxByOrNull { it.lastModified() } }
    val email=ShiftRepository.googleAccountProfile?.email
    val automatic by DriveAutomaticBackup.statusFlow.collectAsState()
    var otherCopies by remember{mutableStateOf(false)}
    fun refreshAutomatic(){DriveAutomaticBackup.refresh(context)}
    LaunchedEffect(email){
        remoteInfo=null
        refreshAutomatic()
    }
    fun failed(error:Exception){feedback=ActionFeedback(error.message?:"Operazione non riuscita. Riprova.",true)}
    fun prepareRestore(source:String,reader:()->String){
        val previous=DatabaseSnapshot.capture();busy=true
        scope.launch{
            try{val parsed=withContext(Dispatchers.IO){DatabaseRestore.parse(reader(),previous)};preview=BackupRestorePreview(parsed,source)}
            catch(e:CancellationException){throw e}catch(e:Exception){failed(e)}finally{busy=false}
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null){val snapshot=exportSnapshot?:DatabaseSnapshot.capture();busy=true
            scope.launch{try{withContext(Dispatchers.IO){val json=GoogleDriveBackupHelper.exportDatabaseToJson(snapshot);val stream=context.contentResolver.openOutputStream(uri)?:error("File non accessibile.");stream.use{it.write(json.toByteArray(Charsets.UTF_8))}};feedback=ActionFeedback("File backup esportato")}
                catch(e:CancellationException){throw e}catch(e:Exception){failed(e)}finally{busy=false;exportSnapshot=null}}
        }else exportSnapshot=null
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)prepareRestore("File selezionato"){readBackupDocument(context,uri)}}
    val enabled=!busy&&!access.busy
    fun saveRemote(token:String,enableAutomatic:Boolean=false){
        if(LocalDatabaseStore.loadError!=null){feedback=ActionFeedback("Dati locali non leggibili: ripristina una copia valida prima di salvare su Drive.",true);return}
        val owner=ShiftRepository.googleAccountProfile?.email?:return
        if(enableAutomatic){DriveAutomaticBackup.configure(context,true,automatic.intervalHours,owner);refreshAutomatic()}
        val snapshot=DatabaseSnapshot.capture();busy=true
        scope.launch{try{
            val saved=withContext(Dispatchers.IO){GoogleDriveRemote.upload(token,GoogleDriveBackupHelper.exportDatabaseToJson(snapshot))}
            if(ShiftRepository.googleAccountProfile?.email==owner){
                DriveAutomaticBackup.recordManualSuccess(context,owner,saved.modifiedTime)
                refreshAutomatic();remoteInfo=formatBackupTime(saved.modifiedTime)
                feedback=ActionFeedback(if(enableAutomatic)"Backup salvato · automatico attivato" else "Backup salvato su Google Drive")
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){failed(e)}finally{busy=false}}
    }
    fun enableAutomatic(hours:Int){
        access.authorize(GoogleFeature.DRIVE){
            val owner=ShiftRepository.googleAccountProfile?.email?:return@authorize
            DriveAutomaticBackup.configure(context,true,hours,owner);refreshAutomatic()
            DriveAutomaticBackup.runNow(context)
            feedback=ActionFeedback("Backup automatico attivato")
        }
    }
    CloudFeatureScaffold("Backup e ripristino",onDismiss,feedback,{feedback=null}){
        GoogleAccountCard(access,{feedback=it},enabled=!busy,feature=GoogleFeature.DRIVE,
            explanation="Accedi con il tuo account per salvare una copia privata su Drive e attivare il backup giornaliero.",
            onConnected={token->saveRemote(token,true)})
        CloudCard("Backup su Google Drive"){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f)){
                    Text("Salvataggio automatico",color=TextPrimary,fontSize=14.sp,fontWeight=FontWeight.Medium)
                    Text(if(automatic.running)"Salvataggio in corso…" else if(automatic.enabled)"In background, quando c’è connessione" else "Attivalo per proteggere le modifiche",color=TextSecondary,fontSize=11.sp,lineHeight=16.sp)
                }
                Switch(checked=automatic.enabled,enabled=enabled,onCheckedChange={on->
                    if(on)enableAutomatic(automatic.intervalHours) else {DriveAutomaticBackup.disable(context);refreshAutomatic();feedback=ActionFeedback("Backup automatico disattivato")}
                })
            }
            AnimatedVisibility(automatic.enabled,enter=expandVertically(spring(stiffness=450f))+fadeIn(),exit=shrinkVertically(spring(stiffness=550f))+fadeOut()){
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text("Ogni quanto salvare",color=TextSecondary,fontSize=12.sp)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        listOf(6,12,24,168).forEach{hours->
                            val selected=automatic.intervalHours==hours
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(selected)ObsidianPrimary else ObsidianSurfaceHigh)
                                .bouncyClickable(enabled){enableAutomatic(hours)}.padding(vertical=10.dp),contentAlignment=Alignment.Center){
                                Text(when(hours){24->"1 giorno";168->"7 giorni";else->"${hours}h"},fontSize=11.sp,fontWeight=FontWeight.Medium,color=if(selected)catalogInk(ObsidianPrimary)else TextPrimary)
                            }
                        }
                    }
                }
            }
            val last=remoteInfo?:automatic.lastSuccessAt.takeIf{it>0L}?.let{formatBackupInstant(it)}
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
                SiteIcon(if(last==null)"cloud" else "check",null,Modifier.size(16.dp),TextSecondary)
                Text(last?.let{"Ultimo backup · $it"}?:"Nessun salvataggio Drive confermato",color=TextSecondary,fontSize=11.sp,lineHeight=16.sp)
            }
            if(automatic.needsConsent)TextButton(onClick={enableAutomatic(automatic.intervalHours)},enabled=enabled){Text("Ricollega Google per riprendere i backup",color=ObsidianPrimary,fontSize=12.sp)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                CloudButton(if(busy)"Salvataggio…" else "Salva ora","upload",{access.authorize(GoogleFeature.DRIVE){saveRemote(it)}},enabled,modifier=Modifier.weight(1f))
                CloudButton("Ripristina","download",{
                    access.authorize(GoogleFeature.DRIVE){token->prepareRestore("Google Drive · ${ShiftRepository.googleAccountProfile?.email.orEmpty()}"){
                        val latest=GoogleDriveRemote.latest(token)?:error("Nessun backup TurniSync trovato in questo account Google.")
                        GoogleDriveRemote.download(token,latest.id)
                    }}
                },enabled,modifier=Modifier.weight(1f),secondary=true)
            }
            Text("Android può posticipare il salvataggio per risparmiare batteria. La data si aggiorna solo dopo la conferma di Drive.",color=TextMuted,fontSize=10.sp,lineHeight=15.sp)
        }
        CloudCard("Passa da SuperShift","Verifica il percorso disponibile per versione gratuita, Pro o un calendario già esportato.") {
            CloudButton("Scegli come trasferire","swap",onImportSuperShift,enabled,secondary=true)
        }
        CloudCard("Altre copie"){
            Row(Modifier.fillMaxWidth().bouncyClickable{otherCopies=!otherCopies}.padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                SiteIcon("folder",null,Modifier.size(20.dp),ObsidianPrimary)
                Column(Modifier.weight(1f)){Text("Dispositivo e file",fontSize=14.sp,color=TextPrimary,fontWeight=FontWeight.Medium);Text("Copia locale, importa ed esporta",fontSize=11.sp,color=TextSecondary)}
                val rotation by androidx.compose.animation.core.animateFloatAsState(if(otherCopies)180f else 0f,spring(stiffness=450f))
                SiteIcon("chevron-down",null,Modifier.size(20.dp).rotate(rotation),TextSecondary)
            }
            AnimatedVisibility(otherCopies,enter=expandVertically(spring(stiffness=450f))+fadeIn(),exit=shrinkVertically()+fadeOut()){
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        Column(Modifier.weight(1f)){Text("Copia locale automatica",color=TextPrimary,fontSize=13.sp,fontWeight=FontWeight.Medium);Text("All’apertura dell’app · resta su questo telefono",color=TextSecondary,fontSize=11.sp,lineHeight=16.sp)}
                        Switch(checked=ShiftRepository.appPreferences.autoBackupDaily,enabled=enabled,onCheckedChange={ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(autoBackupDaily=it);ShiftRepository.notifyDataChanged()})
                    }
                    importRecovery?.let { recovery ->
                        Text("Prima dell’ultima importazione · ${formatBackupInstant(recovery.lastModified())}",color=TextSecondary,fontSize=11.sp)
                        CloudButton("Ripristina prima dell’importazione","undo",{prepareRestore("Copia precedente all’importazione SuperShift"){check(recovery.length() in 1..24L*1024*1024){"Copia di sicurezza non valida."};recovery.readText(Charsets.UTF_8)}},enabled,secondary=true)
                    }
                    localInfo?.let{Text("Ultima copia locale · $it",color=TextSecondary,fontSize=11.sp)}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        CloudButton("Salva locale","database-backup",{
                            val snapshot=DatabaseSnapshot.capture();busy=true
                            scope.launch{try{val ok=withContext(Dispatchers.IO){GoogleDriveBackupHelper.saveLocalBackupDirect(context,snapshot)};check(ok){"Impossibile salvare la copia sul dispositivo."};localInfo=GoogleDriveBackupHelper.getLocalBackupInfo(context);feedback=ActionFeedback("Copia locale salvata")}
                                catch(e:CancellationException){throw e}catch(e:Exception){failed(e)}finally{busy=false}}
                        },enabled,modifier=Modifier.weight(1f),secondary=true)
                        CloudButton("Ripristina locale","undo",{prepareRestore("Copia locale sul dispositivo"){
                            val file=File(context.filesDir,"cloud_backups/turni_cloud_backup_latest.json")
                            check(file.isFile&&file.length() in 1..16L*1024*1024){"Copia locale assente o non valida."};file.readText(Charsets.UTF_8)
                        }},enabled&&localInfo!=null,modifier=Modifier.weight(1f),secondary=true)
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        CloudButton("Esporta file","upload",{exportSnapshot=DatabaseSnapshot.capture();export.launch("TurniSync-backup-${java.time.LocalDate.now()}.json")},enabled,modifier=Modifier.weight(1f),secondary=true)
                        CloudButton("Importa file","download",{import.launch(arrayOf("application/json","text/json","text/plain"))},enabled,modifier=Modifier.weight(1f),secondary=true)
                    }
                }
            }
        }
    }
    preview?.let{candidate->
        AlertDialog(onDismissRequest={preview=null},containerColor=ObsidianSurface,shape=RoundedCornerShape(22.dp),
            title={Text("Ripristinare il backup?",color=TextPrimary)},
            text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(candidate.source,color=TextSecondary,fontSize=12.sp)
                Text("${candidate.data.shifts.values.sumOf{it.size}} turni · ${candidate.data.definitions.size} modelli · ${candidate.data.profiles.size} ${if(candidate.data.profiles.size==1)"lavoro" else "lavori"}",color=TextPrimary,fontSize=14.sp)
                Text("I dati attuali verranno sostituiti con quelli del backup selezionato.",color=TextSecondary,fontSize=13.sp,lineHeight=19.sp)
            }},
            confirmButton={TextButton(onClick={
                try{LocalDatabaseStore.allowPersistenceAfterRestore();DatabaseRestore.apply(candidate.data);preview=null;feedback=ActionFeedback("Backup ripristinato")}
                catch(e:Exception){preview=null;failed(e)}
            }){Text("Conferma ripristino",color=ObsidianPrimary)}},dismissButton={TextButton(onClick={preview=null}){Text("Annulla",color=TextSecondary)}})
    }
}
private fun readBackupDocument(context:android.content.Context,uri:Uri):String{
    val input=context.contentResolver.openInputStream(uri)?:error("File non accessibile.")
    return input.use{stream->val output=java.io.ByteArrayOutputStream();val bytes=ByteArray(8192)
        while(true){val count=stream.read(bytes);if(count<0)break;check(output.size()+count<=16*1024*1024){"Il backup supera il limite di 16 MB."};output.write(bytes,0,count)}
        output.toString("UTF-8").also{check(it.isNotBlank()){"Il file selezionato è vuoto."}}
    }
}
private fun formatBackupTime(value:String):String=runCatching{Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}.getOrDefault("appena salvato")

private fun formatBackupInstant(value:Long):String=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
