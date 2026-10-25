package com.turni.calendario.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val TurniEraserIcon:ImageVector by lazy {
    ImageVector.Builder("Eraser",24.dp,24.dp,24f,24f).apply {
        path(fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,
            strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) {
            moveTo(15.5f,3f);lineTo(22f,9.5f);lineTo(10.5f,21f);lineTo(5f,21f)
            lineTo(1.5f,17.5f);close();moveTo(8f,11f);lineTo(14.5f,17.5f)
            moveTo(10.5f,21f);lineTo(23f,21f)
        }
    }.build()
}
