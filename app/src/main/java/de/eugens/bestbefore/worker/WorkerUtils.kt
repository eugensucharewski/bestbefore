package de.eugens.bestbefore.worker

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object WorkerUtils {
    private const val WORK_NAME = "expiration_check_work"
    private val KEY_CHECK_HOUR = intPreferencesKey("check_hour")
    private val KEY_CHECK_MINUTE = intPreferencesKey("check_minute")
    private val KEY_EXPIRATION_THRESHOLD = intPreferencesKey("expiration_threshold")

    suspend fun scheduleDailyCheck(context: Context, hour: Int = 9, minute: Int = 0) {
        saveCheckTime(context, hour, minute)
        
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val initialDelay = target.timeInMillis - now.timeInMillis

        val workRequest = PeriodicWorkRequestBuilder<ExpirationCheckWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private suspend fun saveCheckTime(context: Context, hour: Int, minute: Int) {
        context.dataStore.edit { settings ->
            settings[KEY_CHECK_HOUR] = hour
            settings[KEY_CHECK_MINUTE] = minute
        }
    }

    fun getCheckTimeFlow(context: Context): Flow<Pair<Int, Int>> {
        return context.dataStore.data.map { preferences ->
            val hour = preferences[KEY_CHECK_HOUR] ?: 9
            val minute = preferences[KEY_CHECK_MINUTE] ?: 0
            hour to minute
        }
    }

    suspend fun getCheckTime(context: Context): Pair<Int, Int> {
        return getCheckTimeFlow(context).first()
    }

    fun getExpirationThresholdFlow(context: Context): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[KEY_EXPIRATION_THRESHOLD] ?: de.eugens.bestbefore.Constants.UPCOMING_EXPIRATION_DAYS_THRESHOLD
        }
    }

    suspend fun saveExpirationThreshold(context: Context, threshold: Int) {
        context.dataStore.edit { settings ->
            settings[KEY_EXPIRATION_THRESHOLD] = threshold
        }
    }
}
