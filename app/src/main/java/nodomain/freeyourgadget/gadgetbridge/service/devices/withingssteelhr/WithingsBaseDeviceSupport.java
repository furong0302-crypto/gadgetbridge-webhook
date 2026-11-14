/*  Copyright (C) 2023-2024 Ascense, Frank Ertl

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.DistanceUnit;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.util.AlarmUtils;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.ServerTransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.activity.WithingsActivityType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.WithingsUUIDs;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.ActivitySampleHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.BatteryStateHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.Conversation;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.ConversationQueue;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.GetAlarmHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.HeartRateHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.ResponseHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.SetupFinishedHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.SimpleConversation;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.SyncFinishedHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.ScreenSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.StoredMeasureSignalHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.WithingsEcgHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.conversation.WorkoutScreenListHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ActivityTarget;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.AlarmName;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.AlarmSettings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.AlarmStatus;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.AncsStatus;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.DataStructureFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.EndOfTransmission;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.FeatureTagDeprecated;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.FeatureTagsUserId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.GetActivitySamples;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ImageData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ImageMetaData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.Locale;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.MoveHand;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.Probe;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ProbeOsVersion;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.ScreenSettings;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalMeta;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsScreenId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.Time;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.TypeVersion;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.VasistasType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.User;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.UserUnit;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.UserUnitConstants;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WorkoutScreen;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructureType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.ExpectedResponse;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.Message;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.MessageBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.MessageFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.SimpleHexToByteMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.WithingsMessageType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.incoming.IncomingMessageHandler;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.message.incoming.IncomingMessageHandlerFactory;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.notification.AncsConstants;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.notification.GetNotificationAttributes;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.notification.GetNotificationAttributesResponse;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.notification.NotificationProvider;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.notification.NotificationSource;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_LANGUAGE;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_LANGUAGE_AUTO;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_NOTIFICATION_ENABLE;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_TIMEFORMAT;

public abstract class WithingsBaseDeviceSupport extends AbstractBTLESingleDeviceSupport {

    private static final Logger logger = LoggerFactory.getLogger(WithingsBaseDeviceSupport.class);
    public static final String LAST_ACTIVITY_SYNC = "lastActivitySync";
    private static final long ACTIVITY_SYNC_OVERLAP_MILLIS = 6L * 60L * 60L * 1000L;
    private static final long MIN_SYNC_TRIGGER_INTERVAL_MS = 15_000L;
    public static final String HANDS_CALIBRATION_CMD = "withings_hands_calibration";
    public static final String START_HANDS_CALIBRATION_CMD = "start_withings_hands_calibration";
    public static final String STOP_HANDS_CALIBRATION_CMD = "stop_withings_hands_calibration";
    private static final String LEGACY_PREF_WITHINGS_SPO2_SKIP_CURSOR = "withings_spo2_skip_cursor";
    /**
     * Device-specific SharedPreferences key for the userId sent to the watch in SET_USER.
     * Used by ScreenSettings and FeatureTagsUserId to ensure a consistent, non-zero userId.
     */
    public static final String PREF_WITHINGS_USER_ID = "withings_user_id";
    private final MessageBuilder messageBuilder;
    private ActivitySampleHandler activitySampleHandler;
    private final ConversationQueue conversationQueue;
    private boolean firstTimeConnect;
    private boolean ancsAwaitingCccReadyEnable;
    private boolean ancsNotificationSourceSubscribed;
    private boolean ancsDataSourceSubscribed;
    private boolean withingsProtocolReady;
    private BluetoothGattCharacteristic notificationSourceCharacteristic;
    private BluetoothGattCharacteristic dataSourceCharacteristic;

    /**
     * Creates a device-specific sample provider for storing activity data.
     * Subclasses return the provider appropriate for their database table.
     */
    public abstract nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider<? extends nodomain.freeyourgadget.gadgetbridge.entities.AbstractWithingsActivitySample> createSampleProvider(nodomain.freeyourgadget.gadgetbridge.impl.GBDevice device, nodomain.freeyourgadget.gadgetbridge.entities.DaoSession session);

    /**
     * Returns the BLE UUID set for this device variant.
     * Steel HR uses suffix "0037"; Scanwatch uses suffix "005d".
     */
    protected abstract WithingsUUIDs getWithingsUUIDs();

    protected Collection<WithingsUUIDs> getWithingsUUIDCandidates() {
        return Collections.singleton(getWithingsUUIDs());
    }

    protected WithingsUUIDs getActiveWithingsUUIDs() {
        for (final WithingsUUIDs withingsUUIDs : getWithingsUUIDCandidates()) {
            if (getCharacteristic(withingsUUIDs.WITHINGS_WRITE_CHARACTERISTIC_UUID) != null) {
                return withingsUUIDs;
            }
        }

        return getWithingsUUIDs();
    }

    private boolean syncInProgress;
    private int storedMeasureDeleteRequestId;
    private int storedMeasureDeleteAttempts;
    private WithingsEcgHandler withingsEcgHandler;
    private long lastSyncTriggerTimestamp;
    private String lastSyncTriggerSource;
    private final ActivityUser activityUser;
    private final NotificationProvider notificationProvider;
    private final IncomingMessageHandlerFactory incomingMessageHandlerFactory;
    private final Handler backgroundTasksHandler = new Handler(Looper.getMainLooper());
    // --- ANCS stall watchdog ---
    // Track the latest pending event generation acknowledged by a Control Point
    // request. If a newer pending event remains unrequested past the threshold, the
    // watch's ANCS client is stalled and the connection needs recovery.
    private volatile long lastControlPointWriteMs;
    private volatile long lastAcknowledgedNotificationMs;
    /**
     * How long (ms) after sending an ADDED event to wait for a Control Point request
     * before declaring an ANCS stall.  The watch normally issues CP within ~200ms.
     * Thirty seconds is far above the normal sub-second response while limiting how
     * long notifications remain blocked behind a stale ANCS session.
     */
    private static final long ANCS_STALL_THRESHOLD_MS = 30_000;
    /**
     * Minimum interval between ANCS recovery attempts to avoid hammering.
     */
    private static final long ANCS_RECOVERY_COOLDOWN_MS = 180_000;
    private volatile long lastAncsRecoveryAttemptMs;
    private final Runnable ancsStallCheckRunnable = this::checkAncsStall;

    public WithingsBaseDeviceSupport() {
        super(logger);
        conversationQueue = new ConversationQueue(this);
        notificationProvider = new NotificationProvider(this);
        messageBuilder = new MessageBuilder(this, new MessageFactory(new DataStructureFactory()));
        incomingMessageHandlerFactory = new IncomingMessageHandlerFactory(this);
        for (final WithingsUUIDs withingsUUIDs : getWithingsUUIDCandidates()) {
            addSupportedService(withingsUUIDs.WITHINGS_SERVICE_UUID);
        }
        addSupportedService(GattService.SERVICE_GAP.uuid);
        addSupportedService(GattService.SERVICE_GATT.uuid);
        addANCSService();
        activityUser = new ActivityUser();

        IntentFilter commandFilter = new IntentFilter(HANDS_CALIBRATION_CMD);
        commandFilter.addAction(START_HANDS_CALIBRATION_CMD);
        commandFilter.addAction(STOP_HANDS_CALIBRATION_CMD);
        final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() == null) {
                    return;
                }

                switch (intent.getAction()) {
                    case HANDS_CALIBRATION_CMD:
                        MoveHand moveHand = new MoveHand();
                        moveHand.setHand(intent.getShortExtra("hand", (short) 1));
                        moveHand.setMovement(intent.getShortExtra("movementAmount", (short) 1));
                        sendToDevice(new WithingsMessage(WithingsMessageType.MOVE_HAND, moveHand));
                        break;
                    case START_HANDS_CALIBRATION_CMD:
                        sendToDevice(new WithingsMessage(WithingsMessageType.START_HANDS_CALIBRATION));
                        break;
                    case STOP_HANDS_CALIBRATION_CMD:
                        sendToDevice(new WithingsMessage(WithingsMessageType.STOP_HANDS_CALIBRATION));
                        break;
                }
            }
        };

        LocalBroadcastManager.getInstance(GBApplication.getContext()).registerReceiver(commandReceiver, commandFilter);
    }

    @Override
    public void dispose() {
        synchronized (ConnectionMonitor) {
            ancsAwaitingCccReadyEnable = false;
            resetAncsSubscriptionState("dispose");
            backgroundTasksHandler.removeCallbacksAndMessages(null);
            if (conversationQueue != null) {
                conversationQueue.clear();
            }

            super.dispose();
        }
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        logger.debug("Starting initialization...");
        conversationQueue.clear();
        builder.setDeviceState(GBDevice.State.INITIALIZING);
        getDevice().setFirmwareVersion("N/A");
        getDevice().setFirmwareVersion2("N/A");

        // Delay initialization with 2 seconds to give the watch time to settle
        backgroundTasksHandler.removeCallbacksAndMessages(null);
        backgroundTasksHandler.postDelayed(this::postConnectInitialization, 2000);

        return builder;
    }

    private void postConnectInitialization() {
        logger.debug("postConnectInitialization: requesting notification enable and MTU change");
        final TransactionBuilder builder = createTransactionBuilder("delayed initialization");
        builder.notify(getActiveWithingsUUIDs().WITHINGS_WRITE_CHARACTERISTIC_UUID, true);
        builder.requestMtu(512);
        builder.queue();
    }

    @Override
    public boolean connectFirstTime() {
        firstTimeConnect = true;
        return connect();
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
        if (status != BluetoothGatt.GATT_SUCCESS) {
            logger.error("Failed to change mtu - disconnecting");
            disconnect();
            return;
        }

        logger.debug("MTU has changed to {}", mtu);
        if (firstTimeConnect) {
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.INITIAL_CONNECT));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_LOCALE, getLocale()));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.START_HANDS_CALIBRATION));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.STOP_HANDS_CALIBRATION));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_TIME, new Time()));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_USER_UNIT, new UserUnit(UserUnitConstants.DISTANCE, getUnit())));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_USER_UNIT, new UserUnit(UserUnitConstants.CLOCK_MODE, getTimeMode())));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_ACTIVITY_TARGET, new ActivityTarget(ActivityTarget.GOAL_TYPE_STEPS, activityUser.getStepsGoal())));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_ANCS_STATUS));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_BATTERY_STATUS), new BatteryStateHandler(this));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SETUP_FINISHED), new SetupFinishedHandler(this));
        } else {
            Message message = new WithingsMessage(WithingsMessageType.PROBE);
            message.addDataStructure(new Probe((short) 1, (short) 1, 5100401));
            message.addDataStructure(new ProbeOsVersion((short) Build.VERSION.SDK_INT));
            conversationQueue.clear();
            addSimpleConversationToQueue(message, new AuthenticationHandler(this));
        }

        conversationQueue.send();
    }

    public void doSync() {
        doSync("unspecified");
    }

    public void doSync(final String triggerSource) {
        if (syncInProgress) {
            logger.debug("Ignoring sync trigger '{}' because sync is already in progress", triggerSource);
            return;
        }

        // Guard against running a full sync while the device is still initializing
        // (i.e., auth/PROBE flow is in progress).  The watch sometimes sends a SYNC
        // request before responding to our PROBE -- if we run doSync() now, the
        // conversationQueue.clear() below will destroy the pending PROBE/auth
        // conversation, and the device will be stuck in INITIALIZING forever.
        // After auth completes, onAuthenticationFinished() will call doSync("post-auth").
        if (getDevice().isInitializing() && !"post-auth".equals(triggerSource)) {
            logger.info("Deferring sync trigger '{}' -- device is still initializing (auth in progress). " +
                    "Sync will run automatically after auth completes.", triggerSource);
            return;
        }

        final long now = System.currentTimeMillis();
        final boolean isWatchSyncRequest = "watch-sync-request".equals(triggerSource);
        if (!isWatchSyncRequest && now - lastSyncTriggerTimestamp < MIN_SYNC_TRIGGER_INTERVAL_MS) {
            logger.info("Ignoring duplicate sync trigger '{}' {} ms after '{}'", triggerSource, now - lastSyncTriggerTimestamp, lastSyncTriggerSource);
            return;
        }

        lastSyncTriggerTimestamp = now;
        lastSyncTriggerSource = triggerSource;

        activitySampleHandler = new ActivitySampleHandler(this);
        conversationQueue.clear();
        if (withingsEcgHandler != null) {
            withingsEcgHandler.reset();
        }

        try {
            getDevice().setBusyTask(R.string.busy_task_syncing, getContext());
            getDevice().sendDeviceUpdateIntent(getContext());
            syncInProgress = true;
            final boolean shouldSync = shouldSync(triggerSource);
            logger.info("Starting Withings sync, trigger={}, shouldSync={}", triggerSource, shouldSync);
            if (withingsEcgHandler == null) {
                withingsEcgHandler = createEcgHandler();
            }
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.INITIAL_CONNECT));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_ANCS_STATUS));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_BATTERY_STATUS), new BatteryStateHandler(this));
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_TIME, new Time()));

            if (shouldSync) {
                logger.debug("Doing full sync... trigger={}", triggerSource);
                storedMeasureDeleteRequestId = 0;
                storedMeasureDeleteAttempts = 0;
                GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).edit()
                        .remove(LEGACY_PREF_WITHINGS_SPO2_SKIP_CURSOR)
                        .apply();
                final User user = getUser();
                // Store the userId we're about to send to the watch so that all subsequent
                // commands built in addExtraSyncCommands() (ScreenSettings, FeatureTagsUserId, etc.)
                // can read a consistent, non-zero userId from prefs.
                GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).edit()
                        .putInt(PREF_WITHINGS_USER_ID, user.getUserID())
                        .apply();
                WithingsMessage message = new WithingsMessage(WithingsMessageType.SET_USER);
                message.addDataStructure(user);
                // The UserSecret appears in the original communication with the HealthMate app. Until now GB works without the secret.
                // This makes the "authentication" far easier. However if it turns out that this is needed, we would need to find a way to savely store a unique generated secret.
                //  message.addDataStructure(new UserSecret());
                addSimpleConversationToQueue(message);
                addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_ACTIVITY_TARGET, new ActivityTarget(ActivityTarget.GOAL_TYPE_STEPS, activityUser.getStepsGoal())));
                addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_USER_UNIT, new UserUnit(UserUnitConstants.DISTANCE, getUnit())));
                addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_USER_UNIT, new UserUnit(UserUnitConstants.CLOCK_MODE, getTimeMode())));
                addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_ALARM_SETTINGS));
                addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_SCREEN_SETTINGS), new ScreenSettingsHandler(this));
                addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_MULTI_ALARM), new GetAlarmHandler(this));
                addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_ALARM_ENABLED));
                addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_WORKOUT_SCREEN_LIST), new WorkoutScreenListHandler(this));
                addExtraSyncCommands();
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(getActivitySyncStartTimestamp());

                // Mimic official app's request sequence for GET_ACTIVITY_SAMPLES with different Vasistas types.
                // If these specific modifiers and TypeVersion/VasistasType modifiers
                // for GET_MOVEMENT_SAMPLES are omitted, the watch sends a default payload that is missing the
                // explicit WORKOUT_TYPE TLV (0x0969/2409). By mimicking the official app's exact requests,
                // the watch includes the correct workout type (e.g., Weightlifting, Cycling, Running) during
                // historical sync, preventing explicit workout types from being lost.
                message = new WithingsMessage(WithingsMessageType.GET_ACTIVITY_SAMPLES, ExpectedResponse.EOT);
                message.addDataStructure(new GetActivitySamples(c.getTimeInMillis() / 1000, (short) 0));
                message.addDataStructure(new VasistasType(6));
                addSimpleConversationToQueue(message, activitySampleHandler);

                message = new WithingsMessage(WithingsMessageType.GET_ACTIVITY_SAMPLES, ExpectedResponse.EOT);
                message.addDataStructure(new GetActivitySamples(c.getTimeInMillis() / 1000, (short) 0));
                message.addDataStructure(new VasistasType(5));
                addSimpleConversationToQueue(message, activitySampleHandler);

                if (supportsSleepBreathingSync()) {
                    message = new WithingsMessage(WithingsMessageType.GET_ACTIVITY_SAMPLES, ExpectedResponse.EOT);
                    message.addDataStructure(new GetActivitySamples(c.getTimeInMillis() / 1000, (short) 0));
                    message.addDataStructure(new VasistasType(VasistasType.TYPE_AHI));
                    addSimpleConversationToQueue(message, activitySampleHandler);
                }

                message = new WithingsMessage(WithingsMessageType.GET_ACTIVITY_SAMPLES, ExpectedResponse.EOT);
                message.addDataStructure(new GetActivitySamples(c.getTimeInMillis() / 1000, (short) 0));
                message.addDataStructure(new VasistasType(VasistasType.TYPE_SPO2));
                addSimpleConversationToQueue(message, activitySampleHandler);

                // Mimic GET_MOVEMENT_SAMPLES with TypeVersion 3
                message = new WithingsMessage(WithingsMessageType.GET_MOVEMENT_SAMPLES, ExpectedResponse.EOT);
                message.addDataStructure(new GetActivitySamples(c.getTimeInMillis() / 1000, (short) 0));
                message.addDataStructure(new TypeVersion((byte) 3));
                addSimpleConversationToQueue(message, activitySampleHandler);

                // The Steel HR does not support this data type
                if (supportsVasistasType4()) {
                    // Mimic GET_MOVEMENT_SAMPLES with VasistasType 4
                    message = new WithingsMessage(WithingsMessageType.GET_MOVEMENT_SAMPLES, ExpectedResponse.EOT);
                    message.addDataStructure(new GetActivitySamples(c.getTimeInMillis() / 1000, (short) 0));
                    message.addDataStructure(new VasistasType(4));
                    addSimpleConversationToQueue(message, activitySampleHandler);
                }

                message = new WithingsMessage(WithingsMessageType.GET_HEARTRATE_SAMPLES, ExpectedResponse.EOT);
                message.addDataStructure(new GetActivitySamples(c.getTimeInMillis() / 1000, (short) 0));
                message.addDataStructure(new TypeVersion());
                addSimpleConversationToQueue(message, activitySampleHandler);

                // Stored-measure sync for signal types 0x0004 (SpO2/HR + mixed ECG keys) and 0x0005.
                // Signal type 0x0001 (ECG waveform) is NOT requested here because it returns raw
                // ECG waveform StoredSignalData packets that the StoredMeasureSignalHandler cannot
                // process, causing the sync to stall.  ECG waveforms are handled by the dedicated
                // WithingsEcgHandler discovery flow instead, which correctly decodes them.
                if (supportsStoredMeasureSync()) {
                    message = new WithingsMessage(WithingsMessageType.GET_STORED_MEASURE_SIGNAL, ExpectedResponse.EOT);
                    message.addDataStructure(new StoredSignalMeta(0x0004, 0));
                    addSimpleConversationToQueue(message, new StoredMeasureSignalHandler(this, gbDevice, 0x0004));

                    message = new WithingsMessage(WithingsMessageType.GET_STORED_MEASURE_SIGNAL, ExpectedResponse.EOT);
                    message.addDataStructure(new StoredSignalMeta(0x0005, 0));
                    addSimpleConversationToQueue(message, new StoredMeasureSignalHandler(this, gbDevice, 0x0005));

                    if (withingsEcgHandler != null) {
                        withingsEcgHandler.start();
                    }
                }
            } else {
                addExtraSyncCommandsWhenSkippingFullSync();
            }
        } catch (Exception e) {
            logger.error("Could not synchronize! ", e);
            conversationQueue.clear();
        } finally {
            // This must be done in all cases or the watch won't respond anymore!
            logger.debug("Queueing SYNC_OK terminator for trigger={}", triggerSource);
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SYNC_OK), new SyncFinishedHandler(this));
        }
        conversationQueue.send();
    }


    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           byte[] data) {
        if (super.onCharacteristicChanged(gatt, characteristic, data)) {
            return true;
        }

        logger.debug("onCharacteristicChanged raw: {}", nodomain.freeyourgadget.gadgetbridge.util.GB.hexdump(data));
        boolean complete = messageBuilder.buildMessage(data);
        if (complete) {
            Message message = messageBuilder.getMessage();
            if (message.isIncomingMessage()) {
                logger.debug("received incoming message: {}", message.getType());
                IncomingMessageHandler handler = incomingMessageHandlerFactory.getHandler(message);
                if (handler == null) {
                    logger.warn("No handler for incoming message type={}", message.getType());
                } else {
                    handler.handleMessage(message);
                }
            } else {
                logger.debug("received response message: type={} (0x{})", message.getType(), Integer.toHexString(message.getType() & 0xffff));
                if (withingsEcgHandler == null &&
                        (message.getType() == WithingsMessageType.MEASURE_START || message.getType() == WithingsMessageType.MEASURE_STOP)) {
                    withingsEcgHandler = createEcgHandler();
                }
                if (withingsEcgHandler != null
                        && !syncInProgress
                        && (message.getType() == WithingsMessageType.MEASURE_START
                        || message.getType() == WithingsMessageType.MEASURE_STOP
                        || message.getType() == WithingsMessageType.TRANSFER_COMPLETE)
                        && !conversationQueue.matchesActiveConversation(message)) {
                    logger.info("Received unsolicited ECG discovery message while idle, starting sync");
                    doSync("unsolicited-ecg-discovery");
                    return true;
                }
                if (withingsEcgHandler != null
                        && !conversationQueue.matchesActiveConversation(message)
                        && withingsEcgHandler.maybeHandleMeasurementMessage(message)) {
                    return true;
                }
                conversationQueue.processResponse(message);
            }
        }

        return true;
    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        if (callSpec.getCommand() == CallSpec.CALL_INCOMING) {
            NotificationSpec notificationSpec = new NotificationSpec();
            notificationSpec.setSourceAppId(callSpec.getSourceAppId() != null ? callSpec.getSourceAppId() : getDefaultDialerPackage());
            notificationSpec.setTitle(callSpec.getNumber());
            notificationSpec.setSender(callSpec.getName());
            notificationSpec.setType(NotificationType.GENERIC_PHONE);
            notificationProvider.notifyClient(notificationSpec);
        } else if (callSpec.getCommand() == CallSpec.CALL_START
                || callSpec.getCommand() == CallSpec.CALL_END
                || callSpec.getCommand() == CallSpec.CALL_REJECT
                || callSpec.getCommand() == CallSpec.CALL_ACCEPT) {
            logger.info("Clearing incoming call state for command {}", callSpec.getCommand());
            notificationProvider.clearActiveIncomingCall();
        } else {
            logger.info("Received yet unhandled call command: " + callSpec.getCommand());
        }
    }

    private String getDefaultDialerPackage() {
        try {
            Context context = getContext();
            if (context != null) {
                TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                if (telecomManager != null) {
                    String defaultDialer = telecomManager.getDefaultDialerPackage();
                    if (defaultDialer != null && !defaultDialer.isEmpty()) {
                        return defaultDialer;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not get default dialer package", e);
        }
        return "com.android.dialer";
    }

    @Override
    public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
        super.onConnectionStateChange(gatt, status, newState);

        if (newState == BluetoothGatt.STATE_CONNECTED) {
            clearStaleSyncState("connect");
            resetAncsSubscriptionState("new connection");
            ancsAwaitingCccReadyEnable = true;
            withingsProtocolReady = false;
            // Reset connection-local ANCS watchdog state. Keep the recovery
            // timestamp so reconnecting cannot bypass the recovery cooldown.
            lastControlPointWriteMs = 0;
            lastAcknowledgedNotificationMs = 0;
            backgroundTasksHandler.removeCallbacks(ancsStallCheckRunnable);
        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
            clearStaleSyncState("disconnect");
        }
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        notificationProvider.notifyClient(notificationSpec);
    }

    @Override
    public void onDeleteNotification(int id) {
        notificationProvider.onDeleteNotification(id);
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {
        if (alarms.size() == 0) {
            return;
        }

        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_ALARM));

        // Build a single CMD_SET_MULTI_ALARM message containing ALL enabled alarms.
        // The watch replaces its entire alarm list with the contents of this message,
        // so sending one message per alarm would cause only the last one to survive.
        Message multiAlarmMessage = new WithingsMessage(WithingsMessageType.SET_ALARM);
        boolean anyAlarmEnabled = false;

        for (Alarm alarm : alarms) {
            if (alarm.getEnabled() && !alarm.getUnused()) {
                anyAlarmEnabled = true;
                addAlarmToMessage(multiAlarmMessage, alarm);
            }
        }

        if (anyAlarmEnabled) {
            addSimpleConversationToQueue(multiAlarmMessage);
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_ALARM_ENABLED, new AlarmStatus(true)));
        } else {
            // Send an empty SET_ALARM to clear all alarms, then disable
            addSimpleConversationToQueue(multiAlarmMessage);
            addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_ALARM_ENABLED, new AlarmStatus(false)));
        }

        conversationQueue.send();
    }

    @Override
    public boolean onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        logger.debug("onDescriptorWriteRequest: device={}, descriptor={}, characteristic={}, value={}",
                device.getAddress(), descriptor.getUuid(),
                descriptor.getCharacteristic() != null ? descriptor.getCharacteristic().getUuid() : "null",
                GB.hexdump(value));

        if (descriptor.getCharacteristic() != null && descriptor.getUuid().equals(getActiveWithingsUUIDs().CCC_DESCRIPTOR_UUID)) {
            final UUID characteristicUuid = descriptor.getCharacteristic().getUuid();
            final boolean enabled = isNotificationCccEnabled(value);
            if (characteristicUuid.equals(getActiveWithingsUUIDs().NOTIFICATION_SOURCE_CHARACTERISTIC_UUID)) {
                ancsNotificationSourceSubscribed = enabled;
                logger.info("ANCS Notification Source CCC {}", enabled ? "enabled" : "disabled");
                maybeHandleAncsSubscriptionsReady();
            } else if (characteristicUuid.equals(getActiveWithingsUUIDs().DATA_SOURCE_CHARACTERISTIC_UUID)) {
                ancsDataSourceSubscribed = enabled;
                logger.info("ANCS Data Source CCC {}", enabled ? "enabled" : "disabled");
                maybeHandleAncsSubscriptionsReady();
            }
        }

        return true;
    }

    @Override
    public boolean onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
        logger.debug("onCharacteristicReadRequest: device={}, characteristic={}, offset={}", device.getAddress(), characteristic.getUuid(), offset);
        return false;
    }

    @Override
    public boolean onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
        if (characteristic.getUuid().equals(getActiveWithingsUUIDs().CONTROL_POINT_CHARACTERISTIC_UUID)) {
            logger.debug("ANCS Control Point write: {}", GB.hexdump(value));
            GetNotificationAttributes request = new GetNotificationAttributes();
            request.deserialize(value);
            final long acknowledgedNotificationMs = notificationProvider.handleNotificationAttributeRequest(request);
            onControlPointWriteReceived(acknowledgedNotificationMs);
        }

        return true;
    }

    @Override
    public void onFetchRecordedData(int dataTypes) {
        doSync("manual-fetch");
    }

    @Override
    public void onHeartRateTest() {
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_HR), new HeartRateHandler(this));
        conversationQueue.send();
    }

    @Override
    public void onSendConfiguration(String config) {
        try {
            switch (config) {
                case HuamiConst.PREF_WORKOUT_ACTIVITY_TYPES_SORTABLE:
                    setWorkoutActivityTypes();
                    break;
                case PREF_TIMEFORMAT:
                    setTimeFormat();
                    break;
                case PREF_LANGUAGE:
                    setLanguage();
                    break;
                case ActivityUser.PREF_USER_STEPS_GOAL:
                    sendStepsGoal();
                    break;
                case PREF_NOTIFICATION_ENABLE:
                    sendNotificationConfiguration();
                    break;
                default:
                    if (!handleExtraConfiguration(config)) {
                        logger.debug("unknown configuration setting received: " + config);
                    }
            }
        } catch (Exception e) {
            GB.toast("Error setting configuration", Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    private void sendStepsGoal() {
        ActivityUser user = new ActivityUser();
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_ACTIVITY_TARGET,
                new ActivityTarget(ActivityTarget.GOAL_TYPE_STEPS, user.getStepsGoal())));
        conversationQueue.send();
    }

    private void setTimeFormat() {
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_USER_UNIT,
                new UserUnit(UserUnitConstants.CLOCK_MODE, getTimeMode())));
        conversationQueue.send();
    }

    protected boolean isNotificationEnabledPreferenceOn() {
        return GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress())
                .getBoolean(PREF_NOTIFICATION_ENABLE, false);
    }

    protected void sendNotificationConfiguration() {
        final boolean enabled = isNotificationEnabledPreferenceOn();
        logger.info("Applying Withings notification toggle: enabled={}", enabled);
        addSimpleConversationToQueue(
                new WithingsMessage(WithingsMessageType.SET_ANCS_STATUS, new AncsStatus(enabled))
        );
        addFeatureTagsMessage();
        addSimpleConversationToQueue(
                new WithingsMessage(WithingsMessageType.GET_ANCS_STATUS),
                response -> {
                    if (enabled) {
                        scheduleNotificationReconnect();
                    }
                }
        );
        sendQueue();
    }

    private void scheduleNotificationReconnect() {
        logger.info("Controlled reconnect requested after enabling notifications to force fresh ANCS discovery");
        ancsAwaitingCccReadyEnable = false;
        resetAncsSubscriptionState("notification toggle enabled");
        backgroundTasksHandler.postDelayed(() -> {
            if (isConnected()) {
                logger.info("Disconnecting after notification toggle enable so the watch reconnects with notifications armed");
                disconnect();
                backgroundTasksHandler.postDelayed(() -> {
                    logger.info("Reconnecting after notification toggle enable");
                    connect();
                }, 1500);
            }
        }, 2000);
    }

    protected void queueNotificationConfiguration(final boolean enabled) {
        queueNotificationConfiguration(enabled, null);
    }

    private void queueNotificationConfiguration(final boolean enabled, @Nullable final Runnable onConfigured) {
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_ANCS_STATUS, new AncsStatus(enabled)));
        addFeatureTagsMessage();
        addPostNotificationConfigurationCommands(enabled);
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.GET_ANCS_STATUS), response -> {
            if (onConfigured != null) {
                onConfigured.run();
            }
        });
    }

    /**
     * Allows device families to restore feature tags that a notification-only feature-tag
     * update would otherwise replace.
     */
    protected void addPostNotificationConfigurationCommands(final boolean enabled) {
    }

    /**
     * Called from {@link #onSendConfiguration(String)} for device-specific configuration keys
     * not handled by the base class.
     *
     * @param config the configuration key
     * @return {@code true} if the config was handled, {@code false} otherwise
     */
    protected boolean handleExtraConfiguration(String config) {
        return false;
    }

    @Override
    public void onTestNewFunction(@Nullable Bundle options) {
        String hexMessage = "0105080015050900111006040102030507000000000000000000";
        addSimpleConversationToQueue(new SimpleHexToByteMessage(hexMessage));
        conversationQueue.send();
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }

    public void sendToDevice(Message message) {
        if (message == null) {
            return;
        }

        if (getQueue() == null) {
            logger.warn("Could not send message because queue is null");
            return;
        }

        try {
            TransactionBuilder builder = createTransactionBuilder("conversation");
            builder.setCallback(this);
            final WithingsUUIDs withingsUUIDs = getActiveWithingsUUIDs();
            if (withingsUUIDs == null) {
                logger.warn("Active Withings UUIDs are null");
                return;
            }
            BluetoothGattCharacteristic characteristic = getCharacteristic(withingsUUIDs.WITHINGS_WRITE_CHARACTERISTIC_UUID);
            if (characteristic == null) {
                logger.info("Characteristic with UUID {} not found.", withingsUUIDs.WITHINGS_WRITE_CHARACTERISTIC_UUID);
                return;
            }

            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

            byte[] rawData = message.getRawData();
            if (rawData == null) {
                logger.warn("Message raw data is null");
                return;
            }
            builder.writeChunkedData(characteristic, rawData, getMTU() - 3);
            builder.queue();
        } catch (Exception e) {
            logger.warn("Could not send message", e);
        }
    }

    /**
     * Returns a reliable {@link BluetoothDevice} reference for GATT server operations.
     * The old approach of storing the device from {@code onDescriptorWriteRequest} was
     * unreliable - if the watch never wrote the CCC descriptor, the field stayed null
     * and all notifications were silently dropped.
     *
     * This uses the canonical Gadgetbridge pattern (same as {@code BtLEQueue.connectImp()}).
     */
    private BluetoothDevice getServerDevice() {
        return getBluetoothAdapter().getRemoteDevice(getDevice().getAddress());
    }

    public void sendAncsNotificationSourceNotification(NotificationSource notificationSource) {
        try {
            ServerTransactionBuilder builder = performServer("notificationSourceNotification");
            byte[] data = notificationSource.serialize();
            builder.notifyCharacteristicChanged(getServerDevice(), notificationSourceCharacteristic, data);
            builder.queue(getQueue());

            // Track ADDED events for stall detection.
            if (notificationSource.getEventID() == AncsConstants.EVENT_ID_NOTIFICATION_ADDED) {
                scheduleAncsStallCheck();
            }
        } catch (IOException e) {
            logger.error("Could not send notification.", e);
            GB.toast("Could not send notification.", Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    /**
     * Send an ANCS DataSource response to the watch, chunked to 20-byte BLE packets.
     * Exact multiples need an empty final packet so the watch can distinguish the
     * final full packet from a continuation.
     */
    public void sendAncsDataSourceNotification(GetNotificationAttributesResponse response) {
        try {
            ServerTransactionBuilder builder = performServer("dataSourceNotification");
            byte[] data = response.serialize();
            int offset = 0;
            for (byte[] chunk : chunkAncsDataSourcePayload(data)) {
                logger.debug("Sending ANCS DataSource chunk offset={}, length={}, total={}", offset, chunk.length, data.length);
                builder.notifyCharacteristicChanged(getServerDevice(), dataSourceCharacteristic, chunk);
                offset += chunk.length;
            }
            builder.queue(getQueue());
        } catch (IOException e) {
            logger.error("Could not send notification.", e);
            GB.toast("Could not send notification.", Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    static List<byte[]> chunkAncsDataSourcePayload(final byte[] data) {
        final int chunkSize = 20;
        final List<byte[]> chunks = new ArrayList<>((data.length / chunkSize) + 1);
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            final int length = Math.min(chunkSize, data.length - offset);
            chunks.add(Arrays.copyOfRange(data, offset, offset + length));
        }
        if (data.length % chunkSize == 0) {
            chunks.add(new byte[0]);
        }
        return chunks;
    }

    public NotificationProvider getNotificationProvider() {
        return notificationProvider;
    }

    public boolean prefersLatestConversationNotification() {
        return false;
    }

    public void finishInitialization() {
        TransactionBuilder builder = createTransactionBuilder("setupFinished");
        builder.setDeviceState(GBDevice.State.INITIALIZED);
        builder.queue();
        withingsProtocolReady = true;
        // Authentication immediately starts a sync, while first-time setup does not.
        // Delay this check so the sync can claim the conversation queue first; its
        // completion will perform the same check again.
        backgroundTasksHandler.postDelayed(this::maybeHandleAncsSubscriptionsReady, 250);
        if (firstTimeConnect) {
            logger.debug("First-time setup completed; future reconnects will use post-auth sync flow");
            firstTimeConnect = false;
        }
        logger.debug("Finished initialization.");
    }

    private void clearStaleSyncState(final String reason) {
        if (withingsEcgHandler != null) {
            withingsEcgHandler.reset();
        } else {
            GB.updateTransferNotification(null, "", false, 100, getContext());
        }

        if (syncInProgress) {
            logger.info("Clearing stale Withings sync state on {}", reason);
            syncInProgress = false;
            conversationQueue.clear();
        }

        final String syncingTask = getContext().getString(R.string.busy_task_syncing);
        if (syncingTask.equals(getDevice().getBusyTask())) {
            getDevice().unsetBusyTask();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    public void finishSync() {
        logger.info("Finishing Withings sync, busyTask={}, syncInProgress={}", getDevice().getBusyTask(), syncInProgress);
        syncInProgress = false;
        if (withingsEcgHandler != null) {
            withingsEcgHandler.onSyncFinished();
        }
        if (getDevice().isBusy()) {
            getDevice().unsetBusyTask();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
        activitySampleHandler.onSyncFinished();
        GB.signalActivityDataFinish(getDevice());
        saveLastSyncTimestamp(new Date().getTime());

        maybeHandleAncsSubscriptionsReady();
    }

    public boolean isSyncInProgress() {
        return syncInProgress;
    }

    public void onConversationTimeout(final short requestType) {
        logger.warn("Recovering Withings connection after conversation timeout for type=0x{}",
                Integer.toHexString(requestType & 0xffff));
        clearStaleSyncState("conversation timeout");
        if (isConnected()) {
            disconnect();
            backgroundTasksHandler.postDelayed(this::connect, 1500);
        }
    }

    void onAuthenticationFinished() {
        finishInitialization();
        doSync("post-auth");
    }

    @Override
    public void onFactoryReset() {
        // Official app dissociation protocol (from btsnoop captures):
        // 1. Send command 0x0123 (dissociate product)
        // 2. Send command 0x0110 (unknown - possibly "forget pairing" or "factory reset")
        // 3. HCI disconnect
        // After this, the watch enters pairing mode.
        conversationQueue.clear();
        addSimpleConversationToQueue(new WithingsMessage((short) 0x0123));
        addSimpleConversationToQueue(new WithingsMessage((short) 0x0110));
        conversationQueue.send();

        // Give the watch time to process both commands, then disconnect
        backgroundTasksHandler.postDelayed(() -> {
            logger.info("Disconnecting after dissociation commands");
            disconnect();
        }, 2000);
    }

    private void addAlarmToMessage(Message multiAlarmMessage, Alarm alarm) {
        AlarmSettings alarmSettings = new AlarmSettings();
        alarmSettings.setHour((short) alarm.getHour());
        alarmSettings.setMinute((short) alarm.getMinute());
        alarmSettings.setDayOfWeek(mapRepetitionToWithingsValue(alarm));

        if (!alarm.isRepetitive()) {
            // One-time alarm: set the target date so the watch knows when to fire.
            // Without a date, flags=0x80 + date=00/00/00 is ambiguous and the watch
            // may ignore the alarm entirely.
            Calendar cal = AlarmUtils.toCalendar(alarm);
            alarmSettings.setDayOfMonth((short) cal.get(Calendar.DAY_OF_MONTH));
            alarmSettings.setMonth((short) (cal.get(Calendar.MONTH) + 1)); // Calendar.MONTH is 0-based
            alarmSettings.setYear((short) (cal.get(Calendar.YEAR) - 2000));
        }

        if (alarm.getSmartWakeup()) {
            final Integer interval = alarm.getSmartWakeupInterval();
            final int maxInterval = gbDevice.getDeviceCoordinator().getSmartWakeupMaxInterval(gbDevice);
            alarmSettings.setSmartWakeupMinutes(clampSmartWakeupInterval(interval, maxInterval));
        }

        multiAlarmMessage.addDataStructure(alarmSettings);
        if (!StringUtils.isEmpty(alarm.getTitle())) {
            AlarmName alarmName = new AlarmName(alarm.getTitle());
            multiAlarmMessage.addDataStructure(alarmName);
        }
    }

    static short clampSmartWakeupInterval(final Integer interval, final int maxInterval) {
        if (interval != null && interval > 0) {
            return (short) Math.min(interval, maxInterval);
        }
        return 20;
    }

    private short mapRepetitionToWithingsValue(Alarm alarm) {
        int repetition = 0;
        if (alarm.getRepetition(Alarm.ALARM_MON)) {
            repetition += 0x02;
        }
        if (alarm.getRepetition(Alarm.ALARM_TUE)) {
            repetition += 0x04;
        }
        if (alarm.getRepetition(Alarm.ALARM_WED)) {
            repetition += 0x08;
        }
        if (alarm.getRepetition(Alarm.ALARM_THU)) {
            repetition += 0x10;
        }
        if (alarm.getRepetition(Alarm.ALARM_FRI)) {
            repetition += 0x20;
        }
        if (alarm.getRepetition(Alarm.ALARM_SAT)) {
            repetition += 0x40;
        }
        if (alarm.getRepetition(Alarm.ALARM_SUN)) {
            repetition += 0x01;
        }

        return (short)(repetition + 0x80);
    }

    private void addANCSService() {
        final WithingsUUIDs withingsUUIDs = getActiveWithingsUUIDs();
        BluetoothGattService withingsGATTService = new BluetoothGattService(withingsUUIDs.WITHINGS_ANCS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        notificationSourceCharacteristic = new BluetoothGattCharacteristic(withingsUUIDs.NOTIFICATION_SOURCE_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ);
        notificationSourceCharacteristic.addDescriptor(new BluetoothGattDescriptor(withingsUUIDs.CCC_DESCRIPTOR_UUID, BluetoothGattCharacteristic.PERMISSION_WRITE));
        withingsGATTService.addCharacteristic(notificationSourceCharacteristic);
        withingsGATTService.addCharacteristic(new BluetoothGattCharacteristic(withingsUUIDs.CONTROL_POINT_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE));
        dataSourceCharacteristic = new BluetoothGattCharacteristic(withingsUUIDs.DATA_SOURCE_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ);
        dataSourceCharacteristic.addDescriptor(new BluetoothGattDescriptor(withingsUUIDs.CCC_DESCRIPTOR_UUID, BluetoothGattCharacteristic.PERMISSION_WRITE));
        withingsGATTService.addCharacteristic(dataSourceCharacteristic);
        addSupportedServerService(withingsGATTService);
    }

    private void resetAncsSubscriptionState(final String reason) {
        logger.debug("Resetting ANCS CCC subscription state ({})", reason);
        ancsNotificationSourceSubscribed = false;
        ancsDataSourceSubscribed = false;
    }

    private boolean isNotificationCccEnabled(final byte[] value) {
        return value != null
                && value.length >= 2
                && value[0] == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[0]
                && value[1] == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE[1];
    }

    private boolean areAncsCccSubscriptionsReady() {
        return ancsNotificationSourceSubscribed && ancsDataSourceSubscribed;
    }

    private void maybeHandleAncsSubscriptionsReady() {
        if (!areAncsCccSubscriptionsReady()) {
            return;
        }

        logger.info("ANCS CCC subscriptions are active on the current GATT server instance");

        if (!ancsAwaitingCccReadyEnable) {
            return;
        }

        if (syncInProgress) {
            logger.debug("ANCS CCC subscriptions became ready during sync; ANCS enable will be sent once sync is over");
            return;
        }

        if (!withingsProtocolReady) {
            logger.debug("ANCS CCC subscriptions became ready during initialization; ANCS enable will be sent after sync");
            return;
        }

        ancsAwaitingCccReadyEnable = false;

        if (!isNotificationEnabledPreferenceOn()) {
            logger.info("Fresh ANCS CCC subscriptions observed, but notification toggle is disabled; skipping ANCS enable");
            return;
        }

        logger.info("Fresh ANCS CCC subscriptions observed; applying enabled notification toggle state");
        queueNotificationConfiguration(true, this::replayPendingNotificationsAfterSubscriptionsRestore);
        conversationQueue.send();
    }

    private void replayPendingNotificationsAfterSubscriptionsRestore() {
        final int replayed = notificationProvider.replayPendingNotifications();
        if (replayed > 0) {
            logger.info("Replayed {} unrequested notification(s) after ANCS subscriptions were restored", replayed);
        }
    }

    /**
     * Queues the SET_FEATURE_TAGS_DEPRECATED message with the feature tags required
     * by this device.
     *
     * <p>The base implementation sends the minimal set for the Steel HR: just
     * {@link FeatureTagDeprecated#TAG_NOTIFICATIONS}, {@link FeatureTagDeprecated#TAG_0x0035},
     * and {@link FeatureTagDeprecated#TAG_0x0058}.
     *
     * <p>Subclasses (e.g. ScanWatch) should override this to include additional
     * feature tags such as ECG and SpO2, so that all features are enabled from the
     * very first connection -- not just after the first periodic sync.
     */
    protected void addFeatureTagsMessage() {
        WithingsMessage featureTagsMsg = new WithingsMessage(WithingsMessageType.SET_FEATURE_TAGS_DEPRECATED);
        featureTagsMsg.addDataStructure(new FeatureTagsUserId(0));
        featureTagsMsg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_NOTIFICATIONS));
        featureTagsMsg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_0x0035));
        featureTagsMsg.addDataStructure(new FeatureTagDeprecated(FeatureTagDeprecated.TAG_0x0058));
        featureTagsMsg.addDataStructure(new EndOfTransmission());
        addSimpleConversationToQueue(featureTagsMsg);
        addFeatureTagsCommitCommands();
    }

    /**
     * Sends the 0x0993 + 0x0994 "commit" commands that the official app always sends
     * after {@code SET_FEATURE_TAGS_DEPRECATED}.  Without these, the watch may not
     * fully activate the feature tag changes (e.g., ANCS notifications only request
     * attribute 0 instead of the full title/subtitle/message).
     *
     * <p>Both are empty GET messages that return a simple ACK response.
     */
    protected void addFeatureTagsCommitCommands() {
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.COMMIT_FEATURE_TAGS));
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.COMMIT_NOTIFICATION_CONFIG));
    }

    /**
     * Schedule a check for the oldest pending event that has not been acknowledged by
     * a later Control Point request. Newer ADDED events cannot postpone this deadline.
     */
    private void scheduleAncsStallCheck() {
        backgroundTasksHandler.removeCallbacks(ancsStallCheckRunnable);
        final long now = System.currentTimeMillis();
        final long oldestPendingMs = notificationProvider.getOldestUnrequestedTimestampAfter(lastAcknowledgedNotificationMs);
        if (oldestPendingMs == 0) {
            return;
        }

        final long delayMs = calculateAncsStallCheckDelay(now, oldestPendingMs, lastAncsRecoveryAttemptMs);
        backgroundTasksHandler.postDelayed(ancsStallCheckRunnable, delayMs);
    }

    static long calculateAncsStallCheckDelay(final long now,
                                             final long oldestPendingMs,
                                             final long lastRecoveryMs) {
        final long stallDueMs = oldestPendingMs + ANCS_STALL_THRESHOLD_MS;
        final long recoveryAllowedMs = lastRecoveryMs > 0
                ? lastRecoveryMs + ANCS_RECOVERY_COOLDOWN_MS
                : stallDueMs;
        return Math.max(0, Math.max(stallDueMs, recoveryAllowedMs) - now);
    }

    /**
     * Check for an ANCS stall: if we have sent ADDED notifications recently but the
     * watch has not issued any Control Point writes within the threshold, attempt
     * recovery. A clean reconnect is required because re-sending the WPP ANCS
     * configuration does not restore Control Point writes on a stale session.
     */
    private void checkAncsStall() {
        final long now = System.currentTimeMillis();

        final long oldestPendingMs = notificationProvider.getOldestUnrequestedTimestampAfter(lastAcknowledgedNotificationMs);
        if (oldestPendingMs == 0) {
            return;
        }

        final long timeSinceLastAdded = now - oldestPendingMs;
        if (timeSinceLastAdded < ANCS_STALL_THRESHOLD_MS) {
            scheduleAncsStallCheck();
            return;
        }

        if (now - lastAncsRecoveryAttemptMs < ANCS_RECOVERY_COOLDOWN_MS) {
            logger.debug("ANCS stall detected but recovery cooldown active ({}ms remaining)",
                    ANCS_RECOVERY_COOLDOWN_MS - (now - lastAncsRecoveryAttemptMs));
            scheduleAncsStallCheck();
            return;
        }

        lastAncsRecoveryAttemptMs = now;
        logger.warn("ANCS stall detected: no CP write for {}ms after ADDED (lastCP={}ms ago). " +
                        "Disconnecting to restore ANCS subscriptions",
                timeSinceLastAdded,
                lastControlPointWriteMs > 0 ? (now - lastControlPointWriteMs) : -1);
        if (isConnected()) {
            disconnect();
            backgroundTasksHandler.postDelayed(() -> {
                logger.info("Reconnecting after ANCS stall recovery");
                connect();
            }, 1500);
        } else {
            connect();
        }
    }

    /**
     * Called when a Control Point write is received from the watch,
     * confirming the ANCS session is alive.
     */
    private void onControlPointWriteReceived(final long acknowledgedNotificationMs) {
        lastControlPointWriteMs = System.currentTimeMillis();
        lastAcknowledgedNotificationMs = Math.max(lastAcknowledgedNotificationMs, acknowledgedNotificationMs);
        scheduleAncsStallCheck();
    }

    public void addSimpleConversationToQueue(Message message) {
        addSimpleConversationToQueue(message, null);
    }

    public void addSimpleConversationToQueue(Message message, ResponseHandler handler) {
        Conversation conversation = new SimpleConversation(handler);
        conversation.setRequest(message);
        conversationQueue.addConversation(conversation);
    }

    public void addSimpleConversationFirst(Message message, ResponseHandler handler) {
        Conversation conversation = new SimpleConversation(handler);
        conversation.setRequest(message);
        conversationQueue.addConversationFirst(conversation);
    }

    public void queueDeleteStoredMeasureSignal(final int signalType, final int signalFlags, final int cursor) {
        queueDeleteStoredMeasureSignal(signalType, signalFlags, cursor, null);
    }

    public void queueDeleteStoredMeasureSignal(final int signalType,
                                               final int signalFlags,
                                               final int cursor,
                                               final ResponseHandler handler) {
        queueDeleteStoredMeasureSignal(signalType, signalFlags, cursor, storedMeasureDeleteRequestId++, handler);
    }

    public void queueDeleteStoredMeasureSignal(final int signalType,
                                               final int signalFlags,
                                               final int cursor,
                                               final int deleteRequestId,
                                               final ResponseHandler handler) {
        Message deleteMessage = new WithingsMessage(WithingsMessageType.DELETE_STORED_MEASURE_SIGNAL, ExpectedResponse.SIMPLE);
        // Official captures include a 0x0145 TLV before 0x0143, and successful multi-page
        // manual SpO2 syncs increment it across successive delete requests in the same session.
        // The cursor in 0x0143 is treated as an opaque delete token for the current head page,
        // not as a "next page" pointer.
        deleteMessage.addDataStructure(new FeatureTagsUserId(deleteRequestId));
        deleteMessage.addDataStructure(new StoredSignalMeta(signalType, signalFlags, cursor));
        addSimpleConversationFirst(deleteMessage, handler);
    }

    public void queueGetStoredMeasureSignal(final int signalType, final int cursor) {
        queueGetStoredMeasureSignal(signalType, 0, cursor, new StoredMeasureSignalHandler(this, gbDevice, signalType));
    }

    public void queueGetStoredMeasureSignal(final int signalType,
                                            final int cursor,
                                            final ResponseHandler handler) {
        queueGetStoredMeasureSignal(signalType, 0, cursor, handler);
    }

    public void queueGetStoredMeasureSignal(final int signalType,
                                            final int signalFlags,
                                            final int cursor,
                                            final ResponseHandler handler) {
        Message getMessage = new WithingsMessage(WithingsMessageType.GET_STORED_MEASURE_SIGNAL, ExpectedResponse.EOT);
        // For stored-measure queues the official app repeatedly re-reads the queue head with
        // cursor 0. The returned StoredSignalMeta carries the delete key for the current head item.
        getMessage.addDataStructure(new StoredSignalMeta(signalType, signalFlags, cursor));
        addSimpleConversationFirst(getMessage, handler);
    }

    public int incrementStoredMeasureDeleteAttempts() {
        return ++storedMeasureDeleteAttempts;
    }

    public boolean hasEndOfTransmission(final Message response) {
        final List<WithingsStructure> dataStructures = response.getDataStructures();
        if (dataStructures == null) {
            return false;
        }

        for (final WithingsStructure structure : dataStructures) {
            if (structure.getType() == WithingsStructureType.END_OF_TRANSMISSION) {
                return true;
            }
        }

        return false;
    }

    /** Clears any pending conversations from the queue. */
    protected void clearQueue() {
        conversationQueue.clear();
    }

    /** Starts sending the queued conversations to the device. */
    protected void sendQueue() {
        conversationQueue.send();
    }

    private void saveLastSyncTimestamp(@NonNull long timestamp) {
        SharedPreferences.Editor editor = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress()).edit();
        editor.putLong(LAST_ACTIVITY_SYNC, timestamp);
        editor.apply();
    }

    private long getLastSyncTimestamp() {
        SharedPreferences settings = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        long lastSyncTime =  settings.getLong(LAST_ACTIVITY_SYNC, 0);
        if (lastSyncTime > 0) {
            return lastSyncTime;
        } else {
            Date currentDate = new Date();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(currentDate.getTime());
            c.add(Calendar.HOUR, - 10);
            return c.getTimeInMillis();
        }
    }

    private long getActivitySyncStartTimestamp() {
        return Math.max(0, getLastSyncTimestamp() - ACTIVITY_SYNC_OVERLAP_MILLIS);
    }

    private boolean shouldSync(final String triggerSource) {
        return shouldRunFullSync(triggerSource, System.currentTimeMillis(), getLastSyncTimestamp());
    }

    static boolean shouldRunFullSync(final String triggerSource, final long now, final long lastSynced) {
        // A user pressing Refresh expects newly recorded health data to be queried immediately.
        // The freshness shortcut is only appropriate for automatic and watch-originated triggers.
        return "manual-fetch".equals(triggerSource) || now - lastSynced > 60_000L;
    }

    private User getUser() {
        User user = new User();
        ActivityUser activityUser = new ActivityUser();
        user.setName(activityUser.getName());
        user.setGender((byte) activityUser.getGender());
        user.setHeight(activityUser.getHeightCm());
        user.setWeight(activityUser.getWeightKg());
        user.setBirthdate(activityUser.getUserBirthday());
        return user;
    }

    protected void addScreenListCommands() {
        // Default screen list used by the Withings Steel HR.
        // Subclasses (e.g. WithingsScanwatchDeviceSupport) should override this
        // to provide a device-specific or user-configurable screen list.
        // Screen IDs are defined in WithingsScreenId; idOnDevice is the slot/position on the watch.
        // TODO: these IDs need to be verified via BLE packet capture (HCI snoop log).
        Message message = new WithingsMessage(WithingsMessageType.SET_SCREEN_LIST);
        message.addDataStructure(buildScreen(WithingsScreenId.NOTIFICATIONS,   (byte) 6));
        message.addDataStructure(buildScreen(WithingsScreenId.HEART_RATE_STEEL,(byte) 1));
        message.addDataStructure(buildScreen(WithingsScreenId.ACTIVITY,        (byte) 4));
        message.addDataStructure(buildScreen(WithingsScreenId.CALORIES_STEEL,  (byte) 2));
        message.addDataStructure(buildScreen(WithingsScreenId.ALARM,           (byte) 3));
        message.addDataStructure(buildScreen(WithingsScreenId.CONFIG,          (byte) 7));
        message.addDataStructure(buildScreen(WithingsScreenId.CHRONOMETER,     (byte) 9));
        message.addDataStructure(new EndOfTransmission());
        addSimpleConversationToQueue(message);
    }

    /**
     * Convenience method: creates a {@link ScreenSettings} with the given screen ID and slot.
     */
    protected ScreenSettings buildScreen(int screenId, byte slotOnDevice) {
        ScreenSettings settings = new ScreenSettings();
        settings.setId(screenId);
        settings.setIdOnDevice(slotOnDevice);
        return settings;
    }

    /**
     * Hook for subclasses to add device-specific commands to the sync queue.
     * Called within the {@code shouldSync()} block of {@link #doSync()}, after the common
     * commands (user, alarms, screen settings, workout screens) have been queued.
     *
     * <p>The default implementation is a no-op.
     */
    protected void addExtraSyncCommands() {
        // no-op by default
    }

    protected boolean supportsStoredMeasureSync() {
        return true;
    }

    protected boolean supportsVasistasType4() { return true; }

    protected boolean supportsSleepBreathingSync() {
        return false;
    }

    /**
     * Hook for subclasses to add lightweight device-specific commands when the current sync
     * trigger does not require a full activity/history sync.
     *
     * <p>This runs in the same sync transaction as INITIAL_CONNECT/GET_ANCS_STATUS/SET_TIME,
     * allowing devices to proactively re-assert critical runtime state on reconnects.
     *
     * <p>The default implementation is a no-op.
     */
    protected void addExtraSyncCommandsWhenSkippingFullSync() {
        // no-op by default
    }

    protected WithingsEcgHandler createEcgHandler() {
        return null;
    }

    public void notifyEcgRecordDiscovered(nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureMeta meta,
                                          int originatingSignalType) {
        if (withingsEcgHandler != null) {
            withingsEcgHandler.handleDiscoveredRecord(meta, originatingSignalType);
        }
    }

    public boolean hasDiscoveredEcgRecord(nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureMeta meta) {
        if (withingsEcgHandler != null) {
            return withingsEcgHandler.hasDiscoveredRecord(meta);
        }
        return false;
    }

    private void setWorkoutActivityTypes() {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress());

        final List<String> defaultActivityTypes = Arrays.asList(getContext().getResources().getStringArray(R.array.pref_withings_steel_activity_types_default));
        final String activityTypesPref = prefs.getString("workout_activity_types_sortable", null);

        final List<String> enabledActivityTypes;
        if (activityTypesPref == null || activityTypesPref.equals("")) {
            enabledActivityTypes = new ArrayList<>(defaultActivityTypes);
        } else {
            enabledActivityTypes = new ArrayList<>(Arrays.asList(activityTypesPref.split(",")));
        }

        enabledActivityTypes.removeIf(String::isEmpty);
        final List<String> deduplicated = new ArrayList<>();
        for (final String type : enabledActivityTypes) {
            if (!deduplicated.contains(type)) {
                deduplicated.add(type);
            }
        }
        enabledActivityTypes.clear();
        enabledActivityTypes.addAll(deduplicated);

        // Official app behavior: keep max 8 user-selected entries, with OTHER always appended.
        enabledActivityTypes.remove("other");
        final int maxWorkoutEntries = 8;
        while (enabledActivityTypes.size() > maxWorkoutEntries) {
            enabledActivityTypes.remove(enabledActivityTypes.size() - 1);
        }
        enabledActivityTypes.add("other");

        if (enabledActivityTypes.isEmpty()) {
            logger.warn("No workout activity types enabled, skipping workout screen update");
            return;
        }

        final List<WithingsStructure> workoutStructures = new ArrayList<>();
        for (final String workoutType : enabledActivityTypes) {
            try {
                final Message workoutMessage = createWorkoutScreenMessage(workoutType, ExpectedResponse.NONE);
                workoutStructures.addAll(workoutMessage.getDataStructures());
            } catch (Exception e) {
                logger.warn("exception in setWorkoutActivityTypes", e);
            }
        }

        if (workoutStructures.isEmpty()) {
            logger.warn("Failed to build workout screen payload, skipping workout screen update");
            return;
        }

        // Official app splits CMD_SET_WORKOUT_SCREEN into multiple protocol messages.
        // Keep room for final EOT (4 bytes), so payload chunks stay <= 194 bytes total.
        final int maxPayloadPerMessage = 190;
        final List<List<WithingsStructure>> chunks = new ArrayList<>();
        List<WithingsStructure> currentChunk = new ArrayList<>();
        int currentChunkSize = 0;

        for (final WithingsStructure structure : workoutStructures) {
            final int structureSize = structure.getLength();
            if (currentChunkSize > 0 && currentChunkSize + structureSize > maxPayloadPerMessage) {
                chunks.add(currentChunk);
                currentChunk = new ArrayList<>();
                currentChunkSize = 0;
            }
            currentChunk.add(structure);
            currentChunkSize += structureSize;
        }
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }

        for (int i = 0; i < chunks.size(); i++) {
            final boolean isFinalChunk = i == chunks.size() - 1;
            final Message message = isFinalChunk
                    ? new WithingsMessage(WithingsMessageType.SET_WORKOUT_SCREEN)
                    : new WithingsMessage(WithingsMessageType.SET_WORKOUT_SCREEN, ExpectedResponse.NONE);

            for (final WithingsStructure structure : chunks.get(i)) {
                message.addDataStructure(structure);
            }

            if (isFinalChunk) {
                message.addDataStructure(new EndOfTransmission());
            }

            addSimpleConversationToQueue(message);
        }

        conversationQueue.send();
    }

    @NonNull
    protected Message createWorkoutScreenMessage(String workoutType, ExpectedResponse expectedResponse) {
        WithingsActivityType withingsActivityType = WithingsActivityType.fromPrefValue(workoutType);
        int code = withingsActivityType.getCode();
        Message message = new WithingsMessage(WithingsMessageType.SET_WORKOUT_SCREEN, expectedResponse);
        WorkoutScreen workoutScreen = new WorkoutScreen();
        workoutScreen.setId(code);
        workoutScreen.setName(getWorkoutScreenName(workoutType, withingsActivityType));
        message.addDataStructure(workoutScreen);

        ImageMetaData imageMetaData = new ImageMetaData();
        imageMetaData.setHeight((byte)24);
        imageMetaData.setWidth((byte)22);
        message.addDataStructure(imageMetaData);

        ImageData imageData = new ImageData();
        final int drawableId = withingsActivityType.toActivityKind().getIcon();
        Drawable drawable = getContext().getDrawable(drawableId);
        imageData.setImageData(IconHelper.getIconBytesFromDrawable(drawable));
        message.addDataStructure(imageData);

        return message;
    }

    protected String getWorkoutScreenName(final String workoutType, final WithingsActivityType activityType) {
        final int stringId = getContext().getResources().getIdentifier("activity_type_" + workoutType, "string", getContext().getPackageName());
        if (stringId != 0) {
            return getContext().getString(stringId);
        }
        return activityType.toActivityKind().getLabel(getContext());
    }

    protected void setLanguage() {
        Locale locale = getLocale();

        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_LOCALE, locale));
        conversationQueue.send();
    }

    private Locale getLocale() {
        String localeString = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress())
                .getString(PREF_LANGUAGE, PREF_LANGUAGE_AUTO);

        if (localeString.equals(PREF_LANGUAGE_AUTO)) {
            localeString = java.util.Locale.getDefault().getLanguage();
        }

        String language = localeString.substring(0, 2);
        switch (language) {
            case "de":
            case "en":
            case "es":
            case "fr":
            case "it":
                return new Locale(language);
            default:
                return new Locale("en");
        }
    }

    private short getTimeMode() {
        if ("24h".equals(getDevicePrefs().getTimeFormat())) {
            return UserUnitConstants.UNIT_24H;
        } else {
            return UserUnitConstants.UNIT_12H;
        }
    }

    private short getUnit() {
        if (GBApplication.getPrefs().getDistanceUnit() == DistanceUnit.METRIC) {
            return UserUnitConstants.UNIT_KM;
        } else {
            return UserUnitConstants.UNIT_MILES;
        }
    }

    @Override
    public boolean getImplicitCallbackModify() {
        return true;
    }

    @Override
    public void onSetTime() {
        addSimpleConversationToQueue(new WithingsMessage(WithingsMessageType.SET_TIME, new Time()));
        conversationQueue.send();
    }
}
