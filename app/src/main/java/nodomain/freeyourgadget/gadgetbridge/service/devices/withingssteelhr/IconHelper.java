/*  Copyright (C) 2023-2024 Frank Ertl

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.util.BitmapUtil;

public class IconHelper {
    public static byte[] getIconBytesFromDrawable(Drawable drawable) {
        return getIconBytesFromDrawable(drawable, 22, 24);
    }

    public static byte[] getIconBytesFromDrawable(Drawable drawable, int width, int height) {
        return getIconBytesFromBitmap(BitmapUtil.toBitmap(drawable), width, height, true);
    }

    public static byte[] getIconBytesFromBitmap(final Bitmap bitmap, final int width, final int height) {
        return getIconBytesFromBitmap(bitmap, width, height, true);
    }

    public static byte[] getIconBytesFromBitmap(final Bitmap bitmap, final int width, final int height, final boolean cropTransparentBounds) {
        return toByteArray(renderBitmap(bitmap, width, height, cropTransparentBounds));
    }

    public static List<byte[]> splitImageData(byte[] imageData, int maxChunkSize) {
        List<byte[]> chunks = new ArrayList<>();
        if (imageData == null || imageData.length == 0) {
            chunks.add(new byte[0]);
            return chunks;
        }

        for (int offset = 0; offset < imageData.length; offset += maxChunkSize) {
            final int chunkLength = Math.min(maxChunkSize, imageData.length - offset);
            final byte[] chunk = new byte[chunkLength];
            System.arraycopy(imageData, offset, chunk, 0, chunkLength);
            chunks.add(chunk);
        }

        return chunks;
    }

    public static byte[] toByteArray(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int bytesPerColumn = getBytesPerColumn(height);
        byte[] rawData = new byte[bytesPerColumn * width];
        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height; row++) {
                int pixel = bitmap.getPixel(col, row);
                if (shouldPixelbeAdded(pixel)) {
                    int bitIndex = bytesPerColumn * col + row / 8;
                    rawData[bitIndex] = setBit(rawData[bitIndex], row);
                }
            }
        }

        return rawData;
    }

    public static byte[] toLuminanceThresholdByteArray(final Bitmap bitmap, final int luminanceThreshold) {
        return toLuminanceThresholdByteArray(bitmap, luminanceThreshold, false);
    }

    public static byte[] toLuminanceThresholdByteArray(final Bitmap bitmap, final int luminanceThreshold, final boolean invert) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int bytesPerColumn = getBytesPerColumn(height);
        final byte[] rawData = new byte[bytesPerColumn * width];

        for (int col = 0; col < width; col++) {
            for (int row = 0; row < height; row++) {
                final int pixel = bitmap.getPixel(col, row);
                if (shouldPixelBeAddedByLuminance(pixel, luminanceThreshold, invert)) {
                    final int bitIndex = bytesPerColumn * col + row / 8;
                    rawData[bitIndex] = setBit(rawData[bitIndex], row);
                }
            }
        }

        return rawData;
    }

    private static Bitmap renderBitmap(final Bitmap source, final int width, final int height, final boolean cropTransparentBounds) {
        final Bitmap sourceBitmap = cropTransparentBounds ? cropTransparentBounds(source) : source;
        final Bitmap renderedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        final int sourceWidth = Math.max(1, sourceBitmap.getWidth());
        final int sourceHeight = Math.max(1, sourceBitmap.getHeight());
        final float scale = Math.min((float) width / sourceWidth, (float) height / sourceHeight);
        final int renderWidth = Math.max(1, Math.round(sourceWidth * scale));
        final int renderHeight = Math.max(1, Math.round(sourceHeight * scale));
        final int left = (width - renderWidth) / 2;
        final int top = (height - renderHeight) / 2;

        final Bitmap scaledBitmap;
        if (sourceWidth == renderWidth && sourceHeight == renderHeight) {
            scaledBitmap = sourceBitmap;
        } else {
            scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, renderWidth, renderHeight, true);
        }

        for (int y = 0; y < renderHeight; y++) {
            for (int x = 0; x < renderWidth; x++) {
                renderedBitmap.setPixel(left + x, top + y, scaledBitmap.getPixel(x, y));
            }
        }

        return renderedBitmap;
    }

    private static Bitmap cropTransparentBounds(final Bitmap bitmap) {
        int minX = bitmap.getWidth();
        int minY = bitmap.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) {
                    continue;
                }

                if (x < minX) {
                    minX = x;
                }
                if (y < minY) {
                    minY = y;
                }
                if (x > maxX) {
                    maxX = x;
                }
                if (y > maxY) {
                    maxY = y;
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return bitmap;
        }

        if (minX == 0 && minY == 0 && maxX == bitmap.getWidth() - 1 && maxY == bitmap.getHeight() - 1) {
            return bitmap;
        }

        return Bitmap.createBitmap(bitmap, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static boolean shouldPixelbeAdded(int pixel) {
        return Color.alpha(pixel) >= 0x80;
    }

    private static boolean shouldPixelBeAddedByLuminance(final int pixel, final int luminanceThreshold, final boolean invert) {
        final int alpha = Color.alpha(pixel);
        if (alpha < 0x80) {
            return false;
        }

        final int luminance = (Color.red(pixel) * 54 + Color.green(pixel) * 183 + Color.blue(pixel) * 19) >> 8;
        return invert ? luminance >= luminanceThreshold : luminance <= luminanceThreshold;
    }

    private static byte setBit(byte bits, int position) {
        bits |= 1 << (position % 8);
        return bits;
    }

    private static int getBytesPerColumn(int rowCount) {
        int result = (int) rowCount / 8;
        if (result * 8 < rowCount) {
            result++;
        }

        return result;
    }
}
