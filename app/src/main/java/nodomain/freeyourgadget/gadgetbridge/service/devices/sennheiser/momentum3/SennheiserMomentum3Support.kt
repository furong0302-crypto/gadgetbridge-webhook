package nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.momentum3

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_3_EQUALIZER
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_BASS
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_MID
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_TREBLE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_3_HIGH_END
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_3_NOISE_CONTROL
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SENNHEISER_MOMENTUM_3_TRANSPARENT_PAUSE
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointDevice
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointPairingActivity
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.devices.sennheiser.SennheiserMomentum3EqualizerPreset
import nodomain.freeyourgadget.gadgetbridge.devices.sennheiser.SennheiserMomentum3NoiseControl
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.service.AbstractHeadphoneBTBRDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.momentum3.SennheiserMomentum3Protocol.Frame
import nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.momentum3.SennheiserMomentum3Protocol.readU16
import nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.momentum3.SennheiserMomentum3Protocol.u16
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.roundToInt

class SennheiserMomentum3Support : AbstractHeadphoneBTBRDeviceSupport(LOG, 1024) {
    private val decoder = SennheiserMomentum3Protocol.Decoder()
    private val handler = Handler(Looper.getMainLooper())

    // The official app waits for each response before sending the next request.
    private val queue = ArrayDeque<Request>()
    private var inFlight: Request? = null

    // The headset only takes a frame that follows a 0x2A, and stops answering after an empty frame,
    // so only the first frame of a connection carries a leading 0x2A.
    private var startDelimiterPending = false

    private var ownDeviceIndex = 0
    private var pairedDeviceCount = 0
    private var pairedDeviceResponses = 0
    private val pairedDevices = sortedMapOf<Int, MultipointDevice>()
    private var receiverRegistered = false
    private var storedLevels: ByteArray? = null
    private var transparentHearing = false
    private var noiseCancellingMode = SennheiserMomentum3NoiseControl.MAXIMUM

    private data class Request(val group: Int, val command: Int, val data: ByteArray = byteArrayOf())

    init {
        addSupportedService(SERVICE_UUID)
    }

    override fun useAutoConnect(): Boolean = false

