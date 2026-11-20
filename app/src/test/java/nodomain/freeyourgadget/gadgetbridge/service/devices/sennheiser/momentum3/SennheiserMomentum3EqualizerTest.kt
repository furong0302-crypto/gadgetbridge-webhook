package nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.momentum3

import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * The filters the official app uploaded for each of the bass, mid and treble settings in a Bluetooth
 * HCI snoop. The app computes them in floating point too, so the coefficients may be off by a few LSB.
 */
class SennheiserMomentum3EqualizerTest {
    private data class Capture(
        val bass: Double,
        val mid: Double,
        val treble: Double,
        val highShelf: String,
        val lowShelf: String,
        val gain: String,
    )

    @Test
    fun matchesTheOfficialApp() {
        for (capture in CAPTURES) {
            val filters = SennheiserMomentum3Equalizer.filters(capture.bass, capture.mid, capture.treble)
            val expected = listOf(capture.highShelf, capture.lowShelf, capture.gain).map { GB.hexStringToByteArray(it) }
            assertEquals(3, filters.size)
            for ((actual, wanted) in filters.zip(expected)) {
                assertArrayEquals("header for $capture", wanted.copyOfRange(0, 6), actual.copyOfRange(0, 6))
                assertEquals(wanted.size, actual.size)
                for (offset in 6 until wanted.size step 4) {
                    val a = int32(actual, offset)
                    val w = int32(wanted, offset)
                    assertTrue("$capture at $offset: $a != $w", abs(a.toLong() - w.toLong()) <= 4)
                }
            }
        }
    }

    /** Decodes the uploaded chain and checks the bass and treble ends, which overflowing coefficients would wreck. */
    @Test
    fun staysWithinRangeForAllSettings() {
        for (bass in -6..6) for (mid in -6..6) for (treble in -6..6) {
            val filters = SennheiserMomentum3Equalizer.filters(bass.toDouble(), mid.toDouble(), treble.toDouble())
            val low = (bass - mid).toDouble()
            val high = (treble - mid).toDouble()
            val output = minOf(-(low + high) / 3, 4 - maxOf(low, high, 0.0))
            val gain = q30(filters[2], 6) * 2.0.pow(int32(filters[2], 10) - 1)
            fun responseDb(frequency: Double): Double {
                val h = magnitude(filters[0], frequency) * magnitude(filters[1], frequency) * gain
                return 20 * log10(h)
            }
            val bassEnd = responseDb(20.0)
            val trebleEnd = responseDb(20000.0)
            val label = "bass $bass mid $mid treble $treble"
            assertEquals(label, output + low, bassEnd, 0.5)
            assertEquals(label, high - low, trebleEnd - bassEnd, 0.5)
        }
    }

    private fun magnitude(filter: ByteArray, frequency: Double): Double {
        val c = DoubleArray(5) { q30(filter, 6 + 4 * it) }
        val w = 2 * PI * frequency / 44100
        fun poly(a: Double, b: Double, d: Double) =
            hypot(a + b * cos(w) + d * cos(2 * w), -(b * sin(w) + d * sin(2 * w)))
        return poly(c[0], c[1], c[2]) / poly(1.0, c[3], c[4])
    }

    private fun q30(data: ByteArray, offset: Int): Double = int32(data, offset) / 2.0.pow(30)

    @Test
    fun convertsLevels() {
        assertEquals(63, SennheiserMomentum3Equalizer.toLevel(3.0))
        assertEquals(-42, SennheiserMomentum3Equalizer.toLevel(-2.0))
        assertEquals(127, SennheiserMomentum3Equalizer.toLevel(6.0))
        assertEquals(-46, SennheiserMomentum3Equalizer.toLevel(-2.2))
    }

