package de.eugens.bestbefore.edit_product.presentation

import de.eugens.bestbefore.products.domain.model.Product

data class EditProductUiState(
    val product: Product = Product(),
    val imagePath: String? = null,
    val error: String? = null
)
