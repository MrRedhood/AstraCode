package com.mrredhood.astracode

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

@Composable
internal fun WebLivePreviewPane(fileName: String, source: String) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileName, source, webView) {
        val view = webView ?: return@LaunchedEffect
        delay(300)
        val document = withContext(Dispatchers.Default) {
            WebPreviewPolicy.buildDocument(fileName, source)
        }
        if (document == null) {
            view.loadUrl("about:blank")
            message = if (WebPreviewPolicy.supports(fileName)) {
                "Preview is limited to " + (WebPreviewPolicy.MAX_PREVIEW_BYTES / 1024) + " KiB. Shorten this draft to preview it."
            } else {
                "Live preview supports HTML, CSS and JavaScript files."
            }
        } else {
            message = null
            view.loadDataWithBaseURL(
                "https://astracode.invalid/",
                document,
                "text/html",
                "UTF-8",
                null,
            )
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView?.let { view ->
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Live preview updates after you pause typing. Remote resources, navigation, file access and network requests are blocked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (message != null) {
            Text(message.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 520.dp),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(Color.WHITE)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = false
                        allowFileAccess = false
                        allowContentAccess = false
                        allowFileAccessFromFileURLs = false
                        allowUniversalAccessFromFileURLs = false
                        blockNetworkLoads = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean = true

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): WebResourceResponse {
                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: android.net.http.SslError?,
                        ) {
                            handler?.cancel()
                        }
                    }
                    webView = this
                }
            },
            update = {},
        )
    }
}
