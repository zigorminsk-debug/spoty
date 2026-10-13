package io.github.zigorminsk.spoty.ui.ztorrents

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.remote.ztorrents.ZRelease
import io.github.zigorminsk.spoty.data.remote.ztorrents.ZTORRENTS_TAG
import io.github.zigorminsk.spoty.di.AppContainer
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.EmptyBox
import io.github.zigorminsk.spoty.ui.components.ErrorBox
import io.github.zigorminsk.spoty.ui.components.LoadingBox
import io.github.zigorminsk.spoty.ui.nav.Routes
import io.github.zigorminsk.spoty.ui.spotyViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ZTorrentsSearchViewModel(
    private val container: AppContainer,
    initialQuery: String,
) : ViewModel() {

    data class State(
        val query: String = "",
        val loading: Boolean = false,
        val searched: Boolean = false,
        /** Текст настоящей ошибки: общая заглушка «не удалось загрузить» причину прятала. */
        val errorMessage: String? = null,
        val results: List<ZRelease> = emptyList(),
    )

    private val _state = MutableStateFlow(State(query = initialQuery))
    val state = _state.asStateFlow()

    init {
        if (initialQuery.isNotBlank()) submit()
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, errorMessage = null) }
            try {
                val results = container.ztorrents.search(query)
                _state.update { it.copy(loading = false, searched = true, results = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Причина — в лог и на экран под общей фразой: по ней видно,
                // это сеть или ответ сервера, а не просто «не удалось».
                Log.w(ZTORRENTS_TAG, "search failed: $query", e)
                _state.update {
                    it.copy(loading = false, errorMessage = e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }
}

@Composable
fun ZTorrentsSearchScreen(initialQuery: String, navController: NavHostController) {
    val viewModel = spotyViewModel { ZTorrentsSearchViewModel(it, initialQuery) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(stringResource(R.string.ztorrents), style = MaterialTheme.typography.headlineSmall)
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.ztorrents_search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), null)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                viewModel.submit()
            }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            state.loading -> LoadingBox()
            state.errorMessage != null -> ErrorBox(
                "${stringResource(R.string.network_error)}\n\n${state.errorMessage}",
                onRetry = { viewModel.submit() },
            )
            !state.searched -> EmptyBox(stringResource(R.string.ztorrents_hint), icon = R.drawable.ic_explore)
            state.results.isEmpty() -> EmptyBox(stringResource(R.string.nothing_found), icon = R.drawable.ic_search)
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.results, key = { it.id }) { release ->
                    ReleaseRow(release) {
                        navController.navigate(Routes.ztorrentsTopic(release.id, release.title))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseRow(release: ZRelease, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(release.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                listOfNotNull(
                    release.category.takeIf { it.isNotBlank() },
                    release.year,
                    release.sizeText,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
