package de.eugens.bestbefore.products.presentation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.MainActivity
import de.eugens.bestbefore.R
import de.eugens.bestbefore.auth.presentation.AuthIntent
import de.eugens.bestbefore.auth.presentation.AuthState
import de.eugens.bestbefore.auth.presentation.AuthViewModel
import de.eugens.bestbefore.auth.presentation.AuthScreen
import de.eugens.bestbefore.edit_product.EditProductScreen
import de.eugens.bestbefore.edit_product.EditProductViewModel
import de.eugens.bestbefore.settings.SettingsScreen
import de.eugens.bestbefore.settings.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProductsScreen(
    productViewModel: ProductViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val state by productViewModel.state.collectAsState()

    val context = LocalContext.current

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

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

                    val intent = Intent(context, MainActivity::class.java).apply {
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
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    LaunchedEffect(state.authState) {
        if (state.authState is AuthState.Authenticated) {
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
        when (state.authState) {
            is AuthState.Authenticated -> {
                NavDisplay(
                    backStack = state.backStack,
                    onBack = {
                        if (state.selectedProductIds.isNotEmpty()) {
                            productViewModel.onAction(ProductIntent.ClearSelection)
                        } else {
                            productViewModel.onAction(ProductIntent.PopBackStack)
                        }
                    },
                    entryProvider = entryProvider {
                        entry<UiState.MainList> { uiState ->
                            MainScreen(
                                products = state.products,
                                currentFilter = state.currentFilter,
                                selectedProductIds = state.selectedProductIds,
                                isLoading = state.isLoading,
                                onFilterChange = { productViewModel.onAction(ProductIntent.SetFilter(it)) },
                                onProductClick = { productViewModel.onAction(ProductIntent.SelectProductForEdit(it)) },
                                onAddClick = { productViewModel.onAction(ProductIntent.StartScanning) },
                                onToggleSelection = { productViewModel.onAction(ProductIntent.ToggleSelection(it)) },
                                onClearSelection = { productViewModel.onAction(ProductIntent.ClearSelection) },
                                onDeleteSelected = { productViewModel.onAction(ProductIntent.DeleteSelectedProducts) },
                                onSettingsClick = { productViewModel.onAction(ProductIntent.OpenSettings) },
                                onLoadImage = { productViewModel.onAction(ProductIntent.LoadImage(it)) }
                            )
                        }

                        entry<UiState.Scanning>(
                            clazzContentKey = { "scanning" }
                        ) { uiState ->
                            val scanningViewModel: ScanningViewModel = hiltViewModel()
                            val scanningState by scanningViewModel.state.collectAsState()

                            LaunchedEffect(Unit) {
                                scanningViewModel.events.collectLatest { event ->
                                    when (event) {
                                        is ScanningEvent.Finished -> {
                                            productViewModel.processItems(event.items)
                                        }
                                    }
                                }
                            }
                            ScanningScreen(
                                state = scanningState,
                                cameraController = scanningViewModel.cameraController as androidx.camera.view.LifecycleCameraController,
                                onFlashToggle = { enabled -> scanningViewModel.onAction(ScanningIntent.SetFlashEnabled(enabled)) },
                                onCaptureClick = { scanningViewModel.onAction(ScanningIntent.RequestCapture) },
                                onCancel = { productViewModel.onAction(ProductIntent.PopBackStack) },
                                onFinish = { scanningViewModel.onAction(ScanningIntent.FinishScanning) }
                            )
                        }

                        entry<UiState.Processing> {
                            ProcessingScreen()
                        }

                        entry<UiState.Settings> {
                            val settingsViewModel: SettingsViewModel = hiltViewModel()
                            SettingsScreen(
                                onBack = { productViewModel.onAction(ProductIntent.PopBackStack) },
                                onSignOut = {
                                    authViewModel.onAction(AuthIntent.SignOut)
                                    productViewModel.onAction(ProductIntent.BackToMain)
                                },
                                viewModel = settingsViewModel
                            )
                        }

                        entry<UiState.EditProduct> { uiState ->
                            val editProductViewModel: EditProductViewModel = hiltViewModel()
                            val bitmap = remember(uiState.productBitmap) {
                                uiState.productBitmap?.let {
                                    BitmapFactory.decodeByteArray(it, 0, it.size)
                                }
                            }
                            LaunchedEffect(uiState.product, bitmap) {
                                editProductViewModel.setProduct(uiState.product, bitmap)
                            }
                            EditProductScreen(
                                viewModel = editProductViewModel,
                                onBack = {
                                    if (state.selectedProductIds.isNotEmpty()) {
                                        productViewModel.onAction(ProductIntent.ClearSelection)
                                    } else {
                                        productViewModel.onAction(ProductIntent.PopBackStack)
                                    }
                                }
                            )
                        }

                        entry<UiState.Error> { uiState ->
                            ErrorScreen(uiState.errorMessage) {
                                productViewModel.onAction(ProductIntent.BackFromError)
                            }
                        }
                    }
                )
            }
            is AuthState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> AuthScreen(authViewModel)
        }
    }
}
