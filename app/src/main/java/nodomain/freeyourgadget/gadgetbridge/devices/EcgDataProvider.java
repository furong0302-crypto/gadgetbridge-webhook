package nodomain.freeyourgadget.gadgetbridge.devices;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.EcgRecord;
import nodomain.freeyourgadget.gadgetbridge.model.EcgSample;

/**
 * The ECG records of a device, and their waveforms.
 */
public interface EcgDataProvider {
    /**
     * Returns all the records, newest first.
     */
    @NonNull
    List<EcgRecord> getRecords();

    /**
     * Returns the values of a record as the device sent them, at the sample rate of the record.
     */
    @NonNull
    float[] getSamples(@NonNull EcgRecord record);

    /**
     * Returns the waveform of a record to display.
     */
    @NonNull
    default List<EcgSample> getDisplaySamples(@NonNull final EcgRecord record) {
        if (record.getSampleRateHz() <= 0) {
            return Collections.emptyList();
        }

        final float[] values = getSamples(record);
        final List<EcgSample> samples = new ArrayList<>(values.length);
        for (int i = 0; i < values.length; i++) {
            samples.add(new EcgSample(Math.round(i * 1000f / record.getSampleRateHz()), values[i]));
        }
        return samples;
    }
}
