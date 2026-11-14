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
 * Tracker wear position (which wrist the watch is worn on).
 *
 * <p>TLV type {@code 0x012F} (303), 1-byte payload:
 * <ul>
 *   <li>{@code 0} = not set</li>
 *   <li>{@code 1} = hip</li>
 *   <li>{@code 2} = left wrist</li>
 *   <li>{@code 3} = right wrist</li>
 * </ul>
 */
public class TrackerWearPos extends WithingsStructure {

    public static final byte POS_LEFT_WRIST = 2;
    public static final byte POS_RIGHT_WRIST = 3;

    private byte position;

    /** No-arg constructor required by {@link DataStructureFactory}. */
    public TrackerWearPos() {}

    public TrackerWearPos(byte position) {
        this.position = position;
    }

    public byte getPosition() {
        return position;
    }

    public void setPosition(byte position) {
        this.position = position;
    }

    /**
     * Returns {@code true} if the position is left wrist.
     */
    public boolean isLeftWrist() {
        return position == POS_LEFT_WRIST;
    }

    @Override
    public short getLength() {
        return 5; // 4 header + 1 data
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.put(position);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() >= 1) {
            position = rawDataBuffer.get();
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.TRACKER_WEAR_POS;
    }
}
