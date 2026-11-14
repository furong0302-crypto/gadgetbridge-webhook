/*  Copyright (C) 2024 Gadgetbridge contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch.WithingsScanwatchDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch.WithingsScanwatchSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractWithingsActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.WithingsUUIDs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.GetShortcutHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.GetUserHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.GetGlanceHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.GetLuminosityHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.GetMoveHandsHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.GetWearPosHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.WithingsEcgHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.EndOfTransmission;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.FeatureTagDeprecated;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.FeatureTagsUserId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.GlanceStatus;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.HrAlertThreshold;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.LocalNotification;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.LuminosityLevel;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ScreenSettings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ShortcutAction;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.TrackerMoveHands;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.TrackerWearPos;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsScreenId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.IconHelper;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity.WithingsActivityType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ImageData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ImageMetaData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WorkoutScreen;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.ExpectedResponse;

/**
 * Device support for Withings Scanwatch. The Scanwatch uses the same BLE protocol as the
 * Steel HR but with a different service UUID suffix (005d vs 0037).
 * All protocol logic is in {@link WithingsBaseDeviceSupport}.
 */
public class WithingsScanwatchDeviceSupport extends WithingsBaseDeviceSupport {

    private static final Logger logger = LoggerFactory.getLogger(WithingsScanwatchDeviceSupport.class);
    private static final int SCANWATCH_IMAGE_DATA_CHUNK_SIZE = 64;

    static final String PREF_SCREENS_SORTABLE = "withings_scanwatch_screens_sortable";
    private static final String PREF_SCREENS_LAST_SENT = "withings_scanwatch_screens_last_sent";
    private static final String PREF_HR_ALERT_LAST_SENT = "withings_scanwatch_hr_alert_last_sent";
    private static final String PREF_RESPIRATORY_AUTO_LAST_START = "withings_scanwatch_respiratory_auto_last_start";
    private static final String PREF_RESPIRATORY_AUTO_LAST_END = "withings_scanwatch_respiratory_auto_last_end";

    @Override
    protected WithingsUUIDs getWithingsUUIDs() {
        return WithingsUUIDs.SCANWATCH;
    }

    @Override
    protected Collection<WithingsUUIDs> getWithingsUUIDCandidates() {
        return Arrays.asList(WithingsUUIDs.SCANWATCH, WithingsUUIDs.SCANWATCH_2, WithingsUUIDs.SCANWATCH_LIGHT);
    }

    @Override
    public AbstractSampleProvider<? extends AbstractWithingsActivitySample> createSampleProvider(GBDevice device, DaoSession session) {
        return new WithingsScanwatchSampleProvider(device, session);
    }

    private static final String PREF_SHORTCUT_ACTION = "withings_scanwatch_shortcut_action";
    static final String PREF_ECG_ENABLE        = "withings_scanwatch_ecg_enable";
    static final String PREF_ECG_ACTIVATED     = "withings_scanwatch_ecg_activated";
    static final String PREF_SPO2_ENABLE       = "withings_scanwatch_spo2_enable";
    static final String PREF_SPO2_ACTIVATED    = "withings_scanwatch_spo2_activated";

    /** SpO2 mode: "on_demand" (manual measurement only) or "sleep" (also monitor during sleep). */
    static final String PREF_SPO2_MODE          = "withings_scanwatch_spo2_mode";
    /** Respiratory scan mode: "off", "automatic" (some nights), or "always" (every night). */
    static final String PREF_RESPIRATORY_SCAN   = "withings_scanwatch_respiratory_scan";
    /** True once respiratory scan has been enabled at least once (used for off-state base tag behavior). */
    static final String PREF_RESPIRATORY_ACTIVATED = "withings_scanwatch_respiratory_activated";
    /** Whether daytime AFib detection is enabled on the watch. */
    static final String PREF_AFIB_DAY_ENABLED   = "withings_scanwatch_afib_day_enabled";
    /** Whether night-time AFib detection is enabled on the watch. */
    static final String PREF_AFIB_NIGHT_ENABLED = "withings_scanwatch_afib_night_enabled";
    /** Whether quicklook / glance (raise-to-wake) is enabled on the watch. */
    static final String PREF_QUICKLOOK = "withings_scanwatch_quicklook";
    /** Whether auto-brightness is enabled on the watch. */
    static final String PREF_AUTO_BRIGHTNESS = "withings_scanwatch_auto_brightness";
    /** Manual brightness level (0-100) when auto-brightness is off. */
    static final String PREF_BRIGHTNESS_LEVEL = "withings_scanwatch_brightness_level";
    /** Whether tracker move-hands (move hands to 10:10 when screen turns on) is enabled. */
    static final String PREF_MOVE_HANDS = "withings_scanwatch_move_hands";
    /** Whether activity reminder is enabled on the watch. */
    static final String PREF_ACTIVITY_REMINDER = "withings_scanwatch_activity_reminder";

    /**
     * Resting heart rate alert mode: {@code "off"}, {@code "automatic"}, or {@code "custom"}.
     * <ul>
     *   <li>{@code "off"} - both high and low resting HR alerts are disabled.</li>
     *   <li>{@code "automatic"} - thresholds are computed from Gadgetbridge's own HR history.</li>
     *   <li>{@code "custom"} - user-specified thresholds via {@link #PREF_HR_ALERT_LOW} /
     *       {@link #PREF_HR_ALERT_HIGH}.</li>
     * </ul>
     */
    static final String PREF_HR_ALERT_MODE = "withings_scanwatch_hr_alert_mode";
    /**
     * Low resting HR alert threshold in BPM (integer string), used when mode is {@code "custom"}.
     * Clamped to [30, 200] before sending to device.
     */
    static final String PREF_HR_ALERT_LOW  = "withings_scanwatch_hr_alert_low";
    /**
     * High resting HR alert threshold in BPM (integer string), used when mode is {@code "custom"}.
     * Clamped to [30, 200] before sending to device.
     */
    static final String PREF_HR_ALERT_HIGH = "withings_scanwatch_hr_alert_high";

