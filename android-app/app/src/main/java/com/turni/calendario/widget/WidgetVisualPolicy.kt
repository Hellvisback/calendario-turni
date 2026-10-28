package com.turni.calendario.widget

internal data class WidgetHeaderLayout(val titleMaxWidthDp:Float,val showWork:Boolean)

/** Navigation targets stay adjacent to the date, with a bounded name on the trailing edge. */
internal fun widgetHeaderLayout(widthDp:Float,today:Boolean):WidgetHeaderLayout {
    val content=(widthDp-16f).coerceAtLeast(1f)
    val navigation=if(today)0f else 64f
    val showWork=if(today)widthDp>=260f else content-navigation>=136f
    val work=if(showWork)(content*.26f).coerceIn(44f,100f)+8f else 0f
    return WidgetHeaderLayout((content-navigation-work).coerceAtLeast(24f),showWork)
}

internal fun widgetDayCellHeight(heightDp:Float,rows:Int,wide:Boolean=false,navigationHeightDp:Float=22f):Float =
    ((heightDp-8f-navigationHeightDp-(if(wide)0f else 10f))/rows.coerceAtLeast(1)).coerceAtLeast(20f)
internal fun widgetDateHeaderHeight(wide:Boolean):Float =
    if(wide)29f else 22f

internal fun widgetShiftBaseHeight(heightDp:Float):Float {
    val remaining=heightDp-widgetDateHeaderHeight(false)-4f
    return remaining.coerceIn(16f,52f)
}

internal data class WidgetDailyShiftLayout(val badgeWidth:Float,val detailX:Float,val detailWidth:Float,val showName:Boolean)

internal fun widgetDailyShiftLayout(width:Float,slotHeight:Float,fontScale:Float):WidgetDailyShiftLayout {
    val badge=(width*.34f).coerceIn(24f,38f)
    val start=9f+badge+8f
    return WidgetDailyShiftLayout(badge,start,(width-start-9f).coerceAtLeast(1f),width>=145f&&slotHeight>=35f*fontScale)
}
