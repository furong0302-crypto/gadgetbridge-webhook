/*  Copyright (C) 2026 d3vv3

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity.ActivityEntry;

public class ActivitySampleHandlerTest {
    @Test
    public void mapsAllSleepStages() {
        assertEquals(ActivityKind.AWAKE_SLEEP, ActivitySampleHandler.getSleepActivityKind(0));
        assertEquals(ActivityKind.LIGHT_SLEEP, ActivitySampleHandler.getSleepActivityKind(1));
        assertEquals(ActivityKind.DEEP_SLEEP, ActivitySampleHandler.getSleepActivityKind(2));
        assertEquals(ActivityKind.REM_SLEEP, ActivitySampleHandler.getSleepActivityKind(3));
    }

    @Test
    public void mergesEqualTimestampHeartRateIntoAuthoritativeSleep() {
        final ActivityEntry sleep = entry(100, 60, ActivityKind.DEEP_SLEEP);
        final ActivityEntry heartRate = heartRate(100, 55);

        final List<ActivityEntry> retained = ActivitySampleHandler.mergeHeartrateSamplesIntoActivitySamples(
                Collections.singletonList(sleep), Collections.singletonList(heartRate));

        assertTrue(retained.isEmpty());
        assertEquals(55, sleep.getHeartrate());
        assertEquals(ActivityKind.DEEP_SLEEP.getCode(), sleep.getRawKind());
    }

    @Test
    public void usesHalfOpenIntervalsAndLatestOverlappingStage() {
        final ActivityEntry first = entry(100, 60, ActivityKind.LIGHT_SLEEP);
        final ActivityEntry second = entry(130, 60, ActivityKind.REM_SLEEP);
        final ActivityEntry overlap = heartRate(140, 60);
        final ActivityEntry boundary = heartRate(190, 61);

        final List<ActivityEntry> retained = ActivitySampleHandler.mergeHeartrateSamplesIntoActivitySamples(
                Arrays.asList(first, second), Arrays.asList(overlap, boundary));

        assertEquals(2, retained.size());
        assertEquals(ActivityKind.REM_SLEEP.getCode(), overlap.getRawKind());
        assertEquals(ActivityKind.NOT_MEASURED.getCode(), boundary.getRawKind());
    }

    private static ActivityEntry entry(final int timestamp, final int duration, final ActivityKind kind) {
        final ActivityEntry entry = new ActivityEntry();
        entry.setTimestamp(timestamp);
        entry.setDuration(duration);
        entry.setRawKind(kind.getCode());
        entry.setHasActivityData(true);
        return entry;
    }

    private static ActivityEntry heartRate(final int timestamp, final int value) {
        final ActivityEntry entry = new ActivityEntry();
        entry.setTimestamp(timestamp);
        entry.setIsHeartrate(true);
        entry.setIsHeartrate(value);
        return entry;
    }
}
