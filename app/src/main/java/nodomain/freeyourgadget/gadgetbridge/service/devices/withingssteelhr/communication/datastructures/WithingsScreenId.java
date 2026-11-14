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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures;

/**
 * Known screen IDs used in the Withings protocol SET_SCREEN_LIST command (message type 1292).
 *
 * <p>Each constant represents the {@code id} field sent inside a {@link ScreenSettings} structure
 * (4-byte big-endian int). The {@code idOnDevice} field is a separate fixed byte per screen whose
 * value was confirmed from BLE captures; it does <em>not</em> determine display order. Display
 * order on the watch is determined solely by the sequence of entries in the SET_SCREEN_LIST packet.
 *
 * <p><b>ScanWatch screen IDs and idOnDevice values</b> were confirmed from five BLE captures of
 * the Withings HealthMate app sending CMD_SCREEN_LIST_SET (0x050C):
 * <ul>
 *   <li>Capture 1 - default order: Date, Sleep, ECG, Elevation, HeartRate, SpO2, Calories,
 *       Settings, Workouts, Distance, Steps, Breathe, Clock</li>
 *   <li>Capture 2 - reordered: Date, Elevation, HeartRate, SpO2, Calories, Settings, Workouts,
 *       Distance, Steps, Breathe, Clock, Sleep, ECG</li>
 *   <li>Capture 3 - Strava added: 14 screens including Strava "Weekly distance"
 *       (screen_id=0x99, idOnDevice=0x02, screenType=0x02)</li>
 *   <li>Capture 4 - Strava moved to position 2</li>
 *   <li>Capture 5 - 5 screens removed (9 screens total)</li>
 * </ul>
 * Every screen_id and idOnDevice value is identical across all captures; only the packet entry
 * sequence differs, confirming that sequence - not idOnDevice - drives display order.
 *
 * <p><b>Steel HR screen IDs</b> come from the original Gadgetbridge implementation and have not
 * been reverified against a fresh packet capture.
 */
public final class WithingsScreenId {

    private WithingsScreenId() {}

    // -----------------------------------------------------------------------
    // Screens - Withings Steel HR (source: original GB implementation)
    // -----------------------------------------------------------------------

    /** Notification screen. */
    public static final int NOTIFICATIONS = 0xFF;

    /** Heart rate (BPM) screen. */
    public static final int HEART_RATE_STEEL = 0x3D;

    /** Activity / step counter screen. */
    public static final int ACTIVITY = 0x33;

    /** Calories screen. */
    public static final int CALORIES_STEEL = 0x2D;

    /** Alarm/timer screen. */
    public static final int ALARM = 0x2A;

    /** Config / battery info screen. */
    public static final int CONFIG = 0x26;

    /** Chronometer / countdown screen. */
    public static final int CHRONOMETER = 0x39;

    // -----------------------------------------------------------------------
    // Screens - Withings ScanWatch
    // screen_id and idOnDevice values confirmed from BLE packet captures.
    // idOnDevice is a fixed per-screen value; display order is determined by
    // the sequence of entries in the SET_SCREEN_LIST packet, not by idOnDevice.
    // -----------------------------------------------------------------------

    /** Date / activity data screen. screen_id=0x84, idOnDevice=0x06. */
    public static final int DATE = 0x84;

    /** Sleep screen. screen_id=0x160, idOnDevice=0x16. */
    public static final int SLEEP = 0x160;

    /** ECG screen. screen_id=0x8a, idOnDevice=0x09. */
    public static final int ECG = 0x8a;

    /** Elevation screen. screen_id=0x8c, idOnDevice=0x0c. */
    public static final int ELEVATION = 0x8c;

    /** Heart rate screen. screen_id=0x82, idOnDevice=0x04. */
    public static final int HEART_RATE = 0x82;

    /** SpO2 / blood oxygen screen. screen_id=0x8b, idOnDevice=0x0a. */
    public static final int SPO2 = 0x8b;

    /** Calories screen. screen_id=0x81, idOnDevice=0x03. */
    public static final int CALORIES = 0x81;

    /** Settings screen. screen_id=0x97, idOnDevice=0x11. */
    public static final int SETTINGS = 0x97;

    /** Workouts screen. screen_id=0x89, idOnDevice=0x0b. */
    public static final int WORKOUTS = 0x89;

    /** Distance screen. screen_id=0x80, idOnDevice=0x02. */
    public static final int DISTANCE = 0x80;

    /** Steps screen. screen_id=0x7f, idOnDevice=0x01. */
    public static final int STEPS = 0x7f;

    /** Breathe / breathing exercises screen. screen_id=0xa1, idOnDevice=0x12. */
    public static final int BREATHE = 0xa1;

    /** Clock (alarms, stopwatch, timer) screen. screen_id=0x96, idOnDevice=0x10. */
    public static final int CLOCK = 0x96;

    // -----------------------------------------------------------------------
    // Screens - Withings ScanWatch - Partner / third-party app screens
    // These use screenType=0x02 in the ScreenSettings entry instead of 0x01.
    // -----------------------------------------------------------------------

    /**
     * Strava "Weekly distance" screen. screen_id=0x99, idOnDevice=0x02, screenType=0x02.
     *
     * <p>This screen appears when Strava is linked in the Withings Health Mate app.
     * Unlike built-in screens which use {@code screenType=0x01}, the Strava screen uses
     * {@code screenType=0x02}, likely indicating a partner/third-party screen.
     *
     * <p>Note: {@code idOnDevice=0x02} is the same value as the Distance screen's idOnDevice.
     * The two are distinguished by their different screen_id values (0x99 vs 0x80).
     */
    public static final int STRAVA_WEEKLY_DISTANCE = 0x99;

    /**
     * Returns the fixed {@code idOnDevice} byte for a given ScanWatch screen ID.
     *
     * <p>This value is a fixed property of each screen confirmed from BLE captures. It is
     * <em>not</em> a position index - display order is determined by packet entry sequence.
     *
     * @param screenId one of the ScanWatch screen ID constants in this class
     * @return the fixed idOnDevice byte, or -1 if the screen ID is not a known ScanWatch screen
     */
    public static byte getScanwatchIdOnDevice(int screenId) {
        switch (screenId) {
            case DATE:      return 0x06;
            case SLEEP:     return 0x16;
            case ECG:       return 0x09;
            case ELEVATION: return 0x0c;
            case HEART_RATE: return 0x04;
            case SPO2:      return 0x0a;
            case CALORIES:  return 0x03;
            case SETTINGS:  return 0x11;
            case WORKOUTS:  return 0x0b;
            case DISTANCE:  return 0x02;
            case STEPS:     return 0x01;
            case BREATHE:   return 0x12;
            case CLOCK:     return 0x10;
            case STRAVA_WEEKLY_DISTANCE: return 0x02;
            default:        return -1;
        }
    }

    /**
     * Returns the {@code screenType} byte for a given ScanWatch screen ID.
     *
     * <p>Built-in screens use 0x01; partner/third-party screens (e.g. Strava) use 0x02.
     * Confirmed from BLE captures of the Withings HealthMate app.
     *
     * @param screenId one of the ScanWatch screen ID constants in this class
     * @return 0x01 for built-in screens, 0x02 for partner screens, or 0x01 as default
     */
    public static byte getScanwatchScreenType(int screenId) {
        switch (screenId) {
            case STRAVA_WEEKLY_DISTANCE: return 0x02;
            default:                     return 0x01;
        }
    }
}
