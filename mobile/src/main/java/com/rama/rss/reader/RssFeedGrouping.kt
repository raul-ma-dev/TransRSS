package com.rama.rss.reader

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal fun RssEntry.isRadio(): Boolean = categories.any { it.trim().equals("Radio", ignoreCase = true) }

internal fun canaltransWithoutRadio(feed: RssFeed?): RssFeed? = feed?.copy(
    entries = feed.entries.filterNot { it.isRadio() }
)

internal fun audioboomWithRadio(canaltrans: RssFeed?, audioboom: RssFeed?): RssFeed? {
    if (canaltrans == null) return null
    val radioEntries = canaltrans.entries.filter { it.isRadio() }
    if (audioboom == null && radioEntries.isEmpty()) return null
    val episodes = audioboom?.entries.orEmpty().mapIndexedNotNull { index, entry ->
        if (entry.audioUrl.isBlank()) null else parsedDate(entry.date)?.let {
            TimedEpisode(it.toInstant(), entry, index)
        }
    }.distinctBy { it.time }.sortedBy { it.time }
    val audioByZone = mutableMapOf<ZoneId, Map<LocalDate, List<TimedEpisode>>>()
    val entries = radioEntries.map { radio ->
        val date = parsedDate(radio.date)
        val match = date?.let {
            val audioByDay = audioByZone.getOrPut(date.zone) {
                episodes.groupBy { it.time.atZone(date.zone).toLocalDate() }
            }
            audioByDay[date.toLocalDate()]?.closestTo(date.toInstant())
        }
        date?.toInstant() to radio.withBaseUrl(canaltrans.baseUrl).copy(audioUrl = match?.audioUrl.orEmpty())
    }
    return RssFeed(
        title = RssSource.AUDIOBOOM.title,
        entries = entries.sortedWith(compareByDescending { it.first }).map { it.second },
        baseUrl = RssSource.AUDIOBOOM.url
    )
}

private data class TimedEpisode(val time: Instant, val entry: RssEntry, val originalIndex: Int)

private fun List<TimedEpisode>.closestTo(time: Instant): RssEntry? {
    var low = 0
    var high = size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (this[middle].time < time) low = middle + 1 else high = middle
    }
    return listOfNotNull(getOrNull(low - 1), getOrNull(low)).minWithOrNull(
        compareBy<TimedEpisode> { Duration.between(time, it.time).abs() }
            .thenBy { it.originalIndex }
    )?.entry
}

private fun RssEntry.withBaseUrl(origin: String): RssEntry =
    if (baseUrl.isBlank()) copy(baseUrl = origin) else this

internal fun parsedDate(date: String): ZonedDateTime? = runCatching {
    ZonedDateTime.parse(date.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
}.recoverCatching {
    OffsetDateTime.parse(date.trim()).toZonedDateTime()
}.getOrNull()

