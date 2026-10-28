package com.turni.calendario.sync

import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToLong

class GoogleApiException(val code: Int, message: String, cause: Throwable? = null) : IOException(message, cause)

data class DriveBackupFile(val id: String, val modifiedTime: String, val size: Long = 0)
data class RemoteCalendarInfo(val id: String, val name: String, val accountName: String = "", val primary: Boolean = false)

private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
private fun encoded(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
private fun query(values: Map<String, String>): String = values.entries.joinToString("&", "?") { "${encoded(it.key)}=${encoded(it.value)}" }

/** Blocking transport: callers must dispatch onto an IO executor. Tokens and server bodies are never logged. */
private object GoogleHttp {
    fun request(token: String, url: String, method: String = "GET", body: ByteArray? = null,
                contentType: String = "application/json; charset=UTF-8"): String {
        if (token.isBlank()) throw GoogleApiException(401, "Accedi di nuovo al tuo account Google.")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = when (code) {
                    401 -> "La sessione Google è scaduta. Accedi di nuovo."
                    403 -> "Google non consente questa operazione. Verifica consenso, permessi e configurazione delle API."
                    404 -> "Il calendario o il backup selezionato non è più disponibile."
                    409 -> "Esiste già un elemento Google con questo identificativo."
                    429 -> "Google ha ricevuto troppe richieste. Riprova tra poco."
                    in 500..599 -> "Il servizio Google non è disponibile. Riprova più tardi."
                    else -> "Google non ha completato l'operazione (HTTP $code)."
                }
                throw GoogleApiException(code, message)
            }
            if (connection.contentLengthLong > MAX_RESPONSE_BYTES) throw GoogleApiException(413, "Il backup supera il limite di 16 MB.")
            return connection.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_RESPONSE_BYTES) throw GoogleApiException(413, "La risposta Google supera il limite di 16 MB.")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }
        } catch (error: GoogleApiException) {
            throw error
        } catch (error: IOException) {
            throw GoogleApiException(0, "Connessione a Google non riuscita. Controlla la rete e riprova.", error)
        } finally {
            connection.disconnect()
        }
    }

    fun json(token: String, url: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val text = request(token, url, method, body?.toString()?.toByteArray(StandardCharsets.UTF_8))
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}

object GoogleDriveRemote {
    private const val FILE_NAME = "turnisync-backup-v3.json"
    private const val ROOT = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    private const val FIELDS = "id,modifiedTime,size"

    private fun JSONObject.file() = DriveBackupFile(getString("id"), optString("modifiedTime"), optLong("size", 0))

    private fun files(token: String): List<DriveBackupFile> {
        var page: String? = null
        val result = mutableListOf<DriveBackupFile>()
        val seen = mutableSetOf<String>()
        do {
            val parameters = linkedMapOf("spaces" to "appDataFolder", "q" to "name = '$FILE_NAME' and trashed = false",
                "orderBy" to "modifiedTime desc", "pageSize" to "100", "fields" to "nextPageToken,files($FIELDS)")
            page?.let { parameters["pageToken"] = it }
            val response = GoogleHttp.json(token, ROOT + query(parameters))
            val files = response.optJSONArray("files") ?: JSONArray()
            for (index in 0 until files.length()) {
                result += files.getJSONObject(index).file()
            }
            page = response.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (page != null && !seen.add(page)) throw GoogleApiException(0, "Google ha restituito una pagina duplicata. Riprova.")
        } while (page != null)
        return result
    }

    fun latest(token: String): DriveBackupFile? = history(token).firstOrNull()

    fun history(token: String): List<DriveBackupFile> = files(token).sortedByDescending { java.time.Instant.parse(it.modifiedTime) }

    fun upload(token: String, json: String): DriveBackupFile = createSnapshot(json) { url,method,data,type ->
        GoogleHttp.request(token,url,method,data,type)
    }

