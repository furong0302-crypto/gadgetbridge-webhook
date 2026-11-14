/*  Copyright (C) 2023-2024 Ascense, Frank Ertl

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

import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.withingssteelhr.WithingsSteelHRSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractWithingsActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsSteelHRActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.WithingsUUIDs;

/**
 * Device support for Withings Steel HR. All protocol logic lives in {@link WithingsBaseDeviceSupport}.
 */
public class WithingsSteelHRDeviceSupport extends WithingsBaseDeviceSupport {

    @Override
    protected WithingsUUIDs getWithingsUUIDs() {
        return WithingsUUIDs.STEEL_HR;
    }

    @Override
    public AbstractSampleProvider<? extends AbstractWithingsActivitySample> createSampleProvider(GBDevice device, DaoSession session) {
        return new WithingsSteelHRSampleProvider(device, session);
    }

    // The Steel HR does not appear to support this
    @Override
    protected void addFeatureTagsMessage() {
    }

    @Override
    protected boolean supportsVasistasType4() {
        return false;
    }

    @Override
    protected boolean supportsStoredMeasureSync() {
        return false;
    }

}
