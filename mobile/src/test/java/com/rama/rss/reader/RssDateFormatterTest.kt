package com.rama.rss.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class RssDateFormatterTest {
    @Test
    fun convertsRfc1123AndPreservesInstantAcrossMidnight() {
        assertEquals("miércoles, 30/09/2026", formatRssDate("Wed, 30 Sep 2026 01:11:10 -0300"))
        assertEquals("martes, 29/09/2026", formatRssDate("2026-09-30T02:30:00Z"))
        assertEquals("miércoles, 30/09/2026", formatRssDate("2026-09-30T03:00:00Z"))
    }

    @Test
    fun convertsIsoDatesWithDifferentOffsets() {
        assertEquals("miércoles, 30/09/2026", formatRssDate("2026-09-30T18:30:00Z"))
        assertEquals("miércoles, 30/09/2026", formatRssDate("2026-09-30T20:30:00+02:00"))
        assertEquals("miércoles, 30/09/2026", formatRssDate("2026-09-30T12:30:00-06:00"))
    }

    @Test
    fun usesFixedOffsetInSummerAndWinter() {
        assertEquals("jueves, 15/01/2026", formatRssDate("2026-01-15T05:30:00Z"))
        assertEquals("miércoles, 15/07/2026", formatRssDate("2026-07-15T05:30:00Z"))
    }

    @Test
    fun conversionHandlesPreviousYear() {
        assertEquals("miércoles, 31/12/2025", formatRssDate("2026-01-01T02:00:00Z"))
    }

    @Test
    fun blankAndUnparseableDatesArePreservedWithoutInventingZone() {
        assertEquals("", formatRssDate("   "))
        assertEquals("30 de septiembre de 2026", formatRssDate("30 de septiembre de 2026"))
        assertEquals("fecha desconocida", formatRssDate(" fecha desconocida "))
        assertEquals("2026-09-30T12:30:00", formatRssDate("2026-09-30T12:30:00"))
    }

    @Test
    fun formattingDoesNotDependOnDeviceTimezoneOrChangeSourceDate() {
        val originalTimezone = TimeZone.getDefault()
        val rawDate = "2026-09-30T18:30:00Z"
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals("miércoles, 30/09/2026", formatRssDate(rawDate))
            assertEquals("2026-09-30T18:30:00Z", rawDate)
        } finally {
            TimeZone.setDefault(originalTimezone)
        }
    }
}

