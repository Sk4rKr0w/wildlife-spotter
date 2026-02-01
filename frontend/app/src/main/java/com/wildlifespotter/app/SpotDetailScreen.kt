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
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.firestore.FirebaseFirestore
import com.wildlifespotter.app.interfaces.RetrofitInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailScreen(
    spotId: String,
    onNavigateBack: () -> Unit,
    onSpotDeleted: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var spot by remember { mutableStateOf<UserSpot?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Desc Edit States
    var showEditDialog by remember { mutableStateOf(false) }
    var editedDescription by remember { mutableStateOf("") }
    
    // Delete confirm stats
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Spot Data
    LaunchedEffect(spotId) {
        try {
            val doc = db.collection("spots").document(spotId).get().await()
            if (!doc.exists()) {
                error = "Spot not found"
                isLoading = false
                return@LaunchedEffect
            }
            
            val speciesRaw = doc.get("species")
            val speciesLabel = when (speciesRaw) {
                is String -> speciesRaw
                is Map<*, *> -> (speciesRaw["label"] as? String) ?: "Unknown species"
                else -> "Unknown species"
            }
            val taxonomyRaw = when (speciesRaw) {
                is Map<*, *> -> (speciesRaw["taxonomy"] as? Map<String, Any?>) ?: emptyMap()
                else -> emptyMap()
            }
            
            spot = UserSpot(
                id = doc.id,
                speciesLabel = speciesLabel.replaceFirstChar { it.uppercase() },
                speciesTaxonomy = taxonomyRaw,
                description = doc.getString("description") ?: "",
                locationName = doc.getString("location_name") ?: "Unknown location",
                imageId = doc.getString("image_id") ?: "",
                userId = doc.getString("user_id") ?: "",
                timestamp = doc.getTimestamp("timestamp")
            )
            editedDescription = spot?.description ?: ""
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        } finally {
            isLoading = false
        }
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
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit description",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete spot",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            spot != null -> {
                SpotDetailContent(
                    spot = spot!!,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
    
    if (showEditDialog && spot != null) {
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
                            try {
                                db.collection("spots")
                                    .document(spotId)
                                    .update("description", editedDescription.trim())
                                    .await()
                                
                                spot = spot?.copy(description = editedDescription.trim())
                                showEditDialog = false
                                snackbarHostState.showSnackbar("Description updated")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    "Error: ${e.message ?: "Unknown error"}"
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
                            try {
                                db.collection("spots")
                                    .document(spotId)
                                    .delete()
                                    .await()

                                if (spot!!.imageId.isNotBlank()) {
                                    try {
                                        RetrofitInstance.api.deleteImage(spot!!.imageId)
                                    } catch (e: Exception) {
                                        // Ignore error, we can still proceed
                                    }
                                }
                                
                                showDeleteDialog = false
                                snackbarHostState.showSnackbar("Spot deleted")
                                onSpotDeleted()
                            } catch (e: Exception) {
                                showDeleteDialog = false
                                snackbarHostState.showSnackbar(
                                    "Error: ${e.message ?: "Unknown error"}"
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
