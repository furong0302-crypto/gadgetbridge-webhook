/*  Copyright (C) 2026 MedusasSphinx

    Protocol derived from OpenFit (https://github.com/jareddlc/OpenFit),
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

import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.ALARM_TYPE_ALARM;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.HOME_TYPE_SET_TIME;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.HOME_TYPE_WEATHER_CLOCK;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.MAX_MESSAGE_CHARS;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.MAX_TITLE_CHARS;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.MEDIA_INFO;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.MEDIA_VOLUME;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.NOTIFICATION_CALL_ENDED;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.NOTIFICATION_INCOMING_CALL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.NUMBER_DATE_FORMAT_TYPE;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_ALARM;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_CALL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_CM_FEATURE;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_FIND;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_FOTA;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_FOTA_COMMAND;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_HEALTH;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_HOME;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_MEDIA;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.PORT_NOTIFICATION;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitConstants.TEXT_DATE_FORMAT_TYPE;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;

/**
 * Builders for the messages the phone sends to a Gear Fit.
 * <p>
 * Strings on the wire are UTF-16LE prefixed with a byte order mark, and every length prefix counts
 * the BOM as well.
 */
public final class GearFitProtocol {
    private GearFitProtocol() {
    }

    /** Messages framed as soon as they are built, so callers can just write the returned array. */
    public static byte[] handshakeFota() {
        return frame(PORT_FOTA, "ODIN".getBytes(StandardCharsets.US_ASCII));
    }

    public static byte[] handshakeFollowUp() {
        return frame(PORT_CM_FEATURE, new byte[]{4, 2, 1, 1, 4, 2, 5, 1});
    }

    public static byte[] handshakeFotaCommand() {
        return frame(PORT_FOTA_COMMAND, new byte[]{1, 1});
    }

    public static byte[] setTime(final boolean is24Hour) {
        final long nowMillis = System.currentTimeMillis();
        final TimeZone timeZone = Calendar.getInstance().getTimeZone();
        final int rawOffsetMinutes = (int) TimeUnit.MILLISECONDS.toMinutes(timeZone.getRawOffset());
        final boolean inDaylightTime = timeZone.inDaylightTime(new Date(nowMillis));
        final boolean useDaylightTime = timeZone.useDaylightTime();

        int previousTransition = 0;
        int nextTransition = 0;
        try {
            final ZoneRules rules = timeZone.toZoneId().getRules();
            final Instant now = Instant.ofEpochMilli(nowMillis);
            final ZoneOffsetTransition previous = rules.previousTransition(now);
            if (previous != null) {
                previousTransition = (int) previous.toEpochSecond();
            }
            final ZoneOffsetTransition next = rules.nextTransition(now);
            if (next != null) {
                nextTransition = (int) next.toEpochSecond();
            }
        } catch (final Exception e) {
            // A few custom time zones have no rules; the watch copes with zeroed transitions.
        }

        final Writer w = new Writer();
        w.u8(HOME_TYPE_SET_TIME);
        w.i32((int) TimeUnit.MILLISECONDS.toSeconds(nowMillis));
        w.i32(rawOffsetMinutes / 60);
        w.i32(rawOffsetMinutes % 60);
        w.u8(TEXT_DATE_FORMAT_TYPE);
        w.u8(is24Hour ? 1 : 0);
        w.u8(inDaylightTime ? 1 : 0);
        w.u8(NUMBER_DATE_FORMAT_TYPE);
        w.u8(useDaylightTime ? 1 : 0);
        w.i32(previousTransition);
        w.i32(nextTransition);
        w.i32((int) TimeUnit.MILLISECONDS.toSeconds(timeZone.getDSTSavings()));
        return frame(PORT_HOME, w.toByteArray());
    }

    public static byte[] findDevice(final boolean start) {
        return frame(PORT_FIND, new byte[]{1, start ? GearFitConstants.FIND_START : GearFitConstants.FIND_STOP});
    }

