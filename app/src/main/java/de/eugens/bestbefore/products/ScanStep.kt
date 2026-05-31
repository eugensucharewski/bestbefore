package de.eugens.bestbefore.products

import kotlinx.serialization.Serializable

@Serializable
enum class ScanStep {
    PRODUCT_PHOTO,
    DATE_PHOTO
}
