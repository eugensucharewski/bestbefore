package de.eugens.bestbefore.products.data

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
        private const val GEMINI_MODEL_NAME = "gemini-flash-latest"
        private const val SYSTEM_INSTRUCTION = "Ты — специализированный ассистент по распознаванию названий продуктов питания и их сроков годности. " +
                "Для каждого изображения (фото продукта и фото даты) определи название продукта и срок годности. " +
                "Верни массив JSON объектов с полями: productName (String), date_found (boolean), expiration_date (DD.MM.YYYY), production_date (YYYY-MM-DD), confidence (high/medium/low), raw_text_detected (string)."
        private const val ANALYZE_PROMPT = "Проанализируй эти пары изображений. Каждая пара изображений - это фото продукта и его срока годности. " +
                "Верни результат в виде JSON массива объектов ExpirationInfo."
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
            val response = generativeModel.generateContent(
                content {
                    items.forEach { item ->
                        item.productBitmap?.let { image(it) }
                        item.dateBitmap?.let { image(it) }
                    }
                    text(ANALYZE_PROMPT)
                }
            )

            val outputContent = response.text ?: throw Exception("Empty response from AI")
            json.decodeFromString<List<ExpirationInfo>>(outputContent)
        }
}
