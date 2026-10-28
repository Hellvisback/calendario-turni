package com.turni.calendario

import com.turni.calendario.sync.CalendarImportDocument
import com.turni.calendario.sync.SuperShiftCalendarAdapter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.ZoneId

class CalendarImportDocumentTest {
    private val calendar = "\uFEFFBEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:qa\r\nSUMMARY:Turno personalizzato è\r\nDTSTART;VALUE=DATE:20261027\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"

    private fun read(text: String) = CalendarImportDocument.read(text.byteInputStream())
    private fun rejected(bytes: ByteArray, message: String) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            CalendarImportDocument.read(ByteArrayInputStream(bytes))
        }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(message))
    }

    @Test fun utf8BomNamesAndDatesSurviveTheDocumentBoundary() {
        val text = read(calendar)
        assertEquals(calendar, text)
        assertEquals("Turno personalizzato è", SuperShiftCalendarAdapter.readCalendar(text, ZoneId.of("Europe/Rome")).single().name)
    }

    @Test fun androidBackupIsNotMistakenForAnImportableExport() {
        rejected("ANDROID BACKUP\n5\n1\nnone\n".toByteArray(), "backup Android")
    }

    @Test fun sqliteAndZipCopiesRequireAValidatedConverter() {
        rejected("SQLite format 3\u0000binary".toByteArray(), "database o un archivio")
        listOf("PK\u0003\u0004", "PK\u0005\u0006", "PK\u0007\u0008").forEach {
            rejected(it.toByteArray(), "database o un archivio")
        }
    }

    @Test fun undocumentedJsonIsNeverGuessedOrPartiallyImported() {
        listOf("{\"shifts\":[]}", "\uFEFF  [1,2]").forEach { rejected(it.toByteArray(), "JSON non supportato") }
    }

    @Test fun emptyUnrelatedAndInvalidUtf8DocumentsHaveReadableErrors() {
        rejected(byteArrayOf(), "vuoto")
        rejected("unrelated document".toByteArray(), "File non riconosciuto")
        rejected(byteArrayOf(0xc3.toByte(), 0x28), "UTF-8")
    }

    @Test fun aDocumentWithALegitimateHeaderStillNeedsFullCalendarValidation() {
        val text = read("BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:x")
        assertThrows(IllegalArgumentException::class.java) { SuperShiftCalendarAdapter.readCalendar(text) }
    }

    @Test fun sizeLimitIsEnforcedWhileStreamingEvenIfTheHeaderLooksValid() {
        val stream = object : InputStream() {
            var remaining = CalendarImportDocument.MAX_BYTES + 1
            override fun read() = if (remaining-- > 0) 65 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(remaining, length)
                bytes.fill(65, offset, offset + count)
                remaining -= count
                return count
            }
        }
        val error = assertThrows(IllegalArgumentException::class.java) { CalendarImportDocument.read(stream) }
        assertTrue(error.message.orEmpty().contains("16 MB"))
    }

    @Test fun streamIsNotClosedByTheReaderAndZeroLengthReadsDoNotHang() {
        var closed = false
        val stream = object : ByteArrayInputStream(calendar.toByteArray()) {
            var first = true
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (first) { first = false; return 0 }
                return super.read(bytes, offset, length)
            }
            override fun close() { closed = true; super.close() }
        }
        assertEquals(calendar, CalendarImportDocument.read(stream))
        assertFalse(closed)
    }
}
