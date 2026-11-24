package com.turni.calendario

import com.turni.calendario.widget.WidgetUpdateManager
import com.turni.calendario.widget.widgetHeaderLayout
import com.turni.calendario.widget.widgetDayCellHeight
import com.turni.calendario.widget.widgetShiftBaseHeight
import com.turni.calendario.widget.widgetDateHeaderHeight
import com.turni.calendario.widget.widgetWeekendHeaderRadii
import com.turni.calendario.widget.widgetWeekendCellRadii
import com.turni.calendario.widget.formatWidgetWorkProfileLabel
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
        assertEquals(22f,header,0f)
        assertTrue(cell>=40f)
    }

    @Test fun resizingNeverDropsTheStartingTimeAfterItBecomesReadable() {
        for(height in 46..180)assertTrue("Two shift lines at $height dp",widgetShiftBaseHeight(height.toFloat())>=20f)
    }

    @Test fun tinyHostPrioritizesTheDateAndLargerHostShowsTheWorkOnTheSameRow() {
        assertFalse(widgetHeaderLayout(160f,false).showWork)
        assertTrue(widgetHeaderLayout(320f,false).showWork)
        assertFalse(widgetHeaderLayout(160f,true).showWork)
        assertTrue(widgetHeaderLayout(320f,true).showWork)
    }

    @Test fun compactDailyWidgetPrioritizesCompleteTimeAndNeverReservesAnUnusedTrailingLane() {
        for(width in listOf(104f,120f,136f))for(font in listOf(1f,1.2f)){
            val plan=com.turni.calendario.widget.widgetDailyShiftLayout(width,32f,font)
            assertFalse(plan.showName)
            assertTrue(plan.detailWidth>=40f)
            assertEquals(width-9f,plan.detailX+plan.detailWidth,.01f)
        }
        assertTrue(com.turni.calendario.widget.widgetDailyShiftLayout(184f,50f,1.2f).showName)
    }

    @Test fun holidayNameIsAnnouncedAlongsideOnCallAndTodaysState() {
        val summary=WidgetUpdateManager.getDaySummary(LocalDate.of(2026,11,1)).copy(
            holidayName="Tutti i Santi",isOnCall=true,isToday=true)
        assertTrue(summary.accessibilityLabel.contains("festività: Tutti i Santi"))
        assertTrue(summary.accessibilityLabel.contains("reperibile"))
        assertTrue(summary.accessibilityLabel.contains("oggi"))
    }

    @Test fun weekendColumnsRoundOuterCornersAndStaySeamlessAcrossRows() {
        val satHeader = widgetWeekendHeaderRadii(prevColWeekend = false, nextColWeekend = true)
        val sunHeader = widgetWeekendHeaderRadii(prevColWeekend = true, nextColWeekend = false)
        assertEquals(8f, satHeader.topLeft, 0f)
        assertEquals(0f, satHeader.topRight, 0f)
        assertEquals(0f, sunHeader.topLeft, 0f)
        assertEquals(8f, sunHeader.topRight, 0f)
        assertEquals(0f, satHeader.bottomLeft, 0f)
        assertEquals(0f, satHeader.bottomRight, 0f)

        val standaloneHeader = widgetWeekendHeaderRadii(prevColWeekend = false, nextColWeekend = false)
        assertEquals(8f, standaloneHeader.topLeft, 0f)
        assertEquals(8f, standaloneHeader.topRight, 0f)

        val middleRowSat = widgetWeekendCellRadii(isLastRow = false, prevColWeekend = false, nextColWeekend = true)
        assertEquals(0f, middleRowSat.topLeft, 0f)
        assertEquals(0f, middleRowSat.topRight, 0f)
        assertEquals(0f, middleRowSat.bottomLeft, 0f)
        assertEquals(0f, middleRowSat.bottomRight, 0f)

        val lastRowSat = widgetWeekendCellRadii(isLastRow = true, prevColWeekend = false, nextColWeekend = true)
        val lastRowSun = widgetWeekendCellRadii(isLastRow = true, prevColWeekend = true, nextColWeekend = false)
        assertEquals(0f, lastRowSat.topLeft, 0f)
        assertEquals(0f, lastRowSat.topRight, 0f)
        assertEquals(8f, lastRowSat.bottomLeft, 0f)
        assertEquals(0f, lastRowSat.bottomRight, 0f)
        assertEquals(0f, lastRowSun.bottomLeft, 0f)
        assertEquals(8f, lastRowSun.bottomRight, 0f)

        val lastRowStandalone = widgetWeekendCellRadii(isLastRow = true, prevColWeekend = false, nextColWeekend = false)
        assertEquals(8f, lastRowStandalone.bottomLeft, 0f)
        assertEquals(8f, lastRowStandalone.bottomRight, 0f)
    }

    @Test fun workProfileChipAppendsDropdownIndicatorOnlyForValidData() {
        assertEquals("Principale ▾", formatWidgetWorkProfileLabel("Principale", invalidData = false))
        assertEquals("Ospedale ▾", formatWidgetWorkProfileLabel("  Ospedale  ", invalidData = false))
        assertEquals("Dati da ripristinare · Apri TurniSync", formatWidgetWorkProfileLabel("Dati da ripristinare · Apri TurniSync", invalidData = true))
    }

    @Test fun widgetLayoutsOnlyUseRemoteViewAllowedTags() {
        val allowedTags = setOf(
            "LinearLayout", "FrameLayout", "RelativeLayout", "TextView", "ImageView",
            "Button", "CheckBox", "ProgressBar", "ViewFlipper", "ListView", "GridView",
            "StackView", "AdapterViewFlipper", "Chronometer", "AnalogClock", "TextClock"
        )
        val candidates = listOf(
            java.io.File("app/src/main/res/layout"),
            java.io.File("src/main/res/layout"),
            java.io.File("android-app/app/src/main/res/layout")
        )
        val layoutDir = candidates.firstOrNull { it.isDirectory }
            ?: error("Widget layout directory not found from ${java.io.File(".").absolutePath}")
        val widgetLayouts = layoutDir.listFiles { _, name ->
            name.startsWith("widget_") && name.endsWith(".xml")
        }?.toList().orEmpty()
        assertTrue("Expected widget layouts to inspect", widgetLayouts.size >= 8)

        val tagRegex = Regex("<([A-Za-z0-9_.]+)")
        widgetLayouts.forEach { file ->
            val xml = file.readText()
            val tags = tagRegex.findAll(xml)
                .map { it.groupValues[1] }
                .filterNot { it.startsWith("?") }
                .toSet()
            val unsupported = tags - allowedTags
            assertTrue("Non-RemoteView tag(s) in ${file.name}: $unsupported", unsupported.isEmpty())
        }
    }
}
