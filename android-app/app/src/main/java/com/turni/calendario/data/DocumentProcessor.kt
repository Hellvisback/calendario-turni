package com.turni.calendario.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Base64

/**
 * Service for processing documents (PDFs, images, screenshots) into high-resolution,
 * memory-efficient bitmaps and Base64 payloads optimized for the Google Gemini Flash Vision API.
 *
 * Implements:
 * 1. Native PDF page rendering via [PdfRenderer] with seekable file descriptor fallback.
 * 2. Pre-filled white canvas to eliminate transparency artifacts during JPEG compression.
 * 3. Proportional downscaling (max 2048px) and optimal [BitmapFactory.Options.inSampleSize] calculation.
 * 4. High-performance JPEG compression and Base64 encoding via [java.util.Base64] (minSdk 26+ / JVM).
 * 5. Low-memory thumbnail generation for UI previews.
 */
open class DocumentProcessor {

    companion object : DocumentProcessor() {
        const val MAX_IMAGE_DIMENSION = 2048
        const val THUMBNAIL_MAX_DIMENSION = 300
        const val DEFAULT_JPEG_QUALITY = 85
        const val DEFAULT_MIME_TYPE = "image/jpeg"

        // In-memory test hooks (used for JVM testing where Android framework classes are unmocked)
        @Volatile
        var testBitmapFallback: ((bitmap: Bitmap?, quality: Int) -> Pair<String, String>)? = null

        @Volatile
        var testImageUriDecoder: ((context: Context?, uri: Uri?) -> Bitmap)? = null

        @Volatile
        var testPdfRenderer: ((context: Context?, uri: Uri?) -> Bitmap)? = null

        @Volatile
        var testThumbnailFallback: ((bitmap: Bitmap?, maxDim: Int) -> Bitmap)? = null

        /**
         * Clears all test hooks. Call in test teardown.
         */
        fun resetTestHooks() {
            testBitmapFallback = null
            testImageUriDecoder = null
            testPdfRenderer = null
            testThumbnailFallback = null
        }
    }

