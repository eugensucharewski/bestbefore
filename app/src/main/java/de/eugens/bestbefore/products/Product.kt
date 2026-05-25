package de.eugens.bestbefore.products

import android.graphics.Bitmap
import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String = "",
    val name: String = "",
    val expirationDate: String = "",
    val productionDate: String? = null,
    val confidence: String? = null,
    val rawText: String? = null,
    val productImage: String? = null, // Base64 encoded resized image
    val userId: String? = null
)

data class ScannedItem(
    val productBitmap: Bitmap? = null,
    val dateBitmap: Bitmap? = null
)

enum class ScanStep {
    PRODUCT_PHOTO,
    DATE_PHOTO
}
