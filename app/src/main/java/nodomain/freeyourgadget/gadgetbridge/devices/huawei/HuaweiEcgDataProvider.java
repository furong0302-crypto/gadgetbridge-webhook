package nodomain.freeyourgadget.gadgetbridge.devices.huawei;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import de.greenrobot.dao.query.DeleteQuery;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.EcgDataProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgDataSample;
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgDataSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgSummarySample;
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgSummarySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.EcgRecord;
import nodomain.freeyourgadget.gadgetbridge.model.EcgSample;

public class HuaweiEcgDataProvider implements EcgDataProvider {
    private final GBDevice device;
    private final DaoSession session;

    public HuaweiEcgDataProvider(final GBDevice device, final DaoSession session) {
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

        final List<HuaweiEcgSummarySample> summaries = session.getHuaweiEcgSummarySampleDao()
            .queryBuilder()
            .where(HuaweiEcgSummarySampleDao.Properties.DeviceId.eq(dbDevice.getId()))
            .orderDesc(HuaweiEcgSummarySampleDao.Properties.StartTimestamp)
            .build()
            .list();

        final List<EcgRecord> records = new ArrayList<>(summaries.size());
        for (final HuaweiEcgSummarySample summary : summaries) {
            final long durationMs = summary.getEndTimestamp() - summary.getStartTimestamp();
            final long sampleCount = session.getHuaweiEcgDataSampleDao().queryBuilder()
                .where(HuaweiEcgDataSampleDao.Properties.EcgId.eq(summary.getEcgId()))
                .count();
            records.add(new EcgRecord(
                Objects.requireNonNull(summary.getEcgId()),
                summary.getStartTimestamp(),
                summary.getEndTimestamp(),
                durationMs > 0 ? sampleCount * 1000f / durationMs : 0,
                summary.getAverageHeartRate() > 0 ? summary.getAverageHeartRate() : null,
                summary.getArrhythmiaType(),
                0,
                Collections.emptyList()
            ));
        }
        return records;
    }

    @NonNull
    @Override
    public float[] getSamples(@NonNull final EcgRecord record) {
        final List<HuaweiEcgDataSample> samples = session.getHuaweiEcgDataSampleDao()
            .queryBuilder()
            .where(HuaweiEcgDataSampleDao.Properties.EcgId.eq(record.getId()))
            .orderAsc(HuaweiEcgDataSampleDao.Properties.TimeDelta)
            .build()
            .list();

        final float[] values = new float[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            values[i] = samples.get(i).getValue();
        }
        return values;
    }

    public long getLatestEndTimestamp() {
        final Device dbDevice = DBHelper.findDevice(device, session);
        if (dbDevice == null) {
            return 0;
        }

        final List<HuaweiEcgSummarySample> samples = session.getHuaweiEcgSummarySampleDao()
            .queryBuilder()
            .where(HuaweiEcgSummarySampleDao.Properties.DeviceId.eq(dbDevice.getId()))
            .orderDesc(HuaweiEcgSummarySampleDao.Properties.EndTimestamp)
            .limit(1)
            .build()
            .list();
        if (samples.isEmpty()) {
            return 0;
        }
        return samples.get(0).getEndTimestamp();
    }

    /**
     * Stores a summary and replaces its samples. If the summary has no id,
     * it replaces the summary with the same timestamp.
     */
    public static void upsertSession(final DaoSession daoSession,
                                     final HuaweiEcgSummarySample summary,
                                     final List<EcgSample> samples) {
        daoSession.runInTx(() -> {
            if (summary.getEcgId() == null) {
                summary.setEcgId(findSessionId(
                    daoSession,
                    summary.getUserId(),
                    summary.getDeviceId(),
                    summary.getStartTimestamp()
                ));
            }
            daoSession.getHuaweiEcgSummarySampleDao().insertOrReplace(summary);

            final DeleteQuery<HuaweiEcgDataSample> deleteQuery = daoSession.getHuaweiEcgDataSampleDao().queryBuilder()
                .where(HuaweiEcgDataSampleDao.Properties.EcgId.eq(summary.getEcgId()))
                .buildDelete();
            deleteQuery.executeDeleteWithoutDetachingEntities();

            final List<HuaweiEcgDataSample> waveformSamples = new ArrayList<>(samples.size());
            for (final EcgSample sample : samples) {
                waveformSamples.add(new HuaweiEcgDataSample(summary.getEcgId(), sample.getTimeDeltaMs(), sample.getValue()));
            }
            daoSession.getHuaweiEcgDataSampleDao().insertInTx(waveformSamples);
        });
    }

    @Nullable
    private static Long findSessionId(final DaoSession daoSession,
                                      final long userId,
                                      final long deviceId,
                                      final long startTimestamp) {
        final List<HuaweiEcgSummarySample> existing = daoSession.getHuaweiEcgSummarySampleDao().queryBuilder()
            .where(
                HuaweiEcgSummarySampleDao.Properties.UserId.eq(userId),
                HuaweiEcgSummarySampleDao.Properties.DeviceId.eq(deviceId),
                HuaweiEcgSummarySampleDao.Properties.StartTimestamp.eq(startTimestamp)
            )
            .build()
            .list();
        if (existing.isEmpty()) {
            return null;
        }
        return existing.get(0).getEcgId();
    }
}
