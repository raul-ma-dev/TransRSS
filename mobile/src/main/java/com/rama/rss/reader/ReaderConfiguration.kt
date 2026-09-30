package com.rama.rss.reader

// Controls only menu visibility. Canaltrans still supplies information for Audioboom.
internal const val CANALTRANS_TAB_ENABLED = false

internal fun enabledRssSources(canaltransTabEnabled: Boolean = CANALTRANS_TAB_ENABLED): List<RssSource> =
    if (canaltransTabEnabled) listOf(RssSource.CANALTRANS, RssSource.AUDIOBOOM)
    else listOf(RssSource.AUDIOBOOM)
