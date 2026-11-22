package com.turni.calendario.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection

class GoogleAccessPolicyTest {
    @Test fun unauthorizedHttpStatusesMatchOnly401And403() {
        assertTrue(GoogleAccessRetryPolicy.isUnauthorizedStatus(HttpURLConnection.HTTP_UNAUTHORIZED))
        assertTrue(GoogleAccessRetryPolicy.isUnauthorizedStatus(HttpURLConnection.HTTP_FORBIDDEN))
        assertFalse(GoogleAccessRetryPolicy.isUnauthorizedStatus(HttpURLConnection.HTTP_OK))
        assertFalse(GoogleAccessRetryPolicy.isUnauthorizedStatus(HttpURLConnection.HTTP_BAD_REQUEST))
        assertFalse(GoogleAccessRetryPolicy.isUnauthorizedStatus(HttpURLConnection.HTTP_NOT_FOUND))
        assertFalse(GoogleAccessRetryPolicy.isUnauthorizedStatus(HttpURLConnection.HTTP_INTERNAL_ERROR))
    }

    @Test fun staleTokenOrMissingScopesTriggerSingleClearAndRetry() {
        assertTrue(
            GoogleAccessRetryPolicy.shouldRetryWithClearedToken(
                allowStaleRetry = true,
                accessToken = "stale-token",
                hasAllRequestedScopes = true,
                userInfoStatusCode = HttpURLConnection.HTTP_UNAUTHORIZED,
            ),
        )
        assertTrue(
            GoogleAccessRetryPolicy.shouldRetryWithClearedToken(
                allowStaleRetry = true,
                accessToken = "stale-token",
                hasAllRequestedScopes = true,
                userInfoStatusCode = HttpURLConnection.HTTP_FORBIDDEN,
            ),
        )
        assertTrue(
            GoogleAccessRetryPolicy.shouldRetryWithClearedToken(
                allowStaleRetry = true,
                accessToken = "partial-scope-token",
                hasAllRequestedScopes = false,
                userInfoStatusCode = null,
            ),
        )
    }

    @Test fun retryIsSuppressedWhenExhaustedOrTokenMissingOrNonAuthError() {
        assertFalse(
            GoogleAccessRetryPolicy.shouldRetryWithClearedToken(
                allowStaleRetry = false,
                accessToken = "stale-token",
                hasAllRequestedScopes = true,
                userInfoStatusCode = HttpURLConnection.HTTP_UNAUTHORIZED,
            ),
        )
        assertFalse(
            GoogleAccessRetryPolicy.shouldRetryWithClearedToken(
                allowStaleRetry = false,
                accessToken = "partial-scope-token",
                hasAllRequestedScopes = false,
                userInfoStatusCode = null,
            ),
        )
        assertFalse(
            GoogleAccessRetryPolicy.shouldRetryWithClearedToken(
                allowStaleRetry = true,
                accessToken = null,
                hasAllRequestedScopes = false,
                userInfoStatusCode = HttpURLConnection.HTTP_UNAUTHORIZED,
            ),
        )
        assertFalse(
            GoogleAccessRetryPolicy.shouldRetryWithClearedToken(
                allowStaleRetry = true,
                accessToken = "   ",
                hasAllRequestedScopes = true,
                userInfoStatusCode = HttpURLConnection.HTTP_UNAUTHORIZED,
            ),
        )
        assertFalse(
            GoogleAccessRetryPolicy.shouldRetryWithClearedToken(
                allowStaleRetry = true,
                accessToken = "valid-token",
                hasAllRequestedScopes = true,
                userInfoStatusCode = HttpURLConnection.HTTP_INTERNAL_ERROR,
            ),
        )
    }

    @Test fun disconnectRevokeRunsOnlyForMatchingAuthGeneration() {
        assertTrue(GoogleAccessRetryPolicy.shouldRunDisconnectRevoke(currentGeneration = 3L, logoutGeneration = 3L))
        assertFalse(GoogleAccessRetryPolicy.shouldRunDisconnectRevoke(currentGeneration = 4L, logoutGeneration = 3L))
    }

    @Test fun googleTokenUnauthorizedExceptionPreservesStatusAndMessage() {
        val ex = GoogleTokenUnauthorizedException(HttpURLConnection.HTTP_UNAUTHORIZED)
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ex.statusCode)
        assertTrue(IllegalStateException::class.java.isInstance(ex))
        assertTrue(!ex.message.isNullOrBlank())
    }

    @Test fun googleFeaturesAlwaysRequestIdentityAndExpectedDataScopes() {
        assertTrue(GoogleFeature.PROFILE.scopes.contains("openid"))
        assertTrue(GoogleFeature.PROFILE.scopes.contains("https://www.googleapis.com/auth/userinfo.email"))
        assertTrue(GoogleFeature.DRIVE.scopes.contains("https://www.googleapis.com/auth/drive.appdata"))
        assertTrue(GoogleFeature.CALENDAR.scopes.contains("https://www.googleapis.com/auth/calendar.events"))
    }
}
