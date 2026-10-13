package com.turni.calendario

import com.turni.calendario.importer.NgeniaTurniParser
import com.turni.calendario.repository.ShiftRepository
import com.turni.calendario.ui.screens.RFI_EXAMPLE_TABELLONE
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class NgeniaTurniParserTest {

    @Before
    fun setUp() {
        // Reset stato repository
        ShiftRepository.defaultRotationId = "turno_a"
    }

    @Test
    fun testParseValidityDates() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)
        assertNotNull(result.validityStart)
        assertNotNull(result.validityEnd)
        assertEquals(LocalDate.of(2026, 9, 21), result.validityStart)
        assertEquals(LocalDate.of(2026, 10, 18), result.validityEnd)
    }

    @Test
    fun testParseHoursSchedule() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)
        assertTrue(result.detectedHours.containsKey("M"))
        assertTrue(result.detectedHours.containsKey("P"))
        assertTrue(result.detectedHours.containsKey("N"))
        assertTrue(result.detectedHours.containsKey("MD"))

        assertEquals(Pair("06:00", "13:36"), result.detectedHours["M"])
        assertEquals(Pair("14:00", "21:36"), result.detectedHours["P"])
        assertEquals(Pair("22:00", "05:36"), result.detectedHours["N"])
        assertEquals(Pair("08:36", "16:12"), result.detectedHours["MD"])
    }

    @Test
    fun testParseAllTeams() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)
        assertEquals(5, result.teams.size)

        val teamNames = result.teams.map { it.teamName }
        assertTrue(teamNames.contains("Squadra A"))
        assertTrue(teamNames.contains("Squadra B"))
        assertTrue(teamNames.contains("Squadra C"))
        assertTrue(teamNames.contains("Squadra D"))
        assertTrue(teamNames.contains("Squadra B 1"))

        for (team in result.teams) {
            assertEquals(28, team.totalDays)
            assertEquals(28, team.dayShiftDefIds.size)
        }
    }

    @Test
    fun testParseEmployees() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)
        assertTrue(result.employees.isNotEmpty())

        val marcozzi = result.employees.find { it.name.contains("Marcozzi", ignoreCase = true) }
        assertNotNull(marcozzi)
        assertEquals("Squadra B", marcozzi?.teamName)
        assertEquals("8024724", marcozzi?.phone)

        val raglione = result.employees.find { it.name.contains("Raglione", ignoreCase = true) }
        assertNotNull(raglione)
    }

    @Test
    fun testImportWithoutDuplicates() {
        val initialDefinitionsCount = ShiftRepository.shiftDefinitions.size
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)

        ShiftRepository.ensureShiftDefinitionsFromParsedHours(result.detectedHours)

        // I turni esistenti M, P, N, MD devono essere stati aggiornati, non duplicati
        val mattinaDefs = ShiftRepository.shiftDefinitions.filter { it.id == "mattina" }
        assertEquals(1, mattinaDefs.size)
        assertEquals("06:00", mattinaDefs.first().startTime)
        assertEquals("13:36", mattinaDefs.first().endTime)

        val pomeriggioDefs = ShiftRepository.shiftDefinitions.filter { it.id == "pomeriggio" }
        assertEquals(1, pomeriggioDefs.size)
        assertEquals("14:00", pomeriggioDefs.first().startTime)
        assertEquals("21:36", pomeriggioDefs.first().endTime)

        // Il conteggio complessivo non deve essere esploso con duplicati
        assertTrue(ShiftRepository.shiftDefinitions.size <= initialDefinitionsCount + 2)
    }

    @Test
    fun testDefaultRotationSortsFirst() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)

        // Importiamo e impostiamo Squadra C come predefinita
        ShiftRepository.importNgeniaTurniResult(
            result = result,
            selectedTeamName = "Squadra C",
            makeDefault = true,
            applyToCalendar = true
        )

        assertEquals("rotazione_squadra_c", ShiftRepository.defaultRotationId)

        val sorted = ShiftRepository.getRotationsSortedByDefault()
        assertFalse(sorted.isEmpty())
        assertEquals("rotazione_squadra_c", sorted.first().id)
        assertEquals("Squadra C", sorted.first().name)
    }

    @Test
    fun testTextImportSanitizerCleansBinaryAndInvalidChars() {
        val dirtyText = "%PDF-1.5\nstream\n\u0000\u0001\u0002Marco Rossi\u0000\uFFFD 8024724 P P Rc N N\nendstream\nendobj"
        val cleaned = com.turni.calendario.importer.TextImportSanitizer.sanitize(dirtyText)

        assertFalse("Cleaned text must not contain null bytes", cleaned.contains("\u0000"))
        assertFalse("Cleaned text must not contain replacement char", cleaned.contains("\uFFFD"))
        assertFalse("Cleaned text must not contain %PDF-", cleaned.contains("%PDF-"))
        assertFalse("Cleaned text must not contain raw stream markers", cleaned.contains("endstream"))
        assertTrue("Cleaned text must retain readable employee line", cleaned.contains("Marco Rossi") && cleaned.contains("8024724"))
    }

    @Test
    fun testFlexibleMultiFormatTableParsing() {
        val genericTable = """
            Periodo: 01/11/2026 - 28/11/2026
            Orari:
            MAT: 06:00 - 14:00
            POM: 14:00 - 22:00
            NOT: 22:00 - 06:00
            
            Squadra Ospedaliera
            01/11 02/11 03/11 04/11 05/11 06/11 07/11
            Bianchi Luca, Infermiere, MAT, MAT, POM, POM, NOT, SMO, RIP
            Verdi Maria, Medico, POM, POM, NOT, SMO, RIP, MAT, MAT
        """.trimIndent()

        val result = NgeniaTurniParser.parseDocumentText(genericTable)
        assertNotNull(result.validityStart)
        assertEquals(LocalDate.of(2026, 11, 1), result.validityStart)
        assertEquals(LocalDate.of(2026, 11, 28), result.validityEnd)

        assertTrue(result.detectedHours.containsKey("MAT"))
        assertTrue(result.detectedHours.containsKey("POM"))
        assertTrue(result.detectedHours.containsKey("NOT"))

        val bianchi = result.employees.find { it.name.contains("Bianchi", ignoreCase = true) }
        assertNotNull(bianchi)
        assertEquals("mattina", NgeniaTurniParser.mapCodeToShiftDefId(bianchi!!.rawShiftSequence[0]))
        assertEquals("pomeriggio", NgeniaTurniParser.mapCodeToShiftDefId(bianchi.rawShiftSequence[2]))
        assertEquals("notte", NgeniaTurniParser.mapCodeToShiftDefId(bianchi.rawShiftSequence[4]))
        assertEquals("smonto_notte", NgeniaTurniParser.mapCodeToShiftDefId(bianchi.rawShiftSequence[5]))
        assertEquals("riposo", NgeniaTurniParser.mapCodeToShiftDefId(bianchi.rawShiftSequence[6]))
    }

    @Test
    fun testToggleDefaultRotationSetsAndRevokes() {
        ShiftRepository.defaultRotationId = ""

        // Primo tocco: imposta come predefinito
        val result1 = ShiftRepository.toggleDefaultRotation("rotazione_squadra_b")
        assertTrue(result1)
        assertEquals("rotazione_squadra_b", ShiftRepository.defaultRotationId)

        // Secondo tocco sulla stessa squadra: revoca tornando a stato neutro
        val result2 = ShiftRepository.toggleDefaultRotation("rotazione_squadra_b")
        assertFalse(result2)
        assertEquals("", ShiftRepository.defaultRotationId)

        // Tocco su altra squadra: imposta quella nuova
        val result3 = ShiftRepository.toggleDefaultRotation("rotazione_squadra_c")
        assertTrue(result3)
        assertEquals("rotazione_squadra_c", ShiftRepository.defaultRotationId)
    }

    @Test
    fun testTextImportSanitizerPdfStreamFlateDecodeExtraction() {
        // Genera uno stream zlib compresso con comandi di testo PDF
        val uncompressedPdfCommands = "BT /F1 12 Tf (Mario Rossi 8024724 P P Rc N N) Tj ET"
        val deflater = java.util.zip.Deflater()
        deflater.setInput(uncompressedPdfCommands.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val buffer = ByteArray(256)
        val compressedLength = deflater.deflate(buffer)
        deflater.end()

        val compressedBytes = buffer.copyOf(compressedLength)
        val header = "%PDF-1.4\n1 0 obj\n<< /Filter /FlateDecode /Length $compressedLength >>\nstream\n".toByteArray(Charsets.ISO_8859_1)
        val trailer = "\nendstream\nendobj\nxref\n0 2\n0000000000 65535 f\n0000000010 00000 n\ntrailer\n<< /Size 2 /Root 1 0 R >>\nstartxref\n120\n%%EOF".toByteArray(Charsets.ISO_8859_1)

        val fullPdfBytes = header + compressedBytes + trailer
        val extracted = com.turni.calendario.importer.TextImportSanitizer.extractAndCleanBytes(fullPdfBytes)

        assertTrue("Extracted text must contain employee info from decompressed stream", extracted.contains("Mario Rossi") && extracted.contains("8024724"))
        assertFalse("Extracted text must not contain PDF structural trailer", extracted.contains("trailer"))
        assertFalse("Extracted text must not contain xref", extracted.contains("xref"))
        assertFalse("Extracted text must not contain startxref", extracted.contains("startxref"))
        assertFalse("Extracted text must not contain raw FlateDecode tag", extracted.contains("FlateDecode"))
    }

    @Test
    fun testTextImportSanitizerRejectsHighDensityBinaryFiles() {
        // Crea un array di byte ad alta densità di byte non stampabili / binari
        val binaryData = ByteArray(100) { idx -> (idx % 256).toByte() }
        val cleaned = com.turni.calendario.importer.TextImportSanitizer.extractAndCleanBytes(binaryData)
        assertEquals("High-density binary byte arrays must produce empty text", "", cleaned)
    }

    @Test
    fun testCsvTableIgnoresHeaderRows() {
        val csv = """
            Nome,Ruolo,01/10,02/10,03/10,04/10
            Rossi Mario,Operatore,M,P,N,RIP
            Verdi Luigi,Tecnico,P,N,RIP,M
        """.trimIndent()

        val result = NgeniaTurniParser.parseDocumentText(csv)
        assertEquals(2, result.employees.size)
        assertFalse("Header row must not be parsed as an employee", result.employees.any { it.name.equals("Nome", ignoreCase = true) })
        assertTrue("Real employee Rossi Mario must be present", result.employees.any { it.name.contains("Rossi", ignoreCase = true) })
        assertTrue("Real employee Verdi Luigi must be present", result.employees.any { it.name.contains("Verdi", ignoreCase = true) })
    }

    @Test
    fun testNonRfiScheduleDoesNotInjectRfiSpecificTeams() {
        val nonRfiText = """
            Periodo: 01/11/2026 - 28/11/2026
            Squadra Alfa
            01/11 02/11 03/11 04/11
            Rossi Mario 8011223 M P N RIP
            
            Squadra Beta
            01/11 02/11 03/11 04/11
            Bianchi Carlo 8022334 P N RIP M
        """.trimIndent()

        val result = NgeniaTurniParser.parseDocumentText(nonRfiText)
        val teamNames = result.teams.map { it.teamName }
        assertTrue("Should contain Squadra Alfa", teamNames.contains("Squadra Alfa"))
        assertTrue("Should contain Squadra Beta", teamNames.contains("Squadra Beta"))
        assertFalse("Should NOT inject RFI specific Squadra B 1", teamNames.contains("Squadra B 1"))
        assertFalse("Should NOT inject RFI specific Squadra C", teamNames.contains("Squadra C"))
        assertFalse("Should NOT inject RFI specific Squadra D", teamNames.contains("Squadra D"))
    }

    @Test
    fun testColumnDatesAreConsecutiveAndAccurateForRfiTabellone() {
        val result = NgeniaTurniParser.parseDocumentText(RFI_EXAMPLE_TABELLONE)
        val marcozzi = result.employees.find { it.name.contains("Marcozzi", ignoreCase = true) }
        assertNotNull("Marcozzi must be found", marcozzi)

        // Verifica che le date siano consecutive e non corrotte dall'header "dal 21/09/2026 al 18/10/2026"
        // Marcozzi turno sequenza: P P Rc N N M Ri Rc N N TGF Rc Ri TGF M M Ri Rc TGF Ri
        val shifts = marcozzi!!.dayShifts
        assertEquals("Day 1 (21/09) must be P", "P", shifts[LocalDate.of(2026, 9, 21)])
        assertEquals("Day 2 (22/09) must be P", "P", shifts[LocalDate.of(2026, 9, 22)])
        assertEquals("Day 3 (23/09) must be RC", "RC", shifts[LocalDate.of(2026, 9, 23)])
        assertEquals("Day 4 (24/09) must be N", "N", shifts[LocalDate.of(2026, 9, 24)])
        assertEquals("Day 5 (25/09) must be N", "N", shifts[LocalDate.of(2026, 9, 25)])
        assertEquals("Day 6 (26/09) must be M", "M", shifts[LocalDate.of(2026, 9, 26)])
        assertEquals("Day 7 (27/09) must be RI", "RI", shifts[LocalDate.of(2026, 9, 27)])

        // 21/09 non deve essere sovrascritto
        assertNotEquals("Day 1 (21/09) must NOT be overwritten by RC from day 3", "RC", shifts[LocalDate.of(2026, 9, 21)])
    }

    @Test
    fun testYearInDateNotTreatedAsMatricola() {
        val tableWithDates = """
            01/10/2026, Rossi Mario, M, P, N, RIP, M, P, N
            02/10/2026, Verdi Luigi, P, N, RIP, M, P, N, RIP
        """.trimIndent()

        val result = NgeniaTurniParser.parseDocumentText(tableWithDates)
        assertTrue("Should parse employees", result.employees.isNotEmpty())
        for (emp in result.employees) {
            assertFalse("Year 2026 must NOT be parsed as phone/matricola", emp.phone == "2026")
            assertFalse("Name must not contain date fragment", emp.name.contains("01/10") || emp.name.contains("02/10"))
        }
    }

    @Test
    fun testSanitizerPreservesLegitimateTextWithSlashes() {
        val textWithSlashes = """
            Reparto / Ospedale Centrale
            Squadra A / Notte
            Mario Rossi 8024724 P P Rc N N M Ri
        """.trimIndent()

        val cleaned = com.turni.calendario.importer.TextImportSanitizer.sanitize(textWithSlashes)
        assertTrue("Legitimate text with slashes must not be dropped", cleaned.contains("Reparto / Ospedale Centrale"))
        assertTrue("Legitimate team header with slashes must not be dropped", cleaned.contains("Squadra A / Notte"))
    }

    @Test
    fun testMapCodeToShiftDefIdRecognizesTrasferta() {
        assertEquals("giornaliero", NgeniaTurniParser.mapCodeToShiftDefId("T"))
        assertEquals("giornaliero", NgeniaTurniParser.mapCodeToShiftDefId("TRASF"))
        assertNotEquals("riposo", NgeniaTurniParser.mapCodeToShiftDefId("T"))
    }

    @Test
    fun testPdfTextPositioningOperatorsPreserveRowSeparation() {
        val pdfCommands = """
            BT
            0 -14 Td
            (Mario Rossi 8024724 P P Rc N N) Tj
            T*
            (Luigi Verdi 8024725 M M Ri Rc N) Tj
            0 -14 Td
            (Carlo Bianchi 8024726 P P N N Ri) Tj
            ET
        """.trimIndent()

        val deflater = java.util.zip.Deflater()
        deflater.setInput(pdfCommands.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val buffer = ByteArray(512)
        val compressedLength = deflater.deflate(buffer)
        deflater.end()

        val compressedBytes = buffer.copyOf(compressedLength)
        val header = "%PDF-1.4\n1 0 obj\n<< /Filter /FlateDecode /Length $compressedLength >>\nstream\n".toByteArray(Charsets.ISO_8859_1)
        val trailer = "\nendstream\nendobj\ntrailer\n<< /Size 2 /Root 1 0 R >>\n%%EOF".toByteArray(Charsets.ISO_8859_1)
        val fullPdfBytes = header + compressedBytes + trailer

        val extracted = com.turni.calendario.importer.TextImportSanitizer.extractAndCleanBytes(fullPdfBytes)
        val lines = extracted.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // Ogni riga deve essere separata e non fusa in una singola riga
        assertEquals("Three separate employee lines must be extracted", 3, lines.size)
        assertTrue("Line 1 contains Mario Rossi", lines[0].contains("Mario Rossi"))
        assertTrue("Line 2 contains Luigi Verdi", lines[1].contains("Luigi Verdi"))
        assertTrue("Line 3 contains Carlo Bianchi", lines[2].contains("Carlo Bianchi"))
    }

    @Test
    fun testMatchesEmployeeQueryFlexibleNameOrderingAndDelimiters() {
        val emp = com.turni.calendario.importer.ParsedEmployee(
            name = "Marcozzi Loris",
            phone = "8024724",
            teamName = "Squadra B"
        )

        // Ricerca esatta o parziale
        assertTrue(NgeniaTurniParser.matchesEmployeeQuery(emp, "Marcozzi"))
        assertTrue(NgeniaTurniParser.matchesEmployeeQuery(emp, "Loris"))
        // Ricerca con inversione Nome Cognome -> Cognome Nome
        assertTrue("Should match reversed name order", NgeniaTurniParser.matchesEmployeeQuery(emp, "Loris Marcozzi"))
        // Ricerca con punteggiatura
        assertTrue("Should match with comma", NgeniaTurniParser.matchesEmployeeQuery(emp, "Marcozzi, Loris"))
        // Ricerca per matricola
        assertTrue("Should match phone/matricola", NgeniaTurniParser.matchesEmployeeQuery(emp, "8024724"))
        // Ricerca per squadra
        assertTrue("Should match team", NgeniaTurniParser.matchesEmployeeQuery(emp, "Squadra B"))

        // Caso con underscore
        val emp2 = com.turni.calendario.importer.ParsedEmployee(
            name = "Di Camillo Gianluca",
            phone = "8043762",
            teamName = "Squadra B"
        )
        assertTrue("Should match with underscore query", NgeniaTurniParser.matchesEmployeeQuery(emp2, "Di_Camillo"))
        assertTrue("Should match reversed order", NgeniaTurniParser.matchesEmployeeQuery(emp2, "Gianluca Di Camillo"))

        // Query non corrispondente
        assertFalse("Should NOT match unrelated query", NgeniaTurniParser.matchesEmployeeQuery(emp, "Rossi Mario"))
    }

    @Test
    fun testMultiWordNamesPreservedWithoutRole() {
        val text = """
            21/09/2026 - 18/10/2026
            Squadra Unica
            21/9 22/9 23/9 24/9 25/9 26/9 27/9
            Maria De Angelis Sì X 8099881 M P N RIP M P N
            Rossi Mario Antonio No 8099882 P N RIP M P N RIP
        """.trimIndent()

        val result = NgeniaTurniParser.parseDocumentText(text)
        val maria = result.employees.find { it.phone == "8099881" }
        assertNotNull(maria)
        assertEquals("Maria De Angelis", maria?.name)

        val rossi = result.employees.find { it.phone == "8099882" }
        assertNotNull(rossi)
        assertEquals("Rossi Mario Antonio", rossi?.name)
    }

    @Test
    fun testFullWordShiftCodesMapping() {
        assertEquals("mattina", NgeniaTurniParser.mapCodeToShiftDefId("MATTINA"))
        assertEquals("pomeriggio", NgeniaTurniParser.mapCodeToShiftDefId("POMERIGGIO"))
        assertEquals("notte", NgeniaTurniParser.mapCodeToShiftDefId("NOTTE"))
        assertEquals("smonto_notte", NgeniaTurniParser.mapCodeToShiftDefId("SMONTO"))
        assertEquals("riposo", NgeniaTurniParser.mapCodeToShiftDefId("RIPOSO"))
        assertEquals("ferie", NgeniaTurniParser.mapCodeToShiftDefId("FERIE"))
        assertEquals("permesso", NgeniaTurniParser.mapCodeToShiftDefId("PERMESSO"))
        assertEquals("malattia", NgeniaTurniParser.mapCodeToShiftDefId("MALATTIA"))
        assertEquals("formazione", NgeniaTurniParser.mapCodeToShiftDefId("FORMAZIONE"))
        assertEquals("giornaliero", NgeniaTurniParser.mapCodeToShiftDefId("GIORNALIERO"))

        val tableFullWords = """
            Squadra A
            Mario Rossi 8011223 MATTINA POMERIGGIO NOTTE SMONTO RIPOSO
        """.trimIndent()
        val result = NgeniaTurniParser.parseDocumentText(tableFullWords)
        val emp = result.employees.firstOrNull()
        assertNotNull(emp)
        assertEquals(5, emp?.rawShiftSequence?.size)
        assertEquals(listOf("MATTINA", "POMERIGGIO", "NOTTE", "SMONTO", "RIPOSO"), emp?.rawShiftSequence)
    }

    @Test
    fun testFallbackDatesWhenNoHeaderProvided() {
        val undatedText = """
            Squadra Alpha
            Luca Bianchi 8011999 M P N RIP M P N
        """.trimIndent()

        val result = NgeniaTurniParser.parseDocumentText(undatedText)
        val emp = result.employees.firstOrNull()
        assertNotNull(emp)
        // Deve aver generato date di fallback a partire da oggi, quindi dayShifts non è vuoto
        assertFalse("dayShifts must not be empty even when document has no explicit dates", emp!!.dayShifts.isEmpty())
        assertEquals(7, emp.dayShifts.size)
        assertTrue(emp.dayShifts.containsKey(LocalDate.now()))
    }
}
