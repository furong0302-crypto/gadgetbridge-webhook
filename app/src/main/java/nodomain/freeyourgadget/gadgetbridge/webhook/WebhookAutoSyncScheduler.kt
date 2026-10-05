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

import android.content.Context
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Lianhuan's primary device-fetch clock.
 *
 * WorkManager periodic jobs are intentionally not used for the five-minute pull:
 * Android enforces a 15-minute minimum for PeriodicWorkRequest. Gadgetbridge is
 * already the long-lived BLE owner, so keeping this tiny scheduler inside the GB
 * process is both simpler and more reliable. A normal WorkManager upload remains
 * registered as a slower fallback in [WebhookScheduler].
 *
 * The tick only asks the connected device to sync. When that sync finishes,
 * GB.signalActivityDataFinish() invokes WebhookScheduler.scheduleImmediate(), so
 * upload happens after fresh samples are committed to the Gadgetbridge database.
 */
object WebhookAutoSyncScheduler {
    private val LOG: Logger = LoggerFactory.getLogger(WebhookAutoSyncScheduler::class.java)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "lianhuan-gb-health-sync").apply { isDaemon = true }
    }

    @Volatile
    private var task: ScheduledFuture<*>? = null

    @Synchronized
    fun schedule(context: Context) {
        task?.cancel(false)
        task = null

        if (!WebhookConfig.isEnabled() || !WebhookConfig.isPreSyncEnabled()) {
            LOG.info("Lianhuan direct health auto-sync disabled")
            return
        }

        val interval = WebhookConfig.PRE_SYNC_MIN_INTERVAL_MINUTES.toLong()
        task = executor.scheduleWithFixedDelay(
            {
                try {
                    val requested = WebhookDeviceSync.syncIfStale()
                    LOG.debug("Lianhuan five-minute device-sync tick requested={}", requested)
                } catch (e: Throwable) {
                    LOG.warn("Lianhuan five-minute device-sync tick failed", e)
                }
            },
            30L,
            interval,
            TimeUnit.MINUTES,
        )
        LOG.info("Lianhuan direct health auto-sync scheduled every {} minutes", interval)
    }
}
