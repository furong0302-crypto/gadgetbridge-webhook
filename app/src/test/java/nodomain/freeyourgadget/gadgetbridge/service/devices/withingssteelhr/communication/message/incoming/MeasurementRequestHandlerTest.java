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

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.EndOfTransmission;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.MeasureCategory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class MeasurementRequestHandlerTest {
    @Test
    public void repliesToMeasurementStartWithoutAdvertisingLiveApp() {
        final Message request = new WithingsMessage(WithingsMessageType.MEASURE_START, true);
        request.addDataStructure(new MeasureCategory(MeasureCategory.ECG));

        assertEquals(
                "014973000B097B00020001099F000100",
                GB.hexdump(MeasurementRequestHandler.createBackgroundReply(request).getRawData())
        );
    }

    @Test
    public void acknowledgesMeasurementStop() {
        final Message request = new WithingsMessage(WithingsMessageType.MEASURE_STOP, true);

        assertEquals(
                "014974000401000000",
                GB.hexdump(MeasurementRequestHandler.createBackgroundReply(request).getRawData())
        );
    }
}
