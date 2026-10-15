package com.turni.calendario

import com.turni.calendario.data.GeminiApiClient
import com.turni.calendario.data.GeminiShiftData
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Rigorous test suite for Gemini Flash Vision JSON building, response parsing,
 * schema enforcement, and error resilience (F2, F6).
 */
class GeminiApiJsonTest {

    private lateinit var client: GeminiApiClient

    @Before
    fun setUp() {
        client = GeminiApiClient()
    }

    // ==========================================
    // Tier 1: Request Construction & JSON Schema (F2)
    // ==========================================

    @Test
    fun testBuildRequestBody_ContainsMultimodalPartsAndValidJson() {
        val dummyBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        val requestJsonString = client.buildRequestBody(
            imageBase64 = dummyBase64,
            mimeType = "image/jpeg"
        )

        val root = JSONObject(requestJsonString)

        // Contents verification
        assertTrue("Request must contain 'contents'", root.has("contents"))
        val contents = root.getJSONArray("contents")
        assertEquals(1, contents.length())

        val contentObj = contents.getJSONObject(0)
        assertEquals("user", contentObj.getString("role"))

        val parts = contentObj.getJSONArray("parts")
        assertEquals("Parts must have prompt text and inline image", 2, parts.length())

        val textPart = parts.getJSONObject(0)
        assertTrue(textPart.has("text"))
        val promptText = textPart.getString("text")
        assertTrue("Prompt should mention shift analysis", promptText.contains("turni"))

        val imagePart = parts.getJSONObject(1)
        assertTrue(imagePart.has("inline_data"))
        val inlineData = imagePart.getJSONObject("inline_data")
        assertEquals("image/jpeg", inlineData.getString("mime_type"))
        assertEquals(dummyBase64, inlineData.getString("data"))
    }

    @Test
    fun testBuildRequestBody_CustomPromptAndMimeType() {
        val customPrompt = "Special prompt for hospital pediatric shift roster"
        val pngBase64 = "iVBORw0KGgoAAAANSUhEUgAA"
        val requestJsonString = client.buildRequestBody(
            imageBase64 = pngBase64,
            mimeType = "image/png",
            promptText = customPrompt
        )

        val root = JSONObject(requestJsonString)
        val parts = root.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertEquals(customPrompt, parts.getJSONObject(0).getString("text"))
        assertEquals("image/png", parts.getJSONObject(1).getJSONObject("inline_data").getString("mime_type"))
    }

    @Test
    fun testBuildRequestBody_ResponseSchemaStructure() {
        val requestJsonString = client.buildRequestBody(
            imageBase64 = "validDummyData",
            mimeType = "image/jpeg"
        )

        val root = JSONObject(requestJsonString)
        val genConfig = root.getJSONObject("generationConfig")
        assertEquals("application/json", genConfig.getString("response_mime_type"))

        val schema = genConfig.getJSONObject("response_schema")
        assertEquals("OBJECT", schema.getString("type"))
        val required = schema.getJSONArray("required")
        val requiredList = (0 until required.length()).map { required.getString(it) }
        assertTrue("shiftDefinitions must be required", requiredList.contains("shiftDefinitions"))
        assertTrue("squads must be required", requiredList.contains("squads"))

        val properties = schema.getJSONObject("properties")
        assertTrue(properties.has("shiftDefinitions"))
        assertTrue(properties.has("squads"))
        assertTrue(properties.has("validityStart"))
        assertTrue(properties.has("validityEnd"))
        assertTrue(properties.has("notes"))
    }

    // ==========================================
    // Tier 1: Response Parsing — Valid Structures (F6)
    // ==========================================

