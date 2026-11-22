package com.turni.calendario.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import com.turni.calendario.ui.theme.PaletteStyle
import com.turni.calendario.ui.theme.contrastRatio
import com.turni.calendario.ui.theme.readableIndicator
import com.turni.calendario.ui.theme.readableInk
import com.turni.calendario.ui.theme.shiftChartColor
import com.turni.calendario.ui.theme.shiftColorStyle
import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import com.turni.calendario.repository.ShiftRepository
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.LocalTime
import kotlin.math.roundToInt

data class PdfShiftRow(val code: String, val name: String, val color: Int, val time: String?, val details: List<String>)
data class PdfDayRow(val date: LocalDate, val shifts: List<PdfShiftRow>, val details: List<String>, val holiday: Boolean = false)
data class PdfExportSnapshot(val work: String, val options: PdfExportOptions, val days: List<PdfDayRow>)

/** Capture repository state on the UI thread; render this immutable snapshot on IO. */
object PdfGenerator {
    fun capture(options: PdfExportOptions): PdfExportSnapshot {
        val work = ShiftRepository.activeWorkProfile
        val days = PdfExportPlan.dates(options).map { date ->
            val source = ShiftRepository.getShiftsForDate(date)
            val all = source.filter {
                ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId == work.id
            }
            val extra = ShiftRepository.getDayExtraInfo(date)
            val rows = all.mapNotNull { shift ->
                val def = ShiftRepository.getShiftDefinition(shift.shiftDefId) ?: return@mapNotNull null
                if (options.filterShiftId != null && def.id != options.filterShiftId) return@mapNotNull null
                val legacyPrimary = source.firstOrNull()?.id == shift.id
                val overtime = if (shift.overtimeHours > 0) shift.overtimeHours else if (legacyPrimary) extra.overtimeHours else 0f
                val actualEnd = shift.actualEndTime ?: if (legacyPrimary) extra.actualEndTime else null
                val lines = buildList {
                    if (options.includeTime) add(if (def.isAllDay) "Tutto il giorno" else "${shift.customStartTime ?: def.startTime} - ${shift.customEndTime ?: def.endTime}")
                    if (actualEnd != null && options.includeTime) add("Stacco effettivo: $actualEnd")
                    if (options.includeDuration && !def.isAllDay) {
                        val start = LocalTime.parse(shift.customStartTime ?: def.startTime)
                        val end = LocalTime.parse(shift.customEndTime ?: def.endTime)
                        val elapsed = (java.time.Duration.between(start, end).toMinutes() + 1440) % 1440
                        val minutes = ((if (elapsed == 0L) 1440 else elapsed) - def.breakMinutes).coerceAtLeast(0)
                        add("Durata prevista: ${formatMinutes(minutes.toInt())}")
                    }
                    if (options.includeBreak && def.breakMinutes > 0) add("Pausa: ${def.breakMinutes} min${def.breakStartTime?.let { " dalle $it" } ?: ""}")
                    if (overtime > 0) add("Straordinario: ${formatMinutes((overtime * 60).roundToInt())}")
                    val permMinutes = if (shift.permissionHours.isFinite() && shift.permissionHours > 0f) (shift.permissionHours * 60).roundToInt() else 0
                    if (permMinutes > 0) add("Permesso: −${formatMinutes(permMinutes)}")
                    if (options.includeLocation && (shift.isTrasferta || (legacyPrimary && extra.isTrasferta))) add("Trasferta")
                    if (options.includeNotes && shift.notes.isNotBlank()) add(shift.notes)
                    if (options.includeNotes && shift.tags.isNotEmpty()) add(shift.tags.joinToString(" · "))
                }
                PdfShiftRow(def.shortCode, if (options.includeTitle) def.name else "", def.colorHex.toInt(), if (options.includeTime && !def.isAllDay) shift.customStartTime ?: def.startTime else null, lines)
            }
            val dayLines = buildList {
                if (ShiftRepository.isOnCall(date)) add("Reperibilità")
                if (options.includeEvents) ShiftRepository.isHoliday(date)?.let { add(it.name) }
                if (options.includeNotes && extra.notes.isNotBlank()) add(extra.notes)
                if (options.includeNotes) extra.tagIds.mapNotNull { id -> ShiftRepository.dayTags.firstOrNull { it.id == id }?.name }.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" · ")) }
            }
            PdfDayRow(date, rows, dayLines, options.includeEvents && ShiftRepository.isHoliday(date)!=null)
        }
        return PdfExportSnapshot(work.name, options, days)
    }

    fun writePdf(context: Context, snapshot: PdfExportSnapshot): File {
        val output = File.createTempFile("turnisync-", ".pdf", context.cacheDir)
        val presentation = PdfExportPlan.presentation(snapshot.options)
        val months=snapshot.days.map{YearMonth.from(it.date)}.distinct().size
        val papers = when (presentation) {
            PdfPresentation.OVERVIEW -> listOf(1191 to 842, 1684 to 1191, 2384 to 1684)
            PdfPresentation.MONTHS -> if (months > 1) listOf(595 to 842, 1191 to 1684) else listOf(595 to 842)
            PdfPresentation.LIST -> listOf(595 to 842, 842 to 1191)
        }
        var lastError:IllegalArgumentException?=null
        for((width,height) in papers.distinct()) {
            val doc=PdfDocument()
            try {
                DocumentWriter(doc,snapshot,width.toFloat(),height.toFloat()).render()
                output.outputStream().use(doc::writeTo)
                return output
            } catch(error:IllegalArgumentException) {
                output.delete()
                if(error.message?.startsWith("I dettagli selezionati")!=true)throw error
                lastError=error
            } catch(error:Exception){output.delete();throw error}
            finally {doc.close()}
        }
        throw lastError?:IllegalArgumentException("Impossibile impaginare il documento")
    }

    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "TurniSync - Prospetto turni")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Condividi PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun generateAndSharePdf(context: Context, options: PdfExportOptions): File? = try {
        writePdf(context, capture(options)).also { sharePdf(context, it) }
    } catch (_: Exception) { null }

    private fun formatMinutes(minutes: Int) = "${minutes / 60} h ${minutes % 60} min"

    private class DocumentWriter(val doc: PdfDocument, val data: PdfExportSnapshot, val width: Float, val height: Float) {
        private val ink = Color.rgb(18, 22, 30)
        private val muted = Color.rgb(89, 99, 117)
        private val softMuted = Color.rgb(138, 149, 168)
        private val accent = Color.rgb(47, 91, 255)
        private val accentInk = Color.rgb(27, 60, 203)
        private val accentTint = Color.rgb(234, 240, 255)
        private val paper = Color.rgb(250, 251, 253)
        private val slateSoft = Color.rgb(243, 246, 250)
        private val rule = Color.rgb(226, 232, 242)
        private val gridRule = Color.rgb(229, 234, 243)
        private val weekRule = Color.rgb(206, 215, 230)
        private val footerRule = Color.rgb(233, 238, 247)
        private val weekend = Color.rgb(235, 240, 255)
        private val weekendCell = Color.rgb(242, 246, 253)
        private val holiday = Color.rgb(255, 240, 242)
        private val holidayCell = Color.rgb(255, 245, 246)
        private val holidayEdge = Color.rgb(242, 201, 208)
        private val holidayInk = Color.rgb(184, 43, 66)
        private val overtimeFill = Color.rgb(220, 238, 246)
        private val overtimeInk = Color.rgb(29, 97, 125)
        private val warningFill = Color.rgb(245, 231, 201)
        private val warningInk = Color.rgb(121, 83, 18)

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private lateinit var canvas: Canvas
        private val margin = 26f
        private val format = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)
        private val weekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN)
        private val listDateFormat = DateTimeFormatter.ofPattern("dd/MM", Locale.ITALIAN)
        private val monthFormat = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)
        private val crowded = "I dettagli selezionati non entrano leggibili in una pagina. Riduci il periodo oppure disattiva note, durata o pause."

        private data class ShiftPaint(val fill: Int, val ink: Int, val accentBar: Int)
        private data class SummaryPill(val label: String, val fill: Int, val ink: Int, val width: Float)

        private fun resolveShiftPaint(rawColor: Int, subdued: Boolean = false): ShiftPaint {
            val source = ComposeColor(rawColor)
            val style = shiftColorStyle(
                source = source,
                dark = false,
                backdrop = ComposeColor.White,
                subdued = subdued,
                style = PaletteStyle.GRAPHITE,
            )
            val fill = style.codeFill.toArgb()
            val safeInk = readableInk(style.codeFill, style.codeInk).toArgb()
            val chart = shiftChartColor(source, dark = false)
            val bar = if (contrastRatio(style.codeFill, chart) >= 2.1f) {
                chart
            } else {
                readableIndicator(style.codeFill, style.codeInk)
            }
            return ShiftPaint(fill, safeInk, bar.toArgb())
        }

        private fun measure(value: String, size: Float, bold: Boolean = false): Float {
            paint.textSize = size
            paint.isFakeBoldText = bold
            return paint.measureText(value)
        }

        private fun text(value: String, x: Float, y: Float, size: Float = 9f, color: Int = ink, bold: Boolean = false) {
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.textSize = size
            paint.isFakeBoldText = bold
            canvas.drawText(value, x, y, paint)
        }

        private fun fittedText(
            value: String,
            x: Float,
            y: Float,
            size: Float,
            maxWidth: Float,
            color: Int = ink,
            bold: Boolean = false,
        ) {
            val measured = measure(value, size, bold).coerceAtLeast(1f)
            val fitted = minOf(size, size * maxWidth.coerceAtLeast(1f) / measured)
            text(value, x, y, fitted, color, bold)
        }

        private fun box(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 5f) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, paint)
        }

        private fun strokeBox(
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            color: Int,
            strokeWidth: Float = 0.5f,
            radius: Float = 4f,
        ) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.color = color
            canvas.drawRoundRect(RectF(x, y, x + w, y + h), radius, radius, paint)
            paint.style = Paint.Style.FILL
        }

        private fun lines(value: String, available: Float, size: Float, bold: Boolean = false): List<String> {
            paint.textSize = size
            paint.isFakeBoldText = bold
            if (Regex("^\\d{2}(?::\\d{2})?–\\d{2}(?::\\d{2})?$").matches(value)) {
                // A clock range is an indivisible unit. Never split a minute or hour in half.
                return if (paint.measureText(value) * 6.5f / size > available) value.split("–") else listOf(value)
            }
            return value.split('\n').flatMap { paragraph ->
                if (paragraph.isBlank()) listOf("") else buildList {
                    var rest = paragraph.trim()
                    while (rest.isNotEmpty()) {
                        val count = paint.breakText(rest, true, available.coerceAtLeast(1f), null).coerceAtLeast(1)
                        val end = if (count < rest.length) rest.lastIndexOf(' ', count).takeIf { it > 0 } ?: count else count
                        add(rest.substring(0, end))
                        rest = rest.substring(end).trimStart()
                    }
                }
            }
        }

        private fun detailTone(detail: String, isHoliday: Boolean = false): Pair<Int, Boolean> = when {
            detail.startsWith("Straordinario:") || detail.startsWith("Extra:") -> overtimeInk to false
            detail.startsWith("Permesso:") || detail.startsWith("Perm:") -> warningInk to false
            detail == "Reperibilità" || detail == "REP" -> warningInk to true
            detail == "Trasferta" -> accentInk to false
            isHoliday -> holidayInk to true
            else -> muted to false
        }

        fun render() {
            val months = data.days.map { YearMonth.from(it.date) }.distinct()
            val presentation = PdfExportPlan.presentation(data.options)
            val calendar = presentation != PdfPresentation.LIST
            val page = doc.startPage(PdfDocument.PageInfo.Builder(width.toInt(), height.toInt(), 1).create())
            try {
                canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val title = when (data.options.mode) {
                    PdfExportMode.ELENCO -> "Elenco turni"
                    PdfExportMode.MESE -> months.first().atDay(1)
                        .format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
                        .replaceFirstChar { it.uppercase() }
                    PdfExportMode.ANNO -> "Calendario ${data.options.selectedYear}"
                    else -> if (calendar) "Calendario del periodo" else "Elenco del periodo"
                }

                val contentW = width - 2 * margin
                val range = PdfExportPlan.range(data.options)
                val assignments = data.days.sumOf { it.shifts.size }
                val activeDays = data.days.count { it.shifts.isNotEmpty() }
                val extraCount = data.days.sumOf { d ->
                    d.shifts.count { s -> s.details.any { it.startsWith("Straordinario:") } }
                }
                val permCount = data.days.sumOf { d ->
                    d.shifts.count { s -> s.details.any { it.startsWith("Permesso:") } }
                }

                val pills = buildList {
                    fun pill(label: String, fill: Int, textInk: Int) =
                        SummaryPill(label, fill, textInk, measure(label, 8f, bold = true) + 14f)
                    add(pill(if (assignments == 1) "1 turno" else "$assignments turni", accentTint, accentInk))
                    add(pill(if (activeDays == 1) "1 giorno" else "$activeDays giorni", slateSoft, ink))
                    if (extraCount > 0) add(pill("$extraCount extra", overtimeFill, overtimeInk))
                    if (permCount > 0) add(pill(if (permCount == 1) "1 permesso" else "$permCount permessi", warningFill, warningInk))
                }

                val pillGap = 5f
                val pillH = 17f
                val singleRowW = pills.sumOf { it.width.toDouble() }.toFloat() + pillGap * (pills.size - 1).coerceAtLeast(0)
                val pillRows: List<List<SummaryPill>> = if (singleRowW <= contentW * 0.46f || pills.size <= 2) {
                    listOf(pills)
                } else {
                    val split = (pills.size + 1) / 2
                    listOf(pills.take(split), pills.drop(split))
                }
                val maxPillRowW = pillRows.maxOf { row ->
                    row.sumOf { it.width.toDouble() }.toFloat() + pillGap * (row.size - 1).coerceAtLeast(0)
                }

                val headerTop = 20f
                val leftTextX = margin + 12f
                val titleMaxW = (contentW - 12f - maxPillRowW - 14f).coerceAtLeast(110f)

                var pillY = 21f
                pillRows.forEach { row ->
                    var px = width - margin
                    for (p in row.asReversed()) {
                        px -= p.width
                        box(px, pillY, p.width, pillH, p.fill, pillH / 2f)
                        text(p.label, px + 7f, pillY + 11.3f, 8f, p.ink, bold = true)
                        px -= pillGap
                    }
                    pillY += pillH + 4f
                }
                val pillsBottom = pillY - 4f

                val eyebrow = (if (data.options.includeLogo) "TURNISYNC" else "PROSPETTO TURNI") +
                    "  ·  " + (if (calendar) "CALENDARIO" else "ELENCO")
                text(eyebrow, leftTextX, 29f, 7.8f, accent, bold = true)

                val titleLines = lines(title, titleMaxW, 17.5f, bold = true)
                var titleY = 48.5f
                titleLines.forEach { line ->
                    text(line, leftTextX, titleY, 17.5f, ink, bold = true)
                    titleY += 19.5f
                }
                val metaBaseY = maxOf(titleY - 3f, pillsBottom + 12f)
                val rangeText = "${range.first.format(format)} – ${range.second.format(format)}"
                val metaText = if (data.work.isNotBlank()) "${data.work}  ·  $rangeText" else rangeText
                var metaY = metaBaseY
                lines(metaText, contentW - 12f, 9.2f, bold = false).forEach { line ->
                    text(line, leftTextX, metaY, 9.2f, muted)
                    metaY += 12f
                }
                val stripeBottom = metaY - 9f
                box(margin, headerTop, 4f, (stripeBottom - headerTop).coerceAtLeast(44f), accent, 2f)

                var top = stripeBottom + 10f
                if (data.options.headerNotes.isNotBlank()) {
                    val noteLines = lines(data.options.headerNotes, contentW - 18f, 8f)
                    val noteH = noteLines.size * 10.5f + 10f
                    box(margin, top, contentW, noteH, slateSoft, 5f)
                    box(margin + 3f, top + 4f, 2.5f, noteH - 8f, muted, 1.25f)
                    var noteY = top + 12.5f
                    noteLines.forEach { line ->
                        text(line, margin + 11f, noteY, 8f, ink)
                        noteY += 10.5f
                    }
                    top += noteH + 8f
                }
                box(margin, top, contentW, 0.8f, rule, 0f)
                top += 10f
                require(top < height - 100f) { crowded }

                when (presentation) {
                    PdfPresentation.LIST -> list(top)
                    PdfPresentation.MONTHS -> calendars(months, top)
                    PdfPresentation.OVERVIEW -> overview(months, top)
                }

                box(margin, height - 30f, contentW, 0.7f, footerRule, 0f)
                val brandMark = if (data.options.includeLogo) "TurniSync" else "Prospetto turni"
                text("$brandMark  ·  Generato il ${LocalDate.now().format(format)}", margin, height - 18f, 7f, softMuted)
                val pageMark = "1 / 1"
                text(pageMark, width - margin - measure(pageMark, 7f), height - 18f, 7f, softMuted)
            } finally {
                doc.finishPage(page)
            }
        }

        private data class ListShiftBlock(
            val code: String,
            val color: Int?,
            val titleLines: List<String>,
            val time: String,
            val timeInline: Boolean,
            val detailLines: List<String>,
        ) {
            val lineCount: Int
                get() {
                    val headLines = if (titleLines.isNotEmpty()) {
                        titleLines.size + (if (!timeInline && time.isNotBlank()) 1 else 0)
                    } else {
                        if (time.isNotBlank()) 1 else 0
                    }
                    return maxOf(1, headLines + detailLines.size)
                }
        }

        private data class ListDayBlock(
            val shifts: List<ListShiftBlock>,
            val dayDetails: List<String>,
            val isEmptyDay: Boolean,
        )

        private fun list(top: Float) {
            val days = data.days.filter { data.options.includeEmptyRows || it.shifts.isNotEmpty() || it.details.isNotEmpty() }
            if (days.isEmpty()) {
                text("Nessun turno nel periodo e nel filtro scelti.", margin, top + 15f, 9.5f, muted)
                return
            }
            val available = height - top - 38f - 18f
            for (font in listOf(10f, 9.5f, 9f, 8.5f, 8f, 7.5f, 7f)) {
                for (columns in 1..3) {
                    val gap = 14f
                    val columnWidth = (width - 2 * margin - gap * (columns - 1)) / columns
                    val dateBadgeW = 40f + font * 1.4f
                    val hasCodes = days.any { d -> d.shifts.any { it.code.isNotBlank() } }
                    val codeFont = (font - 1.2f).coerceAtLeast(6f)
                    val maxCodeW = if (hasCodes) {
                        days.flatMap { it.shifts }.maxOfOrNull { measure(it.code, codeFont, bold = true) } ?: 0f
                    } else 0f
                    val codeBadgeW = if (hasCodes) minOf(maxOf(font * 2.9f, maxCodeW + 11f), font * 4.2f) else 0f
                    val entryOffset = dateBadgeW + 7f
                    val shiftTextOffset = if (hasCodes) entryOffset + codeBadgeW + 7f else entryOffset
                    val shiftContentW = columnWidth - shiftTextOffset - 4f
                    if (shiftContentW < 62f) continue

                    val leading = font + 2.6f
                    val timeFont = (font - 1.4f).coerceAtLeast(6f)
                    val detailFont = (font - 0.7f).coerceAtLeast(6.2f)
                    val dayDetailW = columnWidth - entryOffset - 4f

                    val blocks = days.map { day ->
                        val shiftBlocks = day.shifts.map { shift ->
                            val time = if (data.options.includeTime) shift.details.firstOrNull().orEmpty() else ""
                            val detail = if (data.options.includeTime) shift.details.drop(1) else shift.details
                            val timePillW = if (time.isNotBlank()) measure(time, timeFont) + 10f else 0f
                            val titleRoomWithTime = shiftContentW - timePillW - 7f
                            val timeInline = time.isNotBlank() && (shift.name.isBlank() || titleRoomWithTime >= 52f)
                            val titleAvail = if (timeInline && shift.name.isNotBlank()) titleRoomWithTime else shiftContentW
                            val titleLines = if (shift.name.isNotBlank()) lines(shift.name, titleAvail, font, true) else emptyList()
                            val detailLines = detail.flatMap { lines(it, shiftContentW, detailFont) }
                            ListShiftBlock(
                                code = shift.code,
                                color = shift.color.takeIf { data.options.includeIcon },
                                titleLines = titleLines,
                                time = time,
                                timeInline = timeInline,
                                detailLines = detailLines,
                            )
                        }
                        val dayDetails = day.details.flatMap { lines(it, dayDetailW, detailFont) }
                        ListDayBlock(
                            shifts = shiftBlocks,
                            dayDetails = dayDetails,
                            isEmptyDay = shiftBlocks.isEmpty() && dayDetails.isEmpty(),
                        )
                    }

                    val heights = blocks.map { block ->
                        if (block.isEmptyDay) {
                            leading + 6f
                        } else {
                            val totalLines = block.shifts.sumOf { it.lineCount } + block.dayDetails.size
                            val shiftGap = (block.shifts.size - 1).coerceAtLeast(0) * 2f
                            totalLines * leading + shiftGap + 6f
                        }
                    }
                    val packed = packPdfColumns(heights, columns, available) ?: continue

                    packed.forEachIndexed { column, indexes ->
                        val x = margin + column * (columnWidth + gap)
                        val entryX = x + entryOffset
                        val shiftTextX = x + shiftTextOffset
                        box(x, top, columnWidth, 17f, slateSoft, 4f)
                        text("GIORNO", x + 5f, top + 11.2f, 7.2f, accentInk, true)
                        text("TURNO", entryX, top + 11.2f, 7.2f, accentInk, true)
                        if (data.options.includeTime && blocks.any { b -> b.shifts.any { it.timeInline && it.time.isNotBlank() } }) {
                            val orarioLabel = "ORARIO"
                            text(orarioLabel, x + columnWidth - 4f - measure(orarioLabel, 7.2f, true), top + 11.2f, 7.2f, accentInk, true)
                        }
                        var y = top + 18f
                        indexes.forEach { index ->
                            val day = days[index]
                            val block = blocks[index]
                            val rowHeight = heights[index]
                            val isWeekend = day.date.dayOfWeek.value >= 6
                            val badgeFill = when {
                                day.holiday -> holiday
                                isWeekend -> weekend
                                else -> slateSoft
                            }
                            val dateInk = when {
                                day.holiday -> holidayInk
                                isWeekend -> accentInk
                                else -> ink
                            }
                            val wdInk = when {
                                day.holiday -> holidayInk
                                isWeekend -> accent
                                else -> muted
                            }
                            val badgeH = minOf(rowHeight - 4f, font + 7.5f)
                            box(x + 1f, y + 2f, dateBadgeW, badgeH, badgeFill, 4f)
                            val firstBaseline = y + font + 2.2f
                            text(day.date.format(listDateFormat), x + 4.5f, firstBaseline, font - 0.5f, dateInk, true)
                            val wdLabel = day.date.format(weekdayFormat).uppercase(Locale.ITALIAN).take(3)
                            val wdSize = (font - 2.3f).coerceAtLeast(5.4f)
                            text(wdLabel, x + 1f + dateBadgeW - 3.5f - measure(wdLabel, wdSize, true), firstBaseline, wdSize, wdInk, true)

                            if (block.isEmptyDay) {
                                val midY = y + rowHeight / 2f
                                paint.style = Paint.Style.FILL
                                paint.color = weekRule
                                canvas.drawCircle(entryX + 2.5f, midY, 1.4f, paint)
                                box(entryX + 7f, midY - 0.4f, 22f, 0.8f, rule, 0.4f)
                            } else {
                                var baseline = firstBaseline
                                block.shifts.forEachIndexed { sIdx, s ->
                                    if (sIdx > 0) baseline += 2f
                                    if (s.code.isNotBlank()) {
                                        val pillTop = baseline - font + 0.2f
                                        val pillH = font + 3.6f
                                        if (s.color != null) {
                                            val resolved = resolveShiftPaint(s.color)
                                            box(entryX, pillTop, codeBadgeW, pillH, resolved.fill, 3.5f)
                                            box(entryX + 2f, pillTop + 1.6f, 2f, (pillH - 3.2f).coerceAtLeast(3f), resolved.accentBar, 1f)
                                            val availCodeW = codeBadgeW - 8.5f
                                            val measuredCode = measure(s.code, codeFont, true)
                                            val drawFont = minOf(codeFont, codeFont * availCodeW.coerceAtLeast(1f) / measuredCode.coerceAtLeast(1f))
                                            val drawW = measure(s.code, drawFont, true)
                                            val codeX = entryX + 5.5f + ((availCodeW - drawW) / 2f).coerceAtLeast(0f)
                                            text(s.code, codeX, baseline - 0.4f, drawFont, resolved.ink, true)
                                        } else {
                                            box(entryX, pillTop, codeBadgeW, pillH, slateSoft, 3.5f)
                                            val availCodeW = codeBadgeW - 6f
                                            val measuredCode = measure(s.code, codeFont, true)
                                            val drawFont = minOf(codeFont, codeFont * availCodeW.coerceAtLeast(1f) / measuredCode.coerceAtLeast(1f))
                                            val drawW = measure(s.code, drawFont, true)
                                            val codeX = entryX + 3f + ((availCodeW - drawW) / 2f).coerceAtLeast(0f)
                                            text(s.code, codeX, baseline - 0.4f, drawFont, muted, true)
                                        }
                                    }

                                    val shiftStartBaseline = baseline
                                    if (s.titleLines.isEmpty()) {
                                        if (s.time.isNotBlank()) {
                                            val timeW = measure(s.time, timeFont) + 10f
                                            box(shiftTextX, shiftStartBaseline - font + 0.6f, timeW, font + 2.4f, slateSoft, 3.5f)
                                            text(s.time, shiftTextX + 5f, shiftStartBaseline - 0.3f, timeFont, muted)
                                            baseline += leading
                                        } else if (s.detailLines.isEmpty()) {
                                            baseline += leading
                                        }
                                    } else {
                                        s.titleLines.forEach { titleLine ->
                                            text(titleLine, shiftTextX, baseline, font, ink, true)
                                            baseline += leading
                                        }
                                        if (s.time.isNotBlank()) {
                                            if (s.timeInline) {
                                                val timeW = measure(s.time, timeFont) + 10f
                                                val pillX = x + columnWidth - 4f - timeW
                                                box(pillX, shiftStartBaseline - font + 0.6f, timeW, font + 2.4f, slateSoft, 3.5f)
                                                text(s.time, pillX + 5f, shiftStartBaseline - 0.3f, timeFont, muted)
                                            } else {
                                                text(s.time, shiftTextX, baseline, timeFont, muted)
                                                baseline += leading
                                            }
                                        }
                                    }
                                    s.detailLines.forEach { dLine ->
                                        val (dColor, dBold) = detailTone(dLine)
                                        text(dLine, shiftTextX, baseline, detailFont, dColor, dBold)
                                        baseline += leading
                                    }
                                }
                                block.dayDetails.forEach { dLine ->
                                    val (dColor, dBold) = detailTone(dLine, day.holiday)
                                    text(dLine, entryX, baseline, detailFont, dColor, dBold)
                                    baseline += leading
                                }
                            }
                            y += rowHeight
                            val isWeekEndBoundary = day.date.dayOfWeek.value == 7
                            box(
                                x,
                                y,
                                columnWidth,
                                if (isWeekEndBoundary) 0.9f else 0.4f,
                                if (isWeekEndBoundary) weekRule else gridRule,
                                0f,
                            )
                        }
                    }
                    return
                }
            }
            throw IllegalArgumentException(crowded)
        }

        private data class OverviewLine(
            val label: String,
            val color: Int? = null,
            val bold: Boolean = false,
            val isShiftBadge: Boolean = false,
            val isHolidayDetail: Boolean = false,
        )

        private fun overview(months: List<YearMonth>, top: Float) {
            require(months.size <= 12) { "Per una panoramica su una pagina, seleziona al massimo 12 mesi." }
            val gap = 4.5f
            val columnWidth = (width - 2 * margin - gap * (months.size - 1)) / months.size
            val gridTop = top + 22f
            val availableHeight = height - 42f - gridTop
            val badgeXOffset = 21f
            val pillW = (columnWidth - badgeXOffset - 2f).coerceAtLeast(20f)
            val textAvailW = (pillW - 6.5f).coerceAtLeast(14f)
            val byDate = data.days.associateBy { it.date }

            for (font in listOf(8f, 7.5f, 7f, 6.5f)) {
                val leading = font + 2.2f
                val contents = data.days.associate { day ->
                    day.date to buildList<OverviewLine> {
                        day.shifts.forEach { shift ->
                            val badgeColor = shift.color.takeIf { data.options.includeIcon }
                            if (shift.name.isNotBlank()) {
                                lines(shift.name, textAvailW, font, true).forEach { label ->
                                    add(OverviewLine(label, badgeColor, bold = true, isShiftBadge = true))
                                }
                            }
                            val markFirstDetail = shift.name.isBlank()
                            val firstDetailIdx = size
                            shift.details.forEach { detail ->
                                val compact = compactCalendarDetail(detail).replace("Tutto il giorno", "Intera giornata")
                                val avail = if (markFirstDetail && size == firstDetailIdx) textAvailW else pillW - 2f
                                lines(compact, avail, font).forEach { label ->
                                    val asBadge = markFirstDetail && size == firstDetailIdx
                                    add(
                                        OverviewLine(
                                            label = label,
                                            color = badgeColor.takeIf { asBadge },
                                            bold = asBadge,
                                            isShiftBadge = asBadge,
                                        ),
                                    )
                                }
                            }
                            if (shift.name.isBlank() && size == firstDetailIdx && data.options.includeIcon) {
                                add(OverviewLine(shift.code, shift.color, bold = true, isShiftBadge = true))
                            }
                        }
                        day.details.forEach { detail ->
                            lines(detail, pillW - 2f, font).forEach {
                                add(OverviewLine(it, isHolidayDetail = day.holiday))
                            }
                        }
                    }
                }
                val minimumHeights = FloatArray(31) { 16f }
                contents.forEach { (date, content) ->
                    val index = date.dayOfMonth - 1
                    minimumHeights[index] = maxOf(minimumHeights[index], content.size * leading + 3.5f)
                }
                val spare = availableHeight - minimumHeights.sum()
                if (spare < 0f) continue
                val rowHeights = minimumHeights.map { it + spare / minimumHeights.size }
                val rowTops = rowHeights.runningFold(gridTop, Float::plus)

                months.forEachIndexed { index, month ->
                    val x = margin + index * (columnWidth + gap)
                    box(x, top, columnWidth, 19f, accentTint, 4f)
                    box(x + 1f, top, columnWidth - 2f, 2.2f, accent, 1.1f)
                    val monthLabel = month.atDay(1).format(monthFormat).replaceFirstChar { it.uppercase() }
                    val headerText = if (data.options.mode == PdfExportMode.ANNO) monthLabel else "$monthLabel ${month.year}"
                    val hSize = 8.5f
                    val hTextW = measure(headerText, hSize, bold = true)
                    val hx = x + ((columnWidth - hTextW) / 2f).coerceAtLeast(4f)
                    fittedText(headerText, hx, top + 13.5f, hSize, columnWidth - 8f, accentInk, bold = true)

                    for (dayNumber in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayNumber)
                        val row = byDate[date] ?: continue
                        val y = rowTops[dayNumber - 1]
                        val rowH = rowHeights[dayNumber - 1]
                        val isWeekend = date.dayOfWeek.value >= 6
                        val fill = when {
                            row.holiday -> holidayCell
                            isWeekend -> weekendCell
                            dayNumber % 2 == 0 -> paper
                            else -> Color.WHITE
                        }
                        box(x, y, columnWidth, rowH - 0.4f, fill, 0f)
                        val isWeekStart = date.dayOfWeek.value == 1
                        box(x, y, columnWidth, if (isWeekStart) 0.75f else 0.35f, if (isWeekStart) weekRule else gridRule, 0f)

                        if (row.holiday) {
                            box(x + 2f, y + 1.5f, 16f, 13.5f, holiday, 3f)
                        }
                        val numInk = when {
                            row.holiday -> holidayInk
                            isWeekend -> accentInk
                            else -> ink
                        }
                        val wdInk = when {
                            row.holiday -> holidayInk
                            isWeekend -> accent
                            else -> muted
                        }
                        text(dayNumber.toString(), x + 3.2f, y + 8f, 7.5f, numInk, true)
                        text(date.format(weekdayFormat).uppercase(Locale.ITALIAN).take(3), x + 3.2f, y + 13.6f, 5.2f, wdInk, true)

                        val labelX = x + badgeXOffset
                        contents.getValue(date).forEachIndexed { lineIndex, line ->
                            val baseline = y + font + 1.2f + lineIndex * leading
                            if (line.isShiftBadge && line.color != null) {
                                val resolved = resolveShiftPaint(line.color)
                                val pTop = baseline - font * 0.88f
                                val pH = font * 1.14f
                                box(labelX, pTop, pillW, pH, resolved.fill, 2.4f)
                                box(labelX + 1.2f, pTop + 1.2f, 1.6f, (pH - 2.4f).coerceAtLeast(2f), resolved.accentBar, 0.8f)
                                fittedText(line.label, labelX + 4.5f, baseline, font, textAvailW, resolved.ink, bold = true)
                            } else if (line.isShiftBadge) {
                                box(labelX, baseline - font * 0.88f, pillW, font * 1.14f, slateSoft, 2.4f)
                                fittedText(line.label, labelX + 3f, baseline, font, pillW - 5f, ink, bold = true)
                            } else {
                                val (dColor, dBold) = detailTone(line.label, line.isHolidayDetail)
                                fittedText(line.label, labelX + 1f, baseline, font, pillW - 2f, dColor, line.bold || dBold)
                            }
                        }
                    }
                }
                return
            }
            throw IllegalArgumentException(crowded)
        }

        private fun compactCalendarDetail(value: String): String {
            val range = Regex("^(\\d{2}):(\\d{2}) - (\\d{2}):(\\d{2})$").matchEntire(value) ?: return value
            fun clock(h: String, m: String) = if (m == "00") h else "$h:$m"
            return clock(range.groupValues[1], range.groupValues[2]) + "–" + clock(range.groupValues[3], range.groupValues[4])
        }

        private fun calendars(months: List<YearMonth>, top: Float) {
            require(months.size <= 12) { "Per un calendario leggibile su una pagina, seleziona al massimo 12 mesi." }
            val columns = when {
                months.size == 1 -> 1
                months.size <= 4 -> 2
                else -> 3
            }
            val rows = (months.size + columns - 1) / columns
            val gap = 12f
            val contentW = width - 2 * margin
            val tileW = (contentW - gap * (columns - 1)) / columns

            data class LegendItem(val label: String, val color: Int?, val width: Float)
            val legendRows: List<List<LegendItem>> = if (months.size > 1 && data.options.includeTitle) {
                val rawItems = data.days.flatMap { it.shifts }
                    .distinctBy { it.code to it.name }
                    .map { shift ->
                        val label = "${shift.code}: ${shift.name}"
                        val swatchW = if (data.options.includeIcon) 14f else 0f
                        LegendItem(label, shift.color.takeIf { data.options.includeIcon }, swatchW + measure(label, 7f) + 12f)
                    } + listOf(
                    LegendItem("TG: giornata intera", null, measure("TG: giornata intera", 7f) + 12f),
                    LegendItem("REP: reperibilità", null, measure("REP: reperibilità", 7f) + 12f),
                )
                buildList {
                    var current = mutableListOf<LegendItem>()
                    var used = 0f
                    val maxRowW = contentW - 12f
                    rawItems.forEach { item ->
                        if (current.isNotEmpty() && used + item.width > maxRowW) {
                            add(current)
                            current = mutableListOf()
                            used = 0f
                        }
                        current.add(item)
                        used += item.width
                    }
                    if (current.isNotEmpty()) add(current)
                }
            } else {
                emptyList()
            }

            val legendHeight = if (legendRows.isEmpty()) 0f else legendRows.size * 12f + 10f
            val bottomReserve = if (legendHeight > 0f) 43f + legendHeight else 38f
            val tileH = (height - top - bottomReserve - gap * (rows - 1)) / rows

            if (legendRows.isNotEmpty()) {
                val legendTop = height - 35f - legendHeight
                box(margin, legendTop, contentW, legendHeight, slateSoft, 5f)
                legendRows.forEachIndexed { rowIdx, items ->
                    var lx = margin + 8f
                    val ly = legendTop + 12f + rowIdx * 12f
                    items.forEach { item ->
                        if (item.color != null) {
                            val resolved = resolveShiftPaint(item.color)
                            box(lx, ly - 6.8f, 10f, 8f, resolved.fill, 2.2f)
                            box(lx + 0.8f, ly - 5.8f, 1.5f, 6f, resolved.accentBar, 0.75f)
                            text(item.label, lx + 13.5f, ly, 7f, ink)
                        } else {
                            text(item.label, lx, ly, 7f, muted)
                        }
                        lx += item.width
                    }
                }
            }

            val byDate = data.days.associateBy { it.date }
            val compactDayLabels = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")

            months.forEachIndexed { index, month ->
                val x = margin + (index % columns) * (tileW + gap)
                val y = top + (index / columns) * (tileH + gap)
                val title = month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN))
                    .replaceFirstChar { it.uppercase() }

                val headerOffset = if (months.size > 1) {
                    box(x, y, tileW, 20f, accentTint, 5f)
                    box(x + 2.5f, y + 3.5f, 2.5f, 13f, accent, 1.25f)
                    fittedText(title, x + 9f, y + 13.5f, 9.5f, tileW - 14f, accentInk, bold = true)
                    box(x, y + 23f, tileW, 13f, slateSoft, 3.5f)
                    39f
                } else {
                    box(x, y, tileW, 16f, slateSoft, 5f)
                    20f
                }

                val gridTop = y + headerOffset
                val weekCount = (month.atDay(1).dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7
                val cellW = tileW / 7f
                val cellH = (tileH - headerOffset) / weekCount
                val wdY = if (months.size == 1) y + 11f else y + 32f
                val wdFont = if (months.size == 1) 7.5f else 6.8f

                compactDayLabels.forEachIndexed { dayIdx, label ->
                    val lw = measure(label, wdFont, bold = true)
                    text(label, x + (dayIdx + 0.5f) * cellW - lw / 2f, wdY, wdFont, if (dayIdx >= 5) accent else muted, true)
                }

                val offset = month.atDay(1).dayOfWeek.value - 1
                val compact = months.size > 1
                for (day in 1..month.lengthOfMonth()) {
                    val date = month.atDay(day)
                    val position = offset + day - 1
                    val left = x + (position % 7) * cellW
                    val cellTop = gridTop + (position / 7) * cellH
                    val row = byDate[date]
                    val isWeekend = position % 7 >= 5
                    val isHoliday = row?.holiday == true

                    val cardX = left + 1.5f
                    val cardW = cellW - 3f
                    val cardH = cellH - 3.5f
                    val cardRadius = if (compact) 3.5f else 5f
                    val cardFill = when {
                        isHoliday -> holidayCell
                        isWeekend -> weekendCell
                        else -> paper
                    }
                    box(cardX, cellTop, cardW, cardH, cardFill, cardRadius)
                    strokeBox(cardX, cellTop, cardW, cardH, if (isHoliday) holidayEdge else gridRule, 0.55f, cardRadius)

                    val daySize = if (compact) 7.5f else 9.5f
                    val dayCenter = left + cellW / 2f
                    val dayY = cellTop + if (compact) 8.5f else 11f
                    val dayStr = day.toString()
                    val dayW = measure(dayStr, daySize, bold = true)

                    if (isHoliday) {
                        val pillW = maxOf(dayW + 7f, if (compact) 14f else 18f)
                        val pillH = if (compact) 10f else 12.5f
                        box(dayCenter - pillW / 2f, cellTop + 1.5f, pillW, pillH, holiday, pillH / 2f)
                        text(dayStr, dayCenter - dayW / 2f, dayY, daySize, holidayInk, true)
                    } else {
                        text(dayStr, dayCenter - dayW / 2f, dayY, daySize, if (isWeekend) accentInk else ink, true)
                    }

                    if (row == null) continue
                    var selected: List<Pair<String, PdfShiftRow?>>? = null
                    var font = if (compact) 8f else 11f
                    while (font >= if (compact) 6.5f else 8f) {
                        val content = buildList<Pair<String, PdfShiftRow?>> {
                            row.shifts.forEach { shift ->
                                val titleLine = if (compact || shift.name.isBlank()) shift.code else shift.name
                                lines(titleLine, cellW - 8f, font, true).forEach { add(it to shift) }
                                shift.details.forEach { detail ->
                                    val formatted = if (compact) compactCalendarDetail(detail)
                                        .replace("Durata prevista:", "Dur:")
                                        .replace(" - ", "-")
                                        .replace("Stacco effettivo:", "Stacco:")
                                        .replace("Straordinario:", "Extra:")
                                        .replace("Permesso:", "Perm:")
                                        .replace("Durata:", "Dur:")
                                        .replace("Pausa:", "P:")
                                        .replace("Tutto il giorno", "TG")
                                        .replace("Reperibilità", "REP")
                                    else detail
                                    lines(formatted, cellW - 8f, font).forEach { add(it to null) }
                                }
                            }
                            row.details.forEach { detail ->
                                val formatted = if (compact) compactCalendarDetail(detail)
                                    .replace("Durata prevista:", "Dur:")
                                    .replace(" - ", "-")
                                    .replace("Stacco effettivo:", "Stacco:")
                                    .replace("Straordinario:", "Extra:")
                                    .replace("Permesso:", "Perm:")
                                    .replace("Durata:", "Dur:")
                                    .replace("Pausa:", "P:")
                                    .replace("Tutto il giorno", "TG")
                                    .replace("Reperibilità", "REP")
                                else detail
                                lines(formatted, cellW - 8f, font).forEach { add(it to null) }
                            }
                        }
                        if (content.size * (font + 2.2f) <= cellH - if (compact) 18.5f else 23f) {
                            selected = content
                            break
                        }
                        font -= 0.5f
                    }
                    require(selected != null) { crowded }

                    var cursor = cellTop + if (compact) 14.5f else 18.5f
                    selected.forEach { (value, shift) ->
                        val textBaseline = cursor + font * 0.86f
                        if (shift != null) {
                            val badgeX = left + 3f
                            val badgeW = cellW - 6f
                            val badgeTop = cursor - 0.2f
                            val badgeH = font + 1.4f
                            val badgeRadius = if (compact) 2.5f else 3.5f
                            if (data.options.includeIcon) {
                                val resolved = resolveShiftPaint(shift.color)
                                box(badgeX, badgeTop, badgeW, badgeH, resolved.fill, badgeRadius)
                                val stripeW = if (compact) 1.3f else 1.8f
                                box(badgeX + 1.3f, badgeTop + 1.1f, stripeW, (badgeH - 2.2f).coerceAtLeast(3f), resolved.accentBar, stripeW / 2f)
                                val textStart = badgeX + 1.3f + stripeW + 2.2f
                                fittedText(
                                    value = value,
                                    x = textStart,
                                    y = textBaseline,
                                    size = font,
                                    maxWidth = badgeW - (1.3f + stripeW + 4f),
                                    color = resolved.ink,
                                    bold = true,
                                )
                            } else {
                                box(badgeX, badgeTop, badgeW, badgeH, slateSoft, badgeRadius)
                                fittedText(value, badgeX + 3f, textBaseline, font, badgeW - 6f, ink, bold = true)
                            }
                        } else {
                            val (dColor, dBold) = detailTone(value, isHoliday && value in row.details)
                            fittedText(value, left + 4.5f, textBaseline, font, cellW - 9f, dColor, dBold)
                        }
                        cursor += font + 2.2f
                    }
                }
            }
        }
    }

}
