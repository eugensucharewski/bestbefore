package de.eugens.bestbefore

sealed interface UiState {
    object MainList : UiState
    
    data class Scanning(
        val step: ScanStep,
        val currentItem: ScannedItem = ScannedItem(),
        val scannedItems: List<ScannedItem> = emptyList()
    ) : UiState
    
    object Processing : UiState
    
    object Settings : UiState
    
    data class Success(val products: List<Product>) : UiState
    
    data class Error(val errorMessage: String) : UiState
}

@kotlinx.serialization.Serializable
data class ExpirationInfo(
    val date_found: Boolean,
    val expiration_date: String? = null,
    val production_date: String? = null,
    val confidence: String? = null,
    val raw_text_detected: String? = null
)
