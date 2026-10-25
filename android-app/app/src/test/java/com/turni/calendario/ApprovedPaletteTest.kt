package com.turni.calendario

import com.turni.calendario.repository.*
import org.junit.Assert.*
import org.junit.Test

class ApprovedPaletteTest {
    @Test fun adoptsOnlyRecognizedLegacyDefaultsWithoutChangingAnyAssignments() {
        val original=DatabaseSnapshot.capture()
        val defs=original.definitions.map{it.copy(colorHex=if(it.id=="mattina")0xFF0284C7L else 0xFF123456L)}
        val source=original.copy(definitions=defs)
        val result=adoptApprovedPalette(source)
        assertEquals(0xFFF2C46DL,result.definitions.first{it.id=="mattina"}.colorHex)
        assertTrue(result.definitions.filter{it.id!="mattina"}.all{it.colorHex==0xFF123456L})
        assertEquals(source.shifts,result.shifts)
        assertEquals(source.extras,result.extras)
        assertEquals(source.rotations,result.rotations)
        assertEquals(result,adoptApprovedPalette(result))
    }
}
