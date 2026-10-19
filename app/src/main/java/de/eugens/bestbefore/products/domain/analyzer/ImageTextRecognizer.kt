package de.eugens.bestbefore.products.domain.analyzer

import de.eugens.bestbefore.products.domain.model.ScannedItem

interface ImageTextRecognizer {
    suspend fun analyzeImages(items: List<ScannedItem>): List<TextRecognitionResult>
}