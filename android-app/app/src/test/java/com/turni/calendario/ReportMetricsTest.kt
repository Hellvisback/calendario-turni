package com.turni.calendario

import com.turni.calendario.model.*
import com.turni.calendario.ui.screens.reportMetrics
import com.turni.calendario.ui.screens.reportRingBoundaries
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReportMetricsTest {
    private val date=LocalDate.of(2026,10,26)
    @Test fun distributionIncludesEveryAssignedCategoryAndAllDayModel() {
        val definitions=ShiftCategory.values().mapIndexed { index,category ->
            ShiftDefinition("id$index","Personalizzato $index","X$index",category=category,isAllDay=index>0)
        }
        val assigned=definitions.mapIndexed { index,definition -> AssignedShift("a$index",date,definition.id) }
        val metrics=reportMetrics(assigned,definitions)
        assertEquals(definitions.map{it.id},metrics.map{it.definition.id})
        assertEquals(assigned.size,metrics.sumOf{it.count})
        assertEquals(4,reportRingBoundaries(metrics.map{it.count},10f,100f,10f).size)
    }
    @Test fun identicalNamesRemainDistinctAndUnusedDefinitionsStayOut() {
        val definitions=listOf(ShiftDefinition("a","POM","POM"),ShiftDefinition("b","POM","POM"),ShiftDefinition("unused","Notte","NOT"))
        val metrics=reportMetrics(listOf(AssignedShift("1",date,"b"),AssignedShift("2",date,"a"),AssignedShift("3",date,"b")),definitions)
        assertEquals(listOf("a","b"),metrics.map{it.definition.id})
        assertEquals(listOf(1,2),metrics.map{it.count})
    }
    @Test fun renamingDoesNotChangeDistributionAndActualHoursRemainAccurate() {
        val model=ShiftDefinition("custom","Notte serale","NS",startTime="22:00",endTime="06:00",breakMinutes=30)
        val assignments=listOf(AssignedShift("1",date,model.id,customEndTime="07:00",overtimeHours=.5f))
        val original=reportMetrics(assignments,listOf(model)).single()
        val renamed=reportMetrics(assignments,listOf(model.copy(name="Z",shortCode="Z"))).single()
        assertEquals(9f,original.hours,.001f)
        assertEquals(original.hours,renamed.hours,0f)
        assertEquals(original.count,renamed.count)
    }
    @Test fun emptyPeriodHasNoInventedDefaultModels() {
        assertTrue(reportMetrics(emptyList(),listOf(ShiftDefinition("a","Mattina","MAT"))).isEmpty())
    }
}
