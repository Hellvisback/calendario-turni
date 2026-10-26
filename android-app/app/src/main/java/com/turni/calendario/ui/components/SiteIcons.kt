package com.turni.calendario.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp
import com.turni.calendario.ui.theme.TextPrimary
import kotlin.math.*

/** Original 24-unit outline artwork and articulated motion from the approved prototype. */
private val sitePaths = mapOf(
    "message" to listOf("M4 4h16v12H9l-5 4V4ZM8 8h8M8 12h5"),
    "pipette" to listOf("m16 3 5 5M17.5 1.5l5 5-4 4-5-5ZM14 7 3 18v3h3L17 10M5 17l2 2"),
    "circle-plus" to listOf("M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0ZM12 8v8M8 12h8"),
    "calendar" to listOf("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM3 10h18", "M8 3v4M16 3v4", "M8 14h3M14 14h2M8 17h2"),
    "report" to listOf("M6 16v4", "M12 10v10", "M18 4v16"),
    "edit" to listOf("m14 5 2-2a2.1 2.1 0 0 1 3 0l2 2a2.1 2.1 0 0 1 0 3l-2 2", "m14 5 5 5-10 10-5-5ZM11.5 7.5l5 5", "m4 15-1 6 6-1"),
    "clock" to listOf("M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z", "M12 12V6", "m12 12 4 2"),
    "swap" to listOf("M4 7h15m-4-4 4 4-4 4", "M20 17H5m4-4-4 4 4 4"),
    "layers" to listOf("M13 13.74a2 2 0 0 1-2 0L2.5 8.87a1 1 0 0 1 0-1.74L11 2.26a2 2 0 0 1 2 0l8.5 4.87a1 1 0 0 1 0 1.74Z", "m20 14.285 1.5.845a1 1 0 0 1 0 1.74L13 21.74a2 2 0 0 1-2 0L2.5 16.87a1 1 0 0 1 0-1.74L4 14"),
    "rotation" to listOf("M16 5.0718A8 8 0 0 1 16 18.9282m.8785-3.2785-.8785 3.2785 3.2785.8785", "M8 18.9282A8 8 0 0 1 8 5.0718m-.8785 3.2785.8785-3.2785-3.2785-.8785"),
    "bell" to listOf("M10.27 21a2 2 0 0 0 3.46 0", "M3.26 15.33A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.67C19.41 13.96 18 12.5 18 8A6 6 0 0 0 6 8c0 4.5-1.41 5.96-2.74 7.33"),
    "plane" to listOf("m17.8 3.8-3.6 3.6-6.8-1.2L5 8.6l6 2.4-3.6 3.6-3-.6L2 16.4l5.2 1.8L9 23l2.4-2.4-.6-3L14.4 14l2.4 6 2.4-2.4L18 10.8l3.6-3.6a2.4 2.4 0 0 0-3.8-3.4Z"),
    "settings" to listOf("M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915", "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"),
    "eraser" to listOf("m16 3 6 6-12 12H5l-4-4L16 3ZM8 10l6 6M10 21h13"),
    "x" to listOf("m6 6 12 12M6 18 18 6"),
    "check" to listOf("m20 6-11 11-5-5"),
    "plus" to listOf("M12 5v14M5 12h14"),
    "minus" to listOf("M5 12h14"),
    "chevron-left" to listOf("m15 18-6-6 6-6"),
    "chevron-right" to listOf("m9 18 6-6-6-6"),
    "chevron-down" to listOf("m6 9 6 6 6-6"),
    "arrow-left" to listOf("m12 19-7-7 7-7M5 12h14"),
    "undo" to listOf("M3 10h10a7 7 0 0 1 0 14M3 10l5-5M3 10l5 5"),
    "trash" to listOf("M3 6h18M9 6V4h6v2M5 6l1 15h12l1-15M10 10v7M14 10v7"),
    "notebook" to listOf("M4 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H4V3ZM4 7H2M4 12H2M4 17H2M8 7h8M8 11h5M8 15h4"),
    "briefcase" to listOf("M12 12h.01M16 6V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2M22 13a18.15 18.15 0 0 1-20 0M4 6h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Z"),
    "building" to listOf("M3 21V7h8v14M11 21V3h10v18M1 21h22M6 10h2M6 14h2M6 18h2M14 6h3M14 10h3M14 14h3M14 18h3"),
    "users" to listOf("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z"),
    "laptop" to listOf("M4 3h16v13H4ZM2 20h20l-2-4H4Z"),
    "wrench" to listOf("M14.7 6.3a5.5 5.5 0 0 0-7 7l-5 5a2 2 0 0 0 3 3l5-5a5.5 5.5 0 0 0 7-7l-3 3-3-3 3-3Z"),
    "hospital" to listOf("M8 3h8v18H8ZM8 8H3v13h18V8h-5M11 6h2M12 5v2M11 11h2M11 15h2M11 19h2"),
    "truck" to listOf("M1 3h14v14H1ZM15 8h4l4 5v4h-8M8 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM22 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"),
    "train" to listOf("M8 3h8a4 4 0 0 1 4 4v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a4 4 0 0 1 4-4ZM4 11h16M8 3v8M16 3v8M8 16h.01M16 16h.01M7 20l-2 3M17 20l2 3"),
    "star" to listOf("m12 3 2.8 5.7 6.3.9-4.5 4.4 1 6.2L12 17.3l-5.6 2.9 1-6.2L3 9.6l6.2-.9Z"),
    "smartphone" to listOf("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2ZM10 18h4"),
    "moon" to listOf("M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13Z"),
    "sun" to listOf("M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM12 2v2M12 20v2M2 12h2M20 12h2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"),
    "timer" to listOf("M9 2h6M12 6v6l3 2M19 5l2 2M20 14a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z"),
    "alarm-clock" to listOf("M19 13a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z", "M12 9v4l2.5 1.5", "M2.5 5 6 2.5M18 2.5 21.5 5M7 19l-2 2M17 19l2 2"),
    "file-text" to listOf("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8ZM14 2v6h6M8 13h8M8 17h6"),
    "shield-alert" to listOf("M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6ZM12 8v5M12 16h.01"),
    "database-backup" to listOf("M21 5c0 2.2-4 4-9 4S3 7.2 3 5s4-4 9-4 9 1.8 9 4ZM3 5v12c0 2.2 4 4 9 4M3 11c0 2.2 4 4 9 4M21 5v5M16 14a4 4 0 1 1-2 6M14 16v4h4"),
    "palette" to listOf("M12 3a9 9 0 1 0 0 18h1a2 2 0 0 0 1-3.7 1.5 1.5 0 0 1 1-2.8h2A4 4 0 0 0 21 10a9 9 0 0 0-9-7ZM8 8h.01M12 6h.01M16 8h.01M6 12h.01"),
    "scan-text" to listOf("M4 7V4h3M17 4h3v3M20 17v3h-3M7 20H4v-3M7 8h10M7 12h10M7 16h6"),
    "copy" to listOf("M9 9h12v12H9ZM5 15H3V3h12v2"),
    "download" to listOf("M12 3v12m-5-5 5 5 5-5M5 17v4h14v-4"),
    "upload" to listOf("M12 17V5m-5 5 5-5 5 5M5 17v4h14v-4")
)
private fun canonicalSiteIcon(name:String)=when(name){
    "calendar-days","calendar-heart","calendar-sync","date-range","period"->"calendar"
    "bar-chart","bar-chart-3","statistics"->"report"
    "pencil","pencil-line"->"edit"
    "models","schedule","history"->"clock"
    "layers-2","multiple"->"layers"
    "repeat","rotate-cw","rotate-ccw"->"rotation"
    "arrow-left-right"->"swap"
    "bell-ring"->"bell"
    "gear"->"settings"
    "trash-2"->"trash"
    "notebook-pen","sticky-note","note"->"notebook"
    "briefcase-business","work"->"briefcase"
    "business","building-2"->"building"
    "factory","build"->"wrench"
    "transit"->"train"
    "shipping","local_shipping"->"truck"
    "groups","people"->"users"
    "train-front"->"train"
    "health","medical"->"hospital"
    "computer"->"laptop"
    "colorize"->"palette"
    "undo-2","reset"->"undo"
    else->name
}
private val parsedSitePaths=sitePaths.mapValues{(_,parts)->parts.map{PathParser().parsePathString(it).toNodes()}}
private val siteVectorCache=mutableMapOf<String,ImageVector>()
fun siteVector(name:String):ImageVector=siteVectorCache.getOrPut(name){buildSiteVector(canonicalSiteIcon(name),0f)}
private fun buildSiteVector(name:String,progress:Float):ImageVector {
    val p=progress.coerceIn(0f,1f);val wave=sin(p*PI).toFloat()
    return ImageVector.Builder("Site_$name",24.dp,24.dp,24f,24f).apply {
        val parts=parsedSitePaths[name]?:parsedSitePaths.getValue("briefcase")
        parts.forEachIndexed{i,nodes->
            var rotation=0f;var tx=0f;var ty=0f;var sx=1f;var sy=1f;var py=12f;var alpha=1f
            when(name){
                "calendar"->when(i){0->{sx=1f+sin(p*2*PI).toFloat()*-.015f;sy=sx};1->ty=-wave;2->{sy=1f-.4f*wave;py=10f;alpha=1f-.28f*wave}}
                "report"->{sy=1f+(listOf(.3f,-.2f,-.12f)[i])*wave;py=20f}
                "edit"->{rotation=-11f*wave;if(i==0){tx=-.8f*wave;ty=.8f*wave};if(i==2){tx=-1.2f*wave;ty=1.2f*wave}}
                "clock"->rotation=if(i==1)60f*wave else if(i==2)17f*wave else 0f
                "alarm-clock"->{rotation=if(i==1)35f*wave else if(i==2)sin(p*4*PI).toFloat()*5f*(1-p) else 0f}
                "swap"->tx=if(i==0)4f*wave else -4f*wave
                "layers"->ty=if(i==0)-2f*wave else 2f*wave
                "rotation"->rotation=-360f*p
                "settings"->rotation=90f*p
                "bell"->{rotation=sin(p*4*PI).toFloat()*14f*(1-p);py=4f}
                "plane"->{tx=if(p<.5f)12f*p else -10f*(1-p);ty=-tx;alpha=abs(2*p-1).coerceIn(0f,1f)}
                "eraser"->{tx=sin(p*2*PI).toFloat()*2f;rotation=-12f*wave}
                "undo"->rotation=-26f*wave
                "star"->{rotation=sin(p*2*PI).toFloat()*10f;sx=1f+.2f*wave;sy=sx}
                "chevron-left"->tx=-2.5f*wave
                "chevron-right"->tx=2.5f*wave
            }
            addGroup("part$i",rotation,12f,py,sx,sy,tx,ty)
            addPath(nodes,stroke=SolidColor(Color.Black),strokeAlpha=alpha,strokeLineWidth=if(name=="report")4.5f else 1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round)
            clearGroup()
        }
    }.build()
}
private val artworkCache=mutableMapOf<String,List<Path>>()
private fun artwork(name:String)=artworkCache.getOrPut(name){
    (sitePaths[name]?:sitePaths.getValue("briefcase")).map{PathParser().parsePathString(it).toPath()}
}

