package de.eugens.bestbefore.products.presentation

import kotlinx.serialization.Serializable

@Serializable
enum class ScanStep {
    PRODUCT_PHOTO,
    DATE_PHOTO
}
