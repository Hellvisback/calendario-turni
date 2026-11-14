package com.turni.calendario

import com.turni.calendario.repository.*
import org.junit.Assert.*
import org.junit.Test

class FirstRunPolicyTest {
    @Test fun freshInstallStartsSetupBeforeDefaultsAreSaved() {
        assertEquals(FirstRunDecision.START,firstRunDecision(null,false))
    }
    @Test fun upgradingAnExistingInstallPreservesItsCalendar() {
        assertEquals(FirstRunDecision.READY,firstRunDecision(null,true))
    }
    @Test fun interruptedSetupResumesEvenAfterAutosaveCreatedTheDatabase() {
        assertEquals(FirstRunDecision.RESUME,firstRunDecision("pending",true))
        assertEquals(FirstRunDecision.RESUME,firstRunDecision("pending",false))
    }
    @Test fun restoredCompletionWithoutItsDatabaseMustNotSkipRecovery() {
        assertEquals(FirstRunDecision.START,firstRunDecision("complete",false))
    }
    @Test fun finishedSetupDoesNotRunAgain() {
        assertEquals(FirstRunDecision.READY,firstRunDecision("complete",true))
    }
    @Test fun chosenExamplesBelongToTheUserWorkAndHaveEightHourWorkDurations() {
        val templates=starterShiftDefinitions("job-custom")
        assertEquals(5,templates.size)
        assertTrue(templates.all{it.workProfileId=="job-custom"&&it.shortCode.length<=4})
        assertTrue(templates.filterNot{it.isAllDay}.all{it.durationMinutes==480L})
        assertEquals(templates.size,templates.map{it.id}.distinct().size)
    }
}
