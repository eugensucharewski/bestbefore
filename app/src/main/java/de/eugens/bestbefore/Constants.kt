package de.eugens.bestbefore

object Constants {
    const val COLLECTION_PRODUCTS = "products"
    const val FIELD_USER_ID = "userId"
    
    const val BITMAP_MAX_WIDTH = 400
    const val BITMAP_MAX_HEIGHT = 400
    const val BITMAP_QUALITY = 70
    
    const val NOTIFICATION_CHANNEL_ID = "processing_results"
    const val NOTIFICATION_ID = 1

    const val DATE_FORMAT = "dd.MM.yyyy"

    const val UPCOMING_EXPIRATION_DAYS_THRESHOLD = 2

    object Scanning {
        const val CROP_LEFT = 0.1f
        const val CROP_RIGHT = 0.9f
        const val PRODUCT_TOP = 0.2f
        const val PRODUCT_BOTTOM = 0.6f
        const val DATE_TOP = 0.4f
        const val DATE_BOTTOM = 0.55f
    }
}
