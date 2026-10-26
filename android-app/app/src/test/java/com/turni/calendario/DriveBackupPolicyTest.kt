package com.turni.calendario

import com.turni.calendario.sync.DriveBackupPolicy
import com.turni.calendario.sync.GoogleApiException
import org.junit.Assert.*
import org.junit.Test

class DriveBackupPolicyTest {
    @Test fun onlySupportedIntervalsCanBePersisted() {
        assertEquals(listOf(6, 12, 24, 168), DriveBackupPolicy.intervalsHours)
        assertEquals(24, DriveBackupPolicy.interval(-1))
        assertEquals(24, DriveBackupPolicy.interval(0))
        assertEquals(24, DriveBackupPolicy.interval(Int.MAX_VALUE))
        DriveBackupPolicy.intervalsHours.forEach { assertEquals(it, DriveBackupPolicy.interval(it)) }
    }
    @Test fun accountComparisonNeverMatchesMissingIdentity() {
        assertFalse(DriveBackupPolicy.sameAccount(null, null))
        assertFalse(DriveBackupPolicy.sameAccount("", ""))
        assertFalse(DriveBackupPolicy.sameAccount("a@example.org", "b@example.org"))
        assertTrue(DriveBackupPolicy.sameAccount("A@example.org", "a@example.org"))
    }
    @Test fun onlyTransientHttpFailuresAreRetried() {
        listOf(0, 429, 500, 503, 599).forEach { assertTrue(DriveBackupPolicy.retryable(it)) }
        listOf(200, 400, 401, 403, 404, 409, 413).forEach { assertFalse(DriveBackupPolicy.retryable(it)) }
    }
    @Test fun backoffIsBoundedToThreeRetriesPerFailureSequence() {
        assertFalse(DriveBackupPolicy.retry(0, true))
        (1..3).forEach { assertTrue(DriveBackupPolicy.retry(it, true)) }
        assertFalse(DriveBackupPolicy.retry(4, true))
        assertFalse(DriveBackupPolicy.retry(1, false))
    }
    @Test fun manualSuccessCanBeRememberedWithoutEnablingAutomaticUploads() {
        assertTrue(DriveBackupPolicy.canRecordSuccess("a@example.org", true, "a@example.org", false, null))
        assertTrue(DriveBackupPolicy.canRecordSuccess("a@example.org", true, "a@example.org", false, "old@example.org"))
        assertFalse(DriveBackupPolicy.canRecordSuccess("a@example.org", false, "a@example.org", false, null))
        assertFalse(DriveBackupPolicy.canRecordSuccess("a@example.org", true, "other@example.org", false, null))
        assertFalse(DriveBackupPolicy.canRecordSuccess("a@example.org", true, null, false, null))
    }
    @Test fun completedUploadCannotOverwriteAnotherActiveScheduleAccount() {
        assertFalse(DriveBackupPolicy.canRecordSuccess("a@example.org", true, "a@example.org", true, "b@example.org"))
        assertFalse(DriveBackupPolicy.canRecordSuccess("a@example.org", true, "a@example.org", true, null))
        assertTrue(DriveBackupPolicy.canRecordSuccess("a@example.org", true, "a@example.org", true, "A@example.org"))
    }
    @Test fun dataValidationFailuresAreNotRetriedLikeNetworkFailures() {
        assertFalse(DriveBackupPolicy.retryableFailure(IllegalArgumentException("Invalid references")))
        assertFalse(DriveBackupPolicy.retryableFailure(IllegalStateException("Local database unreadable")))
        assertTrue(DriveBackupPolicy.retryableFailure(java.io.IOException()))
        assertTrue(DriveBackupPolicy.retryableFailure(java.util.concurrent.TimeoutException()))
        assertTrue(DriveBackupPolicy.retryableFailure(GoogleApiException(503, "Unavailable")))
        assertFalse(DriveBackupPolicy.retryableFailure(GoogleApiException(403, "Consent required")))
        assertFalse(DriveBackupPolicy.retryableFailure(GoogleApiException(413, "Too large")))
    }
}
