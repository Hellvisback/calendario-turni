package com.turni.calendario.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.ui.components.*
import com.turni.calendario.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared calendar body: same hierarchy in compact anchored popovers and PDF sheets. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun AppDateGrid(month:YearMonth,selected:LocalDate,changeMonth:(YearMonth)->Unit,
    compact:Boolean=false,rangeStart:LocalDate?=null,rangeEnd:LocalDate?=null,select:(LocalDate)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={if(month>YearMonth.of(1900,1))changeMonth(month.minusMonths(1))},modifier=Modifier.size(36.dp)) {
                SiteIcon("chevron-left","Mese precedente",Modifier.size(18.dp))
            }
            Text(month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ITALIAN)),
                Modifier.weight(1f),color=TextPrimary,fontSize=if(compact)13.sp else 16.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
            IconButton(onClick={if(month<YearMonth.of(2200,12))changeMonth(month.plusMonths(1))},modifier=Modifier.size(36.dp)) {
                SiteIcon("chevron-right","Mese successivo",Modifier.size(18.dp))
            }
        }
        Row(Modifier.fillMaxWidth()) { listOf("L","M","M","G","V","S","D").forEach {
            Text(it,Modifier.weight(1f).padding(vertical=4.dp),color=TextMuted,fontSize=10.sp,textAlign=TextAlign.Center)
        } }
        AnimatedContent(month,transitionSpec={fadeIn(tween(160))+slideInHorizontally(tween(220)){if(targetState>initialState)it/8 else -it/8} with fadeOut(tween(120)) using SizeTransform(clip=true)}) { shown ->
            val offset=shown.atDay(1).dayOfWeek.value-1
            Column {
                repeat((offset+shown.lengthOfMonth()+6)/7) { row-> Row(Modifier.fillMaxWidth()) {
                    repeat(7) { column->
                        val day=row*7+column-offset+1
                        val date=if(day in 1..shown.lengthOfMonth())shown.atDay(day)else null
                        if(date==null)Spacer(Modifier.weight(1f).height(if(compact)34.dp else 40.dp)) else {
                            val chosen=date==selected
                            val within=rangeStart!=null&&rangeEnd!=null&&date in minOf(rangeStart,rangeEnd)..maxOf(rangeStart,rangeEnd)
                            val tone by animateColorAsState(if(chosen)ObsidianPrimary else if(within)ObsidianPrimarySubtle else ObsidianSurface,spring(1f,650f))
                            val shape=RoundedCornerShape(10.dp)
                            Box(Modifier.weight(1f).height(if(compact)34.dp else 40.dp).padding(1.dp).clip(shape).background(tone)
                                .border(if(date==LocalDate.now()&&!chosen)1.dp else 0.dp,if(date==LocalDate.now()&&!chosen)ObsidianPrimary else androidx.compose.ui.graphics.Color.Transparent,shape)
                                .bouncyClickable { select(date) },contentAlignment=Alignment.Center) {
                                Text(day.toString(),color=if(chosen)OnBrand else TextPrimary,fontSize=if(compact)12.sp else 14.sp,fontWeight=if(chosen)FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                } }
            }
        }
    }
}

/** Animated app surface instead of the platform's generic alert layout. */
@Composable
internal fun SelectorSheet(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.(close:()->Unit)->Unit) {
    NativeBottomDialog(onDismiss,bottomInset=0.dp,dimAmount=.22f) { close->
        Row(Modifier.fillMaxWidth().padding(bottom=14.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),color=TextPrimary,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(ObsidianSurfaceContainer).bouncyClickable(onClick=close),contentAlignment=Alignment.Center) {
                SiteIcon("x","Chiudi",Modifier.size(19.dp),TextSecondary)
            }
        }
        content(close)
    }
}
