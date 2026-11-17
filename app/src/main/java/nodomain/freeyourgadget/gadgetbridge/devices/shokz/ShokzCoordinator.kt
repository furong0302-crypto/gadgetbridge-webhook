package nodomain.freeyourgadget.gadgetbridge.devices.shokz

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.Language
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.equalizerPreset
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.languages
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.multipointPairing
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControl
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlDialog
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlOption
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlToggleGroup
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlTrailingAction
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControls
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzEqualizer
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzMediaSource
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzMp3PlaybackMode
import nodomain.freeyourgadget.gadgetbridge.service.devices.shokz.ShokzSupport

abstract class ShokzCoordinator : AbstractBLClassicDeviceCoordinator() {
    override fun getManufacturer(): String? {
        return "Shokz"
    }

    override fun createDeviceSupport(device: GBDevice): DeviceSupport {
        return ShokzSupport()
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun addBatteryPollingSettings(): Boolean {
        // The device only reports battery level once, when we request it right after connecting -
        // there is no notification for battery changes, so we need to poll periodically.
        return true
    }

    override fun getDefaultIconResource(): Int {
        // TODO dedicated icon
        return R.drawable.ic_device_headphones
    }

    /**
     * Whether this device has an onboard MP3 player (e.g. the OpenSwim Pro), exposing a
     * separate media source, MP3-only equalizer preset and playback mode. Most Shokz
     * headphones (e.g. the OpenRun Pro) are Bluetooth-only and don't support this.
     */
    open fun supportsMp3(): Boolean = true

    /**
     * Whether this device consists of two earbuds and a charging case (e.g. the OpenFit Pro),
     * each reporting their own battery level.
     */
    open fun supportsTws(): Boolean = false

    /**
     * Whether this device supports toggling active noise cancelling and adjusting its level.
     */
    open fun supportsNoiseCancelling(): Boolean = false

    open fun supportsDolbyAtmos(): Boolean = false

    /**
     * Whether this device supports head tracking for Dolby Atmos (HEAD_TRACKING_GET/SET). The
     * official app only offers it while Dolby Atmos is enabled, and turns it off along with it.
     */
    open fun supportsHeadTracking(): Boolean = false

    /**
     * Whether this device supports toggling wearing detection (WEARING_DETECTION_GET/SET).
     */
    open fun supportsWearingDetection(): Boolean = false

    override fun getBatteryCount(device: GBDevice): Int = if (supportsTws()) 3 else 1

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> = if (supportsTws()) {
        arrayOf(
            BatteryConfig(0, R.drawable.ic_nothing_ear_l, R.string.left_earbud),
            BatteryConfig(1, R.drawable.ic_nothing_ear_r, R.string.right_earbud),
            BatteryConfig(2, R.drawable.ic_tws_case, R.string.battery_case),
        )
    } else {
        super.getBatteryConfig(device)
    }

    /**
     * Whether this device supports customizing the long-press multi-function button and
     * simultaneous volume up/down controls (CONTROLS_GET/CONTROLS_SET). Confirmed present on
     * the OpenSwim Pro; the OpenRun Pro 2 does not respond to CONTROLS_GET at all.
     */
    open fun supportsControls(): Boolean = true

    /**
     * Whether this device supports the Bass Boost / Treble Boost equalizer presets, in addition
     * to Standard and Vocal. Confirmed present on the OpenRun Pro 2 (codes 0x03/0x04); unverified
     * on other Shokz devices, so disabled by default.
     */
    open fun supportsBassTreble(): Boolean = false

    /**
     * Whether this device supports the 5-band custom equalizer (code 0x05), in addition to the
     * fixed presets. Confirmed present on the OpenRun Pro 2; unverified on other Shokz devices,
     * so disabled by default.
     */
    open fun supportsCustomEqualizer(): Boolean = false

    /**
     * Whether this device supports the Classic (bone-conduction only, air speaker disabled) and
     * Volume Boost equalizer presets (codes 0x08/0x0a). The official app only shows these when
     * its account region is set to the US; Gadgetbridge has no such concept, so this is gated
     * purely on device support. Confirmed present on the OpenRun Pro 2; unverified on other
     * Shokz devices, so disabled by default.
     */
    open fun supportsClassicAndVolumeBoost(): Boolean = false

    /**
     * Whether this device supports the Privacy equalizer preset (code 0x09). Confirmed present on
     * the OpenFit Pro; unverified on other Shokz devices, so disabled by default.
     */
    open fun supportsPrivacyEqualizer(): Boolean = false

    /**
     * Number of bands of the custom equalizer, each in the -5..5 range.
     */
    open fun customEqualizerBandCount(): Int = 5

    /**
     * Whether this device reads the equalizer via EQUALIZER_V2_GET and sets the custom equalizer
     * via EQUALIZER_CUSTOM_SET, instead of EQUALIZER_GET and EQUALIZER_SET. Confirmed on the
     * OpenFit Pro, whose official app never sends EQUALIZER_GET.
     */
    open fun supportsEqualizerV2(): Boolean = false

    /**
     * Encodes the EQUALIZER_SET payload for a given preset. Beyond the preset code (first byte),
     * these carry extra device-specific tuning parameters that were reverse-engineered from the
     * respective official app/device traffic, and may not be identical across Shokz models.
     */
    open fun equalizerArgs(equalizer: ShokzEqualizer): ByteArray = when (equalizer) {
        ShokzEqualizer.STANDARD, ShokzEqualizer.SWIMMING, ShokzEqualizer.BASS, ShokzEqualizer.TREBLE,
        ShokzEqualizer.CLASSIC, ShokzEqualizer.PRIVACY, ShokzEqualizer.VOLUME_BOOST ->
            byteArrayOf(equalizer.code.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        ShokzEqualizer.VOCAL ->
            byteArrayOf(equalizer.code.toByte(), 0xfc.toByte(), 0x00, 0x03, 0x02, 0x02, 0x00, 0x00)

        // CUSTOM carries the user-adjustable band gains, built separately from preferences by
        // the caller - this default is never actually sent.
        ShokzEqualizer.CUSTOM ->
            byteArrayOf(equalizer.code.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    }

    /**
     * Encodes the payload for the custom equalizer, given [customEqualizerBandCount] band gains
     * (each in the -5..5 range, as reverse-engineered from the official Shokz app). It is sent
     * via EQUALIZER_CUSTOM_SET if [supportsEqualizerV2], otherwise via EQUALIZER_SET.
     */
    open fun customEqualizerArgs(bands: IntArray): ByteArray = byteArrayOf(
        ShokzEqualizer.CUSTOM.code.toByte(),
        bands[0].toByte(), bands[1].toByte(), bands[2].toByte(), bands[3].toByte(), bands[4].toByte(),
        0x00, 0x00,
    )

    private fun supportsBluetoothEqualizer(equalizer: ShokzEqualizer): Boolean =
        ShokzMediaSource.BLUETOOTH in equalizer.sources &&
            (supportsBassTreble() || equalizer != ShokzEqualizer.BASS && equalizer != ShokzEqualizer.TREBLE) &&
            (supportsCustomEqualizer() || equalizer != ShokzEqualizer.CUSTOM) &&
            (supportsClassicAndVolumeBoost() || equalizer != ShokzEqualizer.CLASSIC && equalizer != ShokzEqualizer.VOLUME_BOOST) &&
            (supportsPrivacyEqualizer() || equalizer != ShokzEqualizer.PRIVACY)

    private fun mediaSource(device: GBDevice): ShokzMediaSource =
        if (!supportsMp3()) {
            ShokzMediaSource.BLUETOOTH
        } else {
            ShokzMediaSource.fromPreference(
                GBApplication.getDevicePrefs(device)
                    .getString(DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE, "bluetooth")
            ) ?: ShokzMediaSource.BLUETOOTH
        }

    override fun getQuickControls(device: GBDevice): List<QuickControl> {
        val controls = mutableListOf<QuickControl>()

        if (supportsMp3()) {
            controls.add(
                QuickControlToggleGroup.forPreference(
                    key = DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE,
                    title = R.string.media_source,
                    icon = R.drawable.ic_music_note,
                    defaultValue = ShokzMediaSource.BLUETOOTH.name.lowercase(),
                    options = ShokzMediaSource.entries.map { source ->
                        val icon = when (source) {
                            ShokzMediaSource.BLUETOOTH -> R.drawable.ic_bluetooth
                            ShokzMediaSource.MP3 -> R.drawable.ic_music_note
                        }
                        QuickControlOption(source.name.lowercase(), source.label, icon)
                    },
                )
            )
        }

        controls.addAll(
            QuickControls.fromDeviceSettings(
                device,
                keys = emptyList(),
                optionalKeys = listOfNotNull(
                    DeviceSettingsPreferenceConst.PREF_ACTIVE_NOISE_CANCELLING_TOGGLE.takeIf { supportsNoiseCancelling() },
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_DOLBY_ATMOS.takeIf { supportsDolbyAtmos() },
                ),
            )
        )

        controls.add(
            QuickControlDialog.forPreference(
                key = DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_BLUETOOTH,
                icon = R.drawable.ic_graphic_eq,
                title = if (supportsMp3()) R.string.sony_equalizer_bluetooth else R.string.sony_equalizer,
                defaultValue = ShokzEqualizer.STANDARD.name.lowercase(),
                options = ShokzEqualizer.entries
                    .filter { supportsBluetoothEqualizer(it) }
                    .map { QuickControlOption(it.name.lowercase(), it.label) },
                visible = { d -> mediaSource(d) == ShokzMediaSource.BLUETOOTH },
                trailingAction = QuickControlTrailingAction(
                    icon = R.drawable.ic_settings,
                    description = R.string.pref_header_equalizer,
                    visible = { _, value -> ShokzEqualizer.fromPreference(value) == ShokzEqualizer.CUSTOM },
                    onClick = { d, context ->
                        DeviceSettingsActivity.start(context, d)
                    },
                ),
            )
        )

        if (supportsMp3()) {
            controls.add(
                QuickControlDialog.forPreference(
                    key = DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_MP3,
                    icon = R.drawable.ic_graphic_eq,
                    title = R.string.sony_equalizer_mp3,
                    defaultValue = ShokzEqualizer.STANDARD.name.lowercase(),
                    options = ShokzEqualizer.entries
                        .filter { ShokzMediaSource.MP3 in it.sources }
                        .map { QuickControlOption(it.name.lowercase(), it.label) },
                    visible = { d -> mediaSource(d) == ShokzMediaSource.MP3 },
                )
            )
            controls.addAll(
                QuickControls.fromDeviceSettings(
                    device,
                    keys = emptyList(),
                    optionalKeys = listOf(DeviceSettingsPreferenceConst.PREF_MEDIA_PLAYBACK_MODE),
                )
            )
        }

        return controls
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        multipointPairing()
        languages(Language.EN, Language.ZH, Language.JA, Language.KO)

        if (supportsMp3()) {
            enumList<ShokzMediaSource>(
                key = DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE,
                title = R.string.media_source,
                icon = R.drawable.ic_music_note,
                defaultValue = ShokzMediaSource.BLUETOOTH,
            )
        }

        if (supportsNoiseCancelling()) {
            category(
                key = "pref_key_header_shokz_noise_control",
                title = R.string.prefs_noise_control,
            ) {
                switchSetting(
                    key = DeviceSettingsPreferenceConst.PREF_ACTIVE_NOISE_CANCELLING_TOGGLE,
                    title = R.string.prefs_active_noise_cancelling,
                    icon = R.drawable.ic_noise_control_on,
                )
                seekbar(
                    key = DeviceSettingsPreferenceConst.PREF_SHOKZ_NOISE_CANCELLING_LEVEL,
                    title = R.string.prefs_active_noise_cancelling_level,
                    icon = R.drawable.ic_surround,
                    min = 0,
                    max = 100,
                    defaultValue = 100,
                    dependency = DeviceSettingsPreferenceConst.PREF_ACTIVE_NOISE_CANCELLING_TOGGLE,
                )
            }
        }

        if (supportsDolbyAtmos()) {
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_SHOKZ_DOLBY_ATMOS,
                title = R.string.shokz_dolby_atmos,
                icon = R.drawable.ic_surround,
            )
            if (supportsHeadTracking()) {
                switchSetting(
                    key = DeviceSettingsPreferenceConst.PREF_SHOKZ_HEAD_TRACKING,
                    title = R.string.shokz_head_tracking,
                    summary = R.string.shokz_head_tracking_summary,
                    icon = R.drawable.ic_surround,
                    dependency = DeviceSettingsPreferenceConst.PREF_SHOKZ_DOLBY_ATMOS,
                )
            }
        }

        if (supportsWearingDetection()) {
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_SHOKZ_WEARING_DETECTION,
                title = R.string.shokz_wearing_detection,
                icon = R.drawable.ic_ear_sound,
            )
        }

        // Equalizer presets - same enum, filtered to entries valid for each media source
        equalizerPreset<ShokzEqualizer>(
            key = DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_BLUETOOTH,
            title = R.string.sony_equalizer_bluetooth,
            defaultValue = ShokzEqualizer.STANDARD,
            filter = { supportsBluetoothEqualizer(it) },
            visibleWhen = { prefs ->
                !supportsMp3() || ShokzMediaSource.fromPreference(
                    prefs.getString(DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE, "")
                ) == ShokzMediaSource.BLUETOOTH
            },
        )

        if (supportsCustomEqualizer()) {
            screen(
                key = DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM,
                title = R.string.custom,
                icon = R.drawable.ic_graphic_eq,
                visibleWhen = { prefs ->
                    (!supportsMp3() || ShokzMediaSource.fromPreference(
                        prefs.getString(DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE, "")
                    ) == ShokzMediaSource.BLUETOOTH) &&
                        ShokzEqualizer.fromPreference(
                            prefs.getString(DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_BLUETOOTH, "")
                        ) == ShokzEqualizer.CUSTOM
                },
            ) {
                CUSTOM_EQUALIZER_BAND_KEYS.take(customEqualizerBandCount()).forEachIndexed { i, key ->
                    seekbar(
                        key = key,
                        title = CUSTOM_EQUALIZER_BAND_TITLES[i],
                        icon = R.drawable.ic_graphic_eq,
                        min = -5,
                        max = 5,
                        defaultValue = 0,
                    )
                }
            }
        }

        if (supportsMp3()) {
            equalizerPreset<ShokzEqualizer>(
                key = DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_MP3,
                title = R.string.sony_equalizer_mp3,
                defaultValue = ShokzEqualizer.STANDARD,
                filter = { ShokzMediaSource.MP3 in it.sources },
                visibleWhen = { prefs ->
                    ShokzMediaSource.fromPreference(
                        prefs.getString(DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE, "")
                    ) == ShokzMediaSource.MP3
                },
            )

            enumList<ShokzMp3PlaybackMode>(
                key = DeviceSettingsPreferenceConst.PREF_MEDIA_PLAYBACK_MODE,
                title = R.string.media_playback_mode,
                icon = R.drawable.ic_play,
                defaultValue = ShokzMp3PlaybackMode.NORMAL,
                visibleWhen = { prefs ->
                    ShokzMediaSource.fromPreference(
                        prefs.getString(DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE, "")
                    ) == ShokzMediaSource.MP3
                },
            )
        }
        if (supportsControls()) {
            xmlScreen(
                DeviceSpecificSettingsScreen.TOUCH_OPTIONS,
                R.xml.devicesettings_shokz_controls,
                childConnectedKeys = listOf(
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_CONTROLS_LONG_PRESS_MULTI_FUNCTION,
                    DeviceSettingsPreferenceConst.PREF_SHOKZ_CONTROLS_SIMULTANEOUS_VOLUME_UP_DOWN,
                ),
            )
        }
        xmlScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones,
            connectedOnly = false,
        )
    }

    override fun supportsAppNotifications(device: GBDevice): Boolean = true

    override fun supportsPhoneCalls(device: GBDevice): Boolean = true

    override fun supportsCallControl(device: GBDevice): Boolean = true

    companion object {
        val CUSTOM_EQUALIZER_BAND_KEYS = listOf(
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_1,
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_2,
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_3,
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_4,
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_5,
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_6,
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_7,
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_8,
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_9,
            DeviceSettingsPreferenceConst.PREF_SHOKZ_EQUALIZER_CUSTOM_BAND_10,
        )

        private val CUSTOM_EQUALIZER_BAND_TITLES = listOf(
            R.string.shokz_equalizer_band_1,
            R.string.shokz_equalizer_band_2,
            R.string.shokz_equalizer_band_3,
            R.string.shokz_equalizer_band_4,
            R.string.shokz_equalizer_band_5,
            R.string.shokz_equalizer_band_6,
            R.string.shokz_equalizer_band_7,
            R.string.shokz_equalizer_band_8,
            R.string.shokz_equalizer_band_9,
            R.string.shokz_equalizer_band_10,
        )
    }
}
