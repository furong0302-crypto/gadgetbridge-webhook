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

import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication

/**
 * Configuration for the Webhook upload module.
 *
 * All values are stored in the regular Gadgetbridge shared preferences, using the
 * "webhook_" prefix. Everything is a pure read/write wrapper around [GBApplication.getPrefs],
 * so this module never touches any upstream code paths.
 */
object WebhookConfig {

    const val PREF_ENABLED = "webhook_enabled"
    const val PREF_SERVER_URL = "webhook_server_url"
    const val PREF_TOKEN = "webhook_token"
    const val PREF_INTERVAL_MINUTES = "webhook_interval_minutes"
    const val PREF_ALLOW_INSECURE = "webhook_allow_insecure"

    /** Ask the device to sync before uploading, so the upload contains fresh data. */
    const val PREF_PRE_SYNC = "webhook_pre_sync"

    /** Epoch millis of the last pre-upload sync request (throttling). */
    const val PREF_PRE_SYNC_LAST = "webhook_pre_sync_last_time"

    /** Settings screen only: "upload now" button (not persisted). */
    const val PREF_RUN_NOW = "webhook_run_now"

    /** Settings screen only: "re-upload history" button (not persisted). */
    const val PREF_RESET_CURSOR = "webhook_reset_cursor"

    /** Which data categories to upload (StringSet of WebhookDataTypes values). */
    const val PREF_DATA_TYPES = "webhook_data_types"

    /** Binding code used to bind this phone's devices to a chat session on the server. */
    const val PREF_BINDING_CODE = "webhook_binding_code"

    private const val BINDING_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val BINDING_CODE_LENGTH = 6

    /** Data category keys for the upload data-type switches. */
    const val TYPE_SAMPLES = "samples"
    const val TYPE_DISTANCE = "distance"
    const val TYPE_BATTERY = "battery"
    const val TYPE_SPO2 = "spo2"
    const val TYPE_STRESS = "stress"
    const val TYPE_HRV = "hrv"
    const val TYPE_RESPIRATION = "respiration"
    const val TYPE_SLEEP_SESSIONS = "sleep_sessions"
    const val TYPE_DAILY_SUMMARY = "dailysummary"
    const val TYPE_PAI = "pai"
    const val TYPE_WORKOUTS = "workouts"

    val ALL_DATA_TYPES: Set<String> = setOf(
        TYPE_SAMPLES, TYPE_DISTANCE, TYPE_BATTERY, TYPE_SPO2, TYPE_STRESS, TYPE_HRV,
        TYPE_RESPIRATION, TYPE_SLEEP_SESSIONS, TYPE_DAILY_SUMMARY, TYPE_PAI, TYPE_WORKOUTS,
    )

    /** When the last upload (any kind) finished, epoch millis. */
    const val PREF_LAST_EXECUTION = "webhook_last_execution"

    /** Human readable result of the last upload, shown on the settings screen. */
    const val PREF_LAST_STATUS = "webhook_last_status"

    /** Last upload outcome type: "ok" / "pending" / "failed" / "" (never ran). */
    const val PREF_PAIR_STATUS = "webhook_pair_status"

    /** Raw network error text of the last failed upload (diagnostics). */
    const val PREF_LAST_ERROR = "webhook_last_error"

    const val PAIR_STATUS_OK = "ok"
    const val PAIR_STATUS_PENDING = "pending"
    const val PAIR_STATUS_FAILED = "failed"

    fun getPairStatus(): String = GBApplication.getPrefs().getString(PREF_PAIR_STATUS, "")

    fun setPairStatus(status: String) {
        GBApplication.getPrefs().preferences.edit {
            putString(PREF_PAIR_STATUS, status)
        }
    }

    fun getLastError(): String = GBApplication.getPrefs().getString(PREF_LAST_ERROR, "")

    fun setLastError(error: String) {
        GBApplication.getPrefs().preferences.edit {
            putString(PREF_LAST_ERROR, error)
        }
    }

    /** Epoch millis of the last sync-triggered (immediate) upload, for rate limiting. */
    const val PREF_LAST_IMMEDIATE = "webhook_last_immediate"

    const val DEFAULT_INTERVAL_MINUTES = 15

    /** Minimum gap between pre-upload sync requests, to keep BLE traffic (and battery
     *  drain on the band) reasonable. */
    const val PRE_SYNC_MIN_INTERVAL_MINUTES = 5

    const val PRE_SYNC_MIN_INTERVAL_MS = PRE_SYNC_MIN_INTERVAL_MINUTES * 60 * 1000L

    /** Minimum gap between sync-triggered uploads, to avoid hammering the server. */
    const val MIN_IMMEDIATE_INTERVAL_MS = 2 * 60 * 1000L

    /** How far back the first upload goes when no cursor exists yet, in seconds.
     *  Bands keep several days of data on the device, so backfill a week. */
    const val INITIAL_BACKFILL_SECONDS = 7 * 24 * 60 * 60L

    /** Safety cap for a single upload request, in seconds (one week). */
    const val MAX_RANGE_SECONDS = 7 * 24 * 60 * 60L

    /** Per-device upload cursor key: last successfully uploaded timestamp (epoch seconds). */
    fun cursorKey(address: String): String = "webhook_cursor_$address"

