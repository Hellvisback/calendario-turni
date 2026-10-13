package com.turni.calendario.importer

import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.ShiftRotation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * Informazioni estratte per un singolo dipendente dal tabellone.
 */
data class ParsedEmployee(
    val name: String,
    val role: String = "",
    val phone: String = "",
    val teamName: String = "",
    val isOnCallGeneral: Boolean = false,
    val dayShifts: Map<LocalDate, String> = emptyMap(),
    val rawShiftSequence: List<String> = emptyList()
)

/**
 * Squadra e sequenza rotativa ciclica rilevata dal documento.
 */
data class ParsedTeamRotation(
    val teamName: String,
    val rotationId: String,
    val totalDays: Int,
    val dayCodes: List<String>,       // es. ["P", "P", "RIP", "N", "N", "M", ...]
    val dayShiftDefIds: List<String>, // es. ["pomeriggio", "pomeriggio", "riposo", ...]
    val members: List<ParsedEmployee> = emptyList()
)

/**
 * Risultato completo dell'elaborazione di un file/testo 'ngeniaTurni / RFI Mod. P.R.
 */
data class NgeniaParseResult(
    val validityStart: LocalDate? = null,
    val validityEnd: LocalDate? = null,
    val detectedHours: Map<String, Pair<String, String>> = emptyMap(), // Codice -> (Inizio, Fine)
    val teams: List<ParsedTeamRotation> = emptyList(),
    val employees: List<ParsedEmployee> = emptyList(),
    val specialShifts: List<ShiftDefinition> = emptyList(),
    val summaryText: String = ""
)

/**
 * Algoritmo intelligente per analizzare PDF, testo ed esportazioni di 'ngeniaTurni
 * e tabelloni ufficiali RFI (Mod. P.R. DOIT ANCONA / UMIS / Pescara, etc.).
 */
