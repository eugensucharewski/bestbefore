package de.eugens.bestbefore.products.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ScanStep {
    PRODUCT_PHOTO,
    DATE_PHOTO
}
