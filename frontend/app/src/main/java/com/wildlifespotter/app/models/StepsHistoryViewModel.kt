package com.wildlifespotter.app.models

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.wildlifespotter.app.data.StepsHistoryDataSource
import kotlinx.coroutines.launch


data class StepsHistoryUiState(
    val historySteps: Map<String, Long> = emptyMap(),
    val isLoading: Boolean = true
)

class StepsHistoryViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    var uiState by mutableStateOf(StepsHistoryUiState())
        private set

    fun loadHistory() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            uiState = uiState.copy(isLoading = false)
            return
        }
        viewModelScope.launch {
            try {
                val map = StepsHistoryDataSource.loadHistory(userId)
                uiState = uiState.copy(historySteps = map, isLoading = false)
                Log.d("StepsHistory", "Loaded ${map.size} days with steps")
            } catch (e: Exception) {
                Log.e("StepsHistory", "Failed to load history", e)
                uiState = uiState.copy(isLoading = false)
            }
        }
    }
}
