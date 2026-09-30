package com.rama.rss

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rama.rss.reader.RssEntry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleAppearanceTest {
    private val entry = RssEntry("Noticia", "<p>Contenido</p>", "", "30/09/2026")

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
}
