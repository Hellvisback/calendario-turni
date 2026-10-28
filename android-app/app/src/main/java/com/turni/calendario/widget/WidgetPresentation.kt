package com.turni.calendario.widget

import java.time.YearMonth

/** One immutable data read is shared by every size advertised by the launcher. */
internal data class WidgetPresentation(
    val title:String,
    val workName:String,
    val rows:Int,
    val columns:Int,
    val month:YearMonth?,
    val days:List<DayShiftSummary>,
    val invalidData:Boolean=false
)
