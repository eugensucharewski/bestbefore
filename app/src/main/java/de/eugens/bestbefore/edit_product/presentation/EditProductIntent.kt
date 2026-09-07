package de.eugens.bestbefore.edit_product.presentation

import de.eugens.bestbefore.products.domain.model.Product

sealed interface EditProductIntent {
    data class SetProduct(val product: Product, val initialImagePath: String? = null) : EditProductIntent
    data class ChangeName(val newName: String) : EditProductIntent
    data class ChangeExpirationDate(val newDate: String) : EditProductIntent
    data class SaveProduct(val onSuccess: () -> Unit) : EditProductIntent
    data class DeleteProduct(val onSuccess: () -> Unit) : EditProductIntent
}
