/*  Copyright (C) 2026 Toby Murray

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.una

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitRecord
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date

/** A workout and its original FIT payload, retained for track loading and lossless sharing. */
internal class UnaWorkoutFile private constructor(
    private val bytes: ByteArray,
    private val records: List<FitRecord>,
    val startTimestamp: Long,
    private val endTimestamp: Long,
) {
    fun saveToSummary(summary: BaseActivitySummary, kind: ActivityKind, directory: File) {
        // The directory is device-specific. Use the same timestamp as the summary identity,
        // rather than trusting a path supplied by the watch or colliding across watch apps.
        val file = File(directory, "una-workout-$startTimestamp.fit")
        val data = ActivitySummaryData()
        val heartRates = records.mapNotNull { it.heartRate }.filter { it > 0 }
        if (heartRates.isNotEmpty()) {
            data.add(ActivitySummaryEntries.HR_AVG, heartRates.average(), ActivitySummaryEntries.UNIT_BPM)
        }
        records.mapNotNull { it.distance }.maxOrNull()?.let {
            data.add(ActivitySummaryEntries.DISTANCE_METERS, it, ActivitySummaryEntries.UNIT_METERS)
        }
        data.setHasGps(records.any { it.positionLat != null && it.positionLong != null })
        val summaryJson = data.toJson()

        // Publish only a complete file. In particular, a failed retry must not truncate a
        // previous download that the database already references.
        val temporary = File.createTempFile(file.name, ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!temporary.renameTo(file)) {
                throw IOException("Could not replace workout FIT file: $file")
            }
        } finally {
            temporary.delete()
        }

        summary.startTime = Date(startTimestamp * 1000L)
        summary.endTime = Date(endTimestamp * 1000L)
        summary.activityKind = kind.code
        summary.rawDetailsPath = file.absolutePath
        summary.summaryData = summaryJson
    }

    companion object {
        fun parse(bytes: ByteArray): UnaWorkoutFile? {
            val records = FitFile.parseIncoming(bytes).records.filterIsInstance<FitRecord>()
            val timestamps = records.mapNotNull { it.computedTimestamp }
            val start = timestamps.minOrNull() ?: return null
            return UnaWorkoutFile(bytes, records, start, timestamps.maxOrNull()!!)
        }
    }
}