    /**
     * Number of days of HR history used for automatic threshold calculation.
     * @see #computeAutoHrThresholds()
     */
    private static final int AUTO_HR_HISTORY_DAYS = 30;
    /** Minimum valid BPM value accepted by the watch. */
    private static final int HR_BPM_MIN = 30;
    /** Maximum valid BPM value accepted by the watch. */
    private static final int HR_BPM_MAX = 200;

    @Override
    protected void addExtraSyncCommands() {
        // Fetch the watch's Withings userId first so it is stored in prefs before any command
        // that requires it (screen list, HR alert thresholds, feature tags).
        addSimpleConversationToQueue(
                new WithingsMessage(WithingsMessageType.GET_USER),
                new GetUserHandler(this)
        );
        addSimpleConversationToQueue(
                new WithingsMessage(WithingsMessageType.GET_SHORTCUT),
                new GetShortcutHandler(this)
        );
        addSimpleConversationToQueue(
                new WithingsMessage(WithingsMessageType.GLANCE_GET),
                new GetGlanceHandler(this)
        );
        addSimpleConversationToQueue(
                new WithingsMessage(WithingsMessageType.GET_LUMINOSITY_LEVEL),
                new GetLuminosityHandler(this)
        );
        addSimpleConversationToQueue(
                new WithingsMessage(WithingsMessageType.GET_TRACKER_MOVE_HANDS),
                new GetMoveHandsHandler(this)
        );
        addSimpleConversationToQueue(
                new WithingsMessage(WithingsMessageType.GET_TRACKER_WEAR_POS),
                new GetWearPosHandler(this)
        );

        // ScanWatch health feature tags stay in the regular sync flow.
        // Notification enablement is handled separately by the base class via
        // the dedicated notifications toggle flow, which must send
        // TAG_NOTIFICATIONS in its own standalone 0x0987 command.
        //
        // Local notifications and HR alert thresholds are still sent here because
        // they are ScanWatch-specific commands that don't exist in the base class.
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
        final boolean ecgActivated = prefs.getBoolean(PREF_ECG_ACTIVATED, false);
        final boolean spo2Activated = prefs.getBoolean(PREF_SPO2_ACTIVATED, false);
        final String  spo2Mode  = prefs.getString(PREF_SPO2_MODE,           "on_demand");
        final String  respScan  = prefs.getString(PREF_RESPIRATORY_SCAN,    "off");
        final boolean afibDay   = prefs.getBoolean(PREF_AFIB_DAY_ENABLED,   false);
        final boolean afibNight = prefs.getBoolean(PREF_AFIB_NIGHT_ENABLED, false);
        final boolean activityReminderEnabled = prefs.getBoolean(PREF_ACTIVITY_REMINDER, false);
        final String  hrMode    = prefs.getString(PREF_HR_ALERT_MODE,       "off");
        final boolean hrAlertsOn = !"off".equals(hrMode);

        addFeatureTagsCommand(prefs, ecgActivated, spo2Activated, spo2Mode, respScan, afibDay, afibNight, hrAlertsOn);
        addLocalNotificationsCommand(afibDay, afibNight, hrAlertsOn, activityReminderEnabled);
        queueHrAlertCommandIfChanged(prefs, hrMode);
    }

    @Override
    protected void addExtraSyncCommandsWhenSkippingFullSync() {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
        final boolean ecgActivated = prefs.getBoolean(PREF_ECG_ACTIVATED, false);
        final boolean spo2Activated = prefs.getBoolean(PREF_SPO2_ACTIVATED, false);
        final String spo2Mode = prefs.getString(PREF_SPO2_MODE, "on_demand");
        final String respScan = prefs.getString(PREF_RESPIRATORY_SCAN, "off");
        final boolean afibDay = prefs.getBoolean(PREF_AFIB_DAY_ENABLED, false);
        final boolean afibNight = prefs.getBoolean(PREF_AFIB_NIGHT_ENABLED, false);
        final boolean activityReminderEnabled = prefs.getBoolean(PREF_ACTIVITY_REMINDER, false);
        final String hrMode = prefs.getString(PREF_HR_ALERT_MODE, "off");
        final boolean hrAlertsOn = !"off".equals(hrMode);

        logger.info("Reapplying ScanWatch feature tags during lightweight sync (ECG={}, SpO2={}, mode={}, resp={})",
                ecgActivated, spo2Activated, spo2Mode, respScan);

        // Re-assert on-watch feature flags even when we're skipping full history sync.
        // This helps recover cases where ECG/SpO2 state appears to drift between regular syncs.
        addFeatureTagsCommand(prefs, ecgActivated, spo2Activated, spo2Mode, respScan, afibDay, afibNight, hrAlertsOn);
        addLocalNotificationsCommand(afibDay, afibNight, hrAlertsOn, activityReminderEnabled);
        queueHrAlertCommandIfChanged(prefs, hrMode);
    }

    @Override
    protected WithingsEcgHandler createEcgHandler() {
        return getScanwatchCoordinator().supportsEcgMeasurement(gbDevice) ? new WithingsEcgHandler(this, gbDevice) : null;
    }

    private WithingsScanwatchDeviceCoordinator getScanwatchCoordinator() {
        return (WithingsScanwatchDeviceCoordinator) gbDevice.getDeviceCoordinator();
    }

    @Override
    public boolean prefersLatestConversationNotification() {
        return true;
    }

    @Override
    protected boolean supportsStoredMeasureSync() {
        return getScanwatchCoordinator().supportsSpo2(gbDevice)
                || getScanwatchCoordinator().supportsEcgMeasurement(gbDevice);
    }

    @Override
    protected boolean supportsSleepBreathingSync() {
        return getScanwatchCoordinator().supportsRespiratoryScan(gbDevice);
    }

