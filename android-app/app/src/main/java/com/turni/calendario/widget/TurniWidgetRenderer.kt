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
        val dark = context.getSharedPreferences("turnisync_theme", Context.MODE_PRIVATE).getBoolean("dark", true)
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
        val compact = kind == WidgetKind.WEEK ||
            (kind == WidgetKind.MONTH && height < 290) ||
            (kind == WidgetKind.PANORAMA && height < 170) ||
            (kind == WidgetKind.TODAY && height < 150)
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
        views.setViewVisibility(R.id.widget_profile, if (compact) View.GONE else View.VISIBLE)
        views.setTextColor(R.id.widget_previous, palette.brand)
        views.setTextColor(R.id.widget_next, palette.brand)
        views.setViewVisibility(R.id.widget_previous, if (kind == WidgetKind.TODAY) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.widget_next, if (kind == WidgetKind.TODAY) View.GONE else View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_previous, WidgetUpdateManager.createNavPendingIntent(context, id, WidgetUpdateManager.ACTION_NAV_PREV, kind.provider))
        views.setOnClickPendingIntent(R.id.widget_next, WidgetUpdateManager.createNavPendingIntent(context, id, WidgetUpdateManager.ACTION_NAV_NEXT, kind.provider))
        views.setOnClickPendingIntent(R.id.widget_title, WidgetUpdateManager.createNavPendingIntent(context, id, WidgetUpdateManager.ACTION_NAV_RESET, kind.provider))
        views.setOnClickPendingIntent(R.id.widget_profile, WidgetUpdateManager.createDayPendingIntent(context, today, id * 100 + 99))
        views.removeAllViews(R.id.widget_rows)
        val headerHeight = if (compact) 28f else 46f
        val cellWidth = (width - 16f) / columns
        val cellHeight = ((height - 16f - headerHeight) / rowCount).coerceAtLeast(20f)
        // Render at native density on normal hosts; cap the whole bitmap payload for oversized widgets.
        val area = ((width - 16f) * (height - 16f - headerHeight)).coerceAtLeast(1f)
        val density = min(context.resources.displayMetrics.density, sqrt(1_500_000f / area)).coerceAtLeast(1f)
        val fontScale = context.resources.configuration.fontScale.coerceIn(1f, 1.2f)
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
                        LocalDatabaseStore.loadError != null))
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
        val overtime = color(if (dark) "#173F58" else "#C2E7FF")
        val overtimeInk = color(if (dark) "#C0E7FF" else "#164967")
        val travel = color(if (dark) "#18483D" else "#ABE2CF")
        val travelInk = color(if (dark) "#A5EFD8" else "#124D3B")
    }

    private fun renderDay(summary: DayShiftSummary, width: Float, height: Float, density: Float,
        fontScale: Float, palette: Palette, wide: Boolean, faded: Boolean, invalidData: Boolean): Bitmap {
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
        val background = when {
            summary.isToday -> palette.today
            summary.date.dayOfWeek.value >= 6 -> palette.weekend
            else -> palette.surface
        }
        // Weekend tint is independent from the text fading of adjacent months.
        rect(1f, 1f, width - 1f, height - 1f, if (wide) 14f else 8f, background)
        if (!wide && height < 27f) {
            // At legacy two-row host heights, use one horizontal line rather than tiny stacked labels.
            val baseline = height / 2f + 4f
            val numberX = 9f
            if (summary.isToday) rect(1f, height / 2f - 8f, 18f, height / 2f + 8f, 5f, palette.brand)
            text(summary.dayNumber.toString(), numberX, baseline, 10.5f,
                if (summary.isToday) (if (palette.dark) color("#211D38") else Color.WHITE) else palette.text, true, 17f)
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
            if (summary.isToday) {
                rect(width / 2f - 10f, 1f, width / 2f + 10f, numberBaseline + 3f, 5f, palette.brand)
            }
            text(summary.dayNumber.toString(), width / 2f, numberBaseline, numberSize,
                if (summary.isToday) (if (palette.dark) color("#211D38") else Color.WHITE) else palette.text, true)
            if (summary.isOnCall) drawBell(canvas, paint, width - 6f, 7f, 8f, palette.onCall)
            val shift = summary.shifts.firstOrNull()
            if (shift != null && !invalidData) {
                val tint = mix(shift.definition.colorHex.toInt(), background, if (palette.dark) .62f else .26f)
                val fill = if (faded) mix(tint, background, .5f) else tint
                val top = numberBaseline + 4f
                rect(2f, top, width - 2f, height - 1f, 3f, fill)
                text(shift.definition.shortCode.take(4), width / 2f, height - 3f,
                    if (height < 25f) 9f else 10.5f, contrastInk(tint), true, width - 6f)
                if (shift.overtimeHours > 0f || shift.isTrasferta) {
                    paint.color = if (shift.isTrasferta) palette.travelInk else palette.overtimeInk
                    canvas.drawCircle(width - 3f, height - 3f, 1.6f, paint)
                }
                if (summary.shifts.size > 1) {
                    paint.color = summary.shifts[1].definition.colorHex.toInt()
                    canvas.drawCircle(3f, height - 3f, 1.6f, paint)
                }
            }
            return bitmap
        }
        val headerHeight = if (wide) 25f else if (height < 48f) 18f else 27f
        if (wide) {
            val label = if (summary.isToday) "Oggi · ${summary.dayNumber}" else "Domani · ${summary.dayNumber}"
            text(label, 10f, 18f, 12f, if (summary.isToday) palette.brand else palette.muted, maxWidth = width - 32f)
        } else {
            // The weekday and today's number never share a fixed-width highlight.
            val numberX = width / 2f
            val numberTop = if (height < 48f) 1f else 7f
            val baseline = if (height < 48f) 13f else 21f
            if (summary.isToday) rect(numberX - 10f, numberTop, numberX + 10f, baseline + 3f, 5f, palette.brand)
            val ink = if (summary.isToday) (if (palette.dark) color("#24213F") else Color.WHITE) else palette.text
            text(summary.dayNumber.toString(), numberX, baseline, 12f, ink, true, 23f)
            if (height >= 48f) text(summary.dayNameShort, 4f, 10f, 9f, palette.muted, maxWidth = 10f)
        }
        if (summary.isOnCall) drawBell(canvas, paint, width - (if (wide) 17f else 10f),
            if (wide) 13f else 7f, if (wide) 12f else 8f, if (faded) mix(palette.onCall, background, .4f) else palette.onCall)
        if (invalidData) { text("—", width / 2f, height / 2f + 9f, 12f, palette.muted, true); return bitmap }
        if (summary.shifts.isEmpty()) {
            if (wide) text("Nessun turno", 9f, headerHeight + 19f, 12f, palette.muted, maxWidth = width - 18f)
            return bitmap
        }
        val gap = 3f
        val remaining = height - headerHeight - 4f
        if (wide) {
            val count = min(summary.shifts.size, if (remaining >= 66f) 3 else if (remaining >= 40f) 2 else 1)
            val slotHeight = (remaining - (count - 1) * gap) / count
            summary.shifts.take(count).forEachIndexed { index, shift ->
                val y = headerHeight + index * (slotHeight + gap)
                val chipColor = mix(shift.definition.colorHex.toInt(), background, if (palette.dark) .62f else .26f)
                val ink = contrastInk(chipColor)
                rect(6f, y, width - 6f, y + slotHeight, 7f, chipColor)
                val code = shift.definition.shortCode.take(4)
                text(code, 13f, y + min(slotHeight - 5f, 17f), 12f, ink, maxWidth = 40f)
                text(shift.startTime, width - 55f, y + min(slotHeight - 5f, 17f), 11f, ink, maxWidth = 48f)
                if (width >= 220f) text(shift.definition.name, 62f, y + min(slotHeight - 5f, 17f), 11f, ink, maxWidth = width - 125f)
                if (slotHeight >= 34f && (shift.overtimeHours > 0f || shift.isTrasferta)) {
                    val metadata = listOfNotNull(if (shift.overtimeHours > 0f) widgetOvertimeLabel(shift.overtimeHours) else null,
                        if (shift.isTrasferta) "TR" else null).joinToString("   ·   ")
                    text(metadata, 13f, y + slotHeight - 5f, 10f, ink, maxWidth = width - 26f)
                } else if (shift.overtimeHours > 0f || shift.isTrasferta) {
                    rect(width - 10f, y + 3f, width - 6f, y + slotHeight - 3f, 2f,
                        if (shift.isTrasferta) palette.travelInk else palette.overtimeInk)
                }
            }
            drawOverflow(canvas, paint, summary.shifts.drop(count), width - 20f, height - 4f)
            return bitmap
        }
        val baseChip = if (remaining < 34f) remaining.coerceAtLeast(16f) else 35f
        var cursor = headerHeight
        var visible = 0
        for (shift in summary.shifts) {
            val bands = (if (shift.overtimeHours > 0f) 1 else 0) + (if (shift.isTrasferta) 1 else 0)
            if (height - cursor < baseChip + 2f) break
            val nextShiftReserve = if (visible + 1 < summary.shifts.size) baseChip + gap else 0f
            val bandHeight = if (height - cursor - baseChip - nextShiftReserve - 4f >= bands * 11f) 11f else 0f
            val total = baseChip + bands * bandHeight
            val tint = mix(shift.definition.colorHex.toInt(), background, if (palette.dark) .62f else .26f)
            val fill = if (faded) mix(tint, background, .5f) else tint
            val ink = contrastInk(tint)
            rect(3f, cursor, width - 3f, cursor + total, 5f, fill)
            text(shift.definition.shortCode.take(4), width / 2f, cursor + (if (baseChip < 26f) 9f else 13f), if (baseChip < 26f || width < 38f) 10f else 11.5f, ink, true, width - 8f)
            if (baseChip >= 20f) text(shift.startTime, width / 2f, cursor + baseChip - 3f, if (baseChip < 26f) 9f else 10.5f, ink, true, width - 7f)
            var bandTop = cursor + baseChip
            if (bandHeight > 0f) {
                if (shift.overtimeHours > 0f) {
                    rect(3f, bandTop, width - 3f, bandTop + bandHeight, 2f, palette.overtime)
                    text(widgetOvertimeLabel(shift.overtimeHours), width / 2f, bandTop + 8f, 8f, palette.overtimeInk, true, width - 6f)
                    bandTop += bandHeight
                }
                if (shift.isTrasferta) {
                    rect(3f, bandTop, width - 3f, bandTop + bandHeight, 2f, palette.travel)
                    text("TR", width / 2f, bandTop + 8f, 8f, palette.travelInk, true)
                }
            } else if (bands > 0) {
                paint.color = if (shift.isTrasferta) palette.travelInk else palette.overtimeInk
                canvas.drawCircle(width - 7f, cursor + baseChip - 5f, 2f, paint)
            }
            cursor += total + gap
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
