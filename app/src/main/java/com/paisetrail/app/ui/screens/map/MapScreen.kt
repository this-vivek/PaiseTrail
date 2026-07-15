package com.paisetrail.app.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.rememberCameraPositionState
import com.paisetrail.app.BuildConfig
import com.paisetrail.app.data.db.CategoryEntity
import com.paisetrail.app.ui.components.AmountText
import com.paisetrail.app.ui.components.CategoryDot
import com.paisetrail.app.ui.components.FancyChip
import com.paisetrail.app.ui.components.IconPillButton
import com.paisetrail.app.ui.components.MapClusterBubble
import com.paisetrail.app.ui.components.MapPin
import com.paisetrail.app.ui.components.PlaceLine
import com.paisetrail.app.ui.components.parseCategoryColor
import com.paisetrail.app.ui.components.paisaMapStyle
import com.paisetrail.app.ui.theme.PaisaSpacing
import com.paisetrail.app.ui.theme.PaisaTheme
import com.paisetrail.app.ui.theme.SheetShape

/** 100% map, floating controls, nothing else (spec 7.2/7.7). Trip-tagged pins are hidden by
 * default (each trip has its own mini-map, spec 7.4) — a "Trips" toggle reveals them here too.
 * The bottom-sheet-style pin detail and filter panel are lightweight boxes rather than full
 * Material bottom sheets, kept intentionally small given the rest of Phase 4's scope. */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(onNavigateToTransaction: (Long) -> Unit = {}, viewModel: MapViewModel = hiltViewModel()) {
    if (!BuildConfig.HAS_MAPS_API_KEY) {
        MissingApiKeyPlaceholder()
        return
    }

    val items by viewModel.clusterItems.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val lastLocation by viewModel.lastLocation.collectAsState()
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var showTripPins by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<TxnClusterItem?>(null) }
    var selectedCluster by remember { mutableStateOf<List<TxnClusterItem>?>(null) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var hasCenteredOnData by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // India's approximate geographic centroid, zoomed to show the whole country — a
        // reasonable default until the most recent transaction's location is known.
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 4.2f)
        }
        LaunchedEffect(lastLocation) {
            val (lat, lng) = lastLocation ?: return@LaunchedEffect
            if (hasCenteredOnData) return@LaunchedEffect
            hasCenteredOnData = true
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(LatLng(lat, lng), CITY_ZOOM)),
                durationMs = 900,
            )
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapStyleOptions = paisaMapStyle()),
        ) {
            Clustering(
                items = items,
                onClusterClick = { cluster ->
                    selectedCluster = cluster.items.toList()
                    true
                },
                onClusterItemClick = { item ->
                    selectedItem = item
                    true
                },
                clusterContent = { cluster -> MapClusterBubble(cluster.size, categorySlices(cluster)) },
                clusterItemContent = { item -> ClusterPin(item) },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(PaisaSpacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
        ) {
            IconPillButton(
                icon = if (showTripPins) Icons.Filled.Star else Icons.Outlined.Star,
                active = showTripPins,
                onClick = {
                    showTripPins = !showTripPins
                    viewModel.setShowTripPins(showTripPins)
                },
            )
            IconPillButton(
                icon = Icons.Outlined.FilterList,
                active = selectedCategoryId != null,
                onClick = { showFilterPanel = true },
            )
        }

        selectedItem?.let { item ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(PaisaTheme.colors.surfaceGlass, SheetShape)
                    .clickable { selectedItem = null }
                    .padding(PaisaSpacing.gutter),
            ) {
                Column {
                    Text(
                        text = item.txn.payeeNameRaw ?: item.txn.vpa ?: "Unknown",
                        style = PaisaTheme.typography.bodyBold,
                        color = PaisaTheme.colors.ink,
                    )
                    AmountText(amountPaise = item.txn.amountPaise, style = PaisaTheme.typography.amountM)
                    PlaceLine(placeText = item.txn.placeName ?: item.txn.locality)
                    Text(
                        text = "View transaction →",
                        style = PaisaTheme.typography.bodyBold,
                        color = PaisaTheme.colors.accent,
                        modifier = Modifier
                            .clickable {
                                selectedItem = null
                                onNavigateToTransaction(item.txn.id)
                            }
                            .padding(top = PaisaSpacing.tight),
                    )
                }
            }
        }

        selectedCluster?.let { clusterItems ->
            ClusterSheet(
                items = clusterItems,
                onDismiss = { selectedCluster = null },
                onPickTransaction = { txnId ->
                    selectedCluster = null
                    onNavigateToTransaction(txnId)
                },
            )
        }

        if (showFilterPanel) {
            FilterPanel(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onDismiss = { showFilterPanel = false },
                onPick = { categoryId ->
                    selectedCategoryId = categoryId
                    viewModel.setSelectedCategory(categoryId)
                    showFilterPanel = false
                },
            )
        }
    }
}

