package com.turni.calendario

import com.turni.calendario.repository.*
import com.turni.calendario.model.AppPreferences
import com.turni.calendario.model.ShiftDefinition
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
    @Test fun stepNormalizationClampsLegacyStepFiveAndOutOfBoundsValues() {
        assertEquals(0,normalizeFirstRunStep(-2))
        assertEquals(0,normalizeFirstRunStep(0))
        assertEquals(3,normalizeFirstRunStep(3))
        assertEquals(FirstRunStore.LAST_STEP,normalizeFirstRunStep(4))
        assertEquals(FirstRunStore.LAST_STEP,normalizeFirstRunStep(5))
    }
    @Test fun missingStarterDefinitionsAreIdempotentAndRespectExistingModels() {
        val added=missingStarterShiftDefinitions("job-1",emptyList())
        assertEquals(5,added.size)
        assertTrue(added.all{it.workProfileId=="job-1"})

        val custom=listOf(ShiftDefinition("custom-1","Turno A","TA",workProfileId="job-1"))
        assertTrue(missingStarterShiftDefinitions("job-1",custom).isEmpty())

        val otherProfileStarters=listOf(ShiftDefinition("starter-morning","Mattina","MAT",workProfileId="job-2"))
        val filtered=missingStarterShiftDefinitions("job-1",otherProfileStarters)
        assertEquals(4,filtered.size)
        assertFalse(filtered.any{it.id=="starter-morning"})
    }
}
