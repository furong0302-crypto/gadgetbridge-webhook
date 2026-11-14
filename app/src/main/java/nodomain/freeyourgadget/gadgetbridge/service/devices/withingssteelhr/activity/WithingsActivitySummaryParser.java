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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractWithingsActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;

/**
 * Withings currently stores live workouts directly as BaseActivitySummary rows without
 * an additional device-specific binary payload. Returning the summary unchanged is enough
 * to make the generic workouts UI load and render those entries safely.
 */
public class WithingsActivitySummaryParser implements ActivitySummaryParser {
    private static final Logger logger = LoggerFactory.getLogger(WithingsActivitySummaryParser.class);
    private final GBDevice gbDevice;

    public WithingsActivitySummaryParser(final GBDevice gbDevice) {
        this.gbDevice = gbDevice;
    }

    @Override
    public BaseActivitySummary parseBinaryData(final BaseActivitySummary summary, final boolean forDetails) {
        if (summary == null || summary.getStartTime() == null || summary.getEndTime() == null) {
            return summary;
        }

        try (DBHandler dbHandler = GBApplication.acquireDbReadOnly()) {
            DaoSession session = dbHandler.getDaoSession();
            SampleProvider provider = gbDevice.getDeviceCoordinator().getSampleProvider(gbDevice, session);
            if (provider != null) {
                int startTs = (int) (summary.getStartTime().getTime() / 1000);
                int endTs = (int) (summary.getEndTime().getTime() / 1000);
                List<? extends ActivitySample> samples = provider.getAllActivitySamples(startTs, endTs);

                int totalCalories = 0;
                int totalSteps = 0;
                int totalDistance = 0;

                if (samples != null) {
                    for (ActivitySample sample : samples) {
                        if (sample instanceof AbstractWithingsActivitySample) {
                            AbstractWithingsActivitySample withingsSample = (AbstractWithingsActivitySample) sample;
                            totalCalories += withingsSample.getCalories();
                            totalSteps += withingsSample.getSteps();
                            totalDistance += withingsSample.getDistance();
                        }
                    }
                }

                if (totalCalories > 0 || totalSteps > 0 || totalDistance > 0) {
                    final ActivitySummaryData summaryData = ActivitySummaryData.fromJson(summary.getSummaryData());
                    final double totalCaloriesKcal = totalCalories * 0.01d;

                    // Withings sample calories are stored in centi-kcal; summing the raw sample values
                    // directly would inflate the workout total by 100x (e.g. 213 -> 2.13 kcal).
                    if (!summaryData.has(ActivitySummaryEntries.CALORIES_BURNT) && totalCaloriesKcal > 0) {
                        summaryData.add(ActivitySummaryEntries.CALORIES_BURNT, totalCaloriesKcal, ActivitySummaryEntries.UNIT_KCAL);
                    }
                    if (!summaryData.has(ActivitySummaryEntries.STEPS) && totalSteps > 0) {
                        summaryData.add(ActivitySummaryEntries.STEPS, totalSteps, ActivitySummaryEntries.UNIT_STEPS);
                    }
                    if (!summaryData.has(ActivitySummaryEntries.DISTANCE_METERS) && totalDistance > 0) {
                        summaryData.add(ActivitySummaryEntries.DISTANCE_METERS, totalDistance * 0.01, ActivitySummaryEntries.UNIT_METERS);
                    }

                    summary.setSummaryData(summaryData.toJson());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to enrich Withings activity summary with sample data", e);
        }

        return summary;
    }
}
