package com.turni.calendario
import com.turni.calendario.data.*
import com.turni.calendario.repository.*
import com.turni.calendario.sync.DatabaseRestore
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class AiRosterImportTest {
    private fun fixture()=javaClass.getResourceAsStream("/ai-roster-umis-reference.json")!!.bufferedReader().use{it.readText()}
    private fun draft()=AiRosterImport.parse(fixture(),"Alex Esempio",ShiftRepository.activeWorkProfileId)
    @Test fun referenceDocumentPreservesColumnsAndNameMatch(){
        val d=draft();val cycle=d.cycles.single();assertEquals(cycle.rotation.id,d.suggestedCycleId)
        assertEquals(28,cycle.rotation.daySequence.size);assertEquals("observed",cycle.basis)
        val defs=d.shifts.associateBy{it.definition.id}
        assertEquals(listOf("RGLS","N","N","RISE","M","P","MD"),cycle.rotation.daySequence.take(7).map{defs.getValue(it).sourceCode})
        assertEquals("05:36",d.shifts.first{it.sourceCode=="N"}.definition.endTime)
    }
    @Test fun missingHoursNeverBecomeTypicalMorningHours(){
        val d=draft();val t=d.shifts.first{it.sourceCode=="TGF"};assertTrue(t.needsReview);assertEquals("00:00",t.definition.startTime)
        assertTrue(AiRosterImport.validate(d,null,false).isNotEmpty())
    }
    @Test fun duplicateSourceCodesFailInsteadOfMerging(){
        val obj=JSONObject(fixture());val a=obj.getJSONArray("shiftDefinitions");a.put(a.getJSONObject(0))
        assertTrue(runCatching{AiRosterImport.parse(obj.toString(),"Alex","work")}.isFailure)
    }
    @Test fun unknownDayIsRetainedAtItsOriginalPosition(){
        val obj=JSONObject(fixture());obj.getJSONArray("squads").getJSONObject(0).getJSONArray("rotationDays").put(3,"UNKNOWN")
        val d=AiRosterImport.parse(obj.toString(),"Alex","work");assertEquals(28,d.cycles.single().rotation.daySequence.size);assertEquals("",d.cycles.single().rotation.daySequence[3])
        assertTrue(AiRosterImport.validate(d.copy(shifts=d.shifts.filterNot{it.needsReview}),d.suggestedCycleId,true).isNotEmpty())
    }
    @Test fun ambiguousWorkerDoesNotDefaultToFirstSquad(){
        val obj=JSONObject(fixture());val squad=JSONObject(obj.getJSONArray("squads").getJSONObject(0).toString()).put("squadName","Other");obj.getJSONArray("squads").put(squad)
        val d=AiRosterImport.parse(obj.toString(),"Alex","work");assertNull(d.suggestedCycleId)
    }
    @Test fun unmatchedWorkerDoesNotSelectAnyCycle(){assertNull(AiRosterImport.parse(fixture(),"Persona diversa","work").suggestedCycleId)}
    @Test fun urlsAndBracesInsideNotesArePreserved(){
        val obj=JSONObject(fixture()).put("notes","https://example.com/{roster} // literal")
        val d=AiRosterImport.parse("Risposta: ```json\n$obj\n```","Alex","work")
        assertTrue(d.warnings.contains("https://example.com/{roster} // literal"))
    }
    @Test fun truncatedAndOversizedResultsAreRejected(){
        assertTrue(runCatching{AiRosterImport.parse(fixture().dropLast(4),"Alex","work")}.isFailure)
        assertTrue(runCatching{AiRosterImport.jsonPayload(" ".repeat(2_000_001))}.isFailure)
    }
    @Test fun cycleNeedsExplicitUserConfirmation(){
        val d=draft().let{it.copy(shifts=it.shifts.filterNot{row->row.needsReview})}
        assertTrue(AiRosterImport.validate(d,d.suggestedCycleId,false).isNotEmpty())
        assertTrue(AiRosterImport.validate(d,d.suggestedCycleId,true).isEmpty())
    }
    @Test fun invalidCategoryRequiresReview(){
        val obj=JSONObject(fixture());obj.getJSONArray("shiftDefinitions").getJSONObject(0).put("category","invented")
        assertTrue(AiRosterImport.parse(obj.toString(),"Alex","work").shifts.first().needsReview)
    }
    @Test fun draftCreationDoesNotChangeRepository(){val before=DatabaseSnapshot.capture();draft();assertEquals(before,DatabaseSnapshot.capture())}
    @Test fun additiveSavePreservesExistingCalendarAndIsIdempotent(){
        val before=DatabaseSnapshot.capture()
        try{
            val d=draft().let{it.copy(shifts=it.shifts.filterNot{row->row.needsReview})}
            val saved=AiRosterImport.save(d,d.suggestedCycleId,true,false)
            assertTrue(saved.shifts>=0);assertEquals(before.shifts,DatabaseSnapshot.capture().shifts)
            before.definitions.forEach{existing->assertEquals(existing,ShiftRepository.getShiftDefinition(existing.id))}
            val first=DatabaseSnapshot.capture();val repeated=AiRosterImport.save(d,d.suggestedCycleId,true,false)
            assertEquals(0,repeated.shifts);assertEquals(first,DatabaseSnapshot.capture())
        }finally{DatabaseRestore.apply(before)}
    }
    @Test fun invalidSaveHasNoSideEffects(){val before=DatabaseSnapshot.capture();val d=draft();assertTrue(runCatching{AiRosterImport.save(d,d.suggestedCycleId,true,false)}.isFailure);assertEquals(before,DatabaseSnapshot.capture())}
    @Test fun differentWorkProfileCannotReceiveStaleDraft(){val before=DatabaseSnapshot.capture();val d=draft().copy(profileId="other");assertTrue(runCatching{AiRosterImport.save(d,null,false,false)}.isFailure);assertEquals(before,DatabaseSnapshot.capture())}
    @Test fun fullPromptIsSectorNeutralAndDoesNotTeachInventedHours(){
        val prompt=AiRosterImport.prompt("Example")
        assertTrue(prompt.contains("TUTTE le pagine"));assertTrue(prompt.contains("Non dichiarare dimostrato"));assertTrue(prompt.contains("non dedurre gli orari"));assertTrue(prompt.contains("Example"))
    }
    @Test fun fileReaderRejectsEmptyOrOversizedData(){
        assertTrue(runCatching{AiRosterDocuments.readBounded(ByteArrayInputStream(byteArrayOf()))}.isFailure)
        assertTrue(runCatching{AiRosterDocuments.readBounded(ByteArrayInputStream(ByteArray(AiRosterDocuments.MAX_BYTES+1)))}.isFailure)
        assertArrayEquals(byteArrayOf(1,2,3),AiRosterDocuments.readBounded(ByteArrayInputStream(byteArrayOf(1,2,3))))
    }
}
