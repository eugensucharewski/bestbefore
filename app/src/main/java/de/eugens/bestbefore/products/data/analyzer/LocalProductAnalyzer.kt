package de.eugens.bestbefore.products.data.analyzer

import android.util.Log
import de.eugens.bestbefore.Constants
import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.analyzer.ImageTextRecognizer
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class LocalProductAnalyzer @Inject constructor(
    private val textRecognizer: ImageTextRecognizer,
    private val parser: ExpirationDateParser
) : AIProductAnalyzer {

    companion object {
        private const val TAG = "LocalProductAnalyzer"
    }

    private val dateFormatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT)

    override suspend fun analyzeImages(items: List<ScannedItem>): List<ExpirationInfo> {
        val ocrResults = textRecognizer.analyzeImages(items)
        return ocrResults.map { result ->
            val parsedResult = parser.parse(result.expirationDate)

            if (parsedResult != null) {
                Log.d(TAG, "Date found in text: ${result.expirationDate}")
                ExpirationInfo(
                    productName = result.productName,
                    date_found = true,
                    expiration_date = parsedResult.date.format(dateFormatter),
                    production_date = null,
                    confidence = parsedResult.confidence.name.lowercase(),
                    raw_text_detected = result.expirationDate
                )
            } else {
                Log.d(TAG, "No date found in text: ${result.expirationDate}")
                ExpirationInfo(
                    productName = result.productName,
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
