package de.eugens.bestbefore.products.data.analyzer

import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.use_case.ParseExpirationDateUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiNanoProductAnalyzerTest {

    private val analyzer = GeminiNanoProductAnalyzer()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `extractJson should extract JSON array from markdown text`() {
        val markdown = "Here is the parsed result:\n```json\n[{\"productName\": \"Cheese\", \"date_found\": true}]\n```"
        val result = analyzer.extractJson(markdown)
        assertEquals("[{\"productName\": \"Cheese\", \"date_found\": true}]", result)
    }

    @Test
    fun `extractJson should handle plain JSON array`() {
        val cleanJson = "[{\"productName\": \"Apple Juice\", \"date_found\": false}]"
        val result = analyzer.extractJson(cleanJson)
        assertEquals(cleanJson, result)
    }

    @Test
    fun `analyzeContent returns empty list when input items list is empty`() = runTest {
        val result = analyzer.analyzeImages(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parsing structured output handles fields correctly`() {
        val jsonStr = """
            [
              {
                "productName": "Oat Milk",
                "date_found": true,
                "expiration_date": "20.12.2026",
                "confidence": "high",
                "raw_text_detected": "EXP 20.12.2026"
              }
            ]
        """.trimIndent()
        val parsed = json.decodeFromString<List<ExpirationInfo>>(jsonStr)
        assertEquals(1, parsed.size)
        assertEquals("Oat Milk", parsed[0].productName)
        assertEquals(true, parsed[0].date_found)
        assertEquals("20.12.2026", parsed[0].expiration_date)
        assertEquals("high", parsed[0].confidence)
        assertEquals("EXP 20.12.2026", parsed[0].raw_text_detected)
    }

    @Test
    fun `parsing and normalizing structured output converts 2-digit year dates to dd-MM-yyyy`() {
        val parseExpirationDateUseCase = ParseExpirationDateUseCase()
        val jsonStr = """
            [
              {
                "productName": "Yogurt",
                "date_found": true,
                "expiration_date": "20.12.26",
                "production_date": "01.12.26",
                "confidence": "high"
              }
            ]
        """.trimIndent()
        val parsed = json.decodeFromString<List<ExpirationInfo>>(jsonStr).map { info ->
            info.copy(
                expiration_date = parseExpirationDateUseCase.formatToStandardDate(info.expiration_date) ?: info.expiration_date,
                production_date = parseExpirationDateUseCase.formatToStandardDate(info.production_date) ?: info.production_date
            )
        }
        assertEquals(1, parsed.size)
        assertEquals("20.12.2026", parsed[0].expiration_date)
        assertEquals("01.12.2026", parsed[0].production_date)
    }
}
