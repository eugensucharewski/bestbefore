package de.eugens.bestbefore.products.data.analyzer

import android.util.Log
import de.eugens.bestbefore.products.domain.analyzer.AIProductAnalyzer
import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.ScannedItem
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FallbackProductAnalyzer @Inject constructor(
    private val nanoAnalyzer: GeminiNanoProductAnalyzer,
    private val geminiAnalyzer: GeminiProductAnalyzer,
    private val localAnalyzer: LocalProductAnalyzer
) : AIProductAnalyzer {

    companion object {
        private const val TAG = "FallbackProductAnalyzer"
    }

    override suspend fun analyzeImages(items: List<ScannedItem>): List<ExpirationInfo> {
        return try {
            nanoAnalyzer.analyzeImages(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano analyzer is unavailable or failed: ${e.message}. Falling back to remote Gemini analyzer.", e)
            try {
                geminiAnalyzer.analyzeImages(items)
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                Log.w(TAG, "Gemini analyzer is unavailable or failed: ${e2.message}. Falling back to local analyzer.", e2)
                localAnalyzer.analyzeImages(items)
            }
        }
    }
}
