package com.turni.calendario.data

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeminiNetworkAndSecurityTest {

    @Before
    fun setUp() {
        SecurePreferencesManager.clear(null)
    }

    @Test
    fun testSecurePreferencesManager_ApiKeyLifecycleAndMasking() {
        // Initially no key
        assertNull(SecurePreferencesManager.getApiKey(null))
        assertFalse(SecurePreferencesManager.hasApiKey(null))
        assertEquals("", SecurePreferencesManager.getMaskedApiKey(null))

        // Save key
        val sampleKey = "AIzaSyB3TestSecretKeyForTurni1234xQ"
        SecurePreferencesManager.saveApiKey(null, sampleKey)
        assertTrue(SecurePreferencesManager.hasApiKey(null))
        assertEquals(sampleKey, SecurePreferencesManager.getApiKey(null))

        // Check masking: first 6 characters and last 3 characters
        val masked = SecurePreferencesManager.getMaskedApiKey(null)
        assertEquals("AIzaSy...4xQ", masked)

        // Test short key masking
        SecurePreferencesManager.saveApiKey(null, "short123")
        assertEquals("••••••••", SecurePreferencesManager.getMaskedApiKey(null))

        // Test clear
        SecurePreferencesManager.clear(null)
        assertNull(SecurePreferencesManager.getApiKey(null))
        assertFalse(SecurePreferencesManager.hasApiKey(null))
    }

    @Test
    fun testSecurePreferencesManager_UserNameLifecycle() {
        // Defaults to null or empty
        assertNull(SecurePreferencesManager.getUserName(null))

        // Change username
        SecurePreferencesManager.saveUserName(null, "Mario Rossi")
        assertEquals("Mario Rossi", SecurePreferencesManager.getUserName(null))

        // Clear resets to default
        SecurePreferencesManager.clear(null)
        assertNull(SecurePreferencesManager.getUserName(null))
    }

    @Test
    fun testGeminiShiftModels_InstantiationAndDefaults() {
        val shiftDef = GeminiShiftDefinition(
            code = "M",
            name = "Mattina",
            startTime = "06:00",
            endTime = "13:36",
            colorHex = "#0284C7",
            isReperibilita = false
        )
        assertEquals("M", shiftDef.code)
        assertEquals("Mattina", shiftDef.name)
        assertEquals("06:00", shiftDef.startTime)
        assertEquals("13:36", shiftDef.endTime)
        assertEquals("#0284C7", shiftDef.colorHex)
        assertFalse(shiftDef.isReperibilita)

        val squad = GeminiSquadRotation(
            squadName = "Squadra A",
            rotationDays = listOf("M", "P", "N", "SMO", "RIP"),
            employees = listOf("Graziani Marco", "Rossi Mario")
        )
        assertEquals("Squadra A", squad.squadName)
        assertEquals(5, squad.rotationDays.size)
        assertEquals(5, squad.cycleDays)
        assertEquals(2, squad.employees.size)

        val shiftData = GeminiShiftData(
            shiftDefinitions = listOf(shiftDef),
            squads = listOf(squad),
            notes = "Test note",
            validityStart = "2026-10-01",
            validityEnd = "2026-12-31"
        )
        assertEquals(1, shiftData.shiftDefinitions.size)
        assertEquals(1, shiftData.squads.size)
        assertEquals("Test note", shiftData.notes)
        assertEquals("2026-10-01", shiftData.validityStart)
        assertEquals("2026-12-31", shiftData.validityEnd)
    }

    @Test
    fun testGeminiApiClient_BuildRequestBodyStructure() {
        val client = GeminiApiClient()
        val dummyBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        val bodyJsonString = client.buildRequestBody(
            imageBase64 = dummyBase64,
            mimeType = "image/png"
        )

        val root = JSONObject(bodyJsonString)
        assertTrue("Request body must contain 'contents'", root.has("contents"))
        val contents = root.getJSONArray("contents")
        assertEquals(1, contents.length())

        val content0 = contents.getJSONObject(0)
        val parts = content0.getJSONArray("parts")
        assertEquals(2, parts.length())

        // Part 0: text prompt
        val textPart = parts.getJSONObject(0)
        assertTrue(textPart.has("text"))
        assertTrue(textPart.getString("text").contains("ferroviari"))

        // Part 1: inline_data image
        val imagePart = parts.getJSONObject(1)
        assertTrue(imagePart.has("inline_data"))
        val inlineData = imagePart.getJSONObject("inline_data")
        assertEquals("image/png", inlineData.getString("mime_type"))
        assertEquals(dummyBase64, inlineData.getString("data"))

        // generationConfig & response_schema
        assertTrue(root.has("generationConfig"))
        val genConfig = root.getJSONObject("generationConfig")
        assertEquals("application/json", genConfig.getString("response_mime_type"))
        assertTrue(genConfig.has("response_schema"))
        val schema = genConfig.getJSONObject("response_schema")
        val props = schema.getJSONObject("properties")
        assertTrue(props.has("shiftDefinitions"))
        assertTrue(props.has("squads"))
    }

    @Test
    fun testGeminiApiClient_ParseDirectShiftJson() {
        val client = GeminiApiClient()
        val jsonPayload = """
        {
          "shiftDefinitions": [
            {
              "code": "M",
              "name": "Mattina",
              "startTime": "06:00",
              "endTime": "13:36",
              "colorHex": "#0284C7",
              "isReperibilita": false
            },
            {
              "code": "P",
              "name": "Pomeriggio",
              "startTime": "14:00",
              "endTime": "21:36",
              "colorHex": "#EF4444",
              "isReperibilita": true
            }
          ],
          "squads": [
            {
              "squadName": "Squadra A",
              "rotationDays": ["M", "P", "N", "SMO", "RIP"],
              "employees": ["Graziani Marco", "Bianchi Luigi"]
            }
          ],
          "validityStart": "2026-10-01",
          "validityEnd": "2026-12-31",
          "notes": "Reperibilità pomeridiana attiva"
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(jsonPayload)
        assertEquals(2, result.shiftDefinitions.size)
        assertEquals("M", result.shiftDefinitions[0].code)
        assertEquals("Mattina", result.shiftDefinitions[0].name)
        assertEquals("06:00", result.shiftDefinitions[0].startTime)
        assertEquals("13:36", result.shiftDefinitions[0].endTime)
        assertFalse(result.shiftDefinitions[0].isReperibilita)

        assertEquals("P", result.shiftDefinitions[1].code)
        assertTrue(result.shiftDefinitions[1].isReperibilita)

        assertEquals(1, result.squads.size)
        val squadA = result.squads[0]
        assertEquals("Squadra A", squadA.squadName)
        assertEquals(listOf("M", "P", "N", "SMO", "RIP"), squadA.rotationDays)
        assertEquals(listOf("Graziani Marco", "Bianchi Luigi"), squadA.employees)

        assertEquals("2026-10-01", result.validityStart)
        assertEquals("2026-12-31", result.validityEnd)
        assertEquals("Reperibilità pomeridiana attiva", result.notes)
    }

    @Test
    fun testGeminiApiClient_ParseGeminiRestEnvelopeWithMarkdown() {
        val client = GeminiApiClient()
        val innerJson = """
        {
          "shiftDefinitions": [
            {
              "code": "NOT",
              "name": "Notte",
              "startTime": "22:00",
              "endTime": "05:36",
              "isReperibilita": false
            }
          ],
          "squads": [
            {
              "squadName": "Squadra Notturna",
              "rotationDays": ["NOT", "SMO", "RIP"]
            }
          ]
        }
        """.trimIndent()

        // Wrap with markdown fences and outer Gemini candidate envelope
        val escapedInnerJson = JSONObject.quote("```json\n$innerJson\n```")
        val outerEnvelope = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": $escapedInnerJson
                  }
                ],
                "role": "model"
              },
              "finishReason": "STOP"
            }
          ]
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(outerEnvelope)
        assertEquals(1, result.shiftDefinitions.size)
        assertEquals("NOT", result.shiftDefinitions[0].code)
        assertEquals("Notte", result.shiftDefinitions[0].name)
        assertEquals("22:00", result.shiftDefinitions[0].startTime)
        assertEquals("05:36", result.shiftDefinitions[0].endTime)

        assertEquals(1, result.squads.size)
        assertEquals("Squadra Notturna", result.squads[0].squadName)
        assertEquals(listOf("NOT", "SMO", "RIP"), result.squads[0].rotationDays)
        assertNotNull(result.rawJson)
    }

    @Test
    fun testGeminiApiClient_InputValidationFailsFast() = runBlocking {
        val client = GeminiApiClient()

        // Empty API key
        val resultBlankKey = client.extractShiftData(
            apiKey = "   ",
            imageBase64 = "validBase64"
        )
        assertTrue(resultBlankKey.isFailure)
        assertTrue(resultBlankKey.exceptionOrNull() is IllegalArgumentException)
        assertTrue(resultBlankKey.exceptionOrNull()!!.message!!.contains("API Key"))

        // Blank image
        val resultBlankImage = client.extractShiftData(
            apiKey = "AIzaSyTestKey123",
            imageBase64 = "   "
        )
        assertTrue(resultBlankImage.isFailure)
        assertTrue(resultBlankImage.exceptionOrNull() is IllegalArgumentException)
        assertTrue(resultBlankImage.exceptionOrNull()!!.message!!.contains("immagine"))
    }

    @Test
    fun testGeminiApiClient_SafetyFilterException() {
        val client = GeminiApiClient()
        val blockedResponse = """
        {
          "candidates": [
            {
              "finishReason": "SAFETY"
            }
          ]
        }
        """.trimIndent()

        try {
            client.parseGeminiResponse(blockedResponse)
            fail("Should have thrown SecurityException")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("filtri di sicurezza"))
        }
    }
}
