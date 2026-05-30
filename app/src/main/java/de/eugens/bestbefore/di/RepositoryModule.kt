package de.eugens.bestbefore.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.eugens.bestbefore.settings.data.DataStoreSettingsRepository
import de.eugens.bestbefore.products.data.FirebaseProductRepository
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

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
}
