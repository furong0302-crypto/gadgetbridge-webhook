/*  Copyright (C) 2026 Frank Ertl

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.AppNotificationType;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.IconHelper;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.WithingsBaseDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class NotificationProviderTest extends TestBase {

    @Test
    public void testRegularMessageAppsStayRaw() {
        NotificationSpec spec = new NotificationSpec();
        spec.setSourceAppId("org.forkgram.messenger");
        spec.setType(NotificationType.TELEGRAM);

        assertEquals("org.forkgram.messenger", NotificationProvider.getWithingsSourceAppId(spec));
    }

    @Test
    public void testForkgramIsClassifiedAsTelegram() {
        assertEquals(NotificationType.TELEGRAM, AppNotificationType.getInstance().get("org.forkgram.messenger"));
    }

    @Test
    public void testWhatsappIsClassifiedAsWhatsapp() {
        assertEquals(NotificationType.WHATSAPP, AppNotificationType.getInstance().get("com.whatsapp"));
    }

    @Test
    public void testExplicitMsgSuffixIsPreserved() {
        NotificationSpec spec = new NotificationSpec();
        spec.setSourceAppId("org.fossify.messages-msg");
        spec.setType(NotificationType.GENERIC_SMS);

        assertEquals("org.fossify.messages-msg", NotificationProvider.getWithingsSourceAppId(spec));
    }

    @Test
    public void testIncomingCallsUseDialerAppId() {
        NotificationSpec spec = new NotificationSpec();
        spec.setSourceAppId("com.android.dialer");
        spec.setType(NotificationType.GENERIC_PHONE);

        assertEquals("dialerApp-ringing", NotificationProvider.getWithingsSourceAppId(spec));
    }

    @Test
    public void testNotificationSpecCarriesDedicatedIconPackage() {
        NotificationSpec spec = new NotificationSpec();
        spec.setSourceAppId("com.whatsapp");
        spec.setIconPackageId("com.whatsapp");

        assertEquals("com.whatsapp", spec.getIconPackageId());
    }

    @Test
    public void testScanWatchUsesLatestConversationMessage() {
        NotificationSpec spec = new NotificationSpec();
        spec.setTitle("Family Group (9 messages): Alice");
        spec.setBody("Standard body");
        spec.setConversationSender("Alice");
        spec.setConversationBody("Latest message");

        assertEquals("Alice", NotificationProvider.getWithingsTitle(spec, true));
        assertEquals("Latest message", NotificationProvider.getWithingsBody(spec, true));
    }

    @Test
    public void testOtherWithingsDevicesKeepStandardNotificationFields() {
        NotificationSpec spec = new NotificationSpec();
        spec.setTitle("Family Group (9 messages): Alice");
        spec.setBody("Standard body");
        spec.setConversationSender("Alice");
        spec.setConversationBody("Latest message");

        assertEquals("Family Group (9 messages): Alice", NotificationProvider.getWithingsTitle(spec, false));
        assertEquals("Standard body", NotificationProvider.getWithingsBody(spec, false));
    }

    @Test
    public void testPendingNotificationsAreReplayedAfterReconnect() {
        final WithingsBaseDeviceSupport support = mock(WithingsBaseDeviceSupport.class);
        final GBDevice device = mock(GBDevice.class);
        when(device.getAddress()).thenReturn("00:11:22:33:44:55");
        when(support.getDevice()).thenReturn(device);
        final NotificationProvider provider = new NotificationProvider(support);

        final NotificationSpec first = new NotificationSpec(101);
        first.setType(NotificationType.GENERIC_EMAIL);
        final NotificationSpec second = new NotificationSpec(102);
        second.setType(NotificationType.TELEGRAM);
        provider.notifyClient(first);
        provider.notifyClient(second);

        assertEquals(2, provider.replayPendingNotifications());
        final ArgumentCaptor<NotificationSource> sourceCaptor = ArgumentCaptor.forClass(NotificationSource.class);
        verify(support, times(4)).sendAncsNotificationSourceNotification(sourceCaptor.capture());
        final List<NotificationSource> sentSources = sourceCaptor.getAllValues();
        assertEquals(101, sentSources.get(0).getNotificationUID());
        assertEquals(102, sentSources.get(1).getNotificationUID());
        assertEquals(101, sentSources.get(2).getNotificationUID());
        assertEquals(102, sentSources.get(3).getNotificationUID());

        provider.onDeleteNotification(first.getId());
        assertEquals(0, provider.replayPendingNotifications());
    }

    @Test
    public void testPhaseOneRequestedNotificationIsReplayedAfterReconnect() {
        final WithingsBaseDeviceSupport support = mock(WithingsBaseDeviceSupport.class);
        final GBDevice device = mock(GBDevice.class);
        when(device.getAddress()).thenReturn("00:11:22:33:44:66");
        when(support.getDevice()).thenReturn(device);
        final NotificationProvider provider = new NotificationProvider(support);

        final NotificationSpec spec = new NotificationSpec(201);
        spec.setType(NotificationType.TELEGRAM);
        provider.notifyClient(spec);

        final GetNotificationAttributes request = new GetNotificationAttributes();
        request.setNotificationUID(spec.getId());
        final RequestedNotificationAttribute appIdentifier = new RequestedNotificationAttribute();
        appIdentifier.setAttributeID((byte) 0);
        request.addAttribute(appIdentifier);
        final long acknowledgedTimestamp = provider.handleNotificationAttributeRequest(request);

        assertEquals(1, provider.replayPendingNotifications());
        assertEquals(0, provider.getOldestUnrequestedTimestampAfter(acknowledgedTimestamp));
        verify(support, times(2)).sendAncsNotificationSourceNotification(any(NotificationSource.class));
    }

    @Test
    public void testPhaseTwoCompletedNotificationIsNotReplayed() {
        final WithingsBaseDeviceSupport support = mock(WithingsBaseDeviceSupport.class);
        final GBDevice device = mock(GBDevice.class);
        when(device.getAddress()).thenReturn("00:11:22:33:44:67");
        when(support.getDevice()).thenReturn(device);
        final NotificationProvider provider = new NotificationProvider(support);

        final NotificationSpec spec = new NotificationSpec(202);
        spec.setType(NotificationType.TELEGRAM);
        provider.notifyClient(spec);

        final GetNotificationAttributes request = new GetNotificationAttributes();
        request.setNotificationUID(spec.getId());
        final RequestedNotificationAttribute title = new RequestedNotificationAttribute();
        title.setAttributeID((byte) 1);
        request.addAttribute(title);
        provider.handleNotificationAttributeRequest(request);

        assertEquals(0, provider.replayPendingNotifications());
        verify(support, times(1)).sendAncsNotificationSourceNotification(any(NotificationSource.class));
    }

    @Test
    public void testLaterRequestAcknowledgesEarlierSkippedNotification() {
        final WithingsBaseDeviceSupport support = mock(WithingsBaseDeviceSupport.class);
        final GBDevice device = mock(GBDevice.class);
        when(device.getAddress()).thenReturn("00:11:22:33:44:88");
        when(support.getDevice()).thenReturn(device);
        final NotificationProvider provider = new NotificationProvider(support);

        final NotificationSpec skipped = new NotificationSpec(401);
        skipped.setType(NotificationType.TELEGRAM);
        final NotificationSpec requested = new NotificationSpec(402);
        requested.setType(NotificationType.GENERIC_EMAIL);
        provider.notifyClient(skipped);
        provider.notifyClient(requested);

        assertTrue(provider.getOldestUnrequestedTimestampAfter(0) > 0);

        final GetNotificationAttributes request = new GetNotificationAttributes();
        request.setNotificationUID(requested.getId());
        final long acknowledgedTimestamp = provider.handleNotificationAttributeRequest(request);

        assertEquals(0, provider.getOldestUnrequestedTimestampAfter(acknowledgedTimestamp));
    }

    @Test
    public void testEarlierRequestDoesNotAcknowledgeLaterNotification() {
        final WithingsBaseDeviceSupport support = mock(WithingsBaseDeviceSupport.class);
        final GBDevice device = mock(GBDevice.class);
        when(device.getAddress()).thenReturn("00:11:22:33:44:99");
        when(support.getDevice()).thenReturn(device);
        final NotificationProvider provider = new NotificationProvider(support);

        final NotificationSpec requested = new NotificationSpec(501);
        requested.setType(NotificationType.GENERIC_EMAIL);
        final NotificationSpec later = new NotificationSpec(502);
        later.setType(NotificationType.TELEGRAM);
        provider.notifyClient(requested);
        provider.notifyClient(later);

        final GetNotificationAttributes request = new GetNotificationAttributes();
        request.setNotificationUID(requested.getId());
        final long acknowledgedTimestamp = provider.handleNotificationAttributeRequest(request);

        assertTrue(provider.getOldestUnrequestedTimestampAfter(acknowledgedTimestamp) > acknowledgedTimestamp);
    }

    @Test
    public void testMissingNotificationIsRemovedAfterEmptyResponse() {
        final WithingsBaseDeviceSupport support = mock(WithingsBaseDeviceSupport.class);
        final GBDevice device = mock(GBDevice.class);
        when(device.getAddress()).thenReturn("00:11:22:33:44:77");
        when(support.getDevice()).thenReturn(device);
        final NotificationProvider provider = new NotificationProvider(support);

        final GetNotificationAttributes request = new GetNotificationAttributes();
        request.setNotificationUID(301);
        final RequestedNotificationAttribute title = new RequestedNotificationAttribute();
        title.setAttributeID((byte) 1);
        title.setAttributeMaxLength((short) 60);
        request.addAttribute(title);

        provider.handleNotificationAttributeRequest(request);

        final InOrder inOrder = inOrder(support);
        inOrder.verify(support).sendAncsDataSourceNotification(any(GetNotificationAttributesResponse.class));
        final ArgumentCaptor<NotificationSource> sourceCaptor = ArgumentCaptor.forClass(NotificationSource.class);
        inOrder.verify(support).sendAncsNotificationSourceNotification(sourceCaptor.capture());
        assertEquals(301, sourceCaptor.getValue().getNotificationUID());
        assertEquals(AncsConstants.EVENT_ID_NOTIFICATION_REMOVED, sourceCaptor.getValue().getEventID());
    }

    @Test
    public void testIconRenderingCentersTallDrawable() {
        final Bitmap bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 80; y++) {
            for (int x = 10; x < 30; x++) {
                bitmap.setPixel(x, y, Color.BLACK);
            }
        }

        final byte[] iconBytes = IconHelper.getIconBytesFromBitmap(bitmap, 28, 28);

        final int firstFilledColumn = firstFilledColumn(iconBytes, 28, 28);
        final int lastFilledColumn = lastFilledColumn(iconBytes, 28, 28);

        assertTrue(firstFilledColumn > 0);
        assertTrue(lastFilledColumn < 27);
        assertTrue(firstFilledColumn >= 8);
        assertTrue(lastFilledColumn <= 19);
    }

    @Test
    public void testIconRenderingCropsTransparentPadding() {
        final Bitmap bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        for (int y = 20; y < 60; y++) {
            for (int x = 20; x < 60; x++) {
                bitmap.setPixel(x, y, Color.BLACK);
            }
        }

        final byte[] iconBytes = IconHelper.getIconBytesFromBitmap(bitmap, 28, 28);

        assertTrue(firstFilledColumn(iconBytes, 28, 28) <= 1);
        assertTrue(lastFilledColumn(iconBytes, 28, 28) >= 26);
    }

    @Test
    public void testIconRenderingKeepsPaddingWhenCroppingDisabled() {
        final Bitmap bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888);
        for (int y = 20; y < 60; y++) {
            for (int x = 20; x < 60; x++) {
                bitmap.setPixel(x, y, Color.BLACK);
            }
        }

        final byte[] iconBytes = IconHelper.getIconBytesFromBitmap(bitmap, 28, 28, false);

        assertTrue(firstFilledColumn(iconBytes, 28, 28) > 1);
        assertTrue(lastFilledColumn(iconBytes, 28, 28) < 26);
    }

    private boolean columnHasPixels(final byte[] iconBytes, final int column, final int width, final int height) {
        final int bytesPerColumn = (height + 7) / 8;
        for (int i = 0; i < bytesPerColumn; i++) {
            if (iconBytes[column * bytesPerColumn + i] != 0) {
                return true;
            }
        }
        return false;
    }

    private int firstFilledColumn(final byte[] iconBytes, final int width, final int height) {
        for (int column = 0; column < width; column++) {
            if (columnHasPixels(iconBytes, column, width, height)) {
                return column;
            }
        }

        return -1;
    }

    private int lastFilledColumn(final byte[] iconBytes, final int width, final int height) {
        for (int column = width - 1; column >= 0; column--) {
            if (columnHasPixels(iconBytes, column, width, height)) {
                return column;
            }
        }

        return -1;
    }
}
