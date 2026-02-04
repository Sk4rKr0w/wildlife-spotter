package com.wildlifespotter.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import com.wildlifespotter.app.interfaces.RetrofitInstance
import com.wildlifespotter.app.ui.components.AnimatedWaveBackground
import androidx.compose.material3.SnackbarDuration
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildlifespotter.app.models.MySpotsViewModel
import com.wildlifespotter.app.models.UserSpot
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun MySpotsScreen(
    navBackStackEntry: NavBackStackEntry,
    onNavigateToSpotDetail: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resetTokens = remember { mutableStateMapOf<String, Int>() }
    var editingSpot by remember { mutableStateOf<UserSpot?>(null) }
    var editedDescription by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val viewModel: MySpotsViewModel = viewModel()
    val uiState = viewModel.uiState
    val spotCount by rememberUpdatedState(newValue = uiState.spots.size)
    val loadMoreAllowed by rememberUpdatedState(
        newValue = !uiState.isLoadingMore && !uiState.endReached && !uiState.isLoading
    )

    LaunchedEffect(Unit) {
        viewModel.loadSpots()
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }
            .distinctUntilChanged()
            .collectLatest { lastVisible ->
                val threshold = (spotCount - 2).coerceAtLeast(0)
                if (lastVisible >= threshold && loadMoreAllowed) {
                    viewModel.loadMore()
                }
            }
    }

    LaunchedEffect(navBackStackEntry) {
        val spotDeleted = navBackStackEntry.savedStateHandle.get<Boolean>("spot_deleted")
        if (spotDeleted == true) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Spot deleted",
                    duration = SnackbarDuration.Short
                )
            }
            navBackStackEntry.savedStateHandle.remove<Boolean>("spot_deleted")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->

        AnimatedWaveBackground(
            primaryColor = Color(0xFF4CAF50),
            secondaryColor = Color(0xFF2EA333)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {



            Text(
                text = "My Spots",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                uiState.spots.isEmpty() -> {
                    Text(
                        text = "No spots yet",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(uiState.spots, key = { _, spot -> spot.id }) { _, spot ->
                            SwipeToDeleteSpot(
                                spot = spot,
                                onClick = { onNavigateToSpotDetail(spot.id) },
                                resetToken = resetTokens[spot.id] ?: 0,
                                onEdit = {
                                    editingSpot = spot
                                    editedDescription = spot.description
                                },
                                onDelete = {
                                    val removedSpot = spot
                                    scope.launch {
                                        val deleted = viewModel.deleteSpot(removedSpot)
                                        if (!deleted) return@launch

                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Spot deleted",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            val restored = viewModel.undoDelete(removedSpot)
                                            if (restored) {
                                                resetTokens[removedSpot.id] =
                                                    (resetTokens[removedSpot.id] ?: 0) + 1
                                            }
                                        } else {
                                            viewModel.finalizeDeleteImage(removedSpot)
                                        }
                                    }
                                }
                            )
                        }
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (editingSpot != null) {
            AlertDialog(
                onDismissRequest = { editingSpot = null },
                title = { Text("Edit description") },
                text = {
                    TextField(
                        value = editedDescription,
                        onValueChange = { editedDescription = it }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val spot = editingSpot ?: return@TextButton
                            val newText = editedDescription.trim()
                            editingSpot = null
                            scope.launch {
                                viewModel.updateDescription(spot, newText)
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingSpot = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteSpot(
    spot: UserSpot,
    resetToken: Int,
    onClick: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    key(resetToken) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onDelete()
                    true
                } else if (value == SwipeToDismissBoxValue.StartToEnd) {
                    onEdit()
                    false
                } else {
                    false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = true,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    }
                ) {
                    if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        ) {
            SpotCard(spot = spot, onClick = onClick)
        }
    }
}

@Composable
fun SpotCard(
    spot: UserSpot,
    onClick: () -> Unit = {}
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val dateText = spot.timestamp?.toDate()?.let { formatter.format(it) } ?: "Unknown date"

    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (spot.imageId.isNotBlank()) {
                val imageUrl = RetrofitInstance.imageUrl(spot.imageId)
                val painter = rememberAsyncImagePainter(imageUrl)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (painter.state is AsyncImagePainter.State.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
            Text(
                text = spot.speciesLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (spot.description.isNotBlank()) {
                Text(
                    text = spot.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                text = spot.locationName,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
