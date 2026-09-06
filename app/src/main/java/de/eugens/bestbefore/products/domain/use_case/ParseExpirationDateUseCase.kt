package de.eugens.bestbefore.products.domain.use_case

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ParseExpirationDateUseCase @Inject constructor() {

    private val dateFormats = listOf(
        "dd.MM.yyyy",
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "d.M.yyyy",
        "yyyy/MM/dd",
        "MM/dd/yyyy",
        "yyyy.MM.dd",
        "d/M/yyyy"
    )

    operator fun invoke(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        val trimmedDate = dateStr.trim()
        for (format in dateFormats) {
            try {
                return LocalDate.parse(trimmedDate, DateTimeFormatter.ofPattern(format))
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }
}
