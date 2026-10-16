package com.turni.calendario.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial & Concurrency Test Suite for Milestone 1.
 * Evaluates SecurePreferencesManager and GeminiApiClient under:
 * - Boundary values (empty, blank, extremely long keys, unicode, special characters)
 * - Null contexts & fallbacks
 * - High-concurrency multithreaded contention
 * - Scalability with massive datasets (hundreds of shifts and squads)
 * - Adversarial payload injections (null bytes, unescaped JSON, HTML/SQL patterns)
 */
class GeminiConcurrencyAndBoundaryTest {

    @Before
    fun setUp() {
        SecurePreferencesManager.clear(null)
    }

    // ==========================================
    // 1. SecurePreferencesManager: Null Context
    // ==========================================

    @Test
    fun testSecurePreferences_NullContextAllMethodsDoNotCrash() {
        // None of these should throw NullPointerException
        assertNull(SecurePreferencesManager.getApiKey(null))
        assertFalse(SecurePreferencesManager.hasApiKey(null))
        assertEquals("", SecurePreferencesManager.getMaskedApiKey(null))
        assertNull(SecurePreferencesManager.getUserName(null))

        SecurePreferencesManager.saveApiKey(null, "AIzaSyTest123456789")
        assertEquals("AIzaSyTest123456789", SecurePreferencesManager.getApiKey(null))
        assertTrue(SecurePreferencesManager.hasApiKey(null))
        assertEquals("AIzaSy...789", SecurePreferencesManager.getMaskedApiKey(null))

        SecurePreferencesManager.saveUserName(null, "Luca Bianchi")
        assertEquals("Luca Bianchi", SecurePreferencesManager.getUserName(null))

        SecurePreferencesManager.clear(null)
        assertNull(SecurePreferencesManager.getApiKey(null))
        assertFalse(SecurePreferencesManager.hasApiKey(null))
        assertNull(SecurePreferencesManager.getUserName(null))
    }

    // ==========================================
    // 2. Boundary Values: Empty, Blank & Whitespace
    // ==========================================

    @Test
    fun testSecurePreferences_EmptyAndBlankStringEdgeCases() {
        // Save empty string
        SecurePreferencesManager.saveApiKey(null, "")
        assertNull("Empty key should be treated as null", SecurePreferencesManager.getApiKey(null))
        assertFalse(SecurePreferencesManager.hasApiKey(null))
        assertEquals("", SecurePreferencesManager.getMaskedApiKey(null))

        // Save whitespace only
        SecurePreferencesManager.saveApiKey(null, "    \t\n  \r ")
        assertNull("Blank key should be treated as null", SecurePreferencesManager.getApiKey(null))
        assertFalse(SecurePreferencesManager.hasApiKey(null))
        assertEquals("", SecurePreferencesManager.getMaskedApiKey(null))

        // Save key with leading and trailing whitespace
        val rawKey = "   AIzaSyValidWithSpaces999   "
        SecurePreferencesManager.saveApiKey(null, rawKey)
        assertEquals("AIzaSyValidWithSpaces999", SecurePreferencesManager.getApiKey(null))
        assertTrue(SecurePreferencesManager.hasApiKey(null))
        assertEquals("AIzaSy...999", SecurePreferencesManager.getMaskedApiKey(null))

        // User name blank
        SecurePreferencesManager.saveUserName(null, "   ")
        assertNull(
            "Blank username should fall back to default null",
            SecurePreferencesManager.getUserName(null)
        )
    }

    // ==========================================
    // 3. Boundary Values: Key Lengths & Masking
    // ==========================================

    @Test
    fun testSecurePreferences_MaskingBoundaries() {
        // Empty
        SecurePreferencesManager.saveApiKey(null, "")
        assertEquals("", SecurePreferencesManager.getMaskedApiKey(null))

        // Length 1
        SecurePreferencesManager.saveApiKey(null, "A")
        assertEquals("••••••••", SecurePreferencesManager.getMaskedApiKey(null))

        // Length 7
        SecurePreferencesManager.saveApiKey(null, "1234567")
        assertEquals("••••••••", SecurePreferencesManager.getMaskedApiKey(null))

        // Length 8 (boundary)
        SecurePreferencesManager.saveApiKey(null, "12345678")
        assertEquals("••••••••", SecurePreferencesManager.getMaskedApiKey(null))

        // Length 9 (boundary: first 6 + ... + last 3)
        SecurePreferencesManager.saveApiKey(null, "123456789")
        assertEquals("123456...789", SecurePreferencesManager.getMaskedApiKey(null))

        // Typical Gemini API Key (39 chars)
        SecurePreferencesManager.saveApiKey(null, "AIzaSyD0ABCDEFGHIJKLMN123456789opqrstXYZ")
        assertEquals("AIzaSy...XYZ", SecurePreferencesManager.getMaskedApiKey(null))
    }

