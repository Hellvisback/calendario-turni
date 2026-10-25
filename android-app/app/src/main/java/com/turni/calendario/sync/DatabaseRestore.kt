package com.turni.calendario.sync

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.repository.LocalDatabaseStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Parse and validate the entire backup before changing any live state. */
object DatabaseRestore {
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.strings() ?: emptyList()
    private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
    private fun JSONObject.tristate(key: String): Boolean? = if (!has(key) || isNull(key)) null else getBoolean(key)
    private fun <T> JSONObject.items(key: String, fallback: List<T>, map: (JSONObject) -> T): List<T> {
        if (!has(key)) return fallback
        val array = getJSONArray(key)
        require(array.length() <= 100000) { "Backup troppo grande" }
        return (0 until array.length()).map { map(array.getJSONObject(it)) }
    }

    fun parse(text: String, previous: DatabaseSnapshot = DatabaseSnapshot.capture()): DatabaseSnapshot {
        require(text.length <= 24 * 1024 * 1024) { "Backup troppo grande" }
        val root = JSONObject(text)
        require(root.optInt("version", 1) in 1..3) { "Versione backup non supportata" }
        require(root.has("shiftDefinitions") || root.has("calendarShifts")) { "File backup non valido" }
        val definitions = root.items("shiftDefinitions", previous.definitions) { o -> ShiftDefinition(
            id = o.getString("id"), name = o.getString("name"), shortCode = o.getString("shortCode"),
            startTime = o.optString("startTime", "06:00"), endTime = o.optString("endTime", "13:36"),
            isAllDay = o.optBoolean("isAllDay"), breakMinutes = o.optInt("breakMinutes"),
            breakStartTime = o.optional("breakStartTime"), colorHex = o.optLong("colorHex", 0xFF0284C7),
            iconKey = o.optString("iconKey", "sunny"),
            category = runCatching { ShiftCategory.valueOf(o.optString("category", "LAVORO")) }.getOrDefault(ShiftCategory.LAVORO),
            workProfileId = o.optString("workProfileId", "principale"),
            hourlyRate = o.optDouble("hourlyRate", 0.0), allowance = o.optDouble("allowance", 0.0)
        ) }
        val rotations = root.items("rotations", previous.rotations) { o ->
            val sequence = o.strings("daySequence")
            ShiftRotation(o.getString("id"), o.getString("name"), sequence.size, sequence, o.optString("description"))
        }
        val shifts = root.items("calendarShifts", previous.shifts.values.flatten()) { o -> AssignedShift(
            id = o.optString("id", java.util.UUID.randomUUID().toString()), date = LocalDate.parse(o.getString("date")),
            shiftDefId = o.getString("shiftDefId"), originalShiftDefId = o.optional("originalShiftDefId"),
            isModifiedFromRotation = o.optBoolean("isModifiedFromRotation"), isFromRotation = o.optBoolean("isFromRotation"),
            customStartTime = o.optional("customStartTime"), customEndTime = o.optional("customEndTime"),
            actualEndTime = o.optional("actualEndTime"), notes = o.optString("notes"), tags = o.strings("tags"),
            isOvertime = o.optBoolean("isOvertime"), overtimeHours = o.optDouble("overtimeHours", 0.0).toFloat(),
            isTrasferta = o.optBoolean("isTrasferta"), isOnCallManual = o.tristate("isOnCallManual"),
            hideExShift = o.optBoolean("hideExShift")
        ) }
        val extras = root.items("dayExtraInfo", previous.extras.values.toList()) { o -> DayExtraInfo(
            date = LocalDate.parse(o.getString("date")), overtimeHours = o.optDouble("overtimeHours", 0.0).toFloat(),
            isTrasferta = o.optBoolean("isTrasferta"), isOnCallManual = o.tristate("isOnCallManual"),
            hideExShift = o.optBoolean("hideExShift"), rotationCleared = o.optBoolean("rotationCleared"),
            notes = o.optString("notes"), actualEndTime = o.optional("actualEndTime")
        ) }
        val profiles = root.items("workProfiles", previous.profiles) { o -> WorkProfile(
            id = o.getString("id"), name = o.getString("name"), companyOrDepartment = o.optString("companyOrDepartment"),
            ccnlCode = o.optString("ccnlCode", "CCNL_MOBILITA_AF"), ccnlName = o.optString("ccnlName", "CCNL Mobilità & Trasporti"),
            weeklyHours = o.optInt("weeklyHours", 38), minRestHoursBetweenShifts = o.optInt("minRestHoursBetweenShifts", 11),
            nightSurchargePercent = o.optInt("nightSurchargePercent", 35), holidaySurchargePercent = o.optInt("holidaySurchargePercent", 50),
            nightHolidaySurchargePercent = o.optInt("nightHolidaySurchargePercent", 65),
            overtimeDaySurchargePercent = o.optInt("overtimeDaySurchargePercent", 25), overtimeNightSurchargePercent = o.optInt("overtimeNightSurchargePercent", 40),
            colorHex = o.optLong("colorHex", 0xFF0284C7), iconKey = o.optString("iconKey", "work"),
            isDefault = o.optBoolean("isDefault"), enableAdvancedCalc = o.optBoolean("enableAdvancedCalc"),
            ccnlDocumentName = o.optional("ccnlDocumentName"), ccnlDocumentUri = o.optional("ccnlDocumentUri")
        ) }
        require(profiles.isNotEmpty()) { "Nessun profilo lavoro" }
        require(definitions.map { it.id }.distinct().size == definitions.size && definitions.none { it.id.isBlank() }) { "Modelli duplicati" }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "Lavori duplicati" }
        require(shifts.map { it.id }.distinct().size == shifts.size) { "Turni duplicati" }
        require(shifts.all { it.overtimeHours.isFinite() && it.overtimeHours >= 0f }) { "Straordinario non valido" }
        require(extras.all { it.overtimeHours.isFinite() && it.overtimeHours >= 0f }) { "Straordinario non valido" }
        val pref = root.optJSONObject("appPreferences")
        val preferences = if (pref == null) previous.preferences else AppPreferences(
            pref.optBoolean("showExShifts", true), pref.optBoolean("weekStartMonday", true),
            pref.optBoolean("timeFormat24h", true), pref.optBoolean("showHolidays", true), pref.optBoolean("autoBackupDaily", true)
        )
        val c = root.optJSONObject("reperibilitaConfig")
        val seasonal = c?.optJSONObject("seasonal")?.let { s -> SeasonalReperibilitaConfig(
            s.optInt("winterStartMonth", 10), s.optInt("winterStartDay", 1), s.optInt("winterEndMonth", 5), s.optInt("winterEndDay", 31),
            s.strings("winterShiftIds"), s.optInt("summerCycleWorkWeeks", 3), s.optInt("summerCycleOnCallWeeks", 1),
            LocalDate.parse(s.optString("summerCycleReferenceDate", "2026-06-01"))
        ) } ?: previous.onCall.seasonal
        val onCall = if (c == null) previous.onCall else ReperibilitaConfig(
            ReperibilitaMode.valueOf(c.optString("mode", "MANUALE")), c.strings("shiftSpecificIds"), seasonal,
            c.strings("manualDates").map { LocalDate.parse(it) }.toSet()
        )
        val a = root.optJSONObject("smartAlarmsConfig")
        val alarms = if (a == null) previous.alarms else SmartAlarmsConfig(
            a.optBoolean("isGlobalEnabled", true), a.items("rules", emptyList()) { o -> ShiftAlarmRule(
                o.getString("shiftDefId"), o.optBoolean("isEnabled", true), o.optInt("minutesBefore", 60), o.optional("customTime")
            ) }, a.strings("disabledAlarmIds").toSet()
        )
        val activeWork = root.optString("activeWorkProfileId", previous.activeWorkProfileId).takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        val extraByDate = extras.associateBy { it.date }
        val migratedShifts = shifts.groupBy { it.date }.mapValues { (date, assigned) ->
            val legacy = extraByDate[date]
            if (legacy == null || assigned.isEmpty()) assigned else {
                val migrateOvertime = legacy.overtimeHours > 0f && assigned.none { it.isOvertime || it.overtimeHours > 0f || it.actualEndTime != null }
                val migrateTravel = legacy.isTrasferta && assigned.none { it.isTrasferta }
                if (!migrateOvertime && !migrateTravel) assigned else assigned.mapIndexed { index, shift ->
                    if (index != 0) shift else shift.copy(
                        overtimeHours = if (migrateOvertime) legacy.overtimeHours else shift.overtimeHours,
                        isOvertime = shift.isOvertime || migrateOvertime,
                        actualEndTime = if (migrateOvertime) legacy.actualEndTime ?: shift.actualEndTime else shift.actualEndTime,
                        isTrasferta = shift.isTrasferta || migrateTravel
                    )
                }
            }
        }
        return DatabaseSnapshot(definitions, rotations, migratedShifts, extraByDate, profiles,
            activeWork, root.optString("activeRotationId", previous.activeRotationId), root.optString("defaultRotationId", previous.defaultRotationId),
            preferences, onCall, alarms, if (root.has("enabledHolidayIds")) root.strings("enabledHolidayIds").toSet() else previous.holidayIds)
    }

    fun apply(snapshot: DatabaseSnapshot) {
        Snapshot.withMutableSnapshot {
            ShiftRepository.shiftDefinitions.clear(); ShiftRepository.shiftDefinitions.addAll(snapshot.definitions)
            ShiftRepository.rotations.clear(); ShiftRepository.rotations.addAll(snapshot.rotations)
            ShiftRepository.calendarShifts.clear()
            snapshot.shifts.forEach { (date, shifts) -> ShiftRepository.calendarShifts[date] = mutableStateListOf<AssignedShift>().apply { addAll(shifts) } }
            ShiftRepository.dayExtraInfo.clear(); ShiftRepository.dayExtraInfo.putAll(snapshot.extras)
            ShiftRepository.workProfiles.clear(); ShiftRepository.workProfiles.addAll(snapshot.profiles)
            ShiftRepository.activeWorkProfileId = snapshot.activeWorkProfileId
            ShiftRepository.activeRotationId = snapshot.activeRotationId
            ShiftRepository.defaultRotationId = snapshot.defaultRotationId
            ShiftRepository.appPreferences = snapshot.preferences
            ShiftRepository.reperibilitaConfig = snapshot.onCall
            ShiftRepository.smartAlarmsConfig = snapshot.alarms
            ShiftRepository.enabledHolidayIds = snapshot.holidayIds
        }
        ShiftRepository.notifyDataChanged()
    }

    fun restore(text: String): RestoreResult = try {
        val parsed = parse(text)
        ShiftRepository.saveSnapshot()
        LocalDatabaseStore.allowPersistenceAfterRestore()
        apply(parsed)
        RestoreResult(true, "Ripristino completato", parsed.shifts.values.sumOf { it.size }, parsed.definitions.size, parsed.rotations.size)
    } catch (e: Exception) {
        RestoreResult(false, "Backup non valido: ${e.localizedMessage}")
    }
}
