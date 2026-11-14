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

/**
 * Vasistas respiratory rate data structure (TLV type 0x09D9).
 *
 * Wire format: a 32-bit respiratory rate in breaths per minute.
 */
public class VasistasRespiratoryRate extends WithingsStructure {
    private int respiratoryRate;

    /**
     * Returns the respiratory rate in breaths per minute.
     * Values >= 1000 indicate invalid/no-data and should be filtered.
     */
    public int getRespiratoryRate() {
        return respiratoryRate;
    }

    /**
     * Returns true if this sample contains a valid respiratory rate measurement.
     */
    public boolean isValid() {
        return respiratoryRate > 0 && respiratoryRate < 200;
    }

    @Override
    public short getLength() {
        return 8;
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.putInt(respiratoryRate);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() >= 4) {
            respiratoryRate = rawDataBuffer.getInt();
        } else if (rawDataBuffer.remaining() >= 2) {
            respiratoryRate = rawDataBuffer.getShort() & 0xFFFF;
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.VASISTAS_RESPIRATORY_RATE;
    }
}
