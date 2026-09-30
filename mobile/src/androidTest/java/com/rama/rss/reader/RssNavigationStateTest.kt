package com.rama.rss.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class RssNavigationStateTest {
    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private fun sample(url: String) = RssFeed(url, emptyList(), url)

    @Test
    fun switchingFeedsUsesCacheAndRefreshesBothSourcesForAudioboom() = onMain {
        val downloads = mutableListOf<String>()
        val saved = SavedStateHandle()
        val reader = RssViewModel(saved, canaltransEnabled = true) { url -> downloads += url; sample(url) }
        val store = ViewModelStore().apply { put("reader", reader) }
        try {
            assertEquals(RssSource.CANALTRANS.url, reader.feed?.baseUrl)
            reader.selectSource(RssSource.AUDIOBOOM)
            assertEquals(RssSource.AUDIOBOOM.url, reader.feed?.baseUrl)
            reader.refresh()
            reader.selectSource(RssSource.CANALTRANS)
            assertEquals(RssSource.CANALTRANS.url, reader.feed?.baseUrl)
            assertEquals(
                listOf(
                    RssSource.CANALTRANS.url, RssSource.AUDIOBOOM.url,
                    RssSource.CANALTRANS.url, RssSource.AUDIOBOOM.url
                ),
                downloads
            )
            assertEquals(RssSource.CANALTRANS.name, saved.get<String>("selectedSource"))
        } finally {
            store.clear()
        }
    }

    @Test
    fun backgroundDownloadCannotReplaceSelectedFeed() = onMain {
        val pending = RssSource.entries.associateWith { CompletableDeferred<RssFeed>() }
        val reader = RssViewModel(SavedStateHandle(), canaltransEnabled = true) { url ->
            pending.getValue(RssSource.entries.first { it.url == url }).await()
        }
        val store = ViewModelStore().apply { put("reader", reader) }
        try {
            reader.selectSource(RssSource.AUDIOBOOM)
            pending.getValue(RssSource.CANALTRANS).complete(sample(RssSource.CANALTRANS.url))
            assertTrue(reader.loading)
            assertNull(reader.feed)
            pending.getValue(RssSource.AUDIOBOOM).complete(sample(RssSource.AUDIOBOOM.url))
            assertFalse(reader.loading)
            assertEquals(RssSource.AUDIOBOOM.url, reader.feed?.baseUrl)
            reader.selectSource(RssSource.CANALTRANS)
            assertEquals(RssSource.CANALTRANS.url, reader.feed?.baseUrl)
        } finally {
            store.clear()
        }
    }

    @Test
    fun errorsAreIsolatedAndSavedSelectionIsRestored() = onMain {
        val reader = RssViewModel(SavedStateHandle(), canaltransEnabled = true) { url ->
            if (url == RssSource.AUDIOBOOM.url) throw IOException("Sin conexión")
            sample(url)
        }
        val restored = RssViewModel(
            SavedStateHandle(mapOf("selectedSource" to RssSource.AUDIOBOOM.name)),
            canaltransEnabled = true
        ) { sample(it) }
        val store = ViewModelStore().apply { put("reader", reader); put("restored", restored) }
        try {
            reader.selectSource(RssSource.AUDIOBOOM)
            assertNotNull(reader.error)
            reader.selectSource(RssSource.CANALTRANS)
            assertNull(reader.error)
            assertNotNull(reader.feed)
            assertEquals(RssSource.AUDIOBOOM, restored.selectedSource)
            assertEquals(RssSource.AUDIOBOOM.url, restored.feed?.baseUrl)
        } finally {
            store.clear()
        }
    }

    @Test
    fun radioMovesToAudioboomAndRefreshRebuildsGrouping() = onMain {
        var radioCategory = true
        val news = RssEntry("Noticia", "", "", "")
        val radio = RssEntry("Radio", "<p>Información Canaltrans</p>", "", "Wed, 30 Sep 2026 01:11:10 -0300")
        val podcast = RssEntry(
            "Podcast", "<p>No usar esta descripción</p>", "", "Wed, 30 Sep 2026 03:11:00 +0000",
            audioUrl = "https://example.com/podcast.mp3"
        )
        val reader = RssViewModel(SavedStateHandle(), canaltransEnabled = true) { url ->
            if (url == RssSource.CANALTRANS.url) {
                sample(url).copy(entries = listOf(news, radio.copy(categories = if (radioCategory) listOf("Radio") else emptyList())))
            } else sample(url).copy(entries = listOf(podcast))
        }
        val store = ViewModelStore().apply { put("reader", reader) }
        try {
            assertEquals(listOf("Noticia"), reader.feed?.entries?.map { it.title })
            reader.selectSource(RssSource.AUDIOBOOM)
            assertEquals(listOf("Radio"), reader.feed?.entries?.map { it.title })
            assertEquals(podcast.audioUrl, reader.feed?.entries?.single()?.audioUrl)
            assertEquals(radio.html, reader.feed?.entries?.single()?.html)
            radioCategory = false
            reader.refresh()
            assertTrue(reader.feed!!.entries.isEmpty())
            reader.selectSource(RssSource.CANALTRANS)
            assertEquals(listOf("Noticia", "Radio"), reader.feed?.entries?.map { it.title })
        } finally {
            store.clear()
        }
    }

    @Test
    fun radioRemainsAvailableWhenAudioboomFails() = onMain {
        val radio = RssEntry("Radio", "<p>Descripción</p>", "", "", categories = listOf("Radio"))
        val reader = RssViewModel(SavedStateHandle(), canaltransEnabled = true) { url ->
            if (url == RssSource.AUDIOBOOM.url) throw IOException("Sin conexión")
            sample(url).copy(entries = listOf(radio))
        }
        val store = ViewModelStore().apply { put("reader", reader) }
        try {
            assertTrue(reader.feed!!.entries.isEmpty())
            reader.selectSource(RssSource.AUDIOBOOM)
            assertEquals(listOf("Radio"), reader.feed?.entries?.map { it.title })
            assertEquals(RssSource.CANALTRANS.url, reader.feed?.entries?.single()?.baseUrl)
            assertTrue(reader.error!!.contains("Audioboom:"))
            assertFalse(reader.loading)
        } finally {
            store.clear()
        }
    }

    @Test
    fun audioboomOnlyNeverDownloadsCanaltransEvenWithOldSavedSelection() = onMain {
        val saved = SavedStateHandle(mapOf("selectedSource" to RssSource.CANALTRANS.name))
        val downloads = mutableListOf<String>()
        val original = sample(RssSource.AUDIOBOOM.url).copy(
            entries = listOf(RssEntry("Podcast original", "<p>Datos de Audioboom</p>", "", ""))
        )
        val reader = RssViewModel(saved, canaltransEnabled = false) { url ->
            assertEquals(RssSource.AUDIOBOOM.url, url)
            downloads += url
            original
        }
        val store = ViewModelStore().apply { put("reader", reader) }
        try {
            assertEquals(listOf(RssSource.AUDIOBOOM), reader.availableSources)
            assertEquals(RssSource.AUDIOBOOM, reader.selectedSource)
            assertEquals(RssSource.AUDIOBOOM.name, saved.get<String>("selectedSource"))
            assertSame(original, reader.feed)
            reader.selectSource(RssSource.CANALTRANS)
            assertEquals(RssSource.AUDIOBOOM, reader.selectedSource)
            reader.refresh()
            assertEquals(listOf(RssSource.AUDIOBOOM.url, RssSource.AUDIOBOOM.url), downloads)
        } finally {
            store.clear()
        }
    }

    @Test
    fun audioboomOnlyDoesNotFallbackToCanaltransAfterNetworkError() = onMain {
        val downloads = mutableListOf<String>()
        val reader = RssViewModel(SavedStateHandle(), canaltransEnabled = false) { url ->
            downloads += url
            throw IOException("Sin conexión")
        }
        val store = ViewModelStore().apply { put("reader", reader) }
        try {
            assertNotNull(reader.error)
            assertNull(reader.feed)
            reader.selectSource(RssSource.CANALTRANS)
            reader.refresh()
            assertEquals(listOf(RssSource.AUDIOBOOM.url, RssSource.AUDIOBOOM.url), downloads)
        } finally {
            store.clear()
        }
    }
}
