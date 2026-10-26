package com.turni.calendario

import com.turni.calendario.ui.screens.reportEntranceProgress
import org.junit.Assert.*
import org.junit.Test

class ReportEntranceTimelineTest {
    @Test fun offscreenCardsAreAlreadyCompleteWhenThePeriodEntranceEnds() {
        for(index in 0..1000)assertEquals(1f,reportEntranceProgress(1f,index),0f)
    }
    @Test fun everyCardUsesTheSameMonotonicClockAndStaysWithinBounds() {
        for(index in 0..30){
            var previous=0f
            for(frame in 0..100){
                val value=reportEntranceProgress(frame/100f,index)
                assertTrue(value in 0f..1f)
                assertTrue(value>=previous)
                previous=value
            }
        }
    }
}
