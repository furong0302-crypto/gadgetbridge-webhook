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

public class VasistasSpo2 extends WithingsStructure {
    private int spo2DeciPercent;
    private int pulseRate;
    private int status;

    public int getSpo2DeciPercent() {
        return spo2DeciPercent;
    }

    public int getPulseRate() {
        return pulseRate;
    }

    public int getStatus() {
        return status;
    }

    public int getSpo2Percent() {
        if (spo2DeciPercent <= 0) {
            return 0;
        }

        return Math.round(spo2DeciPercent / 10f);
    }

    @Override
    public short getLength() {
        return 16;
    }

    @Override
    protected void fillinTypeSpecificData(final ByteBuffer buffer) {
        buffer.putInt(spo2DeciPercent);
        buffer.putInt(pulseRate);
        buffer.putInt(status);
    }

    @Override
    protected void fillFromRawDataAsBuffer(final ByteBuffer rawDataBuffer) {
        if (rawDataBuffer.remaining() >= 12) {
            spo2DeciPercent = rawDataBuffer.getInt();
            pulseRate = rawDataBuffer.getInt();
            status = rawDataBuffer.getInt();
        } else if (rawDataBuffer.remaining() >= 6) {
            spo2DeciPercent = rawDataBuffer.getShort() & 0xFFFF;
            pulseRate = rawDataBuffer.getShort() & 0xFFFF;
            status = rawDataBuffer.getShort() & 0xFFFF;
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.VASISTAS_SPO2;
    }
}
