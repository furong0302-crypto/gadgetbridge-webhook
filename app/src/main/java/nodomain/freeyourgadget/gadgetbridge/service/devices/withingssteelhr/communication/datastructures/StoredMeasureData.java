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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class StoredMeasureData extends WithingsStructure {
    private static final Logger logger = LoggerFactory.getLogger(StoredMeasureData.class);
    private static final int TYPE_SPO2 = 54;

    private int spo2Percent;
    private int rawValue;
    private int measurementType = -1;
    private short exponent = 0;

    public int getSpo2Times10() {
        return spo2Percent * 10;
    }

    public int getSpo2Percent() {
        return spo2Percent;
    }

    public int getMeasurementType() {
        return measurementType;
    }

    public int getRawValue() {
        return rawValue;
    }

    public short getExponent() {
        return exponent;
    }

    @Override
    public short getLength() {
        return 12;
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.putInt(spo2Percent);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() < 4) {
            logger.debug("StoredMeasureData too short: {} bytes", rawDataBuffer.remaining());
            return;
        }

        rawValue = rawDataBuffer.getInt();
        final int lowBytePercent = rawValue & 0xff;

        if (rawDataBuffer.remaining() >= 4) {
            measurementType = rawDataBuffer.getShort() & 0xffff;
            exponent = rawDataBuffer.getShort();

            final int scaledPercent = (int) Math.round(rawValue * Math.pow(10d, exponent));

            // Preferred path: explicit SpO2 type from captures/spec.
            // Current Withings mixed ECG/SpO2 captures also contain measurement type 11 with
            // values like 65/66; those line up with pulse / heart rate rather than SpO2 and are
            // handled in StoredMeasureSignalHandler instead of being mapped to spo2Percent here.
            if (measurementType == TYPE_SPO2 && scaledPercent >= 1 && scaledPercent <= 100) {
                spo2Percent = scaledPercent;
            } else {
                spo2Percent = -1;
            }
        } else {
            // Legacy fallback for short payload variants.
            spo2Percent = (lowBytePercent >= 1 && lowBytePercent <= 100) ? lowBytePercent : -1;
        }

        logger.debug("Parsed StoredMeasureData: type={} exponent={} rawValue={} spo2Percent={}", measurementType, exponent, rawValue, getSpo2Percent());
    }

    @Override
    public short getType() {
        return WithingsStructureType.STORED_MEASURE_DATA;
    }
}
