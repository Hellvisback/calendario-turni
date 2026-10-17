package com.turni.calendario

import androidx.compose.runtime.mutableStateListOf
import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.widget.WidgetUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class WidgetMultiShiftTest {

    @Before
    fun setUp() {
        ShiftRepository.calendarShifts.clear()
    }

    @Test
    fun testMultiShiftSummary_dotsAndNoBullet() {
        val date = LocalDate.of(2026, 10, 17)
        val shift1 = ShiftDefinition(id = "mat_custom", name = "Mattina", shortCode = "MAT", colorHex = 0xFF0284C7)
        val shift2 = ShiftDefinition(id = "pom_custom", name = "Pomeriggio", shortCode = "POM", colorHex = 0xFFEF4444)
        ShiftRepository.saveShiftDefinition(shift1)
        ShiftRepository.saveShiftDefinition(shift2)

        // 1 singolo turno
        ShiftRepository.calendarShifts[date] = mutableStateListOf(
            AssignedShift(id = "s1", date = date, shiftDefId = shift1.id)
        )
        val summarySingle = WidgetUpdateManager.getDaySummary(date)
        assertEquals("MAT", summarySingle.shortCode)
        assertEquals("MAT", summarySingle.displayShortCode)
        assertFalse(summarySingle.hasMultipleShifts)
        assertTrue(summarySingle.secondaryShiftColors.isEmpty())

        // 2 turni su quel giorno
        ShiftRepository.calendarShifts[date] = mutableStateListOf(
            AssignedShift(id = "s1", date = date, shiftDefId = shift1.id),
            AssignedShift(id = "s2", date = date, shiftDefId = shift2.id)
        )
        val summaryMulti = WidgetUpdateManager.getDaySummary(date)
        // displayShortCode non deve contenere pallini testuali
        assertEquals("MAT", summaryMulti.shortCode)
        assertEquals("MAT", summaryMulti.displayShortCode)
        assertTrue(summaryMulti.hasMultipleShifts)
        assertEquals(1, summaryMulti.secondaryShiftColors.size)
        assertEquals(0xFFEF4444.toInt(), summaryMulti.secondaryShiftColors[0])
    }
}
