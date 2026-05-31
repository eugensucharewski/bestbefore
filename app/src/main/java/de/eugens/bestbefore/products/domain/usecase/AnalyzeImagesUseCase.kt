package de.eugens.bestbefore.products.domain.usecase

import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import javax.inject.Inject

class AnalyzeImagesUseCase @Inject constructor(
    private val analyzer: AIProductAnalyzer
) {
    suspend operator fun invoke(items: List<ScannedItem>): List<ExpirationInfo> =
        analyzer.analyzeImages(items)
}
