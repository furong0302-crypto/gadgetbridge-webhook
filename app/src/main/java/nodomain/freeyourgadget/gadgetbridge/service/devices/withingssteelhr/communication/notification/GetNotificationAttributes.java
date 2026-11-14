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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.notification;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class GetNotificationAttributes {
    private byte commandID;
    private int notificationUID;
    private List<RequestedNotificationAttribute> attributes = new ArrayList<>();

    public byte getCommandID() {
        return commandID;
    }

    public void setCommandID(byte commandID) {
        this.commandID = commandID;
    }

    public int getNotificationUID() {
        return notificationUID;
    }

    public void setNotificationUID(int notificationUID) {
        this.notificationUID = notificationUID;
    }

    public List<RequestedNotificationAttribute> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    public void addAttribute(RequestedNotificationAttribute attribute) {
        attributes.add(attribute);
    }

    public void deserialize(byte[] rawData) {
        ByteBuffer buffer = ByteBuffer.wrap(rawData);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        commandID = buffer.get();
        notificationUID = buffer.getInt();
        while (buffer.hasRemaining()) {
            byte attributeID = buffer.get();
            int length = 1;
            // Title (1), Subtitle (2), Message (3) require a 2-byte max length
            if (attributeID == 1 || attributeID == 2 || attributeID == 3) {
                length = 3;
            }

            byte[] rawAttributeData = new byte[length];
            rawAttributeData[0] = attributeID;
            if (length == 3 && buffer.remaining() >= 2) {
                buffer.get(rawAttributeData, 1, 2);
            }

            RequestedNotificationAttribute requestedNotificationAttribute = new RequestedNotificationAttribute();
            requestedNotificationAttribute.deserialize(rawAttributeData);
            attributes.add(requestedNotificationAttribute);
        }
    }

    public byte[] serialize() {
        return new byte[0];
    }
}
