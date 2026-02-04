package com.wildlifespotter.app.models

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.wildlifespotter.app.data.RankingsDataSource
import kotlinx.coroutines.launch


data class RankingsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val users: List<RankingUser> = emptyList(),
    val currentPage: Int = 0,
    val searchUsername: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<RankingUser> = emptyList(),
    val searchPage: Int = 0,
    val searchDone: Boolean = false
)

class RankingsViewModel : ViewModel() {
    val currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    private val pageCursors = mutableStateListOf<DocumentSnapshot>()
    private var searchCursor: DocumentSnapshot? = null

    var uiState by mutableStateOf(RankingsUiState())
        private set

    fun loadPage(startAfter: DocumentSnapshot? = null, reset: Boolean = false) {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val result = RankingsDataSource.loadPage(startAfter)
                uiState = uiState.copy(users = result.users, isLoading = false)
                if (reset) {
                    pageCursors.clear()
                    uiState = uiState.copy(currentPage = 0)
                }
                result.lastCursor?.let { last ->
                    val idx = uiState.currentPage
                    if (pageCursors.size > idx) pageCursors[idx] = last
                    else pageCursors.add(last)
                }
            } catch (e: Exception) {
                uiState = uiState.copy(isLoading = false, error = e.message ?: "Failed to load rankings")
            }
        }
    }

    fun setSearchUsername(value: String) {
        uiState = uiState.copy(searchUsername = value)
    }

    fun resetSearch() {
        searchCursor = null
        uiState = uiState.copy(
            searchResults = emptyList(),
            searchPage = 0,
            searchDone = false
        )
    }

    fun loadSearchPage() {
        viewModelScope.launch {
            uiState = uiState.copy(isSearching = true)
            try {
                val query = uiState.searchUsername.trim().lowercase()
                if (query.isEmpty()) return@launch

                var fetched = 0
                val targetCount = (uiState.searchPage + 1) * 10
                var done = uiState.searchDone

                while (uiState.searchResults.size < targetCount && !done && fetched < 500) {
                    val batch = RankingsDataSource.fetchSearchBatch(query, searchCursor)
                    if (batch.isDone) {
                        done = true
                        break
                    }
                    searchCursor = batch.nextCursor
                    uiState = uiState.copy(
                        searchResults = (uiState.searchResults + batch.users).distinctBy { it.id }
                    )
                    fetched += batch.fetchedCount
                }

                uiState = uiState.copy(searchDone = done)
            } catch (_: Exception) {
                uiState = uiState.copy(searchDone = true)
            } finally {
                uiState = uiState.copy(isSearching = false)
            }
        }
    }

    fun prevPage() {
        val current = uiState.currentPage
        if (current == 0) return
        val newPage = current - 1
        uiState = uiState.copy(currentPage = newPage)
        val cursor = if (newPage == 0) null else pageCursors.getOrNull(newPage - 1)
        loadPage(startAfter = cursor)
    }

    fun nextPage() {
        val cursor = pageCursors.getOrNull(uiState.currentPage) ?: return
        uiState = uiState.copy(currentPage = uiState.currentPage + 1)
        loadPage(startAfter = cursor)
    }

    fun prevSearchPage() {
        if (uiState.searchPage == 0) return
        uiState = uiState.copy(searchPage = uiState.searchPage - 1)
    }

    fun nextSearchPage() {
        uiState = uiState.copy(searchPage = uiState.searchPage + 1)
        loadSearchPage()
    }

    fun canGoNextRankings(): Boolean = uiState.users.size == 10

    fun canGoNextSearch(): Boolean = !uiState.searchDone || (uiState.searchPage + 1) * 10 < uiState.searchResults.size
}
