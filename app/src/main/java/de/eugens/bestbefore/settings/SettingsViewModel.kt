package de.eugens.bestbefore.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import de.eugens.bestbefore.worker.ExpirationWorkScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val workScheduler: ExpirationWorkScheduler
) : ViewModel() {

    val checkTime: StateFlow<Pair<Int, Int>> = settingsRepository.getCheckTimeFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 9 to 0)

    val threshold: StateFlow<Int> = settingsRepository.getExpirationThresholdFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.UPCOMING_EXPIRATION_DAYS_THRESHOLD)

    fun setCheckTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            workScheduler.scheduleDailyCheck(hour, minute)
        }
    }

    fun setThreshold(newThreshold: Int) {
        if (newThreshold < 1) return
        viewModelScope.launch {
            settingsRepository.saveExpirationThreshold(newThreshold)
        }
    }

    fun setLanguage(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }
    
    fun getCurrentLanguageCode(): String {
        return AppCompatDelegate.getApplicationLocales().toLanguageTags()
    }
}
