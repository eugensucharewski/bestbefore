package de.eugens.bestbefore.products

import androidx.camera.view.LifecycleCameraController
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.R

@Composable
fun ScanningScreen(
    state: UiState.Scanning,
    cameraController: Any,
    onFlashToggle: (Boolean) -> Unit,
    onCaptureClick: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { cameraController as LifecycleCameraController }
    var isFlashOn by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    this.controller = controller
                }
            },
            modifier = Modifier.fillMaxSize()
        )

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
                onFlashToggle(isFlashOn)
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
                left = size.width * Constants.Scanning.CROP_LEFT,
                top = size.height * Constants.Scanning.PRODUCT_TOP,
                right = size.width * Constants.Scanning.CROP_RIGHT,
                bottom = size.height * Constants.Scanning.PRODUCT_BOTTOM
            )
        } else {
            Rect(
                left = size.width * Constants.Scanning.CROP_LEFT,
                top = size.height * Constants.Scanning.DATE_TOP,
                right = size.width * Constants.Scanning.CROP_RIGHT,
                bottom = size.height * Constants.Scanning.DATE_BOTTOM
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
