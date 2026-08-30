package de.eugens.bestbefore

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dagger.hilt.android.AndroidEntryPoint
import de.eugens.bestbefore.settings.domain.repository.SettingsRepository
import de.eugens.bestbefore.products.presentation.ProductFilter
import de.eugens.bestbefore.products.presentation.ProductIntent
import de.eugens.bestbefore.products.presentation.ProductViewModel
import de.eugens.bestbefore.products.presentation.ProductsScreen
import de.eugens.bestbefore.ui.theme.BestBeforeTheme
import de.eugens.bestbefore.worker.ExpirationWorkScheduler
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val productViewModel: ProductViewModel by viewModels()

    @Inject
    lateinit var workScheduler: ExpirationWorkScheduler

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize daily check
        lifecycleScope.launch {
            val (hour, minute) = settingsRepository.getCheckTime()
            workScheduler.scheduleDailyCheck(hour, minute)
        }

        handleIntent(intent)

        setContent {
            BestBeforeTheme {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides this) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ProductsScreen(productViewModel = productViewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra("FILTER_TYPE") == "EXPIRED_AND_UPCOMING") {
            productViewModel.onAction(ProductIntent.SetFilter(ProductFilter.EXPIRED_AND_UPCOMING))
        }
    }
}
