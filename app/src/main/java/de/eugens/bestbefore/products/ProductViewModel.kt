package de.eugens.bestbefore.products

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import de.eugens.bestbefore.products.domain.usecase.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

sealed class ProductIntent {
    data object LoadProducts : ProductIntent()
    data object StartScanning : ProductIntent()
    data object CancelScanning : ProductIntent()
    data object OpenSettings : ProductIntent()
    data object BackToMain : ProductIntent()
    data class RequestCapture(val context: Context) : ProductIntent()
    data class ProcessCapturedImage(val image: ImageProxy) : ProductIntent()
    data class CapturePhoto(val bitmap: Bitmap) : ProductIntent()
    data object FinishScanning : ProductIntent()
    data class DeleteProduct(val productId: String) : ProductIntent()
    data object ClearAllProducts : ProductIntent()
    data class AddProduct(val product: Product) : ProductIntent()
    data class SetFilter(val filter: ProductFilter) : ProductIntent()
    data class SelectProductForEdit(val product: Product) : ProductIntent()
    data class UpdateProduct(val product: Product) : ProductIntent()
}

sealed class ProductEvent {
    data object NotifyCompletion : ProductEvent()
}

enum class ExpirationStatus {
    EXPIRED, UPCOMING, FRESH, UNKNOWN
}

data class ProductUiModel(
    val product: Product,
    val status: ExpirationStatus
)

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val getProductsUseCase: GetProductsUseCase,
    private val addProductUseCase: AddProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
    private val clearAllProductsUseCase: ClearAllProductsUseCase,
    private val analyzeImagesUseCase: AnalyzeImagesUseCase,
    private val saveAnalysisResultsUseCase: SaveAnalysisResultsUseCase,
    private val processImageUseCase: ProcessImageUseCase,
    settingsRepository: SettingsRepository
) : ViewModel() {

    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    private val formatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT)

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

    private val _filteredProducts = MutableStateFlow<List<ProductUiModel>>(emptyList())
    val products: StateFlow<List<ProductUiModel>> = _filteredProducts.asStateFlow()

    val threshold = settingsRepository.getExpirationThresholdFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.UPCOMING_EXPIRATION_DAYS_THRESHOLD)

    init {
        loadProducts()
        observeProductsAndFilter()
    }

    private fun observeProductsAndFilter() {
        viewModelScope.launch {
            combine(_products, _currentFilter, threshold) { products, filter, thresholdValue ->
                val filtered = applyFilter(products, filter, thresholdValue)
                sort(filtered).map { product ->
                    ProductUiModel(product, getExpirationStatus(product, thresholdValue))
                }
            }.collect { uiModels ->
                _filteredProducts.value = uiModels
            }
        }
    }

    private fun getExpirationStatus(product: Product, thresholdValue: Int): ExpirationStatus {
        return try {
            val date = LocalDate.parse(product.expirationDate, formatter)
            val today = LocalDate.now()
            val daysUntil = ChronoUnit.DAYS.between(today, date)
            when {
                daysUntil < 0 -> ExpirationStatus.EXPIRED
                daysUntil <= thresholdValue -> ExpirationStatus.UPCOMING
                else -> ExpirationStatus.FRESH
            }
        } catch (e: Exception) {
            ExpirationStatus.UNKNOWN
        }
    }

    private fun applyFilter(products: List<Product>, filter: ProductFilter, thresholdValue: Int): List<Product> {
        return when (filter) {
            ProductFilter.ALL -> products
            ProductFilter.EXPIRED -> products.filter { isExpired(it) }
            ProductFilter.EXPIRED_AND_UPCOMING -> products.filter { isExpired(it) || isUpcoming(it, thresholdValue) }
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

    private fun isUpcoming(product: Product, thresholdValue: Int): Boolean {
        return try {
            val date = LocalDate.parse(product.expirationDate, formatter)
            val today = LocalDate.now()
            val daysUntil = ChronoUnit.DAYS.between(today, date)
            daysUntil in 0..thresholdValue
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
            is ProductIntent.RequestCapture -> requestCapture(intent.context)
            is ProductIntent.ProcessCapturedImage -> processCapturedImage(intent.image)
            is ProductIntent.CapturePhoto -> capturePhoto(intent.bitmap)
            is ProductIntent.FinishScanning -> finishScanning()
            is ProductIntent.DeleteProduct -> deleteProduct(intent.productId)
            is ProductIntent.ClearAllProducts -> clearAllProducts()
            is ProductIntent.AddProduct -> addProduct(intent.product)
            is ProductIntent.SetFilter -> _currentFilter.value = intent.filter
            is ProductIntent.SelectProductForEdit -> selectProductForEdit(intent.product)
            is ProductIntent.UpdateProduct -> updateProduct(intent.product)
        }
    }

    private fun selectProductForEdit(product: Product) {
        _uiState.value = UiState.EditProduct(product)
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                product.productImage?.let {
                    try {
                        val decodedString = Base64.decode(it, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            val currentState = _uiState.value
            if (currentState is UiState.EditProduct && currentState.product.id == product.id) {
                _uiState.value = currentState.copy(productBitmap = bitmap)
            }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            refreshProducts()
        }
    }

    private suspend fun refreshProducts() {
        try {
            _products.value = getProductsUseCase()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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

    private fun requestCapture(context: Context) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(
            mainExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    processCapturedImage(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun processCapturedImage(image: ImageProxy) {
        val currentState = _uiState.value
        if (currentState is UiState.Scanning) {
            viewModelScope.launch {
                try {
                    val bitmap = processImageUseCase(image, currentState.step)
                    capturePhoto(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "processCapturedImage failed", e)
                    image.close()
                }
            }
        } else {
            image.close()
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
                val results = analyzeImagesUseCase(items)
                saveAnalysisResultsUseCase(results, items)

                refreshProducts()
                _uiState.value = UiState.MainList
                _events.emit(ProductEvent.NotifyCompletion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "processItems failed", e)
                _uiState.value = UiState.Error(e.localizedMessage ?: "Analysis failed")
            }
        }
    }

    private fun deleteProduct(productId: String) {
        viewModelScope.launch {
            try {
                deleteProductUseCase(productId)
                refreshProducts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "deleteProduct failed", e)
            }
        }
    }

    private fun clearAllProducts() {
        viewModelScope.launch {
            try {
                clearAllProductsUseCase()
                refreshProducts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "clearAllProducts failed", e)
            }
        }
    }

    private fun addProduct(product: Product) {
        viewModelScope.launch {
            try {
                addProductUseCase(product)
                refreshProducts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "addProduct failed", e)
            }
        }
    }

    private fun updateProduct(product: Product) {
        viewModelScope.launch {
            try {
                updateProductUseCase(product)
                refreshProducts()
                _uiState.value = UiState.MainList
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "updateProduct failed", e)
            }
        }
    }
}
