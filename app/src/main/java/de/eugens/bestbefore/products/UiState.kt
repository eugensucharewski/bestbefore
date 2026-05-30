package de.eugens.bestbefore.products

import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem
import kotlinx.serialization.Serializable

enum class ProductFilter {
    ALL,
    EXPIRED,
    EXPIRED_AND_UPCOMING
}

sealed interface UiState {
    object MainList : UiState
    
    data class Scanning(
        val step: ScanStep,
        val currentItem: ScannedItem = ScannedItem(),
        val scannedItems: List<ScannedItem> = emptyList()
    ) : UiState
    
    object Processing : UiState
    
    object Settings : UiState

    data class EditProduct(val product: Product, val productBitmap: android.graphics.Bitmap? = null) : UiState
    
    data class Success(val products: List<Product>) : UiState
    
    data class Error(val errorMessage: String) : UiState
}