    /**
     * Keep notification feature tags separate from ScanWatch health tags.
     *
     * <p>The watch expects TAG_NOTIFICATIONS to be sent in a standalone 0x0987 command
     * alongside only the standard companion tags 0x0035 and 0x0058. ScanWatch-specific
     * health tags (ECG, SpO2, respiratory, AFib, HR alerts) are sent separately in the
     * regular sync flow via {@link #addExtraSyncCommands()} and in preference handlers.
     */
    @Override
    protected void addFeatureTagsMessage() {
        super.addFeatureTagsMessage();
    }

    @Override
    protected void addPostNotificationConfigurationCommands(final boolean enabled) {
        if (!enabled) {
            return;
        }

        // ANCS recovery / CCC-ready re-enable paths call this method. On ScanWatch,
        // sending only the notification tag bundle can leave ECG/SpO2 features inactive
        // until the next full sync. Re-assert the ScanWatch health tag bundle in the
        // same transaction to keep ECG/SpO2/respiratory/AFib state stable.
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
        final boolean ecgActivated = prefs.getBoolean(PREF_ECG_ACTIVATED, false);
        final boolean spo2Activated = prefs.getBoolean(PREF_SPO2_ACTIVATED, false);
        final String spo2Mode = prefs.getString(PREF_SPO2_MODE, "on_demand");
        final String respScan = prefs.getString(PREF_RESPIRATORY_SCAN, "off");
        final boolean afibDay = prefs.getBoolean(PREF_AFIB_DAY_ENABLED, false);
        final boolean afibNight = prefs.getBoolean(PREF_AFIB_NIGHT_ENABLED, false);
        final String hrMode = prefs.getString(PREF_HR_ALERT_MODE, "off");
        final boolean hrAlertsOn = !"off".equals(hrMode);

        logger.info("Reapplying ScanWatch health feature tags after notification config (ECG={}, SpO2={}, mode={}, resp={})",
                ecgActivated, spo2Activated, spo2Mode, respScan);

        addFeatureTagsCommand(prefs, ecgActivated, spo2Activated, spo2Mode, respScan, afibDay, afibNight, hrAlertsOn);
    }

    @Override
    protected boolean handleExtraConfiguration(String config) {
        if (PREF_SCREENS_SORTABLE.equals(config)) {
            addScreenListCommands();
            sendQueue();
            return true;
        }
        if (PREF_SHORTCUT_ACTION.equals(config)) {
            final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
            final String val = prefs.getString(PREF_SHORTCUT_ACTION, "0");
            final byte action;
            try {
                action = Byte.parseByte(val);
            } catch (NumberFormatException e) {
                logger.warn("Invalid shortcut action value: {}", val);
                return true;
            }
            if (!isShortcutActionSupported(action,
                    getScanwatchCoordinator().supportsEcgMeasurement(gbDevice),
                    getScanwatchCoordinator().supportsSpo2(gbDevice))) {
                logger.warn("Ignoring unsupported shortcut action: {}", action);
                return true;
            }
            final WithingsMessage msg = new WithingsMessage(WithingsMessageType.SET_SHORTCUT);
            msg.addDataStructure(new ShortcutAction(action));
            addSimpleConversationToQueue(msg);
            sendQueue();
            return true;
        }
        if (PREF_ECG_ENABLE.equals(config) || PREF_SPO2_ENABLE.equals(config)
                || PREF_SPO2_MODE.equals(config) || PREF_RESPIRATORY_SCAN.equals(config)
                || PREF_AFIB_DAY_ENABLED.equals(config) || PREF_AFIB_NIGHT_ENABLED.equals(config)
                || PREF_ACTIVITY_REMINDER.equals(config)) {
            final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
            persistOneWayFeatureActivation(prefs, PREF_ECG_ENABLE, PREF_ECG_ACTIVATED);
            persistOneWayFeatureActivation(prefs, PREF_SPO2_ENABLE, PREF_SPO2_ACTIVATED);
            final boolean ecgActivated = prefs.getBoolean(PREF_ECG_ACTIVATED, false);
            final boolean spo2Activated = prefs.getBoolean(PREF_SPO2_ACTIVATED, false);
            final String  spo2Mode  = prefs.getString(PREF_SPO2_MODE,           "on_demand");
            final String  respScan  = prefs.getString(PREF_RESPIRATORY_SCAN,    "off");
            final boolean afibDay   = prefs.getBoolean(PREF_AFIB_DAY_ENABLED,   false);
            final boolean afibNight = prefs.getBoolean(PREF_AFIB_NIGHT_ENABLED, false);
            final boolean activityReminderEnabled = prefs.getBoolean(PREF_ACTIVITY_REMINDER, false);
            final String  hrMode    = prefs.getString(PREF_HR_ALERT_MODE,       "off");
            final boolean hrAlertsOn = !"off".equals(hrMode);
            addFeatureTagsCommand(prefs, ecgActivated, spo2Activated, spo2Mode, respScan, afibDay, afibNight, hrAlertsOn);
            addLocalNotificationsCommand(afibDay, afibNight, hrAlertsOn, activityReminderEnabled);
            sendQueue();
            return true;
        }
        if (PREF_HR_ALERT_MODE.equals(config) || PREF_HR_ALERT_LOW.equals(config)
                || PREF_HR_ALERT_HIGH.equals(config)) {
            final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
            final boolean ecgActivated = prefs.getBoolean(PREF_ECG_ACTIVATED, false);
            final boolean spo2Activated = prefs.getBoolean(PREF_SPO2_ACTIVATED, false);
            final String  spo2Mode  = prefs.getString(PREF_SPO2_MODE,           "on_demand");
            final String  respScan  = prefs.getString(PREF_RESPIRATORY_SCAN,    "off");
            final boolean afibDay   = prefs.getBoolean(PREF_AFIB_DAY_ENABLED,   false);
            final boolean afibNight = prefs.getBoolean(PREF_AFIB_NIGHT_ENABLED, false);
            final boolean activityReminderEnabled = prefs.getBoolean(PREF_ACTIVITY_REMINDER, false);
            final String  hrMode    = prefs.getString(PREF_HR_ALERT_MODE,       "off");
            final boolean hrAlertsOn = !"off".equals(hrMode);
            addFeatureTagsCommand(prefs, ecgActivated, spo2Activated, spo2Mode, respScan, afibDay, afibNight, hrAlertsOn);
            queueHrAlertCommandIfChanged(prefs, hrMode);
            addLocalNotificationsCommand(afibDay, afibNight, hrAlertsOn, activityReminderEnabled);
            sendQueue();
            return true;
        }
        if (PREF_QUICKLOOK.equals(config)) {
            final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
            final boolean enabled = prefs.getBoolean(PREF_QUICKLOOK, false);
            final WithingsMessage msg = new WithingsMessage(WithingsMessageType.GLANCE_SET,
                    new GlanceStatus(enabled));
            addSimpleConversationToQueue(msg);
            sendQueue();
            return true;
        }
        if (PREF_AUTO_BRIGHTNESS.equals(config) || PREF_BRIGHTNESS_LEVEL.equals(config)) {
            sendLuminosityLevel();
            return true;
        }
        if (PREF_MOVE_HANDS.equals(config)) {
            final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
            final boolean enabled = prefs.getBoolean(PREF_MOVE_HANDS, false);
            final WithingsMessage msg = new WithingsMessage(WithingsMessageType.SET_TRACKER_MOVE_HANDS,
                    new TrackerMoveHands(enabled));
            addSimpleConversationToQueue(msg);
            sendQueue();
            return true;
        }
        if (DeviceSettingsPreferenceConst.PREF_WEARLOCATION.equals(config)) {
            final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
            final String location = prefs.getString(DeviceSettingsPreferenceConst.PREF_WEARLOCATION, "left");
            final byte pos = "left".equals(location) ? TrackerWearPos.POS_LEFT_WRIST : TrackerWearPos.POS_RIGHT_WRIST;
            final WithingsMessage msg = new WithingsMessage(WithingsMessageType.SET_TRACKER_WEAR_POS,
                    new TrackerWearPos(pos));
            addSimpleConversationToQueue(msg);
            sendQueue();
            return true;
        }
        return false;
    }

