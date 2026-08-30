package de.eugens.bestbefore.products.data.analyzer

import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiProductAnalyzerTest {

    private val analyzer = GeminiProductAnalyzer()
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
}
