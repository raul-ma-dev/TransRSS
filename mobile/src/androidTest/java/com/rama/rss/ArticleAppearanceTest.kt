package com.rama.rss

import android.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rama.rss.reader.RssEntry
import com.rama.rss.ui.theme.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleAppearanceTest {
    private val entry = RssEntry("Noticia", "<p>Contenido</p>", "", "30/09/2026")

    @Test
    fun newsCardUsesEpisodeCardTypographyWithoutChangingDetails() {
        val card = articleDocument(entry, cardTypography = Typography)
        assertTrue(card.contains("body { font-size: 14.0px; line-height: 20.0px; }"))
        assertTrue(card.contains("font-size: 16.0px; line-height: 24.0px; font-weight: 500;"))
        assertTrue(card.contains(".date { font-size: 12.0px; line-height: 16.0px; font-weight: 500; }"))
        assertTrue(card.contains(entry.html))
        val detail = articleDocument(entry, audioDetail = true)
        assertTrue(detail.contains("body { font: 18px/1.6 sans-serif;"))
        assertFalse(detail.contains("body { font-size: 14.0px;"))
    }

    @Test
    fun newsCardTypographyFollowsThemeSizes() {
        val theme = Typography.copy(bodyMedium = Typography.bodyMedium.copy(fontSize = 17.sp, lineHeight = 25.sp))
        val document = articleDocument(entry, cardTypography = theme)
        assertTrue(document.contains("body { font-size: 17.0px; line-height: 25.0px; }"))
    }

    @Test
    fun allArticlesDisplayPublicationDateInGmtMinusThree() {
        val datedEntry = entry.copy(date = "Wed, 30 Sep 2026 01:11:10 -0300")
        for (audioDetail in listOf(false, true)) {
            val document = articleDocument(datedEntry, audioDetail = audioDetail)
            assertTrue(document.contains("<p class=\"date\">miércoles, 30/09/2026</p>"))
            assertFalse(document.contains("01:11"))
            assertFalse(document.contains("GMT-3"))
            assertFalse(document.contains(datedEntry.date))
        }
        assertEquals("Wed, 30 Sep 2026 01:11:10 -0300", datedEntry.date)
    }

    @Test
    fun articleUsesDarkPaletteByDefault() {
        val document = articleDocument(entry)
        assertTrue(document.contains("color-scheme: dark"))
        assertTrue(document.contains("color: #E5E7EB"))
        assertTrue(document.contains("a { color: #FF8A91; }"))
        assertTrue(document.contains("html, body { background: transparent; }"))
        assertTrue(document.contains("<p>Contenido</p>"))
    }

    @Test
    fun articleUsesSuppliedThemeColors() {
        val document = articleDocument(entry, "#FFFFFF", "#AABBCC")
        assertTrue(document.contains("color: #FFFFFF"))
        assertTrue(document.contains("a { color: #AABBCC; }"))
    }

    @Test
    fun redAccentIsReadableOnDarkSurfaces() {
        val accent = Color.parseColor("#FF8A91")
        for (background in listOf("#181113", "#2B1E21", "#39272B")) {
            assertTrue(
                "El acento debe mantener contraste sobre $background",
                ColorUtils.calculateContrast(accent, Color.parseColor(background)) >= 4.5
            )
        }
    }

    @Test
    fun articleAllowsHttpsAudioWithoutRelaxingScriptPolicy() {
        val document = articleDocument(entry)
        assertTrue(document.contains("media-src https:;"))
        assertTrue(document.contains("default-src 'none';"))
        assertFalse(document.contains("media-src *"))
        assertTrue(document.contains("audio { display: block; width: 100%;"))
    }

    @Test
    fun articleUpgradesLegacyImageRequestsWithoutAllowingInsecureContent() {
        val html = """<p align="center"><img src="http://www.canaltrans.com/images/a20/ed_pirata_600.jpg"></p>"""
        val document = articleDocument(entry.copy(html = html))
        assertTrue(document.contains(html))
        assertTrue(document.contains("upgrade-insecure-requests"))
        assertTrue(document.contains("img-src https: data:;"))
        assertTrue(document.contains("media-src https:;"))
        assertTrue(document.contains("default-src 'none';"))
        assertFalse(document.contains("img-src http:"))
    }

    @Test
    fun nativeAudioDetailRetainsContentWithoutDuplicatingTheHtmlPlayer() {
        val episode = entry.copy(
            html = """<img src="https://example.com/cover.jpg"><audio controls src="https://example.com/embedded.mp3"><source src="https://example.com/fallback.mp3">Audio</audio><p>Descripción completa</p>""",
            audioUrl = "https://example.com/enclosure.mp3"
        )
        val document = articleDocument(episode, audioDetail = true, nativeAudio = true)
        assertTrue(document.contains("""<img src="https://example.com/cover.jpg">"""))
        assertTrue(document.contains("<p>Descripción completa</p>"))
        assertFalse(document.contains("<audio"))
        assertFalse(document.contains("<source"))
        assertTrue(articleDocument(episode).contains("<audio"))
    }

    @Test
    fun nativePlayerSplitsAfterFirstImageWithoutLosingOrRepeatingContent() {
        val image = """<img src="/cover.jpg" title="Portada > episodio">"""
        val html = """<p>Introducción</p><p align="center">$image</p><p>Descripción completa</p><img src="/second.jpg">"""
        val (before, after) = splitAfterFirstImage(html)
        assertEquals(html, before + after)
        assertTrue(before.endsWith(image))
        assertFalse(before.contains("Descripción completa"))
        assertTrue(after.contains("Descripción completa"))
        assertTrue(after.contains("/second.jpg"))
        val continuation = articleDocument(entry.copy(html = after), audioDetail = true, nativeAudio = true, includeHeader = false)
        assertTrue(continuation.contains("Descripción completa"))
        assertFalse(continuation.contains("<h1>Noticia</h1>"))
        assertFalse(continuation.contains("""<p class="date">"""))
        assertEquals("<p>Sin imagen</p>" to "", splitAfterFirstImage("<p>Sin imagen</p>"))
    }

    @Test
    fun nativeEpisodeAudioResolvesEnclosuresAndEmbeddedSourcesSecurely() {
        assertEquals("https://example.com/episode.mp3", episodeAudioUrl(entry.copy(audioUrl = "/episode.mp3"), "https://example.com/feed.xml"))
        assertEquals(
            "https://example.com/embedded.mp3?a=1&b=2",
            episodeAudioUrl(entry.copy(html = """<audio controls><source src="/embedded.mp3?a=1&amp;b=2"></audio>"""), "https://example.com/feed.xml")
        )
        assertEquals(
            "https://example.com/legacy.mp3",
            episodeAudioUrl(entry.copy(html = """<audio controls src='http://example.com/legacy.mp3'></audio>"""), "")
        )
        assertEquals("", episodeAudioUrl(entry, "https://example.com/feed.xml"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nativeEpisodeAudioRejectsLocalFiles() {
        episodeAudioUrl(entry.copy(audioUrl = "file:///private/episode.mp3"), "https://example.com/feed.xml")
    }

    @Test
    fun enclosureCreatesPlayerWithoutAutoplayOrPreloading() {
        val document = articleDocument(entry.copy(audioUrl = "https://example.com/episode.mp3?a=1&b=2"))
        assertTrue(document.contains("<audio controls=\"controls\" preload=\"none\""))
        assertTrue(document.contains("src=\"https://example.com/episode.mp3?a=1&amp;b=2\""))
        assertFalse(document.contains("autoplay"))
    }

    @Test
    fun existingAudioIsNotDuplicatedByEnclosure() {
        val audio = """<audio controls="controls" preload="none" src="https://example.com/episode.mp3"></audio>"""
        val document = articleDocument(entry.copy(html = audio, audioUrl = "https://example.com/episode.mp3"))
        assertEquals(1, Regex("<audio\\b").findAll(document).count())
        assertTrue(document.contains(audio))
    }

    @Test
    fun audioDetailRestoresDescriptionAndRemovesOnlyItsH1() {
        val episode = entry.copy(
            html = "<h1>TÍTULO_INTERNO_DUPLICADO</h1><p>TEXTO_CDATA_COMPLETO</p><img src='https://example.com/cover.jpg'><p>Texto después de la imagen</p>",
            audioUrl = "https://example.com/episode.mp3"
        )
        val document = articleDocument(episode, audioDetail = true)
        val imagePosition = document.indexOf("<img src='https://example.com/cover.jpg'>")
        val audioPosition = document.indexOf("<audio controls=")
        assertTrue(imagePosition >= 0)
        assertTrue(audioPosition > imagePosition)
        assertTrue(document.contains("<p>TEXTO_CDATA_COMPLETO</p>"))
        assertTrue(document.contains("<p>Texto después de la imagen</p>"))
        assertFalse(document.contains("TÍTULO_INTERNO_DUPLICADO"))
        assertEquals(1, Regex("<h1\\b", RegexOption.IGNORE_CASE).findAll(document).count())
        assertTrue(document.contains("<h1>Noticia</h1>"))
        assertTrue(document.contains("30/09/2026"))
        assertTrue(articleDocument(episode).contains("TÍTULO_INTERNO_DUPLICADO"))
    }

    @Test
    fun audioDetailPreservesExistingPlayerWithoutDuplicatingIt() {
        val audio = "<audio controls src='https://example.com/matched.mp3'></audio>"
        val episode = entry.copy(
            html = "<img src='https://example.com/cover.jpg'>$audio<p>Descripción completa</p>",
            audioUrl = "https://example.com/matched.mp3"
        )
        val document = articleDocument(episode, audioDetail = true)
        assertEquals(1, Regex("<audio\\b").findAll(document).count())
        assertTrue(document.contains(audio))
        assertTrue(document.contains("<p>Descripción completa</p>"))
    }

    @Test
    fun audioDetailPreservesRelativeImageAndOtherHeadingLevels() {
        val image = "<img src='/images/cover.jpg?a=1&amp;b=2' title='Imagen > portada'>"
        val episode = entry.copy(
            html = "<H1 title='Título > duplicado'>\n<strong>Quitar este título</strong>\n</H1>$image<h2>Conservar subtítulo</h2><a href='/extra'>Enlace</a>",
            audioUrl = "https://example.com/episode.mp3",
            baseUrl = "https://example.com/feed.xml"
        )
        val document = articleDocument(episode, audioDetail = true)
        assertTrue(document.contains(image))
        assertFalse(document.contains("Quitar este título"))
        assertTrue(document.contains("<h2>Conservar subtítulo</h2>"))
        assertTrue(document.contains("<a href='/extra'>Enlace</a>"))
        assertTrue(document.contains("$image\n<audio controls="))
        assertEquals(1, Regex("<img\\b").findAll(document).count())
    }

    @Test
    fun audioDetailKeepsDescriptionAndPlayerWhenThereIsNoImage() {
        val document = articleDocument(entry.copy(audioUrl = "https://example.com/episode.mp3"), audioDetail = true)
        assertTrue(document.contains("<audio controls="))
        assertFalse(document.contains("<img "))
        assertTrue(document.contains("<p>Contenido</p>"))
    }
}
