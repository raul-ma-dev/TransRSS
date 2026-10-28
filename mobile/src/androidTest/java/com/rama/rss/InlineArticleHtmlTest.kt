package com.rama.rss

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rama.rss.reader.RssEntry
import com.rama.rss.ui.theme.RssTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class InlineArticleHtmlTest {
    @Test
    fun inlineHtmlExpandsToFullContentWithoutEnablingScripts() {
        val entry = RssEntry(
            "Artículo extenso",
            (1..20).joinToString("") { "<p>Párrafo $it con contenido completo y formato <strong>destacado</strong>.</p>" },
            "https://example.com/article",
            "30 de septiembre de 2026"
        )
        assertExpandedInlineContent(entry)
    }

    @Test
    fun newsCardRespectsLargeFontsAndRetainsFullContent() {
        val entry = RssEntry("Texto ampliado", (1..20).joinToString("") { "<p>Párrafo $it de contenido completo.</p>" }, "", "")
        assertExpandedInlineContent(entry, fontScale = 1.5f)
    }

    @Test
    fun shortNewsCardKeepsStableHeightAcrossRedraws() {
        assertExpandedInlineContent(RssEntry("Noticia", "<p>Contenido breve.</p>", "", ""), minimumContentHeight = 1)
    }

    @Test
    fun centeredHttpImageLoadsOverHttpsAndExpandsCardAutomatically() {
        val entry = mutableStateOf(RssEntry("Imagen centrada", "<p>Contenido breve.</p>", "", ""))
        val requestedUrl = AtomicReference<String>()
        val image = Bitmap.createBitmap(48, 180, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.MAGENTA)
        val imageBytes = ByteArrayOutputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        image.recycle()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    RssTheme(darkTheme = true, dynamicColor = false) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item { FeedArticleCard(entry.value, "https://example.com/feed.xml", Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
            val textHeight = awaitStableHeights(scenario).values.single()
            scenario.onActivity { activity ->
                requireNotNull(findWebView(activity.window.decorView)).webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        if (request.url.host != "images.example.com") return super.shouldInterceptRequest(view, request)
                        requestedUrl.set(request.url.toString())
                        return WebResourceResponse("image/png", null, ByteArrayInputStream(imageBytes))
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        view.requestLayout()
                    }
                }
                entry.value = entry.value.copy(
                    html = """<p>Contenido breve.</p><p align="center"><img src="http://images.example.com/centered.png"></p>"""
                )
            }
            val imageHeight = awaitStableHeights(scenario).values.single()
            assertEquals("https://images.example.com/centered.png", requestedUrl.get())
            assertTrue("La imagen cargada debe aumentar el alto natural de la tarjeta", imageHeight > textHeight)
            val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {
                val pixels = IntArray(screenshot.width * screenshot.height)
                screenshot.getPixels(pixels, 0, screenshot.width, 0, 0, screenshot.width, screenshot.height)
                assertTrue("La imagen debe ser visible, no solo estar presente en el HTML", pixels.count { it == Color.MAGENTA } > 1_000)
            } finally {
                screenshot.recycle()
            }
        }
    }

    @Test
    fun newsCardAutomaticallyShrinksWhenContentBecomesShorter() {
        val entry = mutableStateOf(
            RssEntry("Noticia", (1..20).joinToString("") { "<p>Párrafo $it con contenido completo.</p>" }, "", "")
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    RssTheme(darkTheme = true, dynamicColor = false) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item { FeedArticleCard(entry.value, "https://example.com/feed.xml", Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
            val longHeight = awaitStableHeights(scenario).values.single()
            scenario.onActivity { entry.value = entry.value.copy(html = "<p>Contenido breve.</p>") }
            val shortHeight = awaitStableHeights(scenario).values.single()
            assertTrue("El alto debe reducirse automáticamente al reducir el contenido", shortHeight < longHeight)
        }
    }

    @Test
    fun adjacentNewsCardsStaySeparatedAndKeepTheirHeightAfterScrolling() {
        val listState = LazyListState()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    RssTheme(darkTheme = true, dynamicColor = false) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(20) { index ->
                                FeedArticleCard(
                                    RssEntry("Noticia $index", "<p>Contenido breve.</p>", "", ""),
                                    "https://example.com/feed.xml",
                                    Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            val heights = awaitStableHeights(scenario, minimumCards = 2)
            repeat(30) {
                scenario.onActivity { activity ->
                    val views = findWebViews(activity.window.decorView).sortedBy { view ->
                        IntArray(2).also { view.getLocationOnScreen(it) }[1]
                    }
                    views.forEach { view ->
                        val document = (view.tag as Pair<*, *>).first as String
                        heights[document]?.let { assertEquals("La tarjeta no debe crecer entre fotogramas", it, view.height) }
                        view.invalidate()
                    }
                    views.zipWithNext().forEach { (first, second) ->
                        val firstPosition = IntArray(2).also { first.getLocationOnScreen(it) }
                        val secondPosition = IntArray(2).also { second.getLocationOnScreen(it) }
                        assertTrue("Las tarjetas no deben empalmarse", firstPosition[1] + first.height <= secondPosition[1])
                    }
                }
                SystemClock.sleep(100)
            }
            for (index in listOf(15, 0)) {
                scenario.onActivity { activity ->
                    activity.lifecycleScope.launch { listState.scrollToItem(index) }
                }
                var restored = false
                val scrollDeadline = SystemClock.uptimeMillis() + 10_000
                while (!restored && SystemClock.uptimeMillis() < scrollDeadline) {
                    scenario.onActivity { activity ->
                        val view = findWebViews(activity.window.decorView).firstOrNull {
                            ((it.tag as Pair<*, *>).first as String).contains("<h1>Noticia $index</h1>")
                        }
                        if (view != null && view.height > 0) {
                            restored = true
                        }
                    }
                    if (!restored) SystemClock.sleep(50)
                }
                assertTrue(
                    "Debe mostrarse la tarjeta $index después del desplazamiento; " +
                        "visibles=${listState.layoutInfo.visibleItemsInfo.map { Triple(it.index, it.offset, it.size) }}",
                    restored
                )
                val measuredHeights = awaitStableHeights(scenario)
                if (index == 0) {
                    heights.forEach { (document, height) ->
                        measuredHeights[document]?.let {
                            assertEquals("La tarjeta debe recuperar su altura natural al volver a pantalla", height, it)
                        }
                    }
                }
            }
        }
    }

    private fun assertExpandedInlineContent(entry: RssEntry, fontScale: Float = 1f, minimumContentHeight: Int = 500) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    RssTheme(darkTheme = true, dynamicColor = false) {
                        val density = LocalDensity.current.density
                        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                            LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    FeedArticleCard(entry, "https://example.com/feed.xml", Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
            var expanded = false
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (!expanded && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val view = findWebView(activity.window.decorView)
                    if (view != null && view.contentHeight > minimumContentHeight) {
                        expanded = !view.canScrollVertically(1)
                        assertFalse(view.settings.javaScriptEnabled)
                        assertFalse(view.settings.allowFileAccess)
                        assertFalse(view.settings.allowContentAccess)
                        assertFalse(view.isVerticalScrollBarEnabled)
                        assertEquals((fontScale * 100).toInt(), view.settings.textZoom)
                        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, view.layoutParams.height)
                        val document = (view.tag as Pair<*, *>).first as String
                        assertTrue(document.contains("body { font-size: 14.0px; line-height: 20.0px; }"))
                        assertTrue(document.contains(entry.html))
                    }
                }
                if (!expanded) SystemClock.sleep(100)
            }
            assertTrue("La tarjeta debe mostrar todo el HTML sin un scroll vertical interno", expanded)
            val stableHeight = awaitStableHeights(scenario).values.single()
            repeat(30) {
                scenario.onActivity { activity ->
                    val view = requireNotNull(findWebView(activity.window.decorView))
                    assertEquals("La altura no debe crecer al redibujar el mismo HTML", stableHeight.toDouble(), view.height.toDouble(), 2.0)
                    view.invalidate()
                }
                SystemClock.sleep(100)
            }
        }
    }

    private fun findWebView(view: View): ArticleWebView? {
        if (view is ArticleWebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun awaitStableHeights(scenario: ActivityScenario<MainActivity>, minimumCards: Int = 1): Map<String, Int> {
        var previous = emptyMap<String, Int>()
        var stableSamples = 0
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            var current = emptyMap<String, Int>()
            scenario.onActivity { activity ->
                val views = findWebViews(activity.window.decorView)
                if (views.size >= minimumCards && views.all {
                    it.progress == 100 && it.contentHeight > 1 && it.height > 0 && !it.canScrollVertically(1)
                }) {
                    current = views.associate { ((it.tag as Pair<*, *>).first as String) to it.height }
                }
                views.forEach { it.invalidate() }
            }
            stableSamples = if (current.isNotEmpty() && current == previous) stableSamples + 1 else 0
            if (stableSamples >= 15) return current
            previous = current
            SystemClock.sleep(100)
        }
        throw AssertionError("Las tarjetas deben terminar de cargar y estabilizar su altura sin scroll interno")
    }

    private fun findWebViews(view: View): List<ArticleWebView> = when (view) {
        is ArticleWebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { findWebViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
