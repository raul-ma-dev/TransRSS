package com.rama.rss.reader

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ReaderDateOffset = ZoneOffset.ofHours(-6)
private val ReaderDateFormat = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("es"))

/** Display only: the original RSS date and its timezone remain intact for matching. */
internal fun formatRssDate(date: String): String {
    val parsed = parsedDate(date) ?: return date.trim()
    return parsed.withZoneSameInstant(ReaderDateOffset).format(ReaderDateFormat)
}

