package com.rama.rss

import android.text.Html
import com.rama.rss.reader.RssEntry
import java.net.URL

private val DescriptionHeading = Regex(
    """<h1\b(?:[^"'<>]|"[^"]*"|'[^']*')*>.*?</h1\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val DescriptionImage = Regex(
    """<img\b(?:[^"'<>]|"[^"]*"|'[^']*')*>""",
    RegexOption.IGNORE_CASE
)
private val DescriptionAudio = Regex(
    """<audio\b(?:[^"'<>]|"[^"]*"|'[^']*')*>.*?</audio\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val AudioSource = Regex(
    """<(?:audio|source)\b(?:[^"'<>]|"[^"]*"|'[^']*')*>""",
    RegexOption.IGNORE_CASE
)
private val SourceAttribute = Regex(
    """(?:^|\s)src\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""",
    RegexOption.IGNORE_CASE
)

internal fun episodeAudioUrl(entry: RssEntry, baseUrl: String): String {
    val embeddedAudio = DescriptionAudio.find(entry.html)?.value.orEmpty()
    val embeddedSource = AudioSource.findAll(embeddedAudio).mapNotNull { tag ->
        SourceAttribute.find(tag.value)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
    }.firstOrNull().orEmpty()
    val address = entry.audioUrl.ifBlank {
        Html.fromHtml(embeddedSource, Html.FROM_HTML_MODE_COMPACT).toString()
    }.trim()
    if (address.isBlank()) return ""
    val url = if (baseUrl.isBlank()) URL(address) else URL(URL(baseUrl), address)
    require(url.protocol in listOf("https", "http") && url.host.isNotBlank() && url.userInfo == null) {
        "El episodio no incluye una dirección de audio HTTPS válida."
    }
    return url.toString().replaceFirst(Regex("^http:", RegexOption.IGNORE_CASE), "https:")
}

internal fun withoutAudioPlayers(html: String): String = DescriptionAudio.replace(html, "")

internal fun episodeCoverUrl(html: String, baseUrl: String): String {
    var cover = ""
    Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT, Html.ImageGetter { source ->
        if (cover.isBlank()) cover = safeImageUrl(source.orEmpty(), baseUrl)
        null
    }, null)
    return cover
}

internal fun withoutDescriptionHeading(html: String): String = DescriptionHeading.replace(html, "")

internal fun playerAfterFirstImage(html: String, player: String): String {
    if (player.isBlank()) return html
    val (before, after) = splitAfterFirstImage(html)
    return "$before\n$player\n$after"
}

internal fun splitAfterFirstImage(html: String): Pair<String, String> {
    val position = DescriptionImage.find(html)?.range?.last?.plus(1) ?: html.length
    return html.substring(0, position) to html.substring(position)
}

private fun safeImageUrl(source: String, baseUrl: String): String {
    val address = source.trim()
    if (address.isBlank()) return ""
    if (address.startsWith("data:image/", ignoreCase = true)) return address
    return runCatching {
        val url = if (baseUrl.isBlank()) URL(address) else URL(URL(baseUrl), address)
        url.takeIf { it.protocol in listOf("https", "http") && it.host.isNotBlank() && it.userInfo == null }
            ?.toString()?.replaceFirst(Regex("^http:", RegexOption.IGNORE_CASE), "https:").orEmpty()
    }.getOrDefault("")
}
