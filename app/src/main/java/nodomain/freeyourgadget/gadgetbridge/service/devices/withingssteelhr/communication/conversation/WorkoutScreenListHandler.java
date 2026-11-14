/*  Copyright (C) 2023-2024 Andreas Shimokawa, Frank Ertl

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
import android.text.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity.WithingsActivityType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WorkoutScreenList;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;

public class WorkoutScreenListHandler extends AbstractResponseHandler {
    private static final Logger LOG = LoggerFactory.getLogger(WorkoutScreenListHandler.class);

    public WorkoutScreenListHandler(WithingsBaseDeviceSupport support) {
        super(support);
    }

    @Override
    public void handleResponse(Message response) {
        List<WithingsStructure> data = response.getDataStructures();
        if (data != null && !data.isEmpty()) {
            WorkoutScreenList screenList = (WorkoutScreenList) data.get(0);
            saveScreenList(screenList);
        }
    }

    private void saveScreenList(WorkoutScreenList screenList) {
        int[] workoutIds = screenList.getWorkoutIds();
        List<String> prefValues = new ArrayList<>();
        for (int i = 0; i < workoutIds.length; i++) {
            int currentId = workoutIds[i];
            if (currentId > 0) {
                WithingsActivityType matchedType = null;
                for (final WithingsActivityType type : WithingsActivityType.values()) {
                    if (type.getCode() == currentId) {
                        matchedType = type;
                        break;
                    }
                }

                if (matchedType == null) {
                    LOG.debug("Ignoring unknown workout id from watch: {}", currentId);
                    continue;
                }

                final String prefValue = matchedType.name().toLowerCase(Locale.ROOT);
                if (!prefValues.contains(prefValue)) {
                    prefValues.add(prefValue);
                }
            }
         }

        String workoutActivityTypes = TextUtils.join(",", prefValues);
        GBDevice device = support.getDevice();
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
        prefs.edit().putString("workout_activity_types_sortable", workoutActivityTypes).apply();
    }
}
