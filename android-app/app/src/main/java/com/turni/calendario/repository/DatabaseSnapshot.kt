package com.turni.calendario.repository

import com.turni.calendario.model.*
import java.time.LocalDate

/** Immutable capture: Compose observes state on main; JSON and disk IO run off main. */
data class DatabaseSnapshot(
    val definitions: List<ShiftDefinition>,
    val rotations: List<ShiftRotation>,
    val shifts: Map<LocalDate, List<AssignedShift>>,
    val extras: Map<LocalDate, DayExtraInfo>,
    val profiles: List<WorkProfile>,
    val activeWorkProfileId: String,
    val activeRotationId: String,
    val defaultRotationId: String,
    val preferences: AppPreferences,
    val onCall: ReperibilitaConfig,
    val alarms: SmartAlarmsConfig,
    val holidayIds: Set<String>,
    val statisticsRules: List<StatisticsRule> = emptyList()
) {
    companion object {
        fun capture() = DatabaseSnapshot(
            ShiftRepository.shiftDefinitions.toList(),
            ShiftRepository.rotations.toList(),
            ShiftRepository.calendarShifts.mapValues { it.value.toList() },
            ShiftRepository.dayExtraInfo.toMap(),
            ShiftRepository.workProfiles.toList(),
            ShiftRepository.activeWorkProfileId,
            ShiftRepository.activeRotationId,
            ShiftRepository.defaultRotationId,
            ShiftRepository.appPreferences,
            ShiftRepository.reperibilitaConfig,
            ShiftRepository.smartAlarmsConfig,
            ShiftRepository.enabledHolidayIds.toSet(),
            ShiftRepository.statisticsRules.toList()
        )
    }
}