    public static byte[] mediaVolume(final int volume) {
        final Writer w = new Writer();
        w.u8(MEDIA_VOLUME);
        w.u8(Math.max(0, Math.min(GearFitConstants.MAX_MEDIA_VOLUME, volume)));
        return frame(PORT_MEDIA, w.toByteArray());
    }

    public static byte[] mediaTrack(final String track) {
        final Writer w = new Writer();
        w.u8(MEDIA_INFO);
        w.string8(track);
        return frame(PORT_MEDIA, w.toByteArray());
    }

    public static byte[] fitnessMenuResponse() {
        final Writer w = new Writer();
        w.i32(GearFitConstants.HEALTH_MENU_RESPONSE);
        return frame(PORT_HEALTH, w.toByteArray());
    }

    /**
     * Ask the watch to hand over its fitness records. The watch sends its own sync request first
     * and expects this as the reply.
     */
    public static byte[] fitnessRequest() {
        final Writer w = new Writer();
        w.u8(0);
        w.i32(1);
        return frame(PORT_HEALTH, w.toByteArray());
    }

    public static byte[] syncDone() {
        final Writer w = new Writer();
        w.i32(GearFitConstants.HEALTH_SYNC_DONE);
        return frame(PORT_HEALTH, w.toByteArray());
    }

    public static byte[] gpsReady() {
        final Writer w = new Writer();
        w.i32(GearFitConstants.HEALTH_GPS_READY);
        return frame(PORT_HEALTH, w.toByteArray());
    }

    public static byte[] gpsState(final boolean enabled) {
        final Writer w = new Writer();
        w.i32(enabled ? GearFitConstants.HEALTH_GPS_ON : GearFitConstants.HEALTH_GPS_OFF);
        return frame(PORT_HEALTH, w.toByteArray());
    }

    public static byte[] gpsData(final float totalDistance, final float currentSpeed,
                                 final float calories, final double altitude) {
        final Writer w = new Writer();
        w.i32(GearFitConstants.HEALTH_GPS_DATA);
        w.f32(totalDistance);
        w.f32(currentSpeed);
        w.f32(calories);
        w.f64(altitude);
        return frame(PORT_HEALTH, w.toByteArray());
    }

    public static byte[] gpsResult(final float totalDistance, final float maxAltitude,
                                   final float minAltitude, final float maxSpeed,
                                   final float averageSpeed, final float calories,
                                   final float inclineDistance, final float declineDistance) {
        final Writer w = new Writer();
        w.i32(GearFitConstants.HEALTH_GPS_RESULT);
        w.f32(totalDistance);
        w.f32(maxAltitude);
        w.f32(minAltitude);
        w.f32(maxSpeed);
        w.f32(averageSpeed);
        w.f32(calories);
        w.f32(inclineDistance);
        w.f32(declineDistance);
        return frame(PORT_HEALTH, w.toByteArray());
    }

    public static byte[] notification(final byte type, final long id, final String sender,
                                      final String number, final String title, final String message,
                                      final long timestampMillis) {
        final Writer w = new Writer();
        w.u8(type);
        w.i64(id);
        w.string8(truncate(sender, MAX_TITLE_CHARS));
        w.string8(truncate(number, MAX_TITLE_CHARS));
        w.string8(truncate(title, MAX_TITLE_CHARS));
        w.string16(truncate(message, MAX_MESSAGE_CHARS));
        w.u8(0);
        w.i32((int) TimeUnit.MILLISECONDS.toSeconds(timestampMillis));
        return frame(PORT_NOTIFICATION, w.toByteArray());
    }

