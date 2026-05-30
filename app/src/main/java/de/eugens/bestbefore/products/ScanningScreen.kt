package de.eugens.bestbefore.products

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.eugens.bestbefore.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.camera.core.Preview as CameraPreview

@Composable
fun ScanningScreen(
    state: UiState.Scanning,
    events: Flow<ProductEvent>,
    onCaptureClick: () -> Unit,
    onCaptureResult: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(Unit) {
        events.collectLatest { event ->
            if (event is ProductEvent.TriggerCapture) {
                takePhoto(context, imageCapture, state.step, onCaptureResult)
            }
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = CameraPreview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        ScanningOverlay(state.step)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (state.step == ScanStep.PRODUCT_PHOTO)
                    stringResource(R.string.step_product_photo)
                else 
                    stringResource(R.string.step_date_photo),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        IconButton(
            onClick = {
                isFlashOn = !isFlashOn
                camera?.cameraControl?.enableTorch(isFlashOn)
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = stringResource(R.string.flash),
                tint = Color.White
            )
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.background(Color.Red.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.cancel), tint = Color.White)
            }

            Button(
                onClick = onCaptureClick,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                // Inner circle
            }

            IconButton(
                onClick = onFinish,
                enabled = state.scannedItems.isNotEmpty(),
                modifier = Modifier.background(
                    if (state.scannedItems.isNotEmpty()) Color.Green.copy(alpha = 0.8f) else Color.Gray,
                    CircleShape
                )
            ) {
                BadgedBox(badge = {
                    if (state.scannedItems.isNotEmpty()) {
                        Badge { Text(state.scannedItems.size.toString()) }
                    }
                }) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.finish), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ScanningOverlay(step: ScanStep) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val maskRect = if (step == ScanStep.PRODUCT_PHOTO) {
            Rect(
                left = size.width * 0.1f,
                top = size.height * 0.2f,
                right = size.width * 0.9f,
                bottom = size.height * 0.6f
            )
        } else {
            Rect(
                left = size.width * 0.1f,
                top = size.height * 0.4f,
                right = size.width * 0.9f,
                bottom = size.height * 0.55f
            )
        }

        val maskPath = Path().apply {
            addRoundRect(RoundRect(maskRect, CornerRadius(16f, 16f)))
        }

        clipPath(maskPath, clipOp = ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.5f))
        }
    }
}

fun takePhoto(context: Context, imageCapture: ImageCapture, step: ScanStep, onPhotoTaken: (Bitmap) -> Unit) {
    val mainExecutor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(
        mainExecutor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // Move heavy bitmap processing to a background thread to avoid UI hangs
                val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
                scope.launch {
                    val bitmap = image.toBitmap()
                    val matrix = Matrix()
                    matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                    if (bitmap != rotatedBitmap) {
                        bitmap.recycle()
                    }

                    val width = rotatedBitmap.width
                    val height = rotatedBitmap.height

                    val cropRect = if (step == ScanStep.PRODUCT_PHOTO) {
                        android.graphics.Rect(
                            (width * 0.1f).toInt(),
                            (height * 0.2f).toInt(),
                            (width * 0.9f).toInt(),
                            (height * 0.6f).toInt()
                        )
                    } else {
                        android.graphics.Rect(
                            (width * 0.1f).toInt(),
                            (height * 0.4f).toInt(),
                            (width * 0.9f).toInt(),
                            (height * 0.55f).toInt()
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

                    // Return result to the main thread
                    mainExecutor.execute {
                        onPhotoTaken(croppedBitmap)
                        image.close()
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
            }
        }
    )
}
