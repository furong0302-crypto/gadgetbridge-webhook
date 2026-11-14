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
 * Tracker move-hands status (move hands to 10:10 when the screen turns on).
 *
 * <p>TLV type {@code 0x09BB} (2491), 1-byte payload:
 * <ul>
 *   <li>{@code 0} = disabled</li>
 *   <li>{@code 1} = enabled</li>
 * </ul>
 */
public class TrackerMoveHands extends WithingsStructure {

    private boolean enabled;

    /** No-arg constructor required by {@link DataStructureFactory}. */
    public TrackerMoveHands() {}

    public TrackerMoveHands(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public short getLength() {
        return 5; // 4 header + 1 data
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.put(enabled ? (byte) 1 : (byte) 0);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() >= 1) {
            enabled = rawDataBuffer.get() != 0;
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.TRACKER_MOVE_HANDS;
    }
}
