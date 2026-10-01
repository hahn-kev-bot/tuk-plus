package app.hahn.tukplus.ui.webcheckout

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.hahn.tukplus.R
import app.hahn.tukplus.ui.theme.TukIcons

private const val ORIGIN = "https://tukapp.co"

/**
 * The Tuk web app in a WebView, with our cart in its basket (PLAN.md §8a). The user finishes
 * the order there: address, time, payment and "Place order". When the web app has sent the
 * order and shows its orders list, [onPlaced] runs.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebCheckoutScreen(onClose: () -> Unit, onPlaced: () -> Unit, viewModel: WebCheckoutViewModel = hiltViewModel()) {
    val placed by viewModel.placed.collectAsStateWithLifecycle()
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var askLeave by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }
    val supported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    LaunchedEffect(placed) { if (placed) onPlaced() }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else askLeave = true
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { askLeave = true }) { Icon(TukIcons.Close, contentDescription = stringResource(R.string.close)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.web_checkout_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.web_checkout_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())

        val script = viewModel.script
        val url = viewModel.url
        when {
            script == null || url == null -> Message(stringResource(R.string.web_checkout_no_cart))
            !supported -> Message(stringResource(R.string.web_checkout_unsupported))
            gone -> Message(stringResource(R.string.web_checkout_gone))
            else -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            WebViewCompat.addDocumentStartJavaScript(this, script, setOf(ORIGIN))
                        }
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                            WebViewCompat.addWebMessageListener(this, "TukPlus", setOf(ORIGIN)) { _, message, _, _, _ ->
                                message.data?.let(viewModel::onMessage)
                            }
                        }
                        webViewClient = TukWebViewClient(context, viewModel::onUrl) { crashed ->
                            viewModel.onRenderGone(crashed)
                            webView = null
                            gone = true
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }

                            // "Use my location" in the web checkout: allowed when Tuk plus has the permission.
                            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                callback.invoke(origin, granted && origin.startsWith(ORIGIN), false)
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text(stringResource(R.string.web_checkout_leave_title)) },
            text = { Text(stringResource(R.string.web_checkout_leave_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askLeave = false
                    onClose()
                }) { Text(stringResource(R.string.web_checkout_leave)) }
            },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text(stringResource(R.string.web_checkout_stay)) } },
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
}

/**
 * Keeps the WebView on tukapp.co, reports page changes, and survives a stopped page process.
 * Lint does not see the Kotlin override of [onRenderProcessGone] below, so the warning is suppressed.
 */
@SuppressLint("MissingOnRenderProcessGone")
private class TukWebViewClient(
    private val context: android.content.Context,
    private val onUrl: (String) -> Unit,
    private val onGone: (Boolean) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val target = request.url
        if (target.scheme == "https" && target.host == "tukapp.co") return false
        // Phone numbers, LINE, maps and other sites open outside the app.
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onUrl(url)

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = onUrl(url)

    // The web page process stopped (crash or low memory). Close the page; the cart stays.
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onGone(detail.didCrash())
        return true
    }
}
