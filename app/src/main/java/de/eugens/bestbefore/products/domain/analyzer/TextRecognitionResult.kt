package de.eugens.bestbefore.products.domain.analyzer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TextRecognitionResult(
    val productName: String, 
    val expirationDate: String,
    val dateImagePath: String? = null
): Parcelable