/*  Copyright (C) 2023-2024 Frank Ertl

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
 * Represents a single screen entry in the SET_SCREEN_LIST (0x050C) command.
 *
 * <p>Wire format: TLV type 0x0516, 22 bytes total (4-byte TLV header + 18-byte payload).
 * Payload layout (all multi-byte fields are big-endian):
 * <pre>
 *   Offset  Size  Field
 *   0       4     id          - screen identifier (e.g. 0x84 = Date)
 *   4       4     userId      - Withings account user ID (must be non-zero or the watch reboots)
 *   8       4     reserved1   - always 0x00000000 in all observed captures
 *   12      4     reserved2   - always 0x00000000 in all observed captures
 *   16      1     idOnDevice  - fixed per-screen byte; does NOT determine display order
 *   17      1     screenType  - 0x01 for built-in screens, 0x02 for partner/third-party screens
 * </pre>
 *
 * <p>The {@code reserved1} and {@code reserved2} fields were zero in every entry across all five
 * BLE packet captures analysed (covering default order, reordered, Strava added, Strava moved,
 * and screens removed). They are preserved for round-trip fidelity but are not expected to carry
 * meaningful data.
 *
 * <p>The {@code screenType} field was 0x01 for every built-in screen and 0x02 only for the
 * Strava "Weekly distance" screen. It may distinguish built-in vs partner/third-party screens.
 */
public class ScreenSettings extends WithingsStructure {

    private int id;

    private int userId = 0;
    /** Always 0x00000000 in all observed captures. */
    private int reserved1 = 0;
    /** Always 0x00000000 in all observed captures. */
    private int reserved2 = 0;
    private byte idOnDevice;
    /**
     * Screen type: 0x01 for built-in screens, 0x02 for partner/third-party screens (e.g. Strava).
     * Defaults to 0x01 (built-in).
     */
    private byte screenType = 0x01;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public byte getIdOnDevice() {
        return idOnDevice;
    }

    public void setIdOnDevice(byte idOnDevice) {
        this.idOnDevice = idOnDevice;
    }

    public byte getScreenType() {
        return screenType;
    }

    /**
     * Sets the screen type byte.
     *
     * @param screenType 0x01 for built-in screens (default), 0x02 for partner/third-party screens
     */
    public void setScreenType(byte screenType) {
        this.screenType = screenType;
    }

    @Override
    public short getLength() {
        return 22;
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer rawDataBuffer) {
        this.id = rawDataBuffer.getInt();
        this.userId = rawDataBuffer.getInt();
        this.reserved1 = rawDataBuffer.getInt();
        this.reserved2 = rawDataBuffer.getInt();
        this.idOnDevice = rawDataBuffer.get();
        this.screenType = rawDataBuffer.get();
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.putInt(this.id);
        buffer.putInt(this.userId);
        buffer.putInt(this.reserved1);
        buffer.putInt(this.reserved2);
        buffer.put(this.idOnDevice);
        buffer.put(this.screenType);
    }

    @Override
    public short getType() {
        return WithingsStructureType.SCREEN_SETTINGS;
    }
}
