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
 * Encodes the long-press crown shortcut action (TLV type 0x09A1).
 *
 * <p>Wire format: 2-byte type (0x09A1) + 2-byte length (0x0001) + 1-byte action value.
 *
 * <p>Known action values:
 * <pre>
 *   0  = NONE
 *   1  = ECG_MEAS
 *   2  = SPO2_MEAS
 *   3  = WORKOUT_START
 *   4  = WORKOUT_SELECTION
 *   5  = BREATH
 *   6  = STOPWATCH
 *   7  = TIMER
 *   8  = DND
 *   9  = QUICKLOOK
 *   10 = FINDMYPHONE
 *   11 = FLASHLIGHT
 * </pre>
 */
public class ShortcutAction extends WithingsStructure {

    // Action constants matching the Withings WPP protocol
    public static final byte ACTION_NONE              = 0;
    // TODO: ACTION_ECG_MEAS and ACTION_SPO2_MEAS require the user to have accepted the health
    //       terms & conditions inside the official Withings app before the watch will honour
    //       them via BLE. Sending these values without prior T&C acceptance has no effect.
    public static final byte ACTION_ECG_MEAS          = 1;
    public static final byte ACTION_SPO2_MEAS         = 2;
    public static final byte ACTION_WORKOUT_START     = 3;
    public static final byte ACTION_WORKOUT_SELECTION = 4;
    public static final byte ACTION_BREATH            = 5;
    public static final byte ACTION_STOPWATCH         = 6;
    public static final byte ACTION_TIMER             = 7;
    public static final byte ACTION_DND               = 8;
    public static final byte ACTION_QUICKLOOK         = 9;
    public static final byte ACTION_FINDMYPHONE       = 10;
    public static final byte ACTION_FLASHLIGHT        = 11;

    private byte action;

    /** No-arg constructor required by {@link DataStructureFactory}. */
    public ShortcutAction() {}

    public ShortcutAction(byte action) {
        this.action = action;
    }

    /** @return the action byte received from the watch */
    public byte getAction() {
        return action;
    }

    @Override
    public short getLength() {
        // 4-byte TLV header + 1 byte payload
        return 5;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.put(action);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer buffer) {
        if (buffer.remaining() >= 1) {
            action = buffer.get();
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.SHORTCUT_ACTION;
    }
}
