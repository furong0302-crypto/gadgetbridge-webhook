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

public class StoredSignalMeta extends WithingsStructure {
    private static final Logger logger = LoggerFactory.getLogger(StoredSignalMeta.class);

    private int signalType;
    private int signalFlags;
    private int cursor;

    public StoredSignalMeta() {
    }

    public StoredSignalMeta(final int signalType, final int cursor) {
        this.signalType = signalType;
        this.signalFlags = 0;
        this.cursor = cursor;
    }

    public StoredSignalMeta(final int signalType, final int signalFlags, final int cursor) {
        this.signalType = signalType;
        this.signalFlags = signalFlags;
        this.cursor = cursor;
    }

    public int getSignalType() {
        return signalType;
    }

    public int getCursor() {
        return cursor;
    }

    public int getSignalFlags() {
        return signalFlags;
    }

    @Override
    public short getLength() {
        return 12;
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        // Stored-signal meta uses 2-byte fields for signal type/flags, followed by 4-byte cursor.
        buffer.putShort((short) signalType);
        buffer.putShort((short) signalFlags);
        buffer.putInt(cursor);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() < 8) {
            logger.debug("StoredSignalMeta too short: {} bytes", rawDataBuffer.remaining());
            return;
        }

        signalType = rawDataBuffer.getShort() & 0xffff;
        signalFlags = rawDataBuffer.getShort() & 0xffff;
        cursor = rawDataBuffer.getInt();

        logger.debug("Parsed StoredSignalMeta: signalType={} signalFlags={} cursor={}", signalType, signalFlags, cursor);
    }

    @Override
    public short getType() {
        return WithingsStructureType.STORED_SIGNAL_META;
    }
}
