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
import com.turni.calendario.model.AssignedShift
import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.ShiftRepository
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.LocalTime
import kotlin.math.roundToInt

data class PdfShiftRow(
    val code: String,
    val name: String,
    val color: Int,
    val time: String?,
    val details: List<String>,
    val isAllDay: Boolean = false,
    val startTime: String? = time,
    val endTime: String? = null,
)
data class PdfDayRow(
    val date: LocalDate,
    val shifts: List<PdfShiftRow>,
    val details: List<String>,
    val holiday: Boolean = false,
    val onCall: Boolean = false,
)
data class PdfExportSnapshot(val work: String, val options: PdfExportOptions, val days: List<PdfDayRow>)

/** Capture repository state on the UI thread; render this immutable snapshot on IO. */
object PdfGenerator {
    fun capture(options: PdfExportOptions): PdfExportSnapshot {
        val work = ShiftRepository.activeWorkProfile
        val defsById = ShiftRepository.shiftDefinitions.associateBy { it.id }
        val profileTags = ShiftRepository.dayTagsFor(work.id)
        val days = PdfExportPlan.dates(options).map { date ->
            val all = sortAssignedShiftsChronologically(
                shifts = ShiftRepository.getShiftsForDate(date, work.id),
                defsById = defsById,
            )
            val extra = ShiftRepository.getDayExtraInfo(date, work.id)
            val rows = all.mapNotNull { shift ->
                val def = defsById[shift.shiftDefId] ?: return@mapNotNull null
                if (options.filterShiftId != null && def.id != options.filterShiftId) return@mapNotNull null
                val legacyPrimary = all.firstOrNull()?.id == shift.id
                val splitOt = (if (shift.startOvertimeHours.isFinite()) shift.startOvertimeHours.coerceAtLeast(0f) else 0f) +
                    (if (shift.endOvertimeHours.isFinite()) shift.endOvertimeHours.coerceAtLeast(0f) else 0f)
                val shiftOt = maxOf(
                    if (shift.overtimeHours.isFinite()) shift.overtimeHours.coerceAtLeast(0f) else 0f,
                    splitOt,
                )
                val overtime = if (shiftOt > 0f) shiftOt else if (legacyPrimary) extra.overtimeHours else 0f
                val actualStart = shift.actualStartTime
                val actualEnd = shift.actualEndTime ?: if (legacyPrimary) extra.actualEndTime else null
                val splitPerm = (if (shift.startPermissionHours.isFinite()) shift.startPermissionHours.coerceAtLeast(0f) else 0f) +
                    (if (shift.endPermissionHours.isFinite()) shift.endPermissionHours.coerceAtLeast(0f) else 0f)
                val totalPerm = maxOf(
                    if (shift.permissionHours.isFinite()) shift.permissionHours.coerceAtLeast(0f) else 0f,
                    splitPerm,
                )
                val effectiveStart = if (def.isAllDay) null else (shift.customStartTime ?: def.startTime)
                val effectiveEnd = if (def.isAllDay) null else (shift.customEndTime ?: def.endTime)
                val lines = buildList {
                    if (options.includeTime) add(if (def.isAllDay) "Tutto il giorno" else "$effectiveStart - $effectiveEnd")
                    if (actualStart != null && options.includeTime) add("Ingresso effettivo: $actualStart")
                    if (actualEnd != null && options.includeTime) add("Stacco effettivo: $actualEnd")
                    if (options.includeDuration && !def.isAllDay) {
                        val start = LocalTime.parse(effectiveStart)
                        val end = LocalTime.parse(effectiveEnd)
                        val elapsed = (java.time.Duration.between(start, end).toMinutes() + 1440) % 1440
                        val minutes = ((if (elapsed == 0L) 1440 else elapsed) - def.breakMinutes).coerceAtLeast(0)
                        add("Durata prevista: ${formatMinutes(minutes.toInt())}")
                    }
                    if (options.includeBreak && def.breakMinutes > 0) add("Pausa: ${def.breakMinutes} min${def.breakStartTime?.let { " dalle $it" } ?: ""}")
                    if (overtime > 0) add("Straordinario: ${formatMinutes((overtime * 60).roundToInt())}")
                    val permMinutes = if (totalPerm > 0f) (totalPerm * 60).roundToInt() else 0
                    if (permMinutes > 0) add("Permesso: −${formatMinutes(permMinutes)}")
                    if (options.includeLocation && (shift.isTrasferta || (legacyPrimary && extra.isTrasferta))) add("Trasferta")
                    if (options.includeNotes && shift.notes.isNotBlank()) add(shift.notes)
                    if (options.includeNotes && shift.tags.isNotEmpty()) add(shift.tags.joinToString(" · "))
                }
                PdfShiftRow(
                    code = def.shortCode,
                    name = if (options.includeTitle) def.name else "",
                    color = def.colorHex.toInt(),
                    time = if (options.includeTime && !def.isAllDay) effectiveStart else null,
                    details = lines,
                    isAllDay = def.isAllDay,
                    startTime = effectiveStart,
                    endTime = effectiveEnd,
                )
            }
            val onCall = ShiftRepository.isOnCall(date, work.id)
            val dayLines = buildList {
                if (options.includeEvents) ShiftRepository.isHoliday(date)?.let { add(it.name) }
                if (options.includeNotes && extra.notes.isNotBlank()) add(extra.notes)
                if (options.includeNotes) extra.tagIds.mapNotNull { id -> profileTags.firstOrNull { it.id == id }?.name }.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" · ")) }
            }
            PdfDayRow(
                date = date,
                shifts = rows,
                details = dayLines,
                holiday = options.includeEvents && ShiftRepository.isHoliday(date) != null,
                onCall = onCall,
            )
        }
        return PdfExportSnapshot(work.name, options, days)
    }

    fun writePdf(context: Context, snapshot: PdfExportSnapshot): File {
        val output = File.createTempFile("turnisync-", ".pdf", context.cacheDir)
        val presentation = PdfExportPlan.presentation(snapshot.options)
        val months = snapshot.days.map { YearMonth.from(it.date) }.distinct().size
        val papers = when {
            presentation == PdfPresentation.LIST -> listOf(595 to 842, 842 to 1191)
            presentation == PdfPresentation.MONTHS -> if (months > 1) listOf(595 to 842, 1191 to 1684) else listOf(595 to 842)
            snapshot.options.mode == PdfExportMode.PERSONALIZZATO -> when (resolveCustomPeriodLayout(snapshot.days.size, months)) {
                CustomPeriodLayout.SHORT_DETAILED -> listOf(595 to 842, 842 to 1191)
                CustomPeriodLayout.MEDIUM_TWO_COLUMN -> overviewPaperSizes(2)
                CustomPeriodLayout.EXTENDED_OVERVIEW -> overviewPaperSizes(months)
            }
            else -> overviewPaperSizes(months)
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
            detail.startsWith("Straordinario:") || detail.startsWith("Extra:") || detail.startsWith("+") -> overtimeInk to false
            detail.startsWith("Permesso:") || detail.startsWith("Perm:") || detail.startsWith("−") -> warningInk to false
            detail == "Reperibilità" || detail == "REP" -> warningInk to true
            detail == "Trasferta" || detail == "TR" -> accentInk to false
            isHoliday -> holidayInk to true
            else -> muted to false
        }

        private fun drawOnCallPill(x: Float, baseline: Float, font: Float): Float {
            val pillW = measure("REP", font, bold = true) + 8f
            val pillH = font + 3.2f
            val pillTop = baseline - font * 0.86f - 0.8f
            box(x, pillTop, pillW, pillH, warningFill, 3.2f)
            text("REP", x + 4f, baseline - 0.2f, font, warningInk, bold = true)
            return pillW
        }

        fun render() {
            val months = data.days.map { YearMonth.from(it.date) }.distinct()
            val presentation = PdfExportPlan.presentation(data.options)
            val customLayout = if (data.options.mode == PdfExportMode.PERSONALIZZATO) {
                resolveCustomPeriodLayout(data.days.size, months.size)
            } else null
            val calendar = presentation != PdfPresentation.LIST && customLayout != CustomPeriodLayout.SHORT_DETAILED
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
                    d.shifts.count { s -> s.details.any { it.startsWith("Straordinario:") || it.startsWith("+") } }
                }
                val permCount = data.days.sumOf { d ->
                    d.shifts.count { s -> s.details.any { it.startsWith("Permesso:") || it.startsWith("−") } }
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

                when {
                    presentation == PdfPresentation.LIST || customLayout == CustomPeriodLayout.SHORT_DETAILED -> list(top)
                    presentation == PdfPresentation.MONTHS -> calendars(months, top)
                    customLayout == CustomPeriodLayout.MEDIUM_TWO_COLUMN && months.size == 1 ->
                        overviewSplitTwoColumns(months.first(), top)
                    else -> overview(months, top)
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
            val timeInlineOnLastTitleLine: Boolean,
            val timeOnNewLine: Boolean,
            val metaLines: List<List<String>>,
            val noteLines: List<String>,
        ) {
            val headLines: Int
                get() = maxOf(1, titleLines.size + (if (timeOnNewLine) 1 else 0))

            val lineCount: Int
                get() = maxOf(1, headLines + metaLines.size + noteLines.size)
        }

        private data class ListDayBlock(
            val shifts: List<ListShiftBlock>,
            val onCall: Boolean,
            val repInlineOnFirstShift: Boolean,
            val repExtraLine: Boolean,
            val dayDetails: List<String>,
            val isEmptyDay: Boolean,
        )

        private fun wrapMetaSegments(segments: List<String>, maxWidth: Float, size: Float): List<List<String>> {
            if (segments.isEmpty()) return emptyList()
            val sepW = measure(" · ", size)
            val result = mutableListOf<MutableList<String>>()
            var current = mutableListOf<String>()
            var used = 0f
            segments.forEach { seg ->
                val segW = measure(seg, size)
                val needed = if (current.isEmpty()) segW else sepW + segW
                if (current.isNotEmpty() && used + needed > maxWidth) {
                    result.add(current)
                    current = mutableListOf(seg)
                    used = segW
                } else {
                    current.add(seg)
                    used += needed
                }
            }
            if (current.isNotEmpty()) result.add(current)
            return result
        }

        private fun list(top: Float) {
            val days = data.days.filter {
                data.options.includeEmptyRows ||
                    it.shifts.isNotEmpty() ||
                    it.onCall ||
                    it.details.isNotEmpty()
            }
            if (days.isEmpty()) {
                text("Nessun turno nel periodo e nel filtro scelti.", margin, top + 15f, 9.5f, muted)
                return
            }
            val dayDates = days.map { it.date }
            val distinctMonths = dayDates.map { YearMonth.from(it) }.distinct()
            val columnOrder = preferredListColumns(days.size, distinctMonths.size)
            val available = height - top - 38f - 18f

            for (font in listOf(10f, 9.5f, 9f, 8.5f, 8f, 7.5f, 7f, 6.5f)) {
                for (columns in columnOrder) {
                    val gap = 14f
                    val columnWidth = (width - 2 * margin - gap * (columns - 1)) / columns
                    val dateBadgeW = 40f + font * 1.4f
                    val hasCodes = days.any { d -> d.shifts.any { it.code.isNotBlank() } }
                    val codeFont = (font - 1.2f).coerceAtLeast(5.8f)
                    val maxCodeW = if (hasCodes) {
                        days.flatMap { it.shifts }.maxOfOrNull { measure(it.code, codeFont, bold = true) } ?: 0f
                    } else 0f
                    val codeBadgeW = if (hasCodes) minOf(maxOf(font * 2.9f, maxCodeW + 11f), font * 4.2f) else 0f
                    val entryOffset = dateBadgeW + 7f
                    val shiftTextOffset = if (hasCodes) entryOffset + codeBadgeW + 6.5f else entryOffset
                    val shiftContentW = columnWidth - shiftTextOffset - 4f
                    if (shiftContentW < 60f) continue

                    val leading = font + 2.5f
                    val timeFont = (font - 1.4f).coerceAtLeast(5.8f)
                    val repFont = (font - 1.6f).coerceAtLeast(5.6f)
                    val detailFont = (font - 0.8f).coerceAtLeast(6f)
                    val dayDetailW = columnWidth - entryOffset - 4f
                    val repPillW = measure("REP", repFont, bold = true) + 8f

                    val blocks = days.map { day ->
                        val dayHasLegacyRep = day.details.any { it == "Reperibilità" || it == "REP" }
                        val isOnCall = day.onCall || dayHasLegacyRep
                        val filteredDayDetails = day.details.filterNot { it == "Reperibilità" || it == "REP" }

                        val shiftBlocks = day.shifts.map { shift ->
                            val rawTime = if (data.options.includeTime) shift.details.firstOrNull().orEmpty() else ""
                            val time = if (rawTime.isNotBlank()) formatPdfDetail(rawTime, compact = false) else ""
                            val remainingDetails = if (data.options.includeTime) shift.details.drop(1) else shift.details
                            val metaSegments = remainingDetails
                                .filter(::isCompactMetaDetail)
                                .map { formatPdfDetail(it, compact = false) }
                            val noteSegments = remainingDetails
                                .filterNot(::isCompactMetaDetail)
                                .map { formatPdfDetail(it, compact = false) }

                            val timeW = if (time.isNotBlank()) measure(time, timeFont) + 9f else 0f
                            val titleLines: List<String>
                            val timeInlineOnLastTitleLine: Boolean
                            val timeOnNewLine: Boolean
                            if (shift.name.isBlank()) {
                                titleLines = emptyList()
                                timeInlineOnLastTitleLine = false
                                timeOnNewLine = false
                            } else {
                                val singleW = measure(shift.name, font, bold = true)
                                if (time.isNotBlank() && singleW + 5.5f + timeW <= shiftContentW) {
                                    titleLines = listOf(shift.name)
                                    timeInlineOnLastTitleLine = true
                                    timeOnNewLine = false
                                } else {
                                    titleLines = lines(shift.name, shiftContentW, font, bold = true)
                                    val lastW = measure(titleLines.last(), font, bold = true)
                                    if (time.isNotBlank() && lastW + 5.5f + timeW <= shiftContentW) {
                                        timeInlineOnLastTitleLine = true
                                        timeOnNewLine = false
                                    } else {
                                        timeInlineOnLastTitleLine = false
                                        timeOnNewLine = time.isNotBlank()
                                    }
                                }
                            }
                            ListShiftBlock(
                                code = shift.code,
                                color = shift.color.takeIf { data.options.includeIcon },
                                titleLines = titleLines,
                                time = time,
                                timeInlineOnLastTitleLine = timeInlineOnLastTitleLine,
                                timeOnNewLine = timeOnNewLine,
                                metaLines = wrapMetaSegments(metaSegments, shiftContentW, detailFont),
                                noteLines = noteSegments.flatMap { lines(it, shiftContentW, detailFont) },
                            )
                        }

                        val repInlineOnFirstShift = if (isOnCall && shiftBlocks.isNotEmpty()) {
                            val first = shiftBlocks.first()
                            val firstTimeW = if (first.time.isNotBlank()) measure(first.time, timeFont) + 9f else 0f
                            val usedLine1 = when {
                                first.titleLines.isEmpty() -> firstTimeW
                                first.titleLines.size == 1 -> {
                                    measure(first.titleLines[0], font, bold = true) +
                                        (if (first.timeInlineOnLastTitleLine && first.time.isNotBlank()) 5.5f + firstTimeW else 0f)
                                }
                                else -> measure(first.titleLines[0], font, bold = true)
                            }
                            shiftContentW - usedLine1 >= repPillW + 5f
                        } else {
                            false
                        }
                        val repExtraLine = isOnCall && shiftBlocks.isNotEmpty() && !repInlineOnFirstShift
                        val dayDetails = filteredDayDetails.flatMap { lines(it, dayDetailW, detailFont) }
                        ListDayBlock(
                            shifts = shiftBlocks,
                            onCall = isOnCall,
                            repInlineOnFirstShift = repInlineOnFirstShift,
                            repExtraLine = repExtraLine,
                            dayDetails = dayDetails,
                            isEmptyDay = shiftBlocks.isEmpty() && !isOnCall && dayDetails.isEmpty(),
                        )
                    }

                    val heights = blocks.map { block ->
                        if (block.isEmptyDay) {
                            leading + 6f
                        } else {
                            val shiftLines = block.shifts.sumOf { it.lineCount }
                            val onCallLines = when {
                                block.shifts.isEmpty() && block.onCall -> 1
                                block.repExtraLine -> 1
                                else -> 0
                            }
                            val totalLines = maxOf(1, shiftLines + onCallLines + block.dayDetails.size)
                            val shiftGap = (block.shifts.size - 1).coerceAtLeast(0) * 2f
                            totalLines * leading + shiftGap + 6f
                        }
                    }
                    val packed = planListColumns(dayDates, heights, columns, available) ?: continue

                    val splitByMonth = columns == 2 && distinctMonths.size == 2 &&
                        packed.size == 2 &&
                        packed[0].all { YearMonth.from(days[it].date) == distinctMonths[0] } &&
                        packed[1].all { YearMonth.from(days[it].date) == distinctMonths[1] }

                    packed.forEachIndexed { column, indexes ->
                        val x = margin + column * (columnWidth + gap)
                        val entryX = x + entryOffset
                        val shiftTextX = x + shiftTextOffset
                        box(x, top, columnWidth, 17f, slateSoft, 4f)
                        text("GIORNO", x + 5f, top + 11.2f, 7.2f, accentInk, true)
                        val rightColHeader = if (data.options.includeTime) "TURNO · ORARIO" else "TURNO"
                        text(rightColHeader, entryX, top + 11.2f, 7.2f, accentInk, true)
                        if (splitByMonth && column in distinctMonths.indices) {
                            val monthTag = formatItalianMonthHeader(distinctMonths[column], monthShortYearFormat).uppercase(Locale.ITALIAN)
                            text(monthTag, x + columnWidth - 5f - measure(monthTag, 7f, true), top + 11.2f, 7f, muted, true)
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
                                if (block.shifts.isEmpty() && block.onCall) {
                                    drawOnCallPill(entryX, baseline, repFont)
                                    baseline += leading
                                }
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
                                    var line1RightEnd = shiftTextX
                                    if (s.titleLines.isEmpty()) {
                                        if (s.time.isNotBlank()) {
                                            val timeW = measure(s.time, timeFont) + 9f
                                            box(shiftTextX, shiftStartBaseline - font + 0.6f, timeW, font + 2.4f, slateSoft, 3.5f)
                                            text(s.time, shiftTextX + 4.5f, shiftStartBaseline - 0.3f, timeFont, muted)
                                            line1RightEnd = shiftTextX + timeW
                                        }
                                        baseline += leading
                                    } else {
                                        s.titleLines.forEachIndexed { tIdx, titleLine ->
                                            text(titleLine, shiftTextX, baseline, font, ink, true)
                                            val lineEnd = shiftTextX + measure(titleLine, font, true)
                                            if (tIdx == 0) line1RightEnd = lineEnd
                                            if (tIdx == s.titleLines.lastIndex && s.timeInlineOnLastTitleLine && s.time.isNotBlank()) {
                                                val timeW = measure(s.time, timeFont) + 9f
                                                val pillX = lineEnd + 5.5f
                                                box(pillX, baseline - font + 0.6f, timeW, font + 2.4f, slateSoft, 3.5f)
                                                text(s.time, pillX + 4.5f, baseline - 0.3f, timeFont, muted)
                                                if (tIdx == 0) line1RightEnd = pillX + timeW
                                            }
                                            baseline += leading
                                        }
                                        if (s.timeOnNewLine && s.time.isNotBlank()) {
                                            val timeW = measure(s.time, timeFont) + 9f
                                            box(shiftTextX, baseline - font + 0.6f, timeW, font + 2.4f, slateSoft, 3.5f)
                                            text(s.time, shiftTextX + 4.5f, baseline - 0.3f, timeFont, muted)
                                            baseline += leading
                                        }
                                    }

                                    if (sIdx == 0 && block.repInlineOnFirstShift) {
                                        drawOnCallPill(line1RightEnd + 5f, shiftStartBaseline, repFont)
                                    }

                                    s.metaLines.forEach { segments ->
                                        if (segments.size == 1) {
                                            val seg = segments[0]
                                            val (dColor, dBold) = detailTone(seg)
                                            fittedText(seg, shiftTextX, baseline, detailFont, shiftContentW, dColor, dBold)
                                        } else {
                                            var segX = shiftTextX
                                            segments.forEachIndexed { segIdx, seg ->
                                                if (segIdx > 0) {
                                                    text(" · ", segX, baseline, detailFont, softMuted)
                                                    segX += measure(" · ", detailFont)
                                                }
                                                val (dColor, dBold) = detailTone(seg)
                                                text(seg, segX, baseline, detailFont, dColor, dBold)
                                                segX += measure(seg, detailFont, dBold)
                                            }
                                        }
                                        baseline += leading
                                    }
                                    s.noteLines.forEach { nLine ->
                                        text(nLine, shiftTextX, baseline, detailFont, muted)
                                        baseline += leading
                                    }
                                }
                                if (block.repExtraLine) {
                                    drawOnCallPill(entryX, baseline, repFont)
                                    baseline += leading
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
            val badgeGroupSize: Int = 1,
            val badgeLineIndex: Int = 0,
            val isHolidayDetail: Boolean = false,
            val isOnCallPill: Boolean = false,
        )

        private fun buildOverviewLines(
            day: PdfDayRow,
            pillW: Float,
            textAvailW: Float,
            font: Float,
        ): List<OverviewLine> = buildList {
            day.shifts.forEach { shift ->
                val badgeColor = shift.color.takeIf { data.options.includeIcon }
                if (shift.name.isNotBlank()) {
                    val titleLines = lines(shift.name, textAvailW, font, true)
                    titleLines.forEachIndexed { idx, label ->
                        add(
                            OverviewLine(
                                label = label,
                                color = badgeColor,
                                bold = true,
                                isShiftBadge = true,
                                badgeGroupSize = titleLines.size,
                                badgeLineIndex = idx,
                            ),
                        )
                    }
                }
                val markFirstDetail = shift.name.isBlank()
                val firstDetailIdx = size
                shift.details.forEach { detail ->
                    val compact = formatPdfDetail(detail, compact = true)
                    val avail = if (markFirstDetail && size == firstDetailIdx) textAvailW else pillW - 2f
                    val wrapped = lines(compact, avail, font)
                    wrapped.forEachIndexed { idx, label ->
                        val asBadge = markFirstDetail && size == firstDetailIdx
                        add(
                            OverviewLine(
                                label = label,
                                color = badgeColor.takeIf { asBadge },
                                bold = asBadge,
                                isShiftBadge = asBadge,
                                badgeGroupSize = if (asBadge) wrapped.size else 1,
                                badgeLineIndex = if (asBadge) idx else 0,
                            ),
                        )
                    }
                }
                if (shift.name.isBlank() && size == firstDetailIdx) {
                    add(OverviewLine(shift.code, badgeColor, bold = true, isShiftBadge = true))
                }
            }
            val hasLegacyRep = day.details.any { it == "Reperibilità" || it == "REP" }
            if (day.onCall || hasLegacyRep) {
                add(OverviewLine("REP", bold = true, isOnCallPill = true))
            }
            day.details.filterNot { it == "Reperibilità" || it == "REP" }.forEach { detail ->
                lines(detail, pillW - 2f, font).forEach {
                    add(OverviewLine(it, isHolidayDetail = day.holiday))
                }
            }
        }

        private fun overviewSplitTwoColumns(month: YearMonth, top: Float) {
            if (data.days.isEmpty()) return
            val sortedDays = data.days.sortedBy { it.date }
            val split = (sortedDays.size + 1) / 2
            val leftDates = sortedDays.take(split).map { it.date }
            val rightDates = sortedDays.drop(split).map { it.date }
            val gap = 10f
            val columnWidth = (width - 2 * margin - gap) / 2f
            val gridTop = top + 22f
            val availableHeight = height - 42f - gridTop
            val badgeXOffset = 25f
            val pillW = (columnWidth - badgeXOffset - 2f).coerceAtLeast(20f)
            val textAvailW = (pillW - 6.5f).coerceAtLeast(14f)
            val byDate = data.days.associateBy { it.date }

            for (font in overviewFontCandidates(2)) {
                val leading = font + 2.2f
                val baseRowHeight = if (font >= 9f) 18.5f else 16f
                val contents = data.days.associate { day ->
                    day.date to buildOverviewLines(day, pillW, textAvailW, font)
                }
                fun minH(d: LocalDate): Float = maxOf(baseRowHeight, contents.getValue(d).size * leading + 3.5f)
                val leftMins = leftDates.map(::minH)
                val rightMins = rightDates.map(::minH)
                if (leftMins.sum() > availableHeight || rightMins.sum() > availableHeight) continue
                val maxRows = maxOf(leftMins.size, rightMins.size, 1)
                val minSpare = minOf(availableHeight - leftMins.sum(), availableHeight - rightMins.sum())
                val extraPerRow = (minSpare / maxRows).coerceAtLeast(0f)

                val columnGroups = listOf(leftDates to leftMins, rightDates to rightMins)
                columnGroups.forEachIndexed { colIdx, (colDates, mins) ->
                    if (colDates.isEmpty()) return@forEachIndexed
                    val x = margin + colIdx * (columnWidth + gap)
                    val heights = mins.map { capOverviewRowHeight(it, extraPerRow) }
                    val tops = heights.runningFold(gridTop, Float::plus)
                    val slots = colDates.mapIndexed { rowIndex, date -> OverviewSlot(rowIndex, date) }
                    val plan = OverviewColumnPlan(month, slots, heights, tops)
                    val headerText = "${formatItalianMonthHeader(month, monthFullYearFormat)} (${colDates.first().dayOfMonth}–${colDates.last().dayOfMonth})"
                    drawOverviewHeaderBox(x, top, columnWidth, headerText, 9.5f)
                    drawOverviewColumnRows(
                        x = x,
                        columnWidth = columnWidth,
                        plan = plan,
                        byDate = byDate,
                        contents = contents,
                        font = font,
                        leading = leading,
                        baseRowHeight = baseRowHeight,
                        badgeXOffset = badgeXOffset,
                        pillW = pillW,
                        textAvailW = textAvailW,
                        numSize = if (font >= 9f) 8.5f else 7.5f,
                        wdSize = if (font >= 9f) 5.8f else 5.2f,
                    )
                }
                return
            }
            throw IllegalArgumentException(crowded)
        }

        private fun overview(months: List<YearMonth>, top: Float) {
            require(months.size <= PdfExportPlan.MAX_OVERVIEW_MONTHS) { PdfExportPlan.OVERVIEW_MAX_MONTHS_ERROR }
            if (data.days.isEmpty()) return
            val gap = when {
                months.size <= 3 -> 8f
                months.size <= 6 -> 6f
                else -> 4.5f
            }
            val columnWidth = (width - 2 * margin - gap * (months.size - 1)) / months.size
            val gridTop = top + 22f
            val availableHeight = height - 42f - gridTop
            val badgeXOffset = if (months.size <= 3) 25f else 21f
            val pillW = (columnWidth - badgeXOffset - 2f).coerceAtLeast(20f)
            val textAvailW = (pillW - 6.5f).coerceAtLeast(14f)
            val byDate = data.days.associateBy { it.date }
            val dayDates = data.days.map { it.date }

            for (font in overviewFontCandidates(months.size)) {
                val leading = font + 2.2f
                val baseRowHeight = if (font >= 9f) 18.5f else 16f
                val contents = data.days.associate { day ->
                    day.date to buildOverviewLines(day, pillW, textAvailW, font)
                }
                val columnPlans = planOverviewColumns(
                    months = months,
                    dates = dayDates,
                    mode = data.options.mode,
                    gridTop = gridTop,
                    availableHeight = availableHeight,
                    baseRowHeight = baseRowHeight,
                    leading = leading,
                    lineCounts = contents.mapValues { it.value.size },
                ) ?: continue

                val hSize = when {
                    months.size <= 2 -> 9.5f
                    months.size <= 5 -> 9f
                    else -> 8.5f
                }
                val numSize = if (font >= 9f) 8.5f else 7.5f
                val wdSize = if (font >= 9f) 5.8f else 5.2f

                columnPlans.forEachIndexed { index, plan ->
                    val x = margin + index * (columnWidth + gap)
                    drawOverviewColumnHeader(x, top, columnWidth, plan.month, months.size, hSize)
                    drawOverviewColumnRows(
                        x = x,
                        columnWidth = columnWidth,
                        plan = plan,
                        byDate = byDate,
                        contents = contents,
                        font = font,
                        leading = leading,
                        baseRowHeight = baseRowHeight,
                        badgeXOffset = badgeXOffset,
                        pillW = pillW,
                        textAvailW = textAvailW,
                        numSize = numSize,
                        wdSize = wdSize,
                    )
                }
                return
            }
            throw IllegalArgumentException(crowded)
        }

        private fun drawOverviewHeaderBox(x: Float, top: Float, columnWidth: Float, headerText: String, hSize: Float) {
            box(x, top, columnWidth, 19f, accentTint, 4f)
            box(x + 1f, top, columnWidth - 2f, 2.2f, accent, 1.1f)
            val hTextW = measure(headerText, hSize, bold = true)
            val drawW = minOf(hTextW, columnWidth - 8f)
            val hx = x + ((columnWidth - drawW) / 2f).coerceAtLeast(4f)
            fittedText(headerText, hx, top + 13.5f, hSize, columnWidth - 8f, accentInk, bold = true)
        }

        private fun drawOverviewColumnHeader(
            x: Float,
            top: Float,
            columnWidth: Float,
            month: YearMonth,
            monthCount: Int,
            hSize: Float,
        ) {
            val fullCandidate = formatItalianMonthHeader(month, monthFullYearFormat)
            val fitsFull = measure(fullCandidate, hSize, bold = true) <= columnWidth - 8f
            val headerText = overviewColumnHeaderText(month, data.options.mode, monthCount, fitsFull)
            drawOverviewHeaderBox(x, top, columnWidth, headerText, hSize)
        }

        private fun drawOverviewColumnRows(
            x: Float,
            columnWidth: Float,
            plan: OverviewColumnPlan,
            byDate: Map<LocalDate, PdfDayRow>,
            contents: Map<LocalDate, List<OverviewLine>>,
            font: Float,
            leading: Float,
            baseRowHeight: Float,
            badgeXOffset: Float,
            pillW: Float,
            textAvailW: Float,
            numSize: Float,
            wdSize: Float,
        ) {
            val holidayBoxW = if (badgeXOffset >= 24f) 19.5f else 16f
            val holidayBoxH = if (numSize >= 8f) 15.5f else 13.5f
            val numOffsetY = if (numSize >= 8f) 9.2f else 8f
            val wdOffsetY = if (numSize >= 8f) 15.5f else 13.6f

            plan.slots.forEachIndexed { slotIdx, slot ->
                val date = slot.date
                val row = byDate[date] ?: return@forEachIndexed
                val y = plan.rowTops[slot.rowIndex]
                val rowH = plan.rowHeights[slot.rowIndex]
                val linesForDate = contents.getValue(date)
                val minContentH = maxOf(baseRowHeight, linesForDate.size * leading + 3.5f)
                val topPad = if (data.options.mode == PdfExportMode.ANNO) {
                    0f
                } else {
                    ((rowH - minContentH) / 2f).coerceIn(0f, 5f)
                }

                val isWeekend = date.dayOfWeek.value >= 6
                val fill = when {
                    row.holiday -> holidayCell
                    isWeekend -> weekendCell
                    slot.rowIndex % 2 == 1 -> paper
                    else -> Color.WHITE
                }
                box(x, y, columnWidth, rowH - 0.4f, fill, 0f)
                val isWeekStart = date.dayOfWeek.value == 1
                box(x, y, columnWidth, if (isWeekStart) 0.75f else 0.35f, if (isWeekStart) weekRule else gridRule, 0f)

                if (row.holiday) {
                    box(x + 2f, y + 1.5f + topPad, holidayBoxW, holidayBoxH, holiday, 3f)
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
                text(date.dayOfMonth.toString(), x + 3.2f, y + topPad + numOffsetY, numSize, numInk, true)
                text(date.format(weekdayFormat).uppercase(Locale.ITALIAN).take(3), x + 3.2f, y + topPad + wdOffsetY, wdSize, wdInk, true)

                val labelX = x + badgeXOffset
                linesForDate.forEachIndexed { lineIndex, line ->
                    val baseline = y + topPad + font + 1.2f + lineIndex * leading
                    if (line.isOnCallPill) {
                        val repFont = (font - 0.8f).coerceAtLeast(5.5f)
                        val repW = minOf(pillW, measure("REP", repFont, bold = true) + 7f)
                        val pTop = baseline - font * 0.84f
                        val pH = font * 1.08f
                        box(labelX, pTop, repW, pH, warningFill, 2.4f)
                        fittedText("REP", labelX + 3.5f, baseline - 0.2f, repFont, repW - 7f, warningInk, bold = true)
                    } else if (line.isShiftBadge && line.color != null) {
                        val resolved = resolveShiftPaint(line.color)
                        if (line.badgeLineIndex == 0) {
                            val pTop = baseline - font * 0.88f
                            val pH = (line.badgeGroupSize - 1) * leading + font * 1.14f
                            box(labelX, pTop, pillW, pH, resolved.fill, 2.4f)
                            box(labelX + 1.2f, pTop + 1.2f, 1.6f, (pH - 2.4f).coerceAtLeast(2f), resolved.accentBar, 0.8f)
                        }
                        fittedText(line.label, labelX + 4.5f, baseline, font, textAvailW, resolved.ink, bold = true)
                    } else if (line.isShiftBadge) {
                        if (line.badgeLineIndex == 0) {
                            val pTop = baseline - font * 0.88f
                            val pH = (line.badgeGroupSize - 1) * leading + font * 1.14f
                            box(labelX, pTop, pillW, pH, slateSoft, 2.4f)
                        }
                        fittedText(line.label, labelX + 3f, baseline, font, pillW - 5f, ink, bold = true)
                    } else {
                        val (dColor, dBold) = detailTone(line.label, line.isHolidayDetail)
                        fittedText(line.label, labelX + 1f, baseline, font, pillW - 2f, dColor, line.bold || dBold)
                    }
                }

                val nextSlot = plan.slots.getOrNull(slotIdx + 1)
                if (nextSlot == null || nextSlot.rowIndex != slot.rowIndex + 1) {
                    box(x, y + rowH - 0.4f, columnWidth, 0.4f, gridRule, 0f)
                }
            }
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
                val rawItems = sortShiftRowsChronologically(
                    data.days.flatMap { it.shifts }.distinctBy { it.code to it.name },
                ).map { shift ->
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
                val headerHasRepSlot = cellW >= 54f
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
                    val rowHasOnCall = row.onCall || row.details.any { it == "Reperibilità" || it == "REP" }
                    val showHeaderRep = rowHasOnCall && headerHasRepSlot
                    if (showHeaderRep) {
                        val repSize = if (compact) 5.5f else 6.3f
                        val repPillW = measure("REP", repSize, bold = true) + 5.5f
                        val repPillH = repSize + 3.2f
                        val repX = cardX + cardW - repPillW - 2f
                        val repTop = cellTop + 1.8f
                        box(repX, repTop, repPillW, repPillH, warningFill, 2.5f)
                        text("REP", repX + 2.75f, repTop + repSize + 0.8f, repSize, warningInk, bold = true)
                    }

                    var selected: List<CalendarCellElement>? = null
                    var selectedFont = if (compact) 8f else 11f
                    val minFont = 6.5f
                    val badgeW = cellW - 6f
                    val stripeW = if (compact) 1.3f else 1.8f
                    val badgeTextAvail = (badgeW - (1.3f + stripeW + 4f)).coerceAtLeast(12f)
                    while (selectedFont >= minFont) {
                        val f = selectedFont
                        val content = buildCalendarCellElements(
                            row = row,
                            compact = compact,
                            includeHeaderOnCall = showHeaderRep,
                            wrapBadgeTitle = { title ->
                                if (compact || title.isBlank()) listOf(title)
                                else lines(title, badgeTextAvail, f, true)
                            },
                            wrapDetail = { detail ->
                                lines(detail, cellW - 9f, f)
                            },
                        )
                        val totalLines = content.sumOf { el ->
                            when (el) {
                                is CalendarCellElement.ShiftBadge -> el.lines.size
                                is CalendarCellElement.OnCallPill -> 1
                                is CalendarCellElement.DetailLine -> 1
                            }
                        }
                        if (totalLines * (f + 2.2f) <= cellH - if (compact) 18.5f else 23f) {
                            selected = content
                            break
                        }
                        selectedFont -= 0.5f
                    }
                    require(selected != null) { crowded }

                    val lineStep = selectedFont + 2.2f
                    var cursor = cellTop + if (compact) 14.5f else 18.5f
                    selected.forEach { element ->
                        when (element) {
                            is CalendarCellElement.ShiftBadge -> {
                                val badgeX = left + 3f
                                val badgeTop = cursor - 0.2f
                                val lineCount = element.lines.size.coerceAtLeast(1)
                                val badgeH = (lineCount - 1) * lineStep + selectedFont + 1.6f
                                val badgeRadius = if (compact) 2.5f else 3.5f
                                if (data.options.includeIcon) {
                                    val resolved = resolveShiftPaint(element.shift.color)
                                    box(badgeX, badgeTop, badgeW, badgeH, resolved.fill, badgeRadius)
                                    box(
                                        x = badgeX + 1.3f,
                                        y = badgeTop + 1.1f,
                                        w = stripeW,
                                        h = (badgeH - 2.2f).coerceAtLeast(3f),
                                        color = resolved.accentBar,
                                        radius = stripeW / 2f,
                                    )
                                    val textStart = badgeX + 1.3f + stripeW + 2.2f
                                    element.lines.forEachIndexed { lineIdx, label ->
                                        val baseline = cursor + lineIdx * lineStep + selectedFont * 0.86f
                                        fittedText(
                                            value = label,
                                            x = textStart,
                                            y = baseline,
                                            size = selectedFont,
                                            maxWidth = badgeTextAvail,
                                            color = resolved.ink,
                                            bold = true,
                                        )
                                    }
                                } else {
                                    box(badgeX, badgeTop, badgeW, badgeH, slateSoft, badgeRadius)
                                    element.lines.forEachIndexed { lineIdx, label ->
                                        val baseline = cursor + lineIdx * lineStep + selectedFont * 0.86f
                                        fittedText(label, badgeX + 3f, baseline, selectedFont, badgeW - 6f, ink, bold = true)
                                    }
                                }
                                cursor += lineCount * lineStep
                            }
                            is CalendarCellElement.OnCallPill -> {
                                val textBaseline = cursor + selectedFont * 0.86f
                                val repFont = (selectedFont - 0.6f).coerceAtLeast(5.4f)
                                val repW = minOf(cellW - 8f, measure("REP", repFont, bold = true) + 6f)
                                box(left + 4f, cursor - 0.1f, repW, selectedFont + 1.3f, warningFill, 2.4f)
                                fittedText("REP", left + 7f, textBaseline, repFont, repW - 4f, warningInk, bold = true)
                                cursor += lineStep
                            }
                            is CalendarCellElement.DetailLine -> {
                                val textBaseline = cursor + selectedFont * 0.86f
                                val (dColor, dBold) = detailTone(element.text, element.isHolidayDetail)
                                fittedText(element.text, left + 4.5f, textBaseline, selectedFont, cellW - 9f, dColor, dBold)
                                cursor += lineStep
                            }
                        }
                    }
                }
            }
        }
    }

}

internal enum class CustomPeriodLayout {
    SHORT_DETAILED,
    MEDIUM_TWO_COLUMN,
    EXTENDED_OVERVIEW,
}

internal fun resolveCustomPeriodLayout(dayCount: Int, monthCount: Int): CustomPeriodLayout = when {
    dayCount <= 14 -> CustomPeriodLayout.SHORT_DETAILED
    dayCount in 15..62 && monthCount <= 2 -> CustomPeriodLayout.MEDIUM_TWO_COLUMN
    else -> CustomPeriodLayout.EXTENDED_OVERVIEW
}

internal fun parsePdfTimeOrNull(raw: String?): LocalTime? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    return runCatching { LocalTime.parse(trimmed) }.getOrElse {
        val parts = trimmed.split(':')
        if (parts.size == 2 && parts[0].length == 1) {
            runCatching { LocalTime.parse("0${parts[0]}:${parts[1]}") }.getOrNull()
        } else null
    }
}

internal fun sortAssignedShiftsChronologically(
    shifts: List<AssignedShift>,
    defsById: Map<String, ShiftDefinition>,
): List<AssignedShift> = shifts.sortedWith(
    Comparator { a, b ->
        val defA = defsById[a.shiftDefId]
        val defB = defsById[b.shiftDefId]
        if (defA == null || defB == null) {
            return@Comparator when {
                defA == null && defB == null -> a.id.compareTo(b.id)
                defA == null -> 1
                else -> -1
            }
        }
        val allDayCmp = defA.isAllDay.compareTo(defB.isAllDay)
        if (allDayCmp != 0) return@Comparator allDayCmp
        if (!defA.isAllDay) {
            val startA = parsePdfTimeOrNull(a.customStartTime ?: defA.startTime)
            val startB = parsePdfTimeOrNull(b.customStartTime ?: defB.startTime)
            val startCmp = compareValuesBy(startA, startB) { it }
            if (startCmp != 0) return@Comparator startCmp
            val endA = parsePdfTimeOrNull(a.customEndTime ?: defA.endTime)
            val endB = parsePdfTimeOrNull(b.customEndTime ?: defB.endTime)
            val endCmp = compareValuesBy(endA, endB) { it }
            if (endCmp != 0) return@Comparator endCmp
        }
        val nameCmp = defA.name.compareTo(defB.name, ignoreCase = true)
        if (nameCmp != 0) nameCmp else a.id.compareTo(b.id)
    },
)

internal fun sortShiftRowsChronologically(rows: List<PdfShiftRow>): List<PdfShiftRow> = rows.sortedWith(
    Comparator { a, b ->
        val allDayCmp = a.isAllDay.compareTo(b.isAllDay)
        if (allDayCmp != 0) return@Comparator allDayCmp
        if (!a.isAllDay) {
            val startA = parsePdfTimeOrNull(a.startTime ?: a.time)
            val startB = parsePdfTimeOrNull(b.startTime ?: b.time)
            val startCmp = when {
                startA != null && startB != null -> startA.compareTo(startB)
                startA != null -> -1
                startB != null -> 1
                else -> 0
            }
            if (startCmp != 0) return@Comparator startCmp
            val endA = parsePdfTimeOrNull(a.endTime)
            val endB = parsePdfTimeOrNull(b.endTime)
            val endCmp = when {
                endA != null && endB != null -> endA.compareTo(endB)
                endA != null -> -1
                endB != null -> 1
                else -> 0
            }
            if (endCmp != 0) return@Comparator endCmp
        }
        val labelA = a.name.ifBlank { a.code }
        val labelB = b.name.ifBlank { b.code }
        labelA.compareTo(labelB, ignoreCase = true)
    },
)

internal fun isCompactMetaDetail(rawDetail: String): Boolean =
    rawDetail.startsWith("Ingresso effettivo:") ||
        rawDetail.startsWith("Stacco effettivo:") ||
        rawDetail.startsWith("Durata prevista:") ||
        rawDetail.startsWith("Pausa:") ||
        rawDetail.startsWith("Straordinario:") ||
        rawDetail.startsWith("Permesso:") ||
        rawDetail == "Trasferta"

private val timeRangeRegex = Regex("^(\\d{2}):(\\d{2}) - (\\d{2}):(\\d{2})$")
private val hoursMinutesRegex = Regex("(\\d+) h (\\d+) min")

private fun formatCompactDuration(hours: Int, mins: Int): String = when {
    hours > 0 && mins > 0 -> "${hours}h${mins.toString().padStart(2, '0')}"
    hours > 0 -> "${hours}h"
    else -> "${mins}m"
}

internal fun formatPdfDetail(detail: String, compact: Boolean): String {
    timeRangeRegex.matchEntire(detail)?.let { match ->
        return if (compact) {
            fun shortClock(h: String, m: String) = if (m == "00") h else "$h:$m"
            shortClock(match.groupValues[1], match.groupValues[2]) +
                "–" +
                shortClock(match.groupValues[3], match.groupValues[4])
        } else {
            "${match.groupValues[1]}:${match.groupValues[2]}–${match.groupValues[3]}:${match.groupValues[4]}"
        }
    }
    return when {
        detail == "Tutto il giorno" -> if (compact) "TG" else "Tutto il giorno"
        detail == "Reperibilità" -> "REP"
        detail == "Trasferta" -> if (compact) "TR" else "Trasferta"
        detail.startsWith("Ingresso effettivo:") -> {
            val time = detail.removePrefix("Ingresso effettivo:").trim()
            if (compact) "Att. $time" else "Attacco $time"
        }
        detail.startsWith("Stacco effettivo:") -> {
            val time = detail.removePrefix("Stacco effettivo:").trim()
            "Stacco $time"
        }
        detail.startsWith("Durata prevista:") -> {
            val hm = hoursMinutesRegex.find(detail)
            if (hm != null) {
                val h = hm.groupValues[1].toInt()
                val m = hm.groupValues[2].toInt()
                "Dur. ${formatCompactDuration(h, m)}"
            } else {
                detail.replace("Durata prevista:", "Dur.")
            }
        }
        detail.startsWith("Pausa:") -> {
            val rest = detail.removePrefix("Pausa:").trim()
            val match = Regex("^(\\d+) min(?: dalle (\\d{2}:\\d{2}))?$").matchEntire(rest)
            if (match != null) {
                val mins = match.groupValues[1]
                val at = match.groupValues[2]
                val prefix = if (compact) "P." else "Pausa"
                if (at.isNotEmpty()) "$prefix ${mins}m ($at)" else "$prefix ${mins}m"
            } else {
                detail
            }
        }
        detail.startsWith("Straordinario:") -> {
            val hm = hoursMinutesRegex.find(detail)
            if (hm != null) {
                "+${formatCompactDuration(hm.groupValues[1].toInt(), hm.groupValues[2].toInt())}"
            } else {
                detail.replace("Straordinario:", "+")
            }
        }
        detail.startsWith("Permesso:") -> {
            val hm = hoursMinutesRegex.find(detail)
            if (hm != null) {
                "−${formatCompactDuration(hm.groupValues[1].toInt(), hm.groupValues[2].toInt())}"
            } else {
                detail.replace("Permesso:", "Perm.")
            }
        }
        else -> detail
    }
}

internal fun preferredListColumns(dayCount: Int, monthCount: Int): List<Int> =
    if (dayCount >= 16 || monthCount >= 2) listOf(2, 3, 1) else listOf(1, 2, 3)

internal fun planListColumns(
    dates: List<LocalDate>,
    heights: List<Float>,
    columns: Int,
    available: Float,
): List<List<Int>>? {
    if (columns == 2 && dates.size == heights.size) {
        val months = dates.map(YearMonth::from).distinct()
        if (months.size == 2) {
            val left = dates.indices.filter { YearMonth.from(dates[it]) == months[0] }
            val right = dates.indices.filter { YearMonth.from(dates[it]) == months[1] }
            val leftH = left.sumOf { heights[it].toDouble() }.toFloat()
            val rightH = right.sumOf { heights[it].toDouble() }.toFloat()
            if (left.isNotEmpty() && right.isNotEmpty() && leftH <= available && rightH <= available) {
                return listOf(left, right)
            }
        }
    }
    return packPdfColumns(heights, columns, available)
}

internal sealed interface CalendarCellElement {
    data class ShiftBadge(val shift: PdfShiftRow, val lines: List<String>) : CalendarCellElement
    data object OnCallPill : CalendarCellElement
    data class DetailLine(val text: String, val isHolidayDetail: Boolean = false) : CalendarCellElement
}

internal fun buildCalendarCellElements(
    row: PdfDayRow,
    compact: Boolean,
    includeHeaderOnCall: Boolean,
    wrapBadgeTitle: (String) -> List<String>,
    wrapDetail: (String) -> List<String>,
): List<CalendarCellElement> = buildList {
    row.shifts.forEach { shift ->
        val rawTitle = if (compact || shift.name.isBlank()) shift.code else shift.name
        val badgeLines = if (compact || shift.name.isBlank()) {
            listOf(rawTitle.ifBlank { "-" })
        } else {
            wrapBadgeTitle(rawTitle).ifEmpty { listOf(rawTitle) }
        }
        add(CalendarCellElement.ShiftBadge(shift, badgeLines))
        shift.details.forEach { detail ->
            val formatted = formatPdfDetail(detail, compact = compact)
            wrapDetail(formatted).forEach { add(CalendarCellElement.DetailLine(it)) }
        }
    }
    val hasLegacyRep = row.details.any { it == "Reperibilità" || it == "REP" }
    if ((row.onCall || hasLegacyRep) && !includeHeaderOnCall) {
        add(CalendarCellElement.OnCallPill)
    }
    row.details.filterNot { it == "Reperibilità" || it == "REP" }.forEach { detail ->
        val formatted = formatPdfDetail(detail, compact = compact)
        wrapDetail(formatted).forEach { add(CalendarCellElement.DetailLine(it, isHolidayDetail = row.holiday)) }
    }
}

internal enum class OverviewRowLayout {
    SINGLE_MONTH_COMPACT,
    MULTI_MONTH_COMPACT,
    ALIGNED_MATRIX,
}

internal data class OverviewSlot(
    val rowIndex: Int,
    val date: LocalDate,
)

internal data class OverviewColumnPlan(
    val month: YearMonth,
    val slots: List<OverviewSlot>,
    val rowHeights: List<Float>,
    val rowTops: List<Float>,
)

private val monthShortFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)
private val monthShortYearFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ITALIAN)
private val monthFullYearFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)

