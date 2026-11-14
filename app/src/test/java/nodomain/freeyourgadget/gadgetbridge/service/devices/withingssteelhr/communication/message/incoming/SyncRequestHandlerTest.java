/*  Copyright (C) 2026 d3vv3

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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.mockito.InOrder;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;

public class SyncRequestHandlerTest {
    @Test
    public void watchSyncRequestRespondsBeforeStartingSync() {
        final WithingsBaseDeviceSupport support = mock(WithingsBaseDeviceSupport.class);
        final Message request = mock(Message.class);

        new SyncRequestHandler(support).handleMessage(request);

        final InOrder inOrder = inOrder(support);
        inOrder.verify(support).sendToDevice(argThat(
                response -> response.getType() == WithingsMessageType.SYNC_RESPONSE
        ));
        inOrder.verify(support).doSync("watch-sync-request");
    }
}