object TextImportSanitizer {
    /**
     * Ripulisce il testo rimuovendo byte nulli, caratteri di controllo non validi,
     * sequenze binarie, byte illeggibili o artefatti di decodifica (es. U+FFFD, escape ANSI).
     * Preserva interruzioni di riga (\n, \r), tabulazioni (\t) e tutti i caratteri alfanumerici e di punteggiatura validi.
     */
    fun sanitize(input: String): String {
        if (input.isEmpty()) return ""

        val cleanedChars = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch == '\n' || ch == '\r' || ch == '\t' -> cleanedChars.append(ch)
                ch == '\u0000' || ch == '\uFFFD' || ch == '\uFEFF' -> { /* ignora */ }
                ch.isISOControl() -> { /* ignora caratteri di controllo non stampabili */ }
                ch in '\u0001'..'\u001F' || ch in '\u007F'..'\u009F' -> { /* ignora */ }
                else -> cleanedChars.append(ch)
            }
        }

        val normalized = cleanedChars.toString()
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val filteredLines = normalized.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                ""
            } else if (isBinaryGarbageLine(trimmed)) {
                null
            } else {
                trimmed
            }
        }

        return filteredLines.joinToString("\n").trim()
    }

    private fun isBinaryGarbageLine(line: String): Boolean {
        if (line.startsWith("%PDF-") || line.startsWith("<<") || line.startsWith(">>") ||
            line == "stream" || line == "endstream" || line == "endobj" || line.endsWith(" obj")) {
            return true
        }
        // Identifica e rimuove elementi strutturali PDF e keyword interne
        if (line.equals("xref", ignoreCase = true) || line.equals("trailer", ignoreCase = true) ||
            line.equals("startxref", ignoreCase = true) || line.startsWith("%%EOF") ||
            line.matches(Regex("(?i)^/[A-Za-z0-9_-]+\\s+(?:\\d+\\s+\\d+\\s+R|/[A-Za-z0-9_-]+.*)$")) ||
            line.matches(Regex("^\\d{10}\\s+\\d{5}\\s+[fn]\\s*$")) ||
            line.matches(Regex("^\\d+\\s+\\d+\\s+obj.*$"))) {
            return true
        }

        val letterOrDigitCount = line.count { it.isLetterOrDigit() }
        val totalCount = line.length
        if (totalCount > 10 && (letterOrDigitCount.toDouble() / totalCount) < 0.25) {
            val separators = line.count { it in ",;\t|/-" }
            if ((letterOrDigitCount + separators).toDouble() / totalCount < 0.35) {
                return true
            }
        }
        return false
    }

    /**
     * Estrae testo leggibile da un array di byte, rilevando PDF o archivi Zip/Excel (.xlsx).
     */
    fun extractAndCleanBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        // 1. Rileva se è un file Zip/XLSX (magic: PK\u0003\u0004)
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
            val xlsxText = extractTextFromZipOrXlsx(bytes)
            if (xlsxText.isNotBlank()) {
                return sanitize(xlsxText)
            }
        }

        // 2. Rileva se è un PDF (magic: %PDF-)
        if (bytes.size >= 5 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
            val pdfText = extractTextFromPdfBytes(bytes)
            if (pdfText.isNotBlank()) {
                return sanitize(pdfText)
            }
        }

        // Se i byte contengono un'alta densità di byte binari/non-stampabili, scarta come binario non valido
        val nonPrintable = bytes.count { b ->
            val u = b.toInt() and 0xFF
            u < 0x09 || (u in 0x0E..0x1F) || (u in 0x7F..0x9F)
        }
        if (bytes.size > 20 && (nonPrintable.toDouble() / bytes.size) > 0.20) {
            return ""
        }

        // 3. Fallback: decodifica testo UTF-8 o ISO-8859-1
        val decoded = try {
            val utf8 = String(bytes, Charsets.UTF_8)
            if (utf8.count { it == '\uFFFD' } > 5) {
                String(bytes, Charsets.ISO_8859_1)
            } else {
                utf8
            }
        } catch (_: Exception) {
            String(bytes, Charsets.ISO_8859_1)
        }

        return sanitize(decoded)
    }

    private fun extractTextFromZipOrXlsx(bytes: ByteArray): String {
        return try {
            val bais = java.io.ByteArrayInputStream(bytes)
            val zis = java.util.zip.ZipInputStream(bais)
            val extracted = StringBuilder()
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith("sharedStrings.xml", ignoreCase = true) ||
                    entry.name.endsWith(".csv", ignoreCase = true) ||
                    entry.name.endsWith(".txt", ignoreCase = true) ||
                    (entry.name.contains("sheet", ignoreCase = true) && entry.name.endsWith(".xml", ignoreCase = true))) {
                    val content = zis.bufferedReader(Charsets.UTF_8).readText()
                    if (entry.name.endsWith(".xml", ignoreCase = true)) {
                        val regex = java.util.regex.Pattern.compile("<t[^>]*>(.*?)</t>")
                        val matcher = regex.matcher(content)
                        while (matcher.find()) {
                            val text = (matcher.group(1) ?: "").trim()
                            if (text.isNotEmpty()) {
                                extracted.append(text).append(" ")
                            }
                        }
                        extracted.append("\n")
                    } else {
                        extracted.append(content).append("\n")
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            extracted.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractTextFromPdfBytes(bytes: ByteArray): String {
        return try {
            val raw = String(bytes, Charsets.ISO_8859_1)
            val extractedText = StringBuilder()

            // 1. Rileva stream di contenuto (sia compressi FlateDecode che in chiaro)
            val streamRegex = Pattern.compile("stream\\r?\\n([\\s\\S]*?)\\r?\\nendstream")
            val streamMatcher = streamRegex.matcher(raw)

            while (streamMatcher.find()) {
                val streamContent = streamMatcher.group(1) ?: continue
                val streamBytes = streamContent.toByteArray(Charsets.ISO_8859_1)

                // Decomprime stream zlib (FlateDecode)
                val decompressedText = inflateStream(streamBytes)
                val textToScan = if (decompressedText.isNotEmpty()) decompressedText else streamContent

                // Estrai stringhe dagli operatori di testo PDF: (...) Tj, [...] TJ
                extractPdfTextTokens(textToScan, extractedText)
            }

            // 2. Se non sono stati trovati stream o il testo estratto è minimo, scansiona l'intero file
            if (extractedText.length < 20) {
                extractPdfTextTokens(raw, extractedText)
            }

            if (extractedText.length > 20) {
                extractedText.toString().trim()
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun inflateStream(data: ByteArray): String {
        return try {
            val inflater = java.util.zip.Inflater()
            inflater.setInput(data)
            val outputStream = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                outputStream.write(buffer, 0, count)
            }
            inflater.end()
            String(outputStream.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            try {
                val inflater = java.util.zip.Inflater()
                inflater.setInput(data)
                val outputStream = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0 && inflater.needsInput()) break
                    outputStream.write(buffer, 0, count)
                }
                inflater.end()
                String(outputStream.toByteArray(), Charsets.ISO_8859_1)
            } catch (_: Exception) {
                ""
            }
        }
    }

    private fun extractPdfTextTokens(pdfStreamText: String, out: StringBuilder) {
        val textTokenRegex = Pattern.compile(
            "\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)\\s*(T[jd]|'|\")" +
            "|(?:\\[([^\\]]+)\\]\\s*TJ)" +
            "|(?:(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+T[dD])" +
            "|(\\bET\\b|\\bT\\*(?![A-Za-z0-9]))"
        )
        val matcher = textTokenRegex.matcher(pdfStreamText)

        while (matcher.find()) {
            val singleToken = matcher.group(1)
            val singleOp = matcher.group(2)
            val arrayToken = matcher.group(3)
            val tdY = matcher.group(5)
            val lineBreakOp = matcher.group(6)

            if (singleToken != null) {
                val clean = unescapePdfString(singleToken)
                if (clean.isNotBlank()) {
                    out.append(clean).append(" ")
                    if (clean.contains("\n") || singleOp == "'" || singleOp == "\"") {
                        if (!out.endsWith("\n")) {
                            out.append("\n")
                        }
                    }
                }
            } else if (arrayToken != null) {
                val subMatcher = Pattern.compile("\\(([^()\\\\]*(?:\\\\.[^()\\\\]*)*)\\)").matcher(arrayToken)
                while (subMatcher.find()) {
                    val piece = subMatcher.group(1) ?: continue
                    val clean = unescapePdfString(piece)
                    if (clean.isNotBlank()) {
                        out.append(clean).append(" ")
                    }
                }
                if (!out.endsWith("\n")) {
                    out.append("\n")
                }
            } else if (tdY != null) {
                val yOffset = tdY.toDoubleOrNull() ?: 0.0
                if (kotlin.math.abs(yOffset) > 0.5) {
                    if (out.isNotEmpty() && !out.endsWith("\n")) {
                        out.append("\n")
                    }
                }
            } else if (lineBreakOp != null) {
                if (out.isNotEmpty() && !out.endsWith("\n")) {
                    out.append("\n")
                }
            }
        }
    }

    private fun unescapePdfString(s: String): String {
        return s.replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", " ")
            .replace("\\t", "\t")
            .trim()
    }
}

/**
 * Algoritmo intelligente per analizzare PDF, testo ed esportazioni di 'ngeniaTurni
 * e tabelloni ufficiali RFI (Mod. P.R. DOIT ANCONA / UMIS / Pescara, etc.) nonché schemi tabellari generici.
 */
object NgeniaTurniParser {

    /**
     * Esegue il parsing completo del testo estratto o incollato.
     */
    fun parseDocumentText(rawText: String): NgeniaParseResult {
        val sanitized = TextImportSanitizer.sanitize(rawText)
        val lines = sanitized.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // 1. Estrazione periodo di validità (es. 21/09/2026 - 18/10/2026 o formato flessibile)
        val (valStart, valEnd) = extractValidityDates(sanitized)

        // 2. Estrazione orari turni dal riquadro "Orari:" (M, P, N, MD, MAT, POM, etc.)
        val hoursMap = extractHoursDefinitions(sanitized)

        // 3. Estrazione date delle colonne giorno (es. 21/9, 22/9, ... 18/10)
        val columnDates = extractColumnDates(sanitized, valStart)

        // 4. Estrazione Squadre e dipendenti
        val teams = extractTeamsAndMembers(lines, columnDates)
        val allEmployees = teams.flatMap { it.members }

        val summary = buildString {
            append("Periodo: ")
            if (valStart != null && valEnd != null) {
                append("${valStart.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} - ${valEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
            } else {
                append("Non specificato")
            }
            append(" • Squadre trovate: ${teams.size}")
            append(" • Personale: ${allEmployees.size}")
            if (hoursMap.isNotEmpty()) {
                append(" • Orari: ${hoursMap.keys.joinToString(", ")}")
            }
        }

        return NgeniaParseResult(
            validityStart = valStart,
            validityEnd = valEnd,
            detectedHours = hoursMap,
            teams = teams,
            employees = allEmployees,
            summaryText = summary
        )
    }

    private fun extractValidityDates(text: String): Pair<LocalDate?, LocalDate?> {
        // Pattern 1: "Turno Valido dal 21/09/2026 al 18/10/2026" con / o . o -
        val regex1 = Pattern.compile("(?i)(?:turno\\s+valido\\s+)?dal\\s+(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4})\\s+al\\s+(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4})")
        val matcher1 = regex1.matcher(text)
        if (matcher1.find()) {
            val d1 = parseFlexibleDate(matcher1.group(1) ?: "")
            val d2 = parseFlexibleDate(matcher1.group(2) ?: "")
            if (d1 != null && d2 != null) return Pair(d1, d2)
        }

        // Pattern 2: "21/09/2026 - 18/10/2026"
        val regex2 = Pattern.compile("(?i)(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4})\\s*[-–]\\s*(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4})")
        val matcher2 = regex2.matcher(text)
        if (matcher2.find()) {
            val d1 = parseFlexibleDate(matcher2.group(1) ?: "")
            val d2 = parseFlexibleDate(matcher2.group(2) ?: "")
            if (d1 != null && d2 != null) return Pair(d1, d2)
        }

        return Pair(null, null)
    }

    private fun parseFlexibleDate(str: String): LocalDate? {
        val normalized = str.replace(".", "/").replace("-", "/")
        return try {
            LocalDate.parse(normalized, DateTimeFormatter.ofPattern("d/M/yyyy"))
        } catch (_: Exception) {
            null
        }
    }

    private fun extractHoursDefinitions(text: String): Map<String, Pair<String, String>> {
        val hours = mutableMapOf<String, Pair<String, String>>()
        // Pattern per "M 06:00-13:36", "P: 14:00-21:36", "MAT = 06:00 - 14:00", etc.
        val regex = Pattern.compile("(?i)\\b([A-Z0-9]{1,4})\\s*[:=]?\\s*(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})")
        val matcher = regex.matcher(text)
        while (matcher.find()) {
            val code = (matcher.group(1) ?: "").uppercase()
            val start = matcher.group(2) ?: ""
            val end = matcher.group(3) ?: ""
            if (code.isNotEmpty() && start.isNotEmpty() && end.isNotEmpty()) {
                hours[code] = Pair(start, end)
            }
        }
        return hours
    }

    private fun extractColumnDates(text: String, fallbackStart: LocalDate?): List<LocalDate> {
        val defaultYear = fallbackStart?.year ?: 2026
        // Regex per token giorno/mese che esclude date complete a 3 parti con anno (es. 21/09/2026)
        val dateTokenRegex = Pattern.compile("\\b(\\d{1,2})[/.-](\\d{1,2})(?![/.-]\\d{2,4})\\b")

        // 1. Cerca la riga della tabella che contiene la sequenza di intestazione date (con il maggior numero di date)
        val lines = text.lines()
        var bestLineTokens = emptyList<Pair<Int, Int>>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("valido", ignoreCase = true) ||
                trimmed.contains("orari", ignoreCase = true) ||
                trimmed.contains("•") ||
                trimmed.startsWith("Periodo", ignoreCase = true)) {
                continue
            }

            val matcher = dateTokenRegex.matcher(trimmed)
            val lineTokens = mutableListOf<Pair<Int, Int>>()
            while (matcher.find()) {
                val day = (matcher.group(1) ?: "").toIntOrNull() ?: continue
                val month = (matcher.group(2) ?: "").toIntOrNull() ?: continue
                if (day in 1..31 && month in 1..12) {
                    lineTokens.add(Pair(day, month))
                }
            }

            if (lineTokens.size > bestLineTokens.size) {
                bestLineTokens = lineTokens
            }
        }

        val dates = mutableListOf<LocalDate>()
        if (bestLineTokens.size >= 4) {
            var currentYear = defaultYear
            var prevMonth = -1
            for ((day, month) in bestLineTokens.take(28)) {
                if (prevMonth != -1 && prevMonth == 12 && month == 1) {
                    currentYear++
                }
                try {
                    dates.add(LocalDate.of(currentYear, month, day))
                    prevMonth = month
                } catch (_: Exception) {}
            }
        }

        // 2. Fallback: genera date consecutive a partire da fallbackStart (o data odierna) se non è stata trovata una riga specifica
        if (dates.size < 4) {
            val startDate = fallbackStart ?: LocalDate.now()
            dates.clear()
            for (i in 0 until 28) {
                dates.add(startDate.plusDays(i.toLong()))
            }
        }

        return dates
    }

    private fun extractTeamsAndMembers(
        lines: List<String>,
        columnDates: List<LocalDate>
    ): List<ParsedTeamRotation> {
        val teams = mutableListOf<ParsedTeamRotation>()

        val recognizedTeamNames = listOf("Squadra B 1", "Squadra A", "Squadra B", "Squadra C", "Squadra D")
        val isRfiDoc = lines.any { line ->
            line.contains("MOD. P.R.", ignoreCase = true) ||
            line.contains("ngenia", ignoreCase = true) ||
            line.contains("DOIT ANCONA", ignoreCase = true) ||
            line.contains("UMIS", ignoreCase = true) ||
            line.contains("RFI", ignoreCase = true)
        }

        val teamBlocks = mutableMapOf<String, MutableList<String>>()
        var currentTeam = if (isRfiDoc) "Squadra B" else "Rotazione Turni"

        // Regex per riconoscere intestazioni generiche di squadre / turni / reparti (riga dedicata es. "Squadra Ospedaliera")
        val teamHeaderRegex = Pattern.compile("(?i)^\\s*(?:Squadra|Turno|Gruppo|Team|Reparto)\\s+([A-Za-z0-9_ -]{1,24})[:]?\\s*$")

        for (line in lines) {
            val isDateOrHoursLine = line.contains("valido", ignoreCase = true) ||
                line.contains("orari", ignoreCase = true) ||
                line.contains("•") ||
                Pattern.compile("(?i)\\b(dal|al)\\s+\\d{1,2}[/.-]").matcher(line).find() ||
                Pattern.compile("\\d{1,2}:\\d{2}").matcher(line).find()

            val headerMatcher = if (!isDateOrHoursLine) teamHeaderRegex.matcher(line) else null

            val matchedRfi = if (isRfiDoc) {
                recognizedTeamNames.find { teamName ->
                    val pattern = Pattern.compile("(?i)\\b" + Pattern.quote(teamName) + "\\b")
                    pattern.matcher(line).find()
                }
            } else null

            if (headerMatcher != null && headerMatcher.find()) {
                val headerName = line.trim().trimEnd(':')
                currentTeam = headerName
                teamBlocks.getOrPut(currentTeam) { mutableListOf() }
            } else if (matchedRfi != null) {
                currentTeam = matchedRfi
                teamBlocks.getOrPut(currentTeam) { mutableListOf() }
            } else {
                teamBlocks.getOrPut(currentTeam) { mutableListOf() }.add(line)
            }
        }

        for ((teamName, teamLines) in teamBlocks) {
            val members = mutableListOf<ParsedEmployee>()
            val rawShiftTokens = mutableListOf<String>()

            for (line in teamLines) {
                val emp = parseEmployeeLine(line, columnDates, teamName)
                if (emp != null) {
                    members.add(emp)
                    if (rawShiftTokens.isEmpty()) {
                        rawShiftTokens.addAll(emp.rawShiftSequence)
                    }
                } else {
                    val tokens = extractShiftTokensFromLine(line)
                    if (tokens.size >= 7 && rawShiftTokens.size < 28) {
                        rawShiftTokens.addAll(tokens)
                    }
                }
            }

            val finalCodes = if (rawShiftTokens.size >= 28) {
                rawShiftTokens.take(28)
            } else if (members.isNotEmpty() && members.first().rawShiftSequence.size >= 28) {
                members.first().rawShiftSequence.take(28)
            } else if (rawShiftTokens.isNotEmpty()) {
                rawShiftTokens
            } else {
                generateFallbackSequence(teamName)
            }

            val shiftDefIds = finalCodes.map { mapCodeToShiftDefId(it) }
            val rotId = "rotazione_" + teamName.lowercase().replace(" ", "_").replace("-", "_")

            teams.add(
                ParsedTeamRotation(
                    teamName = teamName,
                    rotationId = rotId,
                    totalDays = finalCodes.size,
                    dayCodes = finalCodes,
                    dayShiftDefIds = shiftDefIds,
                    members = members
                )
            )
        }

        // Se è specificamente un tabellone RFI, assicuriamo la presenza delle squadre standard
        if (isRfiDoc) {
            for (knownTeam in recognizedTeamNames) {
                if (teams.none { it.teamName.equals(knownTeam, ignoreCase = true) }) {
                    val fallbackCodes = generateFallbackSequence(knownTeam)
                    val rotId = "rotazione_" + knownTeam.lowercase().replace(" ", "_")
                    teams.add(
                        ParsedTeamRotation(
                            teamName = knownTeam,
                            rotationId = rotId,
                            totalDays = fallbackCodes.size,
                            dayCodes = fallbackCodes,
                            dayShiftDefIds = fallbackCodes.map { mapCodeToShiftDefId(it) },
                            members = emptyList()
                        )
                    )
                }
            }
        }

        return teams.sortedBy { it.teamName }
    }

    private fun parseEmployeeLine(
        line: String,
        columnDates: List<LocalDate>,
        teamName: String
    ): ParsedEmployee? {
        // Ignora righe di intestazione tabellare
        val lowerTrimmed = line.trim().lowercase()
        if (lowerTrimmed.startsWith("nome") || lowerTrimmed.startsWith("dipendente") ||
            lowerTrimmed.startsWith("cognome") || lowerTrimmed.startsWith("lavoratore") ||
            lowerTrimmed.startsWith("operatore") || lowerTrimmed.startsWith("matricola") ||
            lowerTrimmed.startsWith("collaboratore") || lowerTrimmed.startsWith("personale") ||
            lowerTrimmed.startsWith("data") || lowerTrimmed.startsWith("turni")) {
            return null
        }

        // Ignora righe che contengono principalmente sequenze di date di intestazione
        val dateTokenMatcher = Pattern.compile("\\b(\\d{1,2})[/.-](\\d{1,2})\\b").matcher(line)
        var dateCount = 0
        while (dateTokenMatcher.find()) dateCount++
        if (dateCount >= 4) return null

        // 1. Riconoscimento stile RFI (matricola 80xxxx o 88xxxx o numero matricola 5-8 cifre non parte di una data)
        val phoneMatcher = Pattern.compile("(?<![/.:-])\\b(80\\d{5}|88\\d{5}|\\d{5,8})\\b(?![/.:-])").matcher(line)
        if (phoneMatcher.find()) {
            val phone = phoneMatcher.group(1) ?: ""
            if (phone.isNotEmpty() && line.contains(phone)) {
                val phoneIndex = line.indexOf(phone)
                val beforePhone = line.substring(0, phoneIndex).trim()
                val afterPhone = line.substring(phoneIndex + phone.length).trim()

                val roleRegex = Pattern.compile("(?i)\\b(Capo\\s*Tecn\\.?|Man\\.?|OP\\.?IS|Tecnico|Operatore|Inferm\\.?|Medico)\\b")
                val roleMatcher = roleRegex.matcher(beforePhone)
                val rawExtractedName = if (roleMatcher.find()) {
                    beforePhone.substring(0, roleMatcher.start()).trim().replace("_", " ")
                } else {
                    // Rimuove eventuali flag finali come Sì, Si, No, X preservando nomi composti a 3 o più parole
                    val stripped = beforePhone.replace(Regex("(?i)\\b(Sì|Si|No|X)\\b.*$"), "").trim().replace("_", " ")
                    if (stripped.isNotBlank() && stripped.any { it.isLetter() }) stripped else beforePhone.split(" ").take(2).joinToString(" ").replace("_", " ")
                }
                val name = rawExtractedName.trim().trimEnd(',', ';', ':', '-', ' ').trimStart(',', ';', ':', '-', ' ')
                val role = if (roleMatcher.find(0)) roleMatcher.group(1) ?: "Operatore" else "Operatore"
                val onCall = beforePhone.contains("Sì", ignoreCase = true) || beforePhone.contains("Si", ignoreCase = true)

                val rawTokens = extractShiftTokensFromLine(afterPhone)
                if (rawTokens.isNotEmpty() && name.any { it.isLetter() }) {
                    val dayShifts = mutableMapOf<LocalDate, String>()
                    for (i in rawTokens.indices) {
                        val date = columnDates.getOrNull(i) ?: continue
                        dayShifts[date] = rawTokens[i]
                    }

                    return ParsedEmployee(
                        name = name.ifEmpty { "Dipendente" },
                        role = role,
                        phone = phone,
                        teamName = teamName,
                        isOnCallGeneral = onCall,
                        dayShifts = dayShifts,
                        rawShiftSequence = rawTokens
                    )
                }
            }
        }

        // 2. Riconoscimento stile tabellare delimitato (CSV, TSV, punto e virgola)
        if (line.contains(",") || line.contains(";") || line.contains("\t")) {
            val parts = line.split(Regex("[,;\t]")).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 4) {
                val isPart0Date = parts[0].matches(Regex("^\\d{1,2}[/.-]\\d{1,2}(?:[/.-]\\d{2,4})?$"))
                val (possibleName, shiftParts) = if (isPart0Date && parts.size >= 5 && parts[1].any { it.isLetter() }) {
                    Pair(parts[1].trimEnd(',', ';', ':', '-', ' ').trimStart(',', ';', ':', '-', ' '), parts.drop(2))
                } else {
                    Pair(parts[0].trimEnd(',', ';', ':', '-', ' ').trimStart(',', ';', ':', '-', ' '), parts.drop(1))
                }

                if (possibleName.any { it.isLetter() } && !possibleName.matches(Regex("^\\d{1,2}[/.-]\\d{1,2}.*"))) {
                    val tokens = shiftParts.flatMap { extractShiftTokensFromLine(it) }
                    if (tokens.size >= 3) {
                        val dayShifts = mutableMapOf<LocalDate, String>()
                        for (i in tokens.indices) {
                            val date = columnDates.getOrNull(i) ?: continue
                            dayShifts[date] = tokens[i]
                        }
                        return ParsedEmployee(
                            name = possibleName,
                            role = "Operatore",
                            phone = "",
                            teamName = teamName,
                            isOnCallGeneral = false,
                            dayShifts = dayShifts,
                            rawShiftSequence = tokens
                        )
                    }
                }
            }
        }

        // 3. Riconoscimento riga generica: Nome Cognome seguito da sequenza turni (es. "Mario Rossi M P N RIP M P N")
        val tokens = extractShiftTokensFromLine(line)
        if (tokens.size >= 5) {
            val firstToken = tokens.first()
            val firstTokenIdx = line.indexOf(firstToken)
            if (firstTokenIdx > 2) {
                val rawName = line.substring(0, firstTokenIdx).trim().replace("_", " ")
                    .replace(Regex("^\\s*\\d{1,2}[/.-]\\d{1,2}(?:[/.-]\\d{2,4})?\\s*[,;]?\\s*"), "")
                    .trimEnd(',', ';', ':', '-', ' ').trimStart(',', ';', ':', '-', ' ')
                if (rawName.any { it.isLetter() }) {
                    val dayShifts = mutableMapOf<LocalDate, String>()
                    for (i in tokens.indices) {
                        val date = columnDates.getOrNull(i) ?: continue
                        dayShifts[date] = tokens[i]
                    }
                    return ParsedEmployee(
                        name = rawName,
                        role = "Operatore",
                        phone = "",
                        teamName = teamName,
                        isOnCallGeneral = false,
                        dayShifts = dayShifts,
                        rawShiftSequence = tokens
                    )
                }
            }
        }

        return null
    }

    private fun extractShiftTokensFromLine(text: String): List<String> {
        val tokens = mutableListOf<String>()
        // Codici RFI validi + codici universali / turni ospedalieri / aziendali (inclusi per esteso)
        val codeRegex = Pattern.compile(
            "\\b(MATTINA|POMERIGGIO|NOTTE|SMONTO|RIPOSO|FERIE|PERMESSO|MALATTIA|FORMAZIONE|GIORNALIERO|TGF|RGLS|RISE|GIORN|GIO|SMO|RIP|FER|PER|MAL|MAT|POM|NOT|MD|Rc|Ri|CF|PS|SN|SM|FE|PE|FORM|M|P|N|F|C|T|R|D|L|S|O|1|2|3)\\b",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = codeRegex.matcher(text)
        while (matcher.find()) {
            val code = matcher.group(1)
            if (code != null) {
                tokens.add(code.uppercase())
            }
        }
        return tokens
    }

    /**
     * Mappa un codice/sigla RFI o universale al relativo ID turno dell'applicazione.
     */
    fun mapCodeToShiftDefId(code: String): String {
        return when (code.uppercase()) {
            "M", "MAT", "MATTINA", "1" -> "mattina"
            "P", "POM", "POMERIGGIO", "2" -> "pomeriggio"
            "N", "NOT", "NOTTE", "3" -> "notte"
            "MD" -> "mat_dom"
            "TGF", "GIO", "GIORN", "GIORNALIERO", "D", "O", "T", "TRASF" -> "giornaliero"
            "RC", "RI", "RGLS", "RISE", "RIP", "RIPOSO", "R", "L" -> "riposo"
            "SMO", "SMONTO", "SN", "SM", "S" -> "smonto_notte"
            "F", "FER", "FERIE", "FE" -> "ferie"
            "C", "PER", "PERMESSO", "PE" -> "permesso"
            "MAL", "MALATTIA" -> "malattia"
            "CF", "PS", "FORM", "FORMAZIONE" -> "formazione"
            else -> "riposo"
        }
    }

    /**
     * Verifica la corrispondenza flessibile di un lavoratore con una query di ricerca,
     * gestendo inversioni di nome/cognome, underscore e delimitatori.
     */
    fun matchesEmployeeQuery(employee: ParsedEmployee, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true

        // 1. Corrispondenza diretta (nome, squadra, matricola/telefono)
        if (employee.name.contains(q, ignoreCase = true) ||
            employee.teamName.contains(q, ignoreCase = true) ||
            employee.phone.contains(q)) {
            return true
        }

        // 2. Normalizzazione caratteri speciali, underscore e punteggiatura
        val normEmpName = employee.name.replace("_", " ").replace(Regex("[,;:.]"), " ").lowercase()
        val normQuery = q.replace("_", " ").replace(Regex("[,;:.]"), " ").lowercase()
        if (normEmpName.contains(normQuery)) {
            return true
        }

        // 3. Corrispondenza token per token (gestisce inversione Nome Cognome / Cognome Nome)
        val queryWords = normQuery.split(Regex("\\s+")).filter { it.length >= 2 }
        if (queryWords.isNotEmpty() && queryWords.all { word -> normEmpName.contains(word) }) {
            return true
        }

        return false
    }

    private fun generateFallbackSequence(teamName: String): List<String> {
        val baseSequence = listOf(
            "M", "M", "P", "P", "P", "N", "N",
            "SMO", "RIP", "M", "M", "N", "SMO", "RIP",
            "P", "P", "N", "N", "SMO", "M", "RIP",
            "N", "N", "SMO", "RIP", "M", "P", "MD"
        )
        val shiftOffset = when (teamName.uppercase()) {
            "SQUADRA A" -> 0
            "SQUADRA B" -> 7
            "SQUADRA C" -> 14
            "SQUADRA D" -> 21
            "SQUADRA B 1", "SQUADRA B1" -> 3
            else -> 0
        }
        return baseSequence.drop(shiftOffset % baseSequence.size) + baseSequence.take(shiftOffset % baseSequence.size)
    }
}
