package com.turni.calendario.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Union geometry avoids overlapping seams while the satellites merge horizontally. */
@Composable
fun TurniDock(calendarSelected: Boolean, onCalendar: () -> Unit, onStatistics: () -> Unit,
    onEdit: () -> Unit, onModels: () -> Unit, modifier: Modifier = Modifier, primarySelected:Boolean=true) {
    val progress by animateFloatAsState(if (calendarSelected) 1f else 0f, spring(.9f, 220f))
    val selected by animateFloatAsState(if (calendarSelected) 1f else 0f, spring(.9f, 360f))
    val activeAlpha by animateFloatAsState(if(primarySelected)1f else 0f,spring(1f,400f))
    val body=ObsidianSurfaceHigh;val active=ObsidianPrimary
    Box(modifier.width(260.dp).height(72.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx=size.width/2;val cy=size.height/2;val p=progress.coerceIn(0f,1f)
            val r=(32f+sin(p*Math.PI).toFloat()*.8f).dp.toPx();val gap=29.dp.toPx()
            var shape=Path().apply {
                addOval(Rect(cx-gap-r,cy-r,cx-gap+r,cy+r));addOval(Rect(cx+gap-r,cy-r,cx+gap+r,cy+r))
                addRect(Rect(cx-gap,cy-r,cx+gap,cy+r))
            }
            if(p>.001f) for(sign in listOf(-1,1)) {
                val x=cx+sign*(60f+40f*p).dp.toPx();val radius=(18f+4f*p).dp.toPx()
                shape=Path.combine(PathOperation.Union,shape,Path().apply{addOval(Rect(x-radius,cy-radius,x+radius,cy+radius))})
                if(p<.98f) {
                    val a=cx+sign*55.dp.toPx();val b=x-sign*radius*.6f;val neck=(12f*(1f-p)).dp.toPx()
                    val bridge=Path().apply {
                        moveTo(a,cy-20.dp.toPx());cubicTo((a+b)/2,cy-neck,b,cy-neck,b,cy-radius*.65f)
                        lineTo(b,cy+radius*.65f);cubicTo(b,cy+neck,(a+b)/2,cy+neck,a,cy+20.dp.toPx());close()
                    }
                    shape=Path.combine(PathOperation.Union,shape,bridge)
                }
            }
            drawPath(shape,body);drawCircle(active.copy(alpha=activeAlpha),29.dp.toPx(),Offset(cx+gap*(selected*2-1),cy))
        }
        DockAction(Icons.Default.BarChart,"Statistiche",Modifier.offset(x=(-29).dp).size(58.dp),!calendarSelected&&primarySelected,onStatistics)
        DockAction(Icons.Default.CalendarMonth,"Calendario",Modifier.offset(x=29.dp).size(58.dp),calendarSelected&&primarySelected,onCalendar)
        if(progress>.02f) {
            DockAction(Icons.Default.Edit,"Modifica turni",Modifier.offset(x=(-(60+40*progress)).dp).size(48.dp)
                .graphicsLayer{alpha=progress},false,onEdit)
            DockAction(Icons.Default.Schedule,"Turni e lavori",Modifier.offset(x=(60+40*progress).dp).size(48.dp)
                .graphicsLayer{alpha=progress},false,onModels)
        }
    }
}

@Composable
private fun DockAction(icon:ImageVector,label:String,modifier:Modifier,selected:Boolean,onClick:()->Unit) {
    val motion=remember{Animatable(0f)};val scope=rememberCoroutineScope()
    Box(modifier.bouncyClickable {
        scope.launch{motion.snapTo(0f);motion.animateTo(1f,tween(660));motion.snapTo(0f)};onClick()
    },contentAlignment=Alignment.Center) {
        Icon(icon,label,tint=if(selected) ObsidianBackground else TextPrimary,
            modifier=Modifier.size(25.dp).graphicsLayer {
                rotationZ=sin(motion.value*Math.PI).toFloat()*if(label=="Turni e lavori")22f else 3f
                translationY=-sin(motion.value*Math.PI).toFloat()*1.dp.toPx()
            })
    }
}
