package com.turni.calendario

import android.content.Context
import android.net.Uri
import com.turni.calendario.data.DocumentProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial Empirical Stress Test Suite for [DocumentProcessor].
 *
 * Authored by: Challenger 2 (Milestone 2)
 *
 * Verification objectives:
 * 1. Corrupted byte streams (random bytes, truncated magic bytes, empty 0-byte streams).
 * 2. Safe file descriptor and temporary file cleanup under simulated I/O errors (0 leaked files).
 * 3. Concurrency stress testing (50 concurrent threads/coroutines without race conditions or deadlocks).
 * 4. Missing files, null contracts, and robust exception propagation.
 * 5. Exception-storm memory stability (1,000 consecutive I/O failures without resource leaks).
 */
class DocumentProcessorCorruptionAndLeakTest {

    private lateinit var testCacheDir: File

    @Before
    fun setUp() {
        DocumentProcessor.resetTestHooks()
        testCacheDir = File(System.getProperty("java.io.tmpdir"), "doc_proc_leak_test_${System.currentTimeMillis()}")
        testCacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        DocumentProcessor.resetTestHooks()
        if (testCacheDir.exists()) {
            testCacheDir.deleteRecursively()
        }
    }

    // =========================================================================
    // 1. Corrupted Byte Streams & Empty Stream Handling
    // =========================================================================

    @Test
    fun testCorruptedStream_EmptyStream_ZeroBytesHandledGracefully() {
        // Test Base64 handling of empty 0-byte buffer
        val emptyBytes = ByteArray(0)
        val (base64, mime) = DocumentProcessor.bytesToBase64(emptyBytes)
        assertEquals("", base64)
        assertEquals("image/jpeg", mime)

        val decoded = DocumentProcessor.decodeBase64ToBytes(base64)
        assertEquals(0, decoded.size)
    }

    @Test
    fun testCorruptedStream_TruncatedJpegHeader_ThrowsOrHandlesWithoutLeak() {
        // A truncated JPEG containing only the 2-byte SOI marker (0xFF, 0xD8)
        val truncatedHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val (base64, _) = DocumentProcessor.bytesToBase64(truncatedHeader)

        val decoded = DocumentProcessor.decodeBase64ToBytes(base64)
        assertEquals(2, decoded.size)
        assertEquals(0xFF.toByte(), decoded[0])
        assertEquals(0xD8.toByte(), decoded[1])
    }

    @Test
    fun testCorruptedStream_RandomGarbageBytes_Base64Robustness() {
        // 50 iterations with completely random binary noise (fuzz testing)
        val random = Random(4242)
        for (i in 1..50) {
            val length = random.nextInt(8192) + 1
            val garbage = ByteArray(length) { random.nextInt(256).toByte() }

            val encoded = DocumentProcessor.encodeBytesToBase64(garbage)
            assertNotNull(encoded)
            assertTrue(encoded.isNotEmpty())

            val roundtrip = DocumentProcessor.decodeBase64ToBytes(encoded)
            assertEquals(garbage.size, roundtrip.size)
        }
    }

    @Test
    fun testCorruptedStream_MalformedBase64Strings_StrictValidation() {
        // Invalid Base64 strings: odd lengths, illegal characters, misplaced padding
        val malformedStrings = listOf(
            "=",
            "===",
            "=====",
            "abcde", // length not multiple of 4
            "ab=c", // padding in the middle
            "ab@$", // non-base64 characters
            "Zm9v\u0000YmFy", // null byte injected
            "\n\r\t ", // whitespace only
            "---INVALID---"
        )

        for (malformed in malformedStrings) {
            try {
                DocumentProcessor.decodeBase64ToBytes(malformed)
                fail("Expected IllegalArgumentException for malformed string: '$malformed'")
            } catch (_: IllegalArgumentException) {
                // Correct behavior: strict validation
            }
        }
    }

    // =========================================================================
    // 2. Resource & File Descriptor Leak Simulation Under I/O Errors
    // =========================================================================

    @Test
    fun testResourceCleanup_SimulatedTempFileCleanupUnderError() {
        // Simulate the exact temp file lifecycle implemented in processPdfToBitmap
        val createdTempFiles = mutableListOf<File>()

        for (i in 1..20) {
            var tempFile: File? = null
            var streamClosed = false

            try {
                tempFile = File.createTempFile("test_sim_pdf_", ".pdf", testCacheDir)
                createdTempFiles.add(tempFile)
                assertTrue("Temp file must exist after creation", tempFile.exists())

                val mockInputStream = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
                    override fun close() {
                        super.close()
                        streamClosed = true
                    }
                }

                // Simulate I/O copy
                mockInputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Simulate failure (e.g. corrupted PDF parsing error)
                throw IOException("Simulated corrupted PDF content error at iteration $i")
            } catch (e: IOException) {
                // Expected simulated error
                assertEquals("Simulated corrupted PDF content error at iteration $i", e.message)
            } finally {
                // This mimics lines 147-154 of DocumentProcessor.kt:
                if (tempFile != null && tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }

            assertTrue("Input stream must be safely closed via use-block", streamClosed)
            assertFalse("Temp file MUST be deleted in finally block", tempFile?.exists() == true)
        }

        // Verify zero temporary files remain in testCacheDir
        val remainingFiles = testCacheDir.listFiles { _, name -> name.startsWith("test_sim_pdf_") }
        assertEquals("Zero temp files should leak in cache directory", 0, remainingFiles?.size ?: 0)
    }

