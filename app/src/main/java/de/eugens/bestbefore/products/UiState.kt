package de.eugens.bestbefore.products

import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem
import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
enum class ProductFilter {
    ALL,
    EXPIRED,
    EXPIRED_AND_UPCOMING
}

@Serializable
sealed interface UiState : NavKey {
    @Serializable
    data object MainList : UiState
    
    @Serializable
    data class Scanning(
        val step: ScanStep,
        val currentItem: ScannedItem = ScannedItem(),
        val scannedItems: List<ScannedItem> = emptyList()
    ) : UiState
    
    @Serializable
    data object Processing : UiState
    
    @Serializable
    data object Settings : UiState

    @Serializable
    data class EditProduct(val product: Product, val productBitmap: ByteArray? = null) : UiState
    
    @Serializable
    data class Success(val products: List<Product>) : UiState
    
    @Serializable
    data class Error(val errorMessage: String) : UiState
}