    public static byte[] email(final long id, final String sender, final String number,
                               final String subject, final String body, final long timestampMillis) {
        final Writer w = new Writer();
        w.u8(GearFitConstants.NOTIFICATION_EMAIL);
        w.i64(id);
        w.string8(truncate(sender, MAX_TITLE_CHARS));
        w.string8(truncate(number, MAX_TITLE_CHARS));
        w.string8(truncate(subject, MAX_TITLE_CHARS));
        w.string16(truncate(body, MAX_MESSAGE_CHARS));
        w.u8(0); // number of attachments, shifted
        w.u8(0); // quick reply supported
        w.u8(0); // image attached
        w.i32((int) TimeUnit.MILLISECONDS.toSeconds(timestampMillis));
        return frame(PORT_NOTIFICATION, w.toByteArray());
    }

    public static byte[] incomingCall(final long id, final String name, final String number,
                                      final long timestampMillis) {
        final Writer w = new Writer();
        w.u8(NOTIFICATION_INCOMING_CALL);
        w.i64(id);
        w.u8(0);
        w.string8(truncate(name, MAX_TITLE_CHARS));
        w.string8(truncate(number, MAX_TITLE_CHARS));
        w.i32((int) TimeUnit.MILLISECONDS.toSeconds(timestampMillis));
        return frame(PORT_CALL, w.toByteArray());
    }

    public static byte[] callEnded() {
        return frame(PORT_CALL, new byte[]{NOTIFICATION_CALL_ENDED});
    }

    public static byte[] alarmClear() {
        return frame(PORT_ALARM, new byte[]{0});
    }

    /**
     * Alarm times are packed as decimal HHMM, matching what the watch itself reports back.
     */
    public static byte[] alarm(final int position, final String label, final int hour, final int minute,
                               final boolean snooze) {
        final Writer w = new Writer();
        w.u8(ALARM_TYPE_ALARM);
        w.i64(position);
        w.string8(truncate(label, MAX_TITLE_CHARS));
        w.i32(hour * 100 + minute);
        w.i32(snooze ? 1 : 0);
        return frame(PORT_ALARM, w.toByteArray());
    }

    /**
     * Weather reply for the clock face. {@code unit} is 1 for Celsius and 0 for Fahrenheit, the
     * temperature uses the watch's fixed point encoding (hundredths of a degree).
     */
    public static byte[] weatherClock(final String location, final int temperature, final int unit,
                                      final int icon, final long timestampMillis) {
        final Writer w = new Writer();
        w.u8(HOME_TYPE_WEATHER_CLOCK);
        w.string8(truncate(location, MAX_TITLE_CHARS));
        w.u8(icon);
        w.i32(temperature);
        w.u8(unit);
        w.u8(0);
        w.i32((int) TimeUnit.MILLISECONDS.toSeconds(timestampMillis));
        w.i32((int) TimeUnit.MILLISECONDS.toSeconds(timestampMillis));
        return frame(PORT_HOME, w.toByteArray());
    }

    /** Encodes a string the way the watch expects it: BOM plus UTF-16LE. */
    public static byte[] encodeString(final String value) {
        final byte[] utf16 = (value == null ? "" : value).getBytes(StandardCharsets.UTF_16LE);
        final byte[] result = new byte[GearFitConstants.UTF16_BOM_SIZE + utf16.length];
        result[0] = (byte) 0xFF;
        result[1] = (byte) 0xFE;
        System.arraycopy(utf16, 0, result, GearFitConstants.UTF16_BOM_SIZE, utf16.length);
        return result;
    }

    public static byte[] frame(final int port, final byte[] payload) {
        final byte[] result = new byte[GearFitConstants.FRAME_HEADER_SIZE + payload.length];
        result[0] = (byte) port;
        result[1] = (byte) payload.length;
        result[2] = (byte) (payload.length >>> 8);
        result[3] = (byte) (payload.length >>> 16);
        result[4] = (byte) (payload.length >>> 24);
        System.arraycopy(payload, 0, result, GearFitConstants.FRAME_HEADER_SIZE, payload.length);
        return result;
    }

