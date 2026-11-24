package com.turni.calendario.model

import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class ShiftCategory {
    LAVORO,
    RIPOSO,
    ASSENZA,
    ALTRO
}

/**
 * Modello completamente personalizzabile per i turni dell'utente.
 * Consente orari flessibili (es. 06:00 - 13:36), colore, badge, icone e categorie.
 */
data class ShiftDefinition(
    val id: String,
    val name: String,
    val shortCode: String,
    val startTime: String = "06:00",
    val endTime: String = "13:36",
    val isAllDay: Boolean = false,
    val breakMinutes: Int = 0,
    val breakStartTime: String? = null,
    val colorHex: Long = 0xFF0EA5E9,
    val iconKey: String = "sunny",
    val category: ShiftCategory = ShiftCategory.LAVORO,
    val workProfileId: String = "principale",
    val hourlyRate: Double = 0.0,
    val allowance: Double = 0.0 // Indennità turno
) {
    val color: Color
        // Display-only palette mapping; persistence and exports retain colorHex verbatim.
        get() = com.turni.calendario.ui.theme.shiftDisplayColor(Color(colorHex), definitionId = id)

    val durationMinutes: Long
        get() {
            if (isAllDay) return 0L
            return try {
                val start = LocalTime.parse(startTime)
                val end = LocalTime.parse(endTime)
                var minutes = if (end.isAfter(start)) {
                    java.time.Duration.between(start, end).toMinutes()
                } else {
                    java.time.Duration.between(start, LocalTime.MAX).toMinutes() + 1 +
                            java.time.Duration.between(LocalTime.MIN, end).toMinutes()
                }
                (minutes - breakMinutes).coerceAtLeast(0)
            } catch (e: Exception) {
                456L // 7h 36m
            }
        }

    val durationHoursFloat: Float
        get() = durationMinutes / 60f

    val durationFormatted: String
        get() {
            if (isAllDay) return "Tutto il giorno"
            val totalMins = durationMinutes
            val h = totalMins / 60
            val m = totalMins % 60
            return "${h}h ${m}m"
        }

    val timeRangeFormatted: String
        get() = if (isAllDay) "Tutto il giorno" else "$startTime - $endTime"
}

/** Minutes from 00:00 for chronological sorting; all-day shifts sort last. */
fun ShiftDefinition.startSortMinutes(): Int {
    if (isAllDay) return Int.MAX_VALUE
    return try {
        val parsed = LocalTime.parse(startTime.trim())
        parsed.hour * 60 + parsed.minute
    } catch (_: Exception) {
        Int.MAX_VALUE - 1
    }
}

/** Canonical chronological comparator: timed shifts ascending by start/end/name, then all-day shifts by name. */
val ShiftChronologicalComparator: Comparator<ShiftDefinition> = Comparator { a, b ->
    if (a.isAllDay != b.isAllDay) {
        if (a.isAllDay) 1 else -1
    } else if (!a.isAllDay) {
        val startCmp = a.startSortMinutes().compareTo(b.startSortMinutes())
        if (startCmp != 0) startCmp
        else {
            val endCmp = a.endTime.compareTo(b.endTime)
            if (endCmp != 0) endCmp else a.name.compareTo(b.name, ignoreCase = true)
        }
    } else {
        a.name.compareTo(b.name, ignoreCase = true)
    }
}

fun List<ShiftDefinition>.sortedChronologically(): List<ShiftDefinition> =
    sortedWith(ShiftChronologicalComparator)

fun List<AssignedShift>.sortedChronologically(defs: Map<String, ShiftDefinition>): List<AssignedShift> =
    sortedWith(
        compareBy<AssignedShift, ShiftDefinition?>(nullsLast(ShiftChronologicalComparator)) { shift ->
            defs[shift.shiftDefId]?.let { def ->
                if (shift.customStartTime != null || shift.customEndTime != null) {
                    def.copy(
                        startTime = shift.customStartTime ?: def.startTime,
                        endTime = shift.customEndTime ?: def.endTime
                    )
                } else {
                    def
                }
            }
        }.thenBy { it.id }
    )

/**
 * Singolo turno assegnato ad una data specifica.
 * Una singola data può contenere PIÙ turni (es. Smonto Notte + Giornaliero).
 */
