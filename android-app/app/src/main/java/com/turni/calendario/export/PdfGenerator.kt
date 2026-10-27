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
import com.turni.calendario.repository.ShiftRepository
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.LocalTime
import kotlin.math.roundToInt

data class PdfShiftRow(val code: String, val name: String, val color: Int, val time: String?, val details: List<String>)
data class PdfDayRow(val date: LocalDate, val shifts: List<PdfShiftRow>, val details: List<String>)
data class PdfExportSnapshot(val work: String, val options: PdfExportOptions, val days: List<PdfDayRow>)

/** Capture repository state on the UI thread; render this immutable snapshot on IO. */
object PdfGenerator {
    fun capture(options: PdfExportOptions): PdfExportSnapshot {
        val work = ShiftRepository.activeWorkProfile
        val days = PdfExportPlan.dates(options).map { date ->
            val source = ShiftRepository.getShiftsForDate(date)
            val all = source.filter {
                ShiftRepository.getShiftDefinition(it.shiftDefId)?.workProfileId == work.id
            }
            val extra = ShiftRepository.getDayExtraInfo(date)
            val rows = all.mapNotNull { shift ->
                val def = ShiftRepository.getShiftDefinition(shift.shiftDefId) ?: return@mapNotNull null
                if (options.filterShiftId != null && def.id != options.filterShiftId) return@mapNotNull null
                val legacyPrimary = source.firstOrNull()?.id == shift.id
                val overtime = if (shift.overtimeHours > 0) shift.overtimeHours else if (legacyPrimary) extra.overtimeHours else 0f
                val actualEnd = shift.actualEndTime ?: if (legacyPrimary) extra.actualEndTime else null
                val lines = buildList {
                    if (options.includeTime) add(if (def.isAllDay) "Tutto il giorno" else "${shift.customStartTime ?: def.startTime} - ${shift.customEndTime ?: def.endTime}")
                    if (actualEnd != null && options.includeTime) add("Stacco effettivo: $actualEnd")
                    if (options.includeDuration && !def.isAllDay) {
                        val start = LocalTime.parse(shift.customStartTime ?: def.startTime)
                        val end = LocalTime.parse(shift.customEndTime ?: def.endTime)
                        val elapsed = (java.time.Duration.between(start, end).toMinutes() + 1440) % 1440
                        val minutes = ((if (elapsed == 0L) 1440 else elapsed) - def.breakMinutes).coerceAtLeast(0)
                        add("Durata prevista: ${formatMinutes(minutes.toInt())}")
                    }
                    if (options.includeBreak && def.breakMinutes > 0) add("Pausa: ${def.breakMinutes} min${def.breakStartTime?.let { " dalle $it" } ?: ""}")
                    if (overtime > 0) add("Straordinario: ${formatMinutes((overtime * 60).roundToInt())}")
                    if (options.includeLocation && (shift.isTrasferta || (legacyPrimary && extra.isTrasferta))) add("Trasferta")
                    if (options.includeNotes && shift.notes.isNotBlank()) add(shift.notes)
                    if (options.includeNotes && shift.tags.isNotEmpty()) add(shift.tags.joinToString(" · "))
                }
                PdfShiftRow(def.shortCode, if (options.includeTitle) def.name else "", def.colorHex.toInt(), if (options.includeTime && !def.isAllDay) shift.customStartTime ?: def.startTime else null, lines)
            }
            val dayLines = buildList {
                if (ShiftRepository.isOnCall(date)) add("Reperibilità")
                if (options.includeEvents) ShiftRepository.isHoliday(date)?.let { add(it.name) }
                if (options.includeNotes && extra.notes.isNotBlank()) add(extra.notes)
            }
            PdfDayRow(date, rows, dayLines)
        }
        return PdfExportSnapshot(work.name, options, days)
    }

