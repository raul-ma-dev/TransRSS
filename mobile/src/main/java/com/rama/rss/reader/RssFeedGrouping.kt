package com.rama.rss.reader

import java.time.Duration
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
    val episodes = audioboom?.entries.orEmpty().mapNotNull { entry ->
        if (entry.audioUrl.isBlank()) null else parsedDate(entry.date)?.let { it to entry }
    }
    val audioByZone = mutableMapOf<ZoneId, Map<LocalDate, List<Pair<ZonedDateTime, RssEntry>>>>()
    val entries = radioEntries.map { radio ->
        val date = parsedDate(radio.date)
        val match = date?.let {
            val audioByDay = audioByZone.getOrPut(date.zone) {
                episodes.groupBy { (published, _) -> published.withZoneSameInstant(date.zone).toLocalDate() }
            }
            audioByDay[date.toLocalDate()]?.minByOrNull { (published, _) ->
                Duration.between(date.toInstant(), published.toInstant()).abs()
            }?.second
        }
        radio.withBaseUrl(canaltrans.baseUrl).copy(audioUrl = match?.audioUrl.orEmpty())
    }
    return RssFeed(
        title = RssSource.AUDIOBOOM.title,
        entries = entries.sortedByDescending { publicationTime(it.date) },
        baseUrl = RssSource.AUDIOBOOM.url
    )
}

private fun RssEntry.withBaseUrl(origin: String): RssEntry =
    if (baseUrl.isBlank()) copy(baseUrl = origin) else this

private fun parsedDate(date: String): ZonedDateTime? = runCatching {
    ZonedDateTime.parse(date.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
}.recoverCatching {
    OffsetDateTime.parse(date.trim()).toZonedDateTime()
}.getOrNull()

private fun publicationTime(date: String): Long = parsedDate(date)?.toInstant()?.toEpochMilli() ?: Long.MIN_VALUE
