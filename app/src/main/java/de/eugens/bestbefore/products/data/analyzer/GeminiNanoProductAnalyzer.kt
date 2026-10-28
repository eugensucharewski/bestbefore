package de.eugens.bestbefore.products.data.analyzer

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Content
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.use_case.ParseExpirationDateUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiNanoProductAnalyzer @Inject constructor(
    private val parseExpirationDateUseCase: ParseExpirationDateUseCase
) : AIProductAnalyzer {

    companion object {
        private const val TAG = "GeminiNanoProductAnalyzer"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val generativeModel by lazy { Generation.getClient() }

    override suspend fun analyzeImages(items: List<ScannedItem>): List<ExpirationInfo> =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) {
                return@withContext emptyList()
            }
            try {
                if (!ensureModelReady(generativeModel)) {
                    throw IllegalStateException("Gemini Nano model is not ready")
                }

                val results = mutableListOf<ExpirationInfo>()

                for ((index, item) in items.withIndex()) {
                    val loadedBitmaps = mutableListOf<Bitmap>()
                    
                    try {
                        val contentBuilder = Content.builder()
                        
                        contentBuilder.text(LlmPrompts.NANO_SYSTEM_INSTRUCTION.trimIndent() + "\n\n" + LlmPrompts.NANO_ANALYZE_PROMPT + "\n\nInput Data:\n")

                        item.productImagePath?.let { path ->
                            BitmapUtils.decodeSampledBitmapFromFile(path)?.let { bitmap ->
                                loadedBitmaps.add(bitmap)
                                contentBuilder.text("Product front image (for name):\n")
                                contentBuilder.image(bitmap)
                            }
                        }

                        item.dateImagePath?.let { path ->
                            BitmapUtils.decodeSampledBitmapFromFile(path)?.let { bitmap ->
                                loadedBitmaps.add(bitmap)
                                contentBuilder.text("Product back image (for expiration date):\n")
                                contentBuilder.image(bitmap)
                            }
                        }

                        val content = contentBuilder.build()

                        val request = GenerateContentRequest.builder(content).build()
                        val response = generativeModel.generateContent(request)

                        val outputText = response.candidates.firstOrNull()?.text
                            ?: throw Exception("Empty response from Gemini Nano for item ${index + 1}")

                        val cleanJson = extractJson(outputText)
                        val decoded = if (cleanJson.trim().startsWith("[")) {
                            json.decodeFromString<List<ExpirationInfo>>(cleanJson).firstOrNull()
                        } else {
                            json.decodeFromString<ExpirationInfo>(cleanJson)
                        }
                        
                        if (decoded != null) {
                            val normalized = decoded.copy(
                                item_index = index + 1,
                                expiration_date = parseExpirationDateUseCase.formatToStandardDate(decoded.expiration_date) ?: decoded.expiration_date,
                                production_date = parseExpirationDateUseCase.formatToStandardDate(decoded.production_date) ?: decoded.production_date
                            )
                            results.add(normalized)
                        } else {
                            throw Exception("Failed to decode response for item ${index + 1}")
                        }
                    } finally {
                        loadedBitmaps.forEach { bitmap ->
                            if (!bitmap.isRecycled) {
                                bitmap.recycle()
                            }
                        }
                    }
                }
                
                return@withContext results
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Nano product analysis failed: ${e.message}", e)
                throw e
            }
        }

    internal fun extractJson(text: String): String = LlmPrompts.extractJson(text)

    suspend fun ensureModelReady(generativeModel: GenerativeModel): Boolean {
        return when (val status = generativeModel.checkStatus()) {
            FeatureStatus.AVAILABLE -> true

            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                val finalStatus = generativeModel.download().first { downloadStatus ->
                    downloadStatus is DownloadStatus.DownloadCompleted ||
                            downloadStatus is DownloadStatus.DownloadFailed
                }
                if (finalStatus is DownloadStatus.DownloadFailed) {
                    throw finalStatus.e
                }
                true
            }

            FeatureStatus.UNAVAILABLE -> {
                throw IllegalStateException("Gemini Nano is truly unavailable (status code: $status)")
            }

            else -> false
        }
    }
}
