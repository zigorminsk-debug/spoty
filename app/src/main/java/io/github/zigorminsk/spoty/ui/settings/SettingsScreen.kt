package io.github.zigorminsk.spoty.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.BuildConfig
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.SourceId
import io.github.zigorminsk.spoty.data.settings.ThemeMode
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import kotlinx.coroutines.launch

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(navController: NavHostController, snackbar: SnackbarHostState) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by container.settings.state.collectAsStateWithLifecycle()
    var jamendoId by rememberSaveable { mutableStateOf(container.settings.value.jamendoClientId) }

    val exportedText = stringResource(R.string.backup_exported)
    val importedText = stringResource(R.string.backup_imported)
    val importFailedText = stringResource(R.string.backup_failed)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = container.backup.export()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }.onSuccess { snackbar.showSnackbar(exportedText) }
                    .onFailure { snackbar.showSnackbar(importFailedText) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
                    container.backup.import(json)
                }.onSuccess { snackbar.showSnackbar(importedText) }
                    .onFailure { snackbar.showSnackbar(importFailedText) }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
        }

        SectionTitle(stringResource(R.string.sources))
        SourceId.entries.forEach { source ->
            SwitchRow(
                title = source.title,
                subtitle = when (source) {
                    SourceId.AUDIUS -> stringResource(R.string.source_audius_desc)
                    SourceId.ARCHIVE -> stringResource(R.string.source_archive_desc)
                    SourceId.JAMENDO -> stringResource(R.string.source_jamendo_desc)
                },
                checked = source.key in settings.enabledSources,
                onChange = { enabled ->
                    scope.launch {
                        container.settings.setSourceEnabled(source, enabled)
                        container.catalog.clearCache()
                    }
                },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            OutlinedTextField(
                value = jamendoId,
                onValueChange = { jamendoId = it },
                label = { Text(stringResource(R.string.jamendo_client_id)) },
                supportingText = { Text(stringResource(R.string.jamendo_client_id_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                scope.launch {
                    container.settings.setJamendoClientId(jamendoId)
                    container.catalog.clearCache()
                }
            }) { Text(stringResource(R.string.save)) }
        }
        SwitchRow(
            title = stringResource(R.string.open_license_only),
            subtitle = stringResource(R.string.open_license_only_desc),
            checked = settings.openLicenseOnly,
            onChange = { v ->
                scope.launch {
                    container.settings.setOpenLicenseOnly(v)
                    container.catalog.clearCache()
                }
            },
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle(stringResource(R.string.appearance))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val selected = settings.themeMode == mode
                TextButton(onClick = { scope.launch { container.settings.setThemeMode(mode) } }) {
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        },
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            SwitchRow(
                title = stringResource(R.string.dynamic_color),
                subtitle = stringResource(R.string.dynamic_color_desc),
                checked = settings.dynamicColor,
                onChange = { v -> scope.launch { container.settings.setDynamicColor(v) } },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle(stringResource(R.string.downloads_and_cache))
        SwitchRow(
            title = stringResource(R.string.wifi_only),
            subtitle = stringResource(R.string.wifi_only_desc),
            checked = settings.wifiOnlyDownloads,
            onChange = { v -> scope.launch { container.settings.setWifiOnlyDownloads(v) } },
        )
        SwitchRow(
            title = stringResource(R.string.auto_sync),
            subtitle = stringResource(R.string.auto_sync_desc),
            checked = settings.autoSync,
            onChange = { v -> scope.launch { container.settings.setAutoSync(v) } },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        container.clearMediaCache()
                        snackbar.showSnackbar(context.getString(R.string.cache_cleared))
                    }
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_cleaning_services), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.clear_cache), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        container.downloads.removeAll()
                        snackbar.showSnackbar(context.getString(R.string.downloads_cleared))
                    }
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.delete_all_downloads), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle(stringResource(R.string.backup))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { exportLauncher.launch("spoty-backup.json") }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_file_upload), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.export_library), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { importLauncher.launch(arrayOf("application/json")) }
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_file_download), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.import_library), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionTitle(stringResource(R.string.about))
        Text(
            stringResource(R.string.about_text, BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Text(
            BuildConfig.REPO_URL,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(BuildConfig.REPO_URL)),
                        )
                    }
                }
                .padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Text(
            stringResource(R.string.about_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}
