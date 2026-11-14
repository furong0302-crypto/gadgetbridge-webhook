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

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructureType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;

public abstract class AbstractConversation implements Conversation {

    private List<ConversationObserver> observers = new ArrayList();

    private boolean complete;

    protected Message request;

    private short requestType;

    protected ResponseHandler responseHandler;

    public AbstractConversation(ResponseHandler responseHandler) {
        this.responseHandler = responseHandler;
    }

    @Override
    public void registerObserver(ConversationObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(ConversationObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void setRequest(Message message) {
        this.request = message;
        this.requestType = message.getType();
    }

    @Override
    public Message getRequest() {
        return request;
    }

    @Override
    public void handleResponse(Message response) {
        final boolean transferCompleteForEot = request.needsEOT() && response.getType() == WithingsMessageType.TRANSFER_COMPLETE;
        final boolean transferCompleteForDelete = request.needsResponse()
                && requestType == WithingsMessageType.DELETE_STORED_MEASURE_SIGNAL
                && response.getType() == WithingsMessageType.TRANSFER_COMPLETE;
        final boolean transferCompleteForMeasureStart = request.needsResponse()
                && requestType == WithingsMessageType.MEASURE_START
                && response.getType() == WithingsMessageType.TRANSFER_COMPLETE;
        final boolean measureStopForMeasureStart = request.needsEOT()
                && requestType == WithingsMessageType.MEASURE_START
                && response.getType() == WithingsMessageType.MEASURE_STOP;
        if (response.getType() == requestType || transferCompleteForEot || transferCompleteForDelete || transferCompleteForMeasureStart || measureStopForMeasureStart) {
            if (request.needsResponse()) {
                complete = response.getType() == requestType || transferCompleteForDelete || transferCompleteForMeasureStart;
            } else if (request.needsEOT()) {
                complete = transferCompleteForEot || measureStopForMeasureStart || hasEOT(response);
            }

            doHandleResponse(response);
            if (complete) {
                notifyObservers(requestType);
            }
        }
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    protected void notifyObservers(short messageType) {
        for (ConversationObserver observer : observers) {
            observer.onConversationCompleted(messageType);
        }
    }

    private boolean hasEOT(Message message) {
        List<WithingsStructure> dataList = message.getDataStructures();
        if (dataList != null) {
            for (WithingsStructure strucuter :
                    dataList) {
                if (strucuter.getType() == WithingsStructureType.END_OF_TRANSMISSION) {
                    return true;
                }
            }
        }

        return false;
    }

    protected abstract void doSendRequest(Message message);

    protected abstract void doHandleResponse(Message message);
}
