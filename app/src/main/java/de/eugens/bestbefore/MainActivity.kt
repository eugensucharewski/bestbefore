package de.eugens.bestbefore

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import de.eugens.bestbefore.products.ProductFilter
import de.eugens.bestbefore.products.ProductIntent
import de.eugens.bestbefore.products.ProductViewModel
import de.eugens.bestbefore.products.ProductsScreen
import de.eugens.bestbefore.ui.theme.BestBeforeTheme
import de.eugens.bestbefore.worker.WorkerUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val productViewModel: ProductViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize daily check
        lifecycleScope.launch {
            val (hour, minute) = WorkerUtils.getCheckTime(this@MainActivity)
            WorkerUtils.scheduleDailyCheck(this@MainActivity, hour, minute)
        }

        handleIntent(intent)

        setContent {
            BestBeforeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ProductsScreen(productViewModel = productViewModel)
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
