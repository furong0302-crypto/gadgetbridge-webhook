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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ScreenSettings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;

public class ScreenSettingsHandler extends AbstractResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(ScreenSettingsHandler.class);

    public ScreenSettingsHandler(WithingsBaseDeviceSupport support) {
        super(support);
    }

    @Override
    public void handleResponse(Message response) {
        List<WithingsStructure> data = response.getDataStructures();
        if (data == null || data.isEmpty()) {
            logger.warn("ScreenSettingsHandler: received empty response");
            return;
        }
        logger.info("ScreenSettingsHandler: device reported {} screen(s)", data.size());
        for (WithingsStructure structure : data) {
            if (structure instanceof ScreenSettings) {
                ScreenSettings screen = (ScreenSettings) structure;
                logger.info("Screen from device: id=0x{} ({}), slot={}",
                        Integer.toHexString(screen.getId()),
                        screen.getId(),
                        screen.getIdOnDevice() & 0xFF);
            }
        }
    }
}
