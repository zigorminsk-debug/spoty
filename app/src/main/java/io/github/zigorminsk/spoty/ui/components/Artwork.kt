package io.github.zigorminsk.spoty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import io.github.zigorminsk.spoty.R
import kotlin.math.abs

private val placeholderGradients = listOf(
    listOf(Color(0xFF42506B), Color(0xFF232A3B)),
    listOf(Color(0xFF5B3A64), Color(0xFF2C1F34)),
    listOf(Color(0xFF2F5D50), Color(0xFF1B2E29)),
    listOf(Color(0xFF6B4A32), Color(0xFF32271E)),
    listOf(Color(0xFF39556B), Color(0xFF1F2B35)),
    listOf(Color(0xFF61395B), Color(0xFF2E2030)),
)

@Composable
fun placeholderBrush(seed: String?): Brush {
    val colors = placeholderGradients[abs((seed ?: "spoty").hashCode()) % placeholderGradients.size]
    return Brush.linearGradient(colors)
}

/** Обложка с градиентной заглушкой, пока картинка грузится или отсутствует. */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    seed: String? = url,
    placeholderIcon: Int = R.drawable.ic_music_note,
) {
    Box(modifier = modifier.clip(shape)) {
        val placeholder: @Composable () -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize().background(placeholderBrush(seed)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    painter = painterResource(placeholderIcon),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxSize(0.42f),
                )
            }
        }
        if (url.isNullOrBlank()) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { placeholder() },
                error = { placeholder() },
            )
        }
    }
}

/** Круглый аватар исполнителя. */
@Composable
fun ArtistAvatar(url: String?, modifier: Modifier = Modifier, seed: String? = null) {
    Artwork(
        url = url,
        modifier = modifier,
        shape = CircleShape,
        seed = seed ?: url,
        placeholderIcon = R.drawable.ic_person,
    )
}

/** Бейдж источника (Audius / Internet Archive / Jamendo). */
@Composable
fun SourceBadge(source: String, modifier: Modifier = Modifier) {
    val label = io.github.zigorminsk.spoty.data.model.SourceId.title(source)
    androidx.compose.material3.Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        maxLines = 1,
    )
}
