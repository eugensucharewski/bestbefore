package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AnalyzeImagesUseCaseTest {

    private val analyzer: AIProductAnalyzer = mockk()
    private lateinit var useCase: AnalyzeImagesUseCase

    @Before
    fun setUp() {
        useCase = AnalyzeImagesUseCase(analyzer)
    }

    @Test
    fun `invoke delegates directly to analyzer analyzeContent`() = runTest {
        val scannedItems = listOf(
            ScannedItem(productImagePath = "/path/prod.jpg", dateImagePath = "/path/date.jpg")
        )
        val expectedResults = listOf(
            ExpirationInfo(productName = "Cheese", date_found = true, expiration_date = "01.01.2027")
        )

        coEvery { analyzer.analyzeImages(scannedItems) } returns expectedResults

        val actual = useCase(scannedItems)

        assertEquals(expectedResults, actual)
        coVerify(exactly = 1) { analyzer.analyzeImages(scannedItems) }
    }
}
