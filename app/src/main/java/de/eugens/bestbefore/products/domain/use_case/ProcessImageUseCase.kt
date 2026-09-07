package de.eugens.bestbefore.products.domain.use_case

import android.graphics.Bitmap
import android.graphics.Rect
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.domain.model.ScanStep
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ProcessImageUseCase @Inject constructor(
    private val defaultDispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(bitmap: Bitmap, step: ScanStep): ByteArray = withContext(defaultDispatcher) {
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

        val left = cropRect.left.coerceIn(0, width - 1)
        val top = cropRect.top.coerceIn(0, height - 1)
        val right = cropRect.right.coerceIn(left + 1, width)
        val bottom = cropRect.bottom.coerceIn(top + 1, height)
        val cropWidth = (right - left).coerceAtLeast(1)
        val cropHeight = (bottom - top).coerceAtLeast(1)

        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            left,
            top,
            cropWidth,
            cropHeight
        )

        val stream = ByteArrayOutputStream()
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()

        if (bitmap != croppedBitmap) {
            bitmap.recycle()
        }
        croppedBitmap.recycle()

        byteArray
    }
}
