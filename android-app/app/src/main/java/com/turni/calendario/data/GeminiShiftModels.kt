package com.turni.calendario.data

/**
 * Represents a single shift type definition extracted from a shift roster or timetable document.
 *
 * @property code Short shift code / acronym (e.g. "M", "P", "N", "MD", "SMO", "RIP").
 * @property name Descriptive shift name (e.g. "Mattina", "Pomeriggio", "Notte", "Smonto Notte").
 * @property startTime Start time in "HH:mm" 24-hour format (e.g. "06:00").
 * @property endTime End time in "HH:mm" 24-hour format (e.g. "13:36").
 * @property colorHex Optional hexadecimal color code (e.g. "#0284C7" or "0xFF0284C7").
 * @property isReperibilita Indicates if this shift implies on-call / reperibilità availability.
 */
data class GeminiShiftDefinition(
    val code: String,
    val name: String,
    val startTime: String,
    val endTime: String,
    val colorHex: String? = null,
    val isReperibilita: Boolean = false
)

/**
 * Represents a squad or team and its recurring sequence of shift codes.
 *
 * @property squadName The name or identifier of the squad (e.g. "Squadra A", "Squadra B", "Turno 1").
 * @property rotationDays The ordered sequence of shift codes representing one complete cycle.
 * @property employees Names of workers or personnel assigned to this squad.
 * @property cycleDays Total days in one cycle (defaults to rotationDays.size).
 */
data class GeminiSquadRotation(
    val squadName: String,
    val rotationDays: List<String>,
    val employees: List<String> = emptyList(),
    val cycleDays: Int = rotationDays.size
)

/**
 * Represents individual worker assignments if provided in the document.
 *
 * @property name Full name or surname of the worker.
 * @property squadName Assigned squad or group name.
 * @property role Role or qualification (e.g. "Specialista", "Tecnico", "Capotreno").
 * @property employeeId Optional badge or matricola number.
 * @property isOnCall Whether the worker has active on-call / reperibilità duty.
 * @property dailyShifts Date-to-shift mapping if explicit dates are scheduled (e.g. "2026-10-15" -> "M").
 */
data class GeminiEmployeeAssignment(
    val name: String,
    val squadName: String? = null,
    val role: String? = null,
    val employeeId: String? = null,
    val isOnCall: Boolean = false,
    val dailyShifts: Map<String, String> = emptyMap()
)

/**
 * Root data class representing the complete structured extraction result from Gemini Flash Vision.
 *
 * @property shiftDefinitions List of detected shift definitions and hours.
 * @property squads List of detected teams/squads and their cycle rotations.
 * @property notes Additional annotations, rules or on-call instructions found in the document.
 * @property validityStart Optional validity start date (ISO-8601 "YYYY-MM-DD").
 * @property validityEnd Optional validity end date (ISO-8601 "YYYY-MM-DD").
 * @property employees Detailed employee assignments if present in the document.
 * @property rawJson The raw JSON response string from Gemini for inspection/debugging.
 */
data class GeminiShiftData(
    val shiftDefinitions: List<GeminiShiftDefinition> = emptyList(),
    val squads: List<GeminiSquadRotation> = emptyList(),
    val notes: String? = null,
    val validityStart: String? = null,
    val validityEnd: String? = null,
    val employees: List<GeminiEmployeeAssignment> = emptyList(),
    val rawJson: String? = null
)
