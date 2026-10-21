package de.eugens.bestbefore.products.data.analyzer

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapUtilsTest {

    @Test
    fun `calculateInSampleSize returns 1 when image is within maxDimension`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 800
            outHeight = 600
        }
        val sampleSize = BitmapUtils.calculateInSampleSize(options, maxDimension = 1024)
        assertEquals(1, sampleSize)
    }

    @Test
    fun `calculateInSampleSize calculates correct power of 2 for large square image`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 4096
            outHeight = 4096
        }
        // 4096 / 4 = 1024 (<= 1024)
        val sampleSize = BitmapUtils.calculateInSampleSize(options, maxDimension = 1024)
        assertEquals(4, sampleSize)
    }

    @Test
    fun `calculateInSampleSize handles asymmetric tall image correctly`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 500
            outHeight = 4000
        }
        // 4000 / 4 = 1000 (<= 1024)
        val sampleSize = BitmapUtils.calculateInSampleSize(options, maxDimension = 1024)
        assertEquals(4, sampleSize)
    }

    @Test
    fun `calculateInSampleSize handles asymmetric wide image correctly`() {
        val options = BitmapFactory.Options().apply {
            outWidth = 3000
            outHeight = 800
        }
        // 3000 / 4 = 750 (<= 1024)
        val sampleSize = BitmapUtils.calculateInSampleSize(options, maxDimension = 1024)
        assertEquals(4, sampleSize)
    }
}
