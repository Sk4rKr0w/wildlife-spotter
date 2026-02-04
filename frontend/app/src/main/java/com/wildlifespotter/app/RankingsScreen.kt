package com.wildlifespotter.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildlifespotter.app.models.RankingUser
import com.wildlifespotter.app.models.RankingsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen() {
    val viewModel: RankingsViewModel = viewModel()
    val uiState = viewModel.uiState
    val currentUserId = viewModel.currentUserId

    LaunchedEffect(Unit) { viewModel.loadPage(reset = true) }

    LaunchedEffect(uiState.searchUsername) {
        delay(300)
        if (uiState.searchUsername.trim().isEmpty()) viewModel.resetSearch()
        else {
            viewModel.resetSearch()
            viewModel.loadSearchPage()
        }
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
            value = uiState.searchUsername,
            onValueChange = { viewModel.setSearchUsername(it) },
            label = { Text("Search username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.error != null) Text(uiState.error ?: "Error", color = MaterialTheme.colorScheme.error)
        else {
            val showingSearch = uiState.searchUsername.trim().isNotEmpty()
            val useSearchResults = showingSearch && uiState.searchResults.isNotEmpty()
            val listToShow = if (useSearchResults) {
                val start = uiState.searchPage * 10
                val end = (start + 10).coerceAtMost(uiState.searchResults.size)
                if (start < end) uiState.searchResults.subList(start, end) else emptyList()
            } else {
                if (showingSearch && !uiState.isSearching && !uiState.isLoading) emptyList() else uiState.users
            }
            val showNoResults = showingSearch && !uiState.isSearching && !uiState.isLoading && !useSearchResults
            if (showNoResults) Text("No results found", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().graphicsLayer(alpha = if (uiState.isSearching || uiState.isLoading) 0.6f else 1f)
                ) {
                    itemsIndexed(listToShow) { index, user ->
                        val rank = if (useSearchResults) {
                            user.globalRank ?: (uiState.searchPage * 10 + index + 1)
                        } else uiState.currentPage * 10 + index + 1
                        RankingRow(user, rank, currentUserId)
                    }
                }

                if (uiState.isSearching || uiState.isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = if (showingSearch) uiState.searchPage > 0 else uiState.currentPage > 0,
                    onClick = {
                        if (showingSearch) viewModel.prevSearchPage()
                        else viewModel.prevPage()
                    }
                ) { Text("Prev") }

                Text("Page ${if (showingSearch) uiState.searchPage + 1 else uiState.currentPage + 1}")

                TextButton(
                    enabled = if (showingSearch) viewModel.canGoNextSearch() else viewModel.canGoNextRankings(),
                    onClick = {
                        if (showingSearch) viewModel.nextSearchPage()
                        else viewModel.nextPage()
                    }
                ) { Text("Next") }
            }
        }
    }
}

@Composable
private fun RankingRow(user: RankingUser, rank: Int, currentUserId: String?) {
    val isCurrentUser = user.id == currentUserId

    val accent = when (rank) {
        1 -> Color(0xFFD4AF37)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        4 -> Color(0xFF4CAF50)
        5 -> Color(0xFF03A9F4)
        else -> Color(0xFF546E7A)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) Color(0xFF121212) else Color(0xFF374B5E).copy(alpha = 0.85f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#$rank", color = accent, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(user.username, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Pets, contentDescription = null, tint = Color.LightGray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${user.spots}", color = Color.LightGray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = Color.LightGray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${user.steps}", color = Color.LightGray)
                        }
                    }
                }
            }
            Text("⭐", color = accent)
        }
    }
}
