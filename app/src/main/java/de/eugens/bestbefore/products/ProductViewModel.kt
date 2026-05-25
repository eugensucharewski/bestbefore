package de.eugens.bestbefore.products

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.eugens.bestbefore.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

sealed class ProductIntent {
    data object LoadProducts : ProductIntent()
    data object StartScanning : ProductIntent()
    data object CancelScanning : ProductIntent()
    data object OpenSettings : ProductIntent()
    data object BackToMain : ProductIntent()
    data object RequestCapture : ProductIntent()
    data class CapturePhoto(val bitmap: Bitmap) : ProductIntent()
    data object FinishScanning : ProductIntent()
    data class DeleteProduct(val productId: String) : ProductIntent()
    data object ClearAllProducts : ProductIntent()
    data class AddProduct(val product: Product) : ProductIntent()
    data class SetFilter(val filter: ProductFilter) : ProductIntent()
    data class UpdateProduct(val product: Product) : ProductIntent()
}

sealed class ProductEvent {
    data object TriggerCapture : ProductEvent()
    data object NotifyCompletion : ProductEvent()
}

class ProductViewModel(
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT),
    private val repository: ProductRepository = ProductRepository()
) : ViewModel() {

    companion object {
        private const val TAG = "ProductViewModel"
    }

    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.MainList)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductEvent>()
    val events = _events.asSharedFlow()

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    
    private val _currentFilter = MutableStateFlow(ProductFilter.ALL)
    val currentFilter: StateFlow<ProductFilter> = _currentFilter.asStateFlow()

    private val _filteredProducts = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _filteredProducts.asStateFlow()

    init {
        loadProducts()
        observeProductsAndFilter()
    }

    private fun observeProductsAndFilter() {
        viewModelScope.launch {
            combine(_products, _currentFilter) { products, filter ->
                applyFilter(products, filter)
            }.collect { filtered ->
                _filteredProducts.value = sort(filtered)
            }
        }
    }

    private fun applyFilter(products: List<Product>, filter: ProductFilter): List<Product> {
        return when (filter) {
            ProductFilter.ALL -> products
            ProductFilter.EXPIRED -> products.filter { isExpired(it) }
            ProductFilter.EXPIRED_AND_UPCOMING -> products.filter { isExpired(it) || isUpcoming(it) }
        }
    }

    private fun sort(products: List<Product>): List<Product> {
        return products.sortedBy { product -> LocalDate.parse(product.expirationDate, formatter) }
    }

    private fun isExpired(product: Product): Boolean {
        return try {
            val date = LocalDate.parse(product.expirationDate, formatter)
            date.isBefore(LocalDate.now())
        } catch (e: Exception) {
            false
        }
    }

    private fun isUpcoming(product: Product): Boolean {
        return try {
            val date = LocalDate.parse(product.expirationDate, formatter)
            val today = LocalDate.now()
            val daysUntil = ChronoUnit.DAYS.between(today, date)
            daysUntil in 0..7
        } catch (e: Exception) {
            false
        }
    }

    fun onAction(intent: ProductIntent) {
        when (intent) {
            is ProductIntent.LoadProducts -> loadProducts()
            is ProductIntent.StartScanning -> startScanning()
            is ProductIntent.CancelScanning -> cancelScanning()
            is ProductIntent.OpenSettings -> openSettings()
            is ProductIntent.BackToMain -> backToMain()
            is ProductIntent.RequestCapture -> requestCapture()
            is ProductIntent.CapturePhoto -> capturePhoto(intent.bitmap)
            is ProductIntent.FinishScanning -> finishScanning()
            is ProductIntent.DeleteProduct -> deleteProduct(intent.productId)
            is ProductIntent.ClearAllProducts -> clearAllProducts()
            is ProductIntent.AddProduct -> addProduct(intent.product)
            is ProductIntent.SetFilter -> _currentFilter.value = intent.filter
            is ProductIntent.UpdateProduct -> { /* TODO updateProduct(intent.product) */ }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            refreshProducts()
        }
    }

    private suspend fun refreshProducts() {
        try {
            _products.value = repository.getProducts()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "refreshProducts failed", e)
            _uiState.value = UiState.Error(e.localizedMessage ?: "Failed to load products")
        }
    }

    private fun startScanning() {
        _uiState.value = UiState.Scanning(step = ScanStep.PRODUCT_PHOTO)
    }

    private fun cancelScanning() {
        _uiState.value = UiState.MainList
    }

    private fun openSettings() {
        _uiState.value = UiState.Settings
    }

    private fun backToMain() {
        _uiState.value = UiState.MainList
    }

    private fun requestCapture() {
        viewModelScope.launch {
            _events.emit(ProductEvent.TriggerCapture)
        }
    }

    private fun capturePhoto(bitmap: Bitmap) {
        val currentState = _uiState.value
        if (currentState is UiState.Scanning) {
            if (currentState.step == ScanStep.PRODUCT_PHOTO) {
                _uiState.value = currentState.copy(
                    step = ScanStep.DATE_PHOTO,
                    currentItem = currentState.currentItem.copy(productBitmap = bitmap)
                )
            } else {
                val updatedItem = currentState.currentItem.copy(dateBitmap = bitmap)
                val newList = currentState.scannedItems + updatedItem
                _uiState.value = currentState.copy(
                    step = ScanStep.PRODUCT_PHOTO,
                    currentItem = ScannedItem(),
                    scannedItems = newList
                )
            }
        }
    }

    private fun finishScanning() {
        val currentState = _uiState.value
        if (currentState is UiState.Scanning) {
            val itemsToProcess = currentState.scannedItems
            if (itemsToProcess.isEmpty()) {
                _uiState.value = UiState.MainList
                return
            }
            processItems(itemsToProcess)
        }
    }

    private fun processItems(items: List<ScannedItem>) {
        _uiState.value = UiState.Processing

        viewModelScope.launch {
            try {
                val results = repository.analyzeImages(items)
                repository.saveAnalysisResults(results, items)

                refreshProducts()
                _uiState.value = UiState.MainList
                _events.emit(ProductEvent.NotifyCompletion)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "processItems failed", e)
                _uiState.value = UiState.Error(e.localizedMessage ?: "Analysis failed")
            }
        }
    }

    private fun deleteProduct(productId: String) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(productId)
                refreshProducts()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "deleteProduct failed", e)
            }
        }
    }

    private fun clearAllProducts() {
        viewModelScope.launch {
            try {
                repository.clearAllProducts()
                refreshProducts()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "clearAllProducts failed", e)
            }
        }
    }

    private fun addProduct(product: Product) {
        viewModelScope.launch {
            try {
                repository.addProduct(product)
                refreshProducts()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "addProduct failed", e)
            }
        }
    }

    private fun updateProduct(product: Product) {
        viewModelScope.launch {
            try {
                repository.updateProduct(product)
                refreshProducts()
                _uiState.value = UiState.MainList
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "updateProduct failed", e)
            }
        }
    }
}
