package com.turni.calendario
import androidx.compose.ui.graphics.Color
import com.turni.calendario.ui.screens.panelKeyboardClearance
import com.turni.calendario.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ShiftPanelPresentationTest {
    @Test fun keyboardConsumesDockGapWithoutAddingASheetGapOrAnEntryJump() {
        assertEquals(86f,panelKeyboardClearance(86f,0f,24f),.001f)
        assertEquals(110f,50f+panelKeyboardClearance(86f,50f,24f),.001f)
        assertEquals(8f,panelKeyboardClearance(86f,270f,24f),.001f)
        assertEquals(0f,panelKeyboardClearance(0f,270f,24f),.001f)
    }
    @Test fun activeTravelIsDistinctFromOffAndItsGlyphIsReadable() {
        for(dark in listOf(false,true))for(style in PaletteStyle.values()) {
            val palette=turniPalette(dark,style)
            assertTrue(contrastRatio(Color(palette.travel.fill),Color(palette.surface))>=3f)
            assertTrue(contrastRatio(Color(palette.travel.ink),Color(palette.travel.fill))>=4.5f)
        }
    }
}
