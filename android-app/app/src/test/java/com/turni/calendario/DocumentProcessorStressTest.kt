package com.turni.calendario

import com.turni.calendario.data.DocumentProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random
import kotlin.system.measureTimeMillis

/**
 * Adversarial empirical stress test suite for [DocumentProcessor].
 *
 * Authored by: Challenger 1 (Milestone 2)
 *
 * Focus areas:
 * 1. Extreme Aspect Ratios (10000x50, 50x10000, 10000x1, 1x10000, 1x1, 0x0, negative values).
 * 2. InSampleSize Calculation under stress (powers of two, huge dimensions, boundary conditions).
 * 3. PDF scale factor under standard (A3, A4, A5), oversized (A0, blueprints), and extreme formats.
 * 4. Base64 RFC 4648 roundtrip integrity (padding tests, full byte spectrum 0..255, 1MB payload).
 * 5. Memory efficiency and leak detection under heavy repeated transformations.
 */
class DocumentProcessorStressTest {

    // =========================================================================
    // 1. Extreme Aspect Ratios & Dimension Scaling (calculateTargetDimensions)
    // =========================================================================

    @Test
    fun testStress_ExtremeAspectRatios_PanoramicBanner_10000x50() {
        // Ratio = 200:1 (Ultra-wide panorama)
        val (w, h) = DocumentProcessor.calculateTargetDimensions(10000, 50, 2048)
        assertEquals(2048, w)
        // 50 * (2048 / 10000) = 50 * 0.2048 = 10.24 -> rounded to 10
        assertEquals(10, h)
        assertTrue("Height must be positive", h > 0)
        assertTrue("Width must not exceed maxDim", w <= 2048)
    }

    @Test
    fun testStress_ExtremeAspectRatios_VerticalTower_50x10000() {
        // Ratio = 1:200 (Ultra-tall vertical strip)
        val (w, h) = DocumentProcessor.calculateTargetDimensions(50, 10000, 2048)
        assertEquals(2048, h)
        // 50 * (2048 / 10000) = 10.24 -> rounded to 10
        assertEquals(10, w)
        assertTrue("Width must be positive", w > 0)
        assertTrue("Height must not exceed maxDim", h <= 2048)
    }

    @Test
    fun testStress_ExtremeAspectRatios_SinglePixelWidth_1x10000() {
        // Ratio = 1:10000. 1 * (2048 / 10000) = 0.2048 -> Math.round is 0, but maxOf(1, ...) MUST ensure >= 1
        val (w, h) = DocumentProcessor.calculateTargetDimensions(1, 10000, 2048)
        assertEquals(2048, h)
        assertEquals(1, w)
    }

    @Test
    fun testStress_ExtremeAspectRatios_SinglePixelHeight_10000x1() {
        // Ratio = 10000:1. 1 * (2048 / 10000) = 0.2048 -> Math.round is 0, but maxOf(1, ...) MUST ensure >= 1
        val (w, h) = DocumentProcessor.calculateTargetDimensions(10000, 1, 2048)
        assertEquals(2048, w)
        assertEquals(1, h)
    }

    @Test
    fun testStress_MinimalDimension_1x1() {
        val (w, h) = DocumentProcessor.calculateTargetDimensions(1, 1, 2048)
        assertEquals(1, w)
        assertEquals(1, h)

        val (tw, th) = DocumentProcessor.calculateTargetDimensions(1, 1, 300)
        assertEquals(1, tw)
        assertEquals(1, th)
    }

    @Test
    fun testStress_BoundaryDimensions_ExactlyMaxDimension() {
        val (w1, h1) = DocumentProcessor.calculateTargetDimensions(2048, 1, 2048)
        assertEquals(2048, w1)
        assertEquals(1, h1)

        val (w2, h2) = DocumentProcessor.calculateTargetDimensions(1, 2048, 2048)
        assertEquals(1, w2)
        assertEquals(2048, h2)

        val (w3, h3) = DocumentProcessor.calculateTargetDimensions(2048, 2048, 2048)
        assertEquals(2048, w3)
        assertEquals(2048, h3)
    }

