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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractWithingsActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;

/**
 * This class is needed for sleep tracking as the Withings Steel HR sends heartrate while sleeping
 * in an extra activity. This leads to breaking the sleep session in the sleep calculation of GB.
 */
public class SleepActivitySampleHelper {
    public static <T extends AbstractWithingsActivitySample> T mergeIfNecessary(
            AbstractSampleProvider<T> provider, T sample) {
        if (!shouldMerge(sample)) {
            return sample;
        }

        T overlappingSample = getOverlappingSample(provider, (int) sample.getTimestamp());
        if (overlappingSample != null) {
            sample = doMerge(provider, overlappingSample, sample);
        }

        return sample;
    }

    private static <T extends AbstractWithingsActivitySample> T getOverlappingSample(
            AbstractSampleProvider<T> provider, long timestamp) {
        List<T> samples = provider.getActivitySamples((int) timestamp - 500, (int) timestamp);
        if (samples.isEmpty()) {
            return null;
        }

        for (int i = samples.size() - 1; i >= 0; i--) {
            T lastSample = samples.get(i);
            if (isNotHeartRateOnly(lastSample)) {
                return lastSample;
            }
        }

        return null;
    }

    private static boolean isNotHeartRateOnly(AbstractWithingsActivitySample lastSample) {
        return lastSample.getRawKind() != ActivityKind.NOT_MEASURED.getCode();
    }

    private static boolean shouldMerge(AbstractWithingsActivitySample sample) {
        return sample.getSteps() == 0
                && sample.getDistance() == 0
                && sample.getRawKind() == -1
                && sample.getCalories() == 0
                && sample.getHeartRate() > 1
                && sample.getRawIntensity() == 0;
    }

    private static <T extends AbstractWithingsActivitySample> T doMerge(
            AbstractSampleProvider<T> provider, T origin, T update) {
        T mergeResult = provider.createActivitySample();
        mergeResult.setTimestamp(update.getTimestamp());
        mergeResult.setRawKind(origin.getRawKind());
        mergeResult.setRawIntensity(origin.getRawIntensity());
        mergeResult.setDuration(origin.getDuration() - (update.getTimestamp() - origin.getTimestamp()));
        mergeResult.setDeviceId(origin.getDeviceId());
        mergeResult.setUserId(origin.getUserId());
        mergeResult.setProvider(origin.getProvider());
        mergeResult.setHeartRate(update.getHeartRate());
        return mergeResult;
    }
}
