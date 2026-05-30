package de.eugens.bestbefore.products

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.R
import de.eugens.bestbefore.auth.AuthIntent
import de.eugens.bestbefore.auth.AuthState
import de.eugens.bestbefore.auth.AuthViewModel
import de.eugens.bestbefore.auth.AuthScreen
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.edit.EditProductScreen
import de.eugens.bestbefore.products.edit.EditProductViewModel
import de.eugens.bestbefore.settings.SettingsScreen
import de.eugens.bestbefore.settings.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProductsScreen(
    productViewModel: ProductViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    editProductViewModel: EditProductViewModel = viewModel()
) {
    val uiState by productViewModel.uiState.collectAsState()
    val products by productViewModel.products.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val currentFilter by productViewModel.currentFilter.collectAsState()

    val context = LocalContext.current

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        productViewModel.events.collectLatest { event ->
            when (event) {
                is ProductEvent.NotifyCompletion -> {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channelId = Constants.NOTIFICATION_CHANNEL_ID

                    val channel = NotificationChannel(
                        channelId,
                        context.getString(R.string.processing_results_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationManager.createNotificationChannel(channel)

                    val intent = Intent(context, de.eugens.bestbefore.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notification = NotificationCompat.Builder(context, channelId)
                        .setContentTitle(context.getString(R.string.processing_completed))
                        .setContentText(context.getString(R.string.new_products_added))
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

                    notificationManager.notify(Constants.NOTIFICATION_ID, notification)
                }
                else -> {}
            }
        }
    }

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            productViewModel.onAction(ProductIntent.LoadProducts)
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
        if (notificationPermissionState != null && !notificationPermissionState.status.isGranted) {
            notificationPermissionState.launchPermissionRequest()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (authState) {
            is AuthState.Authenticated -> {
                when (val state = uiState) {
                    is UiState.MainList -> MainScreen(
                        products = products,
                        currentFilter = currentFilter,
                        onFilterChange = { productViewModel.onAction(ProductIntent.SetFilter(it)) },
                        onProductClick = { productViewModel.onAction(ProductIntent.SelectProductForEdit(it)) },
                        onAddClick = { productViewModel.onAction(ProductIntent.StartScanning) },
                        onDeleteProduct = { productViewModel.onAction(ProductIntent.DeleteProduct(it.id)) },
                        onUndoDelete = { productViewModel.onAction(ProductIntent.AddProduct(it)) },
                        onClearAll = { productViewModel.onAction(ProductIntent.ClearAllProducts) },
                        onSettingsClick = { productViewModel.onAction(ProductIntent.OpenSettings) }
                    )
                    is UiState.Scanning -> ScanningScreen(
                        state = state,
                        events = productViewModel.events,
                        onCaptureClick = { productViewModel.onAction(ProductIntent.RequestCapture) },
                        onCaptureResult = { productViewModel.onAction(ProductIntent.CapturePhoto(it)) },
                        onCancel = { productViewModel.onAction(ProductIntent.CancelScanning) },
                        onFinish = { productViewModel.onAction(ProductIntent.FinishScanning) }
                    )
                    is UiState.Processing -> ProcessingScreen()
                    is UiState.Settings -> SettingsScreen(
                        onBack = { productViewModel.onAction(ProductIntent.BackToMain) },
                        onSignOut = {
                            authViewModel.onAction(AuthIntent.SignOut)
                            productViewModel.onAction(ProductIntent.BackToMain)
                        },
                        viewModel = settingsViewModel
                    )
                    is UiState.EditProduct -> {
                        LaunchedEffect(state.product) {
                            editProductViewModel.setProduct(state.product, state.productBitmap)
                        }
                        EditProductScreen(
                            viewModel = editProductViewModel,
                            onBack = { productViewModel.onAction(ProductIntent.BackToMain) }
                        )
                    }
                    is UiState.Error -> ErrorScreen(state.errorMessage) { productViewModel.onAction(
                        ProductIntent.CancelScanning) }
                    else -> {}
                }
            }
            else -> AuthScreen(authViewModel)
        }
    }
}
