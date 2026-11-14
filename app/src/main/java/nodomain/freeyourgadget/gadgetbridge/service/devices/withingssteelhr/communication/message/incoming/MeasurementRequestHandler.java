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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.incoming;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.EndOfTransmission;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.MeasureCategory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.MeasureLiveAppStatus;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;

public class MeasurementRequestHandler implements IncomingMessageHandler {
    private final WithingsBaseDeviceSupport support;

    public MeasurementRequestHandler(final WithingsBaseDeviceSupport support) {
        this.support = support;
    }

    @Override
    public void handleMessage(final Message message) {
        support.sendToDevice(createBackgroundReply(message));
    }

    static Message createBackgroundReply(final Message request) {
        final WithingsMessage reply = new WithingsMessage((short) (request.getType() | 0x4000));
        if (request.getType() == WithingsMessageType.MEASURE_START) {
            final MeasureCategory category = request.getStructureByType(MeasureCategory.class);
            reply.addDataStructure(new MeasureCategory(category != null ? category.getValue() : MeasureCategory.ECG));
            // Gadgetbridge syncs completed records; it does not provide a live measurement view.
            reply.addDataStructure(new MeasureLiveAppStatus(0));
        } else {
            reply.addDataStructure(new EndOfTransmission());
        }
        return reply;
    }
}
