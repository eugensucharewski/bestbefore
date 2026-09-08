package de.eugens.bestbefore.products.presentation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScanStep
import de.eugens.bestbefore.products.domain.model.ScannedItem
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
enum class ProductFilter : Parcelable {
    ALL,
    EXPIRED,
    EXPIRED_AND_UPCOMING
}

@Parcelize
sealed interface UiState : NavKey, Parcelable {
    @Serializable
    data object MainList : UiState
    
    @Parcelize
    data class Scanning(
        val step: ScanStep,
        val currentItem: ScannedItem = ScannedItem(),
        val scannedItems: List<ScannedItem> = emptyList(),
        val scanId: String = ""
    ) : UiState
    
    @Parcelize
    data object Processing : UiState
    
    @Parcelize
    data object Settings : UiState

    @Parcelize
    data class EditProduct(val product: Product, val imagePath: String? = null) : UiState
    
    @Parcelize
    data class Success(val products: List<Product>) : UiState
    
    @Parcelize
    data class Error(val errorMessage: String) : UiState
}
