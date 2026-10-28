@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.BuildConfig
import com.turni.calendario.alarm.ShiftAlarmScheduler
import com.turni.calendario.model.ShiftAlarmRule
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.AnimatedStatusPopup
import com.turni.calendario.ui.components.PopupType
import com.turni.calendario.ui.components.SiteIcon
import com.turni.calendario.ui.components.bouncyClickable
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.launch

private enum class SettingsSubScreen { NONE, APPEARANCE, CLOUD_BACKUP, GOOGLE_CALENDAR_SYNC, IMPORT_SHIFTS, PDF_EXPORT, REPERIBILITA, SMART_ALARMS, HOLIDAYS, STATISTICS_RULES }

@Composable
fun SettingsScreen(onSubScreenActiveChanged:(Boolean)->Unit={},onNavigateToTurni:(ShiftRotation?)->Unit={},onDismiss:()->Unit={}) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val bellMotion=remember{Animatable(0f)}
    var screen by remember{mutableStateOf(SettingsSubScreen.NONE)}
    var deleteStep by remember{mutableStateOf(0)}
    var message by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(screen){onSubScreenActiveChanged(screen!=SettingsSubScreen.NONE)}
    BackHandler{if(screen!=SettingsSubScreen.NONE)screen=SettingsSubScreen.NONE else onDismiss()}
    val close:()->Unit={screen=SettingsSubScreen.NONE}
    Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
        Crossfade(screen,animationSpec=tween(380)){destination->
            Box(Modifier.fillMaxSize().background(ObsidianBackground)) {
                when(destination){
                    SettingsSubScreen.CLOUD_BACKUP->CloudBackupScreen(onDismiss=close)
                    SettingsSubScreen.STATISTICS_RULES->StatisticsRulesScreen(onDismiss=close)
                    SettingsSubScreen.GOOGLE_CALENDAR_SYNC->GoogleCalendarSyncScreen(onDismiss=close)
                    SettingsSubScreen.IMPORT_SHIFTS->GeminiShiftImportScreen(onDismiss=close,onNavigateToTurni={rotation->close();onNavigateToTurni(rotation)})
                    SettingsSubScreen.PDF_EXPORT->PdfExportScreen(onDismiss=close)
                    SettingsSubScreen.REPERIBILITA->ReperibilitaSettingsScreen(onDismiss=close)
                    SettingsSubScreen.SMART_ALARMS->SmartAlarmsSettingsScreen(onDismiss=close)
                    SettingsSubScreen.HOLIDAYS->HolidaysSettingsScreen(onDismiss=close)
                    SettingsSubScreen.APPEARANCE->Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
                        PreferenceHeading("Aspetto e calendario","Preferenze di visualizzazione",close)
                        PreferenceGroup("Tema") { PreferenceThemeSelector() }
                        PreferenceGroup("Calendario") {
                            PreferenceToggle("La settimana inizia lunedì","Disattiva per iniziare dalla domenica",ShiftRepository.appPreferences.weekStartMonday){ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(weekStartMonday=it);ShiftRepository.notifyDataChanged()}
                            PreferenceDivider()
                            PreferenceToggle("Formato 24 ore","Orari compatti e leggibili",ShiftRepository.appPreferences.timeFormat24h){ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(timeFormat24h=it);ShiftRepository.notifyDataChanged()}
                            PreferenceDivider()
                            PreferenceToggle("Mostra turno di provenienza","Mantieni visibile il turno originale dopo una sostituzione",ShiftRepository.appPreferences.showExShifts){ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(showExShifts=it);ShiftRepository.notifyDataChanged()}
                        }
                        Text("TurniSync · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",Modifier.padding(vertical=16.dp),fontSize=10.sp,color=TextMuted)
                        Spacer(Modifier.height(116.dp))
                    }
                    SettingsSubScreen.NONE->Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
                        PreferenceHeading("Impostazioni",ShiftRepository.activeWorkProfile.name,onDismiss)
                        PreferenceGroup("Notifiche","bell",bottom=16,iconProgress=bellMotion.value) {
                            val alarms=ShiftRepository.smartAlarmsConfig
                            PreferenceToggle("Sveglie dei turni","Avvisi sonori prima dell’inizio",alarms.isGlobalEnabled){enabled->
                                if(enabled&&(!ShiftAlarmScheduler.canScheduleExactAlarms(context)||!ShiftAlarmScheduler.hasNotificationPermission(context))){
                                    screen=SettingsSubScreen.SMART_ALARMS
                                }else{
                                ShiftRepository.smartAlarmsConfig=alarms.copy(isGlobalEnabled=enabled)
                                if(enabled){ShiftAlarmScheduler.scheduleNextAlarm(context);scope.launch{bellMotion.snapTo(0f);bellMotion.animateTo(1f,tween(580));bellMotion.snapTo(0f)}}else ShiftAlarmScheduler.cancelScheduledAlarm(context)
                                ShiftRepository.notifyDataChanged()
                                }
                            }
                            PreferenceDivider()
                            PreferenceRow("alarm-clock","Regole e prossime sveglie","Orari, suoni e autorizzazioni"){screen=SettingsSubScreen.SMART_ALARMS}
                        }
                        PreferenceGroup("Il tuo calendario") {
                            PreferenceRow("palette","Aspetto e calendario","Tema automatico, settimana e orari"){screen=SettingsSubScreen.APPEARANCE}
                            PreferenceDivider()
                            PreferenceRow("bell-ring","Reperibilità","Giorni, turni e regole stagionali"){screen=SettingsSubScreen.REPERIBILITA}
                            PreferenceDivider()
                            PreferenceRow("calendar-heart","Festività","Nazionali, religiose e CCNL"){screen=SettingsSubScreen.HOLIDAYS}

                        }
                        PreferenceGroup("Strumenti") {
                            PreferenceRow("chart-no-axes-combined","Conteggio statistiche","Regole per i turni sostituiti"){screen=SettingsSubScreen.STATISTICS_RULES}
                            PreferenceDivider()
                            PreferenceRow("calendar-sync","Google Calendar","Opzioni e anteprima eventi"){screen=SettingsSubScreen.GOOGLE_CALENDAR_SYNC}
                            PreferenceDivider()
                            PreferenceRow("scan-text","Importazione con AI","PDF, nome e revisione dei turni"){screen=SettingsSubScreen.IMPORT_SHIFTS}
                            PreferenceDivider()
                            PreferenceRow("file-text","Esporta & Stampa PDF","Calendario, elenco e riepilogo dei turni"){screen=SettingsSubScreen.PDF_EXPORT}
                        }
                        PreferenceGroup("Gestione dati") {
                            PreferenceRow("database-backup","Backup e ripristino","Conserva una copia del calendario"){screen=SettingsSubScreen.CLOUD_BACKUP}
                            PreferenceDivider()
                            Row(Modifier.padding(top=15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) { SiteIcon("shield-alert",null,Modifier.size(14.dp),tint=TextMuted);Text("Zona pericolosa",fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=TextMuted) }
                            PreferenceRow("trash-2","Elimina tutti i turni","Da tutti i calendari · richiede conferma",true){deleteStep=1}
                        }
                        Spacer(Modifier.height(116.dp))
                    }
                }
            }
        }
        if(deleteStep>0)PreferenceDeleteDialog(deleteStep,{deleteStep=0},{deleteStep=2}){ShiftRepository.clearAllShifts();deleteStep=0;message="Turni eliminati"}
        AnimatedStatusPopup(message,type=if(message=="Turni eliminati")PopupType.ERROR else PopupType.SUCCESS,onDismiss={message=null})
    }
}

