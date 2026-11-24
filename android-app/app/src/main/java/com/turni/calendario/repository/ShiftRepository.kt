package com.turni.calendario.repository

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.turni.calendario.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// Sequenza esatta di 28 giorni ricavata dagli screenshot reali dell'utente
val sequenceTurnoA = listOf(
    "mattina", "mattina", "pomeriggio", "pomeriggio", "pomeriggio", "notte", "notte",
    "smonto_notte", "riposo", "mattina", "mattina", "notte", "smonto_notte", "riposo",
    "pomeriggio", "pomeriggio", "notte", "notte", "smonto_notte", "mattina", "riposo",
    "notte", "notte", "smonto_notte", "riposo", "mattina", "pomeriggio", "mat_dom"
)

object ShiftRepository {

    // ID del profilo di lavoro attivo
    var activeWorkProfileId by mutableStateOf("principale")

    // Lista profili di lavoro
    val workProfiles = mutableStateListOf<WorkProfile>(
        WorkProfile(
            id = "principale",
            name = "Il mio lavoro",
            companyOrDepartment = "",
            weeklyHours = 0,
            minRestHoursBetweenShifts = 0,
            nightSurchargePercent = 35,
            holidaySurchargePercent = 50,
            nightHolidaySurchargePercent = 65,
            overtimeDaySurchargePercent = 25,
            overtimeNightSurchargePercent = 40,
            colorHex = 0xFF0284C7,
            iconKey = "work",
            isDefault = true,
            enableAdvancedCalc = false,
            maxConsecutiveWorkDays = 0
        )
    )

    val activeWorkProfile: WorkProfile
        get() = workProfiles.find { it.id == activeWorkProfileId }
            ?: workProfiles.firstOrNull()
            ?: WorkProfile("principale", "Il mio lavoro", weeklyHours = 0, minRestHoursBetweenShifts = 0, maxConsecutiveWorkDays = 0, isDefault = true)

    fun setActiveWorkProfile(profileId: String) {
        if (workProfiles.none { it.id == profileId }) return
        activeWorkProfileId = profileId
        for (i in workProfiles.indices) {
            val p = workProfiles[i]
            workProfiles[i] = p.copy(isDefault = p.id == profileId)
        }
        dayExtraInfoMapFor(profileId)
        notifyDataChanged()
    }

    fun addWorkProfile(profile: WorkProfile) {
        if (profile.isDefault) {
            for (i in workProfiles.indices) {
                workProfiles[i] = workProfiles[i].copy(isDefault = false)
            }
            activeWorkProfileId = profile.id
        }
        workProfiles.add(profile)
    }

