package de.eugens.bestbefore.products.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.eugens.bestbefore.R
import de.eugens.bestbefore.products.domain.model.ExpirationStatus
import de.eugens.bestbefore.products.domain.model.Product

private val UpcomingWarningColor = Color(0xFFFFC107)

private fun ExpirationStatus.toColor(): Color = when (this) {
    ExpirationStatus.EXPIRED -> Color.Red
    ExpirationStatus.UPCOMING -> UpcomingWarningColor
    ExpirationStatus.FRESH -> Color.Green
    ExpirationStatus.UNKNOWN -> Color.Gray
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    products: List<ProductUiModel>,
    currentFilter: ProductFilter,
    selectedProductIds: Set<String>,
    isLoading: Boolean,
    onFilterChange: (ProductFilter) -> Unit,
    onProductClick: (Product) -> Unit,
    onAddClick: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isSelectionMode = selectedProductIds.isNotEmpty()
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selected_count, selectedProductIds.size))
                    } else {
                        Text(stringResource(R.string.main_title))
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { showDeleteSelectedConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
                        }
                    } else {
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { padding ->
        if (showDeleteSelectedConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteSelectedConfirm = false },
                title = { Text(stringResource(R.string.delete_selected_confirm_title)) },
                text = { Text(stringResource(R.string.delete_selected_confirm_text, selectedProductIds.size)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteSelected()
                            showDeleteSelectedConfirm = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                        Text(stringResource(R.string.cancel))
                    }
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

            if (isLoading) {
                ShimmerLoadingList(modifier = Modifier.fillMaxSize())
            } else if (products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.empty_list))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(products, key = { it.product.id }) { uiModel ->
                        ProductItem(
                            uiModel = uiModel,
                            isSelected = selectedProductIds.contains(uiModel.product.id),
                            isSelectionMode = isSelectionMode,
                            onProductClick = onProductClick,
                            onToggleSelection = onToggleSelection
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShimmerLoadingList(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerColors = remember {
        listOf(
            Color.LightGray.copy(alpha = 0.6f),
            Color.LightGray.copy(alpha = 0.2f),
            Color.LightGray.copy(alpha = 0.6f),
        )
    }

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    LazyColumn(modifier = modifier) {
        items(10) {
            ShimmerProductItem(brush = brush)
        }
    }
}

@Composable
private fun ShimmerProductItem(brush: Brush) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(20.dp)
                        .background(brush)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .background(brush)
                )
            }
        }
    }
}

@Composable
private fun FilterChips(
    currentFilter: ProductFilter,
    onFilterChange: (ProductFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductItem(
    uiModel: ProductUiModel,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onProductClick: (Product) -> Unit,
    onToggleSelection: (String) -> Unit
) {
    val product = uiModel.product
    val haptic = LocalHapticFeedback.current

    val onClick = remember(isSelectionMode, product.id, onToggleSelection, onProductClick) {
        {
            if (isSelectionMode) {
                onToggleSelection(product.id)
            } else {
                onProductClick(product)
            }
        }
    }

    val onLongClick = remember(product.id, onToggleSelection, haptic) {
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggleSelection(product.id)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection(product.id) }
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(uiModel.status.toColor(), CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            if (product.hasImage) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (!uiModel.imagePath.isNullOrEmpty()) {
                        AsyncImage(
                            model = uiModel.imagePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.expires_on, uiModel.formattedExpirationDate),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!uiModel.formattedProductionDate.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.production_date, uiModel.formattedProductionDate),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
