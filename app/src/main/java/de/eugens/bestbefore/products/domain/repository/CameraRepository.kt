package de.eugens.bestbefore.products.domain.repository

import android.graphics.Bitmap

interface CameraRepository {
    fun getController(): Any
    suspend fun takePicture(): Bitmap?
    fun setFlashEnabled(enabled: Boolean)
    fun unbind()
}