data class AssignedShift(
    val id: String,
    val date: LocalDate,
    val shiftDefId: String,
    val originalShiftDefId: String? = null,
    val isModifiedFromRotation: Boolean = false,
    val isFromRotation: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val actualEndTime: String? = null,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val isOvertime: Boolean = false,
    val overtimeHours: Float = 0f,
    val isTrasferta: Boolean = false,
    val isOnCallManual: Boolean? = null,
    val hideExShift: Boolean = false,
    val permissionHours: Float = 0f,
    val actualStartTime: String? = null,
    val startOvertimeHours: Float = 0f,
    val startPermissionHours: Float = 0f,
    val endOvertimeHours: Float = 0f,
    val endPermissionHours: Float = 0f
)

/**
 * Informazioni extra per singola giornata (ore straordinario, trasferta, reperibilità, note).
 */
data class DayExtraInfo(
    val date: LocalDate,
    val overtimeHours: Float = 0f,
    val isTrasferta: Boolean = false,
    val isOnCallManual: Boolean? = null,
    val hideExShift: Boolean = false,
    val rotationCleared: Boolean = false,
    val notes: String = "",
    val actualEndTime: String? = null,
    val tagIds: List<String> = emptyList()
)

/**
 * Modalità di gestione della reperibilità.
 */
enum class ReperibilitaMode(val label: String, val description: String) {
    MANUALE("Reperibilità Singola", "Seleziona o tocca manualmente le date di reperibilità"),
    TURNO_SPECIFICO("Su Turno Specifico", "Attiva quando lavori un turno scelto (es. Pomeriggio)"),
    ALTERNATA_STAGIONALE("Alternata Stagionale", "Inverno su turno (date A-B loop annuale) ed estate a settimane")
}

/**
 * Configurazione della rotazione stagionale (Inverno date A-B vs Estate a rotazione settimanale).
 */
data class SeasonalReperibilitaConfig(
    val winterStartMonth: Int = 10,   // 1 Ottobre
    val winterStartDay: Int = 1,
    val winterEndMonth: Int = 5,      // 31 Maggio
    val winterEndDay: Int = 31,
    val winterShiftIds: List<String> = emptyList(),
    val summerCycleWorkWeeks: Int = 3,    // es. 3 settimane senza rep
    val summerCycleOnCallWeeks: Int = 1,  // 1 settimana di reperibilità
    val summerCycleReferenceDate: LocalDate = LocalDate.of(2026, 6, 1)
)

data class ReperibilitaConfig(
    val mode: ReperibilitaMode = ReperibilitaMode.MANUALE,
    val shiftSpecificIds: List<String> = emptyList(),
    val seasonal: SeasonalReperibilitaConfig = SeasonalReperibilitaConfig(),
    val manualDates: Set<LocalDate> = emptySet()
)

/**
 * Configurazione delle sveglie automatiche intelligenti collegate ai turni.
 */
data class ShiftAlarmRule(
    val shiftDefId: String,
    val isEnabled: Boolean = true,
    val minutesBefore: Int = 60, // Anticipo in minuti
    val customTime: String? = null // Orario fisso alternativo (es. "07:00")
)

data class SmartAlarmsConfig(
    val isGlobalEnabled: Boolean = false,
    val rules: List<ShiftAlarmRule> = emptyList(),
    val disabledAlarmIds: Set<String> = emptySet()
)

/**
 * Preferenze generali dell'applicazione.
 */
data class AppPreferences(
    val showExShifts: Boolean = true,
    val weekStartMonday: Boolean = true,
    val timeFormat24h: Boolean = true,
    val showHolidays: Boolean = true,
    val autoBackupDaily: Boolean = true,
    val excludeVacationOnHolidays: Boolean = false,
    val holidayExcludedShiftIds: Set<String> = emptySet(),
    val themeMode: String? = null
)

/**
 * Definizione contrattuale CCNL con parametri orari, percentuali e riposo minimo obbligatorio.
 * Niente tariffe monetarie: solo regole su ore, maggiorazioni e riposi.
 */
data class CcnlDefinition(
    val code: String,
    val name: String,
    val shortLabel: String,
    val sector: String,
    val standardWeeklyHours: Int,
    val minRestHoursBetweenShifts: Int,
    val nightSurchargePercent: Int,
    val holidaySurchargePercent: Int,
    val nightHolidaySurchargePercent: Int,
    val overtimeDaySurchargePercent: Int = 25,
    val overtimeNightSurchargePercent: Int = 40,
    val onCallNotes: String = "",
    val contractDescription: String = "",
    val restingRulesSummary: String = ""
)

