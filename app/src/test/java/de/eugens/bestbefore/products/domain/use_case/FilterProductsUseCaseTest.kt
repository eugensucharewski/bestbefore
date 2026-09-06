package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.presentation.ProductFilter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FilterProductsUseCaseTest {

    private val parseExpirationDateUseCase = ParseExpirationDateUseCase()
    private val getExpirationStatusUseCase = GetExpirationStatusUseCase(parseExpirationDateUseCase)
    private val useCase = FilterProductsUseCase(getExpirationStatusUseCase)
    private val today = LocalDate.now()
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    @Test
    fun `Filter ALL returns all products`() {
        val products = listOf(
            Product(id = "1", name = "P1", expirationDate = today.minusDays(1).format(formatter)),
            Product(id = "2", name = "P2", expirationDate = today.plusDays(1).format(formatter)),
            Product(id = "3", name = "P3", expirationDate = today.plusDays(10).format(formatter))
        )
        val result = useCase(products, ProductFilter.ALL, thresholdValue = 2)
        assertEquals(3, result.size)
    }

    @Test
    fun `Filter EXPIRED returns only expired products`() {
        val expired = Product(id = "1", name = "Expired", expirationDate = today.minusDays(1).format(formatter))
        val fresh = Product(id = "2", name = "Fresh", expirationDate = today.plusDays(10).format(formatter))
        val products = listOf(expired, fresh)

        val result = useCase(products, ProductFilter.EXPIRED, thresholdValue = 2)
        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun `Filter EXPIRED_AND_UPCOMING returns expired and upcoming products`() {
        val expired = Product(id = "1", name = "Expired", expirationDate = today.minusDays(1).format(formatter))
        val upcoming = Product(id = "2", name = "Upcoming", expirationDate = today.plusDays(1).format(formatter))
        val fresh = Product(id = "3", name = "Fresh", expirationDate = today.plusDays(10).format(formatter))
        val products = listOf(expired, upcoming, fresh)

        val result = useCase(products, ProductFilter.EXPIRED_AND_UPCOMING, thresholdValue = 2)
        assertEquals(2, result.size)
        assertEquals(listOf("1", "2"), result.map { it.id })
    }
}
