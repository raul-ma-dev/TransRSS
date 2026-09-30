package com.rama.rss

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.rama.rss.reader.RssEntry
import com.rama.rss.reader.FEED_PAGE_SIZE
import com.rama.rss.reader.RssFeed
import com.rama.rss.reader.RssSource
import com.rama.rss.reader.RssViewModel
import com.rama.rss.reader.escapeHtml
import com.rama.rss.reader.enabledRssSources
import com.rama.rss.reader.nextFeedPageSize
import com.rama.rss.reader.shouldLoadNextFeedPage
import com.rama.rss.shared.R as SharedR
import com.rama.rss.ui.theme.RssTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val reader = ViewModelProvider(this)[RssViewModel::class.java]
        setContent {
            RssTheme(darkTheme = true, dynamicColor = false) {
                RssApp(reader)
            }
        }
    }
}

@Composable
internal fun RssApp(reader: RssViewModel) {
    RssAppContent(
        selectedSource = reader.selectedSource,
        feed = reader.feed,
        loading = reader.loading,
        error = reader.error,
        onSelectSource = reader::selectSource,
        onRefresh = reader::refresh,
        availableSources = reader.availableSources
    )
}

@Composable
internal fun RssAppContent(
    selectedSource: RssSource,
    feed: RssFeed?,
    loading: Boolean = false,
    error: String? = null,
    onSelectSource: (RssSource) -> Unit = {},
    onRefresh: () -> Unit = {},
    availableSources: List<RssSource> = enabledRssSources()
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { FeedBottomNavigation(selectedSource, onSelectSource, availableSources) }
    ) { innerPadding ->
        key(selectedSource) {
            RssReader(selectedSource, feed, loading, error, onRefresh, Modifier.padding(innerPadding))
        }
    }
}

