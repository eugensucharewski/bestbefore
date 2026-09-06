package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.model.Product
import java.time.LocalDate
import javax.inject.Inject

class SortProductsUseCase @Inject constructor(
    private val parseExpirationDateUseCase: ParseExpirationDateUseCase
) {
    operator fun invoke(products: List<Product>): List<Product> {
        return products.sortedBy { product ->
            parseExpirationDateUseCase(product.expirationDate) ?: LocalDate.MAX
        }
    }
}
