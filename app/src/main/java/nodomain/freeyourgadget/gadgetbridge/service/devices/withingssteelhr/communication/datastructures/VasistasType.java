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

public class VasistasType extends WithingsStructure {
    public static final int TYPE_SPO2 = 8;
    public static final int TYPE_AHI = 9;
    public static final int TYPE_RESPIRATORY_RATE = 12;

    private int value;

    public VasistasType() {
    }

    public VasistasType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public short getLength() {
        return 8;
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.putInt(value);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() >= 4) {
            value = rawDataBuffer.getInt();
        } else if (rawDataBuffer.remaining() >= 2) {
            value = rawDataBuffer.getShort() & 0xFFFF;
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.VASISTAS_TYPE;
    }
}
