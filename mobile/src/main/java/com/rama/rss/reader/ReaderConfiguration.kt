package com.rama.rss.reader

// Temporary test mode. Set to true to restore Canaltrans and feed matching.
internal const val CANALTRANS_ENABLED = false

internal fun enabledRssSources(canaltransEnabled: Boolean = CANALTRANS_ENABLED): List<RssSource> =
    if (canaltransEnabled) listOf(RssSource.CANALTRANS, RssSource.AUDIOBOOM)
    else listOf(RssSource.AUDIOBOOM)
