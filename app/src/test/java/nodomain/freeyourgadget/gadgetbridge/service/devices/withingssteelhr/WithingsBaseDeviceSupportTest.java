/*  Copyright (C) 2026 Gadgetbridge contributors

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class WithingsBaseDeviceSupportTest {
    @Test
    public void smartWakeupIntervalIsClampedToDeviceLimit() {
        assertEquals(60, WithingsBaseDeviceSupport.clampSmartWakeupInterval(90, 60));
        assertEquals(45, WithingsBaseDeviceSupport.clampSmartWakeupInterval(45, 60));
    }

    @Test
    public void smartWakeupIntervalDefaultsToTwentyMinutes() {
        assertEquals(20, WithingsBaseDeviceSupport.clampSmartWakeupInterval(null, 60));
        assertEquals(20, WithingsBaseDeviceSupport.clampSmartWakeupInterval(0, 60));
    }

    @Test
    public void ancsDataSourceExactChunkHasEmptyTerminator() {
        final List<byte[]> chunks = WithingsBaseDeviceSupport.chunkAncsDataSourcePayload(new byte[20]);

        assertEquals(2, chunks.size());
        assertEquals(20, chunks.get(0).length);
        assertEquals(0, chunks.get(1).length);
    }

    @Test
    public void ancsDataSourceShortFinalChunkNeedsNoTerminator() {
        final List<byte[]> chunks = WithingsBaseDeviceSupport.chunkAncsDataSourcePayload(new byte[21]);

        assertEquals(2, chunks.size());
        assertEquals(20, chunks.get(0).length);
        assertEquals(1, chunks.get(1).length);
    }

    @Test
    public void ancsDataSourceMultipleFullChunksHaveEmptyTerminator() {
        final List<byte[]> chunks = WithingsBaseDeviceSupport.chunkAncsDataSourcePayload(new byte[40]);

        assertEquals(3, chunks.size());
        assertEquals(20, chunks.get(0).length);
        assertEquals(20, chunks.get(1).length);
        assertEquals(0, chunks.get(2).length);
    }

    @Test
    public void manualFetchAlwaysRunsFullSync() {
        assertTrue(WithingsBaseDeviceSupport.shouldRunFullSync("manual-fetch", 100_000L, 99_999L));
    }

    @Test
    public void automaticTriggerUsesLastSyncFreshness() {
        assertFalse(WithingsBaseDeviceSupport.shouldRunFullSync("watch-sync-request", 100_000L, 50_000L));
        assertTrue(WithingsBaseDeviceSupport.shouldRunFullSync("watch-sync-request", 110_001L, 50_000L));
    }

    @Test
    public void ancsStallCheckUsesOldestPendingDeadline() {
        assertEquals(20_000L, WithingsBaseDeviceSupport.calculateAncsStallCheckDelay(
                20_000L,
                10_000L,
                0L));
    }

    @Test
    public void ancsStallCheckWaitsUntilRecoveryCooldownExpires() {
        assertEquals(150_000L, WithingsBaseDeviceSupport.calculateAncsStallCheckDelay(
                50_000L,
                10_000L,
                20_000L));
    }

    @Test
    public void overdueAncsStallRunsImmediately() {
        assertEquals(0L, WithingsBaseDeviceSupport.calculateAncsStallCheckDelay(
                400_000L,
                10_000L,
                20_000L));
    }
}
