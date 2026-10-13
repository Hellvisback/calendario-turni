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

        // 1. Header Superiore Elegante (Fascia Navy Scuro Slate)
        val headerRect = RectF(leftMargin, 24f, rightMargin, 74f)
        bgPaint.color = Color.rgb(15, 23, 42) // Slate 900
        canvas.drawRoundRect(headerRect, 8f, 8f, bgPaint)

        // Bordo sottile ciano sull'header per tocco Liquid Glass
        strokePaint.color = Color.rgb(2, 132, 199) // Sky 600
        strokePaint.strokeWidth = 1.2f
        canvas.drawRoundRect(headerRect, 8f, 8f, strokePaint)

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13.5f
            isFakeBoldText = true
            color = Color.WHITE
        }
        canvas.drawText("CALENDARIO TURNI", leftMargin + 14f, 44f, headerTitlePaint)

        val monthName = options.startDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).uppercase(Locale.ITALIAN)
        val periodSubtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            isFakeBoldText = true
            color = Color.rgb(56, 189, 248) // Sky 400
        }
        val periodText = if (options.startDate.month == options.endDate.month && options.startDate.year == options.endDate.year) {
            "PROSPETTO MENSILE • $monthName ${options.startDate.year}"
        } else {
            "PERIODO • ${options.startDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"))} - ${options.endDate.format(DateTimeFormatter.ofPattern("dd/MM/yy"))}"
        }
        canvas.drawText(periodText, leftMargin + 14f, 62f, periodSubtitlePaint)

        // Sezione note o diciture utente nell'header a destra
        val headerNotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            color = Color.rgb(203, 213, 225) // Slate 300
            textAlign = Paint.Align.RIGHT
        }
        if (options.headerNotes.isNotBlank()) {
            canvas.drawText(options.headerNotes.take(45), rightMargin - 14f, 44f, headerNotePaint)
        }
        val genDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(148, 163, 184) // Slate 400
            textAlign = Paint.Align.RIGHT
        }
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        canvas.drawText("Data doc: $todayStr", rightMargin - 14f, 61f, genDatePaint)

        // 2. Card Statistiche Mensili Certificate (4 riquadri orizzontali)
        val stats = ShiftRepository.getComprehensiveReportStats(options.startDate, options.endDate)
        val statsTop = 82f
        val statsBottom = 116f
        val cardGap = 6f
        val cardWidth = (contentWidth - cardGap * 3f) / 4f

        val statCardBgPaint = Paint().apply {
            color = Color.rgb(248, 250, 252) // Slate 50
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
            Pair(String.format(Locale.US, "%.1fh", stats.totalHours), "Ore Lavorate"),
            Pair("${stats.workedDays} turni", "Presenze"),
            Pair("${stats.countRiposo} RIP • ${stats.countSmonto} SMO", "Riposi / Smonti"),
            Pair("+${String.format(Locale.US, "%.1fh", stats.totalOvertimeHours)} • ${stats.countTrasferte} T", "Straord. / Trasferte")
        )

        for (i in 0 until 4) {
            val cLeft = leftMargin + i * (cardWidth + cardGap)
            val cRight = cLeft + cardWidth
            val cRect = RectF(cLeft, statsTop, cRight, statsBottom)
            canvas.drawRoundRect(cRect, 6f, 6f, statCardBgPaint)
            canvas.drawRoundRect(cRect, 6f, 6f, statCardBorderPaint)

            val centerX = cLeft + cardWidth / 2f
            canvas.drawText(cardsData[i].first, centerX, statsTop + 16f, statValuePaint)
            canvas.drawText(cardsData[i].second, centerX, statsTop + 28f, statLabelPaint)
        }

        // 3. Intestazione Tabella Turni
        var currentY = 124f
        val tableHeaderHeight = 18f
        val tableHeaderRect = RectF(leftMargin, currentY, rightMargin, currentY + tableHeaderHeight)
        bgPaint.color = Color.rgb(30, 41, 59) // Slate 800
        canvas.drawRoundRect(tableHeaderRect, 4f, 4f, bgPaint)

        val colHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.8f
            isFakeBoldText = true
            color = Color.WHITE
        }
        canvas.drawText("GIORNO", leftMargin + 10f, currentY + 12f, colHeaderPaint)
        if (options.includeTime) {
            canvas.drawText("ORARIO", leftMargin + 85f, currentY + 12f, colHeaderPaint)
        }
        canvas.drawText("TURNO", leftMargin + 185f, currentY + 12f, colHeaderPaint)
        if (options.includeDuration) {
            canvas.drawText("DURATA", leftMargin + 315f, currentY + 12f, colHeaderPaint)
        }
        canvas.drawText("NOTE / STATO", leftMargin + 380f, currentY + 12f, colHeaderPaint)

        currentY += tableHeaderHeight + 2f

        // 4. Righe della Tabella con Zebra Striping e Pillole Colorate
        val rowHeight = 19.5f
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
            textSize = 8.5f
            isFakeBoldText = true
            color = Color.rgb(15, 23, 42)
        }
        val holidayDateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            isFakeBoldText = true
            color = Color.rgb(220, 38, 38) // Red 600
        }
        val timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            color = Color.rgb(51, 65, 85) // Slate 700
        }
        val durationTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            color = Color.rgb(71, 85, 105) // Slate 600
        }
        val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(100, 116, 139) // Slate 500
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            isFakeBoldText = true
        }

        while (!currDate.isAfter(options.endDate) && currentY + rowHeight <= 800f) {
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

            // Colonna Giorno: "01 gio"
            val dayNumStr = String.format(Locale.ITALIAN, "%02d", currDate.dayOfMonth)
            val dayNameStr = currDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).lowercase(Locale.ITALIAN)
            val dayFullLabel = "$dayNumStr $dayNameStr"
            canvas.drawText(dayFullLabel, leftMargin + 8f, currentY + 13f, if (isHoliday) holidayDateTextPaint else dateTextPaint)

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
                    canvas.drawText(timeStr, leftMargin + 85f, currentY + 13f, timeTextPaint)
                }

                // Pillola Badge Turno
                val badgeColor = (def.colorHex or 0xFF000000).toInt()
                bgPaint.color = badgeColor
                val badgeLeft = leftMargin + 185f
                val badgeWidth = 110f
                val badgeRect = RectF(badgeLeft, currentY + 2.5f, badgeLeft + badgeWidth, currentY + rowHeight - 2.5f)
                canvas.drawRoundRect(badgeRect, 4.5f, 4.5f, bgPaint)

                badgeTextPaint.color = getContrastingTextColor(badgeColor)
                val badgeTitle = "${def.shortCode} • ${def.name.take(11)}"
                canvas.drawText(badgeTitle, badgeLeft + 6f, currentY + 12.5f, badgeTextPaint)

                // Durata
                if (options.includeDuration) {
                    val durStr = if (def.isAllDay) "—" else def.durationFormatted
                    canvas.drawText(durStr, leftMargin + 315f, currentY + 13f, durationTextPaint)
                }

                // Note & Stato (Straordinari, Trasferte, Festività, Note)
                var details = ""
                if (overtime > 0f) details += "+${String.format(Locale.US, "%.1fh", overtime)} "
                if (hasTrasferta) details += "[TRASF] "
                if (activeShift.notes.isNotBlank()) details += activeShift.notes.take(22)
                else if (holiday != null && options.includeEvents) details += holiday.name.take(22)

                if (details.isNotBlank()) {
                    canvas.drawText(details.trim(), leftMargin + 380f, currentY + 13f, noteTextPaint)
                }
            } else if (holiday != null && options.includeEvents) {
                // Riga festività senza turno
                if (options.includeTime) {
                    canvas.drawText("Festivo", leftMargin + 85f, currentY + 13f, holidayDateTextPaint)
                }
                canvas.drawText("★ ${holiday.name}", leftMargin + 185f, currentY + 13f, holidayDateTextPaint)
            } else {
                if (options.includeEmptyRows) {
                    canvas.drawText("—", leftMargin + 185f, currentY + 13f, noteTextPaint)
                }
            }

            currentY += rowHeight
            currDate = currDate.plusDays(1)
            dayIndex++
        }

        // 5. Footer Professionale
        val footerLinePaint = Paint().apply {
            color = Color.rgb(203, 213, 225) // Slate 300
            strokeWidth = 0.8f
        }
        canvas.drawLine(leftMargin, 810f, rightMargin, 810f, footerLinePaint)

        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(100, 116, 139) // Slate 500
        }
        val appTag = "Calendario Turni Pro • Documento aziendale e personale"
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
        val headerRect = RectF(leftMargin, 18f, rightMargin, 58f)
        bgPaint.color = Color.rgb(15, 23, 42) // Slate 900
        canvas.drawRoundRect(headerRect, 8f, 8f, bgPaint)

        strokePaint.color = Color.rgb(2, 132, 199)
        strokePaint.strokeWidth = 1.2f
        canvas.drawRoundRect(headerRect, 8f, 8f, strokePaint)

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            isFakeBoldText = true
            color = Color.WHITE
        }
        val monthName = options.startDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).uppercase(Locale.ITALIAN)
        canvas.drawText("CALENDARIO TURNI • PROSPETTO MENSILE", leftMargin + 16f, 38f, headerTitlePaint)

        val periodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            isFakeBoldText = true
            color = Color.rgb(56, 189, 248)
        }
        canvas.drawText("$monthName ${options.startDate.year}", leftMargin + 16f, 51f, periodPaint)

        // Micro-statistiche nell'header a destra
        val stats = ShiftRepository.getComprehensiveReportStats(options.startDate, options.endDate)
        val headerStatsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            color = Color.rgb(226, 232, 240)
            textAlign = Paint.Align.RIGHT
        }
        val statsSummary = "Totale: ${String.format(Locale.US, "%.1fh", stats.totalHours)} lavorate | ${stats.workedDays} turni | ${stats.countRiposo} riposi | +${String.format(Locale.US, "%.1fh", stats.totalOvertimeHours)} extra"
        canvas.drawText(statsSummary, rightMargin - 16f, 38f, headerStatsPaint)

        val noteOrDate = if (options.headerNotes.isNotBlank()) options.headerNotes.take(50) else "Data: ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
        val subStatsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(148, 163, 184)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(noteOrDate, rightMargin - 16f, 51f, subStatsPaint)

        // 2. Intestazioni Giorni della Settimana (Lunedì - Domenica)
        val daysOfWeek = listOf("Lunedì", "Martedì", "Mercoledì", "Giovedì", "Venerdì", "Sabato", "Domenica")
        val gridTop = 66f
        val colWidth = contentWidth / 7f
        val headerRowHeight = 18f

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
            canvas.drawText(daysOfWeek[i], colCenterX, gridTop + 12.5f, dayHeaderPaint)
        }

        // 3. Griglia del Mese
        val yearMonth = YearMonth.from(options.startDate)
        val firstDayOfMonth = yearMonth.atDay(1)
        val firstDayOfWeekIndex = firstDayOfMonth.dayOfWeek.value - 1 // 0 = Lunedì
        val daysInMonth = yearMonth.lengthOfMonth()

        val cellBorderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val cellWhiteBg = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val cellHolidayBg = Paint().apply { color = Color.rgb(254, 242, 242); style = Paint.Style.FILL }
        val cellInactiveBg = Paint().apply { color = Color.rgb(248, 250, 252); style = Paint.Style.FILL }

        val dateNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            isFakeBoldText = true
            color = Color.rgb(15, 23, 42)
            textAlign = Paint.Align.RIGHT
        }
        val holidayDateNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            isFakeBoldText = true
            color = Color.rgb(220, 38, 38)
            textAlign = Paint.Align.RIGHT
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7f
            isFakeBoldText = true
        }
        val shiftTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.2f
            color = Color.rgb(71, 85, 105)
        }

        val rowHeight = 76f
        var currentDay = 1
        var row = 0

        while (currentDay <= daysInMonth && row < 6) {
            val rowTop = gridTop + headerRowHeight + 2f + row * rowHeight

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
                    canvas.drawText(currentDay.toString(), cellRight - 6f, rowTop + 12f, numPaint)

                    // Eventuale festività indicata in mini testo
                    if (holiday != null && options.includeEvents) {
                        val holNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = 5.5f
                            color = Color.rgb(220, 38, 38)
                            isFakeBoldText = true
                        }
                        canvas.drawText(holiday.name.take(14), cellLeft + 5f, rowTop + 11f, holNamePaint)
                    }

                    // Turni del giorno (Pillole Cromatiche)
                    val shifts = ShiftRepository.getShiftsForDate(date)
                    var shiftY = rowTop + 18f

                    shifts.take(2).forEach { s ->
                        val def = ShiftRepository.getShiftDefinition(s.shiftDefId)
                        if (def != null && (options.filterShiftId == null || def.id == options.filterShiftId)) {
                            val badgeColor = (def.colorHex or 0xFF000000).toInt()
                            bgPaint.color = badgeColor

                            val pillRect = RectF(cellLeft + 4f, shiftY, cellRight - 4f, shiftY + 14f)
                            canvas.drawRoundRect(pillRect, 3.5f, 3.5f, bgPaint)

                            badgeTextPaint.color = getContrastingTextColor(badgeColor)
                            val pillText = if (options.includeTitle) "${def.shortCode} • ${def.name.take(8)}" else def.shortCode
                            canvas.drawText(pillText, cellLeft + 7f, shiftY + 10f, badgeTextPaint)

                            if (options.includeTime && !def.isAllDay) {
                                canvas.drawText("${def.startTime}-${def.endTime}", cellLeft + 5f, shiftY + 22f, shiftTimePaint)
                                shiftY += 25f
                            } else {
                                shiftY += 17f
                            }
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

        // 4. Footer Landscape
        val footerLinePaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 0.8f
        }
        canvas.drawLine(leftMargin, 570f, rightMargin, 570f, footerLinePaint)

        val footerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = Color.rgb(100, 116, 139)
        }
        canvas.drawText("Calendario Turni Pro • Vista Mensile Stampabile", leftMargin, 582f, footerTextPaint)

        if (options.includeLogo) {
            val logoText = "Generato con Calendario Turni per Android"
            canvas.drawText(logoText, rightMargin - footerTextPaint.measureText(logoText), 582f, footerTextPaint)
        }

        doc.finishPage(page)
    }

    /**
     * Disegna il prospetto in formato Anno (A4 orizzontale landscape: 842 x 595 pt).
     * Tabellone 12 colonne con le righe 1..31, badge turno perimetrali e festività in evidenza.
     */
    private fun renderAnnoPdf(doc: PdfDocument, options: PdfExportOptions) {
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val leftMargin = 30f
        val rightMargin = 812f
        val contentWidth = rightMargin - leftMargin

        val bgPaint = Paint().apply { style = Paint.Style.FILL }
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        // Header Annuale
        val headerRect = RectF(leftMargin, 16f, rightMargin, 50f)
        bgPaint.color = Color.rgb(15, 23, 42)
        canvas.drawRoundRect(headerRect, 6f, 6f, bgPaint)

        strokePaint.color = Color.rgb(2, 132, 199)
        strokePaint.strokeWidth = 1f
        canvas.drawRoundRect(headerRect, 6f, 6f, strokePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            isFakeBoldText = true
            color = Color.WHITE
        }
        val year = options.selectedYear
        canvas.drawText("CALENDARIO TURNI • PIANO ANNUALE $year", leftMargin + 14f, 37f, titlePaint)

        val noteTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            color = Color.rgb(203, 213, 225)
            textAlign = Paint.Align.RIGHT
        }
        val noteRight = if (options.headerNotes.isNotBlank()) options.headerNotes.take(45) else "Documento di Pianificazione Annuale"
        canvas.drawText(noteRight, rightMargin - 14f, 37f, noteTextPaint)

        // Colonne dei 12 Mesi
        val months = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")
        val gridTop = 56f
        val colWidth = contentWidth / 12f
        val rowHeight = 15.2f

        val monthHeaderBg = Paint().apply {
            color = Color.rgb(30, 41, 59)
            style = Paint.Style.FILL
        }
        val monthHeaderRect = RectF(leftMargin, gridTop, rightMargin, gridTop + 14f)
        canvas.drawRoundRect(monthHeaderRect, 3f, 3f, monthHeaderBg)

        val monthHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            isFakeBoldText = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        for (m in 0 until 12) {
            val colX = leftMargin + m * colWidth + colWidth / 2f
            canvas.drawText(months[m], colX, gridTop + 10f, monthHeaderPaint)
        }

        val cellBorderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 0.4f
        }
        val holidayBgPaint = Paint().apply {
            color = Color.rgb(254, 242, 242)
            style = Paint.Style.FILL
        }
        val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.2f
            isFakeBoldText = true
            color = Color.rgb(100, 116, 139)
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 5.8f
            isFakeBoldText = true
        }

        // Righe per i giorni 1..31
        for (day in 1..31) {
            val rowY = gridTop + 16f + (day - 1) * rowHeight

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
                    val isHoliday = isSunday || ShiftRepository.isHoliday(date) != null

                    if (isHoliday) {
                        canvas.drawRect(cellRect, holidayBgPaint)
                    }
                    canvas.drawRect(cellRect, cellBorderPaint)

                    // Numero giorno
                    val numStr = String.format(Locale.US, "%02d", day)
                    canvas.drawText(numStr, colLeft + 2f, rowY + 10.5f, dayNumPaint)

                    // Turno del giorno
                    val shifts = ShiftRepository.getShiftsForDate(date)
                    val s = shifts.firstOrNull()
                    if (s != null) {
                        val def = ShiftRepository.getShiftDefinition(s.shiftDefId)
                        if (def != null && (options.filterShiftId == null || def.id == options.filterShiftId)) {
                            val badgeColor = (def.colorHex or 0xFF000000).toInt()
                            bgPaint.color = badgeColor

                            val pillLeft = colLeft + 12f
                            val pillRect = RectF(pillLeft, rowY + 1.8f, colRight - 2f, rowY + rowHeight - 1.8f)
                            canvas.drawRoundRect(pillRect, 2.5f, 2.5f, bgPaint)

                            badgeTextPaint.color = getContrastingTextColor(badgeColor)
                            canvas.drawText(def.shortCode.take(4), pillLeft + 3f, rowY + 10f, badgeTextPaint)
                        }
                    }
                } else {
                    // Giorno non esistente (es. 30 febbraio)
                    canvas.drawRect(cellRect, Paint().apply { color = Color.rgb(241, 245, 249); style = Paint.Style.FILL })
                    canvas.drawRect(cellRect, cellBorderPaint)
                }
            }
        }

        // Footer Annuale
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7f
            color = Color.rgb(100, 116, 139)
        }
        canvas.drawText("Calendario Turni Pro • Tabellone Annuale $year", leftMargin, 575f, footerPaint)

        if (options.includeLogo) {
            val logoText = "Generato con Calendario Turni per Android"
            canvas.drawText(logoText, rightMargin - footerPaint.measureText(logoText), 575f, footerPaint)
        }

        doc.finishPage(page)
    }
}
