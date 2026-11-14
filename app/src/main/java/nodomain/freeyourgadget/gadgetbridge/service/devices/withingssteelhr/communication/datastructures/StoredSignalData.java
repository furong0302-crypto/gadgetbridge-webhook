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

public class StoredSignalData extends WithingsStructure {
    private byte[] samples = new byte[0];

    public byte[] getSamples() {
        return samples.clone();
    }

    public int getSampleCount() {
        return samples.length == 0 ? 0 : (samples[0] & 0xff);
    }

    public byte[] getSampleBytes() {
        if (samples.length <= 1) {
            return new byte[0];
        }
        final byte[] values = new byte[samples.length - 1];
        System.arraycopy(samples, 1, values, 0, values.length);
        return values;
    }

    @Override
    public short getLength() {
        return (short) (HEADER_SIZE + samples.length);
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.put(samples);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        samples = new byte[rawDataBuffer.remaining()];
        rawDataBuffer.get(samples);
    }

    @Override
    public short getType() {
        return WithingsStructureType.STORED_SIGNAL_DATA;
    }
}
