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
package nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;

/**
 * ScanWatch 2 and ScanWatch Nova.
 *
 * <p>In #3595 a ScanWatch Nova was seen as "ScanWatch 2 52" and a first-generation ScanWatch as
 * "ScanWatch 5D". The plain ScanWatch 2 is assumed to advertise like the Nova, which Withings
 * describes as having the same sensors and features. What the trailing suffix means is unknown.
 *
 * <p>Compared with the first generation, the ScanWatch 2 adds 24/7 skin temperature, overnight HRV,
 * respiratory rate and on-watch cycle tracking. Temperature, HRV and cycle tracking are not
 * synchronised yet. Respiratory rate samples are already parsed and stored whenever a Withings
 * watch sends them, but {@link #supportsRespiratoryRate} stays false, as on the first generation,
 * until a ScanWatch 2 log shows real values. So the supported features are the same as the
 * first-generation ScanWatch for now.
 */
public class WithingsScanwatch2DeviceCoordinator extends WithingsScanwatchDeviceCoordinator {
    @Override
    public Pattern getSupportedDeviceName() {
        return Pattern.compile("(?i)^ScanWatch 2(\\s.*)?$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_withings_scanwatch_2;
    }
}
