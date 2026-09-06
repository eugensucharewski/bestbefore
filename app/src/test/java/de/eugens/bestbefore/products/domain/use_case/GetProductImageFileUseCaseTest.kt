package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.repository.ProductRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

@ExperimentalCoroutinesApi
class GetProductImageFileUseCaseTest {

    private lateinit var useCase: GetProductImageFileUseCase
    private val repository: ProductRepository = mockk()

    @Before
    fun setUp() {
        useCase = GetProductImageFileUseCase(repository)
    }

    @Test
    fun `invoke returns absolute path when file exists`() = runTest {
        val mockFile = mockk<File>()
        every { mockFile.exists() } returns true
        every { mockFile.absolutePath } returns "/path/to/image.jpg"
        coEvery { repository.getProductImageFile("prod123") } returns mockFile

        val result = useCase("prod123")

        assertEquals("/path/to/image.jpg", result)
        coVerify { repository.getProductImageFile("prod123") }
    }

    @Test
    fun `invoke returns null when file does not exist`() = runTest {
        val mockFile = mockk<File>()
        every { mockFile.exists() } returns false
        coEvery { repository.getProductImageFile("prod123") } returns mockFile

        val result = useCase("prod123")

        assertNull(result)
        coVerify { repository.getProductImageFile("prod123") }
    }

    @Test
    fun `invoke returns null when file is null`() = runTest {
        coEvery { repository.getProductImageFile("prod123") } returns null

        val result = useCase("prod123")

        assertNull(result)
        coVerify { repository.getProductImageFile("prod123") }
    }
}
