package de.eugens.bestbefore.products.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
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
) : Parcelable

@Parcelize
data class ScannedItem(
    val productImagePath: String? = null,
    val dateImagePath: String? = null
) : Parcelable
