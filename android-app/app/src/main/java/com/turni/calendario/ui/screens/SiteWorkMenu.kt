@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*

/** Compact menu anchored to the original work selector, with an interruptible reveal. */
@Composable
internal fun SiteWorkMenu(open:Boolean,onDismiss:()->Unit,onChoose:(String)->Unit){
    val offset=with(LocalDensity.current){44.dp.roundToPx()}
    val state=remember{MutableTransitionState(false)};state.targetState=open
    if(state.currentState||state.targetState)Popup(alignment=Alignment.TopEnd,offset=IntOffset(0,offset),onDismissRequest=onDismiss,properties=PopupProperties(focusable=true)){
        AnimatedVisibility(state,enter=scaleIn(spring(.85f,250f),initialScale=.86f,transformOrigin=TransformOrigin(.85f,0f))+fadeIn(tween(180))+slideInVertically(spring(.9f,280f)){-it/8},exit=scaleOut(tween(200),targetScale=.94f,transformOrigin=TransformOrigin(.85f,0f))+fadeOut(tween(180))){
            Surface(color=ObsidianSurface,shape=RoundedCornerShape(19.dp),shadowElevation=14.dp,border=BorderStroke(.6.dp,ObsidianBorder),modifier=Modifier.width(218.dp).heightIn(max=320.dp)){
                Column(Modifier.verticalScroll(rememberScrollState()).padding(9.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text("Calendario di lavoro",color=TextSecondary,fontSize=10.sp,modifier=Modifier.padding(horizontal=7.dp,vertical=6.dp))
                    ShiftRepository.workProfiles.forEach{work->
                        val active=work.id==ShiftRepository.activeWorkProfileId
                        Row(Modifier.fillMaxWidth().background(if(active)ObsidianPrimaryContainer else ObsidianSurface,RoundedCornerShape(12.dp)).bouncyClickable{onChoose(work.id)}.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Icon(workProfileIcon(work.iconKey),null,Modifier.size(17.dp),tint=if(active)ObsidianPrimary else TextSecondary)
                            Text(work.name,Modifier.weight(1f),fontSize=12.sp,color=TextPrimary)
                            if(active)SiteIcon("check",null,Modifier.size(15.dp),ObsidianPrimary)
                        }
                    }
                }
            }
        }
    }
}
