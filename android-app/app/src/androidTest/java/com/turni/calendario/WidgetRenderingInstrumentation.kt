package com.turni.calendario

import android.app.Activity
import android.app.Instrumentation
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.turni.calendario.model.ShiftDefinition
import com.turni.calendario.widget.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min
import kotlin.math.roundToInt

/** No repository mutations, permissions changes or launcher rearrangements. Render real RemoteViews. */
class WidgetRenderingInstrumentation:Instrumentation() {
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);start() }
    override fun onStart() {
        val results=Bundle()
        try {
            verifyWidgetMetadata()
            renderPdfFixtures()
            var rendered=0
            runOnMainSync {
                val folder=File(targetContext.filesDir,"widget_qa_v168").apply{check(exists()||mkdirs())}
                val fixtures=listOf(
                    Triple(WidgetKind.WEEK,320,168), Triple(WidgetKind.WEEK,320,84), Triple(WidgetKind.WEEK,280,68),
                    Triple(WidgetKind.PANORAMA,320,252),Triple(WidgetKind.PANORAMA,320,168),Triple(WidgetKind.PANORAMA,280,132),
                    Triple(WidgetKind.MONTH,320,320),Triple(WidgetKind.MONTH,280,300),
                    Triple(WidgetKind.TODAY,160,180),Triple(WidgetKind.TODAY,120,148),
                    Triple(WidgetKind.TODAY,136,160),Triple(WidgetKind.TODAY,200,220)
                )
                for(dark in listOf(true,false))for(scale in listOf(1f,1.3f,1.5f))for((kind,width,height)in fixtures){
                    val config=Configuration(targetContext.resources.configuration).apply{fontScale=scale}
                    val context=targetContext.createConfigurationContext(config)
                    val rows=when(kind){WidgetKind.MONTH->6;WidgetKind.PANORAMA,WidgetKind.TODAY->2;else->1}
                    val columns=if(kind==WidgetKind.TODAY)1 else 7
                    val today=LocalDate.of(2026,10,27)
                    val start=if(kind==WidgetKind.TODAY)today else today.minusDays(1)
                    val names=listOf("Mattina","Pomeriggio","Notte","Riposo","Smonto","Giornaliero")
                    val codes=listOf("MAT","POM","NOT","RIP","SMO","GIO")
                    val times=listOf("06:00","14:00","22:00","— —","— —","08:30")
                    val colors=listOf(0xFFF2C46D,0xFFEE9C9C,0xFFBAAEF6,0xFFAAB9CE,0xFF83CDB7,0xFF94C4EF)
                    val days=(0 until rows*columns).map{index->
                        val date=start.plusDays(index.toLong());val n=index%names.size
                        val definition=ShiftDefinition("preview-$n",names[n],codes[n],colorHex=colors[n])
                        val shift=WidgetShift("sample-$index",definition,times[n],1f,true,"Nota di esempio")
                        DayShiftSummary(date,"",date.dayOfMonth,date==today,definition,codes[n],names[n],times[n],1f,
                            index%5==1,true,shifts=listOf(shift),holidayName=if(date==LocalDate.of(2026,11,1))"Ognissanti" else null)
                    }
                    val title=when(kind){WidgetKind.TODAY->"Oggi e domani";WidgetKind.WEEK->"26 ott – 1 nov";WidgetKind.PANORAMA->"26 ott – 8 nov";else->"Novembre 2026"}
                    val data=WidgetPresentation(title,"Lavoro",rows,columns,YearMonth.of(2026,11),days)
                    val remote=TurniWidgetRenderer.createViews(context,kind,90_000+rendered,width,height,data,dark)
                    val view=remote.apply(context,FrameLayout(context))
                    val density=context.resources.displayMetrics.density
                    val w=(width*density).roundToInt();val h=(height*density).roundToInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
                    view.layout(0,0,w,h)
                    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val name="${kind.name.lowercase()}-${width}x$height-${if(dark)"dark" else "light"}-${scale}.png"
                    File(folder,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                    bitmap.recycle()
                    if(kind==WidgetKind.TODAY){
                        val plan=widgetDailyShiftLayout(width-16f,32f,scale.coerceAtMost(1.2f))
                        val paint=Paint().apply{typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}
                        for(time in listOf("06:00","14:00","22:00","23:59")){
                            paint.textSize=12.5f*scale.coerceAtMost(1.2f)
                            val factor=min(1f,plan.detailWidth/paint.measureText(time))
                            paint.textSize=(paint.textSize*factor).coerceAtLeast(8.5f)
                            check(paint.measureText(time)<=plan.detailWidth+.05f){"Start time would be truncated at $width dp / $scale"}
                        }
                    }
                    rendered++
                }
            }
            results.putString("stream","Rendered $rendered real RemoteViews; all clock-fit checks passed.\n")
            finish(Activity.RESULT_OK,results)
        }catch(error:Throwable){results.putString("stream",error.stackTraceToString());finish(Activity.RESULT_CANCELED,results)}
    }
    private fun verifyWidgetMetadata() {
        val manager=android.appwidget.AppWidgetManager.getInstance(targetContext)
        val providers=manager.installedProviders.filter{it.provider.packageName==targetContext.packageName}
        check(providers.size==4){"Expected four widget providers"}
        val app=targetContext.packageManager.getApplicationInfo(targetContext.packageName,0)
        check(app.icon==R.mipmap.ic_turnisync)
        val folder=File(targetContext.filesDir,"widget_qa_v171").apply{mkdirs()}
        for(provider in providers){
            check(provider.icon==R.mipmap.ic_turnisync){"Widget picker icon differs from app icon"}
            val drawable=provider.loadIcon(targetContext,320) ?: error("Missing picker icon")
            val bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
            drawable.setBounds(0,0,192,192);drawable.draw(Canvas(bitmap))
            File(folder,"${provider.provider.shortClassName.substringAfterLast('.')}-icon.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
            if(provider.provider.className.endsWith("WeekRowWidgetProvider") || provider.provider.className.endsWith("WeeksPanoramaWidgetProvider")){
                check(provider.resizeMode and android.appwidget.AppWidgetProviderInfo.RESIZE_VERTICAL != 0)
                if(android.os.Build.VERSION.SDK_INT>=31){
                    check(provider.targetCellHeight==if(provider.provider.className.endsWith("WeekRowWidgetProvider"))1 else 2)
                }
            }
        }
    }
    private fun renderPdfFixtures(){
        val folder=File(targetContext.filesDir,"pdf_qa_v169").apply{mkdirs()}
        for(mode in listOf(com.turni.calendario.model.PdfExportMode.MESE,com.turni.calendario.model.PdfExportMode.ANNO)){
            val options=com.turni.calendario.model.PdfExportOptions(mode=mode,startDate=LocalDate.of(2026,11,1),selectedYear=2026)
            val days=com.turni.calendario.export.PdfExportPlan.dates(options).mapIndexed{index,date->
                val names=listOf("Mattina","Pomeriggio","Notte","Riposo")
                val codes=listOf("MAT","POM","NOT","RIP")
                val times=listOf("06:00 - 13:36","14:00 - 21:36","22:00 - 05:36","Tutto il giorno")
                val n=index%4
                com.turni.calendario.export.PdfDayRow(date,listOf(com.turni.calendario.export.PdfShiftRow(codes[n],names[n],0xFFBAAEF6.toInt(),null,listOf(times[n]))),emptyList())
            }
            val snapshot=com.turni.calendario.export.PdfExportSnapshot("Lavoro di esempio",options,days)
            val file=com.turni.calendario.export.PdfGenerator.writePdf(targetContext,snapshot)
            val copy=File(folder,"${mode.name.lowercase()}.pdf");file.copyTo(copy,true);file.delete()
            android.os.ParcelFileDescriptor.open(copy,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor->
                android.graphics.pdf.PdfRenderer(descriptor).use { renderer->check(renderer.pageCount==1);renderer.openPage(0).use{page->
                    val bitmap=Bitmap.createBitmap(1800,(1800f*page.height/page.width).toInt(),Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE);page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    File(folder,"${mode.name.lowercase()}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
                }}
            }
        }
    }

}
