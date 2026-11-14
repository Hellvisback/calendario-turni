package com.turni.calendario

import com.turni.calendario.ui.screens.calendarStartLabel
import com.turni.calendario.data.AiRosterImport
import org.junit.Assert.*
import org.junit.Test

class ClockFormatTest {
    @Test fun twelveHourClockSeparatesUppercasePeriodAtDayBoundaries() {
        assertEquals("12:00 AM",calendarStartLabel("00:00",false))
        assertEquals("12:00 PM",calendarStartLabel("12:00",false))
        assertEquals("11:59 PM",calendarStartLabel("23:59",false))
        assertEquals("6:05 AM",calendarStartLabel("06:05",false))
        assertEquals("2:00 PM",calendarStartLabel("14:00",false))
    }
    @Test fun twentyFourHourAndUnknownValuesArePreserved() {
        assertEquals("06:05",calendarStartLabel("06:05",true))
        assertEquals("invalid",calendarStartLabel("invalid",false))
    }
    @Test fun externalAiMustReturnCompletePasteableTextNotDownload() {
        val prompt=AiRosterImport.prompt("Name Example")
        assertTrue(prompt.contains("copiabile e incollabile"))
        assertTrue(prompt.contains("Non creare file"))
        assertTrue(prompt.contains("Non troncare"))
        assertFalse(prompt.contains("prepara un file"))
        assertFalse(prompt.contains("scaricabile"))
    }
}
