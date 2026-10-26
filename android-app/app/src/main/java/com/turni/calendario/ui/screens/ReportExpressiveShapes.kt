package com.turni.calendario.ui.screens

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.*

// Exact 160x160 silhouettes from the approved Sites module2/view.js.
internal val reportShapeKeys = listOf("flower", "circle", "squircle", "cloud", "cushion", "softDiamond", "clover", "scallop", "hexagon", "octagon", "arch", "pebble")
private val reportShapeData = mapOf(
    "flower" to "M80 15C99-1 122 6 127 30C151 32 162 56 146 80C162 102 151 127 127 130C123 153 99 162 80 145C59 162 35 151 31 129C7 126-2 102 14 80C-2 59 8 34 32 31C35 7 58-2 80 15Z",
    "squircle" to "M80 3C112 3 133 5 146 18C157 31 157 53 157 80C157 110 155 132 143 145C130 157 107 157 80 157C49 157 28 155 16 143C3 130 3 109 3 80C3 49 5 28 18 16C31 3 53 3 80 3Z",
    "cloud" to "M30 35C31 14 56 4 76 15C94-2 123 6 128 30C150 31 163 54 152 74C166 96 151 120 136 123C130 150 105 160 84 148C63 164 34 151 31 130C8 126-1 104 11 83C-2 62 8 40 30 35Z",
    "cushion" to "M59 12H101Q148 12 148 59V101Q148 148 101 148H59Q12 148 12 101V59Q12 12 59 12Z",
    "softDiamond" to "M80 3C99 3 109 20 124 35C139 50 157 61 157 80C157 99 139 110 124 125C109 140 99 157 80 157C61 157 51 140 36 125C21 110 3 99 3 80C3 61 21 50 36 35C51 20 61 3 80 3Z",
    "clover" to "M80 17C113-8 151 8 143 44C168 68 157 105 136 113C136 151 101 166 80 143C51 166 17 148 23 116C-6 97-2 60 20 47C13 11 48-6 80 17Z",
    "hexagon" to "M55 5H105Q120 5 130 20L151 54Q160 68 154 89L136 133Q130 151 110 154H50Q30 151 24 133L6 89Q0 68 9 54L30 20Q40 5 55 5Z",
    "octagon" to "M46 4H114Q124 4 132 12L148 28Q156 36 156 46V114Q156 124 148 132L132 148Q124 156 114 156H46Q36 156 28 148L12 132Q4 124 4 114V46Q4 36 12 28L28 12Q36 4 46 4Z",
    "arch" to "M4 80C4 37 38 4 80 4S156 37 156 80V128Q156 156 128 156H32Q4 156 4 128Z",
    "pebble" to "M36 10C70-2 110 3 137 24C160 45 164 87 148 120C132 151 97 163 61 151C26 151 1 123 5 88C0 56 9 24 36 10Z"
)
internal fun reportShapePath(key: String): Path {
    if (key == "circle") return Path().apply { addOval(Rect(3f, 3f, 157f, 157f)) }
    if (key == "cushion") return Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(12f,12f,148f,148f), androidx.compose.ui.geometry.CornerRadius(47f))) }
    if (key != "scallop") return PathParser().parsePathString(reportShapeData[key] ?: reportShapeData.getValue("squircle")).toPath()
    fun point(t: Double): DoubleArray {
        val r=74+4*cos(8*t); val derivative=-32*sin(8*t)
        return doubleArrayOf(80+r*cos(t),80+r*sin(t),derivative*cos(t)-r*sin(t),derivative*sin(t)+r*cos(t))
    }
    val step=PI*2/64; var p=point(0.0)
    return Path().apply {
        moveTo(p[0].toFloat(),p[1].toFloat())
        for(index in 1..64) {
            val q=point(index*step)
            cubicTo((p[0]+p[2]*step/3).toFloat(),(p[1]+p[3]*step/3).toFloat(),(q[0]-q[2]*step/3).toFloat(),(q[1]-q[3]*step/3).toFloat(),q[0].toFloat(),q[1].toFloat())
            p=q
        }
        close()
    }
}
internal fun reportShapePlan(keys: List<String>): List<String> {
    var seed=2166136261L.toInt()
    for(char in keys.firstOrNull() ?: "TurniSync") seed=(seed xor char.code)*16777619
    fun random(): Double { seed=seed xor(seed shl 13);seed=seed xor(seed ushr 17);seed=seed xor(seed shl 5);return (seed.toLong() and 0xffffffffL)/4294967296.0 }
    val result=mutableListOf<String>(); var bag=mutableListOf<String>()
    keys.forEach {
        if(bag.isEmpty()) { bag=reportShapeKeys.toMutableList();for(i in bag.lastIndex downTo 1) { val j=floor(random()*(i+1)).toInt();val old=bag[i];bag[i]=bag[j];bag[j]=old } }
        val candidate=bag.indexOfFirst { it != result.lastOrNull() && it != result.getOrNull(result.size-2) }.coerceAtLeast(0)
        result.add(bag.removeAt(candidate))
    }
    return result
}
