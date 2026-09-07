package de.eugens.bestbefore.products.domain.use_case

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import dagger.hilt.android.qualifiers.ApplicationContext
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.domain.model.ScanStep
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

class ProcessImageUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val defaultDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TEMP_SCAN_DIR = "temp_scans"
        private const val MAX_IMAGE_DIMENSION = 1280
        private const val MIN_FREE_SPACE_BYTES = 10_000_000L // 10 MB
        private const val JPEG_QUALITY = 85
    }

    suspend operator fun invoke(bitmap: Bitmap, step: ScanStep): String = withContext(defaultDispatcher) {
        if (context.cacheDir.usableSpace < MIN_FREE_SPACE_BYTES) {
            throw IllegalStateException("Low storage space on device")
        }

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

        val scaledBitmap = if (croppedBitmap.width > MAX_IMAGE_DIMENSION || croppedBitmap.height > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / maxOf(croppedBitmap.width, croppedBitmap.height)
            val targetW = (croppedBitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (croppedBitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(croppedBitmap, targetW, targetH, true)
        } else {
            croppedBitmap
        }

        val tempDir = File(context.cacheDir, TEMP_SCAN_DIR).apply { mkdirs() }
        val outputFile = File(tempDir, "scan_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")

        FileOutputStream(outputFile).use { stream ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.flush()
        }

        if (bitmap != croppedBitmap) {
            bitmap.recycle()
        }
        if (croppedBitmap != scaledBitmap) {
            croppedBitmap.recycle()
        }
        scaledBitmap.recycle()

        outputFile.absolutePath
    }
}
