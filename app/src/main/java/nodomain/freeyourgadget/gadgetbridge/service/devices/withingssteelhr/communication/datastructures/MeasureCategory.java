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

public class MeasureCategory extends WithingsStructure {
    public static final short NO_MEASURE = 0;
    public static final short ECG = 1;
    public static final short PPG = 2;

    private short value;

    public MeasureCategory() {
    }

    public MeasureCategory(final int value) {
        this.value = (short) value;
    }

    public short getValue() {
        return value;
    }

    @Override
    public short getLength() {
        return 6;
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.putShort(value);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() >= 2) {
            value = rawDataBuffer.getShort();
        } else if (rawDataBuffer.remaining() >= 1) {
            value = (short) (rawDataBuffer.get() & 0xff);
        }
    }

    @Override
    public short getType() {
        return (short) 0x097b;
    }
}
