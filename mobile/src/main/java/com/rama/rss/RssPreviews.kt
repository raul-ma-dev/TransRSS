package com.rama.rss

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rama.rss.reader.RssEntry
import com.rama.rss.reader.RssFeed
import com.rama.rss.reader.RssSource
import com.rama.rss.reader.enabledRssSources
import com.rama.rss.ui.theme.RssTheme

private val SampleArticle = RssEntry(
    title = "Historias para descubrir",
    html = "<p>Una selección de historias y novedades para acompañar tu día.</p>" +
        "<p>Abre cada entrada para consultar su contenido completo y los enlaces publicados.</p>",
    link = "https://example.com/articulo",
    date = "30 de septiembre de 2026"
)

private val SampleAudioboomEpisode = SampleArticle.copy(
    title = "Episodio de Audioboom",
    html = "<img src=\"https://example.com/portada.jpg\" alt=\"Portada\">" + SampleArticle.html,
    categories = listOf("Podcast"),
    audioUrl = "https://example.com/episodio.mp3",
    baseUrl = RssSource.AUDIOBOOM.url
)

private val SampleCanaltransFeed = RssFeed(
    title = "Canaltrans",
    entries = listOf(
        SampleArticle,
        SampleArticle.copy(title = "Novedades de la semana", date = "29 de septiembre de 2026"),
        SampleArticle.copy(title = "Lecturas recomendadas", date = "28 de septiembre de 2026")
    ),
    baseUrl = RssSource.CANALTRANS.url
)

private val SampleAudioboomFeed = RssFeed(
    title = "Audioboom",
    entries = listOf(
        SampleAudioboomEpisode,
        SampleAudioboomEpisode.copy(title = "Episodio de ayer", date = "29 de septiembre de 2026"),
        SampleAudioboomEpisode.copy(title = "Episodio anterior", date = "28 de septiembre de 2026")
    ),
    baseUrl = RssSource.AUDIOBOOM.url
)

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    RssTheme(darkTheme = true, dynamicColor = false) {
        Surface(content = content)
    }
}

@Preview(name = "Inicio · Canaltrans (desactivado)", group = "RSS", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CanaltransHomePreview() {
    PreviewTheme {
        RssAppContent(RssSource.CANALTRANS, SampleCanaltransFeed, availableSources = enabledRssSources(true))
    }
}

@Preview(name = "Inicio · Audioboom", group = "RSS", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun AudioboomHomePreview() {
    PreviewTheme { RssAppContent(RssSource.AUDIOBOOM, SampleAudioboomFeed) }
}

@Preview(name = "Inicio · Cargando", group = "RSS", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoadingHomePreview() {
    PreviewTheme { RssAppContent(RssSource.AUDIOBOOM, feed = null, loading = true) }
}

@Preview(name = "Inicio · Error", group = "RSS", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ErrorHomePreview() {
    PreviewTheme {
        RssAppContent(
            RssSource.AUDIOBOOM,
            feed = null,
            error = "No se pudo descargar el feed. Comprueba tu conexión a Internet."
        )
    }
}

@Preview(name = "Inicio · Sin entradas", group = "RSS", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun EmptyHomePreview() {
    PreviewTheme { RssAppContent(RssSource.AUDIOBOOM, SampleAudioboomFeed.copy(entries = emptyList())) }
}

@Preview(name = "Menú inferior", group = "Navegación", showBackground = true, widthDp = 360, heightDp = 100)
@Composable
private fun BottomNavigationPreview() {
    PreviewTheme { FeedBottomNavigation(RssSource.AUDIOBOOM) }
}

@Preview(name = "Tarjeta completa · Canaltrans (desactivado)", group = "Artículos", showBackground = true, widthDp = 360, heightDp = 560)
@Composable
private fun CanaltransCardPreview() {
    PreviewTheme { CanaltransArticleCard(SampleArticle, RssSource.CANALTRANS.url, Modifier.padding(16.dp)) }
}

@Preview(name = "Artículo con audio", group = "Artículos", showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun AudioArticlePreview() {
    PreviewTheme { Article(SampleAudioboomEpisode, RssSource.AUDIOBOOM.url, onBack = {}) }
}
