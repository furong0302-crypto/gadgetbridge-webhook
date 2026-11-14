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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch.WithingsScanwatchDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch.WithingsScanwatchLightDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ShortcutAction;

public class WithingsScanwatchDeviceSupportTest {
    @Test
    public void lightFiltersUnsupportedScreensAndShortcuts() {
        final WithingsScanwatchLightDeviceCoordinator coordinator = new WithingsScanwatchLightDeviceCoordinator();

        assertEquals(Arrays.asList("date", "sleep", "steps"), WithingsScanwatchDeviceSupport.effectiveScreenKeys(
                Arrays.asList("date", "ecg", "sleep", "spo2", "ecg", "steps", "spo2"),
                coordinator.supportsEcgMeasurement(null), coordinator.supportsSpo2(null)));
        assertFalse(WithingsScanwatchDeviceSupport.isShortcutActionSupported(ShortcutAction.ACTION_ECG_MEAS,
                coordinator.supportsEcgMeasurement(null), coordinator.supportsSpo2(null)));
        assertFalse(WithingsScanwatchDeviceSupport.isShortcutActionSupported(ShortcutAction.ACTION_SPO2_MEAS,
                coordinator.supportsEcgMeasurement(null), coordinator.supportsSpo2(null)));
    }

    @Test
    public void scanwatchRetainsEcgAndSpo2ScreensAndShortcuts() {
        final WithingsScanwatchDeviceCoordinator coordinator = new WithingsScanwatchDeviceCoordinator();
        final List<String> screens = Arrays.asList("date", "ecg", "sleep", "spo2", "steps");

        assertEquals(screens, WithingsScanwatchDeviceSupport.effectiveScreenKeys(screens,
                coordinator.supportsEcgMeasurement(null), coordinator.supportsSpo2(null)));
        assertTrue(WithingsScanwatchDeviceSupport.isShortcutActionSupported(ShortcutAction.ACTION_ECG_MEAS,
                coordinator.supportsEcgMeasurement(null), coordinator.supportsSpo2(null)));
        assertTrue(WithingsScanwatchDeviceSupport.isShortcutActionSupported(ShortcutAction.ACTION_SPO2_MEAS,
                coordinator.supportsEcgMeasurement(null), coordinator.supportsSpo2(null)));
    }
}
