package com.turni.calendario.sync

import com.turni.calendario.model.*
import com.turni.calendario.repository.DatabaseSnapshot
import org.json.JSONObject
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Interchange data only: no guesses about SuperShift's private database or terminology. */
data class ImportCalendar(val id:String,val name:String,val zone:String,val color:String="")
data class ImportShiftEvent(val uid:String,val date:LocalDate,val name:String,val start:String?,val end:String?,val notes:String="",val color:Long?=null) {
    val allDay get()=start==null
}
data class ShiftImportPlan(val definitions:List<ShiftDefinition>,val assignments:List<AssignedShift>,val duplicateCount:Int,val sourceCount:Int)
data class ShiftImportResult(val snapshot:DatabaseSnapshot,val importedCount:Int,val duplicateCount:Int)

object SuperShiftCalendarAdapter {
    const val PROFILE_ID="supershift-import"
    val limitation="Il calendario trasferisce nomi, date, orari e note disponibili. Non contiene il backup completo: rotazioni, pause, reperibilità e turni originali non vengono ricostruiti. Controlla le categorie dei modelli prima di importare."
    private val dateTime=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss")
    private val dateTimeShort=DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmm")
    private fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}.take(32)
    private fun eventId(event:ImportShiftEvent)="ss-event-"+hash(event.uid)
    private fun modelId(event:ImportShiftEvent)="ss-model-"+hash(event.name+"/"+event.allDay)
    private fun parseColor(value:String):Long?=value.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.drop(1)?.toLong(16)?.or(0xFF000000)
    private fun notes(description:String,location:String)=listOf(description,location.takeIf { it.isNotBlank() }?.let { "Luogo: $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")

    fun googleEvent(event:JSONObject,calendar:ImportCalendar):List<ImportShiftEvent> {
        val uid=event.optString("iCalUID").ifBlank { calendar.id+"/"+event.getString("id") }
        val name=event.optString("summary").trim().also { require(it.isNotBlank()) { "Un evento senza titolo non può diventare un turno." } }
        val start=event.getJSONObject("start");val end=event.getJSONObject("end")
        val zone=ZoneId.of(calendar.zone)
        val description=notes(event.optString("description"),event.optString("location"))
        // Expanded recurring instances share iCalUID. Original start distinguishes moved occurrences too.
        val instance=event.optJSONObject("originalStartTime")?.let { original -> if(original.has("dateTime"))OffsetDateTime.parse(original.getString("dateTime")).toInstant().toString() else LocalDate.parse(original.getString("date")).toString() }.orEmpty()
        val identity=uid+if(instance.isBlank())"" else "/"+instance
        if(start.has("date"))return allDayEvents(identity,name,LocalDate.parse(start.getString("date")),LocalDate.parse(end.getString("date")),description,parseColor(calendar.color))
        val begin=OffsetDateTime.parse(start.getString("dateTime")).atZoneSameInstant(zone)
        val finish=OffsetDateTime.parse(end.getString("dateTime")).atZoneSameInstant(zone)
        return listOf(timedEvent(identity,name,begin,finish,description,parseColor(calendar.color)))
    }

    private fun allDayEvents(uid:String,name:String,start:LocalDate,end:LocalDate,notes:String,color:Long?):List<ImportShiftEvent> {
        val count=java.time.temporal.ChronoUnit.DAYS.between(start,end)
        require(count in 1..3660) { "Evento giornaliero con intervallo non valido o troppo lungo." }
        return (0 until count).map { ImportShiftEvent(uid+"/day/"+start.plusDays(it),start.plusDays(it),name,null,null,notes,color) }
    }
    private fun timedEvent(uid:String,name:String,start:ZonedDateTime,end:ZonedDateTime,notes:String,color:Long?):ImportShiftEvent {
        val wallMinutes=java.time.Duration.between(start.toLocalDateTime(),end.toLocalDateTime()).toMinutes()
        require(end.toInstant()>start.toInstant() && wallMinutes in 1..1440) { "Il turno '$name' dura più di 24 ore o ha orari non validi. Nessun dato importato." }
        require(start.second==0&&end.second==0&&start.nano==0&&end.nano==0) { "Il turno '$name' usa secondi: il calendario supporta la precisione al minuto." }
        return ImportShiftEvent(uid,start.toLocalDate(),name,start.toLocalTime().toString(),end.toLocalTime().toString(),notes,color)
    }

    /** Strict subset of RFC5545. Recurrence is expanded by Google, never silently dropped here. */
    fun readCalendar(text:String,defaultZone:ZoneId=ZoneId.systemDefault()):List<ImportShiftEvent> {
        require(text.length<=16*1024*1024) { "Il file supera 16 MB." }
        val unfolded=text.removePrefix("\uFEFF").replace("\r\n","\n").replace(Regex("\n[ \t]"),"")
        require(unfolded.lineSequence().firstOrNull()?.trim()=="BEGIN:VCALENDAR") { "File non riconosciuto. Seleziona un calendario .ics; il backup privato SuperShift non è un calendario esportabile." }
        val blocks=mutableListOf<List<Pair<String,String>>>()
        var current:MutableList<Pair<String,String>>?=null
        var zone=defaultZone
        var nested=0
        var closed=false
        for(raw in unfolded.lineSequence()) {
            val line=raw.trimEnd('\r');val split=line.indexOf(':');if(split<0)continue
            val key=line.substring(0,split);val value=line.substring(split+1)
            when {
                key=="BEGIN"&&value=="VEVENT" -> {require(current==null){"Calendario danneggiato."};current=mutableListOf();nested=0}
                key=="END"&&value=="VEVENT" -> {blocks+=current?:error("Calendario danneggiato.");current=null;require(blocks.size<=100000){"Troppi eventi nel file."}}
                current!=null && key=="BEGIN" -> nested++
                current!=null && key=="END" -> nested--
                current!=null && nested==0 -> current.add(key to value)
                current==null && key=="X-WR-TIMEZONE" -> zone=ZoneId.of(value)
                key=="END"&&value=="VCALENDAR" -> closed=true
            }
        }
        require(closed&&current==null){"Calendario incompleto. Nessun dato importato."}
        var expandedCount=0
        val events=blocks.flatMap { fields ->
            fun one(name:String):Pair<String,String>?=fields.filter { it.first.substringBefore(';').uppercase(Locale.ROOT)==name }.also { require(it.size<=1){"Proprietà $name duplicata."} }.firstOrNull()
            if(one("STATUS")?.second=="CANCELLED")return@flatMap emptyList<ImportShiftEvent>()
            require(listOf("RRULE","RDATE","EXDATE","EXRULE").none { one(it)!=null }) { "Il file contiene ricorrenze. Importa da Google Calendar per espanderle correttamente prima del trasferimento." }
            val uid=one("UID")?.second?.takeIf { it.isNotBlank() }?:error("Evento privo di identificativo: non è possibile evitare duplicati.")
            val name=one("SUMMARY")?.second?.let { unescape(it) }?.trim()?.takeIf { it.isNotBlank() }?:error("Un evento non ha un titolo.")
            val start=one("DTSTART")?:error("Evento senza data iniziale.")
            val finish=one("DTEND")
            require(one("DURATION")==null){"Il file usa durate relative. Importa tramite Google Calendar."}
            val description=notes(one("DESCRIPTION")?.second?.let { unescape(it) }.orEmpty(),one("LOCATION")?.second?.let { unescape(it) }.orEmpty())
            val color=one("COLOR")?.second?.let { parseColor(it) }
            val recurringId=one("RECURRENCE-ID")?.let { if(it.second.length==8)LocalDate.parse(it.second,DateTimeFormatter.BASIC_ISO_DATE).toString() else icsTime(it,zone).toInstant().toString() }.orEmpty()
            val identity=uid+if(recurringId.isBlank())"" else "/"+recurringId
            expandedCount+=if(start.second.length==8&&finish!=null) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(start.second,DateTimeFormatter.BASIC_ISO_DATE),LocalDate.parse(finish.second,DateTimeFormatter.BASIC_ISO_DATE)).coerceAtLeast(1).coerceAtMost(100001).toInt() else 1
            require(expandedCount<=100000){"Troppi turni nel file."}
            if(start.first.split(';').any { it=="VALUE=DATE" }||start.second.length==8) {
                val begin=LocalDate.parse(start.second,DateTimeFormatter.BASIC_ISO_DATE)
                val end=finish?.second?.let { LocalDate.parse(it,DateTimeFormatter.BASIC_ISO_DATE) }?:begin.plusDays(1)
                allDayEvents(identity,name,begin,end,description,color)
            } else {
                require(finish!=null){"Evento senza orario finale."}
                listOf(timedEvent(identity,name,icsTime(start,zone),icsTime(finish,zone),description,color))
            }
        }
        require(events.size<=100000){"Troppi turni nel file."}
        return events
    }
    private fun icsTime(field:Pair<String,String>,defaultZone:ZoneId):ZonedDateTime {
        val utc=field.second.endsWith('Z')
        val local=LocalDateTime.parse(field.second.removeSuffix("Z"),if(field.second.removeSuffix("Z").length==13)dateTimeShort else dateTime)
        val timeZone=field.first.split(';').firstOrNull { it.startsWith("TZID=") }?.substringAfter('=')?.trim('"')?.let { ZoneId.of(it) }?:defaultZone
        if(utc)return local.atZone(ZoneOffset.UTC).withZoneSameInstant(defaultZone)
        require(timeZone.rules.getValidOffsets(local).isNotEmpty()){ "Orario inesistente durante il cambio dell’ora." }
        return local.atZone(timeZone).withZoneSameInstant(defaultZone)
    }
    private fun unescape(value:String):String {
        val result=StringBuilder();var index=0
        while(index<value.length) {val c=value[index++];if(c=='\\'&&index<value.length){val next=value[index++];result.append(if(next=='n'||next=='N')'\n' else next)}else result.append(c)}
        return result.toString()
    }

    fun plan(events:List<ImportShiftEvent>,existing:DatabaseSnapshot):ShiftImportPlan {
        require(events.isNotEmpty()){ "Nessun turno nel calendario selezionato." }
        require(events.size<=100000){ "Troppi turni da importare." }
        val unique=events.groupBy { eventId(it) }.map { (_,matching)->
            require(matching.distinct().size==1){ "Due eventi diversi hanno lo stesso identificativo. Nessun dato importato." };matching.first()
        }
        val prior=existing.shifts.values.flatten().map { it.id }.toSet()
        val added=unique.filter { eventId(it) !in prior }
        val palette=listOf(0xFFF3C66A,0xFFEF9DA0,0xFFB8A3F2,0xFF84C9B5,0xFF91BEE9,0xFFA5B3C8)
        val definitions=added.groupBy { modelId(it) }.entries.sortedBy { it.key }.mapIndexed { index,(id,matches)->
            val template=matches.groupBy { it.start to it.end }.maxByOrNull { it.value.size }!!.value.first()
            existing.definitions.firstOrNull { it.id==id }?:ShiftDefinition(id,template.name,template.name.filter { it.isLetterOrDigit() }.take(4).uppercase(Locale.ROOT).ifBlank { "T" },
                startTime=template.start?:"00:00",endTime=template.end?:"00:00",isAllDay=template.allDay,colorHex=template.color?:palette[index%palette.size],
                category=if(template.allDay)ShiftCategory.ALTRO else ShiftCategory.LAVORO,workProfileId=PROFILE_ID)
        }
        val models=definitions.associateBy { it.id }
        val assignments=added.map { event -> val model=models.getValue(modelId(event))
            AssignedShift(eventId(event),event.date,model.id,customStartTime=event.start?.takeIf { it!=model.startTime },customEndTime=event.end?.takeIf { it!=model.endTime },notes=event.notes)
        }
        return ShiftImportPlan(definitions,assignments,unique.size-added.size,unique.size)
    }
    /** Merge onto current state at confirmation time, never onto a stale preview snapshot. */
    fun merge(plan:ShiftImportPlan,categories:Map<String,ShiftCategory>,current:DatabaseSnapshot):ShiftImportResult {
        val existingIds=current.shifts.values.flatten().map { it.id }.toSet()
        val added=plan.assignments.filterNot { it.id in existingIds }
        val used=added.map { it.shiftDefId }.toSet()
        val newModels=plan.definitions.filter { it.id in used && current.definitions.none { previous -> previous.id==it.id } }.map { it.copy(category=categories[it.id]?:it.category) }
        val existingModels=current.definitions.associateBy { it.id }
        require(plan.definitions.filter { it.id in used }.all { existingModels[it.id]?.workProfileId in listOf(null,PROFILE_ID) }) { "Un modello importato è stato spostato in un altro lavoro: controlla il calendario prima di continuare." }
        require(added.all { it.shiftDefId in used && (existingModels.containsKey(it.shiftDefId)||newModels.any { m -> m.id==it.shiftDefId }) }) { "Modello importato non disponibile." }
        val profile=WorkProfile(PROFILE_ID,"SuperShift",ccnlCode="",ccnlName="",colorHex=0xFFB8A3F2,enableAdvancedCalc=false)
        val combined=(current.shifts.values.flatten()+added).groupBy { it.date }
        return ShiftImportResult(current.copy(definitions=current.definitions+newModels,shifts=combined,
            profiles=if(added.isNotEmpty()&&current.profiles.none { it.id==PROFILE_ID })current.profiles+profile else current.profiles),added.size,plan.duplicateCount+plan.assignments.size-added.size)
    }
}
