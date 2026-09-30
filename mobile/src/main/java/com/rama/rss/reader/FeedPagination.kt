package com.rama.rss.reader

internal const val FEED_PAGE_SIZE = 5

internal fun nextFeedPageSize(current: Int, total: Int): Int =
    (current.toLong() + FEED_PAGE_SIZE).coerceAtMost(total.coerceAtLeast(0).toLong()).toInt()

internal fun shouldLoadNextFeedPage(
    visibleCount: Int,
    totalEntries: Int,
    lastVisibleIndex: Int,
    totalListItems: Int,
    hasScrolled: Boolean
): Boolean = hasScrolled && visibleCount < totalEntries && totalListItems > 0 &&
    lastVisibleIndex >= (totalListItems - 3).coerceAtLeast(0)
