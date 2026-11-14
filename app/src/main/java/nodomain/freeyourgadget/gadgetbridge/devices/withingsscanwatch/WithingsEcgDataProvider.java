package nodomain.freeyourgadget.gadgetbridge.devices.withingsscanwatch;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.EcgDataProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsEcgDataSample;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsEcgDataSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsEcgSummarySample;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsEcgSummarySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.EcgRecord;
import nodomain.freeyourgadget.gadgetbridge.model.EcgSample;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.WithingsEcgHandler;
import nodomain.freeyourgadget.gadgetbridge.util.WithingsEcgWaveformUtil;

@SuppressWarnings("SameParameterValue")
public class WithingsEcgDataProvider implements EcgDataProvider {
    private static final int DISPLAY_MEDIAN_WINDOW = 15;
    private static final int DISPLAY_AVERAGE_WINDOW = 7;
    private static final int DISPLAY_BASELINE_WINDOW = 151;
    private static final int DISPLAY_OUTLIER_WINDOW = 15;
    private static final float DISPLAY_TARGET_AMPLITUDE = 0.95f;
    private static final float DISPLAY_MAX_AMPLITUDE = 1.25f;
    private static final int WATCH_STYLE_RESAMPLE_MS = 24;
    private static final int WATCH_STYLE_AVERAGE_WINDOW = 5;

    private final GBDevice device;
    private final DaoSession session;

    public WithingsEcgDataProvider(final GBDevice device, final DaoSession session) {
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

        final List<WithingsEcgSummarySample> summaries = session.getWithingsEcgSummarySampleDao()
            .queryBuilder()
            .where(WithingsEcgSummarySampleDao.Properties.DeviceId.eq(dbDevice.getId()))
            .orderDesc(WithingsEcgSummarySampleDao.Properties.StartTimestamp)
            .build()
            .list();

        final List<EcgRecord> records = new ArrayList<>(summaries.size());
        for (final WithingsEcgSummarySample summary : summaries) {
            records.add(new EcgRecord(
                Objects.requireNonNull(summary.getEcgId()),
                summary.getStartTimestamp(),
                summary.getEndTimestamp(),
                WithingsEcgHandler.ECG_SAMPLE_RATE_HZ,
                summary.getAverageHeartRate() > 0 ? summary.getAverageHeartRate() : null,
                summary.getClassification(),
                summary.getClassification() == 0 ? R.string.ecg_classification_sinus_rhythm : 0,
                Collections.emptyList()
            ));
        }
        return records;
    }

    @NonNull
    @Override
    public float[] getSamples(@NonNull final EcgRecord record) {
        final List<WithingsEcgDataSample> samples = getDataSamples(record);
        final float[] values = new float[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            values[i] = samples.get(i).getValue();
        }
        return values;
    }

    @NonNull
    @Override
    public List<EcgSample> getDisplaySamples(@NonNull final EcgRecord record) {
        final List<WithingsEcgDataSample> samples = getDataSamples(record);
        final List<EcgSample> waveform = new ArrayList<>(samples.size());
        for (final WithingsEcgDataSample sample : samples) {
            waveform.add(new EcgSample(sample.getTimeDelta(), sample.getValue()));
        }
        return smoothWaveformForDisplay(waveform);
    }

    private List<WithingsEcgDataSample> getDataSamples(final EcgRecord record) {
        return session.getWithingsEcgDataSampleDao()
            .queryBuilder()
            .where(WithingsEcgDataSampleDao.Properties.EcgId.eq(record.getId()))
            .orderAsc(WithingsEcgDataSampleDao.Properties.TimeDelta)
            .build()
            .list();
    }

    private static List<EcgSample> smoothWaveformForDisplay(final List<EcgSample> waveform) {
        if (waveform.size() < 5) {
            return waveform;
        }

        final float[] values = prepareWaveformValues(waveform);
        final float[] medianFiltered = applyMedianFilter(values, DISPLAY_MEDIAN_WINDOW);
        final float[] centered = subtractBaseline(medianFiltered, DISPLAY_BASELINE_WINDOW);
        final float[] despiked = suppressOutliers(centered, DISPLAY_OUTLIER_WINDOW);
        final float[] smoothed = applyMovingAverage(despiked, DISPLAY_AVERAGE_WINDOW);
        final float[] normalized = normalizeForDisplay(smoothed);

        final List<EcgSample> displayWaveform = new ArrayList<>(normalized.length);
        for (int i = 0; i < normalized.length; i++) {
            final int timeDeltaMs = getMappedTimeDeltaMs(waveform, normalized.length, i);
            displayWaveform.add(new EcgSample(timeDeltaMs, normalized[i]));
        }

        final List<EcgSample> resampled = resampleWaveformForDisplay(displayWaveform, WATCH_STYLE_RESAMPLE_MS);

        if (resampled.size() < 5) {
            return resampled;
        }

        final float[] watchValues = new float[resampled.size()];
        for (int i = 0; i < resampled.size(); i++) {
            watchValues[i] = resampled.get(i).getValue();
        }
        final float[] watchSmoothed = applyMovingAverage(watchValues, WATCH_STYLE_AVERAGE_WINDOW);

        final List<EcgSample> watchStyleWaveform = new ArrayList<>(resampled.size());
        for (int i = 0; i < resampled.size(); i++) {
            watchStyleWaveform.add(new EcgSample(resampled.get(i).getTimeDeltaMs(), watchSmoothed[i]));
        }
        return watchStyleWaveform;
    }

