package com.turni.calendario.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.repository.DatabaseSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDate

data class RestoreResult(
    val success: Boolean,
    val message: String,
    val shiftsCount: Int = 0,
    val modelsCount: Int = 0,
    val rotationsCount: Int = 0
)

object GoogleDriveBackupHelper {

    /**
     * Serializza l'intero database dell'applicazione in formato JSON.
     */
    fun exportDatabaseToJson(snapshot: DatabaseSnapshot = DatabaseSnapshot.capture()): String {
        val root = JSONObject()
        root.put("version", 4)
        root.put("statisticsRules", JSONArray().apply { snapshot.statisticsRules.forEach { rule -> put(JSONObject().put("id",rule.id).put("displayedShiftId",rule.displayedShiftId).put("originalShiftIds",JSONArray(rule.originalShiftIds.toList())).put("enabled",rule.enabled)) } })
        root.put("exportDate", LocalDate.now().toString())
        root.put("activeWorkProfileId", snapshot.activeWorkProfileId)
        root.put("activeRotationId", snapshot.activeRotationId)
        root.put("defaultRotationId", snapshot.defaultRotationId)

        // 1. Modelli Turno
        val modelsArray = JSONArray()
        snapshot.definitions.forEach { def ->
            val obj = JSONObject().apply {
                put("id", def.id)
                put("name", def.name)
                put("shortCode", def.shortCode)
                put("startTime", def.startTime)
                put("endTime", def.endTime)
                put("isAllDay", def.isAllDay)
                put("breakMinutes", def.breakMinutes)
                put("breakStartTime", def.breakStartTime ?: "")
                put("colorHex", def.colorHex)
                put("iconKey", def.iconKey)
                put("category", def.category.name)
                put("workProfileId", def.workProfileId)
                put("hourlyRate", def.hourlyRate)
                put("allowance", def.allowance)
            }
            modelsArray.put(obj)
        }
        root.put("shiftDefinitions", modelsArray)

        // 2. Rotazioni
        val rotationsArray = JSONArray()
        snapshot.rotations.forEach { rot ->
            val obj = JSONObject().apply {
                put("id", rot.id)
                put("name", rot.name)
                put("totalDays", rot.totalDays)
                put("description", rot.description)
                val seqArray = JSONArray()
                rot.daySequence.forEach { seqArray.put(it) }
                put("daySequence", seqArray)
            }
            rotationsArray.put(obj)
        }
        root.put("rotations", rotationsArray)

        // 3. Assegnazioni Calendario
        val shiftsArray = JSONArray()
        snapshot.shifts.forEach { (date, list) ->
            list.toList().forEach { s ->
                val obj = JSONObject().apply {
                    put("id", s.id)
                    put("date", date.toString())
                    put("shiftDefId", s.shiftDefId)
                    put("originalShiftDefId", s.originalShiftDefId ?: "")
                    put("isModifiedFromRotation", s.isModifiedFromRotation)
                    put("isFromRotation", s.isFromRotation)
                    put("customStartTime", s.customStartTime ?: "")
                    put("customEndTime", s.customEndTime ?: "")
                    put("actualEndTime", s.actualEndTime ?: "")
                    put("notes", s.notes)
                    put("isOvertime", s.isOvertime)
                    put("overtimeHours", s.overtimeHours.toDouble())
                    put("isTrasferta", s.isTrasferta)
                    put("hideExShift", s.hideExShift)
                    put("tags", JSONArray(s.tags))
                    put("isOnCallManual", s.isOnCallManual ?: JSONObject.NULL)
                }
                shiftsArray.put(obj)
            }
        }
        root.put("calendarShifts", shiftsArray)

        // 4. Extra Info per Giorno (straordinari, trasferte, note)
        val extraArray = JSONArray()
        snapshot.extras.forEach { (date, extra) ->
            val obj = JSONObject().apply {
                put("date", date.toString())
                put("overtimeHours", extra.overtimeHours.toDouble())
                put("isTrasferta", extra.isTrasferta)
                put("hideExShift", extra.hideExShift)
                put("rotationCleared", extra.rotationCleared)
                put("notes", extra.notes)
                put("actualEndTime", extra.actualEndTime ?: "")
                put("isOnCallManual", extra.isOnCallManual ?: JSONObject.NULL)
            }
            extraArray.put(obj)
        }
        root.put("dayExtraInfo", extraArray)

        // 5. Profili di Lavoro
        val profilesArray = JSONArray()
        snapshot.profiles.forEach { p ->
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("companyOrDepartment", p.companyOrDepartment)
                put("weeklyHours", p.weeklyHours)
                put("minRestHoursBetweenShifts", p.minRestHoursBetweenShifts)
                put("nightSurchargePercent", p.nightSurchargePercent)
                put("holidaySurchargePercent", p.holidaySurchargePercent)
                put("nightHolidaySurchargePercent", p.nightHolidaySurchargePercent)
                put("overtimeDaySurchargePercent", p.overtimeDaySurchargePercent)
                put("overtimeNightSurchargePercent", p.overtimeNightSurchargePercent)
                put("colorHex", p.colorHex)
                put("isDefault", p.isDefault)
                put("enableAdvancedCalc", p.enableAdvancedCalc)
                put("iconKey", p.iconKey)
                put("ccnlCode", p.ccnlCode)
                put("ccnlName", p.ccnlName)
                put("ccnlDocumentName", p.ccnlDocumentName ?: "")
                put("ccnlDocumentUri", p.ccnlDocumentUri ?: "")
            }
            profilesArray.put(pObj)
        }
        root.put("workProfiles", profilesArray)

