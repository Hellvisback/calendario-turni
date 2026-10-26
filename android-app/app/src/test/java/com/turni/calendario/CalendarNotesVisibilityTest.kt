package com.turni.calendario

import com.turni.calendario.model.AssignedShift
import com.turni.calendario.ui.screens.calendarHasNotes
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarNotesVisibilityTest {
    private val shift=AssignedShift("a",LocalDate.of(2026,10,26),"custom")
    @Test fun dayAndShiftNotesBothIdentifyTheDay() {
        assertTrue(calendarHasNotes("Nota del giorno",emptyList()))
        assertTrue(calendarHasNotes("",listOf(shift.copy(notes="Nota del turno"))))
        assertFalse(calendarHasNotes("  ",listOf(shift.copy(notes="\n"))))
    }
    @Test fun overtimeAndTravelNeverHideANote() {
        assertTrue(calendarHasNotes("",listOf(shift.copy(notes="Visita",overtimeHours=1f,isTrasferta=true,actualEndTime="15:00"))))
        assertTrue(calendarHasNotes("Giornata",listOf(shift.copy(overtimeHours=2f))))
    }
    @Test fun allAssignmentsParticipateAndTheMarkerDisappearsAfterLastNoteRemoval() {
        val second=shift.copy(id="b",notes="Secondo turno")
        assertTrue(calendarHasNotes("",listOf(shift,second)))
        assertFalse(calendarHasNotes("",listOf(shift,second.copy(notes=""))))
    }
}
