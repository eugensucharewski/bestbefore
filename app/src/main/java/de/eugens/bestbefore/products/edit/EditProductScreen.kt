package de.eugens.bestbefore.products.edit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.eugens.bestbefore.R
import de.eugens.bestbefore.products.domain.model.Product

@Composable
fun EditProductScreen(
    viewModel: EditProductViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_text, uiState.product.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProduct(onBack)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    EditProductContent(
        product = uiState.product,
        productBitmap = uiState.productBitmap,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onExpirationDateChange = viewModel::onExpirationDateChange,
        onSave = {
            viewModel.saveProduct(onBack)
        },
        onDeleteClick = { showDeleteConfirm = true }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductContent(
    product: Product,
    productBitmap: Bitmap? = null,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onExpirationDateChange: (String) -> Unit,
    onSave: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_product)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                    TextButton(onClick = onSave) {
                        Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            productBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop
                )
            }

            OutlinedTextField(
                value = product.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.product_name)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = product.expirationDate,
                onValueChange = onExpirationDateChange,
                label = { Text(stringResource(R.string.expiration_date)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("YYYY-MM-DD") }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditProductScreenPreview() {
    EditProductContent(
        product = Product(
            name = "Test Product",
            expirationDate = "2023-12-31"
        ),
        productBitmap = null,
        onBack = {},
        onNameChange = {},
        onExpirationDateChange = {},
        onSave = {},
        onDeleteClick = {}
    )
}
