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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class MessageBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MessageBuilder.class);
    private WithingsBaseDeviceSupport support;
    private MessageFactory messageFactory;
    private ByteArrayOutputStream pendingMessage;
    private Message message;

    public MessageBuilder(WithingsBaseDeviceSupport support, MessageFactory messageFactory) {
        this.support = support;
        this.messageFactory = messageFactory;
    }

    public synchronized boolean buildMessage(byte[] rawData) {
        if (rawData == null || rawData.length == 0) {
            return false;
        }

        if (rawData[0] == 0x01 && !hasPendingIncompleteMessage()) {
            // First byte is 0x01 (version) and no incomplete message is pending:
            // this is the start of a new WPP message.
            pendingMessage = new ByteArrayOutputStream();
        } else if (pendingMessage == null) {
            logger.warn("Received continuation fragment without a pending message header -- discarding {} bytes", rawData.length);
            return false;
        }
        // Otherwise: we have a pending incomplete message, so this chunk is a
        // continuation fragment even if its first byte happens to be 0x01.

        try {
            pendingMessage.write(rawData);
        } catch(IOException e) {
            logger.error("Could not write data to stream: " + StringUtils.bytesToHex(rawData));
            return false;
        }

        if (!isMessageComplete(pendingMessage.toByteArray())) {
            return false;
        } else {
            Message message = messageFactory.createMessageFromRawData(pendingMessage.toByteArray());
            pendingMessage = null;
            if (message == null) {
                logger.info("Cannot handle null message");
                return false;
            }

            this.message = message;
            return true;
        }
    }

    public Message getMessage() {
        return message;
    }

    /**
     * Returns true when a multi-fragment message is being accumulated and
     * the declared payload length has NOT yet been reached.  In that case an
     * incoming chunk whose first byte happens to be 0x01 must be treated as a
     * continuation fragment, not as a new message header.
     *
     * Safety: if the accumulated data already exceeds the declared length
     * (corrupt header / missed fragment), we consider it stale so the caller
     * can start a fresh message.
     */
    private boolean hasPendingIncompleteMessage() {
        if (pendingMessage == null) {
            return false;
        }
        byte[] accumulated = pendingMessage.toByteArray();
        if (accumulated.length < 5) {
            // Still accumulating the header itself.
            return true;
        }
        short totalDataLength = (short) BLETypeConversions.toInt16(accumulated[4], accumulated[3]);
        int dataReceived = accumulated.length - 5;
        if (dataReceived >= totalDataLength) {
            // Already have enough (or too many) bytes -- treat as stale.
            logger.warn("Pending message has {} data bytes but declared {}; treating as stale",
                    dataReceived, totalDataLength);
            pendingMessage = null;
            return false;
        }
        return true;
    }

    private boolean isMessageComplete(byte[] messageData) {
        if (messageData.length < 5) {
            return false;
        }

        short totalDataLength = (short) BLETypeConversions.toInt16(messageData[4], messageData[3]);
        byte[] rawStructureData = Arrays.copyOfRange(messageData, 5, messageData.length);
        if (rawStructureData.length == totalDataLength) {
            return true;
        }

        return false;
    }
}
