package com.rama.rss

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rama.rss.reader.RssEntry
import com.rama.rss.reader.RssFeed
import com.rama.rss.reader.RssSource
import com.rama.rss.reader.RssViewModel
import com.rama.rss.ui.theme.RssTheme
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class RssBottomNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun episodeThumbnailUsesDetailCoverBesideTheTitle() {
        val image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.MAGENTA)
        val imageUrl = ByteArrayOutputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
            "data:image/png;base64," + Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)
        }
        image.recycle()
        val entry = RssEntry(
            "ECDQEMSD Podcast Show 6423 - La Leyenda del Makech",
            """<p align="center"><img src="$imageUrl"></p><p>Descripción</p><img src="https://example.com/other.jpg">""",
            "", ""
        )
        var clicks = 0
        compose.setContent {
            RssTheme(darkTheme = true, dynamicColor = false) {
                Column {
                    AudioboomSummaryCard(entry, onClick = { clicks++ })
                    AudioboomSummaryCard(entry.copy(title = "Sin portada", html = ""), onClick = {})
                }
            }
        }
        val thumbnail = compose.onNodeWithTag("EpisodeThumbnail", useUnmergedTree = true)
        thumbnail.assertIsDisplayed()
        compose.onAllNodesWithTag("EpisodeThumbnail", useUnmergedTree = true).assertCountEquals(1)
        assertEquals(imageUrl, episodeCoverUrl(entry.html, entry.baseUrl))
        val titleBounds = compose.onNodeWithText("Show 6423 - La Leyenda del Makech", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val imageBounds = thumbnail.getUnclippedBoundsInRoot()
        assertEquals(56f, (imageBounds.right - imageBounds.left).value, 1f)
        assertEquals(56f, (imageBounds.bottom - imageBounds.top).value, 1f)
        assertTrue("La miniatura debe estar a la derecha del título", imageBounds.left >= titleBounds.right)
        assertTrue("La miniatura y el título deben compartir la misma fila", imageBounds.top < titleBounds.bottom && titleBounds.top < imageBounds.bottom)
        compose.waitUntil(10_000) {
            val pixels = thumbnail.captureToImage().toPixelMap()
            pixels[pixels.width / 2, pixels.height / 2].toArgb() == Color.MAGENTA
        }
        compose.onAllNodesWithTag("AudioboomArticleCard")[0].assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun episodeCardsRemoveOnlyTheLeadingPodcastLabel() {
        val entry = RssEntry("ECDQEMSD Podcast Show 6423 - La Leyenda del Makech", "", "", "")
        val otherTitle = "Una historia sobre ECDQEMSD Podcast"
        compose.setContent {
            RssTheme(darkTheme = true, dynamicColor = false) {
                Column {
                    AudioboomSummaryCard(entry, onClick = {})
                    AudioboomSummaryCard(entry.copy(title = otherTitle), onClick = {})
                }
            }
        }
        compose.onNodeWithText("Show 6423 - La Leyenda del Makech").assertIsDisplayed()
        compose.onNodeWithText(entry.title).assertDoesNotExist()
        compose.onNodeWithText(otherTitle).assertIsDisplayed()
        assertEquals("ECDQEMSD Podcast Show 6423 - La Leyenda del Makech", entry.title)
        assertTrue(articleDocument(entry, audioDetail = true, nativeAudio = true).contains("<h1>${entry.title}</h1>"))
    }

    @Test
    fun canaltransIsInlineAndAudioboomSummaryOpensDetail() {
        lateinit var reader: RssViewModel
        val store = ViewModelStore()
        compose.runOnUiThread {
            reader = RssViewModel(SavedStateHandle(), canaltransTabEnabled = true) { url ->
                val source = RssSource.entries.first { it.url == url }
                val news = RssEntry("Entrada Canaltrans", "<p>Texto</p>", "https://example.com/article", "")
                val radio = RssEntry(
                    "Radio Canaltrans", "<p>Descripción Canaltrans</p>", "https://example.com/radio",
                    "Wed, 30 Sep 2026 01:11:10 -0300", categories = listOf("Radio")
                )
                val audio = RssEntry(
                    "Entrada Audioboom", "", "", "Wed, 30 Sep 2026 03:11:00 +0000",
                    audioUrl = "https://example.com/episode.mp3"
                )
                RssFeed(
                    "Noticias ${source.title}",
                    if (source == RssSource.CANALTRANS) listOf(news, radio) else listOf(audio),
                    url
                )
            }
            store.put("reader", reader)
        }
        try {
            compose.setContent {
                RssTheme(darkTheme = true, dynamicColor = false) { RssApp(reader) }
            }
            compose.waitUntil(timeoutMillis = 5_000) { !reader.loading && reader.feed != null }
            compose.onNodeWithText("Canaltrans").assertIsSelected()
            compose.onNodeWithTag("CanaltransArticleCard")
                .assertIsDisplayed()
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            compose.onNodeWithText("Ver en CanalTrans").assertIsDisplayed()
            compose.onNodeWithText("Volver").assertDoesNotExist()
            compose.onNodeWithText("Episodios").performClick().assertIsSelected()
            compose.waitUntil(timeoutMillis = 5_000) { !reader.loading && reader.feed != null }
            compose.onNodeWithText("En Caso De Eue El Mundo Se Desintegre").assertIsDisplayed()
            compose.onNodeWithText("Audioboom").assertDoesNotExist()
            compose.onNodeWithTag("AudioboomArticleCard")
                .assertIsDisplayed()
                .assertHasClickAction()
            compose.onNodeWithText("Radio Canaltrans").assertIsDisplayed()
            compose.onNodeWithText("Descripción Canaltrans").assertIsDisplayed()
            compose.onNodeWithText("Ver en CanalTrans").assertDoesNotExist()
            compose.onNodeWithText("Volver").assertDoesNotExist()
            compose.onNodeWithTag("AudioboomArticleCard").performClick()
            compose.onNodeWithText("Volver").assertIsDisplayed()
            compose.onNodeWithText("Ver en CanalTrans").assertIsDisplayed()
            compose.onNodeWithTag("AudioboomArticleCard").assertDoesNotExist()
            compose.onNodeWithText("Volver").performClick()
            compose.onNodeWithTag("AudioboomArticleCard").assertIsDisplayed()
            compose.onNodeWithText("Descripción Canaltrans").assertIsDisplayed()
            compose.onNodeWithText("Canaltrans").performClick().assertIsSelected()
            compose.onNodeWithTag("CanaltransArticleCard").assertIsDisplayed()
            compose.onNodeWithText("Volver").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { store.clear() }
        }
    }

    @Test
    fun hiddenCanaltransTabLeavesOnlyAudioboomInNavigation() {
        lateinit var reader: RssViewModel
        val store = ViewModelStore()
        compose.runOnUiThread {
            reader = RssViewModel(SavedStateHandle(), canaltransTabEnabled = false) { url ->
                RssFeed("Podcast", emptyList(), url)
            }
            store.put("reader", reader)
        }
        try {
            compose.setContent {
                RssTheme(darkTheme = true, dynamicColor = false) { RssApp(reader) }
            }
            compose.waitUntil(timeoutMillis = 5_000) { !reader.loading && reader.feed != null }
            compose.onNodeWithText("Episodios").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithText("En Caso De Eue El Mundo Se Desintegre").assertIsDisplayed()
            compose.onNodeWithText("Audioboom").assertDoesNotExist()
            compose.onNodeWithText("Canaltrans").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { store.clear() }
        }
    }
}
