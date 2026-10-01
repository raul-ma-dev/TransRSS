package com.rama.rss.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZonedDateTime

class RssFeedGroupingTest {
    private fun entry(title: String, categories: List<String> = emptyList(), date: String = "") =
        RssEntry(title, "<p>$title</p>", "https://example.com/$title", date, categories = categories)

    @Test
    fun preservesOriginalFeedOrderForEquidistantAndDuplicateAudioDates() {
        val radio = entry("Radio", listOf("Radio"), "2026-09-30T12:00:00Z")
        val later = entry("Primero", date = "2026-09-30T13:00:00Z")
            .copy(audioUrl = "https://example.com/first.mp3")
        val earlier = entry("Segundo", date = "2026-09-30T11:00:00Z")
            .copy(audioUrl = "https://example.com/second.mp3")
        val duplicate = later.copy(audioUrl = "https://example.com/duplicate.mp3")
        val result = audioboomWithRadio(
            RssFeed("Canaltrans", listOf(radio), RssSource.CANALTRANS.url),
            RssFeed("Audioboom", listOf(later, duplicate, earlier), RssSource.AUDIOBOOM.url)
        )!!
        assertEquals(later.audioUrl, result.entries.single().audioUrl)
    }

    @Test
    fun indexedMatchingAgreesWithExhaustiveSearchForLargeFeeds() {
        val start = ZonedDateTime.parse("2026-09-01T00:00:00-03:00")
        val radioEntries = (0 until 500).map { index ->
            val date = start.plusMinutes(index * 41L)
                .withZoneSameInstant(java.time.ZoneOffset.ofHours(if (index % 2 == 0) -3 else -4))
            entry("Radio $index", listOf("Radio"), date.toString())
        }
        val audioEntries = (0 until 1000).reversed().map { index ->
            entry("Audio $index", date = start.plusMinutes(index * 23L).toString())
                .copy(audioUrl = "https://example.com/$index.mp3")
        }
        val result = audioboomWithRadio(
            RssFeed("Canaltrans", radioEntries, RssSource.CANALTRANS.url),
            RssFeed("Audioboom", audioEntries, RssSource.AUDIOBOOM.url)
        )!!
        val parsedAudio = audioEntries.map { ZonedDateTime.parse(it.date) to it }
        val expected = radioEntries.associate { radio ->
            val date = ZonedDateTime.parse(radio.date)
            radio.title to parsedAudio.filter { (published, _) ->
                published.withZoneSameInstant(date.zone).toLocalDate() == date.toLocalDate()
            }.minByOrNull { (published, _) ->
                Duration.between(date.toInstant(), published.toInstant()).abs()
            }?.second?.audioUrl.orEmpty()
        }
        assertEquals(expected, result.entries.associate { it.title to it.audioUrl })
        assertEquals(500, result.entries.size)
        assertEquals(
            radioEntries.sortedByDescending { ZonedDateTime.parse(it.date).toInstant() }.map { it.title },
            result.entries.map { it.title }
        )
    }

    @Test
    fun onlyEntriesWithRadioCategoryMoveOutOfCanaltrans() {
        val news = entry("Noticias", listOf("Actualidad, Noticias"))
        val radio = entry("Radio", listOf("Podcast", " Radio "))
        val similar = entry("Otro", listOf("Radio noticias"))
        val uncategorized = entry("Sin categoría")
        val original = RssFeed("Canaltrans", listOf(news, radio, similar, uncategorized), RssSource.CANALTRANS.url)
        assertEquals(listOf(news, similar, uncategorized), canaltransWithoutRadio(original)?.entries)
        assertEquals(4, original.entries.size)
    }

