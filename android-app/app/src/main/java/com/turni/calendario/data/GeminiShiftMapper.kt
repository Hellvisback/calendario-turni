package com.turni.calendario.data

import com.turni.calendario.importer.NgeniaTurniParser
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.repository.ShiftRepository
import java.text.Normalizer
import java.util.UUID

/**
 * Result of registering extracted shift roster data into the application repository.
 *
 * @property registeredShifts List of shifts registered or updated in ShiftRepository.
 * @property registeredRotations List of team rotations saved in ShiftRepository.
 * @property matchedSquadName Name of the squad matched with the target user (if any).
 * @property defaultRotationId ID of the rotation set as default (if matched).
 * @property message Informational summary message for UI feedback.
 */
data class ExtractedSetupResult(
    val registeredShifts: List<ShiftDefinition>,
    val registeredRotations: List<ShiftRotation>,
    val matchedSquadName: String? = null,
    val defaultRotationId: String? = null,
    val message: String = ""
)

/**
 * High-performance domain mapper that transforms Gemini Flash Vision structured outputs
 * (`GeminiShiftData`) into real application domain entities (`ShiftDefinition`, `ShiftRotation`)
 * and integrates them into `ShiftRepository`.
 */
object GeminiShiftMapper {

    /**
     * Established vibrant palette used when custom hex colors are missing:
     * Cyan (#38BDF8), Amber (#F59E0B), Emerald (#10B981), Rose (#EF4444), Indigo (#6366F1), Purple (#8B5CF6).
     */
    val DEFAULT_PALETTE: List<Long> = listOf(
        0xFF38BDF8L, // Cyan / Sky
        0xFFF59E0BL, // Amber
        0xFF10B981L, // Emerald
        0xFFEF4444L, // Rose
        0xFF6366F1L, // Indigo
        0xFF8B5CF6L  // Purple
    )

    /**
     * Maps extracted Gemini shift definitions to real application `ShiftDefinition` models.
     * Assigns unique UUIDs, detects category and icons, and parses or allocates distinctive colors.
     */
    fun mapToShiftDefinitions(data: GeminiShiftData): List<ShiftDefinition> {
        return data.shiftDefinitions.mapIndexed { index, geminiDef ->
            val code = geminiDef.code.trim().uppercase()
            val rawName = geminiDef.name.trim()
            val name = if (rawName.isNotBlank()) rawName else code

            val startTime = geminiDef.startTime.trim().ifBlank { "06:00" }
            val endTime = geminiDef.endTime.trim().ifBlank { "13:36" }

            val color = parseColorHex(geminiDef.colorHex, index)
            val category = detectCategory(name = name, code = code, startTime = startTime)
            val iconKey = detectIconKey(name = name, code = code, category = category, isReperibilita = geminiDef.isReperibilita)

            val isAllDay = (startTime == "00:00" && endTime in listOf("00:00", "23:59")) ||
                    (category == ShiftCategory.RIPOSO && (code == "RIP" || code == "SMO"))

            ShiftDefinition(
                id = UUID.randomUUID().toString(),
                name = name,
                shortCode = code,
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay,
                colorHex = color,
                iconKey = iconKey,
                category = category
            )
        }
    }

    /**
     * Maps extracted squad rotations into cyclic `ShiftRotation` instances.
     * Resolves day sequences from codes to shift definition IDs.
     */
    fun mapToRotations(
        data: GeminiShiftData,
        shiftDefs: List<ShiftDefinition> = emptyList()
    ): List<ShiftRotation> {
        return data.squads.mapIndexed { index, squad ->
            val rotId = normalizeRotationId(squad.squadName)
            val daySequence = squad.rotationDays.flatMap { rawItem ->
                if (rawItem.isBlank()) {
                    listOf("riposo")
                } else {
                    val tokens = rawItem.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
                    if (tokens.isEmpty()) listOf("riposo") else tokens
                }
            }.map { rawCode ->
                resolveShiftDefId(rawCode, shiftDefs)
            }
            val totalDays = if (squad.cycleDays > 0) squad.cycleDays else daySequence.size
            ShiftRotation(
                id = rotId,
                name = squad.squadName.ifBlank { "Squadra ${index + 1}" },
                totalDays = totalDays,
                daySequence = daySequence,
                description = "Rotazione ${squad.squadName} ($totalDays giorni)"
            )
        }
    }

