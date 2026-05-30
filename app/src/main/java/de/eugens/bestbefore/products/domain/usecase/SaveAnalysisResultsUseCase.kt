package de.eugens.bestbefore.products.domain.usecase

import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import javax.inject.Inject

class SaveAnalysisResultsUseCase @Inject constructor(
    private val repository: ProductRepository
) {
    suspend operator fun invoke(results: List<ExpirationInfo>, items: List<ScannedItem>) =
        repository.saveAnalysisResults(results, items)
}
