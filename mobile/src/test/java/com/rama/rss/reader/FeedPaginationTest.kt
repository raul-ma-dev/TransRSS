package com.rama.rss.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPaginationTest {
    @Test
    fun pagesGrowByFiveAndStopAtTotal() {
        assertEquals(5, FEED_PAGE_SIZE)
        assertEquals(10, nextFeedPageSize(5, 13))
        assertEquals(13, nextFeedPageSize(10, 13))
        assertEquals(13, nextFeedPageSize(13, 13))
        assertEquals(0, nextFeedPageSize(5, 0))
    }

    @Test
    fun doesNotLoadMoreBeforeUserScrollsOrWhileFarFromEnd() {
        assertFalse(shouldLoadNextFeedPage(5, 13, 6, 7, hasScrolled = false))
        assertFalse(shouldLoadNextFeedPage(5, 13, 1, 7, hasScrolled = true))
    }

    @Test
    fun loadsNearEndOnlyWhenThereAreMoreEntries() {
        assertTrue(shouldLoadNextFeedPage(5, 13, 4, 7, hasScrolled = true))
        assertFalse(shouldLoadNextFeedPage(13, 13, 12, 14, hasScrolled = true))
        assertFalse(shouldLoadNextFeedPage(5, 13, -1, 0, hasScrolled = true))
    }
}
