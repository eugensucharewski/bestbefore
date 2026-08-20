package de.eugens.bestbefore.products.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.*
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import dagger.hilt.android.qualifiers.ApplicationContext
import de.eugens.bestbefore.products.domain.repository.CameraRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraXRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : CameraRepository {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val controller = LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
    }

    override fun getController(): Any = controller

    override fun setFlashEnabled(enabled: Boolean) {
        controller.enableTorch(enabled)
    }

    override fun unbind() {
        controller.unbind()
    }

    override suspend fun takePicture(): Bitmap? = suspendCancellableCoroutine { continuation ->
        controller.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val rotation = image.imageInfo.rotationDegrees
                        val bitmap = image.toBitmap()
                        
                        val resultBitmap = if (rotation != 0) {
                            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            if (rotated != bitmap) bitmap.recycle()
                            rotated
                        } else {
                            bitmap
                        }
                        
                        continuation.resume(resultBitmap)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }
}