        // 5. Preferenze & Reperibilità
        val prefObj = JSONObject().apply {
            put("showExShifts", snapshot.preferences.showExShifts)
            put("weekStartMonday", snapshot.preferences.weekStartMonday)
            put("timeFormat24h", snapshot.preferences.timeFormat24h)
            put("showHolidays", snapshot.preferences.showHolidays)
            put("autoBackupDaily", snapshot.preferences.autoBackupDaily)
            put("excludeVacationOnHolidays", snapshot.preferences.excludeVacationOnHolidays)
            put("holidayExcludedShiftIds", JSONArray(snapshot.preferences.holidayExcludedShiftIds.toList()))
        }
        root.put("appPreferences", prefObj)
        root.put("enabledHolidayIds", JSONArray(snapshot.holidayIds.toList()))
        root.put("reperibilitaConfig", JSONObject().apply {
            put("mode", snapshot.onCall.mode.name)
            put("shiftSpecificIds", JSONArray(snapshot.onCall.shiftSpecificIds))
            put("manualDates", JSONArray(snapshot.onCall.manualDates.map { it.toString() }))
            put("seasonal", JSONObject().apply {
                val s = snapshot.onCall.seasonal
                put("winterStartMonth", s.winterStartMonth); put("winterStartDay", s.winterStartDay)
                put("winterEndMonth", s.winterEndMonth); put("winterEndDay", s.winterEndDay)
                put("winterShiftIds", JSONArray(s.winterShiftIds))
                put("summerCycleWorkWeeks", s.summerCycleWorkWeeks)
                put("summerCycleOnCallWeeks", s.summerCycleOnCallWeeks)
                put("summerCycleReferenceDate", s.summerCycleReferenceDate.toString())
            })
        })
        root.put("smartAlarmsConfig", JSONObject().apply {
            put("isGlobalEnabled", snapshot.alarms.isGlobalEnabled)
            put("disabledAlarmIds", JSONArray(snapshot.alarms.disabledAlarmIds.toList()))
            put("rules", JSONArray().apply {
                snapshot.alarms.rules.forEach { rule -> put(JSONObject().apply {
                    put("shiftDefId", rule.shiftDefId); put("isEnabled", rule.isEnabled)
                    put("minutesBefore", rule.minutesBefore); put("customTime", rule.customTime ?: "")
                }) }
            })
        })

        return root.toString(2)
    }

    /**
     * Scrive il backup JSON direttamente all'URI selezionato dall'utente (Google Drive o storage locale via SAF).
     */
    fun writeBackupToUri(context: Context, uri: Uri): Boolean {
        return try {
            val json = exportDatabaseToJson()
            val output = context.contentResolver.openOutputStream(uri) ?: return false
            output.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Saves an on-device copy only; remote Drive operations use GoogleDriveRemote.
     * senza richiedere all'utente di selezionare cartelle o confermare dialoghi esterni.
     */
    fun saveLocalBackupDirect(context: Context, snapshot: DatabaseSnapshot = DatabaseSnapshot.capture()): Boolean {
        return try {
            check(com.turni.calendario.repository.LocalDatabaseStore.loadError == null) { "Dati locali non leggibili: copia sospesa." }
            val json = exportDatabaseToJson(snapshot)
            require(json.toByteArray(Charsets.UTF_8).size<=16*1024*1024){"Copia locale troppo grande."}
            DatabaseRestore.parse(json,snapshot)
            val dir = File(context.filesDir, "cloud_backups")
            LocalBackupArchive.save(dir,json)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun hasLocalBackup(context: Context): Boolean {
        val file = File(File(context.filesDir, "cloud_backups"), "turni_cloud_backup_latest.json")
        return file.exists() && file.length() > 0
    }

    fun getLocalBackupInfo(context: Context): String? {
        val file = File(File(context.filesDir, "cloud_backups"), "turni_cloud_backup_latest.json")
        if (!file.exists()) return null
        return try {
            val instant = java.time.Instant.ofEpochMilli(file.lastModified())
            val dt = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
            dt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Restores the legacy on-device copy; it does not contact Google Drive.
     */
    fun restoreLocalBackupDirect(context: Context): RestoreResult {
        return try {
            val file = File(File(context.filesDir, "cloud_backups"), "turni_cloud_backup_latest.json")
            if (!file.exists() || file.length() == 0L) {
                return RestoreResult(false, "Nessuna copia locale disponibile.")
            }
            val json = file.readText(Charsets.UTF_8)
            restoreFromJsonString(json)
        } catch (e: Exception) {
            RestoreResult(false, "Errore ripristino backup: ${e.localizedMessage}")
        }
    }

    /**
     * Ripristina il database completo dall'URI fornito dall'utente (letto da Google Drive o storage locale).
     */
    fun restoreDatabaseFromUri(context: Context, uri: Uri): RestoreResult {
        return try {
            val stringBuilder = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                }
            }
            val jsonStr = stringBuilder.toString()
            if (jsonStr.isBlank()) {
                return RestoreResult(false, "Il file selezionato è vuoto.")
            }
            restoreFromJsonString(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(false, "Errore durante la lettura del file: ${e.localizedMessage}")
        }
    }

    /**
     * Parsing ed importazione del JSON nel database dell'applicazione.
     */
    fun restoreFromJsonString(jsonStr: String): RestoreResult = DatabaseRestore.restore(jsonStr)


    /**
     * Crea un intent per condividere il file di backup direttamente con Google Drive ("Salva su Drive").
     */
    fun createDriveShareIntent(context: Context): Intent? {
        return try {
            val json = exportDatabaseToJson()
            val file = File(context.cacheDir, "turni_backup_${LocalDate.now()}.json")
            file.writeText(json, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Backup Calendario Turni - ${LocalDate.now()}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
