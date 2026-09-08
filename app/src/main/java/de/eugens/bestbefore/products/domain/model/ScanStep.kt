package de.eugens.bestbefore.products.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class ScanStep : Parcelable {
    PRODUCT_PHOTO,
    DATE_PHOTO
}
