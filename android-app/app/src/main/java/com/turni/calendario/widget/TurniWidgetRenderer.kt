package com.turni.calendario.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.SizeF
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
        val options=manager.getAppWidgetOptions(id)
        val portrait=context.resources.configuration.orientation!=Configuration.ORIENTATION_LANDSCAPE
        val width=options.getInt(if(portrait)AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,kind.defaultWidth).takeIf{it>0}?:kind.defaultWidth
        val height=options.getInt(if(portrait)AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,kind.defaultHeight).takeIf{it>0}?:kind.defaultHeight
        val dark=com.turni.calendario.ui.theme.TurniThemeState.isDark(context)
        val content=collectContent(kind,LocalDate.now(),WidgetUpdateManager.getWidgetOffset(context,id))
        val sizes=if(Build.VERSION.SDK_INT>=31) options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            ?.filter{it.width.isFinite()&&it.height.isFinite()&&it.width in 80f..1000f&&it.height in 40f..1000f}?.distinct()?.take(4).orEmpty() else emptyList()
        val views=if(Build.VERSION.SDK_INT>=31&&sizes.isNotEmpty()) {
            RemoteViews(sizes.associateWith{size->createViews(context,kind,id,size.width.toInt(),size.height.toInt(),content,dark,1_500_000f/sizes.size)})
        }else createViews(context,kind,id,width.coerceIn(80,1000),height.coerceIn(40,1000),content,dark)
        manager.updateAppWidget(id,views)
    }

    private fun collectContent(kind:WidgetKind,today:LocalDate,offset:Int):WidgetPresentation {
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
        val title = when (kind) {
            WidgetKind.TODAY -> "Oggi e domani"
            WidgetKind.MONTH -> month.format(monthFormat).replaceFirstChar { it.uppercase() }
            else -> "${start.format(shortMonthFormat)} – ${start.plusDays((rowCount * 7 - 1).toLong()).format(shortMonthFormat)}"
        }
        return WidgetPresentation(title,ShiftRepository.activeWorkProfile.name,rowCount,columns,month,
            (0 until rowCount*columns).map{WidgetUpdateManager.getDaySummary(start.plusDays(it.toLong()),today)},LocalDatabaseStore.loadError!=null)
    }

    internal fun createViews(context:Context,kind:WidgetKind,id:Int,width:Int,height:Int,content:WidgetPresentation,dark:Boolean,pixelBudget:Float=1_500_000f):RemoteViews {
        val palette=Palette(dark)
        val views = RemoteViews(context.packageName, kind.layout)
        views.setInt(R.id.widget_root, "setBackgroundResource", if (dark) R.drawable.bg_widget_turnisync_dark else R.drawable.bg_widget_turnisync_light)
        val header = widgetHeaderLayout(width.toFloat(), kind == WidgetKind.TODAY)
        val title = content.title
        views.setTextViewText(R.id.widget_title, title)
        views.setTextColor(R.id.widget_title, palette.text)
        if(kind==WidgetKind.TODAY){
            val metrics=context.resources.displayMetrics
            val titlePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize=13f*metrics.scaledDensity
                typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            }
            val available=header.titleMaxWidthDp*metrics.density
            val fittedSp=13f*min(1f,available/titlePaint.measureText(title).coerceAtLeast(1f))
            views.setTextViewTextSize(R.id.widget_title,android.util.TypedValue.COMPLEX_UNIT_SP,fittedSp)
        }
        views.setTextViewText(R.id.widget_profile, if(content.invalidData) "Dati da ripristinare · Apri TurniSync" else content.workName)
        views.setTextColor(R.id.widget_profile, palette.muted)
        views.setViewVisibility(R.id.widget_profile, if (header.showWork) View.VISIBLE else View.GONE)
        views.setInt(R.id.widget_title, "setMaxWidth", (header.titleMaxWidthDp * context.resources.displayMetrics.density).toInt())
        views.setContentDescription(R.id.widget_profile, content.workName)
        views.setContentDescription(R.id.widget_title, title)
        views.setInt(R.id.widget_previous, "setColorFilter", palette.brand)
        views.setInt(R.id.widget_next, "setColorFilter", palette.brand)
        views.setViewVisibility(R.id.widget_previous, if (kind == WidgetKind.TODAY) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_next, if (kind == WidgetKind.TODAY) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_previous, WidgetUpdateManager.createNavPendingIntent(context, id, WidgetUpdateManager.ACTION_NAV_PREV, kind.provider))
        views.setOnClickPendingIntent(R.id.widget_next, WidgetUpdateManager.createNavPendingIntent(context, id, WidgetUpdateManager.ACTION_NAV_NEXT, kind.provider))
        views.setOnClickPendingIntent(R.id.widget_title, WidgetUpdateManager.createNavPendingIntent(context, id, WidgetUpdateManager.ACTION_NAV_RESET, kind.provider))
        views.setOnClickPendingIntent(R.id.widget_profile, WidgetUpdateManager.createDayPendingIntent(context, content.days.first().date, id * 100 + 99))
        views.removeAllViews(R.id.widget_rows)
        val navigationHeight=if(kind==WidgetKind.PANORAMA)28f else 22f
        val headerHeight = navigationHeight+if(kind == WidgetKind.TODAY)0f else 10f
        val cellWidth = (width - 16f) / content.columns
        val rowCount=content.rows
        val columns=content.columns
        val cellHeight = widgetDayCellHeight(height.toFloat(),rowCount,kind == WidgetKind.TODAY,navigationHeight)
        // Render at native density on normal hosts; cap the whole bitmap payload for oversized widgets.
        val area = ((width - 16f) * (height - 8f - headerHeight)).coerceAtLeast(1f)
        val density = min(context.resources.displayMetrics.density, sqrt(pixelBudget / area)).coerceAtLeast(.75f)
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
                val summary = content.days[index]
                val day = summary.date
                val cell = RemoteViews(context.packageName, R.layout.widget_turnisync_cell)
                cell.setImageViewBitmap(R.id.widget_day,
                    renderDay(summary, cellWidth, cellHeight, density, fontScale, palette,
                        kind == WidgetKind.TODAY, kind == WidgetKind.MONTH && YearMonth.from(day) != content.month,
                        content.invalidData, showWeekday = false, compactWeek = kind==WidgetKind.WEEK))
                cell.setContentDescription(R.id.widget_day, summary.accessibilityLabel)
                cell.setOnClickPendingIntent(R.id.widget_day, WidgetUpdateManager.createDayPendingIntent(context, day, id * 100 + index))
                row.addView(R.id.widget_row, cell)
            }
            views.addView(R.id.widget_rows, row)
        }
        return views
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
        fontScale: Float, palette: Palette, wide: Boolean, faded: Boolean, invalidData: Boolean, showWeekday: Boolean, compactWeek:Boolean): Bitmap {
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
        fun dayNumber(x:Float,size:Float,maxWidth:Float,box:RectF) {
            val value=summary.dayNumber.toString()
            // One shared center for today's fill, the holiday outline and the actual glyph ink.
            // The caller already leaves room below this date lane for the shift chip.
            val safe=RectF(box.left+.5f,box.top+2f,box.right-.5f,box.bottom-.5f)
            val side=minOf(size*fontScale+3f,safe.width(),safe.height()).coerceAtLeast(1f)
            val centerX=x.coerceIn(safe.left+side/2f,safe.right-side/2f)
            val centerY=safe.centerY()
            val frame=RectF(centerX-side/2f,centerY-side/2f,centerX+side/2f,centerY+side/2f)
            if(summary.isToday)rect(frame.left,frame.top,frame.right,frame.bottom,3f,palette.brand)
            if(summary.holidayName!=null){
                paint.color=palette.holiday;paint.style=Paint.Style.STROKE;paint.strokeWidth=1f
                canvas.drawRoundRect(frame,3f,3f,paint)
                paint.style=Paint.Style.FILL
            }
            val ink=when {
                summary.isOnCall -> if(summary.isToday)color(if(palette.dark)"#493000" else "#FFF0BA")else palette.onCall
                summary.isToday -> if(palette.dark)color("#211D38")else Color.WHITE
                else -> palette.text
            }
            paint.typeface=medium
            paint.textSize=size*fontScale
            paint.textAlign=Paint.Align.LEFT
            val glyphPath=Path()
            val glyphBounds=RectF()
            fun measureGlyph(){
                glyphPath.rewind()
                paint.getTextPath(value,0,value.length,0f,0f,glyphPath)
                glyphPath.computeBounds(glyphBounds,true)
            }
            measureGlyph()
            val allowedWidth=minOf(maxWidth,side-1.5f).coerceAtLeast(1f)
            val allowedHeight=(side-1.5f).coerceAtLeast(1f)
            val fit=minOf(1f,allowedWidth/glyphBounds.width().coerceAtLeast(1f),allowedHeight/glyphBounds.height().coerceAtLeast(1f))
            if(fit<1f){paint.textSize*=fit;measureGlyph()}
            paint.color=if(faded)mix(ink,palette.surface,.48f)else ink
            // Font ascent and advance width are not the visible bounds of digits (notably 1/27).
            canvas.drawText(value,centerX-glyphBounds.centerX(),centerY-glyphBounds.centerY(),paint)
        }
        val background = when {
            summary.isToday -> palette.today
            summary.date.dayOfWeek.value >= 6 -> palette.weekend
            else -> palette.surface
        }
        // Weekend tint is independent from the text fading of adjacent months.
        if (!wide) rect(1f, 1f, width - 1f, height - 1f, 8f, background)
        if(compactWeek && height<34f) {
            val dateHeight=(height-18f).coerceIn(8f,13f)
            dayNumber(width/2f,10.5f,width-12f,RectF(width/2f-10f,0f,width/2f+10f,dateHeight))
            if(summary.isOnCall)drawBell(canvas,paint,width-5f,dateHeight/2f,7f,palette.onCall)
            val shift=summary.shifts.firstOrNull()
            if(shift!=null&&!invalidData){
                val tint=mix(shift.definition.colorHex.toInt(),background,if(palette.dark).62f else .26f)
                rect(3f,dateHeight,width-3f,height-1f,3f,if(faded)mix(tint,background,.5f)else tint)
                val middle=(dateHeight+height-1f)/2f
                text(shift.definition.shortCode.take(4),width/2f,middle-1f,9f,contrastInk(tint),true,width-7f)
                text(shift.startTime,width/2f,height-2f,9f,contrastInk(tint),true,width-7f)
            }
            return bitmap
        }
        if (!wide && height < 27f) {
            // At legacy two-row host heights, use one horizontal line rather than tiny stacked labels.
            val baseline = height / 2f + 4f
            val numberX = 9f
            dayNumber(numberX,10.5f,17f,RectF(1f,height/2f-8f,18f,height/2f+8f))
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
            dayNumber(width/2f,numberSize,22f,RectF(width/2f-11f,1f,width/2f+11f,numberBaseline+3f))
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
        val headerHeight = if(wide)minOf(widgetDateHeaderHeight(true),(height*.38f).coerceAtLeast(18f)) else if(height<48f)20f else 21f
        if (wide) {
            val label = if (summary.isToday) "Oggi" else "Domani"
            val numberX=minOf(if(summary.isToday)59f else 84f,width-if(summary.isOnCall)30f else 14f)
            text(label,9f,headerHeight/2f+4f,12f,if(summary.isToday)palette.brand else palette.muted,maxWidth=numberX-21f)
            dayNumber(numberX,14.5f,24f,RectF(numberX-12f,2f,numberX+12f,headerHeight-3f))
        } else {
            // The weekday and today's number never share a fixed-width highlight.
            val numberX = width / 2f
            val numberSize = if(height<48f)12.5f else 13.5f
            dayNumber(numberX,numberSize,width-12f,RectF(numberX-13f,1f,numberX+13f,headerHeight-1f))
        }
        if (summary.isOnCall) drawBell(canvas, paint, width - (if (wide) 10f else 6f),
            headerHeight/2f, if (wide) 12f else 8f, if (faded) mix(palette.onCall, background, .4f) else palette.onCall)
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
                val plan=widgetDailyShiftLayout(width,slotHeight,fontScale)
                val codeHeight=min(32f,slotHeight-6f)
                val badge=shift.definition.colorHex.toInt()
                rect(9f,y+3f,9f+plan.badgeWidth,y+3f+codeHeight,7f,badge)
                text(code,9f+plan.badgeWidth/2f,y+3f+codeHeight/2f+4f,12f,contrastInk(badge),true,plan.badgeWidth-4f)
                val detailX=plan.detailX
                val timeWidth=plan.detailWidth
                if(!plan.showName) {
                    // Code already identifies the shift. Prioritize the full start time in compact hosts.
                    text(shift.startTime,detailX,y+slotHeight/2f+4f,12.5f,ink,maxWidth=timeWidth)
                }else{
                    text(shift.definition.name,detailX,y+slotHeight/2f-3f,11f,ink,maxWidth=timeWidth)
                    text(shift.startTime,detailX,y+slotHeight/2f+12f,12.5f,ink,maxWidth=timeWidth)
                }


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