/** Path geometry is immutable; articulated motion changes only draw transforms. */
@Composable
fun SiteIcon(name:String,contentDescription:String?=null,modifier:Modifier=Modifier.size(24.dp),tint:Color=TextPrimary,progress:Float=0f){
    SiteIconMotion(name,contentDescription,modifier,tint){progress}
}

@Composable
fun SiteIconMotion(name:String,description:String?=null,modifier:Modifier=Modifier.size(24.dp),tint:Color=TextPrimary,progress:()->Float){
    val canonical=canonicalSiteIcon(name)
    val parts=remember(canonical){artwork(canonical)}
    val stroke=remember(canonical){Stroke(if(canonical=="report")4.5f else 1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round)}
    Canvas(modifier.semantics{if(description!=null)contentDescription=description}) {
        val p=progress().coerceIn(0f,1f);val wave=sin(p*PI).toFloat()
        scale(size.width/24f,size.height/24f,pivot=Offset.Zero){
            parts.forEachIndexed{i,path->
                var rotation=0f;var tx=0f;var ty=0f;var sx=1f;var sy=1f;var py=12f;var alpha=1f
                when(canonical){
                    "calendar"->when(i){0->{sx=1f+sin(p*2*PI).toFloat()*-.015f;sy=sx};1->ty=-wave;2->{sy=1f-.4f*wave;py=10f;alpha=1f-.28f*wave}}
                    "report"->{sy=1f+(when(i){0->.3f;1->-.2f;else->-.12f})*wave;py=20f}
                    "edit"->{rotation=-11f*wave;if(i==0){tx=-.8f*wave;ty=.8f*wave};if(i==2){tx=-1.2f*wave;ty=1.2f*wave}}
                    "clock"->rotation=if(i==1)60f*wave else if(i==2)17f*wave else 0f
                "alarm-clock"->{rotation=if(i==1)35f*wave else if(i==2)sin(p*4*PI).toFloat()*5f*(1-p) else 0f}
                    "swap"->tx=if(i==0)4f*wave else -4f*wave
                    "layers"->ty=if(i==0)-2f*wave else 2f*wave
                    "rotation"->rotation=-360f*p
                    "settings"->rotation=90f*p
                    "bell"->{rotation=sin(p*4*PI).toFloat()*14f*(1-p);py=4f}
                    "plane"->{tx=if(p<.5f)12f*p else -10f*(1-p);ty=-tx;alpha=abs(2*p-1).coerceIn(0f,1f)}
                    "eraser"->{tx=sin(p*2*PI).toFloat()*2f;rotation=-12f*wave}
                    "undo"->rotation=-26f*wave
                    "star"->{rotation=sin(p*2*PI).toFloat()*10f;sx=1f+.2f*wave;sy=sx}
                    "chevron-left"->tx=-2.5f*wave
                    "chevron-right"->tx=2.5f*wave
                }
                translate(tx,ty){rotate(rotation,Offset(12f,py)){scale(sx,sy,Offset(12f,py)){
                    drawPath(path,tint.copy(alpha=tint.alpha*alpha),style=stroke)
                }}}
            }
        }
    }
}
