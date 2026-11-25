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

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;

import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import lineageos.weather.util.TemperatureUtils;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.BaseActivitySummaryProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.samsung_gearfit.GearFitActivitySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCallControl;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventFindPhone;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventMusicControl;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventNotificationControl;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.externalevents.gps.GBLocationProviderType;
import nodomain.freeyourgadget.gadgetbridge.externalevents.gps.GBLocationService;
import nodomain.freeyourgadget.gadgetbridge.externalevents.AlarmClockReceiver;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.GearFitActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec;
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate;
import nodomain.freeyourgadget.gadgetbridge.export.GPXExporter;
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.AbstractBTBRDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;

public class GearFitSupport extends AbstractBTBRDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GearFitSupport.class);

    private final ByteBuffer packetBuffer = ByteBuffer.allocate(GearFitConstants.BUFFER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN);

    /**
     * The watch may spread a fitness sync over several frames. Chunks are collected here until the
     * link has been quiet long enough to consider the sync complete.
     */
    private final ByteArrayOutputStream fitnessStream = new ByteArrayOutputStream();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable fitnessFlush = this::flushFitness;
    private final Runnable batteryRefresh = this::refreshBattery;

    private void refreshBattery() {
        send(GearFitProtocol.frame(GearFitConstants.PORT_STATUS_MANAGER,
                new byte[]{GearFitConstants.STATUS_BATTERY_REQUEST}));
        handler.postDelayed(batteryRefresh, GearFitConstants.BATTERY_REFRESH_INTERVAL_MILLIS);
    }

    private boolean handshakeDone = false;
    private boolean fitnessPending = false;
    private String lastTrack = "";
    private int lastVolume = GearFitConstants.DEFAULT_MEDIA_VOLUME;
    private int activeAlarmNotificationId = GearFitConstants.NO_ACTIVE_ALARM_NOTIFICATION_ID;
    private String activeCallNumber;
    private String[] genericReplies = new String[0];
    private String[] rejectedCallReplies = new String[0];
    private final Map<Long, NotificationSpec> activeNotifications = new TreeMap<>();
    private final List<Location> gpsLocations = new ArrayList<>();
    private final Map<Long, GpsTrack> pendingGpsTracks = new TreeMap<>();
    private boolean gpsTracking;
    private boolean gpsSubscribed = true;
    private long gpsStartTimeMillis;
    private float gpsDistanceMeters;
    private float gpsMaxSpeed;
    private float gpsMinAltitude;
    private float gpsMaxAltitude;
    private Location lastGpsLocation;

    private static final class GpsTrack {
        final long startMillis;
        final long endMillis;
        final String path;

        GpsTrack(final long startMillis, final long endMillis, final String path) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.path = path;
        }
    }

    public GearFitSupport() {
        super(LOG, GearFitConstants.BUFFER_SIZE);
        addSupportedService(GearFitConstants.UUID_SERVICE);
    }

    @Override
    public boolean useAutoConnect() {
        // The Gear Fit reconnects on its own once it has been paired, so the phone has to follow.
        return true;
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        packetBuffer.clear();
        handshakeDone = false;
        builder.setDeviceState(GBDevice.State.INITIALIZED);
        return builder;
    }

    @Override
    public void dispose() {
        handler.removeCallbacks(fitnessFlush);
        handler.removeCallbacks(batteryRefresh);
        stopGpsTracking(false);
        super.dispose();
    }

    @Override
    public void onSocketRead(final byte[] data) {
        packetBuffer.put(data);
        packetBuffer.flip();

        while (packetBuffer.hasRemaining()) {
            packetBuffer.mark();

            if (packetBuffer.remaining() < GearFitConstants.FRAME_HEADER_SIZE) {
                packetBuffer.reset();
                break;
            }

            final int port = packetBuffer.get() & 0xFF;
            final int length = packetBuffer.getInt();

            if (length < 0 || length > GearFitConstants.MAX_PAYLOAD_SIZE) {
                LOG.warn("Invalid Gear Fit payload length {}, dropping buffer", length);
                packetBuffer.position(packetBuffer.limit());
                break;
            }

            if (packetBuffer.remaining() < length) {
                packetBuffer.reset();
                break;
            }

            final byte[] payload = new byte[length];
            packetBuffer.get(payload);

            try {
                handleMessage(port, payload);
            } catch (final Exception e) {
                LOG.error("Failed to handle Gear Fit message on port {}", port, e);
            }
        }

        packetBuffer.compact();
    }

    private void handleMessage(final int port, final byte[] payload) {
        switch (port) {
            case GearFitConstants.PORT_FEATURE:
                if (payload.length >= 4 && readInt(payload, 0) == GearFitConstants.FEATURE_READY) {
                    onReady();
                }
                break;
            case GearFitConstants.PORT_HOME:
                if (payload.length >= 1 && payload[0] == GearFitConstants.HOME_TYPE_WEATHER_REQUEST) {
                    sendWeather();
                }
                break;
            case GearFitConstants.PORT_HEALTH:
                handleHealth(payload);
                break;
            case GearFitConstants.PORT_FIND:
                handleFind(payload);
                break;
            case GearFitConstants.PORT_MEDIA:
                handleMedia(payload);
                break;
            case GearFitConstants.PORT_CALL:
                handleCall(payload);
                break;
            case GearFitConstants.PORT_NOTIFICATION:
                handleNotificationAction(payload);
                break;
            case GearFitConstants.PORT_ALARM:
                handleAlarm(payload);
                break;
            case GearFitConstants.PORT_STATUS_MANAGER:
                handleStatus(payload);
                break;
            case GearFitConstants.PORT_GESTURE:
                handleGesture(payload);
                break;
            default:
                LOG.debug("Unhandled Gear Fit message on port {} ({} bytes)", port, payload.length);
                break;
        }
    }

    private void onReady() {
        if (handshakeDone) {
            LOG.debug("Ignoring repeated Gear Fit ready message");
            return;
        }
        handshakeDone = true;
        LOG.info("Gear Fit is ready, performing handshake");

        final TransactionBuilder builder = createTransactionBuilder("handshake");
        builder.write(GearFitProtocol.handshakeFota());
        builder.write(GearFitProtocol.handshakeFollowUp());
        builder.write(GearFitProtocol.handshakeFotaCommand());
        builder.write(GearFitProtocol.frame(GearFitConstants.PORT_STATUS_MANAGER,
                new byte[]{GearFitConstants.STATUS_INITIALIZE}));
        builder.write(GearFitProtocol.frame(GearFitConstants.PORT_STATUS_MANAGER,
                new byte[]{GearFitConstants.STATUS_DEVICE_INFO_REQUEST}));
        builder.write(GearFitProtocol.setTime(is24Hour()));
        builder.queue();
        handler.removeCallbacks(batteryRefresh);
        handler.postDelayed(batteryRefresh, GearFitConstants.BATTERY_REFRESH_INTERVAL_MILLIS);
    }

    private void handleStatus(final byte[] payload) {
        if (payload.length == 0) {
            return;
        }
        final int type = payload[0] & 0xFF;
        if (type == (GearFitConstants.STATUS_BATTERY_REQUEST & 0xFF) && payload.length >= 3) {
            final GBDeviceEventBatteryInfo battery = new GBDeviceEventBatteryInfo();
            battery.batteryIndex = 0;
            battery.level = payload[1] & 0xFF;
            battery.state = (payload[2] == GearFitConstants.STATUS_BATTERY_CHARGING
                    || payload[2] == GearFitConstants.STATUS_BATTERY_FULL)
                    ? BatteryState.BATTERY_CHARGING : BatteryState.BATTERY_NORMAL;
            handleGBDeviceEvent(battery);
        } else if (type == (GearFitConstants.STATUS_FIRMWARE_VERSION & 0xFF) && payload.length >= 2) {
            final int length = payload[1] & 0xFF;
            if (length > 0 && payload.length >= length + 2) {
                publishVersionInfo(new String(payload, 2, length, StandardCharsets.US_ASCII).trim(), null);
            }
        } else if (type == (GearFitConstants.STATUS_DEVICE_INFO_REQUEST & 0xFF)
                && payload.length >= 3) {
            final int firmwareLength = payload[1] & 0xFF;
            final int modelLengthOffset = 2 + firmwareLength;
            if (firmwareLength == 0 || modelLengthOffset >= payload.length) {
                LOG.warn("Invalid Gear Fit device info response ({} bytes)", payload.length);
                return;
            }
            final String firmware = new String(payload, 2, firmwareLength, StandardCharsets.US_ASCII).trim();
            final int modelLength = payload[modelLengthOffset] & 0xFF;
            if (modelLengthOffset + 1 + modelLength > payload.length) {
                LOG.warn("Invalid Gear Fit model field length {} in {} byte response", modelLength, payload.length);
                return;
            }
            final String model = new String(payload, modelLengthOffset + 1, modelLength,
                    StandardCharsets.US_ASCII).trim();
            publishVersionInfo(firmware, model.isEmpty() ? null : model);
        }
    }

    private void publishVersionInfo(final String firmware, final String model) {
        final GBDeviceEventVersionInfo info = new GBDeviceEventVersionInfo();
        info.fwVersion = firmware;
        if (model != null) {
            info.hwVersion = model;
        }
        handleGBDeviceEvent(info);
        getDevice().setFirmwareVersion(firmware);
    }

    private void handleGesture(final byte[] payload) {
        if (payload.length == 0 || payload[0] != GearFitConstants.GESTURE_TYPE_REQUEST) {
            return;
        }
        final AudioManager audioManager = (AudioManager) getContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        final byte playerRunning = (byte) (audioManager != null && audioManager.isMusicActive() ? 1 : 0);
        send(GearFitProtocol.frame(GearFitConstants.PORT_GESTURE,
                new byte[]{GearFitConstants.GESTURE_TYPE_MEDIA_APP,
                        GearFitConstants.GESTURE_MEDIA_PLAYER_RUNNING, playerRunning}));
    }

    private void handleHealth(final byte[] payload) {
        if (isFitnessPayload(payload)) {
            handleFitnessChunk(payload);
            return;
        }

        if (payload.length < 4) {
            return;
        }

        final int type = readInt(payload, 0);
        switch (type) {
            case GearFitConstants.HEALTH_SYNC_REQUEST:
                LOG.info("Gear Fit requested a fitness sync");
                send(GearFitProtocol.fitnessRequest());
                break;
            case GearFitConstants.HEALTH_MENU:
                send(GearFitProtocol.fitnessMenuResponse());
                break;
            case GearFitConstants.HEALTH_GPS_READY_WATCH:
                final boolean gpsAvailable = isPhoneGpsAvailable();
                LOG.info("Gear Fit requested GPS status, available: {}", gpsAvailable);
                send(GearFitProtocol.gpsState(gpsAvailable));
                break;
            case GearFitConstants.HEALTH_GPS_EXERCISE_START:
                startGpsTracking(payload);
                break;
            case GearFitConstants.HEALTH_GPS_END:
                finishGpsTracking();
                break;
            case GearFitConstants.HEALTH_GPS_SUBSCRIBE:
                gpsSubscribed = true;
                sendCurrentGpsData();
                break;
            case GearFitConstants.HEALTH_GPS_UNSUBSCRIBE:
                gpsSubscribed = false;
                break;
            case GearFitConstants.HEALTH_GPS_START_WALKING:
            case GearFitConstants.HEALTH_GPS_START_RUNNING:
            case GearFitConstants.HEALTH_GPS_START_CYCLING:
            case GearFitConstants.HEALTH_GPS_START_HIKING:
                LOG.debug("Gear Fit selected GPS exercise mode {}", type);
                break;
            default:
                LOG.debug("Unhandled health message type {}", type);
                break;
        }
    }

    /**
     * A fitness payload is a nine byte envelope followed by the record stream:
     * {@code 04 000000}, {@code 0A 000000} and a {@code 0xFF} marker.
     */
    private static boolean isFitnessPayload(final byte[] payload) {
        return payload.length > GearFitConstants.FITNESS_ENVELOPE_SIZE
                && readInt(payload, 0) == GearFitConstants.FITNESS_ENVELOPE_TYPE
                && payload[GearFitConstants.FITNESS_ENVELOPE_SIZE - 1]
                == GearFitConstants.FITNESS_ENVELOPE_MARKER;
    }

    private void startGpsTracking(final byte[] payload) {
        if (payload.length < GearFitConstants.GPS_EXERCISE_START_MIN_SIZE) {
            LOG.warn("Truncated Gear Fit GPS exercise start ({} bytes)", payload.length);
            return;
        }
        if (!isPhoneGpsAvailable()) {
            LOG.warn("Gear Fit requested phone GPS, but GPS is disabled");
            send(GearFitProtocol.gpsState(false));
            return;
        }

        stopGpsTracking(false);
        gpsTracking = true;
        gpsSubscribed = true;
        gpsStartTimeMillis = System.currentTimeMillis();
        gpsDistanceMeters = 0f;
        gpsMaxSpeed = 0f;
        gpsMinAltitude = Float.POSITIVE_INFINITY;
        gpsMaxAltitude = Float.NEGATIVE_INFINITY;
        lastGpsLocation = null;
        gpsLocations.clear();

        final ByteBuffer start = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        start.position(4);
        final int exerciseType = start.getInt();
        final float userHeight = start.getFloat();
        final float userWeight = start.getFloat();
        LOG.info("Starting phone GPS for Gear Fit exercise {}, user height {}, weight {}",
                exerciseType, userHeight, userWeight);
        GBLocationService.start(getContext(), getDevice(), GBLocationProviderType.GPS,
                GearFitConstants.GPS_LOCATION_INTERVAL_MILLIS);
        send(GearFitProtocol.gpsReady());
    }

    private boolean isPhoneGpsAvailable() {
        final boolean hasLocationPermission = ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return hasLocationPermission && GBLocationService.isGpsSupportedAndEnabled();
    }

    private void finishGpsTracking() {
        if (!gpsTracking) {
            LOG.debug("Gear Fit ended GPS exercise without an active phone GPS session");
            return;
        }

        final long endTimeMillis = System.currentTimeMillis();
        GBLocationService.stop(getContext(), getDevice());
        send(GearFitProtocol.gpsState(false));
        final float elapsedSeconds = Math.max(1f,
                (endTimeMillis - gpsStartTimeMillis) / (float) TimeUnit.SECONDS.toMillis(1));
        final float averageSpeed = gpsDistanceMeters / elapsedSeconds;
        final float maxAltitude = gpsMaxAltitude == Float.NEGATIVE_INFINITY ? 0f : gpsMaxAltitude;
        final float minAltitude = gpsMinAltitude == Float.POSITIVE_INFINITY ? 0f : gpsMinAltitude;
        send(GearFitProtocol.gpsResult(gpsDistanceMeters, maxAltitude, minAltitude, gpsMaxSpeed,
                averageSpeed, 0f, 0f, 0f));

        final String trackPath = writeGpsTrack();
        if (trackPath != null) {
            pendingGpsTracks.put(gpsStartTimeMillis,
                    new GpsTrack(gpsStartTimeMillis, endTimeMillis, trackPath));
            while (pendingGpsTracks.size() > GearFitConstants.MAX_PENDING_GPS_TRACKS) {
                pendingGpsTracks.remove(pendingGpsTracks.keySet().iterator().next());
            }
        }
        gpsTracking = false;
        LOG.info("Finished phone GPS session: {} points, {} meters", gpsLocations.size(), gpsDistanceMeters);
    }

    private void stopGpsTracking(final boolean sendResult) {
        if (!gpsTracking) {
            return;
        }
        if (sendResult) {
            finishGpsTracking();
            return;
        }
        GBLocationService.stop(getContext(), getDevice());
        gpsTracking = false;
        gpsLocations.clear();
        lastGpsLocation = null;
    }

    private void sendCurrentGpsData() {
        final float speed = lastGpsLocation != null && lastGpsLocation.hasSpeed()
                ? lastGpsLocation.getSpeed() : 0f;
        final double altitude = lastGpsLocation != null && lastGpsLocation.hasAltitude()
                ? lastGpsLocation.getAltitude() : 0d;
        send(GearFitProtocol.gpsData(gpsDistanceMeters, speed, 0f, altitude));
    }

    private String writeGpsTrack() {
        if (gpsLocations.size() < 2) {
            LOG.debug("Not exporting Gear Fit GPS track with {} point(s)", gpsLocations.size());
            return null;
        }
        try {
            final File exportDirectory = getDevice().getDeviceCoordinator()
                    .getWritableExportDirectory(getDevice(), true);
            if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
                LOG.warn("Failed to create Gear Fit export directory {}", exportDirectory);
                return null;
            }
            final File gpxFile = new File(exportDirectory,
                    "gearfit_" + TimeUnit.MILLISECONDS.toSeconds(gpsStartTimeMillis) + ".gpx");
            final ActivityTrack track = new ActivityTrack();
            track.setBaseTime(new Date(gpsStartTimeMillis));
            track.setName("Gear Fit GPS");
            for (final Location location : gpsLocations) {
                final ActivityPoint point = new ActivityPoint(new Date(location.getTime()));
                point.setLocation(new GPSCoordinate(location.getLongitude(), location.getLatitude(),
                        location.hasAltitude() ? location.getAltitude() : GPSCoordinate.UNKNOWN_ALTITUDE));
                if (location.hasSpeed()) {
                    point.setSpeed(location.getSpeed());
                }
                track.addTrackPoint(point);
            }
            new GPXExporter().performExport(track, gpxFile, null);
            return gpxFile.getAbsolutePath();
        } catch (final Exception e) {
            LOG.error("Failed to export Gear Fit GPS track", e);
            return null;
        }
    }

    @Override
    public void onSetGpsLocation(final Location location) {
        if (!gpsTracking || location == null) {
            return;
        }

        final Location point = new Location(location);
        float speed = point.hasSpeed() ? Math.max(0f, point.getSpeed()) : 0f;
        if (lastGpsLocation != null) {
            final long elapsedMillis = point.getTime() - lastGpsLocation.getTime();
            final float distance = lastGpsLocation.distanceTo(point);
            if (elapsedMillis > 0 && distance < GearFitConstants.MAX_GPS_POINT_DISTANCE_METERS) {
                gpsDistanceMeters += distance;
                if (!point.hasSpeed()) {
                    speed = distance * TimeUnit.SECONDS.toMillis(1) / elapsedMillis;
                    point.setSpeed(speed);
                }
            }
        }
        if (point.hasAltitude()) {
            gpsMinAltitude = Math.min(gpsMinAltitude, (float) point.getAltitude());
            gpsMaxAltitude = Math.max(gpsMaxAltitude, (float) point.getAltitude());
        }
        gpsMaxSpeed = Math.max(gpsMaxSpeed, speed);
        gpsLocations.add(point);
        lastGpsLocation = point;

        if (gpsSubscribed) {
            send(GearFitProtocol.gpsData(gpsDistanceMeters, speed, 0f,
                    point.hasAltitude() ? point.getAltitude() : 0d));
        }
    }

    private void handleFitnessChunk(final byte[] payload) {
        synchronized (fitnessStream) {
            // A new envelope always starts a new sync, anything else continues the current one.
            if (fitnessPending) {
                fitnessStream.write(payload, 0, payload.length);
            } else {
                fitnessStream.reset();
                fitnessStream.write(payload, 0, payload.length);
                fitnessPending = true;
            }
        }

        handler.removeCallbacks(fitnessFlush);
        handler.postDelayed(fitnessFlush, GearFitConstants.FITNESS_SYNC_SETTLE_MILLIS);
    }

    private void flushFitness() {
        final byte[] payload;
        synchronized (fitnessStream) {
            payload = fitnessStream.toByteArray();
            fitnessStream.reset();
            fitnessPending = false;
        }

        final GearFitFitnessParser.Result result = GearFitFitnessParser.parse(payload);
        if (result.isEmpty()) {
            LOG.debug("Fitness payload contained no usable records");
            return;
        }

        LOG.info("Got fitness data: {} pedometer, {} heart rate, {} sleep, {} exercises",
                result.pedometer.size(), result.heartRate.size(), result.sleepInfo.size(),
                result.exercises.size());

        persistFitnessData(result, payload);
        send(GearFitProtocol.syncDone());
    }

    private void persistFitnessData(final GearFitFitnessParser.Result result, final byte[] payload) {
        final Map<Integer, GearFitActivitySample> samples = new TreeMap<>();

        for (final GearFitFitnessParser.PedometerRecord record : result.pedometer) {
            final GearFitActivitySample sample = getOrCreate(samples, record.timestampMillis);
            final int steps = Math.max(0, record.steps);
            sample.setRawKind(ActivityKind.ACTIVITY.getCode());
            sample.setSteps(steps);
            sample.setRawIntensity(Math.min(100, steps));
            sample.setDistanceCm(Math.round(record.distanceMeters * 100f));
            sample.setActiveCalories(Math.round(record.calories));
        }

        for (final GearFitFitnessParser.HeartRateRecord record : result.heartRate) {
            final GearFitActivitySample sample = getOrCreate(samples, record.timestampMillis);
            sample.setHeartRate(record.heartRate);
            if (sample.getRawKind() == ActivitySample.NOT_MEASURED) {
                sample.setRawKind(ActivityKind.UNKNOWN.getCode());
            }
            if (sample.getSteps() == ActivitySample.NOT_MEASURED) {
                sample.setSteps(0);
            }
        }

        for (final GearFitFitnessParser.SleepInfoRecord record : result.sleepInfo) {
            final GearFitActivitySample sample = getOrCreate(samples, record.timestampMillis);
            sample.setRawKind(sleepKind(record.status).getCode());
            sample.setSteps(0);
        }

        if (result.sleepInfo.isEmpty()) {
            for (final GearFitFitnessParser.SleepResultRecord record : result.sleepResults) {
                addSleepWindow(samples, record.startMillis, record.endMillis);
            }
        }

        final List<GearFitActivitySample> list = new ArrayList<>(samples.values());
        final List<BaseActivitySummary> summaries = new ArrayList<>();

        for (final GearFitFitnessParser.ExerciseRecord record : result.exercises) {
            final ActivityKind kind = exerciseKind(record.type);
            summaries.add(createSummary(record, kind, payload));
        }

        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            final DaoSession session = dbHandler.getDaoSession();
            new GearFitActivitySampleProvider(getDevice(), session)
                    .persistSamples(list, getContext());

            if (!summaries.isEmpty()) {
                new BaseActivitySummaryProvider(getDevice(), session)
                        .persistSamples(summaries, getContext());
            }
        } catch (final Exception e) {
            LOG.error("Failed to persist Gear Fit fitness data", e);
        }
    }

    private GearFitActivitySample getOrCreate(final Map<Integer, GearFitActivitySample> samples,
                                              final long timestampMillis) {
        final int timestamp = (int) TimeUnit.MILLISECONDS.toSeconds(timestampMillis);
        GearFitActivitySample sample = samples.get(timestamp);
        if (sample == null) {
            sample = new GearFitActivitySample();
            sample.setTimestamp(timestamp);
            sample.setRawKind(ActivitySample.NOT_MEASURED);
            sample.setRawIntensity(ActivitySample.NOT_MEASURED);
            sample.setSteps(ActivitySample.NOT_MEASURED);
            sample.setHeartRate(ActivitySample.NOT_MEASURED);
            sample.setDistanceCm(ActivitySample.NOT_MEASURED);
            sample.setActiveCalories(ActivitySample.NOT_MEASURED);
            samples.put(timestamp, sample);
        }
        return sample;
    }

    private void addSleepWindow(final Map<Integer, GearFitActivitySample> samples,
                                final long startMillis, final long endMillis) {
        if (endMillis <= startMillis) {
            return;
        }
        // Guard against bogus timestamps producing hundreds of thousands of samples.
        final long cappedEnd = Math.min(endMillis,
                startMillis + GearFitConstants.MAX_SLEEP_WINDOW_MILLIS);
        for (long timestamp = startMillis; timestamp < cappedEnd;
             timestamp += GearFitConstants.SLEEP_SAMPLE_INTERVAL_MILLIS) {
            final GearFitActivitySample sample = getOrCreate(samples, timestamp);
            sample.setRawKind(ActivityKind.LIGHT_SLEEP.getCode());
            sample.setSteps(0);
        }
    }

    private BaseActivitySummary createSummary(final GearFitFitnessParser.ExerciseRecord record,
                                              final ActivityKind kind, final byte[] payload) {
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setName(getContext().getString(kind.getLabel()));
        summary.setStartTime(new Date(record.timestampMillis));
        summary.setEndTime(new Date(record.timestampMillis
                + TimeUnit.SECONDS.toMillis(record.durationSeconds)));
        summary.setActivityKind(kind.getCode());
        summary.setRawSummaryData(payload);
        final GpsTrack gpsTrack = findGpsTrack(record.timestampMillis);
        if (gpsTrack != null) {
            summary.setGpxTrack(gpsTrack.path);
        }
        return summary;
    }

    private GpsTrack findGpsTrack(final long activityStartMillis) {
        Long matchKey = null;
        long nearestDistance = Long.MAX_VALUE;
        for (final Map.Entry<Long, GpsTrack> entry : pendingGpsTracks.entrySet()) {
            final GpsTrack track = entry.getValue();
            final long distance = activityStartMillis < track.startMillis
                    ? track.startMillis - activityStartMillis
                    : activityStartMillis > track.endMillis
                    ? activityStartMillis - track.endMillis : 0L;
            if (distance <= GearFitConstants.GPS_TRACK_MATCH_WINDOW_MILLIS
                    && distance < nearestDistance) {
                nearestDistance = distance;
                matchKey = entry.getKey();
            }
        }
        if (matchKey == null) {
            return null;
        }
        return pendingGpsTracks.remove(matchKey);
    }

    private static ActivityKind sleepKind(final int status) {
        if (status >= GearFitConstants.SLEEP_DEEP_STATUS_MIN) {
            return ActivityKind.DEEP_SLEEP;
        } else if (status >= GearFitConstants.SLEEP_LIGHT_STATUS_MIN) {
            return ActivityKind.LIGHT_SLEEP;
        }
        return ActivityKind.AWAKE_SLEEP;
    }

    private static ActivityKind exerciseKind(final int type) {
        switch (type) {
            case GearFitConstants.EXERCISE_WALKING:
                return ActivityKind.WALKING;
            case GearFitConstants.EXERCISE_RUNNING:
                return ActivityKind.RUNNING;
            case GearFitConstants.EXERCISE_CYCLING:
                return ActivityKind.CYCLING;
            case GearFitConstants.EXERCISE_HIKING:
                return ActivityKind.HIKING;
            default:
                return ActivityKind.ACTIVITY;
        }
    }

    private void handleFind(final byte[] payload) {
        if (payload.length < 2 || payload[0] != GearFitConstants.FIND_EVENT_TYPE) {
            return;
        }
        final GBDeviceEventFindPhone event = new GBDeviceEventFindPhone();
        event.event = payload[1] == GearFitConstants.FIND_START
                ? GBDeviceEventFindPhone.Event.START
                : GBDeviceEventFindPhone.Event.STOP;
        evaluateGBDeviceEvent(event);
    }

    private void handleMedia(final byte[] payload) {
        if (payload.length < 1) {
            return;
        }

        switch (payload[0]) {
            case GearFitConstants.MEDIA_CONTROL:
                if (payload.length < 2) {
                    return;
                }
                switch (payload[1]) {
                    case GearFitConstants.MEDIA_PLAY:
                        evaluateGBDeviceEvent(new GBDeviceEventMusicControl(GBDeviceEventMusicControl.Event.PLAYPAUSE));
                        break;
                    case GearFitConstants.MEDIA_PAUSE:
                    case GearFitConstants.MEDIA_STOP:
                        evaluateGBDeviceEvent(new GBDeviceEventMusicControl(GBDeviceEventMusicControl.Event.PAUSE));
                        break;
                    case GearFitConstants.MEDIA_FORWARD:
                        evaluateGBDeviceEvent(new GBDeviceEventMusicControl(GBDeviceEventMusicControl.Event.NEXT));
                        break;
                    case GearFitConstants.MEDIA_REWIND:
                        evaluateGBDeviceEvent(new GBDeviceEventMusicControl(GBDeviceEventMusicControl.Event.PREVIOUS));
                        break;
                    default:
                        LOG.debug("Unhandled media control {}", payload[1]);
                        break;
                }
                break;
            case GearFitConstants.MEDIA_VOLUME:
                if (payload.length >= 2) {
                    final int volume = payload[1] & 0xFF;
                    if (volume > lastVolume) {
                        final int steps = volume - lastVolume;
                        for (int i = 0; i < steps; i++) {
                            evaluateGBDeviceEvent(new GBDeviceEventMusicControl(GBDeviceEventMusicControl.Event.VOLUMEUP));
                        }
                    } else if (volume < lastVolume) {
                        final int steps = lastVolume - volume;
                        for (int i = 0; i < steps; i++) {
                            evaluateGBDeviceEvent(new GBDeviceEventMusicControl(GBDeviceEventMusicControl.Event.VOLUMEDOWN));
                        }
                    }
                    lastVolume = volume;
                }
                break;
            case GearFitConstants.MEDIA_REQUEST_START:
                send(GearFitProtocol.mediaTrack(lastTrack));
                send(GearFitProtocol.mediaVolume(lastVolume));
                break;
            default:
                LOG.debug("Unhandled media message {}", payload[0]);
                break;
        }
    }

    private void handleCall(final byte[] payload) {
        if (payload.length < 2 || payload[0] != GearFitConstants.CALL_TYPE_REJECT) {
            return;
        }
        if (payload[1] == GearFitConstants.CALL_REJECT_WITH_MESSAGE && payload.length >= 6) {
            final int index = readInt(payload, 2);
            if (index >= 0 && index < rejectedCallReplies.length && activeCallNumber != null) {
                final GBDeviceEventNotificationControl reply = new GBDeviceEventNotificationControl();
                reply.handle = -1;
                reply.phoneNumber = activeCallNumber;
                reply.reply = rejectedCallReplies[index];
                reply.event = GBDeviceEventNotificationControl.Event.REPLY;
                evaluateGBDeviceEvent(reply);
            }
            evaluateGBDeviceEvent(new GBDeviceEventCallControl(GBDeviceEventCallControl.Event.END));
        } else if (payload[1] == GearFitConstants.CALL_REJECT_NOW) {
            evaluateGBDeviceEvent(new GBDeviceEventCallControl(GBDeviceEventCallControl.Event.END));
        }
    }

    private void handleNotificationAction(final byte[] payload) {
        if (payload.length < 9) {
            return;
        }
        final int type = payload[0] & 0xFF;
        final boolean quickReply = type == (GearFitConstants.NOTIFICATION_QUICK_REPLY_ACTION & 0xFF);
        if (quickReply && payload.length < 10) {
            return;
        }
        final long id = ByteBuffer.wrap(payload, quickReply ? 2 : 1, 8)
                .order(ByteOrder.LITTLE_ENDIAN).getLong();
        final NotificationSpec notification = activeNotifications.get(id);
        final GBDeviceEventNotificationControl event = new GBDeviceEventNotificationControl();
        event.handle = id;
        if (type == (GearFitConstants.NOTIFICATION_DELETE_ACTION & 0xFF)) {
            event.event = GBDeviceEventNotificationControl.Event.DISMISS;
        } else if (type == (GearFitConstants.NOTIFICATION_OPEN_ACTION & 0xFF)) {
            event.event = GBDeviceEventNotificationControl.Event.OPEN;
        } else if (type == (GearFitConstants.NOTIFICATION_READ_ACTION & 0xFF)) {
            LOG.debug("Gear Fit marked notification {} as read; Gadgetbridge has no read-only notification action", id);
            return;
        } else if (quickReply) {
            final int index = payload[1] & 0xFF;
            final String[] replies = notification != null && notification.getCannedReplies() != null
                    ? notification.getCannedReplies() : genericReplies;
            if (index >= replies.length) {
                LOG.warn("Gear Fit requested unavailable quick reply {} for notification {}", index, id);
                return;
            }
            event.event = GBDeviceEventNotificationControl.Event.REPLY;
            event.reply = replies[index];
            event.phoneNumber = notification == null ? null : notification.getPhoneNumber();
        } else {
            LOG.debug("Unhandled Gear Fit notification action {}", type);
            return;
        }
        evaluateGBDeviceEvent(event);
    }

    private void handleAlarm(final byte[] payload) {
        if (payload.length < 2 || payload[0] != GearFitConstants.ALARM_TYPE_ACTION) {
            return;
        }
        if (payload[1] == GearFitConstants.ALARM_ACTION_SNOOZED) {
            LOG.info("Alarm snoozed on the Gear Fit");
            sendAlarmClockAction(AlarmClockReceiver.ALARM_SNOOZE_ACTION);
        } else if (payload[1] == GearFitConstants.ALARM_ACTION_DISMISSED) {
            LOG.info("Alarm dismissed on the Gear Fit");
            sendAlarmClockAction(AlarmClockReceiver.ALARM_DISMISS_ACTION);
        } else {
            LOG.debug("Unhandled Gear Fit alarm action {}", payload[1]);
        }
    }

    private void sendAlarmClockAction(final String action) {
        getContext().sendBroadcast(new android.content.Intent(action));
    }

    private void sendWeather() {
        final WeatherSpec weatherSpec = Weather.getWeatherSpec();
        if (weatherSpec == null) {
            LOG.warn("No weather available to send");
            return;
        }

        int temperature = weatherSpec.getCurrentTemp() - GearFitConstants.KELVIN_CELSIUS_OFFSET;
        int unit = GearFitConstants.WEATHER_UNIT_CELSIUS;
        final String unitPreference = GBApplication.getPrefs()
                .getString(SettingsActivity.PREF_UNIT_TEMPERATURE, "celsius");
        if ("fahrenheit".equals(unitPreference)) {
            temperature = Math.round((float) TemperatureUtils.celsiusToFahrenheit(temperature));
            unit = GearFitConstants.WEATHER_UNIT_FAHRENHEIT;
        }

        final String icon = GearFitProtocol.openWeatherIcon(weatherSpec.getCurrentConditionCode());
        final int clockIcon = GearFitProtocol.weatherClockIcon(icon);
        final String location = weatherSpec.getLocation() == null ? "" : weatherSpec.getLocation();

        send(GearFitProtocol.weatherClock(location, GearFitProtocol.encodeTemperature(temperature),
                unit, clockIcon, System.currentTimeMillis()));
    }

    private void send(final byte[] data) {
        final TransactionBuilder builder = createTransactionBuilder("send");
        builder.write(data);
        builder.queue();
    }

    private boolean is24Hour() {
        return DateFormat.is24HourFormat(getContext());
    }

    private static int readInt(final byte[] data, final int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static String firstNonEmpty(final String... values) {
        for (final String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static boolean isEmail(final NotificationType type) {
        if (type == null) {
            return false;
        }
        switch (type) {
            case GENERIC_EMAIL:
            case GMAIL:
            case GOOGLE_INBOX:
            case OUTLOOK:
            case YAHOO_MAIL:
            case MAILBOX:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onSetTime() {
        send(GearFitProtocol.setTime(is24Hour()));
    }

    @Override
    public void onFindDevice(final boolean start) {
        send(GearFitProtocol.findDevice(start));
    }

    @Override
    public void onFindPhone(final boolean start) {
        if (!start) {
            send(GearFitProtocol.findDevice(false));
        }
    }

    @Override
    public void onSetPhoneVolume(final float volume) {
        lastVolume = Math.max(0, Math.min(GearFitConstants.MAX_MEDIA_VOLUME,
                Math.round(volume * GearFitConstants.MAX_MEDIA_VOLUME / 100f)));
        send(GearFitProtocol.mediaVolume(lastVolume));
    }

    @Override
    public void onSetMusicInfo(final MusicSpec musicSpec) {
        lastTrack = GearFitProtocol.formatTrack(musicSpec.getArtist(), musicSpec.getTrack());
        send(GearFitProtocol.mediaTrack(lastTrack));
    }

    @Override
    public void onSetMusicState(final MusicStateSpec stateSpec) {
        // The watch reflects playback state on its own once it has the track information.
        LOG.debug("Ignoring music state {}", stateSpec);
    }

    @Override
    public void onSetAlarms(final ArrayList<? extends Alarm> alarms) {
        // Gear Fit 1 has no local alarm slots; it only mirrors alarms currently ringing on phone.
    }

    @Override
    public void onDeleteNotification(final int id) {
        activeNotifications.remove((long) id);
        if (id == activeAlarmNotificationId) {
            activeAlarmNotificationId = GearFitConstants.NO_ACTIVE_ALARM_NOTIFICATION_ID;
            send(GearFitProtocol.alarmClear());
        }
    }

    @Override
    public void onNotification(final NotificationSpec notificationSpec) {
        if (notificationSpec.getType() == NotificationType.GENERIC_ALARM_CLOCK) {
            activeAlarmNotificationId = notificationSpec.getId();
            final Calendar now = Calendar.getInstance();
            final String alarmName = firstNonEmpty(notificationSpec.getTitle(),
                    getContext().getString(nodomain.freeyourgadget.gadgetbridge.R.string.menuitem_alarm));
            send(GearFitProtocol.alarm(notificationSpec.getId(), alarmName,
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true));
            return;
        }

        final long timestamp = notificationSpec.getWhen() > 0
                ? notificationSpec.getWhen()
                : System.currentTimeMillis();
        final long id = notificationSpec.getId();
        activeNotifications.put(id, notificationSpec);

        final String sender = firstNonEmpty(
                notificationSpec.getSender(),
                notificationSpec.getSourceName(),
                "Gadgetbridge");
        final String number = firstNonEmpty(notificationSpec.getPhoneNumber());
        final String title = firstNonEmpty(
                notificationSpec.getTitle(),
                notificationSpec.getSubject(),
                notificationSpec.getSourceName(),
                sender);
        final String body = firstNonEmpty(
                notificationSpec.getBody(),
                notificationSpec.getSubject());

        if (isEmail(notificationSpec.getType())) {
            send(GearFitProtocol.email(id, sender, number,
                    firstNonEmpty(notificationSpec.getSubject(), title),
                    body, timestamp));
        } else {
            send(GearFitProtocol.notification(GearFitConstants.NOTIFICATION_MESSAGE, id,
                    sender, number, title, body, timestamp));
        }
        while (activeNotifications.size() > GearFitConstants.MAX_ACTIVE_NOTIFICATIONS) {
            activeNotifications.remove(activeNotifications.keySet().iterator().next());
        }
    }

    @Override
    public void onSetCallState(final CallSpec callSpec) {
        switch (callSpec.getCommand()) {
            case CallSpec.CALL_INCOMING:
            case CallSpec.CALL_START:
                activeCallNumber = callSpec.getNumber();
                send(GearFitProtocol.incomingCall(
                        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),
                        firstNonEmpty(callSpec.getName(), callSpec.getNumber(), "Gadgetbridge"),
                        firstNonEmpty(callSpec.getNumber()),
                        System.currentTimeMillis()));
                break;
            case CallSpec.CALL_END:
            case CallSpec.CALL_REJECT:
                activeCallNumber = null;
                send(GearFitProtocol.callEnded());
                break;
            default:
                break;
        }
    }

    @Override
    public void onSendWeather() {
        sendWeather();
    }

    @Override
    public void onSetCannedMessages(final CannedMessagesSpec spec) {
        final String[] messages = spec.getCannedMessages() == null ? new String[0] : spec.getCannedMessages();
        final int count = Math.min(messages.length, GearFitConstants.MAX_CANNED_REPLY_MESSAGES);
        if (spec.getType() == CannedMessagesSpec.TYPE_REJECTEDCALLS) {
            rejectedCallReplies = copyMessages(messages, count);
            sendCannedMessages(GearFitConstants.PORT_CALL, 4, rejectedCallReplies);
        } else if (spec.getType() == CannedMessagesSpec.TYPE_GENERIC) {
            genericReplies = copyMessages(messages, count);
            sendCannedMessages(GearFitConstants.PORT_NOTIFICATION,
                    GearFitConstants.NOTIFICATION_QUICK_REPLY_MESSAGE, genericReplies);
        }
    }

    private static String[] copyMessages(final String[] messages, final int count) {
        final String[] copy = new String[count];
        for (int i = 0; i < count; i++) {
            copy[i] = messages[i] == null ? "" : messages[i];
        }
        return copy;
    }

    private void sendCannedMessages(final int port, final int type, final String[] messages) {
        final int count = Math.max(1, messages.length);
        for (int i = 0; i < count; i++) {
            final String message = messages.length == 0 ? " " : messages[i];
            send(GearFitProtocol.cannedMessage(port, type, messages.length, i, message));
        }
    }

    @Override
    public void onAddCalendarEvent(final CalendarEventSpec event) {
        send(GearFitProtocol.calendarEvent(event));
    }

    @Override
    public void onDeleteCalendarEvent(final byte type, final long id) {
        send(GearFitProtocol.calendarEventRemoved(id));
    }
}
