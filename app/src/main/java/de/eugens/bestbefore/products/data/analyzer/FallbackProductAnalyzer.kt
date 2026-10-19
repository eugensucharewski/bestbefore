package de.eugens.bestbefore.products.data.analyzer

import android.util.Log
import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.analyzer.TextRecognitionResult
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class FallbackProductAnalyzer @Inject constructor(
    private val geminiAnalyzer: GeminiProductAnalyzer,
    private val localAnalyzer: LocalProductAnalyzer
) : AIProductAnalyzer {

    companion object {
        private const val TAG = "FallbackProductAnalyzer"
    }

    override suspend fun analyzeImages(items: List<TextRecognitionResult>): List<ExpirationInfo> {
        return try {
            geminiAnalyzer.analyzeImages(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini analyzer failed: ${e.message}. Falling back to local analyzer.", e)
            localAnalyzer.analyzeImages(items)
        }
    }
}
