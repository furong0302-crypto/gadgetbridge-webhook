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
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.LuminosityLevel;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;

/**
 * Handles the response to {@code CMD_GET_LUMINOSITY_LEVEL} (0x0942).
 *
 * <p>Reads the current brightness mode and level from the watch and persists them to
 * the device-specific {@link SharedPreferences}.
 */
public class GetLuminosityHandler extends AbstractResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetLuminosityHandler.class);

    public static final String PREF_AUTO_BRIGHTNESS = "withings_scanwatch_auto_brightness";
    public static final String PREF_BRIGHTNESS_LEVEL = "withings_scanwatch_brightness_level";

    public GetLuminosityHandler(WithingsBaseDeviceSupport support) {
        super(support);
    }

    @Override
    public void handleResponse(Message response) {
        List<WithingsStructure> data = response.getDataStructures();
        if (data == null || data.isEmpty()) {
            logger.warn("GetLuminosityHandler: received empty response");
            return;
        }
        for (WithingsStructure structure : data) {
            if (structure instanceof LuminosityLevel) {
                final LuminosityLevel lum = (LuminosityLevel) structure;
                final boolean autoMode = lum.isAutoMode();
                final int level = lum.getLevel() & 0xFF;
                logger.info("GetLuminosityHandler: auto={}, level={}", autoMode, level);
                final SharedPreferences prefs =
                        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
                prefs.edit()
                     .putBoolean(PREF_AUTO_BRIGHTNESS, autoMode)
                     .putString(PREF_BRIGHTNESS_LEVEL, String.valueOf(level))
                     .apply();
            }
        }
    }
}