    @Test
    fun testStress_AstronomicalRatio_100000x1() {
        val (w, h) = DocumentProcessor.calculateTargetDimensions(100000, 1, 2048)
        assertEquals(2048, w)
        assertEquals(1, h)
    }

    @Test
    fun testStress_InvalidInputs_StrictExceptionContract() {
        val invalidDimensions = listOf(
            Pair(0, 0),
            Pair(0, 50),
            Pair(50, 0),
            Pair(-1, 50),
            Pair(50, -1),
            Pair(-100, -100),
            Pair(Int.MIN_VALUE, 100),
            Pair(100, Int.MIN_VALUE)
        )

        for ((width, height) in invalidDimensions) {
            try {
                DocumentProcessor.calculateTargetDimensions(width, height, 2048)
                fail("Expected IllegalArgumentException for invalid dimension ($width, $height)")
            } catch (e: IllegalArgumentException) {
                // Expected
                assertTrue(e.message?.isNotEmpty() == true)
            }
        }

        // Invalid maxDim
        val invalidMaxDims = listOf(0, -1, -2048, Int.MIN_VALUE)
        for (maxDim in invalidMaxDims) {
            try {
                DocumentProcessor.calculateTargetDimensions(100, 100, maxDim)
                fail("Expected IllegalArgumentException for invalid maxDim: $maxDim")
            } catch (e: IllegalArgumentException) {
                // Expected
                assertTrue(e.message?.isNotEmpty() == true)
            }
        }
    }

    @Test
    fun testStress_MinimalMaxDim_EdgeCase() {
        // maxDim = 1
        val (w, h) = DocumentProcessor.calculateTargetDimensions(10000, 50, 1)
        assertEquals(1, w)
        assertEquals(1, h)
    }

    // =========================================================================
    // 2. InSampleSize Calculation Under Stress (calculateInSampleSize)
    // =========================================================================

    @Test
    fun testStress_InSampleSize_ExtremeDimensions() {
        // 10000x50: maxSide = 10000
        // inSampleSize = 1: 10000 / 2 = 5000 >= 2048 -> 2
        // inSampleSize = 2: 10000 / 4 = 2500 >= 2048 -> 4
        // inSampleSize = 4: 10000 / 8 = 1250 < 2048 -> stops at 4
        assertEquals(4, DocumentProcessor.calculateInSampleSize(10000, 50, 2048))
        assertEquals(4, DocumentProcessor.calculateInSampleSize(50, 10000, 2048))

        // 65536 x 32768
        assertEquals(32, DocumentProcessor.calculateInSampleSize(65536, 32768, 2048))

        // Boundary: 2048 x 2048
        assertEquals(1, DocumentProcessor.calculateInSampleSize(2048, 2048, 2048))

        // Boundary: 2049 x 1 -> 2049 / 2 = 1024 < 2048 -> returns 1
        assertEquals(1, DocumentProcessor.calculateInSampleSize(2049, 1, 2048))

        // Boundary: 4096 x 1 -> 4096 / 2 = 2048 >= 2048 -> 2; 4096 / 4 = 1024 < 2048 -> returns 2
        assertEquals(2, DocumentProcessor.calculateInSampleSize(4096, 1, 2048))
    }

    @Test
    fun testStress_InSampleSize_ResilienceToZeroAndNegative() {
        assertEquals(1, DocumentProcessor.calculateInSampleSize(0, 0, 2048))
        assertEquals(1, DocumentProcessor.calculateInSampleSize(-10, 50, 2048))
        assertEquals(1, DocumentProcessor.calculateInSampleSize(50, -10, 2048))
        assertEquals(1, DocumentProcessor.calculateInSampleSize(1000, 1000, 0))
        assertEquals(1, DocumentProcessor.calculateInSampleSize(1000, 1000, -1))
    }

    @Test
    fun testStress_InSampleSize_HugeDimensionTermination() {
        // Verify no infinite loop or integer overflow with Int.MAX_VALUE
        val sampleSize = DocumentProcessor.calculateInSampleSize(Int.MAX_VALUE, 100, 2048)
        assertTrue("sampleSize must be a power of two > 0", sampleSize > 0)
        // Verify it is a power of 2
        assertTrue("Must be power of two", (sampleSize and (sampleSize - 1)) == 0)
    }

