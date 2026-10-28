package com.turni.calendario

import com.turni.calendario.ui.screens.ReportCountLabel
import org.junit.Assert.*
import org.junit.Test

class ReportCountLabelTest {
    @Test fun decimalCountsKeepItalianLabelsAndReuseUnchangedValues() {
        val labels=ReportCountLabel(true," h")
        assertEquals("0,0 h",labels.at(0))
        assertEquals("17,4 h",labels.at(174))
        assertSame(labels.at(174),labels.at(174))
        assertEquals("-0,5 h",labels.at(-5))
        assertEquals("214748364,7 h",labels.at(Int.MAX_VALUE))
    }
    @Test fun integerCountsDoNotAcquireDecimalsOrLoseTheSuffix() {
        val labels=ReportCountLabel(false," turni")
        assertEquals("32 turni",labels.at(32))
        assertEquals("0 turni",labels.at(0))
    }
}