    /**
     * Intelligently matches a user's name against squads' employee rosters and individual employee assignments.
     * Handles case-insensitivity, excess whitespace, punctuation, and reversed name/surname orders
     * (e.g. "Marco Graziani" vs "Graziani Marco", or searching by single surname "Graziani").
     *
     * @return The squad name of the matched squad, or null if no match is found.
     */
    fun matchUserSquad(data: GeminiShiftData, userName: String?): String? {
        if (userName.isNullOrBlank()) return null
        val query = userName.trim()

        var bestSquadName: String? = null
        var bestScore = 0

        // 1. Search in squad rosters (GeminiSquadRotation.employees)
        for (squad in data.squads) {
            for (candidate in squad.employees) {
                val score = calculateNameMatchScore(query, candidate)
                if (score > bestScore) {
                    bestScore = score
                    bestSquadName = squad.squadName
                }
            }
        }

        // 2. Search in individual employee assignments (GeminiEmployeeAssignment)
        for (emp in data.employees) {
            val assignedSquad = emp.squadName ?: continue
            val score = calculateNameMatchScore(query, emp.name)
            if (score > bestScore) {
                bestScore = score
                bestSquadName = assignedSquad
            }
        }

        return if (bestScore >= 50) bestSquadName else null
    }

    /**
     * Atomically registers or updates extracted shifts and rotations into `ShiftRepository`.
     * Avoids duplicate definitions by updating existing shift models (e.g. "mattina", "pomeriggio")
     * in-place while inserting brand new shifts with unique IDs.
     *
     * If target user is provided (or configured in preferences) and matched, sets the matched rotation as default.
     */
    fun registerExtractedData(
        repository: ShiftRepository = ShiftRepository,
        data: GeminiShiftData,
        targetUserName: String? = null
    ): ExtractedSetupResult = synchronized(repository) {
        // 1. Map to domain ShiftDefinition list
        val newDefs = mapToShiftDefinitions(data)
        val registeredDefs = mutableListOf<ShiftDefinition>()

        for (newDef in newDefs) {
            val defsSnapshot = repository.shiftDefinitions.toList()
            val existingIndex = defsSnapshot.indexOfFirst { existing ->
                existing.id == newDef.id ||
                existing.shortCode.equals(newDef.shortCode, ignoreCase = true) ||
                existing.name.equals(newDef.name, ignoreCase = true)
            }

            if (existingIndex >= 0) {
                val existing = defsSnapshot[existingIndex]
                val updated = existing.copy(
                    startTime = newDef.startTime,
                    endTime = newDef.endTime,
                    colorHex = newDef.colorHex,
                    category = newDef.category,
                    iconKey = newDef.iconKey,
                    name = newDef.name.ifBlank { existing.name }
                )
                if (existing != updated) {
                    repository.saveShiftDefinition(updated)
                }
                registeredDefs.add(updated)
            } else {
                repository.saveShiftDefinition(newDef)
                registeredDefs.add(newDef)
            }
        }

        // 2. Map squads to ShiftRotation models using all available definitions (registered + repository)
        val allDefs = (registeredDefs + repository.shiftDefinitions.toList()).distinctBy { it.id }
        val rotations = mapToRotations(data, allDefs)

        for (rot in rotations) {
            val existingRot = repository.rotations.find { it.id == rot.id }
            if (existingRot == null || existingRot.daySequence != rot.daySequence || existingRot.totalDays != rot.totalDays || existingRot.name != rot.name) {
                repository.saveRotation(rot)
            }
        }

        // 3. User matching & Default Rotation auto-selection
        val userQuery = targetUserName?.takeIf { it.isNotBlank() }
            ?: SecurePreferencesManager.getUserName()?.takeIf { it.isNotBlank() }

        val matchedSquadName = userQuery?.let { matchUserSquad(data, it) }
        var matchedRotationId: String? = null

        if (matchedSquadName != null) {
            val matchedRot = rotations.find {
                it.name.equals(matchedSquadName, ignoreCase = true) ||
                it.id.equals(normalizeRotationId(matchedSquadName), ignoreCase = true)
            }
            if (matchedRot != null) {
                matchedRotationId = matchedRot.id
                repository.setDefaultRotation(matchedRot.id)
            }
        }

        val summaryMessage = buildString {
            append("Registrati ${registeredDefs.size} turni e ${rotations.size} rotazioni.")
            if (matchedSquadName != null && matchedRotationId != null) {
                append(" Squadra predefinita: $matchedSquadName.")
            }
        }

        return ExtractedSetupResult(
            registeredShifts = registeredDefs,
            registeredRotations = rotations,
            matchedSquadName = matchedSquadName,
            defaultRotationId = matchedRotationId,
            message = summaryMessage
        )
    }

