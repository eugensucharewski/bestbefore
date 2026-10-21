package de.eugens.bestbefore.products.data.analyzer

import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import javax.inject.Inject

class ProductNameExtractor @Inject constructor() {

    companion object {
        // Noise word markers for ingredients, nutrition, and packaging (DE, EN, RU)
        private val NOISE_WORDS_REGEX = Regex(
            pattern = """(?i)\b(ZUTATEN|INGREDIENTS|СОСТАВ|NÄHRWERTE|NUTRITION|ПИЩЕВАЯ|ЭНЕРГЕТИЧЕСКАЯ|MINDESTENS|HALTBAR|BEST\s+BEFORE|ГОДЕН|ИЗГОТОВИТЕЛЬ|HERGESTELLT|GEWICHT|NETTO|МАССА|НЕТТО|KÜHL|LAGERN|ХРАНИТЬ|KCAL|ККАЛ|EIWEISS|FETT|KOHLENHYDRATE|DAVON\s+ZUCKER)\b"""
        )

        // Quantities, volumes, fat percentages, barcodes (500g, 1,5L, 3.5%, 400ml)
        private val QUANTITY_OR_CODE_REGEX = Regex(
            pattern = """(?i)^\s*(\d+[.,]?\d*\s*(g|kg|ml|l|cl|г|кг|мл|л|%|stk|pcs)\b|\d{8,14})\s*$"""
        )
    }

    data class ScoredLine(
        val text: String,
        val boundingBox: Rect,
        val height: Int,
        val score: Double
    )

    fun extractProductName(visionText: Text, imageWidth: Int, imageHeight: Int): String {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) return ""

        // Find the maximum line height in the image (likely the main title)
        val maxLineHeight = allLines.maxOfOrNull { it.boundingBox?.height() ?: 0 } ?: 0
        if (maxLineHeight == 0) return fallbackFromRawText(visionText.text)

        val candidates = mutableListOf<ScoredLine>()

        for (line in allLines) {
            val box = line.boundingBox ?: continue
            val rawText = line.text.trim()

            // 1. Basic noise filtering
            if (!isValidCandidate(rawText)) continue

            // 2. Key factor — relative font height (from 0.0 to 1.0)
            val heightRatio = box.height().toDouble() / maxLineHeight
            // Filter out small text (anything under 40% of the largest font height is description/ingredients)
            if (heightRatio < 0.40) continue

            var score = heightRatio * 100.0

            // 3. Bonus for being vertically central (top 15% and bottom 15% are often occupied by slogans/weight)
            val relativeCenterY = box.centerY().toDouble() / imageHeight
            if (relativeCenterY in 0.2..0.75) {
                score += 15.0
            }

            // 4. Penalty for overly long lines (product names are rarely longer than 30 characters in a single line)
            if (rawText.length > 30) {
                score -= 25.0
            }

            // 5. Penalty for high digit ratio
            val digitRatio = rawText.count { it.isDigit() }.toDouble() / rawText.length
            if (digitRatio > 0.3) {
                score -= 40.0
            }

            if (score > 40.0) {
                candidates.add(ScoredLine(cleanText(rawText), box, box.height(), score))
            }
        }

        if (candidates.isEmpty()) return fallbackFromRawText(visionText.text)

        // Pick the best line (largest and cleanest)
        val bestLine = candidates.maxBy { it.score }

        // Frequently, the product name is split across 2 lines of similar font size (e.g., "FRISCHE" \n "ALPENMILCH")
        // Look for a companion line located right below or above the best line with similar height
        val companionLine = candidates
            .filter { it != bestLine }
            .firstOrNull { other ->
                val heightSimilarity = other.height.toDouble() / bestLine.height
                val verticalGap = if (other.boundingBox.top >= bestLine.boundingBox.bottom) {
                    other.boundingBox.top - bestLine.boundingBox.bottom
                } else {
                    bestLine.boundingBox.top - other.boundingBox.bottom
                }
                // If height differs by no more than 35% and vertical gap is less than 1.5 line heights
                heightSimilarity in 0.65..1.35 && verticalGap in -10..(bestLine.height * 1.5).toInt()
            }

        return if (companionLine != null) {
            // Sort top-to-bottom to preserve natural reading order
            listOf(bestLine, companionLine)
                .sortedBy { it.boundingBox.top }
                .joinToString(" ") { it.text }
        } else {
            bestLine.text
        }
    }

    private fun isValidCandidate(text: String): Boolean {
        if (text.length < 3) return false
        if (NOISE_WORDS_REGEX.containsMatchIn(text)) return false
        if (QUANTITY_OR_CODE_REGEX.matches(text)) return false
        // Must contain at least 70% letters or whitespace
        val letterOrSpaceCount = text.count { it.isLetter() || it.isWhitespace() }
        if (letterOrSpaceCount.toDouble() / text.length < 0.7) return false
        return true
    }

    private fun cleanText(text: String): String {
        // Remove stray OCR special characters from edges and format ALL CAPS to Title Case per word
        val trimmed = text.trim { !it.isLetterOrDigit() }
        return if (trimmed.all { !it.isLetter() || it.isUpperCase() }) {
            trimmed.lowercase().split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        } else {
            trimmed
        }
    }

    // Fallback when bounding box geometry is unavailable (or working purely with plain text)
    fun fallbackFromRawText(rawText: String): String {
        return rawText.lines()
            .map { it.trim() }
            .filter { isValidCandidate(it) && it.length in 3..35 }
            .firstOrNull()
            ?.let { cleanText(it) }
            ?: ""
    }
}