package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.model.ExpirationStatus
import de.eugens.bestbefore.products.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class GetExpirationStatusUseCaseTest {

    private val parseExpirationDateUseCase = ParseExpirationDateUseCase()
    private val useCase = GetExpirationStatusUseCase(parseExpirationDateUseCase)
    private val today = LocalDate.now()
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    @Test
    fun `returns EXPIRED when date is in the past`() {
        val pastDate = today.minusDays(1).format(formatter)
        val product = Product(id = "1", name = "Test", expirationDate = pastDate)
        assertEquals(ExpirationStatus.EXPIRED, useCase(product, thresholdValue = 3))
    }

    @Test
    fun `returns UPCOMING when date is today or within threshold`() {
        val todayStr = today.format(formatter)
        val todayProduct = Product(id = "1", name = "Test", expirationDate = todayStr)
        assertEquals(ExpirationStatus.UPCOMING, useCase(todayProduct, thresholdValue = 3))

        val upcomingDate = today.plusDays(3).format(formatter)
        val upcomingProduct = Product(id = "2", name = "Test", expirationDate = upcomingDate)
        assertEquals(ExpirationStatus.UPCOMING, useCase(upcomingProduct, thresholdValue = 3))
    }

    @Test
    fun `returns FRESH when date is beyond threshold`() {
        val freshDate = today.plusDays(4).format(formatter)
        val product = Product(id = "1", name = "Test", expirationDate = freshDate)
        assertEquals(ExpirationStatus.FRESH, useCase(product, thresholdValue = 3))
    }

    @Test
    fun `returns UNKNOWN when date cannot be parsed`() {
        val product = Product(id = "1", name = "Test", expirationDate = "invalid")
        assertEquals(ExpirationStatus.UNKNOWN, useCase(product, thresholdValue = 3))
    }
}
