package de.eugens.bestbefore.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val KEY_CHECK_HOUR = intPreferencesKey("check_hour")
    private val KEY_CHECK_MINUTE = intPreferencesKey("check_minute")
    private val KEY_EXPIRATION_THRESHOLD = intPreferencesKey("expiration_threshold")

    override fun getCheckTimeFlow(): Flow<Pair<Int, Int>> {
        return context.dataStore.data.map { preferences ->
            val hour = preferences[KEY_CHECK_HOUR] ?: 9
            val minute = preferences[KEY_CHECK_MINUTE] ?: 0
            hour to minute
        }
    }

    override suspend fun getCheckTime(): Pair<Int, Int> {
        return getCheckTimeFlow().first()
    }

    override suspend fun saveCheckTime(hour: Int, minute: Int) {
        context.dataStore.edit { settings ->
            settings[KEY_CHECK_HOUR] = hour
            settings[KEY_CHECK_MINUTE] = minute
        }
    }

    override fun getExpirationThresholdFlow(): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_EXPIRATION_THRESHOLD] ?: Constants.UPCOMING_EXPIRATION_DAYS_THRESHOLD
        }
    }

    override suspend fun saveExpirationThreshold(threshold: Int) {
        context.dataStore.edit { settings ->
            settings[KEY_EXPIRATION_THRESHOLD] = threshold
        }
    }
}
