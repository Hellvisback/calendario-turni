package com.turni.calendario

import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.ShiftRotation
import com.turni.calendario.model.WorkProfile
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.repository.sequenceTurnoA

internal fun seedLegacyDemoRepository() {
    ShiftRepository.workProfiles.clear()
    ShiftRepository.workProfiles.add(
        WorkProfile(
            id = "principale",
            name = "Lavoro Principale",
            weeklyHours = 38,
            minRestHoursBetweenShifts = 11,
            maxConsecutiveWorkDays = 6,
            isDefault = true
        )
    )
    ShiftRepository.activeWorkProfileId = "principale"
    ShiftRepository.shiftDefinitions.clear()
    ShiftRepository.shiftDefinitions.addAll(
        listOf(
            ShiftDefinition("pomeriggio", "Pomeriggio", "POM", "14:00", "21:36", false, colorHex = 0xFFEE9C9C, iconKey = "wb_sunny", category = ShiftCategory.LAVORO),
            ShiftDefinition("mattina", "Mattina", "MAT", "06:00", "13:36", false, colorHex = 0xFFF2C46D, iconKey = "wb_twilight", category = ShiftCategory.LAVORO),
            ShiftDefinition("notte", "Notte", "NOT", "22:00", "05:36", false, colorHex = 0xFFBAAEF6, iconKey = "nightlight_round", category = ShiftCategory.LAVORO),
            ShiftDefinition("mat_dom", "Mat Dom", "DOM", "08:36", "16:12", false, colorHex = 0xFFF2C46D, iconKey = "wb_sunny", category = ShiftCategory.LAVORO),
            ShiftDefinition("smonto_notte", "Smonto Notte", "SMO", "05:36", "05:36", true, colorHex = 0xFF83CDB7, iconKey = "bedtime", category = ShiftCategory.RIPOSO),
            ShiftDefinition("riposo", "Riposo", "RIP", "00:00", "23:59", true, colorHex = 0xFFAAB9CE, iconKey = "weekend", category = ShiftCategory.RIPOSO),
            ShiftDefinition("ferie", "Ferie", "FER", "00:00", "23:59", true, colorHex = 0xFF94C4EF, iconKey = "beach_access", category = ShiftCategory.ASSENZA),
            ShiftDefinition("giornaliero", "Giornaliero", "GIO", "08:30", "17:06", false, breakMinutes = 60, colorHex = 0xFF83CDB7, iconKey = "schedule", category = ShiftCategory.LAVORO),
            ShiftDefinition("uffici", "Uffici", "UFF", "07:30", "15:36", false, colorHex = 0xFFAAB9CE, iconKey = "business", category = ShiftCategory.LAVORO),
            ShiftDefinition("donazione", "Donazione", "DON", "00:00", "23:59", true, colorHex = 0xFFF2C46D, iconKey = "volunteer_activism", category = ShiftCategory.ASSENZA),
            ShiftDefinition("malattia", "Malattia", "MAL", "00:00", "23:59", true, colorHex = 0xFF94C4EF, iconKey = "local_hospital", category = ShiftCategory.ASSENZA)
        )
    )
    ShiftRepository.rotations.clear()
    ShiftRepository.rotations.addAll(
        listOf(
            ShiftRotation("turno_a", "Turno A", 28, sequenceTurnoA, "Ciclo 28 giorni"),
            ShiftRotation("turno_b", "Turno B", 28, sequenceTurnoA.drop(7) + sequenceTurnoA.take(7), "Ciclo sfasato di 28 giorni")
        )
    )
    ShiftRepository.activeRotationId = "turno_a"
    ShiftRepository.defaultRotationId = "turno_a"
}
