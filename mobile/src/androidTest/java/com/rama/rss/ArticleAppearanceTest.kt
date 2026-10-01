package com.rama.rss

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rama.rss.reader.RssEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleAppearanceTest {
    private val entry = RssEntry("Noticia", "<p>Contenido</p>", "", "30/09/2026")

    @Test
    fun allArticlesDisplayPublicationDateInUtcMinusSix() {
        val datedEntry = entry.copy(date = "Wed, 30 Sep 2026 01:11:10 -0300")
        for (audioDetail in listOf(false, true)) {
            val document = articleDocument(datedEntry, audioDetail = audioDetail)
            assertTrue(document.contains("<p class=\"date\">martes, 29/09/2026</p>"))
            assertFalse(document.contains("22:11"))
            assertFalse(document.contains("UTC-6"))
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
