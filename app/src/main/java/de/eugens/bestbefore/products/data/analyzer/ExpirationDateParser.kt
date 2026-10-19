package de.eugens.bestbefore.products.data.analyzer

import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

enum class ConfidenceLevel {
    HIGH,   // Found date near explicit expiration marker (MHD, EXP, BEST BEFORE)
    MEDIUM, // Found full date (with day and month/word) without markers or in a pair (Manufactured + Best before)
    LOW     // Found short date (MM/YYYY or DD.MM.YY) in noisy text without markers
}

data class ExpiryDateResult(
    val date: LocalDate,
    val rawMatch: String,
    val confidence: ConfidenceLevel
)

class ExpiryDateParser(
    private val clock: Clock
) {
    @javax.inject.Inject constructor() : this(Clock.systemDefaultZone())

    companion object {
        // Expiration date markers (increase priority)
        private val EXPIRY_MARKER_REGEX = Regex(
            pattern = """(?i)\b(MHD|MINDESTENS\s+HALTBAR\s+BIS|VERBRAUCHEN\s+BIS|ZU\s+VERBRAUCHEN\s+BIS|BEST\s+BEFORE|USE\s+BY|BBE|BB|EXP|EXPIRY|EXPIRES|ГОДЕН\s+ДО|ГОДЕН|СРОК\s+ГОДНОСТИ|УПОТРЕБИТЬ\s+ДО)\b"""
        )

        // Production date markers (lower priority or discard date if it is the only one)
        private val PROD_MARKER_REGEX = Regex(
            pattern = """(?i)\b(MFG|MFD|PROD|PRODUCTION|PACKED|PKD|HERSTELLUNG|HERGESTELLT|ABFÜLLUNG|ИЗГ|ИЗГОТОВЛЕНО|ДАТА\s+ИЗГОТОВЛЕНИЯ|ПРОИЗВЕДЕНО|ВЫРАБОТАНО)\b"""
        )

        // Noise prefixes before numbers that are not dates
        private val NOISE_PREFIX_REGEX = Regex(
            pattern = """(?i)(LOT|L\.|ART|NO|NR|№|ПАРТИЯ|ВЕС|GEWICHT|NETTO|EAN|GTIN|ТЕЛ|TEL)\s*[:.#-]?\s*$"""
        )

        // Month dictionary (EN, DE, RU)
        private val MONTH_MAP = mapOf(
            // EN
            "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
            "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
            // DE
            "JAN" to 1, "MAER" to 3, "MÄR" to 3, "MRZ" to 3, "MAI" to 5, "OKT" to 10, "DEZ" to 12,
            // RU (3 letters + common variations)
            "ЯНВ" to 1, "ФЕВ" to 2, "МАР" to 3, "АПР" to 4, "МАЙ" to 5, "МАЯ" to 5,
            "ИЮН" to 6, "ИЮЛ" to 7, "АВГ" to 8, "СЕН" to 9, "ОКТ" to 10, "НОЯ" to 11, "ДЕК" to 12
        )

        private val MONTH_NAMES_PATTERN = MONTH_MAP.keys.joinToString("|")

        // 1. DD.MM.YYYY or DD.MM.YY (separators: dot, slash, hyphen, spaces around them)
        private val REGEX_DMY_NUMERIC = Regex(
            """(?<!\d)([0-3OIl|]?\d)\s*[./-]\s*([01OIl|]?\d)\s*[./-]\s*(\d{2,4})(?!\d)"""
        )

        // 2. YYYY-MM-DD or YYYY.MM.DD (ISO)
        private val REGEX_YMD_ISO = Regex(
            """(?<!\d)(20\d{2})\s*[./-]\s*([01OIl|]?\d)\s*[./-]\s*([0-3OIl|]?\d)(?!\d)"""
        )

        // 3. DD MMM YYYY or DD. MMM YY (text month)
        private val REGEX_DMY_TEXT_MONTH = Regex(
            """(?i)(?<!\d)([0-3OIl|]?\d)\s*[./\s-]{1,3}\s*($MONTH_NAMES_PATTERN)[A-ZА-ЯЁ.]*\s*[./\s-]{1,3}\s*(\d{2,4})(?!\d)"""
        )

        // 4. MM/YYYY or MM.YY (without day — normalized to end of month)
        private val REGEX_MY_NUMERIC = Regex(
            """(?<!\d)([01OIl|]\d)\s*[./-]\s*(20\d{2}|\d{2})(?!\d)"""
        )
    }

    private data class Candidate(
        val date: LocalDate,
        val rawMatch: String,
        val lineIndex: Int,
        val matchRange: IntRange,
        val isExplicitFullDate: Boolean,
        val score: Int,
        val confidence: ConfidenceLevel
    )

    fun parse(rawOcrText: String): ExpiryDateResult? {
        if (rawOcrText.isBlank()) return null

        val currentYear = LocalDate.now(clock).year
        val minValidYear = currentYear - 2
        val maxValidYear = currentYear + 10

        val lines = rawOcrText.lines()
        val candidates = mutableListOf<Candidate>()

        // Find indices of lines containing markers
        val expiryMarkerLines = mutableSetOf<Int>()
        val prodMarkerLines = mutableSetOf<Int>()

        lines.forEachIndexed { index, line ->
            if (EXPIRY_MARKER_REGEX.containsMatchIn(line)) expiryMarkerLines.add(index)
            if (PROD_MARKER_REGEX.containsMatchIn(line)) prodMarkerLines.add(index)
        }

        lines.forEachIndexed { lineIdx, rawLine ->
            val cleanedLine = preprocessOcrDigits(rawLine)
            val extractedInLine = mutableListOf<Triple<LocalDate, IntRange, Boolean>>()

            // A. Text month (12 OCT 2026, 24. MAI 26)
            REGEX_DMY_TEXT_MONTH.findAll(cleanedLine).forEach { match ->
                val day = cleanToInt(match.groupValues[1])
                val monthKey = match.groupValues[2].uppercase()
                val month = MONTH_MAP[monthKey]
                val year = normalizeYear(cleanToInt(match.groupValues[3]), currentYear)

                val date = buildValidDate(year, month, day, minValidYear, maxValidYear)
                if (date != null) {
                    extractedInLine.add(Triple(date, match.range, true))
                }
            }

            // B. ISO format (YYYY-MM-DD)
            REGEX_YMD_ISO.findAll(cleanedLine).forEach { match ->
                if (overlapsWithExisting(match.range, extractedInLine)) return@forEach
                val year = cleanToInt(match.groupValues[1])
                val month = cleanToInt(match.groupValues[2])
                val day = cleanToInt(match.groupValues[3])

                val date = buildValidDate(year, month, day, minValidYear, maxValidYear)
                if (date != null) {
                    extractedInLine.add(Triple(date, match.range, true))
                }
            }

            // C. Classic DD.MM.YYYY / DD.MM.YY
            REGEX_DMY_NUMERIC.findAll(cleanedLine).forEach { match ->
                if (overlapsWithExisting(match.range, extractedInLine)) return@forEach
                val day = cleanToInt(match.groupValues[1])
                val month = cleanToInt(match.groupValues[2])
                val rawYearStr = match.groupValues[3]
                if (rawYearStr.length == 3) return@forEach // Exclude artifacts like 12.10.202

                val year = normalizeYear(cleanToInt(rawYearStr), currentYear)
                val isFourDigitYear = rawYearStr.length == 4

                val date = buildValidDate(year, month, day, minValidYear, maxValidYear)
                if (date != null) {
                    extractedInLine.add(Triple(date, match.range, isFourDigitYear))
                }
            }

            // D. Short format MM/YYYY or MM.YY (only if not overlapping with full dates)
            REGEX_MY_NUMERIC.findAll(cleanedLine).forEach { match ->
                if (overlapsWithExisting(match.range, extractedInLine)) return@forEach
                val month = cleanToInt(match.groupValues[1])
                val rawYearStr = match.groupValues[2]
                val year = normalizeYear(cleanToInt(rawYearStr), currentYear)

                if (year != null && month != null && month in 1..12 && year in minValidYear..maxValidYear) {
                    val lastDayOfMonth = YearMonth.of(year, month).atEndOfMonth()
                    extractedInLine.add(Triple(lastDayOfMonth, match.range, false))
                }
            }

            // Evaluate each candidate in the current line
            for ((date, range, isExplicitFull) in extractedInLine) {
                val rawMatch = rawLine.substring(
                    range.first.coerceAtLeast(0),
                    (range.last + 1).coerceAtMost(rawLine.length)
                ).trim()

                val scoreAndConfidence = evaluateCandidate(
                    date = date,
                    line = rawLine,
                    lineIdx = lineIdx,
                    matchRange = range,
                    isExplicitFull = isExplicitFull,
                    allDatesInSameLine = extractedInLine.map { it.first },
                    expiryMarkerLines = expiryMarkerLines,
                    prodMarkerLines = prodMarkerLines
                ) ?: continue

                candidates.add(
                    Candidate(
                        date = date,
                        rawMatch = rawMatch,
                        lineIndex = lineIdx,
                        matchRange = range,
                        isExplicitFullDate = isExplicitFull,
                        score = scoreAndConfidence.first,
                        confidence = scoreAndConfidence.second
                    )
                )
            }
        }

        // If there are no expiration markers in text at all, but there are multiple dates
        // (for example, production date and expiration date printed next to each other without labels),
        // give a bonus to the later date.
        val maxCandidateDate = candidates.maxByOrNull { it.date }?.date

        val finalBest = candidates
            .map { candidate ->
                val bonus = if (candidates.size > 1 && candidate.date == maxCandidateDate) 15 else 0
                candidate.copy(score = candidate.score + bonus)
            }
            .filter { it.score > 0 }
            .maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.date })

        return finalBest?.let {
            ExpiryDateResult(
                date = it.date,
                rawMatch = it.rawMatch,
                confidence = it.confidence
            )
        }
    }

    /**
     * Heuristic candidate evaluation. Returns Pair(Score, ConfidenceLevel) or null if the date should be discarded.
     */
    private fun evaluateCandidate(
        date: LocalDate,
        line: String,
        lineIdx: Int,
        matchRange: IntRange,
        isExplicitFull: Boolean,
        allDatesInSameLine: List<LocalDate>,
        expiryMarkerLines: Set<Int>,
        prodMarkerLines: Set<Int>
    ): Pair<Int, ConfidenceLevel>? {
        val prefixText = line.substring(0, matchRange.first)

        // 1. Check for technical noise directly before numbers (LOT 12.10.26, № 05.2026)
        if (NOISE_PREFIX_REGEX.containsMatchIn(prefixText)) {
            return null
        }

        var score = if (isExplicitFull) 50 else 30

        val hasExpiryInLine = lineIdx in expiryMarkerLines
        val hasExpiryInPrevLine = (lineIdx - 1) in expiryMarkerLines
        val hasProdInLine = lineIdx in prodMarkerLines
        val hasProdInPrevLine = (lineIdx - 1) in prodMarkerLines

        // Marker position analysis within a single line (e.g., "MFG: 01.01.2026 EXP: 01.07.2026")
        val lastExpiryBeforeDate = EXPIRY_MARKER_REGEX.findAll(prefixText).lastOrNull()
        val lastProdBeforeDate = PROD_MARKER_REGEX.findAll(prefixText).lastOrNull()

        val isDirectlyPrecededByExpiry = lastExpiryBeforeDate != null &&
                (lastProdBeforeDate == null || lastExpiryBeforeDate.range.first > lastProdBeforeDate.range.first)

        val isDirectlyPrecededByProd = lastProdBeforeDate != null &&
                (lastExpiryBeforeDate == null || lastProdBeforeDate.range.first > lastExpiryBeforeDate.range.first)

        when {
            isDirectlyPrecededByExpiry -> {
                score += 60
            }
            isDirectlyPrecededByProd -> {
                // If there are two dates in the line ("PROD 01.01.26 EXP 01.06.26" or "MFG / EXP: 01.01.26 01.06.26"),
                // and current date is the later of the two, then it is an expiration date despite the PROD marker at line start.
                if (allDatesInSameLine.size >= 2 && date == allDatesInSameLine.maxOrNull() && hasExpiryInLine) {
                    score += 45
                } else {
                    return null // Discard pure production date
                }
            }
            hasExpiryInLine || hasExpiryInPrevLine -> {
                // Expiration marker exists on this or previous line (e.g., "MHD siehe Deckel:\n15.08.2027")
                if (allDatesInSameLine.size >= 2 && date == allDatesInSameLine.minOrNull()) {
                    // In a pair of dates, the earlier one is the production date
                    return null
                }
                score += 50
            }
            hasProdInLine || hasProdInPrevLine -> {
                // If there is only a production marker and no other dates — discard
                if (allDatesInSameLine.size < 2 || date == allDatesInSameLine.minOrNull()) {
                    return null
                }
            }
        }

        // Bonus if the date is in the future relative to "today"
        val today = LocalDate.now(clock)
        if (!date.isBefore(today)) {
            score += 10
        } else {
            // Product may be slightly expired (user scans fridge),
            // but distant past without an explicit MHD marker is suspicious
            score -= 10
        }

        val confidence = when {
            score >= 90 -> ConfidenceLevel.HIGH
            score >= 55 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        return score to confidence
    }

    /**
     * Replaces common OCR errors (O -> 0, I/l/| -> 1) when they are adjacent to digits or date separators.
     */
    private fun preprocessOcrDigits(input: String): String {
        val chars = input.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            val prev = if (i > 0) chars[i - 1] else ' '
            val next = if (i < chars.lastIndex) chars[i + 1] else ' '

            val isNearDigitOrSep = prev.isDigit() || next.isDigit() ||
                    prev in "./-" || next in "./-"

            if (isNearDigitOrSep) {
                when (c) {
                    'O', 'o', 'О', 'о' -> {
                        // Do not replace letter O inside words (e.g., "OCT", "EXP", "BY")
                        val isSurroundedByLetters = prev.isLetter() && next.isLetter()
                        if (!isSurroundedByLetters && (prev.isDigit() || next.isDigit())) {
                            chars[i] = '0'
                        }
                    }
                    'I', 'l', '|' -> {
                        val isSurroundedByLetters = prev.isLetter() && next.isLetter()
                        if (!isSurroundedByLetters && (prev.isDigit() || next.isDigit())) {
                            chars[i] = '1'
                        }
                    }
                }
            }
        }
        return String(chars)
    }

    private fun cleanToInt(raw: String): Int? {
        val normalized = raw
            .replace('O', '0').replace('o', '0')
            .replace('I', '1').replace('l', '1').replace('|', '1')
            .trim()
        return normalized.toIntOrNull()
    }

    private fun normalizeYear(rawYear: Int?, currentYear: Int): Int? {
        if (rawYear == null) return null
        return if (rawYear in 0..99) {
            val century = (currentYear / 100) * 100
            century + rawYear
        } else {
            rawYear
        }
    }

    private fun buildValidDate(
        year: Int?,
        month: Int?,
        day: Int?,
        minYear: Int,
        maxYear: Int
    ): LocalDate? {
        if (year == null || month == null || day == null) return null
        if (year !in minYear..maxYear) return null
        if (month !in 1..12 || day !in 1..31) return null

        return try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            // Discards non-existent dates like 31.02.2026 or 29.02.2026 (non-leap year)
            null
        }
    }

    private fun overlapsWithExisting(
        range: IntRange,
        existing: List<Triple<LocalDate, IntRange, Boolean>>
    ): Boolean {
        return existing.any { (_, existingRange, _) ->
            range.first <= existingRange.last && existingRange.first <= range.last
        }
    }
}