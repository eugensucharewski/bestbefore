package de.eugens.bestbefore.products.data.analyzer

import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.use_case.ParseExpirationDateUseCase
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiProductAnalyzerTest {

    private val parseExpirationDateUseCase = ParseExpirationDateUseCase()
    private val analyzer = GeminiProductAnalyzer(parseExpirationDateUseCase)
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `extractJson should extract JSON from markdown`() {
        val markdown = "Here is the result:\n```json\n[{\"productName\": \"Milk\", \"date_found\": true}]\n```"
        val result = analyzer.extractJson(markdown)
        assertEquals("[{\"productName\": \"Milk\", \"date_found\": true}]", result)
    }

    @Test
    fun `extractJson should handle clean JSON`() {
        val cleanJson = "[{\"productName\": \"Milk\", \"date_found\": true}]"
        val result = analyzer.extractJson(cleanJson)
        assertEquals(cleanJson, result)
    }

    @Test
    fun `parsing should handle missing date_found`() {
        val jsonStr = "[{\"productName\": \"Milk\"}]"
        val result = json.decodeFromString<List<ExpirationInfo>>(jsonStr)
        assertEquals(1, result.size)
        assertEquals("Milk", result[0].productName)
        assertEquals(false, result[0].date_found)
    }

    @Test
    fun `parsing should handle full response`() {
        val jsonStr = """
            [
              {
                "productName": "Yogurt",
                "date_found": true,
                "expiration_date": "15.09.2026",
                "confidence": "high"
              }
            ]
        """.trimIndent()
        val result = json.decodeFromString<List<ExpirationInfo>>(jsonStr)
        assertEquals(1, result.size)
        assertEquals("Yogurt", result[0].productName)
        assertEquals(true, result[0].date_found)
        assertEquals("15.09.2026", result[0].expiration_date)
    }

    @Test
    fun `parsing and normalizing should convert 2-digit year to dd-MM-yyyy`() {
        val parseExpirationDateUseCase = ParseExpirationDateUseCase()
        val jsonStr = """
            [
              {
                "productName": "Cheese",
                "date_found": true,
                "expiration_date": "15.09.26",
                "production_date": "01.09.26",
                "confidence": "high"
              }
            ]
        """.trimIndent()
        val result = json.decodeFromString<List<ExpirationInfo>>(jsonStr).map { info ->
            info.copy(
                expiration_date = parseExpirationDateUseCase.formatToStandardDate(info.expiration_date) ?: info.expiration_date,
                production_date = parseExpirationDateUseCase.formatToStandardDate(info.production_date) ?: info.production_date
            )
        }
        assertEquals(1, result.size)
        assertEquals("15.09.2026", result[0].expiration_date)
        assertEquals("01.09.2026", result[0].production_date)
    }
}
