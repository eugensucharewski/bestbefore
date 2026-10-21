package de.eugens.bestbefore.products.data.analyzer

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import de.eugens.bestbefore.BuildConfig
import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import de.eugens.bestbefore.products.domain.use_case.ParseExpirationDateUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class GeminiProductAnalyzer @Inject constructor(
    private val parseExpirationDateUseCase: ParseExpirationDateUseCase
) : AIProductAnalyzer {

    constructor() : this(ParseExpirationDateUseCase())

    companion object {
        private const val TAG = "GeminiProductAnalyzer"
        private const val PRIMARY_MODEL_NAME = "gemini-3.8-flash"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val primaryModel = GenerativeModel(
        modelName = PRIMARY_MODEL_NAME,
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content {
            text(LlmPrompts.buildSystemInstruction())
        },
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    override suspend fun analyzeImages(items: List<ScannedItem>): List<ExpirationInfo> =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) {
                return@withContext emptyList()
            }

            val loadedBitmaps = mutableListOf<Bitmap>()
            try {
                val inputContent = content {
                    text(LlmPrompts.ANALYZE_PROMPT)
                    text("\n\nInput Data:\n")

                    items.forEachIndexed { index, item ->
                        text("Item ${index + 1}:\n")

                        item.productImagePath?.let { path ->
                            BitmapUtils.decodeSampledBitmapFromFile(path)?.let { bitmap ->
                                loadedBitmaps.add(bitmap)
                                text("Product image for Item ${index + 1}:")
                                image(bitmap)
                            }
                        }

                        item.dateImagePath?.let { path ->
                            BitmapUtils.decodeSampledBitmapFromFile(path)?.let { bitmap ->
                                loadedBitmaps.add(bitmap)
                                text("Expiration date image for Item ${index + 1}:")
                                image(bitmap)
                            }
                        }
                        text("\n")
                    }
                }

                val outputContent = try {
                    generateContentWithRetry(inputContent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini product analysis failed after retries", e)
                    val errorMsg = e.message ?: ""
                    val errorStr = e.toString()
                    if (errorStr.contains("503") || errorStr.contains("UNAVAILABLE") || errorMsg.contains("503") || errorMsg.contains("UNAVAILABLE")) {
                        throw Exception("AI service is currently busy. Please try again later.")
                    } else if (errorStr.contains("429") || errorStr.contains("RESOURCE_EXHAUSTED") || errorMsg.contains("429")) {
                        throw Exception("AI rate limit exceeded. Please try again in a moment.")
                    } else if (errorMsg.startsWith("AI error:") || errorMsg.startsWith("AI could not")) {
                        throw e
                    } else {
                        throw Exception("AI service is currently unavailable. Please try again later.")
                    }
                }

                val cleanJson = extractJson(outputContent)
                try {
                    val decoded = json.decodeFromString<List<ExpirationInfo>>(cleanJson)
                    decoded.map { info ->
                        info.copy(
                            expiration_date = parseExpirationDateUseCase.formatToStandardDate(info.expiration_date) ?: info.expiration_date,
                            production_date = parseExpirationDateUseCase.formatToStandardDate(info.production_date) ?: info.production_date
                        )
                    }
                } catch (se: Exception) {
                    Log.e(TAG, "Failed to parse AI response. Raw text: $outputContent", se)
                    throw Exception("AI error: invalid response format. Please try again.")
                }
            } finally {
                loadedBitmaps.forEach { bitmap ->
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }
        }

    private suspend fun generateContentWithRetry(contentParam: com.google.ai.client.generativeai.type.Content): String {
        return retryWithBackoff(times = 2, initialDelay = 1000) {
            val response = primaryModel.generateContent(contentParam)
            response.text ?: throw Exception("Empty response from AI")
        }
    }

    private suspend fun <T> retryWithBackoff(
        times: Int = 3,
        initialDelay: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Gemini API call failed (attempt ${attempt + 1}/$times): ${e.message}. Retrying in ${currentDelay}ms...")
                delay(currentDelay.milliseconds)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return block()
    }

    internal fun extractJson(text: String): String = LlmPrompts.extractJson(text)
}
