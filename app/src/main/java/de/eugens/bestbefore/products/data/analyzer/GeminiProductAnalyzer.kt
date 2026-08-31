package de.eugens.bestbefore.products.data.analyzer

import android.graphics.BitmapFactory
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import de.eugens.bestbefore.BuildConfig
import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiProductAnalyzer @Inject constructor() : AIProductAnalyzer {

    companion object {
        private const val TAG = "GeminiProductAnalyzer"
        private const val GEMINI_MODEL_NAME = "gemini-3.5-flash"
        private const val SYSTEM_INSTRUCTION = "You are a specialized assistant for recognizing food product names and their expiration dates. " +
                "For each pair of images (product photo and date photo), determine the product name and expiration date. " +
                "Return ONLY a JSON array of objects with the following fields: productName (String), date_found (boolean), expiration_date (DD.MM.YYYY), production_date (YYYY-MM-DD), confidence (high/medium/low), raw_text_detected (string). " +
                "Do not include any markdown formatting or extra text."
        private const val ANALYZE_PROMPT = "Analyze these image pairs. Each pair consists of a product photo and its expiration date photo. " +
                "Return the results as a JSON array of ExpirationInfo objects. Ensure all fields are present for each item."
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val generativeModel = GenerativeModel(
        modelName = GEMINI_MODEL_NAME,
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content {
            text(SYSTEM_INSTRUCTION)
        },
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    override suspend fun analyzeImages(items: List<ScannedItem>): List<ExpirationInfo> =
        withContext(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(
                    content {
                        items.forEach { item ->
                            item.productBitmap?.let {
                                val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                                image(bitmap)
                            }
                            item.dateBitmap?.let {
                                val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                                image(bitmap)
                            }
                        }
                        text(ANALYZE_PROMPT)
                    }
                )

                val outputContent = response.text ?: throw Exception("Empty response from AI")
                val cleanJson = extractJson(outputContent)
                try {
                    json.decodeFromString<List<ExpirationInfo>>(cleanJson)
                } catch (se: Exception) {
                    Log.e(TAG, "Failed to parse AI response. Raw text: $outputContent", se)
                    throw Exception("AI error: invalid response format. Please try again.")
                }
            } catch (e: Exception) {
                val errorStr = e.toString()
                if (errorStr.contains("503") || errorStr.contains("UNAVAILABLE")) {
                    throw Exception("AI service is currently busy (503). Please try again later.")
                }
                throw e
            }
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