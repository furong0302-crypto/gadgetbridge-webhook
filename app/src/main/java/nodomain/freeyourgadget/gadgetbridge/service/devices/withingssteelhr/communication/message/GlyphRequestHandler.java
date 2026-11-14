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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.IconHelper;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.GlyphId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ImageData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ImageMetaData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.incoming.IncomingMessageHandler;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class GlyphRequestHandler implements IncomingMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlyphRequestHandler.class);
    private static final int EMOJI_LUMINANCE_THRESHOLD = 110;
    private static final boolean EMOJI_LUMINANCE_INVERT = true;
    private final WithingsBaseDeviceSupport support;

    public GlyphRequestHandler(WithingsBaseDeviceSupport support) {
        this.support = support;
    }

    @Override
    public void handleMessage(Message message) {
        try {
            ImageMetaData requestedMetaData = message.getStructureByType(ImageMetaData.class);

            // Collect all GlyphId structures from the request (watch can request multiple glyphs)
            List<GlyphId> glyphIds = new ArrayList<>();
            for (WithingsStructure structure : message.getDataStructures()) {
                if (structure instanceof GlyphId) {
                    glyphIds.add((GlyphId) structure);
                }
            }

            int requestedHeight = requestedMetaData.getHeight() & 0xFF;
            int requestedWidth = requestedMetaData.getWidth() & 0xFF;

            for (GlyphId glyphId : glyphIds) {
                Message reply = new WithingsMessage((short) (WithingsMessageType.GET_UNICODE_GLYPH | 0x4000));
                reply.addDataStructure(glyphId);

                GlyphRenderResult renderResult = createUnicodeImage(glyphId.getUnicode(), requestedWidth, requestedHeight);

                // Set response metadata with the actual rendered width (may be narrower than requested)
                ImageMetaData responseMetaData = new ImageMetaData();
                responseMetaData.setIndex(requestedMetaData.getIndex());
                responseMetaData.setWidth((byte) renderResult.width);
                responseMetaData.setHeight((byte) requestedHeight);
                reply.addDataStructure(responseMetaData);

                // Chunk image data into 64-byte blocks, matching official app behavior
                List<byte[]> imageChunks = IconHelper.splitImageData(renderResult.data, 64);
                for (int i = 0; i < imageChunks.size(); i++) {
                    ImageData imageDataStructure = new ImageData();
                    imageDataStructure.setImageData(imageChunks.get(i));
                    if (i == imageChunks.size() - 1) {
                        imageDataStructure.setEndOfMessage(true);
                    }
                    reply.addDataStructure(imageDataStructure);
                }

                logger.info("Sending reply to glyph request for U+{}: width={}, height={}, dataLen={}",
                        String.format("%04X", glyphId.getUnicode()), renderResult.width, requestedHeight,
                        renderResult.data.length);
                support.sendToDevice(reply);
            }
        } catch (Exception e) {
            logger.error("Failed to respond to glyph request.", e);
            GB.toast("Failed to respond to glyph request:" + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.WARN);
        }
    }

    private static class GlyphRenderResult {
        final byte[] data;
        final int width;

        GlyphRenderResult(byte[] data, int width) {
            this.data = data;
            this.width = width;
        }
    }

    private GlyphRenderResult createUnicodeImage(long unicode, int maxWidth, int height) {
        String str = new String(Character.toChars((int) unicode));
        Paint paint = new Paint();
        paint.setTypeface(null);
        Rect rect = new Rect();
        paint.setTextSize(calculateTextsize(paint, height));
        paint.setAntiAlias(true);
        paint.getTextBounds(str, 0, str.length(), rect);
        paint.setColor(-1);
        Paint.FontMetricsInt fontMetricsInt = paint.getFontMetricsInt();
        int renderedWidth = rect.width();
        if (renderedWidth <= 0) {
            // Even for zero-width characters, produce a 1-pixel-wide empty glyph
            renderedWidth = 1;
        }
        // Cap width to the requested maximum
        int actualWidth = Math.min(renderedWidth, maxWidth);
        Bitmap createBitmap = Bitmap.createBitmap(actualWidth, height, Bitmap.Config.ARGB_8888);
        new Canvas(createBitmap).drawText(str, -rect.left, -fontMetricsInt.top, paint);
        final byte[] imageData;
        if (isLikelyEmoji(unicode)) {
            imageData = IconHelper.toLuminanceThresholdByteArray(createBitmap, EMOJI_LUMINANCE_THRESHOLD, EMOJI_LUMINANCE_INVERT);
        } else {
            imageData = IconHelper.toByteArray(createBitmap);
        }
        return new GlyphRenderResult(imageData, actualWidth);
    }

    private boolean isLikelyEmoji(final long unicode) {
        return (unicode >= 0x1F000 && unicode <= 0x1FAFF)
                || (unicode >= 0x2600 && unicode <= 0x27BF)
                || (unicode >= 0xFE00 && unicode <= 0xFE0F);
    }

    private int calculateTextsize(Paint paint, int height) {
        Paint.FontMetricsInt fontMetricsInt;
        int textsize = 0;
        do {
            textsize++;
            paint.setTextSize(textsize);
            fontMetricsInt = paint.getFontMetricsInt();
        } while (fontMetricsInt.bottom - fontMetricsInt.top < height);
        return textsize - 1;
    }
}
