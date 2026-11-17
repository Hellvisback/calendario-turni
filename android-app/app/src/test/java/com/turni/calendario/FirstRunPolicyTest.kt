package com.turni.calendario

import com.turni.calendario.repository.*
import com.turni.calendario.model.AppPreferences
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
    @Test fun completedSetupSurvivesUpdateEvenBeforeItsDatabaseIsAvailable() {
        // Restored preferences or a temporarily unavailable file must not reopen setup
        // and run its new-installation defaults over a previously configured user.
        assertEquals(FirstRunDecision.READY,firstRunDecision("complete",false))
        assertEquals(FirstRunDecision.READY,firstRunDecision("complete",true))
    }
    @Test fun finishedSetupDoesNotRunAgain() {
        assertEquals(FirstRunDecision.READY,firstRunDecision("complete",true))
    }
    @Test fun restoredCompletionWithoutDataPausesBackupWithoutResettingPreferences() {
        val configured=AppPreferences(autoBackupDaily=true,timeFormat24h=false,weekStartMonday=false)
        val recovery=firstRunRecoveryPreferences("complete",false,configured)
        assertEquals(configured.copy(autoBackupDaily=false),recovery)
        assertTrue(configured.autoBackupDaily)
        assertEquals(configured,firstRunRecoveryPreferences("complete",true,configured))
        assertEquals(configured,firstRunRecoveryPreferences("pending",false,configured))
    }
    @Test fun chosenExamplesBelongToTheUserWorkAndHaveEightHourWorkDurations() {
        val templates=starterShiftDefinitions("job-custom")
        assertEquals(5,templates.size)
        assertTrue(templates.all{it.workProfileId=="job-custom"&&it.shortCode.length<=4})
        assertTrue(templates.filterNot{it.isAllDay}.all{it.durationMinutes==480L})
        assertEquals(templates.size,templates.map{it.id}.distinct().size)
    }
}
