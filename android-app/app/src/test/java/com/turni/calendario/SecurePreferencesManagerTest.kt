package com.turni.calendario

import com.turni.calendario.data.SecurePreferencesManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test suite for [SecurePreferencesManager] validating credential persistence,
 * in-memory fallback, visual masking and user preference management (F1, F8).
 */
class SecurePreferencesManagerTest {

    @Before
    fun setUp() {
        SecurePreferencesManager.clear()
    }

    @After
    fun tearDown() {
        SecurePreferencesManager.clear()
    }

    // ==========================================
    // Tier 1: Feature Coverage (F1, F8)
    // ==========================================

    @Test
    fun testInitialState_NoApiKeyConfigured() {
        assertNull(SecurePreferencesManager.getApiKey())
        assertFalse(SecurePreferencesManager.hasApiKey())
        assertEquals("", SecurePreferencesManager.getMaskedApiKey())
    }

    @Test
    fun testSaveAndRetrieveApiKey() {
        val apiKey = "AIzaSyDaFakeTestKey1234567890abcdef"
        SecurePreferencesManager.saveApiKey(null, apiKey)

        assertTrue(SecurePreferencesManager.hasApiKey())
        assertEquals(apiKey, SecurePreferencesManager.getApiKey())
    }

    @Test
    fun testDefaultUserNameIsMarcoGraziani() {
        // Without explicit user configuration, default must be "Marco Graziani"
        val defaultName = SecurePreferencesManager.getUserName()
        assertEquals("Marco Graziani", defaultName)
    }

    @Test
    fun testSaveAndRetrieveCustomUserName() {
        val customName = "Mario Rossi"
        SecurePreferencesManager.saveUserName(null, customName)

        val retrieved = SecurePreferencesManager.getUserName()
        assertEquals("Mario Rossi", retrieved)
    }

    @Test
    fun testClearCredentialsAndPreferences() {
        SecurePreferencesManager.saveApiKey(null, "AIzaSyTestKey12345")
        SecurePreferencesManager.saveUserName(null, "Luigi Bianchi")

        assertTrue(SecurePreferencesManager.hasApiKey())
        assertEquals("Luigi Bianchi", SecurePreferencesManager.getUserName())

        SecurePreferencesManager.clear()

        assertNull(SecurePreferencesManager.getApiKey())
        assertFalse(SecurePreferencesManager.hasApiKey())
        assertEquals("", SecurePreferencesManager.getMaskedApiKey())
        assertEquals(SecurePreferencesManager.DEFAULT_USER_NAME, SecurePreferencesManager.getUserName())
    }

    // ==========================================
    // Tier 2: Boundary & Corner Cases
    // ==========================================

    @Test
    fun testApiKey_WhitespaceTrimming() {
        val rawKeyWithSpaces = "   AIzaSyKeyWithLeadingAndTrailingSpaces   "
        SecurePreferencesManager.saveApiKey(null, rawKeyWithSpaces)

        assertEquals("AIzaSyKeyWithLeadingAndTrailingSpaces", SecurePreferencesManager.getApiKey())
        assertTrue(SecurePreferencesManager.hasApiKey())
    }

    @Test
    fun testApiKey_EmptyStringClearsKey() {
        SecurePreferencesManager.saveApiKey(null, "AIzaSyInitialValidKey")
        assertTrue(SecurePreferencesManager.hasApiKey())

        // Saving empty or blank string should clear key
        SecurePreferencesManager.saveApiKey(null, "")
        assertNull(SecurePreferencesManager.getApiKey())
        assertFalse(SecurePreferencesManager.hasApiKey())
        assertEquals("", SecurePreferencesManager.getMaskedApiKey())
    }

    @Test
    fun testApiKey_WhitespaceOnlyClearsKey() {
        SecurePreferencesManager.saveApiKey(null, "AIzaSyInitialValidKey")
        assertTrue(SecurePreferencesManager.hasApiKey())

        SecurePreferencesManager.saveApiKey(null, "    ")
        assertNull(SecurePreferencesManager.getApiKey())
        assertFalse(SecurePreferencesManager.hasApiKey())
    }

