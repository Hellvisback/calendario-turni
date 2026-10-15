package com.turni.calendario

import com.turni.calendario.data.DocumentProcessor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Random

/**
 * Unit test suite for [DocumentProcessor] validating:
 * 1. Proportional scaling dimension calculations (max 2048px, thumbnails 300px, aspect ratios).
 * 2. Optimal inSampleSize calculations for high-resolution images avoiding memory issues & OCR blur.
 * 3. High-resolution PDF rendering scale factor calculations for A4 and custom documents.
 * 4. Java 8 / Android 26+ Base64 encoding/decoding integrity and MIME type specifications.
 * 5. Compression quality clamping and error resilience.
 * 6. Test fallback hooks for JVM test runner execution.
 */
class DocumentProcessorTest {

    @Before
    fun setUp() {
        DocumentProcessor.resetTestHooks()
    }

    @After
    fun tearDown() {
        DocumentProcessor.resetTestHooks()
    }

    // =========================================================================
    // 1. Proportional Scaling Dimensions (calculateTargetDimensions)
    // =========================================================================

    @Test
    fun testScaleDimensions_SmallerThanMax_Unchanged() {
        // Image already smaller than 2048px on all sides should not be resized
        val (w, h) = DocumentProcessor.calculateTargetDimensions(1024, 768, 2048)
        assertEquals(1024, w)
        assertEquals(768, h)
    }

    @Test
    fun testScaleDimensions_ExactlyMax_Unchanged() {
        // Image exactly 2048x1536
        val (w, h) = DocumentProcessor.calculateTargetDimensions(2048, 1536, 2048)
        assertEquals(2048, w)
        assertEquals(1536, h)
    }

    @Test
    fun testScaleDimensions_Landscape_ScaledProportionally() {
        // 4096 x 2048 scaled with maxDim 2048 -> 2048 x 1024 (ratio 0.5)
        val (w, h) = DocumentProcessor.calculateTargetDimensions(4096, 2048, 2048)
        assertEquals(2048, w)
        assertEquals(1024, h)
    }

    @Test
    fun testScaleDimensions_Portrait_ScaledProportionally() {
        // 1500 x 3000 scaled with maxDim 2048 -> ratio = 2048 / 3000 = 0.682667 -> 1024 x 2048
        val (w, h) = DocumentProcessor.calculateTargetDimensions(1500, 3000, 2048)
        assertEquals(1024, w)
        assertEquals(2048, h)
    }

    @Test
    fun testScaleDimensions_Square_ScaledToMax() {
        // 3500 x 3500 scaled with maxDim 2048 -> 2048 x 2048
        val (w, h) = DocumentProcessor.calculateTargetDimensions(3500, 3500, 2048)
        assertEquals(2048, w)
        assertEquals(2048, h)
    }

    @Test
    fun testScaleDimensions_ThumbnailCalculation() {
        // 1920 x 1080 scaled with maxDim 300 -> ratio = 300 / 1920 -> 300 x 169
        val (w, h) = DocumentProcessor.calculateTargetDimensions(1920, 1080, 300)
        assertEquals(300, w)
        assertEquals(169, h)
    }

    @Test
    fun testScaleDimensions_ExtremeAspectRatio_HeightNeverZero() {
        // Extremely wide panoramic banner: 10000 x 4 scaled to max 2048
        val (w, h) = DocumentProcessor.calculateTargetDimensions(10000, 4, 2048)
        assertEquals(2048, w)
        assertTrue("Height must be at least 1 pixel", h >= 1)
    }

    @Test
    fun testScaleDimensions_InvalidInputs_ThrowsException() {
        try {
            DocumentProcessor.calculateTargetDimensions(0, 500, 2048)
            fail("Should throw IllegalArgumentException for zero width")
        } catch (_: IllegalArgumentException) {}

        try {
            DocumentProcessor.calculateTargetDimensions(500, -10, 2048)
            fail("Should throw IllegalArgumentException for negative height")
        } catch (_: IllegalArgumentException) {}

        try {
            DocumentProcessor.calculateTargetDimensions(500, 500, 0)
            fail("Should throw IllegalArgumentException for zero maxDim")
        } catch (_: IllegalArgumentException) {}
    }

    // =========================================================================
    // 2. InSampleSize Calculation (calculateInSampleSize)
    // =========================================================================

    @Test
    fun testInSampleSize_SmallerThanMax_ReturnsOne() {
        val sampleSize = DocumentProcessor.calculateInSampleSize(1600, 1200, 2048)
        assertEquals(1, sampleSize)
    }

    @Test
    fun testInSampleSize_ExactlyMaxDim_ReturnsOne() {
        val sampleSize = DocumentProcessor.calculateInSampleSize(2048, 2048, 2048)
        assertEquals(1, sampleSize)
    }