@Composable
private fun PreferenceHeading(title:String,subtitle:String,onBack:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=20.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(ObsidianSurface).bouncyClickable(onClick=onBack),contentAlignment=Alignment.Center){SiteIcon("arrow-left","Indietro",Modifier.size(20.dp),tint=TextPrimary)}
        Column{Text(title,fontSize=24.sp,letterSpacing=(-.7).sp,fontWeight=FontWeight.Bold,color=TextPrimary);Text(subtitle,Modifier.padding(top=4.dp),fontSize=10.sp,color=TextMuted)}
    }
}
@Composable
private fun PreferenceGroup(title:String,icon:String?=null,bottom:Int=6,iconProgress:Float=0f,content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth().padding(bottom=16.dp),shape=RoundedCornerShape(24.dp),color=ObsidianSurface){Column(Modifier.padding(start=16.dp,end=16.dp,top=18.dp,bottom=bottom.dp)){
        Row(Modifier.padding(bottom=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){if(icon!=null)SiteIcon(icon,null,Modifier.size(18.dp),tint=ObsidianPrimary,progress=iconProgress);Text(title,fontSize=13.sp,fontWeight=FontWeight.Bold,color=TextPrimary)}
        content()
    }}
}
@Composable
private fun PreferenceDivider(){Divider(color=ObsidianBorderSubtle)}
@Composable
private fun PreferenceRow(icon:String,title:String,subtitle:String,danger:Boolean=false,onClick:()->Unit){
    val tint=if(danger)ColorNegative else ObsidianPrimary
    val scope=rememberCoroutineScope()
    val motion=remember{Animatable(0f)}
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).bouncyClickable { scope.launch{motion.snapTo(0f);motion.animateTo(1f,tween(520));motion.snapTo(0f)};onClick() }.padding(vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Box(Modifier.size(width=34.dp,height=36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha=.10f)),contentAlignment=Alignment.Center){SiteIcon(icon,null,Modifier.size(19.dp),tint=tint,progress=motion.value)}
        Column(Modifier.weight(1f)){Text(title,color=if(danger)tint else TextPrimary,fontSize=14.sp,lineHeight=19.sp,fontWeight=FontWeight.SemiBold);Text(subtitle,Modifier.padding(top=3.dp),color=TextMuted,fontSize=11.sp,lineHeight=16.sp)}
        SiteIcon("chevron-right",null,Modifier.size(16.dp),tint=TextMuted)
    }
}
@Composable
private fun PreferenceToggle(title:String,subtitle:String,checked:Boolean,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=58.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){
        Column(Modifier.weight(1f)){Text(title,fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold,color=TextPrimary);Text(subtitle,Modifier.padding(top=5.dp),fontSize=11.sp,lineHeight=16.sp,color=TextMuted)}
        val thumb by animateDpAsState(if(checked)19.dp else 3.dp,spring(dampingRatio=.82f,stiffness=280f))
        val fill by animateColorAsState(if(checked)ObsidianPrimary else ObsidianSurfaceHigh,tween(240))
        Box(Modifier.size(44.dp,28.dp).clip(CircleShape).background(fill).toggleable(checked,role=Role.Switch,onValueChange=onChange),contentAlignment=Alignment.CenterStart){Box(Modifier.offset(x=thumb).size(18.dp).clip(CircleShape).background(if(checked)MaterialTheme.colorScheme.onPrimary else TextMuted))}
    }
}
@Composable
private fun PreferenceDeleteDialog(step:Int,onClose:()->Unit,onContinue:()->Unit,onDelete:()->Unit){
    var acknowledged by remember{mutableStateOf(false)}
    val progress=remember{Animatable(0f)};val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){progress.animateTo(1f,spring(dampingRatio=.88f,stiffness=170f))}
    fun dismiss(){scope.launch{progress.animateTo(0f,tween(260));onClose()}}
    Dialog(onDismissRequest={dismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)){
        Box(Modifier.fillMaxSize().padding(horizontal=16.dp),contentAlignment=Alignment.BottomCenter){
            Surface(shape=RoundedCornerShape(24.dp),color=ObsidianSurface,modifier=Modifier.padding(bottom=112.dp).fillMaxWidth().graphicsLayer{alpha=progress.value;translationY=(1-progress.value)*130;scaleX=.96f+.04f*progress.value;scaleY=scaleX}){
                Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(ColorNegative.copy(alpha=.12f)),contentAlignment=Alignment.Center){SiteIcon("trash-2",null,Modifier.size(25.dp),tint=ColorNegative)};Spacer(Modifier.weight(1f));Text("Passaggio $step di 2",fontSize=10.sp,color=TextMuted);Box(Modifier.size(36.dp).bouncyClickable{dismiss()},contentAlignment=Alignment.Center){SiteIcon("x","Chiudi",Modifier.size(19.dp),tint=TextPrimary)}}
                    Text(if(step==1)"Eliminare tutti i turni?" else "Conferma eliminazione",Modifier.padding(top=8.dp,bottom=12.dp),fontSize=22.sp,lineHeight=26.sp,letterSpacing=(-.5).sp,fontWeight=FontWeight.Bold,color=TextPrimary)
                    Text("Sei sicuro di voler eliminare definitivamente tutti i turni? Questa azione è irreversibile.",fontSize=12.sp,lineHeight=20.sp,color=TextPrimary)
                    Surface(shape=RoundedCornerShape(16.dp),color=ObsidianSurfaceHigh,modifier=Modifier.fillMaxWidth().padding(top=16.dp)){Text("Tutti i calendari\nI modelli di turno e i lavori restano disponibili.",Modifier.padding(14.dp),fontSize=11.sp,lineHeight=18.sp,color=TextMuted)}
                    if(step==2)Row(Modifier.padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(acknowledged,{acknowledged=it});Text("Ho capito che i turni e i relativi dettagli verranno eliminati.",fontSize=11.sp,lineHeight=18.sp,color=TextPrimary)}
                    Box(Modifier.fillMaxWidth().padding(top=20.dp).height(46.dp).clip(RoundedCornerShape(15.dp)).background(if(step==1||acknowledged)Color(0xFFB92C40)else Color(0xFFB92C40).copy(alpha=.38f)).bouncyClickable(enabled=step==1||acknowledged){if(step==1)onContinue()else scope.launch{progress.animateTo(0f,tween(260));onDelete()}},contentAlignment=Alignment.Center){Text(if(step==1)"Continua" else "Conferma eliminazione",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Color.White)}
                    Box(Modifier.fillMaxWidth().padding(top=8.dp).height(46.dp).bouncyClickable{dismiss()},contentAlignment=Alignment.Center){Text("Annulla",fontSize=12.sp,color=TextPrimary)}
                }
            }
        }
    }
}

