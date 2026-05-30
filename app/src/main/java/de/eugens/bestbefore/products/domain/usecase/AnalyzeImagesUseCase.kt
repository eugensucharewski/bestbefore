package de.eugens.bestbefore.products.domain.usecase

import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import javax.inject.Inject

class AnalyzeImagesUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    suspend operator fun invoke(items: List<ScannedItem>): List<ExpirationInfo> =
        repository.analyzeImages(items)
}