    /**
     * Reads the current auto-brightness / brightness-level preferences and sends the
     * corresponding {@code CMD_SET_LUMINOSITY_LEVEL} (0x0941) command to the watch.
     */
    private void sendLuminosityLevel() {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
        final boolean autoMode = prefs.getBoolean(PREF_AUTO_BRIGHTNESS, true);
        final byte mode;
        final byte level;
        if (autoMode) {
            mode = LuminosityLevel.MODE_AUTO;
            level = 0;
        } else {
            mode = LuminosityLevel.MODE_MANUAL;
            int rawLevel;
            try {
                rawLevel = Integer.parseInt(prefs.getString(PREF_BRIGHTNESS_LEVEL, "50"));
            } catch (NumberFormatException e) {
                rawLevel = 50;
            }
            level = (byte) Math.max(0, Math.min(100, rawLevel));
        }
        final WithingsMessage msg = new WithingsMessage(WithingsMessageType.SET_LUMINOSITY_LEVEL,
                new LuminosityLevel(mode, level));
        addSimpleConversationToQueue(msg);
        sendQueue();
    }

    private void queueHrAlertCommandIfChanged(final SharedPreferences prefs, final String hrMode) {
        final String currentState = serializeHrAlertState(prefs, hrMode);
        final String lastSentState = prefs.getString(PREF_HR_ALERT_LAST_SENT, null);
        if (Objects.equals(currentState, lastSentState)) {
            logger.debug("HR alert settings unchanged ({}), skipping", currentState);
            return;
        }

        addHrAlertCommand(hrMode, prefs);
        prefs.edit().putString(PREF_HR_ALERT_LAST_SENT, currentState).apply();
    }

    private static String serializeHrAlertState(final SharedPreferences prefs, final String hrMode) {
        if ("custom".equals(hrMode)) {
            return hrMode + '|' + prefs.getString(PREF_HR_ALERT_LOW, "40") + '|'
                    + prefs.getString(PREF_HR_ALERT_HIGH, "100");
        }

        return hrMode;
    }

