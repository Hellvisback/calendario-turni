package com.turni.calendario.ui.screens

import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.ShiftCategory
import com.turni.calendario.model.ShiftDefinition

internal data class ShiftMetric(val definition:ShiftDefinition,val count:Int,val hours:Float)

/** Identity follows the model ID, never a translated name, abbreviation or category. */
internal fun reportMetrics(assignments:List<AssignedShift>,definitions:List<ShiftDefinition>):List<ShiftMetric> {
    val byDefinition=assignments.groupBy { it.shiftDefId }
    return definitions.mapNotNull { definition ->
        val matching=byDefinition[definition.id].orEmpty()
        if(matching.isEmpty()) null else ShiftMetric(definition,matching.size,
            if(definition.category==ShiftCategory.LAVORO) matching.sumOf { shift ->
                definition.copy(startTime=shift.customStartTime?:definition.startTime,
                    endTime=shift.customEndTime?:definition.endTime).durationHoursFloat.toDouble()+shift.overtimeHours
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
