package de.eugens.bestbefore.products.data.analyzer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import de.eugens.bestbefore.products.domain.analyzer.ImageTextRecognizer
import de.eugens.bestbefore.products.domain.analyzer.TextRecognitionResult
import de.eugens.bestbefore.products.domain.model.ScannedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GoogleMlKitTextRecognizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val productNameExtractor: ProductNameExtractor
) : ImageTextRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun analyzeImages(items: List<ScannedItem>): List<TextRecognitionResult> {
        val results: MutableList<TextRecognitionResult> = mutableListOf()
        withContext(Dispatchers.IO) {
            items.forEach { item ->
                try {
                    var productNameText: String? = null
                    var productExpirationDate: String? = null

                    item.productImagePath?.let { path ->
                        val productImageFile = File(path)
                        if (productImageFile.exists()) {
                            val image = InputImage.fromFilePath(
                                context,
                                Uri.fromFile(productImageFile)
                            )
                            val visionText = recognizeText(image)
                            productNameText = productNameExtractor.extractProductName(
                                visionText,
                                image.width,
                                image.height
                            )
                        }
                    }
                    item.dateImagePath?.let { path ->
                        val dateImageFile = File(path)
                        if (dateImageFile.exists()) {
                            val image = InputImage.fromFilePath(
                                context,
                                Uri.fromFile(dateImageFile)
                            )
                            val visionText = recognizeText(image)
                            productExpirationDate = visionText.text
                        }
                    }

                    results.add(
                        TextRecognitionResult(productNameText ?: "", productExpirationDate ?: "", item.dateImagePath)
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("GoogleMlKitTextRecognizer", "Error analyzing item ${item.productImagePath}: $e", e)
                    results.add(TextRecognitionResult("", "", item.dateImagePath))
                }
            }
        }

        return results
    }

    private suspend fun recognizeText(image: InputImage): Text =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e) }
                }
}