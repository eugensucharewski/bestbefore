package de.eugens.bestbefore.products.domain.usecase

import android.graphics.Bitmap
import android.graphics.Rect
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.ScanStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ProcessImageUseCase @Inject constructor() {
    suspend operator fun invoke(bitmap: Bitmap, step: ScanStep): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height

        val cropRect = if (step == ScanStep.PRODUCT_PHOTO) {
            Rect(
                (width * Constants.Scanning.CROP_LEFT).toInt(),
                (height * Constants.Scanning.PRODUCT_TOP).toInt(),
                (width * Constants.Scanning.CROP_RIGHT).toInt(),
                (height * Constants.Scanning.PRODUCT_BOTTOM).toInt()
            )
        } else {
            Rect(
                (width * Constants.Scanning.CROP_LEFT).toInt(),
                (height * Constants.Scanning.DATE_TOP).toInt(),
                (width * Constants.Scanning.CROP_RIGHT).toInt(),
                (height * Constants.Scanning.DATE_BOTTOM).toInt()
            )
        }

        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )

        if (bitmap != croppedBitmap) {
            bitmap.recycle()
        }
        
        croppedBitmap
    }
}
