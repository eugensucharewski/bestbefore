package de.eugens.bestbefore.products.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.core.graphics.scale
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.repository.ProductRepository
import kotlinx.coroutines.CancellationException
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
    private val imagesDir = File(context.filesDir, IMAGE_CACHE_DIR).apply {
        mkdirs()
        // Migrate existing cache files from cacheDir if present
        val oldCacheDir = File(context.cacheDir, IMAGE_CACHE_DIR)
        if (oldCacheDir.exists() && oldCacheDir.isDirectory) {
            oldCacheDir.listFiles()?.forEach { oldFile ->
                val newFile = File(this, oldFile.name)
                if (!newFile.exists() && oldFile.length() > 0) {
                    try {
                        oldFile.copyTo(newFile, overwrite = true)
                    } catch (_: Exception) {}
                }
                oldFile.delete()
            }
            oldCacheDir.delete()
        }
    }

    private fun saveBase64ToCacheFile(
        productId: String,
        base64Str: String,
        forceOverwrite: Boolean = false
    ): File? {
        if (base64Str.isEmpty()) return null
        val cacheFile = File(imagesDir, productId)
        if (!forceOverwrite && cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }
        return try {
            val cleanBase64 = base64Str.substringAfter(",").trim().replace(" ", "+")
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            if (!isValidImageBytes(bytes)) {
                return null
            }
            cacheFile.writeBytes(bytes)
            cacheFile
        } catch (e: Exception) {
            Log.w("FirebaseProductRepository", "Failed to save image to cache: ${e.message}")
            null
        }
    }

    private fun isValidImageBytes(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            options.outWidth > 0 && options.outHeight > 0
        } catch (e: Throwable) {
            false
        }
    }

    override suspend fun getProducts(): List<Product> = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser ?: return@withContext emptyList()
        val snapshot = db.collection(Constants.COLLECTION_PRODUCTS)
            .whereEqualTo(Constants.FIELD_USER_ID, currentUser.uid)
            .get().await()
        snapshot.documents.map { doc ->
            val product = doc.toObject(Product::class.java)?.copy(id = doc.id) ?: Product()
            val extractedImage = extractImageFromDocument(doc)
            val isCached = File(imagesDir, doc.id).let { it.exists() && it.length() > 0 }
            val hasImage = product.hasImage || !extractedImage.isNullOrEmpty() || isCached
            if (!extractedImage.isNullOrEmpty()) {
                saveBase64ToCacheFile(doc.id, extractedImage)
            }
            product.copy(
                hasImage = hasImage,
                productImage = null
            )
        }
    }

    private fun extractImageFromDocument(doc: DocumentSnapshot): String? {
        if (!doc.exists()) return null
        val priorityFields = listOf(PRODUCT_IMAGE, "image", "product_image", "imageData", "data", "base64")
        for (field in priorityFields) {
            val value = doc.getString(field)
            if (!value.isNullOrEmpty() && isBase64ImageHeader(value)) {
                return value
            }
        }
        val data = doc.data ?: return null
        for ((key, value) in data) {
            if (key in priorityFields) continue
            if (value is String && isBase64ImageHeader(value)) {
                return value
            }
        }
        return null
    }

    private fun isBase64ImageHeader(value: String): Boolean {
        val clean = value.trim()
        val pureBase64 = clean.substringAfter(",")
        return pureBase64.startsWith("/9j/") || // JPEG
               pureBase64.startsWith("iVBORw") || // PNG
               pureBase64.startsWith("UklGR") || // WEBP
               pureBase64.startsWith("R0lGOD") || // GIF
               pureBase64.startsWith("Qk") // BMP
    }

    override suspend fun getProductImageFile(productId: String): File? = withContext(Dispatchers.IO) {
        val cacheFile = File(imagesDir, productId)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return@withContext cacheFile
        }

        val base64 = getProductImage(productId)
        if (!base64.isNullOrEmpty()) {
            return@withContext saveBase64ToCacheFile(productId, base64)
        }
        null
    }

    override suspend fun getProductImage(productId: String): String? = withContext(Dispatchers.IO) {
        val cacheFile = File(imagesDir, productId)
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return@withContext try {
                Base64.encodeToString(cacheFile.readBytes(), Base64.NO_WRAP)
            } catch (e: Exception) {
                null
            }
        }

        // 1. Try Firestore sub-collection "media"
        try {
            val mediaSnapshot = db.collection(Constants.COLLECTION_PRODUCTS)
                .document(productId)
                .collection(SUB_COLLECTION_MEDIA)
                .get().await()

            for (mediaDoc in mediaSnapshot.documents) {
                val base64 = extractImageFromDocument(mediaDoc)
                if (!base64.isNullOrEmpty()) {
                    saveBase64ToCacheFile(productId, base64)
                    return@withContext base64
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // ignore
        }

        // 2. Try main product document in Firestore
        try {
            val doc = db.collection(Constants.COLLECTION_PRODUCTS)
                .document(productId)
                .get().await()

            val base64 = extractImageFromDocument(doc)
            if (!base64.isNullOrEmpty()) {
                saveBase64ToCacheFile(productId, base64)
                return@withContext base64
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // ignore
        }

        null
    }

    override suspend fun deleteProduct(productId: String) {
        withContext(Dispatchers.IO) {
            // Delete local cache
            File(imagesDir, productId).delete()

            // Delete sub-collection media
            try {
                val mediaDocs = db.collection(Constants.COLLECTION_PRODUCTS)
                    .document(productId)
                    .collection(SUB_COLLECTION_MEDIA)
                    .get().await()
                for (doc in mediaDocs.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }

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

            val image = product.productImage
            if (!image.isNullOrEmpty()) {
                val imageMap = hashMapOf(PRODUCT_IMAGE to image)
                docRef.collection(SUB_COLLECTION_MEDIA).document(DOC_IMAGE).set(imageMap).await()
                // Cache it
                saveBase64ToCacheFile(docRef.id, image, forceOverwrite = true)
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

            val image = product.productImage
            if (!image.isNullOrEmpty()) {
                val imageMap = hashMapOf(PRODUCT_IMAGE to image)
                db.collection(Constants.COLLECTION_PRODUCTS)
                    .document(product.id)
                    .collection(SUB_COLLECTION_MEDIA)
                    .document(DOC_IMAGE)
                    .set(imageMap).await()
                // Cache it (overwrite if image was updated)
                saveBase64ToCacheFile(product.id, image, forceOverwrite = true)
            }
        }
    }

    override suspend fun saveAnalysisResults(results: List<ExpirationInfo>, items: List<ScannedItem>) {
        withContext(Dispatchers.IO) {
            val currentUser = auth.currentUser
            results.forEachIndexed { index, info ->
                val imagePath = items.getOrNull(index)?.productImagePath
                val encodedImage = imagePath?.let { path ->
                    encodeImageFileToBase64(path)
                }
                val hasImage = !encodedImage.isNullOrEmpty()

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

                if (encodedImage != null) {
                    val imageMap = hashMapOf(PRODUCT_IMAGE to encodedImage)
                    docRef.collection(SUB_COLLECTION_MEDIA).document(DOC_IMAGE).set(imageMap).await()
                    // Cache it
                    saveBase64ToCacheFile(docRef.id, encodedImage, forceOverwrite = true)
                }
            }
        }
    }

    private fun encodeImageFileToBase64(path: String): String? {
        val file = File(path)
        if (!file.exists()) return null

        val reqWidth = Constants.BITMAP_MAX_WIDTH
        val reqHeight = Constants.BITMAP_MAX_HEIGHT

        // 1. Read bounds first to calculate inSampleSize without loading the full image into RAM
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        // 2. Decode subsampled bitmap
        val subsampledBitmap = BitmapFactory.decodeFile(path, options) ?: return null

        // 3. Scale down to exact bounds if necessary
        val ratio = (reqWidth.toFloat() / subsampledBitmap.width)
            .coerceAtMost(reqHeight.toFloat() / subsampledBitmap.height)
        val targetWidth = (ratio * subsampledBitmap.width).toInt().coerceAtLeast(1)
        val targetHeight = (ratio * subsampledBitmap.height).toInt().coerceAtLeast(1)

        val finalBitmap = if (targetWidth < subsampledBitmap.width || targetHeight < subsampledBitmap.height) {
            subsampledBitmap.scale(targetWidth, targetHeight)
        } else {
            subsampledBitmap
        }

        return try {
            val outputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, Constants.BITMAP_QUALITY, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } finally {
            if (finalBitmap != subsampledBitmap && !subsampledBitmap.isRecycled) {
                subsampledBitmap.recycle()
            }
            if (!finalBitmap.isRecycled) {
                finalBitmap.recycle()
            }
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}
