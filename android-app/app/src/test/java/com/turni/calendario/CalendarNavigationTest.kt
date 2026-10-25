package com.turni.calendario

import com.turni.calendario.ui.screens.getMonthGridDays
import com.turni.calendario.ui.screens.monthSwipeDirection
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class CalendarNavigationTest {
    @Test fun shortMidScreenSwipeChangesMonthWithoutCrossingScreen() {
        assertEquals(1,monthSwipeDirection(-180f,-100f,1080f,2.75f))
        assertEquals(-1,monthSwipeDirection(180f,100f,1080f,2.75f))
    }
    @Test fun flingRequiresIntentionalHorizontalTravelAndMatchingVelocity() {
        assertEquals(1,monthSwipeDirection(-45f,-1800f,1080f,2.75f))
        assertEquals(0,monthSwipeDirection(-8f,-1800f,1080f,2.75f))
        assertEquals(0,monthSwipeDirection(-45f,1800f,1080f,2.75f))
        assertEquals(0,monthSwipeDirection(-45f,-100f,1080f,2.75f))
    }
    @Test fun thresholdScalesWithDensityAndContainerWidth() {
        assertEquals(1,monthSwipeDirection(-60f,0f,360f,1f))
        assertEquals(1,monthSwipeDirection(-120f,0f,720f,2f))
        assertEquals(0,monthSwipeDirection(0f,0f,0f,1f))
    }
    @Test fun sixWeekMonthContainsEveryDateAndFullWeekendRows() {
        for(monday in listOf(true,false))for(month in 1..12){
            val target=YearMonth.of(2026,month);val days=getMonthGridDays(target,monday)
            assertEquals(0,days.size%7);assertTrue(days.size<=42)
            assertEquals(target.lengthOfMonth(),days.count{it.isCurrentMonth})
            assertEquals(days.size,days.map{it.date}.distinct().size)
            assertTrue(days.zipWithNext().all{it.first.date.plusDays(1)==it.second.date})
        }
    }
}
