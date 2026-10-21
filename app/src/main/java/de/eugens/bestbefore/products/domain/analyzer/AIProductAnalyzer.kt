package de.eugens.bestbefore.products.domain.analyzer

import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem

interface AIProductAnalyzer {
    suspend fun analyzeImages(items: List<ScannedItem>): List<ExpirationInfo>
}
