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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.incoming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.GlyphRequestHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;

public class IncomingMessageHandlerFactory {

    private static final Logger logger = LoggerFactory.getLogger(IncomingMessageHandlerFactory.class);
    private final WithingsBaseDeviceSupport support;
    private Map<Short, IncomingMessageHandler> handlers = new HashMap<>();

    public IncomingMessageHandlerFactory(WithingsBaseDeviceSupport support) {
        this.support = support;
    }

    public IncomingMessageHandler getHandler(Message message) {
        IncomingMessageHandler handler = handlers.get(message.getType());
        switch (message.getType()) {
            case WithingsMessageType.START_LIVE_WORKOUT:
            case WithingsMessageType.STOP_LIVE_WORKOUT:
            case WithingsMessageType.GET_WORKOUT_GPS_STATUS:
                if (handler == null) {
                    // Use a single shared handler for all live workout messages to preserve state
                    LiveWorkoutHandler sharedHandler = new LiveWorkoutHandler(support);
                    handlers.put(WithingsMessageType.START_LIVE_WORKOUT, sharedHandler);
                    handlers.put(WithingsMessageType.STOP_LIVE_WORKOUT, sharedHandler);
                    handlers.put(WithingsMessageType.GET_WORKOUT_GPS_STATUS, sharedHandler);
                    handler = sharedHandler;
                }
                break;
            case WithingsMessageType.LIVE_WORKOUT_DATA:
                if (handler == null) {
                    handlers.put(message.getType(), new LiveHeartrateHandler(support));
                }
                break;
            case WithingsMessageType.GET_NOTIFICATION:
                if (handler == null) {
                    handlers.put(message.getType(), new NotificationRequestHandler(support));
                }
                break;
            case WithingsMessageType.GET_UNICODE_GLYPH:
                if (handler == null) {
                    handlers.put(message.getType(), new GlyphRequestHandler(support));
                }
                break;
            case WithingsMessageType.SYNC:
                if (handler == null) {
                    handlers.put(message.getType(), new SyncRequestHandler(support));
                }
                break;
            case WithingsMessageType.MEASURE_START:
            case WithingsMessageType.MEASURE_STOP:
                if (handler == null) {
                    final MeasurementRequestHandler measurementHandler = new MeasurementRequestHandler(support);
                    handlers.put(WithingsMessageType.MEASURE_START, measurementHandler);
                    handlers.put(WithingsMessageType.MEASURE_STOP, measurementHandler);
                }
                break;
            default:
                logger.warn("Unhandled incoming message type: " + message.getType());
        }

        return handlers.get(message.getType());
    }

}
