/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class RespiratoryRateSyncerTest {

    private fun sample(timestampMs: Long, rate: Float): RespiratoryRateSample =
        object : RespiratoryRateSample {
            override fun getTimestamp(): Long = timestampMs
            override fun getRespiratoryRate(): Float = rate
        }

    private fun metadata(): Metadata =
        Metadata.autoRecorded(
            Device(
                manufacturer = "Test",
                model = "TestModel",
                type = Device.TYPE_WATCH,
            )
        )

    // convertSample is `protected` on AbstractTimeSampleSyncer; reach it via reflection
    // so the test does not require widening visibility of production code. The compiled
    // method on the concrete subclass has a specialized first parameter (RespiratoryRateSample),
    // so look it up by name+arity instead of an exact-type lookup.
    private fun convert(sample: RespiratoryRateSample, meta: Metadata): RespiratoryRateRecord? {
        val method = RespiratoryRateSyncer::class.java.declaredMethods
            .first { it.name == "convertSample" && it.parameterCount == 4 && it.returnType == RespiratoryRateRecord::class.java }
        method.isAccessible = true
        return method.invoke(RespiratoryRateSyncer, sample, ZoneOffset.UTC, meta, "test-device") as RespiratoryRateRecord?
    }

    @Test
    fun convertSample_validRate_producesRecord() {
        val ts = 1_700_000_000_000L
        val rate = 16.5f

        val record = convert(sample(ts, rate), metadata())

        assertNotNull(record)
        assertEquals(ts, record!!.time.toEpochMilli())
        assertEquals(rate.toDouble(), record.rate, 0.0)
    }

    @Test
    fun convertSample_zeroRate_isSkipped() {
        val record = convert(sample(1_700_000_000_000L, 0f), metadata())
        assertNull(record)
    }

    @Test
    fun convertSample_negativeRate_isSkipped() {
        val record = convert(sample(1_700_000_000_000L, -1f), metadata())
        assertNull(record)
    }
}
