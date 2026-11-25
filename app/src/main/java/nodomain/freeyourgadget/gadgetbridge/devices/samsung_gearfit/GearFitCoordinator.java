/*  Copyright (C) 2026 MedusasSphinx

    Protocol derived from OpenFit (https://github.com/jareddlc/OpenFit),
    Copyright (C) 2015 Jared De La Cruz, MIT licensed.

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
package nodomain.freeyourgadget.gadgetbridge.devices.samsung_gearfit;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.samsung_gearfit.GearFitSupport;

public class GearFitCoordinator extends AbstractBLClassicDeviceCoordinator {
    /**
     * The Gear Fit 1 advertises itself as "Gear Fit" or "Gear Fit (ABCD)". The Gear Fit 2 runs
     * Tizen and uses "Gear Fit2"/"Gear Fit 2", which this deliberately does not match.
     */
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Gear Fit( \\([A-Za-z0-9]+\\))?$", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public String getManufacturer() {
        return "Samsung";
    }

    @Override
    public boolean supportsDataFetching(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsActivityTracking(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSleepMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsHeartRateStats(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsHeartRateMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsManualHeartRateMeasurement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(@NonNull final GBDevice device,
                                                                      @NonNull final DaoSession session) {
        return new GearFitActivitySampleProvider(device, session);
    }

    @Override
    public int getAlarmSlotCount(final GBDevice device) {
        return 0;
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return 1;
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsCalendarEvents(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsPhoneCalls(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsCallControl(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSmsReply(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsGpsSharing(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsMusicInfo(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsWeather(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsFindDevice(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(@NonNull final GBDevice device) {
        return new int[]{
                R.xml.devicesettings_find_phone,
                R.xml.devicesettings_sync_calendar,
                R.xml.devicesettings_transliteration,
        };
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(@NonNull final GBDevice device) {
        return new GearFitSettingsCustomizer();
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_devices_wearables;
    }

    @NonNull
    @Override
    public DeviceSupport createDeviceSupport(final GBDevice device) {
        return new GearFitSupport();
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_samsung_gear_fit;
    }

    @NonNull
    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull final GBDevice device) {
        return DeviceCoordinator.DeviceKind.WATCH;
    }
}
