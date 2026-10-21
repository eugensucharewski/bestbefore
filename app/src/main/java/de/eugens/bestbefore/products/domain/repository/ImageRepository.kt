package de.eugens.bestbefore.products.domain.repository

import de.eugens.bestbefore.products.domain.model.ScanStep
import de.eugens.bestbefore.products.domain.model.ScannedItem

interface ImageRepository {
    suspend fun processImage(imageBytes: ByteArray, step: ScanStep, rotationDegrees: Int): String
    suspend fun clearAllTempFiles()
    suspend fun clearTempFiles(items: List<ScannedItem>)
}
