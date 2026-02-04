package com.wildlifespotter.app.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.wildlifespotter.app.models.RankingUser
import kotlinx.coroutines.tasks.await

object RankingsDataSource {
    private val db = FirebaseFirestore.getInstance()

    data class PageResult(
        val users: List<RankingUser>,
        val lastCursor: DocumentSnapshot?
    )

    data class SearchBatchResult(
        val users: List<RankingUser>,
        val nextCursor: DocumentSnapshot?,
        val isDone: Boolean,
        val fetchedCount: Int
    )

    suspend fun loadPage(startAfter: DocumentSnapshot?): PageResult {
        var query = db.collection("users")
            .orderBy("totalSpots", Query.Direction.DESCENDING)
            .limit(10)
        if (startAfter != null) query = query.startAfter(startAfter)
        val snap = query.get().await()
        val list = snap.documents.map { doc ->
            RankingUser(
                id = doc.id,
                username = doc.getString("username") ?: "Unknown",
                spots = doc.getLong("totalSpots") ?: 0L,
                steps = doc.getLong("totalSteps") ?: 0L
            )
        }
        return PageResult(list, snap.documents.lastOrNull())
    }

    suspend fun computeRank(spots: Long): Int {
        val higherSnap = db.collection("users")
            .whereGreaterThan("totalSpots", spots)
            .get()
            .await()
        return higherSnap.size() + 1
    }

    suspend fun fetchSearchBatch(
        query: String,
        cursor: DocumentSnapshot?
    ): SearchBatchResult {
        var q = db.collection("users")
            .orderBy("username", Query.Direction.ASCENDING)
            .limit(50)
        if (cursor != null) q = q.startAfter(cursor)
        val snap = q.get().await()
        if (snap.isEmpty) {
            return SearchBatchResult(emptyList(), cursor, true, 0)
        }
        val nextCursor = snap.documents.last()
        val matches = snap.documents.filter { doc ->
            (doc.getString("username") ?: "").lowercase().contains(query)
        }
        val usersMatched = matches.map { doc ->
            val spots = doc.getLong("totalSpots") ?: 0L
            val steps = doc.getLong("totalSteps") ?: 0L
            val rank = computeRank(spots)
            RankingUser(
                id = doc.id,
                username = doc.getString("username") ?: "Unknown",
                spots = spots,
                steps = steps,
                globalRank = rank
            )
        }
        return SearchBatchResult(usersMatched, nextCursor, false, snap.size())
    }
}