    public static byte[] notificationAction(final int type, final long id) {
        return frame(PORT_NOTIFICATION, new Writer().u8(type).i64(id).toByteArray());
    }

    public static byte[] cannedMessage(final int port, final int type, final int count,
                                       final int index, final String message) {
        final byte[] encoded = rawString(message);
        final Writer writer = new Writer().u8(type).u8(count).u8(index).u16(encoded.length).bytes(encoded);
        return frame(port, writer.toByteArray());
    }

    public static byte[] calendarEvent(final CalendarEventSpec event) {
        final long start = TimeUnit.SECONDS.toMillis(event.getTimestamp());
        final long end = start + TimeUnit.SECONDS.toMillis(Math.max(0L, event.getDurationInSeconds()));
        final String title = event.getTitle() == null || event.getTitle().isEmpty() ? "Event" : event.getTitle();
        final String description = event.getDescription() == null ? "" : event.getDescription();
        final Writer writer = new Writer().u8(GearFitConstants.HOME_TYPE_CALENDAR_EVENTS).i32(1)
                .i64(event.getId()).u8(0).u8(event.getAllDay() ? 1 : 0).i32(event.getColor())
                .string8Raw(title)
                .i32((int) TimeUnit.MILLISECONDS.toSeconds(start))
                .i32((int) TimeUnit.MILLISECONDS.toSeconds(end))
                .string8Raw(description).u8(event.getAllDay() || isLongEvent(start, end) ? 1 : 0);
        return frame(PORT_HOME, writer.toByteArray());
    }

    public static byte[] calendarEventRemoved(final long id) {
        final int now = (int) TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        final Writer writer = new Writer().u8(GearFitConstants.HOME_TYPE_CALENDAR_EVENTS).i32(1)
                .i64(id).u8(1).u8(0).i32(0).u8(0).i32(now).i32(now).u8(0).u8(0);
        return frame(PORT_HOME, writer.toByteArray());
    }

    private static boolean isLongEvent(final long start, final long end) {
        return end - start >= TimeUnit.HOURS.toMillis(24);
    }

    private static byte[] rawString(final String value) {
        return value.getBytes(StandardCharsets.UTF_16LE);
    }

    /** Maps an OpenWeatherMap condition code to the icon code the watch understands. */
    public static String openWeatherIcon(final int conditionCode) {
        if (conditionCode >= 200 && conditionCode < 300) {
            return "11";
        } else if (conditionCode >= 300 && conditionCode < 400) {
            return "09";
        } else if (conditionCode >= 500 && conditionCode < 505) {
            return "10";
        } else if (conditionCode == 511) {
            return "13";
        } else if (conditionCode >= 520 && conditionCode < 600) {
            return "09";
        } else if (conditionCode >= 600 && conditionCode < 700) {
            return "13";
        } else if (conditionCode >= 700 && conditionCode < 800) {
            return "50";
        } else if (conditionCode == 800) {
            return "01";
        } else if (conditionCode == 801) {
            return "02";
        } else if (conditionCode == 802) {
            return "03";
        } else if (conditionCode >= 803) {
            return "04";
        }
        return "01";
    }

    public static int weatherIcon(final String icon) {
        final String value = icon == null ? "01" : icon;
        if (value.startsWith("01")) {
            return GearFitConstants.WEATHER_SUNNY;
        } else if (value.startsWith("02")) {
            return GearFitConstants.WEATHER_MOSTLY_CLEAR;
        } else if (value.startsWith("03") || value.startsWith("04")) {
            return GearFitConstants.WEATHER_MOSTLY_CLOUDY;
        } else if (value.startsWith("09")) {
            return GearFitConstants.WEATHER_HEAVY_RAIN;
        } else if (value.startsWith("10")) {
            return GearFitConstants.WEATHER_PARTLY_SUNNY_SHOWERS;
        } else if (value.startsWith("11")) {
            return GearFitConstants.WEATHER_THUNDERSTORMS;
        } else if (value.startsWith("13")) {
            return GearFitConstants.WEATHER_SNOW;
        } else if (value.startsWith("50")) {
            return GearFitConstants.WEATHER_FOG;
        }
        return GearFitConstants.WEATHER_SUNNY;
    }

