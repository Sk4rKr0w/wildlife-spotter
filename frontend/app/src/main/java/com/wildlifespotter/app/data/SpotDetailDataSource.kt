package com.wildlifespotter.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.wildlifespotter.app.interfaces.RetrofitInstance
import com.wildlifespotter.app.models.UserSpot
import kotlinx.coroutines.tasks.await

object SpotDetailDataSource {

    data class SpotDetailResult(
        val spot: UserSpot?,
        val ownerName: String?
    )

    suspend fun loadSpot(spotId: String): SpotDetailResult {
        val db = FirebaseFirestore.getInstance()
        val doc = db.collection("spots").document(spotId).get().await()
        if (!doc.exists()) return SpotDetailResult(null, null)

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

        val spot = UserSpot(
            id = doc.id,
            speciesLabel = speciesLabel.replaceFirstChar { it.uppercase() },
            speciesTaxonomy = taxonomyRaw,
            description = doc.getString("description") ?: "",
            locationName = doc.getString("location_name") ?: "Unknown location",
            imageId = doc.getString("image_id") ?: "",
            userId = doc.getString("user_id") ?: "",
            timestamp = doc.getTimestamp("timestamp"),
            dailySteps = doc.getLong("daily_steps") ?: 0L
        )

        val ownerName = if (spot.userId.isNotBlank()) {
            val ownerDoc = db.collection("users").document(spot.userId).get().await()
            ownerDoc.getString("username")
        } else {
            null
        }

        return SpotDetailResult(spot, ownerName)
    }

    suspend fun updateDescription(spotId: String, newText: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("spots")
            .document(spotId)
            .update("description", newText)
            .await()
    }

    suspend fun deleteSpot(spot: UserSpot) {
        val db = FirebaseFirestore.getInstance()
        db.collection("spots")
            .document(spot.id)
            .delete()
            .await()

        if (spot.imageId.isNotBlank()) {
            try {
                RetrofitInstance.api.deleteImage(spot.imageId)
            } catch (_: Exception) {
            }
        }

        if (spot.userId.isNotBlank()) {
            db.collection("users").document(spot.userId)
                .update("totalSpots", FieldValue.increment(-1))
                .await()
        }
    }
}
