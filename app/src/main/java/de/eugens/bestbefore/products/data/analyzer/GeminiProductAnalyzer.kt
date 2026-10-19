package de.eugens.bestbefore.products.data.analyzer

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import de.eugens.bestbefore.BuildConfig
import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.analyzer.TextRecognitionResult
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class GeminiProductAnalyzer @Inject constructor() : AIProductAnalyzer {

    companion object {
        private const val TAG = "GeminiProductAnalyzer"
        private const val PRIMARY_MODEL_NAME = "gemini-3.8-flash"

        private const val SYSTEM_INSTRUCTION =
            "You are a specialized assistant for processing food product names and expiration dates from text extracted via on-device OCR. " +
            "Because the input text was recognized on-device and may contain OCR noise, typos, or misread characters, your task is to clean up, correct, and format the product names and parse expiration or production dates. " +
            "For each item, return a JSON object with the following fields: " +
            "productName (String, cleaned and formatted product name), " +
            "date_found (boolean, true if a valid expiration or production date was extracted), " +
            "expiration_date (String in DD.MM.YYYY format, or null), " +
            "production_date (String in YYYY-MM-DD format, or null), " +
            "confidence (String: 'high', 'medium', or 'low' based on input clarity), " +
            "raw_text_detected (String, the combined original raw OCR input text). " +
            "Return ONLY a JSON array of these objects without any markdown formatting or extra text."

        private const val ANALYZE_PROMPT =
            "Analyze the following items containing raw text extracted by on-device OCR. " +
            "Extract, clean, and structure the product names and expiration dates into a JSON array of ExpirationInfo objects."
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val primaryModel = GenerativeModel(
        modelName = PRIMARY_MODEL_NAME,
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content {
            text(SYSTEM_INSTRUCTION)
        },
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )


    override suspend fun analyzeImages(items: List<TextRecognitionResult>): List<ExpirationInfo> =
        withContext(Dispatchers.IO) {
            val formattedItems = items.mapIndexed { index, item ->
                "Item ${index + 1}:\n- Raw Product Name OCR: \"${item.productName}\"\n- Raw Expiration Date OCR: \"${item.expirationDate}\""
            }.joinToString("\n\n")

            val prompt = "$ANALYZE_PROMPT\n\nInput Data:\n$formattedItems"

            val outputContent = try {
                generateContentWithRetry(prompt)
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
                json.decodeFromString<List<ExpirationInfo>>(cleanJson)
            } catch (se: Exception) {
                Log.e(TAG, "Failed to parse AI response. Raw text: $outputContent", se)
                throw Exception("AI error: invalid response format. Please try again.")
            }
        }

    private suspend fun generateContentWithRetry(prompt: String): String {
        return retryWithBackoff(times = 2, initialDelay = 1000) {
            val response = primaryModel.generateContent(content { text(prompt) })
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

    internal fun extractJson(text: String): String {
        val jsonStart = text.indexOf("[")
        val jsonEnd = text.lastIndexOf("]")
        return if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            text.substring(jsonStart, jsonEnd + 1)
        } else {
            text
        }
    }
}