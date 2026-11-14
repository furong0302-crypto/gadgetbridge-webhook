package nodomain.freeyourgadget.gadgetbridge.util.ui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentColorTest {
    @Test
    fun storedValuesAreRead() {
        AccentColor.entries.forEach { assertEquals(it, AccentColor.fromValue(it.value)) }
    }

    @Test
    fun missingOrUnknownValuesFallBackToRed() {
        assertEquals(AccentColor.RED, AccentColor.DEFAULT)
        assertEquals(AccentColor.RED, AccentColor.fromValue(null))
        assertEquals(AccentColor.RED, AccentColor.fromValue(""))
        assertEquals(AccentColor.RED, AccentColor.fromValue("unknown"))
    }

    @Test
    fun whiteIsOnlyAvailableWithTheDarkTheme() {
        assertTrue(AccentColor.WHITE.isAvailable(dark = true))
        assertFalse(AccentColor.WHITE.isAvailable(dark = false))
        AccentColor.entries.filter { it != AccentColor.WHITE }.forEach {
            assertTrue(it.isAvailable(dark = false))
            assertTrue(it.isAvailable(dark = true))
        }
    }
}
