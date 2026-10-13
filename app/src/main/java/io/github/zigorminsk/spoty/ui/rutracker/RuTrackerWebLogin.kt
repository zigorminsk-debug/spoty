package io.github.zigorminsk.spoty.ui.rutracker

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.remote.rutracker.normalizeMirrorUrl

/**
 * Вход в RuTracker через встроенный WebView: открывается настоящая страница трекера,
 * поэтому JavaScript-защита от ботов пропускает её как обычный браузер. После входа
 * забираем все cookie домена (и сессию bb_session, и cookie самой защиты) — их хватает
 * и поиску, и торрент-движку в OkHttp-запросах.
 */
@Composable
fun RuTrackerWebLoginDialog(
    mirror: String,
    onDismiss: () -> Unit,
    /** Вызывается один раз, когда в cookie домена появилась сессия bb_session. */
    onSessionCookie: (String) -> Unit,
) {
    var taken by remember { mutableStateOf(false) }
    val startUrl = "${normalizeMirrorUrl(mirror)}/forum/login.php"

    AlertDialog(
        onDismissRequest = { if (!taken) onDismiss() },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (!taken) onDismiss() }) { Text(stringResource(R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.rutracker_web_login_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.rutracker_web_login_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (taken) return
                                    val cookie = CookieManager.getInstance().getCookie(url ?: startUrl)
                                    if (cookie != null && "bb_session=" in cookie) {
                                        taken = true
                                        onSessionCookie(cookie)
                                    }
                                }
                            }
                            loadUrl(startUrl)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(380.dp).padding(top = 12.dp),
                )
            }
        },
    )
}
