package io.github.zigorminsk.spoty.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.zigorminsk.spoty.data.local.toModel
import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class CatalogTab { ARTISTS, ALBUMS, TRACKS }

/** 0 — новые, 1 — по алфавиту, 2 — популярные/по году. */
data class CatalogFilter(
    val tab: CatalogTab = CatalogTab.ALBUMS,
    val source: String = "",
    val query: String = "",
    val sort: Int = 0,
)

data class CatalogCounts(val artists: Int = 0, val albums: Int = 0, val tracks: Int = 0)

/**
 * «Каталог» — всё, что приложение нашло и сохранило локально: можно просматривать,
 * фильтровать по источнику и искать даже без сети.
 */
class CatalogViewModel(container: AppContainer) : ViewModel() {
    private val dao = container.database.catalog()

    private val _filter = MutableStateFlow(CatalogFilter())
    val filter: StateFlow<CatalogFilter> = _filter.asStateFlowCompat()

    val counts: StateFlow<CatalogCounts> =
        combine(dao.countArtists(), dao.countAlbums(), dao.countTracks()) { ar, al, t -> CatalogCounts(ar, al, t) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogCounts())

    val artists: StateFlow<List<Artist>> = _filter
        .flatMapLatest { f -> dao.observeArtists(f.source, f.query.trim().lowercase(), f.sort, 400) }
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = _filter
        .flatMapLatest { f -> dao.observeAlbums(f.source, f.query.trim().lowercase(), f.sort, 400) }
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tracks: StateFlow<List<Track>> = _filter
        .flatMapLatest { f -> dao.observeTracks(f.source, f.query.trim().lowercase(), f.sort, 400) }
        .map { list -> list.map { it.toModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setTab(tab: CatalogTab) = _filter.tryEmit(_filter.value.copy(tab = tab)).let { }
    fun setSource(source: String) = _filter.tryEmit(_filter.value.copy(source = source)).let { }
    fun setQuery(query: String) = _filter.tryEmit(_filter.value.copy(query = query)).let { }
    fun setSort(sort: Int) = _filter.tryEmit(_filter.value.copy(sort = sort)).let { }
}

private fun <T> MutableStateFlow<T>.asStateFlowCompat(): StateFlow<T> = this
