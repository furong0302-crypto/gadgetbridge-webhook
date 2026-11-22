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

import nodomain.freeyourgadget.gadgetbridge.devices.una.UnaDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.AbstractFitRecord
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.ActivitySummaryUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UnaWorkoutGpxTest : TestBase() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savedWorkoutCanBeSharedAsGpxWithoutChangingOriginalFit() {
        val start = 1793000000L
        val records = (0..1).map { i ->
            AbstractFitRecord.Builder()
                .setTimestamp(start + i * 10)
                .setPositionLat(45.0 + i * 0.001)
                .setPositionLong(-75.0 - i * 0.001)
                .setHeartRate(120 + i)
                .build()
        }
        val bytes = FitFile(records).outgoingMessage
        val summary = BaseActivitySummary()
        UnaWorkoutFile.parse(bytes)!!.saveToSummary(summary, ActivityKind.RUNNING, temporaryFolder.root)
        val device = createDummyGDevice("00:11:22:33:44:55")
        val provider = UnaDeviceCoordinator().getActivityTrackProvider(device, context)

        val gpx = requireNotNull(ActivitySummaryUtils.getShareableGpxFile(provider, device, summary))
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(gpx)
        val points = document.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "trkpt")
        assertEquals(2, points.length)
        assertEquals(45.0, (points.item(0) as Element).getAttribute("lat").toDouble(), 0.000001)
        assertEquals(-75.001, (points.item(1) as Element).getAttribute("lon").toDouble(), 0.000001)
        assertArrayEquals(bytes, File(requireNotNull(summary.rawDetailsPath)).readBytes())
    }
}
