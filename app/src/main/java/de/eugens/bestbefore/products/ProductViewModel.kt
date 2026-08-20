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
import de.eugens.bestbefore.auth.AuthRepository
import de.eugens.bestbefore.auth.AuthState
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
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
    data class DeleteProduct(val product: Product) : ProductIntent()
    data object ConfirmDelete : ProductIntent()
    data object DismissDelete : ProductIntent()
    data class AddProduct(val product: Product) : ProductIntent()
    data class SetFilter(val filter: ProductFilter) : ProductIntent()
    data class SelectProductForEdit(val product: Product) : ProductIntent()
    data class UpdateProduct(val product: Product) : ProductIntent()
    data class ToggleSelection(val productId: String) : ProductIntent()
    data object ClearSelection : ProductIntent()
    data object DeleteSelectedProducts : ProductIntent()
    data object BackFromError : ProductIntent()
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

data class ProductScreenState(
    val uiState: UiState = UiState.MainList,
    val products: List<ProductUiModel> = emptyList(),
    val currentFilter: ProductFilter = ProductFilter.ALL,
    val authState: AuthState = AuthState.Loading,
    val backStack: List<UiState> = listOf(UiState.MainList),
    val productToDelete: Product? = null,
    val selectedProductIds: Set<String> = emptySet(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val getProductsUseCase: GetProductsUseCase,
    private val addProductUseCase: AddProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val deleteProductUseCase: DeleteProductUseCase,
    private val analyzeImagesUseCase: AnalyzeImagesUseCase,
    private val saveAnalysisResultsUseCase: SaveAnalysisResultsUseCase,
    private val processImageUseCase: ProcessImageUseCase,
    settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    private val formatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT)

    companion object {
        private const val TAG = "ProductViewModel"
    }

    private val _backStack: MutableStateFlow<List<UiState>> = MutableStateFlow(listOf(UiState.MainList))
    private val _products = MutableStateFlow<List<Product>>(emptyList())
    private val _currentFilter = MutableStateFlow(ProductFilter.ALL)
    private val _productToDelete = MutableStateFlow<Product?>(null)
    private val _selectedProductIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isLoading = MutableStateFlow(false)

    private val threshold = settingsRepository.getExpirationThresholdFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.UPCOMING_EXPIRATION_DAYS_THRESHOLD)

    val state: StateFlow<ProductScreenState> = combine(
        _backStack,
        _products,
        _currentFilter,
        _productToDelete,
        _selectedProductIds,
        _isLoading,
        threshold,
        authRepository.observeAuthState().onStart {
            emit(authRepository.currentUserEmail?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated)
        }
    ) { args ->
        val backStack = args[0] as List<UiState>
        val products = args[1] as List<Product>
        val filter = args[2] as ProductFilter
        val productToDelete = args[3] as? Product
        val selectedProductIds = args[4] as Set<String>
        val isLoading = args[5] as Boolean
        val thresholdValue = args[6] as Int
        val authState = args[7] as AuthState

        val filtered = applyFilter(products, filter, thresholdValue)
        val uiModels = sort(filtered).map { product ->
            ProductUiModel(product, getExpirationStatus(product, thresholdValue))
        }
        ProductScreenState(
            uiState = backStack.lastOrNull() ?: UiState.MainList,
            products = uiModels,
            currentFilter = filter,
            authState = authState,
            backStack = backStack,
            productToDelete = productToDelete,
            selectedProductIds = selectedProductIds,
            isLoading = isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProductScreenState()
    )

    private val _events = MutableSharedFlow<ProductEvent>()
    val events = _events.asSharedFlow()

    init {
        loadProducts()
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
            is ProductIntent.DeleteProduct -> _productToDelete.value = intent.product
            is ProductIntent.ConfirmDelete -> {
                _productToDelete.value?.let { deleteProduct(it.id) }
                _productToDelete.value = null
            }
            is ProductIntent.DismissDelete -> _productToDelete.value = null
            is ProductIntent.AddProduct -> addProduct(intent.product)
            is ProductIntent.SetFilter -> _currentFilter.value = intent.filter
            is ProductIntent.SelectProductForEdit -> selectProductForEdit(intent.product)
            is ProductIntent.UpdateProduct -> updateProduct(intent.product)
            is ProductIntent.ToggleSelection -> toggleSelection(intent.productId)
            is ProductIntent.ClearSelection -> _selectedProductIds.value = emptySet()
            is ProductIntent.DeleteSelectedProducts -> deleteSelectedProducts()
            is ProductIntent.BackFromError -> backFromError()
        }
    }

    private fun toggleSelection(productId: String) {
        val current = _selectedProductIds.value
        _selectedProductIds.value = if (current.contains(productId)) {
            current - productId
        } else {
            current + productId
        }
    }

    private fun deleteSelectedProducts() {
        val idsToDelete = _selectedProductIds.value
        if (idsToDelete.isEmpty()) return
        
        viewModelScope.launch {
            try {
                idsToDelete.forEach { id ->
                    deleteProductUseCase(id)
                }
                _selectedProductIds.value = emptySet()
                refreshProducts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "deleteSelectedProducts failed", e)
            }
        }
    }

    private fun selectProductForEdit(product: Product) {
        val editState = UiState.EditProduct(product)
        _backStack.value = _backStack.value + editState
        viewModelScope.launch {
            val bitmapBytes = withContext(Dispatchers.IO) {
                product.productImage?.let {
                    try {
                        Base64.decode(it, Base64.DEFAULT)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            _backStack.value = _backStack.value.map { state ->
                if (state is UiState.EditProduct && state.product.id == product.id) {
                    state.copy(productBitmap = bitmapBytes)
                } else {
                    state
                }
            }
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            try {
                refreshProducts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadProducts failed", e)
                _backStack.value = _backStack.value + UiState.Error(e.localizedMessage ?: "Failed to load products")
            }
        }
    }

    private suspend fun refreshProducts() {
        _isLoading.value = true
        try {
            _products.value = getProductsUseCase()
        } finally {
            _isLoading.value = false
        }
    }

    private fun startScanning() {
        _backStack.value = _backStack.value + UiState.Scanning(step = ScanStep.PRODUCT_PHOTO)
    }

    private fun cancelScanning() {
        if (_backStack.value.size > 1) {
            _backStack.value = _backStack.value.dropLast(1)
        } else {
            _backStack.value = listOf(UiState.MainList)
        }
    }

    private fun openSettings() {
        _backStack.value = _backStack.value + UiState.Settings
    }

    private fun backToMain() {
        _backStack.value = listOf(UiState.MainList)
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
        val currentState = _backStack.value.lastOrNull()
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
        val backStack = _backStack.value
        val currentState = backStack.lastOrNull()
        if (currentState is UiState.Scanning) {
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
            _backStack.value = backStack.dropLast(1) + updatedState
        }
    }

    private fun finishScanning() {
        val currentState = _backStack.value.lastOrNull()
        if (currentState is UiState.Scanning) {
            val itemsToProcess = currentState.scannedItems
            if (itemsToProcess.isEmpty()) {
                _backStack.value = _backStack.value.dropLast(1)
                return
            }
            processItems(itemsToProcess)
        }
    }

    private fun processItems(items: List<ScannedItem>) {
        val backStack = _backStack.value
        _backStack.value = backStack + UiState.Processing

        viewModelScope.launch {
            try {
                val results = analyzeImagesUseCase(items)
                if (results.isEmpty()) {
                    throw Exception("AI could not recognize any products. Please try taking clearer photos.")
                }
                saveAnalysisResultsUseCase(results, items)

                refreshProducts()
                _backStack.value = listOf(UiState.MainList)
                _events.emit(ProductEvent.NotifyCompletion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "processItems failed", e)
                _backStack.value = _backStack.value + UiState.Error(e.localizedMessage ?: "Analysis failed")
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
                _backStack.value = listOf(UiState.MainList)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "updateProduct failed", e)
            }
        }
    }

    private fun backFromError() {
        val currentBackStack = _backStack.value
        if (currentBackStack.lastOrNull() is UiState.Error) {
            val stackWithoutError = currentBackStack.dropLast(1)
            if (stackWithoutError.lastOrNull() is UiState.Processing) {
                _backStack.value = stackWithoutError.dropLast(1)
            } else {
                _backStack.value = stackWithoutError
            }
        }
    }
}
