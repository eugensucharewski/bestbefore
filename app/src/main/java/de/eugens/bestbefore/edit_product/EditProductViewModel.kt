package de.eugens.bestbefore.edit_product

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.products.domain.use_case.UpdateProductUseCase
import de.eugens.bestbefore.products.domain.use_case.DeleteProductUseCase
import de.eugens.bestbefore.products.domain.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EditProductViewModel @Inject constructor(
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditProductUiState())
    val uiState: StateFlow<EditProductUiState> = _uiState.asStateFlow()

    fun setProduct(product: Product, initialBitmap: Bitmap? = null) {
        _uiState.value = EditProductUiState(product = product, productBitmap = initialBitmap)
        if (initialBitmap == null && product.productImage != null) {
            loadBitmap(product.productImage)
        }
    }

    private fun loadBitmap(encodedImage: String) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val decodedString = Base64.decode(encodedImage, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                } catch (e: Exception) {
                    null
                }
            }
            _uiState.value = _uiState.value.copy(productBitmap = bitmap)
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
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}

data class EditProductUiState(
    val product: Product = Product(),
    val productBitmap: Bitmap? = null,
    val error: String? = null
)
