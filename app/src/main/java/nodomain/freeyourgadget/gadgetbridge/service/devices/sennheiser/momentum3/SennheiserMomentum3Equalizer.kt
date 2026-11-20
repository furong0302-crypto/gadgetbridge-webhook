package nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.momentum3

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The headset does not compute its equalizer itself: the app uploads the filter chain. It is a low
 * shelf at 280 Hz carrying bass minus mid, a high shelf at 1600 Hz carrying treble minus mid (both
 * RBJ shelves with slope 1 at 44.1 kHz) and an output gain. The gain moves the average of the three
 * bands to 0 dB, but keeps the loudest band at most 4 dB above it.
 *
 * Coefficients are Q30 fixed point, sent as two little-endian 16-bit words with the high word first.
 * A filter whose coefficients are halved for headroom is made up for by the gain exponent. The app
 * halves the low shelf always and the high shelf from 1 dB of boost; boosts of 8 dB and more need
 * the high shelf quartered to stay within the Q30 range.
 *
 * The app also stores the band levels (dB * 127 / 6, truncated) and flags on the headset, from where
 * they can be read back.
 */
object SennheiserMomentum3Equalizer {
    const val MAX_DB = 6

    private const val SAMPLE_RATE = 44100.0
    private const val LOW_SHELF_HZ = 280.0
    private const val HIGH_SHELF_HZ = 1600.0
    private const val MAX_BOOST_DB = 4.0
    private const val HIGH_SHELF_HEADROOM_DB = 1.0
    private const val LEVEL_SCALE = 127.0
    private const val Q30_LIMIT = 1.999

    const val FLAG_VALID = 0x40
    const val FLAG_HIGH_END = 0x08

    private val HIGH_SHELF_HEADER = byteArrayOf(1, 0, 1, 0, 0, 0)
    private val LOW_SHELF_HEADER = byteArrayOf(0, 0, 1, 0, 0, 0)
    private val GAIN_HEADER = byteArrayOf(0, 0, 0, 0, 3, 0)

    /** Captured from the official app: a 50 Hz dip and a 500 Hz peak. */
    private val HIGH_END_FILTERS = listOf(
        "0100010000006220374104c2d3b0c11de2660984a561473c3450",
        "000001000000e51fd22092c0b20a891ff73d24816315dc3e93bd",
        "0000000003000040000000000300000002000000000000000000",
    ).map { hex -> ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() } }

    data class Levels(val bass: Int, val mid: Int, val treble: Int, val flags: Int) {
        val isValid: Boolean get() = flags and FLAG_VALID != 0
        val highEnd: Boolean get() = flags and FLAG_HIGH_END != 0
    }

    fun toLevel(db: Double): Int = (db * LEVEL_SCALE / MAX_DB).toInt().coerceIn(-127, 127)

    fun toDb(level: Int): Double = level * MAX_DB / LEVEL_SCALE

    /** The payloads of the filter commands, in the order the app sends them. */
    fun filters(bassDb: Double, midDb: Double, trebleDb: Double): List<ByteArray> {
        val low = bassDb - midDb
        val high = trebleDb - midDb
        val outputDb = min(-(low + high) / 3, MAX_BOOST_DB - maxOf(low, high, 0.0))

        val highShelf = shelf(HIGH_SHELF_HZ, high, lowShelf = false)
        val lowShelf = shelf(LOW_SHELF_HZ, low, lowShelf = true)
        var highShift = if (high > HIGH_SHELF_HEADROOM_DB) 1 else 0
        while (!fitsQ30(highShelf, highShift)) {
            highShift++
        }
        var lowShift = 1
        while (!fitsQ30(lowShelf, lowShift)) {
            lowShift++
        }
        var gain = 10.0.pow(outputDb / 20) * 2.0.pow(highShift + lowShift)
        while (gain < 1) {
            highShift++
            gain *= 2
        }
        var exponent = floor(ln(gain) / ln(2.0)).toInt()
        if (gain / 2.0.pow(exponent) >= 2) {
            exponent++
        }

        return listOf(
            HIGH_SHELF_HEADER + biquad(highShelf, highShift),
            LOW_SHELF_HEADER + biquad(lowShelf, lowShift),
            GAIN_HEADER + q30(gain / 2.0.pow(exponent)) + int32(exponent + 1) + int32(2) + int32(0) + int32(0),
        )
    }

    fun highEndFilters(): List<ByteArray> = HIGH_END_FILTERS.map { it.copyOf() }

    /** The payloads storing the levels, which the headset hands back on [readLevels]. */
    fun levels(levels: Levels): List<ByteArray> = listOf(
        byteArrayOf(0, 0, levels.bass.toByte(), levels.mid.toByte()),
        byteArrayOf(1, 0, levels.treble.toByte(), levels.flags.toByte()),
    )

    fun readLevels(first: ByteArray, second: ByteArray): Levels? {
        if (first.size < 2 || second.size < 2) {
            return null
        }
        return Levels(first[0].toInt(), first[1].toInt(), second[0].toInt(), second[1].toInt() and 0xFF)
    }

    /** RBJ audio EQ cookbook shelf with S = 1, normalised to a0 = 1. */
    private fun shelf(frequency: Double, gainDb: Double, lowShelf: Boolean): DoubleArray {
        val a = 10.0.pow(gainDb / 40)
        val w0 = 2 * Math.PI * frequency / SAMPLE_RATE
        val c = cos(w0)
        val beta = 2 * sqrt(a) * sin(w0) / sqrt(2.0)
        val sign = if (lowShelf) 1 else -1
        val b0 = a * ((a + 1) - sign * (a - 1) * c + beta)
        val b1 = sign * 2 * a * ((a - 1) - sign * (a + 1) * c)
        val b2 = a * ((a + 1) - sign * (a - 1) * c - beta)
        val a0 = (a + 1) + sign * (a - 1) * c + beta
        val a1 = -sign * 2 * ((a - 1) + sign * (a + 1) * c)
        val a2 = (a + 1) + sign * (a - 1) * c - beta
        return doubleArrayOf(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }

    private fun fitsQ30(coefficients: DoubleArray, shift: Int): Boolean =
        coefficients.take(3).all { abs(it / 2.0.pow(shift)) < Q30_LIMIT }

    private fun biquad(coefficients: DoubleArray, shift: Int): ByteArray {
        val scale = 1 / 2.0.pow(shift)
        return coefficients.mapIndexed { i, c -> q30(if (i < 3) c * scale else c) }
            .reduce { acc, bytes -> acc + bytes }
    }

    private fun q30(value: Double): ByteArray = int32((value * (1 shl 30)).roundToLong().toInt())

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value shr 16).toByte(), (value shr 24).toByte(), value.toByte(), (value shr 8).toByte(),
    )
}
