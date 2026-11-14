/*  Copyright (C) 2023-2024 Frank Ertl

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message;

/**
 * Contains all identified commandtypes in the used TLV format of the messages exchanged
 * between device and app.
 */
public final class WithingsMessageType {

    public static final short TRANSFER_COMPLETE = 256;
    public static final short PROBE = 257;
    public static final short CHALLENGE = 296;
    public static final short SET_TIME = 1281;
    public static final short GET_BATTERY_STATUS = 1284;
    public static final short SET_SCREEN_LIST = 1292;
    public static final short INITIAL_CONNECT = 273;
    public static final short START_HANDS_CALIBRATION = 286;
    public static final short STOP_HANDS_CALIBRATION = 287;
    public static final short MOVE_HAND = 284;
    public static final short SET_ACTIVITY_TARGET = 1290;
    public static final short SET_USER = 1282;
    public static final short GET_USER = 1283;
    public static final short SET_USER_UNIT = 274;
    public static final short SET_LOCALE = 282;
    public static final short SETUP_FINISHED = 275;
    public static final short GET_HR = 2343;
    public static final short GET_WORKOUT_SCREEN_LIST = 315;
    public static final short SET_WORKOUT_SCREEN = 316;
    public static final short START_LIVE_WORKOUT = 317;
    public static final short STOP_LIVE_WORKOUT = 318;
    public static final short SYNC = 321;
    public static final short SYNC_RESPONSE = 16705;
    public static final short SYNC_OK = 277;
    public static final short GET_ALARM_SETTINGS = 298;
    public static final short SET_ALARM = 325;
    public static final short GET_ALARM = 293;
    public static final short GET_MULTI_ALARM = 326;
    public static final short GET_ALARM_ENABLED = 2330;
    public static final short SET_ALARM_ENABLED = 2331;
    public static final short GET_ANCS_STATUS = 2353;
    public static final short SET_ANCS_STATUS = 2345;
    public static final short GET_LIVE_HR = 2376;
    public static final short MEASURE_LIVE_DATA = 2421;
    public static final short GET_SCREEN_SETTINGS = 1293;
    // The next two do nearly the same, when I look at the responses, though only the first seems to deliver sleep samples
    public static final short GET_ACTIVITY_SAMPLES = 2424;
    public static final short GET_MOVEMENT_SAMPLES = 1286;
    public static final short GET_STORED_MEASURE_SIGNAL = 327;
    public static final short DELETE_STORED_MEASURE_SIGNAL = 328;

    public static final short GET_SPORT_MODE = 2371;
    public static final short GET_WORKOUT_GPS_STATUS = 323;
    public static final short GET_HEARTRATE_SAMPLES = 2344;
    public static final short LIVE_WORKOUT_DATA = 320;
    public static final short GET_NOTIFICATION = 2404;
    public static final short GET_UNICODE_GLYPH = 2403;

    /** Get the currently configured long-press crown shortcut action (cmd 0x0992). */
    public static final short GET_SHORTCUT = (short) 0x0992;  // 2450
    /** Set the long-press crown shortcut action (cmd 0x0989). */
    public static final short SET_SHORTCUT = (short) 0x0989;  // 2441

    /**
     * Set feature-tags-deprecated list (cmd 0x0987).
     * Enables/disables ECG, AFib detection and related health features on the watch.
     * The message body contains a {@code FeatureTagsUserId} header followed by one or more
     * {@code FeatureTagDeprecated} TLVs and a final {@code EndOfTransmission}.
     */
    public static final short SET_FEATURE_TAGS_DEPRECATED = (short) 0x0987;  // 2439

    /**
     * Set local notification slot configuration (cmd 0x0990).
     * Configures which on-watch health alerts (AFib, high/low HR, etc.) are enabled.
     * The message body contains five {@code LocalNotification} TLVs (one per slot) and a final
     * {@code EndOfTransmission}.
     */
    public static final short SET_LOCAL_NOTIFICATIONS = (short) 0x0990;  // 2448

    /**
     * Set heart-rate alert thresholds (cmd 0x098e).
     * Configures the BPM thresholds and enabled/disabled state for high and low resting HR alerts.
     * The message body contains a {@code FeatureTagsUserId} header followed by one
     * {@code HrAlertThreshold} TLV for LOW and one for HIGH, then {@code EndOfTransmission}.
     */
    public static final short SET_HR_ALERT_THRESHOLDS = (short) 0x098e;  // 2446

    /**
     * Commit / apply feature tags (cmd 0x0993).
     * Sent as an empty GET immediately after {@link #SET_FEATURE_TAGS_DEPRECATED}.
     * The watch responds with a simple ACK ({@code 01000000}).
     * Always sent as a pair with {@link #COMMIT_NOTIFICATION_CONFIG}.
     */
    public static final short COMMIT_FEATURE_TAGS = (short) 0x0993;  // 2451

    /**
     * Commit / apply notification configuration (cmd 0x0994).
     * Sent as an empty GET immediately after {@link #COMMIT_FEATURE_TAGS}.
     * The watch responds with a simple ACK ({@code 01000000}).
     */
    public static final short COMMIT_NOTIFICATION_CONFIG = (short) 0x0994;  // 2452

    /** Start/query health measurement flow (cmd 0x0973). */
    public static final short MEASURE_START = (short) 0x0973;  // 2419
    /** Acknowledge/stop health measurement flow (cmd 0x0974). */
    public static final short MEASURE_STOP = (short) 0x0974;  // 2420

    /** Get quicklook / glance (raise-to-wake) status (cmd 0x097B). */
    public static final short GLANCE_GET = (short) 0x097B;  // 2427
    /** Set quicklook / glance (raise-to-wake) status (cmd 0x0971). */
    public static final short GLANCE_SET = (short) 0x0971;  // 2417

    /** Set screen luminosity mode & level (cmd 0x0941). */
    public static final short SET_LUMINOSITY_LEVEL = (short) 0x0941;  // 2369
    /** Get screen luminosity mode & level (cmd 0x0942). */
    public static final short GET_LUMINOSITY_LEVEL = (short) 0x0942;  // 2370

    /** Set tracker move-hands (move hands to 10:10 when screen turns on) (cmd 0x09AB). */
    public static final short SET_TRACKER_MOVE_HANDS = (short) 0x09AB;  // 2475
    /** Get tracker move-hands status (cmd 0x09AC). */
    public static final short GET_TRACKER_MOVE_HANDS = (short) 0x09AC;  // 2476

    /** Set tracker wear position (left/right wrist) (cmd 0x014F). */
    public static final short SET_TRACKER_WEAR_POS = 335;   // 0x014F
    /** Get tracker wear position (cmd 0x0150). */
    public static final short GET_TRACKER_WEAR_POS = 336;   // 0x0150

    private WithingsMessageType() {}
}