    @Test
    fun testUserName_WhitespaceTrimming() {
        val rawName = "   Roberto   Baggio   "
        SecurePreferencesManager.saveUserName(null, rawName)

        assertEquals("Roberto   Baggio", SecurePreferencesManager.getUserName())
    }

    @Test
    fun testUserName_EmptyStringResetsToDefault() {
        SecurePreferencesManager.saveUserName(null, "Alessandro Del Piero")
        assertEquals("Alessandro Del Piero", SecurePreferencesManager.getUserName())

        // Blank string resets to default worker name
        SecurePreferencesManager.saveUserName(null, "   ")
        assertEquals(SecurePreferencesManager.DEFAULT_USER_NAME, SecurePreferencesManager.getUserName())
    }

    @Test
    fun testMaskedApiKey_ShortKey_LengthEightOrLess() {
        // <= 8 characters must produce a full bullet mask
        SecurePreferencesManager.saveApiKey(null, "12345678")
        assertEquals("••••••••", SecurePreferencesManager.getMaskedApiKey())

        SecurePreferencesManager.saveApiKey(null, "abc")
        assertEquals("••••••••", SecurePreferencesManager.getMaskedApiKey())
    }

    @Test
    fun testMaskedApiKey_BoundaryNineCharacters() {
        // Exactly 9 characters: take(6) + "..." + takeLast(3)
        SecurePreferencesManager.saveApiKey(null, "123456789")
        assertEquals("123456...789", SecurePreferencesManager.getMaskedApiKey())
    }

    @Test
    fun testMaskedApiKey_StandardGoogleApiKey() {
        val realFormatKey = "AIzaSyDwTestGeminiKey99182374Zz"
        SecurePreferencesManager.saveApiKey(null, realFormatKey)

        val masked = SecurePreferencesManager.getMaskedApiKey()
        assertEquals("AIzaSy...4Zz", masked)
    }

    @Test
    fun testVeryLongApiKey() {
        val longKey = "AIzaSy" + "A".repeat(250) + "XYZ"
        SecurePreferencesManager.saveApiKey(null, longKey)

        assertEquals(longKey, SecurePreferencesManager.getApiKey())
        assertEquals("AIzaSy...XYZ", SecurePreferencesManager.getMaskedApiKey())
    }

    @Test
    fun testSpecialCharactersInApiKey() {
        val specialKey = "AIzaSy_special-key.with_123~symbols"
        SecurePreferencesManager.saveApiKey(null, specialKey)

        assertEquals(specialKey, SecurePreferencesManager.getApiKey())
        assertEquals("AIzaSy...ols", SecurePreferencesManager.getMaskedApiKey())
    }

    @Test
    fun testOverwriteExistingApiKey() {
        SecurePreferencesManager.saveApiKey(null, "FirstKey123456789")
        assertEquals("FirstKey123456789", SecurePreferencesManager.getApiKey())

        SecurePreferencesManager.saveApiKey(null, "SecondKey987654321")
        assertEquals("SecondKey987654321", SecurePreferencesManager.getApiKey())
        assertEquals("Second...321", SecurePreferencesManager.getMaskedApiKey())
    }

    @Test
    fun testOverwriteExistingUserName() {
        SecurePreferencesManager.saveUserName(null, "Giovanni Rossi")
        assertEquals("Giovanni Rossi", SecurePreferencesManager.getUserName())

        SecurePreferencesManager.saveUserName(null, "Marco Graziani")
        assertEquals("Marco Graziani", SecurePreferencesManager.getUserName())
    }

    @Test
    fun testUnicodeAndAccentedCharactersInUserName() {
        val unicodeName = "Nicolò D'Amico"
        SecurePreferencesManager.saveUserName(null, unicodeName)
        assertEquals("Nicolò D'Amico", SecurePreferencesManager.getUserName())

        val germanName = "Jürgen Müller"
        SecurePreferencesManager.saveUserName(null, germanName)
        assertEquals("Jürgen Müller", SecurePreferencesManager.getUserName())
    }

    @Test
    fun testIsolationAcrossMultipleInvocations() {
        // Verify multiple set/get cycles without memory corruption
        for (i in 1..20) {
            val key = "IterationKey_$i"
            SecurePreferencesManager.saveApiKey(null, key)
            assertEquals(key, SecurePreferencesManager.getApiKey())
        }
    }
}
