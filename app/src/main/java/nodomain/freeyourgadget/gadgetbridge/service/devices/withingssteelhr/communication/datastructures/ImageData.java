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

public class ImageData extends WithingsStructure {

    byte [] imageData;
    private boolean endOfMessage;

    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }

    public void setEndOfMessage(boolean endOfMessage) {
        this.endOfMessage = endOfMessage;
    }

    @Override
    public boolean withEndOfMessage() {
        return endOfMessage;
    }

    @Override
    public short getLength() {
        return imageData != null ? (short)(imageData.length + HEADER_SIZE + 1) : HEADER_SIZE + 1;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        addByteArrayWithLengthByte(buffer, imageData != null ? imageData : new byte[0]);
    }

    @Override
    public short getType() {
        return WithingsStructureType.IMAGE_DATA;
    }
}
