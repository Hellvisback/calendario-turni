package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.BuildConfig
import com.turni.calendario.alarm.ShiftAlarmScheduler
import com.turni.calendario.model.ShiftAlarmRule
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*

private enum class SettingsSubScreen { NONE, CLOUD_BACKUP, GOOGLE_CALENDAR_SYNC, IMPORT_SHIFTS, PDF_EXPORT, REPERIBILITA, SMART_ALARMS, HOLIDAYS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onSubScreenActiveChanged: (Boolean) -> Unit = {},
    onNavigateToTurni: (ShiftRotation?) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(SettingsSubScreen.NONE) }
    var deleteStep by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(screen) { onSubScreenActiveChanged(screen != SettingsSubScreen.NONE) }
    BackHandler { if(screen != SettingsSubScreen.NONE) screen=SettingsSubScreen.NONE else onDismiss() }
    val close: () -> Unit = { screen=SettingsSubScreen.NONE }
    Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
        Crossfade(targetState=screen,animationSpec=tween(320)) { destination ->
            Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
                when(destination) {
                    SettingsSubScreen.CLOUD_BACKUP -> CloudBackupScreen(onDismiss=close)
                    SettingsSubScreen.GOOGLE_CALENDAR_SYNC -> GoogleCalendarSyncScreen(onDismiss=close)
                    SettingsSubScreen.IMPORT_SHIFTS -> GeminiShiftImportScreen(onDismiss=close,onNavigateToTurni={ rotation -> close(); onNavigateToTurni(rotation) })
                    SettingsSubScreen.PDF_EXPORT -> PdfExportScreen(onDismiss=close)
                    SettingsSubScreen.REPERIBILITA -> ReperibilitaSettingsScreen(onDismiss=close)
                    SettingsSubScreen.SMART_ALARMS -> SmartAlarmsSettingsScreen(onDismiss=close)
                    SettingsSubScreen.HOLIDAYS -> HolidaysSettingsScreen(onDismiss=close)
                    SettingsSubScreen.NONE -> Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
                            IconButton(onClick=onDismiss,modifier=Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack,"Indietro",tint=TextPrimary) }
                            Text("Impostazioni",Modifier.padding(start=8.dp),fontSize=26.sp,fontWeight=FontWeight.Bold,color=TextPrimary)
                        }
                        SettingsGroup("Aspetto e calendario") {
                            SettingsToggleRow(Icons.Default.DarkMode,"Tema scuro","Scegli il contrasto più adatto a te",TurniThemeState.dark) { TurniThemeState.setDark(context,it) }
                            SettingsDivider()
                            SettingsToggleRow(Icons.Default.CalendarToday,"La settimana inizia lunedì","Disattiva per iniziare dalla domenica",ShiftRepository.appPreferences.weekStartMonday) { ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(weekStartMonday=it); ShiftRepository.notifyDataChanged() }
                            SettingsDivider()
                            SettingsToggleRow(Icons.Default.Schedule,"Formato 24 ore","Orari compatti e leggibili",ShiftRepository.appPreferences.timeFormat24h) { ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(timeFormat24h=it); ShiftRepository.notifyDataChanged() }
                            SettingsDivider()
                            SettingsToggleRow(Icons.Default.History,"Mostra turno originario","Conserva il riferimento della rotazione",ShiftRepository.appPreferences.showExShifts) { ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(showExShifts=it); ShiftRepository.notifyDataChanged() }
                            SettingsDivider()
                            SettingsRow(Icons.Default.Flag,"Festività","Scegli i giorni da evidenziare") { screen=SettingsSubScreen.HOLIDAYS }
                        }
                        SettingsGroup("Sveglie e avvisi") {
                            val alarms=ShiftRepository.smartAlarmsConfig
                            SettingsToggleRow(Icons.Default.NotificationsActive,"Avvisa prima dell'inizio di ogni turno","Sveglie collegate ai tuoi turni",alarms.isGlobalEnabled) {
                                ShiftRepository.smartAlarmsConfig=alarms.copy(isGlobalEnabled=it)
                                if(it) ShiftAlarmScheduler.scheduleNextAlarm(context) else ShiftAlarmScheduler.cancelScheduledAlarm(context)
                                ShiftRepository.notifyDataChanged()
                            }
                            AnimatedVisibility(alarms.isGlobalEnabled,enter=expandVertically(spring())+fadeIn(),exit=shrinkVertically(spring())+fadeOut()) {
                                Column(Modifier.padding(start=16.dp,end=16.dp,bottom=14.dp)) {
                                    Text("Preavviso per i turni di lavoro",color=TextSecondary,fontSize=12.sp,modifier=Modifier.padding(bottom=8.dp))
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                        listOf(15,30,60).forEach { minutes ->
                                            val workingIds=ShiftRepository.shiftDefinitions.filter { it.category==ShiftCategory.LAVORO && !it.isAllDay }.map { it.id }
                                            val chosen=workingIds.isNotEmpty() && workingIds.all { id -> alarms.rules.any { it.shiftDefId==id && it.minutesBefore==minutes && it.customTime==null && it.isEnabled } }
                                            Surface(Modifier.weight(1f).bouncyClickable {
                                                val retained=alarms.rules.filter { it.shiftDefId !in workingIds }
                                                ShiftRepository.smartAlarmsConfig=alarms.copy(rules=retained+workingIds.map { ShiftAlarmRule(it,true,minutes,null) })
                                                ShiftAlarmScheduler.scheduleNextAlarm(context); ShiftRepository.notifyDataChanged(); message="Preavviso aggiornato"
                                            },shape=RoundedCornerShape(13.dp),color=if(chosen) ObsidianPrimaryContainer else ObsidianSurfaceContainer) {
                                                Text("$minutes min",Modifier.padding(vertical=12.dp),color=if(chosen) MaterialTheme.colorScheme.onPrimaryContainer else TextPrimary,fontSize=12.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                                            }
                                        }
                                    }
                                    SettingsRow(Icons.Default.Alarm,"Regole e sveglie programmate","Personalizza ciascun turno") { screen=SettingsSubScreen.SMART_ALARMS }
                                }
                            }
                        }
                        SettingsGroup("Reperibilità") {
                            SettingsRow(Icons.Default.Notifications,"Regole di reperibilità","Manuale, per turno o stagionale") { screen=SettingsSubScreen.REPERIBILITA }
                        }
                        SettingsGroup("Dati e sincronizzazione") {
                            SettingsRow(Icons.Default.CloudSync,"Backup e ripristino","Copia locale ed esportazione su file") { screen=SettingsSubScreen.CLOUD_BACKUP }
                            SettingsDivider()
                            SettingsRow(Icons.Default.Event,"Google Calendar","Sincronizza gli eventi sul telefono") { screen=SettingsSubScreen.GOOGLE_CALENDAR_SYNC }
                            SettingsDivider()
                            SettingsRow(Icons.Default.AutoAwesome,"Importa turni","Da documenti, immagini e prospetti") { screen=SettingsSubScreen.IMPORT_SHIFTS }
                            SettingsDivider()
                            SettingsRow(Icons.Default.PictureAsPdf,"Esporta PDF","Condividi o stampa il calendario") { screen=SettingsSubScreen.PDF_EXPORT }
                            SettingsDivider()
                            SettingsToggleRow(Icons.Default.Backup,"Backup automatico","Salva una copia locale all’avvio dell’app",ShiftRepository.appPreferences.autoBackupDaily) { ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(autoBackupDaily=it); ShiftRepository.notifyDataChanged() }
                        }
                        SettingsGroup("Gestione dati") {
                            SettingsRow(Icons.Default.DeleteOutline,"Elimina tutti i turni","Apre una conferma prima di procedere",danger=true) { deleteStep=1 }
                        }
                        Text("TurniSync · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",Modifier.fillMaxWidth().padding(vertical=16.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=TextMuted,fontSize=11.sp)
                        Spacer(Modifier.height(105.dp))
                    }
                }
            }
        }
        if(deleteStep>0) AlertDialog(
            onDismissRequest={deleteStep=0},
            icon={Icon(Icons.Default.DeleteOutline,null,tint=MaterialTheme.colorScheme.error)},
            title={Text(if(deleteStep==1) "Eliminare tutti i turni?" else "Conferma eliminazione",color=TextPrimary)},
            text={Text(if(deleteStep==1) "Tutti i turni del calendario e i relativi dettagli verranno rimossi. I modelli e i lavori resteranno disponibili." else "Sei sicuro di voler eliminare definitivamente tutti i turni? Questa azione è irreversibile.",color=TextSecondary)},
            confirmButton={TextButton(onClick={if(deleteStep==1) deleteStep=2 else { ShiftRepository.clearAllShifts(); deleteStep=0; message="Turni eliminati" }}) {Text(if(deleteStep==1) "Continua" else "Conferma eliminazione",color=MaterialTheme.colorScheme.error)}},
            dismissButton={TextButton(onClick={deleteStep=0}) {Text("Annulla")}},containerColor=ObsidianSurface,shape=RoundedCornerShape(26.dp)
        )
        AnimatedStatusPopup(message=message,type=if(message=="Turni eliminati") PopupType.ERROR else PopupType.SUCCESS,onDismiss={message=null})
    }
}