object CcnlCatalog {
    val presets: List<CcnlDefinition> = listOf(
        CcnlDefinition(
            code = "CCNL_MOBILITA_AF",
            name = "CCNL Mobilità & Trasporti",
            shortLabel = "Mobilità / Ferrovie",
            sector = "Ferrovie, Circolazione, Movimento & Infrastruttura",
            standardWeeklyHours = 38,
            minRestHoursBetweenShifts = 14,
            nightSurchargePercent = 35,
            holidaySurchargePercent = 50,
            nightHolidaySurchargePercent = 65,
            overtimeDaySurchargePercent = 25,
            overtimeNightSurchargePercent = 40,
            onCallNotes = "Indennità oraria di reperibilità secondo art. 28 CCNL Mobilità/AF con turni a chiamata",
            contractDescription = "CCNL per il personale delle società del Gruppo FS Italiane e imprese ferroviarie private. Regola l'orario a turnazione rotativa.",
            restingRulesSummary = "Riposo continuativo giornaliero minimo di 14 ore tra due turni (elevato a 16 ore dopo turno notturno o smonto notte)."
        ),
        CcnlDefinition(
            code = "CCNL_SANITA",
            name = "CCNL Sanità Pubblica & Privata",
            shortLabel = "Sanità (Infermieri/Medici)",
            sector = "Ospedali, Cliniche, Pronto Soccorso & RSA",
            standardWeeklyHours = 36,
            minRestHoursBetweenShifts = 11,
            nightSurchargePercent = 25,
            holidaySurchargePercent = 30,
            nightHolidaySurchargePercent = 50,
            overtimeDaySurchargePercent = 15,
            overtimeNightSurchargePercent = 30,
            onCallNotes = "Servizio di Pronta Disponibilità (massimo 10 turni/mese di 12 ore) con recupero orario",
            contractDescription = "CCNL Comparto Sanità per il personale infermieristico, tecnico e sanitario a ciclo continuo H24.",
            restingRulesSummary = "Riposo minimo continuativo di 11 ore ogni 24 ore (D.Lgs. 66/2003) con deroga solo per pronta disponibilità."
        ),
        CcnlDefinition(
            code = "CCNL_METALMECCANICI",
            name = "CCNL Metalmeccanici Industria",
            shortLabel = "Metalmeccanici (H24)",
            sector = "Industria Metalmeccanica, Siderurgica & Automotive",
            standardWeeklyHours = 40,
            minRestHoursBetweenShifts = 11,
            nightSurchargePercent = 30,
            holidaySurchargePercent = 50,
            nightHolidaySurchargePercent = 60,
            overtimeDaySurchargePercent = 25,
            overtimeNightSurchargePercent = 45,
            onCallNotes = "Reperibilità programmata con indennità giornaliera o settimanale e chiamata minima garantita",
            contractDescription = "CCNL Industria Metalmeccanica per turnisti su 3 turni a ciclo continuo (6x6 o 7x7).",
            restingRulesSummary = "Riposo minimo di 11 ore consecutive ogni 24 ore tra la fine di un turno e l'inizio del successivo."
        ),
        CcnlDefinition(
            code = "CCNL_VIGILANZA",
            name = "CCNL Vigilanza Privata & Sicurezza",
            shortLabel = "Vigilanza Privata",
            sector = "Istituti di Vigilanza, Servizi Fiduciari & Trasporto Valori",
            standardWeeklyHours = 40,
            minRestHoursBetweenShifts = 11,
            nightSurchargePercent = 20,
            holidaySurchargePercent = 30,
            nightHolidaySurchargePercent = 45,
            overtimeDaySurchargePercent = 20,
            overtimeNightSurchargePercent = 35,
            onCallNotes = "Disponibilità operativa per interventi su allarme con indennità di centrale operativa",
            contractDescription = "CCNL Istituti di Vigilanza Privata e Servizi di Sicurezza con turni articolati H24.",
            restingRulesSummary = "Riposo giornaliero minimo di 11 ore consecutive nel rispetto delle disposizioni vigenti."
        ),
        CcnlDefinition(
            code = "CCNL_LOGISTICA",
            name = "CCNL Trasporto Merci & Logistica",
            shortLabel = "Logistica & Trasporti",
            sector = "Magazzini, Spedizioni & Distribuzione Merci",
            standardWeeklyHours = 39,
            minRestHoursBetweenShifts = 11,
            nightSurchargePercent = 25,
            holidaySurchargePercent = 40,
            nightHolidaySurchargePercent = 55,
            overtimeDaySurchargePercent = 20,
            overtimeNightSurchargePercent = 35,
            onCallNotes = "Reperibilità autisti e operatori logistici di snodo con rimborso trasferta",
            contractDescription = "CCNL Logistica, Trasporto Merci e Spedizioni per magazzinieri, carrellisti e autisti.",
            restingRulesSummary = "Minimo 11 ore di riposo continuativo tra due prestazioni lavorative giornaliere."
        ),
        CcnlDefinition(
            code = "CCNL_COMMERCIO",
            name = "CCNL Terziario, Distribuzione & Servizi",
            shortLabel = "Commercio & Terziario",
            sector = "Grande Distribuzione, Negozi & Ristorazione",
            standardWeeklyHours = 40,
            minRestHoursBetweenShifts = 11,
            nightSurchargePercent = 15,
            holidaySurchargePercent = 30,
            nightHolidaySurchargePercent = 40,
            overtimeDaySurchargePercent = 15,
            overtimeNightSurchargePercent = 30,
            onCallNotes = "Reperibilità per aperture straordinarie o manutenzione punti vendita",
            contractDescription = "CCNL per i dipendenti da aziende del terziario, commercio, distribuzione e servizi.",
            restingRulesSummary = "Riposo giornaliero minimo di 11 ore consecutive ogni 24 ore lavorative."
        ),
        CcnlDefinition(
            code = "CCNL_PERSONALIZZATO",
            name = "CCNL Personalizzato / Aziendale",
            shortLabel = "Personalizzato",
            sector = "Accordo Integrativo Aziendale o Altro Settore",
            standardWeeklyHours = 38,
            minRestHoursBetweenShifts = 11,
            nightSurchargePercent = 30,
            holidaySurchargePercent = 40,
            nightHolidaySurchargePercent = 50,
            overtimeDaySurchargePercent = 20,
            overtimeNightSurchargePercent = 35,
            onCallNotes = "Condizioni di reperibilità definite da accordo aziendale",
            contractDescription = "Profilo con parametri completamente personalizzabili dall'utente.",
            restingRulesSummary = "Riposo stabilito dal contratto applicato."
        )
    )

