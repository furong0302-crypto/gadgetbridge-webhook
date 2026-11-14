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

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.AlarmName;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.AlarmSettings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;

/**
 * Handles the response to {@code CMD_GET_MULTI_ALARM} (0x0146 / 326).
 *
 * <p>Reads alarm TLVs from the watch response, matches them by position with the
 * Gadgetbridge alarm database, and updates the DB so the UI reflects the watch's
 * current state (enabled/disabled, time, repetition, smart wakeup, title).
 *
 * <p>This enables two important behaviors:
 * <ul>
 *   <li>One-time alarms auto-disable after firing (the watch clears the enabled bit)</li>
 *   <li>Alarms toggled on the watch are reflected in Gadgetbridge on next sync</li>
 * </ul>
 */
public class GetAlarmHandler extends AbstractResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetAlarmHandler.class);

    public GetAlarmHandler(WithingsBaseDeviceSupport support) {
        super(support);
    }

    @Override
    public void handleResponse(Message response) {
        List<WithingsStructure> data = response.getDataStructures();
        if (data == null) {
            logger.info("GetAlarmHandler: null data structures in response");
            return;
        }

        // Collect alarm settings and names from the response.
        // Each AlarmSettings may be followed by an AlarmName for the same slot.
        List<AlarmSettings> watchAlarms = new ArrayList<>();
        List<String> watchAlarmNames = new ArrayList<>();

        for (WithingsStructure structure : data) {
            if (structure instanceof AlarmSettings) {
                watchAlarms.add((AlarmSettings) structure);
                // Placeholder name until we see an AlarmName TLV
                watchAlarmNames.add(null);
            } else if (structure instanceof AlarmName) {
                // The AlarmName belongs to the most recently seen AlarmSettings
                if (!watchAlarmNames.isEmpty()) {
                    watchAlarmNames.set(watchAlarmNames.size() - 1, ((AlarmName) structure).getName());
                }
            }
        }

        logger.info("GetAlarmHandler: watch reported {} alarm(s)", watchAlarms.size());

        // Get the current alarms from the GB database (ordered by position)
        List<Alarm> dbAlarms = DBHelper.getAlarms(device);

        boolean changed = false;

        for (int i = 0; i < dbAlarms.size(); i++) {
            Alarm dbAlarm = dbAlarms.get(i);

            if (i < watchAlarms.size()) {
                AlarmSettings watchAlarm = watchAlarms.get(i);
                String name = watchAlarmNames.get(i);
                boolean watchEnabled = watchAlarm.isEnabled();
                int watchRepetition = watchAlarm.getRepetitionMask();
                int watchHour = watchAlarm.getHour();
                int watchMinute = watchAlarm.getMinute();
                int watchSmartWake = watchAlarm.getSmartWakeupMinutes();

                // Update enabled state
                if (dbAlarm.getEnabled() != watchEnabled) {
                    logger.info("GetAlarmHandler: alarm {} enabled {} -> {}",
                            i, dbAlarm.getEnabled(), watchEnabled);
                    dbAlarm.setEnabled(watchEnabled);
                    changed = true;
                }

                // Update time
                if (dbAlarm.getHour() != watchHour || dbAlarm.getMinute() != watchMinute) {
                    logger.info("GetAlarmHandler: alarm {} time {}:{} -> {}:{}",
                            i, dbAlarm.getHour(), dbAlarm.getMinute(), watchHour, watchMinute);
                    dbAlarm.setHour(watchHour);
                    dbAlarm.setMinute(watchMinute);
                    changed = true;
                }

                // Update repetition
                if (dbAlarm.getRepetition() != watchRepetition) {
                    logger.info("GetAlarmHandler: alarm {} repetition {} -> {}",
                            i, dbAlarm.getRepetition(), watchRepetition);
                    dbAlarm.setRepetition(watchRepetition);
                    changed = true;
                }

                // Update smart wakeup
                if (watchSmartWake > 0) {
                    if (!dbAlarm.getSmartWakeup() || !Integer.valueOf(watchSmartWake).equals(dbAlarm.getSmartWakeupInterval())) {
                        dbAlarm.setSmartWakeup(true);
                        dbAlarm.setSmartWakeupInterval(watchSmartWake);
                        changed = true;
                    }
                } else {
                    if (dbAlarm.getSmartWakeup()) {
                        dbAlarm.setSmartWakeup(false);
                        changed = true;
                    }
                }

                // Update title
                if (name != null && !name.equals(dbAlarm.getTitle())) {
                    dbAlarm.setTitle(name);
                    changed = true;
                }

                if (changed) {
                    DBHelper.store(dbAlarm);
                }
            } else {
                // More DB alarms than watch alarms - disable any extras
                if (dbAlarm.getEnabled()) {
                    logger.info("GetAlarmHandler: alarm {} not on watch, disabling", i);
                    dbAlarm.setEnabled(false);
                    DBHelper.store(dbAlarm);
                    changed = true;
                }
            }
        }

        if (changed) {
            // Broadcast to refresh the alarm UI
            Intent intent = new Intent(DeviceService.ACTION_SAVE_ALARMS);
            LocalBroadcastManager.getInstance(GBApplication.getContext()).sendBroadcast(intent);
            logger.info("GetAlarmHandler: alarm database updated, UI refresh broadcast sent");
        }
    }
}
