package io.github.zigorminsk.spoty.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.zigorminsk.spoty.data.model.SearchResults
import io.github.zigorminsk.spoty.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchTab { ALL, TRACKS, ALBUMS, ARTISTS }

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: SearchResults = SearchResults(),
    val searched: Boolean = false,
    val tab: SearchTab = SearchTab.ALL,
    val error: Boolean = false,
)

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    val history = container.library.searchHistory(12)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var job: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        job?.cancel()
        if (query.trim().length < 2) {
            _state.value = _state.value.copy(results = SearchResults(), searched = false, loading = false, error = false)
            return
        }
        job = viewModelScope.launch {
            delay(400L) // дебаунс ввода
            doSearch(query)
        }
    }

    fun submit(query: String = _state.value.query) {
        job?.cancel()
        _state.value = _state.value.copy(query = query)
        if (query.trim().length < 2) return
        job = viewModelScope.launch {
            doSearch(query)
            container.library.saveSearch(query)
        }
    }

    fun setTab(tab: SearchTab) {
        _state.value = _state.value.copy(tab = tab)
    }

    fun removeFromHistory(query: String) {
        viewModelScope.launch { container.library.deleteSearch(query) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.library.clearSearchHistory() }
    }

    private suspend fun doSearch(query: String) {
        _state.value = _state.value.copy(loading = true, error = false)
        try {
            val results = container.catalog.search(query)
            _state.value = _state.value.copy(loading = false, results = results, searched = true)
            if (!results.isEmpty) container.library.saveSearch(query)
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = true, searched = true)
        }
    }
}