@Composable
private fun SettingsGroup(title:String, content:@Composable ColumnScope.()->Unit) {
    Text(title,Modifier.padding(start=4.dp,top=6.dp,bottom=8.dp),fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=TextSecondary)
    Surface(Modifier.fillMaxWidth().padding(bottom=14.dp),shape=RoundedCornerShape(24.dp),color=ObsidianSurface) { Column(content=content) }
}
@Composable
private fun SettingsDivider() { Divider(Modifier.padding(horizontal=16.dp),color=ObsidianBorderSubtle) }
@Composable
fun SettingsRow(icon:ImageVector,title:String,subtitle:String,danger:Boolean=false,onClick:()->Unit) {
    val tint=if(danger) MaterialTheme.colorScheme.error else ObsidianPrimary
    Row(Modifier.fillMaxWidth().bouncyClickable(onClick=onClick).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(icon,null,Modifier.size(24.dp),tint=tint)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) { Text(title,color=if(danger) tint else TextPrimary,fontWeight=FontWeight.SemiBold,fontSize=14.sp); Text(subtitle,color=TextSecondary,fontSize=12.sp,modifier=Modifier.padding(top=3.dp)) }
        Icon(Icons.Default.ChevronRight,null,Modifier.size(18.dp),tint=TextMuted)
    }
}
@Composable
fun SettingsToggleRow(icon:ImageVector,title:String,subtitle:String,checked:Boolean,onCheckedChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(icon,null,Modifier.size(24.dp),tint=ObsidianPrimary)
        Column(Modifier.weight(1f)) { Text(title,color=TextPrimary,fontWeight=FontWeight.SemiBold,fontSize=14.sp); Text(subtitle,color=TextSecondary,fontSize=12.sp,modifier=Modifier.padding(top=3.dp)) }
        Switch(checked=checked,onCheckedChange=onCheckedChange,colors=SwitchDefaults.colors(checkedThumbColor=MaterialTheme.colorScheme.onPrimary,checkedTrackColor=ObsidianPrimary))
    }
}
