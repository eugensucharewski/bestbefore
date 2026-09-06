package de.eugens.bestbefore.products.domain.use_case

import de.eugens.bestbefore.products.domain.model.ExpirationStatus
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.presentation.ProductFilter
import javax.inject.Inject

class FilterProductsUseCase @Inject constructor(
    private val getExpirationStatusUseCase: GetExpirationStatusUseCase
) {
    operator fun invoke(
        products: List<Product>,
        filter: ProductFilter,
        thresholdValue: Int
    ): List<Product> {
        return when (filter) {
            ProductFilter.ALL -> products
            ProductFilter.EXPIRED -> products.filter {
                getExpirationStatusUseCase(it, thresholdValue) == ExpirationStatus.EXPIRED
            }
            ProductFilter.EXPIRED_AND_UPCOMING -> products.filter {
                val status = getExpirationStatusUseCase(it, thresholdValue)
                status == ExpirationStatus.EXPIRED || status == ExpirationStatus.UPCOMING
            }
        }
    }
}
