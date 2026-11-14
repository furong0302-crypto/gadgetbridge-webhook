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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericRespiratoryRateSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSpo2SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractWithingsActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericRespiratoryRateSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSpo2Sample;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsBreathingDisturbanceSample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity.ActivityEntry;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity.WithingsActivityType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WorkoutType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivitySampleCalories;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivitySampleCalories2;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivitySampleDuration;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivitySampleMovement;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivitySampleSleep;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivitySampleTime;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivitySampleUnknown;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivitySampleWalk;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivityHeartrate;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.VasistasSpo2;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.VasistasAhi;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.VasistasRespiratoryRate;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructureType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class ActivitySampleHandler extends AbstractResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(ActivitySampleHandler.class);
    private ActivityEntry activityEntry;
    private List<ActivityEntry> activityEntries = new ArrayList<>();
    private List<ActivityEntry> heartrateEntries = new ArrayList<>();
    private final List<long[]> spo2Entries = new ArrayList<>();
    private final List<long[]> respiratoryRateEntries = new ArrayList<>();
    private final List<long[]> breathingDisturbanceEntries = new ArrayList<>();

    public ActivitySampleHandler(WithingsBaseDeviceSupport support) {
        super(support);
    }

    @Override
    public void handleResponse(Message response) {
        List<WithingsStructure> data = response.getDataStructures();
        if (data != null) {
            handleActivityData(data, response.getType());
        }
    }

    public void onSyncFinished() {
        heartrateEntries = mergeHeartrateSamplesIntoActivitySamples(activityEntries, heartrateEntries);
        saveData();
    }

    private void handleActivityData(List<WithingsStructure> dataList, short activityType) {
        for (WithingsStructure data : dataList) {
            switch (data.getType()) {
                case WithingsStructureType.ACTIVITY_SAMPLE_TIME:
                    handleTimestamp(data, activityType);
                    break;
                case WithingsStructureType.ACTIVITY_SAMPLE_DURATION:
                    handleDuration(data);
                    break;
                case WithingsStructureType.ACTIVITY_SAMPLE_MOVEMENT:
                    handleMovement(data);
                    break;
                case WithingsStructureType.ACTIVITY_SAMPLE_CALORIES:
                    handleCalories1(data);
                    break;
                case WithingsStructureType.ACTIVITY_SAMPLE_CALORIES_2:
                    handleCalories2(data);
                    break;
                case WithingsStructureType.ACTIVITY_SAMPLE_SLEEP:
                    handleSleep(data);
                    break;
                case WithingsStructureType.ACTIVITY_SAMPLE_WALK:
                    handleWalk(data);
                    break;
                case WithingsStructureType.ACTIVITY_SAMPLE_RUN:
                    handleRun(data);
                    break;
                case WithingsStructureType.ACTIVITY_SAMPLE_SWIM:
                    handleSwim(data);
                    break;
                case WithingsStructureType.ACTIVITY_HR:
                    handleHeartrate(data);
                    break;
                case WithingsStructureType.WORKOUT_TYPE:
                    handleWorkoutType(data);
                    break;
                case WithingsStructureType.VASISTAS_SPO2:
                    handleSpo2(data);
                    break;
                case WithingsStructureType.VASISTAS_AHI:
                    handleAhi(data);
                    break;
                case WithingsStructureType.VASISTAS_RESPIRATORY_RATE:
                    handleRespiratoryRate(data);
                    break;
                default:
                    logUnhandledActivityData(data);
            }
        }

        if (activityEntry != null) {
            addToList(activityEntry);
            activityEntry = null;
        }

    }

    private void handleTimestamp(WithingsStructure data, short activityType) {
        if (activityEntry != null) {
            addToList(activityEntry);
        }

        activityEntry = new ActivityEntry();
        activityEntry.setIsHeartrate(activityType == WithingsMessageType.GET_HEARTRATE_SAMPLES);
        activityEntry.setTimestamp((int) (((ActivitySampleTime) data).getDate().getTime() / 1000));
    }

    private void handleWorkoutType(WithingsStructure data) {
        WithingsActivityType activityType = WithingsActivityType.fromCode(((WorkoutType) data).getActivityType());
        activityEntry.setRawKind(activityType.toActivityKind().getCode());
        activityEntry.setHasActivityData(true);
    }

    private void handleDuration(WithingsStructure data) {
        activityEntry.setDuration(((ActivitySampleDuration) data).getDuration());
    }

    private void handleHeartrate(WithingsStructure data) {
        activityEntry.setIsHeartrate(((ActivityHeartrate) data).getHeartrate());
    }

    private void handleSpo2(final WithingsStructure data) {
        if (activityEntry == null) {
            logger.info("Received Withings SpO2 vasistas without timestamp context: {}", GB.hexdump(data.getRawData()));
            return;
        }

        final VasistasSpo2 vasistasSpo2 = (VasistasSpo2) data;
        final int spo2 = vasistasSpo2.getSpo2Percent();
        if (spo2 < 1 || spo2 > 100) {
            logger.info("Skipping Withings SpO2 vasistas with invalid value: ts={} spo2Tenths={} pulse={} status={}",
                    activityEntry.getTimestamp(),
                    vasistasSpo2.getSpo2DeciPercent(),
                    vasistasSpo2.getPulseRate(),
                    vasistasSpo2.getStatus());
            return;
        }

        final long timestampMs = activityEntry.getTimestamp() * 1000L;
        spo2Entries.add(new long[]{timestampMs, spo2});
        logger.info("Collected Withings SpO2 vasistas sample: ts={} spo2={} pulse={} status={}",
                timestampMs,
                spo2,
                vasistasSpo2.getPulseRate(),
                vasistasSpo2.getStatus());
    }

    private void handleRespiratoryRate(final WithingsStructure data) {
        if (activityEntry == null) {
            logger.info("Received Withings respiratory rate vasistas without timestamp context: {}", GB.hexdump(data.getRawData()));
            return;
        }

        final VasistasRespiratoryRate vasistasRR = (VasistasRespiratoryRate) data;
        final int rate = vasistasRR.getRespiratoryRate();
        if (!vasistasRR.isValid()) {
            logger.info("Skipping Withings respiratory rate vasistas with invalid value: ts={} rate={}",
                    activityEntry.getTimestamp(), rate);
            return;
        }

        final long timestampMs = activityEntry.getTimestamp() * 1000L;
        respiratoryRateEntries.add(new long[]{timestampMs, rate});
        logger.info("Collected Withings respiratory rate vasistas sample: ts={} rate={}",
                timestampMs, rate);
    }

    private void handleAhi(final WithingsStructure data) {
        if (activityEntry == null) {
            logger.info("Received Withings breathing-disturbance vasistas without timestamp context: {}", GB.hexdump(data.getRawData()));
            return;
        }

        final VasistasAhi vasistasAhi = (VasistasAhi) data;
        if (!vasistasAhi.isValid()) {
            logger.info("Skipping Withings breathing-disturbance vasistas with invalid probability: ts={} ahi={} probability={}",
                    activityEntry.getTimestamp(),
                    vasistasAhi.getApneaHypopneaIndex(),
                    vasistasAhi.getBreathingEventProbability());
            return;
        }

        breathingDisturbanceEntries.add(new long[]{
                activityEntry.getTimestamp() * 1000L,
                activityEntry.getDuration(),
                vasistasAhi.getApneaHypopneaIndex(),
                vasistasAhi.getBreathingEventProbability()
        });
    }

    private void handleMovement(WithingsStructure data) {
        if (activityEntry.getRawKind() == ActivityKind.NOT_MEASURED.getCode()) {
            activityEntry.setRawKind(ActivityKind.UNKNOWN.getCode());
        }
        activityEntry.setSteps(((ActivitySampleMovement) data).getSteps());
        activityEntry.setDistance(((ActivitySampleMovement) data).getDistance());
        activityEntry.setHasActivityData(true);
    }

    private void handleWalk(WithingsStructure data) {
        activityEntry.setRawKind(ActivityKind.WALKING.getCode());
        activityEntry.setHasActivityData(true);
    }

    private void handleRun(WithingsStructure data) {
        activityEntry.setRawKind(ActivityKind.RUNNING.getCode());
        activityEntry.setHasActivityData(true);
    }

    private void handleSwim(WithingsStructure data) {
        activityEntry.setRawKind(ActivityKind.SWIMMING.getCode());
        activityEntry.setHasActivityData(true);
    }

    private void handleSleep(WithingsStructure data) {
        final int sleepTypeValue = ((ActivitySampleSleep) data).getSleepType();
        final ActivityKind sleepType = getSleepActivityKind(sleepTypeValue);
        switch (sleepTypeValue) {
            case 0:
                activityEntry.setRawIntensity(0);
                break;
            case 1:
                activityEntry.setRawIntensity(50);
                break;
            case 2:
                activityEntry.setRawIntensity(70);
                break;
            case 3:
                activityEntry.setRawIntensity(80);
                break;
            default:
                activityEntry.setRawIntensity(50);
        }

        activityEntry.setRawKind(sleepType.getCode());
        activityEntry.setHasActivityData(true);
    }

    static ActivityKind getSleepActivityKind(final int sleepType) {
        switch (sleepType) {
            case 0:
                return ActivityKind.AWAKE_SLEEP;
            case 2:
                return ActivityKind.DEEP_SLEEP;
            case 3:
                return ActivityKind.REM_SLEEP;
            case 1:
            default:
                return ActivityKind.LIGHT_SLEEP;
        }
    }

    private void handleCalories1(WithingsStructure data) {
        activityEntry.setRawIntensity(((ActivitySampleCalories) data).getMet());
        activityEntry.setCalories(((ActivitySampleCalories) data).getCalories());
        activityEntry.setHasActivityData(true);
    }

    private void handleCalories2(WithingsStructure data) {
        activityEntry.setRawIntensity(((ActivitySampleCalories2) data).getMet());
        activityEntry.setCalories(((ActivitySampleCalories2) data).getCalories());
        activityEntry.setHasActivityData(true);

    }

    private void logUnhandledActivityData(final WithingsStructure data) {
        if (data instanceof ActivitySampleUnknown) {
            final ActivitySampleUnknown unknown = (ActivitySampleUnknown) data;
            logger.info("Unhandled Withings activity TLV type={} ts={} rawKind={} {} rawHex={}",
                    data.getType(),
                    activityEntry != null ? activityEntry.getTimestamp() : null,
                    activityEntry != null ? activityEntry.getRawKind() : null,
                    unknown.describePayload(),
                    GB.hexdump(unknown.getPayload()));
            return;
        }

        logger.info("Received yet unhandled activity data of type '{}' with data '{}'.", data.getType(), GB.hexdump(data.getRawData()));
    }

    private void addToList(ActivityEntry activityEntry) {
        if (activityEntry.isHeartrate()) {
            heartrateEntries.add(activityEntry);
        } else if (activityEntry.hasActivityData()) {
            activityEntries.add(activityEntry);
        }
    }

    private void saveData() {
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            Long userId = DBHelper.getUser(dbHandler.getDaoSession()).getId();
            Long deviceId = DBHelper.getDevice(device, dbHandler.getDaoSession()).getId();
            AbstractSampleProvider<? extends AbstractWithingsActivitySample> provider =
                    support.createSampleProvider(device, dbHandler.getDaoSession());
            saveActivitySamples(provider, userId, deviceId);

            if (!spo2Entries.isEmpty()) {
                final GenericSpo2SampleProvider spo2Provider = new GenericSpo2SampleProvider(device, dbHandler.getDaoSession());
                final List<GenericSpo2Sample> spo2Samples = new ArrayList<>(spo2Entries.size());
                for (final long[] entry : spo2Entries) {
                    spo2Samples.add(new GenericSpo2Sample(entry[0], deviceId, userId, (int) entry[1]));
                }
                spo2Provider.addSamples(spo2Samples);
                logger.info("Stored {} Withings SpO2 vasistas sample(s)", spo2Samples.size());
            }

            if (!respiratoryRateEntries.isEmpty()) {
                final GenericRespiratoryRateSampleProvider rrProvider = new GenericRespiratoryRateSampleProvider(device, dbHandler.getDaoSession());
                final List<GenericRespiratoryRateSample> rrSamples = new ArrayList<>(respiratoryRateEntries.size());
                for (final long[] entry : respiratoryRateEntries) {
                    final GenericRespiratoryRateSample sample = new GenericRespiratoryRateSample();
                    sample.setTimestamp(entry[0]);
                    sample.setDeviceId(deviceId);
                    sample.setUserId(userId);
                    sample.setRespiratoryRate((float) entry[1]);
                    rrSamples.add(sample);
                }
                rrProvider.addSamples(rrSamples);
                logger.info("Stored {} Withings respiratory rate sample(s)", rrSamples.size());
            }

            if (!breathingDisturbanceEntries.isEmpty()) {
                final List<WithingsBreathingDisturbanceSample> samples = new ArrayList<>(breathingDisturbanceEntries.size());
                for (final long[] entry : breathingDisturbanceEntries) {
                    final WithingsBreathingDisturbanceSample sample = new WithingsBreathingDisturbanceSample();
                    sample.setTimestamp(entry[0]);
                    sample.setDeviceId(deviceId);
                    sample.setUserId(userId);
                    sample.setDuration((int) entry[1]);
                    sample.setApneaHypopneaIndex((int) entry[2]);
                    sample.setBreathingEventProbability((int) entry[3]);
                    samples.add(sample);
                }
                dbHandler.getDaoSession().getWithingsBreathingDisturbanceSampleDao().insertOrReplaceInTx(samples);
                logger.info("Stored {} Withings breathing-disturbance sample(s)", samples.size());
            }
        } catch (Exception ex) {
            logger.warn("Error saving activity data: " + ex.getLocalizedMessage());
        }
    }

    private <T extends AbstractWithingsActivitySample> void saveActivitySamples(
            AbstractSampleProvider<T> provider, long userId, long deviceId) {
        List<T> activitySamples = new ArrayList<>();
        for (ActivityEntry entry : activityEntries) {
            activitySamples.add(convertToSample(provider, entry, userId, deviceId));
        }
        for (ActivityEntry entry : heartrateEntries) {
            activitySamples.add(convertToSample(provider, entry, userId, deviceId));
        }
        provider.addGBActivitySamples(activitySamples);
    }

    private <T extends AbstractWithingsActivitySample> T convertToSample(
            AbstractSampleProvider<T> provider,
            ActivityEntry activityEntry, long userId, long deviceId) {
        T sample = provider.createActivitySample();
        sample.setTimestamp(activityEntry.getTimestamp());
        sample.setDuration(activityEntry.getDuration());
        sample.setHeartRate(activityEntry.getHeartrate());
        sample.setSteps(activityEntry.getSteps());
        sample.setRawKind(activityEntry.getRawKind());
        sample.setCalories(activityEntry.getCalories());
        sample.setDistance(activityEntry.getDistance());
        sample.setRawIntensity(activityEntry.getRawIntensity());
        sample.setDeviceId(deviceId);
        sample.setUserId(userId);
        return sample;
    }

    static List<ActivityEntry> mergeHeartrateSamplesIntoActivitySamples(
            final List<ActivityEntry> activityEntries, final List<ActivityEntry> heartrateEntries) {
        final List<ActivityEntry> retainedHeartrateEntries = new ArrayList<>(heartrateEntries.size());
        for (ActivityEntry heartrateEntry : heartrateEntries) {
            ActivityEntry bestActivityEntry = null;
            for (ActivityEntry activityEntry : activityEntries) {
                if (doActivitiesOverlap(heartrateEntry, activityEntry)
                        && (bestActivityEntry == null || activityEntry.getTimestamp() > bestActivityEntry.getTimestamp())) {
                    bestActivityEntry = activityEntry;
                }
            }

            if (bestActivityEntry == null) {
                retainedHeartrateEntries.add(heartrateEntry);
            } else if (heartrateEntry.getTimestamp() == bestActivityEntry.getTimestamp()) {
                bestActivityEntry.setIsHeartrate(heartrateEntry.getHeartrate());
            } else {
                updateHeartrateEntry(heartrateEntry, bestActivityEntry);
                retainedHeartrateEntries.add(heartrateEntry);
            }
        }
        return retainedHeartrateEntries;
    }

    private static boolean doActivitiesOverlap(ActivityEntry heartrateEntry, ActivityEntry activityEntry) {
        return activityEntry.getTimestamp() <= heartrateEntry.getTimestamp()
                && (activityEntry.getTimestamp() + activityEntry.getDuration()) > heartrateEntry.getTimestamp();
    }

    private static void updateHeartrateEntry(ActivityEntry heartRateEntry, ActivityEntry activityEntry) {
        heartRateEntry.setRawKind(activityEntry.getRawKind());
        heartRateEntry.setRawIntensity(activityEntry.getRawIntensity());
        heartRateEntry.setDuration(activityEntry.getDuration() - (heartRateEntry.getTimestamp() - activityEntry.getTimestamp()));
    }
}
