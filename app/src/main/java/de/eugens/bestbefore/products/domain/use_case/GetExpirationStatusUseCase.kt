package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.model.ExpirationStatus
import de.eugens.bestbefore.products.domain.model.Product
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class GetExpirationStatusUseCase @Inject constructor(
    private val parseExpirationDateUseCase: ParseExpirationDateUseCase
) {
    operator fun invoke(product: Product, thresholdValue: Int, today: LocalDate = LocalDate.now()): ExpirationStatus {
        return invoke(product.expirationDate, thresholdValue, today)
    }

    operator fun invoke(expirationDate: String?, thresholdValue: Int, today: LocalDate = LocalDate.now()): ExpirationStatus {
        val date = parseExpirationDateUseCase(expirationDate) ?: return ExpirationStatus.UNKNOWN
        val daysUntil = ChronoUnit.DAYS.between(today, date)
        return when {
            daysUntil < 0 -> ExpirationStatus.EXPIRED
            daysUntil <= thresholdValue -> ExpirationStatus.UPCOMING
            else -> ExpirationStatus.FRESH
        }
    }
}
