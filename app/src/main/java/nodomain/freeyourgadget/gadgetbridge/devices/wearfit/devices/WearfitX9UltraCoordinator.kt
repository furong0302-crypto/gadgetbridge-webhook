package nodomain.freeyourgadget.gadgetbridge.devices.wearfit.devices

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.wearfit.WearFitCoordinator
import java.util.regex.Pattern

class WearfitX9UltraCoordinator: WearFitCoordinator() {
    override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^X9 UItra$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_wearfit_x9_ultra
    }
}
