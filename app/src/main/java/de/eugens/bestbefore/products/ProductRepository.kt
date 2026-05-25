package de.eugens.bestbefore.products

import android.graphics.Bitmap
import android.util.Base64
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import de.eugens.bestbefore.BuildConfig
import de.eugens.bestbefore.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

class ProductRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val json = Json { ignoreUnknownKeys = true }

    private val generativeModel = GenerativeModel(
        modelName = Constants.GEMINI_MODEL_NAME,
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content {
            text(
                "Ты — специализированный ассистент по распознаванию сроков годности. " +
                        "Для каждого изображения (фото даты) определи срок годности. " +
                        "Верни массив JSON объектов с полями: date_found (boolean), expiration_date (DD.MM.YYYY), production_date (YYYY-MM-DD), confidence (high/medium/low), raw_text_detected (string)."
            )
        },
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    suspend fun getProducts(): List<Product> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext emptyList()
        val snapshot = db.collection(Constants.COLLECTION_PRODUCTS)
            .whereEqualTo(Constants.FIELD_USER_ID, currentUser.uid)
            .get().await()
        snapshot.documents.map { doc ->
            doc.toObject(Product::class.java)?.copy(id = doc.id) ?: Product()
        }
    }

    suspend fun deleteProduct(productId: String) = withContext(Dispatchers.IO) {
        db.collection(Constants.COLLECTION_PRODUCTS).document(productId).delete().await()
    }

    suspend fun clearAllProducts() = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext
        val snapshot = db.collection(Constants.COLLECTION_PRODUCTS)
            .whereEqualTo(Constants.FIELD_USER_ID, currentUser.uid)
            .get().await()
        snapshot.documents.forEach { doc ->
            doc.reference.delete()
        }
    }

    suspend fun addProduct(product: Product) = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        val productMap = hashMapOf(
            "name" to product.name,
            "expirationDate" to product.expirationDate,
            "productionDate" to product.productionDate,
            "confidence" to product.confidence,
            "rawText" to product.rawText,
            "productImage" to product.productImage,
            Constants.FIELD_USER_ID to currentUser?.uid
        )
        db.collection(Constants.COLLECTION_PRODUCTS).add(productMap).await()
    }

    suspend fun updateProduct(product: Product) = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        val productMap = hashMapOf(
            "name" to product.name,
            "expirationDate" to product.expirationDate,
            "productionDate" to product.productionDate,
            "confidence" to product.confidence,
            "rawText" to product.rawText,
            "productImage" to product.productImage,
            Constants.FIELD_USER_ID to currentUser?.uid
        )
        db.collection(Constants.COLLECTION_PRODUCTS).document(product.id).set(productMap).await()
    }

    suspend fun analyzeImages(items: List<ScannedItem>): List<ExpirationInfo> =
        withContext(Dispatchers.IO) {
            val prompt =
                "Проанализируй эти изображения. Каждое изображение - это фото срока годности продукта. " +
                        "Верни результат в виде JSON массива объектов ExpirationInfo."

            val response = generativeModel.generateContent(
                content {
                    items.forEach { item ->
                        item.dateBitmap?.let { image(it) }
                    }
                    text(prompt)
                }
            )

            val outputContent = response.text ?: throw Exception("Empty response from AI")
            json.decodeFromString<List<ExpirationInfo>>(outputContent)
        }

    suspend fun saveAnalysisResults(results: List<ExpirationInfo>, items: List<ScannedItem>) =
        withContext(Dispatchers.IO) {
            val currentUser = auth.currentUser
            results.forEachIndexed { index, info ->
                val productBitmap = items.getOrNull(index)?.productBitmap
                val encodedImage = productBitmap?.let { resizeAndEncodeBitmap(it) }

                val productMap = hashMapOf(
                    "name" to "Product ${index + 1}",
                    "expirationDate" to (info.expiration_date ?: ""),
                    "productionDate" to info.production_date,
                    "confidence" to info.confidence,
                    "rawText" to info.raw_text_detected,
                    "productImage" to encodedImage,
                    Constants.FIELD_USER_ID to currentUser?.uid
                )
                db.collection(Constants.COLLECTION_PRODUCTS).add(productMap).await()
            }
        }

    private fun resizeAndEncodeBitmap(bitmap: Bitmap): String {
        val ratio =
            (Constants.BITMAP_MAX_WIDTH.toFloat() / bitmap.width).coerceAtMost(Constants.BITMAP_MAX_HEIGHT.toFloat() / bitmap.height)
        val width = (ratio * bitmap.width).toInt()
        val height = (ratio * bitmap.height).toInt()

        val resized = bitmap.scale(width, height)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, Constants.BITMAP_QUALITY, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }
}