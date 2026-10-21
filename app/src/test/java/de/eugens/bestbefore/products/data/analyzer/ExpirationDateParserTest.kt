package de.eugens.bestbefore.products.data.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExpirationDateParserTest {

    private lateinit var parser: ExpirationDateParser

    @Before
    fun setUp() {
        // Fixed clock at 2026-01-01
        val fixedInstant = Instant.parse("2026-01-01T00:00:00Z")
        val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))
        parser = ExpirationDateParser(fixedClock)
    }

    @Test
    fun `parse finds standard DD dot MM dot YYYY date`() {
        val result = parser.parse("MHD: 15.10.2026")
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 10, 15), result!!.date)
        assertEquals(ConfidenceLevel.HIGH, result.confidence)
    }

    @Test
    fun `parse handles Cyrillic O OCR substitution in cleanToInt`() {
        val result = parser.parse("EXP 15.1О.2026") // 'О' is Cyrillic
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 10, 15), result!!.date)
    }

    @Test
    fun `parse handles text month`() {
        val result = parser.parse("BEST BEFORE 12 OCT 2026")
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 10, 12), result!!.date)
    }

    @Test
    fun `parse discards lot number prefixed numbers`() {
        val result = parser.parse("LOT 12.10.2026")
        assertNull(result)
    }

    @Test
    fun `parse prefers expiration date over production date when both are present`() {
        val input = """
            MFG: 01.01.2026
            EXP: 01.07.2026
        """.trimIndent()

        val result = parser.parse(input)
        assertNotNull(result)
        assertEquals(LocalDate.of(2026, 7, 1), result!!.date)
    }
}
