package de.eugens.bestbefore.products.presentation

import app.cash.turbine.test
import de.eugens.bestbefore.MainDispatcherRule
import de.eugens.bestbefore.products.domain.repository.ImageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ScanningViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ScanningViewModel
    private val imageRepository: ImageRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        viewModel = ScanningViewModel(
            imageRepository,
            mainDispatcherRule.testDispatcher
        )
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
        val imageBytes = byteArrayOf(1, 2, 3)
        val imagePath = "/tmp/image.jpg"
        coEvery { imageRepository.processImage(any(), ScanStep.PRODUCT_PHOTO, any()) } returns imagePath

        viewModel.state.test {
            awaitItem() // Initial state
            
            // When
            viewModel.onAction(ScanningIntent.PhotoCaptured(imageBytes, 0))
            
            // Then
            val state = awaitItem()
            assertEquals(ScanStep.DATE_PHOTO, state.step)
            Assert.assertNotNull(state.currentItem.productImagePath)
            assertEquals(imagePath, state.currentItem.productImagePath)
        }
    }

    @Test
    fun `capture second photo adds item and returns to product step`() = runTest {
        // Given
        val imageBytes = byteArrayOf(1, 2, 3)
        val imagePath = "/tmp/image.jpg"
        coEvery { imageRepository.processImage(any(), any(), any()) } returns imagePath

        viewModel.state.test {
            awaitItem() // Initial
            
            // First capture (Product)
            viewModel.onAction(ScanningIntent.PhotoCaptured(imageBytes, 0))
            awaitItem() // Skip to Date step
            
            // Second capture (Date)
            viewModel.onAction(ScanningIntent.PhotoCaptured(imageBytes, 0))
            
            // Then
            val state = awaitItem()
            assertEquals(ScanStep.PRODUCT_PHOTO, state.step)
            assertEquals(1, state.scannedItems.size)
            assertEquals(imagePath, state.scannedItems[0].productImagePath)
            assertEquals(imagePath, state.scannedItems[0].dateImagePath)
        }
    }

    @Test
    fun `finishScanning emits Finished event with collected items`() = runTest {
        // Given
        val imageBytes = byteArrayOf(1, 2, 3)
        val imagePath = "/tmp/image.jpg"
        coEvery { imageRepository.processImage(any(), any(), any()) } returns imagePath

        viewModel.events.test {
            // Add one item
            viewModel.onAction(ScanningIntent.PhotoCaptured(imageBytes, 0))
            viewModel.onAction(ScanningIntent.PhotoCaptured(imageBytes, 0))
            
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
        Assert.assertTrue(condition)
    }
}
