package com.turni.calendario.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import com.turni.calendario.ui.theme.readableInk
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
data class PdfDayRow(val date: LocalDate, val shifts: List<PdfShiftRow>, val details: List<String>, val holiday: Boolean = false)
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
                    val permMinutes = if (shift.permissionHours.isFinite() && shift.permissionHours > 0f) (shift.permissionHours * 60).roundToInt() else 0
                    if (permMinutes > 0) add("Permesso: −${formatMinutes(permMinutes)}")
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
                if (options.includeNotes) extra.tagIds.mapNotNull { id -> ShiftRepository.dayTags.firstOrNull { it.id == id }?.name }.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" · ")) }
            }
            PdfDayRow(date, rows, dayLines, options.includeEvents && ShiftRepository.isHoliday(date)!=null)
        }
        return PdfExportSnapshot(work.name, options, days)
    }

    fun writePdf(context: Context, snapshot: PdfExportSnapshot): File {
        val output = File.createTempFile("turnisync-", ".pdf", context.cacheDir)
        val presentation = PdfExportPlan.presentation(snapshot.options)
        val months=snapshot.days.map{YearMonth.from(it.date)}.distinct().size
        val papers = when (presentation) {
            PdfPresentation.OVERVIEW -> listOf(1191 to 842, 1684 to 1191, 2384 to 1684)
            PdfPresentation.MONTHS -> if (months > 1) listOf(595 to 842, 1191 to 1684) else listOf(595 to 842)
            PdfPresentation.LIST -> listOf(595 to 842, 842 to 1191)
        }
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
        private val ink=Color.rgb(25,28,34)
        private val muted=Color.rgb(65,72,84)
        private val accent=Color.rgb(54,91,225)
        private val paper=Color.rgb(243,245,249)
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
        private fun lines(value:String,available:Float,size:Float,bold:Boolean=false):List<String> {
            paint.textSize=size;paint.isFakeBoldText=bold
            if(Regex("^\\d{2}(?::\\d{2})?–\\d{2}(?::\\d{2})?$").matches(value)) {
                // A clock range is an indivisible unit. Never split a minute or hour in half.
                return if(paint.measureText(value)*6.5f/size>available)value.split("–")else listOf(value)
            }
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
            val presentation = PdfExportPlan.presentation(data.options)
            val calendar = presentation != PdfPresentation.LIST
            val page=doc.startPage(PdfDocument.PageInfo.Builder(width.toInt(),height.toInt(),1).create())
            try {
            canvas=page.canvas;canvas.drawColor(Color.WHITE)
            val title=when(data.options.mode){
                PdfExportMode.ELENCO->"Elenco turni"
                PdfExportMode.MESE->months.first().atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.ITALIAN)).replaceFirstChar{it.uppercase()}
                PdfExportMode.ANNO->"Calendario ${data.options.selectedYear}"
                else->if(calendar)"Calendario del periodo" else "Elenco del periodo"
            }
            box(margin,20f,width-2*margin,68f,ink,12f)
            text(if(data.options.includeLogo)"TURNISYNC" else "PROSPETTO TURNI",margin+14f,36f,8f,Color.rgb(178,198,255),true)
            text(title,margin+14f,61f,20f,Color.WHITE,true)
            text(if(calendar)"CALENDARIO" else "ELENCO",width-margin-78f,36f,7f,Color.rgb(216,224,244),true)
            var top=105f
            lines(data.work,width-2*margin,11f,true).forEach{text(it,margin,top,11f,ink,true);top+=14f}
            val range=PdfExportPlan.range(data.options)
            text("${range.first.format(format)} – ${range.second.format(format)}",margin,top,9f,muted);top+=17f
            val assignments=data.days.sumOf{it.shifts.size}
            text("${data.days.count{it.shifts.isNotEmpty()}} giorni con turni · $assignments turni",margin,top,8f,accent,true);top+=21f
            if(data.options.headerNotes.isNotBlank()) {
                lines(data.options.headerNotes,width-2*margin,8f).forEach{text(it,margin,top,8f);top+=10f}
                top+=8f
            }
            require(top<height-100f){crowded}
            when (presentation) {
                PdfPresentation.LIST -> list(top)
                PdfPresentation.MONTHS -> calendars(months,top)
                PdfPresentation.OVERVIEW -> overview(months,top)
            }
            box(margin,height-30f,width-2*margin,1f,Color.rgb(220,224,232),0f)
            text("TurniSync · Generato il ${LocalDate.now().format(format)}",margin,height-18f,7f,muted)
            text("1 / 1",width-margin-22f,height-18f,7f,muted)
            } finally {doc.finishPage(page)}
        }

        private val rule = Color.rgb(215, 221, 231)
        private val weekend = Color.rgb(235, 239, 247)
        private val holiday = Color.rgb(252, 233, 236)
        private val weekdayFormat = DateTimeFormatter.ofPattern("EEE", Locale.ITALIAN)
        private val listDateFormat = DateTimeFormatter.ofPattern("dd/MM", Locale.ITALIAN)
        private val monthFormat = DateTimeFormatter.ofPattern("MMM", Locale.ITALIAN)

        private fun marker(x: Float, y: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(x, y, 2.1f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = .45f
            paint.color = muted
            canvas.drawCircle(x, y, 2.1f, paint)
        }

        private data class ListEntry(val time: String, val labels: List<String>, val color: Int?)

        private fun list(top: Float) {
            val days = data.days.filter { data.options.includeEmptyRows || it.shifts.isNotEmpty() || it.details.isNotEmpty() }
            if (days.isEmpty()) {
                text("Nessun turno nel periodo e nel filtro scelti.", margin, top + 15f)
                return
            }
            val available = height - top - 41f - 18f
            for (font in listOf(10f, 9.5f, 9f, 8.5f, 8f, 7.5f, 7f)) {
                for (columns in 1..3) {
                    val gap = 14f
                    val columnWidth = (width - 2 * margin - gap * (columns - 1)) / columns
                    val timeWidth = if (data.options.includeTime) font * 7.6f else 0f
                    val titleOffset = 65f + timeWidth
                    val labelWidth = columnWidth - titleOffset - 5f
                    if (labelWidth < 65f) continue
                    val leading = font + 2f
                    val entries = days.map { day ->
                        buildList {
                            day.shifts.forEach { shift ->
                                val time = if (data.options.includeTime) shift.details.firstOrNull().orEmpty() else ""
                                val detail = if (data.options.includeTime) shift.details.drop(1) else shift.details
                                val labels = lines(shift.name, labelWidth, font, true) +
                                    detail.flatMap { lines(it, labelWidth, font) }
                                add(ListEntry(time, labels.ifEmpty { listOf("") }, shift.color.takeIf { data.options.includeIcon }))
                            }
                            if (day.shifts.isEmpty() && day.details.isEmpty()) add(ListEntry("", listOf("—"), null))
                            day.details.forEach { add(ListEntry("", lines(it, labelWidth, font), null)) }
                        }
                    }
                    val heights = entries.map { group -> group.sumOf { it.labels.size } * leading + 5f }
                    val packed = packPdfColumns(heights, columns, available) ?: continue
                    packed.forEachIndexed { column, indexes ->
                        val x = margin + column * (columnWidth + gap)
                        box(x, top, columnWidth, 17f, paper, 3f)
                        text("Giorno", x + 5f, top + 11f, 8f, accent, true)
                        if (timeWidth > 0f) text("Orario", x + 64f, top + 11f, 8f, accent, true)
                        text("Turno e dettagli", x + titleOffset, top + 11f, 8f, accent, true)
                        var y = top + 18f
                        indexes.forEach { index ->
                            val day = days[index]
                            val rowHeight = heights[index]
                            if (day.holiday || day.date.dayOfWeek.value >= 6) {
                                box(x, y, 51f, rowHeight, if (day.holiday) holiday else weekend, 0f)
                            }
                            text(day.date.format(listDateFormat), x + 4f, y + font + 1f, font, ink, true)
                            text(day.date.format(weekdayFormat), x + 33f, y + font + 1f, font - 1f, muted)
                            var baseline = y + font + 1f
                            entries[index].forEach { entry ->
                                entry.color?.let { marker(x + 58f, baseline - 2.6f, it) }
                                text(entry.time, x + 64f, baseline, font - 1f, muted)
                                entry.labels.forEachIndexed { line, label ->
                                    text(label, x + titleOffset, baseline, font, if (line == 0) ink else muted, line == 0)
                                    baseline += leading
                                }
                            }
                            y += rowHeight
                            box(x, y, columnWidth, if (day.date.dayOfWeek.value == 7) .85f else .4f, rule, 0f)
                        }
                    }
                    return
                }
            }
            throw IllegalArgumentException(crowded)
        }

        private data class OverviewLine(val label: String, val color: Int? = null, val bold: Boolean = false)

        private fun overview(months: List<YearMonth>, top: Float) {
            require(months.size <= 12) { "Per una panoramica su una pagina, seleziona al massimo 12 mesi." }
            val gap = 4f
            val columnWidth = (width - 2 * margin - gap * (months.size - 1)) / months.size
            val gridTop = top + 21f
            val availableHeight = height - 42f - gridTop
            val labelWidth = columnWidth - 26f
            val byDate = data.days.associateBy { it.date }
            for (font in listOf(8f, 7.5f, 7f, 6.5f)) {
                val leading = font + .8f
                val contents = data.days.associate { day ->
                    day.date to buildList<OverviewLine> {
                        day.shifts.forEach { shift ->
                            if (shift.name.isNotBlank()) {
                                lines(shift.name, labelWidth, font, true).forEachIndexed { index, label ->
                                    val color = shift.color.takeIf { index == 0 && data.options.includeIcon }
                                    add(OverviewLine(label, color, bold = true))
                                }
                            }
                            val markDetails = shift.name.isBlank() && data.options.includeIcon
                            val firstDetail = size
                            shift.details.forEach { detail ->
                                val compact = compactCalendarDetail(detail).replace("Tutto il giorno", "Intera giornata")
                                lines(compact, labelWidth, font).forEach { label ->
                                    add(OverviewLine(label, shift.color.takeIf { markDetails && size == firstDetail }))
                                }
                            }
                            if (shift.name.isBlank() && size == firstDetail && data.options.includeIcon) {
                                add(OverviewLine("", shift.color))
                            }
                        }
                        day.details.forEach { detail ->
                            lines(detail, labelWidth, font).forEach { add(OverviewLine(it)) }
                        }
                    }
                }
                val minimumHeights = FloatArray(31) { 16f }
                contents.forEach { (date, content) ->
                    val index = date.dayOfMonth - 1
                    minimumHeights[index] = maxOf(minimumHeights[index], content.size * leading + 2f)
                }
                val spare = availableHeight - minimumHeights.sum()
                if (spare < 0f) continue
                val rowHeights = minimumHeights.map { it + spare / minimumHeights.size }
                val rowTops = rowHeights.runningFold(gridTop, Float::plus)
                months.forEachIndexed { index, month ->
                    val x = margin + index * (columnWidth + gap)
                    box(x, top, columnWidth, 17f, Color.rgb(227, 234, 255), 3f)
                    val monthLabel = month.atDay(1).format(monthFormat).replaceFirstChar { it.uppercase() }
                    text(if (data.options.mode == PdfExportMode.ANNO) monthLabel else "$monthLabel ${month.year}",
                        x + 5f, top + 11.5f, 9f, accent, true)
                    for (dayNumber in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayNumber)
                        val row = byDate[date] ?: continue
                        val y = rowTops[dayNumber - 1]
                        val fill = when {
                            row.holiday -> holiday
                            date.dayOfWeek.value >= 6 -> weekend
                            dayNumber % 2 == 0 -> paper
                            else -> Color.WHITE
                        }
                        box(x, y, columnWidth, rowHeights[dayNumber - 1] - .4f, fill, 0f)
                        box(x, y, columnWidth, if (date.dayOfWeek.value == 1) .75f else .35f, rule, 0f)
                        text(dayNumber.toString(), x + 3f, y + 8f, 7.5f, ink, true)
                        text(date.format(weekdayFormat), x + 3f, y + 14f, 5.5f, muted)
                        val labelX = x + 24f
                        contents.getValue(date).forEachIndexed { index, line ->
                            val baseline = y + font + .5f + index * leading
                            line.color?.let { marker(labelX - 5f, baseline - 2.2f, it) }
                            text(line.label, labelX, baseline, font, ink, line.bold)
                        }
                    }
                }
                return
            }
            throw IllegalArgumentException(crowded)
        }

        private fun compactCalendarDetail(value:String):String {
            val range=Regex("^(\\d{2}):(\\d{2}) - (\\d{2}):(\\d{2})$").matchEntire(value)?:return value
            fun clock(h:String,m:String)=if(m=="00")h else "$h:$m"
            return clock(range.groupValues[1],range.groupValues[2])+"–"+clock(range.groupValues[3],range.groupValues[4])
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
                if(months.size>1){box(x,y,tileW,19f,ink,5f);text(title,x+8f,y+13f,10f,Color.WHITE,true)}
                val gridTop=y+if(months.size==1)20f else 37f
                val weekCount=(month.atDay(1).dayOfWeek.value-1+month.lengthOfMonth()+6)/7
                val cellW=tileW/7f
                val cellH=(tileH-if(months.size==1)20f else 37f)/weekCount
                listOf("Lun","Mar","Mer","Gio","Ven","Sab","Dom").forEachIndexed { day,label ->
                    paint.textSize=7f;paint.isFakeBoldText=true
                    text(label,x+(day+.5f)*cellW-paint.measureText(label)/2f,y+if(months.size==1)12f else 31f,7f,if(day>=5)accent else muted,true)
                }
                val offset=month.atDay(1).dayOfWeek.value-1
                for(day in 1..month.lengthOfMonth()) {
                    val date=month.atDay(day);val position=offset+day-1
                    val left=x+position%7*cellW;val cellTop=gridTop+position/7*cellH
                    val row=byDate[date]
                    box(left+1f,cellTop,cellW-2f,cellH-3f,if(position%7>=5)Color.rgb(229,234,244)else paper,4f)
                    val daySize=if(months.size==1)10f else 8f
                    val dayCenter=left+cellW/2f
                    val dayY=cellTop+if(months.size==1)10f else 8f
                    paint.textSize=daySize;paint.isFakeBoldText=true
                    if(row?.holiday==true){
                        paint.style=Paint.Style.STROKE;paint.strokeWidth=.8f;paint.color=Color.rgb(178,47,63)
                        canvas.drawRoundRect(RectF(dayCenter-7f,cellTop+1f,dayCenter+7f,cellTop+if(months.size==1)13f else 11f),2f,2f,paint)
                    }
                    text(day.toString(),dayCenter-paint.measureText(day.toString())/2f,dayY,daySize,ink,true)
                    if(row==null)continue
                    val compact=months.size>1
                    var selected:List<Pair<String,PdfShiftRow?>>?=null
                    var font=if(compact)8f else 11f
                    while(font>=if(compact)6.5f else 8f) {
                        val content=buildList<Pair<String,PdfShiftRow?>> {
                            row.shifts.forEach { shift ->
                                val titleLine=if(compact||shift.name.isBlank())shift.code else shift.name
                                lines(titleLine,cellW-6f,font,true).forEach{add(it to shift)}
                                shift.details.forEach{detail->lines(if(compact)compactCalendarDetail(detail).replace("Durata prevista:","Dur:").replace(" - ","-").replace("Stacco effettivo:","Stacco:").replace("Straordinario:","Extra:").replace("Permesso:","Perm:").replace("Durata:","Dur:").replace("Pausa:","P:").replace("Tutto il giorno","TG").replace("Reperibilità","REP")else detail,cellW-6f,font).forEach{add(it to null)}}
                            }
                            row.details.forEach{detail->lines(if(compact)compactCalendarDetail(detail).replace("Durata prevista:","Dur:").replace(" - ","-").replace("Stacco effettivo:","Stacco:").replace("Straordinario:","Extra:").replace("Permesso:","Perm:").replace("Durata:","Dur:").replace("Pausa:","P:").replace("Tutto il giorno","TG").replace("Reperibilità","REP")else detail,cellW-6f,font).forEach{add(it to null)}}
                        }
                        if(content.size*(font+2f)<=cellH-if(compact)16f else 21f){selected=content;break}
                        font-=.5f
                    }
                    require(selected!=null){crowded}
                    var cursor=cellTop+if(compact)14f else 17f
                    selected.forEach { (value,shift) ->
                        var lineInk=ink
                        if(shift!=null&&data.options.includeIcon) {
                            val tint=Color.rgb((Color.red(shift.color)*2+255)/3,(Color.green(shift.color)*2+255)/3,(Color.blue(shift.color)*2+255)/3)
                            box(left+3f,cursor-1f,cellW-6f,font+3f,tint,2f)
                            lineInk=readableInk(ComposeColor(tint),ComposeColor(ink)).toArgb()
                        }
                        paint.textSize=font;paint.isFakeBoldText=shift!=null
                        val fittedFont=minOf(font,font*(cellW-10f)/paint.measureText(value).coerceAtLeast(1f))
                        text(value,left+5f,cursor+font,fittedFont,lineInk,shift!=null)
                        cursor+=font+2f
                    }
                }
            }
        }
    }

}
