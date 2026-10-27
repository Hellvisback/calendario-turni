package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.widget.RemoteViews
import com.turni.calendario.R
import com.turni.calendario.repository.LocalDatabaseStore
import com.turni.calendario.repository.ShiftRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/** Static rasterization keeps RemoteViews crisp at native density within a bounded bitmap budget. */
internal object TurniWidgetRenderer {
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
    private val shortMonthFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

    fun update(context: Context, manager: AppWidgetManager, kind: WidgetKind, id: Int) {
        val dark = com.turni.calendario.ui.theme.TurniThemeState.isDark(context)
        val palette = Palette(dark)
        val options = manager.getAppWidgetOptions(id)
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
            kind.defaultWidth).takeIf { it > 0 }?.coerceIn(120, 700) ?: kind.defaultWidth
        val height = options.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            kind.defaultHeight).takeIf { it > 0 }?.coerceIn(68, 800) ?: kind.defaultHeight
        val today = LocalDate.now()
        val offset = WidgetUpdateManager.getWidgetOffset(context, id)
        val month = YearMonth.from(today).plusMonths(offset.toLong())
        val start: LocalDate
        val rowCount: Int
        val columns: Int
        when (kind) {
            WidgetKind.TODAY -> { start = today; rowCount = 2; columns = 1 }
            WidgetKind.WEEK -> { start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong()); rowCount = 1; columns = 7 }
            WidgetKind.PANORAMA -> { start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong()); rowCount = 2; columns = 7 }
            WidgetKind.MONTH -> {
                val first = month.atDay(1)
                start = first.minusDays((first.dayOfWeek.value - 1).toLong())
                rowCount = ((first.dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7)
                columns = 7
            }
        }
        val views = RemoteViews(context.packageName, kind.layout)
        views.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.bg_widget_turnisync_dark else R.drawable.bg_widget_turnisync_light)
        val header = widgetHeaderLayout(width.toFloat(), kind == WidgetKind.TODAY)
        val title = when (kind) {
            WidgetKind.TODAY -> "Oggi e domani"
            WidgetKind.MONTH -> month.format(monthFormat).replaceFirstChar { it.uppercase() }
            else -> "${start.format(shortMonthFormat)} – ${start.plusDays((rowCount * 7 - 1).toLong()).format(shortMonthFormat)}"
        }
        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, palette.text)
        views.setTextViewText(R.id.widget_profile, LocalDatabaseStore.loadError?.let { "Dati da ripristinare · Apri TurniSync" }
            ?: ShiftRepository.activeWorkProfile.name)
        views.setTextColor(R.id.widget_profile, palette.muted)
        views.setViewVisibility(R.id.widget_profile, if (header.showWork) View.VISIBLE else View.GONE)
        views.setInt(R.id.widget_title, "setMaxWidth", (header.titleMaxWidthDp * context.resources.displayMetrics.density).toInt())
        views.setContentDescription(R.id.widget_profile, ShiftRepository.activeWorkProfile.name)
        views.setContentDescription(R.id.widget_title, title)
        views.setInt(R.id.widget_previous, "setColorFilter", palette.brand)
        views.setInt(R.id.widget_next, "setColorFilter", palette.brand)
        views.setViewVisibility(R.id.widget_previous, if (kind == WidgetKind.TODAY) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_next, if (kind == WidgetKind.TODAY) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_previous, WidgetUpdateManager.createNavPendingIntent(context, id, WidgetUpdateManager.ACTION_NAV_PREV, kind.provider))
        views.setOnClickPendingIntent(R.id.widget_next, WidgetUpdateManager.createNavPendingIntent(context, id, WidgetUpdateManager.ACTION_NAV_NEXT, kind.provider))
        views.setOnClickPendingIntent(R.id.widget_title, WidgetUpdateManager.createNavPendingIntent(context, id, WidgetUpdateManager.ACTION_NAV_RESET, kind.provider))
        views.setOnClickPendingIntent(R.id.widget_profile, WidgetUpdateManager.createDayPendingIntent(context, today, id * 100 + 99))
        views.removeAllViews(R.id.widget_rows)
        val headerHeight = if(kind == WidgetKind.TODAY)22f else 32f
        val cellWidth = (width - 16f) / columns
        val cellHeight = widgetDayCellHeight(height.toFloat(),rowCount,kind == WidgetKind.TODAY)
        // Render at native density on normal hosts; cap the whole bitmap payload for oversized widgets.
        val area = ((width - 16f) * (height - 8f - headerHeight)).coerceAtLeast(1f)
        val density = min(context.resources.displayMetrics.density, sqrt(1_500_000f / area)).coerceAtLeast(1f)
        val fontScale = context.resources.configuration.fontScale.coerceIn(1f, 1.2f)
        views.setViewVisibility(R.id.widget_weekdays, if(kind==WidgetKind.TODAY)View.GONE else View.VISIBLE)
        if(kind!=WidgetKind.TODAY){
            val labels=Bitmap.createBitmap(ceil((width-16f)*density).toInt(),ceil(10f*density).toInt(),Bitmap.Config.ARGB_8888)
            val c=Canvas(labels);c.scale(density,density)
            val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=palette.muted;textSize=9f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);textAlign=Paint.Align.CENTER }
            listOf("LUN","MAR","MER","GIO","VEN","SAB","DOM").forEachIndexed{i,label->c.drawText(label,(i+.5f)*cellWidth,8.5f,pen)}
            views.setImageViewBitmap(R.id.widget_weekdays,labels)
        }
        for (rowIndex in 0 until rowCount) {
            val row = RemoteViews(context.packageName, R.layout.widget_turnisync_row)
            for (column in 0 until columns) {
                val index = rowIndex * columns + column
                val day = start.plusDays(index.toLong())
                val summary = WidgetUpdateManager.getDaySummary(day, today)
                val cell = RemoteViews(context.packageName, R.layout.widget_turnisync_cell)
                cell.setImageViewBitmap(R.id.widget_day,
                    renderDay(summary, cellWidth, cellHeight, density, fontScale, palette,
                        kind == WidgetKind.TODAY, kind == WidgetKind.MONTH && YearMonth.from(day) != month,
                        LocalDatabaseStore.loadError != null, showWeekday = false))
                cell.setContentDescription(R.id.widget_day, summary.accessibilityLabel)
                cell.setOnClickPendingIntent(R.id.widget_day, WidgetUpdateManager.createDayPendingIntent(context, day, id * 100 + index))
                row.addView(R.id.widget_row, cell)
            }
            views.addView(R.id.widget_rows, row)
        }
        manager.updateAppWidget(id, views)
    }

    private data class Palette(val dark: Boolean) {
        val surface = color(if (dark) "#1A1C22" else "#FFFFFF")
        val text = color(if (dark) "#F4F3F7" else "#1C2B43")
        val muted = color(if (dark) "#BEC1CD" else "#56657A")
        val weekend = color(if (dark) "#22242E" else "#E6EDF7")
        val today = color(if (dark) "#323040" else "#E7E2FF")
        val brand = color(if (dark) "#BCB8FF" else "#5743A4")
        val onCall = color(if (dark) "#F4CA70" else "#805100")
        val holiday = com.turni.calendario.ui.theme.holidayDayColor(dark).toInt()
    }

    private fun renderDay(summary: DayShiftSummary, width: Float, height: Float, density: Float,
        fontScale: Float, palette: Palette, wide: Boolean, faded: Boolean, invalidData: Boolean, showWeekday: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(ceil(width * density).toInt().coerceAtLeast(1),
            ceil(height * density).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(density, density)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fun rect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, fill: Int) {
            paint.color = fill
            canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, paint)
        }
        fun text(value: String, x: Float, baseline: Float, size: Float, ink: Int, centered: Boolean = false, maxWidth: Float = width - 4f) {
            paint.typeface = medium
            paint.textSize = size * fontScale
            val available = maxWidth.coerceAtLeast(1f)
            if (paint.measureText(value) > available && value.length <= 5) {
                paint.textSize = (paint.textSize * available / paint.measureText(value)).coerceAtLeast(8.5f)
            }
            paint.color = if (faded) mix(ink, palette.surface, .48f) else ink
            val measured = TextUtils.ellipsize(value, android.text.TextPaint(paint), maxWidth.coerceAtLeast(1f), TextUtils.TruncateAt.END).toString()
            paint.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
            canvas.drawText(measured, x, baseline, paint)
        }
        fun dayNumber(x:Float,baseline:Float,size:Float,maxWidth:Float,box:RectF) {
            // A tight, shared date capsule leaves a gap to the shift chip and bell.
            val halfWidth=(size*fontScale*.28f*summary.dayNumber.toString().length+1f).coerceAtMost((box.width()-1f)/2f)
            val top=maxOf(box.top,baseline-size*fontScale-1f)
            val bottom=minOf(box.bottom,baseline+2f)
            val capsule=RectF(x-halfWidth,top,x+halfWidth,bottom)
            if(summary.isToday) rect(capsule.left,capsule.top,capsule.right,capsule.bottom,4f,palette.brand)
            if(summary.holidayName!=null){
                paint.color=palette.holiday;paint.style=Paint.Style.STROKE;paint.strokeWidth=1f
                val side=minOf(size*fontScale+5f,box.height(),width-16f)
                val holidayBox=RectF(x-side/2f,(top+bottom-side)/2f,x+side/2f,(top+bottom+side)/2f)
                canvas.drawRoundRect(holidayBox,3f,3f,paint);paint.style=Paint.Style.FILL
            }
            val ink=when {
                summary.isOnCall -> if(summary.isToday) color(if(palette.dark) "#493000" else "#FFF0BA") else palette.onCall
                summary.isToday -> if(palette.dark) color("#211D38") else Color.WHITE
                else -> palette.text
            }
            text(summary.dayNumber.toString(),x,baseline,size,ink,true,maxWidth)
        }
        val background = when {
            summary.isToday -> palette.today
            summary.date.dayOfWeek.value >= 6 -> palette.weekend
            else -> palette.surface
        }
        // Weekend tint is independent from the text fading of adjacent months.
        if (!wide) rect(1f, 1f, width - 1f, height - 1f, 8f, background)
        if (!wide && height < 27f) {
            // At legacy two-row host heights, use one horizontal line rather than tiny stacked labels.
            val baseline = height / 2f + 4f
            val numberX = 9f
            dayNumber(numberX,baseline,10.5f,17f,RectF(1f,height/2f-8f,18f,height/2f+8f))
            val shift = summary.shifts.firstOrNull()
            if (shift != null && !invalidData) {
                val tint = mix(shift.definition.colorHex.toInt(), background, if (palette.dark) .62f else .26f)
                val fill = if (faded) mix(tint, background, .5f) else tint
                rect(19f, height / 2f - 8f, width - 1f, height / 2f + 8f, 3f, fill)
                text(shift.definition.shortCode.take(4), (width + 18f) / 2f, baseline, 10f,
                    contrastInk(tint), true, width - 21f)
                if (summary.shifts.size > 1) {
                    paint.color = summary.shifts[1].definition.colorHex.toInt()
                    canvas.drawCircle(width - 3f, height - 2f, 1.8f, paint)
                }
            }
            if (summary.isOnCall) drawBell(canvas, paint, width - 4f, 4f, 7f, palette.onCall)
            return bitmap
        }
        if (!wide && height < 34f) {
            // A compact month uses one readable code, without squeezing two illegible text lines.
            val numberSize = if (height < 25f) 10f else 11f
            val numberBaseline = if (height < 25f) 10f else 12f
            dayNumber(width/2f,numberBaseline,numberSize,22f,RectF(width/2f-11f,1f,width/2f+11f,numberBaseline+3f))
            if(showWeekday)text(summary.dayNameShort,1f,10f,8f,palette.muted,maxWidth=8f)
            if (summary.isOnCall) drawBell(canvas, paint, width - 6f, 7f, 8f, palette.onCall)
            val shift = summary.shifts.firstOrNull()
            if (shift != null && !invalidData) {
                val tint = mix(shift.definition.colorHex.toInt(), background, if (palette.dark) .62f else .26f)
                val fill = if (faded) mix(tint, background, .5f) else tint
                val top = numberBaseline + 4f
                rect(2f, top, width - 2f, height - 1f, 3f, fill)
                text(shift.definition.shortCode.take(4), width / 2f, height - 3f,
                    if (height < 25f) 9f else 10.5f, contrastInk(tint), true, width - 6f)
                if (summary.shifts.size > 1) {
                    paint.color = summary.shifts[1].definition.colorHex.toInt()
                    canvas.drawCircle(3f, height - 3f, 1.6f, paint)
                }
            }
            return bitmap
        }
        val headerHeight = if(wide)widgetDateHeaderHeight(true) else if(height<48f)18f else 21f
        if (wide) {
            val label = if (summary.isToday) "Oggi" else "Domani"
            val numberX=if(summary.isToday)53f else 78f
            text(label,9f,19f,12f,if(summary.isToday)palette.brand else palette.muted,maxWidth=numberX-15f)
            dayNumber(numberX+6f,19f,14.5f,24f,RectF(numberX-6f,2f,numberX+18f,23f))
        } else {
            // The weekday and today's number never share a fixed-width highlight.
            val numberX = width / 2f
            val numberSize = if(height<48f)12.5f else 13.5f
            val baseline = if(height<48f)14f else 17f
            dayNumber(numberX,baseline,numberSize,width-12f,RectF(numberX-13f,1f,numberX+13f,headerHeight-1f))
        }
        if (summary.isOnCall) drawBell(canvas, paint, width - (if (wide) 17f else 6f),
            if (wide) 14f else headerHeight/2f, if (wide) 12f else 8f, if (faded) mix(palette.onCall, background, .4f) else palette.onCall)
        if (invalidData) { text("—", width / 2f, height / 2f + 9f, 12f, palette.muted, true); return bitmap }
        if (summary.shifts.isEmpty()) {
            if (wide) text("Nessun turno", 9f, headerHeight + 19f, 12f, palette.muted, maxWidth = width - 18f)
            return bitmap
        }
        val gap = 3f
        val remaining = height - headerHeight - 4f
        if (wide) {
            val count = min(summary.shifts.size, if (remaining >= 66f) 3 else if (remaining >= 40f) 2 else 1)
            val slotHeight = ((remaining - (count - 1) * gap) / count).coerceAtMost(50f)
            summary.shifts.take(count).forEachIndexed { index, shift ->
                val y = headerHeight + index * (slotHeight + gap)
                val chipColor = mix(shift.definition.colorHex.toInt(), background, if (palette.dark) .62f else .26f)
                val ink = contrastInk(chipColor)
                rect(5f, y, width - 5f, y + slotHeight, 9f, chipColor)
                val code = shift.definition.shortCode.take(4)
                val codeHeight=min(32f,slotHeight-6f)
                val badge=shift.definition.colorHex.toInt()
                rect(9f,y+3f,47f,y+3f+codeHeight,7f,badge)
                text(code,28f,y+3f+codeHeight/2f+4f,12f,contrastInk(badge),true,34f)
                val detailX=55f
                text(shift.definition.name,detailX,y+slotHeight/2f-3f,11f,ink,maxWidth=width-detailX-10f)
                text(shift.startTime,detailX,y+slotHeight/2f+12f,12.5f,ink,maxWidth=width-detailX-40f)


            }
            drawOverflow(canvas, paint, summary.shifts.drop(count), width - 20f, height - 4f)
            return bitmap
        }
        val availableHeight=height-headerHeight-2f
        val capacity=if(summary.shifts.size>1 && availableHeight>=58f)2 else 1
        val baseChip=((availableHeight-(capacity-1)*gap)/capacity).coerceIn(17f,29f)
        var cursor = headerHeight
        var visible = 0
        for (shift in summary.shifts) {
            if (height - cursor < baseChip + 2f) break
            val tint=mix(shift.definition.colorHex.toInt(),background,if(palette.dark).62f else .26f)
            val fill=if(faded)mix(tint,background,.5f)else tint
            val ink=contrastInk(tint)
            rect(3f,cursor,width-3f,cursor+baseChip,5f,fill)
            text(shift.definition.shortCode.take(4),width/2f,cursor+baseChip/2f-1f,if(baseChip<26f)10.5f else 11.5f,ink,true,width-7f)
            if(baseChip>=20f)text(shift.startTime,width/2f,cursor+baseChip/2f+(if(baseChip<26f)9f else 10.5f),if(baseChip<26f)10f else 10.5f,ink,true,width-7f)
            cursor += baseChip + gap
            visible++
        }
        drawOverflow(canvas, paint, summary.shifts.drop(visible), width / 2f, height - 3f)
        return bitmap
    }

    private fun drawOverflow(canvas: Canvas, paint: Paint, shifts: List<WidgetShift>, x: Float, y: Float) {
        if (shifts.isEmpty()) return
        // All shifts remain accessible on tap; reserve dots instead of clipping a partial badge.
        val shown = min(3, shifts.size)
        for (index in 0 until shown) {
            paint.color = shifts[index].definition.colorHex.toInt()
            canvas.drawCircle(x + (index - (shown - 1) / 2f) * 6f, y, 2f, paint)
        }
    }

    private fun drawBell(canvas: Canvas, paint: Paint, x: Float, y: Float, size: Float, ink: Int) {
        canvas.save()
        canvas.translate(x - size / 2f, y - size / 2f)
        canvas.scale(size / 24f, size / 24f)
        paint.color = ink; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        val path = Path().apply {
            moveTo(5f, 17f); lineTo(7f, 14f); lineTo(7f, 9f)
            cubicTo(7f, 2f, 17f, 2f, 17f, 9f)
            lineTo(17f, 14f); lineTo(19f, 17f); close()
        }
        canvas.drawPath(path, paint)
        canvas.drawArc(RectF(10f, 18f, 14f, 21f), 0f, 180f, false, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun color(hex: String) = Color.parseColor(hex)
    private fun mix(first: Int, second: Int, ratio: Float): Int = Color.rgb(
        (Color.red(first) * (1f - ratio) + Color.red(second) * ratio).toInt(),
        (Color.green(first) * (1f - ratio) + Color.green(second) * ratio).toInt(),
        (Color.blue(first) * (1f - ratio) + Color.blue(second) * ratio).toInt())
    private fun contrastInk(background: Int): Int {
        fun component(value: Int): Double { val unit = value / 255.0; return if (unit <= .04045) unit / 12.92 else Math.pow((unit + .055) / 1.055, 2.4) }
        val luminance = .2126 * component(Color.red(background)) + .7152 * component(Color.green(background)) + .0722 * component(Color.blue(background))
        return if ((luminance + .05) / .05 > 1.05 / (luminance + .05)) Color.BLACK else Color.WHITE
    }
}
