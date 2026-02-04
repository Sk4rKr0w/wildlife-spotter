package com.wildlifespotter.app.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.wildlifespotter.app.data.SpotDetailDataSource
import kotlinx.coroutines.launch


data class SpotDetailUiState(
    val spot: UserSpot? = null,
    val ownerName: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUserId: String? = null
)

class SpotDetailViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    var uiState by mutableStateOf(
        SpotDetailUiState(currentUserId = auth.currentUser?.uid)
    )
        private set

    fun loadSpot(spotId: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val result = SpotDetailDataSource.loadSpot(spotId)
                if (result.spot == null) {
                    uiState = uiState.copy(isLoading = false, error = "Spot not found")
                } else {
                    uiState = uiState.copy(
                        spot = result.spot,
                        ownerName = result.ownerName,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    suspend fun updateDescription(spotId: String, newText: String): Boolean {
        return try {
            SpotDetailDataSource.updateDescription(spotId, newText)
            uiState = uiState.copy(spot = uiState.spot?.copy(description = newText))
            true
        } catch (e: Exception) {
            uiState = uiState.copy(error = e.message ?: "Unknown error")
            false
        }
    }

    suspend fun deleteSpot(): Boolean {
        val spot = uiState.spot ?: return false
        return try {
            SpotDetailDataSource.deleteSpot(spot)
            true
        } catch (e: Exception) {
            uiState = uiState.copy(error = e.message ?: "Unknown error")
            false
        }
    }
}
