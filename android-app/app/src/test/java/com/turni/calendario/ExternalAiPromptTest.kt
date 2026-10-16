package com.turni.calendario

import com.turni.calendario.data.GeminiShiftMapper
import com.turni.calendario.data.SecurePreferencesManager
import com.turni.calendario.repository.ShiftRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for External AI Prompt Generator and pasted JSON extraction.
 * Verifies prompt personalization, markdown fence cleaning, conversational text isolation,
 * and seamless domain mapping into ShiftRepository.
 */
class ExternalAiPromptTest {

    @Before
    fun setUp() {
        ShiftRepository.calendarShifts.clear()
        ShiftRepository.dayExtraInfo.clear()
        SecurePreferencesManager.clear()
    }

    @After
    fun tearDown() {
        ShiftRepository.calendarShifts.clear()
        ShiftRepository.dayExtraInfo.clear()
        SecurePreferencesManager.clear()
    }

    @Test
    fun testGenerateExternalAiPrompt_IncludesTargetUserWhenProvided() {
        val promptWithUser = GeminiShiftMapper.generateExternalAiPrompt("Marco Graziani")
        assertTrue(promptWithUser.contains("Marco Graziani"))
        assertTrue(promptWithUser.contains("shiftDefinitions"))
        assertTrue(promptWithUser.contains("squads"))
        assertTrue(promptWithUser.contains("rotationDays"))

        val promptWithoutUser = GeminiShiftMapper.generateExternalAiPrompt(null)
        assertFalse(promptWithoutUser.contains("INDIVIDUAZIONE E ASSEGNAZIONE LAVORATORE"))
        assertTrue(promptWithoutUser.contains("shiftDefinitions"))
    }

    @Test
    fun testExtractJsonPayload_RawJson() {
        val raw = """{"shiftDefinitions":[],"squads":[]}"""
        val extracted = GeminiShiftMapper.extractJsonPayload(raw)
        assertEquals(raw, extracted)
    }

    @Test
    fun testExtractJsonPayload_MarkdownFencedJson() {
        val text = """
            Certamente! Ecco i dati dei turni estratti dal documento:
            ```json
            {
              "shiftDefinitions": [
                { "code": "M", "name": "Mattina", "startTime": "06:00", "endTime": "13:36" }
              ],
              "squads": [
                { "squadName": "Squadra A", "rotationDays": ["M", "P"], "employees": ["Rossi Mario"] }
              ]
            }
            ```
            Fammi sapere se hai bisogno di altre modifiche!
        """.trimIndent()

        val extracted = GeminiShiftMapper.extractJsonPayload(text)
        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
        assertTrue(extracted.contains("\"Squadra A\""))
        assertFalse(extracted.contains("Certamente!"))
        assertFalse(extracted.contains("Fammi sapere"))
    }

    @Test
    fun testExtractJsonPayload_ConversationalWithoutMarkdown() {
        val text = """
            Ecco il JSON generato per te:
            {
              "shiftDefinitions": [
                { "code": "N", "name": "Notte", "startTime": "22:00", "endTime": "05:36" }
              ],
              "squads": [
                { "squadName": "Squadra Notturna", "rotationDays": ["N", "SMO", "RIP"], "employees": [] }
              ]
            }
            Spero sia utile.
        """.trimIndent()

        val extracted = GeminiShiftMapper.extractJsonPayload(text)
        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
        assertTrue(extracted.contains("\"Squadra Notturna\""))
        assertFalse(extracted.contains("Ecco il JSON"))
    }

    @Test
    fun testParseFromExternalAiText_SuccessAndRepositoryRegistration() {
        val chatGptOutput = """
            Ecco la rotazione completa:
            ```json
            {
              "shiftDefinitions": [
                { "code": "M", "name": "Mattina", "startTime": "06:00", "endTime": "13:36", "colorHex": "#38BDF8" },
                { "code": "P", "name": "Pomeriggio", "startTime": "14:00", "endTime": "21:36", "colorHex": "#F59E0B" }
              ],
              "squads": [
                {
                  "squadName": "Squadra B",
                  "cycleDays": 4,
                  "rotationDays": ["M", "P", "SMO", "RIP"],
                  "employees": ["Graziani Marco", "Bianchi Luca"]
                }
              ]
            }
            ```
        """.trimIndent()

        val result = GeminiShiftMapper.parseFromExternalAiText(chatGptOutput)
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals(2, data!!.shiftDefinitions.size)
        assertEquals(1, data.squads.size)
        assertEquals("Squadra B", data.squads[0].squadName)

        // Test registering with user match
        val setupResult = GeminiShiftMapper.registerExtractedData(
            repository = ShiftRepository,
            data = data,
            targetUserName = "Marco Graziani"
        )
        assertEquals("Squadra B", setupResult.matchedSquadName)
        assertEquals("rotazione_squadra_b", setupResult.defaultRotationId)
        assertEquals("rotazione_squadra_b", ShiftRepository.defaultRotationId)
    }

    @Test
    fun testParseFromExternalAiText_EmptyOrInvalidTextReturnsFailure() {
        val emptyResult = GeminiShiftMapper.parseFromExternalAiText("")
        assertTrue(emptyResult.isFailure)

        val nonJsonResult = GeminiShiftMapper.parseFromExternalAiText("Non sono riuscito a leggere il file.")
        assertTrue(nonJsonResult.isFailure)
        assertTrue(nonJsonResult.exceptionOrNull()?.message?.contains("JSON") == true)
    }
}