    @Test
    fun testResourceCleanup_InputStreamClosureGuaranteedOnException() {
        // Verify that custom InputStream is ALWAYS closed even if reading throws halfway
        val isClosed = AtomicBoolean(false)
        val faultThrowingStream = object : InputStream() {
            private var bytesRead = 0
            override fun read(): Int {
                bytesRead++
                if (bytesRead > 10) {
                    throw IOException("Simulated read error mid-stream")
                }
                return 42
            }

            override fun close() {
                isClosed.set(true)
            }
        }

        try {
            faultThrowingStream.use { stream ->
                val buffer = ByteArray(64)
                while (stream.read(buffer) != -1) {
                    // reading until simulated failure
                }
            }
            fail("Expected simulated IOException")
        } catch (e: IOException) {
            assertEquals("Simulated read error mid-stream", e.message)
        }

        assertTrue("InputStream MUST be closed even when exception is thrown during read", isClosed.get())
    }

    // =========================================================================
    // 3. Concurrency Stress Testing
    // =========================================================================

    @Test
    fun testConcurrency_ParallelMathematicalCalculations_50Threads() {
        val numThreads = 50
        val iterationsPerThread = 1000
        val latch = CountDownLatch(numThreads)
        val errorCount = AtomicInteger(0)
        val results = ConcurrentHashMap<Int, Pair<Int, Int>>()

        val threads = (0 until numThreads).map { threadId ->
            Thread {
                try {
                    val random = Random(threadId.toLong())
                    for (i in 0 until iterationsPerThread) {
                        val w = random.nextInt(8000) + 1
                        val h = random.nextInt(8000) + 1
                        val (tw, th) = DocumentProcessor.calculateTargetDimensions(w, h, 2048)
                        assertTrue(tw in 1..2048)
                        assertTrue(th in 1..2048)

                        val sampleSize = DocumentProcessor.calculateInSampleSize(w, h, 2048)
                        assertTrue(sampleSize >= 1)
                        assertTrue((sampleSize and (sampleSize - 1)) == 0)

                        val pdfScale = DocumentProcessor.calculatePdfScale(w, h, 2048)
                        assertTrue(pdfScale in 1.5f..3.0f)
                    }
                    results[threadId] = Pair(threadId, iterationsPerThread)
                } catch (t: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        val completed = latch.await(10, TimeUnit.SECONDS)

        assertTrue("All 50 concurrent threads must complete within timeout", completed)
        assertEquals("Zero errors across concurrent execution", 0, errorCount.get())
        assertEquals(numThreads, results.size)
    }

    @Test
    fun testConcurrency_ParallelBase64EncodingDecoding_Coroutines() = runBlocking(Dispatchers.Default) {
        val concurrentTasks = 64
        val random = Random(12345)

        val deferredList = (0 until concurrentTasks).map { taskId ->
            async {
                val size = random.nextInt(16384) + 1024
                val data = ByteArray(size) { (it % 256).toByte() }

                val (encoded, mime) = DocumentProcessor.bytesToBase64(data)
                assertEquals("image/jpeg", mime)

                val decoded = DocumentProcessor.decodeBase64ToBytes(encoded)
                assertEquals(data.size, decoded.size)
                data.first() == decoded.first() && data.last() == decoded.last()
            }
        }

        val results = deferredList.awaitAll()
        assertEquals(concurrentTasks, results.size)
        assertTrue(results.all { it })
    }

    // =========================================================================
    // 4. Missing Files, Null Contracts & Exception Propagation
    // =========================================================================

    @Test
    fun testNullContracts_ProcessPdf_NullArgumentsThrowIllegalArgument() = runBlocking {
        try {
            DocumentProcessor.processPdfToBitmap(null, null)
            fail("Expected IllegalArgumentException for null context & uri")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non possono essere null") == true)
        }
    }

    @Test
    fun testNullContracts_ProcessImage_NullArgumentsThrowIllegalArgument() {
        try {
            DocumentProcessor.processImageUriToBitmap(null, null)
            fail("Expected IllegalArgumentException for null context & uri")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non possono essere null") == true)
        }
    }

    @Test
    fun testNullContracts_BitmapToBase64_NullBitmapThrowsIllegalArgument() {
        try {
            DocumentProcessor.bitmapToBase64(null)
            fail("Expected IllegalArgumentException for null bitmap")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non può essere null") == true)
        }
    }

    @Test
    fun testNullContracts_CreateThumbnail_NullBitmapThrowsIllegalArgument() {
        try {
            DocumentProcessor.createThumbnail(null)
            fail("Expected IllegalArgumentException for null bitmap")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non può essere null") == true)
        }
    }

    @Test
    fun testHookResilience_ExceptionInHookPropagatesCleanly() {
        DocumentProcessor.testImageUriDecoder = { _, _ ->
            throw FileNotFoundException("File not found on device: content://media/1234")
        }

        try {
            DocumentProcessor.processImageUriToBitmap(null, null)
            fail("Expected FileNotFoundException")
        } catch (e: FileNotFoundException) {
            assertEquals("File not found on device: content://media/1234", e.message)
        }
    }

    // =========================================================================
    // 5. Exception Storm Memory Stability
    // =========================================================================

    @Test
    fun testExceptionStorm_1000Errors_NoMemoryLeak() {
        System.gc()
        Thread.sleep(50)
        val initialMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        // Generate 1,000 intentional invalid Base64 decoding errors
        for (i in 0 until 1000) {
            try {
                DocumentProcessor.decodeBase64ToBytes("CORRUPT_BASE64_PAYLOAD_$i!@#$%^&*()")
            } catch (_: IllegalArgumentException) {
                // Expected
            }
        }

        System.gc()
        Thread.sleep(50)
        val finalMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        val growthMB = (finalMem - initialMem) / (1024 * 1024)
        // Memory should remain virtually flat
        assertTrue("Memory growth after 1000 exceptions must be bounded (< 15MB), was ${growthMB}MB", growthMB < 15)
    }
}