internal fun formatItalianMonthHeader(month: YearMonth, formatter: DateTimeFormatter): String =
    month.atDay(1).format(formatter).replaceFirstChar { it.uppercase() }

internal fun overviewPaperSizes(monthCount: Int): List<Pair<Int, Int>> = when {
    monthCount <= 1 -> listOf(595 to 842, 842 to 1191)
    monthCount <= 3 -> listOf(595 to 842, 842 to 595, 1191 to 842)
    monthCount <= 6 -> listOf(842 to 595, 1191 to 842, 1684 to 1191)
    else -> listOf(1191 to 842, 1684 to 1191, 2384 to 1684)
}

internal fun overviewFontCandidates(monthCount: Int): List<Float> = when {
    monthCount <= 2 -> listOf(10f, 9.5f, 9f, 8.5f, 8f, 7.5f, 7f, 6.5f)
    monthCount <= 5 -> listOf(9f, 8.5f, 8f, 7.5f, 7f, 6.5f)
    else -> listOf(8f, 7.5f, 7f, 6.5f)
}

internal fun resolveOverviewRowLayout(
    mode: PdfExportMode,
    monthCount: Int,
    dayCount: Int,
): OverviewRowLayout = when {
    mode == PdfExportMode.PERSONALIZZATO && monthCount == 1 -> OverviewRowLayout.SINGLE_MONTH_COMPACT
    mode == PdfExportMode.PERSONALIZZATO && monthCount >= 2 && dayCount <= 31 -> OverviewRowLayout.MULTI_MONTH_COMPACT
    else -> OverviewRowLayout.ALIGNED_MATRIX
}

