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
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ShortcutAction;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;

/**
 * Handles the response to {@code GET_SHORTCUT} (cmd 0x0992).
 *
 * <p>Reads the current long-press crown shortcut action from the watch and persists it to the
 * device-specific {@link SharedPreferences} under
 * {@link #PREF_SHORTCUT_ACTION} so that the UI can display the current value.
 */
public class GetShortcutHandler extends AbstractResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetShortcutHandler.class);

    /** Device-specific shared-prefs key for the shortcut action byte (stored as int). */
    public static final String PREF_SHORTCUT_ACTION = "withings_scanwatch_shortcut_action";

    public GetShortcutHandler(WithingsBaseDeviceSupport support) {
        super(support);
    }

    @Override
    public void handleResponse(Message response) {
        List<WithingsStructure> data = response.getDataStructures();
        if (data == null || data.isEmpty()) {
            logger.warn("GetShortcutHandler: received empty response");
            return;
        }
        for (WithingsStructure structure : data) {
            if (structure instanceof ShortcutAction) {
                final byte action = ((ShortcutAction) structure).getAction();
                logger.info("GetShortcutHandler: current shortcut action = {}", action);
                final SharedPreferences prefs =
                        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
                prefs.edit()
                     .putString(PREF_SHORTCUT_ACTION, String.valueOf(action))
                     .apply();
            }
        }
    }
}
