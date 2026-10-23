package com.tuneplayer.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuneplayer.app.R

/**
 * WebView login for YouTube Music.
 *
 * Google's sign-in flow writes the session cookies for music.youtube.com. We
 * only accept the page once SAPISID is present, which is the cookie InnerTube
 * uses for account-bound requests. The cookie is stored by the ViewModel; the
 * WebView itself is disposable and contains no app state.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginDialog(
    onDismiss: () -> Unit,
    onAuthenticated: (String) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var authenticated by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(true) }
    var attempts by remember { mutableIntStateOf(0) }

    fun capture(view: WebView) {
        if (!active || authenticated) return
        val cookie = CookieManager.getInstance()
            .getCookie("https://music.youtube.com")
            .orEmpty()
        if (cookie.contains("SAPISID=", ignoreCase = true)) {
            authenticated = true
            CookieManager.getInstance().flush()
            onAuthenticated(cookie)
            onDismiss()
        } else if (attempts < 20) {
            attempts += 1
            view.postDelayed({ capture(view) }, 750L)
        } else {
            failed = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            active = false
            webView?.apply {
                stopLoading()
                clearHistory()
                destroy()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.youtube_login_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.youtube_login_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        val scheme = request.url.scheme
                                        return scheme != "http" && scheme != "https"
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        super.onPageFinished(view, url)
                                        val page = url?.let(Uri::parse)
                                        if (active &&
                                            !authenticated &&
                                            page?.scheme == "https" &&
                                            page.host == "music.youtube.com"
                                        ) {
                                            attempts = 0
                                            view.postDelayed({ capture(view) }, 600L)
                                        }
                                    }
                                }
                                webView = this
                                loadUrl(
                                    "https://accounts.google.com/ServiceLogin" +
                                        "?continue=https%3A%2F%2Fmusic.youtube.com",
                                )
                            }
                        },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (failed) {
                            stringResource(R.string.youtube_login_failed)
                        } else {
                            stringResource(R.string.youtube_login_waiting)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.youtube_login_close))
                    }
                }
            }
        }
    }
}
