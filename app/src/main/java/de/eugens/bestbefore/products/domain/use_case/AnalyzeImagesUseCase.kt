package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.analyzer.ImageTextRecognizer
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import javax.inject.Inject

class AnalyzeImagesUseCase @Inject constructor(
    private val textRecognizer: ImageTextRecognizer,
    private val analyzer: AIProductAnalyzer
) {
    suspend operator fun invoke(items: List<ScannedItem>): List<ExpirationInfo> {
        val textRecognitionResult = textRecognizer.analyzeImages(items)
        return analyzer.analyzeImages(textRecognitionResult)
    }
}
