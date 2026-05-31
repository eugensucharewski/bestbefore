package de.eugens.bestbefore.products.domain.repository

import de.eugens.bestbefore.products.domain.model.ExpirationInfo
import de.eugens.bestbefore.products.domain.model.Product
import de.eugens.bestbefore.products.domain.model.ScannedItem

interface ProductRepository {
    suspend fun getProducts(): List<Product>
    suspend fun deleteProduct(productId: String)
    suspend fun clearAllProducts()
    suspend fun addProduct(product: Product)
    suspend fun updateProduct(product: Product)
    suspend fun saveAnalysisResults(results: List<ExpirationInfo>, items: List<ScannedItem>)
}