    @Test
    fun testInSampleSize_SubSamplingPreservesSharpness() {
        // 3500 x 2000: 3500 / 2 = 1750 < 2048, so inSampleSize = 1 is preserved
        // to avoid undersampling and losing OCR text legibility
        val sampleSize = DocumentProcessor.calculateInSampleSize(3500, 2000, 2048)
        assertEquals(1, sampleSize)
    }

    @Test
    fun testInSampleSize_HighResPhoto_ReturnsPowerOfTwo() {
        // 8192 x 4096: 8192 / (1*2) = 4096 >= 2048 -> 2; 8192 / (2*2) = 2048 >= 2048 -> 4; 8192 / (4*2) = 1024 < 2048 -> stop
        val sampleSize = DocumentProcessor.calculateInSampleSize(8192, 4096, 2048)
        assertEquals(4, sampleSize)
    }

    @Test
    fun testInSampleSize_ExtremeResolution_SafePowerOfTwo() {
        // 16384 x 8192
        val sampleSize = DocumentProcessor.calculateInSampleSize(16384, 8192, 2048)
        assertEquals(8, sampleSize)
    }

    @Test
    fun testInSampleSize_InvalidDimensions_ReturnsOne() {
        assertEquals(1, DocumentProcessor.calculateInSampleSize(0, 1000, 2048))
        assertEquals(1, DocumentProcessor.calculateInSampleSize(-100, 500, 2048))
        assertEquals(1, DocumentProcessor.calculateInSampleSize(1000, 1000, -10))
    }

    // =========================================================================
    // 3. PDF Rendering Scale Factor (calculatePdfScale)
    // =========================================================================

    @Test
    fun testPdfScale_StandardA4Portrait() {
        // Standard A4 at 72 DPI: 595 x 842 points
        // Scale = 2048 / 842 = ~2.432f
        val scale = DocumentProcessor.calculatePdfScale(595, 842, 2048)
        assertTrue("Scale should be approx 2.43f, was $scale", scale in 2.40f..2.45f)
        assertTrue("Scale must be in [1.5, 3.0]", scale in 1.5f..3.0f)
    }

    @Test
    fun testPdfScale_StandardA4Landscape() {
        // Standard A4 landscape: 842 x 595 points
        val scale = DocumentProcessor.calculatePdfScale(842, 595, 2048)
        assertTrue("Scale should be approx 2.43f, was $scale", scale in 2.40f..2.45f)
    }

    @Test
    fun testPdfScale_SmallDocument_ClampedToMaxScale() {
        // Very small document: 200 x 200 pt -> 2048 / 200 = 10.24 -> clamped to maxScale 3.0f
        val scale = DocumentProcessor.calculatePdfScale(200, 200, 2048)
        assertEquals(3.0f, scale, 0.001f)
    }

    @Test
    fun testPdfScale_HugeDocument_ClampedToMinScale() {
        // Huge blueprint: 4000 x 4000 pt -> 2048 / 4000 = 0.512 -> clamped to minScale 1.5f
        val scale = DocumentProcessor.calculatePdfScale(4000, 4000, 2048)
        assertEquals(1.5f, scale, 0.001f)
    }

    @Test
    fun testPdfScale_InvalidInputs_DefaultsToTwo() {
        assertEquals(2.0f, DocumentProcessor.calculatePdfScale(0, 800), 0.001f)
        assertEquals(2.0f, DocumentProcessor.calculatePdfScale(-500, -500), 0.001f)
    }

    // =========================================================================
    // 4. Base64 Encoding & Decoding Integrity (RFC 4648)
    // =========================================================================

    @Test
    fun testBase64_EncodeAndDecode_Roundtrip() {
        val testData = "CalediarioTurni_GeminiVision_TestPayload_12345!@#$".toByteArray(Charsets.UTF_8)
        val encoded = DocumentProcessor.encodeBytesToBase64(testData)
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        val decoded = DocumentProcessor.decodeBase64ToBytes(encoded)
        assertArrayEquals(testData, decoded)
    }

    @Test
    fun testBase64_EmptyBytes() {
        val empty = ByteArray(0)
        val encoded = DocumentProcessor.encodeBytesToBase64(empty)
        assertEquals("", encoded)

        val decoded = DocumentProcessor.decodeBase64ToBytes("")
        assertEquals(0, decoded.size)
    }

    @Test
    fun testBase64_RealisticJpegPayload() {
        // Simulate a JPEG byte buffer with SOI (0xFF, 0xD8) and random body
        val random = Random(42)
        val jpegBytes = ByteArray(10240) { random.nextInt(256).toByte() }
        jpegBytes[0] = 0xFF.toByte()
        jpegBytes[1] = 0xD8.toByte()

        val (base64, mimeType) = DocumentProcessor.bytesToBase64(jpegBytes)
        assertEquals("image/jpeg", mimeType)
        assertTrue(base64.isNotEmpty())

        // Validate decoded payload matches original byte for byte
        val decoded = DocumentProcessor.decodeBase64ToBytes(base64)
        assertArrayEquals(jpegBytes, decoded)
        assertEquals(0xFF.toByte(), decoded[0])
        assertEquals(0xD8.toByte(), decoded[1])
    }

