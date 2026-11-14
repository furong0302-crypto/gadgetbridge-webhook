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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ImageMetaData;

public class NotificationRequestHandlerTest {
    @Test
    public void oversizedRequestMetadataMatchesSafeRenderedSize() {
        final ImageMetaData imageMetaData = new ImageMetaData();
        imageMetaData.setWidth((byte) 28);
        imageMetaData.setHeight((byte) 28);

        NotificationRequestHandler.normalizeImageMetaData(imageMetaData);

        assertEquals(22, imageMetaData.getWidth() & 0xFF);
        assertEquals(24, imageMetaData.getHeight() & 0xFF);
    }

    @Test
    public void safeRequestMetadataIsPreserved() {
        final ImageMetaData imageMetaData = new ImageMetaData();
        imageMetaData.setWidth((byte) 20);
        imageMetaData.setHeight((byte) 20);

        NotificationRequestHandler.normalizeImageMetaData(imageMetaData);

        assertEquals(20, imageMetaData.getWidth() & 0xFF);
        assertEquals(20, imageMetaData.getHeight() & 0xFF);
    }
}