    fun updateWorkProfile(profile: WorkProfile) {
        val index = workProfiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            if (profile.isDefault) {
                for (i in workProfiles.indices) {
                    if (i != index) {
                        workProfiles[i] = workProfiles[i].copy(isDefault = false)
                    }
                }
                activeWorkProfileId = profile.id
            }
            workProfiles[index] = profile
        }
    }

    fun deleteWorkProfile(profileId: String): Boolean {
        if (workProfiles.size <= 1) return false
        val index = workProfiles.indexOfFirst { it.id == profileId }
        if (index != -1) {
            val wasDefault = workProfiles[index].isDefault
            workProfiles.removeAt(index)
            if (wasDefault || activeWorkProfileId == profileId) {
                workProfiles[0] = workProfiles[0].copy(isDefault = true)
                activeWorkProfileId = workProfiles[0].id
            }
            val destination = workProfiles.firstOrNull { it.id == activeWorkProfileId } ?: workProfiles.first()
            for (i in shiftDefinitions.indices) {
                val definition = shiftDefinitions[i]
                if (definition.workProfileId == profileId) {
                    shiftDefinitions[i] = definition.copy(workProfileId = destination.id)
                }
            }
            notifyDataChanged()
            return true
        }
        return false
    }

    // Lista dei modelli di turno configurati (Modificabili, Aggiungibili, Eliminabili)
    val shiftDefinitions = mutableStateListOf<ShiftDefinition>()

    // Mappa delle assegnazioni sul calendario: LocalDate -> Lista di AssignedShift
    val calendarShifts = mutableStateMapOf<LocalDate, MutableList<AssignedShift>>()
    val minimumRestAlerts by androidx.compose.runtime.derivedStateOf {
        minimumRestWarnings(calendarShifts.values.flatMap{it.toList()},shiftDefinitions.toList(),workProfiles.toList())
    }
    val consecutiveShiftAlerts by androidx.compose.runtime.derivedStateOf {
        consecutiveShiftWarnings(
            calendarShifts.values.flatMap { it.toList() },
            shiftDefinitions.toList(),
            workProfiles.toList(),
        )
    }


    // Per-profile day extra info (overtime, travel, on-call override, notes, hide ex-shift)
    val dayExtraInfoByProfile = mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateMap<LocalDate, DayExtraInfo>>()

    fun dayExtraInfoReadOnly(profileId: String = activeWorkProfileId): Map<LocalDate, DayExtraInfo> =
        dayExtraInfoByProfile[profileId] ?: emptyMap()

    fun dayExtraInfoMapFor(profileId: String = activeWorkProfileId): androidx.compose.runtime.snapshots.SnapshotStateMap<LocalDate, DayExtraInfo> =
        dayExtraInfoByProfile.getOrPut(profileId) { mutableStateMapOf() }

    val dayExtraInfo: MutableMap<LocalDate, DayExtraInfo>
        get() = dayExtraInfoMapFor(activeWorkProfileId)

    val dayTags = mutableStateListOf<DayTag>()

    fun dayTagsFor(profileId: String = activeWorkProfileId): List<DayTag> =
        dayTags.filter { it.workProfileId == profileId }

    val activeDayTags: List<DayTag>
        get() = dayTagsFor(activeWorkProfileId)

    fun saveDayTag(tag: DayTag) {
        val clean = tag.copy(name = tag.name.trim().replace(Regex("\\s+"), " "))
        val updated = dayTags.filterNot { it.id == clean.id } + clean
        val allExtras = dayExtraInfoByProfile.values.flatMap { it.entries }.associate { it.key to it.value }
        validateDayTags(updated, allExtras)
        val index = dayTags.indexOfFirst { it.id == clean.id }
        if (index < 0) dayTags.add(clean) else dayTags[index] = clean
        notifyDataChanged()
    }

    fun deleteUnusedDayTag(id: String) {
        val tagProfileId = dayTags.firstOrNull { it.id == id }?.workProfileId
        require(dayExtraInfoByProfile.values.all { map -> map.values.none { id in it.tagIds } }) {
            "Archivia l’etichetta per conservare i giorni già segnati"
        }
        dayTags.removeAll { it.id == id }
        // Undo must never revive a reference to a deliberately deleted definition.
        for (index in undoHistory.indices) {
            val snapshot = undoHistory[index]
            undoHistory[index] = snapshot.copy(
                extrasByProfile = snapshot.extrasByProfile.mapValues { (pId, extrasMap) ->
                    if (tagProfileId != null && pId != tagProfileId) extrasMap
                    else extrasMap.mapValues { (_, extra) -> extra.copy(tagIds = extra.tagIds.filterNot { it == id }) }
                }
            )
        }
        notifyDataChanged()
    }

    fun saveDayAnnotations(date: LocalDate, notes: String, tagIds: List<String>, profileId: String = activeWorkProfileId) {
        val known = dayTagsFor(profileId).map { it.id }.toSet()
        require(tagIds.all { it in known }) { "Un’etichetta non è più disponibile" }
        val previous = getDayExtraInfo(date, profileId)
        val updated = previous.copy(notes = notes.take(500), tagIds = tagIds.distinct())
        if (updated == previous) return
        saveSnapshot()
        dayExtraInfoMapFor(profileId)[date] = updated
        notifyDataChanged()
    }

    // Per-profile on-call configuration
    val reperibilitaByProfile = mutableStateMapOf<String, ReperibilitaConfig>()

    var reperibilitaConfig: ReperibilitaConfig
        get() = reperibilitaConfigFor(activeWorkProfileId)
        set(value) { reperibilitaByProfile[activeWorkProfileId] = value }

    fun reperibilitaConfigFor(profileId: String = activeWorkProfileId): ReperibilitaConfig =
        reperibilitaByProfile[profileId] ?: ReperibilitaConfig()

    fun setReperibilitaConfigFor(profileId: String, config: ReperibilitaConfig) {
        reperibilitaByProfile[profileId] = config
    }

    fun updateReperibilitaConfig(
        profileId: String = activeWorkProfileId,
        update: (ReperibilitaConfig) -> ReperibilitaConfig
    ) {
        val current = reperibilitaConfigFor(profileId)
        val updated = update(current)
        if (updated == current) return

        val autoShiftIds: Set<String> = when (updated.mode) {
            ReperibilitaMode.TURNO_SPECIFICO -> updated.shiftSpecificIds.toSet()
            ReperibilitaMode.ALTERNATA_STAGIONALE -> updated.seasonal.winterShiftIds.toSet()
            ReperibilitaMode.MANUALE -> emptySet()
        }

        if (autoShiftIds.isNotEmpty()) {
            val extrasMap = dayExtraInfoByProfile[profileId]
            if (extrasMap != null) {
                val defsById = shiftDefinitions.associateBy { it.id }
                for ((date, assignedList) in calendarShifts) {
                    val extra = extrasMap[date] ?: continue
                    if (extra.isOnCallManual != false) continue
                    val matchesAutoShift = assignedList.any { assigned ->
                        defsById[assigned.shiftDefId]?.workProfileId == profileId &&
                            assigned.shiftDefId in autoShiftIds
                    }
                    if (matchesAutoShift) {
                        extrasMap[date] = extra.copy(isOnCallManual = null)
                    }
                }
            }
        }

        reperibilitaByProfile[profileId] = updated
        notifyDataChanged()
    }

    // Per-profile smart alarms configuration
    val smartAlarmsByProfile = mutableStateMapOf<String, SmartAlarmsConfig>()

    var smartAlarmsConfig: SmartAlarmsConfig
        get() = smartAlarmsConfigFor(activeWorkProfileId)
        set(value) { smartAlarmsByProfile[activeWorkProfileId] = value }

    fun smartAlarmsConfigFor(profileId: String = activeWorkProfileId): SmartAlarmsConfig =
        smartAlarmsByProfile[profileId] ?: SmartAlarmsConfig()

    fun setSmartAlarmsConfigFor(profileId: String, config: SmartAlarmsConfig) {
        smartAlarmsByProfile[profileId] = config
    }

    /** Apply only the edited field to the latest saved rule, never a stale UI snapshot. */
    fun updateSmartAlarmRule(id: String, update: (ShiftAlarmRule) -> ShiftAlarmRule) =
        updateSmartAlarmRule(id, activeWorkProfileId, update)

    fun updateSmartAlarmRule(id: String, profileId: String, update: (ShiftAlarmRule) -> ShiftAlarmRule) {
        val current = smartAlarmsConfigFor(profileId)
        val previous = current.rules.firstOrNull { it.shiftDefId == id }
            ?: ShiftAlarmRule(id, isEnabled = false, customTime = if (id == "reperibilita") "20:00" else null)
        val changed = update(previous).copy(shiftDefId = id)
        setSmartAlarmsConfigFor(
            profileId,
            current.copy(
                rules = if (current.rules.any { it.shiftDefId == id })
                    current.rules.map { if (it.shiftDefId == id) changed else it } else current.rules + changed
            )
        )
        notifyDataChanged()
    }

    // Preferenze dell'applicazione (Gestite in "Altro")
    var appPreferences by mutableStateOf(AppPreferences())

    // Rotazioni cicliche disponibili
    val rotations = mutableStateListOf<ShiftRotation>()

    val statisticsRules = mutableStateListOf<StatisticsRule>()

    fun statisticsRulesFor(profileId: String = activeWorkProfileId): List<StatisticsRule> =
        statisticsRules.filter { it.workProfileId == profileId }

    val activeStatisticsRules: List<StatisticsRule>
        get() = statisticsRulesFor(activeWorkProfileId)

    fun saveStatisticsRule(rule: StatisticsRule) {
        val byId = shiftDefinitions.associateBy { it.id }
        val normalized = byId[rule.displayedShiftId]?.workProfileId?.let { rule.copy(workProfileId = it) } ?: rule
        val updated = statisticsRules.filterNot { it.id == normalized.id } + normalized
        validateStatisticsRules(updated, shiftDefinitions.toList())
        statisticsRules.clear(); statisticsRules.addAll(updated); notifyDataChanged()
    }

    fun removeStatisticsRule(id: String) { statisticsRules.removeAll { it.id == id }; notifyDataChanged() }

    val activeRotationIdByProfile = mutableStateMapOf<String, String>()
    val defaultRotationIdByProfile = mutableStateMapOf<String, String>()

    var activeRotationId: String
        get() = activeRotationIdByProfile[activeWorkProfileId] ?: ""
        set(value) { activeRotationIdByProfile[activeWorkProfileId] = value }

    fun activeRotationIdFor(profileId: String = activeWorkProfileId): String =
        activeRotationIdByProfile[profileId] ?: ""

    val activeRotation: ShiftRotation?
        get() = rotations.firstOrNull { it.workProfileId == activeWorkProfileId && it.id == activeRotationId }
            ?: rotations.firstOrNull { it.workProfileId == activeWorkProfileId }

    var defaultRotationId: String
        get() = defaultRotationIdByProfile[activeWorkProfileId] ?: ""
        set(value) { defaultRotationIdByProfile[activeWorkProfileId] = value }

    fun defaultRotationIdFor(profileId: String = activeWorkProfileId): String =
        defaultRotationIdByProfile[profileId] ?: ""

    // Giorno selezionato proveniente dal tocco su un Widget Android
    var selectedDateFromWidget by mutableStateOf<LocalDate?>(null)

    // Listener per aggiornamento istantaneo widget e observer
    var onDataChangedListener: (() -> Unit)? = null

    fun notifyDataChanged() {
        try {
            onDataChangedListener?.invoke()
        } catch (_: Exception) {}
    }

    fun setDefaultRotation(rotationId: String, profileId: String = activeWorkProfileId) {
        defaultRotationIdByProfile[profileId] = rotationId
        if (rotationId.isNotEmpty()) {
            activeRotationIdByProfile[profileId] = rotationId
        }
    }

    fun toggleDefaultRotation(rotationId: String, profileId: String = activeWorkProfileId): Boolean {
        val currentDefault = defaultRotationIdFor(profileId)
        return if (currentDefault == rotationId) {
            defaultRotationIdByProfile[profileId] = ""
            false
        } else {
            defaultRotationIdByProfile[profileId] = rotationId
            activeRotationIdByProfile[profileId] = rotationId
            true
        }
    }

    fun getRotationsSortedByDefault(profileId: String = activeWorkProfileId): List<ShiftRotation> {
        val profileRotations = rotations.filter { it.workProfileId == profileId }
        val defId = defaultRotationIdFor(profileId)
        if (defId.isEmpty()) return profileRotations
        return profileRotations.sortedByDescending { it.id == defId }
    }

    // Festività nazionali, religiose e CCNL configurabili
    var enabledHolidayIds by mutableStateOf(
        HolidaysCatalog.allHolidays.filter { it.isDefaultEnabled }.map { it.id }.toSet()
    )

    fun holidaysForDate(date:LocalDate):List<HolidayItem> {
        if(!appPreferences.showHolidays)return emptyList()
        return HolidaysCatalog.allHolidays.filter { it.id in enabledHolidayIds && it.getDateForYear(date.year)==date }
    }

    fun isHoliday(date:LocalDate):HolidayItem? = holidaysForDate(date).firstOrNull()

    fun toggleHoliday(holidayId: String) {
        enabledHolidayIds = if (enabledHolidayIds.contains(holidayId)) {
            enabledHolidayIds - holidayId
        } else {
            enabledHolidayIds + holidayId
        }
    }

    fun resetHolidaysToDefault() {
        enabledHolidayIds = HolidaysCatalog.allHolidays.filter { it.isDefaultEnabled }.map { it.id }.toSet()
    }

    // Set di date per retrocompatibilità
    val holidays: Set<LocalDate>
        get() {
            val set = mutableSetOf<LocalDate>()
            for (year in 2026..2027) {
                for (h in HolidaysCatalog.allHolidays) {
                    if (enabledHolidayIds.contains(h.id)) {
                        set.add(h.getDateForYear(year))
                    }
                }
            }
            return set
        }

    // Identity is accepted only after Google consent and verified userinfo, never from a default profile.
    var googleAccountProfile by mutableStateOf<GoogleAccountProfile?>(null)
        private set
    fun setVerifiedGoogleAccount(email:String,displayName:String){
        googleAccountProfile=GoogleAccountProfile(email=email,displayName=displayName,isConnected=true)
    }
    fun disconnectGoogleAccount(){googleAccountProfile=null}

    // Opzioni correnti per Esportazione PDF & Stampa
    var pdfExportOptions by mutableStateOf(PdfExportOptions())

    // Data di inizio del ciclo continuo di 28 giorni (Lunedì 28 Settembre 2026 inizia con Mattina)
    val cycleReferenceDate: LocalDate = LocalDate.of(2026, 9, 28)

    init {
        // Clean initial state: no hardcoded demo shifts or rotations.
    }

    /**
     * Calcola il turno pianificato dal ciclo di rotazione per una data specifica.
     * Gestisce dinamicamente qualsiasi lunghezza del ciclo (7, 14, 21, 28, 35 gg, ecc.).
     */
    fun getPlannedRotationShift(
        date: LocalDate,
        rotationId: String = activeRotationId,
        profileId: String = activeWorkProfileId
    ): ShiftDefinition? = synchronized(this) {
        val profileRotations = rotations.filter { it.workProfileId == profileId }
        val targetId = if (rotationId.isNotBlank()) rotationId else activeRotationIdFor(profileId)
        val rot = profileRotations.firstOrNull { it.id == targetId }
            ?: rotations.firstOrNull { it.id == targetId }
            ?: profileRotations.firstOrNull()
            ?: return null
        if (rot.daySequence.isEmpty()) return null
        val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(cycleReferenceDate, date)
        val cycleIndex = Math.floorMod(daysDiff, rot.daySequence.size.toLong()).toInt()
        val defId = rot.daySequence[cycleIndex]
        return getShiftDefinition(defId)
    }

    // CRUD per Modelli Turno
    fun saveShiftDefinition(def: ShiftDefinition) = synchronized(this) {
        val existingIndex = shiftDefinitions.indexOfFirst { it.id == def.id }
        if (existingIndex >= 0) {
            shiftDefinitions[existingIndex] = def
        } else {
            shiftDefinitions.add(def)
        }
        val sorted = shiftDefinitions.toList().sortedChronologically()
        shiftDefinitions.clear()
        shiftDefinitions.addAll(sorted)
        val byId = shiftDefinitions.associateBy { it.id }
        val cleaned = statisticsRules.map { rule ->
            rule.copy(originalShiftIds = rule.originalShiftIds.filter { byId[it]?.workProfileId == byId[rule.displayedShiftId]?.workProfileId }.toSet())
        }.filter { it.originalShiftIds.isNotEmpty() }
        statisticsRules.clear(); statisticsRules.addAll(cleaned)
    }

    fun duplicateShiftToProfile(definitionId: String, targetProfileId: String): ShiftDefinition? = synchronized(this) {
        val source = shiftDefinitions.firstOrNull { it.id == definitionId } ?: return null
        val targetExisting = shiftDefinitions.filter { it.workProfileId == targetProfileId }
        val isDuplicate = targetExisting.any { existing ->
            existing.name.trim().equals(source.name.trim(), ignoreCase = true) &&
                existing.isAllDay == source.isAllDay &&
                existing.startTime == source.startTime &&
                existing.endTime == source.endTime
        }
        if (isDuplicate) return null
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            workProfileId = targetProfileId
        )
        shiftDefinitions.add(copy)
        val sorted = shiftDefinitions.toList().sortedChronologically()
        shiftDefinitions.clear()
        shiftDefinitions.addAll(sorted)
        notifyDataChanged()
        return copy
    }

    fun duplicateAllShiftsToProfile(sourceProfileId: String, targetProfileId: String): Int = synchronized(this) {
        if (sourceProfileId == targetProfileId) return 0
        val sourceDefs = shiftDefinitions.filter { it.workProfileId == sourceProfileId }
        var count = 0
        for (source in sourceDefs) {
            val targetExisting = shiftDefinitions.filter { it.workProfileId == targetProfileId }
            val isDuplicate = targetExisting.any { existing ->
                existing.name.trim().equals(source.name.trim(), ignoreCase = true) &&
                    existing.isAllDay == source.isAllDay &&
                    existing.startTime == source.startTime &&
                    existing.endTime == source.endTime
            }
            if (!isDuplicate) {
                shiftDefinitions.add(source.copy(id = UUID.randomUUID().toString(), workProfileId = targetProfileId))
                count++
            }
        }
        if (count > 0) {
            val sorted = shiftDefinitions.toList().sortedChronologically()
            shiftDefinitions.clear()
            shiftDefinitions.addAll(sorted)
            notifyDataChanged()
        }
        return count
    }

    fun deleteShiftDefinition(id: String) = synchronized(this) {
        shiftDefinitions.removeAll { it.id == id }
        val remainingRules = statisticsRules.filterNot { it.displayedShiftId == id }
            .map { it.copy(originalShiftIds = it.originalShiftIds - id) }.filter { it.originalShiftIds.isNotEmpty() }
        statisticsRules.clear(); statisticsRules.addAll(remainingRules)
        calendarShifts.keys.toList().forEach { date ->
            val retained = getShiftsForDate(date).filter { it.shiftDefId != id }.map { assigned ->
                if (assigned.originalShiftDefId == id) assigned.copy(originalShiftDefId = null, isModifiedFromRotation = false)
                else assigned
            }
            calendarShifts[date] = mutableStateListOf<AssignedShift>().apply { addAll(retained) }
            synchronizeAssignmentExtras(date)
        }
        for (i in rotations.indices) {
            val rotation = rotations[i]
            rotations[i] = rotation.copy(daySequence = rotation.daySequence.map { if (it == id) "" else it })
        }
        reperibilitaByProfile.keys.toList().forEach { pId ->
            val cfg = reperibilitaByProfile[pId] ?: return@forEach
            reperibilitaByProfile[pId] = cfg.copy(
                shiftSpecificIds = cfg.shiftSpecificIds.filterNot { it == id },
                seasonal = cfg.seasonal.copy(winterShiftIds = cfg.seasonal.winterShiftIds.filterNot { it == id })
            )
        }
        smartAlarmsByProfile.keys.toList().forEach { pId ->
            val cfg = smartAlarmsByProfile[pId] ?: return@forEach
            smartAlarmsByProfile[pId] = cfg.copy(rules = cfg.rules.filterNot { it.shiftDefId == id })
        }
        // Undo must never resurrect assignments whose definition no longer exists.
        undoHistory.clear()
        notifyDataChanged()
    }

    fun getShiftDefinition(id: String): ShiftDefinition? = synchronized(this) {
        return shiftDefinitions.firstOrNull { it.id == id }
    }

    // Cronologia modifiche per funzione Annulla (Undo)
    private data class CalendarSnapshot(
        val shifts: Map<LocalDate, List<AssignedShift>>,
        val extrasByProfile: Map<String, Map<LocalDate, DayExtraInfo>>
    )
    private val undoHistory = mutableListOf<CalendarSnapshot>()

    fun saveSnapshot() {
        val snapshot = calendarShifts.mapValues { (_, list) -> list.toList() }
        val extrasSnap = dayExtraInfoByProfile.mapValues { (_, map) -> map.toMap() }
        undoHistory.add(CalendarSnapshot(snapshot, extrasSnap))
        if (undoHistory.size > 40) undoHistory.removeAt(0)
    }

    fun canUndo(): Boolean = undoHistory.isNotEmpty()

    /** A full database restore changes model IDs; calendar-only undo must not cross that boundary. */
    fun clearCalendarUndoHistory() { undoHistory.clear() }

    fun undo(): Boolean {
        if (undoHistory.isEmpty()) return false
        val last = undoHistory.removeAt(undoHistory.size - 1)
        calendarShifts.clear()
        last.shifts.forEach { (date, list) ->
            val mList = mutableStateListOf<AssignedShift>()
            mList.addAll(list)
            calendarShifts[date] = mList
        }
        dayExtraInfoByProfile.clear()
        last.extrasByProfile.forEach { (pId, map) ->
            val mMap = mutableStateMapOf<LocalDate, DayExtraInfo>()
            mMap.putAll(map)
            dayExtraInfoByProfile[pId] = mMap
        }
        notifyDataChanged()
        return true
    }

    private fun sortShiftsForDate(list: List<AssignedShift>): List<AssignedShift> {
        val defs = shiftDefinitions.associateBy { it.id }
        return list.sortedChronologically(defs)
    }

    // Gestione Turni nel Calendario
    fun getShiftsForDate(date: LocalDate): List<AssignedShift> {
        val raw = calendarShifts[date] ?: return emptyList()
        return sortShiftsForDate(raw)
    }

    fun getShiftsForDate(date: LocalDate, profileId: String): List<AssignedShift> {
        val defs = shiftDefinitions.associateBy { it.id }
        return getShiftsForDate(date).filter { defs[it.shiftDefId]?.workProfileId == profileId }
    }

    fun assignShift(
        date: LocalDate,
        shiftDefId: String,
        notes: String = "",
        isOvertime: Boolean = false,
        overtimeHours: Float = 0f,
        isModified: Boolean = false,
        isFromRotation: Boolean = false
    ) {
        val currentList = calendarShifts[date]?.toList() ?: emptyList()
        val profileId = getShiftDefinition(shiftDefId)?.workProfileId ?: activeWorkProfileId
        val ownShifts = currentList.filter { getShiftDefinition(it.shiftDefId)?.workProfileId == profileId }
        val prev = ownShifts.firstOrNull()
        val isCleared = dayExtraInfoReadOnly(profileId)[date]?.rotationCleared == true
        val isFromRotationOrigin = !isCleared && (isFromRotation || (prev != null && (prev.isFromRotation || prev.isModifiedFromRotation)))

        val origId: String?
        val modifiedFromRot: Boolean
        val fromRotation: Boolean

        if (isFromRotation) {
            fromRotation = true
            modifiedFromRot = false
            origId = null
        } else if (isFromRotationOrigin && prev != null) {
            val baseRotationDefId = prev.originalShiftDefId ?: prev.shiftDefId
            if (shiftDefId == baseRotationDefId) {
                fromRotation = true
                modifiedFromRot = false
                origId = null
            } else {
                fromRotation = false
                modifiedFromRot = true
                origId = baseRotationDefId
            }
        } else {
            fromRotation = false
            modifiedFromRot = false
            origId = null
        }

        val isAbsenceOrSpecial = shiftDefId in listOf("ferie", "malattia", "donazione", "riposo")
        val newList = mutableStateListOf<AssignedShift>()
        newList.addAll(currentList.filter { getShiftDefinition(it.shiftDefId)?.workProfileId != profileId })
        if (!modifiedFromRot && !isAbsenceOrSpecial && !isFromRotation) {
            newList.addAll(ownShifts)
        }

        val newEntry = AssignedShift(
            id = UUID.randomUUID().toString(),
            date = date,
            shiftDefId = shiftDefId,
            originalShiftDefId = origId,
            isModifiedFromRotation = modifiedFromRot,
            isFromRotation = fromRotation,
            notes = notes,
            isOvertime = isOvertime,
            overtimeHours = overtimeHours
        )
        newList.add(newEntry)
        val sorted = sortShiftsForDate(newList)
        calendarShifts[date] = mutableStateListOf<AssignedShift>().apply { addAll(sorted) }
        if (shiftDefId.isNotBlank() && shiftDefId != "empty" && shiftDefId != "eraser" && getShiftDefinition(shiftDefId) != null) {
            val currentExtra = getDayExtraInfo(date, profileId)
            if (currentExtra.isOnCallManual == false) {
                dayExtraInfoMapFor(profileId)[date] = currentExtra.copy(isOnCallManual = null)
            }
        }
        notifyDataChanged()
    }

    /**
     * Aggiunge un turno secondario alla data (es. turno doppio, straordinario, reperibilità)
     */
    fun addSecondaryShift(date: LocalDate, shiftDefId: String, notes: String = "") {
        if (getShiftDefinition(shiftDefId) == null) return
        saveSnapshot()
        addSecondaryWithoutSnapshot(date, shiftDefId, notes)
        notifyDataChanged()
    }

    private fun addSecondaryWithoutSnapshot(date: LocalDate, shiftDefId: String, notes: String = "") {
        val definition = getShiftDefinition(shiftDefId) ?: return
        val profileId = definition.workProfileId
        val currentList = calendarShifts[date]?.toList() ?: emptyList()
        val newList = mutableListOf<AssignedShift>()
        newList.addAll(currentList)
        val newEntry = AssignedShift(
            id = UUID.randomUUID().toString(),
            date = date,
            shiftDefId = shiftDefId,
            notes = notes,
            isFromRotation = false,
            isModifiedFromRotation = false
        )
        newList.add(newEntry)
        val sorted = sortShiftsForDate(newList)
        calendarShifts[date] = mutableStateListOf<AssignedShift>().apply { addAll(sorted) }
        val currentExtra = getDayExtraInfo(date, profileId)
        if (currentExtra.isOnCallManual == false) {
            dayExtraInfoMapFor(profileId)[date] = currentExtra.copy(isOnCallManual = null)
        }
    }

    /**
     * Sostituisce il turno principale di una data salvando lo storico per Undo.
     * Regola tassativa: l'indicatore 'ex turno' compare ESCLUSIVAMENTE se il turno
     * preesistente apparteneva a un ciclo di rotazione programmato.
     * Se l'utente inserisce turni singoli manuali, l'ex-turno NON deve apparire mai.
     */
    fun replaceShift(date: LocalDate, newShiftDefId: String) {
        if (getShiftDefinition(newShiftDefId) == null) return
        saveSnapshot()
        replacePrimaryWithoutSnapshot(date, newShiftDefId)
        notifyDataChanged()
    }

    private fun replacePrimaryWithoutSnapshot(date: LocalDate, newShiftDefId: String) {
        val definition = getShiftDefinition(newShiftDefId) ?: return
        val profileId = definition.workProfileId
        val current = getShiftsForDate(date).toList()
        val primaryIndex = current.indexOfFirst { getShiftDefinition(it.shiftDefId)?.workProfileId == profileId }
        val previous = current.getOrNull(primaryIndex)
        val original = previous?.takeIf { it.isFromRotation || it.isModifiedFromRotation }?.let { it.originalShiftDefId ?: it.shiftDefId }
        val replacement = AssignedShift(
            id = UUID.randomUUID().toString(), date = date, shiftDefId = newShiftDefId,
            isFromRotation = original == newShiftDefId,
            isModifiedFromRotation = original != null && original != newShiftDefId,
            originalShiftDefId = original?.takeIf { it != newShiftDefId }
        )
        val updated = mutableListOf<AssignedShift>().apply { addAll(current) }
        if (primaryIndex < 0) updated.add(replacement) else updated[primaryIndex] = replacement
        val sorted = sortShiftsForDate(updated)
        calendarShifts[date] = mutableStateListOf<AssignedShift>().apply { addAll(sorted) }
        val currentExtra = getDayExtraInfo(date, profileId)
        dayExtraInfoMapFor(profileId)[date] = currentExtra.copy(
            hideExShift = false,
            isOnCallManual = if (currentExtra.isOnCallManual == false) null else currentExtra.isOnCallManual
        )
        synchronizeAssignmentExtras(date, profileId)
    }

    /**
     * Assegna un turno a un intervallo di date in blocco (da startDate a endDate)
     */
    fun applyShiftRange(shiftDefId: String, startDate: LocalDate, endDate: LocalDate, addSecondary: Boolean = false) {
        val erasing = shiftDefId == "eraser" || shiftDefId == "empty"
        if (!erasing && getShiftDefinition(shiftDefId) == null) return
        saveSnapshot()
        val start = minOf(startDate, endDate)
        val end = maxOf(startDate, endDate)
        var current = start
        while (!current.isAfter(end)) {
            if (erasing) clearWorkDayWithoutSnapshot(current, activeWorkProfileId)
            else if (addSecondary) addSecondaryWithoutSnapshot(current, shiftDefId)
            else replacePrimaryWithoutSnapshot(current, shiftDefId)
            current = current.plusDays(1)
        }
        notifyDataChanged()
    }

    /**
     * Modalità Pennello (Paint Mode):
     * Se tocchi un giorno con un turno selezionato:
     * - Se il turno è già presente, lo rimuove e ripristina eventualmente il turno originario di rotazione.
     * - Se è diverso, lo sostituisce salvando il turno originario SOLO se proveniva da una rotazione.
     */
    fun toggleShiftOnDate(date: LocalDate, shiftDefId: String) {
        if (shiftDefId == "eraser" || shiftDefId == "empty") {
            clearWorkDayShifts(date)
            return
        }
        val definition = getShiftDefinition(shiftDefId) ?: return
        val existing = getShiftsForDate(date).firstOrNull {
            it.shiftDefId == shiftDefId && getShiftDefinition(it.shiftDefId)?.workProfileId == definition.workProfileId
        }
        if (existing != null) removeAssignedShift(date, existing.id) else replaceShift(date, shiftDefId)
    }

    fun saveRotation(updated: ShiftRotation) = synchronized(this) {
        val normalized = updated.copy(totalDays = updated.daySequence.size)
        val idx = rotations.indexOfFirst { it.id == normalized.id }
        if (idx >= 0) {
            rotations[idx] = normalized
        } else {
            rotations.add(normalized)
        }
    }

    fun deleteRotation(id: String) = synchronized(this) {
        val targetProfileId = rotations.firstOrNull { it.id == id }?.workProfileId ?: activeWorkProfileId
        val profileRotations = rotations.filter { it.workProfileId == targetProfileId }
        if (profileRotations.size > 1 || rotations.size > 1) {
            rotations.removeAll { it.id == id }
            if (activeRotationIdFor(targetProfileId) == id) {
                activeRotationIdByProfile[targetProfileId] = rotations.firstOrNull { it.workProfileId == targetProfileId }?.id ?: ""
            }
            if (defaultRotationIdFor(targetProfileId) == id) {
                defaultRotationIdByProfile[targetProfileId] = ""
            }
        }
    }

    fun removeAssignedShift(date: LocalDate, assignedId: String, clearOnCall:Boolean=false) {
        saveSnapshot()
        val current = calendarShifts[date]?.toList() ?: emptyList()
        val filtered = current.filter { it.id != assignedId }
        val newList = mutableStateListOf<AssignedShift>()
        newList.addAll(filtered)
        calendarShifts[date] = newList

        val anyTrasferta = newList.any { it.isTrasferta }
        val extra = getDayExtraInfo(date)
        val isNowEmpty = newList.isEmpty()
        dayExtraInfo[date] = extra.copy(
            isOnCallManual = if(clearOnCall)false else extra.isOnCallManual,
            isTrasferta = anyTrasferta,
            overtimeHours = newList.sumOf { it.overtimeHours.toDouble() }.toFloat(),
            actualEndTime = newList.firstOrNull()?.actualEndTime,
            rotationCleared = if (isNowEmpty) true else extra.rotationCleared,
            hideExShift = if (isNowEmpty) true else extra.hideExShift
        )
        notifyDataChanged()
    }

    fun toggleShiftTrasferta(date: LocalDate, assignedShiftId: String) {
        saveSnapshot()
        val current = calendarShifts[date]?.toList() ?: return
        val newList = mutableStateListOf<AssignedShift>()
        current.forEach { shift ->
            if (shift.id == assignedShiftId) {
                newList.add(shift.copy(isTrasferta = !shift.isTrasferta))
            } else {
                newList.add(shift)
            }
        }
        calendarShifts[date] = newList
        val anyTrasferta = newList.any { it.isTrasferta }
        val extra = getDayExtraInfo(date)
        dayExtraInfo[date] = extra.copy(isTrasferta = anyTrasferta)
        notifyDataChanged()
    }

    fun setShiftTrasferta(date: LocalDate, assignedShiftId: String, isTrasferta: Boolean) {
        saveSnapshot()
        val current = calendarShifts[date]?.toList() ?: return
        val newList = mutableStateListOf<AssignedShift>()
        current.forEach { shift ->
            if (shift.id == assignedShiftId) {
                newList.add(shift.copy(isTrasferta = isTrasferta))
            } else {
                newList.add(shift)
            }
        }
        calendarShifts[date] = newList
        val anyTrasferta = newList.any { it.isTrasferta }
        val extra = getDayExtraInfo(date)
        dayExtraInfo[date] = extra.copy(isTrasferta = anyTrasferta)
        notifyDataChanged()
    }

    fun setShiftOvertime(date: LocalDate, assignedShiftId: String, overtimeHours: Float) {
        saveSnapshot()
        val current = calendarShifts[date]?.toList() ?: return
        val newList = mutableStateListOf<AssignedShift>()
        val clampedHours = overtimeHours.coerceAtLeast(0f)
        current.forEach { shift ->
            if (shift.id == assignedShiftId) {
                newList.add(shift.copy(isOvertime = clampedHours > 0f, overtimeHours = clampedHours))
            } else {
                newList.add(shift)
            }
        }
        calendarShifts[date] = newList
        val totalOvertime = newList.sumOf { it.overtimeHours.toDouble() }.toFloat()
        val extra = getDayExtraInfo(date)
        dayExtraInfo[date] = extra.copy(overtimeHours = totalOvertime)
        notifyDataChanged()
    }

    fun setShiftActualEndTime(date: LocalDate, assignedShiftId: String, actualEndTime: String?, overtimeHours: Float) {
        saveSnapshot()
        val current = calendarShifts[date]?.toList() ?: return
        val newList = mutableStateListOf<AssignedShift>()
        val clampedHours = overtimeHours.coerceAtLeast(0f)
        current.forEach { shift ->
            if (shift.id == assignedShiftId) {
                newList.add(
                    shift.copy(
                        actualEndTime = actualEndTime,
                        isOvertime = clampedHours > 0f,
                        overtimeHours = clampedHours
                    )
                )
            } else {
                newList.add(shift)
            }
        }
        calendarShifts[date] = newList
        val totalOvertime = newList.sumOf { it.overtimeHours.toDouble() }.toFloat()
        val extra = getDayExtraInfo(date)
        dayExtraInfo[date] = extra.copy(overtimeHours = totalOvertime, actualEndTime = actualEndTime)
        notifyDataChanged()
    }

    fun setShiftNotes(date: LocalDate, assignedShiftId: String, notes: String) {
        saveSnapshot()
        val current = calendarShifts[date]?.toList() ?: return
        val newList = mutableStateListOf<AssignedShift>()
        current.forEach { shift ->
            if (shift.id == assignedShiftId) {
                newList.add(shift.copy(notes = notes))
            } else {
                newList.add(shift)
            }
        }
        calendarShifts[date] = newList
        notifyDataChanged()
    }

    fun setShiftExtraDetails(
        date: LocalDate,
        assignedShiftId: String,
        actualEndTime: String?,
        overtimeHours: Float,
        notes: String,
        isTrasferta: Boolean? = null,
        permissionHours: Float? = null,
        actualStartTime: String? = null,
        startOvertimeHours: Float? = null,
        startPermissionHours: Float? = null,
        endOvertimeHours: Float? = null,
        endPermissionHours: Float? = null
    ) {
        saveSnapshot()
        val current = calendarShifts[date]?.toList() ?: return
        val newList = mutableStateListOf<AssignedShift>()
        val clampedLegacyOt = if (overtimeHours.isFinite()) overtimeHours.coerceAtLeast(0f) else 0f
        val hasSplitOt = startOvertimeHours != null || endOvertimeHours != null
        val resolvedStartOt = if (hasSplitOt) (startOvertimeHours ?: 0f).let { if (it.isFinite()) it.coerceAtLeast(0f) else 0f } else 0f
        val resolvedEndOt = if (hasSplitOt) (endOvertimeHours ?: 0f).let { if (it.isFinite()) it.coerceAtLeast(0f) else 0f } else clampedLegacyOt
        val totalOvertime = resolvedStartOt + resolvedEndOt

        current.forEach { shift ->
            if (shift.id == assignedShiftId) {
                val rawLegacyPerm = permissionHours ?: shift.permissionHours
                val clampedLegacyPerm = if (rawLegacyPerm.isFinite()) rawLegacyPerm.coerceAtLeast(0f) else 0f
                val hasSplitPerm = startPermissionHours != null || endPermissionHours != null
                val resolvedStartPerm = if (hasSplitPerm) (startPermissionHours ?: 0f).let { if (it.isFinite()) it.coerceAtLeast(0f) else 0f } else 0f
                val resolvedEndPerm = if (hasSplitPerm) (endPermissionHours ?: 0f).let { if (it.isFinite()) it.coerceAtLeast(0f) else 0f } else clampedLegacyPerm
                val totalPermission = resolvedStartPerm + resolvedEndPerm

                newList.add(
                    shift.copy(
                        actualStartTime = actualStartTime ?: if (hasSplitOt || hasSplitPerm) null else shift.actualStartTime,
                        actualEndTime = actualEndTime,
                        isOvertime = totalOvertime > 0f,
                        overtimeHours = totalOvertime,
                        notes = notes,
                        isTrasferta = isTrasferta ?: shift.isTrasferta,
                        permissionHours = totalPermission,
                        startOvertimeHours = resolvedStartOt,
                        startPermissionHours = resolvedStartPerm,
                        endOvertimeHours = resolvedEndOt,
                        endPermissionHours = resolvedEndPerm
                    )
                )
            } else {
                newList.add(shift)
            }
        }
        calendarShifts[date] = newList
        val sumOvertime = newList.sumOf { it.overtimeHours.toDouble() }.toFloat()
        val anyTrasferta = newList.any { it.isTrasferta }
        val extra = getDayExtraInfo(date)
        dayExtraInfo[date] = extra.copy(
            overtimeHours = sumOvertime,
            actualEndTime = actualEndTime,
            isTrasferta = anyTrasferta
        )
        notifyDataChanged()
    }

    fun hasTrasfertaForDate(date: LocalDate): Boolean {
        return calendarShifts[date]?.any { it.isTrasferta } == true || dayExtraInfoReadOnly()[date]?.isTrasferta == true
    }

    private fun synchronizeAssignmentExtras(date: LocalDate, profileId: String = activeWorkProfileId) {
        val shifts = getShiftsForDate(date)
        dayExtraInfoMapFor(profileId)[date] = getDayExtraInfo(date, profileId).copy(
            overtimeHours = shifts.sumOf { it.overtimeHours.toDouble() }.toFloat(),
            isTrasferta = shifts.any { it.isTrasferta },
            actualEndTime = shifts.firstOrNull()?.actualEndTime
        )
    }

    fun clearDayShifts(date: LocalDate) {
        saveSnapshot()
        clearDayShiftsWithoutSnapshot(date)
        notifyDataChanged()
    }

    fun clearWorkDayShifts(date: LocalDate, profileId: String = activeWorkProfileId, clearOnCall: Boolean = false) {
        saveSnapshot()
        clearWorkDayWithoutSnapshot(date, profileId)
        if (clearOnCall) dayExtraInfoMapFor(profileId)[date] = getDayExtraInfo(date, profileId).copy(isOnCallManual = false)
        notifyDataChanged()
    }

    /** One undo operation for an inclusive interval, preserving other work calendars and notes. */
    fun clearWorkShiftRange(first: LocalDate, last: LocalDate, profileId: String = activeWorkProfileId) {
        saveSnapshot()
        var date = minOf(first, last)
        val end = maxOf(first, last)
        while (!date.isAfter(end)) {
            clearWorkDayWithoutSnapshot(date, profileId)
            dayExtraInfoMapFor(profileId)[date] = getDayExtraInfo(date, profileId).copy(isOnCallManual = false)
            date = date.plusDays(1)
        }
        notifyDataChanged()
    }

    private fun clearWorkDayWithoutSnapshot(date: LocalDate, profileId: String) {
        val remaining = getShiftsForDate(date).filter { getShiftDefinition(it.shiftDefId)?.workProfileId != profileId }
        calendarShifts[date] = mutableStateListOf<AssignedShift>().apply { addAll(remaining) }
        val extra = getDayExtraInfo(date, profileId)
        val profileRemaining = remaining.filter { getShiftDefinition(it.shiftDefId)?.workProfileId == profileId }
        dayExtraInfoMapFor(profileId)[date] = extra.copy(
            rotationCleared = profileRemaining.isEmpty(),
            hideExShift = if (profileRemaining.isEmpty()) true else extra.hideExShift
        )
        synchronizeAssignmentExtras(date, profileId)
    }

    private fun clearDayShiftsWithoutSnapshot(date: LocalDate) {
        calendarShifts[date] = mutableStateListOf()
        val extra = getDayExtraInfo(date)
        dayExtraInfo[date] = extra.copy(
            overtimeHours = 0f,
            isTrasferta = false,
            actualEndTime = null,
            hideExShift = true,
            rotationCleared = true
        )
    }

    fun applyRotation(rotationId: String, startDate: LocalDate, daysCount: Int = 90, profileId: String = activeWorkProfileId) {
        val rotation = rotations.firstOrNull { it.id == rotationId } ?: return
        if (rotation.daySequence.isEmpty() || daysCount <= 0) return
        saveSnapshot()
        var curr = startDate
        val seqSize = rotation.daySequence.size
        for (i in 0 until daysCount) {
            val defId = rotation.daySequence[i % seqSize]
            val definition = getShiftDefinition(defId)
            if (definition != null && definition.workProfileId != profileId) {
                curr = curr.plusDays(1)
                continue
            }
            val list = calendarShifts.getOrPut(curr) { mutableStateListOf() }
            val otherWorkShifts = list.filter { getShiftDefinition(it.shiftDefId)?.workProfileId != profileId }
            list.clear()
            list.addAll(otherWorkShifts)
            val hasAssignedShift = defId.isNotBlank() && defId != "empty" && defId != "eraser" && definition != null
            if (hasAssignedShift) list.add(
                AssignedShift(
                    id = UUID.randomUUID().toString(),
                    date = curr,
                    shiftDefId = defId,
                    isFromRotation = true,
                    originalShiftDefId = null,
                    isModifiedFromRotation = false
                )
            )
            val sorted = sortShiftsForDate(list)
            list.clear()
            list.addAll(sorted)
            val extra = getDayExtraInfo(curr, profileId)
            val profileList = list.filter { getShiftDefinition(it.shiftDefId)?.workProfileId == profileId }
            dayExtraInfoMapFor(profileId)[curr] = extra.copy(
                rotationCleared = false,
                hideExShift = false,
                isOnCallManual = if (hasAssignedShift && extra.isOnCallManual == false) null else extra.isOnCallManual,
                overtimeHours = profileList.sumOf { it.overtimeHours.toDouble() }.toFloat(),
                actualEndTime = profileList.firstOrNull()?.actualEndTime,
                isTrasferta = profileList.any { it.isTrasferta }
            )
            curr = curr.plusDays(1)
        }
        notifyDataChanged()
    }

    fun clearAllShifts() {
        saveSnapshot()
        val annotations = dayExtraInfo.filterValues { it.notes.isNotBlank() || it.tagIds.isNotEmpty() }
            .mapValues { (date, extra) -> DayExtraInfo(date, notes=extra.notes, tagIds=extra.tagIds) }
        calendarShifts.clear()
        dayExtraInfo.clear()
        dayExtraInfo.putAll(annotations)
        notifyDataChanged()
    }

    fun clearShiftsForMonth(yearMonth: YearMonth) {
        saveSnapshot()
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        var curr = start
        while (!curr.isAfter(end)) {
            clearDayShiftsWithoutSnapshot(curr)
            curr = curr.plusDays(1)
        }
        notifyDataChanged()
    }

    fun applyRotationBetween(rotationId: String, startDate: LocalDate, endDate: LocalDate) {
        val start = if (startDate.isBefore(endDate)) startDate else endDate
        val end = if (startDate.isBefore(endDate)) endDate else startDate
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
        if (days > 0) {
            applyRotation(rotationId, start, days)
        }
    }

    // Parser reale CSV / Excel-text
    fun importFromCsv(csvText: String): Int {
        var count = 0
        val lines = csvText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("Data")) continue
            val parts = trimmed.split(";", ",", "\t")
            if (parts.size >= 2) {
                try {
                    val dateStr = parts[0].trim()
                    val shiftNameOrCode = parts[1].trim()
                    val date = LocalDate.parse(dateStr)
                    val def = shiftDefinitions.firstOrNull {
                        it.name.equals(shiftNameOrCode, ignoreCase = true) ||
                                it.shortCode.equals(shiftNameOrCode, ignoreCase = true) ||
                                it.id.equals(shiftNameOrCode, ignoreCase = true)
                    } ?: shiftDefinitions.firstOrNull()

                    if (def != null) {
                        assignShift(date, def.id, notes = if (parts.size > 2) parts[2].trim() else "")
                        count++
                    }
                } catch (e: Exception) {
                    // Salta riga malformata
                }
            }
        }
        return count
    }

    /**
     * Integra ed aggiorna gli orari dei turni dal tabellone PDF/NgeniaTurni SENZA creare duplicati.
     * Se il turno (Mattina, Pomeriggio, Notte, Mat Dom, etc.) esiste già, ne aggiorna gli orari.
     * Solo se è un turno inedito, viene creato con la relativa categoria e colori.
     */
    fun ensureShiftDefinitionsFromParsedHours(hours: Map<String, Pair<String, String>>) {
        for ((code, times) in hours) {
            val (start, end) = times
            when (code.uppercase()) {
                "M" -> {
                    val existing = shiftDefinitions.find { it.id == "mattina" || it.shortCode.equals("MAT", ignoreCase = true) || it.shortCode.equals("M", ignoreCase = true) }
                    if (existing != null) {
                        saveShiftDefinition(existing.copy(startTime = start, endTime = end))
                    } else {
                        saveShiftDefinition(
                            ShiftDefinition(
                                id = "mattina",
                                name = "Mattina",
                                shortCode = "MAT",
                                startTime = start,
                                endTime = end,
                                colorHex = 0xFF0284C7,
                                iconKey = "wb_twilight",
                                category = ShiftCategory.LAVORO
                            )
                        )
                    }
                }
                "P" -> {
                    val existing = shiftDefinitions.find { it.id == "pomeriggio" || it.shortCode.equals("POM", ignoreCase = true) || it.shortCode.equals("P", ignoreCase = true) }
                    if (existing != null) {
                        saveShiftDefinition(existing.copy(startTime = start, endTime = end))
                    } else {
                        saveShiftDefinition(
                            ShiftDefinition(
                                id = "pomeriggio",
                                name = "Pomeriggio",
                                shortCode = "POM",
                                startTime = start,
                                endTime = end,
                                colorHex = 0xFFEF4444,
                                iconKey = "wb_sunny",
                                category = ShiftCategory.LAVORO
                            )
                        )
                    }
                }
                "N" -> {
                    val existing = shiftDefinitions.find { it.id == "notte" || it.shortCode.equals("NOT", ignoreCase = true) || it.shortCode.equals("N", ignoreCase = true) }
                    if (existing != null) {
                        saveShiftDefinition(existing.copy(startTime = start, endTime = end))
                    } else {
                        saveShiftDefinition(
                            ShiftDefinition(
                                id = "notte",
                                name = "Notte",
                                shortCode = "NOT",
                                startTime = start,
                                endTime = end,
                                colorHex = 0xFF22C55E,
                                iconKey = "nightlight_round",
                                category = ShiftCategory.LAVORO
                            )
                        )
                    }
                }
                "MD" -> {
                    val existing = shiftDefinitions.find { it.id == "mat_dom" || it.shortCode.equals("DOM", ignoreCase = true) || it.shortCode.equals("MD", ignoreCase = true) }
                    if (existing != null) {
                        saveShiftDefinition(existing.copy(startTime = start, endTime = end))
                    } else {
                        saveShiftDefinition(
                            ShiftDefinition(
                                id = "mat_dom",
                                name = "Mat Dom",
                                shortCode = "DOM",
                                startTime = start,
                                endTime = end,
                                colorHex = 0xFFF59E0B,
                                iconKey = "wb_sunny",
                                category = ShiftCategory.LAVORO
                            )
                        )
                    }
                }
                "TGF", "GIO" -> {
                    val existing = shiftDefinitions.find { it.id == "tgf" || it.id == "giornaliero" || it.shortCode.equals("TGF", ignoreCase = true) || it.shortCode.equals("GIO", ignoreCase = true) }
                    if (existing != null) {
                        saveShiftDefinition(existing.copy(startTime = start, endTime = end))
                    } else {
                        saveShiftDefinition(
                            ShiftDefinition(
                                id = "tgf",
                                name = "Turno Formazione",
                                shortCode = "TGF",
                                startTime = start,
                                endTime = end,
                                colorHex = 0xFF0D9488,
                                iconKey = "school",
                                category = ShiftCategory.LAVORO
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Importa ed applica le squadre e le rotazioni estratte da file o tabella importata.
     */
    fun importNgeniaTurniResult(
        result: com.turni.calendario.importer.NgeniaParseResult,
        selectedTeamName: String?,
        makeDefault: Boolean = true,
        applyToCalendar: Boolean = true
    ): String {
        // 1. Assicura orari turni senza duplicati
        ensureShiftDefinitionsFromParsedHours(result.detectedHours)

        // 2. Registra o aggiorna tutte le rotazioni delle squadre rilevate
        for (team in result.teams) {
            val rot = ShiftRotation(
                id = team.rotationId,
                name = team.teamName,
                totalDays = team.totalDays,
                daySequence = team.dayShiftDefIds,
                description = "Rotazione ${team.teamName} estratta da 'ngeniaTurni (${team.totalDays} giorni)"
            )
            saveRotation(rot)
        }

        // 3. Seleziona la squadra target
        val targetTeam = result.teams.find { it.teamName.equals(selectedTeamName, ignoreCase = true) }
            ?: result.teams.firstOrNull()

        if (targetTeam != null) {
            if (makeDefault) {
                setDefaultRotation(targetTeam.rotationId)
            }
            if (applyToCalendar) {
                val startDate = result.validityStart ?: LocalDate.of(2026, 9, 21)
                val daysCount = result.validityEnd?.let { end ->
                    (java.time.temporal.ChronoUnit.DAYS.between(startDate, end).toInt() + 1).coerceAtLeast(targetTeam.totalDays)
                } ?: targetTeam.totalDays
                applyRotation(targetTeam.rotationId, startDate, daysCount)
            }
            return "Importate con successo ${result.teams.size} squadre! Squadra '${targetTeam.teamName}' impostata come rotazione${if (makeDefault) " predefinita" else ""}."
        }
        return "Importate ${result.teams.size} rotazioni con successo."
    }

    /**
     * Applica direttamente al calendario i turni estratti per uno specifico dipendente dal tabellone.
     * @param employee Il dipendente con la mappa dei suoi turni per data (dayShifts).
     * @param overwrite Se true, sovrascrive i turni esistenti nelle date indicate; se false, aggiunge solo dove non ce ne sono.
     * @param alsoSetOnCall Se true e il dipendente ha reperibilità, imposta la reperibilità manuale per quelle date.
     * @return Il numero di giorni/turni applicati al calendario.
     */
    fun applyEmployeeShiftsToCalendar(
        employee: com.turni.calendario.importer.ParsedEmployee,
        overwrite: Boolean = true,
        alsoSetOnCall: Boolean = true
    ): Int {
        if (employee.dayShifts.isEmpty()) return 0
        saveSnapshot()
        var appliedCount = 0

        for ((date, rawCode) in employee.dayShifts) {
            val defId = com.turni.calendario.importer.NgeniaTurniParser.mapCodeToShiftDefId(rawCode)
            val currentList = calendarShifts[date]

            if (overwrite || currentList.isNullOrEmpty()) {
                val newList = androidx.compose.runtime.mutableStateListOf<AssignedShift>()
                newList.add(
                    AssignedShift(
                        id = UUID.randomUUID().toString(),
                        date = date,
                        shiftDefId = defId,
                        isFromRotation = false,
                        isModifiedFromRotation = false,
                        notes = "Turno $rawCode da tabellone"
                    )
                )
                calendarShifts[date] = newList
                appliedCount++

                if (alsoSetOnCall && employee.isOnCallGeneral) {
                    setDayOnCallManual(date, true)
                }
            }
        }
        return appliedCount
    }


    // Gestione Informazioni Extra del Giorno (Ore straordinario, Trasferte, Note, Reperibilità)
    fun getDayExtraInfo(date: LocalDate, profileId: String = activeWorkProfileId): DayExtraInfo {
        return dayExtraInfoReadOnly(profileId)[date] ?: DayExtraInfo(date = date)
    }

    fun updateDayExtraInfo(date: LocalDate, profileId: String = activeWorkProfileId, update: (DayExtraInfo) -> DayExtraInfo) {
        val current = getDayExtraInfo(date, profileId)
        dayExtraInfoMapFor(profileId)[date] = update(current)
        notifyDataChanged()
    }

    fun updateDayExtraInfo(date: LocalDate, extra: DayExtraInfo, profileId: String = activeWorkProfileId) {
        dayExtraInfoMapFor(profileId)[date] = extra
        notifyDataChanged()
    }

    fun setDayOvertime(date: LocalDate, hours: Float) {
        val current = getDayExtraInfo(date)
        dayExtraInfo[date] = current.copy(overtimeHours = hours.coerceAtLeast(0f))
        notifyDataChanged()
    }

    fun setDayActualEndTime(date: LocalDate, endTime: String?, calculatedOvertime: Float? = null) {
        val current = getDayExtraInfo(date)
        val newOvertime = calculatedOvertime ?: current.overtimeHours
        dayExtraInfo[date] = current.copy(
            actualEndTime = endTime,
            overtimeHours = newOvertime.coerceAtLeast(0f)
        )
        notifyDataChanged()
    }

    fun calculateOvertimeFromEndTime(shiftEndTime: String, actualEndTime: String): Float {
        return try {
            val scheduled = java.time.LocalTime.parse(shiftEndTime.trim())
            val actual = java.time.LocalTime.parse(actualEndTime.trim())
            val minutes = Math.floorMod(actual.toSecondOfDay() / 60 - scheduled.toSecondOfDay() / 60, 1440)
            if (minutes in 1..960) minutes / 60f else 0f
        } catch (_: Exception) {
            0f
        }
    }

    fun setDayTrasferta(date: LocalDate, isTrasferta: Boolean) {
        val current = getDayExtraInfo(date)
        dayExtraInfo[date] = current.copy(isTrasferta = isTrasferta)
        notifyDataChanged()
    }

    fun toggleDayTrasferta(date: LocalDate) {
        val current = hasTrasfertaForDate(date)
        val target = !current
        setDayTrasferta(date, target)
        val list = calendarShifts[date]
        if (!list.isNullOrEmpty()) {
            val newList = list.map { it.copy(isTrasferta = target) }.toMutableList()
            calendarShifts[date] = newList
        }
        notifyDataChanged()
    }

    fun setDayOnCallManual(date: LocalDate, isOnCall: Boolean?) {
        val current = getDayExtraInfo(date)
        if(current.isOnCallManual==isOnCall)return
        saveSnapshot()
        dayExtraInfo[date] = current.copy(isOnCallManual = isOnCall)
        notifyDataChanged()
    }

    /** One undo operation for an inclusive manual on-call interval. */
    fun setDayOnCallRange(first: LocalDate, last: LocalDate, isOnCall: Boolean, profileId: String = activeWorkProfileId) {
        saveSnapshot()
        var date = minOf(first, last)
        val end = maxOf(first, last)
        while (!date.isAfter(end)) {
            dayExtraInfoMapFor(profileId)[date] = getDayExtraInfo(date, profileId).copy(isOnCallManual = isOnCall)
            date = date.plusDays(1)
        }
        notifyDataChanged()
    }

    fun setDayHideExShift(date: LocalDate, hide: Boolean) {
        val current = getDayExtraInfo(date)
        dayExtraInfo[date] = current.copy(hideExShift = hide)
        notifyDataChanged()
    }

    fun isRotationCleared(date: LocalDate): Boolean {
        return dayExtraInfoReadOnly()[date]?.rotationCleared == true
    }

    fun setRotationCleared(date: LocalDate, cleared: Boolean) {
        val current = getDayExtraInfo(date)
        dayExtraInfo[date] = current.copy(rotationCleared = cleared)
        notifyDataChanged()
    }

    fun setDayNotes(date: LocalDate, notes: String) {
        val current = getDayExtraInfo(date)
        dayExtraInfo[date] = current.copy(notes = notes)
        notifyDataChanged()
    }

    // Gestione Reperibilità Software Intelligente
    fun isDateInAnnualRange(date: LocalDate, startM: Int, startD: Int, endM: Int, endD: Int): Boolean {
        val md = date.monthValue * 100 + date.dayOfMonth
        val startMd = startM * 100 + startD
        val endMd = endM * 100 + endD
        return if (startMd <= endMd) {
            md in startMd..endMd
        } else {
            md >= startMd || md <= endMd
        }
    }

    fun isOnCall(date: LocalDate, profileId: String = activeWorkProfileId): Boolean {
        // 1. Controllo override manuale specifico del giorno
        val manual = dayExtraInfoByProfile[profileId]?.get(date)?.isOnCallManual
        if (manual != null) return manual

        // 2. Controllo configurazione attiva per profilo
        val config = reperibilitaConfigFor(profileId)
        return when (config.mode) {
            ReperibilitaMode.MANUALE -> {
                date in config.manualDates
            }
            ReperibilitaMode.TURNO_SPECIFICO -> {
                val shifts = getShiftsForDate(date, profileId)
                shifts.any { it.shiftDefId in config.shiftSpecificIds }
            }
            ReperibilitaMode.ALTERNATA_STAGIONALE -> {
                val s = config.seasonal
                val isWinter = isDateInAnnualRange(date, s.winterStartMonth, s.winterStartDay, s.winterEndMonth, s.winterEndDay)
                if (isWinter) {
                    val shifts = getShiftsForDate(date, profileId)
                    shifts.any { it.shiftDefId in s.winterShiftIds }
                } else {
                    // Estate: ciclo settimanale da data di riferimento (es. 1 settimana ogni 3)
                    val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(s.summerCycleReferenceDate, date)
                    val weekIndex = Math.floorDiv(daysDiff, 7L)
                    val totalCycleWeeks = (s.summerCycleWorkWeeks + s.summerCycleOnCallWeeks).coerceAtLeast(1)
                    val posInCycle = Math.floorMod(weekIndex, totalCycleWeeks.toLong()).toInt()
                    // La settimana 0 è quella di reperibilità iniziale
                    posInCycle < s.summerCycleOnCallWeeks
                }
            }
        }
    }

    fun toggleManualDateOnCall(date: LocalDate, profileId: String = activeWorkProfileId) {
        val currentConfig = reperibilitaConfigFor(profileId)
        val current = currentConfig.manualDates
        val newDates = if (date in current) current - date else current + date
        setReperibilitaConfigFor(profileId, currentConfig.copy(manualDates = newDates))
    }

    /** Statistical holidays follow enabled holidays, independently of calendar visibility. */
    fun statisticsEntry(assigned:AssignedShift,definitions:Map<String,ShiftDefinition>,rules:List<StatisticsRule>):AssignedShift? =
        countedStatisticsAssignment(assigned,definitions,rules,
            HolidaysCatalog.allHolidays.any{it.id in enabledHolidayIds&&it.getDateForYear(assigned.date.year)==assigned.date},
            appPreferences.excludeVacationOnHolidays,appPreferences.holidayExcludedShiftIds)

    // Statistiche Report Compatibili
    fun getReportStats(start: LocalDate, end: LocalDate): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val definitions=shiftDefinitions.associateBy { it.id }
        val rules=statisticsRules.toList()
        var d = start
        while (!d.isAfter(end)) {
            val shifts = calendarShifts[d] ?: emptyList()
            for (assigned in shifts) {
                val s=statisticsEntry(assigned, definitions, rules) ?: continue
                counts[s.shiftDefId] = (counts[s.shiftDefId] ?: 0) + 1
            }
            d = d.plusDays(1)
        }
        return counts
    }

    // Statistiche Complete Report Avanzate (con ore reali, straordinari, trasferte e regola ferie su riposo/smonto)
    fun getComprehensiveReportStats(start: LocalDate, end: LocalDate): ComprehensiveReportStats {
        var daysWithWork = 0
        var hoursSum = 0f
        var totalOvertimeSum = 0f
        var totalPermissionSum = 0f
        var notti = 0
        var riposi = 0
        var smonti = 0
        var ferie = 0
        var trasferte = 0
        var reperibilitaCount = 0
        val counts = mutableMapOf<String, Int>()

        val statisticsModels=shiftDefinitions.associateBy { it.id }
        val countRules=statisticsRules.toList()
        var curr = start
        while (!curr.isAfter(end)) {
            val extra = dayExtraInfoReadOnly()[curr]
            val dayShifts = getShiftsForDate(curr).mapNotNull { statisticsEntry(it, statisticsModels, countRules) }
            val shiftOvertime = dayShifts.sumOf { it.overtimeHours.toDouble() }.toFloat()
            val overtime = if (dayShifts.any { it.isOvertime || it.overtimeHours > 0f || it.actualEndTime != null }) {
                shiftOvertime
            } else extra?.overtimeHours ?: 0f
            totalOvertimeSum += overtime
            val shiftTrasferte = dayShifts.count { it.isTrasferta }
            if (shiftTrasferte > 0) {
                trasferte += shiftTrasferte
            } else if (extra?.isTrasferta == true) {
                trasferte++
            }
            if (isOnCall(curr)) {
                reperibilitaCount++
            }
            var workedToday = false

            for (s in dayShifts) {
                val def = getShiftDefinition(s.shiftDefId)
                if (def != null) {
                    counts[s.shiftDefId] = (counts[s.shiftDefId] ?: 0) + 1
                    when (def.category) {
                        ShiftCategory.LAVORO -> {
                            workedToday = true
                            val baseHours = def.copy(
                                startTime = s.customStartTime ?: def.startTime,
                                endTime = s.customEndTime ?: def.endTime
                            ).durationHoursFloat
                            val perm = if (def.isAllDay) 0f else (if (s.permissionHours.isFinite()) s.permissionHours else 0f).coerceIn(0f, baseHours)
                            hoursSum += (baseHours - perm)
                            totalPermissionSum += perm
                            if (def.id=="notte" || def.name.contains("Notte",ignoreCase=true)) notti++
                        }
                        ShiftCategory.RIPOSO -> if(def.id=="smonto_notte") smonti++ else riposi++
                        ShiftCategory.ASSENZA -> if(def.id=="ferie") ferie++
                        else -> {}
                    }
                }
            }

            if (workedToday || overtime > 0f) {
                daysWithWork++
                hoursSum += overtime
            }

            curr = curr.plusDays(1)
        }

        return ComprehensiveReportStats(
            workedDays = daysWithWork,
            totalHours = hoursSum,
            totalOvertimeHours = totalOvertimeSum,
            countNotte = notti,
            countRiposo = riposi,
            countSmonto = smonti,
            countFerie = ferie,
            countTrasferte = trasferte,
            countReperibilita = reperibilitaCount,
            shiftCounts = counts,
            totalPermissionHours = totalPermissionSum
        )
    }

    fun getComprehensiveReportStats(yearMonth: YearMonth): ComprehensiveReportStats {
        return getComprehensiveReportStats(yearMonth.atDay(1), yearMonth.atEndOfMonth())
    }
}

data class ComprehensiveReportStats(
    val workedDays: Int,
    val totalHours: Float,
    val totalOvertimeHours: Float,
    val countNotte: Int,
    val countRiposo: Int,
    val countSmonto: Int,
    val countFerie: Int,
    val countTrasferte: Int,
    val countReperibilita: Int,
    val shiftCounts: Map<String, Int>,
    val totalPermissionHours: Float = 0f
)

