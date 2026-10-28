package com.turni.calendario.sync

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Reads a user-selected interchange file. Never opens another app's private storage. */
object CalendarImportDocument {
    const val MAX_BYTES = 16 * 1024 * 1024

    /** Caller owns the stream. Classification is based on contents, never on a filename. */
    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val next = input.read()
                if (next < 0) break
                require(output.size() < MAX_BYTES) { "Il file supera 16 MB. Nessun dato importato." }
                output.write(next)
            } else {
                require(output.size() <= MAX_BYTES - count) { "Il file supera 16 MB. Nessun dato importato." }
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        require(bytes.isNotEmpty()) { "Il file è vuoto. Nessun dato importato." }
        fun startsWith(signature: String) = bytes.size >= signature.length &&
            signature.indices.all { bytes[it] == signature[it].code.toByte() }
        require(!startsWith("ANDROID BACKUP\n")) {
            "È un backup Android: qui è richiesto un calendario .ics. Nessun dato importato."
        }
        require(!startsWith("SQLite format 3\u0000") && !startsWith("PK\u0003\u0004") &&
            !startsWith("PK\u0005\u0006") && !startsWith("PK\u0007\u0008")) {
            "Il file è un database o un archivio: serve un convertitore verificato. Nessun dato importato."
        }
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw IllegalArgumentException("Il file non è un calendario .ics in formato UTF-8. Nessun dato importato.")
        }
        val content = text.removePrefix("\uFEFF")
        require(content.lineSequence().firstOrNull()?.trim() == "BEGIN:VCALENDAR") {
            if (content.trimStart().startsWith("{") || content.trimStart().startsWith("["))
                "File JSON non supportato: serve un convertitore verificato. Nessun dato importato."
            else "File non riconosciuto: seleziona un calendario .ics. Nessun dato importato."
        }
        return text
    }
}
