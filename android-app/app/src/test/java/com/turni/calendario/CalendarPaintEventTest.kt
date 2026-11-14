package com.turni.calendario
import com.turni.calendario.ui.screens.calendarPaintPending
import org.junit.Assert.*
import org.junit.Test

class CalendarPaintEventTest {
    @Test fun onlyANewUnconsumedRequestStartsAnEntryWave() {
        assertTrue(calendarPaintPending(3L,2L,true))
        assertFalse(calendarPaintPending(3L,3L,true))
        assertFalse(calendarPaintPending(null,3L,true))
        assertFalse(calendarPaintPending(4L,3L,false))
    }
}
