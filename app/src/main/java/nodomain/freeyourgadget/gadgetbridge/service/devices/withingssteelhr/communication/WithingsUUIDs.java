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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication;

import java.util.UUID;

/**
 * Holds the device-specific BLE UUIDs for Withings devices.
 * Steel HR uses suffix "0037", Scanwatch uses suffix "005d".
 */
public class WithingsUUIDs {

    public final UUID WITHINGS_SERVICE_UUID;
    public final UUID WITHINGS_WRITE_CHARACTERISTIC_UUID;
    public final UUID WITHINGS_APP_CHARACTERISTIC_UUID;
    public final UUID WITHINGS_APP_CHARACTERISTIC2_UUID;
    public final UUID CCC_DESCRIPTOR_UUID;
    public final UUID WITHINGS_ANCS_SERVICE_UUID;
    public final UUID NOTIFICATION_SOURCE_CHARACTERISTIC_UUID;
    public final UUID CONTROL_POINT_CHARACTERISTIC_UUID;
    public final UUID DATA_SOURCE_CHARACTERISTIC_UUID;

    public WithingsUUIDs(final String protocolSuffix) {
        this(protocolSuffix, protocolSuffix);
    }

    public WithingsUUIDs(final String protocolSuffix, final String ancsSuffix) {
        this(
                UUID.fromString("00000020-5749-5448-" + protocolSuffix + "-000000000000"),
                UUID.fromString("00000024-5749-5448-" + protocolSuffix + "-000000000000"),
                UUID.fromString("10000059-5749-5448-" + protocolSuffix + "-000000000000"),
                UUID.fromString("10000028-5749-5448-" + protocolSuffix + "-000000000000"),
                ancsSuffix
        );
    }

    /**
     * Constructor for V2 namespace which uses a base suffix and characteristic offsets.
     */
    public WithingsUUIDs(final String baseSuffix, final int serviceOffset, final int writeOffset, final int app1Offset, final int app2Offset, final String ancsSuffix) {
        this(
                UUID.fromString(String.format("%08x", serviceOffset) + baseSuffix),
                UUID.fromString(String.format("%08x", writeOffset) + baseSuffix),
                UUID.fromString(String.format("%08x", app1Offset) + baseSuffix),
                UUID.fromString(String.format("%08x", app2Offset) + baseSuffix),
                ancsSuffix
        );
    }

    public WithingsUUIDs(final UUID withingsServiceUuid,
                         final UUID withingsWriteCharacteristicUuid,
                         final UUID withingsAppCharacteristicUuid,
                         final UUID withingsAppCharacteristic2Uuid,
                         final String ancsSuffix) {
        WITHINGS_SERVICE_UUID                 = withingsServiceUuid;
        WITHINGS_WRITE_CHARACTERISTIC_UUID    = withingsWriteCharacteristicUuid;
        WITHINGS_APP_CHARACTERISTIC_UUID      = withingsAppCharacteristicUuid;
        WITHINGS_APP_CHARACTERISTIC2_UUID     = withingsAppCharacteristic2Uuid;
        CCC_DESCRIPTOR_UUID                   = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        WITHINGS_ANCS_SERVICE_UUID            = UUID.fromString("10000057-5749-5448-" + ancsSuffix + "-000000000000");
        NOTIFICATION_SOURCE_CHARACTERISTIC_UUID = UUID.fromString("10000059-5749-5448-" + ancsSuffix + "-000000000000");
        CONTROL_POINT_CHARACTERISTIC_UUID     = UUID.fromString("10000058-5749-5448-" + ancsSuffix + "-000000000000");
        DATA_SOURCE_CHARACTERISTIC_UUID       = UUID.fromString("1000005a-5749-5448-" + ancsSuffix + "-000000000000");
    }

    /** Steel HR UUID set */
    public static final WithingsUUIDs STEEL_HR = new WithingsUUIDs("0037");

    /** Scanwatch UUID set */
    public static final WithingsUUIDs SCANWATCH = new WithingsUUIDs("005d", "0037");

    /**
     * Scanwatch Light UUID set.
     * The Scanwatch Light is a pared-back version of the Scanwatch 2.
     * It lacks some features of the full model, such as ECG support.
     *
     * <p>Confirmed via BLE HCI capture against the official Health Mate app: the ANCS-style
     * notification service uses suffix "0037", same as every other Withings model, even though
     * the main protocol service uses "005f".
     */
    public static final WithingsUUIDs SCANWATCH_LIGHT = new WithingsUUIDs("005f", "0037");

    /** Scanwatch 2 UUID set */
    public static final String V2_BASE_SUFFIX = "-0000-5749-5448-494e47530000";

    public static final WithingsUUIDs SCANWATCH_2 = new WithingsUUIDs(
            V2_BASE_SUFFIX,
            0x00, // Service
            0x03, // Write
            0x04, // App1
            0x05, // App2
            "0037"
    );
}
