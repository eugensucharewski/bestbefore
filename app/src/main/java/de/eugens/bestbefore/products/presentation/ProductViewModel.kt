package de.eugens.bestbefore.products.presentation

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.auth.domain.repository.AuthRepository
import de.eugens.bestbefore.auth.presentation.AuthState
import de.eugens.bestbefore.products.domain.model.ExpirationStatus
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.use_case.AddProductUseCase
import de.eugens.bestbefore.products.domain.use_case.AnalyzeImagesUseCase
import de.eugens.bestbefore.products.domain.use_case.ClearTempScanFilesUseCase
import de.eugens.bestbefore.products.domain.use_case.DeleteProductUseCase
import de.eugens.bestbefore.products.domain.use_case.FilterProductsUseCase
import de.eugens.bestbefore.products.domain.use_case.FormatExpirationDateUseCase
import de.eugens.bestbefore.products.domain.use_case.GetExpirationStatusUseCase
import de.eugens.bestbefore.products.domain.use_case.GetProductImageFileUseCase
import de.eugens.bestbefore.products.domain.use_case.GetProductsUseCase
import de.eugens.bestbefore.products.domain.use_case.SaveAnalysisResultsUseCase
import de.eugens.bestbefore.products.domain.use_case.SortProductsUseCase
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    data class ToggleSelection(val productId: String) : ProductIntent()
    data object ClearSelection : ProductIntent()
    data object DeleteSelectedProducts : ProductIntent()
    data object BackFromError : ProductIntent()
    data class LoadImage(val productId: String) : ProductIntent()
}

sealed class ProductEvent {
    data object NotifyCompletion : ProductEvent()
}

