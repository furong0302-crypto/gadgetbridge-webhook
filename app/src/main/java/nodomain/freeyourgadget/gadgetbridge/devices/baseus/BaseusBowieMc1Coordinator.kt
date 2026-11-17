package nodomain.freeyourgadget.gadgetbridge.devices.baseus

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BASEUS_BASS_BOOST
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BASEUS_LOW_LATENCY
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.equalizerPreset
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.baseus.BaseusBowieMc1Support
import java.util.regex.Pattern

class BaseusBowieMc1Coordinator : AbstractBLClassicDeviceCoordinator() {
    // Classic name is "Baseus Bowie MC1", BLE name "Bowie MC1"; the Pro is a different product.
    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^(Baseus )?Bowie MC1(?! Pro)( .*)?$")

    override fun getManufacturer(): String = "Baseus"

    override fun createDeviceSupport(device: GBDevice): DeviceSupport = BaseusBowieMc1Support()

    override fun getDeviceNameResource(): Int = R.string.devicetype_baseus_bowie_mc1

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_headphones

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.EARBUDS

    override fun isExperimental(): Boolean = true

    override fun getBatteryCount(device: GBDevice): Int = 3

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> = arrayOf(
        BatteryConfig(0, R.drawable.ic_nothing_ear_l, R.string.left_earbud),
        BatteryConfig(1, R.drawable.ic_nothing_ear_r, R.string.right_earbud),
        BatteryConfig(2, R.drawable.ic_buds_pro_case, R.string.battery_case),
    )

    /** The buds only report their levels when asked. */
    override fun addBatteryPollingSettings(): Boolean = true

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(
            key = DeviceSpecificSettingsScreen.SOUND.key,
            title = R.string.pref_header_sound,
            icon = R.drawable.ic_volume_up,
        ) {
            equalizerPreset<BaseusEqPreset>(defaultValue = BaseusEqPreset.BASEUS_CLASSIC)
            switchSetting(
                key = PREF_BASEUS_BASS_BOOST,
                title = R.string.pref_title_equalizer_bass_boost,
                icon = R.drawable.ic_equalizer,
                defaultValue = false,
            )
            switchSetting(
                key = PREF_BASEUS_LOW_LATENCY,
                title = R.string.earfun_low_latency_mode,
                summary = R.string.ugreen_game_mode_summary,
                icon = R.drawable.ic_videogame,
                defaultValue = false,
            )
        }
    }
}
