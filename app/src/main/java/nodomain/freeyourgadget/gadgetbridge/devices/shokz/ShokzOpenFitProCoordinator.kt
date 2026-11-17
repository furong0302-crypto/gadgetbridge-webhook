package nodomain.freeyourgadget.gadgetbridge.devices.shokz

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzEqualizer
import java.util.regex.Pattern

class ShokzOpenFitProCoordinator : ShokzCoordinator() {
    override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^OpenFit Pro by Shokz$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_shokz_openfit_pro
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.EARBUDS
    }

    override fun supportsMp3(): Boolean {
        return false
    }

    // The official app never sends CONTROLS_GET to this device.
    override fun supportsControls(): Boolean {
        return false
    }

    override fun supportsTws(): Boolean {
        return true
    }

    override fun supportsNoiseCancelling(): Boolean {
        return true
    }

    override fun supportsDolbyAtmos(): Boolean {
        return true
    }

    override fun supportsHeadTracking(): Boolean = true

    override fun supportsWearingDetection(): Boolean = true

    override fun supportsBassTreble(): Boolean = true

    override fun supportsPrivacyEqualizer(): Boolean = true

    override fun supportsCustomEqualizer(): Boolean = true

    override fun supportsEqualizerV2(): Boolean = true

    override fun customEqualizerBandCount(): Int = 10

    // Bytes captured from a live Bluetooth HCI snoop of the official Shokz app talking to a
    // real OpenFit Pro. VOCAL and TREBLE match the defaults and the OpenRun Pro 2 respectively.
    override fun equalizerArgs(equalizer: ShokzEqualizer): ByteArray = when (equalizer) {
        ShokzEqualizer.BASS ->
            byteArrayOf(0x03, 0x02, 0x01, 0xfe.toByte(), 0xff.toByte(), 0x00, 0x00, 0x00)

        ShokzEqualizer.TREBLE ->
            byteArrayOf(0x04, 0x00, 0x00, 0x01, 0x04, 0x05, 0x00, 0x00)

        else -> super.equalizerArgs(equalizer)
    }

    override fun customEqualizerArgs(bands: IntArray): ByteArray =
        byteArrayOf(ShokzEqualizer.CUSTOM.code.toByte(), bands.size.toByte()) +
            bands.map { it.toByte() }.toByteArray()
}
