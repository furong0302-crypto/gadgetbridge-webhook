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

public class StoredMeasureMeta extends WithingsStructure {
    private static final Logger logger = LoggerFactory.getLogger(StoredMeasureMeta.class);

    private byte[] rawPayload = new byte[23];
    private int measurementType;
    private int timestampUtc;

    public byte[] getRawPayload() {
        return rawPayload.clone();
    }

    public int getMeasurementType() {
        return measurementType;
    }

    public long getTimestampMs() {
        return (timestampUtc & 0xffffffffL) * 1000L;
    }

    @Override
    public short getLength() {
        return 27;
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.put(rawPayload);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() < 21) {
            logger.debug("StoredMeasureMeta too short: {} bytes", rawDataBuffer.remaining());
            return;
        }

        rawPayload = new byte[rawDataBuffer.remaining()];
        rawDataBuffer.mark();
        rawDataBuffer.get(rawPayload);
        rawDataBuffer.reset();

        rawDataBuffer.getInt(); // sequence index

        final int secondWord = rawDataBuffer.getInt();
        final int highWord = (secondWord >>> 16) & 0xffff;
        final int lowWord = secondWord & 0xffff;
        measurementType = highWord != 0 ? highWord : lowWord;

        // For current ScanWatch payloads, UTC timestamp is carried in the trailing 4 bytes.
        final ByteBuffer tailReader = rawDataBuffer.duplicate();
        tailReader.position(tailReader.limit() - 4);
        timestampUtc = tailReader.getInt();

        logger.debug("Parsed StoredMeasureMeta: measurementType={} timestampUtc={}", measurementType, timestampUtc & 0xffffffffL);
    }

    @Override
    public short getType() {
        return WithingsStructureType.STORED_MEASURE_META;
    }
}
