package nodomain.freeyourgadget.gadgetbridge.activities

import android.content.Intent
import android.widget.SeekBar
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.service.DeviceCommunicationService
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

class VibrationActivityTest : TestBase() {
    private fun nextStartedService(): Intent? = shadowOf(app).nextStartedService

    private fun startedSeekBar(): SeekBar {
        val activity = Robolectric.buildActivity(VibrationActivity::class.java).setup().get()
        while (nextStartedService() != null) {
            // Drain startup noise so each check sees only the seek bar's command.
        }
        return activity.findViewById(R.id.vibration_seekbar)
    }

    private fun assertVibrationSent(expectedIntensity: Int) {
        val intent = nextStartedService()
        assertNotNull(intent)
        assertEquals(DeviceCommunicationService::class.java.name, intent!!.component?.className)
        assertEquals(DeviceService.ACTION_SET_CONSTANT_VIBRATION, intent.action)
        assertEquals(expectedIntensity, intent.getIntExtra(DeviceService.EXTRA_VIBRATION_INTENSITY, -1))
        assertNull(nextStartedService())
    }

    @Test
    fun eachPositionSendsItsIntensity() {
        val seekBar = startedSeekBar()

        seekBar.progress = 1
        assertVibrationSent(15)

        seekBar.progress = 16
        assertVibrationSent(255)

        seekBar.progress = 0
        assertVibrationSent(0)
    }
}
