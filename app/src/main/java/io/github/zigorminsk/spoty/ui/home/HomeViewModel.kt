package io.github.zigorminsk.spoty.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.Genre
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.remote.safely
import io.github.zigorminsk.spoty.di.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val offline: Boolean = false,
    val forYou: List<Track> = emptyList(),
    val trending: List<Track> = emptyList(),
    val newAlbums: List<Album> = emptyList(),
    val popularArtists: List<Artist> = emptyList(),
    val recent: List<Track> = emptyList(),
    val personalGenres: List<Genre> = emptyList(),
) {
    val isEmpty: Boolean get() = forYou.isEmpty() && trending.isEmpty() && newAlbums.isEmpty() && popularArtists.isEmpty() && recent.isEmpty()
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            container.library.recentTracks(15).collect { recent ->
                _state.value = _state.value.copy(recent = recent)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = _state.value.isEmpty)
            coroutineScope {
                val forYou = async { safely(emptyList<Track>()) { container.recommendations.forYou(30) } }
                val trending = async { safely(emptyList<Track>()) { container.catalog.trendingTracks(null, 30) } }
                val albums = async { safely(emptyList<Album>()) { container.catalog.popularAlbums(null, 24) } }
                val artists = async { safely(emptyList<Artist>()) { container.catalog.popularArtists(20) } }
                val genres = async { safely(emptyList<Genre>()) { container.recommendations.personalGenres(8) } }
                val fy = forYou.await()
                val tr = trending.await()
                val al = albums.await()
                val ar = artists.await()
                // Если сети нет совсем — пробуем показать локальный каталог.
                val offline = fy.isEmpty() && tr.isEmpty() && al.isEmpty()
                val fallbackTracks = if (offline) container.catalogStore.randomTracks(30) else emptyList()
                val fallbackAlbums = if (offline) container.catalogStore.latestAlbums(24) else emptyList()
                val fallbackArtists = if (offline) container.catalogStore.latestArtists(20) else emptyList()
                _state.value = _state.value.copy(
                    loading = false,
                    offline = offline && fallbackTracks.isNotEmpty(),
                    forYou = fy.ifEmpty { fallbackTracks },
                    trending = tr.ifEmpty { fallbackTracks.shuffled() },
                    newAlbums = al.ifEmpty { fallbackAlbums },
                    popularArtists = ar.ifEmpty { fallbackArtists },
                    personalGenres = genres.await(),
                )
            }
        }
    }
}
