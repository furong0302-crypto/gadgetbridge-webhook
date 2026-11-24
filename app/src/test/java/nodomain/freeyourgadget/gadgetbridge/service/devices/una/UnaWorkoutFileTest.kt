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

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.devices.MockCoordinatorDependencies
import nodomain.freeyourgadget.gadgetbridge.devices.una.UnaDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.export.FitExporter
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.AbstractFitRecord
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RecordData
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File
import java.io.IOException

class UnaWorkoutFileTest {
    @get:Rule
    val coordinatorDependencies = MockCoordinatorDependencies()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val start = 1793000000L

    @Test
    fun gpsWorkoutRetainsOriginalFitAndLoadsTrackThroughCoordinator() {
        val bytes = fit(record(start, 45.0, -75.0), record(start + 10, 45.001, -75.001))
        val summary = save(bytes, ActivityKind.RUNNING)

        assertArrayEquals(bytes, requireNotNull(FitExporter.resolveRawFitFile(summary)).readBytes())
        assertEquals(start * 1000, summary.startTime.time)
        assertEquals((start + 10) * 1000, summary.endTime.time)
        assertEquals(ActivityKind.RUNNING.code, summary.activityKind)
        val data = ActivitySummaryData.fromJson(summary.summaryData)
        assertTrue(data.hasGps())
        assertEquals(120.0, data.getNumber(ActivitySummaryEntries.HR_AVG, -1).toDouble(), 0.01)
        assertEquals(123.0, data.getNumber(ActivitySummaryEntries.DISTANCE_METERS, -1).toDouble(), 0.01)

        val provider = UnaDeviceCoordinator().getActivityTrackProvider(mock(GBDevice::class.java), mock(Context::class.java))
        val points = provider.getActivityTrack(summary)!!.segments.flatten()
        assertEquals(2, points.size)
        assertEquals(45.0, points.first().location!!.latitude, 0.000001)
        assertEquals(-75.001, points.last().location!!.longitude, 0.000001)
        assertEquals(120, points.first().heartRate)
    }

    @Test
    fun indoorWorkoutRetainsFitWithoutAdvertisingGps() {
        val bytes = fit(record(start), record(start + 10))
        val summary = save(bytes, ActivityKind.TREADMILL)
        assertFalse(ActivitySummaryData.fromJson(summary.summaryData).hasGps())
        assertArrayEquals(bytes, File(requireNotNull(summary.rawDetailsPath)).readBytes())
    }

    @Test
    fun incompleteCoordinatesDoNotAdvertiseGps() {
        for (point in listOf(record(start, 45.0), record(start, longitude = -75.0))) {
            val summary = save(fit(point), ActivityKind.RUNNING)
            assertFalse(ActivitySummaryData.fromJson(summary.summaryData).hasGps())
        }
    }

    @Test
    fun zeroCoordinatesAreValid() {
        val summary = save(fit(record(start, 0.0, 0.0)), ActivityKind.RUNNING)
        assertTrue(ActivitySummaryData.fromJson(summary.summaryData).hasGps())
    }

    @Test
    fun failedFileWriteDoesNotPublishSummary() {
        val workout = UnaWorkoutFile.parse(fit(record(start)))!!
        val summary = BaseActivitySummary()
        val notADirectory = temporaryFolder.newFile()
        assertThrows(IOException::class.java) {
            workout.saveToSummary(summary, ActivityKind.RUNNING, notADirectory)
        }
        assertNull(summary.rawDetailsPath)
        assertNull(summary.summaryData)
        // The same payload can be retried after the storage problem is resolved.
        workout.saveToSummary(summary, ActivityKind.RUNNING, temporaryFolder.root)
        assertTrue(File(requireNotNull(summary.rawDetailsPath)).isFile)
    }

    @Test
    fun retryReplacesOriginalFitAndPreservesUserMetadata() {
        val initial = fit(record(start, 45.0, -75.0))
        val summary = save(initial, ActivityKind.RUNNING)
        val path = summary.rawDetailsPath
        summary.name = "Morning run"
        val updated = fit(record(start, 45.0, -75.0), record(start + 10, 45.001, -75.001))
        UnaWorkoutFile.parse(updated)!!.saveToSummary(summary, ActivityKind.RUNNING, temporaryFolder.root)
        assertEquals(path, summary.rawDetailsPath)
        assertEquals("Morning run", summary.name)
        assertArrayEquals(updated, File(requireNotNull(path)).readBytes())
        assertEquals(listOf(File(path).name), temporaryFolder.root.list()!!.toList())
    }

    @Test
    fun failedReplacementDoesNotPublishSummaryAndCleansStagingFile() {
        val bytes = fit(record(start, 45.0, -75.0))
        val summary = BaseActivitySummary()
        val destination = temporaryFolder.newFolder("una-workout-$start.fit")
        val existing = File(destination, "keep").apply { writeText("existing") }
        assertThrows(IOException::class.java) {
            UnaWorkoutFile.parse(bytes)!!.saveToSummary(summary, ActivityKind.RUNNING, temporaryFolder.root)
        }
        assertNull(summary.rawDetailsPath)
        assertNull(summary.summaryData)
        assertEquals("existing", existing.readText())
        assertEquals(listOf(destination.name), temporaryFolder.root.list()!!.toList())
    }

    @Test
    fun emptyFitDoesNotCreateWorkout() {
        assertNull(UnaWorkoutFile.parse(fit()))
    }

    private fun save(bytes: ByteArray, kind: ActivityKind): BaseActivitySummary =
        BaseActivitySummary().also {
            UnaWorkoutFile.parse(bytes)!!.saveToSummary(it, kind, temporaryFolder.root)
        }

    private fun fit(vararg records: RecordData): ByteArray = FitFile(records.toList()).outgoingMessage

    private fun record(timestamp: Long, latitude: Double? = null, longitude: Double? = null): RecordData =
        AbstractFitRecord.Builder()
            .setTimestamp(timestamp)
            .setPositionLat(latitude)
            .setPositionLong(longitude)
            .setHeartRate(120)
            .setDistance(123.0)
            .build()
}
