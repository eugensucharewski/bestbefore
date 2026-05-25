package de.eugens.bestbefore

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

sealed class ProductIntent {
    data object LoadProducts : ProductIntent()
    data object StartScanning : ProductIntent()
    data object CancelScanning : ProductIntent()
    data object OpenSettings : ProductIntent()
    data object BackToMain : ProductIntent()
    data class CapturePhoto(val bitmap: Bitmap) : ProductIntent()
    data object FinishScanning : ProductIntent()
    data class DeleteProduct(val productId: String) : ProductIntent()
    data object ClearAllProducts : ProductIntent()
    data class AddProduct(val product: Product) : ProductIntent()
}

class ProductViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ProductViewModel"
    }
    private val auth = Firebase.auth
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.MainList)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val db = Firebase.firestore
    private val json = Json { ignoreUnknownKeys = true }

    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val generativeModel = GenerativeModel(
        modelName = "gemini-flash-latest",
        apiKey = apiKey,
        systemInstruction = content {
            text("Ты — специализированный ассистент по распознаванию сроков годности. " +
                    "Для каждого изображения (фото даты) определи срок годности. " +
                    "Верни массив JSON объектов с полями: date_found (boolean), expiration_date (YYYY-MM-DD), production_date (YYYY-MM-DD), confidence (high/medium/low), raw_text_detected (string).")
        },
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    init {
        loadProducts()
    }

    fun onAction(intent: ProductIntent) {
        when (intent) {
            is ProductIntent.LoadProducts -> loadProducts()
            is ProductIntent.StartScanning -> startScanning()
            is ProductIntent.CancelScanning -> cancelScanning()
            is ProductIntent.OpenSettings -> openSettings()
            is ProductIntent.BackToMain -> backToMain()
            is ProductIntent.CapturePhoto -> capturePhoto(intent.bitmap)
            is ProductIntent.FinishScanning -> finishScanning()
            is ProductIntent.DeleteProduct -> deleteProduct(intent.productId)
            is ProductIntent.ClearAllProducts -> clearAllProducts()
            is ProductIntent.AddProduct -> addProduct(intent.product)
        }
    }

    private fun loadProducts() {
        viewModelScope.launch {
            refreshProducts()
        }
    }

    private suspend fun refreshProducts() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _products.value = emptyList()
            return
        }
        try {
            val snapshot = db.collection("products")
                .whereEqualTo("userId", currentUser.uid)
                .get().await()
            val productList = snapshot.documents.map { doc ->
                doc.toObject(Product::class.java)?.copy(id = doc.id) ?: Product()
            }
            _products.value = productList
        } catch (e: Exception) {
             if (e is CancellationException) {
                 throw e
             }
            // Handle error
            Log.e(TAG, "refreshProducts", e)
        }
    }

    private fun startScanning() {
        _uiState.value = UiState.Scanning(step = ScanStep.PRODUCT_PHOTO)
    }

    private fun cancelScanning() {
        _uiState.value = UiState.MainList
    }

    private fun openSettings() {
        _uiState.value = UiState.Settings
    }

    private fun backToMain() {
        _uiState.value = UiState.MainList
    }

    private fun capturePhoto(bitmap: Bitmap) {
        val currentState = _uiState.value
        if (currentState is UiState.Scanning) {
            if (currentState.step == ScanStep.PRODUCT_PHOTO) {
                _uiState.value = currentState.copy(
                    step = ScanStep.DATE_PHOTO,
                    currentItem = currentState.currentItem.copy(productBitmap = bitmap)
                )
            } else {
                val updatedItem = currentState.currentItem.copy(dateBitmap = bitmap)
                val newList = currentState.scannedItems + updatedItem
                _uiState.value = currentState.copy(
                    step = ScanStep.PRODUCT_PHOTO,
                    currentItem = ScannedItem(),
                    scannedItems = newList
                )
            }
        }
    }

    private fun finishScanning() {
        val currentState = _uiState.value
        if (currentState is UiState.Scanning) {
            val itemsToProcess = currentState.scannedItems
            if (itemsToProcess.isEmpty()) {
                _uiState.value = UiState.MainList
                return
            }
            processItems(itemsToProcess)
        }
    }

    private fun processItems(items: List<ScannedItem>) {
        _uiState.value = UiState.Processing

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val prompt = "Проанализируй эти изображения. Каждое изображение - это фото срока годности продукта. " +
                        "Верни результат в виде JSON массива объектов ExpirationInfo."

                val response = generativeModel.generateContent(
                    content {
                        items.forEach { item ->
                            item.dateBitmap?.let { image(it) }
                        }
                        text(prompt)
                    }
                )

                response.text?.let { outputContent ->
                    try {
                        val results = json.decodeFromString<List<ExpirationInfo>>(outputContent)
                        saveResultsToFirestore(results, items)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _uiState.postValue(UiState.Error(getApplication<Application>().getString(R.string.parsing_error, e.localizedMessage)))
                    }
                } ?: run {
                    println("пустой ответ")
                    _uiState.postValue(UiState.Error(getApplication<Application>().getString(R.string.empty_response)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (e is CancellationException) throw e
                _uiState.postValue(UiState.Error(e.localizedMessage ?: getApplication<Application>().getString(R.string.error)))
            }
        }
    }

    private suspend fun saveResultsToFirestore(results: List<ExpirationInfo>, items: List<ScannedItem>) {
        val currentUser = auth.currentUser
        val context = getApplication<Application>()
        results.forEachIndexed { index, info ->
            val productBitmap = items.getOrNull(index)?.productBitmap
            val encodedImage = productBitmap?.let { resizeAndEncodeBitmap(it) }

            val product = hashMapOf(
                "name" to context.getString(R.string.product_prefix, index + 1),
                "expirationDate" to (info.expiration_date ?: ""),
                "productionDate" to info.production_date,
                "confidence" to info.confidence,
                "rawText" to info.raw_text_detected,
                "productImage" to encodedImage,
                "userId" to currentUser?.uid
            )
            db.collection("products").add(product).await()
        }
        
        refreshProducts()
        _uiState.postValue(UiState.MainList)
        sendCompletionNotification()
    }

    private fun resizeAndEncodeBitmap(bitmap: Bitmap): String {
        val maxWidth = 400
        val maxHeight = 400
        val ratio = Math.min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        val width = (ratio * bitmap.width).toInt()
        val height = (ratio * bitmap.height).toInt()
        
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    private fun deleteProduct(productId: String) {
        viewModelScope.launch {
            try {
                db.collection("products").document(productId).delete().await()
                refreshProducts()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun clearAllProducts() {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection("products")
                    .whereEqualTo("userId", currentUser.uid)
                    .get().await()
                snapshot.documents.forEach { doc ->
                    doc.reference.delete()
                }
                refreshProducts()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun addProduct(product: Product) {
        val currentUser = auth.currentUser
        viewModelScope.launch {
            try {
                val productMap = hashMapOf(
                    "name" to product.name,
                    "expirationDate" to product.expirationDate,
                    "productionDate" to product.productionDate,
                    "confidence" to product.confidence,
                    "rawText" to product.rawText,
                    "productImage" to product.productImage,
                    "userId" to currentUser?.uid
                )
                db.collection("products").add(productMap).await()
                refreshProducts()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun sendCompletionNotification() {
        val context = getApplication<Application>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channel = NotificationChannel("processing_results", "Processing Results", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, "processing_results")
            .setContentTitle(context.getString(R.string.processing_completed))
            .setContentText(context.getString(R.string.new_products_added))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(1, notification)
    }

    private fun MutableStateFlow<UiState>.postValue(value: UiState) {
        this.value = value
    }
}
