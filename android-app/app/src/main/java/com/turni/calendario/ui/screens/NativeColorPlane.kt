package com.turni.calendario.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Continuous saturation/value input, shared by both work and shift editors. */
@Composable
internal fun NativeColorPlane(hue:Float,saturation:Float,brightness:Float,onChange:(Float,Float)->Unit) {
    var width by remember{mutableStateOf(1f)};var height by remember{mutableStateOf(1f)}
    val update by rememberUpdatedState(onChange)
    fun select(point:Offset){update((point.x/width).coerceIn(0f,1f),(1-point.y/height).coerceIn(0f,1f))}
    Canvas(Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(16.dp)).onSizeChanged{width=it.width.toFloat();height=it.height.toFloat()}
        .semantics {
            contentDescription="Saturazione e luminosità"
            stateDescription="Saturazione ${(saturation*100).toInt()}%, luminosità ${(brightness*100).toInt()}%"
            customActions=listOf(
                CustomAccessibilityAction("Aumenta saturazione"){update((saturation+.05f).coerceAtMost(1f),brightness);true},
                CustomAccessibilityAction("Riduci saturazione"){update((saturation-.05f).coerceAtLeast(0f),brightness);true},
                CustomAccessibilityAction("Aumenta luminosità"){update(saturation,(brightness+.05f).coerceAtMost(1f));true},
                CustomAccessibilityAction("Riduci luminosità"){update(saturation,(brightness-.05f).coerceAtLeast(0f));true})
        }
        .pointerInput(Unit){detectTapGestures{select(it)}}
        .pointerInput(Unit){detectDragGestures(onDragStart={select(it)},onDrag={change,_->change.consume();select(change.position)})}) {
        drawRect(Brush.horizontalGradient(listOf(Color.White,hsvToColor(hue,1f,1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
        val center=Offset((saturation*size.width).coerceIn(7.dp.toPx(),size.width-7.dp.toPx()),((1-brightness)*size.height).coerceIn(7.dp.toPx(),size.height-7.dp.toPx()))
        drawCircle(Color.Black.copy(alpha=.4f),7.dp.toPx(),center,style=Stroke(3.dp.toPx()))
        drawCircle(Color.White,6.dp.toPx(),center,style=Stroke(2.dp.toPx()))
    }
}
