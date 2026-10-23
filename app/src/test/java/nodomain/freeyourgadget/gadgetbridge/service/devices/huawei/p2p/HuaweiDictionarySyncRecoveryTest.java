package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.p2p;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class HuaweiDictionarySyncRecoveryTest extends TestBase {
    private static final int DICT_CLASS = 500002;
    private TestService service;
    private Result result;

    private static class TestService extends HuaweiP2PDataDictionarySyncService {
        TestService() { super(null); }
        @Override public void sendPing(HuaweiP2PCallback callback) { callback.onResponse(0xca, null); }
        @Override public void sendCommand(byte[] data, HuaweiP2PCallback callback) { }
    }

    private static class Result implements HuaweiP2PDataDictionarySyncService.DictionarySyncCallback {
        int completions;
        boolean success;
        @Override public long onGetLastDataSyncTimestamp(int dictClass) { return 0; }
        @Override public void onData(int dictClass, List<HuaweiP2PDataDictionarySyncService.DictData> data) { }
        @Override public void onComplete(boolean complete) { completions++; success = complete; }
    }

    @Before public void prepareService() {
        service = new TestService();
        service.registered();
        result = new Result();
        start();
    }

    private void start() {
        service.startSync(new ArrayList<>(Collections.singletonList(DICT_CLASS)), result);
    }

    private byte[] response(int operation) {
        byte[] tlv = new HuaweiTLV().put(1, operation)
                .put(2, DICT_CLASS).serialize();
        byte[] data = new byte[tlv.length + 1];
        data[0] = 1;
        System.arraycopy(tlv, 0, data, 1, tlv.length);
        return data;
    }

    @Test public void missingReplyReleasesSyncAndAllowsRetry() {
        shadowOf(Looper.getMainLooper()).idleFor(61, TimeUnit.SECONDS);
        assertEquals(1, result.completions);
        assertFalse(result.success);
        start();
        shadowOf(Looper.getMainLooper()).idleFor(61, TimeUnit.SECONDS);
        assertEquals(2, result.completions);
    }

    @Test public void malformedReplyBeforeClassReleasesPendingCallbackOnce() {
        service.handleData(new byte[]{1});
        assertEquals(1, result.completions);
        assertFalse(result.success);
        shadowOf(Looper.getMainLooper()).idleFor(61, TimeUnit.SECONDS);
        assertEquals(1, result.completions);
    }

    @Test public void truncatedIntegerReleasesSync() {
        byte[] tlv = new HuaweiTLV().put(1, (byte) 1).serialize();
        byte[] data = new byte[tlv.length + 1];
        data[0] = 1;
        System.arraycopy(tlv, 0, data, 1, tlv.length);
        service.handleData(data);
        assertEquals(1, result.completions);
        assertFalse(result.success);
    }

    @Test public void unsupportedOperationReleasesSync() {
        service.handleData(response(2));
        assertEquals(1, result.completions);
        assertFalse(result.success);
    }

    @Test public void completedReplyCancelsTimeout() {
        service.handleData(response(1));
        assertEquals(1, result.completions);
        assertTrue(result.success);
        shadowOf(Looper.getMainLooper()).idleFor(61, TimeUnit.SECONDS);
        assertEquals(1, result.completions);
    }

    @Test public void disconnectReleasesSyncAndCancelsTimeout() {
        service.unregister();
        assertEquals(1, result.completions);
        assertFalse(result.success);
        shadowOf(Looper.getMainLooper()).idleFor(61, TimeUnit.SECONDS);
        assertEquals(1, result.completions);
    }
}
