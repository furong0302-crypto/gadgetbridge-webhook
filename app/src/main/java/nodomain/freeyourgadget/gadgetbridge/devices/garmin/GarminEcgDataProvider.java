package nodomain.freeyourgadget.gadgetbridge.devices.garmin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.EcgDataProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEcgDataSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEcgDataSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEcgSummarySample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEcgSummarySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.EcgExtra;
import nodomain.freeyourgadget.gadgetbridge.model.EcgRecord;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.EcgRhythmClassification;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Side;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionEcgRhythmClassification;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.fieldDefinitions.FieldDefinitionSide;

public class GarminEcgDataProvider implements EcgDataProvider {
    private final GBDevice device;
    private final DaoSession session;

    public GarminEcgDataProvider(final GBDevice device, final DaoSession session) {
        this.device = device;
        this.session = session;
    }

    @NonNull
    @Override
    public List<EcgRecord> getRecords() {
        final Device dbDevice = DBHelper.findDevice(device, session);
        if (dbDevice == null) {
            return Collections.emptyList();
        }

        final List<GarminEcgSummarySample> summaries = session.getGarminEcgSummarySampleDao()
            .queryBuilder()
            .where(GarminEcgSummarySampleDao.Properties.DeviceId.eq(dbDevice.getId()))
            .orderDesc(GarminEcgSummarySampleDao.Properties.StartTimestamp)
            .build()
            .list();

        final List<EcgRecord> records = new ArrayList<>(summaries.size());
        for (final GarminEcgSummarySample summary : summaries) {
            final Integer categorization = summary.getCategorization();
            records.add(new EcgRecord(
                Objects.requireNonNull(summary.getEcgId()),
                summary.getStartTimestamp(),
                summary.getEndTimestamp(),
                summary.getSampleRate(),
                summary.getAverageHeartRate(),
                categorization != null ? Long.valueOf(categorization) : null,
                getClassification(categorization),
                getExtras(summary)
            ));
        }
        return records;
    }

    @NonNull
    @Override
    public float[] getSamples(@NonNull final EcgRecord record) {
        final List<GarminEcgDataSample> samples = session.getGarminEcgDataSampleDao()
            .queryBuilder()
            .where(GarminEcgDataSampleDao.Properties.EcgId.eq(record.getId()))
            .orderAsc(GarminEcgDataSampleDao.Properties.SampleIndex)
            .build()
            .list();

        final float[] values = new float[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            values[i] = samples.get(i).getValue();
        }
        return values;
    }

    @StringRes
    private static int getClassification(@Nullable final Integer categorization) {
        final EcgRhythmClassification classification = categorization != null
            ? FieldDefinitionEcgRhythmClassification.fromId(categorization)
            : null;
        if (classification == null) {
            return 0;
        }
        return switch (classification) {
            case SINUS_NORMAL -> R.string.ecg_classification_sinus_rhythm;
            case INCONCLUSIVE_HR_UNDER_50 -> R.string.ecg_classification_inconclusive_hr_under_50;
        };
    }

    private static List<EcgExtra> getExtras(final GarminEcgSummarySample summary) {
        final List<EcgExtra> extras = new ArrayList<>(2);
        if (summary.getRmssdHrv() != null) {
            extras.add(new EcgExtra(R.string.hrv, R.string.hrv_status_unit, summary.getRmssdHrv()));
        }
        final Side side = summary.getMountingSide() != null ? FieldDefinitionSide.fromId(summary.getMountingSide()) : null;
        if (side != null) {
            extras.add(new EcgExtra(R.string.ecg_wrist, side == Side.LEFT ? R.string.left : R.string.right));
        }
        return extras;
    }
}
