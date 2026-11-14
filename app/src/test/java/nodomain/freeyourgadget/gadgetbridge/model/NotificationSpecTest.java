/*  Copyright (C) 2026 d3vv3

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
package nodomain.freeyourgadget.gadgetbridge.model;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Parcel;

import org.junit.Test;

import java.util.ArrayList;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NotificationSpecTest extends TestBase {
    @Test
    public void conversationMetadataSurvivesParcelableIntent() {
        for (NotificationSpec spec : new NotificationSpec[]{new NotificationSpec(), new NotificationSpec(123)}) {
            populateConversation(spec);
            final ArrayList<NotificationSpec.Action> actions = new ArrayList<>();
            actions.add(new NotificationSpec.Action(NotificationSpec.Action.TYPE_WEARABLE_REPLY, 42L, "Reply"));
            spec.setAttachedActions(actions);
            final Intent intent = new Intent(DeviceService.ACTION_NOTIFICATION)
                    .putExtra(DeviceService.EXTRA_NOTIFICATION_SPEC, spec);
            final Parcel parcel = Parcel.obtain();
            try {
                intent.writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                final Intent restoredIntent = Intent.CREATOR.createFromParcel(parcel);
                restoredIntent.setExtrasClassLoader(NotificationSpec.class.getClassLoader());
                final NotificationSpec restored = restoredIntent.getParcelableExtra(DeviceService.EXTRA_NOTIFICATION_SPEC);
                assertEquals(spec, restored);
                assertEquals(spec.getId(), restored.getId());
                assertEquals("Group", restored.getConversationTitle());
                assertEquals("Alice", restored.getConversationSender());
                assertEquals("Latest message", restored.getConversationBody());
                assertEquals(42L, restored.getAttachedActions().get(0).getHandle());
            } finally {
                parcel.recycle();
            }
        }
    }

    @Test
    public void privacyClearsConversationAlongsideStandardFieldsWithoutChangingOriginal() {
        final NotificationSpec spec = new NotificationSpec();
        populateConversation(spec);
        final NotificationSpec bodyHidden = spec.withMessageBodyCleared();
        assertNull(bodyHidden.getBody());
        assertNull(bodyHidden.getConversationBody());
        assertEquals("Group", bodyHidden.getConversationTitle());
        assertEquals("Alice", bodyHidden.getConversationSender());

        final NotificationSpec detailsHidden = spec.withMessageDetailsCleared();
        assertNull(detailsHidden.getTitle());
        assertNull(detailsHidden.getSender());
        assertNull(detailsHidden.getBody());
        assertNull(detailsHidden.getConversationTitle());
        assertNull(detailsHidden.getConversationSender());
        assertNull(detailsHidden.getConversationBody());
        assertEquals(spec.getId(), detailsHidden.getId());
        assertEquals("Standard body", spec.getBody());
        assertEquals("Latest message", spec.getConversationBody());
    }

    @Test
    public void conversationTextUsesDeviceFilterAndTransliterator() {
        final DeviceSupport support = mock(DeviceSupport.class);
        final GBDevice device = mock(GBDevice.class);
        final DeviceCoordinator coordinator = mock(DeviceCoordinator.class);
        when(support.getDevice()).thenReturn(device);
        when(device.getDeviceCoordinator()).thenReturn(coordinator);
        when(coordinator.supportsUnicodeEmojis(device)).thenReturn(true);
        when(support.customStringFilter(anyString())).thenAnswer(invocation -> "filtered:" + invocation.getArgument(0));
        final NotificationSpec spec = new NotificationSpec();
        populateConversation(spec);

        final NotificationSpec adapted = spec.transliterated(support, text -> "transliterated:" + text);
        assertEquals("transliterated:filtered:Group", adapted.getConversationTitle());
        assertEquals("transliterated:filtered:Alice", adapted.getConversationSender());
        assertEquals("transliterated:filtered:Latest message", adapted.getConversationBody());
        assertEquals("transliterated:filtered:Standard body", adapted.getBody());
        assertEquals("Latest message", spec.getConversationBody());
        assertEquals(spec.getId(), adapted.getId());
    }

    @Test
    public void conversationTextReceivesRtlFix() {
        final SharedPreferences prefs = GBApplication.getPrefs().getPreferences();
        final boolean hadPreference = prefs.contains(GBPrefs.RTL_SUPPORT);
        final boolean originalPreference = prefs.getBoolean(GBPrefs.RTL_SUPPORT, false);
        prefs.edit().putBoolean(GBPrefs.RTL_SUPPORT, true).apply();
        try {
            final NotificationSpec spec = new NotificationSpec();
            spec.setConversationTitle("אבג");
            spec.setConversationSender("דהו");
            spec.setConversationBody("זחט");
            final NotificationSpec adapted = spec.withRtlFix();
            assertEquals("גבא", adapted.getConversationTitle());
            assertEquals("והד", adapted.getConversationSender());
            assertEquals("טחז", adapted.getConversationBody());
            assertEquals("זחט", spec.getConversationBody());
        } finally {
            final SharedPreferences.Editor editor = prefs.edit();
            if (hadPreference) {
                editor.putBoolean(GBPrefs.RTL_SUPPORT, originalPreference);
            } else {
                editor.remove(GBPrefs.RTL_SUPPORT);
            }
            editor.apply();
        }
    }

    private static void populateConversation(final NotificationSpec spec) {
        spec.setTitle("Group (9 messages): Alice");
        spec.setSender("Standard sender");
        spec.setBody("Standard body");
        spec.setConversationTitle("Group");
        spec.setConversationSender("Alice");
        spec.setConversationBody("Latest message");
    }
}