    internal fun createSnapshot(json:String,request:(String,String,ByteArray,String)->String):DriveBackupFile {
        val data = json.toByteArray(StandardCharsets.UTF_8)
        if (data.size > MAX_RESPONSE_BYTES) throw GoogleApiException(413, "Il backup supera il limite di 16 MB.")
        JSONObject(json)
        // Immutable snapshots: a new/empty installation can never overwrite an older good copy.
        val boundary = "turnisync_${UUID.randomUUID()}"
        val metadata = JSONObject().put("name", FILE_NAME).put("parents", JSONArray().put("appDataFolder")).put("mimeType", "application/json")
        val body = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
            "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$json\r\n--$boundary--\r\n").toByteArray(StandardCharsets.UTF_8)
        return JSONObject(request(UPLOAD + query(mapOf("uploadType" to "multipart", "fields" to FIELDS)),
            "POST", body, "multipart/related; boundary=$boundary")).file()
    }

    fun download(token: String, id: String): String {
        require(id.isNotBlank()) { "Backup non selezionato." }
        // Verify membership using spaces: the appDataFolder alias is not necessarily returned as a parent ID.
        val backup = files(token).find { it.id == id }
            ?: throw GoogleApiException(403, "Il file selezionato non è un backup privato di TurniSync.")
        if (backup.size > MAX_RESPONSE_BYTES) throw GoogleApiException(413, "Il backup supera il limite di 16 MB.")
        val json = GoogleHttp.request(token, "$ROOT/${encoded(id)}?alt=media")
        JSONObject(json)
        return json
    }
}

/** Pure event construction kept separate so date, overtime and ownership invariants can be tested. */
object GoogleCalendarEventBuilder {
    fun stableId(date: LocalDate, assignmentId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$date\u0000$assignmentId".toByteArray(StandardCharsets.UTF_8))
        return "ts" + digest.take(24).joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun build(date: LocalDate, shift: AssignedShift, definition: ShiftDefinition, syncExactHours: Boolean = true,
              reminderMinutes: Int? = 60, zone: ZoneId = ZoneId.systemDefault()): JSONObject {
        val event = JSONObject().put("id", stableId(date, shift.id)).put("summary", definition.name)
            .put("description", buildString {
                if (shift.isTrasferta) append("Trasferta\n")
                if (shift.overtimeHours > 0f) append("Straordinario: ${ (shift.overtimeHours * 60).roundToLong() } minuti\n")
                if (shift.notes.isNotBlank()) append(shift.notes)
            }.trim())
            .put("extendedProperties", JSONObject().put("private", JSONObject().put("turnisync", "1")
                .put("assignmentId", shift.id).put("localDate", date.toString()).put("workProfileId", definition.workProfileId)))
        if (definition.isAllDay || !syncExactHours) {
            event.put("start", JSONObject().put("date", date.toString()))
            event.put("end", JSONObject().put("date", date.plusDays(1).toString()))
        } else {
            val startTime = LocalTime.parse(shift.customStartTime ?: definition.startTime)
            val endTime = LocalTime.parse(shift.customEndTime ?: definition.endTime)
            val start = date.atTime(startTime).atZone(zone)
            val scheduledDate = if (endTime <= startTime) date.plusDays(1) else date
            val scheduled = scheduledDate.atTime(endTime).atZone(zone)
            val actual = shift.actualEndTime?.let { LocalTime.parse(it) }
            val end = if (actual != null) {
                val minutes = Math.floorMod(actual.toSecondOfDay() / 60 - endTime.toSecondOfDay() / 60, 1440)
                if (minutes in 1..960) scheduled.plusMinutes(minutes.toLong())
                else (if (actual <= startTime) date.plusDays(1) else date).atTime(actual).atZone(zone)
            } else scheduled.plusMinutes(if (shift.overtimeHours.isFinite()) (shift.overtimeHours.coerceAtLeast(0f) * 60).roundToLong() else 0)
            require(end.toInstant() > start.toInstant()) { "L'orario finale del turno non è valido." }
            event.put("start", JSONObject().put("dateTime", start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).put("timeZone", zone.id))
            event.put("end", JSONObject().put("dateTime", end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).put("timeZone", zone.id))
        }
        val overrides = JSONArray()
        reminderMinutes?.let { overrides.put(JSONObject().put("method", "popup").put("minutes", it.coerceIn(0, 40320))) }
        event.put("reminders", JSONObject().put("useDefault", false).put("overrides", overrides))
        return event
    }

    fun isOwned(event: JSONObject): Boolean = event.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("turnisync") == "1"

    fun localDate(event: JSONObject): LocalDate? = try {
        val property = event.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("localDate").orEmpty()
        if (property.isNotBlank()) LocalDate.parse(property) else {
            val start = event.optJSONObject("start")
            val value = start?.optString("date").orEmpty().ifBlank { start?.optString("dateTime").orEmpty().take(10) }
            LocalDate.parse(value)
        }
    } catch (_: Exception) { null }
}

object GoogleCalendarRemote {
    private const val ROOT = "https://www.googleapis.com/calendar/v3"