@Composable
internal fun FeedBottomNavigation(
    selectedSource: RssSource,
    onSelectSource: (RssSource) -> Unit = {},
    availableSources: List<RssSource> = enabledRssSources()
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        availableSources.forEach { source ->
            NavigationBarItem(
                selected = selectedSource == source,
                onClick = { onSelectSource(source) },
                icon = {
                    Icon(
                        painter = painterResource(
                            if (source == RssSource.CANALTRANS) R.drawable.ic_rss
                            else R.drawable.ic_headphones
                        ),
                        contentDescription = null
                    )
                },
                label = { Text(source.title) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssReader(
    source: RssSource,
    feed: RssFeed?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    var selectedEntryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var visibleCount by rememberSaveable(source) { mutableIntStateOf(FEED_PAGE_SIZE) }
    val entries = feed?.entries.orEmpty()
    val selected = if (source == RssSource.AUDIOBOOM) {
        feed?.entries?.firstOrNull { articleKey(it) == selectedEntryKey }
    } else null
    if (!LocalInspectionMode.current) {
        BackHandler(enabled = selected != null) { selectedEntryKey = null }
    }
    if (selected != null) {
        Article(selected, selected.baseUrl.ifBlank { feed?.baseUrl.orEmpty() }, { selectedEntryKey = null }, modifier)
        return
    }
    val visibleEntries = remember(entries, visibleCount) { entries.take(visibleCount) }
    LaunchedEffect(listState, entries.size, visibleCount, loading) {
        snapshotFlow {
            !loading && shouldLoadNextFeedPage(
                visibleCount = visibleCount,
                totalEntries = entries.size,
                lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                totalListItems = listState.layoutInfo.totalItemsCount,
                hasScrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
            )
        }.collect { shouldLoad ->
            if (shouldLoad) visibleCount = nextFeedPageSize(visibleCount, entries.size)
        }
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
            isRefreshing = loading,
            onRefresh = { visibleCount = FEED_PAGE_SIZE; onRefresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("RssEntryList"),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                error?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
                if (feed != null) {
                    item { Text(plainText(feed.title), style = MaterialTheme.typography.titleLarge) }
                    if (feed.entries.isEmpty()) {
                        item { Text("Este feed no contiene entradas.") }
                    }
                }
                items(visibleEntries) { entry ->
                    if (source == RssSource.CANALTRANS) {
                        CanaltransArticleCard(entry, entry.baseUrl.ifBlank { feed?.baseUrl.orEmpty() })
                    } else {
                        Card(modifier = Modifier.fillMaxWidth().clickable { selectedEntryKey = articleKey(entry) }) {
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
                if (visibleEntries.size < entries.size) {
                    item {
                        Text(
                            "Desliza para ver más entradas",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CanaltransArticleCard(entry: RssEntry, baseUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    Card(modifier.fillMaxWidth().testTag("CanaltransArticleCard")) {
        Column(Modifier.fillMaxWidth().background(readerBackground())) {
            if (isPreview) {
                ArticlePreviewContent(entry, Modifier.fillMaxWidth(), scrollable = false)
            } else {
                ArticleHtmlContent(entry, baseUrl, Modifier.fillMaxWidth(), fitContent = true)
            }
            if (entry.link.isNotBlank()) {
                TextButton(
                    onClick = { if (!isPreview) openInBrowser(context, entry.link) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) { Text("Abrir original") }
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
internal fun Article(entry: RssEntry, baseUrl: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    Column(modifier.fillMaxSize().background(readerBackground())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Volver") }
            if (entry.link.isNotBlank()) {
                TextButton(onClick = { if (!isPreview) openInBrowser(context, entry.link) }) { Text("Abrir original") }
            }
        }
        if (isPreview) {
            ArticlePreviewContent(entry, Modifier.fillMaxWidth().weight(1f), audioDetail = true)
            return@Column
        }
        ArticleHtmlContent(entry, baseUrl, Modifier.fillMaxWidth().weight(1f), audioDetail = true)
    }
}

@Composable
private fun ArticlePreviewContent(
    entry: RssEntry,
    modifier: Modifier,
    scrollable: Boolean = true,
    audioDetail: Boolean = false
) {
    val contentModifier = if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier
    Column(
        modifier = contentModifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(entry.title, style = MaterialTheme.typography.headlineMedium)
        if (entry.date.isNotBlank()) {
            Text(entry.date, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (audioDetail && episodeCoverUrl(entry.html, entry.baseUrl).isNotBlank()) {
            Box(Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceContainer)) {
                Text("Imagen del episodio", modifier = Modifier.align(Alignment.Center))
            }
        }
        if (entry.audioUrl.isNotBlank() ||
            (!audioDetail && Regex("<audio\\b", RegexOption.IGNORE_CASE).containsMatchIn(entry.html))
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Reproductor de audio", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {}, enabled = false) { Text("▶ Reproducir") }
                    Slider(value = 0f, onValueChange = {}, enabled = false)
                    Text("00:00", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            plainText(if (audioDetail) withoutDescriptionHeading(entry.html) else entry.html),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "Vista previa estática. En la app, el contenido HTML y el audio se muestran en WebView.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun articleKey(entry: RssEntry): String = "${entry.baseUrl}|${entry.link}|${entry.title}|${entry.date}"

private fun plainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim()

internal fun openInBrowser(context: Context, address: String) {
    val uri = Uri.parse(address)
    if (uri.scheme !in listOf("http", "https")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No hay un navegador disponible.", android.widget.Toast.LENGTH_SHORT).show()
    }
}

internal fun Color.toCssHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

internal fun articleDocument(
    entry: RssEntry,
    textColor: String = "#E5E7EB",
    linkColor: String = "#FF8A91",
    audioDetail: Boolean = false
): String {
    val description = if (audioDetail) withoutDescriptionHeading(entry.html) else entry.html
    val hasEmbeddedAudio = Regex("<audio\\b", RegexOption.IGNORE_CASE).containsMatchIn(description)
    val player = if (entry.audioUrl.isNotBlank() && !hasEmbeddedAudio) {
        """<audio controls="controls" preload="none" src="${escapeHtml(entry.audioUrl)}" aria-label="Reproducir episodio">Tu dispositivo no puede reproducir este audio.</audio>"""
    } else ""
    val content = description.ifBlank {
        if (player.isNotBlank()) "<p>Esta entrada incluye un episodio de audio.</p>"
        else "<p>El feed no incluye contenido. Usa «Abrir original» para visitar el artículo.</p>"
    }
    val body = if (audioDetail) playerAfterFirstImage(content, player) else "$player\n$content"
    return """
    <!doctype html>
    <html lang="es"><head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="color-scheme" content="dark">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src https: data:; media-src https:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
    <style>
      :root { color-scheme: dark; }
      html, body { background: transparent; }
      body { font: 18px/1.6 sans-serif; margin: 20px; overflow-wrap: anywhere; color: $textColor; }
      h1 { font-size: 1.6em; line-height: 1.3; } img, video { max-width: 100%; height: auto; }
      img.episode-cover { display: block; width: 100%; height: auto; border-radius: 12px; }
      audio { display: block; width: 100%; margin: 20px 0; accent-color: $linkColor; }
      audio::-webkit-media-controls-panel { background-color: #2B1E21; }
      pre { white-space: pre-wrap; padding: 12px; background: #2B1E21; border-radius: 8px; }
      code { background: #2B1E21; border-radius: 4px; }
      blockquote { margin: 16px 0; padding: 8px 16px; border-left: 3px solid $linkColor; background: #24191C; }
      table { display: block; overflow-x: auto; } th, td { border-bottom: 1px solid #51363B; padding: 8px; }
      hr { border: 0; border-top: 1px solid #51363B; }
      a { color: $linkColor; } .date { font-size: .85em; opacity: .7; }
    </style></head><body>
    <h1>${escapeHtml(plainText(entry.title))}</h1>
    <p class="date">${escapeHtml(entry.date)}</p>
    $body
    </body></html>
""".trimIndent()
}