    @Test
    fun testBase64_CustomMimeType() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val (base64, mimeType) = DocumentProcessor.bytesToBase64(bytes, "image/png")
        assertEquals("image/png", mimeType)
        assertEquals("AQIDBA==", base64)
    }

    @Test
    fun testBase64_InvalidCharacters_ThrowsException() {
        try {
            DocumentProcessor.decodeBase64ToBytes("This is not valid Base64!!!")
            fail("Should throw IllegalArgumentException on invalid base64")
        } catch (_: IllegalArgumentException) {}
    }

    // =========================================================================
    // 5. Quality Clamping & Fallback Test Hooks
    // =========================================================================

    @Test
    fun testBitmapToBase64_QualityClampingInHook() {
        var observedQuality = -1
        DocumentProcessor.testBitmapFallback = { _, quality ->
            observedQuality = quality
            Pair("fake_base64", "image/jpeg")
        }

        // Quality 150 should be clamped to 100
        DocumentProcessor.bitmapToBase64(null, 150)
        assertEquals(100, observedQuality)

        // Quality -20 should be clamped to 1
        DocumentProcessor.bitmapToBase64(null, -20)
        assertEquals(1, observedQuality)

        // Quality 85 remains 85
        DocumentProcessor.bitmapToBase64(null, 85)
        assertEquals(85, observedQuality)
    }

    @Test
    fun testBitmapToBase64_NullBitmapWithoutHook_ThrowsException() {
        try {
            DocumentProcessor.bitmapToBase64(null, 85)
            fail("Expected IllegalArgumentException when bitmap is null without hook")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun testProcessImageUri_NullInputs_ThrowsException() {
        try {
            DocumentProcessor.processImageUriToBitmap(null, null)
            fail("Expected IllegalArgumentException for null context and uri")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun testProcessImageUri_TestHookInvoked() {
        var hookCalled = false
        DocumentProcessor.testImageUriDecoder = { _, _ ->
            hookCalled = true
            throw IllegalStateException("IMAGE_HOOK_SUCCESS")
        }

        try {
            DocumentProcessor.processImageUriToBitmap(null, null)
            fail("Should have called hook")
        } catch (e: IllegalStateException) {
            assertEquals("IMAGE_HOOK_SUCCESS", e.message)
        }
        assertTrue("Test image URI hook should be invoked", hookCalled)
    }

    @Test
    fun testProcessPdfToBitmap_NullInputs_ThrowsException() = runBlocking {
        try {
            DocumentProcessor.processPdfToBitmap(null, null)
            fail("Expected IllegalArgumentException for null context and uri")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun testProcessPdfToBitmap_TestHookInvoked() = runBlocking {
        var hookCalled = false
        DocumentProcessor.testPdfRenderer = { _, _ ->
            hookCalled = true
            throw IllegalStateException("PDF_HOOK_SUCCESS")
        }

        try {
            DocumentProcessor.processPdfToBitmap(null, null)
            fail("Should have called hook")
        } catch (e: IllegalStateException) {
            assertEquals("PDF_HOOK_SUCCESS", e.message)
        }
        assertTrue("Test PDF renderer hook should be invoked", hookCalled)
    }

    @Test
    fun testCreateThumbnail_WithTestHook() {
        var hookCalled = false
        DocumentProcessor.testThumbnailFallback = { _, dim ->
            hookCalled = true
            assertEquals(150, dim)
            throw IllegalStateException("THUMB_HOOK_SUCCESS")
        }

        try {
            DocumentProcessor.createThumbnail(null, 150)
            fail("Should have called thumbnail hook")
        } catch (e: IllegalStateException) {
            assertEquals("THUMB_HOOK_SUCCESS", e.message)
        }
        assertTrue("Test thumbnail hook should be invoked", hookCalled)
    }

    @Test
    fun testResetTestHooks_ClearsAllHooks() {
        DocumentProcessor.testBitmapFallback = { _, _ -> Pair("a", "b") }
        DocumentProcessor.testImageUriDecoder = { _, _ -> throw Exception() }
        DocumentProcessor.testPdfRenderer = { _, _ -> throw Exception() }
        DocumentProcessor.testThumbnailFallback = { _, _ -> throw Exception() }

        DocumentProcessor.resetTestHooks()

        org.junit.Assert.assertNull(DocumentProcessor.testBitmapFallback)
        org.junit.Assert.assertNull(DocumentProcessor.testImageUriDecoder)
        org.junit.Assert.assertNull(DocumentProcessor.testPdfRenderer)
        org.junit.Assert.assertNull(DocumentProcessor.testThumbnailFallback)
    }
}
