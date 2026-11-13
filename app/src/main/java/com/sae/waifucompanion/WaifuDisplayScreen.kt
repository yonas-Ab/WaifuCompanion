package com.sae.waifucompanion

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WaifuDisplayScreen(
    url: String,
    reloadTrigger: Long,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(url, reloadTrigger) {
        webViewInstance?.loadUrl(url)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        databaseEnabled = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    webViewClient = object : WebViewClient() {}
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.let {
                                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in it.resources) {
                                    it.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                                } else {
                                    it.grant(it.resources)
                                }
                            }
                        }

                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                val level = when (it.messageLevel()) {
                                    ConsoleMessage.MessageLevel.ERROR -> LogLevel.ERROR
                                    ConsoleMessage.MessageLevel.WARNING -> LogLevel.WARNING
                                    else -> LogLevel.INFO
                                }
                                ConsoleLogManager.addLog(
                                    level = level,
                                    message = it.message(),
                                    sourceId = it.sourceId() ?: "inline",
                                    lineNumber = it.lineNumber()
                                )
                            }
                            return true
                        }
                    }
                    webViewInstance = this
                    onWebViewCreated(this)
                    loadUrl(url)
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose { webViewInstance?.stopLoading() }
    }
}
