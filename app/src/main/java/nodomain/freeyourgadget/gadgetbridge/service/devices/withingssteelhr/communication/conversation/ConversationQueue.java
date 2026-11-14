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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation;

import android.os.Handler;
import android.os.Looper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;

public class ConversationQueue implements ConversationObserver
{
    private static final Logger logger = LoggerFactory.getLogger(ConversationQueue.class);
    private static final long CONVERSATION_TIMEOUT_MS = 15_000; // 15 seconds
    private final LinkedList<Conversation> queue = new LinkedList<>();
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = this::onConversationTimeout;
    private WithingsBaseDeviceSupport support;
    private Conversation activeConversation;
    private long activeConversationStartTime;

    public ConversationQueue(WithingsBaseDeviceSupport support) {
        this.support = support;
    }

    @Override
    public synchronized void onConversationCompleted(short conversationType) {
        if (activeConversation != null
                && activeConversation.getRequest() != null
                && activeConversation.getRequest().getType() == conversationType) {
            queue.remove(activeConversation);
            activeConversation = null;
        } else {
            logger.warn("Ignoring completion for non-active conversation type={} (0x{})",
                    conversationType, Integer.toHexString(conversationType & 0xffff));
            return;
        }
        cancelTimeout();
        send();
    }

    public synchronized void clear() {
        queue.clear();
        activeConversation = null;
        cancelTimeout();
    }

    private void scheduleTimeout() {
        cancelTimeout();
        timeoutHandler.postDelayed(timeoutRunnable, CONVERSATION_TIMEOUT_MS + 500);
    }

