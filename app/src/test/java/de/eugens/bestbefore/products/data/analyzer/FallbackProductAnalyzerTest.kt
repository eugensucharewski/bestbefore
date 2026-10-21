package de.eugens.bestbefore.products.data.analyzer

import android.util.Log
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FallbackProductAnalyzerTest {

    private val nanoAnalyzer: GeminiNanoProductAnalyzer = mockk()
    private val geminiAnalyzer: GeminiProductAnalyzer = mockk()
    private val localAnalyzer: LocalProductAnalyzer = mockk()

    private lateinit var fallbackAnalyzer: FallbackProductAnalyzer

    private val testItems = listOf(
        ScannedItem(productImagePath = "/path/to/product.jpg", dateImagePath = "/path/to/date.jpg")
    )

    private val expectedInfo = ExpirationInfo(
        productName = "Milk",
        date_found = true,
        expiration_date = "15.10.2026",
        confidence = "high"
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0

        fallbackAnalyzer = FallbackProductAnalyzer(
            nanoAnalyzer = nanoAnalyzer,
            geminiAnalyzer = geminiAnalyzer,
            localAnalyzer = localAnalyzer
        )
    }

    @Test
    fun `analyzeImages uses Gemini Nano when available and successful`() = runTest {
        coEvery { nanoAnalyzer.analyzeImages(testItems) } returns listOf(expectedInfo)

        val result = fallbackAnalyzer.analyzeImages(testItems)

        assertEquals(listOf(expectedInfo), result)
        coVerify(exactly = 1) { nanoAnalyzer.analyzeImages(testItems) }
        coVerify(exactly = 0) { geminiAnalyzer.analyzeImages(any()) }
        coVerify(exactly = 0) { localAnalyzer.analyzeImages(any()) }
    }

    @Test
    fun `analyzeImages falls back to remote Gemini when Gemini Nano fails`() = runTest {
        coEvery { nanoAnalyzer.analyzeImages(testItems) } throws Exception("Gemini Nano unavailable")
        coEvery { geminiAnalyzer.analyzeImages(testItems) } returns listOf(expectedInfo)

        val result = fallbackAnalyzer.analyzeImages(testItems)

        assertEquals(listOf(expectedInfo), result)
        coVerify(exactly = 1) { nanoAnalyzer.analyzeImages(testItems) }
        coVerify(exactly = 1) { geminiAnalyzer.analyzeImages(testItems) }
        coVerify(exactly = 0) { localAnalyzer.analyzeImages(any()) }
    }

    @Test
    fun `analyzeImages falls back to local analyzer when both Gemini Nano and remote Gemini fail`() = runTest {
        coEvery { nanoAnalyzer.analyzeImages(testItems) } throws Exception("Gemini Nano unavailable")
        coEvery { geminiAnalyzer.analyzeImages(testItems) } throws Exception("Remote Gemini 503 Service Unavailable")
        coEvery { localAnalyzer.analyzeImages(testItems) } returns listOf(expectedInfo)

        val result = fallbackAnalyzer.analyzeImages(testItems)

        assertEquals(listOf(expectedInfo), result)
        coVerify(exactly = 1) { nanoAnalyzer.analyzeImages(testItems) }
        coVerify(exactly = 1) { geminiAnalyzer.analyzeImages(testItems) }
        coVerify(exactly = 1) { localAnalyzer.analyzeImages(testItems) }
    }

    @Test(expected = CancellationException::class)
    fun `analyzeImages rethrows CancellationException when Gemini Nano is cancelled`() = runTest {
        coEvery { nanoAnalyzer.analyzeImages(testItems) } throws CancellationException("Job cancelled")

        fallbackAnalyzer.analyzeImages(testItems)
    }

    @Test(expected = CancellationException::class)
    fun `analyzeImages rethrows CancellationException when remote Gemini is cancelled`() = runTest {
        coEvery { nanoAnalyzer.analyzeImages(testItems) } throws Exception("Gemini Nano unavailable")
        coEvery { geminiAnalyzer.analyzeImages(testItems) } throws CancellationException("Job cancelled")

        fallbackAnalyzer.analyzeImages(testItems)
    }
}
