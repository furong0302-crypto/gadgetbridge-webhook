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

public class StoredSignalMetaExtended extends WithingsStructure {
    private byte[] rawPayload = new byte[0];

    public byte[] getRawPayload() {
        return rawPayload.clone();
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
    }

    @Override
    public short getType() {
        return WithingsStructureType.STORED_SIGNAL_META_EXTEND;
    }
}
