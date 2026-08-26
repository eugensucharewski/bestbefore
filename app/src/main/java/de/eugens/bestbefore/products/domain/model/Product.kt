package de.eugens.bestbefore.products.domain.model

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
    val hasImage: Boolean = false,
    val userId: String? = null
)

@Serializable
data class ScannedItem(
    val productBitmap: ByteArray? = null,
    val dateBitmap: ByteArray? = null
)