    // ==========================================
    // 4. Boundary Values: Extremely Large Strings
    // ==========================================

    @Test
    fun testSecurePreferences_ExtremelyLargeKeysAndNames() {
        val largeKeyBuilder = StringBuilder()
        repeat(100_000) { largeKeyBuilder.append("A") }
        largeKeyBuilder.append("END")
        val hugeKey = largeKeyBuilder.toString()

        SecurePreferencesManager.saveApiKey(null, hugeKey)
        assertEquals(hugeKey, SecurePreferencesManager.getApiKey(null))
        assertTrue(SecurePreferencesManager.hasApiKey(null))

        val masked = SecurePreferencesManager.getMaskedApiKey(null)
        assertEquals("AAAAAA...END", masked)

        // Huge username
        val hugeName = "Dipendente_".repeat(10_000)
        SecurePreferencesManager.saveUserName(null, hugeName)
        assertEquals(hugeName, SecurePreferencesManager.getUserName(null))
    }

    // ==========================================
    // 5. Special Characters & Unicode Preservation
    // ==========================================

    @Test
    fun testSecurePreferences_SpecialCharactersAndUnicode() {
        val specialKey = "AIzaSy🔑_€#@%&*!§°çéèàòù\u00A9\u20AC_XYZ"
        SecurePreferencesManager.saveApiKey(null, specialKey)
        assertEquals(specialKey, SecurePreferencesManager.getApiKey(null))

        val specialName = "Giacomo D'Agostini \uD83D\uDC68\u200D\u2708\uFE0F"
        SecurePreferencesManager.saveUserName(null, specialName)
        assertEquals(specialName, SecurePreferencesManager.getUserName(null))
    }

    // ==========================================
    // 6. Concurrency Stress Test on Preferences
    // ==========================================

