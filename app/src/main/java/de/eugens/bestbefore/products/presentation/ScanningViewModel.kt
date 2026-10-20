package de.eugens.bestbefore.products.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.repository.ImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanningIntent {
    data class PhotoCaptured(
        val imageBytes: ByteArray,
        val rotationDegrees: Int
    ) : ScanningIntent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PhotoCaptured

            if (rotationDegrees != other.rotationDegrees) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = rotationDegrees
            result = 31 * result + imageBytes.contentHashCode()
            return result
        }
    }

    data object FinishScanning : ScanningIntent()
    data object CancelScanning : ScanningIntent()
}

sealed class ScanningEvent {
    data class Finished(val items: List<ScannedItem>) : ScanningEvent()
}

@HiltViewModel
class ScanningViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(UiState.Scanning(step = ScanStep.PRODUCT_PHOTO))
    val state: StateFlow<UiState.Scanning> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScanningEvent>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch(defaultDispatcher) {
            imageRepository.clearAllTempFiles()
        }
    }

    fun onAction(intent: ScanningIntent) {
        when (intent) {
            is ScanningIntent.PhotoCaptured -> handlePhotoCaptured(
                intent.imageBytes,
                intent.rotationDegrees
            )

            is ScanningIntent.FinishScanning -> finishScanning()
            is ScanningIntent.CancelScanning -> cancelScanning()
        }
    }

    private fun handlePhotoCaptured(
        imageBytes: ByteArray,
        rotationDegrees: Int
    ) {
        viewModelScope.launch(defaultDispatcher) {
            try {
                val imagePath =
                    imageRepository.processImage(imageBytes, _state.value.step, rotationDegrees)
                onPhotoCaptured(imagePath)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ScanningViewModel", "Photo capture failed", e)
            }
        }
    }

    private fun onPhotoCaptured(imagePath: String) {
        _state.update { currentState ->
            if (currentState.step == ScanStep.PRODUCT_PHOTO) {
                currentState.copy(
                    step = ScanStep.DATE_PHOTO,
                    currentItem = currentState.currentItem.copy(productImagePath = imagePath)
                )
            } else {
                val updatedItem = currentState.currentItem.copy(dateImagePath = imagePath)
                val newList = currentState.scannedItems + updatedItem
                currentState.copy(
                    step = ScanStep.PRODUCT_PHOTO,
                    currentItem = ScannedItem(),
                    scannedItems = newList
                )
            }
        }
    }

    private fun finishScanning() {
        viewModelScope.launch {
            val items = _state.value.scannedItems
            _state.update { UiState.Scanning(step = ScanStep.PRODUCT_PHOTO) }
            _events.emit(ScanningEvent.Finished(items))
        }
    }

    private fun cancelScanning() {
        viewModelScope.launch(defaultDispatcher) {
            val currentItem = _state.value.currentItem
            val itemsToClear = if (currentItem.productImagePath != null || currentItem.dateImagePath != null) {
                _state.value.scannedItems + currentItem
            } else {
                _state.value.scannedItems
            }
            imageRepository.clearTempFiles(itemsToClear)
            _state.update { UiState.Scanning(step = ScanStep.PRODUCT_PHOTO) }
        }
    }
}