    // ==========================================
    // Internal Helper Functions
    // ==========================================

    fun parseColorHex(hexStr: String?, fallbackIndex: Int = 0): Long {
        if (hexStr.isNullOrBlank()) {
            return DEFAULT_PALETTE[Math.floorMod(fallbackIndex, DEFAULT_PALETTE.size)]
        }
        val clean = hexStr.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
        return try {
            val parsed = clean.toLong(16)
            if (clean.length <= 6) {
                0xFF000000L or parsed
            } else {
                parsed
            }
        } catch (_: Exception) {
            DEFAULT_PALETTE[Math.floorMod(fallbackIndex, DEFAULT_PALETTE.size)]
        }
    }

    fun detectCategory(name: String, code: String, startTime: String = ""): ShiftCategory {
        val upperCode = code.uppercase().trim()
        val upperName = name.uppercase().trim()

        if (upperCode in listOf("RIP", "RIPOSO", "SMO", "SMONTO", "RC", "RI", "RGLS", "RISE", "R", "L", "S", "SN", "SM") ||
            upperName.contains("RIPOSO") || upperName.contains("SMONTO") || upperName.contains("REST")
        ) {
            return ShiftCategory.RIPOSO
        }

        if (upperCode in listOf("FER", "FERIE", "F", "FE", "MAL", "MALATTIA", "PER", "PERMESSO", "PE", "DON", "DONAZIONE") ||
            upperName.contains("FERIE") || upperName.contains("MALATTIA") || upperName.contains("PERMESSO") || upperName.contains("ASSENZA")
        ) {
            return ShiftCategory.ASSENZA
        }

        // Even with non-standard names, time intervals (e.g. 06:00, 14:00, 22:00) represent regular work
        if (startTime.isNotBlank()) {
            return ShiftCategory.LAVORO
        }

        return ShiftCategory.LAVORO
    }

    fun detectIconKey(name: String, code: String, category: ShiftCategory, isReperibilita: Boolean): String {
        if (isReperibilita) return "phone_in_talk"
        val upperCode = code.uppercase().trim()
        val upperName = name.uppercase().trim()

        if (category == ShiftCategory.RIPOSO) {
            return if (upperCode in listOf("SMO", "SMONTO", "S", "SN", "SM") || upperName.contains("SMONTO")) {
                "bedtime"
            } else {
                "weekend"
            }
        }

        if (category == ShiftCategory.ASSENZA) {
            return if (upperCode in listOf("FER", "FERIE", "F", "FE") || upperName.contains("FERIE")) {
                "beach_access"
            } else if (upperCode in listOf("MAL", "MALATTIA") || upperName.contains("MALATTIA")) {
                "local_hospital"
            } else {
                "event_busy"
            }
        }

        return when {
            upperCode in listOf("N", "NOT", "3") || upperName.contains("NOTTE") -> "nightlight_round"
            upperCode in listOf("P", "POM", "2") || upperName.contains("POMERIGGIO") -> "wb_sunny"
            upperCode in listOf("M", "MAT", "1") || upperName.contains("MATTINA") -> "wb_twilight"
            upperCode in listOf("MD", "DOM") || upperName.contains("DOM") -> "wb_sunny"
            upperCode in listOf("TGF", "GIO", "UFF") || upperName.contains("GIORNALIERO") || upperName.contains("UFFICI") -> "business"
            else -> "schedule"
        }
    }

