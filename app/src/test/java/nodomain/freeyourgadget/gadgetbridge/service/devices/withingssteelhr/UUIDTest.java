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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.WithingsUUIDs;

public class UUIDTest {
    @Test
    public void scanwatchUses005dProtocolAnd0037Ancs() {
        assertEquals(uuid("00000020-5749-5448-005d-000000000000"), WithingsUUIDs.SCANWATCH.WITHINGS_SERVICE_UUID);
        assertEquals(uuid("10000058-5749-5448-0037-000000000000"), WithingsUUIDs.SCANWATCH.CONTROL_POINT_CHARACTERISTIC_UUID);
    }

    @Test
    public void scanwatch2UsesOffsetCharacteristicsAnd0037Ancs() {
        assertEquals(uuid("00000000-0000-5749-5448-494e47530000"), WithingsUUIDs.SCANWATCH_2.WITHINGS_SERVICE_UUID);
        assertEquals(uuid("00000003-0000-5749-5448-494e47530000"), WithingsUUIDs.SCANWATCH_2.WITHINGS_WRITE_CHARACTERISTIC_UUID);
        assertEquals(uuid("10000058-5749-5448-0037-000000000000"), WithingsUUIDs.SCANWATCH_2.CONTROL_POINT_CHARACTERISTIC_UUID);
    }

    @Test
    public void scanwatchLightUses005fProtocolAnd0037Ancs() {
        // Confirmed via BLE HCI capture against the official Health Mate app: ScanWatch Light
        // uses its own "005f" suffix for the main protocol service, but the ANCS-style
        // notification service uses "0037", same as every other Withings model.
        assertEquals(uuid("00000020-5749-5448-005f-000000000000"), WithingsUUIDs.SCANWATCH_LIGHT.WITHINGS_SERVICE_UUID);
        assertEquals(uuid("10000058-5749-5448-0037-000000000000"), WithingsUUIDs.SCANWATCH_LIGHT.CONTROL_POINT_CHARACTERISTIC_UUID);
    }

    private static UUID uuid(final String value) {
        return UUID.fromString(value);
    }
}
