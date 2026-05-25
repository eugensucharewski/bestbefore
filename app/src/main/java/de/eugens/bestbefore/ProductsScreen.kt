package de.eugens.bestbefore

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.camera.core.Preview as CameraPreview

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ProductsScreen(
    productViewModel: ProductViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val uiState by productViewModel.uiState.collectAsState()
    val products by productViewModel.products.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val currentFilter by productViewModel.currentFilter.collectAsState()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current

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

                    val notification = NotificationCompat.Builder(context, channelId)
                        .setContentTitle(context.getString(R.string.processing_completed))
                        .setContentText(context.getString(R.string.new_products_added))
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
                        }
                    )
                    is UiState.Error -> ErrorScreen(state.errorMessage) { productViewModel.onAction(ProductIntent.CancelScanning) }
                    else -> {}
                }
            }
            else -> AuthScreen(authViewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    products: List<Product>,
    currentFilter: ProductFilter,
    onFilterChange: (ProductFilter) -> Unit,
    onAddClick: () -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onUndoDelete: (Product) -> Unit,
    onClearAll: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                actions = {
                    if (products.isNotEmpty() || currentFilter != ProductFilter.ALL) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_all))
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { padding ->
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text(stringResource(R.string.clear_confirm_title)) },
                text = { Text(stringResource(R.string.clear_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        onClearAll()
                        showClearConfirm = false
                    }) { Text(stringResource(R.string.clear_confirm_button)) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        Column(modifier = Modifier.padding(padding)) {
            FilterChips(
                currentFilter = currentFilter,
                onFilterChange = onFilterChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentFilter == ProductFilter.ALL)
                            stringResource(R.string.empty_list)
                        else
                            stringResource(R.string.empty_list) // Could use a specific "no results for filter" string
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(products, key = { it.id }) { product ->
                        val dismissState = rememberSwipeToDismissBoxState()

                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                onDeleteProduct(product)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.deleted_message, product.name),
                                        actionLabel = context.getString(R.string.undo),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onUndoDelete(product)
                                    }
                                }
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Color.Red
                                    SwipeToDismissBoxValue.EndToStart -> Color.Red
                                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = Color.White
                                    )
                                }
                            }
                        ) {
                            ProductItem(product)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChips(
    currentFilter: ProductFilter,
    onFilterChange: (ProductFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProductFilter.entries.forEach { filter ->
            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        text = when (filter) {
                            ProductFilter.ALL -> stringResource(R.string.filter_all)
                            ProductFilter.EXPIRED -> stringResource(R.string.filter_expired)
                            ProductFilter.EXPIRED_AND_UPCOMING -> stringResource(R.string.filter_upcoming)
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun ProductItem(product: Product) {
    val bitmap = remember(product.productImage) {
        product.productImage?.let {
            try {
                val decodedString = android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            } catch (e: Exception) {
                Log.d("ProductsScreen", "decodeByteArray", e)
                null
            }
        }
    }

    val statusColor = remember(product.expirationDate) {
        try {
            val date = java.time.LocalDate.parse(product.expirationDate)
            val today = java.time.LocalDate.now()
            val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, date)
            when {
                daysUntil < 0 -> Color.Red
                daysUntil <= 7 -> Color(0xFFFFC107) // Amber/Yellow
                else -> Color.Green
            }
        } catch (e: Exception) {
            Color.Gray
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = stringResource(R.string.expires_on, product.expirationDate))
                if (!product.productionDate.isNullOrEmpty()) {
                    Text(text = stringResource(R.string.production_date, product.productionDate), fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanningScreen(
    state: UiState.Scanning,
    events: Flow<ProductEvent>,
    onCaptureClick: () -> Unit,
    onCaptureResult: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var isFlashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(Unit) {
        events.collectLatest { event ->
            if (event is ProductEvent.TriggerCapture) {
                takePhoto(context, imageCapture, state.step, onCaptureResult)
            }
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = CameraPreview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraX", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        ScanningOverlay(state.step)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (state.step == ScanStep.PRODUCT_PHOTO) 
                    stringResource(R.string.step_product_photo) 
                else 
                    stringResource(R.string.step_date_photo),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        IconButton(
            onClick = {
                isFlashOn = !isFlashOn
                camera?.cameraControl?.enableTorch(isFlashOn)
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = stringResource(R.string.flash),
                tint = Color.White
            )
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.background(Color.Red.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.cancel), tint = Color.White)
            }

            Button(
                onClick = onCaptureClick,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                // Inner circle
            }

            IconButton(
                onClick = onFinish,
                enabled = state.scannedItems.isNotEmpty(),
                modifier = Modifier.background(
                    if (state.scannedItems.isNotEmpty()) Color.Green.copy(alpha = 0.8f) else Color.Gray,
                    CircleShape
                )
            ) {
                BadgedBox(badge = {
                    if (state.scannedItems.isNotEmpty()) {
                        Badge { Text(state.scannedItems.size.toString()) }
                    }
                }) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.finish), tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun ScanningOverlay(step: ScanStep) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val maskRect = if (step == ScanStep.PRODUCT_PHOTO) {
            Rect(
                left = size.width * 0.1f,
                top = size.height * 0.2f,
                right = size.width * 0.9f,
                bottom = size.height * 0.6f
            )
        } else {
            Rect(
                left = size.width * 0.1f,
                top = size.height * 0.4f,
                right = size.width * 0.9f,
                bottom = size.height * 0.55f
            )
        }

        val maskPath = Path().apply {
            addRoundRect(RoundRect(maskRect, CornerRadius(16f, 16f)))
        }

        clipPath(maskPath, clipOp = ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun ProcessingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.processing), fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ErrorScreen(message: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = Color.Red, textAlign = TextAlign.Center)
            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.back))
            }
        }
    }
}

fun takePhoto(context: Context, imageCapture: ImageCapture, step: ScanStep, onPhotoTaken: (Bitmap) -> Unit) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = image.toBitmap()
                val matrix = Matrix()
                matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                
                // Clean up original bitmap if a new one was created for rotation
                if (bitmap != rotatedBitmap) {
                    bitmap.recycle()
                }

                val width = rotatedBitmap.width
                val height = rotatedBitmap.height
                
                val cropRect = if (step == ScanStep.PRODUCT_PHOTO) {
                    android.graphics.Rect(
                        (width * 0.1f).toInt(),
                        (height * 0.2f).toInt(),
                        (width * 0.9f).toInt(),
                        (height * 0.6f).toInt()
                    )
                } else {
                    android.graphics.Rect(
                        (width * 0.1f).toInt(),
                        (height * 0.4f).toInt(),
                        (width * 0.9f).toInt(),
                        (height * 0.55f).toInt()
                    )
                }

                val croppedBitmap = Bitmap.createBitmap(
                    rotatedBitmap,
                    cropRect.left,
                    cropRect.top,
                    cropRect.width(),
                    cropRect.height()
                )
                
                // Clean up rotated bitmap after cropping
                if (rotatedBitmap != croppedBitmap) {
                    rotatedBitmap.recycle()
                }
                
                onPhotoTaken(croppedBitmap)
                image.close()
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LanguagePicker()

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}

@Composable
fun LanguagePicker() {
    val languages = listOf(
        "en" to "English",
        "de" to "Deutsch",
        "ru" to "Русский"
    )

    val currentLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    Column {
        languages.forEach { (code, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentLocale.startsWith(code),
                    onClick = {
                        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(code)
                        AppCompatDelegate.setApplicationLocales(appLocale)
                    }
                )
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
