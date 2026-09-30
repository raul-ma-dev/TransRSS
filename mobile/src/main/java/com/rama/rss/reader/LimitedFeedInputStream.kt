package com.rama.rss.reader

import java.io.InputStream

internal class LimitedFeedInputStream(
    private val source: InputStream,
    private val maxBytes: Long = 5L * 1024 * 1024
) : InputStream() {
    private var total = 0L

    override fun read(): Int {
        val value = source.read()
        if (value != -1) record(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = source.read(buffer, offset, length)
        if (count > 0) record(count)
        return count
    }

    private fun record(count: Int) {
        total += count
        require(total <= maxBytes) { "El feed supera el límite de 5 MB." }
    }

    override fun close() = source.close()
}