    /**
     * Renders the first page of a PDF document to a high-resolution [Bitmap] using native [PdfRenderer].
     *
     * Handles SAF content URIs safely: if direct [ParcelFileDescriptor] is not seekable,
     * copies the stream to a temporary cache file. Pre-fills canvas with [Color.WHITE] so that
     * transparent PDF backgrounds do not turn black on JPEG compression.
     * All file descriptors, renderers, and temporary files are cleaned up in `finally` blocks.
     *
     * @param context Application context used to resolve content URIs and access cache directory.
     * @param pdfUri Content or file URI pointing to the PDF document.
     * @return Rendered [Bitmap] with white background and dimensions scaled up to [MAX_IMAGE_DIMENSION].
     */
    open suspend fun processPdfToBitmap(context: Context?, pdfUri: Uri?): Bitmap = withContext(Dispatchers.IO) {
        val testHook = testPdfRenderer
        if (testHook != null) {
            return@withContext testHook(context, pdfUri)
        }

        if (context == null || pdfUri == null) {
            throw IllegalArgumentException("Context e pdfUri non possono essere null")
        }

        var pfd: ParcelFileDescriptor? = null
        var tempFile: File? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null

        try {
            // Attempt 1: Try opening direct ParcelFileDescriptor from ContentResolver
            try {
                val directPfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                if (directPfd != null) {
                    try {
                        renderer = PdfRenderer(directPfd)
                        pfd = directPfd
                    } catch (_: Exception) {
                        // Non-seekable stream or unsupported provider descriptor; close and fallback
                        try { directPfd.close() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
                // Direct descriptor access failed; fallback to cache file copy
            }

            // Attempt 2 (Fallback): Copy stream to a temporary file in cacheDir
            if (renderer == null) {
                tempFile = File.createTempFile("temp_pdf_render_", ".pdf", context.cacheDir)
                val inputStream = context.contentResolver.openInputStream(pdfUri)
                    ?: throw IOException("Impossibile aprire il flusso di lettura per l'URI PDF: $pdfUri")

                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val filePfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    ?: throw IOException("Impossibile ottenere ParcelFileDescriptor per il file temporaneo: ${tempFile.absolutePath}")
                pfd = filePfd
                renderer = PdfRenderer(filePfd)
            }

            if (renderer.pageCount <= 0) {
                throw IOException("Il documento PDF non contiene pagine.")
            }

            page = renderer.openPage(0)
            val pageWidth = page.width
            val pageHeight = page.height

            if (pageWidth <= 0 || pageHeight <= 0) {
                throw IOException("Dimensioni non valide per la pagina PDF: ${pageWidth}x$pageHeight")
            }

            // High-resolution rendering scale: aim for ~2048px on the longest side (1.5x - 3.0x range)
            val scale = calculatePdfScale(pageWidth, pageHeight, MAX_IMAGE_DIMENSION)
            val targetWidth = maxOf(1, (pageWidth * scale).toInt())
            val targetHeight = maxOf(1, (pageHeight * scale).toInt())

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE) // Ensure solid white background behind transparent PDF content

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            if (tempFile != null && tempFile.exists()) {
                try { tempFile.delete() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Decodes an image from an [imageUri] calculating an optimal [BitmapFactory.Options.inSampleSize]
     * to avoid Out-Of-Memory errors on high-resolution camera photos or screenshots.
     * Scales proportionally so the longest edge does not exceed [MAX_IMAGE_DIMENSION].
     *
     * @param context Application context used to open input stream.
     * @param imageUri Image URI to decode.
     * @return Decoded and appropriately scaled [Bitmap].
     */
    open fun processImageUriToBitmap(context: Context?, imageUri: Uri?): Bitmap {
        val testHook = testImageUriDecoder
        if (testHook != null) {
            return testHook(context, imageUri)
        }

        if (context == null || imageUri == null) {
            throw IllegalArgumentException("Context e imageUri non possono essere null")
        }

        // Pass 1: Decode image bounds only to measure dimensions without memory allocation
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        val openStream: () -> InputStream = {
            context.contentResolver.openInputStream(imageUri)
                ?: throw IOException("Impossibile aprire il flusso dati per l'URI immagine: $imageUri")
        }

        openStream().use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) {
            throw IOException("Impossibile determinare le dimensioni dell'immagine: ${origWidth}x$origHeight")
        }

        // Pass 2: Calculate inSampleSize to fit within MAX_IMAGE_DIMENSION
        val sampleSize = calculateInSampleSize(origWidth, origHeight, MAX_IMAGE_DIMENSION)

        // Pass 3: Decode bitmap with sample size
        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = openStream().use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: throw IOException("Errore durante la decodifica del bitmap da $imageUri")

        // Pass 4: Fine scale down if remaining dimensions still exceed MAX_IMAGE_DIMENSION
        return scaleBitmapToMaxDimension(decodedBitmap, MAX_IMAGE_DIMENSION)
    }

    /**
     * Compresses a [Bitmap] to JPEG and converts it to a standard Base64 string payload
     * suitable for transmission to Google Gemini Flash Vision API.
     *
     * Automatically scales the bitmap down if its dimensions exceed [MAX_IMAGE_DIMENSION]
     * while strictly maintaining aspect ratio.
     *
     * @param bitmap The source bitmap to encode.
     * @param quality JPEG compression quality between 1 and 100 (defaults to [DEFAULT_JPEG_QUALITY]).
     * @return [Pair] of `(base64Data, mimeType)` with MIME type defaulting to `"image/jpeg"`.
     */
    open fun bitmapToBase64(bitmap: Bitmap?, quality: Int = DEFAULT_JPEG_QUALITY): Pair<String, String> {
        val clampedQuality = quality.coerceIn(1, 100)

        val testHook = testBitmapFallback
        if (testHook != null) {
            return testHook(bitmap, clampedQuality)
        }

        if (bitmap == null) {
            throw IllegalArgumentException("Bitmap non può essere null")
        }

        return try {
            val scaledBitmap = scaleBitmapToMaxDimension(bitmap, MAX_IMAGE_DIMENSION)
            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, clampedQuality, baos)
            val jpegBytes = baos.toByteArray()
            val base64String = Base64.getEncoder().encodeToString(jpegBytes)
            Pair(base64String, DEFAULT_MIME_TYPE)
        } catch (e: RuntimeException) {
            // Fallback for JVM unit tests where android.graphics.Bitmap methods are unmocked
            val fallback = testBitmapFallback
            if (fallback != null) {
                fallback(bitmap, clampedQuality)
            } else {
                throw e
            }
        }
    }

    /**
     * Creates a lightweight thumbnail bitmap suitable for fast UI preview rendering
     * without consuming large amounts of graphic memory.
     *
     * @param bitmap Source bitmap.
     * @param maxDim Maximum width or height of the thumbnail (defaults to [THUMBNAIL_MAX_DIMENSION]).
     * @return Scaled thumbnail [Bitmap].
     */
    open fun createThumbnail(bitmap: Bitmap?, maxDim: Int = THUMBNAIL_MAX_DIMENSION): Bitmap {
        val testHook = testThumbnailFallback
        if (testHook != null) {
            return testHook(bitmap, maxDim)
        }

        if (bitmap == null) {
            throw IllegalArgumentException("Bitmap non può essere null")
        }

        val origWidth = bitmap.width
        val origHeight = bitmap.height
        if (origWidth <= 0 || origHeight <= 0 || maxDim <= 0) return bitmap

        val (targetW, targetH) = calculateTargetDimensions(origWidth, origHeight, maxDim)
        if (targetW == origWidth && targetH == origHeight) {
            return bitmap
        }

        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    /**
     * Scales down [bitmap] so that neither width nor height exceeds [maxDim],
     * strictly preserving the original aspect ratio. If the bitmap is already within
     * the constraint, returns the original instance without creating an extra copy.
     */
    open fun scaleBitmapToMaxDimension(bitmap: Bitmap?, maxDim: Int = MAX_IMAGE_DIMENSION): Bitmap {
        if (bitmap == null) {
            throw IllegalArgumentException("Bitmap non può essere null")
        }

        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0 || maxDim <= 0) return bitmap

        val (targetW, targetH) = calculateTargetDimensions(width, height, maxDim)
        if (targetW == width && targetH == height) {
            return bitmap
        }

        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    // =========================================================================
    // Pure Mathematical & Conversion Helpers (100% JVM Testable without Mocks)
    // =========================================================================

    /**
     * Calculates proportional target dimensions preserving aspect ratio such that
     * neither width nor height exceeds [maxDim].
     *
     * @param origWidth Original image width.
     * @param origHeight Original image height.
     * @param maxDim Maximum bounding dimension (default [MAX_IMAGE_DIMENSION]).
     * @return [Pair] of `(targetWidth, targetHeight)`.
     */
    fun calculateTargetDimensions(origWidth: Int, origHeight: Int, maxDim: Int = MAX_IMAGE_DIMENSION): Pair<Int, Int> {
        require(origWidth > 0 && origHeight > 0) { "Dimensioni originali non valide: width=$origWidth, height=$origHeight" }
        require(maxDim > 0) { "maxDim deve essere positivo: $maxDim" }

        val currentMax = maxOf(origWidth, origHeight)
        if (currentMax <= maxDim) {
            return Pair(origWidth, origHeight)
        }

        val ratio = maxDim.toDouble() / currentMax.toDouble()
        val targetWidth = maxOf(1, Math.round(origWidth * ratio).toInt())
        val targetHeight = maxOf(1, Math.round(origHeight * ratio).toInt())

        return Pair(targetWidth, targetHeight)
    }

    /**
     * Calculates the optimal [BitmapFactory.Options.inSampleSize] power-of-two factor
     * to decode an image without exceeding [maxDim] while preserving maximum OCR sharpness.
     */
    fun calculateInSampleSize(origWidth: Int, origHeight: Int, maxDim: Int = MAX_IMAGE_DIMENSION): Int {
        if (origWidth <= 0 || origHeight <= 0 || maxDim <= 0) return 1
        var inSampleSize = 1
        val maxSide = maxOf(origWidth, origHeight)

        while ((maxSide / (inSampleSize * 2)) >= maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    /**
     * Calculates optimal scale factor for rendering PDF pages with high clarity
     * for vision models, constrained within [[minScale], [maxScale]].
     */
    fun calculatePdfScale(
        pageWidth: Int,
        pageHeight: Int,
        maxDim: Int = MAX_IMAGE_DIMENSION,
        minScale: Float = 1.5f,
        maxScale: Float = 3.0f
    ): Float {
        if (pageWidth <= 0 || pageHeight <= 0) return 2.0f
        val currentMax = maxOf(pageWidth, pageHeight).toFloat()
        if (currentMax <= 0f) return 2.0f
        val calculatedScale = (maxDim.toFloat() / currentMax)
        return calculatedScale.coerceIn(minScale, maxScale)
    }

    /**
     * Encodes a raw byte array to standard Base64 string using [java.util.Base64].
     */
    fun encodeBytesToBase64(bytes: ByteArray): String {
        return Base64.getEncoder().encodeToString(bytes)
    }

    /**
     * Decodes a Base64 string back to byte array using [java.util.Base64].
     */
    fun decodeBase64ToBytes(base64: String): ByteArray {
        return Base64.getDecoder().decode(base64)
    }

    /**
     * Wraps raw image bytes into a Base64 string and MIME type pair.
     */
    fun bytesToBase64(bytes: ByteArray, mimeType: String = DEFAULT_MIME_TYPE): Pair<String, String> {
        return Pair(encodeBytesToBase64(bytes), mimeType)
    }
}
