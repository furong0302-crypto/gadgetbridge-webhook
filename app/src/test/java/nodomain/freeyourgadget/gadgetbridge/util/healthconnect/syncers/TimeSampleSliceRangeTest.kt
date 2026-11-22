package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import nodomain.freeyourgadget.gadgetbridge.model.TimeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TimeSampleSliceRangeTest {

    private val start = Instant.ofEpochMilli(1_000_400L)
    private val end = Instant.ofEpochSecond(2_000L)

    private fun sample(timestampMillis: Long): TimeSample = object : TimeSample {
        override fun getTimestamp(): Long = timestampMillis
    }

    private fun filtered(vararg timestampsMillis: Long): List<Long> =
        samplesWithinWriteRange(timestampsMillis.map { sample(it) }, start, end)
            .map { it.getTimestamp() }

    @Test
    fun sampleAtCursor_isExcluded() {
        assertTrue(filtered(1_000_400L).isEmpty())
    }

    @Test
    fun sampleAfterCursorInSameSecond_isIncluded() {
        assertEquals(listOf(1_000_650L), filtered(1_000_650L))
    }

    @Test
    fun sampleAtEnd_isIncluded() {
        assertEquals(listOf(2_000_000L), filtered(2_000_000L))
    }

    @Test
    fun sampleAfterEnd_isExcluded() {
        assertTrue(filtered(2_000_500L).isEmpty())
    }
}
