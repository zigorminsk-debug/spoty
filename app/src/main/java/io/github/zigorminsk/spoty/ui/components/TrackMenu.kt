package io.github.zigorminsk.spoty.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.SourceId
import io.github.zigorminsk.spoty.data.model.Track

/** Управляет показом меню трека из любого экрана. */
class TrackMenuController {
    var track by mutableStateOf<Track?>(null)
        private set

    fun show(track: Track) {
        this.track = track
    }

    fun dismiss() {
        track = null
    }
}

data class TrackMenuActions(
    val liked: (Track) -> Boolean,
    val downloaded: (Track) -> Boolean,
    val onPlayNext: (Track) -> Unit,
    val onEnqueue: (Track) -> Unit,
    val onToggleLike: (Track) -> Unit,
    val onDislike: (Track) -> Unit,
    val onAddToPlaylist: (Track) -> Unit,
    val onDownload: (Track) -> Unit,
    val onRemoveDownload: (Track) -> Unit,
    val onOpenArtist: (Track) -> Unit,
    val onOpenAlbum: (Track) -> Unit,
    val onStartWave: (Track) -> Unit,
    val onShare: (Track) -> Unit,
)

@Composable
private fun MenuItem(iconRes: Int, title: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tint ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun TrackMenuSheet(controller: TrackMenuController, actions: TrackMenuActions) {
    val track = controller.track ?: return
    val dismiss = { controller.dismiss() }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Artwork(track.artworkUrl, Modifier.size(52.dp), seed = track.id)
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${track.artistName} · ${SourceId.title(track.source)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            val liked = actions.liked(track)
            MenuItem(R.drawable.ic_queue_play_next, stringResource(R.string.play_next), { actions.onPlayNext(track); dismiss() })
            MenuItem(R.drawable.ic_queue, stringResource(R.string.add_to_queue), { actions.onEnqueue(track); dismiss() })
            MenuItem(
                if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                stringResource(if (liked) R.string.remove_like else R.string.add_like),
                { actions.onToggleLike(track); dismiss() },
                tint = if (liked) MaterialTheme.colorScheme.primary else null,
            )
            MenuItem(R.drawable.ic_thumb_down, stringResource(R.string.dislike_hint), { actions.onDislike(track); dismiss() })
            MenuItem(R.drawable.ic_playlist_add, stringResource(R.string.add_to_playlist), { actions.onAddToPlaylist(track); dismiss() })
            if (actions.downloaded(track)) {
                MenuItem(R.drawable.ic_delete, stringResource(R.string.remove_download), { actions.onRemoveDownload(track); dismiss() })
            } else {
                MenuItem(R.drawable.ic_download, stringResource(R.string.download), { actions.onDownload(track); dismiss() })
            }
            MenuItem(R.drawable.ic_waves, stringResource(R.string.wave_by_track), { actions.onStartWave(track); dismiss() })
            if (track.artistId != null) {
                MenuItem(R.drawable.ic_person, stringResource(R.string.go_to_artist), { actions.onOpenArtist(track); dismiss() })
            }
            if (track.albumId != null) {
                MenuItem(R.drawable.ic_album, stringResource(R.string.go_to_album), { actions.onOpenAlbum(track); dismiss() })
            }
            if (track.webUrl != null) {
                MenuItem(R.drawable.ic_share, stringResource(R.string.share), { actions.onShare(track); dismiss() })
            }
        }
    }
}
