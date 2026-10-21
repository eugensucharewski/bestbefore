package de.eugens.bestbefore.products.data.analyzer

import android.util.Log
import de.eugens.bestbefore.products.domain.analyzer.ImageTextRecognizer
import de.eugens.bestbefore.products.domain.analyzer.TextRecognitionResult
import de.eugens.bestbefore.products.domain.model.ScannedItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class LocalProductAnalyzerTest {

    private val textRecognizer: ImageTextRecognizer = mockk()
    private val parser: ExpirationDateParser = mockk()

    private lateinit var localAnalyzer: LocalProductAnalyzer

    private val testScannedItems = listOf(
        ScannedItem(productImagePath = "/path/prod.jpg", dateImagePath = "/path/date.jpg")
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0

        localAnalyzer = LocalProductAnalyzer(
            textRecognizer = textRecognizer,
            parser = parser
        )
    }

    @Test
    fun `analyzeContent invokes textRecognizer on demand and returns parsed expiration date when date found`() = runTest {
        val ocrResults = listOf(
            TextRecognitionResult(productName = "Milk", expirationDate = "15.10.2026", dateImagePath = "/path/date.jpg")
        )
        val parsedDate = ExpiryDateResult(
            date = LocalDate.of(2026, 10, 15),
            rawMatch = "15.10.2026",
            confidence = ConfidenceLevel.HIGH
        )

        coEvery { textRecognizer.analyzeImages(testScannedItems) } returns ocrResults
        every { parser.parse("15.10.2026") } returns parsedDate

        val result = localAnalyzer.analyzeImages(testScannedItems)

        assertEquals(1, result.size)
        val item = result[0]
        assertEquals("Milk", item.productName)
        assertTrue(item.date_found)
        assertEquals("15.10.2026", item.expiration_date)
        assertEquals("high", item.confidence)

        coVerify(exactly = 1) { textRecognizer.analyzeImages(testScannedItems) }
        coVerify(exactly = 1) { parser.parse("15.10.2026") }
    }

    @Test
    fun `analyzeContent returns date_found false when date parser returns null`() = runTest {
        val ocrResults = listOf(
            TextRecognitionResult(productName = "Unknown", expirationDate = "no date here", dateImagePath = "/path/date.jpg")
        )

        coEvery { textRecognizer.analyzeImages(testScannedItems) } returns ocrResults
        every { parser.parse("no date here") } returns null

        val result = localAnalyzer.analyzeImages(testScannedItems)

        assertEquals(1, result.size)
        val item = result[0]
        assertEquals("Unknown", item.productName)
        assertFalse(item.date_found)
        assertEquals("", item.expiration_date)
        assertEquals("low", item.confidence)

        coVerify(exactly = 1) { textRecognizer.analyzeImages(testScannedItems) }
    }
}