    fun findByCode(code: String): CcnlDefinition {
        return presets.find { it.code == code } ?: presets.first()
    }
}

/**
 * Profilo di lavoro configurabile con CCNL associato e parametri orari contrattuali.
 */
data class WorkProfile(
    val id: String,
    val name: String,
    val companyOrDepartment: String = "",
    val ccnlCode: String = "CCNL_MOBILITA_AF",
    val ccnlName: String = "CCNL Mobilità & Trasporti",
    val weeklyHours: Int = 0,
    val minRestHoursBetweenShifts: Int = 0,
    val nightSurchargePercent: Int = 35,
    val holidaySurchargePercent: Int = 50,
    val nightHolidaySurchargePercent: Int = 65,
    val overtimeDaySurchargePercent: Int = 25,
    val overtimeNightSurchargePercent: Int = 40,
    val colorHex: Long = 0xFF0284C7,
    val iconKey: String = "work",
    val isDefault: Boolean = true,
    val enableAdvancedCalc: Boolean = false,
    val ccnlDocumentName: String? = null,
    val ccnlDocumentUri: String? = null,
    val maxConsecutiveWorkDays: Int = 0
) {
    val linkedCcnl: CcnlDefinition
        get() = CcnlCatalog.findByCode(ccnlCode)
}

/**
 * Rotazione ciclica (es. Turno A 28 giorni, Turno B 28 giorni).
 */
data class ShiftRotation(
    val id: String,
    val name: String,
    val totalDays: Int,
    val daySequence: List<String>, // Lista di shiftDefId ordinati per giorno
    val description: String = "",
    val workProfileId: String = "principale"
)

/**
 * Periodo festivo o festività nazionale.
 */
