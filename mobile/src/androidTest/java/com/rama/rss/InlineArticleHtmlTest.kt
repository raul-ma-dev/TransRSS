package com.rama.rss

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rama.rss.reader.RssEntry
import com.rama.rss.ui.theme.RssTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    RssTheme(darkTheme = true, dynamicColor = false) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item { ArticleHtmlContent(entry, "https://example.com/feed.xml", Modifier.fillMaxWidth(), fitContent = true) }
                        }
                    }
                }
            }
            var expanded = false
            val deadline = SystemClock.uptimeMillis() + 15_000
            while (!expanded && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val view = findWebView(activity.window.decorView)
                    if (view != null && view.contentHeight > 500) {
                        val expected = ceil(view.contentHeight * view.resources.displayMetrics.density.toDouble()).toInt()
                        expanded = view.height >= expected - 2
                        assertFalse(view.settings.javaScriptEnabled)
                        assertFalse(view.settings.allowFileAccess)
                        assertFalse(view.settings.allowContentAccess)
                        assertFalse(view.isVerticalScrollBarEnabled)
                    }
                }
                if (!expanded) SystemClock.sleep(100)
            }
            assertTrue("La tarjeta debe mostrar todo el HTML sin un scroll vertical interno", expanded)
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
}
