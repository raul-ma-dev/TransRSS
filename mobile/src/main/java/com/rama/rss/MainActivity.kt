package com.rama.rss

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import com.rama.rss.reader.RssEntry
import com.rama.rss.reader.RssViewModel
import com.rama.rss.reader.escapeHtml
import com.rama.rss.shared.R as SharedR
import com.rama.rss.ui.theme.RssTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reader = ViewModelProvider(this)[RssViewModel::class.java]
        setContent {
            RssTheme(darkTheme = true, dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RssReader(reader, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RssReader(reader: RssViewModel, modifier: Modifier = Modifier) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(-1) }
    val feed = reader.feed
    val selected = feed?.entries?.getOrNull(selectedIndex)
    BackHandler(enabled = selected != null) { selectedIndex = -1 }
    if (selected != null) {
        Article(selected, feed.baseUrl, { selectedIndex = -1 }, modifier)
        return
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Image(
            painter = painterResource(SharedR.drawable.background),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.45f
        )
        PullToRefreshBox(
            isRefreshing = reader.loading,
            onRefresh = reader::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                reader.error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                if (feed != null) {
                    item { Text(plainText(feed.title), style = MaterialTheme.typography.titleLarge) }
                    if (feed.entries.isEmpty()) {
                        item { Text("Este feed no contiene entradas.") }
                    }
                }
                itemsIndexed(feed?.entries.orEmpty()) { index, entry ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedIndex = index }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(plainText(entry.title), style = MaterialTheme.typography.titleMedium)
                            if (entry.date.isNotBlank()) Text(entry.date, style = MaterialTheme.typography.labelMedium)
                            Text(
                                plainText(entry.html).ifBlank { "Pulsa para ver la entrada" },
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun readerBackground(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        listOf(
            lerp(colors.surface, colors.primaryContainer, 0.4f),
            colors.surface,
            lerp(colors.surface, colors.tertiaryContainer, 0.25f)
        )
    )
}

@Composable
private fun Article(entry: RssEntry, baseUrl: String, onBack: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val document = articleDocument(entry, colors.onSurface.toCssHex(), colors.primary.toCssHex())
    Column(modifier.fillMaxSize().background(readerBackground())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Volver") }
            if (entry.link.isNotBlank()) {
                TextButton(onClick = { openInBrowser(context, entry.link) }) { Text("Abrir original") }
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (request.isForMainFrame && request.hasGesture()) {
                                openInBrowser(view.context, request.url.toString())
                            }
                            return true
                        }
                    }
                }
            },
            update = { view ->
                if (view.tag != document) {
                    view.tag = document
                    view.loadDataWithBaseURL(baseUrl, document, "text/html", "UTF-8", null)
                }
            },
            onRelease = { it.stopLoading(); it.destroy() }
        )
    }
}

private fun plainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()

private fun openInBrowser(context: Context, address: String) {
    val uri = Uri.parse(address)
    if (uri.scheme !in listOf("http", "https")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No hay un navegador disponible.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun Color.toCssHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

internal fun articleDocument(
    entry: RssEntry,
    textColor: String = "#E5E7EB",
    linkColor: String = "#FF8A91"
): String = """
    <!doctype html>
    <html lang="es"><head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="dark">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
    <style>
      :root { color-scheme: dark; }
      html, body { background: transparent; }
      body { font: 18px/1.6 sans-serif; margin: 20px; overflow-wrap: anywhere; color: $textColor; }
      h1 { font-size: 1.6em; line-height: 1.3; } img, video { max-width: 100%; height: auto; }
      pre { white-space: pre-wrap; padding: 12px; background: #2B1E21; border-radius: 8px; }
      code { background: #2B1E21; border-radius: 4px; }
      blockquote { margin: 16px 0; padding: 8px 16px; border-left: 3px solid $linkColor; background: #24191C; }
      table { display: block; overflow-x: auto; } th, td { border-bottom: 1px solid #51363B; padding: 8px; }
      hr { border: 0; border-top: 1px solid #51363B; }
      a { color: $linkColor; } .date { font-size: .85em; opacity: .7; }
    </style></head><body>
    <h1>${escapeHtml(plainText(entry.title))}</h1>
    <p class="date">${escapeHtml(entry.date)}</p>
    ${entry.html.ifBlank { "<p>El feed no incluye contenido. Usa «Abrir original» para visitar el artículo.</p>" }}
    </body></html>
""".trimIndent()
