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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import com.wildlifespotter.app.interfaces.RetrofitInstance
import com.wildlifespotter.app.ui.components.AnimatedWaveBackground

data class UserSpot(
    val id: String,
    val speciesLabel: String,
    val speciesTaxonomy: Map<String, Any?>,
    val description: String,
    val locationName: String,
    val imageId: String,
    val userId: String,
    val timestamp: Timestamp?
)

@Composable
fun MySpotsScreen(
    onNavigateToSpotDetail: (String) -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resetTokens = remember { mutableStateMapOf<String, Int>() }
    var editingSpot by remember { mutableStateOf<UserSpot?>(null) }
    var editedDescription by remember { mutableStateOf("") }

    var spots by remember { mutableStateOf<List<UserSpot>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            error = "Not logged in"
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val snapshot = db.collection("spots")
                .whereEqualTo("user_id", userId)
                .get()
                .await()

            spots = snapshot.documents.map { doc ->
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
                UserSpot(
                    id = doc.id,
                    speciesLabel = speciesLabel.replaceFirstChar { it.uppercase() },                    speciesTaxonomy = taxonomyRaw,
                    description = doc.getString("description") ?: "",
                    locationName = doc.getString("location_name") ?: "Unknown location",
                    imageId = doc.getString("image_id") ?: "",
                    userId = userId,
                    timestamp = doc.getTimestamp("timestamp")
                )
            }.sortedByDescending { it.timestamp?.seconds ?: 0L }
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
        } finally {
            isLoading = false
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
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(
                        text = error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                spots.isEmpty() -> {
                    Text(
                        text = "No spots yet",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(spots, key = { it.id }) { spot ->
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
                                    spots = spots.filterNot { it.id == removedSpot.id }
                                    scope.launch {
                                        try {
                                            db.collection("spots").document(removedSpot.id).delete().await()
                                        } catch (e: Exception) {
                                            error = e.message ?: "Delete failed"
                                            spots = (listOf(removedSpot) + spots)
                                                .sortedByDescending { it.timestamp?.seconds ?: 0L }
                                            return@launch
                                        }

                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Spot deleted",
                                            actionLabel = "Undo"
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            val restoreData = hashMapOf(
                                                "species" to mapOf(
                                                    "label" to removedSpot.speciesLabel,
                                                    "taxonomy" to removedSpot.speciesTaxonomy
                                                ),
                                                "description" to removedSpot.description,
                                                "location_name" to removedSpot.locationName,
                                                "image_id" to removedSpot.imageId,
                                                "user_id" to removedSpot.userId,
                                                "timestamp" to removedSpot.timestamp
                                            )
                                            try {
                                                db.collection("spots")
                                                    .document(removedSpot.id)
                                                    .set(restoreData)
                                                    .await()
                                                spots = (listOf(removedSpot) + spots)
                                                    .sortedByDescending { it.timestamp?.seconds ?: 0L }
                                                resetTokens[removedSpot.id] =
                                                    (resetTokens[removedSpot.id] ?: 0) + 1
                                            } catch (e: Exception) {
                                                error = e.message ?: "Undo failed"
                                            }
                                        }
                                    }
                                }
                            )
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
                                try {
                                    db.collection("spots")
                                        .document(spot.id)
                                        .update("description", newText)
                                        .await()
                                    spots = spots.map {
                                        if (it.id == spot.id) it.copy(description = newText) else it
                                    }
                                } catch (e: Exception) {
                                    error = e.message ?: "Update failed"
                                }
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
                Image(
                    painter = rememberAsyncImagePainter(imageUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(bottom = 12.dp),
                    contentScale = ContentScale.Crop
                )
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
