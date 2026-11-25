/*  Copyright (C) 2026 MedusasSphinx

    Fitness payload layout derived from OpenFit (https://github.com/jareddlc/OpenFit),
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Parses the record stream carried by a health port payload.
 * <p>
 * The payload starts with a nine byte envelope (four byte length, four byte type, one marker byte)
 * followed by a sequence of self describing records. Parsing stops at the first record type whose
 * size is unknown, so a malformed or extended payload can never desynchronize the stream.
 */
public final class GearFitFitnessParser {
    private static final Logger LOG = LoggerFactory.getLogger(GearFitFitnessParser.class);

    private GearFitFitnessParser() {
    }

    public static final class PedometerRecord {
        public final long timestampMillis;
        public final int steps;
        public final float distanceMeters;
        public final float calories;

        PedometerRecord(final long timestampMillis, final int steps, final float distanceMeters, final float calories) {
            this.timestampMillis = timestampMillis;
            this.steps = steps;
            this.distanceMeters = distanceMeters;
            this.calories = calories;
        }
    }

    public static final class HeartRateRecord {
        public final long timestampMillis;
        public final int heartRate;

        HeartRateRecord(final long timestampMillis, final int heartRate) {
            this.timestampMillis = timestampMillis;
            this.heartRate = heartRate;
        }
    }

    public static final class SleepInfoRecord {
        public final long timestampMillis;
        public final int status;

        SleepInfoRecord(final long timestampMillis, final int status) {
            this.timestampMillis = timestampMillis;
            this.status = status;
        }
    }

    public static final class SleepResultRecord {
        public final long startMillis;
        public final long endMillis;
        public final float efficiency;
        public final int index;
        public final int length;

