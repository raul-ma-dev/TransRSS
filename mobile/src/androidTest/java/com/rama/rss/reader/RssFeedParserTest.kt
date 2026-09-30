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
}
