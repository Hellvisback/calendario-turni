package com.turni.calendario.ui.screens

import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.model.sortedChronologically

internal data class ShiftMetric(val definition:ShiftDefinition,val count:Int,val hours:Float)

internal fun AssignedShift.effectiveOvertimeHours(): Float {
    val legacy = if (overtimeHours.isFinite()) overtimeHours.coerceAtLeast(0f) else 0f
    val start = if (startOvertimeHours.isFinite()) startOvertimeHours.coerceAtLeast(0f) else 0f
    val end = if (endOvertimeHours.isFinite()) endOvertimeHours.coerceAtLeast(0f) else 0f
    return maxOf(legacy, start + end)
}

internal fun AssignedShift.effectivePermissionHours(): Float {
    val legacy = if (permissionHours.isFinite()) permissionHours.coerceAtLeast(0f) else 0f
    val start = if (startPermissionHours.isFinite()) startPermissionHours.coerceAtLeast(0f) else 0f
    val end = if (endPermissionHours.isFinite()) endPermissionHours.coerceAtLeast(0f) else 0f
    return maxOf(legacy, start + end)
}

/** Identity follows the model ID, never a translated name, abbreviation or category. */
internal fun reportMetrics(assignments:List<AssignedShift>,definitions:List<ShiftDefinition>):List<ShiftMetric> {
    val byDefinition=assignments.groupBy { it.shiftDefId }
    return definitions.sortedChronologically().mapNotNull { definition ->
        val matching=byDefinition[definition.id].orEmpty()
        if(matching.isEmpty()) null else ShiftMetric(definition,matching.size,
            if(definition.category==ShiftCategory.LAVORO) matching.sumOf { shift ->
                val baseHours = definition.copy(
                    startTime = shift.customStartTime ?: definition.startTime,
                    endTime = shift.customEndTime ?: definition.endTime
                ).durationHoursFloat
                val perm = if (definition.isAllDay) 0f else shift.effectivePermissionHours().coerceIn(0f, baseHours)
                (baseHours - perm).toDouble() + shift.effectiveOvertimeHours()
            }.toFloat() else 0f)
    }
}

/** No Formatter/locale allocations on animation frames; unchanged values reuse their label. */
internal class ReportCountLabel(private val decimal:Boolean,private val suffix:String) {
    private var previous:Int?=null
    private var label=""
    fun at(step:Int):String {
        if(previous!=step){
            previous=step
            val magnitude=kotlin.math.abs(step.toLong())
            label=(if(decimal)(if(step<0)"-" else "")+"${magnitude/10},${magnitude%10}" else step.toString())+suffix
        }
        return label
    }
}