        SleepResultRecord(final long startMillis, final long endMillis, final float efficiency,
                          final int index, final int length) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.efficiency = efficiency;
            this.index = index;
            this.length = length;
        }
    }

    public static final class ExerciseRecord {
        public final long timestampMillis;
        public final int durationSeconds;
        public final float calories;
        public final int averageHeartRate;
        public final float distanceMeters;
        public final int fitnessLevel;
        public final int type;
        public final float averageSpeed;
        public final float maxSpeed;
        public final int maxHeartRate;
        public final float maxAltitude;
        public final float minAltitude;
        public final float inclineDistance;
        public final float declineDistance;

        ExerciseRecord(final long timestampMillis, final int durationSeconds, final float calories,
                       final int averageHeartRate, final float distanceMeters, final int fitnessLevel,
                       final int type, final float averageSpeed, final float maxSpeed,
                       final int maxHeartRate, final float maxAltitude, final float minAltitude,
                       final float inclineDistance, final float declineDistance) {
            this.timestampMillis = timestampMillis;
            this.durationSeconds = durationSeconds;
            this.calories = calories;
            this.averageHeartRate = averageHeartRate;
            this.distanceMeters = distanceMeters;
            this.fitnessLevel = fitnessLevel;
            this.type = type;
            this.averageSpeed = averageSpeed;
            this.maxSpeed = maxSpeed;
            this.maxHeartRate = maxHeartRate;
            this.maxAltitude = maxAltitude;
            this.minAltitude = minAltitude;
            this.inclineDistance = inclineDistance;
            this.declineDistance = declineDistance;
        }
    }

    public static final class Result {
        public final List<PedometerRecord> pedometer = new ArrayList<>();
        public final List<HeartRateRecord> heartRate = new ArrayList<>();
        public final List<SleepInfoRecord> sleepInfo = new ArrayList<>();
        public final List<SleepResultRecord> sleepResults = new ArrayList<>();
        public final List<ExerciseRecord> exercises = new ArrayList<>();

        public boolean isEmpty() {
            return pedometer.isEmpty() && heartRate.isEmpty() && sleepInfo.isEmpty()
                    && sleepResults.isEmpty() && exercises.isEmpty();
        }
    }

    public static Result parse(final byte[] payload) {
        final Result result = new Result();
        if (payload == null || payload.length < GearFitConstants.FITNESS_ENVELOPE_SIZE + 1) {
            return result;
        }

        final ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(GearFitConstants.FITNESS_ENVELOPE_SIZE);

        while (buffer.hasRemaining()) {
            final byte recordType = buffer.get();
            try {
                if (!parseRecord(recordType, buffer, result)) {
                    LOG.debug("Stopping fitness parse at unsupported record type {}", recordType & 0xFF);
                    break;
                }
            } catch (final BufferUnderflowException e) {
                LOG.warn("Truncated fitness record type {}", recordType & 0xFF);
                break;
            }
        }

        return result;
    }

    /**
     * @return false when the record type has an unknown size and parsing cannot continue safely.
     */
    private static boolean parseRecord(final byte recordType, final ByteBuffer buffer, final Result result) {
        switch (recordType) {
            case GearFitConstants.FITNESS_USER_PROFILE: {
                // The leading integer is not a count, the profile always carries ten integers.
                buffer.getInt();
                skip(buffer, GearFitConstants.USER_PROFILE_REMAINING_SIZE);
                return true;
            }
            case GearFitConstants.FITNESS_PEDOMETER_PROFILE: {
                skip(buffer, GearFitConstants.PEDOMETER_PROFILE_SIZE);
                return true;
            }
            case GearFitConstants.FITNESS_COACHING_PROFILE:
                return false;
            case GearFitConstants.FITNESS_PEDO_RESULT_RECORD:
                // Unused in practice, and its length is not self describing.
                return false;
            case GearFitConstants.FITNESS_PEDO_INFO: {
                final int count = buffer.getInt();
                for (int i = 0; i < count; i++) {
                    result.pedometer.add(new PedometerRecord(
                            TimeUnit.SECONDS.toMillis(buffer.getInt()),
                            buffer.getInt(),
                            buffer.getFloat(),
                            buffer.getFloat()));
                }
                return true;
            }
            case GearFitConstants.FITNESS_SLEEP_RESULT_RECORD: {
                final int count = buffer.getInt();
                for (int i = 0; i < count; i++) {
                    result.sleepResults.add(new SleepResultRecord(
                            TimeUnit.SECONDS.toMillis(buffer.getInt()),
                            TimeUnit.SECONDS.toMillis(buffer.getInt()),
                            buffer.getFloat(),
                            buffer.getInt(),
                            buffer.getInt()));
                }
                return true;
            }
            case GearFitConstants.FITNESS_SLEEP_INFO: {
                final int count = buffer.getInt();
                for (int i = 0; i < count; i++) {
                    buffer.getInt(); // index
                    result.sleepInfo.add(new SleepInfoRecord(
                            TimeUnit.SECONDS.toMillis(buffer.getInt()),
                            buffer.getInt()));
                }
                return true;
            }
            case GearFitConstants.FITNESS_HEART_RATE_RESULT_RECORD: {
                final int count = buffer.getInt();
                for (int i = 0; i < count; i++) {
                    result.heartRate.add(new HeartRateRecord(
                            TimeUnit.SECONDS.toMillis(buffer.getInt()),
                            buffer.getInt()));
                }
                return true;
            }
            case GearFitConstants.FITNESS_COACHING_VARS: {
                skip(buffer, GearFitConstants.COACHING_VARS_SIZE);
                return true;
            }
            case GearFitConstants.FITNESS_COACHING_EXERCISE_RESULT: {
                final int count = buffer.getInt();
                skip(buffer, count * GearFitConstants.COACHING_EXERCISE_RESULT_SIZE);
                return true;
            }
            case GearFitConstants.FITNESS_COACHING_RUNNING_EXERCISE:
            case GearFitConstants.FITNESS_COACHING_ENERGY_EXERCISE:
                return false;
            case GearFitConstants.FITNESS_COACHING_RESULT_RECORD: {
                final int count = buffer.getInt();
                for (int i = 0; i < count; i++) {
                    final long timestampMillis = TimeUnit.SECONDS.toMillis(buffer.getInt());
                    final int durationSeconds = buffer.getInt();
                    final float calories = buffer.getFloat();
                    final int avgHeartRate = buffer.getInt();
                    final float distanceMeters = buffer.getFloat();
                    final int fitnessLevel = buffer.get() & 0xFF;
                    final int type = buffer.getInt();
                    final float avgSpeed = buffer.getFloat();
                    final float maxSpeed = buffer.getFloat();
                    final float maxAlt = buffer.getFloat();
                    final float minAlt = buffer.getFloat();
                    final int maxHeartRate = buffer.getInt();
                    final float inclineDistance = buffer.getFloat();
                    final float declineDistance = buffer.getFloat();

                    result.exercises.add(new ExerciseRecord(
                            timestampMillis,
                            durationSeconds,
                            calories,
                            avgHeartRate,
                            distanceMeters,
                            fitnessLevel,
                            type,
                            avgSpeed,
                            maxSpeed,
                            maxHeartRate,
                            maxAlt,
                            minAlt,
                            inclineDistance,
                            declineDistance));
                }
                return true;
            }
            case GearFitConstants.FITNESS_GPS_INFO:
                return false;
            default:
                return false;
        }
    }

    private static void skip(final ByteBuffer buffer, final int bytes) {
        if (bytes < 0 || bytes > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        buffer.position(buffer.position() + bytes);
    }
}
