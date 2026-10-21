package de.eugens.bestbefore.products.data.analyzer

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProductNameExtractorTest {

    private lateinit var extractor: ProductNameExtractor

    @Before
    fun setUp() {
        extractor = ProductNameExtractor()
    }

    @Test
    fun `fallbackFromRawText selects first clean line and formats title case`() {
        val rawText = """
            ZUTATEN: MILCH, ZUCKER
            FRISCHE ALPENMILCH
            500g
        """.trimIndent()

        val result = extractor.fallbackFromRawText(rawText)
        assertEquals("Frische Alpenmilch", result)
    }

    @Test
    fun `fallbackFromRawText filters noise words like ingredients and weight`() {
        val rawText = """
            INGREDIENTS: MILK, WATER
            1000 ml
            850g
        """.trimIndent()

        val result = extractor.fallbackFromRawText(rawText)
        assertEquals("", result)
    }

    @Test
    fun `fallbackFromRawText returns empty string when no valid candidate exists`() {
        val rawText = """
            12.10.2026
            400g
            KCAL 120
        """.trimIndent()

        val result = extractor.fallbackFromRawText(rawText)
        assertEquals("", result)
    }
}