    @Test(timeout = 15_000)
    fun testSecurePreferences_ConcurrentReadWriteStress() {
        val numThreads = 30
        val iterationsPerThread = 200
        val executor = Executors.newFixedThreadPool(numThreads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(numThreads)
        val failureFlag = AtomicBoolean(false)
        val errorMessages = StringBuilder()

        for (t in 0 until numThreads) {
            val threadId = t
            executor.submit {
                try {
                    startLatch.await()
                    for (i in 0 until iterationsPerThread) {
                        val key = "AIzaSy_Thread_${threadId}_Iter_${i}_SecretKey4xQ"
                        val user = "User_${threadId}_Iter_${i}"

                        SecurePreferencesManager.saveApiKey(null, key)
                        SecurePreferencesManager.saveUserName(null, user)

                        val retrievedKey = SecurePreferencesManager.getApiKey(null)
                        assertNotNull("Key must never be null during concurrent writes", retrievedKey)
                        assertTrue(retrievedKey!!.startsWith("AIzaSy_Thread_"))
                        assertTrue(SecurePreferencesManager.hasApiKey(null))

                        val masked = SecurePreferencesManager.getMaskedApiKey(null)
                        assertTrue("Masked key must contain ellipsis", masked.contains("..."))

                        val retrievedUser = SecurePreferencesManager.getUserName(null)
                        assertNotNull(retrievedUser)
                    }
                } catch (e: Throwable) {
                    failureFlag.set(true)
                    synchronized(errorMessages) {
                        errorMessages.append("Thread $threadId failed: ${e.javaClass.simpleName} - ${e.message}\n")
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All concurrent threads should complete within timeout", completed)
        assertFalse("No thread should encounter an error: $errorMessages", failureFlag.get())
    }

    @Test(timeout = 15_000)
    fun testSecurePreferences_ConcurrentMixedReadWriteClearInvariant() {
        val numThreads = 20
        val iterationsPerThread = 150
        val executor = Executors.newFixedThreadPool(numThreads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(numThreads)
        val failureFlag = AtomicBoolean(false)
        val errorMessages = StringBuilder()

        for (t in 0 until numThreads) {
            val threadId = t
            executor.submit {
                try {
                    startLatch.await()
                    for (i in 0 until iterationsPerThread) {
                        if (i % 10 == 0) {
                            SecurePreferencesManager.clear(null)
                        } else {
                            val key = "AIzaSy_Mixed_${threadId}_$i"
                            SecurePreferencesManager.saveApiKey(null, key)
                            SecurePreferencesManager.saveUserName(null, "User_${threadId}_$i")
                        }

                        // Verify internal consistency: getMaskedApiKey and getUserName should never throw
                        val masked = SecurePreferencesManager.getMaskedApiKey(null)
                        assertNotNull(masked)

                        val user = SecurePreferencesManager.getUserName(null)
                        if (user != null) {
                            assertTrue(user.startsWith("User_"))
                        }

                        // Invariants: getApiKey when non-null must be a valid key
                        val currentKey = SecurePreferencesManager.getApiKey(null)
                        if (currentKey != null) {
                            assertTrue(currentKey.startsWith("AIzaSy_Mixed_"))
                        }

                        // getMaskedApiKey must either be empty (if key is null/blank) or masked with ellipsis
                        val maskedKey = SecurePreferencesManager.getMaskedApiKey(null)
                        assertTrue(maskedKey.isEmpty() || maskedKey.contains("..."))
                    }
                } catch (e: Throwable) {
                    failureFlag.set(true)
                    synchronized(errorMessages) {
                        errorMessages.append("Thread $threadId failed: ${e.javaClass.simpleName} - ${e.message}\n")
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue("All concurrent threads should complete within timeout", completed)
        assertFalse("No thread should encounter an error during mixed clear: $errorMessages", failureFlag.get())
    }

    // ==========================================
    // 7. GeminiApiClient: Concurrent Request Body Building
    // ==========================================

    @Test(timeout = 15_000)
    fun testGeminiApiClient_ConcurrentRequestBodyBuilding() {
        val client = GeminiApiClient()
        val numThreads = 20
        val iterations = 50
        val executor = Executors.newFixedThreadPool(numThreads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(numThreads)
        val errorCount = AtomicInteger(0)

        for (t in 0 until numThreads) {
            val threadId = t
            executor.submit {
                try {
                    startLatch.await()
                    for (i in 0 until iterations) {
                        val prompt = "Prompt from thread $threadId iter $i"
                        val base64 = "base64_data_${threadId}_${i}_" + "X".repeat(500)
                        val mime = "image/png"

                        val body = client.buildRequestBody(base64, mime, prompt)
                        val json = JSONObject(body)

                        val contents = json.getJSONArray("contents")
                        val parts = contents.getJSONObject(0).getJSONArray("parts")
                        val text = parts.getJSONObject(0).getString("text")
                        val data = parts.getJSONObject(1).getJSONObject("inline_data").getString("data")

                        assertEquals(prompt, text)
                        assertEquals(base64, data)
                    }
                } catch (_: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue(completed)
        assertEquals("Thread-safety issue detected during buildRequestBody", 0, errorCount.get())
    }

    // ==========================================
    // 8. GeminiApiClient: Concurrent Response Parsing
    // ==========================================

    @Test(timeout = 15_000)
    fun testGeminiApiClient_ConcurrentResponseParsing() {
        val client = GeminiApiClient()
        val numThreads = 20
        val iterations = 50
        val executor = Executors.newFixedThreadPool(numThreads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(numThreads)
        val errorCount = AtomicInteger(0)

        for (t in 0 until numThreads) {
            val threadId = t
            executor.submit {
                try {
                    startLatch.await()
                    for (i in 0 until iterations) {
                        val payload = """
                        {
                            "shiftDefinitions": [
                                { "code": "T$threadId", "name": "Turno $threadId", "startTime": "08:00", "endTime": "16:00" }
                            ],
                            "squads": [
                                { "squadName": "Squadra $threadId", "rotationDays": ["T$threadId", "RIP"], "employees": ["Worker $i"] }
                            ],
                            "notes": "Note $threadId-$i"
                        }
                        """.trimIndent()

                        val data = client.parseGeminiResponse(payload)
                        assertEquals(1, data.shiftDefinitions.size)
                        assertEquals("T$threadId", data.shiftDefinitions[0].code)
                        assertEquals(1, data.squads.size)
                        assertEquals("Squadra $threadId", data.squads[0].squadName)
                        assertEquals("Note $threadId-$i", data.notes)
                    }
                } catch (_: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val completed = doneLatch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertTrue(completed)
        assertEquals("Thread-safety issue detected during parseGeminiResponse", 0, errorCount.get())
    }

    // ==========================================
    // 9. GeminiApiClient: Adversarial Injections & Escapes
    // ==========================================

    @Test
    fun testGeminiApiClient_AdversarialPromptAndDataEscapes() {
        val client = GeminiApiClient()

        // Adversarial prompt with quotes, backslashes, newlines, JSON brackets, and script tags
        val maliciousPrompt = """
        {"hack": true}, \n \r \t \" \\ \u0000 <script>alert('xss')</script>
        ' OR '1'='1'; DROP TABLE shifts; --
        """.trimIndent()

        val maliciousBase64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==\"};\r\n"

        val body = client.buildRequestBody(
            imageBase64 = maliciousBase64,
            mimeType = "image/png",
            promptText = maliciousPrompt
        )

        // Must produce valid JSON without parsing error
        val root = JSONObject(body)
        val text = root.getJSONArray("contents")
            .getJSONObject(0)
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        assertEquals(maliciousPrompt, text)

        val inlineData = root.getJSONArray("contents")
            .getJSONObject(0)
            .getJSONArray("parts")
            .getJSONObject(1)
            .getJSONObject("inline_data")

        assertEquals(maliciousBase64, inlineData.getString("data"))
    }

    // ==========================================
    // 10. GeminiApiClient: Massive Scale Parsing
    // ==========================================

    @Test
    fun testGeminiApiClient_ScalabilityMassivePayload() {
        val client = GeminiApiClient()

        val shiftDefsJson = JSONArray()
        for (i in 1..200) {
            shiftDefsJson.put(JSONObject().apply {
                put("code", "C$i")
                put("name", "Turno Codice $i")
                put("startTime", "06:00")
                put("endTime", "14:00")
                put("colorHex", "#%06X".format(i * 1000 and 0xFFFFFF))
                put("isReperibilita", i % 2 == 0)
            })
        }

        val squadsJson = JSONArray()
        for (s in 1..50) {
            val rotArr = JSONArray()
            repeat(30) { rotArr.put("C${(it % 200) + 1}") }
            val empArr = JSONArray()
            repeat(10) { empArr.put("Lavoratore_${s}_$it") }

            squadsJson.put(JSONObject().apply {
                put("squadName", "Squadra Complessa $s")
                put("rotationDays", rotArr)
                put("employees", empArr)
            })
        }

        val hugePayload = JSONObject().apply {
            put("shiftDefinitions", shiftDefsJson)
            put("squads", squadsJson)
            put("notes", "Massive dataset benchmark test")
            put("validityStart", "2026-01-01")
            put("validityEnd", "2026-12-31")
        }.toString()

        val startTime = System.currentTimeMillis()
        val result = client.parseGeminiResponse(hugePayload)
        val duration = System.currentTimeMillis() - startTime

        assertEquals(200, result.shiftDefinitions.size)
        assertEquals(50, result.squads.size)
        assertEquals(30, result.squads[0].rotationDays.size)
        assertEquals(10, result.squads[0].employees.size)
        assertTrue("Parsing 200 shifts and 50 squads should complete rapidly (< 1000ms), took ${duration}ms", duration < 1000)
    }

    // ==========================================
    // 11. GeminiApiClient: Empty and Minimal Payloads
    // ==========================================

    @Test
    fun testGeminiApiClient_EmptyAndMinimalJson() {
        val client = GeminiApiClient()

        // Empty JSON object
        val emptyData = client.parseGeminiResponse("{}")
        assertTrue(emptyData.shiftDefinitions.isEmpty())
        assertTrue(emptyData.squads.isEmpty())
        assertTrue(emptyData.employees.isEmpty())
        assertNull(emptyData.notes)

        // Empty arrays
        val emptyArraysData = client.parseGeminiResponse("""{"shiftDefinitions": [], "squads": []}""")
        assertTrue(emptyArraysData.shiftDefinitions.isEmpty())
        assertTrue(emptyArraysData.squads.isEmpty())

        // Missing fields in shift definitions should use graceful defaults
        val partialShiftJson = """
        {
            "shiftDefinitions": [
                { "code": "X" }
            ],
            "squads": [
                { "squadName": "DefaultSquad", "rotationDays": ["X"] }
            ]
        }
        """.trimIndent()
        val partialData = client.parseGeminiResponse(partialShiftJson)
        assertEquals(1, partialData.shiftDefinitions.size)
        val shiftX = partialData.shiftDefinitions[0]
        assertEquals("X", shiftX.code)
        assertEquals("X", shiftX.name) // defaults to code
        assertEquals("06:00", shiftX.startTime) // default
        assertEquals("13:36", shiftX.endTime) // default
        assertFalse(shiftX.isReperibilita)
    }

    // ==========================================
    // 12. GeminiApiClient: ExecuteRequest Boundaries
    // ==========================================

    @Test
    fun testGeminiApiClient_ExecuteRequestValidationBoundaries() {
        val client = GeminiApiClient()

        // 1. Empty API key
        try {
            client.executeRequest(apiKey = "", imageBase64 = "validBase64")
            fail("Expected IllegalArgumentException for empty API key")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("API Key"))
        }

        // 2. Whitespace-only API key
        try {
            client.executeRequest(apiKey = "   \n\t  ", imageBase64 = "validBase64")
            fail("Expected IllegalArgumentException for blank API key")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("API Key"))
        }

        // 3. Empty image Base64
        try {
            client.executeRequest(apiKey = "valid_key", imageBase64 = "")
            fail("Expected IllegalArgumentException for empty imageBase64")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("immagine"))
        }

        // 4. Whitespace-only image Base64
        try {
            client.executeRequest(apiKey = "valid_key", imageBase64 = "   \r\n ")
            fail("Expected IllegalArgumentException for blank imageBase64")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("immagine"))
        }
    }
}
