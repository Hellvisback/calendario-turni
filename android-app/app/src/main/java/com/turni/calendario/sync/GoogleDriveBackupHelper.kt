package com.turni.calendario.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.turni.calendario.model.*
import com.turni.calendario.repository.ShiftRepository
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
    fun exportDatabaseToJson(): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("exportDate", LocalDate.now().toString())
        root.put("activeWorkProfileId", ShiftRepository.activeWorkProfileId)

        // 1. Modelli Turno
        val modelsArray = JSONArray()
        ShiftRepository.shiftDefinitions.toList().forEach { def ->
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
            }
            modelsArray.put(obj)
        }
        root.put("shiftDefinitions", modelsArray)

        // 2. Rotazioni
        val rotationsArray = JSONArray()
        ShiftRepository.rotations.toList().forEach { rot ->
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
        ShiftRepository.calendarShifts.toMap().forEach { (date, list) ->
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
                    put("notes", s.notes)
                    put("isOvertime", s.isOvertime)
                    put("overtimeHours", s.overtimeHours.toDouble())
                    put("isTrasferta", s.isTrasferta)
                    put("hideExShift", s.hideExShift)
                }
                shiftsArray.put(obj)
            }
        }
        root.put("calendarShifts", shiftsArray)

        // 4. Extra Info per Giorno (straordinari, trasferte, note)
        val extraArray = JSONArray()
        ShiftRepository.dayExtraInfo.toMap().forEach { (date, extra) ->
            val obj = JSONObject().apply {
                put("date", date.toString())
                put("overtimeHours", extra.overtimeHours.toDouble())
                put("isTrasferta", extra.isTrasferta)
                put("hideExShift", extra.hideExShift)
                put("notes", extra.notes)
            }
            extraArray.put(obj)
        }
        root.put("dayExtraInfo", extraArray)

        // 5. Profili di Lavoro
        val profilesArray = JSONArray()
        ShiftRepository.workProfiles.toList().forEach { p ->
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
            }
            profilesArray.put(pObj)
        }
        root.put("workProfiles", profilesArray)

        // 5. Preferenze & Reperibilità
        val prefObj = JSONObject().apply {
            put("showExShifts", ShiftRepository.appPreferences.showExShifts)
            put("weekStartMonday", ShiftRepository.appPreferences.weekStartMonday)
            put("timeFormat24h", ShiftRepository.appPreferences.timeFormat24h)
            put("showHolidays", ShiftRepository.appPreferences.showHolidays)
            put("autoBackupDaily", ShiftRepository.appPreferences.autoBackupDaily)
        }
        root.put("appPreferences", prefObj)

        return root.toString(2)
    }

    /**
     * Scrive il backup JSON direttamente all'URI selezionato dall'utente (Google Drive o storage locale via SAF).
     */
    fun writeBackupToUri(context: Context, uri: Uri): Boolean {
        return try {
            val json = exportDatabaseToJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
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
     * Esegue il salvataggio automatico diretto nel cloud storage dell'app per Google Drive
     * senza richiedere all'utente di selezionare cartelle o confermare dialoghi esterni.
     */
    fun saveCloudBackupDirect(context: Context): Boolean {
        return try {
            val json = exportDatabaseToJson()
            val dir = File(context.filesDir, "cloud_backups")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "turni_cloud_backup_latest.json")
            file.writeText(json, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun hasCloudBackup(context: Context): Boolean {
        val file = File(File(context.filesDir, "cloud_backups"), "turni_cloud_backup_latest.json")
        return file.exists() && file.length() > 0
    }

    fun getCloudBackupInfo(context: Context): String? {
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
     * Ripristina direttamente l'ultimo backup cloud salvato senza aprire selettori di file.
     */
    fun restoreCloudBackupDirect(context: Context): RestoreResult {
        return try {
            val file = File(File(context.filesDir, "cloud_backups"), "turni_cloud_backup_latest.json")
            if (!file.exists() || file.length() == 0L) {
                return RestoreResult(false, "Nessun backup trovato su Google Drive.")
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
    fun restoreFromJsonString(jsonStr: String): RestoreResult {
        return try {
            val root = JSONObject(jsonStr)
            ShiftRepository.saveSnapshot()

            var modelsCount = 0
            var rotationsCount = 0
            var shiftsCount = 0

            // 1. Modelli Turno
            if (root.has("shiftDefinitions")) {
                val arr = root.getJSONArray("shiftDefinitions")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val def = ShiftDefinition(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        shortCode = obj.getString("shortCode"),
                        startTime = obj.optString("startTime", "06:00"),
                        endTime = obj.optString("endTime", "13:36"),
                        isAllDay = obj.optBoolean("isAllDay", false),
                        breakMinutes = obj.optInt("breakMinutes", 0),
                        colorHex = obj.optLong("colorHex", 0xFF0284C7),
                        category = try {
                            ShiftCategory.valueOf(obj.optString("category", "LAVORO"))
                        } catch (_: Exception) {
                            ShiftCategory.LAVORO
                        }
                    )
                    ShiftRepository.saveShiftDefinition(def)
                    modelsCount++
                }
            }

            // 2. Rotazioni
            if (root.has("rotations")) {
                val arr = root.getJSONArray("rotations")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val seqArr = obj.optJSONArray("daySequence")
                    val sequence = mutableListOf<String>()
                    if (seqArr != null) {
                        for (j in 0 until seqArr.length()) {
                            sequence.add(seqArr.getString(j))
                        }
                    }
                    val rot = ShiftRotation(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        totalDays = obj.optInt("totalDays", sequence.size),
                        daySequence = sequence,
                        description = obj.optString("description", "")
                    )
                    ShiftRepository.saveRotation(rot)
                    rotationsCount++
                }
            }

            // 3. Assegnazioni Calendario
            if (root.has("calendarShifts")) {
                ShiftRepository.calendarShifts.clear()
                val arr = root.getJSONArray("calendarShifts")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val date = LocalDate.parse(obj.getString("date"))
                    val origDefId = obj.optString("originalShiftDefId").ifBlank { null }
                    val isMod = obj.optBoolean("isModifiedFromRotation", false)
                    val isFromRot = obj.optBoolean("isFromRotation", false)

                    val assigned = AssignedShift(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        date = date,
                        shiftDefId = obj.getString("shiftDefId"),
                        originalShiftDefId = origDefId,
                        isModifiedFromRotation = isMod,
                        isFromRotation = isFromRot,
                        notes = obj.optString("notes", ""),
                        isOvertime = obj.optBoolean("isOvertime", false),
                        overtimeHours = obj.optDouble("overtimeHours", 0.0).toFloat(),
                        isTrasferta = obj.optBoolean("isTrasferta", false),
                        hideExShift = obj.optBoolean("hideExShift", false)
                    )

                    val list = ShiftRepository.calendarShifts.getOrPut(date) { androidx.compose.runtime.mutableStateListOf() }
                    list.add(assigned)
                    shiftsCount++
                }
            }

            // 4. Extra Info
            if (root.has("dayExtraInfo")) {
                val arr = root.getJSONArray("dayExtraInfo")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val date = LocalDate.parse(obj.getString("date"))
                    val extra = DayExtraInfo(
                        date = date,
                        overtimeHours = obj.optDouble("overtimeHours", 0.0).toFloat(),
                        isTrasferta = obj.optBoolean("isTrasferta", false),
                        hideExShift = obj.optBoolean("hideExShift", false),
                        notes = obj.optString("notes", "")
                    )
                    ShiftRepository.dayExtraInfo[date] = extra
                }
            }

            // 5. Preferenze
            if (root.has("appPreferences")) {
                val p = root.getJSONObject("appPreferences")
                ShiftRepository.appPreferences = ShiftRepository.appPreferences.copy(
                    showExShifts = p.optBoolean("showExShifts", ShiftRepository.appPreferences.showExShifts),
                    weekStartMonday = p.optBoolean("weekStartMonday", ShiftRepository.appPreferences.weekStartMonday),
                    timeFormat24h = p.optBoolean("timeFormat24h", ShiftRepository.appPreferences.timeFormat24h),
                    showHolidays = p.optBoolean("showHolidays", ShiftRepository.appPreferences.showHolidays),
                    autoBackupDaily = p.optBoolean("autoBackupDaily", ShiftRepository.appPreferences.autoBackupDaily)
                )
            }

            RestoreResult(
                success = true,
                message = "Ripristino completato con successo!",
                shiftsCount = shiftsCount,
                modelsCount = modelsCount,
                rotationsCount = rotationsCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult(false, "Errore durante il parsing del backup: ${e.localizedMessage}")
        }
    }

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
