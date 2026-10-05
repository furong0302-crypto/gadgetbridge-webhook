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

import android.annotation.SuppressLint
import android.database.Cursor
import android.net.Uri
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Uploads the health data of all known devices to the configured webhook endpoint.
 *
 * Data is read through Gadgetbridge's vendor-neutral [SampleProvider] abstraction
 * (one sample per minute: steps, heart rate, normalized activity kind, intensity),
 * plus a generic "extended" reader for the vendor-specific metric tables (SpO2,
 * stress, HRV/RR intervals, respiratory rate, sleep sessions, daily summaries,
 * PAI, workouts). Which categories are uploaded is controlled by the data-type
 * switches in the Webhook settings ([WebhookConfig.getEnabledDataTypes]).
 *
 * Progress is tracked with a per-device cursor (epoch seconds of the last
 * successfully uploaded sample). The cursor is only advanced after the server
 * acknowledged the upload, so failed uploads are re-sent on the next run.
 */
object WebhookUploader {

    private val LOG: Logger = LoggerFactory.getLogger(WebhookUploader::class.java)

    data class Result(
        val success: Boolean,
        val message: String,
        val uploadedSamples: Int = 0,
        val pendingBind: Boolean = false,
    )

    /** Max rows read per extended table in one upload (newest first). */
    private const val MAX_EXTENDED_ROWS_PER_TABLE = 2000

    private data class ExtTable(
        val name: String,
        val tsColumn: String = "TIMESTAMP",
        val deviceColumn: String = "DEVICE_ID",
        val tsMillis: Boolean = false,
        /** Rows from different tables sharing the same timestamp get distinct seq so the
         *  server keeps both (BASE_ACTIVITY_SUMMARY and HUAWEI_WORKOUT_SUMMARY_SAMPLE). */
        val seq: Int = 0,
    )

    /**
     * Extended metric tables per data category. Column names are the greenDAO
     * generated ones (uppercase). Only tables that actually exist in the local
     * database are read, so devices that never produced a metric are skipped.
     */
    private val EXTENDED_TABLES: Map<String, List<ExtTable>> = mapOf(
        WebhookConfig.TYPE_SPO2 to listOf(
            ExtTable("HuamiSpo2Sample"), ExtTable("CmfSpo2Sample"), ExtTable("ColmiSpo2Sample"),
            ExtTable("HybridHRSpo2Sample"), ExtTable("MoyoungSpo2Sample"), ExtTable("GarminSpo2Sample"),
        ),
        WebhookConfig.TYPE_STRESS to listOf(
            ExtTable("HuamiStressSample"), ExtTable("CmfStressSample"), ExtTable("ColmiStressSample"),
            ExtTable("MoyoungStressSample"), ExtTable("GarminStressSample"), ExtTable("Wena3StressSample"),
        ),
        WebhookConfig.TYPE_HRV to listOf(
            ExtTable("HeartRrIntervalSample"), ExtTable("ColmiHrvValueSample"),
            ExtTable("ColmiHrvSummarySample"), ExtTable("GarminHrvValueSample"), ExtTable("GarminHrvSummarySample"),
        ),
        WebhookConfig.TYPE_RESPIRATION to listOf(
            ExtTable("HuamiSleepRespiratoryRateSample"), ExtTable("GarminRespiratoryRateSample"),
        ),
        WebhookConfig.TYPE_SLEEP_SESSIONS to listOf(
            ExtTable("HuamiSleepSessionSample"), ExtTable("XiaomiSleepTimeSample"),
            ExtTable("CmfSleepSessionSample"), ExtTable("ColmiSleepSessionSample"),
            ExtTable("LefunSleepSample"), ExtTable("XiaomiSleepStageSample"),
        ),
        WebhookConfig.TYPE_DAILY_SUMMARY to listOf(
            ExtTable("XiaomiDailySummarySample"), ExtTable("XiaomiManualSample"),
            ExtTable("HuamiHeartRateManualSample"),
        ),
        WebhookConfig.TYPE_PAI to listOf(ExtTable("HuamiPaiSample")),
        WebhookConfig.TYPE_WORKOUTS to listOf(
            ExtTable("BaseActivitySummary", tsColumn = "START_TIME", tsMillis = true),
            // Huawei/Honor stores workouts in its own table with epoch-second timestamps.
            // seq=1 so the same timestamp keeps both rows on the server.
            ExtTable("HuaweiWorkoutSummarySample", tsColumn = "START_TIMESTAMP", seq = 1),
        ),
    )

