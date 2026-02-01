package com.wildlifespotter.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Pets

private data class RankingUser(
    val id: String,
    val username: String,
    val spots: Long,
    val steps: Long,
    val globalRank: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen() {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var users by remember { mutableStateOf<List<RankingUser>>(emptyList()) }

    val pageCursors = remember { mutableStateListOf<DocumentSnapshot>() }
    var currentPage by remember { mutableStateOf(0) }

    var searchUsername by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<RankingUser>>(emptyList()) }
    var searchCursor by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var searchPage by remember { mutableStateOf(0) }
    var searchDone by remember { mutableStateOf(false) }

    fun loadPage(startAfter: DocumentSnapshot? = null, reset: Boolean = false) {
        scope.launch {
            isLoading = true
            error = null
            try {
                var query = db.collection("users")
                    .orderBy("totalSpots", Query.Direction.DESCENDING)
                    .limit(10)
                if (startAfter != null) {
                    query = query.startAfter(startAfter)
                }
                val snap = query.get().await()
                val list = snap.documents.map { doc ->
                    RankingUser(
                        id = doc.id,
                        username = doc.getString("username") ?: "Unknown",
                        spots = doc.getLong("totalSpots") ?: 0L,
                        steps = doc.getLong("totalSteps") ?: 0L
                    )
                }
                users = list
                if (reset) {
                    pageCursors.clear()
                    currentPage = 0
                }
                val last = snap.documents.lastOrNull()
                if (last != null) {
                    if (pageCursors.size > currentPage) {
                        pageCursors[currentPage] = last
                    } else {
                        pageCursors.add(last)
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to load rankings"
            } finally {
                isLoading = false
            }
        }
    }

    suspend fun computeRank(spots: Long): Int {
        val higherSnap = db.collection("users")
            .whereGreaterThan("totalSpots", spots)
            .get()
            .await()
        return higherSnap.size() + 1
    }

    fun resetSearch() {
        searchResults = emptyList()
        searchCursor = null
        searchPage = 0
        searchDone = false
    }

    fun loadSearchPage() {
        scope.launch {
            isSearching = true
            try {
                val query = searchUsername.trim().lowercase()
                if (query.isEmpty()) return@launch
                var fetched = 0
                val targetCount = (searchPage + 1) * 10
                var cursor = searchCursor
                while (searchResults.size < targetCount && !searchDone && fetched < 500) {
                    var q = db.collection("users")
                        .orderBy("username", Query.Direction.ASCENDING)
                        .limit(50)
                    if (cursor != null) {
                        q = q.startAfter(cursor)
                    }
                    val snap = q.get().await()
                    if (snap.isEmpty) {
                        searchDone = true
                        break
                    }
                    cursor = snap.documents.last()
                    val matches = snap.documents.filter { doc ->
                        val uname = (doc.getString("username") ?: "").lowercase()
                        uname.contains(query)
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
                    searchResults = (searchResults + usersMatched).distinctBy { it.id }
                    fetched += snap.size()
                    searchCursor = cursor
                }
            } catch (e: Exception) {
                searchDone = true
            } finally {
                isSearching = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPage(reset = true)
    }

    LaunchedEffect(searchUsername) {
        delay(300)
        val query = searchUsername.trim()
        if (query.isEmpty()) {
            resetSearch()
            return@LaunchedEffect
        }
        resetSearch()
        loadSearchPage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Rankings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchUsername,
            onValueChange = { searchUsername = it },
            label = { Text("Search username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (error != null) {
            Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
        } else {
            val showingSearch = searchUsername.trim().isNotEmpty()
            val useSearchResults = showingSearch && searchResults.isNotEmpty()
            val listToShow = if (useSearchResults) {
                val start = searchPage * 10
                val end = (start + 10).coerceAtMost(searchResults.size)
                if (start < end) searchResults.subList(start, end) else emptyList()
            } else {
                if (showingSearch && !isSearching && !isLoading) emptyList() else users
            }
            val showNoResults = showingSearch && !isSearching && !isLoading && !useSearchResults
            if (showNoResults) {
                Text("No results found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = if (isSearching || isLoading) 0.6f else 1f)
                ) {
                    itemsIndexed(listToShow) { index, user ->
                        val rank = if (useSearchResults) {
                            user.globalRank ?: (searchPage * 10 + index + 1)
                        } else {
                            currentPage * 10 + index + 1
                        }
                        RankingRow(user, rank)
                    }
                }
                if (isSearching || isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = if (showingSearch) searchPage > 0 else currentPage > 0,
                    onClick = {
                        if (showingSearch) {
                            if (searchPage == 0) return@TextButton
                            searchPage -= 1
                        } else {
                            if (currentPage == 0) return@TextButton
                            currentPage -= 1
                            val cursor = if (currentPage == 0) null else pageCursors.getOrNull(currentPage - 1)
                            loadPage(startAfter = cursor)
                        }
                    }
                ) { Text("Prev") }

                Text("Page ${if (showingSearch) searchPage + 1 else currentPage + 1}")

                TextButton(
                    enabled = if (showingSearch) {
                        !searchDone || (searchPage + 1) * 10 < searchResults.size
                    } else {
                        users.size == 10
                    },
                    onClick = {
                        if (showingSearch) {
                            searchPage += 1
                            loadSearchPage()
                        } else {
                            val cursor = pageCursors.getOrNull(currentPage) ?: return@TextButton
                            currentPage += 1
                            loadPage(startAfter = cursor)
                        }
                    }
                ) { Text("Next") }
            }
        }
    }
}

@Composable
private fun RankingRow(user: RankingUser, rank: Int) {
    val accent = when (rank) {
        1 -> Color(0xFFD4AF37) // gold
        2 -> Color(0xFFC0C0C0) // silver
        3 -> Color(0xFFCD7F32) // bronze
        4 -> Color(0xFF4CAF50) // green
        5 -> Color(0xFF03A9F4) // blue
        else -> Color(0xFF546E7A)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF374B5E).copy(alpha = 0.85f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$rank",
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Column {
                    Text(user.username, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Pets, contentDescription = null, tint = Color.LightGray)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("${user.spots}", color = Color.LightGray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = Color.LightGray)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("${user.steps}", color = Color.LightGray)
                        }
                    }
                }
            }
            Text("⭐", color = accent)
        }
    }
}
