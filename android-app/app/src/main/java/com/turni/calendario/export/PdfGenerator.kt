package com.turni.calendario.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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
     * Disegna il prospetto in formato Elenco (A4 verticale: 595 x 842 pt)
     */
    private fun renderElencoPdf(doc: PdfDocument, options: PdfExportOptions) {
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            isFakeBoldText = true
            color = android.graphics.Color.DKGRAY
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            color = android.graphics.Color.BLACK
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }
        val holidayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(200, 30, 30)
        }
        val linePaint = Paint().apply {
            color = android.graphics.Color.rgb(220, 220, 225)
            strokeWidth = 0.8f
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Titolo (es. "ottobre 2026")
        val monthName = options.startDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).lowercase(Locale.ITALIAN)
        val titleText = "$monthName ${options.startDate.year}"
        canvas.drawText(titleText, 40f, 50f, titlePaint)

        // Intestazione note personalizzate
        var currentY = 70f
        if (options.headerNotes.isNotBlank()) {
            val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 9f
                color = android.graphics.Color.GRAY
            }
            canvas.drawText(options.headerNotes.take(80), 40f, currentY, notePaint)
            currentY += 15f
        }

        // Intestazioni colonne tabella
        canvas.drawText("Giorno", 40f, currentY + 10f, headerPaint)
        if (options.includeTime) {
            canvas.drawText("Ora", 120f, currentY + 10f, headerPaint)
        }
        if (options.includeTitle) {
            canvas.drawText("Titolo", 250f, currentY + 10f, headerPaint)
        }
        currentY += 16f
        canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
        currentY += 6f

        // Righe dei giorni
        var currDate = options.startDate
        val maxDays = 31
        var dayCount = 0

        while (!currDate.isAfter(options.endDate) && dayCount < maxDays && currentY < 780f) {
            val dayNum = currDate.dayOfMonth
            val dayOfWeek = currDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ITALIAN).lowercase(Locale.ITALIAN)
            val isSunday = currDate.dayOfWeek == java.time.DayOfWeek.SUNDAY
            val holiday = ShiftRepository.isHoliday(currDate)
            val isHoliday = holiday != null || isSunday

            // Giorno (es. "1 gio")
            val dayText = "$dayNum  $dayOfWeek"
            canvas.drawText(dayText, 40f, currentY + 10f, if (isHoliday) holidayPaint else datePaint)

            val shifts = ShiftRepository.getShiftsForDate(currDate)
            val activeShift = shifts.firstOrNull() ?: if (options.includeEmptyRows) null else null

            if (activeShift != null) {
                val def = ShiftRepository.getShiftDefinition(activeShift.shiftDefId)
                if (def != null && (options.filterShiftId == null || def.id == options.filterShiftId)) {
                    // Pallino colorato icona
                    if (options.includeIcon) {
                        dotPaint.color = (def.colorHex or 0xFF000000).toInt()
                        canvas.drawCircle(115f, currentY + 7f, 3.5f, dotPaint)
                    }

                    // Ora
                    if (options.includeTime) {
                        val timeStr = if (def.isAllDay) "Tutto il giorno" else "${def.startTime} - ${def.endTime}"
                        canvas.drawText(timeStr, 126f, currentY + 10f, textPaint)
                    }

                    // Titolo
                    if (options.includeTitle) {
                        canvas.drawText(def.name, 250f, currentY + 10f, textPaint)
                    }
                }
            } else if (holiday != null && options.includeEvents) {
                // Mostra festività sulla riga
                canvas.drawText("Festività: ${holiday.name}", 126f, currentY + 10f, holidayPaint)
            }

            currentY += 18f
            canvas.drawLine(40f, currentY, 555f, currentY, linePaint)
            currentY += 4f

            currDate = currDate.plusDays(1)
            dayCount++
        }

        // Footer festività e logo
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            color = android.graphics.Color.rgb(100, 100, 110)
        }
        val holidayBottom = ShiftRepository.isHoliday(LocalDate.of(options.startDate.year, 10, 4))
        if (holidayBottom != null) {
            val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 8.5f
                color = android.graphics.Color.rgb(30, 100, 200)
            }
            canvas.drawText("04/10 ${holidayBottom.name}", 40f, 805f, bluePaint)
        }

        if (options.includeLogo) {
            val logoText = "Realizzato con Calendario Turni per Android"
            val textWidth = footerPaint.measureText(logoText)
            canvas.drawText(logoText, 555f - textWidth, 805f, footerPaint)
        }

        doc.finishPage(page)
    }

    /**
     * Disegna il prospetto in formato Mese (A4 orizzontale landscape: 842 x 595 pt)
     */
    private fun renderMesePdf(doc: PdfDocument, options: PdfExportOptions) {
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            isFakeBoldText = true
            color = android.graphics.Color.DKGRAY
            textAlign = Paint.Align.CENTER
        }
        val cellBorderPaint = Paint().apply {
            color = android.graphics.Color.rgb(190, 195, 205)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val cellHolidayBgPaint = Paint().apply {
            color = android.graphics.Color.rgb(255, 215, 215)
            style = Paint.Style.FILL
        }
        val dateNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            isFakeBoldText = true
            color = android.graphics.Color.DKGRAY
        }
        val shiftTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 7.5f
            color = android.graphics.Color.BLACK
        }
        val shiftTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.8f
            color = android.graphics.Color.DKGRAY
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Titolo
        val monthName = options.startDate.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).lowercase(Locale.ITALIAN)
        canvas.drawText("$monthName ${options.startDate.year}", 40f, 45f, titlePaint)

        // Intestazioni giorni della settimana (Lunedì - Domenica)
        val daysOfWeek = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica")
        val gridLeft = 40f
        val gridTop = 65f
        val gridWidth = 762f
        val colWidth = gridWidth / 7f
        val rowHeight = 75f

        for (i in 0 until 7) {
            val colCenterX = gridLeft + i * colWidth + colWidth / 2f
            canvas.drawText(daysOfWeek[i], colCenterX, gridTop + 12f, headerPaint)
        }

        // Griglia del mese
        val yearMonth = YearMonth.from(options.startDate)
        val firstDayOfMonth = yearMonth.atDay(1)
        val firstDayOfWeekIndex = (firstDayOfMonth.dayOfWeek.value - 1) // 0 for Monday
        val daysInMonth = yearMonth.lengthOfMonth()

        var currentDay = 1
        var row = 0

        while (currentDay <= daysInMonth && row < 6) {
            val rowTop = gridTop + 20f + row * rowHeight

            for (col in 0 until 7) {
                val cellLeft = gridLeft + col * colWidth
                val cellRight = cellLeft + colWidth
                val cellBottom = rowTop + rowHeight
                val rect = RectF(cellLeft, rowTop, cellRight, cellBottom)

                val dayIndex = row * 7 + col
                if (dayIndex >= firstDayOfWeekIndex && currentDay <= daysInMonth) {
                    val date = yearMonth.atDay(currentDay)
                    val isSunday = col == 6
                    val holiday = ShiftRepository.isHoliday(date)
                    val isHoliday = holiday != null || isSunday

                    if (isHoliday) {
                        canvas.drawRect(rect, cellHolidayBgPaint)
                    }
                    canvas.drawRect(rect, cellBorderPaint)

                    // Numero del giorno
                    canvas.drawText(currentDay.toString(), cellRight - 14f, rowTop + 12f, dateNumPaint)

                    // Turni del giorno
                    val shifts = ShiftRepository.getShiftsForDate(date)
                    var shiftY = rowTop + 24f
                    shifts.take(2).forEach { s ->
                        val def = ShiftRepository.getShiftDefinition(s.shiftDefId)
                        if (def != null && (options.filterShiftId == null || def.id == options.filterShiftId)) {
                            if (options.includeIcon) {
                                dotPaint.color = (def.colorHex or 0xFF000000).toInt()
                                canvas.drawCircle(cellLeft + 8f, shiftY - 2.5f, 2.5f, dotPaint)
                            }
                            val textStart = if (options.includeIcon) cellLeft + 14f else cellLeft + 6f
                            if (options.includeTitle) {
                                canvas.drawText(def.name.take(12), textStart, shiftY, shiftTitlePaint)
                            }
                            if (options.includeTime && !def.isAllDay) {
                                canvas.drawText("${def.startTime} - ${def.endTime}", textStart, shiftY + 9f, shiftTimePaint)
                            }
                            shiftY += 20f
                        }
                    }

                    currentDay++
                } else {
                    // Cella vuota
                    canvas.drawRect(rect, cellBorderPaint)
                }
            }
            row++
        }

        // Footer
        if (options.includeLogo) {
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 8.5f
                color = android.graphics.Color.rgb(100, 100, 110)
            }
            val logoText = "Realizzato con Calendario Turni per Android"
            canvas.drawText(logoText, 842f - 40f - footerPaint.measureText(logoText), 570f, footerPaint)
        }

        doc.finishPage(page)
    }

    /**
     * Disegna il prospetto in formato Anno (A4 orizzontale landscape: 842 x 595 pt)
     * Tabellone 12 colonne (gen - dic) con le righe 1..31.
     */
    private fun renderAnnoPdf(doc: PdfDocument, options: PdfExportOptions) {
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }
        val monthHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(180, 40, 40)
            textAlign = Paint.Align.CENTER
        }
        val dayNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 6.5f
            isFakeBoldText = true
            color = android.graphics.Color.DKGRAY
        }
        val shiftTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 5.8f
            color = android.graphics.Color.BLACK
        }
        val holidayBgPaint = Paint().apply {
            color = android.graphics.Color.rgb(255, 220, 220)
            style = Paint.Style.FILL
        }
        val linePaint = Paint().apply {
            color = android.graphics.Color.rgb(230, 230, 235)
            strokeWidth = 0.5f
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val year = options.selectedYear
        canvas.drawText(year.toString(), 35f, 40f, titlePaint)

        val months = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
        val gridLeft = 35f
        val gridTop = 55f
        val colWidth = 64f
        val rowHeight = 15f

        // Intestazioni mesi
        for (m in 0 until 12) {
            val colX = gridLeft + m * colWidth + colWidth / 2f
            canvas.drawText(months[m], colX, gridTop + 8f, monthHeaderPaint)
        }

        // Righe per i giorni da 1 a 31
        for (day in 1..31) {
            val rowY = gridTop + 14f + (day - 1) * rowHeight

            for (m in 0 until 12) {
                val monthIndex = m + 1
                val colLeft = gridLeft + m * colWidth

                val date = try {
                    LocalDate.of(year, monthIndex, day)
                } catch (_: Exception) {
                    null
                }

                if (date != null) {
                    val isSunday = date.dayOfWeek == java.time.DayOfWeek.SUNDAY
                    val isHoliday = isSunday || ShiftRepository.isHoliday(date) != null

                    if (isHoliday) {
                        canvas.drawRect(
                            RectF(colLeft, rowY - 2f, colLeft + colWidth - 2f, rowY + rowHeight - 2f),
                            holidayBgPaint
                        )
                    }

                    // Numero giorno
                    canvas.drawText(day.toString(), colLeft + 2f, rowY + 9f, dayNumPaint)

                    // Turno
                    val shifts = ShiftRepository.getShiftsForDate(date)
                    val s = shifts.firstOrNull()
                    if (s != null) {
                        val def = ShiftRepository.getShiftDefinition(s.shiftDefId)
                        if (def != null && (options.filterShiftId == null || def.id == options.filterShiftId)) {
                            if (options.includeIcon) {
                                dotPaint.color = (def.colorHex or 0xFF000000).toInt()
                                canvas.drawCircle(colLeft + 16f, rowY + 7f, 2f, dotPaint)
                            }
                            val textX = if (options.includeIcon) colLeft + 20f else colLeft + 15f
                            canvas.drawText(def.name.take(6), textX, rowY + 9f, shiftTextPaint)
                        }
                    }
                }
                canvas.drawLine(colLeft, rowY + rowHeight - 2f, colLeft + colWidth - 2f, rowY + rowHeight - 2f, linePaint)
            }
        }

        // Footer
        if (options.includeLogo) {
            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 8f
                color = android.graphics.Color.rgb(100, 100, 110)
            }
            val logoText = "Realizzato con Calendario Turni per Android"
            canvas.drawText(logoText, 842f - 40f - footerPaint.measureText(logoText), 565f, footerPaint)
        }

        doc.finishPage(page)
    }
}
