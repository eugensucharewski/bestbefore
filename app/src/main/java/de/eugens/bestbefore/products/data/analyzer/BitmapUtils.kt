package de.eugens.bestbefore.products.data.analyzer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object BitmapUtils {

    fun decodeSampledBitmapFromFile(filePath: String, maxDimension: Int = 1024): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null

        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)

            options.inSampleSize = calculateInSampleSize(options, maxDimension)
            options.inJustDecodeBounds = false

            BitmapFactory.decodeFile(filePath, options)
        } catch (_: Exception) {
            null
        }
    }

    internal fun calculateInSampleSize(options: BitmapFactory.Options, maxDimension: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        while ((height / inSampleSize) > maxDimension || (width / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