private const val CITY_ZOOM = 12f

@Composable
private fun ClusterPin(item: TxnClusterItem) {
    MapPin(item.txn.amountPaise, item.categoryColorHex, item.categoryEmoji, isTripTagged = item.txn.tripId != null)
}

/** Each represented category's color, weighted by how many pins of it are in the cluster — the
 * data behind [MapClusterBubble]'s proportional ring. */
private fun categorySlices(cluster: Cluster<TxnClusterItem>): List<Pair<Color, Float>> =
    cluster.items
        .groupBy { it.categoryColorHex }
        .map { (colorHex, items) -> parseCategoryColor(colorHex) to items.size.toFloat() }

/** Tapping a cluster bubble reveals exactly which transactions it's made of (spec 7.2) — a
 * scrollable list rather than forcing a zoom-in-and-guess, each row tappable straight through to
 * that transaction's detail screen. */
@Composable
private fun ClusterSheet(
    items: List<TxnClusterItem>,
    onDismiss: () -> Unit,
    onPickTransaction: (Long) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(onClick = onDismiss),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PaisaTheme.colors.surface1, SheetShape),
        ) {
            Column(modifier = Modifier.padding(PaisaSpacing.gutter)) {
                Text(
                    text = "${items.size} transactions here",
                    style = PaisaTheme.typography.label,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(bottom = PaisaSpacing.tight),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(items, key = { it.txn.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPickTransaction(item.txn.id) }
                                .padding(vertical = PaisaSpacing.tight),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryDot(item.categoryColorHex, item.categoryEmoji)
                                Column(modifier = Modifier.padding(start = PaisaSpacing.tight)) {
                                    Text(
                                        text = item.txn.payeeNameRaw ?: item.txn.vpa ?: "Unknown",
                                        style = PaisaTheme.typography.bodyBold,
                                        color = PaisaTheme.colors.ink,
                                    )
                                    PlaceLine(placeText = item.txn.placeName ?: item.txn.locality)
                                }
                            }
                            AmountText(amountPaise = item.txn.amountPaise, style = PaisaTheme.typography.amountM)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(onClick = onDismiss),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PaisaTheme.colors.surface1, SheetShape),
        ) {
            Column(modifier = Modifier.padding(PaisaSpacing.gutter)) {
                Text(
                    text = "Filter by category",
                    style = PaisaTheme.typography.label,
                    color = PaisaTheme.colors.inkMuted,
                    modifier = Modifier.padding(bottom = PaisaSpacing.tight),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(PaisaSpacing.tight),
                ) {
                    FancyChip(
                        label = "All",
                        emoji = null,
                        colorHex = null,
                        selected = selectedCategoryId == null,
                        onClick = { onPick(null) },
                    )
                    categories.forEach { category ->
                        FancyChip(
                            label = category.name,
                            emoji = category.emoji,
                            colorHex = category.colorHex,
                            selected = selectedCategoryId == category.id,
                            onClick = { onPick(category.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingApiKeyPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(PaisaSpacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Map needs a Google Maps API key",
                style = PaisaTheme.typography.body,
                color = PaisaTheme.colors.ink,
            )
            Text(
                text = "Add MAPS_API_KEY to local.properties to enable the map view",
                style = PaisaTheme.typography.caption,
                color = PaisaTheme.colors.inkMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
