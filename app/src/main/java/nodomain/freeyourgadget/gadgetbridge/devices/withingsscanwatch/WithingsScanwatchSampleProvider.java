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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.withingssteelhr.WithingsSleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsScanwatchActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsScanwatchActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;

public class WithingsScanwatchSampleProvider extends AbstractSampleProvider<WithingsScanwatchActivitySample> {
    public WithingsScanwatchSampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
    }

    @Override
    protected List<WithingsScanwatchActivitySample> getGBActivitySamples(final int timestamp_from, final int timestamp_to) {
        final List<WithingsScanwatchActivitySample> samples = getRawActivitySamples(timestamp_from, timestamp_to);
        overlaySleep(new WithingsSleepSessionProvider(this::getRawActivitySamples), samples, timestamp_from, timestamp_to);
        return samples;
    }

    /**
     * The samples as stored, without the sleep sessions overlaid. The sessions are built from
     * these, so they must not be read through {@link #getGBActivitySamples(int, int)}.
     */
    public List<WithingsScanwatchActivitySample> getRawActivitySamples(final int timestamp_from, final int timestamp_to) {
        return super.getGBActivitySamples(timestamp_from, timestamp_to);
    }

    @Override
    public AbstractDao<WithingsScanwatchActivitySample, ?> getSampleDao() {
        return getSession().getWithingsScanwatchActivitySampleDao();
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return WithingsScanwatchActivitySampleDao.Properties.RawKind;
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return WithingsScanwatchActivitySampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return WithingsScanwatchActivitySampleDao.Properties.DeviceId;
    }

    @Override
    public ActivityKind normalizeType(int rawType) {
        return ActivityKind.fromCode(rawType);
    }

    @Override
    public int toRawActivityKind(ActivityKind activityKind) {
        return activityKind.getCode();
    }

    @Override
    public float normalizeIntensity(int rawIntensity) {
        if (rawIntensity > 0) {
            return (float) (Math.log(rawIntensity) / 8);
        }
        return 0;
    }

    @Override
    public WithingsScanwatchActivitySample createActivitySample() {
        return new WithingsScanwatchActivitySample();
    }
}
