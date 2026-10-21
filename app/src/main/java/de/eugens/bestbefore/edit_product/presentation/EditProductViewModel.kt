package de.eugens.bestbefore.edit_product.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.edit_product.domain.use_case.UpdateProductUseCase
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.use_case.DeleteProductUseCase
import de.eugens.bestbefore.products.domain.use_case.GetProductImageFileUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class EditProductEvent {
    data object ProductSaved : EditProductEvent()
    data object ProductDeleted : EditProductEvent()
}

@HiltViewModel
class EditProductViewModel @Inject constructor(
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
    private val getProductImageFileUseCase: GetProductImageFileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProductUiState())
    val uiState: StateFlow<EditProductUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditProductEvent>()
    val events: Flow<EditProductEvent> = _events.receiveAsFlow()

    fun onAction(intent: EditProductIntent) {
        when (intent) {
            is EditProductIntent.SetProduct -> setProduct(intent.product, intent.initialImagePath)
            is EditProductIntent.ChangeName -> onNameChange(intent.newName)
            is EditProductIntent.ChangeExpirationDate -> onExpirationDateChange(intent.newDate)
            is EditProductIntent.SaveProduct -> saveProduct()
            is EditProductIntent.DeleteProduct -> deleteProduct()
        }
    }

    fun setProduct(product: Product, initialImagePath: String? = null) {
        _uiState.value = EditProductUiState(product = product, imagePath = initialImagePath)
        if (initialImagePath == null && product.hasImage) {
            loadImagePathFromRepository(product.id)
        }
    }

    private fun loadImagePathFromRepository(productId: String) {
        viewModelScope.launch {
            val imagePath = try {
                getProductImageFileUseCase(productId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
            _uiState.value = _uiState.value.copy(imagePath = imagePath)
        }
    }

    fun onNameChange(newName: String) {
        _uiState.value = _uiState.value.copy(
            product = _uiState.value.product.copy(name = newName)
        )
    }

    fun onExpirationDateChange(newDate: String) {
        _uiState.value = _uiState.value.copy(
            product = _uiState.value.product.copy(expirationDate = newDate)
        )
    }

    fun saveProduct() {
        viewModelScope.launch {
            try {
                updateProductUseCase(_uiState.value.product)
                _events.send(EditProductEvent.ProductSaved)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteProduct() {
        viewModelScope.launch {
            try {
                deleteProductUseCase(_uiState.value.product.id)
                _events.send(EditProductEvent.ProductDeleted)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
