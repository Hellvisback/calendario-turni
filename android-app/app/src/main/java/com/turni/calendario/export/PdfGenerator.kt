package com.turni.calendario.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.repository.ShiftRepository
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object PdfGenerator {

    /**
     * Genera il file PDF del prospetto turni e apre il chooser nativo di sistema per condividerlo o stamparlo.
     */
    fun generateAndSharePdf(context: Context, options: PdfExportOptions): File? {
        val pdfDocument = PdfDocument()

        when (options.mode) {
            PdfExportMode.ELENCO -> renderElencoPdf(pdfDocument, options)
            PdfExportMode.MESE -> renderMesePdf(pdfDocument, options)
            PdfExportMode.ANNO -> renderAnnoPdf(pdfDocument, options)
            PdfExportMode.PERSONALIZZATO -> renderPersonalizzatoPdf(pdfDocument, options)
        }

        val outputFile = File(context.cacheDir, "prospetto_turni.pdf")
        try {
            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            // Condivisione con FileProvider sicuro
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Prospetto Turni - Calendario Turni")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Condividi o Stampa PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            return outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
            return null
        }
    }

    /**
     * Calcola il colore ottimale del testo (bianco o scuro) per garantire il massimo contrasto WCAG
     * su qualsiasi colore di sfondo del turno.
     */
    private fun getContrastingTextColor(colorInt: Int): Int {
        val r = (colorInt shr 16) and 0xFF
        val g = (colorInt shr 8) and 0xFF
        val b = colorInt and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.62) Color.rgb(15, 23, 42) else Color.WHITE
    }

    /**
     * Disegna il prospetto in formato Elenco (A4 verticale: 595 x 842 pt).
     * Layout professionale: Fascia superiore navy, card statistiche mensili, tabella con zebra striping e pillole colorate.
     */
    private fun renderElencoPdf(doc: PdfDocument, options: PdfExportOptions) {
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val leftMargin = 32f
        val rightMargin = 563f
        val contentWidth = rightMargin - leftMargin

        val bgPaint = Paint().apply { style = Paint.Style.FILL }
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // 1. Header Superiore Elegante (Fascia Navy Scuro Slate 900 con rifinitura ciano)
        val headerRect = RectF(leftMargin, 20f, rightMargin, 74f)
        bgPaint.color = Color.rgb(15, 23, 42) // Slate 900 profondo
        canvas.drawRoundRect(headerRect, 9f, 9f, bgPaint)

        // Bordo superiore azzurro cielo
        strokePaint.color = Color.rgb(2, 132, 199) // Sky 600
        strokePaint.strokeWidth = 1.2f
        canvas.drawRoundRect(headerRect, 9f, 9f, strokePaint)

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            isFakeBoldText = true
            color = Color.WHITE
        }
        canvas.drawText("PROSPETTO TURNI DI SERVIZIO", leftMargin + 14f, 42f, headerTitlePaint)

        val monthName = options.startDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).uppercase(Locale.ITALIAN)
        val periodSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            isFakeBoldText = true
            color = Color.rgb(56, 189, 248) // Sky 400
        }
        val workProfile = ShiftRepository.activeWorkProfile
        val profileLabel = if (workProfile.companyOrDepartment.isNotBlank()) {
            "${workProfile.name} • ${workProfile.companyOrDepartment}"
        } else {
            workProfile.name
        }
        val periodText = if (options.startDate.month == options.endDate.month && options.startDate.year == options.endDate.year) {
            "$profileLabel • $monthName ${options.startDate.year}"
        } else {
            "$profileLabel • ${options.startDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"))} - ${options.endDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"))}"
        }
        canvas.drawText(periodText, leftMargin + 14f, 60f, periodSubtitlePaint)

        // Sezione note o diciture utente nell'header a destra
        val headerNotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            color = Color.rgb(203, 213, 225) // Slate 300
            textAlign = Paint.Align.RIGHT
        }
        if (options.headerNotes.isNotBlank()) {
            canvas.drawText(options.headerNotes.take(45), rightMargin - 14f, 42f, headerNotePaint)
        }
        val genDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(148, 163, 184) // Slate 400
            textAlign = Paint.Align.RIGHT
        }
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        canvas.drawText("Documento emesso: $todayStr", rightMargin - 14f, 60f, genDatePaint)

        // 2. Card Statistiche Mensili Certificate con Accent Bar Laterale
        val stats = ShiftRepository.getComprehensiveReportStats(options.startDate, options.endDate)
        val statsTop = 82f
        val statsBottom = 118f
        val cardGap = 6f
        val cardWidth = (contentWidth - cardGap * 3f) / 4f

        val statCardBgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val statCardBorderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240) // Slate 200
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f
            isFakeBoldText = true
            color = Color.rgb(15, 23, 42) // Slate 900
            textAlign = Paint.Align.CENTER
        }
        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.8f
            color = Color.rgb(100, 116, 139) // Slate 500
            textAlign = Paint.Align.CENTER
        }

        val cardsData = listOf(
            Triple(String.format(Locale.US, "%.1fh", stats.totalHours), "Ore Lavorate", Color.rgb(2, 132, 199)),      // Ciano
            Triple("${stats.workedDays} turni", "Presenze di Turno", Color.rgb(16, 185, 129)),                         // Smeraldo
            Triple("${stats.countRiposo} RIP • ${stats.countSmonto} SMO", "Riposi / Smonti", Color.rgb(99, 102, 241)),  // Indaco
            Triple("+${String.format(Locale.US, "%.1fh", stats.totalOvertimeHours)} • ${stats.countTrasferte} T", "Straord. / Trasferte", Color.rgb(245, 158, 11)) // Ambra
        )

        val accentBarPaint = Paint().apply { style = Paint.Style.FILL }

        for (i in 0 until 4) {
            val cLeft = leftMargin + i * (cardWidth + cardGap)
            val cRight = cLeft + cardWidth
            val cRect = RectF(cLeft, statsTop, cRight, statsBottom)
            canvas.drawRoundRect(cRect, 7f, 7f, statCardBgPaint)
            canvas.drawRoundRect(cRect, 7f, 7f, statCardBorderPaint)

            // Accent bar laterale a sinistra di 3.5dp
            accentBarPaint.color = cardsData[i].third
            val accentRect = RectF(cLeft, statsTop + 3f, cLeft + 3.5f, statsBottom - 3f)
            canvas.drawRoundRect(accentRect, 2f, 2f, accentBarPaint)

            val centerX = cLeft + (cardWidth + 3.5f) / 2f
            canvas.drawText(cardsData[i].first, centerX, statsTop + 17f, statValuePaint)
            canvas.drawText(cardsData[i].second, centerX, statsTop + 29f, statLabelPaint)
        }

        // 3. Intestazione Tabella Turni
        var currentY = 126f
        val tableHeaderHeight = 19f
        val tableHeaderRect = RectF(leftMargin, currentY, rightMargin, currentY + tableHeaderHeight)
        bgPaint.color = Color.rgb(30, 41, 59) // Slate 800
        canvas.drawRoundRect(tableHeaderRect, 5f, 5f, bgPaint)

        val colHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            isFakeBoldText = true
            color = Color.WHITE
        }
        canvas.drawText("GIORNO", leftMargin + 10f, currentY + 12.5f, colHeaderPaint)
        if (options.includeTime) {
            canvas.drawText("ORARIO", leftMargin + 85f, currentY + 12.5f, colHeaderPaint)
        }
        canvas.drawText("TURNO DI SERVIZIO", leftMargin + 185f, currentY + 12.5f, colHeaderPaint)
        if (options.includeDuration) {
            canvas.drawText("DURATA", leftMargin + 315f, currentY + 12.5f, colHeaderPaint)
        }
        canvas.drawText("STATO & NOTE", leftMargin + 375f, currentY + 12.5f, colHeaderPaint)

        currentY += tableHeaderHeight + 2f

        // 4. Righe della Tabella con Zebra Striping e Pillole Colorate
        val rowHeight = 18.8f
        var currDate = options.startDate
        var dayIndex = 0

        val rowWhiteBg = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val rowZebraBg = Paint().apply { color = Color.rgb(248, 250, 252); style = Paint.Style.FILL } // Slate 50
        val rowHolidayBg = Paint().apply { color = Color.rgb(254, 242, 242); style = Paint.Style.FILL } // Rose 50
        val rowDividerPaint = Paint().apply {
            color = Color.rgb(226, 232, 240) // Slate 200
            strokeWidth = 0.5f
        }

        val dateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.2f
            isFakeBoldText = true
            color = Color.rgb(15, 23, 42)
        }
        val holidayDateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.2f
            isFakeBoldText = true
            color = Color.rgb(220, 38, 38) // Red 600
        }
        val timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.8f
            color = Color.rgb(51, 65, 85) // Slate 700
        }
        val durationTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.8f
            color = Color.rgb(71, 85, 105) // Slate 600
        }
        val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(71, 85, 105) // Slate 600
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            isFakeBoldText = true
        }

        // Chip Paint per Straordinario e Trasferta
        val chipBgPaint = Paint().apply { style = Paint.Style.FILL }
        val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.8f
            isFakeBoldText = true
        }

        val maxTableY = 765f
        while (!currDate.isAfter(options.endDate) && currentY + rowHeight <= maxTableY) {
            val isSunday = currDate.dayOfWeek == java.time.DayOfWeek.SUNDAY
            val holiday = ShiftRepository.isHoliday(currDate)
            val isHoliday = holiday != null || isSunday

            // Background riga
            val rowRect = RectF(leftMargin, currentY, rightMargin, currentY + rowHeight)
            val rowBg = when {
                isHoliday -> rowHolidayBg
                dayIndex % 2 == 1 -> rowZebraBg
                else -> rowWhiteBg
            }
            canvas.drawRect(rowRect, rowBg)
            canvas.drawLine(leftMargin, currentY + rowHeight, rightMargin, currentY + rowHeight, rowDividerPaint)

            // Colonna Giorno: "01 GIO"
            val dayNumStr = String.format(Locale.ITALIAN, "%02d", currDate.dayOfMonth)
            val dayNameStr = currDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase(Locale.ITALIAN)
            val dayFullLabel = "$dayNumStr $dayNameStr"
            canvas.drawText(dayFullLabel, leftMargin + 8f, currentY + 12.5f, if (isHoliday) holidayDateTextPaint else dateTextPaint)

            val shifts = ShiftRepository.getShiftsForDate(currDate)
            val activeShift = shifts.firstOrNull()
            val def = activeShift?.let { ShiftRepository.getShiftDefinition(it.shiftDefId) }
            val extraInfo = ShiftRepository.dayExtraInfo[currDate]
            val hasTrasferta = ShiftRepository.hasTrasfertaForDate(currDate)
            val overtime = extraInfo?.overtimeHours ?: (activeShift?.overtimeHours ?: 0f)

            if (def != null && (options.filterShiftId == null || def.id == options.filterShiftId)) {
                // Orario
                if (options.includeTime) {
                    val timeStr = if (def.isAllDay) "Tutto il giorno" else "${def.startTime} - ${def.endTime}"
                    canvas.drawText(timeStr, leftMargin + 85f, currentY + 12.5f, timeTextPaint)
                }

                // Pillola Badge Turno elegante
                val badgeColor = (def.colorHex or 0xFF000000).toInt()
                bgPaint.color = badgeColor
                val badgeLeft = leftMargin + 185f
                val badgeWidth = 112f
                val badgeRect = RectF(badgeLeft, currentY + 2f, badgeLeft + badgeWidth, currentY + rowHeight - 2f)
                canvas.drawRoundRect(badgeRect, 5f, 5f, bgPaint)

                badgeTextPaint.color = getContrastingTextColor(badgeColor)
                val badgeTitle = "${def.shortCode} • ${def.name.take(12)}"
                canvas.drawText(badgeTitle, badgeLeft + 6f, currentY + 12f, badgeTextPaint)

                // Durata
                if (options.includeDuration) {
                    val durStr = if (def.isAllDay) "—" else def.durationFormatted
                    canvas.drawText(durStr, leftMargin + 315f, currentY + 12.5f, durationTextPaint)
                }

                // Note & Chip di Stato
                var chipX = leftMargin + 375f

                // Chip Straordinario (ambra caldo)
                if (overtime > 0f) {
                    val chipStr = "+${String.format(Locale.US, "%.1fh", overtime)}"
                    val chipW = chipTextPaint.measureText(chipStr) + 8f
                    val chipRect = RectF(chipX, currentY + 3f, chipX + chipW, currentY + rowHeight - 3f)
                    chipBgPaint.color = Color.rgb(254, 243, 199) // Amber 100
                    canvas.drawRoundRect(chipRect, 4f, 4f, chipBgPaint)
                    chipTextPaint.color = Color.rgb(180, 83, 9) // Amber 700
                    canvas.drawText(chipStr, chipX + 4f, currentY + 12f, chipTextPaint)
                    chipX += chipW + 4f
                }

                // Chip Trasferta (smeraldo fresco)
                if (hasTrasferta) {
                    val chipStr = "TRASF"
                    val chipW = chipTextPaint.measureText(chipStr) + 8f
                    val chipRect = RectF(chipX, currentY + 3f, chipX + chipW, currentY + rowHeight - 3f)
                    chipBgPaint.color = Color.rgb(209, 250, 229) // Emerald 100
                    canvas.drawRoundRect(chipRect, 4f, 4f, chipBgPaint)
                    chipTextPaint.color = Color.rgb(4, 120, 87) // Emerald 700
                    canvas.drawText(chipStr, chipX + 4f, currentY + 12f, chipTextPaint)
                    chipX += chipW + 4f
                }

                // Note testuali o festività
                val noteStr = if (activeShift.notes.isNotBlank()) {
                    activeShift.notes.take(24)
                } else if (holiday != null && options.includeEvents) {
                    holiday.name.take(24)
                } else ""

                if (noteStr.isNotBlank()) {
                    canvas.drawText(noteStr, chipX, currentY + 12.5f, noteTextPaint)
                }
            } else if (holiday != null && options.includeEvents) {
                // Riga festività senza turno
                if (options.includeTime) {
                    canvas.drawText("Festivo", leftMargin + 85f, currentY + 12.5f, holidayDateTextPaint)
                }
                canvas.drawText("★ ${holiday.name}", leftMargin + 185f, currentY + 12.5f, holidayDateTextPaint)
            } else {
                if (options.includeEmptyRows) {
                    canvas.drawText("—", leftMargin + 185f, currentY + 12.5f, noteTextPaint)
                }
            }

            currentY += rowHeight
            currDate = currDate.plusDays(1)
            dayIndex++
        }

        // 5. Striscia Legenda Turni (chiara, compatta e professionale)
        val legendY = 780f
        val legendHeight = 22f
        val legendRect = RectF(leftMargin, legendY, rightMargin, legendY + legendHeight)
        bgPaint.color = Color.rgb(248, 250, 252) // Slate 50
        canvas.drawRoundRect(legendRect, 5f, 5f, bgPaint)
        strokePaint.color = Color.rgb(226, 232, 240) // Slate 200
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(legendRect, 5f, 5f, strokePaint)

        val legendTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7f
            isFakeBoldText = true
            color = Color.rgb(71, 85, 105) // Slate 600
        }
        canvas.drawText("LEGENDA:", leftMargin + 8f, legendY + 14f, legendTitlePaint)

        var legendX = leftMargin + 50f
        val legendDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.8f
            color = Color.rgb(30, 41, 59)
        }

        val allDefs = ShiftRepository.shiftDefinitions.toList()
        for (def in allDefs.take(7)) {
            val dotColor = (def.colorHex or 0xFF000000).toInt()
            legendDotPaint.color = dotColor
            canvas.drawCircle(legendX + 4f, legendY + 11.5f, 3.2f, legendDotPaint)

            val itemLabel = "${def.shortCode} ${def.name.take(9)}"
            canvas.drawText(itemLabel, legendX + 10f, legendY + 14f, legendTextPaint)
            legendX += legendTextPaint.measureText(itemLabel) + 16f
            if (legendX > rightMargin - 40f) break
        }

        // 6. Footer Professionale
        val footerLinePaint = Paint().apply {
            color = Color.rgb(226, 232, 240) // Slate 200
            strokeWidth = 0.8f
        }
        canvas.drawLine(leftMargin, 810f, rightMargin, 810f, footerLinePaint)

        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(100, 116, 139) // Slate 500
        }
        val appTag = "Calendario Turni Pro • Documento aziendale e personale di servizio"
        canvas.drawText(appTag, leftMargin, 824f, footerTextPaint)

        if (options.includeLogo) {
            val pageInfoText = "Generato con Calendario Turni per Android • Pagina 1 di 1"
            val textWidth = footerTextPaint.measureText(pageInfoText)
            canvas.drawText(pageInfoText, rightMargin - textWidth, 824f, footerTextPaint)
        }

        doc.finishPage(page)
    }

    /**
     * Disegna il prospetto in formato Mese (A4 orizzontale landscape: 842 x 595 pt).
     * Griglia calendario ad alta leggibilità con header cromatico, pillole turno e riepilogo statistico.
     */
    private fun renderMesePdf(doc: PdfDocument, options: PdfExportOptions) {
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val leftMargin = 32f
        val rightMargin = 810f
        val contentWidth = rightMargin - leftMargin

        val bgPaint = Paint().apply { style = Paint.Style.FILL }
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // 1. Header Superiore Elegante Landscape
        val headerRect = RectF(leftMargin, 16f, rightMargin, 58f)
        bgPaint.color = Color.rgb(15, 23, 42) // Slate 900
        canvas.drawRoundRect(headerRect, 8f, 8f, bgPaint)

        strokePaint.color = Color.rgb(2, 132, 199) // Sky 600
        strokePaint.strokeWidth = 1.2f
        canvas.drawRoundRect(headerRect, 8f, 8f, strokePaint)

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f
            isFakeBoldText = true
            color = Color.WHITE
        }
        val monthName = options.startDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).uppercase(Locale.ITALIAN)
        canvas.drawText("CALENDARIO TURNI • PROSPETTO MENSILE", leftMargin + 16f, 35f, headerTitlePaint)

        val periodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f
            isFakeBoldText = true
            color = Color.rgb(56, 189, 248) // Sky 400
        }
        val periodText = "$monthName ${options.startDate.year}"
        canvas.drawText(periodText, leftMargin + 16f, 50f, periodPaint)

        // Pillola Profilo Lavoro Attivo
        val activeProfile = ShiftRepository.activeWorkProfile
        val profLabel = "PROFILO: ${activeProfile.name.uppercase(Locale.ITALIAN)}"
        val profPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7f
            isFakeBoldText = true
            color = Color.rgb(186, 230, 253) // Sky 200
        }
        val profLeft = leftMargin + 16f + periodPaint.measureText(periodText) + 12f
        val profW = profPaint.measureText(profLabel) + 10f
        val profRect = RectF(profLeft, 39f, profLeft + profW, 51.5f)
        val profBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(30, 58, 138) // Blue 900
        }
        canvas.drawRoundRect(profRect, 3.5f, 3.5f, profBgPaint)
        canvas.drawText(profLabel, profLeft + 5f, 48f, profPaint)

        // Micro-statistiche nell'header a destra
        val stats = ShiftRepository.getComprehensiveReportStats(options.startDate, options.endDate)
        val headerStatsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            color = Color.rgb(226, 232, 240)
            textAlign = Paint.Align.RIGHT
        }
        val statsSummary = "Totale: ${String.format(Locale.US, "%.1fh", stats.totalHours)} | ${stats.workedDays} turni | ${stats.countRiposo} riposi | +${String.format(Locale.US, "%.1fh", stats.totalOvertimeHours)} extra"
        canvas.drawText(statsSummary, rightMargin - 16f, 35f, headerStatsPaint)

        val noteOrDate = if (options.headerNotes.isNotBlank()) options.headerNotes.take(50) else "Data: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
        val subStatsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(148, 163, 184)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(noteOrDate, rightMargin - 16f, 50f, subStatsPaint)

        // 2. Intestazioni Giorni della Settimana (Lunedì - Domenica)
        val daysOfWeek = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
        val gridTop = 64f
        val colWidth = contentWidth / 7f
        val headerRowHeight = 16f

        val dayHeaderRect = RectF(leftMargin, gridTop, rightMargin, gridTop + headerRowHeight)
        bgPaint.color = Color.rgb(30, 41, 59)
        canvas.drawRoundRect(dayHeaderRect, 4f, 4f, bgPaint)

        val dayHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            isFakeBoldText = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        for (i in 0 until 7) {
            val colCenterX = leftMargin + i * colWidth + colWidth / 2f
            canvas.drawText(daysOfWeek[i], colCenterX, gridTop + 11.5f, dayHeaderPaint)
        }

        // 3. Griglia del Mese
        val yearMonth = YearMonth.from(options.startDate)
        val firstDayOfMonth = yearMonth.atDay(1)
        val firstDayOfWeekIndex = firstDayOfMonth.dayOfWeek.value - 1 // 0 = Lunedì
        val daysInMonth = yearMonth.lengthOfMonth()

        val totalCells = firstDayOfWeekIndex + daysInMonth
        val numRows = if (totalCells > 35) 6 else 5

        val legendY = 544f
        val gridBottom = legendY - 6f
        val gridTopAfterHeader = gridTop + headerRowHeight + 2f
        val gridAvailableHeight = gridBottom - gridTopAfterHeader
        val rowHeight = gridAvailableHeight / numRows.toFloat()

        val cellBorderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val cellWhiteBg = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val cellHolidayBg = Paint().apply { color = Color.rgb(254, 242, 242); style = Paint.Style.FILL }
        val cellInactiveBg = Paint().apply { color = Color.rgb(248, 250, 252); style = Paint.Style.FILL }

        val dateNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            isFakeBoldText = true
            color = Color.rgb(15, 23, 42)
            textAlign = Paint.Align.RIGHT
        }
        val holidayDateNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            isFakeBoldText = true
            color = Color.rgb(220, 38, 38)
            textAlign = Paint.Align.RIGHT
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (numRows == 5) 7.5f else 6.8f
            isFakeBoldText = true
        }
        val shiftTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (numRows == 5) 6.8f else 6.0f
            color = Color.rgb(71, 85, 105)
        }

        var currentDay = 1
        var row = 0

        while (currentDay <= daysInMonth && row < numRows) {
            val rowTop = gridTopAfterHeader + row * rowHeight

            for (col in 0 until 7) {
                val cellLeft = leftMargin + col * colWidth
                val cellRight = cellLeft + colWidth
                val cellBottom = rowTop + rowHeight
                val rect = RectF(cellLeft, rowTop, cellRight, cellBottom)

                val dayIndex = row * 7 + col
                if (dayIndex >= firstDayOfWeekIndex && currentDay <= daysInMonth) {
                    val date = yearMonth.atDay(currentDay)
                    val isSunday = col == 6
                    val holiday = ShiftRepository.isHoliday(date)
                    val isHoliday = holiday != null || isSunday

                    canvas.drawRect(rect, if (isHoliday) cellHolidayBg else cellWhiteBg)
                    canvas.drawRect(rect, cellBorderPaint)

                    // Numero Giorno
                    val numPaint = if (isHoliday) holidayDateNumPaint else dateNumPaint
                    canvas.drawText(currentDay.toString(), cellRight - 6f, rowTop + 13f, numPaint)

                    // Eventuale festività indicata in mini testo
                    if (holiday != null && options.includeEvents) {
                        val holNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = 5.8f
                            color = Color.rgb(220, 38, 38)
                            isFakeBoldText = true
                        }
                        canvas.drawText(holiday.name.take(15), cellLeft + 5f, rowTop + 12f, holNamePaint)
                    }

                    // Turni del giorno (Pillole Cromatiche ad altezza dinamica)
                    val shifts = ShiftRepository.getShiftsForDate(date)
                    var shiftY = rowTop + 17f
                    val pillH = if (numRows == 5) 16f else 13.5f

                    shifts.take(2).forEach { s ->
                        val def = ShiftRepository.getShiftDefinition(s.shiftDefId)
                        if (def != null && (options.filterShiftId == null || def.id == options.filterShiftId)) {
                            val badgeColor = (def.colorHex or 0xFF000000).toInt()
                            bgPaint.color = badgeColor

                            val pillRect = RectF(cellLeft + 4f, shiftY, cellRight - 4f, shiftY + pillH)
                            canvas.drawRoundRect(pillRect, 3.5f, 3.5f, bgPaint)

                            badgeTextPaint.color = getContrastingTextColor(badgeColor)
                            val pillText = if (options.includeTitle) "${def.shortCode} • ${def.name.take(9)}" else def.shortCode
                            canvas.drawText(pillText, cellLeft + 7f, shiftY + pillH - 4.5f, badgeTextPaint)

                            if (options.includeTime && !def.isAllDay) {
                                canvas.drawText("${def.startTime}-${def.endTime}", cellLeft + 6f, shiftY + pillH + 9f, shiftTimePaint)
                                shiftY += pillH + 11.5f
                            } else {
                                shiftY += pillH + 3.5f
                            }
                        }
                    }

                    // Chip Straordinario o Trasferta in fondo alla cella
                    val extraInfo = ShiftRepository.dayExtraInfo[date]
                    val hasTrasferta = ShiftRepository.hasTrasfertaForDate(date)
                    val overtime = extraInfo?.overtimeHours ?: (shifts.firstOrNull()?.overtimeHours ?: 0f)

                    if (overtime > 0f || hasTrasferta) {
                        var chipX = cellLeft + 4f
                        val chipY = cellBottom - (if (numRows == 5) 12.5f else 11f)
                        val chipH = if (numRows == 5) 9.5f else 8f
                        if (overtime > 0f) {
                            val otStr = "+${String.format(Locale.US, "%.1fh", overtime)}"
                            val otPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = 5.8f
                                isFakeBoldText = true
                                color = Color.rgb(180, 83, 9)
                            }
                            val otBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.rgb(254, 243, 199)
                                style = Paint.Style.FILL
                            }
                            val w = otPaint.measureText(otStr) + 6f
                            canvas.drawRoundRect(RectF(chipX, chipY, chipX + w, chipY + chipH), 2f, 2f, otBg)
                            canvas.drawText(otStr, chipX + 3f, chipY + chipH - 2f, otPaint)
                            chipX += w + 3f
                        }
                        if (hasTrasferta) {
                            val trPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                textSize = 5.8f
                                isFakeBoldText = true
                                color = Color.rgb(4, 120, 87)
                            }
                            val trBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.rgb(209, 250, 229)
                                style = Paint.Style.FILL
                            }
                            val w = trPaint.measureText("TRASF") + 6f
                            canvas.drawRoundRect(RectF(chipX, chipY, chipX + w, chipY + chipH), 2f, 2f, trBg)
                            canvas.drawText("TRASF", chipX + 3f, chipY + chipH - 2f, trPaint)
                        }
                    }

                    currentDay++
                } else {
                    // Cella inattiva (mese precedente o successivo)
                    canvas.drawRect(rect, cellInactiveBg)
                    canvas.drawRect(rect, cellBorderPaint)
                }
            }
            row++
        }

        // 4. Striscia Legenda Turni Orizzontale
        val legendHeight = 18f
        val legendRect = RectF(leftMargin, legendY, rightMargin, legendY + legendHeight)
        bgPaint.color = Color.rgb(248, 250, 252) // Slate 50
        canvas.drawRoundRect(legendRect, 4f, 4f, bgPaint)
        strokePaint.color = Color.rgb(226, 232, 240) // Slate 200
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(legendRect, 4f, 4f, strokePaint)

        val legendTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.8f
            isFakeBoldText = true
            color = Color.rgb(71, 85, 105) // Slate 600
        }
        canvas.drawText("LEGENDA TURNI:", leftMargin + 8f, legendY + 11.5f, legendTitlePaint)

        var legendX = leftMargin + 76f
        val legendDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.5f
            color = Color.rgb(30, 41, 59)
        }

        val allDefs = ShiftRepository.shiftDefinitions.toList()
        for (def in allDefs.take(9)) {
            val dotColor = (def.colorHex or 0xFF000000).toInt()
            legendDotPaint.color = dotColor
            canvas.drawCircle(legendX + 3.5f, legendY + 9.5f, 3f, legendDotPaint)

            val itemLabel = "${def.shortCode} ${def.name.take(10)}"
            canvas.drawText(itemLabel, legendX + 9f, legendY + 11.5f, legendTextPaint)
            legendX += legendTextPaint.measureText(itemLabel) + 14f
            if (legendX > rightMargin - 40f) break
        }

        // 5. Footer Landscape
        val footerLinePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
        }
        canvas.drawLine(leftMargin, 570f, rightMargin, 570f, footerLinePaint)

        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(100, 116, 139)
        }
        canvas.drawText("Calendario Turni Pro • Prospetto Mensile Ufficiale", leftMargin, 582f, footerTextPaint)

        if (options.includeLogo) {
            val logoText = "Generato con Calendario Turni per Android"
            canvas.drawText(logoText, rightMargin - footerTextPaint.measureText(logoText), 582f, footerTextPaint)
        }

        doc.finishPage(page)
    }

    /**
     * Disegna il prospetto in formato Anno (A4 orizzontale landscape: 842 x 595 pt).
     * Tabellone 12 colonne con le righe 1..31, pillole turno contrastate, statistiche e legenda.
     */
    private fun renderAnnoPdf(doc: PdfDocument, options: PdfExportOptions) {
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val leftMargin = 28f
        val rightMargin = 814f
        val contentWidth = rightMargin - leftMargin

        val bgPaint = Paint().apply { style = Paint.Style.FILL }
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // 1. Header Superiore Annuale Slate 900
        val headerRect = RectF(leftMargin, 16f, rightMargin, 54f)
        bgPaint.color = Color.rgb(15, 23, 42)
        canvas.drawRoundRect(headerRect, 8f, 8f, bgPaint)

        strokePaint.color = Color.rgb(2, 132, 199)
        strokePaint.strokeWidth = 1.2f
        canvas.drawRoundRect(headerRect, 8f, 8f, strokePaint)

        val year = options.selectedYear
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f
            isFakeBoldText = true
            color = Color.WHITE
        }
        canvas.drawText("CALENDARIO TURNI • PIANO ANNUALE $year", leftMargin + 14f, 34f, titlePaint)

        // Pillola Profilo Lavoro Attivo
        val activeProfile = ShiftRepository.activeWorkProfile
        val profLabel = "PROFILO: ${activeProfile.name.uppercase(Locale.ITALIAN)}"
        val profPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7f
            isFakeBoldText = true
            color = Color.rgb(186, 230, 253)
        }
        val profLeft = leftMargin + 14f
        val profW = profPaint.measureText(profLabel) + 10f
        val profRect = RectF(profLeft, 38f, profLeft + profW, 49.5f)
        val profBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(30, 58, 138)
        }
        canvas.drawRoundRect(profRect, 3.5f, 3.5f, profBgPaint)
        canvas.drawText(profLabel, profLeft + 5f, 46.5f, profPaint)

        // Statistiche Annuali sull'Header a destra
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        val yearStats = ShiftRepository.getComprehensiveReportStats(yearStart, yearEnd)

        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            color = Color.rgb(226, 232, 240)
            textAlign = Paint.Align.RIGHT
        }
        val statsSummary = "Totale: ${String.format(Locale.US, "%.0fh", yearStats.totalHours)} | ${yearStats.workedDays} turni | ${yearStats.countRiposo} riposi | +${String.format(Locale.US, "%.0fh", yearStats.totalOvertimeHours)} extra"
        canvas.drawText(statsSummary, rightMargin - 14f, 34f, statsPaint)

        val noteOrDate = if (options.headerNotes.isNotBlank()) options.headerNotes.take(45) else "Pianificazione di Servizio Annuale"
        val subStatsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(148, 163, 184)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(noteOrDate, rightMargin - 14f, 47f, subStatsPaint)

        // 2. Colonne dei 12 Mesi
        val months = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")
        val gridTop = 58f
        val colWidth = contentWidth / 12f
        val headerRowHeight = 15f

        val monthHeaderRect = RectF(leftMargin, gridTop, rightMargin, gridTop + headerRowHeight)
        bgPaint.color = Color.rgb(30, 41, 59)
        canvas.drawRoundRect(monthHeaderRect, 4f, 4f, bgPaint)

        val monthHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            isFakeBoldText = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        for (m in 0 until 12) {
            val colX = leftMargin + m * colWidth + colWidth / 2f
            canvas.drawText(months[m], colX, gridTop + 10.5f, monthHeaderPaint)
        }

        // 3. Righe per i giorni 1..31 (scalate per riempire tutta la pagina)
        val legendY = 544f
        val gridStartRowY = gridTop + headerRowHeight + 2f
        val rowHeight = (legendY - 4f - gridStartRowY) / 31f

        val cellBorderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        val holidayBgPaint = Paint().apply {
            color = Color.rgb(254, 242, 242)
            style = Paint.Style.FILL
        }
        val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.2f
            isFakeBoldText = true
            color = Color.rgb(71, 85, 105)
        }
        val dayLetterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 5.5f
            color = Color.rgb(148, 163, 184)
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.0f
            isFakeBoldText = true
        }

        for (day in 1..31) {
            val rowY = gridStartRowY + (day - 1) * rowHeight

            for (m in 0 until 12) {
                val monthIndex = m + 1
                val colLeft = leftMargin + m * colWidth
                val colRight = colLeft + colWidth
                val cellRect = RectF(colLeft, rowY, colRight, rowY + rowHeight)

                val date = try {
                    LocalDate.of(year, monthIndex, day)
                } catch (_: Exception) {
                    null
                }

                if (date != null) {
                    val isSunday = date.dayOfWeek == java.time.DayOfWeek.SUNDAY
                    val isSaturday = date.dayOfWeek == java.time.DayOfWeek.SATURDAY
                    val holiday = ShiftRepository.isHoliday(date)
                    val isHoliday = isSunday || holiday != null

                    if (isHoliday) {
                        canvas.drawRect(cellRect, holidayBgPaint)
                    } else if (isSaturday) {
                        canvas.drawRect(cellRect, Paint().apply { color = Color.rgb(248, 250, 252); style = Paint.Style.FILL })
                    }
                    canvas.drawRect(cellRect, cellBorderPaint)

                    // Numero giorno + iniziale giorno settimana
                    val dayLetter = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ITALIAN)
                    canvas.drawText(String.format(Locale.US, "%02d", day), colLeft + 2.5f, rowY + rowHeight - 4f, if (isHoliday) Paint(dayNumPaint).apply { color = Color.rgb(220, 38, 38) } else dayNumPaint)
                    canvas.drawText(dayLetter, colLeft + 11.5f, rowY + rowHeight - 4f, dayLetterPaint)

                    // Turno del giorno
                    val shifts = ShiftRepository.getShiftsForDate(date)
                    val s = shifts.firstOrNull()
                    if (s != null) {
                        val def = ShiftRepository.getShiftDefinition(s.shiftDefId)
                        if (def != null && (options.filterShiftId == null || def.id == options.filterShiftId)) {
                            val badgeColor = (def.colorHex or 0xFF000000).toInt()
                            bgPaint.color = badgeColor

                            val pillLeft = colLeft + 19f
                            val pillRect = RectF(pillLeft, rowY + 1.5f, colRight - 2f, rowY + rowHeight - 1.5f)
                            canvas.drawRoundRect(pillRect, 2.5f, 2.5f, bgPaint)

                            badgeTextPaint.color = getContrastingTextColor(badgeColor)
                            canvas.drawText(def.shortCode.take(4), pillLeft + 2.5f, rowY + rowHeight - 4f, badgeTextPaint)
                        }
                    }
                } else {
                    // Giorno non esistente (es. 30 febbraio)
                    canvas.drawRect(cellRect, Paint().apply { color = Color.rgb(241, 245, 249); style = Paint.Style.FILL })
                    canvas.drawRect(cellRect, cellBorderPaint)
                }
            }
        }

        // 4. Striscia Legenda Turni
        val legendHeight = 18f
        val legendRect = RectF(leftMargin, legendY, rightMargin, legendY + legendHeight)
        bgPaint.color = Color.rgb(248, 250, 252)
        canvas.drawRoundRect(legendRect, 4f, 4f, bgPaint)
        strokePaint.color = Color.rgb(226, 232, 240)
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(legendRect, 4f, 4f, strokePaint)

        val legendTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.8f
            isFakeBoldText = true
            color = Color.rgb(71, 85, 105)
        }
        canvas.drawText("LEGENDA TURNI:", leftMargin + 8f, legendY + 11.5f, legendTitlePaint)

        var legendX = leftMargin + 76f
        val legendDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.5f
            color = Color.rgb(30, 41, 59)
        }

        val allDefs = ShiftRepository.shiftDefinitions.toList()
        for (def in allDefs.take(10)) {
            val dotColor = (def.colorHex or 0xFF000000).toInt()
            legendDotPaint.color = dotColor
            canvas.drawCircle(legendX + 3.5f, legendY + 9.5f, 3f, legendDotPaint)

            val itemLabel = "${def.shortCode} ${def.name.take(10)}"
            canvas.drawText(itemLabel, legendX + 9f, legendY + 11.5f, legendTextPaint)
            legendX += legendTextPaint.measureText(itemLabel) + 14f
            if (legendX > rightMargin - 40f) break
        }

        // 5. Footer Annuale
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(100, 116, 139)
        }
        val footerLinePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
        }
        canvas.drawLine(leftMargin, 570f, rightMargin, 570f, footerLinePaint)
        canvas.drawText("Calendario Turni Pro • Tabellone Annuale Ufficiale $year", leftMargin, 582f, footerPaint)

        if (options.includeLogo) {
            val logoText = "Generato con Calendario Turni per Android"
            canvas.drawText(logoText, rightMargin - footerPaint.measureText(logoText), 582f, footerPaint)
        }

        doc.finishPage(page)
    }

    /**
     * Disegna il prospetto PERSONALIZZATO adattivo (A4 orizzontale landscape: 842 x 595 pt):
     * - Se intervallo <= 35 giorni: visualizzazione calendario mese dinamico esteso a tutta pagina.
     * - Se intervallo > 35 giorni: tabellone multi-mese dinamico con colonne calibrate per riempire
     *   l'intera pagina senza vuoti e con testo ingrandito.
     */
    private fun renderPersonalizzatoPdf(doc: PdfDocument, options: PdfExportOptions) {
        val daysBetween = ChronoUnit.DAYS.between(options.startDate, options.endDate) + 1

        if (daysBetween <= 35) {
            // Delega a Mese con le date del periodo personalizzato
            renderMesePdf(doc, options)
            return
        }

        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val leftMargin = 28f
        val rightMargin = 814f
        val contentWidth = rightMargin - leftMargin

        val bgPaint = Paint().apply { style = Paint.Style.FILL }
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // 1. Header Personalizzato Superiore
        val headerRect = RectF(leftMargin, 16f, rightMargin, 54f)
        bgPaint.color = Color.rgb(15, 23, 42)
        canvas.drawRoundRect(headerRect, 8f, 8f, bgPaint)

        strokePaint.color = Color.rgb(2, 132, 199)
        strokePaint.strokeWidth = 1.2f
        canvas.drawRoundRect(headerRect, 8f, 8f, strokePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f
            isFakeBoldText = true
            color = Color.WHITE
        }
        canvas.drawText("CALENDARIO TURNI • PROSPETTO PERSONALIZZATO", leftMargin + 14f, 34f, titlePaint)

        // Sottotitolo Periodo + Profilo
        val activeProfile = ShiftRepository.activeWorkProfile
        val intervalStr = "${options.startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} ➜ ${options.endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
        val subTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            isFakeBoldText = true
            color = Color.rgb(56, 189, 248)
        }
        canvas.drawText(intervalStr, leftMargin + 14f, 48f, subTitlePaint)

        val profLabel = "PROFILO: ${activeProfile.name.uppercase(Locale.ITALIAN)}"
        val profPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7f
            isFakeBoldText = true
            color = Color.rgb(186, 230, 253)
        }
        val profLeft = leftMargin + 14f + subTitlePaint.measureText(intervalStr) + 12f
        val profW = profPaint.measureText(profLabel) + 10f
        val profRect = RectF(profLeft, 38f, profLeft + profW, 49.5f)
        val profBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(30, 58, 138)
        }
        canvas.drawRoundRect(profRect, 3.5f, 3.5f, profBgPaint)
        canvas.drawText(profLabel, profLeft + 5f, 46.5f, profPaint)

        // Statistiche Intervallo
        val stats = ShiftRepository.getComprehensiveReportStats(options.startDate, options.endDate)
        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            color = Color.rgb(226, 232, 240)
            textAlign = Paint.Align.RIGHT
        }
        val statsSummary = "Totale: ${String.format(Locale.US, "%.0fh", stats.totalHours)} | ${stats.workedDays} turni | ${stats.countRiposo} riposi | +${String.format(Locale.US, "%.0fh", stats.totalOvertimeHours)} extra"
        canvas.drawText(statsSummary, rightMargin - 14f, 34f, statsPaint)

        val noteOrDate = if (options.headerNotes.isNotBlank()) options.headerNotes.take(45) else "Prospetto Dinamico (${daysBetween} giorni)"
        val subStatsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(148, 163, 184)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(noteOrDate, rightMargin - 14f, 47f, subStatsPaint)

        // 2. Calcolo Mesi Compresi nell'Intervallo
        var curYm = YearMonth.from(options.startDate)
        val endYm = YearMonth.from(options.endDate)
        val monthsList = mutableListOf<YearMonth>()
        while (!curYm.isAfter(endYm) && monthsList.size < 12) {
            monthsList.add(curYm)
            curYm = curYm.plusMonths(1)
        }
        val numMonths = monthsList.size.coerceAtLeast(1)
        val colWidth = contentWidth / numMonths.toFloat()

        // Intestazioni Mesi
        val gridTop = 58f
        val headerRowHeight = 16f
        val monthHeaderRect = RectF(leftMargin, gridTop, rightMargin, gridTop + headerRowHeight)
        bgPaint.color = Color.rgb(30, 41, 59)
        canvas.drawRoundRect(monthHeaderRect, 4f, 4f, bgPaint)

        val monthHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (numMonths <= 4) 8.5f else 7.5f
            isFakeBoldText = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        for (m in 0 until numMonths) {
            val ym = monthsList[m]
            val colX = leftMargin + m * colWidth + colWidth / 2f
            val mLabel = if (numMonths <= 4) {
                "${ym.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).uppercase(Locale.ITALIAN)} ${ym.year}"
            } else {
                "${ym.month.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).uppercase(Locale.ITALIAN)} '${ym.year.toString().takeLast(2)}"
            }
            canvas.drawText(mLabel, colX, gridTop + 11.5f, monthHeaderPaint)
        }

        // 3. Righe dei Giorni 1..31
        val legendY = 544f
        val gridStartRowY = gridTop + headerRowHeight + 2f
        val rowHeight = (legendY - 4f - gridStartRowY) / 31f

        val cellBorderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
        }
        val holidayBgPaint = Paint().apply {
            color = Color.rgb(254, 242, 242)
            style = Paint.Style.FILL
        }
        val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (numMonths <= 4) 7.5f else 6.2f
            isFakeBoldText = true
            color = Color.rgb(71, 85, 105)
        }
        val dayLetterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (numMonths <= 4) 6.8f else 5.5f
            color = Color.rgb(148, 163, 184)
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (numMonths <= 4) 7.2f else 6.0f
            isFakeBoldText = true
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.2f
            color = Color.rgb(71, 85, 105)
        }

        for (day in 1..31) {
            val rowY = gridStartRowY + (day - 1) * rowHeight

            for (m in 0 until numMonths) {
                val ym = monthsList[m]
                val colLeft = leftMargin + m * colWidth
                val colRight = colLeft + colWidth
                val cellRect = RectF(colLeft, rowY, colRight, rowY + rowHeight)

                val date = try {
                    ym.atDay(day)
                } catch (_: Exception) {
                    null
                }

                if (date != null && !date.isBefore(options.startDate) && !date.isAfter(options.endDate)) {
                    val isSunday = date.dayOfWeek == java.time.DayOfWeek.SUNDAY
                    val isSaturday = date.dayOfWeek == java.time.DayOfWeek.SATURDAY
                    val holiday = ShiftRepository.isHoliday(date)
                    val isHoliday = isSunday || holiday != null

                    if (isHoliday) {
                        canvas.drawRect(cellRect, holidayBgPaint)
                    } else if (isSaturday) {
                        canvas.drawRect(cellRect, Paint().apply { color = Color.rgb(248, 250, 252); style = Paint.Style.FILL })
                    }
                    canvas.drawRect(cellRect, cellBorderPaint)

                    val dayLetter = if (numMonths <= 4) {
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN)
                    } else {
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ITALIAN)
                    }

                    canvas.drawText(String.format(Locale.US, "%02d", day), colLeft + 3f, rowY + rowHeight - 4.5f, if (isHoliday) Paint(dayNumPaint).apply { color = Color.rgb(220, 38, 38) } else dayNumPaint)
                    val letterOffset = if (numMonths <= 4) 15f else 11.5f
                    canvas.drawText(dayLetter, colLeft + letterOffset, rowY + rowHeight - 4.5f, dayLetterPaint)

                    // Turno
                    val shifts = ShiftRepository.getShiftsForDate(date)
                    val s = shifts.firstOrNull()
                    if (s != null) {
                        val def = ShiftRepository.getShiftDefinition(s.shiftDefId)
                        if (def != null && (options.filterShiftId == null || def.id == options.filterShiftId)) {
                            val badgeColor = (def.colorHex or 0xFF000000).toInt()
                            bgPaint.color = badgeColor

                            val pillLeft = colLeft + (if (numMonths <= 4) 34f else 20f)
                            val pillW = if (numMonths <= 4) (colRight - pillLeft - (if (options.includeTime && !def.isAllDay) 72f else 4f)) else (colRight - pillLeft - 2f)
                            val pillRect = RectF(pillLeft, rowY + 1.5f, pillLeft + pillW, rowY + rowHeight - 1.5f)
                            canvas.drawRoundRect(pillRect, 2.5f, 2.5f, bgPaint)

                            badgeTextPaint.color = getContrastingTextColor(badgeColor)
                            val label = if (numMonths <= 4) "${def.shortCode} • ${def.name.take(10)}" else def.shortCode
                            canvas.drawText(label, pillLeft + 3f, rowY + rowHeight - 4.5f, badgeTextPaint)

                            // Se le colonne sono larghe, mostra anche l'orario e straordinario!
                            if (numMonths <= 4 && options.includeTime && !def.isAllDay) {
                                canvas.drawText("${def.startTime}-${def.endTime}", pillLeft + pillW + 5f, rowY + rowHeight - 4.5f, timePaint)
                            }
                        }
                    }
                } else if (date != null) {
                    // Giorno fuori dall'intervallo selezionato
                    canvas.drawRect(cellRect, Paint().apply { color = Color.rgb(248, 250, 252); style = Paint.Style.FILL })
                    canvas.drawRect(cellRect, cellBorderPaint)
                } else {
                    // Giorno non esistente
                    canvas.drawRect(cellRect, Paint().apply { color = Color.rgb(241, 245, 249); style = Paint.Style.FILL })
                    canvas.drawRect(cellRect, cellBorderPaint)
                }
            }
        }

        // 4. Striscia Legenda Turni
        val legendHeight = 18f
        val legendRect = RectF(leftMargin, legendY, rightMargin, legendY + legendHeight)
        bgPaint.color = Color.rgb(248, 250, 252)
        canvas.drawRoundRect(legendRect, 4f, 4f, bgPaint)
        strokePaint.color = Color.rgb(226, 232, 240)
        strokePaint.strokeWidth = 0.8f
        canvas.drawRoundRect(legendRect, 4f, 4f, strokePaint)

        val legendTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.8f
            isFakeBoldText = true
            color = Color.rgb(71, 85, 105)
        }
        canvas.drawText("LEGENDA TURNI:", leftMargin + 8f, legendY + 11.5f, legendTitlePaint)

        var legendX = leftMargin + 76f
        val legendDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val legendTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.5f
            color = Color.rgb(30, 41, 59)
        }

        val allDefs = ShiftRepository.shiftDefinitions.toList()
        for (def in allDefs.take(10)) {
            val dotColor = (def.colorHex or 0xFF000000).toInt()
            legendDotPaint.color = dotColor
            canvas.drawCircle(legendX + 3.5f, legendY + 9.5f, 3f, legendDotPaint)

            val itemLabel = "${def.shortCode} ${def.name.take(10)}"
            canvas.drawText(itemLabel, legendX + 9f, legendY + 11.5f, legendTextPaint)
            legendX += legendTextPaint.measureText(itemLabel) + 14f
            if (legendX > rightMargin - 40f) break
        }

        // 5. Footer Personalizzato
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(100, 116, 139)
        }
        val footerLinePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
        }
        canvas.drawLine(leftMargin, 570f, rightMargin, 570f, footerLinePaint)
        canvas.drawText("Calendario Turni Pro • Prospetto Personalizzato Dinamico", leftMargin, 582f, footerPaint)

        if (options.includeLogo) {
            val logoText = "Generato con Calendario Turni per Android"
            canvas.drawText(logoText, rightMargin - footerPaint.measureText(logoText), 582f, footerPaint)
        }

        doc.finishPage(page)
    }
}
