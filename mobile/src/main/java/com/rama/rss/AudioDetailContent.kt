package com.rama.rss

import android.text.Html
import java.net.URL

private val DescriptionHeading = Regex(
    """<h1\b(?:[^"'<>]|"[^"]*"|'[^']*')*>.*?</h1\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val DescriptionImage = Regex(
    """<img\b(?:[^"'<>]|"[^"]*"|'[^']*')*>""",
    RegexOption.IGNORE_CASE
)

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
    val position = DescriptionImage.find(html)?.range?.last?.plus(1) ?: html.length
    return html.substring(0, position) + "\n" + player + "\n" + html.substring(position)
}

private fun safeImageUrl(source: String, baseUrl: String): String {
    val address = source.trim()
    if (address.isBlank()) return ""
    if (address.startsWith("data:image/", ignoreCase = true)) return address
    return runCatching {
        val url = if (baseUrl.isBlank()) URL(address) else URL(URL(baseUrl), address)
        url.takeIf { it.protocol == "https" && it.host.isNotBlank() && it.userInfo == null }
            ?.toString().orEmpty()
    }.getOrDefault("")
}

