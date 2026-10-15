package com.turni.calendario

import com.turni.calendario.data.GeminiEmployeeAssignment
import com.turni.calendario.data.GeminiShiftData
import com.turni.calendario.data.GeminiShiftMapper
import com.turni.calendario.data.GeminiSquadRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Empirical Stress Test Suite for [GeminiShiftMapper.matchUserSquad]
 * and [GeminiShiftMapper.calculateNameMatchScore].
 *
 * Authored by Empirical Challenger 1 (Milestone 3).
 * Evaluates:
 * 1. Accents and diacritics normalization
 * 2. Typographical apostrophes (U+2019 vs U+0027) & space variations
 * 3. Compound first names and composite surnames (De Luca, Van Der Bellen, Gian Marco)
 * 4. Punctuation, delimiters, noise annotations (e.g. "(Capoturno)")
 * 5. Partial homonymy & false positive vulnerabilities
 * 6. Adversarial inputs & crash resistance
 */
class GeminiUserMatchingStressTest {

    // =========================================================================
    // 1. ACCENTS & DIACRITICS
    // =========================================================================

    @Test
    fun testAccents_MatchingBehavior() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra A", listOf("M"), listOf("Niccolò Paganini")),
                GeminiSquadRotation("Squadra B", listOf("P"), listOf("René Dupont")),
                GeminiSquadRotation("Squadra C", listOf("N"), listOf("José Garcia"))
            )
        )

        // Case 1A: Single token without accent vs candidate with accent: "Niccolo" vs "Niccolò Paganini"
        val scoreSingleNoAccent = GeminiShiftMapper.calculateNameMatchScore("Niccolo", "Niccolò Paganini")
        val matchSingleNoAccent = GeminiShiftMapper.matchUserSquad(data, "Niccolo")
        println("[ACCENT TEST 1A] 'Niccolo' vs 'Niccolò Paganini' -> score: $scoreSingleNoAccent, match: $matchSingleNoAccent")
        assertEquals(80, scoreSingleNoAccent)
        assertEquals("Squadra A", matchSingleNoAccent)

        // Case 1B: Two tokens without accent: "Niccolo Paganini" vs "Niccolò Paganini"
        val scoreTwoTokens = GeminiShiftMapper.calculateNameMatchScore("Niccolo Paganini", "Niccolò Paganini")
        val matchTwoTokens = GeminiShiftMapper.matchUserSquad(data, "Niccolo Paganini")
        println("[ACCENT TEST 1B] 'Niccolo Paganini' vs 'Niccolò Paganini' -> score: $scoreTwoTokens, match: $matchTwoTokens")
        assertEquals(100, scoreTwoTokens)
        assertEquals("Squadra A", matchTwoTokens)

        // Case 1C: René vs Rene
        val scoreRene = GeminiShiftMapper.calculateNameMatchScore("Rene", "René Dupont")
        val matchRene = GeminiShiftMapper.matchUserSquad(data, "Rene")
        println("[ACCENT TEST 1C] 'Rene' vs 'René Dupont' -> score: $scoreRene, match: $matchRene")
        assertEquals(80, scoreRene)
        assertEquals("Squadra B", matchRene)
    }

    // =========================================================================
    // 2. APOSTROPHES & QUOTATION MARKS
    // =========================================================================

    @Test
    fun testApostrophes_AsciiVsTypographicalCurly() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra 1", listOf("M"), listOf("Marco D'Angelo")),
                GeminiSquadRotation("Squadra 2", listOf("P"), listOf("Giuseppe Dell'Amico"))
            )
        )

        // Case 2A: Exact ASCII match
        val scoreAscii = GeminiShiftMapper.calculateNameMatchScore("Marco D'Angelo", "Marco D'Angelo")
        assertEquals(100, scoreAscii)

        // Case 2B: Typographical curly apostrophe (U+2019) 'Marco D’Angelo' vs ASCII 'Marco D'Angelo'
        val scoreFullCurly = GeminiShiftMapper.calculateNameMatchScore("Marco D’Angelo", "Marco D'Angelo")
        val matchFullCurly = GeminiShiftMapper.matchUserSquad(data, "Marco D’Angelo")
        println("[APOSTROPHE TEST 2B] 'Marco D’Angelo' (U+2019) vs 'Marco D'Angelo' (ASCII) -> score: $scoreFullCurly, match: $matchFullCurly")
        assertEquals(100, scoreFullCurly)
        assertEquals("Squadra 1", matchFullCurly)

        // Case 2C: Single surname with curly apostrophe: "D’Angelo" vs candidate "Marco D'Angelo"
        val scoreSurnameCurly = GeminiShiftMapper.calculateNameMatchScore("D’Angelo", "Marco D'Angelo")
        val matchSurnameCurly = GeminiShiftMapper.matchUserSquad(data, "D’Angelo")
        println("[APOSTROPHE TEST 2C] Single surname 'D’Angelo' (U+2019) vs candidate 'Marco D'Angelo' -> score: $scoreSurnameCurly, match: $matchSurnameCurly")
        assertEquals(80, scoreSurnameCurly)
        assertEquals("Squadra 1", matchSurnameCurly)

        // Case 2D: Space after apostrophe in PDF OCR: "Marco D' Angelo" vs "Marco D'Angelo"
        val scoreSpaceAfter = GeminiShiftMapper.calculateNameMatchScore("Marco D' Angelo", "Marco D'Angelo")
        val matchSpaceAfter = GeminiShiftMapper.matchUserSquad(data, "Marco D' Angelo")
        println("[APOSTROPHE TEST 2D] Space after apostrophe 'Marco D' Angelo' vs 'Marco D'Angelo' -> score: $scoreSpaceAfter, match: $matchSpaceAfter")
        assertEquals(90, scoreSpaceAfter)
        assertEquals("Squadra 1", matchSpaceAfter)
    }

    // =========================================================================
    // 3. COMPOUND NAMES (Nomi Composti) & PREFIXES
    // =========================================================================

    @Test
    fun testCompoundNames_GianMarcoVsGianmarco() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra Alfa", listOf("M"), listOf("Gianmarco Rossi")),
                GeminiSquadRotation("Squadra Beta", listOf("P"), listOf("Piergiorgio Bianchi"))
            )
        )

        // Case 3A: Split query vs fused candidate: "Gian Marco Rossi" vs "Gianmarco Rossi"
        val scoreSplitQuery = GeminiShiftMapper.calculateNameMatchScore("Gian Marco Rossi", "Gianmarco Rossi")
        val matchSplit = GeminiShiftMapper.matchUserSquad(data, "Gian Marco Rossi")
        println("[COMPOUND TEST 3A] 'Gian Marco Rossi' vs 'Gianmarco Rossi' -> score: $scoreSplitQuery, match: $matchSplit")
        assertEquals("Squadra Alfa", matchSplit)

        // Case 3B: Fused query vs split candidate
        val dataSplit = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra Gamma", listOf("M"), listOf("Gian Marco Rossi"))
            )
        )
        val scoreFusedQuery = GeminiShiftMapper.calculateNameMatchScore("Gianmarco Rossi", "Gian Marco Rossi")
        val matchFused = GeminiShiftMapper.matchUserSquad(dataSplit, "Gianmarco Rossi")
        println("[COMPOUND TEST 3B] 'Gianmarco Rossi' vs 'Gian Marco Rossi' -> score: $scoreFusedQuery, match: $matchFused")
        assertEquals("Squadra Gamma", matchFused)
    }

    @Test
    fun testCompoundNames_SurnamesWithPrefixes() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra DeLuca", listOf("M"), listOf("Antonio De Luca")),
                GeminiSquadRotation("Squadra VanDer", listOf("P"), listOf("Alexander Van Der Bellen"))
            )
        )

        // Case 3C: Inverted with prefixes
        assertEquals("Squadra DeLuca", GeminiShiftMapper.matchUserSquad(data, "De Luca Antonio"))
        assertEquals("Squadra VanDer", GeminiShiftMapper.matchUserSquad(data, "Van Der Bellen Alexander"))

        // Case 3D: Surname query only
        val scoreDeLuca = GeminiShiftMapper.calculateNameMatchScore("De Luca", "Antonio De Luca")
        val matchDeLuca = GeminiShiftMapper.matchUserSquad(data, "De Luca")
        println("[PREFIX TEST 3D] 'De Luca' vs 'Antonio De Luca' -> score: $scoreDeLuca, match: $matchDeLuca")
        assertEquals("Squadra DeLuca", matchDeLuca)
    }

    // =========================================================================
    // 4. PUNCTUATION, DELIMITERS & NOISE
    // =========================================================================

    @Test
    fun testPunctuationAndNoise() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra X", listOf("M"), listOf("Marco Graziani"))
            )
        )

        // Case 4A: Trailing role annotation
        val scoreParen = GeminiShiftMapper.calculateNameMatchScore("Marco Graziani (Capoturno)", "Marco Graziani")
        val matchParen = GeminiShiftMapper.matchUserSquad(data, "Marco Graziani (Capoturno)")
        println("[NOISE TEST 4A] '(Capoturno)' query -> score: $scoreParen, match: $matchParen")
        assertEquals("Squadra X", matchParen)

        // Case 4B: Slash delimiter
        val scoreSlash = GeminiShiftMapper.calculateNameMatchScore("Marco / Graziani", "Marco Graziani")
        println("[NOISE TEST 4B] 'Marco / Graziani' -> score: $scoreSlash")

        // Case 4C: Whitespace, tabs and linebreaks
        val scoreTabs = GeminiShiftMapper.calculateNameMatchScore("   Marco \t\t  Graziani  \n", "Marco Graziani")
        println("[NOISE TEST 4C] Whitespace & tabs -> score: $scoreTabs")
        assertEquals(95, scoreTabs)
    }

    // =========================================================================
    // 5. PARTIAL HOMONYMY & FALSE POSITIVE VULNERABILITIES
    // =========================================================================

    @Test
    fun testPartialHomonymy_DifferentPersonSharingOneToken() {
        // Scenario 5A: User is "Andrea Romano".
        // Andrea Romano is NOT in the roster!
        // But Squad 1 contains "Andrea Bianchi".
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra 1", listOf("M"), listOf("Andrea Bianchi", "Roberto Ferri")),
                GeminiSquadRotation("Squadra 2", listOf("P"), listOf("Luigi Verdi", "Paolo Neri"))
            )
        )

        val score = GeminiShiftMapper.calculateNameMatchScore("Andrea Romano", "Andrea Bianchi")
        val matchedSquad = GeminiShiftMapper.matchUserSquad(data, "Andrea Romano")
        println("[HOMONYMY TEST 5A] 'Andrea Romano' vs 'Andrea Bianchi' -> score: $score, matched squad: $matchedSquad")
        assertEquals(35, score)
        assertNull(matchedSquad)

        // Scenario 5B: User is "Marco Bianchi".
        // Squad A has "Marco Rossi", Squad B has "Luca Bianchi".
        val dataSplitTokens = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra Rossi", listOf("M"), listOf("Marco Rossi")),
                GeminiSquadRotation("Squadra Bianchi", listOf("P"), listOf("Luca Bianchi"))
            )
        )
        val scoreA = GeminiShiftMapper.calculateNameMatchScore("Marco Bianchi", "Marco Rossi")
        val scoreB = GeminiShiftMapper.calculateNameMatchScore("Marco Bianchi", "Luca Bianchi")
        val matchedSplit = GeminiShiftMapper.matchUserSquad(dataSplitTokens, "Marco Bianchi")
        println("[HOMONYMY TEST 5B] 'Marco Bianchi' against 'Marco Rossi' (score $scoreA) vs 'Luca Bianchi' (score $scoreB) -> matched: $matchedSplit")
        assertEquals(35, scoreA)
        assertEquals(35, scoreB)
        assertNull(matchedSplit)
    }

    @Test
    fun testPartialHomonymy_SingleSurnameAmbiguity() {
        // Two squads have workers with the same common surname "Rossi"
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra Mattina", listOf("M"), listOf("Mario Rossi")),
                GeminiSquadRotation("Squadra Notte", listOf("N"), listOf("Giuseppe Rossi"))
            )
        )

        val score1 = GeminiShiftMapper.calculateNameMatchScore("Rossi", "Mario Rossi")
        val score2 = GeminiShiftMapper.calculateNameMatchScore("Rossi", "Giuseppe Rossi")
        val matchedSquad = GeminiShiftMapper.matchUserSquad(data, "Rossi")
        println("[AMBIGUITY TEST 5C] Surname 'Rossi' across two squads -> score1: $score1, score2: $score2, matched: $matchedSquad")
        assertEquals(80, score1)
        assertEquals(80, score2)
        assertEquals("Squadra Mattina", matchedSquad)
    }

    @Test
    fun testSubstringMatching_ShortNames() {
        // Anti-substring: Query "Dan" (len 3) against "Giordano Bruno" must NOT match substring
        val scoreDanGiordano = GeminiShiftMapper.calculateNameMatchScore("Dan", "Giordano Bruno")
        println("[SUBSTRING TEST 5D] 'Dan' in 'Giordano Bruno' -> score: $scoreDanGiordano")
        assertEquals(0, scoreDanGiordano)

        // Anti-substring: Query "Leo" (len 3) against "Pantaleo De Rosa" must NOT match substring
        val scoreLeoPantaleo = GeminiShiftMapper.calculateNameMatchScore("Leo", "Pantaleo De Rosa")
        println("[SUBSTRING TEST 5E] 'Leo' in 'Pantaleo De Rosa' -> score: $scoreLeoPantaleo")
        assertEquals(0, scoreLeoPantaleo)

        // Positive exact short token: "Dan" matches candidate with exact token "Dan"
        val scoreDanExact = GeminiShiftMapper.calculateNameMatchScore("Dan", "Dan Smith")
        assertEquals(80, scoreDanExact)

        // Positive exact short token: "Leo" matches candidate with exact token "Leo"
        val scoreLeoExact = GeminiShiftMapper.calculateNameMatchScore("Leo", "Leo Messi")
        assertEquals(80, scoreLeoExact)
    }

    // =========================================================================
    // 6. ADVERSARIAL EDGE CASES & CRASH RESISTANCE
    // =========================================================================

    @Test
    fun testAdversarialInputs_NoExceptionsOrCrashes() {
        val data = GeminiShiftData(
            squads = listOf(
                GeminiSquadRotation("Squadra Test", listOf("M"), listOf("Marco Graziani")),
                GeminiSquadRotation("Squadra Empty", listOf("M"), emptyList())
            ),
            employees = listOf(
                GeminiEmployeeAssignment(name = "", squadName = null),
                GeminiEmployeeAssignment(name = "   ", squadName = "Squadra Void")
            )
        )

        // Special symbols only
        assertNull(GeminiShiftMapper.matchUserSquad(data, "!@#$%^&*()_+=-[]{}|;:'\",.<>?/"))
        // Emojis
        assertNull(GeminiShiftMapper.matchUserSquad(data, "👨‍🏭 👷‍♂️ 📅 ⏰"))
        // Extremely long string (10k chars)
        val longString = "A".repeat(10_000)
        assertNull(GeminiShiftMapper.matchUserSquad(data, longString))
        // Null and blank
        assertNull(GeminiShiftMapper.matchUserSquad(data, null))
        assertNull(GeminiShiftMapper.matchUserSquad(data, ""))
        assertNull(GeminiShiftMapper.matchUserSquad(data, "    "))
        // SQL injection / formatting injection attempts
        assertNull(GeminiShiftMapper.matchUserSquad(data, "'; DROP TABLE Turni; --"))
        assertNull(GeminiShiftMapper.matchUserSquad(data, "%s%d%n\\0"))
    }
}
