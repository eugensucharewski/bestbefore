package de.eugens.bestbefore.settings.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getCheckTimeFlow(): Flow<Pair<Int, Int>>
    suspend fun getCheckTime(): Pair<Int, Int>
    suspend fun saveCheckTime(hour: Int, minute: Int)
    fun getExpirationThresholdFlow(): Flow<Int>
    suspend fun saveExpirationThreshold(threshold: Int)
}
