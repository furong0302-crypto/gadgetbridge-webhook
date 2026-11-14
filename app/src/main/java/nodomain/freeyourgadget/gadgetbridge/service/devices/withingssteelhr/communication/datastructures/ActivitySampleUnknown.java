/*  Copyright (C) 2023-2024 Frank Ertl

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
import java.nio.ByteOrder;
import java.util.Arrays;

public class ActivitySampleUnknown extends WithingsStructure {
    private byte[] payload = new byte[0];

    @Override
    public short getLength() {
        return (short) (HEADER_SIZE + payload.length);
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.put(payload);
    }

    @Override
    public short getType() {
        return WithingsStructureType.ACTIVITY_SAMPLE_UNKNOWN;
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        payload = new byte[rawDataBuffer.remaining()];
        rawDataBuffer.get(payload);
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public String describePayload() {
        final StringBuilder builder = new StringBuilder();
        builder.append("payload=").append(Arrays.toString(payload));

        if (payload.length >= 1) {
            builder.append(", u8[0]=").append(payload[0] & 0xff);
        }
        if (payload.length >= 2) {
            builder.append(", u16le[0]=")
                    .append(ByteBuffer.wrap(payload, 0, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff);
        }
        if (payload.length >= 4) {
            builder.append(", u16le[1]=")
                    .append(ByteBuffer.wrap(payload, 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff)
                    .append(", u32le=")
                    .append(ByteBuffer.wrap(payload, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL);
        }

        return builder.toString();
    }
}
