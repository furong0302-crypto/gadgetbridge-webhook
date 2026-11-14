package nodomain.freeyourgadget.gadgetbridge.model;

import androidx.annotation.StringRes;

/**
 * A device-specific value of an ECG record, as a label and a format string, and its arguments.
 */
public class EcgExtra {
    @StringRes
    private final int label;
    @StringRes
    private final int format;
    private final Object[] formatArgs;

    public EcgExtra(@StringRes final int label, @StringRes final int format, final Object... formatArgs) {
        this.label = label;
        this.format = format;
        this.formatArgs = formatArgs;
    }

    @StringRes
    public int getLabel() {
        return label;
    }

    @StringRes
    public int getFormat() {
        return format;
    }

    public Object[] getFormatArgs() {
        return formatArgs;
    }
}
