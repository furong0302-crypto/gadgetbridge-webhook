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

import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractWithingsActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity.SleepActivitySampleHelper;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.LiveHeartRate;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;

public class LiveHeartrateHandler implements IncomingMessageHandler {
    private static final Logger logger = LoggerFactory.getLogger(LiveHeartrateHandler.class);
    private final WithingsBaseDeviceSupport support;

    public LiveHeartrateHandler(WithingsBaseDeviceSupport support) {
        this.support = support;
    }


    @Override
    public void handleMessage(Message message) {
        List<WithingsStructure> data = message.getDataStructures();
        if (data == null || data.isEmpty()) {
            return;
        }

        WithingsStructure structure = data.get(0);
        int heartRate = 0;
        if (structure instanceof LiveHeartRate) {
            heartRate = ((LiveHeartRate)structure).getHeartrate();
        }

        if (heartRate > 0) {
            saveHeartRateData(heartRate);
        }

    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractWithingsActivitySample> void saveHeartRateData(final int heartRate) {
        logger.debug("Received live heart rate {}", heartRate);
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            final Long userId = Objects.requireNonNull(DBHelper.getUser(dbHandler.getDaoSession()).getId());
            final Long deviceId = Objects.requireNonNull(DBHelper.getDevice(support.getDevice(), dbHandler.getDaoSession()).getId());
            final AbstractSampleProvider<T> provider =
                    (AbstractSampleProvider<T>) support.createSampleProvider(support.getDevice(), dbHandler.getDaoSession());
            T sample = provider.createActivitySample();
            sample.setTimestamp((int) (GregorianCalendar.getInstance().getTimeInMillis() / 1000L));
            sample.setHeartRate(heartRate);
            sample.setDeviceId(deviceId);
            sample.setUserId(userId);
            sample = SleepActivitySampleHelper.mergeIfNecessary(provider, sample);
            provider.addGBActivitySample(sample);
            final Intent intent = new Intent(DeviceService.ACTION_REALTIME_SAMPLES)
                    .putExtra(GBDevice.EXTRA_DEVICE, support.getDevice())
                    .putExtra(DeviceService.EXTRA_REALTIME_SAMPLE, (Serializable) sample);
            LocalBroadcastManager.getInstance(support.getContext()).sendBroadcast(intent);
        } catch (final Exception ex) {
            logger.error("Error saving current heart rate", ex);
        }
    }
}
