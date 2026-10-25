package com.turni.calendario.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object TurniThemeState {
    var dark by mutableStateOf(true)
        private set
    fun initialize(context: Context) { dark = context.getSharedPreferences("turnisync_theme", 0).getBoolean("dark", true) }
    fun setDark(context: Context, value: Boolean) {
        dark = value
        context.getSharedPreferences("turnisync_theme", 0).edit().putBoolean("dark", value).apply()
    }
}
private fun themed(dark: Long, light: Long) = Color(if (TurniThemeState.dark) dark else light)
val ObsidianBackground get() = themed(0xFF101114, 0xFFF4F6F9)
val ObsidianSurface get() = themed(0xFF1A1C22, 0xFFFFFFFF)
val ObsidianSurfaceContainer get() = themed(0xFF22242C, 0xFFEDF0F6)
val ObsidianSurfaceHigh get() = themed(0xFF262932, 0xFFE6EBF4)
val ObsidianSurfaceHighest get() = themed(0xFF323540, 0xFFDEE5F0)
val ObsidianBorder get() = themed(0xFF383B46, 0xFFD7DEEA)
val ObsidianBorderSubtle get() = themed(0xFF2C2F38, 0xFFE8ECF3)
val ObsidianBorderActive get() = ObsidianPrimary
val ObsidianPrimary get() = themed(0xFFBCB8FF, 0xFF3866E8)
val ObsidianPrimaryContainer get() = themed(0xFF454064, 0xFFDFE6FF)
val ObsidianPrimarySubtle get() = themed(0xFF302D45, 0xFFEBEFFF)
val ObsidianSecondary get() = themed(0xFFC3BBE9, 0xFF655195)
val ObsidianTertiary get() = themed(0xFF83CDB7, 0xFF166B56)
val TextPrimary get() = themed(0xFFF4F3F7, 0xFF202431)
val TextSecondary get() = themed(0xFFBEC1CD, 0xFF545D70)
val TextMuted get() = themed(0xFFADB1C0, 0xFF657086)
val PrimaryGradient get() = Brush.horizontalGradient(listOf(ObsidianPrimary, ObsidianSecondary))
val AmberGradient = Brush.horizontalGradient(listOf(Color(0xFFD97706), Color(0xFFF2C46D)))
val EmeraldGradient = Brush.horizontalGradient(listOf(Color(0xFF166B56), Color(0xFF83CDB7)))
val PurpleGradient = Brush.horizontalGradient(listOf(Color(0xFF655195), Color(0xFFBAAEF6)))
val RoseGradient = Brush.horizontalGradient(listOf(Color(0xFFB94059), Color(0xFFEE9C9C)))
val ColorMattina = Color(0xFFF2C46D)
val ColorPomeriggio = Color(0xFFEE9C9C)
val ColorNotte = Color(0xFFBAAEF6)
val ColorRiposo = Color(0xFFAAB9CE)
val ColorFerie = Color(0xFF94C4EF)
val ColorSmontoNotte = Color(0xFF83CDB7)
val ColorGiornaliero = Color(0xFF83CDB7)
val ColorMatDom = Color(0xFFF2C46D)
val ColorUffici = Color(0xFFAAB9CE)
val ColorDonazione = Color(0xFFEE9C9C)
val ColorMalattia = Color(0xFFB4D995)
val ColorReperibilita get() = themed(0xFFF4CA70, 0xFF805100)
val ColorTrasferta get() = themed(0xFF94C4EF, 0xFF235C98)

val ColorNegative get() = themed(0xFFFFA1AB, 0xFFB3263C)
