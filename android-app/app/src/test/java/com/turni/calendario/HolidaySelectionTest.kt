package com.turni.calendario

import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.model.HolidaysCatalog
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HolidaySelectionTest {
    private fun isolated(block:()->Unit) {
        val enabled=ShiftRepository.enabledHolidayIds;val preferences=ShiftRepository.appPreferences
        try {ShiftRepository.appPreferences=preferences.copy(showHolidays=true);block()}
        finally {ShiftRepository.enabledHolidayIds=enabled;ShiftRepository.appPreferences=preferences}
    }
    @Test fun allEnabledCatalogEntriesResolveOnTheirDate()=isolated {
        ShiftRepository.enabledHolidayIds=HolidaysCatalog.allHolidays.map{it.id}.toSet()
        for(year in listOf(2026,2027,2028))for(holiday in HolidaysCatalog.allHolidays)
            assertTrue(ShiftRepository.holidaysForDate(holiday.getDateForYear(year)).any{it.id==holiday.id})
    }
    @Test fun deselectingOneHolidayPreservesOtherHolidaysOnTheSameDate()=isolated {
        val date=LocalDate.of(2026,4,25)
        ShiftRepository.enabledHolidayIds=setOf("liberazione","san_marco")
        assertEquals(2,ShiftRepository.holidaysForDate(date).size)
        ShiftRepository.toggleHoliday("san_marco")
        assertEquals(listOf("liberazione"),ShiftRepository.holidaysForDate(date).map{it.id})
        ShiftRepository.toggleHoliday("liberazione")
        assertTrue(ShiftRepository.holidaysForDate(date).isEmpty());assertNull(ShiftRepository.isHoliday(date))
    }
    @Test fun disablingHolidaysHidesBothMarkerAndNames()=isolated {
        ShiftRepository.enabledHolidayIds=setOf("natale")
        ShiftRepository.appPreferences=ShiftRepository.appPreferences.copy(showHolidays=false)
        assertTrue(ShiftRepository.holidaysForDate(LocalDate.of(2026,12,25)).isEmpty())
        assertNull(ShiftRepository.isHoliday(LocalDate.of(2026,12,25)))
    }
}