    @Test
    fun combinesCanaltransInformationWithAudioboomAudioByDay() {
        val radio = entry("Radio", listOf("radio"), "Wed, 30 Sep 2026 01:11:10 -0300")
            .copy(audioUrl = "https://example.com/old-radio.mp3")
        val podcast = entry("Podcast", listOf("Radio"), "2026-09-30T06:00:00Z")
            .copy(audioUrl = "https://audioboom.com/episode.mp3")
        val undated = entry("Sin fecha", listOf("Radio"), "fecha desconocida")
        val canaltrans = RssFeed("Canaltrans", listOf(radio, undated), RssSource.CANALTRANS.url)
        val audioboom = RssFeed("Audioboom", listOf(podcast), RssSource.AUDIOBOOM.url)
        val result = audioboomWithRadio(canaltrans, audioboom)!!
        assertEquals(listOf("Radio", "Sin fecha"), result.entries.map { it.title })
        assertEquals(RssSource.CANALTRANS.url, result.entries[0].baseUrl)
        assertEquals(podcast.audioUrl, result.entries[0].audioUrl)
        assertEquals(radio.html, result.entries[0].html)
        assertEquals(radio.link, result.entries[0].link)
        assertEquals(radio.date, result.entries[0].date)
        assertEquals(radio.categories, result.entries[0].categories)
        assertEquals("", result.entries[1].audioUrl)
    }

    @Test
    fun partialResultsAreAvailableAndCombinationDoesNotAccumulateDuplicates() {
        val radio = entry("Radio", listOf("Radio"))
        val canaltrans = RssFeed("Canaltrans", listOf(radio), RssSource.CANALTRANS.url)
        val audioboom = RssFeed("Audioboom", listOf(entry("Podcast")), RssSource.AUDIOBOOM.url)
        assertEquals(listOf("Radio"), audioboomWithRadio(canaltrans, null)?.entries?.map { it.title })
        assertEquals(audioboomWithRadio(canaltrans, audioboom), audioboomWithRadio(canaltrans, audioboom))
        assertEquals(1, audioboomWithRadio(canaltrans, audioboom)?.entries?.size)
        assertNull(audioboomWithRadio(null, null))
        assertNull(audioboomWithRadio(null, audioboom))
        assertNull(audioboomWithRadio(canaltrans.copy(entries = emptyList()), null))
        assertTrue(audioboomWithRadio(canaltrans.copy(entries = emptyList()), audioboom)!!.entries.isEmpty())
    }

    @Test
    fun matchesDatesAcrossMidnightUsingCanaltransTimezone() {
        val radio = entry("Radio", listOf("Radio"), "Wed, 30 Sep 2026 23:30:00 -0300")
        val audio = entry("Audio", date = "Thu, 1 Oct 2026 01:30:00 +0000")
            .copy(audioUrl = "https://example.com/matching.mp3")
        val wrongDay = entry("Otro", date = "Wed, 30 Sep 2026 00:30:00 +0000")
            .copy(audioUrl = "https://example.com/wrong.mp3")
        val result = audioboomWithRadio(
            RssFeed("Canaltrans", listOf(radio), RssSource.CANALTRANS.url),
            RssFeed("Audioboom", listOf(wrongDay, audio), RssSource.AUDIOBOOM.url)
        )!!
        assertEquals(audio.audioUrl, result.entries.single().audioUrl)
    }

    @Test
    fun choosesClosestAudioOnSameDayAndDoesNotMatchDifferentDays() {
        val radio = entry("Radio", listOf("Radio"), "Wed, 30 Sep 2026 01:11:10 -0300")
        val close = entry("Cercano", date = "2026-09-30T03:11:00Z").copy(audioUrl = "https://example.com/close.mp3")
        val far = entry("Lejano", date = "2026-09-30T08:00:00Z").copy(audioUrl = "https://example.com/far.mp3")
        val otherDay = entry("Otro día", date = "2026-09-29T08:00:00Z").copy(audioUrl = "https://example.com/other.mp3")
        val canaltrans = RssFeed("Canaltrans", listOf(radio), RssSource.CANALTRANS.url)
        assertEquals(
            close.audioUrl,
            audioboomWithRadio(canaltrans, RssFeed("Audioboom", listOf(far, otherDay, close), RssSource.AUDIOBOOM.url))
                ?.entries?.single()?.audioUrl
        )
        assertEquals(
            "",
            audioboomWithRadio(canaltrans, RssFeed("Audioboom", listOf(otherDay), RssSource.AUDIOBOOM.url))
                ?.entries?.single()?.audioUrl
        )
    }
}
