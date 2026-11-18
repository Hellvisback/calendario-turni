package com.turni.calendario

import com.turni.calendario.ui.screens.calendarOvertimeLabel
import com.turni.calendario.ui.screens.calendarShiftLayout
import com.turni.calendario.ui.screens.calendarShiftAccessibility
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.io.File

class CalendarExtrasPresentationTest {
    @Test fun overtimeLabelsRetainExactMinutesWithoutZeroHourPrefix(){
        assertEquals("+15m",calendarOvertimeLabel(.25f));assertEquals("+30m",calendarOvertimeLabel(.5f))
        assertEquals("+1h",calendarOvertimeLabel(1f));assertEquals("+1h30",calendarOvertimeLabel(1.5f))
        assertEquals("+10h15",calendarOvertimeLabel(10.25f))
    }
    @Test fun bothCompactMarkersAreDescribedWithTheExactOvertime() {
        assertEquals(
            "Mattina, 06:00–14:00, straordinario +1h30, trasferta",
            calendarShiftAccessibility("Mattina", "06:00–14:00", 1.5f, true)
        )
    }

    @Test fun anUnmodifiedShiftHasNoExtraStatusInItsDescription() {
        assertEquals("Notte, 22:00–06:00", calendarShiftAccessibility("Notte", "22:00–06:00", 0f, false))
    }

    @Test fun addingAssignmentsNeverCompressesExistingBadges() {
        for(height in listOf(0f,23f,50f,76f,90f,120f))for(scale in listOf(1f,1.3f,1.5f,2f)) {
            val single=calendarShiftLayout(height,1,scale)
            for(count in 2..8) {
                val plan=calendarShiftLayout(height,count,scale)
                assertEquals(single.chipHeight,plan.chipHeight,.001f)
                assertTrue(plan.visibleCount in 0..minOf(count,3))
                if(plan.visibleCount>0)assertTrue(plan.visibleCount*plan.chipHeight+(plan.visibleCount-1)*plan.gap+plan.overflowHeight<=height+.001f)
            }
        }
        assertEquals(1,calendarShiftLayout(50f,2,1f).visibleCount)
        assertEquals(2,calendarShiftLayout(76f,3,1f).visibleCount)
        assertEquals(3,calendarShiftLayout(100f,4,1f).visibleCount)
    }
    @Test fun bakedDockIsFiniteSymmetricAndDetachesAtRest(){
        DataInputStream(File("src/main/res/raw/dock_contours.bin").inputStream()).use{input->
            val frames=input.readInt();val samples=input.readInt();assertEquals(61,frames);assertEquals(761,samples)
            assertEquals(.5f,input.readFloat(),.001f);assertEquals(92f,input.readFloat(),.001f)
            val table=Array(frames){FloatArray(samples){input.readFloat()}}
            table.forEach{row->row.indices.forEach{i->assertTrue(row[i].isFinite()&&row[i] in 0f..45f);assertEquals(row[i],row[samples-1-i],.03f)}}
            assertEquals(0f,table.first()[193],.001f)
            assertTrue(table.last()[193]>22f)
            assertEquals(0f,table.last()[248],.001f)
            assertEquals(-1,input.read())
        }
    }
}
