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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import com.rama.rss.reader.RssEntry
import com.rama.rss.reader.RssViewModel
import com.rama.rss.reader.escapeHtml
import com.rama.rss.ui.theme.RssTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reader = ViewModelProvider(this)[RssViewModel::class.java]
        setContent {
            RssTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RssReader(reader, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

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
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Lector RSS", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = reader.url,
            onValueChange = reader::updateUrl,
            label = { Text("URL del feed") },
            placeholder = { Text("https://ejemplo.com/feed.xml") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            enabled = !reader.loading,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { selectedIndex = -1; reader.load() },
            enabled = reader.url.isNotBlank() && !reader.loading
        ) { Text(if (reader.loading) "Cargando…" else "Cargar feed") }
        if (reader.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        reader.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (feed == null && !reader.loading) {
            Text("Introduce la URL HTTPS de tu feed RSS o Atom para leer sus entradas.")
        }
        if (feed != null) {
            Text(plainText(feed.title), style = MaterialTheme.typography.titleLarge)
            if (feed.entries.isEmpty()) Text("Este feed no contiene entradas.")
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(feed.entries) { index, entry ->
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
private fun Article(entry: RssEntry, baseUrl: String, onBack: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val document = articleDocument(entry)
    Column(modifier.fillMaxSize()) {
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

internal fun articleDocument(entry: RssEntry): String = """
    <!doctype html>
    <html lang="es"><head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
    <style>
      body { font: 18px/1.6 sans-serif; margin: 20px; overflow-wrap: anywhere; color: #202124; background: #fff; }
      h1 { font-size: 1.6em; line-height: 1.3; } img, video { max-width: 100%; height: auto; }
      pre { white-space: pre-wrap; } table { display: block; overflow-x: auto; }
      a { color: #1565c0; } .date { font-size: .85em; opacity: .7; }
      @media (prefers-color-scheme: dark) { body { color: #eee; background: #121212; } a { color: #90caf9; } }
    </style></head><body>
    <h1>${escapeHtml(plainText(entry.title))}</h1>
    <p class="date">${escapeHtml(entry.date)}</p>
    ${entry.html.ifBlank { "<p>El feed no incluye contenido. Usa «Abrir original» para visitar el artículo.</p>" }}
    </body></html>
""".trimIndent()
