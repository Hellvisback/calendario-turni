package com.turni.calendario.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.turni.calendario.model.WorkProfile
import java.io.File
import kotlin.math.roundToInt

fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = (hue % 360f + 360f) % 360f
    val c = value * saturation
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = value - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}

fun colorToLong(color: Color): Long {
    val a = (color.alpha * 255).roundToInt().toLong().coerceIn(0, 255)
    val r = (color.red * 255).roundToInt().toLong().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().toLong().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().toLong().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

fun colorToHex(color: Color): String {
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

fun parseHexToColor(hex: String): Color? {
    val clean = hex.removePrefix("#").trim()
    return try {
        if (clean.length == 6) {
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            Color(r, g, b)
        } else if (clean.length == 8) {
            val a = clean.substring(0, 2).toInt(16)
            val r = clean.substring(2, 4).toInt(16)
            val g = clean.substring(4, 6).toInt(16)
            val b = clean.substring(6, 8).toInt(16)
            Color(r, g, b, a)
        } else null
    } catch (e: Exception) {
        null
    }
}

fun openWorkProfilePdf(context: Context, profile: WorkProfile) {
    val file = profile.ccnlDocumentUri?.let { File(it) }
        ?.takeIf { it.exists() && it.length() > 0 }
        ?: File(context.filesDir, "ccnl_${profile.id}.pdf")
    val hasValidFile = file.exists() && file.length() > 0
    if (hasValidFile) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "CCNL - ${profile.name}"))
        } catch (e: Exception) {
            Toast.makeText(context, "Nessun lettore PDF trovato sul dispositivo", Toast.LENGTH_LONG).show()
        }
    } else {
        Toast.makeText(context, "Nessun file PDF allegato per ${profile.name}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Risolve l'icona appropriata in base al settore lavorativo / profilo.
 */
fun getWorkProfileIcon(iconKey: String): ImageVector {
    return when (iconKey) {
        "people" -> Icons.Default.PeopleOutline
        "computer" -> Icons.Default.Computer
        "work" -> Icons.Default.WorkOutline
        "transit" -> Icons.Default.DirectionsTransit
        "hospital" -> Icons.Default.LocalHospital
        "factory" -> Icons.Default.PrecisionManufacturing
        "security" -> Icons.Default.Security
        "shipping" -> Icons.Default.LocalShipping
        "store" -> Icons.Default.Storefront
        else -> Icons.Default.Business
    }
}

