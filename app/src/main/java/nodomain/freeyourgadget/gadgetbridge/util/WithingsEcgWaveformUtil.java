/*  Copyright (C) 2026 d3vv3

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
package nodomain.freeyourgadget.gadgetbridge.util;

import java.util.ArrayList;
import java.util.List;

public final class WithingsEcgWaveformUtil {
    private static final int ANCHOR_MARKER = 0x80;

    private WithingsEcgWaveformUtil() {
    }

    public static float[] decodeStoredValuesIfNeeded(final float[] values) {
        if (!looksEncoded(values)) {
            return values;
        }

        final StreamingDecoder decoder = new StreamingDecoder();
        final float[] decoded = new float[values.length];
        int out = 0;
        for (final float value : values) {
            final int raw = clampToByte(Math.round(value));
            final Float decodedSample = decoder.feed(raw & 0xff);
            if (decodedSample != null && out < decoded.length) {
                decoded[out++] = decodedSample;
            }
        }

        if (out < Math.max(16, values.length / 2)) {
            return values;
        }

        final float[] trimmed = new float[out];
        System.arraycopy(decoded, 0, trimmed, 0, out);
        return trimmed;
    }

    public static List<Float> decodePacket(final byte[] encodedBytes, final StreamingDecoder decoder) {
        final List<Float> decoded = new ArrayList<>(encodedBytes.length);
        for (final byte encodedByte : encodedBytes) {
            final Float decodedSample = decoder.feed(encodedByte & 0xff);
            if (decodedSample != null) {
                decoded.add(decodedSample);
            }
        }
        return decoded;
    }

    private static boolean looksEncoded(final float[] values) {
        if (values.length < 32) {
            return false;
        }

        int exactIntegers = 0;
        int sentinelCount = 0;
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (final float value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            if (Math.abs(value - Math.round(value)) < 0.001f) {
                exactIntegers++;
            }
            if (Math.round(value) == -128) {
                sentinelCount++;
            }
        }

        final boolean byteRange = min >= -128f && max <= 127f;
        final boolean integerLike = exactIntegers >= values.length * 0.95f;
        final boolean hasAnchors = sentinelCount >= Math.max(2, values.length / 80);
        return byteRange && integerLike && hasAnchors;
    }

    private static int clampToByte(final int value) {
        if (value < -128) {
            return -128;
        }
        if (value > 127) {
            return 127;
        }
        return value;
    }

    public static final class StreamingDecoder {
        private int pendingAnchorBytes = 0;
        private int anchorHighByte = 0;
        private int lastSample = 0;
        private boolean hasLastSample = false;

        public Float feed(final int rawByte) {
            if (pendingAnchorBytes == 2) {
                anchorHighByte = rawByte;
                pendingAnchorBytes = 1;
                return null;
            }

            if (pendingAnchorBytes == 1) {
                lastSample = ((anchorHighByte & 0xff) << 8) | (rawByte & 0xff);
                hasLastSample = true;
                pendingAnchorBytes = 0;
                return (float) lastSample;
            }

            if ((rawByte & 0xff) == ANCHOR_MARKER) {
                pendingAnchorBytes = 2;
                return null;
            }

            final int signedDelta = rawByte > 127 ? rawByte - 256 : rawByte;
            if (!hasLastSample) {
                lastSample = signedDelta;
                hasLastSample = true;
            } else {
                lastSample += signedDelta;
            }
            return (float) lastSample;
        }
    }
}
