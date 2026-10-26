package com.turni.calendario

import androidx.compose.runtime.mutableStateListOf
import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.widget.WidgetUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class WidgetMultiShiftTest {
    private lateinit var original: com.turni.calendario.repository.DatabaseSnapshot

    @Before
    fun setUp() {
        val captured = com.turni.calendario.repository.DatabaseSnapshot.capture()
        val modelIds = captured.definitions.map { it.id }.toSet()
        // Isolate widgets from incomplete synthetic cycles created by unrelated mapping stress tests.
        original = captured.copy(rotations = captured.rotations.filter { rotation ->
            rotation.daySequence.all { it.isBlank() || it in modelIds }
        })
        com.turni.calendario.sync.DatabaseRestore.apply(original)
        ShiftRepository.calendarShifts.clear()
        ShiftRepository.dayExtraInfo.clear()
    }

    @org.junit.After fun restoreRepository() { com.turni.calendario.sync.DatabaseRestore.apply(original) }

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

    @Test
    fun erasedOrUnassignedDayDoesNotInventAnImplicitRotation() {
        val date = LocalDate.of(2026, 10, 17)
        assertNull(WidgetUpdateManager.getDaySummary(date).shiftDef)
        val definition=ShiftDefinition("widget_erase", "Test", "TST", workProfileId=ShiftRepository.activeWorkProfileId)
        ShiftRepository.saveShiftDefinition(definition)
        ShiftRepository.saveRotation(com.turni.calendario.model.ShiftRotation("widget_cycle","Test",1,listOf(definition.id)))
        ShiftRepository.applyRotation("widget_cycle", date, 1)
        assertEquals(definition.id, WidgetUpdateManager.getDaySummary(date).shiftDef?.id)
        ShiftRepository.clearDayShifts(date)
        val erased = WidgetUpdateManager.getDaySummary(date)
        assertNull(erased.shiftDef)
        assertEquals("—", erased.shortCode)
        assertFalse(erased.hasMultipleShifts)
    }

    @Test
    fun summaryUsesSelectedWorkAndPerShiftExtrasInsteadOfDayTotals() {
        val date = LocalDate.of(2026, 10, 17)
        val profileId = ShiftRepository.activeWorkProfileId
        val first = ShiftDefinition("widget_own_1", "Primo", "UNO", startTime = "06:00", workProfileId = profileId)
        val second = ShiftDefinition("widget_own_2", "Secondo", "DUE", workProfileId = profileId)
        ShiftRepository.addWorkProfile(com.turni.calendario.model.WorkProfile("widget_other_work", "Altro lavoro", isDefault = false))
        val foreign = ShiftDefinition("widget_foreign", "Altro lavoro", "ALT", workProfileId = "widget_other_work")
        listOf(first, second, foreign).forEach { ShiftRepository.saveShiftDefinition(it) }
        ShiftRepository.calendarShifts[date] = mutableStateListOf(
            AssignedShift("foreign", date, foreign.id, overtimeHours = 12f, isTrasferta = true),
            AssignedShift("own_1", date, first.id, customStartTime = "07:15", overtimeHours = .5f, notes = "Nota personale"),
            AssignedShift("own_2", date, second.id, overtimeHours = 1.25f, isTrasferta = true)
        )
        ShiftRepository.dayExtraInfo[date] = com.turni.calendario.model.DayExtraInfo(date, overtimeHours = 99f)
        val result = WidgetUpdateManager.getDaySummary(date, date)
        assertEquals("UNO", result.shortCode)
        assertEquals("07:15", result.timeRange)
        assertEquals(1.75f, result.overtimeHours, .001f)
        assertEquals(listOf("own_1", "own_2"), result.shifts.map { it.id })
        assertFalse(result.shifts.first().isTrasferta)
        assertTrue(result.shifts.last().isTrasferta)
        assertEquals("Nota personale", result.notes)
        assertTrue(result.accessibilityLabel.contains("Secondo"))
        assertFalse(result.accessibilityLabel.contains("Altro lavoro"))
    }

    @Test
    fun summaryDoesNotInventAllDayHoursOrAnUnassignedRestShift() {
        val date = LocalDate.of(2026, 10, 17)
        val definition = ShiftDefinition("widget_all_day", "Ferie", "FER", isAllDay = true,
            workProfileId = ShiftRepository.activeWorkProfileId)
        ShiftRepository.saveShiftDefinition(definition)
        ShiftRepository.calendarShifts[date] = mutableStateListOf(AssignedShift("all_day", date, definition.id))
        assertEquals("— —", WidgetUpdateManager.getDaySummary(date).timeRange)
        assertTrue(WidgetUpdateManager.getDaySummary(date.plusDays(1)).shifts.isEmpty())
    }

    @Test
    fun widgetOvertimeUsesExactMinutesAndHandlesInvalidNumbers() {
        assertEquals("+15m", com.turni.calendario.widget.widgetOvertimeLabel(.25f))
        assertEquals("+1h", com.turni.calendario.widget.widgetOvertimeLabel(1f))
        assertEquals("+1h05", com.turni.calendario.widget.widgetOvertimeLabel(65f / 60f))
        assertEquals("+1h30", com.turni.calendario.widget.widgetOvertimeLabel(1.5f))
        assertEquals("+0m", com.turni.calendario.widget.widgetOvertimeLabel(Float.NaN))
        assertEquals("+0m", com.turni.calendario.widget.widgetOvertimeLabel(-1f))
    }
}
