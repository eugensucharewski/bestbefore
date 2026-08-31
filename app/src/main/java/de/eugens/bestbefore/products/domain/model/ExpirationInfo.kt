package de.eugens.bestbefore.products.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExpirationInfo(
    val productName: String? = null,
    val date_found: Boolean = false,
    val expiration_date: String? = null,
    val production_date: String? = null,
    val confidence: String? = null,
    val raw_text_detected: String? = null
)
