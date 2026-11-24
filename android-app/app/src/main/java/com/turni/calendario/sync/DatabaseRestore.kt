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

    private fun validateReferences(definitions: List<ShiftDefinition>, profiles: List<WorkProfile>,
                                   shifts: List<AssignedShift>, rotations: List<ShiftRotation>) {
        val modelIds = definitions.map { it.id }.toSet()
        val profileIds = profiles.map { it.id }.toSet()
        require(profiles.isNotEmpty() && profiles.none { it.id.isBlank() } && profileIds.size == profiles.size) { "Identificativi lavoro vuoti o duplicati" }
        require(definitions.none { it.id.isBlank() } && modelIds.size == definitions.size) { "Identificativi modello vuoti o duplicati" }
        require(definitions.all { it.workProfileId in profileIds }) { "Il backup contiene un modello senza un lavoro valido" }
        require(shifts.none { it.id.isBlank() } && shifts.map { it.id }.distinct().size == shifts.size) { "Identificativi turno vuoti o duplicati" }
        require(shifts.all { it.shiftDefId in modelIds }) { "Il backup contiene un turno senza un modello valido" }
        require(shifts.all { it.originalShiftDefId == null || it.originalShiftDefId in modelIds }) { "Il backup contiene un turno originale non più definito" }
        require(rotations.none { it.id.isBlank() } && rotations.map { it.id }.distinct().size == rotations.size) { "Identificativi rotazione vuoti o duplicati" }
        // Empty cycle slots intentionally mean that no shift is assigned on that day.
        require(rotations.all { rotation -> rotation.daySequence.all { it.isBlank() || it in modelIds } }) { "Il backup contiene una rotazione con un modello non definito" }
    }

    fun parse(text: String, previous: DatabaseSnapshot = DatabaseSnapshot.capture()): DatabaseSnapshot {
        require(text.length <= 24 * 1024 * 1024) { "Backup troppo grande" }
        val root = JSONObject(text)
        val version = root.optInt("version", 1)
        require(version in 1..6) { "Versione backup non supportata" }
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
        val profiles = root.items("workProfiles", previous.profiles) { o -> WorkProfile(
            id = o.getString("id"), name = o.getString("name"), companyOrDepartment = o.optString("companyOrDepartment"),
            ccnlCode = o.optString("ccnlCode", "CCNL_MOBILITA_AF"), ccnlName = o.optString("ccnlName", "CCNL Mobilità & Trasporti"),
            weeklyHours = o.optInt("weeklyHours", 38), minRestHoursBetweenShifts = o.optInt("minRestHoursBetweenShifts", 11),
            nightSurchargePercent = o.optInt("nightSurchargePercent", 35), holidaySurchargePercent = o.optInt("holidaySurchargePercent", 50),
            nightHolidaySurchargePercent = o.optInt("nightHolidaySurchargePercent", 65),
            overtimeDaySurchargePercent = o.optInt("overtimeDaySurchargePercent", 25), overtimeNightSurchargePercent = o.optInt("overtimeNightSurchargePercent", 40),
            colorHex = o.optLong("colorHex", 0xFF0284C7), iconKey = o.optString("iconKey", "work"),
            isDefault = o.optBoolean("isDefault"), enableAdvancedCalc = o.optBoolean("enableAdvancedCalc"),
            ccnlDocumentName = o.optional("ccnlDocumentName"), ccnlDocumentUri = o.optional("ccnlDocumentUri"),
            maxConsecutiveWorkDays = o.optInt("maxConsecutiveWorkDays", 6).coerceIn(0, 31)
        ) }
        val activeWork = root.optString("activeWorkProfileId", previous.activeWorkProfileId).takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        val rotations = root.items("rotations", previous.rotations) { o ->
            val sequence = o.strings("daySequence")
            ShiftRotation(
                o.getString("id"), o.getString("name"), sequence.size, sequence,
                o.optString("description"), o.optString("workProfileId", activeWork)
            )
        }
        val shifts = root.items("calendarShifts", previous.shifts.values.flatten()) { o -> AssignedShift(
            id = o.optString("id", java.util.UUID.randomUUID().toString()), date = LocalDate.parse(o.getString("date")),
            shiftDefId = o.getString("shiftDefId"), originalShiftDefId = o.optional("originalShiftDefId"),
            isModifiedFromRotation = o.optBoolean("isModifiedFromRotation"), isFromRotation = o.optBoolean("isFromRotation"),
            customStartTime = o.optional("customStartTime"), customEndTime = o.optional("customEndTime"),
            actualStartTime = o.optional("actualStartTime"),
            actualEndTime = o.optional("actualEndTime"), notes = o.optString("notes"), tags = o.strings("tags"),
            isOvertime = o.optBoolean("isOvertime"), overtimeHours = o.optDouble("overtimeHours", 0.0).toFloat(),
            isTrasferta = o.optBoolean("isTrasferta"), isOnCallManual = o.tristate("isOnCallManual"),
            hideExShift = o.optBoolean("hideExShift"),
            permissionHours = o.optDouble("permissionHours", 0.0).toFloat(),
            startOvertimeHours = o.optDouble("startOvertimeHours", 0.0).toFloat(),
            startPermissionHours = o.optDouble("startPermissionHours", 0.0).toFloat(),
            endOvertimeHours = o.optDouble("endOvertimeHours", 0.0).toFloat(),
            endPermissionHours = o.optDouble("endPermissionHours", 0.0).toFloat()
        ) }
        fun parseExtra(o: JSONObject) = DayExtraInfo(
            date = LocalDate.parse(o.getString("date")), overtimeHours = o.optDouble("overtimeHours", 0.0).toFloat(),
            isTrasferta = o.optBoolean("isTrasferta"), isOnCallManual = o.tristate("isOnCallManual"),
            hideExShift = o.optBoolean("hideExShift"), rotationCleared = o.optBoolean("rotationCleared"),
            notes = o.optString("notes"), actualEndTime = o.optional("actualEndTime"), tagIds = if(o.has("tagIds"))o.getJSONArray("tagIds").strings()else emptyList()
        )
        val extras = root.items("dayExtraInfo", previous.extras.values.toList(), ::parseExtra)
        validateReferences(definitions, profiles, shifts, rotations)
        require(shifts.all {
            it.overtimeHours.isFinite() && it.overtimeHours >= 0f &&
                it.startOvertimeHours.isFinite() && it.startOvertimeHours >= 0f &&
                it.endOvertimeHours.isFinite() && it.endOvertimeHours >= 0f
        }) { "Straordinario non valido" }
        require(shifts.all {
            it.permissionHours.isFinite() && it.permissionHours >= 0f &&
                it.startPermissionHours.isFinite() && it.startPermissionHours >= 0f &&
                it.endPermissionHours.isFinite() && it.endPermissionHours >= 0f
        }) { "Ore di permesso non valide" }
        require(extras.all { it.overtimeHours.isFinite() && it.overtimeHours >= 0f }) { "Straordinario non valido" }
        val pref = root.optJSONObject("appPreferences")
        val preferences = if (pref == null) previous.preferences else AppPreferences(
            pref.optBoolean("showExShifts", true), pref.optBoolean("weekStartMonday", true),
            pref.optBoolean("timeFormat24h", true), pref.optBoolean("showHolidays", true), pref.optBoolean("autoBackupDaily", true),
            pref.optBoolean("excludeVacationOnHolidays",false),pref.strings("holidayExcludedShiftIds").filter{ id->definitions.any{it.id==id} }.toSet(),
            pref.optional("themeMode").also{require(it==null||it in setOf("AUTOMATIC","LIGHT","DARK")){"Tema del backup non valido"}}
        )
        fun parseOnCall(c: JSONObject, fallback: ReperibilitaConfig): ReperibilitaConfig {
            val seasonal = c.optJSONObject("seasonal")?.let { s -> SeasonalReperibilitaConfig(
                s.optInt("winterStartMonth", 10), s.optInt("winterStartDay", 1), s.optInt("winterEndMonth", 5), s.optInt("winterEndDay", 31),
                s.strings("winterShiftIds"), s.optInt("summerCycleWorkWeeks", 3), s.optInt("summerCycleOnCallWeeks", 1),
                LocalDate.parse(s.optString("summerCycleReferenceDate", "2026-06-01"))
            ) } ?: fallback.seasonal
            return ReperibilitaConfig(
                ReperibilitaMode.valueOf(c.optString("mode", "MANUALE")), c.strings("shiftSpecificIds"), seasonal,
                c.strings("manualDates").map { LocalDate.parse(it) }.toSet()
            )
        }
        fun parseAlarms(a: JSONObject): SmartAlarmsConfig = SmartAlarmsConfig(
            a.optBoolean("isGlobalEnabled", true), a.items("rules", emptyList()) { o -> ShiftAlarmRule(
                o.getString("shiftDefId"), o.optBoolean("isEnabled", true), o.optInt("minutesBefore", 60), o.optional("customTime")
            ) }, a.strings("disabledAlarmIds").toSet()
        )
        val c = root.optJSONObject("reperibilitaConfig")
        val onCall = if (c == null) previous.onCall else parseOnCall(c, previous.onCall)
        val a = root.optJSONObject("smartAlarmsConfig")
        val alarms = if (a == null) previous.alarms else parseAlarms(a)
        val extraByDate = extras.associateBy { it.date }
        val extrasByProfile = (if (version >= 6) root.optJSONObject("dayExtraInfoByProfile") else null)?.let { obj ->
            obj.keys().asSequence().associateWith { pId ->
                val arr = obj.getJSONArray(pId)
                (0 until arr.length()).map { parseExtra(arr.getJSONObject(it)) }.associateBy { it.date }
            }.filterValues { it.isNotEmpty() }
        } ?: if (extraByDate.isNotEmpty()) mapOf(activeWork to extraByDate) else emptyMap()
        val onCallByProfile = (if (version >= 6) root.optJSONObject("reperibilitaByProfile") else null)?.let { obj ->
            obj.keys().asSequence().associateWith { pId -> parseOnCall(obj.getJSONObject(pId), onCall) }
        } ?: if (onCall != ReperibilitaConfig()) mapOf(activeWork to onCall) else emptyMap()
        val alarmsByProfile = (if (version >= 6) root.optJSONObject("smartAlarmsByProfile") else null)?.let { obj ->
            obj.keys().asSequence().associateWith { pId -> parseAlarms(obj.getJSONObject(pId)) }
        } ?: if (alarms != SmartAlarmsConfig()) mapOf(activeWork to alarms) else emptyMap()
        val activeRotationId = root.optString("activeRotationId", previous.activeRotationId)
        val defaultRotationId = root.optString("defaultRotationId", previous.defaultRotationId)
        val activeRotationIdByProfile = (if (version >= 6) root.optJSONObject("activeRotationIdByProfile") else null)?.let { obj ->
            obj.keys().asSequence().associateWith { pId -> obj.optString(pId, "") }.filterValues { it.isNotEmpty() }
        } ?: if (activeRotationId.isNotEmpty()) mapOf(activeWork to activeRotationId) else emptyMap()
        val defaultRotationIdByProfile = (if (version >= 6) root.optJSONObject("defaultRotationIdByProfile") else null)?.let { obj ->
            obj.keys().asSequence().associateWith { pId -> obj.optString(pId, "") }.filterValues { it.isNotEmpty() }
        } ?: if (defaultRotationId.isNotEmpty()) mapOf(activeWork to defaultRotationId) else emptyMap()

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
        val defProfileById = definitions.associate { it.id to it.workProfileId }
        val statisticsRules=root.items("statisticsRules", legacyStatisticsRules(definitions)) { o ->
            val dispId = o.getString("displayedShiftId")
            val ruleProfile = o.optString("workProfileId", defProfileById[dispId] ?: activeWork)
            StatisticsRule(o.getString("id"), dispId, o.strings("originalShiftIds").toSet(), o.optBoolean("enabled",true), ruleProfile)
        }
        validateStatisticsRules(statisticsRules,definitions)
        val dayTags = root.items("dayTags", emptyList<DayTag>()) { o ->
            DayTag(o.getString("id"), o.getString("name"), o.optString("iconKey", "tag"),
                o.optLong("colorHex", 0xFF94C4EF), o.optBoolean("archived"), o.optString("workProfileId", activeWork))
        }
        validateDayTags(dayTags, extrasByProfile.values.flatMap { it.entries }.associate { it.key to it.value })
        return DatabaseSnapshot(definitions, rotations, migratedShifts, extraByDate, profiles,
            activeWork, activeRotationId, defaultRotationId,
            preferences, onCall, alarms, if (root.has("enabledHolidayIds")) root.strings("enabledHolidayIds").toSet() else previous.holidayIds, statisticsRules, dayTags,
            extrasByProfile, onCallByProfile, alarmsByProfile, activeRotationIdByProfile, defaultRotationIdByProfile)
    }

    fun apply(snapshot: DatabaseSnapshot) {
        validateReferences(snapshot.definitions, snapshot.profiles, snapshot.shifts.values.flatten(), snapshot.rotations)
        validateStatisticsRules(snapshot.statisticsRules,snapshot.definitions)
        val allExtras = if (snapshot.extrasByProfile.isNotEmpty()) {
            snapshot.extrasByProfile.values.flatMap { it.entries }.associate { it.key to it.value }
        } else snapshot.extras
        validateDayTags(snapshot.dayTags, allExtras)
        Snapshot.withMutableSnapshot {
            ShiftRepository.shiftDefinitions.clear(); ShiftRepository.shiftDefinitions.addAll(snapshot.definitions.sortedChronologically())
            ShiftRepository.rotations.clear(); ShiftRepository.rotations.addAll(snapshot.rotations)
            ShiftRepository.calendarShifts.clear()
            val defMap = snapshot.definitions.associateBy { it.id }
            snapshot.shifts.forEach { (date, shifts) ->
                ShiftRepository.calendarShifts[date] = mutableStateListOf<AssignedShift>().apply { addAll(shifts.sortedChronologically(defMap)) }
            }
            ShiftRepository.workProfiles.clear(); ShiftRepository.workProfiles.addAll(snapshot.profiles)
            ShiftRepository.activeWorkProfileId = snapshot.activeWorkProfileId

            ShiftRepository.dayExtraInfoByProfile.clear()
            val resolvedExtrasByProfile = if (snapshot.extrasByProfile.isNotEmpty()) snapshot.extrasByProfile
                else if (snapshot.extras.isNotEmpty()) mapOf(snapshot.activeWorkProfileId to snapshot.extras) else emptyMap()
            resolvedExtrasByProfile.forEach { (pId, map) ->
                if (map.isNotEmpty()) ShiftRepository.dayExtraInfoMapFor(pId).putAll(map)
            }

            ShiftRepository.activeRotationIdByProfile.clear()
            val resolvedActiveRots = if (snapshot.activeRotationIdByProfile.isNotEmpty()) snapshot.activeRotationIdByProfile
                else if (snapshot.activeRotationId.isNotEmpty()) mapOf(snapshot.activeWorkProfileId to snapshot.activeRotationId) else emptyMap()
            ShiftRepository.activeRotationIdByProfile.putAll(resolvedActiveRots)

            ShiftRepository.defaultRotationIdByProfile.clear()
            val resolvedDefaultRots = if (snapshot.defaultRotationIdByProfile.isNotEmpty()) snapshot.defaultRotationIdByProfile
                else if (snapshot.defaultRotationId.isNotEmpty()) mapOf(snapshot.activeWorkProfileId to snapshot.defaultRotationId) else emptyMap()
            ShiftRepository.defaultRotationIdByProfile.putAll(resolvedDefaultRots)

            ShiftRepository.appPreferences = snapshot.preferences

            ShiftRepository.reperibilitaByProfile.clear()
            val resolvedOnCall = if (snapshot.onCallByProfile.isNotEmpty()) snapshot.onCallByProfile
                else if (snapshot.onCall != ReperibilitaConfig()) mapOf(snapshot.activeWorkProfileId to snapshot.onCall) else emptyMap()
            ShiftRepository.reperibilitaByProfile.putAll(resolvedOnCall)

            ShiftRepository.smartAlarmsByProfile.clear()
            val resolvedAlarms = if (snapshot.alarmsByProfile.isNotEmpty()) snapshot.alarmsByProfile
                else if (snapshot.alarms != SmartAlarmsConfig()) mapOf(snapshot.activeWorkProfileId to snapshot.alarms) else emptyMap()
            ShiftRepository.smartAlarmsByProfile.putAll(resolvedAlarms)

            ShiftRepository.enabledHolidayIds = snapshot.holidayIds
            ShiftRepository.statisticsRules.clear(); ShiftRepository.statisticsRules.addAll(snapshot.statisticsRules)
            ShiftRepository.dayTags.clear(); ShiftRepository.dayTags.addAll(snapshot.dayTags)
        }
        com.turni.calendario.ui.theme.TurniThemeState.restoreStoredMode(snapshot.preferences.themeMode)
        ShiftRepository.clearCalendarUndoHistory()
        ShiftRepository.notifyDataChanged()
    }

    fun restore(text: String): RestoreResult = try {
        val parsed = parse(text)
        LocalDatabaseStore.allowPersistenceAfterRestore()
        apply(parsed)
        RestoreResult(true, "Ripristino completato", parsed.shifts.values.sumOf { it.size }, parsed.definitions.size, parsed.rotations.size)
    } catch (e: Exception) {
        RestoreResult(false, "Backup non valido: ${e.localizedMessage}")
    }
}
