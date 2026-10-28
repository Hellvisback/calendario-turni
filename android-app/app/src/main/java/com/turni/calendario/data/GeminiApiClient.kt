package com.turni.calendario.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

/**
 * REST Client for Google Gemini Flash Vision API using native [HttpsURLConnection].
 *
 * Implements multimodal content generation, Base64 image payload transmission,
 * structured JSON output enforcement (`response_schema`), comprehensive HTTP and network error
 * handling, and resilient JSON response parsing.
 */
open class GeminiApiClient(
    val model: String = DEFAULT_MODEL,
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS
) {

    companion object : GeminiApiClient() {
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 30_000
        const val DEFAULT_READ_TIMEOUT_MS = 120_000

        val DEFAULT_PROMPT: String get() = AiRosterImport.prompt(null)
    }

    /**
     * Executes multimodal shift extraction against Google Gemini Flash.
     * Runs asynchronously on [Dispatchers.IO] and returns a [Result] containing [GeminiShiftData]
     * or a descriptive failure exception.
     *
     * @param apiKey The user's Google Gemini API Key.
     * @param imageBase64 Base64-encoded image or document bitmap.
     * @param mimeType MIME type of the image (defaults to "image/jpeg").
     * @param promptText Optional custom prompt string (defaults to [DEFAULT_PROMPT]).
     */
    suspend fun extractShiftData(
        apiKey: String,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        promptText: String? = null
    ): Result<GeminiShiftData> = withContext(Dispatchers.IO) {
        runCatching {
            executeRequest(apiKey, imageBase64, mimeType, promptText)
        }
    }

    /**
     * Synchronous execution of the HTTPS POST request to Gemini REST endpoint.
     */
    @Throws(Exception::class)
    fun executeRequest(
        apiKey: String,
        imageBase64: String,
        mimeType: String = "image/jpeg",
        promptText: String? = null
    ): GeminiShiftData {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            throw IllegalArgumentException(
                "API Key di Google Gemini non configurata. Inseriscila nelle impostazioni prima di procedere."
            )
        }
        if (imageBase64.isBlank()) {
            throw IllegalArgumentException("I dati dell'immagine sono vuoti o non validi.")
        }

        val requestUrl = "$BASE_URL/$model:generateContent"
        val url = URL(requestUrl)
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-goog-api-key", cleanKey)
        }

        try {
            val requestBody = buildRequestBody(imageBase64, mimeType, promptText)
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseString = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                return parseGeminiResponse(responseString)
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                } catch (_: Exception) {
                    ""
                }

                val errorMessage = extractErrorMessage(errorBody, responseCode)
                when (responseCode) {
                    400 -> throw IllegalArgumentException(
                        "Richiesta non valida (HTTP 400): $errorMessage. Verifica che la chiave API e il documento siano corretti."
                    )
                    401, 403 -> throw SecurityException(
                        "Accesso negato (HTTP $responseCode): chiave API non valida o priva di autorizzazioni per Gemini Flash. $errorMessage"
                    )
                    429 -> throw IllegalStateException(
                        "Quota richieste Gemini superata (HTTP 429): limite di frequenza raggiunto o quota esaurita. Attendi qualche istante prima di riprovare."
                    )
                    in 500..599 -> throw IOException(
                        "I server di Google Gemini sono temporaneamente non disponibili (HTTP $responseCode). Riprova tra poco."
                    )
                    else -> throw IOException(
                        "Errore durante la chiamata a Google Gemini (HTTP $responseCode): $errorMessage"
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            throw IOException(
                "Timeout di connessione: il server Gemini ha impiegato troppo tempo a rispondere. Controlla la velocità della rete.",
                e
            )
        } catch (e: UnknownHostException) {
            throw IOException(
                "Nessuna connessione a internet. Impossibile raggiungere i server di Google Gemini. Verifica la rete.",
                e
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Builds the multimodal JSON request body including image inline_data and response_schema.
     */
    fun buildRequestBody(
        imageBase64: String,
        mimeType: String = "image/jpeg",
        promptText: String? = null
    ): String {
        val prompt = promptText ?: DEFAULT_PROMPT

        val textPart = JSONObject().apply {
            put("text", prompt)
        }

        val inlineData = JSONObject().apply {
            put("mime_type", mimeType)
            put("data", imageBase64)
        }

        val imagePart = JSONObject().apply {
            put("inline_data", inlineData)
        }

        val partsArray = JSONArray().apply {
            put(textPart)
            put(imagePart)
        }

        val contentObj = JSONObject().apply {
            put("role", "user")
            put("parts", partsArray)
        }

        val contentsArray = JSONArray().apply {
            put(contentObj)
        }

        // Response Schema definition
        val shiftDefProperties = JSONObject().apply {
            put("code", JSONObject().apply {
                put("type", "STRING")
                put("description", "Sigla del turno (es. M, P, N, MD, GIO, UFF, SMO, RIP)")
            })
            put("name", JSONObject().apply {
                put("type", "STRING")
                put("description", "Nome descrittivo del turno (es. Mattina, Pomeriggio, Notte, Smonto Notte)")
            })
            put("startTime", JSONObject().apply {
                put("type", "STRING")
                put("description", "Orario inizio nel formato HH:mm (es. 06:00)")
            })
            put("endTime", JSONObject().apply {
                put("type", "STRING")
                put("description", "Orario fine nel formato HH:mm (es. 13:36)")
            })
            put("colorHex", JSONObject().apply {
                put("type", "STRING")
                put("description", "Colore esadecimale opzionale es. #0284C7")
            })
            put("isReperibilita", JSONObject().apply {
                put("type", "BOOLEAN")
                put("description", "True se il turno prevede reperibilità associata")
            })
        }
        val shiftDefRequired = JSONArray().apply {
            put("code")
            put("name")
            put("startTime")
            put("endTime")
        }
        val shiftDefItems = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", shiftDefProperties)
            put("required", shiftDefRequired)
        }

        val squadProperties = JSONObject().apply {
            put("squadName", JSONObject().apply {
                put("type", "STRING")
                put("description", "Nome della squadra (es. Squadra A, Squadra B)")
            })
            put("rotationDays", JSONObject().apply {
                put("type", "ARRAY")
                put("description", "Sequenza ordinata di codici turno per il ciclo (es. [\"M\", \"P\", \"N\", \"SMO\", \"RIP\"])")
                put("items", JSONObject().apply { put("type", "STRING") })
            })
            put("employees", JSONObject().apply {
                put("type", "ARRAY")
                put("description", "Elenco dei nominativi dei lavoratori assegnati a questa squadra")
                put("items", JSONObject().apply { put("type", "STRING") })
            })
        }
        val squadRequired = JSONArray().apply {
            put("squadName")
            put("rotationDays")
        }
        val squadItems = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", squadProperties)
            put("required", squadRequired)
        }

        val responseSchemaProperties = JSONObject().apply {
            put("shiftDefinitions", JSONObject().apply {
                put("type", "ARRAY")
                put("description", "Definizioni e orari dei singoli turni")
                put("items", shiftDefItems)
            })
            put("squads", JSONObject().apply {
                put("type", "ARRAY")
                put("description", "Elenco delle squadre e rotazioni cicliche")
                put("items", squadItems)
            })
            put("validityStart", JSONObject().apply {
                put("type", "STRING")
                put("description", "Data di inizio validità del prospetto se presente (YYYY-MM-DD)")
            })
            put("validityEnd", JSONObject().apply {
                put("type", "STRING")
                put("description", "Data di fine validità del prospetto se presente (YYYY-MM-DD)")
            })
            put("notes", JSONObject().apply {
                put("type", "STRING")
                put("description", "Note su reperibilità o particolarità del prospetto")
            })
        }
        val responseSchemaRequired = JSONArray().apply {
            put("shiftDefinitions")
            put("squads")
        }
        val responseSchema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", responseSchemaProperties)
            put("required", responseSchemaRequired)
        }

        val generationConfig = JSONObject().apply {
            put("temperature", 0.1)
            put("response_mime_type", "application/json")
            put("response_schema", AiRosterImport.responseSchema())
            put("maxOutputTokens", 32768)
        }

        val root = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }

        return root.toString()
    }

    private fun normalizeTimeString(raw: String, default: String): String {
        val t = raw.trim().replace('.', ':')
        if (t.matches(Regex("^\\d{1,2}:\\d{2}$"))) {
            val parts = t.split(":")
            val h = parts[0].padStart(2, '0')
            val m = parts[1]
            return "$h:$m"
        }
        return if (t.isNotBlank()) t else default
    }

    /**
     * Parses the JSON string from Gemini into [GeminiShiftData].
     * Resiliently handles full REST envelopes (with "candidates"), inner JSON text blocks,
     * markdown code block fences (```json ... ```), and alternative key naming.
     */
    @Throws(Exception::class)
    fun parseGeminiResponse(jsonString: String): GeminiShiftData {
        var cleaned = jsonString.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.removePrefix("```json").trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```").trim()
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.removeSuffix("```").trim()
        }

        val root = JSONObject(cleaned)

        // If this is the outer Gemini REST response envelope
        if (root.has("candidates")) {
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() == 0) {
                throw IllegalStateException("Nessun contenuto generato dal modello Gemini (array candidates vuoto).")
            }
            val candidate = candidates.getJSONObject(0)
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason == "SAFETY") {
                throw SecurityException("La risposta è stata bloccata dai filtri di sicurezza di Gemini.")
            }

            val content = candidate.optJSONObject("content")
                ?: throw IllegalStateException("Risposta Gemini non valida: manca l'oggetto 'content'.")
            val parts = content.optJSONArray("parts")
                ?: throw IllegalStateException("Risposta Gemini non valida: manca l'array 'parts'.")
            if (parts.length() == 0) {
                throw IllegalStateException("Risposta Gemini non valida: array 'parts' vuoto.")
            }
            if(finishReason=="MAX_TOKENS")throw IllegalStateException("Risposta AI incompleta. Prova un prospetto più breve.")
            val innerText = (0 until parts.length()).map{parts.getJSONObject(it)}.filter{!it.optBoolean("thought",false)}.mapNotNull{it.optString("text").takeIf{value->value.isNotBlank()}}.joinToString("")
            val parsedInner = parseGeminiResponse(innerText)
            return parsedInner.copy(rawJson = jsonString)
        }

        // Parse shiftDefinitions with extensive fallback key aliases
        val shiftDefinitionsArray = root.optJSONArray("shiftDefinitions")
            ?: root.optJSONArray("shift_definitions")
            ?: root.optJSONArray("shifts")
            ?: root.optJSONArray("turni")
            ?: root.optJSONArray("shiftHours")
            ?: root.optJSONArray("definizioniTurni")
            ?: root.optJSONArray("definizioni_turni")
            ?: JSONArray()

        val shiftDefinitions = mutableListOf<GeminiShiftDefinition>()
        for (i in 0 until shiftDefinitionsArray.length()) {
            val item = shiftDefinitionsArray.getJSONObject(i)
            val code = item.optString("code", item.optString("sigla", item.optString("codice", item.optString("id", "")))).trim().uppercase()
            if (code.isBlank()) continue
            val name = item.optString("name", item.optString("nome", item.optString("descrizione", item.optString("description", code)))).trim()
            val rawStartTime = item.optString("startTime", item.optString("start_time", item.optString("inizio", item.optString("orarioInizio", item.optString("start", "06:00"))))).trim()
            val rawEndTime = item.optString("endTime", item.optString("end_time", item.optString("fine", item.optString("orarioFine", item.optString("end", "13:36"))))).trim()
            val startTime = normalizeTimeString(rawStartTime, "06:00")
            val endTime = normalizeTimeString(rawEndTime, "13:36")
            val colorHex = when {
                item.has("colorHex") && !item.isNull("colorHex") -> item.getString("colorHex")
                item.has("color_hex") && !item.isNull("color_hex") -> item.getString("color_hex")
                item.has("colore") && !item.isNull("colore") -> item.getString("colore")
                item.has("color") && !item.isNull("color") -> item.getString("color")
                else -> null
            }
            val isReperibilita = item.optBoolean("isReperibilita", item.optBoolean("is_reperibilita", item.optBoolean("reperibilita", item.optBoolean("reperibile", false))))

            shiftDefinitions.add(
                GeminiShiftDefinition(
                    code = code,
                    name = name,
                    startTime = startTime,
                    endTime = endTime,
                    colorHex = colorHex,
                    isReperibilita = isReperibilita
                )
            )
        }

        // Parse squads with extensive fallback aliases (teams, squadre, rotazioni, rotations, gruppi)
        val squadsArray = root.optJSONArray("squads")
            ?: root.optJSONArray("teams")
            ?: root.optJSONArray("squadre")
            ?: root.optJSONArray("rotazioni")
            ?: root.optJSONArray("rotations")
            ?: root.optJSONArray("gruppi")
            ?: JSONArray()

        val squads = mutableListOf<GeminiSquadRotation>()
        for (i in 0 until squadsArray.length()) {
            val item = squadsArray.getJSONObject(i)
            val squadName = item.optString("squadName", item.optString("squad_name", item.optString("teamName", item.optString("team_name", item.optString("nome", item.optString("nome_squadra", "Squadra ${i + 1}")))))).trim()

            val rotDaysArray = item.optJSONArray("rotationDays")
                ?: item.optJSONArray("rotation_days")
                ?: item.optJSONArray("rotationSequence")
                ?: item.optJSONArray("sequenza")
                ?: item.optJSONArray("turni")
                ?: item.optJSONArray("giorni")
                ?: JSONArray()
            val rotationDays = mutableListOf<String>()
            for (j in 0 until rotDaysArray.length()) {
                val rotVal = rotDaysArray.get(j)
                val dayCode = if (rotVal is JSONObject) {
                    rotVal.optString("code", rotVal.optString("sigla", rotVal.optString("turno", "")))
                } else {
                    rotVal.toString()
                }.trim().uppercase()
                if (dayCode.isNotEmpty()) {
                    rotationDays.add(dayCode)
                }
            }

            val empArray = item.optJSONArray("employees")
                ?: item.optJSONArray("dipendenti")
                ?: item.optJSONArray("lavoratori")
                ?: item.optJSONArray("membri")
                ?: item.optJSONArray("colleghi")
                ?: JSONArray()
            val employees = mutableListOf<String>()
            for (k in 0 until empArray.length()) {
                val empVal = empArray.get(k)
                val empName = if (empVal is JSONObject) {
                    empVal.optString("name", empVal.optString("nome", ""))
                } else {
                    empVal.toString()
                }.trim()
                if (empName.isNotEmpty()) {
                    employees.add(empName)
                }
            }

            val cycleDays = item.optInt("cycleDays", item.optInt("cycle_days", item.optInt("giorni_ciclo", rotationDays.size)))

            squads.add(
                GeminiSquadRotation(
                    squadName = squadName,
                    rotationDays = rotationDays,
                    cycleDays = if (cycleDays > 0) cycleDays else rotationDays.size,
                    employees = employees
                )
            )
        }

        // Parse optional employees list
        val employeesArray = root.optJSONArray("employees") ?: JSONArray()
        val employeesList = mutableListOf<GeminiEmployeeAssignment>()
        for (i in 0 until employeesArray.length()) {
            val empObj = employeesArray.optJSONObject(i) ?: continue
            val name = empObj.optString("name", "").trim()
            if (name.isBlank()) continue
            val squadName = when {
                empObj.has("teamName") && !empObj.isNull("teamName") -> empObj.getString("teamName")
                empObj.has("squadName") && !empObj.isNull("squadName") -> empObj.getString("squadName")
                else -> null
            }
            val role = if (empObj.has("role") && !empObj.isNull("role")) empObj.getString("role") else null
            val employeeId = if (empObj.has("employeeId") && !empObj.isNull("employeeId")) empObj.getString("employeeId") else null
            val isOnCall = empObj.optBoolean("isOnCall", false)

            val dailyShiftsMap = mutableMapOf<String, String>()
            val dailyShiftsArr = empObj.optJSONArray("dailyShifts")
            if (dailyShiftsArr != null) {
                for (d in 0 until dailyShiftsArr.length()) {
                    val dObj = dailyShiftsArr.optJSONObject(d)
                    if (dObj != null) {
                        val date = dObj.optString("date", "")
                        val code = dObj.optString("code", "")
                        if (date.isNotEmpty() && code.isNotEmpty()) {
                            dailyShiftsMap[date] = code
                        }
                    }
                }
            }

            employeesList.add(
                GeminiEmployeeAssignment(
                    name = name,
                    squadName = squadName,
                    role = role,
                    employeeId = employeeId,
                    isOnCall = isOnCall,
                    dailyShifts = dailyShiftsMap
                )
            )
        }

        val notes = if (root.has("notes") && !root.isNull("notes")) root.getString("notes") else null
        val validityStart = if (root.has("validityStart") && !root.isNull("validityStart")) root.getString("validityStart") else null
        val validityEnd = if (root.has("validityEnd") && !root.isNull("validityEnd")) root.getString("validityEnd") else null

        return GeminiShiftData(
            shiftDefinitions = shiftDefinitions,
            squads = squads,
            notes = notes,
            validityStart = validityStart,
            validityEnd = validityEnd,
            employees = employeesList,
            rawJson = jsonString
        )
    }

    private fun extractErrorMessage(errorBody: String, responseCode: Int): String {
        if (errorBody.isBlank()) return "Errore HTTP $responseCode"
        return try {
            val root = JSONObject(errorBody)
            if (root.has("error")) {
                val errObj = root.getJSONObject("error")
                errObj.optString("message", errorBody.take(150))
            } else {
                errorBody.take(150)
            }
        } catch (_: Exception) {
            errorBody.take(150)
        }
    }
}
