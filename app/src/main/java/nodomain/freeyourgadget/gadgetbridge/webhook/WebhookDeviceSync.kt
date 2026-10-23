/*  Copyright (C) 2026 gadgetbridge-webhook contributors

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
package nodomain.freeyourgadget.gadgetbridge.webhook

import android.os.SystemClock
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Asks the connected devices for a data sync before an upload runs.
 *
 * Why this exists: many devices (Huawei/Honor bands in particular) do not push
 * their recorded data on their own — the phone has to request it. Without a
 * request, Gadgetbridge's local database stays empty and the webhook upload
 * would happily upload nothing. Triggering the fetch first means the upload
 * that follows ([WebhookScheduler.scheduleImmediate] is called by
 * [nodomain.freeyourgadget.gadgetbridge.util.GB.signalActivityDataFinish] when
 * the sync finishes) carries fresh data.
 *
 * The request is throttled by [WebhookConfig.PRE_SYNC_MIN_INTERVAL_MS] so we do
 * not keep the BLE link (and the band's battery) busy, and it is skipped for
 * devices that are not connected or currently busy with another operation.
 */
object WebhookDeviceSync {

    private val LOG: Logger = LoggerFactory.getLogger(WebhookDeviceSync::class.java)
    private val lastSessionRecovery = mutableMapOf<String, Long>()

    /** Reauthenticate an established Huawei session whose encrypted replies no longer validate. */
    @Synchronized
    fun recoverHuaweiSession(device: GBDevice?): Boolean {
        if (device?.isInitialized != true || !WebhookConfig.isEnabled() || !WebhookConfig.isPreSyncEnabled()) return false
        val now = SystemClock.elapsedRealtime()
        val previous = lastSessionRecovery[device.address]
        if (previous != null && now - previous < WebhookConfig.PRE_SYNC_MIN_INTERVAL_MS) return false
        lastSessionRecovery[device.address] = now
        LOG.warn("Reconnecting Huawei health session after encrypted response failure")
        val service = GBApplication.deviceService(device)
        service.disconnect()
        service.connect(false)
        return true
    }

    /**
     * Requests a sync from every connected device, at most once per
     * [WebhookConfig.PRE_SYNC_MIN_INTERVAL_MS].
     *
     * @return true when at least one device was asked to sync.
     */
    @Synchronized
    fun syncIfStale(force: Boolean = false): Boolean {
        if (!WebhookConfig.isPreSyncEnabled()) {
            return false
        }

        val now = System.currentTimeMillis()
        val sinceLast = now - WebhookConfig.getPreSyncLast()
        if (!force && sinceLast < WebhookConfig.PRE_SYNC_MIN_INTERVAL_MS) {
            LOG.debug(
                "Skipping pre-upload sync, last one was {} minutes ago",
                sinceLast / 60000,
            )
            return false
        }

        return try {
            // The device service is bound from GBApplication.onCreate; if the app
            // process was started for this worker only, it may not be up yet.
            GBApplication.deviceService() ?: return false

            var requested = false
            for (device in GBApplication.app().deviceManager.selectedDevices) {
                if (!device.isInitialized) {
                    LOG.debug("Not asking {} for a sync, not connected", device)
                    continue
                }
                if (device.isBusy) {
                    LOG.debug("Not asking {} for a sync, device is busy", device)
                    continue
                }
                LOG.info("Requesting pre-upload sync from {}", device)
                GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
                requested = true
            }

            if (requested) {
                WebhookConfig.setPreSyncLast(now)
            }
            requested
        } catch (e: Exception) {
            // Never let this break the upload itself.
            LOG.warn("Pre-upload sync request failed", e)
            false
        }
    }
}
