package com.turni.calendario

import com.turni.calendario.export.packPdfColumns
import org.junit.Assert.*
import org.junit.Test

class PdfSinglePageLayoutTest {
    @Test fun ordinaryMonthFitsWithoutExtraPages() {
        val result=packPdfColumns(List(31){20f},1,700f)!!
        assertEquals(listOf((0..30).toList()),result)
    }
    @Test fun columnsRetainEveryAssignmentInOrder() {
        val result=packPdfColumns(List(60){20f},2,700f)!!
        assertEquals((0..59).toList(),result.flatten())
        assertTrue(result.all{it.size*20f<=700f})
    }
    @Test fun crowdedContentIsReportedInsteadOfDiscarded() {
        assertNull(packPdfColumns(List(100){30f},3,700f))
    }
    @Test fun aLongNoteCannotBeSplitOrClippedSilently() {
        assertNull(packPdfColumns(listOf(701f),3,700f))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidHeightIsRejected() {
        packPdfColumns(listOf(Float.NaN),1,700f)
    }
}
