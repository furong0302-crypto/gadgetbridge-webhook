/*  Copyright (C) 2024 Gadgetbridge contributors

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

/**
 * Screen luminosity mode and level.
 *
 * <p>TLV type {@code 0x0937} (2359), 2-byte payload:
 * <ul>
 *   <li>byte 0: mode - {@code 0} = auto, {@code 1} = manual</li>
 *   <li>byte 1: level - {@code 0}-{@code 100} (only meaningful in manual mode)</li>
 * </ul>
 */
public class LuminosityLevel extends WithingsStructure {

    public static final byte MODE_AUTO = 0;
    public static final byte MODE_MANUAL = 1;

    private byte mode;
    private byte level;

    /** No-arg constructor required by {@link DataStructureFactory}. */
    public LuminosityLevel() {}

    public LuminosityLevel(byte mode, byte level) {
        this.mode = mode;
        this.level = level;
    }

    public byte getMode() {
        return mode;
    }

    public byte getLevel() {
        return level;
    }

    public boolean isAutoMode() {
        return mode == MODE_AUTO;
    }

    @Override
    public short getLength() {
        return 6; // 4 header + 2 data
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.put(mode);
        buffer.put(level);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() >= 2) {
            mode = rawDataBuffer.get();
            level = rawDataBuffer.get();
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.LUMINOSITY_LEVEL;
    }
}