    /**
     * Uploads all devices. [maxRangeSeconds] caps how far back a single upload may
     * go (the periodic worker uses the 7-day default; a manual upload may pass a
     * larger value after the user confirmed a full re-upload).
     */
    /**
     * How many days of unsent data exist in total across all devices
     * (based on the upload cursors).
     */
    fun estimateBacklogDays(): Long {
        val now = System.currentTimeMillis() / 1000
        var maxDays = 0L
        try {
            GBApplication.acquireDB().use { db ->
                for (gbDevice in DeviceHelper.getInstance().availableDevices) {
                    if (!gbDevice.type.isSupported) {
                        continue
                    }
                    var from = WebhookConfig.getCursor(gbDevice.address)
                    if (from <= 0) {
                        from = now - WebhookConfig.INITIAL_BACKFILL_SECONDS
                    }
                    val days = (now - from) / 86400
                    if (days > maxDays) {
                        maxDays = days
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("Could not estimate backlog", e)
        }
        return maxDays
    }

    fun uploadAll(maxRangeSeconds: Long = WebhookConfig.MAX_RANGE_SECONDS): Result {
        if (!WebhookConfig.isEnabled()) {
            return Result(true, "Webhook upload disabled")
        }
        val serverUrl = WebhookConfig.getUploadEndpoint()
        if (serverUrl.isEmpty()) {
            return Result(false, "Server URL not configured")
        }
        // Lianhuan direct-health uses its narrow bearer token. The token can only
        // write wearable health data; it is not accepted by chat, memory or MCP APIs.
        val token = WebhookConfig.getToken()
        if (token.isEmpty()) {
            return Result(false, "Lianhuan health sync token not configured")
        }

        val nowSeconds = System.currentTimeMillis() / 1000
        var anyFailure = false
        var anyPending = false
        var totalSamples = 0
        var lastMessage = ""

        try {
            GBApplication.acquireDB().use { db ->
                for (gbDevice in DeviceHelper.getInstance().availableDevices) {
                    if (!gbDevice.type.isSupported) {
                        continue
                    }
                    val result = uploadDevice(gbDevice, db, serverUrl, token, nowSeconds, maxRangeSeconds)
                    totalSamples += result.uploadedSamples
                    if (result.pendingBind) {
                        anyPending = true
                    } else if (!result.success) {
                        anyFailure = true
                        lastMessage = result.message
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Webhook upload failed", e)
            WebhookConfig.setPairStatus(WebhookConfig.PAIR_STATUS_FAILED)
            return Result(false, e.message ?: "Exception during upload")
        }

        WebhookConfig.setLastExecution(System.currentTimeMillis())
        val message = if (anyFailure) "Partial failure: $lastMessage" else "OK, $totalSamples samples uploaded"
        WebhookConfig.setLastStatus(message)
        if (!anyFailure) {
            WebhookConfig.setLastError("")
        }
        WebhookConfig.setPairStatus(
            when {
                anyFailure -> WebhookConfig.PAIR_STATUS_FAILED
                anyPending -> WebhookConfig.PAIR_STATUS_PENDING
                else -> WebhookConfig.PAIR_STATUS_OK
            }
        )
        LOG.info("Webhook upload finished: {}", message)
        return Result(!anyFailure, message, totalSamples)
    }

    private fun uploadDevice(
        gbDevice: GBDevice,
        db: DBHandler,
        serverUrl: String,
        token: String,
        nowSeconds: Long,
        maxRangeSeconds: Long,
    ): Result {
        val address = gbDevice.address
        val coordinator = gbDevice.deviceCoordinator
        val provider = coordinator.getSampleProvider(gbDevice, db.daoSession)
        if (provider == null) {
            return Result(true, "No sample provider for ${gbDevice.name}")
        }

        var from = WebhookConfig.getCursor(address)
        if (from <= 0) {
            from = nowSeconds - WebhookConfig.INITIAL_BACKFILL_SECONDS
        }
        // Re-scan window: at least the last 24h, but if the cursor is old (the
        // band and phone were disconnected for a while, so the band only synced
        // its locally stored history when reconnected), scan back by the cursor
        // age — capped at 7 days, the typical on-device storage period.
        // The server upserts idempotently, so re-uploading is harmless.
        val cursorAge = nowSeconds - from
        val lookbackSeconds = minOf(
            WebhookConfig.INITIAL_BACKFILL_SECONDS,
            maxOf(24 * 60 * 60L, cursorAge),
        )
        from = minOf(from, nowSeconds - lookbackSeconds)
        if (from >= nowSeconds) {
            return Result(true, "Nothing new for ${gbDevice.name}")
        }
        val to = minOf(nowSeconds, from + maxRangeSeconds)

        val enabledTypes = WebhookConfig.getEnabledDataTypes()

        // While the device is waiting for pairing, only send a light heartbeat
        // (device info + binding code, no samples) — the server rejects the data
        // anyway, and large payloads over a slow link would just time out.
        val pendingOnly = WebhookConfig.isPendingBind(address)
        val samples = if (pendingOnly) {
            emptyList()
        } else {
            provider.getAllActivitySamples(from.toInt(), to.toInt())
        }

                // Live battery level, if the device is currently managed by the DeviceManager.
        val liveDevice = GBApplication.app().deviceManager.getDeviceByAddress(address)
        val battery = (liveDevice ?: gbDevice).getBatteryLevel(0)

        // Diagnostics: what the provider actually returned (first samples).
        LOG.info(
            "Webhook: {} provider={} range=[{}..{}] samples={}",
            address,
            provider.javaClass.simpleName,
            from,
            to,
            samples.size,
        )
        samples.take(5).forEach { s ->
            LOG.info(
                "Webhook: sample ts={} kind={} rawKind={} steps={} hr={} intensity={}",
                s.timestamp,
                provider.normalizeType(s.rawKind).name,
                s.rawKind,
                s.steps,
                s.heartRate,
                s.intensity,
            )
        }

        val deviceJson = JSONObject()
        deviceJson.put("address", address)
        deviceJson.put("name", gbDevice.name)
        deviceJson.put("type", gbDevice.type.name)
        // Binding code lets the server bind this device to a chat session (/bind command).
        deviceJson.put("binding_code", WebhookConfig.getOrCreateBindingCode())
        if (battery in 0..100 && WebhookConfig.TYPE_BATTERY in enabledTypes) {
            deviceJson.put("battery", battery)
        }

        val samplesJson = JSONArray()
        for (sample in samples) {
            val entry = JSONObject()
            entry.put("ts", sample.timestamp)
            entry.put("kind", provider.normalizeType(sample.rawKind).name)
            val steps = sample.steps
            if (steps >= 0) {
                entry.put("steps", steps)
            }
            val hr = sample.heartRate
            if (hr > 0) {
                entry.put("hr", hr)
            }
            val intensity = provider.normalizeIntensity(sample.rawIntensity)
            if (intensity >= 0) {
                entry.put("intensity", intensity)
            }
            if (WebhookConfig.TYPE_DISTANCE in enabledTypes) {
                val distanceCm = sample.distanceCm
                if (distanceCm >= 0) {
                    entry.put("distance_cm", distanceCm)
                }
                val calories = sample.activeCalories
                if (calories >= 0) {
                    entry.put("calories", calories)
                }
            }
            samplesJson.put(entry)
        }

        val body = JSONObject()
        body.put("device", deviceJson)
        body.put("since", from)
        body.put("samples", samplesJson)

        val extended = if (pendingOnly) JSONObject() else readExtended(db, gbDevice, from, to, enabledTypes)
        if (extended.length() > 0) {
            body.put("extended", extended)
        }

        val headers = mutableMapOf("Content-Type" to "application/json")
        if (token.isNotEmpty()) {
            headers["Authorization"] = "Bearer $token"
        }

        // Use our own client with long timeouts: Cloudflare edges are often slow
        // (multi-second TLS handshakes), and the default 10s OkHttp timeout would
        // fail even though the endpoint is reachable.
        val response = postJson(serverUrl, headers, body.toString())

        if (response != null && response.optString("status") == "ok") {
            WebhookConfig.setCursor(address, to)
            WebhookConfig.setPendingBind(address, false)
            LOG.info(
                "Uploaded {} samples + {} extended categories for {} ({})",
                samplesJson.length(),
                extended.length(),
                gbDevice.name,
                address
            )
            return Result(true, "OK", samplesJson.length())
        }

        // Device not bound yet: server rejected the data, phone enters "waiting for
        // pairing". Reported as success so the worker does not retry in a tight loop;
        // the next periodic run re-checks and starts uploading once paired.
        if (response != null && response.optString("status") == "pending_bind") {
            val message = response.optString("message", "等待配对")
            LOG.info("Device {} is not paired yet: {}", gbDevice.name, message)
            WebhookConfig.setPairStatus(WebhookConfig.PAIR_STATUS_PENDING)
            val wasPending = WebhookConfig.isPendingBind(address)
            // From now on only send light heartbeats for this device until it is paired.
            WebhookConfig.setPendingBind(address, true)
            // Notify once when first entering the waiting state.
            if (!wasPending) {
                WebhookNotifier.notifyPendingBind(
                    GBApplication.getContext(),
                    gbDevice.name,
                    WebhookConfig.getOrCreateBindingCode(),
                )
            }
            return Result(true, message, pendingBind = true)
        }

        val serverMessage = response?.optString("message")?.takeIf { it.isNotBlank() }
            ?: "no response from server"
        LOG.warn("Webhook rejected for {}: {}", gbDevice.name, serverMessage)
        return Result(false, "Server: $serverMessage")
    }

    /**
     * greenDAO stores tables as UPPER_SNAKE (e.g. "BASE_ACTIVITY_SUMMARY") while
     * the entity class name is camelCase ("BaseActivitySummary"). SQLite treats
     * quoted identifiers as case-sensitive, so we must use the real table name.
     */
    private fun dbTableName(className: String): String =
        className.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").uppercase()

    /**
     * Reads the enabled extended metric tables for this device via raw SQL and
     * returns a JSON object like {"spo2": [{"timestamp":..., "spo2": 97}, ...]}.
     * All values are normalized to epoch seconds; blob columns are skipped.
     */
    private fun readExtended(
        db: DBHandler,
        gbDevice: GBDevice,
        from: Long,
        to: Long,
        enabledTypes: Set<String>,
    ): JSONObject {
        val result = JSONObject()
        val session = db.daoSession
        val deviceEntity = DBHelper.getDevice(gbDevice, session)
        val deviceId = deviceEntity.id
        val knownTables = mutableSetOf<String>()

        fun tableExists(name: String): Boolean {
            val dbName = dbTableName(name)
            if (dbName in knownTables) {
                return true
            }
            val cursor = session.database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(dbName),
            )
            val exists = cursor.use { it.moveToFirst() }
            if (exists) {
                knownTables.add(dbName)
            }
            return exists
        }

        // ---- diagnostics (temporary): dump what the extended reader actually sees ----
        try {
            val devRows = session.database.rawQuery("SELECT _id, NAME, IDENTIFIER FROM DEVICE", null).use { c ->
                val sb = StringBuilder()
                while (c.moveToNext()) {
                    sb.append("[id=").append(c.getLong(0)).append(" name=").append(c.getString(1))
                        .append(" idt=").append(c.getString(2)).append("] ")
                }
                sb.toString()
            }
            LOG.warn("Webhook diag: deviceEntityId={} devices={}", deviceId, devRows)
            for ((category, tables) in EXTENDED_TABLES) {
                if (category !in enabledTypes) {
                    continue
                }
                for (t in tables) {
                    if (!tableExists(t.name)) {
                        LOG.warn("Webhook diag: table {} MISSING", t.name)
                        continue
                    }
                    val total = session.database.rawQuery("SELECT COUNT(*) FROM ${dbTableName(t.name)}", null)
                        .use { if (it.moveToFirst()) it.getLong(0) else -1 }
                    val byDevice = session.database.rawQuery(
                        "SELECT ${t.deviceColumn}, COUNT(*) FROM ${dbTableName(t.name)} GROUP BY ${t.deviceColumn}", null,
                    ).use { c ->
                        val sb = StringBuilder()
                        while (c.moveToNext()) {
                            sb.append("dev=").append(c.getLong(0)).append(":").append(c.getLong(1)).append(" ")
                        }
                        sb.toString()
                    }
                    LOG.warn("Webhook diag: table={} total={} byDevice={}", t.name, total, byDevice)
                }
            }
        } catch (e: Exception) {
            LOG.warn("Webhook diag failed: {}", e.message)
        }
        // ---- end diagnostics ----

        for ((category, tables) in EXTENDED_TABLES) {
            if (category !in enabledTypes) {
                continue
            }
            val rows = JSONArray()
            for (t in tables) {
                if (!tableExists(t.name)) {
                    continue
                }
                try {
                    // tsMillis tables store epoch milliseconds (e.g. BaseActivitySummary.START_TIME),
                    // while our cursor range is in seconds — convert for the comparison.
                    val fromArg = if (t.tsMillis) from * 1000 else from
                    val toArg = if (t.tsMillis) to * 1000 else to
                    val cursor = session.database.rawQuery(
                        "SELECT * FROM ${dbTableName(t.name)} WHERE ${t.deviceColumn} = ?" +
                            " AND ${t.tsColumn} > ? AND ${t.tsColumn} <= ?" +
                            " ORDER BY ${t.tsColumn} DESC LIMIT $MAX_EXTENDED_ROWS_PER_TABLE",
                        arrayOf(deviceId.toString(), fromArg.toString(), toArg.toString()),
                    )
                    cursor.use {
                        while (it.moveToNext()) {
                            rows.put(cursorToJson(it, t))
                        }
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to read extended table {}: {}", t.name, e.message)
                }
            }
            if (rows.length() > 0) {
                result.put(category, rows)
            }
        }

        // Workout process data (5-second HR/step-rate/speed from Huawei) goes to its own
        // category so the server can present it as detail without mixing with summaries.
        if (WebhookConfig.TYPE_WORKOUTS in enabledTypes &&
            tableExists("HuaweiWorkoutDataSample") && tableExists("HuaweiWorkoutSummarySample")
        ) {
            try {
                val cursor = session.database.rawQuery(
                    "SELECT TIMESTAMP, HEART_RATE, STEP_RATE, SPEED FROM HUAWEI_WORKOUT_DATA_SAMPLE" +
                        " WHERE WORKOUT_ID IN (SELECT WORKOUT_ID FROM HUAWEI_WORKOUT_SUMMARY_SAMPLE" +
                        " WHERE DEVICE_ID = ? AND START_TIMESTAMP > ? AND START_TIMESTAMP <= ?)" +
                        " AND TIMESTAMP > ? AND TIMESTAMP <= ? ORDER BY TIMESTAMP DESC LIMIT 5000",
                    arrayOf(
                        deviceId.toString(), from.toString(), to.toString(),
                        from.toString(), to.toString(),
                    ),
                )
                val hrRows = JSONArray()
                cursor.use {
                    while (it.moveToNext()) {
                        val row = JSONObject()
                        row.put("timestamp", it.getLong(0))
                        // GB stores per-point heart rate / step rate as signed bytes,
                        // so values > 127 overflow to negatives (180 -> -76). The GB
                        // charts mask with & 0xFF on read; do the same here.
                        var hr = it.getLong(1)
                        if (hr < 0) hr += 256
                        if (hr > 0) row.put("heart_rate", hr)
                        var sr = it.getLong(2)
                        if (sr < 0) sr += 256
                        if (sr > 0) row.put("step_rate", sr)
                        val sp = it.getLong(3)
                        if (sp > 0) row.put("speed", sp)
                        hrRows.put(row)
                    }
                }
                if (hrRows.length() > 0) {
                    result.put("workout_hr", hrRows)
                }
            } catch (e: Exception) {
                LOG.warn("Failed to read workout process data: {}", e.message)
            }
        }
        return result
    }

    private fun cursorToJson(cursor: Cursor, table: ExtTable): JSONObject {
        val row = JSONObject()
        val columns = cursor.columnNames
        var rawTs = 0L
        for (i in columns.indices) {
            if (cursor.isNull(i)) {
                continue
            }
            val column = columns[i]
            val value = when (cursor.getType(i)) {
                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                Cursor.FIELD_TYPE_BLOB -> continue
                else -> continue
            }
            if (column.equals(table.tsColumn, ignoreCase = true)) {
                rawTs = value as Long
                continue
            }
            if (column == "DEVICE_ID" || column == "USER_ID") {
                continue
            }
            // GB stores several workout fields as signed bytes; normalize values
            // that overflowed (e.g. HR peak 180 -> -76, activity type 128 -> -128)
            // the same way the GB chart code masks with & 0xFF.
            val asLong = value as? Long
            val normalize = (column.equals("MIN_HEART_RATE_PEAK", ignoreCase = true) ||
                column.equals("MAX_HEART_RATE_PEAK", ignoreCase = true) ||
                (table.name == "HuaweiWorkoutSummarySample" && column.equals("TYPE", ignoreCase = true))) &&
                asLong != null && asLong < 0
            if (normalize) {
                row.put(column.lowercase(), asLong!! + 256)
                continue
            }
            row.put(column.lowercase(), value)
        }
        var ts = rawTs
        if (table.tsMillis) {
            ts /= 1000
        }
        row.put("timestamp", ts)
        if (table.seq > 0) {
            row.put("seq", table.seq)
        }
        return row
    }

    // ------------------------------------------------------------------ HTTP

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Default client: long timeouts for slow Cloudflare edges. */
    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Insecure client (self-signed / http-testing), only used when allowInsecure is on. */
    private val insecureClient: OkHttpClient by lazy {
        val trustAll = arrayOf<X509TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAll, SecureRandom())
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAll[0])
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * POSTs JSON and parses the response. Returns null on any network failure,
     * storing the raw error text for diagnostics (shown on the settings screen).
     */
    private fun postJson(serverUrl: String, headers: Map<String, String>, body: String): JSONObject? {
        return try {
            val builder = Request.Builder().url(serverUrl).post(body.toRequestBody(jsonMediaType))
            for ((key, value) in headers) {
                builder.addHeader(key, value)
            }
            val client = if (WebhookConfig.allowInsecure()) insecureClient else defaultClient
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string()
                if (text.isNullOrBlank()) {
                    val error = "HTTP ${response.code} with empty body"
                    LOG.warn("Empty response from {}: {}", serverUrl, error)
                    WebhookConfig.setLastError(error)
                    return null
                }
                try {
                    JSONObject(text)
                } catch (e: org.json.JSONException) {
                    // Response was not JSON (e.g. a Cloudflare error page). Surface the
                    // status code and a snippet so the real cause is visible.
                    val snippet = text.replace('\n', ' ').take(150)
                    val error = "HTTP ${response.code}: $snippet"
                    LOG.warn("Non-JSON response from {}: {}", serverUrl, error)
                    WebhookConfig.setLastError(error)
                    null
                }
            }
        } catch (e: Exception) {
            val reason = e.message ?: e.toString()
            LOG.warn("Webhook request to {} failed: {}", serverUrl, reason)
            WebhookConfig.setLastError(reason)
            null
        }
    }
}
