package nodomain.freeyourgadget.gadgetbridge.service.devices.baseus

import android.os.Handler
import android.os.Looper
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BASEUS_BASS_BOOST
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BASEUS_LOW_LATENCY
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BATTERY_POLLING_ENABLE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BATTERY_POLLING_INTERVAL
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HEADPHONES_EQUALIZER
import nodomain.freeyourgadget.gadgetbridge.devices.baseus.BaseusEqPreset
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.AbstractHeadphoneBTBRDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Battery, EQ preset, bass boost and low latency. The buds are polled; the case level is read on connect (usually lid open)
 * and whenever the buds push it, since it is only known while the buds sit in the case.
 */
class BaseusBowieMc1Support : AbstractHeadphoneBTBRDeviceSupport(LOG, BUFFER_SIZE) {
    companion object {
        private val LOG = LoggerFactory.getLogger(BaseusBowieMc1Support::class.java)
        private const val BUFFER_SIZE = 1024
        private val SERIAL_PORT_SERVICE: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val RFCOMM_CHANNEL = 1
    }

    private val batteryPollHandler = Handler(Looper.getMainLooper())
    private val batteryPollRunnable = Runnable {
        pollBattery()
        scheduleBatteryPoll()
    }

    init {
        addSupportedService(SERIAL_PORT_SERVICE)
    }

    override fun useAutoConnect(): Boolean = false

    override fun getRfcommChannel(): Int = RFCOMM_CHANNEL

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        builder.write(*BaseusProtocol.encodeGetStatus())
        scheduleBatteryPoll()
        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }

    override fun onSocketRead(data: ByteArray) {
        for (event in BaseusProtocol.decode(data)) {
            evaluateGBDeviceEvent(event)
        }
    }

    override fun onSendConfiguration(config: String) {
        when (config) {
            PREF_BATTERY_POLLING_ENABLE, PREF_BATTERY_POLLING_INTERVAL -> scheduleBatteryPoll()
            PREF_BASEUS_BASS_BOOST ->
                send("set bass boost", BaseusProtocol.encodeSetBassBoost(devicePrefs.getBoolean(config, false)))

            PREF_HEADPHONES_EQUALIZER -> send(
                "set EQ preset",
                BaseusProtocol.encodeSetEqPreset(BaseusEqPreset.fromPreference(devicePrefs.getString(config, null))),
            )

            PREF_BASEUS_LOW_LATENCY ->
                send("set low latency", BaseusProtocol.encodeSetLowLatency(devicePrefs.getBoolean(config, false)))

            else -> super.onSendConfiguration(config)
        }
    }

    private fun send(taskName: String, payload: ByteArray) {
        val builder = createTransactionBuilder(taskName)
        builder.write(*payload)
        builder.queue()
    }

    private fun scheduleBatteryPoll() {
        batteryPollHandler.removeCallbacks(batteryPollRunnable)
        if (devicePrefs.batteryPollingEnabled) {
            batteryPollHandler.postDelayed(
                batteryPollRunnable,
                devicePrefs.batteryPollingIntervalMinutes * 60 * 1000L,
            )
        }
    }

    private fun pollBattery() {
        if (!isConnected) {
            return
        }
        send("poll battery", BaseusProtocol.encodeGetBudsBattery())
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
            batteryPollHandler.removeCallbacksAndMessages(null)
            super.dispose()
        }
    }
}
