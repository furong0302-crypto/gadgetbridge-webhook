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
 * Encodes the user-ID header TLV (type {@code 0x0145}) that precedes the feature-tag list in
 * {@code CMD_FEATURE_TAGS_SET_DEPRECATED_V2} (0x0987) messages.
 *
 * <p>Wire format: 2-byte type + 2-byte length (4) + 4-byte uint32 user-ID (always {@code 0} in
 * captures - may be a Withings account ID that Gadgetbridge does not have).
 */
public class FeatureTagsUserId extends WithingsStructure {

    private int userId;

    /** Constructs with userId = 0 (Gadgetbridge does not have a Withings account ID). */
    public FeatureTagsUserId() {
        this.userId = 0;
    }

    public FeatureTagsUserId(int userId) {
        this.userId = userId;
    }

    @Override
    public short getLength() {
        // 4-byte header + 4-byte uint32
        return 8;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.putInt(userId);
    }

    @Override
    protected void fillFromRawDataAsBuffer(ByteBuffer buffer) {
        if (buffer.remaining() >= 4) {
            userId = buffer.getInt();
        }
    }

    @Override
    public short getType() {
        return WithingsStructureType.FEATURE_TAGS_USER_ID;
    }

    @Override
    public boolean withEndOfMessage() {
        return true;
    }
}
