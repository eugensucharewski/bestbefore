package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.repository.ProductRepository
import javax.inject.Inject

class GetProductImageFileUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    suspend operator fun invoke(productId: String): String? {
        val file = repository.getProductImageFile(productId)
        return if (file != null && file.exists()) {
            file.absolutePath
        } else {
            null
        }
    }
}
