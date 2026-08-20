package de.eugens.bestbefore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.eugens.bestbefore.auth.data.repository.FirebaseAuthRepository
import de.eugens.bestbefore.auth.domain.repository.AuthRepository
import de.eugens.bestbefore.products.data.repository.CameraXRepository
import de.eugens.bestbefore.products.data.repository.FirebaseProductRepository
import de.eugens.bestbefore.products.data.analyzer.GeminiProductAnalyzer
import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.repository.CameraRepository
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import de.eugens.bestbefore.settings.data.DataStoreSettingsRepository
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: FirebaseAuthRepository
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: DataStoreSettingsRepository
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(
        productRepositoryImpl: FirebaseProductRepository
    ): ProductRepository

    @Binds
    @Singleton
    abstract fun bindAIProductAnalyzer(
        geminiProductAnalyzerImpl: GeminiProductAnalyzer
    ): AIProductAnalyzer

    @Binds
    @Singleton
    abstract fun bindCameraRepository(
        cameraXRepositoryImpl: CameraXRepository
    ): CameraRepository
}
