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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;

/**
 * ANCS notification provider for Withings ScanWatch / Steel HR.
 *
 * Based on HCI snoop capture analysis of the official Withings app:
 *
 * <ul>
 *   <li><b>Immediate ADDED delivery:</b> ADDED events are forwarded to the watch
 *       immediately, matching the official app's behavior. DataSource responses
 *       are chunked to 20 bytes (BLE 4.0 default ATT MTU), with an empty
 *       terminating packet when the payload size is an exact multiple of 20.</li>
 *   <li><b>Immediate REMOVED delivery:</b> REMOVED events are forwarded to the
 *       watch immediately, matching the official app behavior observed in HCI
 *       captures.</li>
 *   <li><b>BLE Notifications (not indications):</b> Both NotificationSource and
 *       DataSource use BLE Notifications (ATT opcode 0x1b, confirm=false),
 *       matching the official app. The watch subscribes for notifications
 *       (CCC=0x0001) and expects this format.</li>
 *   <li><b>Attribute format:</b> attr0=app bundle ID, attr1=sender/title,
 *       attr2=empty string (always), attr3=message body. The watch displays
 *       as "{attr1} - {attr3}".</li>
 * </ul>
 */
public class NotificationProvider {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProvider.class);
    private static final Pattern WITHINGS_SOURCE_APP_SUFFIX = Pattern.compile("-(msg|ringing|missed)$", Pattern.CASE_INSENSITIVE);
    private static final String WITHINGS_DIALER_APP_ID = "dialerApp";
    private static final String UNKNOWN_DEVICE_KEY = "unknown-withings-device";
    private static final Map<String, NotificationState> NOTIFICATION_STATES = new ConcurrentHashMap<>();
    private final WithingsBaseDeviceSupport support;
    private static final int RECENT_NOTIFICATION_CACHE_SIZE = 256;
    private volatile Integer activeIncomingCallUid;

    /**
     * How long a pending notification can sit without the watch requesting its
     * attributes before we consider it stale and remove it. The watch normally
     * fetches PHASE1 within ~200ms and PHASE2 within ~500ms, but may skip
     * PHASE2 entirely (~14% of the time based on official app captures).
     * Ten minutes keeps notifications available across watchdog reconnects while
     * still bounding state when the watch intentionally skips PHASE2.
     */
    private static final long STALE_PENDING_TIMEOUT_MS = 10 * 60_000;

    public NotificationProvider(WithingsBaseDeviceSupport support) {
        this.support = support;
    }

    /**
     * Send an ANCS ADDED event for the given notification spec.
     */
    public void notifyClient(NotificationSpec spec) {
        final NotificationState state = getState();

        if (spec.getSourceAppId() != null) {
            state.latestNotificationByApp.put(normalizeSourceAppId(spec.getSourceAppId()), spec);
            state.latestNotificationByApp.put(normalizeSourceAppId(getWithingsSourceAppId(spec)), spec);
        }

        // Clean up stale pending notifications (watch skipped PHASE2)
        evictStalePending(state);

        NotificationSource notificationSource = new NotificationSource(spec.getId(),
                                                                        AncsConstants.EVENT_ID_NOTIFICATION_ADDED,
                                                                        AncsConstants.EVENT_FLAGS_IMPORTANT,
                                                                        mapNotificationType(spec.getType()),
                                                                        (byte)1);

        final int uid = notificationSource.getNotificationUID();

        if (spec.getType() == NotificationType.GENERIC_PHONE) {
            activeIncomingCallUid = uid;
        }

        state.pendingNotifications.put(uid, new PendingNotification(spec, nextPendingTimestamp(state)));

        logger.info("Withings sending ADDED id={}, source={}, type={}, pendingCount={}, deviceKey={}",
                spec.getId(),
                spec.getSourceAppId(),
                spec.getType(),
                state.pendingNotifications.size(),
                getDeviceKey());
        support.sendAncsNotificationSourceNotification(notificationSource);
    }

    /**
     * Replay notifications that were sent before a stale ANCS connection was
     * replaced. Entries removed on the phone or completed by the watch are no
     * longer pending and are therefore not replayed.
     */
    public int replayPendingNotifications() {
        final NotificationState state = getState();
        evictStalePending(state);

        final List<Map.Entry<Integer, PendingNotification>> pending = new ArrayList<>(state.pendingNotifications.entrySet());
        pending.sort(Comparator.comparingLong(entry -> entry.getValue().timestampMs));

        int replayed = 0;
        for (Map.Entry<Integer, PendingNotification> entry : pending) {
            final int uid = entry.getKey();
            final PendingNotification current = state.pendingNotifications.get(uid);
            if (current == null || current.replayedAfterRecovery) {
                continue;
            }

            // A recovered watch can request the app ID immediately after CCC
            // subscription, then abandon phase 2 while WPP initialization runs.
            // Re-announce every entry that is still pending once initialization
            // and ANCS configuration are complete. Phase-2-completed entries have
            // already been removed from this map.
            current.attributesRequested = false;
            current.replayedAfterRecovery = true;
            current.timestampMs = nextPendingTimestamp(state);
            support.sendAncsNotificationSourceNotification(createAddedNotificationSource(uid, current.spec));
            replayed++;
        }

        return replayed;
    }

    private NotificationSource createAddedNotificationSource(final int uid, final NotificationSpec spec) {
        return new NotificationSource(uid,
                AncsConstants.EVENT_ID_NOTIFICATION_ADDED,
                AncsConstants.EVENT_FLAGS_IMPORTANT,
                mapNotificationType(spec.getType()),
                (byte) 1);
    }

    /**
     * Called when the user dismisses a notification on the phone.
     */
    public void onDeleteNotification(final int notificationUID) {
        final NotificationState state = getState();

        if (activeIncomingCallUid != null && activeIncomingCallUid == notificationUID) {
            activeIncomingCallUid = null;
        }

        state.pendingNotifications.remove(notificationUID);
        synchronized (state.recentlyCompletedNotifications) {
            state.recentlyCompletedNotifications.remove(notificationUID);
        }
        logger.info("Withings sending REMOVED for UID={}", notificationUID);
        sendNotificationRemoved(notificationUID);
    }

    public void clearActiveIncomingCall() {
        final Integer uid = activeIncomingCallUid;
        if (uid == null) {
            logger.debug("No active incoming call notification to clear");
            return;
        }

        logger.info("Clearing active incoming call notification UID={}", uid);
        onDeleteNotification(uid);
    }

    /**
     * Handle an ANCS Control Point write from the watch requesting notification
     * attributes. The watch sends two requests per notification:
     * <ol>
     *   <li>PHASE1: requests attr0 (app bundle ID)</li>
     *   <li>PHASE2: requests attr1 (title), attr2 (subtitle), attr3 (message)</li>
     * </ol>
     *
     * The watch may interleave requests for different notification UIDs.
     * We respond immediately for each request.
     */
    public long handleNotificationAttributeRequest(GetNotificationAttributes request) {
        logger.debug("Request has ID: " + request.getNotificationUID());
        final NotificationState state = getState();
        final long requestTimestampMs = System.currentTimeMillis();

        NotificationSpec spec = null;
        PendingNotification pending = state.pendingNotifications.get(request.getNotificationUID());
        long acknowledgedNotificationMs = requestTimestampMs;
        if (pending != null) {
            pending.attributesRequested = true;
            spec = pending.spec;
            acknowledgedNotificationMs = pending.timestampMs;
        }
        if (spec == null) {
            synchronized (state.recentlyCompletedNotifications) {
                spec = state.recentlyCompletedNotifications.get(request.getNotificationUID());
            }
        }
        if (spec == null) {
            logger.info("No pending notification with notificationUID " + request.getNotificationUID());

            // Reply on the Data Source to unblock the watch's ANCS state machine
            GetNotificationAttributesResponse emptyResponse = new GetNotificationAttributesResponse(request.getNotificationUID());
            for (RequestedNotificationAttribute requestedAttribute : request.getAttributes()) {
                NotificationAttribute attr = new NotificationAttribute();
                attr.setAttributeID(requestedAttribute.getAttributeID());
                attr.setAttributeMaxLength(requestedAttribute.getAttributeMaxLength());
                attr.setValue("");
                emptyResponse.addAttribute(attr);
            }
            support.sendAncsDataSourceNotification(emptyResponse);
            // The watch can retain an ADDED event across a stale ANCS session even
            // after Android has removed it. Complete the request first, then repeat
            // REMOVED so the recovered session clears the stale notification.
            sendNotificationRemoved(request.getNotificationUID());
            return acknowledgedNotificationMs;
        }

        logger.info("Handling notification attribute request id={}, attrs={}, pendingNow={}",
                request.getNotificationUID(),
                request.getAttributes().size(),
                state.pendingNotifications.size());

        GetNotificationAttributesResponse response = new GetNotificationAttributesResponse(request.getNotificationUID());
        List<RequestedNotificationAttribute> requestedAttributes = request.getAttributes();
        logger.debug(requestedAttributes.size() + " attributes requested.");

        boolean complete = false;

        for (RequestedNotificationAttribute requestedAttribute : requestedAttributes) {
            NotificationAttribute attribute = new NotificationAttribute();
            attribute.setAttributeID(requestedAttribute.getAttributeID());
            attribute.setAttributeMaxLength(requestedAttribute.getAttributeMaxLength());
            logger.debug("Handling attribute " + attribute.getAttributeID() + " with maxLength " + attribute.getAttributeLength());
            String value = "";
            if (requestedAttribute.getAttributeID() == 0) {
                // attr0 = app bundle ID (e.g. "com.whatsapp", "im.molly.app-msg")
                value = getWithingsSourceAppId(spec);
            }
            if (requestedAttribute.getAttributeID() == 1) {
                // attr1 = sender/title line (displayed before the dash on the watch)
                // For messaging apps: sender name (e.g., "~ Bogo", "~ Carlos Mora")
                // For other apps (Gotify, etc.): title if available, otherwise app name
                complete = true;
                value = getWithingsTitle(spec, support.prefersLatestConversationNotification());
            }
            if (requestedAttribute.getAttributeID() == 2) {
                // attr2 = subtitle -- official app always sends empty string here.
                // The watch displays "{attr1} - {attr3}", so attr2 is unused.
                complete = true;
                value = "";
            }
            if (requestedAttribute.getAttributeID() == 3) {
                // attr3 = message body (displayed after the dash on the watch)
                // For messaging apps: message content
                // For other apps: body text, or title if body is absent
                complete = true;
                value = getWithingsBody(spec, support.prefersLatestConversationNotification());
            }

            if (value != null) {
                // Remove linefeed and carriage returns as the watch cannot display this:
                value = value.replace("\n", " ");
                value = value.replace("\r", " ");
                if (requestedAttribute.getAttributeMaxLength() == 0 || requestedAttribute.getAttributeMaxLength() >= value.length()) {
                    attribute.setValue(value);
                } else {
                    attribute.setValue(value.substring(0, requestedAttribute.getAttributeMaxLength()));
                }
            }

            logger.debug("Sending attribute " + attribute.getAttributeID() + " with value " + attribute.getValue());
            response.addAttribute(attribute);
        }

        support.sendAncsDataSourceNotification(response);

        if (complete) {
            // PHASE2 complete -- move notification from pending to recently-completed cache
            PendingNotification completedPending = state.pendingNotifications.remove(request.getNotificationUID());
            if (completedPending != null) {
                logger.info("Completed notification id={}, moving to recent cache, pendingAfter={}",
                        request.getNotificationUID(),
                        state.pendingNotifications.size());
                cacheCompletedNotification(state, request.getNotificationUID(), completedPending.spec);
            }
        }
        return acknowledgedNotificationMs;
    }

    /**
     * Return the oldest ADDED event that the watch has not requested and that was
     * sent after the latest acknowledged notification generation.
     */
    public long getOldestUnrequestedTimestampAfter(final long acknowledgedNotificationMs) {
        final NotificationState state = getState();
        long oldestTimestampMs = Long.MAX_VALUE;
        for (PendingNotification pending : state.pendingNotifications.values()) {
            if (!pending.attributesRequested
                    && !pending.replayedAfterRecovery
                    && pending.timestampMs > acknowledgedNotificationMs) {
                oldestTimestampMs = Math.min(oldestTimestampMs, pending.timestampMs);
            }
        }
        return oldestTimestampMs == Long.MAX_VALUE ? 0 : oldestTimestampMs;
    }

    private static long nextPendingTimestamp(final NotificationState state) {
        synchronized (state) {
            state.lastAssignedPendingTimestampMs = Math.max(
                    System.currentTimeMillis(),
                    state.lastAssignedPendingTimestampMs + 1);
            return state.lastAssignedPendingTimestampMs;
        }
    }

    static String getWithingsTitle(final NotificationSpec spec, final boolean preferLatestConversation) {
        if (preferLatestConversation && spec.getConversationSender() != null) {
            return spec.getConversationSender();
        }
        if (spec.getSender() != null) {
            return spec.getSender();
        }
        if (spec.getPhoneNumber() != null) {
            return spec.getPhoneNumber();
        }
        if (spec.getTitle() != null) {
            return spec.getTitle();
        }
        if (spec.getSourceName() != null) {
            return spec.getSourceName();
        }
        return "Unknown";
    }

    static String getWithingsBody(final NotificationSpec spec, final boolean preferLatestConversation) {
        if (preferLatestConversation && spec.getConversationBody() != null) {
            return spec.getConversationBody();
        }
        if (spec.getBody() != null) {
            return spec.getBody();
        }
        if (spec.getTitle() != null && spec.getSender() != null) {
            return spec.getTitle();
        }
        return " ";
    }

    private void sendNotificationRemoved(final int notificationUID) {
        NotificationSource notificationSource = new NotificationSource(notificationUID,
                                                                        AncsConstants.EVENT_ID_NOTIFICATION_REMOVED,
                                                                        AncsConstants.EVENT_FLAGS_IMPORTANT,
                                                                        (byte)0,
                                                                        (byte)0);
        support.sendAncsNotificationSourceNotification(notificationSource);
    }

    /**
     * Remove pending notifications that the watch never fetched attributes for.
     *
     * The watch normally fetches PHASE1 (attr0) within ~200ms and PHASE2 (attrs 1+2+3)
     * within ~500ms total. However, it skips PHASE2 entirely in ~14% of cases (normal
     * behaviour based on official app captures). This method evicts notifications older
     * than ten minutes to prevent unbounded growth of the pending map while allowing
     * a stalled ANCS session to reconnect and replay them.
     */
    private void evictStalePending(final NotificationState state) {
        final long now = System.currentTimeMillis();
        final Iterator<Map.Entry<Integer, PendingNotification>> it = state.pendingNotifications.entrySet().iterator();
        int evicted = 0;
        while (it.hasNext()) {
            final Map.Entry<Integer, PendingNotification> entry = it.next();
            if (now - entry.getValue().timestampMs > STALE_PENDING_TIMEOUT_MS) {
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            logger.info("Withings evicted {} stale pending notifications, remaining={}", evicted, state.pendingNotifications.size());
        }
    }

    private void cacheCompletedNotification(final NotificationState state, final int notificationUID, final NotificationSpec completedSpec) {
        synchronized (state.recentlyCompletedNotifications) {
            state.recentlyCompletedNotifications.put(notificationUID, completedSpec);
            while (state.recentlyCompletedNotifications.size() > RECENT_NOTIFICATION_CACHE_SIZE) {
                final Integer eldestKey = state.recentlyCompletedNotifications.keySet().iterator().next();
                state.recentlyCompletedNotifications.remove(eldestKey);
            }
        }
    }

    public NotificationSpec getNotificationSpecForSourceAppId(String sourceAppId) {
        final String normalizedSourceAppId = normalizeSourceAppId(sourceAppId);
        final NotificationState state = getState();

        // First try finding it in pending notifications (if still active)
        for (PendingNotification pending : state.pendingNotifications.values()) {
            if (matchesSourceAppId(pending.spec, normalizedSourceAppId)) {
                return pending.spec;
            }
        }

        // Fallback to the latest known notification spec for this app
        return state.latestNotificationByApp.get(normalizedSourceAppId);
    }

    private NotificationState getState() {
        return NOTIFICATION_STATES.computeIfAbsent(getDeviceKey(), ignored -> new NotificationState());
    }

    private String getDeviceKey() {
        if (support.getDevice() != null && support.getDevice().getAddress() != null) {
            return support.getDevice().getAddress();
        }
        return UNKNOWN_DEVICE_KEY;
    }

    private String normalizeSourceAppId(final String sourceAppId) {
        if (sourceAppId == null) {
            return null;
        }
        return WITHINGS_SOURCE_APP_SUFFIX.matcher(sourceAppId).replaceFirst("");
    }

    static String getWithingsSourceAppId(final NotificationSpec spec) {
        if (spec == null || spec.getSourceAppId() == null) {
            return null;
        }

        if (WITHINGS_SOURCE_APP_SUFFIX.matcher(spec.getSourceAppId()).find()) {
            return spec.getSourceAppId();
        }

        if (spec.getType() == NotificationType.GENERIC_PHONE) {
            return WITHINGS_DIALER_APP_ID + "-ringing";
        }

        return spec.getSourceAppId();
    }

    private boolean matchesSourceAppId(final NotificationSpec notificationSpec, final String normalizedSourceAppId) {
        if (notificationSpec.getSourceAppId() != null && notificationSpec.getSourceAppId().equalsIgnoreCase(normalizedSourceAppId)) {
            return true;
        }

        final String withingsSourceAppId = normalizeSourceAppId(getWithingsSourceAppId(notificationSpec));
        return withingsSourceAppId != null && withingsSourceAppId.equalsIgnoreCase(normalizedSourceAppId);
    }

    private byte mapNotificationType(NotificationType type) {
        switch (type) {
            case GENERIC_ALARM_CLOCK:
            case BUSINESS_CALENDAR:
            case GENERIC_CALENDAR:
                return AncsConstants.CATEGORY_ID_SCHEDULE;
            case GENERIC_EMAIL:
            case YAHOO_MAIL:
            case GOOGLE_INBOX:
            case GMAIL:
            case OUTLOOK:
                return AncsConstants.CATEGORY_ID_EMAIL;
            case GENERIC_NAVIGATION:
                return AncsConstants.CATEGORY_ID_LOCATION;
            case GENERIC_PHONE:
                return AncsConstants.CATEGORY_ID_INCOMING_CALL;
            case MAILBOX:
                return AncsConstants.CATEGORY_ID_MISSED_CALL;
            case LINE:
            case SIGNAL:
            case WIRE:
            case SKYPE:
            case SLACK:
            case SNAPCHAT:
            case TELEGRAM:
            case THREEMA:
            case KONTALK:
            case ANTOX:
            case DISCORD:
            case TRANSIT:
            case TWITTER:
            case VIBER:
            case WECHAT:
            case WHATSAPP:
            case FACEBOOK:
            case FACEBOOK_MESSENGER:
            case LINKEDIN:
            case HIPCHAT:
            case INSTAGRAM:
            case KAKAO_TALK:
            case GENERIC_SMS:
            case GOOGLE_MESSENGER:
            case GOOGLE_HANGOUTS:
                return AncsConstants.CATEGORY_ID_SOCIAL;
            default:
                return AncsConstants.CATEGORY_ID_OTHER;
        }
    }

    /**
     * Per-device notification state. Keyed by device address so multiple
     * watches can each have independent notification tracking.
     */
    private static final class NotificationState {
        /** Notifications that have been sent as ADDED but haven't completed PHASE2 yet. */
        private final Map<Integer, PendingNotification> pendingNotifications = new ConcurrentHashMap<>();
        /** Latest notification per app, for icon-tap lookup on the watch. */
        private final Map<String, NotificationSpec> latestNotificationByApp = new ConcurrentHashMap<>();
        /** Recently completed notifications, kept for re-fetch requests from the watch. */
        private final LinkedHashMap<Integer, NotificationSpec> recentlyCompletedNotifications = new LinkedHashMap<>(RECENT_NOTIFICATION_CACHE_SIZE + 1, 0.75f, true);
        /** Strictly monotonic wall-clock timestamp used as the watchdog acknowledgment watermark. */
        private long lastAssignedPendingTimestampMs;
    }

    /**
     * Wraps a {@link NotificationSpec} with a timestamp for stale eviction.
     */
    private static final class PendingNotification {
        final NotificationSpec spec;
        volatile long timestampMs;
        volatile boolean attributesRequested;
        volatile boolean replayedAfterRecovery;

        PendingNotification(NotificationSpec spec, long timestampMs) {
            this.spec = spec;
            this.timestampMs = timestampMs;
        }
    }
    
}
