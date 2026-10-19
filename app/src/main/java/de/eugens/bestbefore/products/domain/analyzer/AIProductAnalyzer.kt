package de.eugens.bestbefore.products.domain.analyzer

import de.eugens.bestbefore.products.domain.model.ExpirationInfo

interface AIProductAnalyzer {
    suspend fun analyzeImages(items: List<TextRecognitionResult>): List<ExpirationInfo>
}
