package de.eugens.bestbefore.products.domain.usecase

import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import javax.inject.Inject

class AddProductUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    suspend operator fun invoke(product: Product) = repository.addProduct(product)
}
