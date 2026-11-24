package com.turni.calendario
import com.turni.calendario.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MinimumRestTest {
    private val date=LocalDate.of(2026,11,30)
    private val profile=WorkProfile("job","Test",minRestHoursBetweenShifts=11)
    private fun def(id:String,start:String,end:String)=ShiftDefinition(id,id,id,startTime=start,endTime=end,workProfileId="job")
    private fun shift(id:String,day:Int=0)=AssignedShift(id,date.plusDays(day.toLong()),id)
    private fun check(shifts:List<AssignedShift>,defs:List<ShiftDefinition>)=minimumRestWarnings(shifts,defs,listOf(profile))
    @Test fun sameDayAndInsertionOrder(){
        val alerts=check(listOf(shift("b"),shift("a")),listOf(def("a","06:00","14:00"),def("b","14:00","22:00")))
        assertEquals(0L,alerts.single().restMinutes);assertEquals("b",alerts.single().nextId)
    }
    @Test fun overnightAcrossMonthBoundary(){
        val alerts=check(listOf(shift("n"),shift("m",1)),listOf(def("n","22:00","06:00"),def("m","08:00","16:00")))
        assertEquals(120L,alerts.single().restMinutes);assertEquals(date.plusDays(1),alerts.single().date)
    }
    @Test fun exactThresholdIsAllowed(){assertTrue(check(listOf(shift("a"),shift("b",1)),listOf(def("a","06:00","19:00"),def("b","06:00","14:00"))).isEmpty())}
    @Test fun actualEndAndOvertimeReduceRest(){
        val defs=listOf(def("a","06:00","18:00"),def("b","06:00","14:00"))
        assertTrue(check(listOf(shift("a"),shift("b",1)),defs).isEmpty())
        assertEquals(600L,check(listOf(shift("a").copy(actualEndTime="20:00"),shift("b",1)),defs).single().restMinutes)
        assertEquals(600L,check(listOf(shift("a").copy(overtimeHours=2f),shift("b",1)),defs).single().restMinutes)
    }
    @Test fun restAndAbsenceAreNotWorkIntervals(){
        val defs=listOf(def("a","06:00","18:00"),def("r","00:00","23:59").copy(category=ShiftCategory.RIPOSO),def("f","00:00","23:59").copy(category=ShiftCategory.ASSENZA),def("all","00:00","00:00").copy(isAllDay=true))
        assertTrue(check(listOf(shift("a"),shift("r"),shift("f"),shift("all")),defs).isEmpty())
    }
    @Test fun nestedIntervalsDoNotHideOverlap(){
        val alerts=check(listOf(shift("a"),shift("b"),shift("c")),listOf(def("a","06:00","22:00"),def("b","08:00","10:00"),def("c","12:00","14:00")))
        assertEquals(2,alerts.size);assertTrue(alerts.all{it.restMinutes<0})
    }
    @Test fun labelsDoNotMatterAndUnknownModelIsIgnored(){
        assertEquals(1,check(listOf(shift("a"),shift("b"),shift("missing")),listOf(def("a","06:00","14:00").copy(name="A scelta"),def("b","15:00","20:00").copy(name="Qualsiasi"))).size)
    }
    @Test fun earlyActualDepartureDoesNotBecomeNextDay(){
        assertTrue(check(listOf(shift("a").copy(actualEndTime="17:00"),shift("b",1)),listOf(def("a","06:00","18:00"),def("b","06:00","14:00"))).isEmpty())
    }
    @Test fun configuredThresholdIsUsedAcrossJobs(){
        val a=def("a","06:00","18:00");val b=def("b","06:00","14:00").copy(workProfileId="other")
        val alerts=minimumRestWarnings(listOf(shift("a"),shift("b",1)),listOf(a,b),listOf(profile,profile.copy(id="other",minRestHoursBetweenShifts=14)))
        assertEquals(14,alerts.single().requiredHours)
    }

    @Test fun startOvertimeAndPermissionAdjustRestInterval() {
        val defs = listOf(def("a", "06:00", "14:00"), def("b", "02:00", "10:00"))
        // Scheduled gap: 14:00 -> 02:00 next day = 12h (720m), allowed (>= 11h).
        assertTrue(check(listOf(shift("a"), shift("b", 1)), defs).isEmpty())

        // Start overtime on shift b (starts 2h earlier -> 00:00) reduces gap to 10h (600m) -> warning!
        val startOtAlerts = check(listOf(shift("a"), shift("b", 1).copy(startOvertimeHours = 2f)), defs)
        assertEquals(600L, startOtAlerts.single().restMinutes)

        // End overtime 2h + start permission 1h on shift b: 14:00+2h=16:00 to 02:00+1h=03:00 -> 11h (660m), allowed!
        assertTrue(
            check(
                listOf(
                    shift("a").copy(endOvertimeHours = 2f),
                    shift("b", 1).copy(startPermissionHours = 1f)
                ),
                defs
            ).isEmpty()
        )
    }

    @Test fun emptyDaysAndZeroLimitNeverFireWarnings() {
        assertTrue(minimumRestWarnings(emptyList(), emptyList(), listOf(profile)).isEmpty())
        assertTrue(consecutiveShiftWarnings(emptyList(), emptyList(), listOf(profile)).isEmpty())

        val zeroProfile = profile.copy(minRestHoursBetweenShifts = 0, maxConsecutiveWorkDays = 0)
        val defs = listOf(def("a", "06:00", "14:00"), def("b", "14:00", "22:00"))
        assertTrue(minimumRestWarnings(listOf(shift("a"), shift("b")), defs, listOf(zeroProfile)).isEmpty())
        assertTrue(consecutiveShiftWarnings(listOf(shift("a"), shift("b")), defs, listOf(zeroProfile)).isEmpty())
    }
}
