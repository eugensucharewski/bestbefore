package de.eugens.bestbefore.edit_product.presentation

import de.eugens.bestbefore.MainDispatcherRule
import de.eugens.bestbefore.edit_product.domain.use_case.UpdateProductUseCase
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.use_case.DeleteProductUseCase
import de.eugens.bestbefore.products.domain.use_case.GetProductImageFileUseCase
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
        viewModel.onAction(EditProductIntent.SetProduct(product, imagePath))
        
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
        viewModel.onAction(EditProductIntent.SetProduct(product))

        // Then
        coVerify { getProductImageFileUseCase("1") }
        assertEquals("/path/to/cached/image.jpg", viewModel.uiState.value.imagePath)
    }

    @Test
    fun `onNameChange updates product name in state`() = runTest {
        // Given
        val product = Product(id = "1", name = "Old Name")
        viewModel.onAction(EditProductIntent.SetProduct(product))
        
        // When
        viewModel.onAction(EditProductIntent.ChangeName("New Name"))
        
        // Then
        assertEquals("New Name", viewModel.uiState.value.product.name)
    }

    @Test
    fun `saveProduct calls updateProductUseCase and onSuccess`() = runTest {
        // Given
        val product = Product(id = "1", name = "Updated Product")
        viewModel.onAction(EditProductIntent.SetProduct(product))
        coEvery { updateProductUseCase(any()) } returns Unit
        var successCalled = false
        
        // When
        viewModel.onAction(EditProductIntent.SaveProduct { successCalled = true })
        
        // Then
        coVerify { updateProductUseCase(product) }
        assertEquals(true, successCalled)
    }

    @Test
    fun `saveProduct failure sets error in state`() = runTest {
        // Given
        val product = Product(id = "1", name = "Faulty Save")
        viewModel.onAction(EditProductIntent.SetProduct(product))
        val errorMessage = "Database Error"
        coEvery { updateProductUseCase(any()) } throws Exception(errorMessage)
        
        // When
        viewModel.onAction(EditProductIntent.SaveProduct { })
        
        // Then
        assertEquals(errorMessage, viewModel.uiState.value.error)
    }

    @Test
    fun `deleteProduct calls deleteProductUseCase and onSuccess`() = runTest {
        // Given
        val product = Product(id = "1", name = "Product to delete")
        viewModel.onAction(EditProductIntent.SetProduct(product))
        coEvery { deleteProductUseCase(any()) } returns Unit
        var successCalled = false

        // When
        viewModel.onAction(EditProductIntent.DeleteProduct { successCalled = true })

        // Then
        coVerify { deleteProductUseCase("1") }
        assertEquals(true, successCalled)
    }
}