    private void cancelTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
    }

    private void onConversationTimeout() {
        send(); // send() already checks elapsed time and force-completes stale conversations
    }

    public synchronized void send() {
        if (!support.isConnected()) {
            logger.debug("Device is not connected, dropping queued messages.");
            clear();
            return;
        }

        logger.debug("Sending of queued messages has been requested.");
        if (activeConversation != null && !activeConversation.isComplete()) {
            long elapsed = System.currentTimeMillis() - activeConversationStartTime;
            if (elapsed < CONVERSATION_TIMEOUT_MS) {
                final Message activeRequest = activeConversation.getRequest();
                if (activeRequest != null) {
                    logger.debug("A conversation is already active: type={} (0x{}) -- elapsed {}ms",
                            activeRequest.getType(), Integer.toHexString(activeRequest.getType() & 0xffff), elapsed);
                }
                return;
            }
            // WPP has no request identifier. Advancing here would allow a late response to
            // complete the next queued request when multiple requests share a command type.
            final Message activeRequest = activeConversation.getRequest();
            final short requestType = activeRequest != null ? activeRequest.getType() : -1;
            logger.warn("Active conversation timed out after {}ms: type={} (0x{}) -- aborting connection",
                    elapsed,
                    requestType,
                    activeRequest != null ? Integer.toHexString(requestType & 0xffff) : "?");
            clear();
            support.onConversationTimeout(requestType);
            return;
        }

        if (!queue.isEmpty()) {
            Conversation nextInLine = queue.peek();
            if (nextInLine!= null) {
                activeConversation = nextInLine;
                activeConversationStartTime = System.currentTimeMillis();
                logger.debug("Sending next queued message type={} (0x{})", nextInLine.getRequest().getType(), Integer.toHexString(nextInLine.getRequest().getType() & 0xffff));
                Message request = nextInLine.getRequest();
                scheduleTimeout();
                support.sendToDevice(request);
            }
        } else {
            logger.debug("Queue is empty, nothing to send.");
        }
    }

    public synchronized void addConversation(Conversation conversation) {
        if (conversation == null) {
            return;
        }

        if (conversation.getRequest().needsResponse() || conversation.getRequest().needsEOT()) {
            logger.debug("addConversation: queuing type={} (0x{}) needsResponse={} needsEOT={}", conversation.getRequest().getType(), Integer.toHexString(conversation.getRequest().getType() & 0xffff), conversation.getRequest().needsResponse(), conversation.getRequest().needsEOT());
            queue.add(conversation);
            conversation.registerObserver(this);
        } else {
            logger.debug("addConversation: fire-and-forget type={} (0x{})", conversation.getRequest().getType(), Integer.toHexString(conversation.getRequest().getType() & 0xffff));
            support.sendToDevice(conversation.getRequest());
        }
    }

    public synchronized void addConversationFirst(Conversation conversation) {
        if (conversation == null) {
            return;
        }

        if (conversation.getRequest().needsResponse() || conversation.getRequest().needsEOT()) {
            logger.debug("addConversationFirst: queuing type={} (0x{}) needsResponse={} needsEOT={}", conversation.getRequest().getType(), Integer.toHexString(conversation.getRequest().getType() & 0xffff), conversation.getRequest().needsResponse(), conversation.getRequest().needsEOT());

            // Insert after the active conversation if there is one, otherwise at the very front
            if (activeConversation != null && queue.peek() == activeConversation) {
                queue.add(1, conversation);
            } else {
                queue.addFirst(conversation);
            }

            conversation.registerObserver(this);
        } else {
            logger.debug("addConversationFirst: fire-and-forget type={} (0x{})", conversation.getRequest().getType(), Integer.toHexString(conversation.getRequest().getType() & 0xffff));
            support.sendToDevice(conversation.getRequest());
        }
    }

    public synchronized void processResponse(Message response) {
        Conversation conversation = matchesActiveConversation(response) ? activeConversation : null;

        if (conversation == null && response.getType() == WithingsMessageType.TRANSFER_COMPLETE) {
            conversation = getActiveEotConversation();
            if (conversation != null) {
                logger.debug("processResponse: remapping transfer-complete type=0x100 to pending EOT conversation type={} (0x{})",
                        conversation.getRequest().getType(), Integer.toHexString(conversation.getRequest().getType() & 0xffff));
            }
        }

        if (conversation == null && response.getType() == WithingsMessageType.TRANSFER_COMPLETE) {
            final Conversation head = activeConversation;
            if (head != null && head.getRequest() != null
                    && head.getRequest().getType() == WithingsMessageType.MEASURE_START
                    && head.getRequest().needsResponse()) {
                logger.debug("processResponse: remapping transfer-complete type=0x100 to pending measure-start conversation type=0x973");
                conversation = head;
            }
        }

        if (conversation == null && response.getType() == WithingsMessageType.MEASURE_STOP) {
            final Conversation head = activeConversation;
            if (head != null && head.getRequest() != null
                    && head.getRequest().getType() == WithingsMessageType.MEASURE_START
                    && head.getRequest().needsEOT()) {
                logger.debug("processResponse: remapping measure-stop type=0x974 to pending measure-start conversation type=0x973");
                conversation = head;
            }
        }

        if (conversation != null) {
            logger.debug("processResponse: matched conversation for type={}", response.getType());
            if (conversation == activeConversation) {
                // Reset timeout -- the watch is still responding to this conversation
                activeConversationStartTime = System.currentTimeMillis();
                scheduleTimeout();
            }
            conversation.handleResponse(response);
        } else {
            logger.warn("processResponse: no conversation found for type={} (0x{}) -- message dropped!", response.getType(), Integer.toHexString(response.getType() & 0xffff));
        }
    }

    private synchronized Conversation getConversation(short requestType) {
        for (Conversation conversation : queue) {
            if (conversation.getRequest() != null && conversation.getRequest().getType() == requestType) {
                return conversation;
            }
        }

        return null;
    }

    public synchronized boolean matchesActiveConversation(final Message response) {
        if (activeConversation == null || activeConversation.getRequest() == null) {
            return false;
        }

        final Message request = activeConversation.getRequest();
        final short requestType = request.getType();
        final short responseType = response.getType();

        if (responseType == requestType) {
            return true;
        }

        if (request.needsEOT() && responseType == WithingsMessageType.TRANSFER_COMPLETE) {
            return true;
        }

        if (request.needsResponse()
                && requestType == WithingsMessageType.DELETE_STORED_MEASURE_SIGNAL
                && responseType == WithingsMessageType.TRANSFER_COMPLETE) {
            return true;
        }

        if (request.needsResponse()
                && requestType == WithingsMessageType.MEASURE_START
                && responseType == WithingsMessageType.TRANSFER_COMPLETE) {
            return true;
        }

        return request.needsEOT()
                && requestType == WithingsMessageType.MEASURE_START
                && responseType == WithingsMessageType.MEASURE_STOP;
    }

    private synchronized Conversation getActiveEotConversation() {
        if (activeConversation != null
                && activeConversation.getRequest() != null
                && (activeConversation.getRequest().needsEOT()
                || activeConversation.getRequest().getType() == WithingsMessageType.DELETE_STORED_MEASURE_SIGNAL)) {
            return activeConversation;
        }
        return null;
    }
}
