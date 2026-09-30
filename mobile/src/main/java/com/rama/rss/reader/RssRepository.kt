package com.rama.rss.reader

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class RssEntry(
    val title: String,
    val html: String,
    val link: String,
    val date: String
)

data class RssFeed(val title: String, val entries: List<RssEntry>, val baseUrl: String)

class RssRepository {
    suspend fun load(address: String): RssFeed = withContext(Dispatchers.IO) {
        var url = secureUrl(address.trim())
        repeat(6) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
            }
            try {
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                        ?: error("El servidor redirigió sin indicar una URL.")
                    url = secureUrl(URL(url, location).toString())
                } else {
                    check(code in 200..299) { "No se pudo descargar el feed (HTTP $code)." }
                    val bytes = connection.inputStream.use { it.readBytesLimited() }
                    return@withContext RssFeedParser.parse(ByteArrayInputStream(bytes), url.toString())
                }
            } finally {
                connection.disconnect()
            }
        }
        error("El feed tiene demasiadas redirecciones.")
    }

    private fun secureUrl(value: String): URL {
        val url = try {
            URL(value)
        } catch (_: Exception) {
            throw IllegalArgumentException("Introduce una URL válida que comience por https://.")
        }
        require(url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null) {
            "Introduce una URL HTTPS sin credenciales."
        }
        return url
    }

    private fun InputStream.readBytesLimited(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count == -1) break
            require(output.size() + count <= 5 * 1024 * 1024) { "El feed supera el límite de 5 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}

object RssFeedParser {
    fun parse(input: InputStream, baseUrl: String): RssFeed {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, null)
        }
        var title = ""
        var validRoot = false
        val entries = mutableListOf<RssEntry>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            require(parser.eventType != XmlPullParser.DOCDECL) { "No se permiten declaraciones DTD en el feed." }
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.depth == 1) {
                    validRoot = parser.name in listOf("rss", "feed", "RDF")
                    require(validRoot) { "La URL no contiene un feed RSS o Atom." }
                }
                when (parser.name) {
                    "item", "entry" -> {
                        if (entries.size < 300) entries += readEntry(parser, baseUrl)
                    }
                    "title" -> if (title.isBlank()) title = readValue(parser)
                }
            }
            parser.nextToken()
        }
        require(validRoot) { "El feed está vacío o no es XML válido." }
        return RssFeed(title.ifBlank { "Feed RSS" }, entries, baseUrl)
    }

    private fun readEntry(parser: XmlPullParser, baseUrl: String): RssEntry {
        val depth = parser.depth
        var title = ""
        var summary = ""
        var content = ""
        var link = ""
        var date = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG || parser.depth != depth + 1) continue
            when (parser.name) {
                "title" -> title = readValue(parser)
                "description", "summary" -> summary = readValue(parser)
                "encoded", "content" -> content = readValue(parser)
                "pubDate", "published", "date" -> date = readValue(parser)
                "updated" -> if (date.isBlank()) date = readValue(parser)
                "link" -> {
                    val href = parser.getAttributeValue(null, "href")
                    val rel = parser.getAttributeValue(null, "rel")
                    if (href == null) link = readValue(parser)
                    else if (rel == null || rel == "alternate") link = href
                }
            }
        }
        val resolvedLink = runCatching { URL(URL(baseUrl), link.trim()).toString() }
            .getOrDefault("").takeIf { link.isNotBlank() && (it.startsWith("https://") || it.startsWith("http://")) }.orEmpty()
        return RssEntry(title.ifBlank { "Sin título" }, content.ifBlank { summary }, resolvedLink, date)
    }

    // RSS escapes HTML or uses CDATA; Atom may also contain nested XHTML.
    private fun readValue(parser: XmlPullParser): String {
        val depth = parser.depth
        val text = StringBuilder()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.END_TAG -> {
                    if (parser.depth == depth) return text.toString().trim()
                    text.append("</").append(parser.name).append('>')
                }
                XmlPullParser.START_TAG -> {
                    text.append('<').append(parser.name)
                    for (index in 0 until parser.attributeCount) {
                        text.append(' ').append(parser.getAttributeName(index)).append("=\"")
                            .append(escapeHtml(parser.getAttributeValue(index))).append('"')
                    }
                    text.append('>')
                }
                XmlPullParser.TEXT -> text.append(
                    if (parser.depth > depth) escapeHtml(parser.text) else parser.text
                )
            }
        }
        error("El XML del feed está incompleto.")
    }
}

internal fun escapeHtml(value: String): String = value.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
