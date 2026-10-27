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
            name = "Lavoro Principale",
            companyOrDepartment = "",
            weeklyHours = 38,
            minRestHoursBetweenShifts = 11,
            nightSurchargePercent = 35,
            holidaySurchargePercent = 50,
            nightHolidaySurchargePercent = 65,
            overtimeDaySurchargePercent = 25,
            overtimeNightSurchargePercent = 40,
            colorHex = 0xFF0284C7,
            iconKey = "work",
            isDefault = true,
            enableAdvancedCalc = false
        )
    )

    val activeWorkProfile: WorkProfile
        get() = workProfiles.find { it.id == activeWorkProfileId }
            ?: workProfiles.firstOrNull()
            ?: WorkProfile("principale", "Lavoro Principale", isDefault = true)

    fun setActiveWorkProfile(profileId: String) {
        if (workProfiles.none { it.id == profileId }) return
        activeWorkProfileId = profileId
        for (i in workProfiles.indices) {
            val p = workProfiles[i]
            workProfiles[i] = p.copy(isDefault = p.id == profileId)
        }
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

    // Extra informazioni giorno: ore straordinario, trasferta, override reperibilità, note, nascondi ex-turno
    val dayExtraInfo = mutableStateMapOf<LocalDate, DayExtraInfo>()

    // Configurazione Reperibilità (Gestita in "Altro")
    var reperibilitaConfig by mutableStateOf(ReperibilitaConfig())

    // Configurazione Sveglie Automatiche Intelligenti (Gestita in "Altro")
    var smartAlarmsConfig by mutableStateOf(SmartAlarmsConfig())

    // Preferenze dell'applicazione (Gestite in "Altro")
    var appPreferences by mutableStateOf(AppPreferences())

    // Rotazioni cicliche disponibili
    val rotations = mutableStateListOf<ShiftRotation>()

    // Rotazione attiva corrente (inizializzata PRIMA di init)
    var activeRotationId by mutableStateOf("turno_a")

    val activeRotation: ShiftRotation?
        get() = rotations.firstOrNull { it.id == activeRotationId } ?: rotations.firstOrNull()

    // Rotazione / Turno Predefinito dell'utente (appare per primo ovunque)
    var defaultRotationId by mutableStateOf("turno_a")

    // Giorno selezionato proveniente dal tocco su un Widget Android
    var selectedDateFromWidget by mutableStateOf<LocalDate?>(null)

    // Listener per aggiornamento istantaneo widget e observer
    var onDataChangedListener: (() -> Unit)? = null

    fun notifyDataChanged() {
        try {
            onDataChangedListener?.invoke()
        } catch (_: Exception) {}
    }

    fun setDefaultRotation(rotationId: String) {
        defaultRotationId = rotationId
        if (rotationId.isNotEmpty()) {
            activeRotationId = rotationId
        }
    }

    fun toggleDefaultRotation(rotationId: String): Boolean {
        return if (defaultRotationId == rotationId) {
            defaultRotationId = ""
            false
        } else {
            defaultRotationId = rotationId
            activeRotationId = rotationId
            true
        }
    }

    fun getRotationsSortedByDefault(): List<ShiftRotation> {
        if (defaultRotationId.isEmpty()) return rotations.toList()
        return rotations.sortedByDescending { it.id == defaultRotationId }
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
        initDefaultShiftDefinitions()
        initRotations()
    }

    private fun initDefaultShiftDefinitions() {
        shiftDefinitions.clear()
        shiftDefinitions.addAll(
            listOf(
                ShiftDefinition(
                    id = "pomeriggio",
                    name = "Pomeriggio",
                    shortCode = "POM",
                    startTime = "14:00",
                    endTime = "21:36",
                    isAllDay = false,
                    colorHex = 0xFFEE9C9C, // Coral
                    iconKey = "wb_sunny",
                    category = ShiftCategory.LAVORO
                ),
                ShiftDefinition(
                    id = "mattina",
                    name = "Mattina",
                    shortCode = "MAT",
                    startTime = "06:00",
                    endTime = "13:36",
                    isAllDay = false,
                    colorHex = 0xFFF2C46D, // Amber
                    iconKey = "wb_twilight",
                    category = ShiftCategory.LAVORO
                ),
                ShiftDefinition(
                    id = "notte",
                    name = "Notte",
                    shortCode = "NOT",
                    startTime = "22:00",
                    endTime = "05:36",
                    isAllDay = false,
                    colorHex = 0xFFBAAEF6, // Lavender
                    iconKey = "nightlight_round",
                    category = ShiftCategory.LAVORO
                ),
                ShiftDefinition(
                    id = "mat_dom",
                    name = "Mat Dom",
                    shortCode = "DOM",
                    startTime = "08:36",
                    endTime = "16:12",
                    isAllDay = false,
                    colorHex = 0xFFF2C46D, // Amber
                    iconKey = "wb_sunny",
                    category = ShiftCategory.LAVORO
                ),
                ShiftDefinition(
                    id = "smonto_notte",
                    name = "Smonto Notte",
                    shortCode = "SMO",
                    startTime = "05:36",
                    endTime = "05:36",
                    isAllDay = true,
                    colorHex = 0xFF83CDB7, // Mint
                    iconKey = "bedtime",
                    category = ShiftCategory.RIPOSO
                ),
                ShiftDefinition(
                    id = "riposo",
                    name = "Riposo",
                    shortCode = "RIP",
                    startTime = "00:00",
                    endTime = "23:59",
                    isAllDay = true,
                    colorHex = 0xFFAAB9CE, // Pearl
                    iconKey = "weekend",
                    category = ShiftCategory.RIPOSO
                ),
                ShiftDefinition(
                    id = "ferie",
                    name = "Ferie",
                    shortCode = "FER",
                    startTime = "00:00",
                    endTime = "23:59",
                    isAllDay = true,
                    colorHex = 0xFF94C4EF, // Sky
                    iconKey = "beach_access",
                    category = ShiftCategory.ASSENZA
                ),
                ShiftDefinition(
                    id = "giornaliero",
                    name = "Giornaliero",
                    shortCode = "GIO",
                    startTime = "08:30",
                    endTime = "17:06",
                    breakMinutes = 60,
                    isAllDay = false,
                    colorHex = 0xFF83CDB7, // Mint
                    iconKey = "schedule",
                    category = ShiftCategory.LAVORO
                ),
                ShiftDefinition(
                    id = "uffici",
                    name = "Uffici",
                    shortCode = "UFF",
                    startTime = "07:30",
                    endTime = "15:36",
                    isAllDay = false,
                    colorHex = 0xFFAAB9CE, // Pearl
                    iconKey = "business",
                    category = ShiftCategory.LAVORO
                ),
                ShiftDefinition(
                    id = "donazione",
                    name = "Donazione",
                    shortCode = "DON",
                    startTime = "00:00",
                    endTime = "23:59",
                    isAllDay = true,
                    colorHex = 0xFFF2C46D, // Amber
                    iconKey = "volunteer_activism",
                    category = ShiftCategory.ASSENZA
                ),
                ShiftDefinition(
                    id = "malattia",
                    name = "Malattia",
                    shortCode = "MAL",
                    startTime = "00:00",
                    endTime = "23:59",
                    isAllDay = true,
                    colorHex = 0xFF94C4EF, // Sky
                    iconKey = "local_hospital",
                    category = ShiftCategory.ASSENZA
                )
            )
        )
    }

    private fun initRotations() {
        rotations.clear()
        rotations.add(
            ShiftRotation(
                id = "turno_a",
                name = "Turno A",
                totalDays = 28,
                daySequence = sequenceTurnoA,
                description = "Ciclo 28 giorni (Mattina, Pomeriggio, Notte, Smonto, Riposo, Mat Dom)"
            )
        )
        rotations.add(
            ShiftRotation(
                id = "turno_b",
                name = "Turno B",
                totalDays = 28,
                daySequence = sequenceTurnoA.drop(7) + sequenceTurnoA.take(7),
                description = "Ciclo sfasato di 28 giorni"
            )
        )
    }

    /**
     * Calcola il turno pianificato dal ciclo di rotazione per una data specifica.
     * Gestisce dinamicamente qualsiasi lunghezza del ciclo (7, 14, 21, 28, 35 gg, ecc.).
     */
    fun getPlannedRotationShift(date: LocalDate, rotationId: String = activeRotationId): ShiftDefinition? = synchronized(this) {
        val targetId = if (rotationId.isNotBlank()) rotationId else (activeRotationId.ifEmpty { "turno_a" })
        val rot = rotations.firstOrNull { it.id == targetId } ?: rotations.firstOrNull() ?: return null
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
    }

    fun deleteShiftDefinition(id: String) = synchronized(this) {
        shiftDefinitions.removeAll { it.id == id }
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
        reperibilitaConfig = reperibilitaConfig.copy(
            shiftSpecificIds = reperibilitaConfig.shiftSpecificIds.filterNot { it == id },
            seasonal = reperibilitaConfig.seasonal.copy(winterShiftIds = reperibilitaConfig.seasonal.winterShiftIds.filterNot { it == id })
        )
        smartAlarmsConfig = smartAlarmsConfig.copy(rules = smartAlarmsConfig.rules.filterNot { it.shiftDefId == id })
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
        val extras: Map<LocalDate, DayExtraInfo>
    )
    private val undoHistory = mutableListOf<CalendarSnapshot>()

    fun saveSnapshot() {
        val snapshot = calendarShifts.mapValues { (_, list) -> list.map { it.copy() } }
        undoHistory.add(CalendarSnapshot(snapshot, dayExtraInfo.toMap()))
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
        dayExtraInfo.clear()
        dayExtraInfo.putAll(last.extras)
        notifyDataChanged()
        return true
    }

    // Gestione Turni nel Calendario
    fun getShiftsForDate(date: LocalDate): List<AssignedShift> {
        return calendarShifts[date] ?: emptyList()
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
        val isCleared = dayExtraInfo[date]?.rotationCleared == true
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
        calendarShifts[date] = newList
        notifyDataChanged()
    }

    /**
     * Aggiunge un turno secondario alla data (es. turno doppio, straordinario, reperibilità)
     */
    fun addSecondaryShift(date: LocalDate, shiftDefId: String, notes: String = "") {
        saveSnapshot()
        val currentList = calendarShifts[date]?.toList() ?: emptyList()
        val newList = mutableStateListOf<AssignedShift>()
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
        calendarShifts[date] = newList
        notifyDataChanged()
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
        val current = getShiftsForDate(date).toList()
        val primaryIndex = current.indexOfFirst { getShiftDefinition(it.shiftDefId)?.workProfileId == definition.workProfileId }
        val previous = current.getOrNull(primaryIndex)
        val original = previous?.takeIf { it.isFromRotation || it.isModifiedFromRotation }?.let { it.originalShiftDefId ?: it.shiftDefId }
        val replacement = AssignedShift(
            id = UUID.randomUUID().toString(), date = date, shiftDefId = newShiftDefId,
            isFromRotation = original == newShiftDefId,
            isModifiedFromRotation = original != null && original != newShiftDefId,
            originalShiftDefId = original?.takeIf { it != newShiftDefId }
        )
        val updated = mutableStateListOf<AssignedShift>().apply { addAll(current) }
        if (primaryIndex < 0) updated.add(replacement) else updated[primaryIndex] = replacement
        calendarShifts[date] = updated
        dayExtraInfo[date] = getDayExtraInfo(date).copy(hideExShift = false)
        synchronizeAssignmentExtras(date)
    }

    /**
     * Assegna un turno a un intervallo di date in blocco (da startDate a endDate)
     */
    fun applyShiftRange(shiftDefId: String, startDate: LocalDate, endDate: LocalDate) {
        val erasing = shiftDefId == "eraser" || shiftDefId == "empty"
        if (!erasing && getShiftDefinition(shiftDefId) == null) return
        saveSnapshot()
        val start = minOf(startDate, endDate)
        val end = maxOf(startDate, endDate)
        var current = start
        while (!current.isAfter(end)) {
            if (erasing) clearWorkDayWithoutSnapshot(current, activeWorkProfileId)
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
        if (rotations.size > 1) {
            rotations.removeAll { it.id == id }
            if (activeRotationId == id) {
                activeRotationId = rotations.first().id
            }
            if (defaultRotationId == id) defaultRotationId = ""
        }
    }

    fun removeAssignedShift(date: LocalDate, assignedId: String) {
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
        isTrasferta: Boolean? = null
    ) {
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
                        overtimeHours = clampedHours,
                        notes = notes,
                        isTrasferta = isTrasferta ?: shift.isTrasferta
                    )
                )
            } else {
                newList.add(shift)
            }
        }
        calendarShifts[date] = newList
        val totalOvertime = newList.sumOf { it.overtimeHours.toDouble() }.toFloat()
        val anyTrasferta = newList.any { it.isTrasferta }
        val extra = getDayExtraInfo(date)
        dayExtraInfo[date] = extra.copy(
            overtimeHours = totalOvertime,
            actualEndTime = actualEndTime,
            isTrasferta = anyTrasferta
        )
        notifyDataChanged()
    }

    fun hasTrasfertaForDate(date: LocalDate): Boolean {
        return calendarShifts[date]?.any { it.isTrasferta } == true || dayExtraInfo[date]?.isTrasferta == true
    }

    private fun synchronizeAssignmentExtras(date: LocalDate) {
        val shifts = getShiftsForDate(date)
        dayExtraInfo[date] = getDayExtraInfo(date).copy(
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
        if (clearOnCall) dayExtraInfo[date] = getDayExtraInfo(date).copy(isOnCallManual = false)
        notifyDataChanged()
    }

    private fun clearWorkDayWithoutSnapshot(date: LocalDate, profileId: String) {
        val remaining = getShiftsForDate(date).filter { getShiftDefinition(it.shiftDefId)?.workProfileId != profileId }
        calendarShifts[date] = mutableStateListOf<AssignedShift>().apply { addAll(remaining) }
        val extra = getDayExtraInfo(date)
        dayExtraInfo[date] = extra.copy(
            rotationCleared = remaining.isEmpty(),
            hideExShift = if (remaining.isEmpty()) true else extra.hideExShift
        )
        synchronizeAssignmentExtras(date)
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
            if (defId.isNotBlank() && defId != "empty" && defId != "eraser" && getShiftDefinition(defId) != null) list.add(
                AssignedShift(
                    id = UUID.randomUUID().toString(),
                    date = curr,
                    shiftDefId = defId,
                    isFromRotation = true,
                    originalShiftDefId = null,
                    isModifiedFromRotation = false
                )
            )
            val extra = getDayExtraInfo(curr)
            dayExtraInfo[curr] = extra.copy(
                rotationCleared = false, hideExShift = false,
                overtimeHours = list.sumOf { it.overtimeHours.toDouble() }.toFloat(),
                actualEndTime = list.firstOrNull()?.actualEndTime, isTrasferta = list.any { it.isTrasferta }
            )
            curr = curr.plusDays(1)
        }
        notifyDataChanged()
    }

    fun clearAllShifts() {
        saveSnapshot()
        calendarShifts.clear()
        dayExtraInfo.clear()
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
    fun getDayExtraInfo(date: LocalDate): DayExtraInfo {
        return dayExtraInfo[date] ?: DayExtraInfo(date = date)
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

    fun setDayHideExShift(date: LocalDate, hide: Boolean) {
        val current = getDayExtraInfo(date)
        dayExtraInfo[date] = current.copy(hideExShift = hide)
        notifyDataChanged()
    }

    fun isRotationCleared(date: LocalDate): Boolean {
        return dayExtraInfo[date]?.rotationCleared == true
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

    fun isOnCall(date: LocalDate): Boolean {
        // 1. Controllo override manuale specifico del giorno
        val manual = dayExtraInfo[date]?.isOnCallManual
        if (manual != null) return manual

        // 2. Controllo configurazione attiva
        return when (reperibilitaConfig.mode) {
            ReperibilitaMode.MANUALE -> {
                date in reperibilitaConfig.manualDates
            }
            ReperibilitaMode.TURNO_SPECIFICO -> {
                val shifts = getShiftsForDate(date)
                shifts.any { it.shiftDefId in reperibilitaConfig.shiftSpecificIds }
            }
            ReperibilitaMode.ALTERNATA_STAGIONALE -> {
                val s = reperibilitaConfig.seasonal
                val isWinter = isDateInAnnualRange(date, s.winterStartMonth, s.winterStartDay, s.winterEndMonth, s.winterEndDay)
                if (isWinter) {
                    val shifts = getShiftsForDate(date)
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

    fun toggleManualDateOnCall(date: LocalDate) {
        val current = reperibilitaConfig.manualDates
        val newDates = if (date in current) current - date else current + date
        reperibilitaConfig = reperibilitaConfig.copy(manualDates = newDates)
    }

    // Statistiche Report Compatibili
    fun getReportStats(start: LocalDate, end: LocalDate): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        var d = start
        while (!d.isAfter(end)) {
            val shifts = calendarShifts[d] ?: emptyList()
            for (s in shifts) {
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
        var notti = 0
        var riposi = 0
        var smonti = 0
        var ferie = 0
        var trasferte = 0
        var reperibilitaCount = 0
        val counts = mutableMapOf<String, Int>()

        var curr = start
        while (!curr.isAfter(end)) {
            val extra = dayExtraInfo[curr]
            val dayShifts = getShiftsForDate(curr)
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
                    // Regola fondamentale richiesta dall'utente:
                    // Se l'utente ha segnato Ferie sopra uno Smonto o Riposo,
                    // nel report contali come smonto e riposo, NON come giorni di ferie usati!
                    if (s.shiftDefId == "ferie") {
                        val planned = getPlannedRotationShift(curr)
                        val origId = s.originalShiftDefId ?: if (s.isFromRotation) planned?.id else null
                        val origDef = origId?.let { getShiftDefinition(it) } ?: if (s.isFromRotation) planned else null
                        val isSmonto = origId == "smonto_notte" || origDef?.id == "smonto_notte"
                        val isRiposo = origId == "riposo" || origDef?.id == "riposo" || origDef?.category == ShiftCategory.RIPOSO

                        if (isSmonto) {
                            smonti++
                            counts["smonto_notte"] = (counts["smonto_notte"] ?: 0) + 1
                        } else if (isRiposo) {
                            riposi++
                            counts["riposo"] = (counts["riposo"] ?: 0) + 1
                        } else {
                            // Giorno lavorativo effettivo convertito in ferie
                            ferie++
                            counts["ferie"] = (counts["ferie"] ?: 0) + 1
                        }
                    } else {
                        counts[s.shiftDefId] = (counts[s.shiftDefId] ?: 0) + 1

                        when (def.category) {
                            ShiftCategory.LAVORO -> {
                                workedToday = true
                                hoursSum += def.copy(
                                    startTime = s.customStartTime ?: def.startTime,
                                    endTime = s.customEndTime ?: def.endTime
                                ).durationHoursFloat
                                if (def.id == "notte" || def.name.contains("Notte", ignoreCase = true)) {
                                    notti++
                                }
                            }
                            ShiftCategory.RIPOSO -> {
                                if (def.id == "smonto_notte") smonti++ else riposi++
                            }
                            ShiftCategory.ASSENZA -> {
                                // ferie gestite sopra, malattia/donazione qui
                            }
                            else -> {}
                        }
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
            shiftCounts = counts
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
    val shiftCounts: Map<String, Int>
)