    @Test
    fun testParseGeminiResponse_StandardRosterJson() {
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
            },
            {
              "code": "N",
              "name": "Notte",
              "startTime": "22:00",
              "endTime": "05:36",
              "colorHex": "#22C55E",
              "isReperibilita": false
            },
            {
              "code": "MD",
              "name": "Mattina Domenicale",
              "startTime": "08:36",
              "endTime": "16:12",
              "colorHex": "#F59E0B",
              "isReperibilita": true
            }
          ],
          "squads": [
            {
              "squadName": "Squadra A",
              "rotationDays": ["M", "M", "P", "P", "N", "SMO", "RIP"],
              "employees": ["Rossi Mario", "Bianchi Luigi"]
            },
            {
              "squadName": "Squadra B",
              "rotationDays": ["P", "P", "N", "N", "SMO", "RIP", "M"],
              "employees": ["Graziani Marco", "Verdi Giuseppe"]
            }
          ],
          "validityStart": "2026-10-01",
          "validityEnd": "2026-10-28",
          "notes": "Reperibilità pomeridiana su turno P e festiva su MD"
        }
        """.trimIndent()

        val data: GeminiShiftData = client.parseGeminiResponse(jsonPayload)

        assertEquals(4, data.shiftDefinitions.size)
        assertEquals("M", data.shiftDefinitions[0].code)
        assertEquals("Mattina", data.shiftDefinitions[0].name)
        assertEquals("06:00", data.shiftDefinitions[0].startTime)
        assertEquals("13:36", data.shiftDefinitions[0].endTime)
        assertEquals("#0284C7", data.shiftDefinitions[0].colorHex)
        assertFalse(data.shiftDefinitions[0].isReperibilita)

        assertEquals("P", data.shiftDefinitions[1].code)
        assertTrue(data.shiftDefinitions[1].isReperibilita)

        assertEquals("MD", data.shiftDefinitions[3].code)
        assertTrue(data.shiftDefinitions[3].isReperibilita)

        assertEquals(2, data.squads.size)
        assertEquals("Squadra A", data.squads[0].squadName)
        assertEquals(7, data.squads[0].rotationDays.size)
        assertEquals(listOf("M", "M", "P", "P", "N", "SMO", "RIP"), data.squads[0].rotationDays)
        assertEquals(listOf("Rossi Mario", "Bianchi Luigi"), data.squads[0].employees)

        assertEquals("Squadra B", data.squads[1].squadName)
        assertEquals(listOf("Graziani Marco", "Verdi Giuseppe"), data.squads[1].employees)

        assertEquals("2026-10-01", data.validityStart)
        assertEquals("2026-10-28", data.validityEnd)
        assertEquals("Reperibilità pomeridiana su turno P e festiva su MD", data.notes)
    }

    @Test
    fun testParseGeminiResponse_FullRestEnvelopeWithCandidates() {
        val innerJson = """
        {
          "shiftDefinitions": [
            { "code": "GIO", "name": "Giornaliero", "startTime": "08:30", "endTime": "17:06" }
          ],
          "squads": [
            { "squadName": "Ufficio Tecnico", "rotationDays": ["GIO", "GIO", "GIO", "GIO", "GIO", "RIP", "RIP"] }
          ]
        }
        """.trimIndent()

        val quotedInner = JSONObject.quote(innerJson)
        val envelope = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  { "text": $quotedInner }
                ],
                "role": "model"
              },
              "finishReason": "STOP"
            }
          ]
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(envelope)
        assertEquals(1, result.shiftDefinitions.size)
        assertEquals("GIO", result.shiftDefinitions[0].code)
        assertEquals("08:30", result.shiftDefinitions[0].startTime)
        assertEquals("17:06", result.shiftDefinitions[0].endTime)
        assertEquals(1, result.squads.size)
        assertEquals(7, result.squads[0].rotationDays.size)
        assertNotNull(result.rawJson)
    }

    @Test
    fun testParseGeminiResponse_MarkdownCodeBlockFences() {
        val rawMarkdown = """
        ```json
        {
          "shiftDefinitions": [
            { "code": "NOT", "name": "Notte", "startTime": "22:00", "endTime": "05:36" }
          ],
          "squads": [
            { "squadName": "Notturni", "rotationDays": ["NOT", "SMO", "RIP"] }
          ]
        }
        ```
        """.trimIndent()

        val result = client.parseGeminiResponse(rawMarkdown)
        assertEquals(1, result.shiftDefinitions.size)
        assertEquals("NOT", result.shiftDefinitions[0].code)
        assertEquals("Notturni", result.squads[0].squadName)
    }

    @Test
    fun testParseGeminiResponse_AlternativeKeyNamesFallback() {
        // Models might sometimes return "shiftHours" instead of "shiftDefinitions"
        // and "teams" instead of "squads" with "rotationSequence"
        val altJson = """
        {
          "shiftHours": [
            { "code": "M", "name": "Mattina", "startTime": "06:00", "endTime": "14:00" },
            { "code": "P", "name": "Pomeriggio", "startTime": "14:00", "endTime": "22:00" }
          ],
          "teams": [
            {
              "teamName": "Gruppo 1",
              "rotationSequence": ["M", "P", "RIP"],
              "employees": [{"name": "Marco Graziani"}]
            }
          ]
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(altJson)
        assertEquals(2, result.shiftDefinitions.size)
        assertEquals("M", result.shiftDefinitions[0].code)
        assertEquals("P", result.shiftDefinitions[1].code)

        assertEquals(1, result.squads.size)
        assertEquals("Gruppo 1", result.squads[0].squadName)
        assertEquals(listOf("M", "P", "RIP"), result.squads[0].rotationDays)
        assertEquals(listOf("Marco Graziani"), result.squads[0].employees)
    }

    @Test
    fun testParseGeminiResponse_DetailedEmployeeAssignmentsWithDates() {
        val payloadWithEmployees = """
        {
          "shiftDefinitions": [
            { "code": "M", "name": "Mattina", "startTime": "06:00", "endTime": "13:36" }
          ],
          "squads": [
            { "squadName": "Squadra A", "rotationDays": ["M", "RIP"] }
          ],
          "employees": [
            {
              "name": "Graziani Marco",
              "teamName": "Squadra A",
              "role": "Tecnico Specialista",
              "employeeId": "8050253",
              "isOnCall": true,
              "dailyShifts": [
                { "date": "2026-10-15", "code": "M" },
                { "date": "2026-10-16", "code": "RIP" }
              ]
            }
          ]
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(payloadWithEmployees)
        assertEquals(1, result.employees.size)
        val emp = result.employees[0]
        assertEquals("Graziani Marco", emp.name)
        assertEquals("Squadra A", emp.squadName)
        assertEquals("Tecnico Specialista", emp.role)
        assertEquals("8050253", emp.employeeId)
        assertTrue(emp.isOnCall)
        assertEquals(2, emp.dailyShifts.size)
        assertEquals("M", emp.dailyShifts["2026-10-15"])
        assertEquals("RIP", emp.dailyShifts["2026-10-16"])
    }

    // ==========================================
    // Tier 2: Boundary, Corner Cases & Resilience (F2, F6)
    // ==========================================

    @Test
    fun testValidation_BlankApiKeyThrowsDescriptiveException() = runBlocking {
        val result = client.extractShiftData(
            apiKey = "   ",
            imageBase64 = "validBase64"
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Must throw IllegalArgumentException", ex is IllegalArgumentException)
        assertTrue("Must mention API Key in Italian", ex!!.message!!.contains("API Key"))
    }

    @Test
    fun testValidation_BlankImageBase64ThrowsDescriptiveException() = runBlocking {
        val result = client.extractShiftData(
            apiKey = "valid_api_key_12345",
            imageBase64 = ""
        )
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
        assertTrue("Must mention immagine in Italian", ex!!.message!!.contains("immagine"))
    }

    @Test
    fun testSafetyFilter_ThrowsSecurityException() {
        val safetyBlockedJson = """
        {
          "candidates": [
            {
              "finishReason": "SAFETY"
            }
          ]
        }
        """.trimIndent()

        try {
            client.parseGeminiResponse(safetyBlockedJson)
            fail("Should throw SecurityException on SAFETY block")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("filtri di sicurezza"))
        }
    }

    @Test
    fun testEmptyCandidatesArray_ThrowsIllegalStateException() {
        val emptyCandidates = """{ "candidates": [] }"""
        try {
            client.parseGeminiResponse(emptyCandidates)
            fail("Should throw IllegalStateException on empty candidates")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("vuoto"))
        }
    }

    @Test
    fun testMalformedJson_ThrowsJSONException() {
        val brokenJson = "{ \"shiftDefinitions\": [ { \"code\": \"M\" " // unclosed
        try {
            client.parseGeminiResponse(brokenJson)
            fail("Should throw JSONException on malformed JSON")
        } catch (e: JSONException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun testBlankAndMissingCodes_GracefullySkipped() {
        val jsonWithBlanks = """
        {
          "shiftDefinitions": [
            { "code": "", "name": "Empty Code", "startTime": "00:00", "endTime": "00:00" },
            { "code": "   ", "name": "Whitespace Code", "startTime": "00:00", "endTime": "00:00" },
            { "code": "VALID", "name": "Valid Code", "startTime": "08:00", "endTime": "16:00" }
          ],
          "squads": [
            {
              "squadName": "Squadra 1",
              "rotationDays": ["", "  ", "VALID", ""]
            }
          ]
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(jsonWithBlanks)
        assertEquals("Blank codes should be skipped", 1, result.shiftDefinitions.size)
        assertEquals("VALID", result.shiftDefinitions[0].code)

        assertEquals(1, result.squads.size)
        assertEquals(listOf("VALID"), result.squads[0].rotationDays)
    }

    @Test
    fun testNullOptionalFields_ParsedCorrectly() {
        val jsonWithNulls = """
        {
          "shiftDefinitions": [
            { "code": "M", "name": "Mattina", "startTime": "06:00", "endTime": "13:36", "colorHex": null }
          ],
          "squads": [
            { "squadName": "Squadra Nulls", "rotationDays": ["M"] }
          ],
          "notes": null,
          "validityStart": null,
          "validityEnd": null
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(jsonWithNulls)
        assertNull(result.shiftDefinitions[0].colorHex)
        assertNull(result.notes)
        assertNull(result.validityStart)
        assertNull(result.validityEnd)
    }

    @Test
    fun testLargeRosterPayload_Handles50SquadsEffortlessly() {
        val root = JSONObject()
        val shiftsArr = JSONArray()
        shiftsArr.put(JSONObject().apply {
            put("code", "M")
            put("name", "Mattina")
            put("startTime", "06:00")
            put("endTime", "14:00")
        })
        shiftsArr.put(JSONObject().apply {
            put("code", "P")
            put("name", "Pomeriggio")
            put("startTime", "14:00")
            put("endTime", "22:00")
        })
        shiftsArr.put(JSONObject().apply {
            put("code", "N")
            put("name", "Notte")
            put("startTime", "22:00")
            put("endTime", "06:00")
        })
        root.put("shiftDefinitions", shiftsArr)

        val squadsArr = JSONArray()
        for (i in 1..50) {
            val sqObj = JSONObject().apply {
                put("squadName", "Squadra $i")
                put("rotationDays", JSONArray(listOf("M", "P", "N", "SMO", "RIP")))
                put("employees", JSONArray(listOf("Worker_${i}_A", "Worker_${i}_B")))
            }
            squadsArr.put(sqObj)
        }
        root.put("squads", squadsArr)

        val parsed = client.parseGeminiResponse(root.toString())
        assertEquals(3, parsed.shiftDefinitions.size)
        assertEquals(50, parsed.squads.size)
        assertEquals("Squadra 50", parsed.squads[49].squadName)
        assertEquals(2, parsed.squads[49].employees.size)
    }
}
