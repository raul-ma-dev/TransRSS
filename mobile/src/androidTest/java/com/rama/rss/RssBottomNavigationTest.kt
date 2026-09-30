package com.rama.rss

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rama.rss.reader.RssEntry
import com.rama.rss.reader.RssFeed
import com.rama.rss.reader.RssSource
import com.rama.rss.reader.RssViewModel
import com.rama.rss.ui.theme.RssTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssBottomNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun canaltransHasInlineCardsAndOnlyAudioboomOpensDetails() {
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
            compose.onNodeWithText("Canaltrans").assertIsSelected()
            compose.onNodeWithTag("CanaltransArticleCard")
                .assertIsDisplayed()
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            compose.onNodeWithText("Abrir original").assertIsDisplayed()
            compose.onNodeWithText("Volver").assertDoesNotExist()
            compose.onNodeWithText("Audioboom").performClick().assertIsSelected()
            compose.onNodeWithText("Radio Canaltrans").performClick()
            compose.onNodeWithText("Volver").assertIsDisplayed()
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
            compose.onNodeWithText("Audioboom").assertIsDisplayed().assertIsSelected()
            compose.onNodeWithText("Canaltrans").assertDoesNotExist()
        } finally {
            compose.runOnUiThread { store.clear() }
        }
    }
}
