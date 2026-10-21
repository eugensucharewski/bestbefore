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
            val loadedBitmaps = mutableListOf<Bitmap>()
            try {
                if (!ensureModelReady(generativeModel)) {
                    throw IllegalStateException("Gemini Nano model is not ready")
                }

                val promptBuilder = StringBuilder()
                promptBuilder.append(LlmPrompts.buildSystemInstruction())
                promptBuilder.append("\n\n")
                promptBuilder.append(LlmPrompts.ANALYZE_PROMPT)
                promptBuilder.append("\n\nInput Data:\n")

                val contentBuilder = Content.builder()

                items.forEachIndexed { index, item ->
                    promptBuilder.append("Item ${index + 1}:\n")

                    item.productImagePath?.let { path ->
                        BitmapUtils.decodeSampledBitmapFromFile(path)?.let { bitmap ->
                            loadedBitmaps.add(bitmap)
                            promptBuilder.append("Product image for Item ${index + 1} attached.\n")
                            contentBuilder.image(bitmap)
                        }
                    }

                    item.dateImagePath?.let { path ->
                        BitmapUtils.decodeSampledBitmapFromFile(path)?.let { bitmap ->
                            loadedBitmaps.add(bitmap)
                            promptBuilder.append("Expiration date image for Item ${index + 1} attached.\n")
                            contentBuilder.image(bitmap)
                        }
                    }
                    promptBuilder.append("\n")
                }

                contentBuilder.text(promptBuilder.toString())
                val content = contentBuilder.build()

                val request = GenerateContentRequest.builder(content).build()
                val response = generativeModel.generateContent(request)

                val outputText = response.candidates.firstOrNull()?.text
                    ?: throw Exception("Empty response from Gemini Nano")

                val cleanJson = extractJson(outputText)
                try {
                    val decoded = json.decodeFromString<List<ExpirationInfo>>(cleanJson)
                    return@withContext decoded.map { info ->
                        info.copy(
                            expiration_date = parseExpirationDateUseCase.formatToStandardDate(info.expiration_date) ?: info.expiration_date,
                            production_date = parseExpirationDateUseCase.formatToStandardDate(info.production_date) ?: info.production_date
                        )
                    }
                } catch (se: Exception) {
                    Log.e(TAG, "Failed to parse Gemini Nano response. Raw text: $outputText", se)
                    throw Exception("Gemini Nano invalid response format: ${se.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Nano product analysis failed: ${e.message}", e)
                throw e
            } finally {
                loadedBitmaps.forEach { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
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
