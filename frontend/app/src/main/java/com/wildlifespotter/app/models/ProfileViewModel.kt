package com.wildlifespotter.app.models

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildlifespotter.app.data.ProfileDataSource
import kotlinx.coroutines.launch


data class ProfileUiState(
    val username: String = "",
    val email: String = "",
    val supportsPassword: Boolean = false,
    val isLoadingProfile: Boolean = true
)

class ProfileViewModel : ViewModel() {
    var uiState by mutableStateOf(ProfileUiState())
        private set

    fun loadProfile() {
        val user = ProfileDataSource.currentUser()
        if (user == null) {
            uiState = uiState.copy(isLoadingProfile = false)
            return
        }
        viewModelScope.launch {
            val result = ProfileDataSource.loadProfile(user)
            uiState = uiState.copy(
                username = result.username,
                email = result.email,
                supportsPassword = result.supportsPassword,
                isLoadingProfile = false
            )
        }
    }

    fun saveProfile(
        context: Context,
        username: String,
        email: String,
        changePassword: Boolean,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onResult: (ProfileDataSource.ProfileSaveResult) -> Unit
    ) {
        val user = ProfileDataSource.currentUser()
        if (user == null) return
        viewModelScope.launch {
            val result = ProfileDataSource.saveProfile(
                context = context,
                user = user,
                username = username,
                email = email,
                changePassword = changePassword,
                currentPassword = currentPassword,
                newPassword = newPassword,
                confirmPassword = confirmPassword
            )
            if (result.success) {
                uiState = uiState.copy(username = username, email = email)
            }
            onResult(result)
        }
    }
}
