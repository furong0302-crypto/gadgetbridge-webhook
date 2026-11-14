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
import java.util.Arrays;

public class RawWithingsStructure extends WithingsStructure {
    private final short type;
    private byte[] payload;

    public RawWithingsStructure(final short type) {
        this(type, new byte[0]);
    }

    public RawWithingsStructure(final short type, final byte[] payload) {
        this.type = type;
        this.payload = payload != null ? payload.clone() : new byte[0];
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public void setPayload(final byte[] payload) {
        this.payload = payload != null ? payload.clone() : new byte[0];
    }

    @Override
    public short getLength() {
        return (short) (HEADER_SIZE + payload.length);
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.put(payload);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        final byte[] rawPayload = new byte[rawDataBuffer.remaining()];
        rawDataBuffer.get(rawPayload);
        this.payload = rawPayload;
    }

    @Override
    public short getType() {
        return type;
    }

    @Override
    public String toString() {
        return "RawWithingsStructure{" +
                "type=" + type +
                ", payload=" + Arrays.toString(payload) +
                '}';
    }
}
