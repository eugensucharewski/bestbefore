package de.eugens.bestbefore.products.data

import android.graphics.Bitmap
import android.util.Base64
import androidx.core.graphics.scale
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseProductRepository @Inject constructor() : ProductRepository {

    companion object {
        private const val NAME = "name"
        private const val EXPIRATION_DATE = "expirationDate"
        private const val PRODUCTION_DATE = "productionDate"
        private const val CONFIDENCE = "confidence"
        private const val RAW_TEXT = "rawText"
        private const val PRODUCT_IMAGE = "productImage"
    }

    private val db = Firebase.firestore
    private val auth = Firebase.auth

    override suspend fun getProducts(): List<Product> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext emptyList()
        val snapshot = db.collection(Constants.COLLECTION_PRODUCTS)
            .whereEqualTo(Constants.FIELD_USER_ID, currentUser.uid)
            .get().await()
        snapshot.documents.map { doc ->
            doc.toObject(Product::class.java)?.copy(id = doc.id) ?: Product()
        }
    }

    override suspend fun deleteProduct(productId: String) {
        withContext(Dispatchers.IO) {
            db.collection(Constants.COLLECTION_PRODUCTS).document(productId).delete().await()
        }
    }

    override suspend fun clearAllProducts() = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext
        val snapshot = db.collection(Constants.COLLECTION_PRODUCTS)
            .whereEqualTo(Constants.FIELD_USER_ID, currentUser.uid)
            .get().await()
        snapshot.documents.forEach { doc ->
            doc.reference.delete()
        }
    }

    override suspend fun addProduct(product: Product) {
        withContext(Dispatchers.IO) {
            val currentUser = auth.currentUser
            val productMap = hashMapOf(
                NAME to product.name,
                EXPIRATION_DATE to product.expirationDate,
                PRODUCTION_DATE to product.productionDate,
                CONFIDENCE to product.confidence,
                RAW_TEXT to product.rawText,
                PRODUCT_IMAGE to product.productImage,
                Constants.FIELD_USER_ID to currentUser?.uid
            )
            db.collection(Constants.COLLECTION_PRODUCTS).add(productMap).await()
        }
    }

    override suspend fun updateProduct(product: Product) {
        withContext(Dispatchers.IO) {
            val currentUser = auth.currentUser
            val productMap = hashMapOf(
                NAME to product.name,
                EXPIRATION_DATE to product.expirationDate,
                PRODUCTION_DATE to product.productionDate,
                CONFIDENCE to product.confidence,
                RAW_TEXT to product.rawText,
                PRODUCT_IMAGE to product.productImage,
                Constants.FIELD_USER_ID to currentUser?.uid
            )
            db.collection(Constants.COLLECTION_PRODUCTS)
                .document(product.id).set(productMap).await()
        }
    }

    override suspend fun saveAnalysisResults(results: List<ExpirationInfo>, items: List<ScannedItem>) {
        withContext(Dispatchers.IO) {
            val currentUser = auth.currentUser
            results.forEachIndexed { index, info ->
                val productBitmap = items.getOrNull(index)?.productBitmap
                val encodedImage = productBitmap?.let { resizeAndEncodeBitmap(it) }

                val productMap = hashMapOf(
                    NAME to info.productName,
                    EXPIRATION_DATE to (info.expiration_date ?: ""),
                    PRODUCTION_DATE to info.production_date,
                    CONFIDENCE to info.confidence,
                    RAW_TEXT to info.raw_text_detected,
                    PRODUCT_IMAGE to encodedImage,
                    Constants.FIELD_USER_ID to currentUser?.uid
                )
                db.collection(Constants.COLLECTION_PRODUCTS).add(productMap).await()
            }
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
