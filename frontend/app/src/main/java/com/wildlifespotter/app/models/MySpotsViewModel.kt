package com.wildlifespotter.app.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.wildlifespotter.app.data.MySpotsDataSource
import kotlinx.coroutines.launch


data class MySpotsUiState(
    val spots: List<UserSpot> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null
)

class MySpotsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val pageSize = 5L
    private var lastDoc: DocumentSnapshot? = null

    var uiState by mutableStateOf(MySpotsUiState())
        private set

    fun loadSpots() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            uiState = uiState.copy(isLoading = false, error = "Not logged in")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, endReached = false)
            try {
                val (spots, newLast) = MySpotsDataSource.loadUserSpotsPage(
                    userId = userId,
                    lastDoc = null,
                    pageSize = pageSize
                )
                lastDoc = newLast
                uiState = uiState.copy(
                    spots = spots,
                    isLoading = false,
                    endReached = spots.isEmpty()
                )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun loadMore() {
        val userId = auth.currentUser?.uid ?: return
        if (uiState.isLoadingMore || uiState.endReached || uiState.isLoading) return
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingMore = true, error = null)
            try {
                val (spots, newLast) = MySpotsDataSource.loadUserSpotsPage(
                    userId = userId,
                    lastDoc = lastDoc,
                    pageSize = pageSize
                )
                if (spots.isEmpty()) {
                    uiState = uiState.copy(isLoadingMore = false, endReached = true)
                    return@launch
                }
                lastDoc = newLast
                uiState = uiState.copy(
                    spots = uiState.spots + spots,
                    isLoadingMore = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(isLoadingMore = false, error = e.message ?: "Load failed")
            }
        }
    }

    suspend fun deleteSpot(spot: UserSpot): Boolean {
        val previous = uiState.spots
        uiState = uiState.copy(spots = previous.filterNot { it.id == spot.id })
        return try {
            MySpotsDataSource.deleteSpot(spot)
            true
        } catch (e: Exception) {
            uiState = uiState.copy(
                spots = (listOf(spot) + uiState.spots)
                    .sortedByDescending { it.timestamp?.seconds ?: 0L },
                error = e.message ?: "Delete failed"
            )
            false
        }
    }

    suspend fun undoDelete(spot: UserSpot): Boolean {
        return try {
            MySpotsDataSource.restoreSpot(spot)
            uiState = uiState.copy(
                spots = (listOf(spot) + uiState.spots)
                    .sortedByDescending { it.timestamp?.seconds ?: 0L }
            )
            true
        } catch (e: Exception) {
            uiState = uiState.copy(error = e.message ?: "Undo failed")
            false
        }
    }

    suspend fun updateDescription(spot: UserSpot, newText: String): Boolean {
        return try {
            MySpotsDataSource.updateDescription(spot.id, newText)
            uiState = uiState.copy(
                spots = uiState.spots.map {
                    if (it.id == spot.id) it.copy(description = newText) else it
                }
            )
            true
        } catch (e: Exception) {
            uiState = uiState.copy(error = e.message ?: "Update failed")
            false
        }
    }

    suspend fun finalizeDeleteImage(spot: UserSpot) {
        try {
            MySpotsDataSource.deleteImage(spot.imageId)
        } catch (_: Exception) {
        }
    }
}
