package de.eugens.bestbefore.products.data.analyzer

import android.util.Log
import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.analyzer.TextRecognitionResult
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class LocalProductAnalyzer @Inject constructor(
    private val parser: ExpiryDateParser
) : AIProductAnalyzer {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override suspend fun analyzeImages(items: List<TextRecognitionResult>): List<ExpirationInfo> {
        return items.map { result ->
            val parsedResult = parser.parse(result.expirationDate)
            
            if (parsedResult != null) {
                Log.d("AIProductAnalyzer", "Date found in text: ${result.expirationDate}")
                ExpirationInfo(
                    productName = "",
                    date_found = true,
                    expiration_date = parsedResult.date.format(dateFormatter),
                    production_date = null,
                    confidence = parsedResult.confidence.name.lowercase(),
                    raw_text_detected = result.expirationDate
                )
            } else {
                Log.d("AIProductAnalyzer", "No date found in text: ${result.expirationDate}")
                ExpirationInfo(
                    productName = "",
                    date_found = false,
                    expiration_date = "",
                    production_date = "",
                    confidence = "low",
                    raw_text_detected = result.expirationDate
                )
            }
        }
    }
}
