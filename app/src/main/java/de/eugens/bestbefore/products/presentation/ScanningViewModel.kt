package de.eugens.bestbefore.products.presentation

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.repository.CameraRepository
import de.eugens.bestbefore.products.domain.use_case.ProcessImageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

sealed class ScanningIntent {
    data object RequestCapture : ScanningIntent()
    data object FinishScanning : ScanningIntent()
    data class SetFlashEnabled(val enabled: Boolean) : ScanningIntent()
}

sealed class ScanningEvent {
    data class Finished(val items: List<ScannedItem>) : ScanningEvent()
}

@HiltViewModel
class ScanningViewModel @Inject constructor(
    private val cameraRepository: CameraRepository,
    private val processImageUseCase: ProcessImageUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(UiState.Scanning(step = ScanStep.PRODUCT_PHOTO))
    val state: StateFlow<UiState.Scanning> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScanningEvent>()
    val events = _events.asSharedFlow()

    val cameraController = cameraRepository.getController()

    fun onAction(intent: ScanningIntent) {
        when (intent) {
            is ScanningIntent.RequestCapture -> requestCapture()
            is ScanningIntent.FinishScanning -> finishScanning()
            is ScanningIntent.SetFlashEnabled -> cameraRepository.setFlashEnabled(intent.enabled)
        }
    }

    private fun requestCapture() {
        viewModelScope.launch {
            try {
                val bitmap = cameraRepository.takePicture()
                if (bitmap != null) {
                    val processedBitmap = processImageUseCase(bitmap, _state.value.step)
                    capturePhoto(processedBitmap)
                }
            } catch (e: Exception) {
                Log.e("ScanningViewModel", "Photo capture failed", e)
            }
        }
    }

    private suspend fun capturePhoto(bitmap: Bitmap) = withContext(Dispatchers.Default) {
        val currentState = _state.value
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()

        val updatedState = if (currentState.step == ScanStep.PRODUCT_PHOTO) {
            currentState.copy(
                step = ScanStep.DATE_PHOTO,
                currentItem = currentState.currentItem.copy(productBitmap = byteArray)
            )
        } else {
            val updatedItem = currentState.currentItem.copy(dateBitmap = byteArray)
            val newList = currentState.scannedItems + updatedItem
            currentState.copy(
                step = ScanStep.PRODUCT_PHOTO,
                currentItem = ScannedItem(),
                scannedItems = newList
            )
        }
        _state.value = updatedState
    }

    private fun finishScanning() {
        viewModelScope.launch {
            _events.emit(ScanningEvent.Finished(_state.value.scannedItems))
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraRepository.unbind()
    }
}
