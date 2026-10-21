package de.eugens.bestbefore.products.domain.use_case

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ParseExpirationDateUseCaseTest {

    private val useCase = ParseExpirationDateUseCase()

    @Test
    fun `returns null when date string is null or blank`() {
        assertNull(useCase(null))
        assertNull(useCase(""))
        assertNull(useCase("   "))
    }

    @Test
    fun `parses dd dot MM dot yyyy correctly`() {
        val result = useCase("31.12.2025")
        assertEquals(LocalDate.of(2025, 12, 31), result)
    }

    @Test
    fun `parses yyyy-MM-dd correctly`() {
        val result = useCase("2025-12-31")
        assertEquals(LocalDate.of(2025, 12, 31), result)
    }

    @Test
    fun `parses dd slash MM slash yyyy correctly`() {
        val result = useCase("31/12/2025")
        assertEquals(LocalDate.of(2025, 12, 31), result)
    }

    @Test
    fun `parses single digit day and month correctly`() {
        val result = useCase("1.5.2025")
        assertEquals(LocalDate.of(2025, 5, 1), result)
    }

    @Test
    fun `parses 2-digit year with dots correctly`() {
        val result = useCase("15.10.26")
        assertEquals(LocalDate.of(2026, 10, 15), result)
    }

    @Test
    fun `parses 2-digit year single digit day and month correctly`() {
        val result = useCase("1.5.26")
        assertEquals(LocalDate.of(2026, 5, 1), result)
    }

    @Test
    fun `parses 2-digit year with slashes correctly`() {
        val result = useCase("15/10/26")
        assertEquals(LocalDate.of(2026, 10, 15), result)
    }

    @Test
    fun `parses 2-digit year with hyphens correctly`() {
        val result = useCase("15-10-26")
        assertEquals(LocalDate.of(2026, 10, 15), result)
    }

    @Test
    fun `parses date with trailing dot punctuation correctly`() {
        val result = useCase("15.10.26.")
        assertEquals(LocalDate.of(2026, 10, 15), result)
    }

    @Test
    fun `formatToStandardDate transforms 2-digit year dd-MM-yy to dd-MM-yyyy`() {
        assertEquals("15.10.2026", useCase.formatToStandardDate("15.10.26"))
    }

    @Test
    fun `formatToStandardDate transforms single digit d-M-yy to dd-MM-yyyy`() {
        assertEquals("01.05.2026", useCase.formatToStandardDate("1.5.26"))
    }

    @Test
    fun `formatToStandardDate transforms ISO yyyy-MM-dd to dd-MM-yyyy`() {
        assertEquals("15.10.2026", useCase.formatToStandardDate("2026-10-15"))
    }

    @Test
    fun `formatToStandardDate transforms slash format to dd-MM-yyyy`() {
        assertEquals("15.10.2026", useCase.formatToStandardDate("15/10/26"))
    }

    @Test
    fun `formatToStandardDate preserves already formatted dd-MM-yyyy`() {
        assertEquals("15.10.2026", useCase.formatToStandardDate("15.10.2026"))
    }

    @Test
    fun `formatToStandardDate returns null for invalid or blank date string`() {
        assertNull(useCase.formatToStandardDate(null))
        assertNull(useCase.formatToStandardDate(""))
        assertNull(useCase.formatToStandardDate("   "))
        assertNull(useCase.formatToStandardDate("invalid-date"))
        assertNull(useCase.formatToStandardDate("32.12.2026"))
    }

    @Test
    fun `returns null for invalid date string`() {
        assertNull(useCase("invalid-date"))
        assertNull(useCase("32.13.2025"))
    }
}
