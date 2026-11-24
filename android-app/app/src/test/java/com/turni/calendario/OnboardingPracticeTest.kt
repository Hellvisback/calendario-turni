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

        // Lesson 0: Step 0 highlights Pomeriggio brush, Step 1 highlights day 1 with TAP and reflects selected brush
        val l0s0 = evaluatePracticeCoach(0, 0, CalendarEditMode.REPLACE, null, "starter-morning", null, ids, names, dates)
        assertEquals(0, l0s0.subStepIndex)
        assertEquals("starter-afternoon", l0s0.target.brushId)
        assertNull(l0s0.target.dayIndex)

        val l0s1 = evaluatePracticeCoach(0, 1, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(1, l0s1.subStepIndex)
        assertEquals(1, l0s1.target.dayIndex)
        assertEquals(PracticeTargetAction.TAP, l0s1.target.action)
        assertTrue(l0s1.instruction.contains("Pomeriggio"))

        val l0s1Custom = evaluatePracticeCoach(0, 1, CalendarEditMode.REPLACE, null, "starter-night", null, ids, names, dates)
        assertEquals(1, l0s1Custom.subStepIndex)
        assertTrue(l0s1Custom.instruction.contains("Notte"))

        // Lesson 1: Step 0 highlights day 3 with LONG_PRESS, Step 1 highlights day 6 with TAP (or day 4 if anchor == 6)
        val l1s0 = evaluatePracticeCoach(1, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(0, l1s0.subStepIndex)
        assertEquals(3, l1s0.target.dayIndex)
        assertEquals(PracticeTargetAction.LONG_PRESS, l1s0.target.action)

        val l1s1 = evaluatePracticeCoach(1, 1, CalendarEditMode.REPLACE, null, "starter-afternoon", 3, ids, names, dates)
        assertEquals(1, l1s1.subStepIndex)
        assertEquals(6, l1s1.target.dayIndex)
        assertEquals(PracticeTargetAction.TAP, l1s1.target.action)

        val l1s1Alt = evaluatePracticeCoach(1, 1, CalendarEditMode.REPLACE, null, "starter-afternoon", 6, ids, names, dates)
        assertEquals(4, l1s1Alt.target.dayIndex)

        // Lesson 2: 3-step multi-shift guide (ADD mode -> 2nd shift -> 3rd shift with overflow dots • •, plus interval anchor)
        val l2s0 = evaluatePracticeCoach(2, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(0, l2s0.subStepIndex)
        assertEquals(3, l2s0.totalSubSteps)
        assertEquals(CalendarEditMode.ADD, l2s0.target.mode)
        val l2s1 = evaluatePracticeCoach(2, 1, CalendarEditMode.ADD, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(1, l2s1.subStepIndex)
        assertEquals(4, l2s1.target.dayIndex)
        val l2s2 = evaluatePracticeCoach(2, 2, CalendarEditMode.ADD, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(2, l2s2.subStepIndex)
        assertEquals(4, l2s2.target.dayIndex)
        assertTrue(l2s2.instruction.contains("• •"))
        val l2s2Range = evaluatePracticeCoach(2, 2, CalendarEditMode.ADD, null, "starter-afternoon", 3, ids, names, dates)
        assertEquals(6, l2s2Range.target.dayIndex)

        // Lesson 3: Step 0 highlights bell tool, Step 1 highlights day 5 (or end day if long-press anchor set) and falls back if bell toggled off
        val l3s0 = evaluatePracticeCoach(3, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals("bell", l3s0.target.tool)
        val l3s1 = evaluatePracticeCoach(3, 1, CalendarEditMode.REPLACE, "bell", "starter-afternoon", null, ids, names, dates)
        assertEquals(5, l3s1.target.dayIndex)
        assertEquals(PracticeTargetAction.TAP, l3s1.target.action)
        val l3s1Range = evaluatePracticeCoach(3, 1, CalendarEditMode.REPLACE, "bell", "starter-afternoon", 5, ids, names, dates)
        assertEquals(7, l3s1Range.target.dayIndex)
        assertEquals(PracticeTargetAction.TAP, l3s1Range.target.action)
        assertTrue(l3s1Range.instruction.contains("reperibili"))
        val l3s1Off = evaluatePracticeCoach(3, 1, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals("bell", l3s1Off.target.tool)

        // Lesson 4: Step 0 highlights mode ROTATION, Step 1 highlights start day 7, Step 2 highlights end day 13
        val l4s0 = evaluatePracticeCoach(4, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(CalendarEditMode.ROTATION, l4s0.target.mode)
        val l4s1 = evaluatePracticeCoach(4, 1, CalendarEditMode.ROTATION, null, "starter-afternoon", null, ids, names, dates)
        assertEquals(7, l4s1.target.dayIndex)
        val l4s2 = evaluatePracticeCoach(4, 2, CalendarEditMode.ROTATION, null, "starter-afternoon", 7, ids, names, dates)
        assertEquals(13, l4s2.target.dayIndex)

        // Lesson 5: Step 0 highlights eraser, Step 1 highlights LONG_PRESS on day (or TAP end day if anchor set), Step 2 highlights undo
        val l5s0 = evaluatePracticeCoach(5, 0, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals("eraser", l5s0.target.tool)
        val l5s1Desync = evaluatePracticeCoach(5, 1, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates)
        assertEquals("eraser", l5s1Desync.target.tool)
        val l5s1 = evaluatePracticeCoach(5, 1, CalendarEditMode.REPLACE, "eraser", "starter-afternoon", null, ids, names, dates, preferredEraseDay = 2)
        assertEquals(1, l5s1.subStepIndex)
        assertEquals(2, l5s1.target.dayIndex)
        assertEquals(PracticeTargetAction.LONG_PRESS, l5s1.target.action)
        val l5s1Range = evaluatePracticeCoach(5, 1, CalendarEditMode.REPLACE, "eraser", "starter-afternoon", 2, ids, names, dates)
        assertEquals(1, l5s1Range.subStepIndex)
        assertEquals(5, l5s1Range.target.dayIndex)
        assertEquals(PracticeTargetAction.TAP, l5s1Range.target.action)
        val l5s2 = evaluatePracticeCoach(5, 2, CalendarEditMode.REPLACE, "eraser", "starter-afternoon", null, ids, names, dates, canUndo = true)
        assertEquals("undo", l5s2.target.tool)
        val l5s2NoUndo = evaluatePracticeCoach(5, 2, CalendarEditMode.REPLACE, null, "starter-afternoon", null, ids, names, dates, canUndo = false)
        assertEquals("eraser", l5s2NoUndo.target.tool)
    }

    @Test fun applyBellPracticeRangeTogglesIntervalsCleanly() {
        val initial = initialCalendarPractice(listOf("morning", "afternoon", "rest"))
        val (enabledState, enabled) = applyBellPracticeRange(initial, 4, 6)
        assertTrue(enabled)
        assertTrue((4..6).all { it in enabledState.bells })
        assertTrue(9 in enabledState.bells)

        val (disabledState, enabledAfter) = applyBellPracticeRange(enabledState, 6, 4)
        assertFalse(enabledAfter)
        assertTrue((4..6).none { it in disabledState.bells })
        assertTrue(9 in disabledState.bells)
    }

    @Test fun multiShiftStackingAndEraserIntervalWorkCleanlyInRehearsal() {
        val ids = listOf("starter-morning", "starter-afternoon", "starter-night", "starter-rest")
        val initial = initialCalendarPractice(ids)
        val secondAdded = applyCalendarPractice(initial, 4, 4, "starter-afternoon", CalendarEditMode.ADD, false, emptyList())
        assertEquals(2, secondAdded.days[4].size)
        val thirdAdded = applyCalendarPractice(secondAdded, 4, 4, "starter-morning", CalendarEditMode.ADD, false, emptyList())
        assertEquals(3, thirdAdded.days[4].size)

        // Long-press interval erase clears range and bells/overtime/travel inside the range without touching other days
        val erasedRange = applyCalendarPractice(thirdAdded, 7, 9, "", CalendarEditMode.REPLACE, true, emptyList())
        assertTrue((7..9).all { erasedRange.days[it].isEmpty() })
        assertFalse(7 in erasedRange.overtime)
        assertFalse(9 in erasedRange.bells)
        assertEquals(thirdAdded.days[4], erasedRange.days[4])
    }
}
