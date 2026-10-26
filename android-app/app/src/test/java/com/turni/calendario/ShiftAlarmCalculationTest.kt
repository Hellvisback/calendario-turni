package com.turni.calendario

import com.turni.calendario.alarm.ShiftAlarmScheduler
import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository as Repository
import com.turni.calendario.sync.DatabaseRestore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ShiftAlarmCalculationTest {
    private lateinit var original: DatabaseSnapshot
    private val day = LocalDate.of(2027, 5, 12)

    @Before fun prepare() {
        val captured = DatabaseSnapshot.capture()
        val modelIds = captured.definitions.map { it.id }.toSet()
        // Mapping stress suites leave synthetic cycles behind; this fixture retains only complete cycles.
        original = captured.copy(rotations = captured.rotations.filter { rotation ->
            rotation.daySequence.all { it.isBlank() || it in modelIds }
        })
        DatabaseRestore.apply(original)
        Repository.calendarShifts.clear()
        Repository.dayExtraInfo.clear()
        Repository.activeWorkProfileId = "principale"
        Repository.reperibilitaConfig = ReperibilitaConfig(mode = ReperibilitaMode.MANUALE)
        Repository.smartAlarmsConfig = SmartAlarmsConfig(rules = listOf(ShiftAlarmRule("mattina"), ShiftAlarmRule("notte")))
    }

    @After fun restore() { DatabaseRestore.apply(original) }

    @Test fun unassignedAndErasedDatesNeverScheduleImplicitRotationAlarms() {
        val now = day.atStartOfDay()
        assertTrue(ShiftAlarmScheduler.calculateUpcomingAlarms(now, 10).isEmpty())
        Repository.applyRotation("turno_a", day, 1)
        assertEquals(1, ShiftAlarmScheduler.calculateUpcomingAlarms(now, 1).size)
        Repository.clearWorkDayShifts(day)
        assertTrue(ShiftAlarmScheduler.calculateUpcomingAlarms(now, 10).isEmpty())
    }

    @Test fun earlyCustomStartMovesAlarmToPreviousCalendarDate() {
        Repository.assignShift(day.plusDays(1), "notte")
        val assigned = Repository.getShiftsForDate(day.plusDays(1)).single()
        Repository.calendarShifts[day.plusDays(1)] = mutableListOf(assigned.copy(customStartTime = "00:30"))
        val alarm = ShiftAlarmScheduler.calculateUpcomingAlarms(day.atTime(20, 0), 2).single()
        assertEquals(day.atTime(23, 30), alarm.triggerDateTime)
        assertEquals(day.plusDays(1), alarm.date)
        assertEquals("${day.plusDays(1)}_notte", alarm.id)
    }

    @Test fun overnightShiftUsesStartDateAndConfiguredLeadTime() {
        Repository.assignShift(day, "notte")
        Repository.smartAlarmsConfig = SmartAlarmsConfig(rules = listOf(ShiftAlarmRule("notte", minutesBefore = 120)))
        val alarm = ShiftAlarmScheduler.calculateUpcomingAlarms(day.atStartOfDay(), 1).single()
        assertEquals(day.atTime(20, 0), alarm.triggerDateTime)
    }

    @Test fun explicitCustomAlarmTimeRetainsSameShiftDate() {
        Repository.assignShift(day, "mattina")
        Repository.smartAlarmsConfig = SmartAlarmsConfig(rules = listOf(ShiftAlarmRule("mattina", customTime = "04:45")))
        assertEquals(day.atTime(4, 45), ShiftAlarmScheduler.calculateUpcomingAlarms(day.atStartOfDay(), 1).single().triggerDateTime)
    }

    @Test fun repeatedModelUsesNextFutureAssignmentWithStableAlarmId() {
        Repository.assignShift(day, "mattina")
        Repository.addSecondaryShift(day, "mattina")
        val assigned = Repository.getShiftsForDate(day).toList()
        Repository.calendarShifts[day] = assigned.mapIndexed { index, shift ->
            shift.copy(customStartTime = if (index == 0) "06:00" else "14:00")
        }.toMutableList()
        val alarms = ShiftAlarmScheduler.calculateUpcomingAlarms(day.atTime(7, 0), 1)
        assertEquals(1, alarms.size)
        assertEquals(day.atTime(13, 0), alarms.single().triggerDateTime)
        assertEquals("${day}_mattina", alarms.single().id)
    }

    @Test fun onCallReminderIsIndependentOfAssignedShiftsAndKeepsPreviousDayId() {
        Repository.setDayOnCallManual(day.plusDays(1), true)
        Repository.smartAlarmsConfig = SmartAlarmsConfig(rules = listOf(ShiftAlarmRule("reperibilita", customTime = "20:00")))
        val alarm = ShiftAlarmScheduler.calculateUpcomingAlarms(day.atTime(18, 0), 1).single()
        assertTrue(alarm.isReperibilita)
        assertEquals(day.atTime(20, 0), alarm.triggerDateTime)
        assertEquals(day.plusDays(1), alarm.date)
        assertEquals("${day}_rep", alarm.id)
    }

    @Test fun disabledAlarmsRemainDisabledAndExpiredTriggersAreExcluded() {
        Repository.assignShift(day, "mattina")
        val id = "${day}_mattina"
        Repository.smartAlarmsConfig = SmartAlarmsConfig(rules = listOf(ShiftAlarmRule("mattina")), disabledAlarmIds = setOf(id))
        assertFalse(ShiftAlarmScheduler.calculateUpcomingAlarms(day.atStartOfDay(), 0).single().isEnabled)
        assertTrue(ShiftAlarmScheduler.calculateUpcomingAlarms(day.atTime(8, 0), 0).isEmpty())
        assertTrue(ShiftAlarmScheduler.calculateUpcomingAlarms(LocalDateTime.of(day, java.time.LocalTime.MIN), -1).isEmpty())
    }

    @Test fun zeroDayWindowIncludesTomorrowShiftWhoseAlarmIsTonight() {
        Repository.assignShift(day.plusDays(1), "notte")
        val assigned = Repository.getShiftsForDate(day.plusDays(1)).single()
        Repository.calendarShifts[day.plusDays(1)] = mutableListOf(assigned.copy(customStartTime = "00:15"))
        Repository.smartAlarmsConfig = SmartAlarmsConfig(rules = listOf(ShiftAlarmRule("notte", minutesBefore = 30)))
        val alarm = ShiftAlarmScheduler.calculateUpcomingAlarms(day.atTime(22, 0), 0).single()
        assertEquals(day.atTime(23, 45), alarm.triggerDateTime)
        assertEquals(day.plusDays(1), alarm.date)
    }

    @Test fun triggerOutsideRequestedWindowIsNotReturnedByLookAhead() {
        Repository.assignShift(day.plusDays(1), "mattina")
        assertTrue(ShiftAlarmScheduler.calculateUpcomingAlarms(day.atTime(22, 0), 0).isEmpty())
    }

    @Test fun negativeLeadNeverDelaysAlarmPastShiftStart() {
        Repository.assignShift(day, "mattina")
        Repository.smartAlarmsConfig = SmartAlarmsConfig(rules = listOf(ShiftAlarmRule("mattina", minutesBefore = -90)))
        assertEquals(day.atTime(6, 0), ShiftAlarmScheduler.calculateUpcomingAlarms(day.atStartOfDay(), 0).single().triggerDateTime)
    }

    @Test fun invalidExplicitTimeIsNotSilentlyReplacedWithAnotherAlarm() {
        Repository.assignShift(day, "mattina")
        Repository.smartAlarmsConfig = SmartAlarmsConfig(rules = listOf(ShiftAlarmRule("mattina", customTime = "25:30")))
        assertTrue(ShiftAlarmScheduler.calculateUpcomingAlarms(day.atStartOfDay(), 0).isEmpty())
    }

    @Test fun daylightSavingGapAndOverlapResolveInCurrentTimeZone() {
        val rome = java.time.ZoneId.of("Europe/Rome")
        val gap = LocalDateTime.of(2027, 3, 28, 2, 30)
        val resolvedGap = java.time.Instant.ofEpochMilli(ShiftAlarmScheduler.resolveEpochMillis(gap, rome)).atZone(rome)
        assertEquals(java.time.LocalTime.of(3, 30), resolvedGap.toLocalTime())
        val overlap = LocalDateTime.of(2026, 10, 25, 2, 30)
        assertEquals(java.time.Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), ShiftAlarmScheduler.resolveEpochMillis(overlap, rome))
    }

    @Test fun invalidOnCallTimeDoesNotTriggerAnUnexpectedDefaultAlarm() {
        Repository.setDayOnCallManual(day.plusDays(1), true)
        Repository.smartAlarmsConfig = SmartAlarmsConfig(rules = listOf(ShiftAlarmRule("reperibilita", customTime = "99:00")))
        assertTrue(ShiftAlarmScheduler.calculateUpcomingAlarms(day.atTime(18, 0), 0).isEmpty())
        assertNull(ShiftAlarmScheduler.getResolvedAlarmTime(ShiftAlarmRule("mattina", customTime = "25:00")))
    }
}
