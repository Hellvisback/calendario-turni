package com.turni.calendario.model

/** Count the recorded original model only for these explicit replacements. No name inference or chains. */
data class StatisticsRule(
    val id: String,
    val displayedShiftId: String,
    val originalShiftIds: Set<String>,
    val enabled: Boolean = true
)

fun statisticsAssignment(shift: AssignedShift, definitions: Map<String, ShiftDefinition>, rules: List<StatisticsRule>): AssignedShift {
    val original = shift.originalShiftDefId?.let { definitions[it] } ?: return shift
    val displayed = definitions[shift.shiftDefId] ?: return shift
    if (displayed.workProfileId != original.workProfileId) return shift
    if (rules.none { it.enabled && it.displayedShiftId == shift.shiftDefId && original.id in it.originalShiftIds }) return shift
    // Actual overtime and notes remain attached to the assignment; template overrides belong to the visible model.
    return shift.copy(shiftDefId=original.id, customStartTime=null, customEndTime=null)
}

/** Preserve the pre-existing rule when reading a backup written before configurable rules existed. */
fun legacyStatisticsRules(definitions: List<ShiftDefinition>): List<StatisticsRule> {
    val holiday = definitions.firstOrNull { it.id == "ferie" } ?: return emptyList()
    val originals = definitions.filter { it.id in setOf("smonto_notte", "riposo") && it.workProfileId == holiday.workProfileId }.map { it.id }.toSet()
    return if (originals.isEmpty()) emptyList() else listOf(StatisticsRule("legacy-ferie-rest", holiday.id, originals))
}

fun validateStatisticsRules(rules: List<StatisticsRule>, definitions: List<ShiftDefinition>) {
    val byId=definitions.associateBy { it.id }
    require(rules.size<=1000 && rules.map { it.id }.distinct().size==rules.size && rules.none { it.id.isBlank() }) { "Regole statistiche duplicate o non valide" }
    require(rules.map { it.displayedShiftId }.distinct().size==rules.size) { "Un modello può avere una sola regola di conteggio" }
    require(rules.all { rule -> val displayed=byId[rule.displayedShiftId]
        displayed!=null && rule.originalShiftIds.isNotEmpty() && rule.displayedShiftId !in rule.originalShiftIds &&
            rule.originalShiftIds.all { byId[it]?.workProfileId==displayed.workProfileId }
    }) { "Seleziona modelli diversi appartenenti allo stesso lavoro" }
}

/** Original-model rules take precedence; only the resulting selected vacation model is excluded. */
fun countedStatisticsAssignment(shift:AssignedShift,definitions:Map<String,ShiftDefinition>,rules:List<StatisticsRule>,
    configuredHoliday:Boolean,excludeVacationOnHolidays:Boolean,excludedModelIds:Set<String>):AssignedShift? {
    val resolved=statisticsAssignment(shift,definitions,rules)
    return resolved.takeUnless{configuredHoliday&&excludeVacationOnHolidays&&it.shiftDefId in excludedModelIds}
}

/** A suggested model is selected only when enabling, never while disabling the option. */
fun holidayExclusionSelectionForToggle(current:Set<String>,definitions:List<ShiftDefinition>,enabling:Boolean):Set<String> =
    if(enabling&&current.isEmpty())definitions.filter{it.id=="ferie"}.map{it.id}.toSet()else current
