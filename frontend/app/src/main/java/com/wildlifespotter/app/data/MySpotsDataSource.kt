package com.wildlifespotter.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.wildlifespotter.app.interfaces.RetrofitInstance
import com.wildlifespotter.app.models.UserSpot
import kotlinx.coroutines.tasks.await

object MySpotsDataSource {

    suspend fun loadUserSpotsPage(
        userId: String,
        lastDoc: DocumentSnapshot?,
        pageSize: Long
    ): Pair<List<UserSpot>, DocumentSnapshot?> {
        val db = FirebaseFirestore.getInstance()

        var query = db.collection("spots")
            .whereEqualTo("user_id", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(pageSize)

        if (lastDoc != null) {
            query = query.startAfter(lastDoc)
        }

        val snapshot = query.get().await()

        val spots = snapshot.documents.map { doc ->
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
                speciesLabel = speciesLabel.replaceFirstChar { it.uppercase() },
                speciesTaxonomy = taxonomyRaw,
                description = doc.getString("description") ?: "",
                locationName = doc.getString("location_name") ?: "Unknown location",
                imageId = doc.getString("image_id") ?: "",
                userId = userId,
                timestamp = doc.getTimestamp("timestamp"),
                dailySteps = doc.getLong("daily_steps") ?: 0L
            )
        }

        val newLast = snapshot.documents.lastOrNull()
        return spots to newLast
    }

    suspend fun deleteSpot(spot: UserSpot) {
        val db = FirebaseFirestore.getInstance()

        db.collection("spots").document(spot.id).delete().await()
        if (spot.userId.isNotBlank()) {
            db.collection("users").document(spot.userId)
                .update("totalSpots", FieldValue.increment(-1))
                .await()
        }
    }

    suspend fun restoreSpot(spot: UserSpot) {
        val db = FirebaseFirestore.getInstance()

        val restoreData = hashMapOf(
            "species" to mapOf(
                "label" to spot.speciesLabel,
                "taxonomy" to spot.speciesTaxonomy
            ),
            "description" to spot.description,
            "location_name" to spot.locationName,
            "image_id" to spot.imageId,
            "user_id" to spot.userId,
            "timestamp" to spot.timestamp
        )
        db.collection("spots")
            .document(spot.id)
            .set(restoreData)
            .await()

        if (spot.userId.isNotBlank()) {
            db.collection("users").document(spot.userId)
                .update("totalSpots", FieldValue.increment(1))
                .await()
        }
    }

    suspend fun updateDescription(spotId: String, newText: String) {
        val db = FirebaseFirestore.getInstance()

        db.collection("spots")
            .document(spotId)
            .update("description", newText)
            .await()
    }

    suspend fun deleteImage(imageId: String) {
        if (imageId.isBlank()) return
        RetrofitInstance.api.deleteImage(imageId)
    }
}
