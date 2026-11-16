package nodomain.freeyourgadget.gadgetbridge.deviceevents

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

class GBDeviceEventVersionInfoTest : TestBase() {
    private val device = GBDevice("00:00:00:00:00:00", "Test", null, null, DeviceType.UNKNOWN)

    @Test
    @Config(qualifiers = "ru")
    fun versionsNotReportedAreNotAvailableInTheAppLanguageFromCreation() {
        val event = GBDeviceEventVersionInfo()

        val notAvailable = app.getString(R.string.n_a)
        assertEquals("Недоступно", notAvailable)
        assertEquals(notAvailable, event.fwVersion)
        assertEquals(notAvailable, event.hwVersion)

        event.evaluate(app, device)

        assertEquals(notAvailable, device.firmwareVersion)
        assertEquals(notAvailable, device.model)
    }

    @Test
    fun reportedVersionsAreKept() {
        val event = GBDeviceEventVersionInfo()
        event.fwVersion = "1.2.3"
        event.hwVersion = "HW-7"

        event.evaluate(app, device)

        assertEquals("1.2.3", device.firmwareVersion)
        assertEquals("HW-7", device.model)
    }

    @Test
    fun versionsClearedByTheDeviceSupportLeaveTheDeviceUnchanged() {
        device.firmwareVersion = "0.9"
        device.model = "Old"
        val event = GBDeviceEventVersionInfo()
        event.fwVersion = null
        event.hwVersion = null

        event.evaluate(app, device)

        assertEquals("0.9", device.firmwareVersion)
        assertEquals("Old", device.model)
    }
}
