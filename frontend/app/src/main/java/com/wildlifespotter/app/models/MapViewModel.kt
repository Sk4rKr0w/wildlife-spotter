package com.wildlifespotter.app.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.FirebaseFirestore
import com.wildlifespotter.app.SpotLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MapViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private var loadJob: Job? = null

    var spots by mutableStateOf<List<SpotLocation>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun loadSpots(centerLat: Double, centerLng: Double, rangeKm: Float) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(300)
            isLoading = true
            errorMessage = null
            try {
                val center = GeoLocation(centerLat, centerLng)
                val radiusM = rangeKm * 1000.0
                val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusM)
                val seen = LinkedHashMap<String, SpotLocation>()

                bounds.forEach { b ->
                    val snapshot = db.collection("spots")
                        .orderBy("geohash")
                        .startAt(b.startHash)
                        .endAt(b.endHash)
                        .get()
                        .await()

                    snapshot.documents.forEach { doc ->
                        val lat = doc.getDouble("latitude") ?: return@forEach
                        val lng = doc.getDouble("longitude") ?: return@forEach
                        val distanceM = GeoFireUtils.getDistanceBetween(
                            GeoLocation(lat, lng),
                            center
                        )
                        if (distanceM > radiusM) return@forEach

                        val speciesRaw = doc.get("species")
                        val speciesLabel = when (speciesRaw) {
                            is String -> speciesRaw
                            is Map<*, *> -> speciesRaw["label"] as? String ?: "Unknown"
                            else -> "Unknown"
                        }
                        val spot = SpotLocation(
                            id = doc.id,
                            latitude = lat,
                            longitude = lng,
                            speciesLabel = speciesLabel.replaceFirstChar { it.uppercase() },
                            locationName = doc.getString("location_name") ?: "Unknown",
                            userId = doc.getString("user_id") ?: ""
                        )
                        seen[doc.id] = spot
                    }
                }

                spots = seen.values.toList()
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }
}