    fun writePdf(context: Context, snapshot: PdfExportSnapshot): File {
        val output = File(context.cacheDir, "turnisync-${System.currentTimeMillis()}.pdf")
        val calendar=PdfExportPlan.presentation(snapshot.options)!=PdfPresentation.LIST
        val months=snapshot.days.map{YearMonth.from(it.date)}.distinct().size
        val first=if(calendar&&months>2)842 to 1191 else 595 to 842
        val papers=if(calendar&&months>1)listOf(first,1191 to 1684)else listOf(first)
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

    private class DocumentWriter(val doc: PdfDocument, val data: PdfExportSnapshot, val width:Float, val height:Float) {
        private val ink=Color.rgb(28,43,67)
        private val muted=Color.rgb(86,101,122)
        private val accent=Color.rgb(56,102,232)
        private val paper=Color.rgb(244,246,249)
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        private lateinit var canvas:Canvas
        private val margin=26f
        private val format=DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ITALIAN)
        private val crowded="I dettagli selezionati non entrano leggibili in una pagina. Riduci il periodo oppure disattiva note, durata o pause."

        private fun text(value:String,x:Float,y:Float,size:Float=9f,color:Int=ink,bold:Boolean=false) {
            paint.style=Paint.Style.FILL;paint.color=color;paint.textSize=size;paint.isFakeBoldText=bold
            canvas.drawText(value,x,y,paint)
        }
        private fun box(x:Float,y:Float,w:Float,h:Float,color:Int,radius:Float=5f) {
            paint.style=Paint.Style.FILL;paint.color=color
            canvas.drawRoundRect(RectF(x,y,x+w,y+h),radius,radius,paint)
        }
        private fun lines(value:String,available:Float,size:Float):List<String> {
            paint.textSize=size;paint.isFakeBoldText=false
            return value.split('\n').flatMap { paragraph ->
                if(paragraph.isBlank())listOf("") else buildList {
                    var rest=paragraph.trim()
                    while(rest.isNotEmpty()) {
                        val count=paint.breakText(rest,true,available.coerceAtLeast(1f),null).coerceAtLeast(1)
                        val end=if(count<rest.length)rest.lastIndexOf(' ',count).takeIf{it>0}?:count else count
                        add(rest.substring(0,end));rest=rest.substring(end).trimStart()
                    }
                }
            }
        }
        fun render() {
            val months=data.days.map{YearMonth.from(it.date)}.distinct()
            val calendar=PdfExportPlan.presentation(data.options)!=PdfPresentation.LIST
            val page=doc.startPage(PdfDocument.PageInfo.Builder(width.toInt(),height.toInt(),1).create())
            try {
            canvas=page.canvas;canvas.drawColor(Color.WHITE)
            val title=when(data.options.mode){
                PdfExportMode.ELENCO->"Elenco turni"
                PdfExportMode.MESE->months.first().atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ITALIAN)).replaceFirstChar{it.uppercase()}
                PdfExportMode.ANNO->"Calendario ${data.options.selectedYear}"
                else->if(calendar)"Calendario del periodo" else "Elenco del periodo"
            }
            text(if(data.options.includeLogo)"TurniSync" else "Prospetto turni",margin,29f,9f,accent,true)
            text(title,margin,55f,20f,ink,true)
            var top=72f
            lines(data.work,width-2*margin,10f).forEach{text(it,margin,top,10f,muted);top+=13f}
            val range=PdfExportPlan.range(data.options)
            text("${range.first.format(format)} - ${range.second.format(format)}",margin,top,8f,muted);top+=18f
            if(data.options.headerNotes.isNotBlank()) {
                lines(data.options.headerNotes,width-2*margin,8f).forEach{text(it,margin,top,8f);top+=10f}
                top+=8f
            }
            require(top<height-100f){crowded}
            if(calendar)calendars(months,top) else list(top)
            text("Generato il ${LocalDate.now().format(format)}",margin,height-18f,7f,muted)
            text("1 / 1",width-margin-22f,height-18f,7f,muted)
            } finally {doc.finishPage(page)}
        }

        private data class ListBlock(val date:String,val title:String,val detail:String,val color:Int?)
        private fun list(top:Float) {
            val blocks=data.days.flatMap { day ->
                val date=day.date.format(DateTimeFormatter.ofPattern("EEE dd/MM",Locale.ITALIAN))
                if(day.shifts.isEmpty()) {
                    if(data.options.includeEmptyRows||day.details.isNotEmpty())listOf(ListBlock(date,"Nessun turno",day.details.joinToString(" · "),null)) else emptyList()
                } else day.shifts.mapIndexed { index,shift ->
                    ListBlock(date,if(shift.name.isBlank())shift.code else shift.name,
                        (shift.details+if(index==0)day.details else emptyList()).joinToString(" · "),
                        if(data.options.includeIcon)shift.color else null)
                }
            }
            if(blocks.isEmpty()){text("Nessun turno nel periodo e nel filtro scelti.",margin,top+15f);return}
            val available=height-top-38f
            for(font in listOf(9f,8.5f,8f,7.5f,7f,6.5f,6f)) {
                for(columns in 1..3) {
                    val gap=12f
                    val columnWidth=(width-2*margin-gap*(columns-1))/columns
                    val detailWidth=columnWidth-16f
                    val wrapped=blocks.map { block ->
                        lines("${block.date}  ${block.title}",detailWidth,font) +
                            if(block.detail.isBlank())emptyList() else lines(block.detail,detailWidth,font-.5f)
                    }
                    val heights=wrapped.map{it.size*(font+2f)+9f}
                    val packed=packPdfColumns(heights,columns,available)?:continue
                    packed.forEachIndexed { column,indexes ->
                        var y=top
                        val x=margin+column*(columnWidth+gap)
                        indexes.forEach { index ->
                            val block=blocks[index]
                            box(x,y,columnWidth,heights[index]-3f,if(index%2==0)paper else Color.WHITE)
                            block.color?.let{box(x,y+4f,3f,heights[index]-11f,it,1.5f)}
                            wrapped[index].forEachIndexed { line,value ->
                                text(value,x+8f,y+font+3f+line*(font+2f),if(line==0)font else font-.5f,if(line==0)ink else muted,line==0)
                            }
                            y+=heights[index]
                        }
                    }
                    return
                }
            }
            throw IllegalArgumentException(crowded)
        }

        private fun calendars(months:List<YearMonth>,top:Float) {
            require(months.size<=12){"Per un calendario leggibile su una pagina, seleziona al massimo 12 mesi."}
            val columns=when{months.size==1->1;months.size<=4->2;else->3}
            val rows=(months.size+columns-1)/columns
            val gap=12f
            val tileW=(width-2*margin-gap*(columns-1))/columns
            val legend=if(months.size>1&&data.options.includeTitle)lines(data.days.flatMap{it.shifts}.distinctBy{it.code to it.name}.joinToString("   |   "){"${it.code}: ${it.name}"}+"   |   TG: giornata intera   |   REP: reperibilità",width-2*margin,7f)else emptyList()
            val legendHeight=if(legend.isEmpty())0f else legend.size*9f+8f
            val tileH=(height-top-38f-legendHeight-gap*(rows-1))/rows
            legend.forEachIndexed{i,value->text(value,margin,height-30f-legendHeight+i*9f,7f,muted)}
            val byDate=data.days.associateBy{it.date}
            months.forEachIndexed { index,month ->
                val x=margin+(index%columns)*(tileW+gap)
                val y=top+(index/columns)*(tileH+gap)
                val title=month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ITALIAN)).replaceFirstChar{it.uppercase()}
                if(months.size>1)text(title,x+3f,y+12f,10f,ink,true)
                val gridTop=y+if(months.size==1)18f else 33f
                val weekCount=(month.atDay(1).dayOfWeek.value-1+month.lengthOfMonth()+6)/7
                val cellW=tileW/7f
                val cellH=(tileH-if(months.size==1)18f else 33f)/weekCount
                listOf("L","M","M","G","V","S","D").forEachIndexed { day,label -> text(label,x+day*cellW+5f,y+if(months.size==1)11f else 26f,7f,muted,true) }
                val offset=month.atDay(1).dayOfWeek.value-1
                for(day in 1..month.lengthOfMonth()) {
                    val date=month.atDay(day);val position=offset+day-1
                    val left=x+position%7*cellW;val cellTop=gridTop+position/7*cellH
                    val row=byDate[date]
                    box(left+1f,cellTop,cellW-2f,cellH-3f,if(position%7>=5)Color.rgb(230,237,247)else paper,4f)
                    text(day.toString(),left+4f,cellTop+if(months.size==1)10f else 8f,if(months.size==1)9f else 7f,ink,true)
                    if(row==null)continue
                    val compact=months.size>1
                    var selected:List<Pair<String,PdfShiftRow?>>?=null
                    var font=if(compact)6f else 9f
                    while(font>=5f) {
                        val content=buildList<Pair<String,PdfShiftRow?>> {
                            row.shifts.forEach { shift ->
                                val titleLine=if(compact||shift.name.isBlank())shift.code else shift.name
                                lines(titleLine,cellW-6f,font).forEach{add(it to shift)}
                                shift.details.forEach{detail->lines(if(compact)detail.replace("Durata prevista:","Dur:").replace(" - ","-").replace("Stacco effettivo:","Stacco:").replace("Straordinario:","Extra:").replace("Durata:","Dur:").replace("Pausa:","P:").replace("Tutto il giorno","TG").replace("Reperibilità","REP")else detail,cellW-6f,font).forEach{add(it to null)}}
                            }
                            row.details.forEach{detail->lines(if(compact)detail.replace("Durata prevista:","Dur:").replace(" - ","-").replace("Stacco effettivo:","Stacco:").replace("Straordinario:","Extra:").replace("Durata:","Dur:").replace("Pausa:","P:").replace("Tutto il giorno","TG").replace("Reperibilità","REP")else detail,cellW-6f,font).forEach{add(it to null)}}
                        }
                        if(content.size*(font+2f)<=cellH-if(compact)12f else 17f){selected=content;break}
                        font-=.5f
                    }
                    require(selected!=null){crowded}
                    var cursor=cellTop+if(compact)10f else 13f
                    selected.forEach { (value,shift) ->
                        if(shift!=null&&data.options.includeIcon) {
                            val tint=Color.rgb((Color.red(shift.color)+255*3)/4,(Color.green(shift.color)+255*3)/4,(Color.blue(shift.color)+255*3)/4)
                            box(left+3f,cursor-1f,cellW-6f,font+3f,tint,2f)
                        }
                        text(value,left+5f,cursor+font,font,if(shift!=null)ink else muted,shift!=null)
                        cursor+=font+2f
                    }
                }
            }
        }
    }

}