    fun normalizeRotationId(squadName: String): String {
        val clean = squadName.lowercase().trim()
            .replace(Regex("[^a-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return if (clean.startsWith("rotazione_") || clean.startsWith("turno_")) {
            clean
        } else {
            "rotazione_$clean"
        }
    }

    fun resolveShiftDefId(rawCode: String, shiftDefs: List<ShiftDefinition>): String {
        val sanitized = rawCode.trim().trim { it in "*#°^_()[]" }
        if (sanitized.isBlank()) return "riposo"
        val code = sanitized

        // 1. Direct match in provided shiftDefs (by id, shortCode, or name)
        shiftDefs.find { it.id.equals(code, ignoreCase = true) }?.let { return it.id }
        shiftDefs.find { it.shortCode.equals(code, ignoreCase = true) }?.let { return it.id }
        shiftDefs.find { it.name.equals(code, ignoreCase = true) }?.let { return it.id }

        // 2. Direct match in ShiftRepository.shiftDefinitions snapshot to avoid CME
        val repoDefs = ShiftRepository.shiftDefinitions.toList()
        repoDefs.find { it.id.equals(code, ignoreCase = true) }?.let { return it.id }
        repoDefs.find { it.shortCode.equals(code, ignoreCase = true) }?.let { return it.id }
        repoDefs.find { it.name.equals(code, ignoreCase = true) }?.let { return it.id }

        // 3. Fallback to Ngenia canonical ID mapping (e.g. "M" -> "mattina", "P" -> "pomeriggio")
        val standardId = NgeniaTurniParser.mapCodeToShiftDefId(code)
        shiftDefs.find { it.id == standardId }?.let { return it.id }
        repoDefs.find { it.id == standardId }?.let { return it.id }

        // 4. Safe final fallback to riposo
        return if (repoDefs.any { it.id == standardId }) standardId else "riposo"
    }

    fun normalizeName(raw: String): String {
        if (raw.isBlank()) return ""
        val nfd = Normalizer.normalize(raw, Normalizer.Form.NFD)
        val withoutDiacritics = nfd.replace(Regex("\\p{M}"), "")
        return withoutDiacritics
            .replace(Regex("[’‘`'′]"), " ")
            .replace(Regex("[,;._\\-/()]"), " ")
            .lowercase()
            .trim()
    }

    fun calculateNameMatchScore(query: String, candidate: String): Int {
        val q = query.trim()
        val c = candidate.trim()
        if (q.isBlank() || c.isBlank()) return 0

        // 1. Exact match (case-insensitive)
        if (q.equals(c, ignoreCase = true)) return 100

        val normQ = normalizeName(q)
        val normC = normalizeName(c)

        if (normQ.isBlank() || normC.isBlank()) return 0

        if (normQ == normC) return 100

        val qTokens = normQ.split(Regex("\\s+")).filter { it.isNotBlank() }
        val cTokens = normC.split(Regex("\\s+")).filter { it.isNotBlank() }

        if (qTokens.isEmpty() || cTokens.isEmpty()) return 0

        // 2. Set equality (e.g. "Graziani Marco" vs "Marco Graziani", or spacing variations)
        if (qTokens.toSet() == cTokens.toSet()) {
            // Space after apostrophe in OCR (e.g. "Marco D' Angelo" vs "Marco D'Angelo")
            if (q.contains(Regex("[’‘`'′]\\s+")) || c.contains(Regex("[’‘`'′]\\s+"))) {
                return 90
            }
            return 95
        }

        // Flat matching for compound names without spaces (e.g. "Gian Marco Rossi" vs "Gianmarco Rossi")
        if (normQ.replace(" ", "") == normC.replace(" ", "")) return 90

        // Compound token matching (e.g. "Gian Marco" vs "Gianmarco Rossi")
        val compoundMatch = (qTokens.size > 1 && cTokens.any { it == qTokens.joinToString("") }) ||
                (cTokens.size > 1 && qTokens.any { it == cTokens.joinToString("") })
        if (compoundMatch) return 90

        // Helper for matching tokens: exact match to avoid substring collisions on short names/internal roots
        val tokenMatches = { qTok: String, cTok: String ->
            qTok == cTok
        }

        // 3. All query tokens in candidate tokens
        val allQueryInCandidate = qTokens.all { qTok ->
            cTokens.any { cTok -> tokenMatches(qTok, cTok) }
        }
        if (allQueryInCandidate) {
            val isSingleWordQuery = !q.contains(" ")
            return if (isSingleWordQuery || qTokens.size == 1) 80 else 90
        }

        // 4. All candidate tokens in query
        val allCandidateInQuery = cTokens.all { cTok ->
            qTokens.any { qTok -> tokenMatches(qTok, cTok) }
        }
        if (allCandidateInQuery) {
            return if (cTokens.size > 1) 85 else 75
        }

        // 5. Partial match: count matching query tokens
        val matchedQCount = qTokens.count { qTok ->
            cTokens.any { cTok -> tokenMatches(qTok, cTok) }
        }

        // Anti-homonymy: if query has multiple tokens (e.g. "Andrea Romano"), matching only 1 token
        // ("Andrea" in "Andrea Bianchi") must NOT exceed the assignment threshold (score 35 < 50)
        if (qTokens.size >= 2) {
            return if (matchedQCount == 1) 35 else if (matchedQCount > 1) 45 else 0
        }

        // Single-token query matching a candidate token
        if (qTokens.size == 1 && matchedQCount == 1) {
            return 80
        }

        return 0
    }

    // ==========================================
    // External AI Prompt Generator & Text Parser
    // ==========================================

    /**
     * Generates a ready-to-use multimodal prompt for external AI chatbots (ChatGPT, Claude, Gemini Web, DeepSeek).
     * Includes instructions for structured JSON output matching the app's internal domain schema,
     * along with dynamic employee assignment targeting if a user name is specified.
     *
     * @param targetUserName Optional name of the user to automatically match with their assigned team.
     * @return Formatted multi-line prompt string.
     */
    fun generateExternalAiPrompt(targetUserName: String? = null): String {
        val cleanUser = targetUserName?.trim()
        val userInstruction = if (!cleanUser.isNullOrBlank()) {
            "\nIMPORTANTE PER L'ASSEGNAZIONE DEL LAVORATORE:\n" +
            "Il lavoratore per cui configurare l'applicazione si chiama: \"$cleanUser\".\n" +
            "Cerca questo nominativo (o la combinazione Cognome Nome / Nome Cognome) all'interno del prospetto " +
            "e assicurati di inserirlo nella lista \"employees\" della squadra a cui è effettivamente assegnato.\n"
        } else {
            ""
        }

        return """
Sei un assistente esperto nell'analisi di prospetti turni ferroviari, ospedalieri e industriali (come il MOD. P.R. o prospetti tabellari di servizio).
Analizza con estrema precisione l'immagine o documento allegato ed estrai TUTTI i dati dei turni nel formato JSON strutturato indicato di seguito.
$userInstruction
Rispondi ESCLUSIVAMENTE con un blocco JSON valido (senza testo introduttivo, commenti o saluti, e senza testo dopo il JSON):

```json
{
  "shiftDefinitions": [
    {
      "code": "M",
      "name": "Mattina",
      "startTime": "06:00",
      "endTime": "13:36",
      "colorHex": "#38BDF8",
      "isReperibilita": false
    },
    {
      "code": "P",
      "name": "Pomeriggio",
      "startTime": "14:00",
      "endTime": "21:36",
      "colorHex": "#F59E0B",
      "isReperibilita": false
    },
    {
      "code": "N",
      "name": "Notte",
      "startTime": "22:00",
      "endTime": "05:36",
      "colorHex": "#10B981",
      "isReperibilita": false
    },
    {
      "code": "SMO",
      "name": "Smonto Notte",
      "startTime": "00:00",
      "endTime": "23:59",
      "colorHex": "#6366F1",
      "isReperibilita": false
    },
    {
      "code": "RIP",
      "name": "Riposo",
      "startTime": "00:00",
      "endTime": "23:59",
      "colorHex": "#8B5CF6",
      "isReperibilita": false
    }
  ],
  "squads": [
    {
      "squadName": "Squadra A",
      "rotationDays": ["M", "P", "N", "SMO", "RIP"],
      "cycleDays": 5,
      "employees": ["Cognome Nome 1", "Cognome Nome 2"]
    }
  ],
  "validityStart": "2026-01-01",
  "validityEnd": "2026-12-31",
  "notes": "Note eventuali su reperibilità o particolarità"
}
```

REGOLE ESSENZIALI:
1. In 'shiftDefinitions' includi tutti i codici orari trovati nella legenda del foglio con i rispettivi orari di inizio e fine nel formato HH:mm a 24 ore (es. 06:00, 13:36, 14:00, 21:36, 22:00, 05:36).
2. Se un turno è di riposo, smonto o assenza (es. RIP, SMO, FER), usa 00:00 e 23:59 se non ci sono orari specifici.
3. In 'squads' inserisci tutte le squadre/linee individuate, con la sequenza completa e ordinata dei codici turno ('rotationDays') e i nomi di tutti i colleghi appartenenti ('employees').
4. Se il prospetto indica che certi turni hanno la reperibilità (es. sui pomeriggi 'P' o domeniche 'MD'), imposta 'isReperibilita': true.
5. NON aggiungere alcun testo di contorno prima o dopo il codice JSON.
        """.trimIndent()
    }

    /**
     * Resiliently extracts the JSON payload from text pasted by the user.
     * Handles markdown code block fences (```json ... ``` or ``` ... ```),
     * conversational text wrappers ("Ecco il JSON: { ... }"), and raw JSON.
     *
     * @param pastedText Raw text pasted from external AI output.
     * @return Cleaned JSON substring ready for parsing.
     */
    fun extractJsonPayload(pastedText: String): String {
        val trimmed = pastedText.trim()
        if (trimmed.isEmpty()) return ""

        // 1. Check for markdown code fences with ```json ... ```
        val jsonFenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        val match = jsonFenceRegex.find(trimmed)
        if (match != null) {
            val insideFence = match.groupValues[1].trim()
            if (insideFence.startsWith("{") && insideFence.endsWith("}")) {
                return insideFence
            }
        }

        // 2. Fallback to locating outermost curly brackets { ... }
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1).trim()
        }

        return trimmed
    }

    /**
     * Parses shifts and squads from text pasted from an external AI chatbot.
     *
     * @param rawText Text copied from ChatGPT, Claude, Gemini or other AI.
     * @return Result containing [GeminiShiftData] or descriptive error.
     */
    fun parseFromExternalAiText(rawText: String): Result<GeminiShiftData> = runCatching {
        if (rawText.isBlank()) {
            throw IllegalArgumentException("Il testo inserito è vuoto. Incolla prima la risposta ottenuta dall'AI.")
        }

        val jsonPayload = extractJsonPayload(rawText)
        if (jsonPayload.isBlank() || !jsonPayload.startsWith("{") || !jsonPayload.endsWith("}")) {
            throw IllegalArgumentException(
                "Nessun blocco JSON valido individuato nel testo incollato. " +
                "Assicurati di aver copiato la risposta completa dell'AI."
            )
        }

        val data = GeminiApiClient.parseGeminiResponse(jsonPayload)
        if (data.shiftDefinitions.isEmpty() && data.squads.isEmpty()) {
            throw IllegalArgumentException(
                "Il JSON incollato non contiene definizioni di turni o squadre. " +
                "Verifica che l'AI abbia seguito il formato richiesto."
            )
        }

        data
    }
}

