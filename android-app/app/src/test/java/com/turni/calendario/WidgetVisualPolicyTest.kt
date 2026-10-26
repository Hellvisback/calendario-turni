package com.turni.calendario

import com.turni.calendario.widget.WidgetUpdateManager
import com.turni.calendario.widget.widgetHeaderLayout
import com.turni.calendario.widget.widgetDayCellHeight
import com.turni.calendario.widget.widgetShiftBaseHeight
import com.turni.calendario.widget.widgetDateHeaderHeight
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WidgetVisualPolicyTest {
    @Test fun headerKeepsNavigationTargetsAndWorkWithinHostBounds() {
        for(width in 120..700){
            for(today in listOf(false,true)){
                val plan=widgetHeaderLayout(width.toFloat(),today)
                val navigation=if(today)0f else 64f
                assertTrue(plan.titleMaxWidthDp>=24f)
                assertTrue(plan.titleMaxWidthDp+navigation<=width-16f)
                if(plan.showWork)assertTrue(width-16f-navigation-plan.titleMaxWidthDp>=52f)
            }
        }
    }

    @Test fun existing84DpWeekReservesTwoReadableShiftLines() {
        val cell=widgetDayCellHeight(84f,1)
        val header=widgetDateHeaderHeight(false)
        assertEquals(44f,cell,0f)
        assertEquals(14f,header,0f)
        assertTrue(cell-header-4f>=26f)
    }

    @Test fun resizingNeverDropsTheStartingTimeAfterItBecomesReadable() {
        for(height in 38..180)assertTrue("Two shift lines at $height dp",widgetShiftBaseHeight(height.toFloat())>=20f)
    }

    @Test fun tinyHostPrioritizesTheDateAndLargerHostShowsTheWorkOnTheSameRow() {
        assertFalse(widgetHeaderLayout(160f,false).showWork)
        assertTrue(widgetHeaderLayout(320f,false).showWork)
        assertTrue(widgetHeaderLayout(160f,true).showWork)
    }

    @Test fun holidayNameIsAnnouncedAlongsideOnCallAndTodaysState() {
        val summary=WidgetUpdateManager.getDaySummary(LocalDate.of(2026,11,1)).copy(
            holidayName="Tutti i Santi",isOnCall=true,isToday=true)
        assertTrue(summary.accessibilityLabel.contains("festività: Tutti i Santi"))
        assertTrue(summary.accessibilityLabel.contains("reperibile"))
        assertTrue(summary.accessibilityLabel.contains("oggi"))
    }
}
