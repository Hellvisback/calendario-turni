package com.turni.calendario.data

import android.content.Context
import android.net.Uri
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

data class AiRosterDocument(val name:String,val mime:String,val pages:Int)
object AiRosterDocuments {
    const val MAX_BYTES=12*1024*1024
    fun readBounded(input:InputStream):ByteArray {
        val output=ByteArrayOutputStream();val buffer=ByteArray(16384)
        while(true){val count=input.read(buffer);if(count<0)break;if(count==0)continue
            require(output.size()+count<=MAX_BYTES){"Il documento supera 12 MB. Usa un file più piccolo, senza saltare pagine necessarie."}
            output.write(buffer,0,count)
        }
        require(output.size()>0){"Il documento è vuoto."};return output.toByteArray()
    }
    fun read(context:Context,uri:Uri):AiRosterDocument {
        val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->if(cursor.moveToFirst())cursor.getString(0)else null}?:"Documento"
        val raw=context.contentResolver.openInputStream(uri)?.use(::readBounded)?:throw IllegalArgumentException("Impossibile leggere il documento.")
        val pdf=raw.size>=5&&String(raw,0,5,Charsets.US_ASCII)=="%PDF-"
        if(pdf){
            val temp=File.createTempFile("ai-roster-",".pdf",context.cacheDir)
            try {
                temp.writeBytes(raw)
                val pages=ParcelFileDescriptor.open(temp,ParcelFileDescriptor.MODE_READ_ONLY).use{descriptor->PdfRenderer(descriptor).use{it.pageCount}}
                require(pages in 1..100){"Scegli un PDF con un massimo di 100 pagine."}
                return AiRosterDocument(name,"application/pdf",pages)
            }finally{temp.delete()}
        }
        require(context.contentResolver.getType(uri)?.startsWith("image/")==true){"Scegli un PDF oppure un’immagine del prospetto."}
        val bitmap=DocumentProcessor.processImageUriToBitmap(context,uri)
        try {return AiRosterDocument(name,"image/jpeg",1)}finally{bitmap.recycle()}
    }
}
