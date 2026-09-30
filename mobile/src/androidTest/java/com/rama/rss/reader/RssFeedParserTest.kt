package com.rama.rss.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssFeedParserTest {
    private fun parse(xml: String): RssFeed = RssFeedParser.parse(
        xml.byteInputStream(), "https://example.com/feed.xml"
    )

    @Test
    fun rssUsesFullContentAndResolvesRelativeLinks() {
        val feed = parse("""
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel><title>Noticias</title><item>
                <title>Una noticia</title><description>Resumen</description>
                <content:encoded><![CDATA[<p>Contenido <strong>completo</strong></p>]]></content:encoded>
                <link>/noticia</link><pubDate>Wed, 30 Sep 2026 12:00:00 GMT</pubDate>
              </item></channel>
            </rss>
        """.trimIndent())
        assertEquals("Noticias", feed.title)
        assertEquals(1, feed.entries.size)
        assertEquals("<p>Contenido <strong>completo</strong></p>", feed.entries.single().html)
        assertEquals("https://example.com/noticia", feed.entries.single().link)
        assertTrue(feed.entries.single().date.isNotBlank())
    }

    @Test
    fun atomSupportsXhtmlAndAlternateLinks() {
        val feed = parse("""
            <feed xmlns="http://www.w3.org/2005/Atom"><title>Atom</title><entry>
              <title>Artículo</title><link rel="self" href="/api/1"/>
              <link rel="alternate" href="/article"/>
              <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Hola &amp; adiós</p></div></content>
            </entry></feed>
        """.trimIndent())
        assertEquals("Atom", feed.title)
        assertEquals("https://example.com/article", feed.entries.single().link)
        assertTrue(feed.entries.single().html.contains("<p>Hola &amp; adiós</p>"))
    }

    @Test
    fun escapedHtmlAndEmptyFeedsAreSupported() {
        val feed = parse("<rss><channel><item><description>&lt;p&gt;Hola&lt;/p&gt;</description></item></channel></rss>")
        assertEquals("<p>Hola</p>", feed.entries.single().html)
        assertEquals("Sin título", feed.entries.single().title)
        assertTrue(parse("<rss><channel><title>Vacío</title></channel></rss>").entries.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHtmlPages() {
        parse("<html><body>No es RSS</body></html>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDoctypes() {
        parse("<!DOCTYPE rss [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><rss><channel/></rss>")
    }

    @Test
    fun ignoresUnsafeArticleLinks() {
        val feed = parse("<rss><channel><item><link>javascript:alert(1)</link></item></channel></rss>")
        assertEquals("", feed.entries.single().link)
    }

    @Test
    fun extractsMp3EnclosureWithQueryParameters() {
        val feed = parse("""
            <rss><channel><item><title>Episodio</title>
              <enclosure type="audio/mpeg" url="https://audioboom.com/posts/8958622.mp3?modified=1790728641&amp;sid=3716163&amp;source=rss"/>
            </item></channel></rss>
        """.trimIndent())
        assertEquals(
            "https://audioboom.com/posts/8958622.mp3?modified=1790728641&sid=3716163&source=rss",
            feed.entries.single().audioUrl
        )
    }

    @Test
    fun preservesEmbeddedAudioInDescription() {
        val audio = """<audio controls="controls" preload="none" src="https://example.com/episode.mp3"></audio>"""
        val feed = parse("<rss><channel><item><description><![CDATA[$audio]]></description></item></channel></rss>")
        assertEquals(audio, feed.entries.single().html)
    }

    @Test
    fun extractsAtomAudioEnclosure() {
        val feed = parse("""
            <feed xmlns="http://www.w3.org/2005/Atom"><entry>
              <link rel="enclosure" type="audio/mpeg" href="/episode.mp3"/>
            </entry></feed>
        """.trimIndent())
        assertEquals("https://example.com/episode.mp3", feed.entries.single().audioUrl)
    }

    @Test
    fun rejectsUnsafeAudioAndIgnoresNonAudioAttachments() {
        val feed = parse("""
            <rss><channel><item>
              <enclosure type="image/png" url="https://example.com/image.png"/>
              <enclosure type="audio/mpeg" url="http://example.com/episode.mp3"/>
              <enclosure type="audio/mpeg" url="javascript:alert(1)"/>
            </item></channel></rss>
        """.trimIndent())
        assertEquals("", feed.entries.single().audioUrl)
    }

    @Test
    fun readsMultipleRssCategoriesAndKeepsEntryOrigin() {
        val feed = parse("""
            <rss><channel><item><title>Radio</title>
              <category> Radio </category><category>Podcast</category><category>Radio</category>
              <description><![CDATA[<img src="/cover.png"/>]]></description>
            </item><item><title>Sin categoría</title></item></channel></rss>
        """.trimIndent())
        assertEquals(listOf("Radio", "Podcast"), feed.entries.first().categories)
        assertEquals("https://example.com/feed.xml", feed.entries.first().baseUrl)
        assertTrue(feed.entries.last().categories.isEmpty())
    }

    @Test
    fun readsAtomCategoryTerms() {
        val feed = parse("""
            <feed xmlns="http://www.w3.org/2005/Atom"><entry>
              <category term="Radio"/><category term="Podcast"/>
            </entry></feed>
        """.trimIndent())
        assertEquals(listOf("Radio", "Podcast"), feed.entries.single().categories)
    }

    @Test
    fun retainsAllEntriesForPaginationBeyondThreeHundred() {
        val items = (1..325).joinToString("") { "<item><title>Entrada $it</title></item>" }
        val xml = "<rss><channel><title>Archivo</title>$items</channel></rss>"
        val feed = LimitedFeedInputStream(xml.byteInputStream()).buffered().use {
            RssFeedParser.parse(it, "https://example.com/feed.xml")
        }
        assertEquals(325, feed.entries.size)
        assertEquals("Entrada 325", feed.entries.last().title)
    }
}
