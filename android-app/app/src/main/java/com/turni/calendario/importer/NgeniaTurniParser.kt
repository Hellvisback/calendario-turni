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
object NgeniaTurniParser {

    /**
     * Esegue il parsing completo del testo estratto o incollato.
     */
    fun parseDocumentText(rawText: String): NgeniaParseResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // 1. Estrazione periodo di validità (es. 21/09/2026 - 18/10/2026)
        val (valStart, valEnd) = extractValidityDates(rawText)

        // 2. Estrazione orari turni dal riquadro "Orari:" (M, P, N, MD, etc.)
        val hoursMap = extractHoursDefinitions(rawText)

        // 3. Estrazione date delle colonne giorno (es. 21/9, 22/9, ... 18/10)
        val columnDates = extractColumnDates(rawText, valStart)

        // 4. Estrazione Squadre e dipendenti
        val teams = extractTeamsAndMembers(lines, columnDates, hoursMap)
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
        // Pattern: "Turno Valido dal 21/09/2026 al 18/10/2026"
        val regex = Pattern.compile("(?i)(?:turno\\s+valido\\s+)?dal\\s+(\\d{1,2}/\\d{1,2}/\\d{4})\\s+al\\s+(\\d{1,2}/\\d{1,2}/\\d{4})")
        val matcher = regex.matcher(text)
        if (matcher.find()) {
            val d1Str = matcher.group(1)
            val d2Str = matcher.group(2)
            try {
                val dtf = DateTimeFormatter.ofPattern("d/M/yyyy")
                val start = LocalDate.parse(d1Str, dtf)
                val end = LocalDate.parse(d2Str, dtf)
                return Pair(start, end)
            } catch (_: Exception) {}
        }
        return Pair(null, null)
    }

    private fun extractHoursDefinitions(text: String): Map<String, Pair<String, String>> {
        val hours = mutableMapOf<String, Pair<String, String>>()
        // Pattern per "M 06:00-13:36", "P 14:00-21:36", "N 22:00-05:36", "MD 08:36-16:12"
        val regex = Pattern.compile("\\b([A-Z]{1,3})\\s+(\\d{1,2}:\\d{2})\\s*[-–]\\s*(\\d{1,2}:\\d{2})")
        val matcher = regex.matcher(text)
        while (matcher.find()) {
            val code = matcher.group(1).uppercase()
            val start = matcher.group(2)
            val end = matcher.group(3)
            hours[code] = Pair(start, end)
        }
        return hours
    }

    private fun extractColumnDates(text: String, fallbackStart: LocalDate?): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        val defaultYear = fallbackStart?.year ?: 2026

        // Cerchiamo sequenze di giorni es: 21/9 22/9 23/9 ...
        val dateTokenRegex = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})\\b")
        val matcher = dateTokenRegex.matcher(text)
        val tokens = mutableListOf<Pair<Int, Int>>()
        while (matcher.find()) {
            val day = matcher.group(1).toIntOrNull() ?: continue
            val month = matcher.group(2).toIntOrNull() ?: continue
            if (day in 1..31 && month in 1..12) {
                tokens.add(Pair(day, month))
            }
        }

        // Troviamo una sequenza consecutiva di almeno 7 date
        if (tokens.size >= 7) {
            for ((day, month) in tokens.take(28)) {
                try {
                    dates.add(LocalDate.of(defaultYear, month, day))
                } catch (_: Exception) {}
            }
        }

        // Se non riusciamo ad estrarre dai token, ma abbiamo la data di inizio
        if (dates.size < 7 && fallbackStart != null) {
            dates.clear()
            for (i in 0 until 28) {
                dates.add(fallbackStart.plusDays(i.toLong()))
            }
        }

        return dates
    }

    private fun extractTeamsAndMembers(
        lines: List<String>,
        columnDates: List<LocalDate>,
        hoursMap: Map<String, Pair<String, String>>
    ): List<ParsedTeamRotation> {
        val teams = mutableListOf<ParsedTeamRotation>()

        // Lista nomi squadre note o riconosciute
        val recognizedTeamNames = listOf("Squadra A", "Squadra B", "Squadra C", "Squadra D", "Squadra B 1")

        // Mappiamo blocchi di testo per squadra
        val teamBlocks = mutableMapOf<String, MutableList<String>>()
        var currentTeam = "Squadra B" // Default se inizia senza tag

        for (line in lines) {
            val matchedTeam = recognizedTeamNames.find { line.contains(it, ignoreCase = true) }
            if (matchedTeam != null) {
                currentTeam = matchedTeam
                teamBlocks.getOrPut(currentTeam) { mutableListOf() }
            } else {
                teamBlocks.getOrPut(currentTeam) { mutableListOf() }.add(line)
            }
        }

        // Per ogni squadra estraiamo la sequenza master e i singoli dipendenti
        for ((teamName, teamLines) in teamBlocks) {
            val members = mutableListOf<ParsedEmployee>()
            val rawShiftTokens = mutableListOf<String>()

            for (line in teamLines) {
                // Riconoscimento riga dipendente: "Marcozzi Loris Man. Sì X 8024724 P P Rc N N..."
                val emp = parseEmployeeLine(line, columnDates, teamName)
                if (emp != null) {
                    members.add(emp)
                    if (rawShiftTokens.isEmpty()) {
                        rawShiftTokens.addAll(emp.rawShiftSequence)
                    }
                } else {
                    // Controlliamo se è una riga con sequenza di turni della squadra (es: "N N M P MD M M P P P ...")
                    val tokens = extractShiftTokensFromLine(line)
                    if (tokens.size >= 10 && rawShiftTokens.size < 28) {
                        rawShiftTokens.addAll(tokens)
                    }
                }
            }

            // Normalizziamo a 28 giorni se necessario
            val finalCodes = if (rawShiftTokens.size >= 28) {
                rawShiftTokens.take(28)
            } else if (members.isNotEmpty() && members.first().rawShiftSequence.size >= 28) {
                members.first().rawShiftSequence.take(28)
            } else {
                // Sequenza predefinita basata su turno ferroviario se incompleta
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

        // Se qualche squadra nota non è stata trovata, la aggiungiamo con sfasamento ciclico
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

        return teams.sortedBy { it.teamName }
    }

    private fun parseEmployeeLine(
        line: String,
        columnDates: List<LocalDate>,
        teamName: String
    ): ParsedEmployee? {
        // Un dipendente tipico contiene matricola/telefono a 7 cifre (es. 8024724) o nome con profilo
        val phoneMatcher = Pattern.compile("\\b(80\\d{5}|88\\d{5})\\b").matcher(line)
        if (!phoneMatcher.find()) return null

        val phone = phoneMatcher.group(1)
        val phoneIndex = line.indexOf(phone)
        val beforePhone = line.substring(0, phoneIndex).trim()
        val afterPhone = line.substring(phoneIndex + phone.length).trim()

        // Estrazione nome: prima parte prima di Man./Capo Tecn./OP.IS
        val roleRegex = Pattern.compile("(?i)\\b(Capo\\s*Tecn\\.?|Man\\.?|OP\\.?IS|Tecnico)\\b")
        val roleMatcher = roleRegex.matcher(beforePhone)
        val name = if (roleMatcher.find()) {
            beforePhone.substring(0, roleMatcher.start()).trim().replace("_", " ")
        } else {
            beforePhone.split(" ").take(2).joinToString(" ").replace("_", " ")
        }
        val role = if (roleMatcher.find(0)) roleMatcher.group(1) else "Operatore"

        val onCall = beforePhone.contains("Sì", ignoreCase = true) || beforePhone.contains("Si", ignoreCase = true)

        // Turni giornalieri dopo il telefono
        val rawTokens = extractShiftTokensFromLine(afterPhone)
        val dayShifts = mutableMapOf<LocalDate, String>()
        for (i in rawTokens.indices) {
            val date = columnDates.getOrNull(i) ?: continue
            dayShifts[date] = rawTokens[i]
        }

        return ParsedEmployee(
            name = name.ifEmpty { "Dipendente RFI" },
            role = role,
            phone = phone,
            teamName = teamName,
            isOnCallGeneral = onCall,
            dayShifts = dayShifts,
            rawShiftSequence = rawTokens
        )
    }

    private fun extractShiftTokensFromLine(text: String): List<String> {
        val tokens = mutableListOf<String>()
        // Codici RFI validi: M, P, N, MD, TGF, CF, PS, C, F, T, Rc, Ri, RGLS, RISE
        val codeRegex = Pattern.compile("\\b(TGF|RGLS|RISE|MD|Rc|Ri|CF|PS|M|P|N|F|C|T)\\b", Pattern.CASE_INSENSITIVE)
        val matcher = codeRegex.matcher(text)
        while (matcher.find()) {
            tokens.add(matcher.group(1).uppercase())
        }
        return tokens
    }

    /**
     * Mappa un codice/sigla RFI al relativo ID turno dell'applicazione.
     */
    fun mapCodeToShiftDefId(code: String): String {
        return when (code.uppercase()) {
            "M" -> "mattina"
            "P" -> "pomeriggio"
            "N" -> "notte"
            "MD" -> "mat_dom"
            "TGF", "GIO" -> "giornaliero"
            "RC", "RI", "RGLS", "RISE", "RIP", "R" -> "riposo"
            "SMO", "SN" -> "smonto_notte"
            "F", "FER" -> "ferie"
            "C" -> "permesso"
            "CF", "PS" -> "formazione"
            else -> "riposo"
        }
    }

    private fun generateFallbackSequence(teamName: String): List<String> {
        // Sequenza canonica 28 giorni ricavata dalla matrice ufficiale
        val baseSequence = listOf(
            "M", "M", "P", "P", "P", "N", "N",
            "SMO", "RIP", "M", "M", "N", "SMO", "RIP",
            "P", "P", "N", "N", "SMO", "M", "RIP",
            "N", "N", "SMO", "RIP", "M", "P", "MD"
        )
        // Sfasamento per le diverse squadre
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