data class Holiday(
    val date: LocalDate,
    val name: String
)

enum class HolidayCategory {
    NAZIONALE,
    RICORRENZA,
    PATRONO
}

data class HolidayItem(
    val id: String,
    val name: String,
    val dateExample: String,
    val month: Int,
    val day: Int,
    val isEasterDependent: Boolean = false,
    val easterOffsetDays: Int = 0,
    val isCcnlExHoliday: Boolean = false,
    val isPatronSaint: Boolean = false,
    val isDefaultEnabled: Boolean = true,
    val category: HolidayCategory = when {
        isPatronSaint -> HolidayCategory.PATRONO
        isCcnlExHoliday -> HolidayCategory.RICORRENZA
        else -> HolidayCategory.NAZIONALE
    }
) {
    fun getDateForYear(year: Int): LocalDate {
        if (!isEasterDependent) {
            return LocalDate.of(year, month, day)
        }
        val easter = calculateEaster(year)
        return easter.plusDays(easterOffsetDays.toLong())
    }

    private fun calculateEaster(year: Int): LocalDate {
        // Algoritmo matematico computazionale dell'Anonimo Gregoriano
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}

object HolidaysCatalog {
    val allHolidays: List<HolidayItem> = listOf(
        // 12 Festività nazionali italiane (attive di default)
        HolidayItem("capodanno", "Capodanno", "es. 1 gen 2026", 1, 1, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("epifania", "Epifania", "es. 6 gen 2026", 1, 6, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("pasqua", "Pasqua", "es. 5 apr 2026", 4, 5, isEasterDependent = true, easterOffsetDays = 0, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("lunedi_pasqua", "Lunedì di Pasqua", "es. 6 apr 2026", 4, 6, isEasterDependent = true, easterOffsetDays = 1, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("liberazione", "Giorno della Liberazione", "es. 25 apr 2026", 4, 25, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("festa_lavoro", "Festa del Lavoro", "es. 1 mag 2026", 5, 1, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("repubblica", "Anniversario della Repubblica", "es. 2 giu 2026", 6, 2, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("ferragosto", "Ferragosto", "es. 15 ago 2026", 8, 15, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("ognissanti", "Ognissanti", "es. 1 nov 2026", 11, 1, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("immacolata", "Immacolata Concezione", "es. 8 dic 2026", 12, 8, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("natale", "Natale", "es. 25 dic 2026", 12, 25, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),
        HolidayItem("santo_stefano", "Santo Stefano", "es. 26 dic 2026", 12, 26, isDefaultEnabled = true, category = HolidayCategory.NAZIONALE),

        // Ricorrenze civili, religiose ed ex-festività CCNL
        HolidayItem("martedi_grasso", "Martedì Grasso", "es. 17 feb 2026", 2, 17, isEasterDependent = true, easterOffsetDays = -47, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("san_giuseppe", "San Giuseppe (Ex-festività CCNL)", "es. 19 mar 2026", 3, 19, isCcnlExHoliday = true, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("venerdi_santo", "Venerdì Santo", "es. 3 apr 2026", 4, 3, isEasterDependent = true, easterOffsetDays = -2, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("ascensione", "Ascensione (Ex-festività CCNL)", "es. 14 mag 2026", 5, 14, isEasterDependent = true, easterOffsetDays = 39, isCcnlExHoliday = true, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("domenica_pentecoste", "Pentecoste", "es. 24 mag 2026", 5, 24, isEasterDependent = true, easterOffsetDays = 49, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("pentecoste", "Lunedì di Pentecoste", "es. 25 mag 2026", 5, 25, isEasterDependent = true, easterOffsetDays = 50, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("corpus_domini", "Corpus Domini (Ex-festività CCNL)", "es. 4 giu 2026", 6, 4, isEasterDependent = true, easterOffsetDays = 60, isCcnlExHoliday = true, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("san_francesco", "San Francesco d'Assisi", "es. 4 ott 2026", 10, 4, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("defunti", "Commemorazione dei Defunti", "es. 2 nov 2026", 11, 2, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("unita_nazionale", "4 Novembre (Unità Nazionale)", "es. 4 nov 2026", 11, 4, isCcnlExHoliday = true, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("vigilia_natale", "Vigilia di Natale", "es. 24 dic 2026", 12, 24, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),
        HolidayItem("san_silvestro", "San Silvestro", "es. 31 dic 2026", 12, 31, isDefaultEnabled = false, category = HolidayCategory.RICORRENZA),

        // Santi Patroni delle principali città italiane
        HolidayItem("sant_ilario", "Sant'Ilario (Parma)", "es. 13 gen 2026", 1, 13, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_costanzo", "San Costanzo (Perugia)", "es. 29 gen 2026", 1, 29, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_geminiano", "San Geminiano (Modena)", "es. 31 gen 2026", 1, 31, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("sant_agata", "Sant'Agata (Catania)", "es. 5 feb 2026", 2, 5, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("santi_faustino_giovita", "Santi Faustino e Giovita (Brescia)", "es. 15 feb 2026", 2, 15, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_giorgio", "San Giorgio (Reggio Calabria, Campobasso)", "es. 23 apr 2026", 4, 23, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_marco", "San Marco (Venezia)", "es. 25 apr 2026", 4, 25, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_ciriaco", "San Ciriaco (Ancona)", "es. 4 mag 2026", 5, 4, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_zeno", "San Zeno (Verona)", "es. 21 mag 2026", 5, 21, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_gerardo", "San Gerardo (Potenza)", "es. 30 mag 2026", 5, 30, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("madonna_lettera", "Madonna della Lettera (Messina)", "es. 3 giu 2026", 6, 3, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_massimo", "San Massimo (L'Aquila)", "es. 10 giu 2026", 6, 10, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("sant_antonio_padova", "Sant'Antonio di Padova (Padova)", "es. 13 giu 2026", 6, 13, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_giovanni", "San Giovanni Battista (Torino, Firenze, Genova)", "es. 24 giu 2026", 6, 24, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_vigilio", "San Vigilio (Trento)", "es. 26 giu 2026", 6, 26, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("pietro_paolo", "Santi Pietro e Paolo (Roma)", "es. 29 giu 2026", 6, 29, isCcnlExHoliday = true, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("santa_rosalia", "Santa Rosalia (Palermo)", "es. 15 lug 2026", 7, 15, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("maria_assunta_bolzano", "Maria Assunta (Bolzano)", "es. 15 ago 2026", 8, 15, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("sant_alessandro", "Sant'Alessandro (Bergamo)", "es. 26 ago 2026", 8, 26, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_grato", "San Grato (Aosta)", "es. 7 set 2026", 9, 7, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_gennaro", "San Gennaro (Napoli)", "es. 19 set 2026", 9, 19, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_petronio", "San Petronio (Bologna)", "es. 4 ott 2026", 10, 4, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_saturnino", "San Saturnino (Cagliari)", "es. 30 ott 2026", 10, 30, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_giusto", "San Giusto (Trieste)", "es. 3 nov 2026", 11, 3, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("san_nicola", "San Nicola (Bari)", "es. 6 dic 2026", 12, 6, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO),
        HolidayItem("sant_ambrogio", "Sant'Ambrogio (Milano)", "es. 7 dic 2026", 12, 7, isPatronSaint = true, isDefaultEnabled = false, category = HolidayCategory.PATRONO)
    )
}

data class GoogleAccountProfile(
    val email: String,
    val displayName: String = "",
    val photoUrl: String? = null,
    val isConnected: Boolean = true,
    val availableCalendars: List<String> = emptyList()
)

enum class PdfExportMode(val label: String) {
    ELENCO("Elenco"),
    MESE("Mese"),
    ANNO("Anno"),
    PERSONALIZZATO("Periodo")
}

data class PdfExportOptions(
    val mode: PdfExportMode = PdfExportMode.ELENCO,
    val startDate: LocalDate = LocalDate.of(2026, 10, 1),
    val endDate: LocalDate = LocalDate.of(2026, 10, 31),
    val selectedYear: Int = 2026,
    val filterShiftId: String? = null,
    val includeEvents: Boolean = false,
    val includeIcon: Boolean = true,
    val includeTime: Boolean = true,
    val includeTitle: Boolean = true,
    val includeLocation: Boolean = true,
    val includeNotes: Boolean = true,
    val includeBreak: Boolean = false,
    val includeDuration: Boolean = false,
    val includeEmptyRows: Boolean = true,
    val includeLogo: Boolean = true,
    val headerNotes: String = ""
)

