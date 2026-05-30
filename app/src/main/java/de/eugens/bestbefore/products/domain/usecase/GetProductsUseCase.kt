package de.eugens.bestbefore.products.domain.usecase

import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import javax.inject.Inject

class GetProductsUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    suspend operator fun invoke(): List<Product> = repository.getProducts()
}
