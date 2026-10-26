package com.turni.calendario.widget

internal data class WidgetHeaderLayout(val titleMaxWidthDp:Float,val showWork:Boolean)

/** Navigation targets stay adjacent to the date, with a bounded name on the trailing edge. */
internal fun widgetHeaderLayout(widthDp:Float,today:Boolean):WidgetHeaderLayout {
    val content=(widthDp-16f).coerceAtLeast(1f)
    val navigation=if(today)0f else 64f
    val showWork=content-navigation>=136f
    val work=if(showWork)(content*.26f).coerceIn(44f,100f)+8f else 0f
    return WidgetHeaderLayout((content-navigation-work).coerceAtLeast(24f),showWork)
}

internal fun widgetDayCellHeight(heightDp:Float,rows:Int):Float =
    ((heightDp-8f-32f)/rows.coerceAtLeast(1)).coerceAtLeast(20f)
internal fun widgetDateHeaderHeight(wide:Boolean):Float =
    if(wide)25f else 14f

internal fun widgetShiftBaseHeight(heightDp:Float):Float {
    val remaining=heightDp-widgetDateHeaderHeight(false)-4f
    return if(remaining<34f)remaining.coerceAtLeast(16f)else 35f
}
