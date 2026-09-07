package de.eugens.bestbefore.edit_product.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.edit_product.domain.use_case.UpdateProductUseCase
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.use_case.DeleteProductUseCase
import de.eugens.bestbefore.products.domain.use_case.GetProductImageFileUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditProductViewModel @Inject constructor(
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
    private val getProductImageFileUseCase: GetProductImageFileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProductUiState())
    val uiState: StateFlow<EditProductUiState> = _uiState.asStateFlow()

    fun onAction(intent: EditProductIntent) {
        when (intent) {
            is EditProductIntent.SetProduct -> setProduct(intent.product, intent.initialImagePath)
            is EditProductIntent.ChangeName -> onNameChange(intent.newName)
            is EditProductIntent.ChangeExpirationDate -> onExpirationDateChange(intent.newDate)
            is EditProductIntent.SaveProduct -> saveProduct(intent.onSuccess)
            is EditProductIntent.DeleteProduct -> deleteProduct(intent.onSuccess)
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

    fun saveProduct(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                updateProductUseCase(_uiState.value.product)
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteProduct(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                deleteProductUseCase(_uiState.value.product.id)
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
