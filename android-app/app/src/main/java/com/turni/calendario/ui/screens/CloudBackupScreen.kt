@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.repository.*
import com.turni.calendario.sync.*
import com.turni.calendario.ui.components.ActionFeedback
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.SiteIconMotion
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class BackupRestorePreview(val data:DatabaseSnapshot,val source:String,val remoteOwner:String?=null)
private data class BackupReviewRequest(val token:String,val owner:String,val latest:DriveBackupFile,val save:Boolean,val automaticHours:Int?)

@Composable
fun CloudBackupScreen(onDismiss:()->Unit,restoreOnly:Boolean=false,onRestored:()->Unit={},accountEntry:Boolean=false){
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var feedback by remember{mutableStateOf<ActionFeedback?>(null)}
    var busy by remember{mutableStateOf(false)}
    val access=rememberGoogleAccess{feedback=ActionFeedback(it,true)}
    var remoteInfo by remember{mutableStateOf<String?>(null)}
    var remoteInfoOwner by remember{mutableStateOf<String?>(null)}
    var pendingDriveAction by remember{mutableStateOf<((String)->Unit)?>(null)}
    var localInfo by remember{mutableStateOf(GoogleDriveBackupHelper.getLocalBackupInfo(context))}
    var preview by remember{mutableStateOf<BackupRestorePreview?>(null)}
    var review by remember{mutableStateOf<BackupReviewRequest?>(null)}
    var history by remember{mutableStateOf<List<DriveBackupFile>>(emptyList())}
    var historyToken by remember{mutableStateOf<String?>(null)}
    var historyOwner by remember{mutableStateOf<String?>(null)}
    var localHistory by remember{mutableStateOf<List<File>>(emptyList())}
    var exportSnapshot by remember{mutableStateOf<DatabaseSnapshot?>(null)}
    val importRecovery=remember { File(context.filesDir,"import_recovery").listFiles()?.filter { it.name.startsWith("before-supershift-")&&it.extension=="json" }?.maxByOrNull { it.lastModified() } }
    val email=ShiftRepository.googleAccountProfile?.email
    val automatic by DriveAutomaticBackup.statusFlow.collectAsState()
    var otherCopies by remember{mutableStateOf(restoreOnly&&!accountEntry)}
    fun refreshAutomatic(){DriveAutomaticBackup.refresh(context)}
    LaunchedEffect(email){
        if(email==null||remoteInfoOwner!=email){remoteInfo=null;remoteInfoOwner=null}
        if(review?.owner!=email)review=null
        if(historyOwner!=email){history=emptyList();historyToken=null;historyOwner=null}
        if(automatic.enabled&&!BackupInstallationApproval.contains(context.noBackupFilesDir,email))
            DriveAutomaticBackup.configure(context,false,automatic.intervalHours)
        refreshAutomatic()
    }
    fun failed(error:Exception){feedback=ActionFeedback(error.message?:"Operazione non riuscita. Riprova.",true)}
    fun prepareRestore(source:String,remoteOwner:String?=null,reader:()->String){
        val previous=DatabaseSnapshot.capture();busy=true
        scope.launch{
            try{val parsed=withContext(Dispatchers.IO){DatabaseRestore.parse(reader(),previous)}
                check(remoteOwner==null||DriveBackupPolicy.sameAccount(remoteOwner,ShiftRepository.googleAccountProfile?.email)){"L’account è cambiato. Seleziona nuovamente il backup."}
                preview=BackupRestorePreview(parsed,source,remoteOwner)}
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
    val enabled=!busy&&!access.busy&&!automatic.running
    fun saveRemote(token:String){
        if(LocalDatabaseStore.loadError!=null){feedback=ActionFeedback("Dati locali non leggibili: ripristina una copia valida prima di salvare su Drive.",true);return}
        val owner=ShiftRepository.googleAccountProfile?.email?:return
        val snapshot=DatabaseSnapshot.capture();busy=true
        scope.launch{try{
            val saved=withContext(Dispatchers.IO){
                val json=GoogleDriveBackupHelper.exportDatabaseToJson(snapshot)
                DatabaseRestore.parse(json,snapshot)
                GoogleDriveRemote.upload(token,json)
            }
            if(ShiftRepository.googleAccountProfile?.email==owner){
                DriveAutomaticBackup.recordManualSuccess(context,owner,saved.modifiedTime)
                refreshAutomatic();remoteInfo=formatBackupTime(saved.modifiedTime);remoteInfoOwner=owner
                feedback=ActionFeedback("Nuova copia salvata su Google Drive")
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){failed(e)}finally{busy=false}}
    }
    fun proceed(token:String,owner:String,save:Boolean,hours:Int?){
        if(!DriveBackupPolicy.sameAccount(owner,ShiftRepository.googleAccountProfile?.email))return
        try{
            BackupInstallationApproval.approve(context.noBackupFilesDir,owner)
            if(hours!=null){
                DriveAutomaticBackup.configure(context,true,hours,owner);refreshAutomatic()
                check(DriveAutomaticBackup.runNow(context)){"Impossibile avviare il salvataggio automatico. Riprova."}
                feedback=ActionFeedback("Backup automatico attivato")
            }else if(save)saveRemote(token)
            else feedback=ActionFeedback("Dati del dispositivo confermati · nessun backup avviato")
        }catch(e:Exception){failed(e)}
    }
    fun checkRemote(token:String,save:Boolean=false,hours:Int?=null){
        val owner=ShiftRepository.googleAccountProfile?.email?:return
        busy=true
        scope.launch{var continueWithLocal=false
          try{
            val latest=withContext(Dispatchers.IO){GoogleDriveRemote.latest(token)}
            if(DriveBackupPolicy.sameAccount(owner,ShiftRepository.googleAccountProfile?.email)){
                remoteInfo=latest?.let{formatBackupTime(it.modifiedTime)};remoteInfoOwner=owner
                if(latest!=null&&!BackupInstallationApproval.contains(context.noBackupFilesDir,owner))
                    review=BackupReviewRequest(token,owner,latest,save,hours)
                else if(save||hours!=null)continueWithLocal=true
                else feedback=ActionFeedback(if(latest==null)"Nessun backup su Drive · scegli quando salvare" else "Backup trovato · nessun salvataggio avviato")
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){failed(e)}finally{busy=false}
          if(continueWithLocal)proceed(token,owner,save,hours)
        }
    }
    fun runWithDriveAccount(action:(String)->Unit){
        if(ShiftRepository.googleAccountProfile==null){
            pendingDriveAction=action
        }else{
            access.authorize(GoogleFeature.DRIVE,onToken=action)
        }
    }
    fun enableAutomatic(hours:Int){
        runWithDriveAccount{token->
            checkRemote(token,hours=hours)
        }
    }
    val intervals = remember { listOf(6, 12, 24, 168) }
    val intervalItems = remember(intervals) {
        intervals.map { hours ->
            when (hours) {
                24 -> "1 giorno"
                168 -> "7 giorni"
                else -> "${hours}h"
            } to null
        }
    }
    val palette = currentTurniPalette
    val driveBadgeMotion = remember { Animatable(0f) }
    var wasBackupBusy by remember { mutableStateOf(false) }
    val driveActivityBusy = busy || automatic.running
    LaunchedEffect(driveActivityBusy) {
        if (driveActivityBusy) {
            wasBackupBusy = true
            while (true) {
                driveBadgeMotion.snapTo(0f)
                driveBadgeMotion.animateTo(1f, tween(580, easing = FastOutSlowInEasing))
                driveBadgeMotion.snapTo(0f)
                delay(420L)
            }
        } else if (wasBackupBusy) {
            wasBackupBusy = false
            driveBadgeMotion.snapTo(0f)
        }
    }
    val localSectionMotion = remember { Animatable(0f) }
    var localSectionJob by remember { mutableStateOf<Job?>(null) }

    CloudFeatureScaffold(
        title = if (accountEntry) "Accedi a TurniSync" else "Backup e ripristino",
        subtitle = when {
            accountEntry -> "Recupera i tuoi turni da Google Drive"
            restoreOnly -> "Scegli e controlla una copia prima di ripristinarla"
            else -> "Google Drive e copie locali sul dispositivo"
        },
        onDismiss = onDismiss,
        feedback = feedback,
        onDismissFeedback = { feedback = null },
    ) {
        GoogleAccountCard(
            access = access,
            onFeedback = { feedback = it },
            enabled = !busy && !automatic.running,
            feature = GoogleFeature.DRIVE,
            explanation = if (accountEntry) {
                "Accedi con l’account Google che usavi con TurniSync per recuperare il tuo calendario."
            } else {
                "L’accesso controlla le copie esistenti: non avvia salvataggi e non sostituisce i tuoi dati."
            },
            onConnected = { token ->
                if (!restoreOnly) {
                    checkRemote(token)
                } else {
                    feedback = ActionFeedback(
                        if (accountEntry) "Account collegato: recupera il tuo calendario" else "Account collegato: scegli Ripristina",
                    )
                }
            },
        )
        CloudCard {
            val last = remoteInfo ?: automatic.lastSuccessAt.takeIf { it > 0L }?.let { formatBackupInstant(it) }
            val driveHealthy = last != null && !automatic.running && (restoreOnly || !automatic.needsConsent)
            val statusLabel = when {
                automatic.running -> "In corso"
                !restoreOnly && automatic.needsConsent -> "Consenso"
                last != null -> if (restoreOnly) "Disponibile" else "Protetto"
                else -> "In attesa"
            }
            val statusFill by animateColorAsState(
                targetValue = when {
                    automatic.running -> ObsidianPrimarySubtle
                    !restoreOnly && automatic.needsConsent -> Color(palette.warning.fill)
                    driveHealthy -> Color(palette.success.fill)
                    else -> ObsidianSurfaceContainer
                },
                animationSpec = tween(180),
                label = "backupStatusFill",
            )
            val statusInk by animateColorAsState(
                targetValue = when {
                    automatic.running -> BrandInk
                    !restoreOnly && automatic.needsConsent -> Color(palette.warning.ink)
                    driveHealthy -> Color(palette.success.ink)
                    else -> TextSecondary
                },
                animationSpec = tween(180),
                label = "backupStatusInk",
            )
            val statusIcon = if (driveHealthy) "check" else "cloud"

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "database-backup",
                    size = 42.dp,
                    cornerRadius = 13.dp,
                    iconSize = 20.dp,
                    fill = if (driveHealthy) ObsidianPrimaryContainer else ObsidianSurfaceContainer,
                    tint = BrandInk,
                    progress = driveBadgeMotion.value,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (restoreOnly) "Recupera il tuo calendario" else "Backup su Google Drive",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (restoreOnly) "Copie salvate su Drive" else "Spazio privato TurniSync su Drive",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BackupStatusPill(
                    text = statusLabel,
                    fill = statusFill,
                    ink = statusInk,
                    icon = statusIcon,
                    progress = driveBadgeMotion.value,
                )
            }

            if (restoreOnly) {
                Text(
                    text = "Scegli una copia e controllane il contenuto. Il collegamento non crea backup e non modifica quelli esistenti.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
            if (!restoreOnly) {
                val toggleAutomatic: (Boolean) -> Unit = { on ->
                    if (on) {
                        enableAutomatic(automatic.intervalHours)
                    } else {
                        DriveAutomaticBackup.disable(context)
                        refreshAutomatic()
                        feedback = ActionFeedback("Backup automatico disattivato")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled,
                        ) { toggleAutomatic(!automatic.enabled) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Salvataggio automatico", color = TextPrimary, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = if (automatic.running) {
                                "Salvataggio in corso…"
                            } else if (automatic.enabled) {
                                "In background, quando c’è connessione"
                            } else {
                                "Attivalo per proteggere le modifiche"
                            },
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    TurniSwitch(
                        checked = automatic.enabled,
                        onCheckedChange = toggleAutomatic,
                        enabled = enabled,
                    )
                }
                AnimatedVisibility(
                    visible = automatic.enabled,
                    enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
                    exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(160)),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ogni quanto salvare", color = TextSecondary, fontSize = 12.sp)
                        SlidingSegmentBar(
                            items = intervalItems,
                            selectedIndex = intervals.indexOf(automatic.intervalHours).coerceAtLeast(0),
                            onSelect = { idx -> enableAutomatic(intervals[idx]) },
                            enabled = enabled,
                            height = 42.dp,
                        )
                    }
                }
                SettingsDivider()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(ObsidianSurfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val statusIconTint = if (driveHealthy) Color(palette.success.ink) else TextSecondary
                SiteIcon(if (last == null) "cloud" else "check", null, Modifier.size(16.dp), statusIconTint)
                Text(
                    text = last?.let { "Ultimo backup · $it" } ?: "Nessun salvataggio Drive confermato",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
            if (!restoreOnly && automatic.needsConsent) {
                TextButton(
                    onClick = { enableAutomatic(automatic.intervalHours) },
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = BrandInk),
                ) {
                    Text("Ricollega Google per riprendere i backup", color = BrandInk, fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!restoreOnly) {
                    CloudButton(
                        label = if (busy) "Operazione…" else "Salva ora",
                        icon = "upload",
                        onClick = { runWithDriveAccount { checkRemote(it, save = true) } },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                }
                CloudButton(
                    label = if (accountEntry) "Recupera il calendario" else "Ripristina",
                    icon = "download",
                    onClick = {
                        runWithDriveAccount { token ->
                            val owner = ShiftRepository.googleAccountProfile?.email ?: return@runWithDriveAccount
                            busy = true
                            scope.launch {
                                try {
                                    val copies = withContext(Dispatchers.IO) { GoogleDriveRemote.history(token) }
                                    check(copies.isNotEmpty()) { "Nessun backup TurniSync trovato in questo account Google." }
                                    if (DriveBackupPolicy.sameAccount(owner, ShiftRepository.googleAccountProfile?.email)) {
                                        history = copies
                                        historyToken = token
                                        historyOwner = owner
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    failed(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    secondary = !restoreOnly,
                )
            }
            Text(
                text = if (restoreOnly) {
                    "Il calendario viene recuperato da una copia salvata. Potrai controllarla prima di applicarla."
                } else {
                    "Ogni salvataggio crea una nuova copia: le precedenti restano ripristinabili. Dopo una reinstallazione scegli prima se recuperare i dati da Drive. Il backup automatico si attiva solo con il tuo consenso."
                },
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
        }
        CloudCard {
            val rotation by animateFloatAsState(
                targetValue = if (otherCopies) 180f else 0f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                label = "otherCopiesChevron",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .bouncyClickable {
                        otherCopies = !otherCopies
                        localSectionJob?.cancel()
                        localSectionJob = scope.launch {
                            localSectionMotion.snapTo(0f)
                            localSectionMotion.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                            localSectionMotion.snapTo(0f)
                        }
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "folder-open",
                    size = 42.dp,
                    cornerRadius = 13.dp,
                    iconSize = 20.dp,
                    progress = localSectionMotion.value,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Dispositivo e file", fontSize = 15.sp, lineHeight = 20.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Copia locale, importa ed esporta", fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = TextSecondary)
                }
                if (localInfo != null && !otherCopies) {
                    BackupStatusPill(
                        text = "Locale",
                        fill = ObsidianSurfaceContainer,
                        ink = TextSecondary,
                    )
                }
                SiteIcon("chevron-down", null, Modifier.size(20.dp).rotate(rotation), TextSecondary)
            }
            AnimatedVisibility(
                visible = otherCopies,
                enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
                exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(160)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsDivider()
                    if (!restoreOnly) {
                        val toggleLocalAuto: (Boolean) -> Unit = { checked ->
                            ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(autoBackupDaily = checked)
                            ShiftRepository.notifyDataChanged()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = enabled,
                                ) { toggleLocalAuto(!ShiftRepository.appPreferences.autoBackupDaily) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Copia locale automatica", color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                                Text("All’apertura dell’app · resta su questo telefono", color = TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                            TurniSwitch(
                                checked = ShiftRepository.appPreferences.autoBackupDaily,
                                onCheckedChange = toggleLocalAuto,
                                enabled = enabled,
                            )
                        }
                        SettingsDivider()
                    }
                    importRecovery?.let { recovery ->
                        Text(
                            text = "Prima dell’ultima importazione · ${formatBackupInstant(recovery.lastModified())}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                        CloudButton(
                            label = "Ripristina prima dell’importazione",
                            icon = "undo",
                            onClick = {
                                prepareRestore("Copia precedente all’importazione") {
                                    check(recovery.length() in 1..24L * 1024 * 1024) { "Copia di sicurezza non valida." }
                                    recovery.readText(Charsets.UTF_8)
                                }
                            },
                            enabled = enabled,
                            secondary = true,
                        )
                        SettingsDivider()
                    }
                    localInfo?.let {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(ObsidianSurfaceContainer)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SiteIcon("database-backup", null, Modifier.size(16.dp), TextSecondary)
                            Text("Ultima copia locale · $it", color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!restoreOnly) {
                            CloudButton(
                                label = "Salva locale",
                                icon = "database-backup",
                                onClick = {
                                    val snapshot = DatabaseSnapshot.capture()
                                    busy = true
                                    scope.launch {
                                        try {
                                            val ok = withContext(Dispatchers.IO) {
                                                GoogleDriveBackupHelper.saveLocalBackupDirect(context, snapshot)
                                            }
                                            check(ok) { "Impossibile salvare la copia sul dispositivo." }
                                            localInfo = GoogleDriveBackupHelper.getLocalBackupInfo(context)
                                            feedback = ActionFeedback("Copia locale salvata")
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            failed(e)
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                secondary = true,
                            )
                        }
                        CloudButton(
                            label = "Ripristina locale",
                            icon = "undo",
                            onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        localHistory = withContext(Dispatchers.IO) {
                                            LocalBackupArchive.copies(File(context.filesDir, "cloud_backups"))
                                        }
                                        check(localHistory.isNotEmpty()) { "Nessuna copia locale disponibile." }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        failed(e)
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = enabled && localInfo != null,
                            modifier = Modifier.weight(1f),
                            secondary = true,
                        )
                    }
                    SettingsDivider()
                    Text("Hai i turni nell’altra app?", color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Nella vecchia app apri Backup e ripristino ed esporta un file JSON. Importalo qui e controlla l’anteprima prima di confermare. Le copie locali restano separate tra le due app; per trasferire il file non serve accedere a Google.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!restoreOnly) {
                            CloudButton(
                                label = "Esporta file",
                                icon = "upload",
                                onClick = {
                                    exportSnapshot = DatabaseSnapshot.capture()
                                    export.launch("TurniSync-backup-${java.time.LocalDate.now()}.json")
                                },
                                enabled = enabled,
                                modifier = Modifier.weight(1f),
                                secondary = true,
                            )
                        }
                        CloudButton(
                            label = "Importa file",
                            icon = "download",
                            onClick = {
                                import.launch(arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"))
                            },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            secondary = true,
                        )
                    }
                }
            }
        }
    }
    review?.let { request ->
        TurniSafeBottomDialog(onClose = { review = null }) { finish ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "database-backup",
                    size = 40.dp,
                    cornerRadius = 12.dp,
                    iconSize = 19.dp,
                    fill = ObsidianPrimaryContainer,
                    tint = BrandInk,
                )
                Text(
                    text = "Backup già presente",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Chiudi",
                    onClick = { finish { review = null } },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = Color(palette.danger.fill),
                    tint = Color(palette.danger.ink),
                )
            }
            Text(
                text = "Su Drive c’è una copia del ${formatBackupTime(request.latest.modifiedTime)}. Nessun dato è stato inviato.",
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = "Controlla la copia prima di ripristinarla, oppure scegli esplicitamente di proseguire con i dati di questo dispositivo. Le copie precedenti verranno conservate.",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            CloudButton(
                label = "Usa i dati di questo dispositivo",
                icon = "smartphone",
                onClick = {
                    finish {
                        review = null
                        proceed(request.token, request.owner, request.save, request.automaticHours)
                    }
                },
                secondary = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudButton(
                    label = "Più tardi",
                    icon = "x",
                    onClick = { finish { review = null } },
                    modifier = Modifier.weight(1f),
                    secondary = true,
                )
                CloudButton(
                    label = "Controlla il backup",
                    icon = "download",
                    onClick = {
                        finish {
                            review = null
                            prepareRestore("Google Drive · ${formatBackupTime(request.latest.modifiedTime)}", request.owner) {
                                GoogleDriveRemote.download(request.token, request.latest.id)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (history.isNotEmpty()) {
        TurniSafeBottomDialog(
            onClose = {
                history = emptyList()
                historyToken = null
            },
            scrollContent = false,
        ) { finish ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "cloud",
                    size = 40.dp,
                    cornerRadius = 12.dp,
                    iconSize = 19.dp,
                    fill = ObsidianPrimaryContainer,
                    tint = BrandInk,
                )
                Text(
                    text = "Copie su Google Drive",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Chiudi",
                    onClick = {
                        finish {
                            history = emptyList()
                            historyToken = null
                        }
                    },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = Color(palette.danger.fill),
                    tint = Color(palette.danger.ink),
                )
            }
            Text(
                text = "Scegli una copia per controllarne il contenuto. Il ripristino richiederà una seconda conferma.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEachIndexed { index, copy ->
                    CloudButton(
                        label = "${if (index == 0) "Ultima · " else ""}${formatBackupTime(copy.modifiedTime)}",
                        icon = "database-backup",
                        onClick = {
                            val token = historyToken
                            val owner = historyOwner
                            finish {
                                history = emptyList()
                                historyToken = null
                                if (token != null && owner != null) {
                                    prepareRestore("Google Drive · ${formatBackupTime(copy.modifiedTime)}", owner) {
                                        GoogleDriveRemote.download(token, copy.id)
                                    }
                                }
                            }
                        },
                        enabled = enabled,
                        secondary = true,
                    )
                }
            }
            CloudButton(
                label = "Chiudi",
                icon = "x",
                onClick = {
                    finish {
                        history = emptyList()
                        historyToken = null
                    }
                },
                secondary = true,
            )
        }
    }
    if (localHistory.isNotEmpty()) {
        TurniSafeBottomDialog(
            onClose = { localHistory = emptyList() },
            scrollContent = false,
        ) { finish ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "folder-open",
                    size = 40.dp,
                    cornerRadius = 12.dp,
                    iconSize = 19.dp,
                    fill = ObsidianPrimaryContainer,
                    tint = BrandInk,
                )
                Text(
                    text = "Copie sul dispositivo",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Chiudi",
                    onClick = { finish { localHistory = emptyList() } },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = Color(palette.danger.fill),
                    tint = Color(palette.danger.ink),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                localHistory.forEachIndexed { index, copy ->
                    CloudButton(
                        label = "${if (index == 0) "Ultima · " else ""}${formatBackupInstant(copy.lastModified())}",
                        icon = "database-backup",
                        onClick = {
                            finish {
                                localHistory = emptyList()
                                prepareRestore("Copia locale · ${formatBackupInstant(copy.lastModified())}") {
                                    check(copy.length() in 1..16L * 1024 * 1024) { "Copia locale non valida." }
                                    copy.readText(Charsets.UTF_8)
                                }
                            }
                        },
                        enabled = enabled,
                        secondary = true,
                    )
                }
            }
            CloudButton(
                label = "Chiudi",
                icon = "x",
                onClick = { finish { localHistory = emptyList() } },
                secondary = true,
            )
        }
    }
    preview?.let { candidate ->
        TurniSafeBottomDialog(onClose = { preview = null }) { finish ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeutralIconBadge(
                    icon = "database-backup",
                    size = 40.dp,
                    cornerRadius = 12.dp,
                    iconSize = 19.dp,
                    fill = Color(palette.warning.fill),
                    tint = Color(palette.warning.ink),
                )
                Text(
                    text = "Ripristinare il backup?",
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                TonalIconButton(
                    icon = "x",
                    contentDescription = "Chiudi",
                    onClick = { finish { preview = null } },
                    size = 36.dp,
                    cornerRadius = 12.dp,
                    fill = Color(palette.danger.fill),
                    tint = Color(palette.danger.ink),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianSurfaceContainer)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(candidate.source, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                Text(
                    text = "${candidate.data.shifts.values.sumOf { it.size }} turni · ${candidate.data.definitions.size} modelli · ${candidate.data.profiles.size} ${if (candidate.data.profiles.size == 1) "lavoro" else "lavori"}",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${candidate.data.rotations.size} rotazioni · note, turni originali e configurazioni incluse",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            Text(
                text = "I dati attuali verranno sostituiti con quelli del backup selezionato.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CloudButton(
                    label = "Annulla",
                    icon = "x",
                    onClick = { finish { preview = null } },
                    modifier = Modifier.weight(1f),
                    secondary = true,
                )
                CloudButton(
                    label = "Conferma ripristino",
                    icon = "check",
                    onClick = {
                        finish {
                            try {
                                check(candidate.remoteOwner == null || DriveBackupPolicy.sameAccount(candidate.remoteOwner, ShiftRepository.googleAccountProfile?.email)) {
                                    "L’account è cambiato: seleziona nuovamente la copia."
                                }
                                DriveAutomaticBackup.disable(context)
                                candidate.remoteOwner?.let { BackupInstallationApproval.approve(context.noBackupFilesDir, it) }
                                LocalDatabaseStore.allowPersistenceAfterRestore()
                                DatabaseRestore.apply(candidate.data)
                                refreshAutomatic()
                                preview = null
                                feedback = ActionFeedback("Backup ripristinato · salvataggio automatico disattivato")
                                onRestored()
                            } catch (e: Exception) {
                                preview = null
                                failed(e)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    pendingDriveAction?.let { nextAction ->
        GoogleAccountSheet(
            access = access,
            feature = GoogleFeature.DRIVE,
            onFeedback = { feedback = it },
            onDismiss = { pendingDriveAction = null },
            onConnected = { token ->
                pendingDriveAction = null
                nextAction(token)
            },
        )
    }
}

@Composable
private fun BackupStatusPill(
    text: String,
    fill: Color,
    ink: Color,
    icon: String? = null,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            SiteIconMotion(
                name = icon,
                description = null,
                modifier = Modifier.size(12.dp),
                tint = ink,
            ) { progress }
        }
        Text(
            text = text,
            color = ink,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
