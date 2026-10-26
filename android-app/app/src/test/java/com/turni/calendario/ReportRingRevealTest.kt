package com.turni.calendario

import com.turni.calendario.ui.screens.reportRingBoundaries
import org.junit.Assert.*
import org.junit.Test

class ReportRingRevealTest {
    @Test fun visibleProportionsExcludeHiddenContinuations() {
        val parts = reportRingBoundaries(listOf(1, 2, 1), 20f, 100f, 30f)
        assertEquals(0f, parts[0].startDistance, .001f)
        assertEquals(45f, parts[0].endDistance, .001f)
        assertEquals(95f, parts[1].endDistance, .001f)
        assertEquals(150f, parts[2].endDistance, .001f)
    }
    @Test fun onlyTheHeadAdvancesAndPreviousSegmentsStayFixed() {
        val parts = reportRingBoundaries(listOf(1, 1, 1), 15f, 90f, 15f)
        for (step in 0..120) {
            val head = step.toFloat()
            val partial = parts.count { head > it.startDistance && head < it.endDistance }
            assertTrue("At most one partial segment", partial <= 1)
            parts.forEach { part ->
                val previousEnd = minOf((head - 1f).coerceAtLeast(0f), part.endDistance)
                val currentEnd = minOf(head, part.endDistance)
                assertTrue(currentEnd >= previousEnd)
            }
        }
    }
    @Test fun finalSegmentHasOneFixedStartIncludingItsContinuation() {
        val last = reportRingBoundaries(listOf(2, 1), 20f, 90f, 25f).last()
        assertEquals(80f, last.startDistance, .001f)
        assertEquals(135f, last.endDistance, .001f)
        assertEquals(120f, minOf(120f, last.endDistance), .001f)
        assertEquals(80f, last.startDistance, .001f)
    }
    @Test fun singleTypeOwnsTheWholeContour() {
        val only = reportRingBoundaries(listOf(100), 10f, 80f, 10f).single()
        assertEquals(0f, only.startDistance, .001f)
        assertEquals(100f, only.endDistance, .001f)
    }
    @Test fun emptyPeriodHasNoColoredSegments() {
        assertTrue(reportRingBoundaries(emptyList(), 10f, 80f, 10f).isEmpty())
    }
}
