package de.eugens.bestbefore.products.data.analyzer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
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
    @param:ApplicationContext private val context: Context
) : ImageTextRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun analyzeImages(items: List<ScannedItem>): List<TextRecognitionResult> {
        val results: MutableList<TextRecognitionResult> = mutableListOf()
        withContext(Dispatchers.IO) {
            try {
                items.forEach { item ->
                    var productNameText: String? = null
                    var productExpirationDate: String? = null

                    item.productImagePath?.let { path ->
                        val productImageFile = File(path)
                        if (productImageFile.exists()) {
                            val image = InputImage.fromFilePath(
                                context,
                                Uri.fromFile(productImageFile)
                            )
                            productNameText = recognizeText(image)
                        }
                    }
                    item.dateImagePath?.let { path ->
                        val dateImageFile = File(path)
                        if (dateImageFile.exists()) {
                            val image = InputImage.fromFilePath(
                                context,
                                Uri.fromFile(dateImageFile)
                            )

                            productExpirationDate = recognizeText(image)
                        }
                    }

                    results.add(
                        TextRecognitionResult(productNameText ?: "", productExpirationDate ?: "")
                    )
                }
            } catch (e: Exception) {
                Log.d("GoogleMlKitTextRecognizer", "Error analyzing images: $e")
                if (e is CancellationException) throw e
            }
        }

        return results
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e) }
                }
}