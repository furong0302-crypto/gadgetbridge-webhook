package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.EcgDataProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.model.EcgExtra;
import nodomain.freeyourgadget.gadgetbridge.model.EcgRecord;

public class TestEcgDataProvider implements EcgDataProvider {
    private static final long MEASUREMENT_TIME_MILLIS = 10 * 60 * 60 * 1000L;
    private static final int DURATION_SECONDS = 30;
    private static final int SAMPLE_RATE_HZ = 128;
    private static final int HISTORY_DAYS = 90;

    /**
     * The P, Q, R, S and T waves of a beat, as:
     * - offset from the R peak, in seconds
     * - amplitude in mV
     * - width in seconds
     */
    private static final float[][] WAVES = {
        {-0.16f, 0.12f, 0.02f},
        {-0.04f, -0.1f, 0.01f},
        {0f, 1f, 0.01f},
        {0.04f, -0.25f, 0.012f},
        {0.25f, 0.3f, 0.045f},
    };

    @NonNull
    @Override
    public List<EcgRecord> getRecords() {
        final List<EcgRecord> records = new ArrayList<>();
        final long now = System.currentTimeMillis();
        final long firstDay = AbstractTestSampleProvider.startOfDay(now) - (HISTORY_DAYS - 1) * AbstractTestSampleProvider.DAY_MILLIS;

        for (long day = AbstractTestSampleProvider.startOfDay(now); day >= firstDay; day -= AbstractTestSampleProvider.DAY_MILLIS) {
            final long ts = day + MEASUREMENT_TIME_MILLIS;
            if (ts > now || !TestDeviceRand.randBool(ts, 0.5f)) {
                continue;
            }

            final boolean sinusRhythm = TestDeviceRand.randBool(ts + 2, 0.8f);
            records.add(new EcgRecord(
                ts,
                ts,
                ts + DURATION_SECONDS * 1000L,
                SAMPLE_RATE_HZ,
                TestDeviceRand.randInt(ts + 1, 50, 90),
                sinusRhythm ? 0L : 1L,
                sinusRhythm ? R.string.ecg_classification_sinus_rhythm : 0,
                Collections.singletonList(
                    new EcgExtra(R.string.hrv, R.string.hrv_status_unit, TestDeviceRand.randInt(ts + 3, 20, 80))
                )
            ));
        }

        return records;
    }

    @NonNull
    @Override
    public float[] getSamples(@NonNull final EcgRecord record) {
        final Random random = new Random(record.getStartTimestamp() - TestDeviceRand.BASE_TIMESTAMP);

        final List<Float> beats = new ArrayList<>();
        final float beatSeconds = 60f / Objects.requireNonNull(record.getAverageHeartRate());
        for (float beat = beatSeconds / 2; beat < DURATION_SECONDS; beat += beatSeconds * (0.97f + random.nextFloat() * 0.06f)) {
            beats.add(beat);
        }

        final float[] samples = new float[DURATION_SECONDS * SAMPLE_RATE_HZ];
        for (int i = 0; i < samples.length; i++) {
            final float seconds = (float) i / SAMPLE_RATE_HZ;
            float value = (random.nextFloat() - 0.5f) * 0.02f;
            for (final float beat : beats) {
                for (final float[] wave : WAVES) {
                    final float distance = (seconds - beat - wave[0]) / wave[2];
                    value += (float) (wave[1] * Math.exp(-distance * distance / 2));
                }
            }
            samples[i] = value;
        }
        return samples;
    }
}
