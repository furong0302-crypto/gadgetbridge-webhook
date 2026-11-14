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

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.User;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;

/**
 * Handles the response to {@code GET_USER} (cmd 0x0503).
 *
 * <p>The userId is pre-populated in device prefs by {@code WithingsBaseDeviceSupport.doSync()}
 * before any commands are built, so all queued commands use the correct userId immediately.
 * This handler updates the stored value with whatever the watch echoes back (which should be
 * the same value we sent in SET_USER).
 */
public class GetUserHandler extends AbstractResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetUserHandler.class);

    public GetUserHandler(WithingsBaseDeviceSupport support) {
        super(support);
    }

    @Override
    public void handleResponse(Message response) {
        final User user = response.getStructureByType(User.class);
        if (user == null) {
            logger.warn("GetUserHandler: no User structure in response");
            return;
        }
        final int userId = user.getUserID();
        if (userId == 0) {
            logger.warn("GetUserHandler: received userId=0, ignoring");
            return;
        }
        logger.info("GetUserHandler: confirmed watch userId=0x{}", Integer.toHexString(userId));
        final SharedPreferences prefs =
                GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
        prefs.edit()
             .putInt(WithingsBaseDeviceSupport.PREF_WITHINGS_USER_ID, userId)
             .apply();
    }
}
