/*  Copyright (C) 2026 Gadgetbridge contributors

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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Date;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsScanwatchActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class WithingsActivitySummaryParserTest extends TestBase {
    @Test
    public void usesSuppliedDeviceWhenSummaryHasNoDatabaseDevice() {
        final GBDevice gbDevice = new GBDevice("00:11:22:33:44:55", "ScanWatch", null, null, DeviceType.WITHINGS_SCANWATCH);
        final Device dbDevice = DBHelper.getDevice(gbDevice, daoSession);
        final WithingsScanwatchActivitySample sample = new WithingsScanwatchActivitySample();
        sample.setTimestamp(100);
        sample.setDeviceId(dbDevice.getId());
        sample.setUserId(0);
        sample.setCalories(250);
        sample.setSteps(42);
        sample.setDistance(123);
        daoSession.getWithingsScanwatchActivitySampleDao().insert(sample);

        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setStartTime(new Date(100_000L));
        summary.setEndTime(new Date(100_000L));

        new WithingsActivitySummaryParser(gbDevice).parseBinaryData(summary, false);

        final ActivitySummaryData summaryData = ActivitySummaryData.fromJson(summary.getSummaryData());
        assertEquals(2.5d, summaryData.getNumber(ActivitySummaryEntries.CALORIES_BURNT, 0).doubleValue(), 0);
        assertEquals(42d, summaryData.getNumber(ActivitySummaryEntries.STEPS, 0).doubleValue(), 0);
        assertEquals(1.23d, summaryData.getNumber(ActivitySummaryEntries.DISTANCE_METERS, 0).doubleValue(), 0);
    }
}
