package de.eugens.bestbefore.products.presentation

import android.graphics.Bitmap
import app.cash.turbine.test
import de.eugens.bestbefore.MainDispatcherRule
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.repository.CameraRepository
import de.eugens.bestbefore.products.domain.use_case.ProcessImageUseCase
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ScanningViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ScanningViewModel
    private val cameraRepository: CameraRepository = mockk()
    private val processImageUseCase: ProcessImageUseCase = mockk()

    @Before
    fun setUp() {
        every { cameraRepository.getController() } returns mockk()
        viewModel = ScanningViewModel(cameraRepository, processImageUseCase)
    }

    @Test
    fun `initial state is scanning product photo`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(ScanStep.PRODUCT_PHOTO, state.step)
        }
    }

    @Test
    fun `capture photo transitions from product to date step`() = runTest {
        // Given
        val bitmap: Bitmap = mockk()
        val processedBitmap: Bitmap = mockk()
        coEvery { cameraRepository.takePicture() } returns bitmap
        coEvery { processImageUseCase(bitmap, ScanStep.PRODUCT_PHOTO) } returns processedBitmap
        
        // Mock bitmap compression
        every { processedBitmap.compress(any(), any(), any()) } returns true

        viewModel.state.test {
            awaitItem() // Initial state
            
            // When
            viewModel.onAction(ScanningIntent.RequestCapture)
            
            // Then
            val state = awaitItem()
            assertEquals(ScanStep.DATE_PHOTO, state.step)
            org.junit.Assert.assertNotNull(state.currentItem.productBitmap)
        }
    }

    @Test
    fun `capture second photo adds item and returns to product step`() = runTest {
        // Given
        val bitmap: Bitmap = mockk()
        val processedBitmap: Bitmap = mockk()
        coEvery { cameraRepository.takePicture() } returns bitmap
        coEvery { processImageUseCase(any(), any()) } returns processedBitmap
        every { processedBitmap.compress(any(), any(), any()) } returns true

        viewModel.state.test {
            awaitItem() // Initial
            
            // First capture (Product)
            viewModel.onAction(ScanningIntent.RequestCapture)
            awaitItem() // Skip to Date step
            
            // Second capture (Date)
            viewModel.onAction(ScanningIntent.RequestCapture)
            
            // Then
            val state = awaitItem()
            assertEquals(ScanStep.PRODUCT_PHOTO, state.step)
            assertEquals(1, state.scannedItems.size)
        }
    }

    @Test
    fun `finishScanning emits Finished event with collected items`() = runTest {
        // Given
        val bitmap: Bitmap = mockk()
        val processedBitmap: Bitmap = mockk()
        coEvery { cameraRepository.takePicture() } returns bitmap
        coEvery { processImageUseCase(any(), any()) } returns processedBitmap
        every { processedBitmap.compress(any(), any(), any()) } returns true

        viewModel.events.test {
            // Add one item
            viewModel.onAction(ScanningIntent.RequestCapture)
            viewModel.onAction(ScanningIntent.RequestCapture)
            
            // When
            viewModel.onAction(ScanningIntent.FinishScanning)
            
            // Then
            val event = awaitItem()
            assertTrue(event is ScanningEvent.Finished)
            assertEquals(1, (event as ScanningEvent.Finished).items.size)
            
            // Verify state is reset
            val finalState = viewModel.state.value
            assertEquals(0, finalState.scannedItems.size)
            assertEquals(ScanStep.PRODUCT_PHOTO, finalState.step)
        }
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
