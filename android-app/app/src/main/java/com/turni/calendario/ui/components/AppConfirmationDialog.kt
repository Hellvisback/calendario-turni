package com.turni.calendario.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Centered confirmation with explicit safe/destructive actions and an animated exit. */
@OptIn(ExperimentalAnimationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun AppConfirmationDialog(title:String,message:String,confirmLabel:String,dismissLabel:String,
    onDismiss:()->Unit,onConfirm:()->Unit) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    fun finish(action:()->Unit) { if(!closing){closing=true;scope.launch{visible=false;delay(200);action()}} }
    LaunchedEffect(Unit){visible=true}
    Dialog(onDismissRequest={finish(onDismiss)},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().padding(horizontal=22.dp).navigationBarsPadding(),contentAlignment=Alignment.Center) {
            AnimatedVisibility(visible,enter=fadeIn(tween(220))+scaleIn(spring(.9f,400f),initialScale=.94f),
                exit=fadeOut(tween(180))+scaleOut(tween(200),targetScale=.96f)) {
                Surface(shape=RoundedCornerShape(26.dp),color=ObsidianSurface,modifier=Modifier.widthIn(max=380.dp).heightIn(max=(LocalConfiguration.current.screenHeightDp-48).coerceAtLeast(120).dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.errorContainer),contentAlignment=Alignment.Center) {
                                SiteIcon("trash-2",null,Modifier.size(23.dp),MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(Modifier.weight(1f))
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(ObsidianSurfaceContainer).bouncyClickable{finish(onDismiss)},contentAlignment=Alignment.Center){SiteIcon("x","Continua revisione",Modifier.size(18.dp),TextSecondary)}
                        }
                        Text(title,color=TextPrimary,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
                        Text(message,color=TextSecondary,fontSize=13.sp,lineHeight=20.sp)
                        Button(onClick={finish(onDismiss)},enabled=!closing,modifier=Modifier.fillMaxWidth().heightIn(min=46.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=ObsidianPrimary,contentColor=OnBrand)) {
                            Text(dismissLabel,fontWeight=FontWeight.SemiBold)
                        }
                        OutlinedButton(onClick={finish(onConfirm)},enabled=!closing,modifier=Modifier.fillMaxWidth().heightIn(min=46.dp),shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.error.copy(alpha=.55f)),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) {
                            Text(confirmLabel,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

