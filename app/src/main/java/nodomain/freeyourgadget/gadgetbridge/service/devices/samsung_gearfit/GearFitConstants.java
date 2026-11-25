/*  Copyright (C) 2026 MedusasSphinx

    Protocol constants derived from OpenFit (https://github.com/jareddlc/OpenFit),
    Copyright (C) 2015 Jared De La Cruz, MIT licensed.

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Wire protocol constants for the Samsung Gear Fit (SM-R350, "Wingtip").
 * <p>
 * Every message consists of a single "port" byte, a 32 bit little endian payload length and the
 * payload itself.
 */
public final class GearFitConstants {
    private GearFitConstants() {
    }

    public static final UUID UUID_SERVICE = UUID.fromString("9c86c750-870d-11e3-baa7-0800200c9a66");

    public static final int BUFFER_SIZE = 8192;
    public static final int MAX_PAYLOAD_SIZE = 4096;
    public static final int FRAME_HEADER_SIZE = 5;
    public static final int MAX_STRING8_BYTES = 254;
    public static final int UTF16_BOM_SIZE = 2;

    public static final int STATUS_INITIALIZE = 1;
    public static final byte STATUS_FIRMWARE_VERSION = 0;
    public static final byte STATUS_BATTERY_CHARGING = 1;
    public static final byte STATUS_BATTERY_FULL = 2;

    public static final int FITNESS_ENVELOPE_SIZE = 9;
    public static final int FITNESS_ENVELOPE_TYPE = 4;
    public static final byte FITNESS_ENVELOPE_MARKER = (byte) 0xFF;

    public static final int USER_PROFILE_REMAINING_SIZE = 40;
    public static final int PEDOMETER_PROFILE_SIZE = 16;
    public static final int COACHING_VARS_SIZE = 54;
    public static final int COACHING_EXERCISE_RESULT_SIZE = 28;

    public static final int DEFAULT_MEDIA_VOLUME = 7;
    public static final int GPS_LOCATION_INTERVAL_MILLIS = (int) TimeUnit.SECONDS.toMillis(1);
    public static final int MAX_PENDING_GPS_TRACKS = 20;
    public static final int MAX_ACTIVE_NOTIFICATIONS = 100;
    public static final int MAX_CANNED_REPLY_MESSAGES = 10;
    public static final float MAX_GPS_POINT_DISTANCE_METERS = 5000f;
    public static final long GPS_TRACK_MATCH_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(5);
    public static final long MAX_SLEEP_WINDOW_MILLIS = TimeUnit.HOURS.toMillis(24);
    public static final long SLEEP_SAMPLE_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    public static final int SLEEP_DEEP_STATUS_MIN = 80;
    public static final int SLEEP_LIGHT_STATUS_MIN = 30;
    public static final int KELVIN_CELSIUS_OFFSET = 273;
    public static final int NO_ACTIVE_ALARM_NOTIFICATION_ID = -1;

    public static final int GPS_EXERCISE_START_MIN_SIZE = 16;
    public static final int WEATHER_UNIT_FAHRENHEIT = 0;
    public static final int WEATHER_UNIT_CELSIUS = 1;
    public static final byte FIND_EVENT_TYPE = 1;

    /** Ports (first byte of every message). */
    public static final byte PORT_FEATURE = 0;
    public static final byte PORT_HOME = 1;
    public static final byte PORT_HEALTH = 2;
    public static final byte PORT_NOTIFICATION = 3;
    public static final byte PORT_STATUS_MANAGER = 4;
    public static final byte STATUS_BATTERY_REQUEST = 11;
    public static final byte STATUS_DEVICE_INFO_REQUEST = 23;
    public static final byte PORT_FIND = 5;
    public static final byte PORT_MEDIA = 6;
    public static final byte PORT_GESTURE = 7;
    public static final byte PORT_SENSOR = 8;
    public static final byte PORT_CALL = 9;
    public static final byte PORT_ALARM = 10;
    public static final byte PORT_SPLANNER = 11;
    public static final byte PORT_FOTA = 77;
    public static final byte PORT_FOTA_COMMAND = 78;
    public static final byte PORT_CM = 99;
    public static final byte PORT_CM_FEATURE = 100;

