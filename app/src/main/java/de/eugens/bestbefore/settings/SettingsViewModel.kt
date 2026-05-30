package de.eugens.bestbefore.settings

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.worker.WorkerUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    val checkTime: StateFlow<Pair<Int, Int>> = WorkerUtils.getCheckTimeFlow(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 9 to 0)

    val threshold: StateFlow<Int> = WorkerUtils.getExpirationThresholdFlow(application)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Constants.UPCOMING_EXPIRATION_DAYS_THRESHOLD)

    fun setCheckTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            WorkerUtils.scheduleDailyCheck(getApplication(), hour, minute)
        }
    }

    fun setThreshold(newThreshold: Int) {
        if (newThreshold < 1) return
        viewModelScope.launch {
            WorkerUtils.saveExpirationThreshold(getApplication(), newThreshold)
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
