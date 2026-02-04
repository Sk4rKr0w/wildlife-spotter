package com.wildlifespotter.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.wildlifespotter.app.interfaces.RetrofitInstance
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import com.wildlifespotter.app.models.SpotDetailViewModel
import com.wildlifespotter.app.models.UserSpot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailScreen(
    spotId: String,
    onNavigateBack: () -> Unit,
    onSpotDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val viewModel: SpotDetailViewModel = viewModel()
    val uiState = viewModel.uiState
    
    // Desc Edit States
    var showEditDialog by remember { mutableStateOf(false) }
    var editedDescription by remember { mutableStateOf("") }
    
    // Delete confirm stats
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Spot Data
    LaunchedEffect(spotId) {
        viewModel.loadSpot(spotId)
    }

    LaunchedEffect(uiState.spot?.description) {
        editedDescription = uiState.spot?.description ?: ""
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spot Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    val isOwner = uiState.spot != null &&
                        uiState.currentUserId != null &&
                        uiState.spot!!.userId == uiState.currentUserId
                    if (isOwner) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit description",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (isOwner) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete spot",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            uiState.spot != null -> {
                SpotDetailContent(
                    spot = uiState.spot!!,
                    ownerName = uiState.ownerName,
                    isOwnSpot = uiState.currentUserId != null &&
                        uiState.currentUserId == uiState.spot!!.userId,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
    
    if (showEditDialog && uiState.spot != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Description") },
            text = {
                TextField(
                    value = editedDescription,
                    onValueChange = { editedDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = viewModel.updateDescription(
                                spotId,
                                editedDescription.trim()
                            )
                            if (ok) {
                                showEditDialog = false
                                snackbarHostState.showSnackbar("Description updated")
                            } else {
                                snackbarHostState.showSnackbar(
                                    "Error: ${uiState.error ?: "Unknown error"}"
                                )
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Spot") },
            text = { Text("Are you sure you want to delete this spot? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val deleted = viewModel.deleteSpot()
                            if (deleted) {
                                showDeleteDialog = false
                                onSpotDeleted()
                            } else {
                                showDeleteDialog = false
                                snackbarHostState.showSnackbar(
                                    message = "Error: ${uiState.error ?: "Unknown error"}",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SpotDetailContent(
    spot: UserSpot,
    ownerName: String?,
    isOwnSpot: Boolean,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.getDefault()) }
    val dateText = spot.timestamp?.toDate()?.let { formatter.format(it) } ?: "Unknown date"
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (spot.imageId.isNotBlank()) {
            val imageUrl = RetrofitInstance.imageUrl(spot.imageId)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(imageUrl),
                    contentDescription = "Spot image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
        
        DetailSection(title = "Species") {
            Text(
                text = spot.speciesLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        DetailSection(title = "Spotted by") {
            Text(
                text = if (isOwnSpot) "🌿 Your spot" else (ownerName ?: "Unknown user"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isOwnSpot) FontWeight.SemiBold else FontWeight.Normal
            )
        }
        
        if (spot.description.isNotBlank()) {
            DetailSection(title = "Description") {
                Text(
                    text = spot.description,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        DetailSection(title = "Date & Time") {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (spot.dailySteps > 0L) {
            DetailSection(title = "Daily Steps at Spot") {
                Text(
                    text = "${spot.dailySteps} steps",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        DetailSection(title = "Location") {
            Text(
                text = spot.locationName,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        
        if (spot.speciesTaxonomy.isNotEmpty()) {
            DetailSection(title = "Taxonomy") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    spot.speciesTaxonomy.forEach { (key, value) ->
                        if (key == "id") return@forEach
                        if (value != null && value.toString().isNotBlank()) {
                            TaxonomyItem(
                                label = key.replaceFirstChar { it.uppercase() },
                                value = value.toString().replaceFirstChar { it.uppercase() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
fun TaxonomyItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}
