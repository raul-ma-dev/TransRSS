package com.rama.rss.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class LimitedFeedInputStreamTest {
    @Test
    fun readsExactLimitAndClosesOriginalStream() {
        var closed = false
        val input = object : ByteArrayInputStream("feed".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        LimitedFeedInputStream(input, maxBytes = 4).use {
            assertEquals("feed", it.readBytes().decodeToString())
        }
        assertTrue(closed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedBlockReads() {
        LimitedFeedInputStream("large feed".byteInputStream(), maxBytes = 4).use { it.readBytes() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedSingleByteReads() {
        LimitedFeedInputStream("12345".byteInputStream(), maxBytes = 4).use { input ->
            while (input.read() != -1) { /* Read until EOF or the size limit. */ }
        }
    }
}
