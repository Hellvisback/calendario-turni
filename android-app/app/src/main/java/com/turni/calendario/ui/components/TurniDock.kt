package com.turni.calendario.ui.components

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import com.turni.calendario.R
import com.turni.calendario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.DataInputStream
import kotlin.math.*

private class DockContours(frames:Array<FloatArray>,step:Float){
    // Prepare immutable native paths on the loader's IO thread. The UI thread never emits
    // 1,500 path commands per frame; 181 phases retain sub-pixel motion at phone sizes.
    private val paths=Array(181){phase->
        val position=phase.toFloat()/180f*(frames.size-1)
        val a=position.toInt();val b=min(a+1,frames.lastIndex);val mix=position-a
        val heights=FloatArray(frames[0].size){i->frames[a][i]+(frames[b][i]-frames[a][i])*mix}
        Path().apply{
            var start=-1
            fun close(end:Int){if(start>=0){for(j in end downTo start)lineTo(j*step,heights[j]);close();start=-1}}
            for(i in heights.indices){val h=heights[i]
                if(h>.001f){if(start<0){start=i;moveTo(i*step,-h)}else lineTo(i*step,-h)}else close(i-1)
            };close(heights.lastIndex)
        }
    }
    fun path(progress:Float):Path=paths[(progress.coerceIn(0f,1f)*180f).roundToInt()]
}
private object DockContourAssets{
    @Volatile var cache:DockContours?=null
    fun load(context:Context):DockContours=cache?:synchronized(this){cache?:context.resources.openRawResource(R.raw.dock_contours).use{raw->
        DataInputStream(raw.buffered()).use{input->val count=input.readInt();val samples=input.readInt();val step=input.readFloat();input.readFloat();DockContours(Array(count){FloatArray(samples){input.readFloat()}},step).also{cache=it}}
    }}
}
private fun smooth(a:Float,b:Float,value:Float):Float {val p=((value-a)/(b-a)).coerceIn(0f,1f);return p*p*(3-2*p)}

/** Original contour, baked off-device. Animation state is read only in draw/placement phases. */
@Composable
fun TurniDock(calendarSelected:Boolean,onCalendar:()->Unit,onStatistics:()->Unit,onEdit:()->Unit,onModels:()->Unit,modifier:Modifier=Modifier,primarySelected:Boolean=true){
    // A timed material response leaves enough frames for the neck to form and pinch off.
    // Only the baked contour, placement and icon opacity observe this animation.
    val progress=animateFloatAsState(if(calendarSelected)1f else 0f,
        tween(if(calendarSelected)740 else 660,easing=FastOutSlowInEasing))
    val selected=animateFloatAsState(if(calendarSelected)1f else 0f,spring(.9f,360f))
    val activeAlpha=animateFloatAsState(if(primarySelected)1f else 0f)
    val context=LocalContext.current.applicationContext
    val contours by produceState(DockContourAssets.cache,context){if(value==null)value=withContext(Dispatchers.IO){DockContourAssets.load(context)}}
    val showSatellites by remember{derivedStateOf{progress.value>.015f}}
    val body=DockBody;val active=ObsidianPrimary
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal=8.dp).height(92.dp),contentAlignment=Alignment.Center){
        val width=maxWidth.value;val scale=width/380f;val left=width*.425f;val right=width*.575f
        Canvas(Modifier.fillMaxSize()){
            val unit=size.width/width
            drawContext.canvas.save();drawContext.canvas.scale(unit,unit)
            if(contours!=null){
                drawContext.canvas.save();drawContext.canvas.translate(0f,46f);drawContext.canvas.scale(scale,scale)
                drawPath(contours!!.path(progress.value),body);drawContext.canvas.restore()
            }else{
                // Brief first-frame fallback while the tiny asset is read on IO.
                drawRoundRect(body,Offset(left-34*scale,46-34*scale),androidx.compose.ui.geometry.Size(right-left+68*scale,68*scale),androidx.compose.ui.geometry.CornerRadius(34*scale))
                if(calendarSelected)listOf(left-71*scale,right+71*scale).forEach{drawCircle(body,23*scale,Offset(it,46f))}
            }
            drawCircle(active.copy(alpha=activeAlpha.value),34*scale-3f,Offset(left+(right-left)*selected.value,46f));drawContext.canvas.restore()
        }
        DockAction("report","Statistiche",Modifier.offset(x=(left-width/2).dp).size(44.dp).zIndex(1f),!calendarSelected&&primarySelected,onStatistics)
        DockAction("calendar","Calendario",Modifier.offset(x=(right-width/2).dp).size(44.dp).zIndex(1f),calendarSelected&&primarySelected,onCalendar)
        if(showSatellites){
            DockAction("edit","Modifica turni",Modifier.offset{IntOffset(((left-width/2-71*scale*smooth(.06f,.96f,progress.value)).dp).roundToPx(),0)}.size(44.dp).graphicsLayer{alpha=smooth(.10f,.56f,progress.value)},false,onEdit)
            DockAction("clock","Turni e lavori",Modifier.offset{IntOffset(((right-width/2+71*scale*smooth(.06f,.96f,progress.value)).dp).roundToPx(),0)}.size(44.dp).graphicsLayer{alpha=smooth(.10f,.56f,progress.value)},false,onModels)
        }
    }
}
@Composable
private fun DockAction(name:String,label:String,modifier:Modifier,selected:Boolean,onClick:()->Unit){
    val motion=remember{Animatable(0f)};val scope=rememberCoroutineScope()
    Box(modifier.bouncyClickable{scope.launch{motion.stop();motion.snapTo(0f);motion.animateTo(1f,tween(if(name=="clock")760 else 720,easing=FastOutSlowInEasing));motion.snapTo(0f)};onClick()},contentAlignment=Alignment.Center){
        SiteIconMotion(name,label,Modifier.size(23.dp),if(selected)OnBrand else DockInk){motion.value}
    }
}
