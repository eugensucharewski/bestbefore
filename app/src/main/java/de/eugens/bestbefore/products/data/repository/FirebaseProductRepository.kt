package de.eugens.bestbefore.products.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.graphics.scale
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseProductRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ProductRepository {

    companion object {
        private const val NAME = "name"
        private const val EXPIRATION_DATE = "expirationDate"
        private const val PRODUCTION_DATE = "productionDate"
        private const val CONFIDENCE = "confidence"
        private const val RAW_TEXT = "rawText"
        private const val PRODUCT_IMAGE = "productImage"
        private const val HAS_IMAGE = "hasImage"
        private const val SUB_COLLECTION_MEDIA = "media"
        private const val DOC_IMAGE = "image_data"
        private const val IMAGE_CACHE_DIR = "product_images"
    }

    private val db = Firebase.firestore
    private val auth = Firebase.auth
    private val cacheDir = File(context.cacheDir, IMAGE_CACHE_DIR).apply { mkdirs() }

    override suspend fun getProducts(): List<Product> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext emptyList()
        val snapshot = db.collection(Constants.COLLECTION_PRODUCTS)
            .whereEqualTo(Constants.FIELD_USER_ID, currentUser.uid)
            .get().await()
        snapshot.documents.map { doc ->
            doc.toObject(Product::class.java)?.copy(id = doc.id) ?: Product()
        }
    }

    override suspend fun getProductImage(productId: String): String? = withContext(Dispatchers.IO) {
        // 1. Try local cache
        val cacheFile = File(cacheDir, productId)
        if (cacheFile.exists()) {
            return@withContext cacheFile.readText()
        }

        // 2. Try Firestore sub-collection
        try {
            val doc = db.collection(Constants.COLLECTION_PRODUCTS)
                .document(productId)
                .collection(SUB_COLLECTION_MEDIA)
                .document(DOC_IMAGE)
                .get().await()
            
            val base64 = doc.getString(PRODUCT_IMAGE)
            if (base64 != null) {
                // Save to cache
                cacheFile.writeText(base64)
            }
            base64
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun deleteProduct(productId: String) {
        withContext(Dispatchers.IO) {
            // Delete local cache
            File(cacheDir, productId).delete()
            
            // Delete sub-collection (manually, as Firestore doesn't delete sub-collections automatically)
            db.collection(Constants.COLLECTION_PRODUCTS)
                .document(productId)
                .collection(SUB_COLLECTION_MEDIA)
                .document(DOC_IMAGE)
                .delete().await()

            db.collection(Constants.COLLECTION_PRODUCTS).document(productId).delete().await()
        }
    }

    override suspend fun clearAllProducts() = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext
        val snapshot = db.collection(Constants.COLLECTION_PRODUCTS)
            .whereEqualTo(Constants.FIELD_USER_ID, currentUser.uid)
            .get().await()
        
        snapshot.documents.forEach { doc ->
            deleteProduct(doc.id)
        }
    }

    override suspend fun addProduct(product: Product) {
        withContext(Dispatchers.IO) {
            val currentUser = auth.currentUser
            val hasImage = !product.productImage.isNullOrEmpty()
            
            val productMap = hashMapOf(
                NAME to product.name,
                EXPIRATION_DATE to product.expirationDate,
                PRODUCTION_DATE to product.productionDate,
                CONFIDENCE to product.confidence,
                RAW_TEXT to product.rawText,
                HAS_IMAGE to hasImage,
                Constants.FIELD_USER_ID to currentUser?.uid
            )
            
            val docRef = db.collection(Constants.COLLECTION_PRODUCTS).add(productMap).await()
            
            if (hasImage) {
                val image = product.productImage ?: return@withContext
                val imageMap = hashMapOf(PRODUCT_IMAGE to image)
                docRef.collection(SUB_COLLECTION_MEDIA).document(DOC_IMAGE).set(imageMap).await()
                // Cache it
                File(cacheDir, docRef.id).writeText(image)
            }
        }
    }

    override suspend fun updateProduct(product: Product) {
        withContext(Dispatchers.IO) {
            val currentUser = auth.currentUser
            val hasImage = !product.productImage.isNullOrEmpty()

            val productMap = hashMapOf(
                NAME to product.name,
                EXPIRATION_DATE to product.expirationDate,
                PRODUCTION_DATE to product.productionDate,
                CONFIDENCE to product.confidence,
                RAW_TEXT to product.rawText,
                HAS_IMAGE to hasImage,
                Constants.FIELD_USER_ID to currentUser?.uid
            )
            db.collection(Constants.COLLECTION_PRODUCTS)
                .document(product.id).set(productMap).await()

            if (hasImage) {
                val image = product.productImage ?: return@withContext
                val imageMap = hashMapOf(PRODUCT_IMAGE to image)
                db.collection(Constants.COLLECTION_PRODUCTS)
                    .document(product.id)
                    .collection(SUB_COLLECTION_MEDIA)
                    .document(DOC_IMAGE)
                    .set(imageMap).await()
                // Cache it
                File(cacheDir, product.id).writeText(image)
            }
        }
    }

    override suspend fun saveAnalysisResults(results: List<ExpirationInfo>, items: List<ScannedItem>) {
        withContext(Dispatchers.IO) {
            val currentUser = auth.currentUser
            results.forEachIndexed { index, info ->
                val productByteArray = items.getOrNull(index)?.productBitmap
                val encodedImage = productByteArray?.let { 
                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    resizeAndEncodeBitmap(bitmap) 
                }
                val hasImage = encodedImage != null

                val productMap = hashMapOf(
                    NAME to info.productName,
                    EXPIRATION_DATE to (info.expiration_date ?: ""),
                    PRODUCTION_DATE to info.production_date,
                    CONFIDENCE to info.confidence,
                    RAW_TEXT to info.raw_text_detected,
                    HAS_IMAGE to hasImage,
                    Constants.FIELD_USER_ID to currentUser?.uid
                )
                val docRef = db.collection(Constants.COLLECTION_PRODUCTS).add(productMap).await()

                if (hasImage && encodedImage != null) {
                    val imageMap = hashMapOf(PRODUCT_IMAGE to encodedImage)
                    docRef.collection(SUB_COLLECTION_MEDIA).document(DOC_IMAGE).set(imageMap).await()
                    // Cache it
                    File(cacheDir, docRef.id).writeText(encodedImage)
                }
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
