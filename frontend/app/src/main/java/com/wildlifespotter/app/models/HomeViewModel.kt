package com.wildlifespotter.app.models

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.wildlifespotter.app.data.StepsDataSource
import kotlinx.coroutines.launch


data class HomeUiState(
    val dailySteps: Int = 0,
    val totalSteps: Long = 0L,
    val isLoadingSteps: Boolean = true
)

class HomeViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private var baselineTotal = 0L

    var uiState by mutableStateOf(HomeUiState())
        private set

    fun initialize(todayKey: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            uiState = uiState.copy(isLoadingSteps = false)
            return
        }
        viewModelScope.launch {
            try {
                val result = StepsDataSource.loadInitial(userId, todayKey)
                baselineTotal = result.baselineTotal
                uiState = uiState.copy(
                    dailySteps = result.dailySteps,
                    totalSteps = baselineTotal + result.dailySteps.toLong(),
                    isLoadingSteps = false
                )
                Log.d("StepCounter", "Initialized: daily=${result.dailySteps}, baseline=$baselineTotal")
            } catch (e: Exception) {
                Log.e("StepCounter", "Failed to initialize", e)
                uiState = uiState.copy(isLoadingSteps = false)
            }
        }
    }

    fun onStepDetected(todayKey: String) {
        val userId = auth.currentUser?.uid ?: return
        val newDaily = uiState.dailySteps + 1
        val newTotal = baselineTotal + newDaily.toLong()
        uiState = uiState.copy(dailySteps = newDaily, totalSteps = newTotal)
        if (newDaily > 0 && newDaily % 10 == 0) {
            viewModelScope.launch {
                try {
                    StepsDataSource.sync(userId, todayKey, newDaily, newTotal)
                    Log.d("StepCounter", "Synced: daily=$newDaily, total=$newTotal")
                } catch (e: Exception) {
                    Log.e("StepCounter", "Failed to sync", e)
                }
            }
        }
    }
}
