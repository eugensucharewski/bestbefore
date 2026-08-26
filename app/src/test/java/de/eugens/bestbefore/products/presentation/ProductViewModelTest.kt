package de.eugens.bestbefore.products.presentation

import app.cash.turbine.test
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.MainDispatcherRule
import de.eugens.bestbefore.auth.data.repository.FirebaseAuthRepository
import de.eugens.bestbefore.auth.presentation.AuthState
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.use_case.*
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ExperimentalCoroutinesApi
class ProductViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ProductViewModel
    private val getProductsUseCase: GetProductsUseCase = mockk()
    private val addProductUseCase: AddProductUseCase = mockk()
    private val updateProductUseCase: UpdateProductUseCase = mockk()
    private val deleteProductUseCase: DeleteProductUseCase = mockk()
    private val analyzeImagesUseCase: AnalyzeImagesUseCase = mockk()
    private val saveAnalysisResultsUseCase: SaveAnalysisResultsUseCase = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private val authRepository: FirebaseAuthRepository = mockk()

    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    private val thresholdFlow = MutableStateFlow(2)
    private val today = LocalDate.now()
    private val formatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT)

    @Before
    fun setUp() {
        coEvery { getProductsUseCase() } returns emptyList()
        every { settingsRepository.getExpirationThresholdFlow() } returns thresholdFlow
        every { authRepository.observeAuthState() } returns authStateFlow
        every { authRepository.currentUserEmail } returns null
        
        initViewModel()
    }

    private fun initViewModel() {
        viewModel = ProductViewModel(
            getProductsUseCase,
            addProductUseCase,
            updateProductUseCase,
            deleteProductUseCase,
            analyzeImagesUseCase,
            saveAnalysisResultsUseCase,
            settingsRepository,
            authRepository
        )
    }

    private fun createProduct(id: String, daysOffset: Long, name: String = "Product $id"): Product {
        return Product(
            id = id,
            name = name,
            expirationDate = today.plusDays(daysOffset).format(formatter)
        )
    }

    @Test
    fun `initial state loads products and uses default threshold`() = runTest {
        // Given
        val products = listOf(
            createProduct(id = "1", daysOffset = -1, name = "Expired"),
            createProduct(id = "2", daysOffset = 10, name = "Fresh")
        )
        coEvery { getProductsUseCase() } returns products
        
        // When
        initViewModel()

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(2, state.products.size)
            assertEquals(ExpirationStatus.EXPIRED, state.products.find { it.product.id == "1" }?.status)
            assertEquals(ExpirationStatus.FRESH, state.products.find { it.product.id == "2" }?.status)
        }
    }

    @Test
    fun `getExpirationStatus correctly identifies UPCOMING status`() = runTest {
        // Given
        val products = listOf(createProduct(id = "1", daysOffset = 1))
        coEvery { getProductsUseCase() } returns products
        thresholdFlow.value = 2
        
        // When
        viewModel.onAction(ProductIntent.LoadProducts)

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(ExpirationStatus.UPCOMING, state.products[0].status)
        }
    }

    @Test
    fun `applyFilter ALL returns all products`() = runTest {
        // Given
        val products = listOf(
            createProduct(id = "1", daysOffset = -1),
            createProduct(id = "2", daysOffset = 5)
        )
        coEvery { getProductsUseCase() } returns products
        viewModel.onAction(ProductIntent.LoadProducts)
        
        // When
        viewModel.onAction(ProductIntent.SetFilter(ProductFilter.ALL))

        // Then
        viewModel.state.test {
            assertEquals(2, awaitItem().products.size)
        }
    }

    @Test
    fun `applyFilter EXPIRED returns only expired products`() = runTest {
        // Given
        val products = listOf(
            createProduct(id = "1", daysOffset = -1, name = "Expired"),
            createProduct(id = "2", daysOffset = 5, name = "Fresh")
        )
        coEvery { getProductsUseCase() } returns products
        viewModel.onAction(ProductIntent.LoadProducts)
        
        // When
        viewModel.onAction(ProductIntent.SetFilter(ProductFilter.EXPIRED))

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(1, state.products.size)
            assertEquals("Expired", state.products[0].product.name)
        }
    }

    @Test
    fun `sorting orders products by expiration date`() = runTest {
        // Given
        val products = listOf(
            createProduct(id = "1", daysOffset = 10),
            createProduct(id = "2", daysOffset = 1),
            createProduct(id = "3", daysOffset = -5)
        )
        coEvery { getProductsUseCase() } returns products
        
        // When
        viewModel.onAction(ProductIntent.LoadProducts)

        // Then
        viewModel.state.test {
            val state = awaitItem()
            assertEquals("3", state.products[0].product.id)
            assertEquals("2", state.products[1].product.id)
            assertEquals("1", state.products[2].product.id)
        }
    }

    @Test
    fun `backstack management works correctly`() = runTest {
        viewModel.state.test {
            // Initial
            assertEquals(UiState.MainList, awaitItem().uiState)
            
            // When
            viewModel.onAction(ProductIntent.StartScanning)
            // Then
            assertTrue(awaitItem().uiState is UiState.Scanning)
            
            // When
            viewModel.onAction(ProductIntent.CancelScanning)
            // Then
            assertEquals(UiState.MainList, awaitItem().uiState)
        }
    }

    @Test
    fun `toggleSelection updates selectedProductIds`() = runTest {
        viewModel.state.test {
            awaitItem() // Initial state
            
            // When
            viewModel.onAction(ProductIntent.ToggleSelection("1"))
            // Then
            assertEquals(setOf("1"), awaitItem().selectedProductIds)
            
            // When
            viewModel.onAction(ProductIntent.ToggleSelection("1"))
            // Then
            assertEquals(emptySet<String>(), awaitItem().selectedProductIds)
            
            // When
            viewModel.onAction(ProductIntent.ToggleSelection("2"))
            // Then
            assertEquals(setOf("2"), awaitItem().selectedProductIds)
        }
    }

    @Test
    fun `deleteProduct action sets productToDelete`() = runTest {
        val product = createProduct("1", 1)
        viewModel.state.test {
            viewModel.onAction(ProductIntent.DeleteProduct(product))
            // Skip initial states if any and wait for the one with productToDelete
            var lastState = awaitItem()
            while (lastState.productToDelete != product) {
                lastState = awaitItem()
            }
            assertEquals(product, lastState.productToDelete)
        }
    }

    @Test
    fun `confirmDelete action calls deleteProductUseCase`() = runTest {
        // Given
        val product = createProduct("1", 1)
        coEvery { deleteProductUseCase(any()) } returns Unit
        coEvery { getProductsUseCase() } returns emptyList()

        viewModel.state.test {
            viewModel.onAction(ProductIntent.DeleteProduct(product))
            
            // Wait for productToDelete to be set
            while (awaitItem().productToDelete != product) { 
                // Wait
            }
            
            // When
            viewModel.onAction(ProductIntent.ConfirmDelete)
            
            // Then
            // Wait for productToDelete to be null and isLoading to be false
            var finalState = awaitItem()
            while (finalState.productToDelete != null || finalState.isLoading) {
                finalState = awaitItem()
            }
            org.junit.Assert.assertNull(finalState.productToDelete)
            coVerify { deleteProductUseCase("1") }
        }
    }

    @Test
    fun `deleteSelectedProducts action calls deleteProductUseCase for each id`() = runTest {
        // Given
        coEvery { deleteProductUseCase(any()) } returns Unit
        coEvery { getProductsUseCase() } returns emptyList()

        viewModel.state.test {
            viewModel.onAction(ProductIntent.ToggleSelection("1"))
            while (awaitItem().selectedProductIds.isEmpty()) { 
                // Wait
            }
            
            viewModel.onAction(ProductIntent.ToggleSelection("2"))
            while (awaitItem().selectedProductIds.size < 2) { 
                // Wait
            }

            // When
            viewModel.onAction(ProductIntent.DeleteSelectedProducts)

            // Then
            var finalState = awaitItem()
            while (finalState.selectedProductIds.isNotEmpty() || finalState.isLoading) {
                finalState = awaitItem()
            }
            coVerify { deleteProductUseCase("1") }
            coVerify { deleteProductUseCase("2") }
            assertEquals(emptySet<String>(), finalState.selectedProductIds)
        }
    }

    @Test
    fun `processItems updates backstack and calls use cases`() = runTest {
        // Given
        val items = emptyList<ScannedItem>()
        val analysisResults = listOf(ExpirationInfo(productName = "New", date_found = true, expiration_date = "2024-12-31"))
        coEvery { analyzeImagesUseCase(any()) } returns analysisResults
        coEvery { saveAnalysisResultsUseCase(any(), any()) } returns Unit
        coEvery { getProductsUseCase() } returns emptyList()

        viewModel.state.test {
            // When
            viewModel.processItems(items)

            // Then
            var state = awaitItem()
            // Wait for Processing state
            while (state.backStack.lastOrNull() !is UiState.Processing) {
                state = awaitItem()
            }
            
            // Wait for MainList state
            while (state.backStack.lastOrNull() !is UiState.MainList || state.isLoading) {
                state = awaitItem()
            }
            
            assertEquals(UiState.MainList, state.backStack.last())
            coVerify { analyzeImagesUseCase(items) }
            coVerify { saveAnalysisResultsUseCase(analysisResults, items) }
        }
    }
}

