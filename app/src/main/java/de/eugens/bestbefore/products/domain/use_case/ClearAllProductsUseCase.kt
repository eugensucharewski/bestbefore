package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.repository.ProductRepository
import javax.inject.Inject

class ClearAllProductsUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    suspend operator fun invoke() = repository.clearAllProducts()
}