    @Test
    fun encodesAndReadsLevels() {
        val levels = SennheiserMomentum3Equalizer.Levels(14, 25, -46, SennheiserMomentum3Equalizer.FLAG_VALID)
        val payloads = SennheiserMomentum3Equalizer.levels(levels)
        assertArrayEquals(GB.hexStringToByteArray("00000e19"), payloads[0])
        assertArrayEquals(GB.hexStringToByteArray("0100d240"), payloads[1])
        assertEquals(
            levels,
            SennheiserMomentum3Equalizer.readLevels(GB.hexStringToByteArray("0e19"), GB.hexStringToByteArray("d240")),
        )
    }

    private fun int32(data: ByteArray, offset: Int): Int {
        fun u(i: Int) = data[offset + i].toInt() and 0xFF
        return (u(1) shl 24) or (u(0) shl 16) or (u(3) shl 8) or u(2)
    }

    companion object {
        private val CAPTURES = listOf(
            Capture(-6.0, 0.0, 0.0, "010001000000004000007d94a7cc5d2e37227d94a7cc5d2e3722", "000001000000b01f7c3320c2ed9f381e1b1a4a848922d93bdf7d", "0000000003009250e33b00000200000002000000000000000000"),
            Capture(6.0, 0.0, 0.0, "010001000000004000007d94a7cc5d2e37227d94a7cc5d2e3722", "0000010000005020779589c1064d381e931a0983faa0083d0567", "000000000300ac652f8c00000100000002000000000000000000"),
            Capture(6.0, -6.0, 0.0, "010001000000663c250786970c39082e6aab4098d194a92b6542", "000001000000a420d66550c1d1b8241eb3628e8260587e3dd277", "000000000300ea65fd5900000100000002000000000000000000"),
            Capture(6.0, 6.0, 0.0, "010001000000e8214a7908c93a7221178bde4b91f36bc7301b5e", "00000100000000200000cec11d0e3e1e789f9c833a1c7d3cf13e", "0000000003009250e33b00000200000002000000000000000000"),
            Capture(6.0, 6.0, -6.0, "010001000000fe11830e12e40da36f0bbb39958eb0a0ea329b4a", "00000100000000200000cec11d0e3e1e789f9c833a1c7d3cf13e", "0000000003006e65dbe300000200000002000000000000000000"),
            Capture(6.0, 6.0, 6.0, "010001000000004000007d94a7cc5d2e37227d94a7cc5d2e3722", "00000100000000200000cec11d0e3e1e789f9c833a1c7d3cf13e", "0000000003000040000000000200000002000000000000000000"),
            Capture(0.0, 3.0, 0.0, "010001000000942e6d8111b3f239c920337ad3921cd89b2f765d", "000001000000d81f9726f5c1947e3c1eb7feef83db602e3c51ae", "0000000003009250e33b00000200000002000000000000000000"),
            Capture(0.0, 2.0, 3.0, "0100010000002647727fd88723ba0334aa68139509acee2d36f6", "000001000000e51f356ee7c151f33d1e46e6d2835cd2493cb194", "0000000003008042d5fa00000200000002000000000000000000"),
            Capture(2.0, -2.0, 2.0, "010001000000e030e69f39ac0e10a924e384ee96284f982c851a", "0000010000003520b67c9ec12acb3b1e9fba3783ceaddc3c2786", "000000000300295e808600000200000002000000000000000000"),
            Capture(3.0, 2.0, 0.0, "010001000000c833d47cf9a9a14fcf2470825d93f8fa332fee53", "0000010000000d20c84fc1c1a2af3e1e3c71818319e9963cdd0b", "0000000003008042d5fa00000200000002000000000000000000"),
            Capture(-2.0, 0.0, 1.0, "0100010000002647727fd88723ba0334aa68139509acee2d36f6", "000001000000e51f356ee7c151f33d1e46e6d2835cd2493cb194", "0000000003008042d5fa00000200000002000000000000000000"),
            Capture(0.7, 1.2, -2.2, "010001000000a62cd7086eb6e4e14d1ffc219d9247a3c42f7169", "000001000000f91f5b5ad4c1f8613e1ee293a983b77e703c4497", "000000000300554a912c00000200000002000000000000000000"),
        )
    }
}
