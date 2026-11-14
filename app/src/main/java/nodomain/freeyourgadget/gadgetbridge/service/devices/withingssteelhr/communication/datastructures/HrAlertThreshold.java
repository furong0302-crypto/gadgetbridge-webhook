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
 * Encodes one heart-rate alert threshold entry (TLV type {@code 0x09A5}) used in
 * {@code CMD_SET_HR_ALERT_THRESHOLDS} (0x098e) messages.
 *
 * <p>Wire format: 2-byte type + 2-byte length (8) + 8-byte payload:
 * <pre>
 *   pad        (uint8)   - always 0x00
 *   direction  (uint8)   - 0x01 = HIGH resting HR, 0x02 = LOW resting HR
 *   enabled    (uint8)   - 0x01 = alert enabled, 0x00 = alert disabled
 *   pad        (uint32)  - always 0x00000000
 *   threshold  (uint8)   - BPM value that triggers the alert
 * </pre>
 *
 * <p>Two entries are always sent together - one for {@link #DIRECTION_HIGH} and one for
 * {@link #DIRECTION_LOW} - preceded by a {@link FeatureTagsUserId} header and followed by
 * {@link EndOfTransmission}.
 */
public class HrAlertThreshold extends WithingsStructure {

    // ---- Direction constants ----

    /** High resting heart rate alert direction. */
    public static final byte DIRECTION_HIGH = 0x01;
    /** Low resting heart rate alert direction. */
    public static final byte DIRECTION_LOW  = 0x02;

    // ---- Enabled/disabled ----

    public static final byte ENABLED  = 0x01;
    public static final byte DISABLED = 0x00;

    // ---- Payload ----

    private byte direction;
    private byte enabled;
    private byte thresholdBpm;

    /** No-arg constructor required by {@link DataStructureFactory}. */
    public HrAlertThreshold() {}

    /**
     * @param direction    {@link #DIRECTION_HIGH} or {@link #DIRECTION_LOW}
     * @param enabled      {@link #ENABLED} or {@link #DISABLED}
     * @param thresholdBpm BPM value at which the alert fires
     */
    public HrAlertThreshold(byte direction, byte enabled, byte thresholdBpm) {
        this.direction    = direction;
        this.enabled      = enabled;
        this.thresholdBpm = thresholdBpm;
    }

    public byte getDirection()    { return direction; }
    public byte getEnabled()      { return enabled; }
    public byte getThresholdBpm() { return thresholdBpm; }

    @Override
    public short getLength() {
        // 4-byte TLV header + 8-byte payload
        return 12;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.put((byte) 0x00);     // pad
        buffer.put(direction);
        buffer.put(enabled);
        buffer.putInt(0x00000000);   // pad (4 bytes)
        buffer.put(thresholdBpm);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer buffer) {
        if (buffer.remaining() >= 8) {
            buffer.get();              // skip pad
            direction    = buffer.get();
            enabled      = buffer.get();
            buffer.getInt();           // skip pad
            thresholdBpm = buffer.get();
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.HR_ALERT_THRESHOLD;
    }
}
