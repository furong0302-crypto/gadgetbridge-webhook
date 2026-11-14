/*  Copyright (C) 2026 d3vv3

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

/** Minute-level sleep breathing-disturbance data (TLV type 0x09A0). */
public class VasistasAhi extends WithingsStructure {
    private int apneaHypopneaIndex;
    private int breathingEventProbability;

    public int getApneaHypopneaIndex() {
        return apneaHypopneaIndex;
    }

    public int getBreathingEventProbability() {
        return breathingEventProbability;
    }

    public boolean isValid() {
        return breathingEventProbability >= 0 && breathingEventProbability <= 127;
    }

    @Override
    public short getLength() {
        return 8;
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.putShort((short) apneaHypopneaIndex);
        buffer.putShort((short) breathingEventProbability);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() >= 4) {
            apneaHypopneaIndex = rawDataBuffer.getShort();
            breathingEventProbability = rawDataBuffer.getShort();
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.VASISTAS_AHI;
    }
}
