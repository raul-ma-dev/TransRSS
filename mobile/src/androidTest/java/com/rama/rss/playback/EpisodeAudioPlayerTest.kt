package com.rama.rss.playback

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rama.rss.Article
import com.rama.rss.ArticleHtmlContent
import com.rama.rss.R
import com.rama.rss.RocketLoadingIndicator
import com.rama.rss.reader.RssEntry
import com.rama.rss.ui.theme.RssTheme
import java.util.concurrent.TimeUnit
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpisodeAudioPlayerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun inspectionModeRendersLocalHtmlAudioAndLoadingWithoutConnectingToServices() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previewContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun bindService(service: Intent, connection: ServiceConnection, flags: Int): Boolean {
                throw AssertionError("El preview no debe conectarse a un servicio")
            }
        }
        val entry = RssEntry("Artículo de muestra", "<p>Texto local del preview</p>", "", "")
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true, LocalContext provides previewContext) {
                RssTheme(darkTheme = true, dynamicColor = false) {
                    Column {
                        ArticleHtmlContent(entry, "https://example.com/feed.xml", fitContent = true)
                        EpisodeAudioPlayer(entry, "https://example.com/episode.mp3")
                        RocketLoadingIndicator(animating = true, modifier = Modifier.size(100.dp))
                    }
                }
            }
        }
        compose.onNodeWithText("Artículo de muestra").assertExists()
        compose.onNodeWithText("Texto local del preview").assertExists()
        compose.onNodeWithText("01:30 / 30:00").assertExists()
        compose.onNodeWithText(context.getString(R.string.playback_connecting)).assertDoesNotExist()
        compose.onNodeWithTag("RocketLaunchScreen").assertDoesNotExist()
    }

    @Test
    fun nativePlayerIsBelowTheCoverAndAboveTheRemainingDescription() {
        val image = Bitmap.createBitmap(48, 80, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.MAGENTA)
        val encoded = ByteArrayOutputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
            Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)
        }
        image.recycle()
        val entry = RssEntry(
            "Episodio con portada",
            """<p align="center"><img src="data:image/png;base64,$encoded"></p><p>Texto después de la imagen</p>""",
            "", "", audioUrl = "https://example.com/episode.mp3"
        )
        compose.setContent {
            RssTheme(darkTheme = true, dynamicColor = false) {
                Article(entry, "https://example.com/feed.xml", onBack = {})
            }
        }
        compose.waitUntil(10_000) {
            val bounds = compose.onNodeWithTag("EpisodeContentBeforePlayer").getUnclippedBoundsInRoot()
            bounds.bottom - bounds.top > 100.dp
        }
        compose.onNodeWithTag("EpisodeAudioPlayer").performScrollTo()
        val imageBounds = compose.onNodeWithTag("EpisodeContentBeforePlayer").getUnclippedBoundsInRoot()
        val playerBounds = compose.onNodeWithTag("EpisodeAudioPlayer").getUnclippedBoundsInRoot()
        val descriptionBounds = compose.onNodeWithTag("EpisodeContentAfterPlayer").getUnclippedBoundsInRoot()
        assertTrue("El reproductor debe quedar debajo de la imagen", playerBounds.top >= imageBounds.bottom)
        assertTrue("La descripción debe continuar debajo del reproductor", descriptionBounds.top >= playerBounds.bottom)
    }

    @Test
    fun episodeDetailShowsNativeControlsWithoutStartingAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = RssEntry(
            "Episodio de prueba", "<p>Descripción del episodio</p>", "", "",
            audioUrl = "https://example.com/episode.mp3"
        )
        compose.setContent {
            RssTheme(darkTheme = true, dynamicColor = false) {
                Article(entry, "https://example.com/feed.xml", onBack = {})
            }
        }
        compose.waitUntil(10_000) {
            compose.onNodeWithText("Reproducir").fetchSemanticsNode().config.let {
                !it.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
        compose.onNodeWithText("Reproductor de audio").assertIsDisplayed()
        compose.onNodeWithText("Reproducir").assertIsEnabled()
        compose.onNodeWithText("Detener").assertIsDisplayed()
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
        compose.runOnIdle {
            future = MediaController.Builder(
                context, SessionToken(context, ComponentName(context, EpisodePlaybackService::class.java))
            ).buildAsync()
        }
        val controller = future.get(10, TimeUnit.SECONDS)
        try {
            compose.runOnIdle {
                assertFalse(controller.isPlaying)
                assertEquals(0, controller.mediaItemCount)
            }
        } finally {
            compose.runOnIdle { controller.release() }
        }
    }
}
