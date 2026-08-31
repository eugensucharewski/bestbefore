package de.eugens.bestbefore

import android.app.Application
import android.util.Log
import com.google.android.gms.security.ProviderInstaller
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BestBeforeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Initialize ProviderInstaller to ensure the device's security provider is up-to-date
        // and avoid GMS connection issues (like Failed to register providerinstaller).
        ProviderInstaller.installIfNeededAsync(this, object : ProviderInstaller.ProviderInstallListener {
            override fun onProviderInstalled() {
                Log.d("BestBeforeApp", "ProviderInstaller: Security provider installed successfully.")
            }

            override fun onProviderInstallFailed(errorCode: Int, recoveryIntent: android.content.Intent?) {
                Log.e("BestBeforeApp", "ProviderInstaller: Security provider installation failed with code: $errorCode")
            }
        })
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