    /** Gesture service messages. */
    public static final byte GESTURE_TYPE_REQUEST = 0;
    public static final byte GESTURE_TYPE_MEDIA_APP = 4;
    public static final byte GESTURE_MEDIA_PLAYER_RUNNING = 1;

    /** Home port payload types. */
    public static final byte HOME_TYPE_SET_TIME = 1;
    public static final byte HOME_TYPE_WEATHER_REQUEST = 12;
    public static final byte HOME_TYPE_WEATHER_CLOCK = 9;

    /** Feature port payload type sent by the watch once it is ready to talk. */
    public static final int FEATURE_READY = 3;

    /** Notification port payload types. */
    public static final byte NOTIFICATION_INCOMING_CALL = 0;
    public static final byte NOTIFICATION_MISSED_CALL = 1;
    public static final byte NOTIFICATION_CALL_ENDED = 2;
    public static final byte NOTIFICATION_EMAIL = 3;
    public static final byte NOTIFICATION_MESSAGE = 4;
    public static final byte NOTIFICATION_ALARM = 5;
    public static final byte NOTIFICATION_WEATHER = 7;
    public static final byte NOTIFICATION_GENERAL = 12;
    public static final byte NOTIFICATION_REJECT_ACTION = 13;
    public static final byte NOTIFICATION_ALARM_ACTION = 14;
    public static final byte NOTIFICATION_READ_ACTION = 19;
    public static final byte NOTIFICATION_DELETE_ACTION = 22;
    public static final byte NOTIFICATION_OPEN_ACTION = 23;
    public static final byte NOTIFICATION_QUICK_REPLY_MESSAGE = 24;
    public static final byte NOTIFICATION_QUICK_REPLY_ACTION = 25;

    /** Calendar event list update on the home port. */
    public static final byte HOME_TYPE_CALENDAR_EVENTS = 5;

    /** Notification action reply packet. */
    public static final byte NOTIFICATION_REJECT_MESSAGE = 26;

    /** Call app payload types. */
    public static final byte CALL_TYPE_REJECT = 3;
    public static final byte CALL_REJECT_NOW = 1;
    public static final byte CALL_REJECT_WITH_MESSAGE = 2;
    public static final byte CALL_REJECT_MESSAGES = 4;

    /** Alarm app payload types. */
    public static final byte ALARM_TYPE_ALARM = 1;
    public static final byte ALARM_TYPE_ACTION = 3;
    public static final byte ALARM_ACTION_DISMISSED = 0;
    public static final byte ALARM_ACTION_SNOOZED = 1;

    /** Media controller types. */
    public static final byte MEDIA_CONTROL = 0;
    public static final byte MEDIA_VOLUME = 1;
    public static final int MAX_MEDIA_VOLUME = 15;
    public static final byte MEDIA_INFO = 2;
    public static final byte MEDIA_REQUEST_START = 3;
    public static final byte MEDIA_REQUEST_STOP = 4;

    public static final byte MEDIA_OPEN = 0;
    public static final byte MEDIA_PLAY = 1;
    public static final byte MEDIA_PAUSE = 2;
    public static final byte MEDIA_STOP = 3;
    public static final byte MEDIA_FORWARD = 4;
    public static final byte MEDIA_REWIND = 5;
    public static final byte MEDIA_FORWARD_RELEASE = 6;
    public static final byte MEDIA_REWIND_RELEASE = 7;

    /** Find my device payload types. */
    public static final byte FIND_START = 0;
    public static final byte FIND_STOP = 1;

