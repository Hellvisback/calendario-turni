package com.turni.calendario

import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.ui.screens.ActualExitEvaluation
import com.turni.calendario.ui.screens.calendarPermissionLabel
import com.turni.calendario.ui.screens.calendarShiftAccessibility
import com.turni.calendario.ui.screens.evaluateActualExitTime
import com.turni.calendario.ui.screens.resolvedShiftDisplayExitTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UnifiedShiftTimeTest {
    @Test
    fun evaluateActualExitTimeHandlesRegularEarlyAndOvertimeForDayShift() {
        val regular = evaluateActualExitTime("08:00", "16:00", 0, null, "16:00", 480L, 8f)
        assertEquals(ActualExitEvaluation(0f, 0f, true), regular)

        val early = evaluateActualExitTime("08:00", "16:00", 0, null, "14:30", 480L, 8f)
        assertTrue(early.valid)
        assertEquals(0f, early.overtimeHours, 0.001f)
        assertEquals(1.5f, early.permissionHours, 0.001f)

        val fullPermission = evaluateActualExitTime("08:00", "16:00", 0, null, "08:00", 480L, 8f)
        assertTrue(fullPermission.valid)
        assertEquals(0f, fullPermission.overtimeHours, 0.001f)
        assertEquals(8f, fullPermission.permissionHours, 0.001f)

        val overtime = evaluateActualExitTime("08:00", "16:00", 0, null, "17:45", 480L, 8f)
        assertTrue(overtime.valid)
        assertEquals(1.75f, overtime.overtimeHours, 0.001f)
        assertEquals(0f, overtime.permissionHours, 0.001f)

        val midnightWrapOvertime = evaluateActualExitTime("08:00", "16:00", 0, null, "02:00", 480L, 8f)
        assertTrue(midnightWrapOvertime.valid)
        assertEquals(10f, midnightWrapOvertime.overtimeHours, 0.001f)
        assertEquals(0f, midnightWrapOvertime.permissionHours, 0.001f)
    }

    @Test
    fun evaluateActualExitTimeEnforcesSixteenHourOvertimeCapAndRejectsInvalidInput() {
        val maxOvertime = evaluateActualExitTime("08:00", "12:00", 0, null, "04:00", 240L, 4f)
        assertTrue(maxOvertime.valid)
        assertEquals(16f, maxOvertime.overtimeHours, 0.001f)
        assertEquals(0f, maxOvertime.permissionHours, 0.001f)

        val beyondCap = evaluateActualExitTime("08:00", "12:00", 0, null, "04:01", 240L, 4f)
        assertFalse(beyondCap.valid)
        assertEquals(0f, beyondCap.overtimeHours, 0.001f)
        assertEquals(0f, beyondCap.permissionHours, 0.001f)

        assertFalse(evaluateActualExitTime("08:00", "12:00", 0, null, "07:59", 240L, 4f).valid)
        assertFalse(evaluateActualExitTime("08:00", "16:00", 0, null, "", 480L, 8f).valid)
        assertFalse(evaluateActualExitTime("08:00", "16:00", 0, null, "14:3", 480L, 8f).valid)
        assertFalse(evaluateActualExitTime("08:00", "16:00", 0, null, "25:00", 480L, 8f).valid)
    }

    @Test
    fun evaluateActualExitTimeHandlesOvernightShiftAcrossMidnight() {
        val regular = evaluateActualExitTime("22:00", "06:00", 0, null, "06:00", 480L, 8f)
        assertEquals(ActualExitEvaluation(0f, 0f, true), regular)

        val earlyBeforeMidnight = evaluateActualExitTime("22:00", "06:00", 0, null, "23:30", 480L, 8f)
        assertTrue(earlyBeforeMidnight.valid)
        assertEquals(0f, earlyBeforeMidnight.overtimeHours, 0.001f)
        assertEquals(6.5f, earlyBeforeMidnight.permissionHours, 0.001f)

        val earlyAfterMidnight = evaluateActualExitTime("22:00", "06:00", 0, null, "04:30", 480L, 8f)
        assertTrue(earlyAfterMidnight.valid)
        assertEquals(0f, earlyAfterMidnight.overtimeHours, 0.001f)
        assertEquals(1.5f, earlyAfterMidnight.permissionHours, 0.001f)

        val overtime = evaluateActualExitTime("22:00", "06:00", 0, null, "07:30", 480L, 8f)
        assertTrue(overtime.valid)
        assertEquals(1.5f, overtime.overtimeHours, 0.001f)
        assertEquals(0f, overtime.permissionHours, 0.001f)

        val longOvertime = evaluateActualExitTime("22:00", "06:00", 0, null, "21:30", 480L, 8f)
        assertTrue(longOvertime.valid)
        assertEquals(15.5f, longOvertime.overtimeHours, 0.001f)
        assertEquals(0f, longOvertime.permissionHours, 0.001f)
    }

    @Test
    fun evaluateActualExitTimeRespectsBreaks() {
        val earlyAfterBreak = evaluateActualExitTime("08:00", "17:00", 60, "12:00", "15:30", 480L, 8f)
        assertTrue(earlyAfterBreak.valid)
        assertEquals(0f, earlyAfterBreak.overtimeHours, 0.001f)
        assertEquals(1.5f, earlyAfterBreak.permissionHours, 0.001f)

        val earlyBeforeBreak = evaluateActualExitTime("08:00", "17:00", 60, "12:00", "11:00", 480L, 8f)
        assertTrue(earlyBeforeBreak.valid)
        assertEquals(0f, earlyBeforeBreak.overtimeHours, 0.001f)
        assertEquals(5f, earlyBeforeBreak.permissionHours, 0.001f)

        val earlyDuringBreak = evaluateActualExitTime("08:00", "17:00", 60, "12:00", "12:30", 480L, 8f)
        assertTrue(earlyDuringBreak.valid)
        assertEquals(0f, earlyDuringBreak.overtimeHours, 0.001f)
        assertEquals(4f, earlyDuringBreak.permissionHours, 0.001f)

        val overtimeAfterBreak = evaluateActualExitTime("08:00", "17:00", 60, "12:00", "18:15", 480L, 8f)
        assertTrue(overtimeAfterBreak.valid)
        assertEquals(1.25f, overtimeAfterBreak.overtimeHours, 0.001f)
        assertEquals(0f, overtimeAfterBreak.permissionHours, 0.001f)
    }

    @Test
    fun calendarPermissionLabelFormatsMinutesAndHours() {
        assertEquals("−0m", calendarPermissionLabel(0f))
        assertEquals("−15m", calendarPermissionLabel(0.25f))
        assertEquals("−30m", calendarPermissionLabel(0.5f))
        assertEquals("−1h", calendarPermissionLabel(1f))
        assertEquals("−1h30", calendarPermissionLabel(1.5f))
        assertEquals("−1h05", calendarPermissionLabel(65f / 60f))
        assertEquals("−8h", calendarPermissionLabel(8f))
        assertEquals("−0m", calendarPermissionLabel(-1f))
        assertEquals("−0m", calendarPermissionLabel(Float.NaN))
    }

    @Test
    fun calendarShiftAccessibilityDescribesPermissionAndPreservesCompatibility() {
        assertEquals(
            "Mattina, 06:00–14:00",
            calendarShiftAccessibility("Mattina", "06:00–14:00", 0f, false),
        )
        assertEquals(
            "Mattina, 06:00–14:00, permesso −1h30",
            calendarShiftAccessibility("Mattina", "06:00–14:00", 0f, false, permission = 1.5f),
        )
        assertEquals(
            "Mattina, 06:00–14:00, permesso −45m, trasferta",
            calendarShiftAccessibility("Mattina", "06:00–14:00", 0f, true, permission = 0.75f),
        )
        assertEquals(
            "Notte, 22:00–06:00, straordinario +1h, trasferta",
            calendarShiftAccessibility("Notte", "22:00–06:00", 1f, true),
        )
        assertEquals(
            "Mattina, 06:00–14:00, straordinario +30m, permesso −15m, trasferta",
            calendarShiftAccessibility("Mattina", "06:00–14:00", 0.5f, true, permission = 0.25f),
        )
    }

    @Test
    fun resolvedShiftDisplayExitTimeComputesPermissionExitAndPreservesActualEndTime() {
        val sampleDate = LocalDate.of(2026, 11, 22)
        val morningDef = ShiftDefinition(
            id = "mattina",
            name = "Mattina",
            shortCode = "M",
            startTime = "06:00",
            endTime = "14:00",
            colorHex = 0xFFEBC986,
        )
        val regularShift = AssignedShift(id = "s-reg", date = sampleDate, shiftDefId = "mattina")
        assertNull(resolvedShiftDisplayExitTime(regularShift, morningDef))

        val overtimeShift = AssignedShift(
            id = "s-ot",
            date = sampleDate,
            shiftDefId = "mattina",
            overtimeHours = 1.5f,
            actualEndTime = "15:30",
        )
        assertEquals("15:30", resolvedShiftDisplayExitTime(overtimeShift, morningDef))

        val permissionShift = AssignedShift(
            id = "s-perm",
            date = sampleDate,
            shiftDefId = "mattina",
            permissionHours = 1.5f,
            actualEndTime = null,
        )
        assertEquals("12:30", resolvedShiftDisplayExitTime(permissionShift, morningDef))

        val breakDef = morningDef.copy(
            startTime = "08:00",
            endTime = "17:00",
            breakMinutes = 60,
            breakStartTime = "12:00",
        )
        val afterBreakPerm = AssignedShift(
            id = "s-perm-after-break",
            date = sampleDate,
            shiftDefId = "mattina",
            permissionHours = 1.5f,
        )
        assertEquals("15:30", resolvedShiftDisplayExitTime(afterBreakPerm, breakDef))

        val beforeBreakPerm = AssignedShift(
            id = "s-perm-before-break",
            date = sampleDate,
            shiftDefId = "mattina",
            permissionHours = 5f,
        )
        assertEquals("11:00", resolvedShiftDisplayExitTime(beforeBreakPerm, breakDef))

        val allDayDef = morningDef.copy(isAllDay = true)
        assertNull(resolvedShiftDisplayExitTime(permissionShift, allDayDef))
    }
}