    public static int weatherClockIcon(final String icon) {
        final String value = icon == null ? "01" : icon;
        if (value.startsWith("01")) {
            return GearFitConstants.WEATHER_CLOCK_SUNNY;
        } else if (value.startsWith("02")) {
            return GearFitConstants.WEATHER_CLOCK_CLEAR;
        } else if (value.startsWith("03") || value.startsWith("04")) {
            return GearFitConstants.WEATHER_CLOCK_MOSTLY_CLOUDY;
        } else if (value.startsWith("09")) {
            return GearFitConstants.WEATHER_CLOCK_SHOWERS;
        } else if (value.startsWith("10")) {
            return GearFitConstants.WEATHER_CLOCK_PARTLY_SUNNY_SHOWERS;
        } else if (value.startsWith("11")) {
            return GearFitConstants.WEATHER_CLOCK_THUNDERSTORMS;
        } else if (value.startsWith("13")) {
            return GearFitConstants.WEATHER_CLOCK_SNOW;
        } else if (value.startsWith("50")) {
            return GearFitConstants.WEATHER_CLOCK_FOG;
        }
        return GearFitConstants.WEATHER_CLOCK_SUNNY;
    }

    /**
     * The watch's temperature field is a fixed point number whose scale depends on the magnitude.
     */
    public static int encodeTemperature(final int degrees) {
        if (degrees < 100 && degrees > -100) {
            return degrees * 100;
        } else if (degrees < 1000 && degrees > -1000) {
            return degrees * 10;
        }
        return degrees;
    }

    private static String truncate(final String value, final int maxChars) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    public static String formatTrack(final String artist, final String track) {
        if (artist == null || artist.isEmpty()) {
            return track == null ? "" : track;
        }
        if (track == null || track.isEmpty()) {
            return artist;
        }
        return String.format(Locale.ROOT, "%s - %s", artist, track);
    }

    private static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);

        Writer u8(final int value) {
            out.write(value & 0xFF);
            return this;
        }

        Writer u16(final int value) {
            out.write(value & 0xFF);
            out.write((value >>> 8) & 0xFF);
            return this;
        }

        Writer bytes(final byte[] value) {
            out.write(value, 0, value.length);
            return this;
        }

        Writer string8Raw(final String value) {
            final byte[] encoded = rawString(value == null ? "" : value);
            final int length = Math.min(GearFitConstants.MAX_STRING8_BYTES, encoded.length & ~1);
            u8(length);
            out.write(encoded, 0, length);
            return this;
        }

        Writer i32(final int value) {
            out.write(value & 0xFF);
            out.write((value >>> 8) & 0xFF);
            out.write((value >>> 16) & 0xFF);
            out.write((value >>> 24) & 0xFF);
            return this;
        }

        Writer f32(final float value) {
            return i32(Float.floatToIntBits(value));
        }

        Writer f64(final double value) {
            final long bits = Double.doubleToLongBits(value);
            return i64(bits);
        }

        Writer i64(final long value) {
            for (int i = 0; i < 8; i++) {
                out.write((int) ((value >>> (8 * i)) & 0xFF));
            }
            return this;
        }

        Writer string8(final String value) {
            final byte[] encoded = encodeString(value);
            final int len = Math.min(GearFitConstants.MAX_STRING8_BYTES, encoded.length);
            u8(len);
            out.write(encoded, 0, len);
            return this;
        }

        Writer string16(final String value) {
            final byte[] encoded = encodeString(value);
            out.write(encoded.length & 0xFF);
            out.write((encoded.length >>> 8) & 0xFF);
            out.write(encoded, 0, encoded.length);
            return this;
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
