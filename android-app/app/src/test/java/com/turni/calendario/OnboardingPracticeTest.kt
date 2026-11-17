package com.turni.calendario

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
}
