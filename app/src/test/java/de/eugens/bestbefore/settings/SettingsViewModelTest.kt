package de.eugens.bestbefore.settings

import de.eugens.bestbefore.MainDispatcherRule
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import de.eugens.bestbefore.worker.ExpirationWorkScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: SettingsViewModel
    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val workScheduler: ExpirationWorkScheduler = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { settingsRepository.getCheckTimeFlow() } returns flowOf(9 to 0)
        every { settingsRepository.getExpirationThresholdFlow() } returns flowOf(2)
        
        viewModel = SettingsViewModel(settingsRepository, workScheduler)
    }

    @Test
    fun `setCheckTime calls workScheduler`() = runTest {
        // When
        viewModel.setCheckTime(10, 30)
        
        // Then
        coVerify { workScheduler.scheduleDailyCheck(10, 30) }
    }

    @Test
    fun `setThreshold validates and calls repository`() = runTest {
        // When
        viewModel.setThreshold(5)
        // Then
        coVerify { settingsRepository.saveExpirationThreshold(5) }
        
        // When
        viewModel.setThreshold(0) // Invalid input
        // Then
        coVerify(exactly = 0) { settingsRepository.saveExpirationThreshold(0) }
    }
}
