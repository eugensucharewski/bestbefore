package de.eugens.bestbefore.products.domain.use_case

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.eugens.bestbefore.products.domain.model.ScannedItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ClearTempScanFilesUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val defaultDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TEMP_SCAN_DIR = "temp_scans"
    }

    suspend operator fun invoke(items: List<ScannedItem>) = withContext(defaultDispatcher) {
        items.forEach { item ->
            item.productImagePath?.let { path ->
                try {
                    File(path).delete()
                } catch (_: Exception) {}
            }
            item.dateImagePath?.let { path ->
                try {
                    File(path).delete()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun clearAllTempFiles() = withContext(defaultDispatcher) {
        try {
            val tempDir = File(context.cacheDir, TEMP_SCAN_DIR)
            if (tempDir.exists() && tempDir.isDirectory) {
                tempDir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }
}
