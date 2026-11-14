package com.turni.calendario
import com.turni.calendario.ui.screens.calendarAnnotationIcon
import org.junit.Assert.*
import org.junit.Test

class CalendarAnnotationIndicatorTest {
    @Test fun notesTakePriorityOverTagsAndEmptyDaysHaveNoIndicator() {
        assertNull(calendarAnnotationIcon(false,false))
        assertEquals("tag",calendarAnnotationIcon(false,true))
        assertEquals("message",calendarAnnotationIcon(true,false))
        assertEquals("message",calendarAnnotationIcon(true,true))
    }
}
