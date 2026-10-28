package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.Constants
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import javax.inject.Inject

class ParseExpirationDateUseCase @Inject constructor() {

    private val standardFormatter = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT)

    private val dateFormatters: List<DateTimeFormatter> = listOf(
        // Standard dot formats with 4-digit year (dd.MM.yyyy, d.M.yyyy)
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),

        // Standard dot formats with 2-digit year (dd.MM.yy, d.M.yy)
        createTwoDigitYearDotFormatter(),

        // ISO / YMD formats with 4-digit year (yyyy-MM-dd, yyyy.MM.dd, yyyy/MM/dd)
        DateTimeFormatter.ofPattern("yyyy-M-d"),
        DateTimeFormatter.ofPattern("yyyy.M.d"),
        DateTimeFormatter.ofPattern("yyyy/M/d"),

        // Slash formats with 4-digit year (dd/MM/yyyy, d/M/yyyy)
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),

        // Slash formats with 2-digit year (dd/MM/yy, d/M/yy)
        createTwoDigitYearSlashFormatter(),

        // Hyphen formats with 4-digit year (dd-MM-yyyy, d-M-yyyy)
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),

        // Hyphen formats with 2-digit year (dd-MM-yy, d-M-yy)
        createTwoDigitYearHyphenFormatter(),

        // ISO / YMD formats with 2-digit year (yy-MM-dd, yy.MM.dd, yy/MM/dd)
        createTwoDigitYearYmdFormatter("-"),
        createTwoDigitYearYmdFormatter("."),
        createTwoDigitYearYmdFormatter("/"),

        // US format (MM/dd/yyyy, M/d/yyyy, MM/dd/yy, M/d/yy)
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        createTwoDigitYearUsFormatter(),
    )

    operator fun invoke(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null

        var sanitized = dateStr.trim()
            .trim('"', '\'', '`')
            .trim()
            .removeSuffix(".")
            .removeSuffix(",")
            .removeSuffix(";")
            .trim()

        if (sanitized.isBlank()) return null
        
        // Handle "MM.YYYY" or "MM/YYYY" or "MM-YYYY" (Missing day)
        val missingDayRegex4 = Regex("^(\\d{1,2})[./-](\\d{4})$")
        val match4 = missingDayRegex4.matchEntire(sanitized)
        if (match4 != null) {
            sanitized = "01.${match4.groupValues[1]}.${match4.groupValues[2]}"
        } else {
            // Handle "MM.YY" vs "DD.MM"
            val twoPartRegex = Regex("^(\\d{1,2})[./-](\\d{1,2})$")
            val match2 = twoPartRegex.matchEntire(sanitized)
            if (match2 != null) {
                val part2 = match2.groupValues[2].toInt()
                if (part2 > 12) {
                    // It must be a year, e.g. 10.26 (Oct 2026) -> Missing day
                    sanitized = "01.${match2.groupValues[1]}.${match2.groupValues[2]}"
                } else {
                    // Ambiguous, assume DD.MM -> Missing year. Append current year.
                    val currentYear = LocalDate.now().year
                    sanitized = "${match2.groupValues[1]}.${match2.groupValues[2]}.$currentYear"
                }
            }
        }

        for (formatter in dateFormatters) {
            try {
                return LocalDate.parse(sanitized, formatter)
            } catch (_: Exception) {
                // Ignore and proceed to the next formatter
            }
        }
        return null
    }

    /**
     * Formats any valid date string into the standard format defined in [Constants.DATE_FORMAT] ("dd.MM.yyyy").
     * Returns null if the date string is null, blank, or cannot be parsed.
     */
    fun formatToStandardDate(dateStr: String?): String? {
        val parsed = invoke(dateStr) ?: return null
        return parsed.format(standardFormatter)
    }

    private fun createTwoDigitYearDotFormatter(): DateTimeFormatter {
        return DateTimeFormatterBuilder()
            .appendPattern("d.M.")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter()
    }

    private fun createTwoDigitYearSlashFormatter(): DateTimeFormatter {
        return DateTimeFormatterBuilder()
            .appendPattern("d/M/")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter()
    }

    private fun createTwoDigitYearHyphenFormatter(): DateTimeFormatter {
        return DateTimeFormatterBuilder()
            .appendPattern("d-M-")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter()
    }

    private fun createTwoDigitYearYmdFormatter(separator: String): DateTimeFormatter {
        return DateTimeFormatterBuilder()
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .appendPattern("${separator}M${separator}d")
            .toFormatter()
    }

    private fun createTwoDigitYearUsFormatter(): DateTimeFormatter {
        return DateTimeFormatterBuilder()
            .appendPattern("M/d/")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
            .toFormatter()
    }
}