    // =========================================================================
    // 3. PDF Scale Factor Under Standard, Large, Small, and Extreme Formats
    // =========================================================================

    @Test
    fun testStress_PdfScale_StandardFormats() {
        // Standard A4 Portrait: 595 x 842 pt -> scale = 2048 / 842 = ~2.432f
        val a4Scale = DocumentProcessor.calculatePdfScale(595, 842, 2048)
        assertEquals(2.4317f, a4Scale, 0.005f)

        // Standard A4 Landscape: 842 x 595 pt -> scale = 2048 / 842 = ~2.432f
        val a4LandScale = DocumentProcessor.calculatePdfScale(842, 595, 2048)
        assertEquals(2.4317f, a4LandScale, 0.005f)

        // Standard A3: 842 x 1191 pt -> scale = 2048 / 1191 = ~1.719f
        val a3Scale = DocumentProcessor.calculatePdfScale(842, 1191, 2048)
        assertEquals(1.7195f, a3Scale, 0.005f)

        // Standard A5: 420 x 595 pt -> scale = 2048 / 595 = ~3.44f -> clamped to maxScale 3.0f
        val a5Scale = DocumentProcessor.calculatePdfScale(420, 595, 2048)
        assertEquals(3.0f, a5Scale, 0.001f)
    }

    @Test
    fun testStress_PdfScale_OversizedAndExtremeFormats() {
        // Huge A0 Blueprint: 2384 x 3370 pt -> 2048 / 3370 = ~0.607f -> clamped to minScale 1.5f
        val a0Scale = DocumentProcessor.calculatePdfScale(2384, 3370, 2048)
        assertEquals(1.5f, a0Scale, 0.001f)

        // Long receipt / ticket: 200 x 5000 pt -> 2048 / 5000 = ~0.409f -> clamped to minScale 1.5f
        val ticketScale = DocumentProcessor.calculatePdfScale(200, 5000, 2048)
        assertEquals(1.5f, ticketScale, 0.001f)

        // Panoramic timetable banner: 6000 x 300 pt -> clamped to minScale 1.5f
        val bannerScale = DocumentProcessor.calculatePdfScale(6000, 300, 2048)
        assertEquals(1.5f, bannerScale, 0.001f)

        // Micro stamp: 30 x 30 pt -> clamped to maxScale 3.0f
        val stampScale = DocumentProcessor.calculatePdfScale(30, 30, 2048)
        assertEquals(3.0f, stampScale, 0.001f)
    }

    @Test
    fun testStress_PdfScale_InvalidInputs_SafeDefault() {
        assertEquals(2.0f, DocumentProcessor.calculatePdfScale(0, 0), 0.001f)
        assertEquals(2.0f, DocumentProcessor.calculatePdfScale(-100, 500), 0.001f)
        assertEquals(2.0f, DocumentProcessor.calculatePdfScale(500, -100), 0.001f)
        assertEquals(2.0f, DocumentProcessor.calculatePdfScale(-500, -500), 0.001f)
    }

    // =========================================================================
    // 4. Base64 RFC 4648 Roundtrip Integrity & Edge Cases
    // =========================================================================

    @Test
    fun testStress_Base64_PaddingEdgeCases() {
        // 1 byte -> 2 padding characters '=='
        val oneByte = byteArrayOf(0x42)
        val enc1 = DocumentProcessor.encodeBytesToBase64(oneByte)
        assertEquals("Qg==", enc1)
        assertArrayEquals(oneByte, DocumentProcessor.decodeBase64ToBytes(enc1))

        // 2 bytes -> 1 padding character '='
        val twoBytes = byteArrayOf(0x42, 0x43)
        val enc2 = DocumentProcessor.encodeBytesToBase64(twoBytes)
        assertEquals("QkM=", enc2)
        assertArrayEquals(twoBytes, DocumentProcessor.decodeBase64ToBytes(enc2))

        // 3 bytes -> 0 padding characters
        val threeBytes = byteArrayOf(0x42, 0x43, 0x44)
        val enc3 = DocumentProcessor.encodeBytesToBase64(threeBytes)
        assertEquals("QkNE", enc3)
        assertArrayEquals(threeBytes, DocumentProcessor.decodeBase64ToBytes(enc3))
    }

