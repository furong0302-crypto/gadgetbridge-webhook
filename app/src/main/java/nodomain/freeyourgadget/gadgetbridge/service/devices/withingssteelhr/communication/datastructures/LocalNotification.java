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
 * Encodes one on-device notification slot configuration (TLV type {@code 0x09A8}).
 *
 * <p>Wire format: 2-byte type + 2-byte length (5) + 5-byte payload:
 * <pre>
 *   pad        (uint16 BE)  - always 0x0000
 *   pad        (uint8)      - always 0x00
 *   notifId    (uint8)      - notification slot identifier (see {@code NOTIF_*} constants)
 *   status     (uint8)      - 0x00 = disabled, 0x01 = enabled
 * </pre>
 *
 * <p>Known notification slot IDs observed from HCI captures:
 * <ul>
 *   <li>{@link #NOTIF_PPG_AFIB}       (1) - AFib daytime detection alert</li>
 *   <li>{@link #NOTIF_HIGH_HR}        (2) - High heart rate alert</li>
 *   <li>{@link #NOTIF_LOW_HR}         (3) - Low heart rate alert</li>
 *   <li>{@link #NOTIF_SLOT_4}         (4) - Purpose unknown (seen disabled unless ECG active)</li>
 *   <li>{@link #NOTIF_PPG_AFIB_NIGHT} (5) - Night-time AFib detection alert</li>
 * </ul>
 *
 * <p>The full 5-slot configuration must always be sent together when using
 * {@code CMD_LOCAL_NOTIFICATIONS_CONFIG_SET} (0x0990).
 */
public class LocalNotification extends WithingsStructure {

    // ---- Notification slot identifiers ----

    /** AFib daytime detection alert. */
    public static final byte NOTIF_PPG_AFIB       = 1;
    /** High heart rate alert. */
    public static final byte NOTIF_HIGH_HR        = 2;
    /** Low heart rate alert. */
    public static final byte NOTIF_LOW_HR         = 3;
    /** Purpose unknown (observed disabled unless ECG active). */
    public static final byte NOTIF_SLOT_4         = 4;
    /** Night-time AFib detection alert. */
    public static final byte NOTIF_PPG_AFIB_NIGHT = 5;

    // ---- Status values ----

    public static final byte STATUS_DISABLED = 0x00;
    public static final byte STATUS_ENABLED  = 0x01;

    // ---- Payload ----

    private byte notifId;
    private byte status;

    /** No-arg constructor required by {@link DataStructureFactory}. */
    public LocalNotification() {}

    /**
     * @param notifId one of the {@code NOTIF_*} constants
     * @param status  {@link #STATUS_ENABLED} or {@link #STATUS_DISABLED}
     */
    public LocalNotification(byte notifId, byte status) {
        this.notifId = notifId;
        this.status  = status;
    }

    public byte getNotifId() { return notifId; }
    public byte getStatus()  { return status; }

    @Override
    public short getLength() {
        // 4-byte TLV header + 5-byte payload
        return 9;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.putShort((short) 0x0000);  // pad
        buffer.put((byte) 0x00);          // pad
        buffer.put(notifId);
        buffer.put(status);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer buffer) {
        if (buffer.remaining() >= 5) {
            buffer.getShort(); // skip pad
            buffer.get();      // skip pad
            notifId = buffer.get();
            status  = buffer.get();
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.LOCAL_NOTIFICATION;
    }
}