    /**
      * Queues a {@code CMD_FEATURE_TAGS_SET_DEPRECATED_V2} (0x0987) message activating the
      * feature tags required for ECG, SpO2, respiratory scan, AFib, and/or resting HR alerts.
     *
     * <p>Tag set derived from HCI captures (corrected mapping):
     * <ul>
      *   <li>ECG enabled once: tag 0x0004</li>
      *   <li>SpO2 enabled once, sleep mode only: tag 0x0005</li>
     *   <li>Respiratory off:       tag 0x000A only if respiratory was previously activated</li>
     *   <li>Respiratory automatic: tags 0x0009, 0x000A, 0x000B with app-driven schedule windows</li>
     *   <li>Respiratory always-on: tags 0x0009 (start=0, end=0), 0x000A</li>
     *   <li>AFib on: tags 0x000E, 0x0011</li>
     *   <li>HR alerts on: tag 0x0016 (LOW_HR)</li>
     * </ul>
      * 0x0035 and 0x0058 are always sent. 0x000F (SpO2 measurement) is only sent after
      * the user has explicitly enabled SpO2 once.
      * Official captures use userId=0 in the 0x0145 feature-tag header for this command.
      */
    private void addFeatureTagsCommand(final SharedPreferences prefs,
                                       final boolean ecgActivated, final boolean spo2Activated,
                                       final String spo2Mode, final String respiratoryScan,
                                       final boolean afibDayEnabled, final boolean afibNightEnabled,
                                       final boolean hrAlertsOn) {
        final WithingsMessage msg = new WithingsMessage(WithingsMessageType.SET_FEATURE_TAGS_DEPRECATED);
        final SharedPreferences featurePrefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
        msg.addDataStructure(new FeatureTagsUserId(0));

        final WithingsScanwatchDeviceCoordinator coordinator = getScanwatchCoordinator();
        final boolean ecgSupported = coordinator.supportsEcgMeasurement(gbDevice);
        final boolean spo2Supported = coordinator.supportsSpo2(gbDevice);
        final boolean respiratorySupported = coordinator.supportsRespiratoryScan(gbDevice);
        final boolean spo2SleepMode = spo2Supported && spo2Activated && "sleep".equals(spo2Mode);

        final boolean respAutomatic = respiratorySupported && "automatic".equals(respiratoryScan);
        final boolean respAlways    = respiratorySupported && "always".equals(respiratoryScan);
        final boolean respActive    = respAutomatic || respAlways;
        boolean respEverActivated   = featurePrefs.getBoolean(PREF_RESPIRATORY_ACTIVATED, false);

        if (respActive && !respEverActivated) {
            featurePrefs.edit().putBoolean(PREF_RESPIRATORY_ACTIVATED, true).apply();
            respEverActivated = true;
        }

        final boolean includeRespBase = respActive || respEverActivated;

        if (ecgSupported && ecgActivated) {
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_ECG_TERMS));
        }
        if (spo2SleepMode) {
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_SPO2_SLEEP));
        }
        // Respiratory scan tags
        if (respAutomatic) {
            final int[] window = computeAutomaticRespiratoryWindow(prefs);
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_RESP_SCAN, window[0], window[1]));
        } else if (respAlways) {
            // Always-on: TAG_RESP_SCAN with start=0, end=0
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_RESP_SCAN));
        }
        // Off: TAG_RESP_SCAN (0x0009) is absent
        // TAG_RESP_BASE (0x000A) is present only after respiratory has been activated at least once
        if (includeRespBase) {
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_RESP_BASE));
        }
        if (respAutomatic) {
            // TAG_RESP_AUTO (0x000B) is only present in automatic mode
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_RESP_AUTO));
        }
        // AFib detection: tags 0x000E + 0x0011
        if (ecgSupported && (afibDayEnabled || afibNightEnabled)) {
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_AFIB));
        }
        if (spo2Supported && spo2Activated) {
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_SPO2_MEAS));
        }
        if (ecgSupported && (afibDayEnabled || afibNightEnabled)) {
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_AFIB_2));
        }
        if (hrAlertsOn) {
            // TAG_LOW_HR (0x0016) is sent when resting HR alerts are enabled.
            msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_LOW_HR));
        }
        // Baseline tags always present
        msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_0x0035));
        msg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_0x0058));
        msg.addDataStructure(new EndOfTransmission());
        addSimpleConversationToQueue(msg);
        addFeatureTagsCommitCommands();
    }

    private static void persistOneWayFeatureActivation(final SharedPreferences prefs,
                                                       final String enablePref,
                                                       final String activatedPref) {
        final boolean enabled = prefs.getBoolean(enablePref, false);
        final boolean activated = prefs.getBoolean(activatedPref, false);

        if (enabled && !activated) {
            prefs.edit().putBoolean(activatedPref, true).apply();
        }
    }

    private int[] computeAutomaticRespiratoryWindow(final SharedPreferences prefs) {
        final int now = (int) (System.currentTimeMillis() / 1000);
        final int lastStart = prefs.getInt(PREF_RESPIRATORY_AUTO_LAST_START, 0);
        final int lastEnd = prefs.getInt(PREF_RESPIRATORY_AUTO_LAST_END, 0);

        final int start;
        final int end;
        if (lastStart > 0 && lastEnd > lastStart) {
            if (lastStart < now && isPreviousWindowLastNight(lastStart, now)) {
                final int duration = Math.max(1, lastEnd - lastStart);
                start = addMonthsToEpochSeconds(lastStart, 1);
                end = start + duration;
                logger.debug("Respiratory automatic: previous window was last night, scheduling next in one month {} -> {}", start, end);
            } else if (lastEnd > now) {
                start = lastStart;
                end = lastEnd;
            } else {
                final int[] fallback = computeDefaultAutomaticRespiratoryWindow(now);
                start = fallback[0];
                end = fallback[1];
            }
        } else {
            final int[] fallback = computeDefaultAutomaticRespiratoryWindow(now);
            start = fallback[0];
            end = fallback[1];
        }

        prefs.edit()
                .putInt(PREF_RESPIRATORY_AUTO_LAST_START, start)
                .putInt(PREF_RESPIRATORY_AUTO_LAST_END, end)
                .apply();
        return new int[]{start, end};
    }

    private static int[] computeDefaultAutomaticRespiratoryWindow(final int now) {
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        final int noonNextDay = (int) (cal.getTimeInMillis() / 1000);
        return new int[]{now, noonNextDay};
    }

    private static int addMonthsToEpochSeconds(final int epochSeconds, final int months) {
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochSeconds * 1000L);
        cal.add(Calendar.MONTH, months);
        return (int) (cal.getTimeInMillis() / 1000);
    }

    private static boolean isPreviousWindowLastNight(final int startEpochSeconds, final int nowEpochSeconds) {
        final Calendar start = Calendar.getInstance();
        start.setTimeInMillis(startEpochSeconds * 1000L);
        final Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowEpochSeconds * 1000L);

        final Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        final boolean startedYesterday = start.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
                && start.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR);

        // When the automatic window starts shortly after midnight, it still represents
        // the scan for the previous night.
        final boolean startedEarlyToday = start.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && start.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                && start.get(Calendar.HOUR_OF_DAY) < 6;

        return startedYesterday || startedEarlyToday;
    }

    /**
     * Queues a {@code CMD_LOCAL_NOTIFICATIONS_CONFIG_SET} (0x0990) message configuring the
     * five on-watch notification slots.
     *
     * <p>All five slots must always be sent together. Slot mapping from HCI captures:
     * <ol>
     *   <li>PPG_AFIB - enabled when AFib daytime is on</li>
     *   <li>HIGH_HR  - enabled when resting HR alerts are on (independent of AFib)</li>
     *   <li>LOW_HR   - enabled when resting HR alerts are on (independent of AFib)</li>
     *   <li>SLOT_4   - Activity reminder</li>
     *   <li>PPG_AFIB_NIGHT - enabled when AFib night is on</li>
     * </ol>
     *
     * <p>Note: HIGH_HR (slot 2) and LOW_HR (slot 3) are independent of AFib.
     * The resting HR alert enable/disable state is controlled separately via
     * {@link #addHrAlertCommand}.
     */
    private void addLocalNotificationsCommand(final boolean afibDayEnabled,
                                              final boolean afibNightEnabled,
                                              final boolean hrAlertsOn,
                                              final boolean activityReminderEnabled) {
        final WithingsMessage msg = new WithingsMessage(WithingsMessageType.SET_LOCAL_NOTIFICATIONS);
        // Order must match the official app: AFIB_DAY(1), SLOT_4(4), AFIB_NIGHT(5), HIGH_HR(2), LOW_HR(3)
        msg.addDataStructure(new LocalNotification(LocalNotification.NOTIF_PPG_AFIB,
                afibDayEnabled ? LocalNotification.STATUS_ENABLED : LocalNotification.STATUS_DISABLED));
        // Slot 4 tracks activity reminder on ScanWatch.
        msg.addDataStructure(new LocalNotification(LocalNotification.NOTIF_SLOT_4,
                activityReminderEnabled ? LocalNotification.STATUS_ENABLED : LocalNotification.STATUS_DISABLED));
        msg.addDataStructure(new LocalNotification(LocalNotification.NOTIF_PPG_AFIB_NIGHT,
                afibNightEnabled ? LocalNotification.STATUS_ENABLED : LocalNotification.STATUS_DISABLED));
        msg.addDataStructure(new LocalNotification(LocalNotification.NOTIF_HIGH_HR,
                hrAlertsOn ? LocalNotification.STATUS_ENABLED : LocalNotification.STATUS_DISABLED));
        msg.addDataStructure(new LocalNotification(LocalNotification.NOTIF_LOW_HR,
                hrAlertsOn ? LocalNotification.STATUS_ENABLED : LocalNotification.STATUS_DISABLED));
        msg.addDataStructure(new EndOfTransmission());
        addSimpleConversationToQueue(msg);
    }

    /**
     * Queues a {@code CMD_SET_HR_ALERT_THRESHOLDS} (0x098e) message configuring the resting
     * heart rate alert thresholds and their enabled/disabled state.
     *
     * <p>The message always contains both a LOW and a HIGH threshold entry.  When the mode is
     * {@code "off"} both entries are sent with {@code enabled=0} but the threshold values are
     * preserved from the previous setting (or fall back to the default custom values) so that the
     * watch can restore them if alerts are re-enabled.
     *
     * <p>The watch echoes the command type (0x098E) back as the response when the payload is
     * accepted, so the default {@link ExpectedResponse#SIMPLE} is used.  Earlier versions of this
     * code used {@link ExpectedResponse#NONE} as a workaround for a stall caused by the watch
     * returning {@code CMD_ERROR (0x0100)} when it received a malformed payload; that workaround
     * is no longer necessary now that the payload is correct.
     *
     * @param hrMode {@code "off"}, {@code "automatic"}, or {@code "custom"}
     * @param prefs  device-specific {@link SharedPreferences} from which to read custom thresholds
     */
    private void addHrAlertCommand(final String hrMode, final SharedPreferences prefs) {
        final byte enabled = "off".equals(hrMode) ? HrAlertThreshold.DISABLED : HrAlertThreshold.ENABLED;

        final int lowBpm;
        final int highBpm;

        if ("automatic".equals(hrMode)) {
            final int[] auto = computeAutoHrThresholds();
            lowBpm  = auto[0];
            highBpm = auto[1];
        } else {
            // "custom" or "off" - read persisted values (defaults: low=40, high=100)
            int rawLow  = 40;
            int rawHigh = 100;
            try {
                rawLow  = Integer.parseInt(prefs.getString(PREF_HR_ALERT_LOW,  "40"));
                rawHigh = Integer.parseInt(prefs.getString(PREF_HR_ALERT_HIGH, "100"));
            } catch (NumberFormatException e) {
                logger.warn("Invalid HR alert threshold pref: low='{}' high='{}'",
                        prefs.getString(PREF_HR_ALERT_LOW, "40"),
                        prefs.getString(PREF_HR_ALERT_HIGH, "100"));
            }
            lowBpm  = Math.max(HR_BPM_MIN, Math.min(HR_BPM_MAX, rawLow));
            highBpm = Math.max(HR_BPM_MIN, Math.min(HR_BPM_MAX, rawHigh));
        }

        final WithingsMessage msg = new WithingsMessage(WithingsMessageType.SET_HR_ALERT_THRESHOLDS);
        // Use the Withings userId stored during the GET_USER handshake. The watch rejects userId=0
        // with CMD_ERROR(-4); if not yet stored, fall back to hardcoded userId.
        // TODO: replace hardcoded userId with a user-configurable setting
        final int userId = prefs.getInt(WithingsBaseDeviceSupport.PREF_WITHINGS_USER_ID, 0x01f53022);
        msg.addDataStructure(new FeatureTagsUserId(userId));
        msg.addDataStructure(new HrAlertThreshold(HrAlertThreshold.DIRECTION_LOW,  enabled, (byte) lowBpm));
        msg.addDataStructure(new HrAlertThreshold(HrAlertThreshold.DIRECTION_HIGH, enabled, (byte) highBpm));
        msg.addDataStructure(new EndOfTransmission());
        addSimpleConversationToQueue(msg);
    }

    /**
     * Computes automatic resting heart rate alert thresholds from Gadgetbridge's own HR history.
     *
     * <p>Queries the last {@value #AUTO_HR_HISTORY_DAYS} days of activity samples, takes the
     * average of all valid (measured) HR readings, then sets:
     * <ul>
     *   <li>Low threshold  = average - 20 bpm  (floor {@value #HR_BPM_MIN})</li>
     *   <li>High threshold = average + 30 bpm  (ceiling {@value #HR_BPM_MAX})</li>
     * </ul>
     * If no HR history is available the defaults 40 / 100 bpm are returned.
     *
     * @return int[2] where [0] = low threshold and [1] = high threshold, in BPM
     */
    private int[] computeAutoHrThresholds() {
        final int defaultLow  = 40;
        final int defaultHigh = 100;

        final Calendar cal = Calendar.getInstance();
        final int tsTo   = (int) (cal.getTimeInMillis() / 1000);
        cal.add(Calendar.DAY_OF_YEAR, -AUTO_HR_HISTORY_DAYS);
        final int tsFrom = (int) (cal.getTimeInMillis() / 1000);

        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            final AbstractSampleProvider<? extends AbstractWithingsActivitySample> provider =
                    createSampleProvider(gbDevice, dbHandler.getDaoSession());
            final List<? extends AbstractWithingsActivitySample> samples =
                    provider.getAllActivitySamples(tsFrom, tsTo);

            long hrSum   = 0;
            int  hrCount = 0;
            for (final AbstractWithingsActivitySample sample : samples) {
                final int hr = sample.getHeartRate();
                if (hr > ActivitySample.NOT_MEASURED && hr > 0) {
                    hrSum += hr;
                    hrCount++;
                }
            }
            if (hrCount == 0) {
                logger.debug("No HR history for auto thresholds, using defaults {}/{}", defaultLow, defaultHigh);
                return new int[]{defaultLow, defaultHigh};
            }
            final int avg = (int) (hrSum / hrCount);
            logger.debug("Auto HR thresholds: avg={}bpm from {} samples", avg, hrCount);
            final int low  = Math.max(HR_BPM_MIN, avg - 20);
            final int high = Math.min(HR_BPM_MAX, avg + 30);
            logger.debug("Auto HR thresholds computed: low={}bpm, high={}bpm", low, high);
            return new int[]{low, high};
        } catch (Exception e) {
            logger.warn("Could not read HR history for auto thresholds: {}", e.getMessage());
            return new int[]{defaultLow, defaultHigh};
        }
    }

    /**
     * Builds a SET_WORKOUT_SCREEN message for the ScanWatch with correct mode/flags and
     * two icon sizes (20x20 at idx=0, 28x28 at idx=1) as required by the ScanWatch protocol.
     */
    @NonNull
    @Override
    protected Message createWorkoutScreenMessage(String workoutType, ExpectedResponse expectedResponse) {
        final WithingsActivityType activityType = WithingsActivityType.fromPrefValue(workoutType);
        final int code = activityType.getCode();

        Message message = new WithingsMessage(WithingsMessageType.SET_WORKOUT_SCREEN, expectedResponse);

        // Workout screen settings with correct mode and flags
        WorkoutScreen workoutScreen = new WorkoutScreen();
        workoutScreen.setId(code);
        workoutScreen.setName(getWorkoutScreenName(workoutType, activityType));
        workoutScreen.setMode(activityType.getWorkoutMode());
        workoutScreen.yetunknown2 = activityType.getWorkoutFlags();
        message.addDataStructure(workoutScreen);

        // Get the drawable for this activity type
        final int drawableId = activityType.toActivityKind().getIcon();
        final Drawable drawable = getContext().getDrawable(drawableId);

        // Icon 0: 20x20
        ImageMetaData meta0 = new ImageMetaData();
        meta0.setWidth((byte) 20);
        meta0.setHeight((byte) 20);
        message.addDataStructure(meta0);

        addImageDataStructures(message, IconHelper.getIconBytesFromDrawable(drawable, 20, 20));

        // Icon 1: 28x28
        ImageMetaData meta1 = new ImageMetaData();
        meta1.setIndex((byte) 1);
        meta1.setWidth((byte) 28);
        meta1.setHeight((byte) 28);
        message.addDataStructure(meta1);

        addImageDataStructures(message, IconHelper.getIconBytesFromDrawable(drawable, 28, 28));

        return message;
    }

    private void addImageDataStructures(final Message message, final byte[] imageData) {
        for (final byte[] chunk : IconHelper.splitImageData(imageData, SCANWATCH_IMAGE_DATA_CHUNK_SIZE)) {
            final ImageData imageChunk = new ImageData();
            imageChunk.setImageData(chunk);
            message.addDataStructure(imageChunk);
        }
    }

    /**
     * Builds the Scanwatch screen list from the user's drag-sort preference.
     *
     * <p>The preference {@code withings_scanwatch_screens_sortable} stores a comma-separated list
     * of screen value keys in the order the user has arranged them. Only screens present in the
     * list are enabled; removing an entry from the list disables that screen on the watch.
     *
     * <p>Display order on the watch is determined by the <em>sequence</em> of entries in the
     * SET_SCREEN_LIST packet - entries are sent in the same order the user arranged them. Each
     * screen also carries a fixed {@code idOnDevice} byte (confirmed from BLE captures) that is
     * not a position index; it is a fixed property of each screen.
     *
     * <p>Screen ID <-> idOnDevice mapping is defined in
     * {@link WithingsScreenId#getScanwatchIdOnDevice(int)}.
     *
     * <p><b>Pinning:</b> The {@code "date"} screen (Watch face / Date) is always enforced at
     * position 0 before sending, regardless of what is stored in the preference. If it is missing
     * from the stored list it is re-inserted; if it is present but not first it is moved to the
     * front. The UI-layer customizer also normalises the stored value on every change, so the two
     * layers stay in sync.
     *
     * <p>This method is a no-op if the current screen list (after pinning) matches what was last
     * successfully sent to the watch.
     */
    @Override
    protected void addScreenListCommands() {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());
        final int userId = prefs.getInt(WithingsBaseDeviceSupport.PREF_WITHINGS_USER_ID, 0);
        if (userId <= 0) {
            logger.warn("Skipping SET_SCREEN_LIST: missing confirmed watch userId (value={})", userId);
            return;
        }

        final List<String> defaultScreens = Arrays.asList(
                getContext().getResources().getStringArray(R.array.pref_withings_scanwatch_screens_default));

        final String screensPref = prefs.getString(PREF_SCREENS_SORTABLE, null);
        List<String> enabledScreens;
        if (screensPref == null || screensPref.isEmpty()) {
            enabledScreens = new ArrayList<>(defaultScreens);
        } else {
            enabledScreens = new ArrayList<>(Arrays.asList(screensPref.split(",")));
        }

        enabledScreens = effectiveScreenKeys(enabledScreens,
                getScanwatchCoordinator().supportsEcgMeasurement(gbDevice),
                getScanwatchCoordinator().supportsSpo2(gbDevice));

        // "Watch face (Date)" is always pinned at position 0. Enforce this at send time
        // regardless of what the UI preference contains, so the watch is always consistent
        // even if the user somehow bypassed the UI-level constraint.
        final String PINNED_SCREEN = "date";
        enabledScreens.remove(PINNED_SCREEN);
        enabledScreens.add(0, PINNED_SCREEN);

        // Normalise to a canonical comma-separated string for comparison
        final String currentValue = String.join(",", enabledScreens);
        final String lastSentValue = prefs.getString(PREF_SCREENS_LAST_SENT, null);

        if (Objects.equals(currentValue, lastSentValue)) {
            logger.debug("Screen list unchanged ({}), skipping SET_SCREEN_LIST", currentValue);
            return;
        }

        final List<ScreenSettings> screenEntries = new ArrayList<>();
        for (final String screenKey : enabledScreens) {
            final int screenId = screenKeyToId(screenKey);
            if (screenId < 0) {
                logger.warn("Unknown screen key '{}', skipping", screenKey);
                continue;
            }
            final byte idOnDevice = WithingsScreenId.getScanwatchIdOnDevice(screenId);
            if (idOnDevice < 0) {
                logger.warn("No idOnDevice for screen key '{}' (id=0x{:04x}), skipping", screenKey, screenId);
                continue;
            }
            screenEntries.add(buildScanwatchScreen(screenId, idOnDevice, userId));
        }

        if (screenEntries.isEmpty()) {
            logger.warn("No valid ScanWatch screens to send, skipping SET_SCREEN_LIST");
            return;
        }

        // Official app sends CMD_SET_SCREEN_LIST in two logical messages for long lists:
        // first up to 8 entries (without EOT), then remaining entries with EOT.
        // Matching this framing avoids very large single-message payloads that can reboot the watch.
        final int maxEntriesPerMessage = 8;
        for (int i = 0; i < screenEntries.size(); i += maxEntriesPerMessage) {
            final int chunkEnd = Math.min(i + maxEntriesPerMessage, screenEntries.size());
            final boolean isFinalChunk = chunkEnd >= screenEntries.size();
            final Message message = isFinalChunk
                    ? new WithingsMessage(WithingsMessageType.SET_SCREEN_LIST)
                    : new WithingsMessage(WithingsMessageType.SET_SCREEN_LIST, ExpectedResponse.NONE);
            for (int j = i; j < chunkEnd; j++) {
                message.addDataStructure(screenEntries.get(j));
            }
            if (isFinalChunk) {
                message.addDataStructure(new EndOfTransmission());
            }
            addSimpleConversationToQueue(message);
        }

        // Record what we sent so we can skip on future syncs if nothing changed
        prefs.edit().putString(PREF_SCREENS_LAST_SENT, currentValue).apply();
    }

    static List<String> effectiveScreenKeys(final List<String> screenKeys,
                                            final boolean ecgSupported,
                                            final boolean spo2Supported) {
        final List<String> effectiveKeys = new ArrayList<>(screenKeys);
        if (!ecgSupported) {
            effectiveKeys.removeIf("ecg"::equals);
        }
        if (!spo2Supported) {
            effectiveKeys.removeIf("spo2"::equals);
        }
        return effectiveKeys;
    }

    static boolean isShortcutActionSupported(final byte action,
                                             final boolean ecgSupported,
                                             final boolean spo2Supported) {
        return (action != ShortcutAction.ACTION_ECG_MEAS || ecgSupported)
                && (action != ShortcutAction.ACTION_SPO2_MEAS || spo2Supported);
    }

    /**
     * Builds a {@link ScreenSettings} entry for the ScanWatch with the stored Withings userId.
     *
     * <p>The ScanWatch reboots if it receives a {@code CMD_SCREEN_LIST_SET} (0x050C) packet
     * containing {@link ScreenSettings} entries with {@code userId = 0}. The official app always
     * sends the real Withings account ID; we read it from prefs (stored by {@link GetUserHandler}
     * during the handshake).
     */
    private ScreenSettings buildScanwatchScreen(final int screenId, final byte idOnDevice, final int userId) {
        final ScreenSettings settings = new ScreenSettings();
        settings.setId(screenId);
        settings.setIdOnDevice(idOnDevice);
        settings.setUserId(userId);
        settings.setScreenType(WithingsScreenId.getScanwatchScreenType(screenId));
        return settings;
    }

    /**
     * Maps a screen preference value key (as stored in the DragSortListPreference) to the
     * corresponding {@link WithingsScreenId} constant.
     *
     * @param key the string value from {@code pref_withings_scanwatch_screens_values}
     * @return the screen ID constant, or {@code -1} if the key is not recognised
     */
    private static int screenKeyToId(final String key) {
        switch (key) {
            case "date":       return WithingsScreenId.DATE;
            case "sleep":      return WithingsScreenId.SLEEP;
            case "ecg":        return WithingsScreenId.ECG;
            case "elevation":  return WithingsScreenId.ELEVATION;
            case "heart_rate": return WithingsScreenId.HEART_RATE;
            case "spo2":       return WithingsScreenId.SPO2;
            case "calories":   return WithingsScreenId.CALORIES;
            case "settings":   return WithingsScreenId.SETTINGS;
            case "workouts":   return WithingsScreenId.WORKOUTS;
            case "distance":   return WithingsScreenId.DISTANCE;
            case "steps":      return WithingsScreenId.STEPS;
            case "breathe":    return WithingsScreenId.BREATHE;
            case "clock":      return WithingsScreenId.CLOCK;
            default:           return -1;
        }
    }
}
