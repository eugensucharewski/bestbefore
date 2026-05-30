package de.eugens.bestbefore.products.domain.usecase

import de.eugens.bestbefore.products.domain.repository.ProductRepository
import javax.inject.Inject

class DeleteProductUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    suspend operator fun invoke(productId: String) = repository.deleteProduct(productId)
}
