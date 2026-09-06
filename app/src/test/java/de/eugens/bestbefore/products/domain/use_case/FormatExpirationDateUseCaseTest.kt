package de.eugens.bestbefore.products.domain.use_case

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormatExpirationDateUseCaseTest {

    private val parseExpirationDateUseCase = ParseExpirationDateUseCase()
    private val useCase = FormatExpirationDateUseCase(parseExpirationDateUseCase)

    @Test
    fun `formats ISO date string to dd MM yyyy for German locale`() {
        val result = useCase("2025-12-31", Locale.GERMAN)
        assertEquals("31.12.2025", result)
    }

    @Test
    fun `formats ISO date string to dd MM yyyy for Russian locale`() {
        val result = useCase("2025-12-31", Locale.forLanguageTag("ru"))
        assertEquals("31.12.2025", result)
    }

    @Test
    fun `formats slash date string to dd MM yyyy for German locale`() {
        val result = useCase("31/12/2025", Locale.GERMAN)
        assertEquals("31.12.2025", result)
    }

    @Test
    fun `formats slash date string to dd MM yyyy for Russian locale`() {
        val result = useCase("31/12/2025", Locale.forLanguageTag("ru"))
        assertEquals("31.12.2025", result)
    }

    @Test
    fun `formats dot date string for German and Russian locales`() {
        val deResult = useCase("05.04.2024", Locale.GERMAN)
        val ruResult = useCase("05.04.2024", Locale.forLanguageTag("ru"))
        assertEquals("05.04.2024", deResult)
        assertEquals("05.04.2024", ruResult)
    }

    @Test
    fun `returns raw string for invalid date`() {
        val result = useCase("invalid-date", Locale.GERMAN)
        assertEquals("invalid-date", result)
    }

    @Test
    fun `returns empty string for null or empty input`() {
        assertEquals("", useCase(null, Locale.GERMAN))
        assertEquals("", useCase("", Locale.GERMAN))
    }
}
