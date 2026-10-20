package de.eugens.bestbefore.products.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import dagger.hilt.android.qualifiers.ApplicationContext
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.domain.model.ScanStep
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidImageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ImageRepository {

    companion object {
        private const val TEMP_SCAN_DIR = "temp_scans"
        private const val MAX_IMAGE_DIMENSION = 1280
        private const val MIN_FREE_SPACE_BYTES = 10_000_000L // 10 MB
        private const val JPEG_QUALITY = 85
    }

    override suspend fun processImage(
        imageBytes: ByteArray,
        step: ScanStep,
        rotationDegrees: Int
    ): String = withContext(Dispatchers.IO) {
        if (context.cacheDir.usableSpace < MIN_FREE_SPACE_BYTES) {
            throw IllegalStateException("Low storage space on device")
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        val rawWidth = options.outWidth
        val rawHeight = options.outHeight

        var sampleSize = 1
        if (rawWidth > MAX_IMAGE_DIMENSION || rawHeight > MAX_IMAGE_DIMENSION) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2
            while ((halfWidth / sampleSize) >= MAX_IMAGE_DIMENSION && (halfHeight / sampleSize) >= MAX_IMAGE_DIMENSION) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        val decodedBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
            ?: throw IllegalArgumentException("Could not decode image")

        var currentBitmap = decodedBitmap
        var croppedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null

        try {
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(currentBitmap, 0, 0, currentBitmap.width, currentBitmap.height, matrix, true)
                if (rotated != currentBitmap) {
                    currentBitmap.recycle()
                    currentBitmap = rotated
                }
            }

            val width = currentBitmap.width
            val height = currentBitmap.height

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

            val left = cropRect.left.coerceIn(0, (width - 1).coerceAtLeast(0))
            val top = cropRect.top.coerceIn(0, (height - 1).coerceAtLeast(0))
            val right = cropRect.right.coerceIn(left + 1, width.coerceAtLeast(left + 1))
            val bottom = cropRect.bottom.coerceIn(top + 1, height.coerceAtLeast(top + 1))
            val cropWidth = (right - left).coerceAtLeast(1)
            val cropHeight = (bottom - top).coerceAtLeast(1)

            croppedBitmap = Bitmap.createBitmap(
                currentBitmap,
                left,
                top,
                cropWidth,
                cropHeight
            )

            scaledBitmap = if (croppedBitmap.width > MAX_IMAGE_DIMENSION || croppedBitmap.height > MAX_IMAGE_DIMENSION) {
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

            outputFile.absolutePath
        } finally {
            if (currentBitmap != croppedBitmap && !currentBitmap.isRecycled) {
                currentBitmap.recycle()
            }
            if (croppedBitmap != null && croppedBitmap != scaledBitmap && !croppedBitmap.isRecycled) {
                croppedBitmap.recycle()
            }
            if (scaledBitmap != null && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
        }
    }

    override suspend fun clearAllTempFiles() = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, TEMP_SCAN_DIR)
        if (tempDir.exists() && tempDir.isDirectory) {
            tempDir.listFiles()?.forEach { it.delete() }
        }
    }

    override suspend fun clearTempFiles(items: List<ScannedItem>) = withContext(Dispatchers.IO) {
        items.forEach { item ->
            item.productImagePath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
            item.dateImagePath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
    }
}
