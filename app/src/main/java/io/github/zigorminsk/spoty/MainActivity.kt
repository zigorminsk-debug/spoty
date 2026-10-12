package io.github.zigorminsk.spoty

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.nav.SpotyRoot
import io.github.zigorminsk.spoty.ui.theme.SpotyTheme
import io.github.zigorminsk.spoty.util.NotificationChannels

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SpotyApp).container
        container.player.connect()

        if (Build.VERSION.SDK_INT >= 33 && !NotificationChannels.canPost(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppContainer provides container) {
                SpotyTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                    SpotyRoot()
                }
            }
        }
    }
}
