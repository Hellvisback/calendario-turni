package com.turni.calendario.export

import com.turni.calendario.model.PdfExportMode
import com.turni.calendario.model.PdfExportOptions
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class PdfPresentation { LIST, MONTHS, OVERVIEW }

/** Pure range planning shared by the screen and document renderer. */
object PdfExportPlan {
    const val MAX_OVERVIEW_MONTHS = 12
    const val OVERVIEW_MAX_MONTHS_ERROR =
        "Per una panoramica su una pagina, seleziona al massimo 12 mesi."

    fun presentation(options: PdfExportOptions): PdfPresentation = when (options.mode) {
        PdfExportMode.ELENCO -> PdfPresentation.LIST
        PdfExportMode.MESE -> PdfPresentation.MONTHS
        PdfExportMode.ANNO, PdfExportMode.PERSONALIZZATO -> PdfPresentation.OVERVIEW
    }

    fun range(options: PdfExportOptions): Pair<LocalDate, LocalDate> = when (options.mode) {
        PdfExportMode.MESE -> YearMonth.from(options.startDate).let { it.atDay(1) to it.atEndOfMonth() }
        PdfExportMode.ANNO -> LocalDate.of(options.selectedYear, 1, 1) to LocalDate.of(options.selectedYear, 12, 31)
        else -> options.startDate to options.endDate
    }

    fun dates(options: PdfExportOptions): List<LocalDate> {
        val (start, end) = range(options)
        require(!end.isBefore(start)) { "La data finale precede quella iniziale" }
        require(start.year in 1900..2200 && end.year in 1900..2200) { "Scegli un anno tra 1900 e 2200" }
        require(ChronoUnit.DAYS.between(start, end) < 3660) { "Esporta al massimo 10 anni alla volta" }
        return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    }

    fun months(options: PdfExportOptions): List<YearMonth> = dates(options).map(YearMonth::from).distinct()

    fun validateForExport(options: PdfExportOptions): List<LocalDate> {
        val allDates = dates(options)
        if (presentation(options) == PdfPresentation.OVERVIEW) {
            val monthCount = allDates.map(YearMonth::from).distinct().size
            require(monthCount <= MAX_OVERVIEW_MONTHS) { OVERVIEW_MAX_MONTHS_ERROR }
        }
        return allDates
    }
}
