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
package nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;

import de.greenrobot.dao.query.QueryBuilder;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericRespiratoryRateSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSpo2SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.SleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.withingssteelhr.WithingsSleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.EcgDataProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.ServiceDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity.WithingsActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch.WithingsScanwatchDeviceSupport;

public class WithingsScanwatchDeviceCoordinator extends AbstractBLEDeviceCoordinator {

    @Override
    public EnumSet<ServiceDeviceSupport.Flags> getInitialFlags() {
        // Withings uses ANCS (GATT server) for notifications, which is independent
        // of the WPP conversation queue used for sync. Disabling BUSY_CHECKING
        // allows notifications to flow through during sync instead of being dropped.
        return EnumSet.noneOf(ServiceDeviceSupport.Flags.class);
    }

    @Override
    public Pattern getSupportedDeviceName() {
        return Pattern.compile("(?i)^ScanWatch(?! Light| 2(\\s|$)).*");
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
                R.xml.devicesettings_withingsscanwatch,
                R.xml.devicesettings_wearlocation
        };
    }

    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(final GBDevice device) {
        return new WithingsScanwatchSettingsCustomizer();
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_BOND;
    }

    @Override
    public boolean supportsAwakeSleep(@NonNull final GBDevice device) {
        return true;
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
    public boolean supportsActiveCalories(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSpo2(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsEcgMeasurement(@NonNull final GBDevice device) {
        return true;
    }

    @Nullable
    @Override
    public EcgDataProvider getEcgDataProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        return new WithingsEcgDataProvider(device, session);
    }

    public boolean supportsRespiratoryScan(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsRespiratoryRate(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public TimeSampleProvider<? extends RespiratoryRateSample> getRespiratoryRateSampleProvider(final GBDevice device, final DaoSession session) {
        return new GenericRespiratoryRateSampleProvider(device, session);
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
        return new WithingsScanwatchSampleProvider(device, session);
    }

    @Override
    @NonNull
    public SleepSessionProvider getSleepSessionProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        return new WithingsSleepSessionProvider(new WithingsScanwatchSampleProvider(device, session)::getRawActivitySamples);
    }

    @Override
    public TimeSampleProvider<? extends Spo2Sample> getSpo2SampleProvider(final GBDevice device, final DaoSession session) {
        return new GenericSpo2SampleProvider(device, session);
    }

    @Override
    public int getAlarmSlotCount(GBDevice gbDevice) {
        return 10;
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
    public boolean supportsSmartWakeupInterval(@NonNull GBDevice device, int alarmPosition) {
        return true;
    }

    @Override
    public int getSmartWakeupMaxInterval(@NonNull GBDevice device) {
        return 60;
    }

    @Override
    public String getSmartWakeupDescription(@NonNull GBDevice device) {
        return GBApplication.getContext().getString(R.string.withings_scanwatch_alarm_smart_wakeup_description);
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
        return new String[]{ "auto", "de_DE", "en_US", "es_ES", "fr_FR", "it_IT" };
    }

    @NonNull
    @Override
    public DeviceSupport createDeviceSupport(final GBDevice device) {
        return new WithingsScanwatchDeviceSupport();
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_withings_scanwatch;
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
