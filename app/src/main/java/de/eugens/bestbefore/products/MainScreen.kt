package de.eugens.bestbefore.products

import de.eugens.bestbefore.products.domain.model.Product

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.eugens.bestbefore.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    products: List<ProductUiModel>,
    currentFilter: ProductFilter,
    onFilterChange: (ProductFilter) -> Unit,
    onProductClick: (Product) -> Unit,
    onAddClick: () -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onUndoDelete: (Product) -> Unit,
    onClearAll: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                actions = {
                    if (products.isNotEmpty() || currentFilter != ProductFilter.ALL) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.clear_all))
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { padding ->
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text(stringResource(R.string.clear_confirm_title)) },
                text = { Text(stringResource(R.string.clear_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        onClearAll()
                        showClearConfirm = false
                    }) { Text(stringResource(R.string.clear_confirm_button)) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        Column(modifier = Modifier.padding(padding)) {
            FilterChips(
                currentFilter = currentFilter,
                onFilterChange = onFilterChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty_list)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(products, key = { it.product.id }) { uiModel ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { it * 0.7f }
                        )

                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                onDeleteProduct(uiModel.product)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.deleted_message, uiModel.product.name),
                                        actionLabel = context.getString(R.string.undo),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onUndoDelete(uiModel.product)
                                    }
                                }
                            }
                        }

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                val color = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.EndToStart -> Color.Red
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        ) {
                            ProductItem(uiModel, onProductClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChips(
    currentFilter: ProductFilter,
    onFilterChange: (ProductFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProductFilter.entries.forEach { filter ->
            FilterChip(
                selected = currentFilter == filter,
                onClick = { onFilterChange(filter) },
                label = {
                    Text(
                        text = when (filter) {
                            ProductFilter.ALL -> stringResource(R.string.filter_all)
                            ProductFilter.EXPIRED -> stringResource(R.string.filter_expired)
                            ProductFilter.EXPIRED_AND_UPCOMING -> stringResource(R.string.filter_upcoming)
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun ProductItem(uiModel: ProductUiModel, onProductClick: (Product) -> Unit) {
    val product = uiModel.product
    val bitmap = remember(product.productImage) {
        product.productImage?.let {
            try {
                val decodedString = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            } catch (e: Exception) {
                Log.d("MainScreen", "decodeByteArray", e)
                null
            }
        }
    }

    val statusColor = when (uiModel.status) {
        ExpirationStatus.EXPIRED -> Color.Red
        ExpirationStatus.UPCOMING -> Color(0xFFFFC107)
        ExpirationStatus.FRESH -> Color.Green
        ExpirationStatus.UNKNOWN -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = {
                onProductClick.invoke(product)
            }),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = product.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = stringResource(R.string.expires_on, product.expirationDate))
                if (!product.productionDate.isNullOrEmpty()) {
                    Text(text = stringResource(R.string.production_date, product.productionDate), fontSize = 12.sp)
                }
            }
        }
    }
}
