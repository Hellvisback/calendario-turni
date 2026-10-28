package com.turni.calendario.data

import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Empirical Stress Test Suite for [GeminiApiClient].
 * Adversarially challenges:
 * 1. Truncated payloads (outer envelope and inner JSON)
 * 2. Non-conforming JSON responses (HTML, plain text, conversational markdown wrappers, invalid element types)
 * 3. Rate limit 429 and HTTP error decoding
 * 4. Empty and blank responses
 * 5. Unicode and adversarial boundary inputs
 */
class GeminiApiClientStressTest {

    private val client = GeminiApiClient()

    // =========================================================================
    // 1. Truncated Payloads
    // =========================================================================

    @Test
    fun testTruncatedPayload_OuterEnvelopeThrowsJsonException() {
        // Payload cut off mid-stream at outer envelope level
        val truncatedEnvelope = """{"candidates": [{"content": {"parts": [{"text": """

        try {
            client.parseGeminiResponse(truncatedEnvelope)
            fail("Expected JSONException for abruptly truncated outer envelope")
        } catch (e: Exception) {
            assertTrue(
                "Must fail with JSONException or IllegalStateException, got: ${e.javaClass.name}",
                e is JSONException || e is IllegalStateException
            )
        }
    }

    @Test
    fun testTruncatedPayload_InnerJsonTextTruncatedThrowsJsonException() {
        // Outer envelope is valid JSON, but inner parts text was truncated (e.g. model hit MAX_TOKENS)
        val truncatedInner = """{"shiftDefinitions": [{"code": "M", "name": "Mattina", "startTime": "06:"""
        val escapedInner = JSONObject.quote(truncatedInner)
        val envelope = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  { "text": $escapedInner }
                ]
              },
              "finishReason": "MAX_TOKENS"
            }
          ]
        }
        """.trimIndent()

        try {
            client.parseGeminiResponse(envelope)
            fail("Expected JSONException when inner JSON text is truncated")
        } catch (e: Exception) {
            assertTrue(
                "Must explain an incomplete model response, got: ${e.javaClass.name}",
                e is IllegalStateException && e.message?.contains("incompleta")==true
            )
        }
    }

    // =========================================================================
    // 2. Non-Conforming JSON Responses
    // =========================================================================

    @Test
    fun testNonConformingJson_HtmlGatewayError() {
        // Upstream CDN or proxy returns HTML error page (e.g. Cloudflare / Google 502/503)
        val htmlResponse = """
        <!DOCTYPE html>
        <html>
        <head><title>502 Bad Gateway</title></head>
        <body><center><h1>502 Bad Gateway</h1></center></body>
        </html>
        """.trimIndent()

        try {
            client.parseGeminiResponse(htmlResponse)
            fail("Expected JSONException when response is HTML instead of JSON")
        } catch (e: Exception) {
            assertTrue("Expected JSONException on HTML body, got ${e.javaClass.name}", e is JSONException)
        }
    }

    @Test
    fun testNonConformingJson_RootIsJsonArrayInsteadOfObject() {
        // Gemini or proxy returns a JSON array at root level instead of JSONObject
        val arrayJson = """[{"code": "M", "name": "Mattina"}]"""

        try {
            client.parseGeminiResponse(arrayJson)
            fail("Expected JSONException when root is JSONArray instead of JSONObject")
        } catch (e: Exception) {
            assertTrue("Expected JSONException, got ${e.javaClass.name}", e is JSONException)
        }
    }

    @Test
    fun testNonConformingJson_ConversationalMarkdownWrapping() {
        // When LLM precedes or succeeds markdown fences with explanatory text
        val conversationalPayload = """
        Ecco l'estrazione dei turni da te richiesta:
        ```json
        {
          "shiftDefinitions": [
            { "code": "M", "name": "Mattina", "startTime": "06:00", "endTime": "13:36" }
          ],
          "squads": [
            { "squadName": "Squadra 1", "rotationDays": ["M"] }
          ]
        }
        ```
        Spero che questa analisi sia utile per il tuo calendario!
        """.trimIndent()

        // Demonstrates whether current parser handles conversational text wrapping code fences
        val result = runCatching { client.parseGeminiResponse(conversationalPayload) }
        // Empirical observation: current parser only checks startsWith("```json"), so this fails
        assertTrue(
            "Parser fails on conversational markdown without regex block extraction",
            result.isFailure
        )
    }

    @Test
    fun testNonConformingJson_PrimitiveInsideShiftDefinitionsArray() {
        // Malformed JSON: shiftDefinitions contains integer or string instead of JSONObject
        val malformedArrayPayload = """
        {
          "shiftDefinitions": [
            "NOT_AN_OBJECT",
            12345
          ],
          "squads": []
        }
        """.trimIndent()

        try {
            client.parseGeminiResponse(malformedArrayPayload)
            fail("Expected JSONException when array items are not JSONObjects")
        } catch (e: Exception) {
            assertTrue("Must throw JSONException on malformed array items", e is JSONException)
        }
    }

    // =========================================================================
    // 3. Empty Responses & Missing Candidates
    // =========================================================================

    @Test
    fun testEmptyResponses_EmptyStringAndWhitespace() {
        // Completely empty string
        val resultEmpty = runCatching { client.parseGeminiResponse("") }
        assertTrue("Empty string must fail cleanly", resultEmpty.isFailure)
        assertTrue(resultEmpty.exceptionOrNull() is JSONException)

        // Whitespace only
        val resultWhitespace = runCatching { client.parseGeminiResponse("   \n\t  ") }
        assertTrue("Whitespace-only must fail cleanly", resultWhitespace.isFailure)
        assertTrue(resultWhitespace.exceptionOrNull() is JSONException)
    }

    @Test
    fun testEmptyResponses_EmptyJsonObject() {
        // Empty JSON object: should return empty GeminiShiftData without crashing
        val data = client.parseGeminiResponse("{}")
        assertNotNull(data)
        assertTrue(data.shiftDefinitions.isEmpty())
        assertTrue(data.squads.isEmpty())
        assertTrue(data.employees.isEmpty())
        assertNull(data.notes)
        assertNull(data.validityStart)
        assertNull(data.validityEnd)
    }

    @Test
    fun testEmptyResponses_EmptyCandidatesArray() {
        val payload = """{ "candidates": [] }"""
        val result = runCatching { client.parseGeminiResponse(payload) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("candidates vuoto"))
    }

    @Test
    fun testEmptyResponses_CandidateWithNullOrEmptyContent() {
        // Missing content object
        val missingContent = """{ "candidates": [{}] }"""
        val res1 = runCatching { client.parseGeminiResponse(missingContent) }
        assertTrue(res1.isFailure)
        assertTrue(res1.exceptionOrNull()!!.message!!.contains("manca l'oggetto 'content'"))

        // Missing parts array
        val missingParts = """{ "candidates": [{ "content": {} }] }"""
        val res2 = runCatching { client.parseGeminiResponse(missingParts) }
        assertTrue(res2.isFailure)
        assertTrue(res2.exceptionOrNull()!!.message!!.contains("manca l'array 'parts'"))

        // Empty parts array
        val emptyParts = """{ "candidates": [{ "content": { "parts": [] } }] }"""
        val res3 = runCatching { client.parseGeminiResponse(emptyParts) }
        assertTrue(res3.isFailure)
        assertTrue(res3.exceptionOrNull()!!.message!!.contains("array 'parts' vuoto"))
    }

    @Test
    fun testEmptyResponses_PartWithEmptyText() {
        val emptyTextEnvelope = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  { "text": "" }
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val result = runCatching { client.parseGeminiResponse(emptyTextEnvelope) }
        assertTrue("Empty text in parts must throw JSONException", result.isFailure)
        assertTrue(result.exceptionOrNull() is JSONException)
    }

    // =========================================================================
    // 4. Rate Limit 429 & HTTP Error Decoding Stress
    // =========================================================================

    @Test
    fun testRateLimit429_ErrorMessageExtraction() {
        // Test extractErrorMessage via reflection to stress-test error parsing logic
        val method = GeminiApiClient::class.java.getDeclaredMethod(
            "extractErrorMessage",
            String::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }

        // Standard Google Cloud error format
        val google429Body = """
        {
          "error": {
            "code": 429,
            "message": "Resource has been exhausted (e.g. check quota-per-minute).",
            "status": "RESOURCE_EXHAUSTED"
          }
        }
        """.trimIndent()
        val extracted1 = method.invoke(client, google429Body, 429) as String
        assertEquals("Resource has been exhausted (e.g. check quota-per-minute).", extracted1)

        // Non-JSON error body (plain text)
        val textErrorBody = "Quota exceeded for project 123456789"
        val extracted2 = method.invoke(client, textErrorBody, 429) as String
        assertEquals("Quota exceeded for project 123456789", extracted2)

        // Blank error body
        val extracted3 = method.invoke(client, "", 429) as String
        assertEquals("Errore HTTP 429", extracted3)
    }

    // =========================================================================
    // 5. Robustness & Fault Tolerance on Missing Fields
    // =========================================================================

    @Test
    fun testFaultTolerance_MissingShiftFieldsFallbacks() {
        val payload = """
        {
          "shiftDefinitions": [
            {
              "code": "M"
            },
            {
              "code": "   "
            }
          ],
          "squads": [
            {
              "squadName": "Squadra 1",
              "rotationDays": ["M", ""]
            }
          ]
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(payload)
        // Blank code ("   ") must be skipped
        assertEquals(1, result.shiftDefinitions.size)
        val shift = result.shiftDefinitions[0]
        assertEquals("M", shift.code)
        assertEquals("M", shift.name) // fallback to code
        assertEquals("06:00", shift.startTime) // fallback default
        assertEquals("13:36", shift.endTime) // fallback default
        assertNull(shift.colorHex)
        assertFalse(shift.isReperibilita)

        // Blank day in rotationDays must be filtered out
        assertEquals(1, result.squads.size)
        assertEquals(listOf("M"), result.squads[0].rotationDays)
    }

    @Test
    fun testFaultTolerance_CorruptedUnicodeAndEmoji() {
        val unicodePayload = """
        {
          "shiftDefinitions": [
            {
              "code": "🌅",
              "name": "Mattina \uD83D\uDE00 Speciale \u0000",
              "startTime": "06:00",
              "endTime": "13:36"
            }
          ],
          "squads": [
            {
              "squadName": "Squadra 🚂 Ferroviaria",
              "rotationDays": ["🌅"]
            }
          ],
          "notes": "Note con caratteri speciali: àèìòù € © 漢字"
        }
        """.trimIndent()

        val result = client.parseGeminiResponse(unicodePayload)
        assertEquals(1, result.shiftDefinitions.size)
        assertEquals("🌅", result.shiftDefinitions[0].code)
        assertTrue(result.shiftDefinitions[0].name.contains("Speciale"))
        assertEquals("Squadra 🚂 Ferroviaria", result.squads[0].squadName)
        assertEquals(listOf("🌅"), result.squads[0].rotationDays)
        assertEquals("Note con caratteri speciali: àèìòù € © 漢字", result.notes)
    }
}
