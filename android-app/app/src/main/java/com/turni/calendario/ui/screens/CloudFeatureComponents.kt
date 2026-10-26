@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.turni.calendario.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.turni.calendario.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*

@Composable
internal fun CloudFeatureScaffold(title:String,onDismiss:()->Unit,feedback:ActionFeedback?,onDismissFeedback:()->Unit,content:@Composable ColumnScope.()->Unit){
    BackHandler(onBack=onDismiss)
    Box(Modifier.fillMaxSize().background(ObsidianBackground).statusBarsPadding().navigationBarsPadding()){
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(ObsidianSurface).bouncyClickable(onClick=onDismiss),contentAlignment=Alignment.Center){SiteIcon("arrow-left","Indietro",Modifier.size(22.dp))}
                Text(title,color=TextPrimary,fontSize=22.sp,lineHeight=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            }
            content();Spacer(Modifier.height(24.dp))
        }
        MagicalActionFeedbackHUD(feedback,onDismissFeedback,Modifier.align(Alignment.TopCenter))
    }
}
@Composable
internal fun CloudCard(title:String,subtitle:String?=null,content:@Composable ColumnScope.()->Unit){
    Surface(shape=RoundedCornerShape(20.dp),color=ObsidianSurface,modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(title,color=TextPrimary,fontSize=16.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold)
            subtitle?.let{Text(it,color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)}
            content()
        }
    }
}
@Composable
internal fun CloudButton(label:String,icon:String,onClick:()->Unit,enabled:Boolean=true,destructive:Boolean=false,modifier:Modifier=Modifier,secondary:Boolean=false){
    val fill=if(destructive)if(TurniThemeState.dark)androidx.compose.ui.graphics.Color(0xFF442330)else androidx.compose.ui.graphics.Color(0xFFFFE9ED)else if(secondary)ObsidianSurfaceHigh else ObsidianPrimary
    val ink=if(destructive)if(TurniThemeState.dark)androidx.compose.ui.graphics.Color(0xFFFFADB6)else androidx.compose.ui.graphics.Color(0xFF9C2940)else if(secondary)TextPrimary else catalogInk(fill)
    Surface(shape=RoundedCornerShape(14.dp),color=fill,modifier=modifier.fillMaxWidth().heightIn(min=46.dp).bouncyClickable(enabled,onClick)){
        Row(Modifier.padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
            SiteIcon(icon,null,Modifier.size(18.dp),ink.copy(alpha=if(enabled)1f else .45f));Spacer(Modifier.width(8.dp))
            Text(label,color=ink.copy(alpha=if(enabled)1f else .45f),fontSize=13.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}
/** Google branding stays recognizable in both application themes. */
@Composable
internal fun GoogleSignInButton(enabled:Boolean,loading:Boolean,onClick:()->Unit){
    Surface(color=Color.White,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,Color(0xFF747775)),
        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).bouncyClickable(enabled,onClick)){
        Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
            Image(painterResource(R.drawable.google_g),contentDescription=null,modifier=Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(if(loading)"Collegamento…" else "Accedi con Google",color=Color(0xFF1F1F1F).copy(alpha=if(enabled)1f else .55f),fontSize=14.sp,fontWeight=FontWeight.Medium)
        }
    }
}

@Composable
internal fun GoogleAccountCard(access:GoogleAccess,onFeedback:(ActionFeedback)->Unit,enabled:Boolean=true,
    feature:GoogleFeature=GoogleFeature.PROFILE,explanation:String?=null,onConnected:((String)->Unit)?=null){
    val profile=ShiftRepository.googleAccountProfile
    fun connect(){access.authorize(feature,true){token->onConnected?.invoke(token)?:onFeedback(ActionFeedback("Account Google collegato"))}}
    CloudCard("Account Google",if(profile==null)explanation?:"Accedi con il tuo account. I tuoi dati rimangono nel tuo spazio Google." else null){
        if(profile==null){
            GoogleSignInButton(enabled&&!access.busy,access.busy,::connect)
        }else{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(ObsidianSurfaceHigh),contentAlignment=Alignment.Center){
                    Image(painterResource(R.drawable.google_g),null,Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(2.dp))
                }
                Column(Modifier.weight(1f)){
                    Text(profile.displayName,color=TextPrimary,fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(profile.email,color=TextSecondary,fontSize=12.sp,lineHeight=17.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                TextButton(onClick=::connect,enabled=enabled&&!access.busy){Text("Cambia account",color=ObsidianPrimary,fontSize=12.sp)}
                TextButton(onClick={access.disconnect{onFeedback(ActionFeedback("Account Google scollegato"))}},enabled=enabled&&!access.busy){Text("Disconnetti",color=TextSecondary,fontSize=12.sp)}
            }
        }
    }
}
