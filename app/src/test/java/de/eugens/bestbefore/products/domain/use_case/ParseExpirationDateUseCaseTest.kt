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
    fun `returns null for invalid date string`() {
        assertNull(useCase("invalid-date"))
        assertNull(useCase("32.13.2025"))
    }
}
