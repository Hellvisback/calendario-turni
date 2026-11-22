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
    "alert-triangle" to listOf("M12 3 22 21H2L12 3ZM12 9v5M12 17h.01"),
    "tag" to listOf("M3 3h8l10 10-8 8L3 11V3ZM7 7h.01"),
    "graduation-cap" to listOf("m2 9 10-5 10 5-10 5L2 9ZM6 11v6c4 3 8 3 12 0v-6M22 9v8"),
    "heart-pulse" to listOf("M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8ZM3 12h5l2-4 4 8 2-4h5"),
    "book-open" to listOf("M12 5v15M12 5C8 2 4 3 2 4v15c4-1 7-1 10 1 3-2 6-2 10-1V4c-2-1-6-2-10 1Z"),
    "smile" to listOf("M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0ZM8 9h.01M16 9h.01M7 14c3 5 7 5 10 0"),
    "flag" to listOf("M4 22V3c6-4 10 4 16 0v12c-6 4-10-4-16 0"),
    "folder-open" to listOf("M3 8V5a2 2 0 0 1 2-2h4l2 3h8a2 2 0 0 1 2 2v2", "M2 10h20l-3 10H5L2 10Z"),
    "search" to listOf("M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0ZM15 15l6 6"),
    "message" to listOf("M4 4h16v12H9l-5 4V4ZM8 8h8M8 12h5"),
    "pipette" to listOf("m16 3 5 5M17.5 1.5l5 5-4 4-5-5ZM14 7 3 18v3h3L17 10M5 17l2 2"),
    "circle-plus" to listOf("M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0ZM12 8v8M8 12h8"),
    "calendar" to listOf("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM3 10h18", "M8 3v4M16 3v4", "M8 14h3M14 14h2M8 17h2"),
    "calendar-heart" to listOf(
        "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM3 10h18",
        "M8 3v4M16 3v4",
        "M12 13.3 C11.1 11.9 8.6 12.1 8.6 14.2 C8.6 15.8 10.2 17.0 12 18.7 C13.8 17.0 15.4 15.8 15.4 14.2 C15.4 12.1 12.9 11.9 12 13.3 Z"
    ),
    "calendar-sync" to listOf(
        "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM3 10h18",
        "M8 3v4M16 3v4",
        "M9.2 13.9 A3.2 3.2 0 0 1 15.1 14.7 M15.4 13.2 L15.1 14.7 L13.6 14.5",
        "M14.8 17.1 A3.2 3.2 0 0 1 8.9 16.3 M8.6 17.8 L8.9 16.3 L10.4 16.5"
    ),
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
    "plus" to listOf("M12 5v14", "M5 12h14"),
    "minus" to listOf("M5 12h14"),
    "chevron-left" to listOf("m15 18-6-6 6-6"),
    "chevron-right" to listOf("m9 18 6-6-6-6"),
    "chevron-down" to listOf("m6 9 6 6 6-6"),
    "chevron-up" to listOf("m6 15 6-6 6 6"),
    "arrow-left" to listOf("m12 19-7-7 7-7M5 12h14"),
    "arrow-right" to listOf("m12 5 7 7-7 7M5 12h14"),
    "undo" to listOf("M3 10h10a7 7 0 0 1 0 14M3 10l5-5M3 10l5 5"),
    "trash" to listOf("M3 6h18M9 6V4h6v2", "M5 6l1 15h12l1-15M10 10v7M14 10v7"),
    "notebook" to listOf("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5V3ZM5 7H3M5 12H3M5 17H3M9 7h8M9 11h5M9 15h4"),
    "briefcase" to listOf("M22 13a18.15 18.15 0 0 1-20 0M4 6h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Z", "M16 6V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2", "M12 12h.01"),
    "building" to listOf("M3 21V7h8v14M11 21V3h10v18M1 21h22", "M6 10h2M6 14h2M6 18h2", "M14 6h3M14 10h3M14 14h3M14 18h3"),
    "users" to listOf("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z", "M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"),
    "laptop" to listOf("M4 3h16v13H4Z", "M2 20h20l-2-4H4Z"),
    "wrench" to listOf("M14.7 6.3a5.5 5.5 0 0 0-7 7l-5 5a2 2 0 0 0 3 3l5-5a5.5 5.5 0 0 0 7-7l-3 3-3-3 3-3Z"),
    "hospital" to listOf("M8 3h8v18H8ZM8 8H3v13h18V8h-5M11 11h2M11 15h2M11 19h2", "M11 6h2M12 5v2"),
    "truck" to listOf("M1 3h14v14H1ZM15 8h4l4 5v4h-8", "M8 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0ZM22 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z"),
    "train" to listOf("M8 3h8a4 4 0 0 1 4 4v10a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V7a4 4 0 0 1 4-4ZM4 11h16M8 3v8M16 3v8", "M8 16h.01M16 16h.01", "M7 20l-2 3M17 20l2 3"),
    "star" to listOf("m12 3 2.8 5.7 6.3.9-4.5 4.4 1 6.2L12 17.3l-5.6 2.9 1-6.2L3 9.6l6.2-.9Z"),
    "smartphone" to listOf("M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Z", "M10 18h4"),
    "moon" to listOf("M20.9 13A9 9 0 0 1 11 3.1 9 9 0 1 0 20.9 13Z"),
    "sun" to listOf("M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z", "M12 2v2M12 20v2M2 12h2M20 12h2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"),
    "timer" to listOf("M20 14a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z", "M9 2h6M19 5l2 2", "M12 6v6l3 2"),
    "alarm-clock" to listOf("M19 13a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z", "M12 9v4l2.5 1.5", "M2.5 5 6 2.5M18 2.5 21.5 5", "M7 19l-2 2M17 19l2 2"),
    "file-text" to listOf("M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8ZM14 2v6h6", "M8 13h8", "M8 17h6"),
    "shield-alert" to listOf("M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6Z", "M12 8v5M12 16h.01"),
    "cloud" to listOf("M20 16a4 4 0 0 0-1-7.87A7 7 0 0 0 5.3 8.4 4 4 0 0 0 6 16Z"),
    "database-backup" to listOf("M21 5c0 2.2-4 4-9 4S3 7.2 3 5s4-4 9-4 9 1.8 9 4ZM3 5v12c0 2.2 4 4 9 4M3 11c0 2.2 4 4 9 4M21 5v5", "M16 14a4 4 0 1 1-2 6M14 16v4h4"),
    "palette" to listOf("M12 3a9 9 0 1 0 0 18h1a2 2 0 0 0 1-3.7 1.5 1.5 0 0 1 1-2.8h2A4 4 0 0 0 21 10a9 9 0 0 0-9-7Z", "M8 8h.01", "M12 6h.01", "M16 8h.01", "M6 12h.01"),
    "scan-text" to listOf("M4 7V4h3M17 4h3v3M20 17v3h-3M7 20H4v-3", "M7 8h10", "M7 12h10", "M7 16h6"),
    "copy" to listOf("M9 9h12v12H9Z", "M5 15H3V3h12v2"),
    "download" to listOf("M12 3v12m-5-5 5 5 5-5", "M5 17v4h14v-4"),
    "upload" to listOf("M12 17V5m-5 5 5-5 5 5", "M5 17v4h14v-4")
)
private fun canonicalSiteIcon(name:String)=when(name){
    "calendar-days","date-range","period"->"calendar"
    "chart-no-axes-combined","bar-chart","bar-chart-3","statistics"->"report"
    "pencil","pencil-line"->"edit"
    "models","schedule","history"->"clock"
    "folder"->"folder-open"
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
private data class SitePartTransform(
    val rotation: Float = 0f,
    val px: Float = 12f,
    val py: Float = 12f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    val tx: Float = 0f,
    val ty: Float = 0f,
    val alpha: Float = 1f,
)
private fun pulseWindow(p: Float, start: Float, span: Float): Float =
    sin(((p - start) / span).coerceIn(0f, 1f) * PI).toFloat()

private fun sitePartTransform(name: String, i: Int, p: Float, wave: Float): SitePartTransform {
    var rotation = 0f; var px = 12f; var py = 12f; var sx = 1f; var sy = 1f; var tx = 0f; var ty = 0f; var alpha = 1f
    when (name) {
        "calendar" -> when (i) {
            0 -> { sx = 1f + sin(p * 2 * PI).toFloat() * -.015f; sy = sx }
            1 -> ty = -wave
            2 -> { sy = 1f - .4f * wave; py = 10f; alpha = 1f - .28f * wave }
        }
        "calendar-heart" -> when (i) {
            0 -> {
                ty = -1.2f * wave
                sx = 1f + .06f * wave; sy = sx
            }
            1 -> ty = -2.1f * wave
            2 -> {
                py = 15.5f
                ty = -1.0f * wave
                val beat1 = pulseWindow(p, 0.06f, 0.34f)
                val beat2 = pulseWindow(p, 0.42f, 0.44f)
                sx = 1f + 0.32f * beat1 + 0.42f * beat2
                sy = sx
            }
        }
        "calendar-sync" -> when (i) {
            0 -> { ty = -1.1f * wave; sx = 1f + .05f * wave; sy = sx }
            1 -> ty = -2.1f * wave
            2 -> { py = 15.5f; ty = -1.0f * wave; rotation = 360f * p; sx = 1f + .14f * wave; sy = sx }
            3 -> { py = 15.5f; ty = -1.0f * wave; rotation = -360f * p; sx = 1f + .14f * wave; sy = sx }
        }
        "report" -> {
            val barWave = pulseWindow(p, i * 0.08f, 0.84f)
            sy = 1f + (listOf(.42f, -.34f, .26f)[i]) * barWave
            py = 20f
        }
        "edit" -> { rotation = -11f * wave; if (i == 0) { tx = -.8f * wave; ty = .8f * wave }; if (i == 2) { tx = -1.2f * wave; ty = 1.2f * wave } }
        "clock" -> rotation = if (i == 1) 60f * wave else if (i == 2) 17f * wave else 0f
        "alarm-clock" -> {
            px = 12f; py = 13f
            when (i) {
                0 -> {
                    ty = -.8f * wave
                    rotation = sin(p * 6 * PI).toFloat() * 3.5f * wave
                    sx = 1f + .05f * wave; sy = sx
                }
                1 -> {
                    ty = -.8f * wave
                    rotation = 65f * wave
                }
                2 -> {
                    ty = -1.4f * wave
                    rotation = sin(p * 6 * PI).toFloat() * 11f * (1f - 0.25f * p)
                }
                else -> ty = -.3f * wave
            }
        }
        "swap" -> tx = if (i == 0) 4f * wave else -4f * wave
        "layers" -> ty = if (i == 0) -2f * wave else 2f * wave
        "rotation" -> rotation = -360f * p
        "settings" -> rotation = 90f * p
        "bell" -> {
            val ring = sin(p * 4 * PI).toFloat() * (1f - 0.5f * p)
            py = 3.5f
            if (i == 0) {
                rotation = ring * 26f
            } else {
                rotation = ring * 23.5f
                sx = 1f + .05f * wave; sy = sx
            }
        }
        "plane" -> { tx = if (p < .5f) 12f * p else -10f * (1 - p); ty = -tx; alpha = abs(2 * p - 1).coerceIn(0f, 1f) }
        "eraser" -> { tx = sin(p * 2 * PI).toFloat() * 2f; rotation = -12f * wave }
        "undo" -> rotation = -26f * wave
        "tag", "graduation-cap", "heart-pulse", "book-open", "smile", "flag" -> { rotation = 4f * wave; sy = 1f + .06f * wave }
        "star" -> { rotation = sin(p * 2 * PI).toFloat() * 10f; sx = 1f + .2f * wave; sy = sx }
        "chevron-left" -> tx = -2.5f * wave
        "chevron-right" -> tx = 2.5f * wave
        "chevron-up" -> ty = -2.5f * wave
        "chevron-down" -> ty = 2.5f * wave
        "arrow-left" -> tx = -2f * wave
        "arrow-right" -> tx = 2f * wave
        "download" -> if (i == 0) { ty = 2.4f * wave; sy = 1f + .08f * wave } else { ty = .6f * wave; sx = 1f + .05f * wave; py = 19f }
        "upload" -> if (i == 0) { ty = -2.4f * wave; sy = 1f + .08f * wave } else { ty = .5f * wave; sx = 1f + .05f * wave; py = 19f }
        "plus" -> { rotation = 90f * p; sx = 1f + .16f * wave; sy = sx }
        "minus" -> { sx = 1f - .22f * wave; sy = 1f + .12f * wave }
        "check" -> { px = 11f; py = 13f; sx = 1f + .22f * wave; sy = sx; rotation = -6f * sin(p * 2 * PI).toFloat() }
        "copy" -> { val d = 1.2f * wave; if (i == 0) { tx = d; ty = d } else { tx = -d; ty = -d } }
        "trash" -> if (i == 0) { py = 6f; ty = -3.4f * wave; rotation = -22f * wave } else { py = 14f; sy = 1f - .08f * wave; sx = 1f + .05f * wave; ty = .8f * wave }
        "sun" -> if (i == 0) { sx = 1f + .12f * wave; sy = sx } else { rotation = 90f * p; sx = 1f + .06f * wave; sy = sx }
        "moon" -> { rotation = sin(p * 2 * PI).toFloat() * 14f; sx = 1f + .06f * wave; sy = sx }
        "smartphone" -> {
            rotation = sin(p * 3 * PI).toFloat() * 7f * (1f - .4f * p)
            sx = 1f + .05f * wave; sy = sx
            if (i == 1) ty = -.7f * wave
        }
        "cloud" -> { ty = -1.8f * wave; tx = sin(p * 2 * PI).toFloat() * 1.1f; sx = 1f + .06f * wave; sy = 1f - .03f * wave }
        "timer" -> when (i) {
            0 -> { py = 14f; sx = 1f + .04f * wave; sy = sx }
            1 -> ty = 1.2f * wave
            2 -> { py = 14f; rotation = 360f * p }
        }
        "shield-alert" -> if (i == 0) {
            rotation = sin(p * 3 * PI).toFloat() * 6f * (1f - .4f * p)
            sx = 1f + .04f * wave; sy = sx
        } else {
            ty = -1.1f * wave; sx = 1f + .14f * wave; sy = sx
        }
        "palette" -> when (i) {
            0 -> { rotation = -18f * sin(p * 2 * PI).toFloat(); sx = 1f + .08f * wave; sy = sx }
            else -> {
                val pivots = listOf(8f to 8f, 12f to 6f, 16f to 8f, 6f to 12f)
                val (dx, dy) = pivots[(i - 1).coerceIn(0, 3)]
                val dotWave = pulseWindow(p, (i - 1) * 0.14f, 0.48f)
                px = dx; py = dy
                sx = 1f + .85f * dotWave; sy = sx
                ty = -1.1f * dotWave
            }
        }
        "scan-text" -> if (i == 0) {
            sx = 1f + .10f * wave; sy = sx
        } else {
            val scan = pulseWindow(p, (i - 1) * 0.14f, 0.58f)
            px = 7f; py = (8 + (i - 1) * 4).toFloat()
            sx = 1f - .52f * scan
            alpha = 1f - .55f * scan
            tx = 2.6f * scan
        }
        "file-text" -> if (i == 0) {
            ty = -1.8f * wave; sx = 1f + .06f * wave; sy = sx
        } else {
            val lineWave = pulseWindow(p, (i - 1) * 0.16f, 0.62f)
            px = 8f; py = if (i == 1) 13f else 17f
            ty = -1.8f * wave
            tx = 1.6f * lineWave
            sx = 1f - .48f * lineWave
            alpha = 1f - .45f * lineWave
        }
        "database-backup" -> if (i == 0) {
            ty = -1.4f * wave; sx = 1f + .05f * wave; sy = sx
        } else {
            px = 17.3f; py = 17.8f; rotation = -360f * p; sx = 1f + .16f * wave; sy = sx
        }
        "briefcase" -> when (i) {
            0 -> { ty = -.8f * wave; rotation = 3f * sin(p * 2 * PI).toFloat() }
            1 -> ty = -1.7f * wave
            2 -> { py = 12f; ty = -.8f * wave; sx = 1f + .45f * wave; sy = sx }
        }
        "building" -> {
            py = 21f; sy = 1f + .06f * wave
            if (i == 1) alpha = 1f - .38f * sin(p * 2 * PI).toFloat().coerceAtLeast(0f)
            if (i == 2) alpha = 1f - .38f * (-sin(p * 2 * PI).toFloat()).coerceAtLeast(0f)
        }
        "users" -> if (i == 0) {
            px = 9f; py = 14f; ty = -.9f * wave; sx = 1f + .05f * wave; sy = sx
        } else {
            tx = 1.2f * wave; ty = -.5f * wave
        }
        "laptop" -> if (i == 0) {
            py = 16f; sy = 1f + .08f * wave; ty = -.6f * wave
        } else {
            py = 20f; sx = 1f + .04f * wave
        }
        "wrench" -> {
            rotation = -24f * sin(p * 2 * PI).toFloat()
            sx = 1f + .06f * wave; sy = sx
        }
        "hospital" -> if (i == 0) {
            py = 21f; sy = 1f + .05f * wave
        } else {
            py = 6f; sx = 1f + .35f * wave; sy = sx
        }
        "truck" -> {
            tx = sin(p * 2 * PI).toFloat() * 1.6f
            if (i == 0) ty = -.8f * abs(sin(p * 2 * PI).toFloat())
        }
        "train" -> when (i) {
            0 -> { ty = -.9f * wave; sx = 1f + .05f * wave; sy = sx }
            1 -> { ty = -.9f * wave; sx = 1f + .35f * wave; sy = sx; alpha = .55f + .45f * abs(cos(p * 2 * PI).toFloat()) }
            2 -> { py = 21f; sx = 1f + .05f * wave }
        }
        "folder-open" -> if (i == 0) {
            ty = -1.2f * wave
        } else {
            py = 10f; sy = 1f - .14f * wave; sx = 1f + .06f * wave
        }
    }
    return SitePartTransform(rotation, px, py, sx, sy, tx, ty, alpha)
}

private val parsedSitePaths=sitePaths.mapValues{(_,parts)->parts.map{PathParser().parsePathString(it).toNodes()}}
private val siteVectorCache=mutableMapOf<String,ImageVector>()
fun siteVector(name:String):ImageVector=siteVectorCache.getOrPut(name){buildSiteVector(canonicalSiteIcon(name),0f)}
private fun buildSiteVector(name:String,progress:Float):ImageVector {
    val p=progress.coerceIn(0f,1f);val wave=sin(p*PI).toFloat()
    return ImageVector.Builder("Site_$name",24.dp,24.dp,24f,24f).apply {
        val parts=parsedSitePaths[name]?:parsedSitePaths.getValue("briefcase")
        parts.forEachIndexed{i,nodes->
            val t=sitePartTransform(name,i,p,wave)
            addGroup("part$i",t.rotation,t.px,t.py,t.sx,t.sy,t.tx,t.ty)
            addPath(nodes,stroke=SolidColor(Color.Black),strokeAlpha=t.alpha,strokeLineWidth=if(name=="report")4.5f else 1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round)
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
                val t=sitePartTransform(canonical,i,p,wave)
                translate(t.tx,t.ty){rotate(t.rotation,Offset(t.px,t.py)){scale(t.sx,t.sy,Offset(t.px,t.py)){
                    drawPath(path,tint.copy(alpha=tint.alpha*t.alpha),style=stroke)
                }}}
            }
        }
    }
}
