package de.eugens.bestbefore.products.domain.usecase

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.ScanStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ProcessImageUseCase @Inject constructor() {
    suspend operator fun invoke(image: ImageProxy, step: ScanStep): Bitmap = withContext(Dispatchers.Default) {
        image.use { proxy ->
            val bitmap = proxy.toBitmap()
            val matrix = Matrix().apply {
                postRotate(proxy.imageInfo.rotationDegrees.toFloat())
            }
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            if (bitmap != rotatedBitmap) {
                rotatedBitmap.recycle()
            }

            val width = rotatedBitmap.width
            val height = rotatedBitmap.height

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
                rotatedBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
            )

            if (rotatedBitmap != croppedBitmap) {
                rotatedBitmap.recycle()
            }
            
            croppedBitmap
        }
    }
}
