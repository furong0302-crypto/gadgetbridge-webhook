package nodomain.freeyourgadget.gadgetbridge.webhook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.Test;

import java.lang.reflect.Field;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class WebhookSessionRecoveryTest extends TestBase {
    @Test public void recoveryPreservesPairingAndIsRateLimited() throws Exception {
        Field field = GBApplication.class.getDeclaredField("deviceService");
        field.setAccessible(true);
        Object previous = field.get(null);
        DeviceService service = mock(DeviceService.class);
        when(service.forDevice(any(GBDevice.class))).thenReturn(service);
        field.set(null, service);
        try {
            GBApplication.getPrefs().getPreferences().edit()
                    .putBoolean(WebhookConfig.PREF_ENABLED, true)
                    .putBoolean(WebhookConfig.PREF_PRE_SYNC, true).commit();
            GBDevice device = createDummyGDevice("AA:BB:CC:11:22:33");
            device.setState(GBDevice.State.INITIALIZED);
            assertTrue(WebhookDeviceSync.INSTANCE.recoverHuaweiSession(device));
            assertFalse(WebhookDeviceSync.INSTANCE.recoverHuaweiSession(device));
            verify(service, times(1)).disconnect();
            verify(service, times(1)).connect(false);
        } finally {
            field.set(null, previous);
            GBApplication.getPrefs().getPreferences().edit().putBoolean(WebhookConfig.PREF_ENABLED, false).commit();
        }
    }

    @Test public void disconnectedDeviceDoesNotTriggerRecovery() {
        assertFalse(WebhookDeviceSync.INSTANCE.recoverHuaweiSession(createDummyGDevice("AA:BB:CC:11:22:44")));
    }
}