    /** Health port sync / GPS control types. */
    public static final int HEALTH_SYNC_REQUEST = 5;
    public static final int HEALTH_SYNC_DONE = 1;
    public static final int HEALTH_GPS_OFF = 17;
    public static final int HEALTH_GPS_ON = 16;
    public static final int HEALTH_GPS_READY = 12;
    /** The watch asks the phone whether GPS tracking is available for an exercise. */
    public static final int HEALTH_GPS_READY_WATCH = 18;
    public static final int HEALTH_GPS_START_WALKING = 8;
    public static final int HEALTH_GPS_START_RUNNING = 9;
    public static final int HEALTH_GPS_START_CYCLING = 7;
    public static final int HEALTH_GPS_START_HIKING = 6;
    public static final int HEALTH_GPS_END = 10;
    public static final int HEALTH_GPS_REQUEST = 11;
    public static final int HEALTH_GPS_DATA = 13;
    public static final int HEALTH_GPS_SUBSCRIBE = 14;
    public static final int HEALTH_GPS_UNSUBSCRIBE = 15;
    public static final int HEALTH_GPS_EXERCISE_START = 19;
    public static final int HEALTH_GPS_RESULT = 22;

    /** Quiet time after the last fitness frame before the sync is considered complete. */
    public static final long FITNESS_SYNC_SETTLE_MILLIS = 1500;
    public static final long BATTERY_REFRESH_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(1);
    public static final int HEALTH_MENU_RESPONSE = 28;
    public static final int HEALTH_MENU = 27;
    public static final int HEALTH_CANCEL = 10;
    public static final int HEALTH_UNKNOWN = 5;

    /** Fitness record types inside a health payload. */
    public static final byte FITNESS_USER_PROFILE = 0;
    public static final byte FITNESS_PEDOMETER_PROFILE = 1;
    public static final byte FITNESS_COACHING_PROFILE = 2;
    public static final byte FITNESS_PEDO_RESULT_RECORD = 3;
    public static final byte FITNESS_PEDO_INFO = 4;
    public static final byte FITNESS_SLEEP_RESULT_RECORD = 5;
    public static final byte FITNESS_SLEEP_INFO = 6;
    public static final byte FITNESS_HEART_RATE_RESULT_RECORD = 7;
    public static final byte FITNESS_COACHING_VARS = 8;
    public static final byte FITNESS_COACHING_EXERCISE_RESULT = 9;
    public static final byte FITNESS_COACHING_RUNNING_EXERCISE = 10;
    public static final byte FITNESS_COACHING_ENERGY_EXERCISE = 11;
    public static final byte FITNESS_COACHING_RESULT_RECORD = 12;
    public static final byte FITNESS_GPS_INFO = 13;

    /** Exercise types reported in coaching result records. */
    public static final int EXERCISE_WALKING = 18002;
    public static final int EXERCISE_RUNNING = 18003;
    public static final int EXERCISE_CYCLING = 18004;
    public static final int EXERCISE_HIKING = 18005;

    /** Weather condition types understood by the watch. */
    public static final int WEATHER_SUNNY = 19;
    public static final int WEATHER_MOSTLY_CLEAR = 8;
    public static final int WEATHER_MOSTLY_CLOUDY = 9;
    public static final int WEATHER_HEAVY_RAIN = 5;
    public static final int WEATHER_PARTLY_SUNNY_SHOWERS = 13;
    public static final int WEATHER_THUNDERSTORMS = 20;
    public static final int WEATHER_SNOW = 18;
    public static final int WEATHER_FOG = 3;

    /** Weather clock icon types. */
    public static final int WEATHER_CLOCK_SUNNY = 1;
    public static final int WEATHER_CLOCK_CLEAR = 18;
    public static final int WEATHER_CLOCK_MOSTLY_CLOUDY = 3;
    public static final int WEATHER_CLOCK_SHOWERS = 6;
    public static final int WEATHER_CLOCK_PARTLY_SUNNY_SHOWERS = 7;
    public static final int WEATHER_CLOCK_THUNDERSTORMS = 8;
    public static final int WEATHER_CLOCK_SNOW = 12;
    public static final int WEATHER_CLOCK_FOG = 5;

    /** Date format bytes used in the time payload (mirrors the watch's own settings). */
    public static final byte TEXT_DATE_FORMAT_TYPE = 1;
    public static final byte NUMBER_DATE_FORMAT_TYPE = 2;

    public static final int MAX_TITLE_CHARS = 50;
    public static final int MAX_MESSAGE_CHARS = 250;
}
