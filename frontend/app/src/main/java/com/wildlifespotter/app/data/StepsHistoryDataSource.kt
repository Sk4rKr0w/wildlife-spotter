package com.wildlifespotter.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object StepsHistoryDataSource {

    suspend fun loadHistory(userId: String): Map<String, Long> {
        val db = FirebaseFirestore.getInstance()
        val snapshot = db.collection("users")
            .document(userId)
            .collection("steps")
            .get()
            .await()

        val map = mutableMapOf<String, Long>()
        for (doc in snapshot.documents) {
            val steps = doc.getLong("dailySteps") ?: 0L
            if (steps > 0) {
                map[doc.id] = steps
            }
        }
        return map
    }
}
