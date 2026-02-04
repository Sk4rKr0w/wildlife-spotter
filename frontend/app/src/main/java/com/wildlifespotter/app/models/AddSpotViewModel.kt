package com.wildlifespotter.app.models

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wildlifespotter.app.data.AddSpotDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class AddSpotEvent {
    data class Message(val message: String) : AddSpotEvent()
}

data class AddSpotUiState(
    val selectedImageUri: Uri? = null,
    val compressedImage: ByteArray? = null,
    val description: String = "",
    val isLoading: Boolean = false,
    val isAnalyzingImage: Boolean = false,
    val locationName: String = "Getting location...",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationAvailable: Boolean = true,
    val showLocationDialog: Boolean = false
)

class AddSpotViewModel : ViewModel() {
    var uiState by mutableStateOf(AddSpotUiState())
        private set

    private val _events = MutableSharedFlow<AddSpotEvent>()
    val events = _events.asSharedFlow()

    private var locationJob: Job? = null

    fun updateDescription(value: String) {
        uiState = uiState.copy(description = value)
    }

    fun onPhotoPicked(context: Context, uri: Uri) {
        uiState = uiState.copy(selectedImageUri = uri, isAnalyzingImage = true)
        viewModelScope.launch {
            val compressed = AddSpotDataSource.compressImage(context, uri)
            handleCompressedImage(compressed)
        }
    }

    fun onCameraCaptured(bitmap: Bitmap) {
        uiState = uiState.copy(selectedImageUri = null, isAnalyzingImage = true)
        val compressed = AddSpotDataSource.compressBitmap(bitmap)
        handleCompressedImage(compressed)
    }

    private fun handleCompressedImage(compressed: ByteArray?) {
        if (compressed == null) {
            uiState = uiState.copy(isAnalyzingImage = false)
            return
        }
        uiState = uiState.copy(compressedImage = compressed)
        viewModelScope.launch {
            delay(500)
            uiState = uiState.copy(isAnalyzingImage = false)
        }
    }

    fun startLocationUpdates(context: Context) {
        if (locationJob != null) return
        locationJob = viewModelScope.launch {
            while (isActive) {
                val location = AddSpotDataSource.getFastLocation(context)
                uiState = uiState.copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    locationName = location.name,
                    locationAvailable = location.isValid,
                    showLocationDialog = !location.isValid
                )
                delay(2000)
            }
        }
    }

    fun submit(azimuth: Float) {
        val bytes = uiState.compressedImage ?: return
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true)
            val result = AddSpotDataSource.uploadSpotWithBytes(
                bytes = bytes,
                species = "",
                description = uiState.description,
                latitude = uiState.latitude,
                longitude = uiState.longitude,
                locationName = uiState.locationName,
                azimuth = azimuth
            )
            uiState = uiState.copy(isLoading = false)
            if (result.success) {
                uiState = uiState.copy(
                    description = "",
                    compressedImage = null,
                    selectedImageUri = null,
                    locationName = "Getting location..."
                )
            }
            _events.emit(AddSpotEvent.Message(result.message))
        }
    }

    override fun onCleared() {
        locationJob?.cancel()
        super.onCleared()
    }
}
