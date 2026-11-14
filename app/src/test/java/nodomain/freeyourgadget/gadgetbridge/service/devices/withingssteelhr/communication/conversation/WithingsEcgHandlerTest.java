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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class WithingsEcgHandlerTest {
    @Test
    public void storedEcgHeadProbeMatchesOfficialApp() {
        assertArrayEquals(
                new byte[]{
                        0x01, 0x01, 0x47, 0x00, 0x0c,
                        0x01, 0x43, 0x00, 0x08,
                        0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
                },
                WithingsEcgHandler.createStoredEcgHeadProbe().getRawData()
        );
    }
}