    override fun setContext(gbDevice: GBDevice, btAdapter: BluetoothAdapter, context: Context) {
        super.setContext(gbDevice, btAdapter, context)
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(MultipointPairingActivity.ACTION_MULTIPOINT_GET_STATUS)
                addAction(MultipointPairingActivity.ACTION_MULTIPOINT_GET_DEVICES)
                addAction(MultipointPairingActivity.ACTION_MULTIPOINT_CONNECT_DEVICE)
                addAction(MultipointPairingActivity.ACTION_MULTIPOINT_DISCONNECT_DEVICE)
                addAction(MultipointPairingActivity.ACTION_MULTIPOINT_FORGET_DEVICE)
            }
            LocalBroadcastManager.getInstance(context).registerReceiver(multipointReceiver, filter)
            receiverRegistered = true
        }
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
            handler.removeCallbacksAndMessages(null)
            if (receiverRegistered) {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(multipointReceiver)
                receiverRegistered = false
            }
            super.dispose()
        }
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        handler.removeCallbacksAndMessages(null)
        synchronized(queue) {
            queue.clear()
            inFlight = null
            startDelimiterPending = true
        }

        request(GROUP_SYSTEM, CMD_PROTOCOL_VERSION)
        request(GROUP_BATTERY, CMD_BATTERY_LEVEL)
        request(GROUP_DEVICES, CMD_OWN_DEVICE_INDEX)
        request(GROUP_EQUALIZER, CMD_EQUALIZER_READ_LEVELS, u16(0))
        request(GROUP_EQUALIZER, CMD_EQUALIZER_READ_LEVELS, u16(1))
        request(GROUP_NOISE, CMD_NOISE_STATE)
        request(GROUP_NOISE, CMD_NOISE_CANCELLING_MODE)
        request(GROUP_NOISE, CMD_TRANSPARENT_KEEP_PLAYING)
        handler.postDelayed(batteryPoll, BATTERY_POLL_MILLIS)
        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }

    override fun onSocketRead(data: ByteArray) {
        for (frame in decoder.decode(data)) {
            handleFrame(frame)
        }
    }

    private fun handleFrame(frame: Frame) {
        if (frame.isNotification) {
            handleNotification(frame)
            return
        }

        val request = synchronized(queue) {
            val current = inFlight
            if (current == null || current.group != frame.group || current.command != frame.command) {
                LOG.warn("Unexpected response {}/{}: {}", hex(frame.group), hex(frame.command), GB.hexdump(frame.data))
                return
            }
            inFlight = null
            current
        }
        handler.removeCallbacks(requestTimeout)
        handleResponse(request, frame.data)
        sendNext()
    }

    private fun handleResponse(request: Request, data: ByteArray) {
        when (request.group to request.command) {
            GROUP_BATTERY to CMD_BATTERY_LEVEL -> if (data.size >= 2) {
                evaluateGBDeviceEvent(GBDeviceEventBatteryInfo().apply {
                    level = readU16(data, 0)
                    state = BatteryState.BATTERY_NORMAL
                })
            }

            GROUP_DEVICES to CMD_OWN_DEVICE_INDEX -> if (data.size >= 2) {
                ownDeviceIndex = readU16(data, 0)
            }

            GROUP_DEVICES to CMD_PAIRED_DEVICE_COUNT -> if (data.size >= 2) {
                pairedDeviceCount = readU16(data, 0)
                pairedDeviceResponses = 0
                pairedDevices.clear()
                if (pairedDeviceCount == 0) {
                    broadcastDeviceList()
                }
                for (index in 1..pairedDeviceCount) {
                    request(GROUP_DEVICES, CMD_PAIRED_DEVICE, u16(index))
                }
            }

            GROUP_DEVICES to CMD_PAIRED_DEVICE -> {
                parsePairedDevice(data)?.let { pairedDevices[it.first] = it.second }
                pairedDeviceResponses++
                if (pairedDeviceResponses >= pairedDeviceCount) {
                    broadcastDeviceList()
                }
            }

            GROUP_EQUALIZER to CMD_EQUALIZER_READ_LEVELS -> handleStoredLevels(readU16(request.data, 0), data)

            GROUP_NOISE to CMD_NOISE_STATE -> handleNoiseState(data)

            GROUP_NOISE to CMD_NOISE_CANCELLING_MODE -> if (data.size >= 2) {
                SennheiserMomentum3NoiseControl.fromCode(readU16(data, 0))?.let { noiseCancellingMode = it }
            }

            GROUP_NOISE to CMD_TRANSPARENT_KEEP_PLAYING -> if (data.size >= 2) {
                evaluateGBDeviceEvent(GBDeviceEventUpdatePreferences(PREF_SENNHEISER_MOMENTUM_3_TRANSPARENT_PAUSE, readU16(data, 0) == 0))
            }

            else -> LOG.debug("Response {}/{}: {}", hex(request.group), hex(request.command), GB.hexdump(data))
        }
    }

    private fun handleStoredLevels(index: Int, data: ByteArray) {
        if (index == 0) {
            storedLevels = data
            return
        }
        val first = storedLevels ?: return
        storedLevels = null
        val levels = SennheiserMomentum3Equalizer.readLevels(first, data)
        if (levels == null || !levels.isValid) {
            return
        }
        val preset = SennheiserMomentum3EqualizerPreset.entries.firstOrNull {
            it != SennheiserMomentum3EqualizerPreset.CUSTOM &&
                SennheiserMomentum3Equalizer.toLevel(it.bass) == levels.bass &&
                SennheiserMomentum3Equalizer.toLevel(it.mid) == levels.mid &&
                SennheiserMomentum3Equalizer.toLevel(it.treble) == levels.treble
        } ?: SennheiserMomentum3EqualizerPreset.CUSTOM
        val prefs = GBDeviceEventUpdatePreferences()
            .withPreference(PREF_SENNHEISER_MOMENTUM_3_EQUALIZER, preset.name.lowercase())
            .withPreference(PREF_SENNHEISER_MOMENTUM_3_HIGH_END, levels.highEnd)
        if (preset == SennheiserMomentum3EqualizerPreset.CUSTOM) {
            prefs.withPreference(PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_BASS, SennheiserMomentum3Equalizer.toDb(levels.bass).roundToInt())
                .withPreference(PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_MID, SennheiserMomentum3Equalizer.toDb(levels.mid).roundToInt())
                .withPreference(PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_TREBLE, SennheiserMomentum3Equalizer.toDb(levels.treble).roundToInt())
        }
        evaluateGBDeviceEvent(prefs)
    }

    override fun onSendConfiguration(config: String) {
        when (config) {
            PREF_SENNHEISER_MOMENTUM_3_EQUALIZER,
            PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_BASS,
            PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_MID,
            PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_TREBLE,
            PREF_SENNHEISER_MOMENTUM_3_HIGH_END -> sendEqualizer()

            PREF_SENNHEISER_MOMENTUM_3_NOISE_CONTROL -> sendNoiseControl()

            PREF_SENNHEISER_MOMENTUM_3_TRANSPARENT_PAUSE -> request(
                GROUP_NOISE,
                CMD_SET_TRANSPARENT_KEEP_PLAYING,
                u16(if (devicePrefs.getBoolean(config, true)) 0 else 1),
            )

            else -> super.onSendConfiguration(config)
        }
    }

    private fun sendEqualizer() {
        val prefs = devicePrefs
        val preset = SennheiserMomentum3EqualizerPreset.fromPreference(prefs.getString(PREF_SENNHEISER_MOMENTUM_3_EQUALIZER, ""))
        val (bass, mid, treble) = if (preset == SennheiserMomentum3EqualizerPreset.CUSTOM) {
            Triple(
                prefs.getInt(PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_BASS, 0).toDouble(),
                prefs.getInt(PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_MID, 0).toDouble(),
                prefs.getInt(PREF_SENNHEISER_MOMENTUM_3_EQUALIZER_TREBLE, 0).toDouble(),
            )
        } else {
            Triple(preset.bass, preset.mid, preset.treble)
        }
        val highEnd = prefs.getBoolean(PREF_SENNHEISER_MOMENTUM_3_HIGH_END, false)
        val levels = SennheiserMomentum3Equalizer.Levels(
            SennheiserMomentum3Equalizer.toLevel(bass),
            SennheiserMomentum3Equalizer.toLevel(mid),
            SennheiserMomentum3Equalizer.toLevel(treble),
            SennheiserMomentum3Equalizer.FLAG_VALID or (if (highEnd) SennheiserMomentum3Equalizer.FLAG_HIGH_END else 0),
        )
        val filters = if (highEnd) {
            SennheiserMomentum3Equalizer.highEndFilters()
        } else {
            SennheiserMomentum3Equalizer.filters(bass, mid, treble)
        }
        for (filter in filters) {
            request(GROUP_EQUALIZER, CMD_EQUALIZER_FILTER, filter)
        }
        for (payload in SennheiserMomentum3Equalizer.levels(levels)) {
            request(GROUP_EQUALIZER, CMD_EQUALIZER_STORE_LEVELS, payload)
        }
    }

    /**
     * Switching transparent hearing off returns to the previous noise cancelling mode, so leaving it
     * for another mode takes both commands.
     */
    private fun sendNoiseControl() {
        val target = SennheiserMomentum3NoiseControl.fromPreference(
            devicePrefs.getString(PREF_SENNHEISER_MOMENTUM_3_NOISE_CONTROL, "")
        ) ?: return
        val wantTransparent = target == SennheiserMomentum3NoiseControl.TRANSPARENT_HEARING
        if (wantTransparent != transparentHearing) {
            request(GROUP_NOISE, CMD_TOGGLE_TRANSPARENT_HEARING)
            transparentHearing = wantTransparent
        }
        val code = target.code ?: return
        if (target != noiseCancellingMode) {
            request(GROUP_NOISE, CMD_SET_NOISE_CANCELLING_MODE, u16(code))
            noiseCancellingMode = target
        }
    }

    /** `state mode`: state 1 is noise cancelling in the given mode, 2 transparent hearing. */
    private fun handleNoiseState(data: ByteArray) {
        if (data.size < 4) {
            return
        }
        val state = readU16(data, 0)
        val mode = readU16(data, 2)
        val control = when (state) {
            NOISE_STATE_CANCELLING -> SennheiserMomentum3NoiseControl.fromCode(mode)?.also {
                noiseCancellingMode = it
                transparentHearing = false
            }

            NOISE_STATE_TRANSPARENT -> SennheiserMomentum3NoiseControl.TRANSPARENT_HEARING.also {
                transparentHearing = true
            }

            else -> null
        }
        if (control == null) {
            LOG.warn("Unknown noise control state {} mode {}", state, mode)
            return
        }
        evaluateGBDeviceEvent(GBDeviceEventUpdatePreferences(PREF_SENNHEISER_MOMENTUM_3_NOISE_CONTROL, control.name.lowercase()))
    }

    private fun handleNotification(frame: Frame) {
        when (frame.group to frame.command) {
            GROUP_NOISE to NOTIFY_NOISE_STATE -> handleNoiseState(frame.data)
            GROUP_DEVICES to NOTIFY_DEVICE_STATE -> handleDeviceState(frame.data)
            else -> LOG.debug("Notification {}/{}: {}", hex(frame.group), hex(frame.command), GB.hexdump(frame.data))
        }
    }

    /** `0005 index state`: state 0 is disconnected, 1 connected and 0xFF forgotten. */
    private fun handleDeviceState(data: ByteArray) {
        if (data.size < 6) {
            return
        }
        val index = readU16(data, 2)
        when (val state = readU16(data, 4)) {
            0, 1 -> {
                val device = pairedDevices[index]
                if (device == null) {
                    request(GROUP_DEVICES, CMD_PAIRED_DEVICE_COUNT)
                    return
                }
                pairedDevices[index] = device.copy(isConnected = state == 1)
                broadcastDeviceList()
            }

            else -> request(GROUP_DEVICES, CMD_PAIRED_DEVICE_COUNT)
        }
    }

    private fun parsePairedDevice(data: ByteArray): Pair<Int, MultipointDevice>? {
        if (data.size < 6) {
            return null
        }
        val index = readU16(data, 0)
        val connected = readU16(data, 4) != 0
        val name = StringBuilder()
        var offset = 6
        while (offset + 1 < data.size && offset < 6 + NAME_LENGTH) {
            val c = readU16(data, offset)
            if (c == 0) {
                break
            }
            name.append(c.toChar())
            offset += 2
        }
        // An unused slot comes without a name.
        if (name.isBlank()) {
            return null
        }
        return index to MultipointDevice(
            address = index.toString(),
            name = name.toString(),
            isConnected = connected,
            canForget = index != ownDeviceIndex,
        )
    }

    private val multipointReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
            if (device?.address != getDevice().address) {
                return
            }
            val index = intent.getStringExtra(MultipointPairingActivity.EXTRA_DEVICE_ADDRESS)?.toIntOrNull()
            when (intent.action) {
                MultipointPairingActivity.ACTION_MULTIPOINT_GET_STATUS -> broadcastMultipointStatus()
                MultipointPairingActivity.ACTION_MULTIPOINT_GET_DEVICES -> {
                    request(GROUP_DEVICES, CMD_OWN_DEVICE_INDEX)
                    request(GROUP_DEVICES, CMD_PAIRED_DEVICE_COUNT)
                }

                MultipointPairingActivity.ACTION_MULTIPOINT_CONNECT_DEVICE ->
                    index?.let { request(GROUP_DEVICES, CMD_CONNECT_DEVICE, u16(it)) }

                MultipointPairingActivity.ACTION_MULTIPOINT_DISCONNECT_DEVICE ->
                    index?.let { request(GROUP_DEVICES, CMD_DISCONNECT_DEVICE, u16(it)) }

                MultipointPairingActivity.ACTION_MULTIPOINT_FORGET_DEVICE ->
                    index?.let { request(GROUP_DEVICES, CMD_FORGET_DEVICE, u16(it)) }
            }
        }
    }

    private fun broadcastMultipointStatus() {
        val intent = Intent(MultipointPairingActivity.ACTION_MULTIPOINT_STATUS_UPDATE).apply {
            putExtra(GBDevice.EXTRA_DEVICE, getDevice())
            putExtra(MultipointPairingActivity.EXTRA_MULTIPOINT_ENABLED, true)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun broadcastDeviceList() {
        val intent = Intent(MultipointPairingActivity.ACTION_MULTIPOINT_DEVICE_LIST).apply {
            putExtra(GBDevice.EXTRA_DEVICE, getDevice())
            putParcelableArrayListExtra(MultipointPairingActivity.EXTRA_DEVICE_LIST, ArrayList(pairedDevices.values))
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private val batteryPoll = object : Runnable {
        override fun run() {
            request(GROUP_BATTERY, CMD_BATTERY_LEVEL)
            handler.postDelayed(this, BATTERY_POLL_MILLIS)
        }
    }

    private val requestTimeout = Runnable {
        val timedOut = synchronized(queue) { inFlight.also { inFlight = null } }
        if (timedOut != null) {
            LOG.warn("No response to {}/{}", hex(timedOut.group), hex(timedOut.command))
        }
        sendNext()
    }

    private fun request(group: Int, command: Int, data: ByteArray = byteArrayOf()) {
        synchronized(queue) {
            queue.add(Request(group, command, data))
        }
        sendNext()
    }

    private fun sendNext() {
        var frame = ByteArray(0)
        val next = synchronized(queue) {
            if (!isConnected) {
                queue.clear()
                inFlight = null
                return
            }
            if (inFlight != null || queue.isEmpty()) {
                return
            }
            val request = queue.poll() ?: return
            inFlight = request
            frame = SennheiserMomentum3Protocol.encode(request.group, request.command, request.data)
            if (startDelimiterPending) {
                frame = START_DELIMITER + frame
                startDelimiterPending = false
            }
            request
        }
        handler.postDelayed(requestTimeout, REQUEST_TIMEOUT_MILLIS)
        val builder = createTransactionBuilder("request ${hex(next.group)}/${hex(next.command)}")
        builder.write(*frame)
        builder.queue()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SennheiserMomentum3Support::class.java)

        private val SERVICE_UUID: UUID = UUID.fromString("1ddce62a-ecb1-4455-8153-0743c87aec9f")
        private val START_DELIMITER = byteArrayOf(0x2A)
        private const val REQUEST_TIMEOUT_MILLIS = 2000L
        private const val BATTERY_POLL_MILLIS = 10 * 60 * 1000L
        private const val NAME_LENGTH = 64

        const val GROUP_SYSTEM = 0x01
        const val CMD_PROTOCOL_VERSION = 0x16

        const val GROUP_DEVICES = 0x06
        const val CMD_PAIRED_DEVICE = 0x32
        const val CMD_CONNECT_DEVICE = 0x33
        const val CMD_DISCONNECT_DEVICE = 0x34
        const val CMD_FORGET_DEVICE = 0x36
        const val CMD_OWN_DEVICE_INDEX = 0x38
        const val CMD_PAIRED_DEVICE_COUNT = 0x44
        const val NOTIFY_DEVICE_STATE = 0xF0

        const val GROUP_EQUALIZER = 0x0A
        const val CMD_EQUALIZER_FILTER = 0x0D
        const val CMD_EQUALIZER_STORE_LEVELS = 0x0E
        const val CMD_EQUALIZER_READ_LEVELS = 0x0F

        const val GROUP_BATTERY = 0x12
        const val CMD_BATTERY_LEVEL = 0x03

        const val GROUP_NOISE = 0x17
        const val CMD_SET_NOISE_CANCELLING_MODE = 0x08
        const val CMD_NOISE_CANCELLING_MODE = 0x09
        const val CMD_SET_TRANSPARENT_KEEP_PLAYING = 0x0A
        const val CMD_TRANSPARENT_KEEP_PLAYING = 0x0B
        const val NOTIFY_NOISE_STATE = 0x0E
        const val CMD_NOISE_STATE = 0x10
        const val CMD_TOGGLE_TRANSPARENT_HEARING = 0x11
        private const val NOISE_STATE_CANCELLING = 1
        private const val NOISE_STATE_TRANSPARENT = 2

        private fun hex(value: Int) = String.format("%02x", value)
    }
}
