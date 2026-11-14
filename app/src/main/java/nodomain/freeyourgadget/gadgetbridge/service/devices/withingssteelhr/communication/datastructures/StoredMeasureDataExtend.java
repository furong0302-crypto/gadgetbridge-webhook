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

import java.nio.ByteBuffer;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoredMeasureDataExtend extends WithingsStructure {
    private static final Logger logger = LoggerFactory.getLogger(StoredMeasureDataExtend.class);

    private byte[] rawPayload = new byte[0];
    private int measurementType = -1;
    private long extraData = 0; // Using long for 4-byte unsigned int

    public byte[] getRawPayload() {
        return rawPayload.clone();
    }

    public int getMeasurementType() {
        return measurementType;
    }

    public long getExtraData() {
        return extraData;
    }

    @Override
    public short getLength() {
        return (short) (HEADER_SIZE + rawPayload.length);
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.put(rawPayload);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        rawPayload = new byte[rawDataBuffer.remaining()];
        rawDataBuffer.get(rawPayload);

        if (rawPayload.length >= 8) {
            ByteBuffer parseBuffer = ByteBuffer.wrap(rawPayload);
            measurementType = parseBuffer.getInt();
            extraData = parseBuffer.getInt() & 0xffffffffL;
        }

        logger.debug("Parsed StoredMeasureDataExtend: type={} extraData={} rawPayload={}",
                measurementType, extraData, GB.hexdump(rawPayload));
    }

    @Override
    public short getType() {
        return WithingsStructureType.STORED_MEASURE_DATA_EXTEND;
    }
}
