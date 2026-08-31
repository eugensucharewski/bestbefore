package de.eugens.bestbefore.products.presentation

import android.util.Base64
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.auth.data.repository.FirebaseAuthRepository
import de.eugens.bestbefore.auth.presentation.AuthState
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import de.eugens.bestbefore.products.domain.repository.CameraRepository
import de.eugens.bestbefore.products.domain.use_case.*
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
import java.util.UUID
import javax.inject.Inject

sealed class ProductIntent {
    data object LoadProducts : ProductIntent()
    data object StartScanning : ProductIntent()
    data object PopBackStack : ProductIntent()
    data object OpenSettings : ProductIntent()
    data object BackToMain : ProductIntent()
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
    data class LoadImage(val productId: String) : ProductIntent()
}

sealed class ProductEvent {
    data object NotifyCompletion : ProductEvent()
}

enum class ExpirationStatus {
    EXPIRED, UPCOMING, FRESH, UNKNOWN
}

data class ProductUiModel(
    val product: Product,
    val status: ExpirationStatus,
    val imageBase64: String? = null
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
    settingsRepository: SettingsRepository,
    private val authRepository: FirebaseAuthRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val formatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT)

    companion object {
        private const val TAG = "ProductViewModel"
        private const val BACKSTACK_KEY = "backstack"
    }

    private val _backStack = savedStateHandle.getStateFlow(BACKSTACK_KEY, listOf<UiState>(UiState.MainList))
    
    private var backStack: List<UiState>
        get() = _backStack.value
        set(value) {
            savedStateHandle[BACKSTACK_KEY] = value
        }

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    private val _currentFilter = MutableStateFlow(ProductFilter.ALL)
    private val _productToDelete = MutableStateFlow<Product?>(null)
    private val _selectedProductIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isLoading = MutableStateFlow(false)

    private val threshold = settingsRepository.getExpirationThresholdFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.UPCOMING_EXPIRATION_DAYS_THRESHOLD)

    private val _loadedImages = MutableStateFlow<Map<String, String>>(emptyMap())

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<ProductScreenState> = combine(
        _backStack,
        _products,
        _currentFilter,
        _productToDelete,
        _selectedProductIds,
        _isLoading,
        threshold,
        _loadedImages,
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
        val loadedImages = args[7] as Map<String, String>
        val authState = args[8] as AuthState

        val filtered = applyFilter(products, filter, thresholdValue)
        val uiModels = sort(filtered).map { product ->
            ProductUiModel(
                product = product,
                status = getExpirationStatus(product, thresholdValue),
                imageBase64 = loadedImages[product.id]
            )
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

    private fun parseDate(dateStr: String): LocalDate? {
        val formats = listOf("dd.MM.yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "d.M.yyyy", "yyyy/MM/dd")
        for (format in formats) {
            try {
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(format))
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    private fun getExpirationStatus(product: Product, thresholdValue: Int): ExpirationStatus {
        val date = parseDate(product.expirationDate) ?: return ExpirationStatus.UNKNOWN
        val today = LocalDate.now()
        val daysUntil = ChronoUnit.DAYS.between(today, date)
        return when {
            daysUntil < 0 -> ExpirationStatus.EXPIRED
            daysUntil <= thresholdValue -> ExpirationStatus.UPCOMING
            else -> ExpirationStatus.FRESH
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
        return products.sortedBy { product -> 
            parseDate(product.expirationDate) ?: LocalDate.MAX 
        }
    }

    private fun isExpired(product: Product): Boolean {
        val date = parseDate(product.expirationDate) ?: return false
        return date.isBefore(LocalDate.now())
    }

    private fun isUpcoming(product: Product, thresholdValue: Int): Boolean {
        val date = parseDate(product.expirationDate) ?: return false
        val today = LocalDate.now()
        val daysUntil = ChronoUnit.DAYS.between(today, date)
        return daysUntil in 0..thresholdValue
    }

    fun onAction(intent: ProductIntent) {
        when (intent) {
            is ProductIntent.LoadProducts -> loadProducts()
            is ProductIntent.StartScanning -> startScanning()
            is ProductIntent.PopBackStack -> popBackStack()
            is ProductIntent.OpenSettings -> openSettings()
            is ProductIntent.BackToMain -> backToMain()
            is ProductIntent.FinishScanning -> { /* handled by scanning session flow */ }
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
            is ProductIntent.LoadImage -> loadImage(intent.productId)
        }
    }

    private fun loadImage(productId: String) {
        if (_loadedImages.value.containsKey(productId)) return
        
        viewModelScope.launch {
            try {
                val image = getProductsUseCase.getImage(productId)
                if (image != null) {
                    _loadedImages.value = _loadedImages.value.toMutableMap().apply {
                        put(productId, image)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadImage failed for $productId", e)
            }
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
        backStack = backStack + editState
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
            backStack = backStack.map { state ->
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
                backStack = backStack + UiState.Error(e.localizedMessage ?: "Failed to load products")
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
        backStack = backStack + UiState.Scanning(
            step = ScanStep.PRODUCT_PHOTO,
            scanId = UUID.randomUUID().toString()
        )
    }

    private fun popBackStack() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
        } else {
            backStack = listOf(UiState.MainList)
        }
    }

    private fun openSettings() {
        backStack = backStack + UiState.Settings
    }

    private fun backToMain() {
        backStack = listOf(UiState.MainList)
    }

    fun processItems(items: List<ScannedItem>) {
        backStack = backStack + UiState.Processing

        viewModelScope.launch {
            try {
                val results = analyzeImagesUseCase(items)
                if (results.isEmpty()) {
                    throw Exception("AI could not recognize any products. Please try taking clearer photos.")
                }
                saveAnalysisResultsUseCase(results, items)

                refreshProducts()
                backStack = listOf(UiState.MainList)
                _events.emit(ProductEvent.NotifyCompletion)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "processItems failed", e)
                backStack = backStack + UiState.Error(e.localizedMessage ?: "Analysis failed")
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
                backStack = listOf(UiState.MainList)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "updateProduct failed", e)
            }
        }
    }

    private fun backFromError() {
        val currentBackStack = backStack
        if (currentBackStack.lastOrNull() is UiState.Error) {
            val stackWithoutError = currentBackStack.dropLast(1)
            if (stackWithoutError.lastOrNull() is UiState.Processing) {
                backStack = stackWithoutError.dropLast(1)
            } else {
                backStack = stackWithoutError
            }
        }
    }
}
