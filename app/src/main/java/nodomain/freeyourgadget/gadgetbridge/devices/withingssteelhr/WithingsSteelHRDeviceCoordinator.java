/*  Copyright (C) 2023-2024 Ascense, Daniel Dakhno, Frank Ertl, José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices.withingssteelhr;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.EnumSet;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.SleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.ServiceDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity.WithingsActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsSteelHRDeviceSupport;

public class WithingsSteelHRDeviceCoordinator extends AbstractBLEDeviceCoordinator {

    @Override
    public EnumSet<ServiceDeviceSupport.Flags> getInitialFlags() {
        // Withings uses ANCS (GATT server) for notifications, which is independent
        // of the WPP conversation queue used for sync. Disabling BUSY_CHECKING
        // allows notifications to flow through during sync instead of being dropped.
        return EnumSet.noneOf(ServiceDeviceSupport.Flags.class);
    }

    @Override
    public boolean supports(GBDeviceCandidate candidate) {
        String name = candidate.getName();
        return name != null && (name.toLowerCase(Locale.ROOT).startsWith("steel") || name.toLowerCase(Locale.ROOT).startsWith("activite"));
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
                R.xml.devicesettings_withingssteelhr
        };
    }

    @Override
    public int getBondingStyle(){
        return BONDING_STYLE_BOND;
    }

    @Override
    public boolean supportsDataFetching(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsActivityTracking(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsRecordedActivities(final GBDevice device) {
        return true;
    }

    @Override
    public ActivitySummaryParser getActivitySummaryParser(final GBDevice device, final Context context) {
        return new WithingsActivitySummaryParser(device);
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(GBDevice device, DaoSession session) {
        return new WithingsSteelHRSampleProvider(device, session);
    }

    @Override
    @NonNull
    public SleepSessionProvider getSleepSessionProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        return new WithingsSleepSessionProvider(new WithingsSteelHRSampleProvider(device, session)::getRawActivitySamples);
    }

    @Override
    public int getAlarmSlotCount(GBDevice gbDevice) {
        return 3;
    }

    @Override
    public boolean supportsAlarmTitle(GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsAlarmDescription(GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSmartWakeup(GBDevice device, int position) {
        return true;
    }

    @Override
    public boolean supportsHeartRateMeasurement(GBDevice device) {
        return true;
    }

    @Override
    public String getManufacturer() {
        return "Withings";
    }

    @Override
    public boolean supportsRealtimeData(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsLiveOnlyHeartRateDisplay(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public String[] getSupportedLanguageSettings(GBDevice device) {
        return new String[]{
                "auto",
                "de_DE",
                "en_US",
                "es_ES",
                "fr_FR",
                "it_IT",
        };
    }

    @NonNull
    @Override
    public DeviceSupport createDeviceSupport(final GBDevice device) {
        return new WithingsSteelHRDeviceSupport();
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_withings_steel_hr;
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_watchxplus;
    }

    @Override
    public boolean supportsUnicodeEmojis(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.WATCH;
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsPhoneCalls(@NonNull final GBDevice device) {
        return true;
    }
}
