package de.eugens.bestbefore.edit_product

import de.eugens.bestbefore.MainDispatcherRule
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.use_case.DeleteProductUseCase
import de.eugens.bestbefore.products.domain.use_case.GetProductImageFileUseCase
import de.eugens.bestbefore.products.domain.use_case.UpdateProductUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class EditProductViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: EditProductViewModel
    private val updateProductUseCase: UpdateProductUseCase = mockk()
    private val deleteProductUseCase: DeleteProductUseCase = mockk()
    private val getProductImageFileUseCase: GetProductImageFileUseCase = mockk()

    @Before
    fun setUp() {
        coEvery { getProductImageFileUseCase(any()) } returns null
        viewModel = EditProductViewModel(
            updateProductUseCase,
            deleteProductUseCase,
            getProductImageFileUseCase
        )
    }

    @Test
    fun `setProduct updates state`() = runTest {
        // Given
        val product = Product(id = "1", name = "Test Product")
        val imagePath = "/path/to/image.jpg"
        
        // When
        viewModel.setProduct(product, imagePath)
        
        // Then
        assertEquals(product, viewModel.uiState.value.product)
        assertEquals(imagePath, viewModel.uiState.value.imagePath)
    }

    @Test
    fun `setProduct with hasImage calls getProductImageFileUseCase`() = runTest {
        // Given
        val product = Product(id = "1", name = "Test Product", hasImage = true)
        coEvery { getProductImageFileUseCase("1") } returns "/path/to/cached/image.jpg"

        // When
        viewModel.setProduct(product)

        // Then
        coVerify { getProductImageFileUseCase("1") }
        assertEquals("/path/to/cached/image.jpg", viewModel.uiState.value.imagePath)
    }

    @Test
    fun `onNameChange updates product name in state`() = runTest {
        // Given
        val product = Product(id = "1", name = "Old Name")
        viewModel.setProduct(product)
        
        // When
        viewModel.onNameChange("New Name")
        
        // Then
        assertEquals("New Name", viewModel.uiState.value.product.name)
    }

    @Test
    fun `saveProduct calls updateProductUseCase and onSuccess`() = runTest {
        // Given
        val product = Product(id = "1", name = "Updated Product")
        viewModel.setProduct(product)
        coEvery { updateProductUseCase(any()) } returns Unit
        var successCalled = false
        
        // When
        viewModel.saveProduct { successCalled = true }
        
        // Then
        coVerify { updateProductUseCase(product) }
        assertEquals(true, successCalled)
    }

    @Test
    fun `saveProduct failure sets error in state`() = runTest {
        // Given
        val product = Product(id = "1", name = "Faulty Save")
        viewModel.setProduct(product)
        val errorMessage = "Database Error"
        coEvery { updateProductUseCase(any()) } throws Exception(errorMessage)
        
        // When
        viewModel.saveProduct { }
        
        // Then
        assertEquals(errorMessage, viewModel.uiState.value.error)
    }
}