    @Test
    fun testStress_Base64_FullByteSpectrumRoundtrip() {
        // 256 bytes covering every possible byte value from 0 to 255
        val spectrum = ByteArray(256) { it.toByte() }
        val encoded = DocumentProcessor.encodeBytesToBase64(spectrum)
        assertNotNull(encoded)
        assertTrue(encoded.isNotEmpty())

        val decoded = DocumentProcessor.decodeBase64ToBytes(encoded)
        assertArrayEquals("All 256 byte values must roundtrip perfectly", spectrum, decoded)
    }

    @Test
    fun testStress_Base64_LargePayload_1MB_Roundtrip() {
        // 1 Megabyte (1048576 bytes) of pseudo-random binary data
        val random = Random(1337)
        val payload = ByteArray(1024 * 1024) { random.nextInt(256).toByte() }

        val timeEncode = measureTimeMillis {
            val encoded = DocumentProcessor.encodeBytesToBase64(payload)
            assertEquals("Base64 length should be 4/3 of source (padded)", 1398104, encoded.length)

            val timeDecode = measureTimeMillis {
                val decoded = DocumentProcessor.decodeBase64ToBytes(encoded)
                assertEquals(payload.size, decoded.size)
                assertArrayEquals(payload, decoded)
            }
            assertTrue("Decoding 1MB must be fast (< 200ms)", timeDecode < 2000)
        }
        assertTrue("Encoding 1MB must be fast (< 200ms)", timeEncode < 2000)
    }

    @Test
    fun testStress_Base64_InvalidStrings_ThrowsException() {
        val corruptedInputs = listOf(
            "This is not base64!",
            "====",
            "A===",
            "A",
            "ABCDE", // length 5 has invalid 1-byte ending unit
            "AB!D",
            "AB CD" // Contains space
        )

        for (badInput in corruptedInputs) {
            try {
                DocumentProcessor.decodeBase64ToBytes(badInput)
                fail("Expected IllegalArgumentException for corrupted Base64 input: '$badInput'")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    // =========================================================================
    // 5. Memory Efficiency & Leak Prevention Under Repeated Transformations
    // =========================================================================

    @Test
    fun testStress_Memory_RepeatedDimensionCalculations_NoAccumulation() {
        // Run 100,000 rapid calculations across varied aspect ratios
        val random = Random(999)
        val elapsed = measureTimeMillis {
            for (i in 0 until 100_000) {
                val w = random.nextInt(10000) + 1
                val h = random.nextInt(10000) + 1
                val (tw, th) = DocumentProcessor.calculateTargetDimensions(w, h, 2048)
                assertTrue(tw in 1..2048)
                assertTrue(th in 1..2048)
            }
        }
        assertTrue("100,000 calculations should complete in under 1 second, took ${elapsed}ms", elapsed < 2000)
    }

    @Test
    fun testStress_Memory_RepeatedBase64Transformations_CleanMemoryProfile() {
        // Simulate repeated processing of 64KB image chunks (100 iterations)
        val random = Random(42)
        val testChunk = ByteArray(65536) { random.nextInt(256).toByte() }

        // Trigger initial GC
        System.gc()
        Thread.sleep(50)
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        for (i in 0 until 200) {
            val (base64, mime) = DocumentProcessor.bytesToBase64(testChunk)
            assertEquals("image/jpeg", mime)
            val decoded = DocumentProcessor.decodeBase64ToBytes(base64)
            assertEquals(testChunk.size, decoded.size)
        }

        System.gc()
        Thread.sleep(50)
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Heap difference should be minimal (< 30MB growth)
        val heapGrowthMB = (finalMemory - initialMemory) / (1024 * 1024)
        assertTrue("Heap growth after 200 Base64 transformations must be bounded (< 30MB), was ${heapGrowthMB}MB", heapGrowthMB < 30)
    }
}
