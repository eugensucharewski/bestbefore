package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SortProductsUseCaseTest {

    private val parseExpirationDateUseCase = ParseExpirationDateUseCase()
    private val useCase = SortProductsUseCase(parseExpirationDateUseCase)
    private val today = LocalDate.now()
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    @Test
    fun `sorts products ascending by expiration date`() {
        val p1 = Product(id = "1", name = "Far Future", expirationDate = today.plusDays(10).format(formatter))
        val p2 = Product(id = "2", name = "Near Future", expirationDate = today.plusDays(2).format(formatter))
        val p3 = Product(id = "3", name = "Past", expirationDate = today.minusDays(5).format(formatter))
        val p4 = Product(id = "4", name = "Invalid Date", expirationDate = "unknown")

        val sorted = useCase(listOf(p1, p2, p3, p4))

        assertEquals("3", sorted[0].id)
        assertEquals("2", sorted[1].id)
        assertEquals("1", sorted[2].id)
        assertEquals("4", sorted[3].id)
    }
}