@Composable
private fun PreferenceThemeSelector() {
    val context=LocalContext.current
    val selected=TurniThemeState.mode
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ObsidianSurfaceHigh).padding(4.dp)) {
            val tabWidth=maxWidth/3
            val offset by animateDpAsState(tabWidth*selected.ordinal,spring(dampingRatio=.9f,stiffness=300f))
            Box(Modifier.offset(x=offset).width(tabWidth).height(64.dp).clip(RoundedCornerShape(12.dp)).background(ObsidianPrimary))
            Row { ThemeMode.values().forEach { mode ->
                val active=mode==selected
                val iconMotion=remember{Animatable(0f)}
                val scope=rememberCoroutineScope()
                Column(Modifier.width(tabWidth).height(64.dp).bouncyClickable {
                    TurniThemeState.setMode(context,mode)
                    scope.launch{iconMotion.snapTo(0f);iconMotion.animateTo(1f,tween(560));iconMotion.snapTo(0f)}
                }.padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    val ink=if(active)OnBrand else TextSecondary
                    SiteIcon(mode.icon,null,Modifier.size(20.dp),tint=ink,progress=iconMotion.value)
                    Text(mode.label,fontSize=12.sp,color=ink,fontWeight=FontWeight.Medium,maxLines=1)
                }
            } }
        }
        AnimatedContent(selected,transitionSpec={fadeIn(tween(220)) with fadeOut(tween(160))}) { mode ->
            Text(when(mode){ThemeMode.AUTOMATIC->"Segue il tema del dispositivo, anche quando cambia durante la giornata.";ThemeMode.LIGHT->"Superfici chiare e colori ad alto contrasto.";ThemeMode.DARK->"Superfici scure e luminosità ridotta."},color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
        }
    }
}
