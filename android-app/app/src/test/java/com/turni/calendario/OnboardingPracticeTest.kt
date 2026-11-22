package com.turni.calendario

import com.turni.calendario.model.ReperibilitaConfig
import com.turni.calendario.model.ReperibilitaMode
import com.turni.calendario.model.ShiftAlarmRule
import com.turni.calendario.model.SmartAlarmsConfig
import com.turni.calendario.ui.screens.*
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class OnboardingPracticeTest {
    @Test fun previewAlwaysContainsTwoCompleteWeeksAcrossYearBoundary() {
        val today = LocalDate.of(2026, 12, 31)
        for (monday in listOf(true, false)) {
            val dates = calendarPracticeDates(today, monday)
            assertEquals(14, dates.size)
            assertEquals(if (monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY, dates.first().dayOfWeek)
            assertEquals(dates.first().plusDays(13), dates.last())
            assertTrue(today in dates)
            assertEquals(2027, dates.last().year)
        }
    }

    @Test fun rehearsalBeginsWithRealisticAssignmentsWithoutChangingTheCatalog() {
        val ids = mutableListOf("morning", "afternoon", "night", "rest")
        val example = initialCalendarPractice(ids)
        assertEquals(14, example.days.size)
        assertTrue(example.days.all { shifts -> shifts.isNotEmpty() && shifts.all { it in ids } })
        assertEquals(listOf("morning", "afternoon", "night", "rest"), ids)
        assertTrue(example.bells.isNotEmpty())
        assertTrue(example.notes.isNotEmpty())
    }

    @Test fun reverseIntervalAndUndoSnapshotKeepOtherDaysAndMetadata() {
        val before = initialCalendarPractice(listOf("morning", "afternoon", "rest"))
        val changed = applyCalendarPractice(before, 8, 5, "night", CalendarEditMode.REPLACE, false, emptyList())
        assertTrue((5..8).all { changed.days[it] == listOf("night") })
        assertEquals(before.days.take(5), changed.days.take(5))
        assertEquals(before.days.drop(9), changed.days.drop(9))
        assertEquals(before.notes, changed.notes)
        assertEquals(listOf("morning"), before.days.first())
        assertNotEquals(before.days, changed.days)
    }

    @Test fun addThenEraseDoesNotDeleteDayNotesOrUnselectedDates() {
        val before = initialCalendarPractice(listOf("morning", "rest"))
        val added = applyCalendarPractice(before, 2, 2, "night", CalendarEditMode.ADD, false, emptyList())
        assertEquals(before.days[2] + "night", added.days[2])
        assertEquals(1, before.days[2].size)
        val erased = applyCalendarPractice(added, 9, 2, "", CalendarEditMode.REPLACE, true, emptyList())
        assertTrue((2..9).all { erased.days[it].isEmpty() })
        assertFalse(9 in erased.bells)
        assertEquals(before.notes, erased.notes)
        assertEquals(before.days[10], erased.days[10])
    }

    @Test fun rotationRepeatsFromItsAnchorWithoutChangingExistingSnapshot() {
        val before = initialCalendarPractice(listOf("morning", "rest"))
        val rotated = applyCalendarPractice(before, 4, 11, "morning", CalendarEditMode.ROTATION, false, listOf("morning", "night", "rest"))
        assertEquals(listOf("morning", "night", "rest", "morning", "night", "rest", "morning", "night"), rotated.days.slice(4..11).map { it.single() })
        assertEquals(before.days[3], rotated.days[3])
        assertEquals(before.days[12], rotated.days[12])
    }

    @Test fun actionMappingCoversAllInteractiveLessons() {
        assertEquals(0, completedLessonForAction(CalendarEditMode.REPLACE, null, 1))
        assertEquals(1, completedLessonForAction(CalendarEditMode.REPLACE, null, 3))
        assertEquals(2, completedLessonForAction(CalendarEditMode.ADD, null, 1))
        assertEquals(2, completedLessonForAction(CalendarEditMode.ADD, null, 4))
        assertEquals(3, completedLessonForAction(CalendarEditMode.REPLACE, "eraser", 1))
        assertEquals(3, completedLessonForAction(CalendarEditMode.REPLACE, "bell", 1))
        assertEquals(4, completedLessonForAction(CalendarEditMode.ROTATION, null, 5))
    }

    @Test fun setupSummariesReflectLiveOnCallAndAlarmConfigs() {
        assertEquals("Manuale · nessuna data attiva", reperibilitaSetupSummary(ReperibilitaConfig(mode = ReperibilitaMode.MANUALE)))
        assertEquals("Manuale · 1 data", reperibilitaSetupSummary(ReperibilitaConfig(mode = ReperibilitaMode.MANUALE, manualDates = setOf(LocalDate.of(2026, 11, 20)))))
        assertEquals("Per turno · 2 turni", reperibilitaSetupSummary(ReperibilitaConfig(mode = ReperibilitaMode.TURNO_SPECIFICO, shiftSpecificIds = listOf("m", "p"))))
        assertEquals("Stagionale · regole invernali ed estive", reperibilitaSetupSummary(ReperibilitaConfig(mode = ReperibilitaMode.ALTERNATA_STAGIONALE)))

        assertEquals("Spente · attivabili quando vuoi", alarmsSetupSummary(SmartAlarmsConfig(isGlobalEnabled = false)))
        assertEquals("Attive · nessuna regola", alarmsSetupSummary(SmartAlarmsConfig(isGlobalEnabled = true, rules = emptyList())))
        assertEquals(
            "Attive · 1 regola",
            alarmsSetupSummary(SmartAlarmsConfig(isGlobalEnabled = true, rules = listOf(ShiftAlarmRule("m", true), ShiftAlarmRule("p", false))))
        )
    }

    @Test fun interactiveCoachGuidesStepByStepAcrossAllLessons() {
        val dates = calendarPracticeDates(LocalDate.of(2026, 11, 22), true)
        val ids = listOf("starter-morning", "starter-afternoon", "starter-night", "starter-rest")
        val names = mapOf(
            "starter-morning" to "Mattina",
            "starter-afternoon" to "Pomeriggio",
            "starter-night" to "Notte",
            "starter-rest" to "Riposo"
        )

        assertEquals("starter-afternoon", recommendedPracticeBrushId(ids))

        // Lesson 0: Step 0 highlights Pomeriggio brush, Step 1 highlights day 1 with TAP
        val l0s0 = evaluatePracticeCoach(0, 0, CalendarEditMode.REPLACE, null, "starter-morning", null, ids, names, dates)
        assertEquals(0, l0s0.subStepIndex)
        assertEquals("starter-afternoon", l0s0.target.brushId)
        assertNull(l0s0.target.dayIndex)

        val l0s1 = evaluatePracticeCoach(0, 1, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(1, l0s1.subStepIndex)
        assertEquals(1, l0s1.target.dayIndex)
        assertEquals(PracticeTargetAction.TAP, l0s1.target.action)

        // Lesson 1: Step 0 highlights day 3 with LONG_PRESS, Step 1 highlights day 6 with TAP
        val l1s0 = evaluatePracticeCoach(1, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(0, l1s0.subStepIndex)
        assertEquals(3, l1s0.target.dayIndex)
        assertEquals(PracticeTargetAction.LONG_PRESS, l1s0.target.action)

        val l1s1 = evaluatePracticeCoach(1, 1, CalendarEditMode.REPLACE, null, "starter-afternoon", 3, ids, names, dates)
        assertEquals(1, l1s1.subStepIndex)
        assertEquals(6, l1s1.target.dayIndex)
        assertEquals(PracticeTargetAction.TAP, l1s1.target.action)

        // Lesson 2: Step 0 highlights mode ADD, Step 1 highlights day 4
        val l2s0 = evaluatePracticeCoach(2, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(CalendarEditMode.ADD, l2s0.target.mode)
        val l2s1 = evaluatePracticeCoach(2, 1, CalendarEditMode.ADD, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(4, l2s1.target.dayIndex)

        // Lesson 3: Step 0 highlights bell tool, Step 1 highlights day 5
        val l3s0 = evaluatePracticeCoach(3, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals("bell", l3s0.target.tool)
        val l3s1 = evaluatePracticeCoach(3, 1, CalendarEditMode.REPLACE, "bell", "starter-afternoon", null, ids, names, dates)
        assertEquals(5, l3s1.target.dayIndex)

        // Lesson 4: Step 0 highlights mode ROTATION, Step 1 highlights start day 7, Step 2 highlights end day 13
        val l4s0 = evaluatePracticeCoach(4, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(CalendarEditMode.ROTATION, l4s0.target.mode)
        val l4s1 = evaluatePracticeCoach(4, 1, CalendarEditMode.ROTATION, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(7, l4s1.target.dayIndex)
        val l4s2 = evaluatePracticeCoach(4, 2, CalendarEditMode.ROTATION, null, "starter-afternoon", 7, ids, names, dates)
        assertEquals(13, l4s2.target.dayIndex)

        // Lesson 5: Step 0 highlights eraser, Step 1 highlights day 2, Step 2 highlights undo
        val l5s0 = evaluatePracticeCoach(5, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals("eraser", l5s0.target.tool)
        val l5s1 = evaluatePracticeCoach(5, 1, CalendarEditMode.REPLACE, "eraser", "starter-afternoon", null, ids, names, dates)
        assertEquals(2, l5s1.target.dayIndex)
        val l5s2 = evaluatePracticeCoach(5, 2, CalendarEditMode.REPLACE, "eraser", "starter-afternoon", null, ids, names, dates)
        assertEquals("undo", l5s2.target.tool)
    }
}
