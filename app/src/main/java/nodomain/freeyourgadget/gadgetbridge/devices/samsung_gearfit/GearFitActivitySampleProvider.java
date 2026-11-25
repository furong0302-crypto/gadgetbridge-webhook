/*  Copyright (C) 2026 MedusasSphinx

    Fitness data model derived from OpenFit (https://github.com/jareddlc/OpenFit),
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
import androidx.annotation.Nullable;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.GearFitActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.GearFitActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;

public class GearFitActivitySampleProvider extends AbstractSampleProvider<GearFitActivitySample> {
    public GearFitActivitySampleProvider(final GBDevice device, final DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<GearFitActivitySample, ?> getSampleDao() {
        return getSession().getGearFitActivitySampleDao();
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return GearFitActivitySampleDao.Properties.RawKind;
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return GearFitActivitySampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return GearFitActivitySampleDao.Properties.DeviceId;
    }

    @Override
    public ActivityKind normalizeType(final int rawType) {
        return ActivityKind.fromCode(rawType);
    }

    @Override
    public int toRawActivityKind(final ActivityKind activityKind) {
        return activityKind.getCode();
    }

    @Override
    public float normalizeIntensity(final int rawIntensity) {
        return rawIntensity / 100f;
    }

    @Override
    public GearFitActivitySample createActivitySample() {
        return new GearFitActivitySample();
    }
}
