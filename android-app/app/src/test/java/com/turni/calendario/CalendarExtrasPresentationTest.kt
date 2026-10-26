package com.turni.calendario

import com.turni.calendario.ui.screens.calendarOvertimeLabel
import com.turni.calendario.ui.screens.calendarExtraBandHeight
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
    @Test fun metadataNeverConsumesTheBaseShiftOrExceedsTheCell(){
        for(available in listOf(39f,40f,43f,62f,100f))for(rows in listOf(0f,.2f,1f,2f)){
            val h=calendarExtraBandHeight(available,40f,rows)
            assertTrue(h in 0f..13f)
            if(available>=40f)assertTrue(40f+h*rows<=available+.001f)
            else assertEquals(0f,h,.001f)
        }
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
