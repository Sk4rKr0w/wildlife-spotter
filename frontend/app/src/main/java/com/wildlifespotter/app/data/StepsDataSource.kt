package com.wildlifespotter.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object StepsDataSource {

    data class StepLoadResult(
        val dailySteps: Int,
        val baselineTotal: Long,
        val totalSpots: Long
    )

    suspend fun loadInitial(userId: String, todayKey: String): StepLoadResult {
        val db = FirebaseFirestore.getInstance()
        val todayDoc = db.collection("users")
            .document(userId)
            .collection("steps")
            .document(todayKey)
            .get()
            .await()
        val daily = todayDoc.getLong("dailySteps")?.toInt() ?: 0

        val userDoc = db.collection("users")
            .document(userId)
            .get()
            .await()
        var total = userDoc.getLong("totalSteps") ?: 0L
        total -= daily.toLong()
        val totalSpots = userDoc.getLong("totalSpots") ?: 0L

        return StepLoadResult(dailySteps = daily, baselineTotal = total, totalSpots = totalSpots)
    }

    suspend fun sync(
        userId: String,
        todayKey: String,
        dailySteps: Int,
        totalSteps: Long
    ) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .document(userId)
            .collection("steps")
            .document(todayKey)
            .set(
                mapOf(
                    "dailySteps" to dailySteps.toLong(),
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
            .await()

        db.collection("users")
            .document(userId)
            .set(
                mapOf(
                    "totalSteps" to totalSteps,
                    "stepsUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }
}
