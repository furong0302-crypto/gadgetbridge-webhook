/*  Copyright (C) 2024 Gadgetbridge contributors

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

import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.TrackerMoveHands;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;

/**
 * Handles the response to {@code CMD_TRACKER_MOVE_HANDS_GET} (0x09AC).
 *
 * <p>Reads the current tracker move-hands status from the watch and persists it to the
 * device-specific {@link SharedPreferences} under {@link #PREF_MOVE_HANDS}.
 */
public class GetMoveHandsHandler extends AbstractResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetMoveHandsHandler.class);

    /** Device-specific shared-prefs key for the move-hands status (boolean). */
    public static final String PREF_MOVE_HANDS = "withings_scanwatch_move_hands";

    public GetMoveHandsHandler(WithingsBaseDeviceSupport support) {
        super(support);
    }

    @Override
    public void handleResponse(Message response) {
        List<WithingsStructure> data = response.getDataStructures();
        if (data == null || data.isEmpty()) {
            logger.warn("GetMoveHandsHandler: received empty response");
            return;
        }
        for (WithingsStructure structure : data) {
            if (structure instanceof TrackerMoveHands) {
                final boolean enabled = ((TrackerMoveHands) structure).isEnabled();
                logger.info("GetMoveHandsHandler: move-hands = {}", enabled);
                final SharedPreferences prefs =
                        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
                prefs.edit()
                     .putBoolean(PREF_MOVE_HANDS, enabled)
                     .apply();
            }
        }
    }
}
