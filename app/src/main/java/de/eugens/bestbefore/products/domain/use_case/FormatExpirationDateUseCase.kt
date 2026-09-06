package de.eugens.bestbefore.products.domain.use_case

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

class FormatExpirationDateUseCase @Inject constructor(
    private val parseExpirationDateUseCase: ParseExpirationDateUseCase
) {
    operator fun invoke(dateStr: String?, locale: Locale = Locale.getDefault()): String {
        return formatDateForDisplay(dateStr, locale, parseExpirationDateUseCase)
    }
}

fun formatDateForDisplay(
    dateStr: String?,
    locale: Locale = Locale.getDefault(),
    parseExpirationDateUseCase: ParseExpirationDateUseCase = ParseExpirationDateUseCase()
): String {
    if (dateStr.isNullOrBlank()) return ""
    val parsedDate = parseExpirationDateUseCase(dateStr) ?: return dateStr
    val formatter = when (locale.language) {
        "de", "ru" -> DateTimeFormatter.ofPattern("dd.MM.yyyy")
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    }
    return parsedDate.format(formatter)
}
