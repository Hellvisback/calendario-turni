package com.turni.calendario.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turni.calendario.ui.theme.TurniThemeState
import kotlinx.coroutines.delay
import kotlin.math.*

internal data class StatusEdgePoint(val x:Float,val y:Float,val nx:Float,val ny:Float)

/** Uniform arc-length sampling starts on the actual rounded-rectangle border. */
internal fun statusEdgePoint(width:Float,height:Float,radius:Float,fraction:Float):StatusEdgePoint {
    val r=radius.coerceIn(.001f,min(width,height).coerceAtLeast(.002f)/2f)
    val horizontal=(width-2*r).coerceAtLeast(0f);val vertical=(height-2*r).coerceAtLeast(0f);val arc=PI.toFloat()*r/2f
    val segments=floatArrayOf(horizontal,arc,vertical,arc,horizontal,arc,vertical,arc)
    var distance=((fraction%1f+1f)%1f)*segments.sum();var segment=0
    while(segment<7&&distance>segments[segment])distance-=segments[segment++]
    return when(segment){
        0->StatusEdgePoint(r+distance,0f,0f,-1f)
        2->StatusEdgePoint(width,r+distance,1f,0f)
        4->StatusEdgePoint(width-r-distance,height,0f,1f)
        6->StatusEdgePoint(0f,height-r-distance,-1f,0f)
        else->{
            val angle=when(segment){1->-PI/2;3->0.0;5->PI/2;else->PI}+distance/r
            val nx=cos(angle).toFloat();val ny=sin(angle).toFloat()
            StatusEdgePoint((if(segment==1||segment==3)width-r else r)+nx*r,(if(segment==1||segment==7)r else height-r)+ny*r,nx,ny)
        }
    }
}

/** Message-sized, theme-aware overlay. Its last content survives the entire exit animation. */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ActionStatusPopup(feedback:ActionFeedback?,onDismiss:()->Unit,modifier:Modifier=Modifier){
    var retained by remember{mutableStateOf(feedback)}
    var visible by remember{mutableStateOf(false)}
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(feedback?.timestamp){
        if(feedback!=null){retained=feedback;visible=true;delay(2300);visible=false;dismiss()}
        else {visible=false;delay(300);retained=null}
    }
    AnimatedVisibility(visible,modifier=modifier.padding(horizontal=22.dp,vertical=18.dp),
        enter=slideInVertically(spring(.88f,360f)){-it/3}+fadeIn(tween(210)),
        exit=slideOutVertically(tween(240,easing=FastOutSlowInEasing)){-it/3}+fadeOut(tween(220))){
        retained?.let{item->key(item.timestamp){
            val dark=TurniThemeState.dark;val destructive=item.isDestructive
            val background=if(destructive){if(dark)Color(0xFF3D2029)else Color(0xFFFFEBEE)}else{if(dark)Color(0xFF18382C)else Color(0xFFE5F7ED)}
            val ink=if(destructive){if(dark)Color(0xFFFFE1E5)else Color(0xFF93263C)}else{if(dark)Color(0xFFDAF9E8)else Color(0xFF194D35)}
            val border=if(destructive){if(dark)Color(0xFF81434F)else Color(0xFFE9AEB9)}else{if(dark)Color(0xFF38664E)else Color(0xFFB2D9C2)}
            val icon=remember{Animatable(0f)}
            LaunchedEffect(Unit){icon.animateTo(1f,tween(520,easing=FastOutSlowInEasing))}
            Box(contentAlignment=Alignment.Center){
                Surface(shape=RoundedCornerShape(16.dp),color=background,border=BorderStroke(1.dp,border),
                    modifier=Modifier.widthIn(max=320.dp).semantics{liveRegion=LiveRegionMode.Polite}){
                    Row(Modifier.defaultMinSize(minHeight=44.dp).padding(horizontal=14.dp,vertical=10.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                        SiteIcon(if(destructive)"trash" else "check",null,Modifier.size(20.dp).graphicsLayer{
                            val wave=sin(icon.value*PI).toFloat();scaleX=1f+.12f*wave;scaleY=scaleX
                            rotationZ=if(destructive)sin(icon.value*2*PI).toFloat()*8f else 0f
                        },tint=ink)
                        Text(item.message,Modifier.weight(1f,fill=false),color=ink,fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)
                    }
                }
                StatusPerimeterBurst(destructive,Modifier.matchParentSize())
            }
        }}
    }
}

@Composable
private fun StatusPerimeterBurst(destructive:Boolean,modifier:Modifier){
    var bounds by remember{mutableStateOf(IntSize.Zero)}
    val density=LocalDensity.current.density
    val flight=remember{Animatable(0f)}
    val points=remember(bounds,density){
        if(bounds.width==0||bounds.height==0)emptyList() else{
            val perimeter=2*(bounds.width+bounds.height)/density-8*16+2*PI.toFloat()*16
            val count=(perimeter/38).roundToInt().coerceIn(10,14)
            List(count){statusEdgePoint(bounds.width.toFloat(),bounds.height.toFloat(),16*density,(it+.4f)/count)}
        }
    }
    val colors=remember(destructive){if(destructive)listOf(Color(0xFFFF8995),Color(0xFFFFB1A8))else listOf(Color(0xFF70D6AC),Color(0xFFA9E9CE),Color(0xFFEBCE83))}
    LaunchedEffect(Unit){delay(140);flight.animateTo(1f,tween(850,easing=LinearEasing))}
    Canvas(modifier.onSizeChanged{bounds=it}){
        points.forEachIndexed{i,point->
            val p=((flight.value-i%3*.03f)/.9f).coerceIn(0f,1f)
            if(p>0f&&p<1f){
                val alpha=sin(p*PI).toFloat()*.9f;val reach=(2f+(14f+i%3*3f)*FastOutSlowInEasing.transform(p))*density
                val position=Offset(point.x+point.nx*reach,point.y+point.ny*reach)
                val scale=.4f+.6f*sin(p*PI).toFloat();val width=3*density*scale;val height=(if(i%3==0)5 else 3)*density*scale
                rotate((if(i%2==0)-65 else 65)*p,position){
                    drawRoundRect(colors[i%colors.size].copy(alpha=alpha),position-Offset(width/2,height/2),Size(width,height),CornerRadius(if(i%2==0)density else width/2))
                }
            }
        }
    }
}
