package io.github.zigorminsk.spoty.ui.rutracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
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
import io.github.zigorminsk.spoty.data.remote.rutracker.RuTrackerError
import io.github.zigorminsk.spoty.data.remote.rutracker.RuTrackerTopic
import io.github.zigorminsk.spoty.di.AppContainer
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.EmptyBox
import io.github.zigorminsk.spoty.ui.components.ErrorBox
import io.github.zigorminsk.spoty.ui.components.LoadingBox
import io.github.zigorminsk.spoty.ui.nav.Routes
import io.github.zigorminsk.spoty.ui.spotyViewModel
import io.github.zigorminsk.spoty.util.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RuTrackerSearchViewModel(
    private val container: AppContainer,
    initialQuery: String,
) : ViewModel() {

    enum class ErrorKind { AUTH, CAPTCHA, BLOCKED, NETWORK }

    data class State(
        val query: String = "",
        val loading: Boolean = false,
        val searched: Boolean = false,
        val error: ErrorKind? = null,
        val results: List<RuTrackerTopic> = emptyList(),
    )

    private val _state = MutableStateFlow(State(query = initialQuery))
    val state = _state.asStateFlow()

    init {
        if (initialQuery.isNotBlank() && container.settings.value.rutrackerConnected) submit()
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val results = container.rutracker.search(query)
                _state.update { it.copy(loading = false, searched = true, results = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuTrackerError.Captcha) {
                _state.update { it.copy(loading = false, error = ErrorKind.CAPTCHA) }
            } catch (e: RuTrackerError.Blocked) {
                _state.update { it.copy(loading = false, error = ErrorKind.BLOCKED) }
            } catch (e: RuTrackerError) {
                _state.update { it.copy(loading = false, error = ErrorKind.AUTH) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = ErrorKind.NETWORK) }
            }
        }
    }
}

@Composable
fun RuTrackerSearchScreen(initialQuery: String, navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel = spotyViewModel { RuTrackerSearchViewModel(it, initialQuery) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(stringResource(R.string.rutracker), style = MaterialTheme.typography.headlineSmall)
        }

        if (!settings.rutrackerConnected) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_key),
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.rutracker_need_login),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Button(onClick = { navController.navigate(Routes.SETTINGS) }) {
                    Text(stringResource(R.string.rutracker_open_settings))
                }
            }
            return
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.rutracker_search_hint)) },
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
            state.error == RuTrackerSearchViewModel.ErrorKind.AUTH ->
                ErrorBox(stringResource(R.string.rutracker_login_failed), onRetry = { viewModel.submit() })
            state.error == RuTrackerSearchViewModel.ErrorKind.CAPTCHA ->
                ErrorBox(stringResource(R.string.rutracker_captcha), onRetry = { viewModel.submit() })
            state.error == RuTrackerSearchViewModel.ErrorKind.BLOCKED ->
                ErrorBox(stringResource(R.string.rutracker_search_blocked), onRetry = { viewModel.submit() })
            state.error == RuTrackerSearchViewModel.ErrorKind.NETWORK ->
                ErrorBox(stringResource(R.string.network_error), onRetry = { viewModel.submit() })
            !state.searched -> EmptyBox(stringResource(R.string.rutracker_hint), icon = R.drawable.ic_travel_explore)
            state.results.isEmpty() -> EmptyBox(stringResource(R.string.nothing_found), icon = R.drawable.ic_search)
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.results, key = { it.id }) { topic ->
                    TopicRow(topic) {
                        navController.navigate(Routes.rutrackerTopic(topic.id, topic.title))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(topic: RuTrackerTopic, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(topic.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                listOfNotNull(
                    topic.forum.takeIf { it.isNotBlank() },
                    formatBytes(topic.sizeBytes).takeIf { topic.sizeBytes > 0 },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
            Text(
                "▲ ${topic.seeders}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "▼ ${topic.leechers}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