@Immutable
data class ProductUiModel(
    val product: Product,
    val status: ExpirationStatus,
    val imagePath: String? = null,
    val formattedExpirationDate: String = "",
    val formattedProductionDate: String? = null
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
    private val deleteProductUseCase: DeleteProductUseCase,
    private val analyzeImagesUseCase: AnalyzeImagesUseCase,
    private val saveAnalysisResultsUseCase: SaveAnalysisResultsUseCase,
    private val clearTempScanFilesUseCase: ClearTempScanFilesUseCase,
    private val getProductImageFileUseCase: GetProductImageFileUseCase,
    private val getExpirationStatusUseCase: GetExpirationStatusUseCase,
    private val filterProductsUseCase: FilterProductsUseCase,
    private val sortProductsUseCase: SortProductsUseCase,
    private val formatExpirationDateUseCase: FormatExpirationDateUseCase,
    settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "ProductViewModel"
        private const val BACKSTACK_KEY = "backstack"
    }

    private val _backStack =
        savedStateHandle.getStateFlow(BACKSTACK_KEY, listOf<UiState>(UiState.MainList))

    private var backStack: List<UiState>
        get() = _backStack.value
        set(value) {
            savedStateHandle[BACKSTACK_KEY] = value
        }

    private val threshold = settingsRepository.getExpirationThresholdFlow()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            Constants.UPCOMING_EXPIRATION_DAYS_THRESHOLD
        )

    private data class InternalState(
        val products: List<Product> = emptyList(),
        val currentFilter: ProductFilter = ProductFilter.ALL,
        val productToDelete: Product? = null,
        val selectedProductIds: Set<String> = emptySet(),
        val isLoading: Boolean = false,
        val loadedImagePaths: Map<String, String?> = emptyMap()
    )

    private val _internalState = MutableStateFlow(InternalState())

    val state: StateFlow<ProductScreenState> = combine(
        _internalState,
        _backStack,
        threshold,
        authRepository.observeAuthState().onStart {
            emit(
                authRepository.currentUserEmail?.let { AuthState.Authenticated(it) }
                    ?: AuthState.Unauthenticated
            )
        }
    ) { internal, backStack, thresholdValue, authState ->
        val filtered = filterProductsUseCase(internal.products, internal.currentFilter, thresholdValue)
        val uiModels = sortProductsUseCase(filtered).map { product ->
            ProductUiModel(
                product = product,
                status = getExpirationStatusUseCase(product, thresholdValue),
                imagePath = internal.loadedImagePaths[product.id],
                formattedExpirationDate = formatExpirationDateUseCase(product.expirationDate),
                formattedProductionDate = product.productionDate?.let { formatExpirationDateUseCase(it) }
            )
        }
        ProductScreenState(
            uiState = backStack.lastOrNull() ?: UiState.MainList,
            products = uiModels,
            currentFilter = internal.currentFilter,
            authState = authState,
            backStack = backStack,
            productToDelete = internal.productToDelete,
            selectedProductIds = internal.selectedProductIds,
            isLoading = internal.isLoading
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProductScreenState()
        )

    private val _events = MutableSharedFlow<ProductEvent>()
    val events = _events.asSharedFlow()

    init {
        loadProducts()
    }

    fun onAction(intent: ProductIntent) {
        when (intent) {
            is ProductIntent.LoadProducts -> loadProducts()
            is ProductIntent.StartScanning -> startScanning()
            is ProductIntent.PopBackStack -> popBackStack()
            is ProductIntent.OpenSettings -> openSettings()
            is ProductIntent.BackToMain -> backToMain()
            is ProductIntent.FinishScanning -> { /* handled by scanning session flow */
            }

            is ProductIntent.DeleteProduct -> _internalState.update { it.copy(productToDelete = intent.product) }
            is ProductIntent.ConfirmDelete -> {
                _internalState.value.productToDelete?.let { deleteProduct(it.id) }
                _internalState.update { it.copy(productToDelete = null) }
            }

            is ProductIntent.DismissDelete -> _internalState.update { it.copy(productToDelete = null) }
            is ProductIntent.AddProduct -> addProduct(intent.product)
            is ProductIntent.SetFilter -> _internalState.update { it.copy(currentFilter = intent.filter) }
            is ProductIntent.SelectProductForEdit -> selectProductForEdit(intent.product)
            is ProductIntent.ToggleSelection -> toggleSelection(intent.productId)
            is ProductIntent.ClearSelection -> _internalState.update { it.copy(selectedProductIds = emptySet()) }
            is ProductIntent.DeleteSelectedProducts -> deleteSelectedProducts()
            is ProductIntent.BackFromError -> backFromError()
            is ProductIntent.LoadImage -> loadImage(intent.productId)
        }
    }

    private val loadingProductIds = mutableSetOf<String>()

    private fun loadImage(productId: String) {
        synchronized(loadingProductIds) {
            if (_internalState.value.loadedImagePaths.containsKey(productId) || !loadingProductIds.add(productId)) {
                return
            }
        }

        viewModelScope.launch {
            try {
                val imagePath = getProductImageFileUseCase(productId)
                _internalState.update { it.copy(loadedImagePaths = it.loadedImagePaths + (productId to imagePath)) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "loadImage failed for $productId", e)
                _internalState.update { it.copy(loadedImagePaths = it.loadedImagePaths + (productId to null)) }
            } finally {
                synchronized(loadingProductIds) {
                    loadingProductIds.remove(productId)
                }
            }
        }
    }

    private fun toggleSelection(productId: String) {
        _internalState.update { state ->
            val current = state.selectedProductIds
            val newSelection = if (current.contains(productId)) {
                current - productId
            } else {
                current + productId
            }
            state.copy(selectedProductIds = newSelection)
        }
    }

    private fun deleteSelectedProducts() {
        val idsToDelete = _internalState.value.selectedProductIds
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            try {
                idsToDelete.forEach { id ->
                    deleteProductUseCase(id)
                }
                _internalState.update { it.copy(selectedProductIds = emptySet()) }
                refreshProducts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "deleteSelectedProducts failed", e)
            }
        }
    }

    private fun selectProductForEdit(product: Product) {
        viewModelScope.launch {
            val imagePath = try {
                getProductImageFileUseCase(product.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to get image file for product ${product.id}", e)
                null
            }
            backStack = backStack + UiState.EditProduct(product = product, imagePath = imagePath)
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
                backStack =
                    backStack + UiState.Error(e.localizedMessage ?: "Failed to load products")
            }
        }
    }

    private suspend fun refreshProducts() {
        _internalState.update { it.copy(isLoading = true) }
        try {
            val products = getProductsUseCase()
            val imageMap = mutableMapOf<String, String?>()
            for (product in products) {
                if (product.hasImage) {
                    try {
                        val path = getProductImageFileUseCase(product.id)
                        if (path != null) {
                            imageMap[product.id] = path
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Failed to preload image for product ${product.id}", e)
                    }
                }
            }
            _internalState.update {
                it.copy(
                    products = products,
                    loadedImagePaths = if (imageMap.isNotEmpty()) it.loadedImagePaths + imageMap else it.loadedImagePaths,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _internalState.update { it.copy(isLoading = false) }
            throw e
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
            } finally {
                clearTempScanFilesUseCase(items)
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