    /** Per-device "waiting for pairing" marker: while set, only a light heartbeat is sent. */
    fun pendingKey(address: String): String = "webhook_pending_$address"

    fun isPendingBind(address: String): Boolean =
        GBApplication.getPrefs().getBoolean(pendingKey(address), false)

    fun setPendingBind(address: String, pending: Boolean) {
        GBApplication.getPrefs().preferences.edit {
            putBoolean(pendingKey(address), pending)
        }
    }

    /** Clears all upload cursors, so the next upload re-backfills the full range. */
    fun resetCursors() {
        val editor = GBApplication.getPrefs().preferences.edit()
        for (key in GBApplication.getPrefs().preferences.all.keys) {
            if (key.startsWith("webhook_cursor_")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    /** Default Lianhuan direct-health endpoint. */
    const val DEFAULT_UPLOAD_PATH = "/api/health/gadgetbridge"

    fun isEnabled(): Boolean = GBApplication.getPrefs().getBoolean(PREF_ENABLED, false)

    /** Raw server address as entered by the user (root address, scheme optional). */
    fun getServerUrl(): String = GBApplication.getPrefs().getString(PREF_SERVER_URL, "").trim()

    /**
     * Full upload endpoint URL.
     *
     * The user only enters the root address (e.g. "example.com" or "http://192.168.1.5"),
     * everything else is completed automatically:
     * - "https://" is prepended when no scheme was given;
     * - "/api/health/gadgetbridge" is appended unless a full /api/ URL was entered.
     */
    fun getUploadEndpoint(): String {
        var url = getServerUrl()
        if (url.isEmpty()) {
            return ""
        }
        if (!url.contains("://")) {
            url = "https://$url"
        }
        if (!url.contains("/api/")) {
            url = url.trimEnd('/') + DEFAULT_UPLOAD_PATH
        }
        return url
    }

    fun getToken(): String = GBApplication.getPrefs().getString(PREF_TOKEN, "").trim()

    fun getIntervalMinutes(): Int =
        GBApplication.getPrefs().getInt(PREF_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES).coerceAtLeast(1)

    fun allowInsecure(): Boolean = GBApplication.getPrefs().getBoolean(PREF_ALLOW_INSECURE, false)

    /** Whether to nudge the device for a sync before uploading (default: on). */
    fun isPreSyncEnabled(): Boolean = GBApplication.getPrefs().getBoolean(PREF_PRE_SYNC, true)

    fun getPreSyncLast(): Long = GBApplication.getPrefs().getLong(PREF_PRE_SYNC_LAST, 0)

    fun setPreSyncLast(timestampMillis: Long) {
        GBApplication.getPrefs().preferences.edit {
            putLong(PREF_PRE_SYNC_LAST, timestampMillis)
        }
    }

    /**
     * Enabled data categories. When the preference was never set, everything is enabled.
     */
    fun getEnabledDataTypes(): Set<String> {
        val stored = GBApplication.getPrefs().getStringSet(PREF_DATA_TYPES, null)
        if (stored == null) {
            return ALL_DATA_TYPES
        }
        return stored
    }

    fun isDataTypeEnabled(type: String): Boolean = type in getEnabledDataTypes()

    fun getCursor(address: String): Long = GBApplication.getPrefs().getLong(cursorKey(address), 0)

    fun setCursor(address: String, timestampSeconds: Long) {
        GBApplication.getPrefs().preferences.edit {
            putLong(cursorKey(address), timestampSeconds)
        }
    }

    fun getLastExecution(): Long = GBApplication.getPrefs().getLong(PREF_LAST_EXECUTION, 0)

    fun setLastExecution(timestampMillis: Long) {
        GBApplication.getPrefs().preferences.edit {
            putLong(PREF_LAST_EXECUTION, timestampMillis)
        }
    }

    fun getLastStatus(): String = GBApplication.getPrefs().getString(PREF_LAST_STATUS, "")

    fun setLastStatus(status: String) {
        GBApplication.getPrefs().preferences.edit {
            putString(PREF_LAST_STATUS, status)
        }
    }

    fun getLastImmediate(): Long = GBApplication.getPrefs().getLong(PREF_LAST_IMMEDIATE, 0)

    fun setLastImmediate(timestampMillis: Long) {
        GBApplication.getPrefs().preferences.edit {
            putLong(PREF_LAST_IMMEDIATE, timestampMillis)
        }
    }

    /**
     * The binding code for this phone, generated once on first access and persisted.
     * Sent with every upload so the server can bind this device to a chat session.
     * Displayed on the settings screen as "GB-XXXXXX".
     */
    fun getOrCreateBindingCode(): String {
        val prefs = GBApplication.getPrefs()
        val existing = prefs.getString(PREF_BINDING_CODE, "")
        if (existing.isNotEmpty()) {
            return existing
        }
        val random = java.security.SecureRandom()
        val code = buildString {
            repeat(BINDING_CODE_LENGTH) {
                append(BINDING_CODE_ALPHABET[random.nextInt(BINDING_CODE_ALPHABET.length)])
            }
        }
        prefs.preferences.edit {
            putString(PREF_BINDING_CODE, code)
        }
        return code
    }
}