internal fun overviewColumnHeaderText(
    month: YearMonth,
    mode: PdfExportMode,
    monthCount: Int,
    fitsFullMonthYear: Boolean,
): String = when {
    mode == PdfExportMode.ANNO -> formatItalianMonthHeader(month, monthShortFormat)
    monthCount <= 2 && fitsFullMonthYear -> formatItalianMonthHeader(month, monthFullYearFormat)
    else -> formatItalianMonthHeader(month, monthShortYearFormat)
}

internal fun capOverviewRowHeight(minHeight: Float, extraPerRow: Float): Float {
    val maxRowHeight = maxOf(minHeight, minOf(minHeight * 2.2f, 38f))
    return minOf(minHeight + extraPerRow, maxRowHeight)
}

internal fun planOverviewColumns(
    months: List<YearMonth>,
    dates: List<LocalDate>,
    mode: PdfExportMode,
    gridTop: Float,
    availableHeight: Float,
    baseRowHeight: Float,
    leading: Float,
    lineCounts: Map<LocalDate, Int>,
): List<OverviewColumnPlan>? {
    if (months.isEmpty()) return emptyList()
    val layout = resolveOverviewRowLayout(mode, months.size, dates.size)
    fun minHeightFor(date: LocalDate): Float {
        val count = lineCounts[date] ?: 0
        return maxOf(baseRowHeight, count * leading + 3.5f)
    }

    return when (layout) {
        OverviewRowLayout.SINGLE_MONTH_COMPACT,
        OverviewRowLayout.MULTI_MONTH_COMPACT -> {
            val colDatesList = months.map { month ->
                if (layout == OverviewRowLayout.SINGLE_MONTH_COMPACT) {
                    dates.sorted()
                } else {
                    dates.filter { YearMonth.from(it) == month }.sorted()
                }
            }
            val colMinHeights = colDatesList.map { colDates ->
                colDates.map { d -> minHeightFor(d) }
            }
            if (colMinHeights.any { it.sum() > availableHeight }) return null
            val maxRows = colMinHeights.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
            val minSpare = colMinHeights.minOfOrNull { availableHeight - it.sum() } ?: availableHeight
            val extraPerRow = (minSpare / maxRows).coerceAtLeast(0f)
            months.mapIndexed { index, month ->
                val colDates = colDatesList[index]
                val mins = colMinHeights[index]
                val heights = mins.map { capOverviewRowHeight(it, extraPerRow) }
                val tops = heights.runningFold(gridTop, Float::plus)
                val slots = colDates.mapIndexed { rowIndex, date -> OverviewSlot(rowIndex, date) }
                OverviewColumnPlan(month, slots, heights, tops)
            }
        }
        OverviewRowLayout.ALIGNED_MATRIX -> {
            val maxDay = if (mode == PdfExportMode.ANNO) 31 else months.maxOf { it.lengthOfMonth() }
            val minimumHeights = FloatArray(maxDay) { baseRowHeight }
            dates.forEach { date ->
                val index = (date.dayOfMonth - 1).coerceIn(0, maxDay - 1)
                minimumHeights[index] = maxOf(minimumHeights[index], minHeightFor(date))
            }
            val spare = availableHeight - minimumHeights.sum()
            if (spare < 0f) return null
            val extraPerRow = spare / minimumHeights.size
            val rowHeights = if (mode == PdfExportMode.ANNO) {
                minimumHeights.map { it + extraPerRow }
            } else {
                minimumHeights.map { capOverviewRowHeight(it, extraPerRow) }
            }
            val rowTops = rowHeights.runningFold(gridTop, Float::plus)
            val dateSet = dates.toSet()
            months.map { month ->
                val slots = (1..month.lengthOfMonth()).mapNotNull { dayNumber ->
                    val date = month.atDay(dayNumber)
                    if (date in dateSet) OverviewSlot(dayNumber - 1, date) else null
                }
                OverviewColumnPlan(month, slots, rowHeights, rowTops)
            }
        }
    }
}