    fun calendars(token: String): List<RemoteCalendarInfo> {
        val results = mutableListOf<RemoteCalendarInfo>()
        pages(token, "$ROOT/users/me/calendarList", mapOf("minAccessRole" to "writer", "maxResults" to "250")) { calendar ->
            if (calendar.optString("accessRole") in listOf("writer", "owner")) results += RemoteCalendarInfo(
                calendar.getString("id"), calendar.optString("summaryOverride").ifBlank { calendar.optString("summary", "Calendario") },
                primary = calendar.optBoolean("primary"))
        }
        return results
    }

    fun importCalendars(token:String):List<ImportCalendar> {
        val results=mutableListOf<ImportCalendar>()
        pages(token,"$ROOT/users/me/calendarList",mapOf("minAccessRole" to "reader","maxResults" to "250")) { item ->
            if(item.optString("accessRole") in listOf("reader","writer","owner"))results+=ImportCalendar(item.getString("id"),item.optString("summaryOverride").ifBlank { item.optString("summary","Calendario") },item.optString("timeZone","UTC"),item.optString("backgroundColor"))
        }
        return results
    }

    fun importEvents(token:String,calendar:ImportCalendar,start:LocalDate,end:LocalDate):List<ImportShiftEvent> {
        require(!end.isBefore(start) && java.time.temporal.ChronoUnit.DAYS.between(start,end)<=3660) { "Scegli un periodo non superiore a dieci anni." }
        val zone=ZoneId.of(calendar.zone)
        val results=mutableListOf<ImportShiftEvent>()
        pages(token,eventsUrl(calendar.id),mapOf("singleEvents" to "true","showDeleted" to "false","maxResults" to "2500",
            "timeMin" to start.atStartOfDay(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "timeMax" to end.plusDays(1).atStartOfDay(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))) { event ->
            require(results.size<100000) { "Troppi eventi: riduci il periodo." }
            if(event.optString("status")!="cancelled")results+=SuperShiftCalendarAdapter.googleEvent(event,calendar)
        }
        return results.filter { it.date>=start && it.date<=end }
    }

    private fun eventsUrl(calendarId: String) = "$ROOT/calendars/${encoded(calendarId)}/events"
    private fun validateCalendar(token: String, calendarId: String) {
        require(calendarId.isNotBlank()) { "Seleziona il calendario Google da utilizzare." }
        if (calendars(token).none { it.id == calendarId }) throw GoogleApiException(403, "Seleziona un calendario del tuo account Google con permesso di scrittura.")
    }

    fun sync(token: String, calendarId: String, shifts: List<Pair<LocalDate, AssignedShift>>, definitions: List<ShiftDefinition>,
             start: LocalDate, end: LocalDate, syncExactHours: Boolean = true, reminderMinutes: Int? = 60,
             includeRestDays: Boolean = false, workProfileId: String? = null): Int {
        require(!end.isBefore(start)) { "L'intervallo delle date non è valido." }
        validateCalendar(token, calendarId)
        val url = eventsUrl(calendarId)
        val zone = ZoneId.systemDefault()
        val selectedWorkIds = workProfileId?.let { setOf(it) } ?: definitions.map { it.workProfileId }.toSet()
        val existing = mutableListOf<JSONObject>()
        pages(token, url, mapOf("privateExtendedProperty" to "turnisync=1", "maxResults" to "2500", "showDeleted" to "false",
            "timeMin" to start.atStartOfDay(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            "timeMax" to end.plusDays(1).atStartOfDay(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))) { event ->
            val date = GoogleCalendarEventBuilder.localDate(event)
            val workId = event.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("workProfileId")
            if (GoogleCalendarEventBuilder.isOwned(event) && workId in selectedWorkIds && date != null && date >= start && date <= end) existing += event
        }
        val models = definitions.associateBy { it.id }
        // Prepare the entire batch before any network write: malformed local data cannot trigger cleanup.
        val desired = shifts.filter { it.first >= start && it.first <= end }.mapNotNull { (date, shift) ->
            val definition = models[shift.shiftDefId] ?: throw IllegalArgumentException("Il modello del turno non è disponibile.")
            if (!includeRestDays && definition.category == ShiftCategory.RIPOSO) null
            else GoogleCalendarEventBuilder.build(date, shift, definition, syncExactHours, reminderMinutes, zone)
        }.associateBy { it.getString("id") }
        val existingById = existing.associateBy { it.getString("id") }
        for ((id, event) in desired) {
            if (existingById.containsKey(id)) GoogleHttp.json(token, "$url/${encoded(id)}", "PUT", event)
            else try {
                GoogleHttp.json(token, url, "POST", event)
            } catch (error: GoogleApiException) {
                if (error.code != 409) throw error
                val prior = GoogleHttp.json(token, "$url/${encoded(id)}")
                val assignment = prior.optJSONObject("extendedProperties")?.optJSONObject("private")?.optString("assignmentId")
                if (!GoogleCalendarEventBuilder.isOwned(prior) || assignment != event.getJSONObject("extendedProperties").getJSONObject("private").getString("assignmentId")) throw error
                GoogleHttp.json(token, "$url/${encoded(id)}", "PUT", event)
            }
        }
        // A failed upsert exits before stale events are removed; personal events are never selected.
        existing.filter { it.getString("id") !in desired }.forEach { event ->
            GoogleHttp.request(token, "$url/${encoded(event.getString("id"))}", "DELETE")
        }
        return desired.size
    }

    fun deleteOwnedEvents(token: String, calendarId: String): Int {
        validateCalendar(token, calendarId)
        val url = eventsUrl(calendarId)
        val ids = mutableListOf<String>()
        pages(token, url, mapOf("privateExtendedProperty" to "turnisync=1", "maxResults" to "2500", "showDeleted" to "false")) { event ->
            if (GoogleCalendarEventBuilder.isOwned(event)) ids += event.getString("id")
        }
        // Collect all pages before deleting, so mutation cannot invalidate pagination.
        ids.forEach { GoogleHttp.request(token, "$url/${encoded(it)}", "DELETE") }
        return ids.size
    }

    private fun pages(token: String, url: String, base: Map<String, String>, consume: (JSONObject) -> Unit) {
        var page: String? = null
        val seen = mutableSetOf<String>()
        do {
            val parameters = base.toMutableMap()
            page?.let { parameters["pageToken"] = it }
            val result = GoogleHttp.json(token, url + query(parameters))
            val items = result.optJSONArray("items") ?: JSONArray()
            for (index in 0 until items.length()) consume(items.getJSONObject(index))
            page = result.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (page != null && !seen.add(page)) throw GoogleApiException(0, "Google ha restituito una pagina duplicata. Riprova.")
        } while (page != null)
    }
}