    private static int getMappedTimeDeltaMs(final List<EcgSample> waveform, final int outputSize, final int index) {
        if (waveform.isEmpty()) {
            return index;
        }
        if (outputSize <= 1 || waveform.size() == 1) {
            return waveform.get(0).getTimeDeltaMs();
        }
        if (outputSize == waveform.size()) {
            return waveform.get(index).getTimeDeltaMs();
        }

        final float position = index * (waveform.size() - 1f) / (outputSize - 1f);
        final int mappedIndex = Math.min(waveform.size() - 1, Math.max(0, Math.round(position)));
        return waveform.get(mappedIndex).getTimeDeltaMs();
    }

    private static float[] prepareWaveformValues(final List<EcgSample> waveform) {
        final float[] values = new float[waveform.size()];
        for (int i = 0; i < waveform.size(); i++) {
            values[i] = waveform.get(i).getValue();
        }

        return WithingsEcgWaveformUtil.decodeStoredValuesIfNeeded(values);
    }

    private static List<EcgSample> resampleWaveformForDisplay(final List<EcgSample> waveform, final int bucketMs) {
        if (waveform.size() < 3 || bucketMs <= 1) {
            return waveform;
        }

        final List<EcgSample> resampled = new ArrayList<>();
        int bucketStart = waveform.get(0).getTimeDeltaMs();
        int lastTime = bucketStart;
        float sum = 0f;
        int count = 0;

        for (final EcgSample sample : waveform) {
            final int time = sample.getTimeDeltaMs();
            if (time - bucketStart >= bucketMs && count > 0) {
                final int midpoint = bucketStart + ((lastTime - bucketStart) / 2);
                resampled.add(new EcgSample(midpoint, sum / count));
                bucketStart = time;
                sum = 0f;
                count = 0;
            }

            sum += sample.getValue();
            count++;
            lastTime = time;
        }

        if (count > 0) {
            final int midpoint = bucketStart + ((lastTime - bucketStart) / 2);
            resampled.add(new EcgSample(midpoint, sum / count));
        }

        return resampled.size() >= 3 ? resampled : waveform;
    }

    private static float[] applyMedianFilter(final float[] values, final int windowSize) {
        final float[] filtered = new float[values.length];
        final int halfWindow = windowSize / 2;

        for (int i = 0; i < values.length; i++) {
            final int start = Math.max(0, i - halfWindow);
            final int end = Math.min(values.length - 1, i + halfWindow);
            final float[] window = new float[end - start + 1];
            if (end + 1 - start >= 0) {
                System.arraycopy(values, start, window, 0, end + 1 - start);
            }
            Arrays.sort(window);
            filtered[i] = window[window.length / 2];
        }

        return filtered;
    }

    private static float[] applyMovingAverage(final float[] values, final int windowSize) {
        final float[] filtered = new float[values.length];
        final int halfWindow = windowSize / 2;

        for (int i = 0; i < values.length; i++) {
            final int start = Math.max(0, i - halfWindow);
            final int end = Math.min(values.length - 1, i + halfWindow);
            float sum = 0f;
            for (int j = start; j <= end; j++) {
                sum += values[j];
            }
            filtered[i] = sum / (end - start + 1);
        }

        return filtered;
    }

    private static float[] subtractBaseline(final float[] values, final int windowSize) {
        final float[] baseline = applyMovingAverage(values, windowSize);
        final float[] flattened = new float[values.length];

        for (int i = 0; i < values.length; i++) {
            flattened[i] = values[i] - baseline[i];
        }

        return flattened;
    }

    private static float[] suppressOutliers(final float[] values, final int windowSize) {
        final float[] filtered = new float[values.length];
        final int halfWindow = windowSize / 2;

        for (int i = 0; i < values.length; i++) {
            final int start = Math.max(0, i - halfWindow);
            final int end = Math.min(values.length - 1, i + halfWindow);
            final float[] window = new float[end - start + 1];
            if (end + 1 - start >= 0) {
                System.arraycopy(values, start, window, 0, end + 1 - start);
            }
            Arrays.sort(window);

            final float median = window[window.length / 2];
            final float[] deviations = new float[window.length];
            for (int j = 0; j < window.length; j++) {
                deviations[j] = Math.abs(window[j] - median);
            }
            Arrays.sort(deviations);
            final float mad = deviations[deviations.length / 2];
            final float threshold = Math.max(2.5f, mad * 3f);

            filtered[i] = Math.abs(values[i] - median) > threshold ? median : values[i];
        }

        return filtered;
    }

    private static float[] normalizeForDisplay(final float[] values) {
        final float[] absValues = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            absValues[i] = Math.abs(values[i]);
        }
        Arrays.sort(absValues);

        final int percentileIndex = Math.min(absValues.length - 1, Math.max(0, (int) (absValues.length * 0.95f)));
        final float referenceAmplitude = Math.max(1f, absValues[percentileIndex]);
        final float scale = DISPLAY_TARGET_AMPLITUDE / referenceAmplitude;

        final float[] normalized = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            final float scaled = values[i] * scale;
            normalized[i] = (float) (DISPLAY_MAX_AMPLITUDE * Math.tanh(scaled / DISPLAY_MAX_AMPLITUDE));
        }

        return normalized;
    }
}
