package com.turni.calendario

import com.turni.calendario.ui.screens.calendarWaveRadius
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class CalendarWaveGeometryTest {
    @Test fun waveStartsAtTouchAndEndsBeyondEveryCellCorner() {
        val width=54f;val height=96f;val x=9f;val y=17f
        assertEquals(0f,calendarWaveRadius(width,height,x,y,0f),.001f)
        val radius=calendarWaveRadius(width,height,x,y,1f)
        for(cx in listOf(0f,width))for(cy in listOf(0f,height)){
            val distance=sqrt((cx-x)*(cx-x)+(cy-y)*(cy-y))
            assertTrue("The completed wave must cover every corner",radius+.001f>=distance)
        }
    }

    @Test fun waveIsContinuousAndClampedForInterruptedTransitions() {
        val full=calendarWaveRadius(54f,96f,27f,48f,1f)
        assertEquals(full/2,calendarWaveRadius(54f,96f,27f,48f,.5f),.001f)
        assertEquals(0f,calendarWaveRadius(54f,96f,27f,48f,-1f),.001f)
        assertEquals(full,calendarWaveRadius(54f,96f,27f,48f,2f),.001f)
    }

    @Test fun touchAtTheEdgeNeedsALargerWaveThanTouchAtTheCenter() {
        assertTrue(calendarWaveRadius(54f,96f,0f,0f,1f)>calendarWaveRadius(54f,96f,27f,48f,1f))
        assertEquals(calendarWaveRadius(54f,96f,0f,96f,1f),calendarWaveRadius(54f,96f,-5f,100f,1f),.001f)
    }
}
