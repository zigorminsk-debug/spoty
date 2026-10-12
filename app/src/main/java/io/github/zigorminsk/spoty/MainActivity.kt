package io.github.zigorminsk.spoty

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.serialization.Serializable

@Serializable
data class Probe(val name: String = "spoty")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val cls = ExoPlayer::class.java.simpleName
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface { Text("Spoty ${BuildConfig.VERSION_NAME} $cls ${Probe().name}") }
            }
        }
    }
}
